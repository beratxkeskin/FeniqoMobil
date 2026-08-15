package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.domain.model.EntityId
import kotlin.time.Instant

/** İlk girişte V1 kişisel verisini Room'a tek ve atomik bir anlık görüntü olarak uygular. */
class InitialRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    suspend fun pullFor(userId: EntityId): InitialSyncResult {
        val profile = requireNotNull(remote.fetchProfile(userId.value)) {
            "Geçerli oturum için uzak profil bulunamadı."
        }
        val categories = fetchAll { page ->
            remote.fetchCategories(CategoryRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val transactions = fetchAll { page ->
            remote.fetchTransactions(TransactionRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val receivedAt = nowEpochMillisProvider()

        remoteSyncDao.applyInitialSnapshot(
            profile = profile.toDomain().toEntity(profile.toRemoteSyncMetadata(receivedAt)),
            categories = categories.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt), slug = dto.slug)
            },
            transactions = transactions.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            cursors = listOfNotNull(
                cursorFor(PROFILE, listOf(profile.id to (profile.updatedAt ?: profile.createdAt))),
                cursorFor(CATEGORY, categories.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(TRANSACTION, transactions.map { it.id to (it.updatedAt ?: it.createdAt) }),
            ),
        )
        return InitialSyncResult(categories.size, transactions.size)
    }

    private suspend fun <T> fetchAll(fetch: suspend (RemotePageRequest) -> RemotePage<T>): List<T> {
        val result = mutableListOf<T>()
        var request = RemotePageRequest()
        do {
            val page = fetch(request)
            result += page.items
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)
        return result
    }

    private fun cursorFor(entityType: String, records: List<Pair<String, String>>): SyncCursorEntity? {
        val last = records.maxWithOrNull(
            compareBy<Pair<String, String>> { Instant.parse(it.second) }.thenBy { it.first },
        ) ?: return null
        return SyncCursorEntity(
            entityTypeCode = entityType,
            updatedAtEpochMillis = Instant.parse(last.second).toEpochMilliseconds(),
            entityId = last.first,
        )
    }

    private companion object {
        const val PROFILE = "PROFILE"
        const val CATEGORY = "CATEGORY"
        const val TRANSACTION = "TRANSACTION"
    }
}

data class InitialSyncResult(
    val categoryCount: Int,
    val transactionCount: Int,
)
