package com.feniqo.mobile.presentation.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncDisplayModelTest {

    @Test
    fun testPriority1_conflictTakesHighestPrecedenceAndShowsNoAction() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.OFFLINE,
            isSyncing = true,
            pendingCount = 5,
            failedCount = 3,
            conflictCount = 2,
            lastSuccessfulSyncAtEpochMillis = 1000L,
            errorType = SyncErrorUiType.NETWORK,
            canManualSync = false,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.CONFLICT, model.severity)
        assertEquals("Çakışma", model.title)
        assertTrue(model.message.contains("2 çakışma"))
        assertFalse(model.showProgress)
        assertNull(model.actionType)
        assertNull(model.actionText)
        assertFalse(model.isActionEnabled)
    }

    @Test
    fun testPriority2_errorWithFailedCountOffersRetryAction() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.ONLINE,
            isSyncing = false,
            pendingCount = 5,
            failedCount = 2,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = SyncErrorUiType.NETWORK,
            canManualSync = true,
            canRetryFailed = true,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.ERROR, model.severity)
        assertEquals("Hata", model.title)
        assertEquals(SyncDisplayActionType.RETRY_FAILED, model.actionType)
        assertEquals("Yeniden Dene", model.actionText)
        assertTrue(model.isActionEnabled)
    }

    @Test
    fun testPriority2_networkOrUnknownErrorWithoutFailedCountOffersManualSyncAction() {
        val networkErrorState = SyncStatusUiState(
            connectionState = SyncConnectionUiState.ONLINE,
            isSyncing = false,
            pendingCount = 0,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = SyncErrorUiType.NETWORK,
            canManualSync = true,
            canRetryFailed = false,
        )

        val networkModel = resolveSyncDisplayModel(networkErrorState)
        assertEquals(SyncDisplaySeverity.ERROR, networkModel.severity)
        assertEquals(SyncDisplayActionType.MANUAL_SYNC, networkModel.actionType)
        assertEquals("Yeniden Dene", networkModel.actionText)
        assertTrue(networkModel.isActionEnabled)

        val unknownErrorState = networkErrorState.copy(errorType = SyncErrorUiType.UNKNOWN)
        val unknownModel = resolveSyncDisplayModel(unknownErrorState)
        assertEquals(SyncDisplaySeverity.ERROR, unknownModel.severity)
        assertEquals(SyncDisplayActionType.MANUAL_SYNC, unknownModel.actionType)
        assertEquals("Yeniden Dene", unknownModel.actionText)
        assertTrue(unknownModel.isActionEnabled)
    }

    @Test
    fun testPriority2_authStorageValidationConflictErrorsShowNoAction() {
        val nonRetryableErrors = listOf(
            SyncErrorUiType.AUTHENTICATION,
            SyncErrorUiType.STORAGE,
            SyncErrorUiType.VALIDATION,
            SyncErrorUiType.CONFLICT,
        )

        for (errorType in nonRetryableErrors) {
            val state = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = errorType,
                canManualSync = true,
                canRetryFailed = false,
            )

            val model = resolveSyncDisplayModel(state)
            assertEquals(SyncDisplaySeverity.ERROR, model.severity)
            assertNull(model.actionType)
            assertNull(model.actionText)
            assertFalse(model.isActionEnabled)
        }
    }

    @Test
    fun testPriority2_allErrorTypesMapToSafeTurkishMessages() {
        val errorMappings = mapOf(
            SyncErrorUiType.NETWORK to "Ağ bağlantısı hatası oluştu.",
            SyncErrorUiType.AUTHENTICATION to "Oturum süresi doldu. Lütfen tekrar giriş yapın.",
            SyncErrorUiType.CONFLICT to "Veri çakışması tespit edildi.",
            SyncErrorUiType.VALIDATION to "Geçersiz veri nedeniyle senkronizasyon tamamlanamadı.",
            SyncErrorUiType.STORAGE to "Cihaz depolama hatası oluştu.",
            SyncErrorUiType.UNKNOWN to "Senkronizasyon sırasında beklenmeyen bir hata oluştu.",
        )

        for ((errorType, expectedMessage) in errorMappings) {
            val state = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 1,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = errorType,
                canManualSync = true,
                canRetryFailed = true,
            )
            val model = resolveSyncDisplayModel(state)
            assertEquals(SyncDisplaySeverity.ERROR, model.severity)
            assertEquals(expectedMessage, model.message)
        }
    }

    @Test
    fun testPriority3_offlineTakesPrecedenceOverCheckingAndBelow() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.OFFLINE,
            isSyncing = true,
            pendingCount = 4,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = null,
            canManualSync = false,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.OFFLINE, model.severity)
        assertEquals("Çevrimdışı", model.title)
        assertEquals("Çevrimdışı — değişikliklerin cihazda güvende.", model.message)
        assertFalse(model.showProgress)
        assertNull(model.actionType)
    }

    @Test
    fun testPriority4_checkingTakesPrecedenceOverSyncingAndBelow() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.CHECKING,
            isSyncing = true,
            pendingCount = 2,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = null,
            canManualSync = false,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.CHECKING, model.severity)
        assertEquals("Bağlantı kontrol ediliyor...", model.message)
        assertTrue(model.showProgress)
        assertNull(model.actionType)
    }

    @Test
    fun testPriority5_syncingTakesPrecedenceOverPending() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.ONLINE,
            isSyncing = true,
            pendingCount = 3,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = null,
            canManualSync = false,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.SYNCING, model.severity)
        assertEquals("Senkronize ediliyor...", model.message)
        assertTrue(model.showProgress)
        assertNull(model.actionType)
    }

    @Test
    fun testPriority6_pendingTakesPrecedenceOverUpToDate() {
        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.ONLINE,
            isSyncing = false,
            pendingCount = 3,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = null,
            errorType = null,
            canManualSync = true,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state)
        assertEquals(SyncDisplaySeverity.PENDING, model.severity)
        assertEquals("3 değişiklik eşitlenmeyi bekliyor.", model.message)
        assertEquals(SyncDisplayActionType.MANUAL_SYNC, model.actionType)
        assertEquals("Eşitle", model.actionText)
        assertTrue(model.isActionEnabled)
    }

    @Test
    fun testPriority7_upToDateShowsFormattedTimeAndManualSyncAction() {
        val now = 1_000_000L
        val syncTime = now - (5 * 60 * 1000L) // 5 mins ago

        val state = SyncStatusUiState(
            connectionState = SyncConnectionUiState.ONLINE,
            isSyncing = false,
            pendingCount = 0,
            failedCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAtEpochMillis = syncTime,
            errorType = null,
            canManualSync = true,
            canRetryFailed = false,
        )

        val model = resolveSyncDisplayModel(state, nowEpochMillis = now)
        assertEquals(SyncDisplaySeverity.UP_TO_DATE, model.severity)
        assertEquals("Son eşitleme: 5 dk önce", model.message)
        assertEquals(SyncDisplayActionType.MANUAL_SYNC, model.actionType)
        assertEquals("Şimdi Eşitle", model.actionText)
        assertTrue(model.isActionEnabled)
    }

    @Test
    fun testFormatRelativeTime() {
        val now = 100_000_000L

        assertNull(formatRelativeTime(null, now))
        assertEquals("Az önce", formatRelativeTime(now - 30_000L, now))
        assertEquals("15 dk önce", formatRelativeTime(now - (15 * 60 * 1000L), now))
        assertEquals("2 sa önce", formatRelativeTime(now - (2 * 60 * 60 * 1000L), now))
        assertEquals("3 gün önce", formatRelativeTime(now - (3 * 24 * 60 * 60 * 1000L), now))
    }
}
