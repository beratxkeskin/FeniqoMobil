package com.feniqo.mobile.data.remote.realtime

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.SyncEntityType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kanal yaşam döngüsünü ve tablo değişikliklerini tek, platformdan bağımsız akışta taşır. */
sealed interface RealtimeSignal

/** İlk bağlantıda ve her başarılı yeniden bağlantıda kaçırılmış kayıtları kontrol ettirir. */
data object RealtimeConnectionReady : RealtimeSignal

/** Realtime payload'ını veri kaynağı yapmadan yalnız Room pull sinyaline dönüştürür. */
data class RealtimeInvalidation(
    val entityType: SyncEntityType,
) : RealtimeSignal

interface RealtimeInvalidationSource {
    fun observeFor(userId: EntityId): Flow<RealtimeSignal>
}

class SupabaseRealtimeInvalidationSource(
    private val client: SupabaseClient,
) : RealtimeInvalidationSource {

    override fun observeFor(userId: EntityId): Flow<RealtimeSignal> = channelFlow {
        val channel = client.channel(CHANNEL_NAME)
        val collectors = listOf(
            launch {
                channel.status.asConnectionReadySignals().collect { send(it) }
            },
            launch {
                channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
                    table = PROFILES_TABLE
                    filter(PROFILE_OWNER_COLUMN, FilterOperator.EQ, userId.value)
                }.collect { send(RealtimeInvalidation(SyncEntityType.PROFILE)) }
            },
            launch {
                channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
                    table = CATEGORIES_TABLE
                    filter(ROW_OWNER_COLUMN, FilterOperator.EQ, userId.value)
                }.collect { send(RealtimeInvalidation(SyncEntityType.CATEGORY)) }
            },
            launch {
                channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
                    table = TRANSACTIONS_TABLE
                    filter(ROW_OWNER_COLUMN, FilterOperator.EQ, userId.value)
                }.collect { send(RealtimeInvalidation(SyncEntityType.TRANSACTION)) }
            },
        )

        try {
            channel.subscribe(blockUntilSubscribed = true)
            awaitCancellation()
        } finally {
            collectors.forEach { it.cancel() }
            withContext(NonCancellable) {
                channel.unsubscribe()
            }
        }
    }

    private companion object {
        const val CHANNEL_NAME = "feniqo-v1-room-invalidation"
        const val PUBLIC_SCHEMA = "public"
        const val PROFILES_TABLE = "profiles"
        const val CATEGORIES_TABLE = "categories"
        const val TRANSACTIONS_TABLE = "transactions"
        const val PROFILE_OWNER_COLUMN = "id"
        const val ROW_OWNER_COLUMN = "user_id"
    }
}

/** Yalnız gerçek abonelik başarılarını iletir; ara durumlar Room senkronizasyonu başlatmaz. */
internal fun Flow<RealtimeChannel.Status>.asConnectionReadySignals(): Flow<RealtimeSignal> =
    filter { it == RealtimeChannel.Status.SUBSCRIBED }
        .map { RealtimeConnectionReady }
