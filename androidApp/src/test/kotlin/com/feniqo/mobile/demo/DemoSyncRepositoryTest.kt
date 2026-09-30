package com.feniqo.mobile.demo

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.domain.repository.SyncPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class DemoSyncRepositoryTest {

    private val canonicalUserScope = SyncScopeKey.user(DemoAuthRepository.USER_ID.value).rawValue

    private fun createFakes(
        initialPendingCount: Int = 0,
        onScopeObserved: (String) -> Unit = {},
    ): Pair<OfflineWriteQueue, MutableStateFlow<Int>> {
        val pendingCountFlow = MutableStateFlow(initialPendingCount)

        val mutationDao = Proxy.newProxyInstance(
            LocalMutationDao::class.java.classLoader,
            arrayOf(LocalMutationDao::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    return when (method.returnType) {
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Boolean.TYPE -> false
                        else -> null
                    }
                }
            },
        ) as LocalMutationDao

        val operationDao = Proxy.newProxyInstance(
            SyncOperationDao::class.java.classLoader,
            arrayOf(SyncOperationDao::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    if (method.name == "observePendingCount" && args != null && args.isNotEmpty()) {
                        val scopeKey = args[0] as String
                        onScopeObserved(scopeKey)
                        return pendingCountFlow
                    }
                    return when (method.returnType) {
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Boolean.TYPE -> false
                        else -> null
                    }
                }
            },
        ) as SyncOperationDao

        val queue = OfflineWriteQueue(
            mutationDao = mutationDao,
            operationDao = operationDao,
        )
        return queue to pendingCountFlow
    }

    @Test
    fun demo_overview_explicitly_reports_no_legacy_quarantine() = runTest {
        val (queue, _) = createFakes(initialPendingCount = 0)
        val repository = DemoSyncRepository(queue)

        val overview = repository.observeOverview().first()

        assertFalse(overview.hasLegacyQuarantinedData)
        assertEquals(SyncPhase.OFFLINE, overview.phase)
        assertEquals(0, overview.failedOperationCount)
        assertEquals(0, overview.conflictCount)
        assertNull(overview.lastSuccessfulSyncAt)
        assertNull(overview.lastError)
    }

    @Test
    fun demo_pending_count_uses_only_canonical_demo_user_scope() = runTest {
        val observedScopes = mutableListOf<String>()
        val (queue, pendingFlow) = createFakes(
            initialPendingCount = 7,
            onScopeObserved = { observedScopes.add(it) },
        )
        val repository = DemoSyncRepository(queue)

        val overviewInitial = repository.observeOverview().first()

        assertEquals(listOf(canonicalUserScope), observedScopes)
        assertEquals(7, overviewInitial.pendingOperationCount)
        assertFalse(overviewInitial.hasLegacyQuarantinedData)

        // Verifying update via canonical flow
        pendingFlow.value = 12
        val overviewUpdated = repository.observeOverview().first()
        assertEquals(12, overviewUpdated.pendingOperationCount)
        assertFalse(overviewUpdated.hasLegacyQuarantinedData)

        // Ensure canonical scope format is strictly USER:<uuid>
        assertEquals("USER:${DemoAuthRepository.USER_ID.value}", canonicalUserScope)
        assertFalse(observedScopes.contains("LEGACY_UNRESOLVED"))
    }
}
