package com.feniqo.mobile.data.remote.realtime

import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeInvalidationSourceTest {

    @Test
    fun connection_loss_and_rejoin_emit_two_catch_up_signals() = runTest {
        val statuses = flowOf(
            RealtimeChannel.Status.UNSUBSCRIBED,
            RealtimeChannel.Status.SUBSCRIBING,
            RealtimeChannel.Status.SUBSCRIBED,
            RealtimeChannel.Status.UNSUBSCRIBING,
            RealtimeChannel.Status.UNSUBSCRIBED,
            RealtimeChannel.Status.SUBSCRIBING,
            RealtimeChannel.Status.SUBSCRIBED,
        )

        val signals = statuses.asConnectionReadySignals().toList()

        assertEquals(listOf(RealtimeConnectionReady, RealtimeConnectionReady), signals)
    }
}
