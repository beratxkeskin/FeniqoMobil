package com.feniqo.mobile.presentation.settings

import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.BackupScope
import com.feniqo.mobile.data.backup.PersonalBackupExporter
import com.feniqo.mobile.data.backup.PersonalBackupImporter
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savePersonalInfo_validatesDisplayName_viaAuthValidationRules() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        // Boş veya çok kısa isim (AuthValidationRules MIN_FULL_NAME_LENGTH = 2)
        viewModel.savePersonalInfo("A")
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.profileError)
        assertEquals(false, viewModel.uiState.value.profileSaveSuccess)

        // Geçerli görünen ad
        viewModel.savePersonalInfo("Ali Veli")
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.profileError)
        assertEquals(true, viewModel.uiState.value.profileSaveSuccess)
        assertEquals("Ali Veli", authRepo.updatedFullName)
    }

    @Test
    fun changePassword_emptyCurrentPassword_doesNotCallRepository() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("", "newpass123", "newpass123")
        testScheduler.advanceUntilIdle()

        assertEquals(0, authRepo.changePasswordCallCount)
        assertEquals("Mevcut parolanızı girmelisiniz.", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_shortNewPassword_doesNotCallRepository() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("oldpass123", "12345", "12345")
        testScheduler.advanceUntilIdle()

        assertEquals(0, authRepo.changePasswordCallCount)
        assertTrue(viewModel.uiState.value.passwordError?.contains("en az 6") == true)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_mismatchedConfirmPassword_doesNotCallRepository() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("oldpass123", "newpass123", "mismatch123")
        testScheduler.advanceUntilIdle()

        assertEquals(0, authRepo.changePasswordCallCount)
        assertEquals("Yeni parolalar birbiriyle eşleşmiyor.", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_samePasswordAsCurrent_doesNotCallRepository() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("samepassword123", "samepassword123", "samepassword123")
        testScheduler.advanceUntilIdle()

        assertEquals(0, authRepo.changePasswordCallCount)
        assertEquals("Yeni parolanız mevcut parolanızdan farklı olmalıdır.", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_validInput_callsRepositoryOnceWithArgumentsAndSucceeds() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("oldpass123", "newpass123", "newpass123")
        testScheduler.advanceUntilIdle()

        assertEquals(1, authRepo.changePasswordCallCount)
        assertEquals("oldpass123", authRepo.receivedCurrentPassword)
        assertEquals("newpass123", authRepo.receivedNewPassword)
        assertTrue(viewModel.uiState.value.passwordSuccess)
        assertNull(viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_invalidCredentials_showsAppropriateError() = runTest {
        val authRepo = FakeAuthRepository().apply {
            changePasswordResult = RepositoryResult.Failure(AppError.Authentication("auth_invalid_credentials"))
        }
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("wrongpass123", "newpass123", "newpass123")
        testScheduler.advanceUntilIdle()

        assertEquals(1, authRepo.changePasswordCallCount)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertEquals("Mevcut parolanız hatalı.", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_networkUnavailable_showsNetworkError() = runTest {
        val authRepo = FakeAuthRepository().apply {
            changePasswordResult = RepositoryResult.Failure(AppError.Network("network_unavailable"))
        }
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("oldpass123", "newpass123", "newpass123")
        testScheduler.advanceUntilIdle()

        assertEquals(1, authRepo.changePasswordCallCount)
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertEquals("Bağlantı kurulamadı. Lütfen biraz sonra tekrar deneyin.", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun changePassword_clearPasswordStatus_clearsSuccessAndErrorState() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.changePassword("oldpass123", "newpass123", "newpass123")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.passwordSuccess)

        viewModel.clearPasswordStatus()
        assertFalse(viewModel.uiState.value.passwordSuccess)
        assertNull(viewModel.uiState.value.passwordError)
    }

    @Test
    fun changePassword_concurrentCall_isBlockedWhileFirstIsRunning() = runTest {
        val authRepo = FakeAuthRepository()
        val gate = CompletableDeferred<Unit>()
        authRepo.changePasswordGate = gate
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        // İlk çağrı başlar ve gate üzerinde asılı kalır
        viewModel.changePassword("oldpass123", "newpass123", "newpass123")
        // İlk çağrı repository'yi tetiklemiş olmalı
        assertEquals(1, authRepo.changePasswordCallCount)
        assertTrue(viewModel.uiState.value.isChangingPassword)

        // İkinci eşzamanlı çağrı yapılır
        viewModel.changePassword("oldpass123", "anotherpass123", "anotherpass123")
        // İkinci çağrı koruma nedeniyle yoksayılır, çağrı sayısı 1 kalır
        assertEquals(1, authRepo.changePasswordCallCount)

        // İlk çağrının tamamlanmasına izin verilir
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, authRepo.changePasswordCallCount)
        assertEquals("newpass123", authRepo.receivedNewPassword)
        assertTrue(viewModel.uiState.value.passwordSuccess)
        assertFalse(viewModel.uiState.value.isChangingPassword)
    }

    @Test
    fun updateNotificationPreferences_delegatesToRepository() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)

        val newPrefs = NotificationPreferences(
            enabled = true,
            remindSubscriptions = false,
            remindDebts = true,
            quietHoursEnabled = true,
            quietHoursStartHour = 23,
            quietHoursEndHour = 7,
        )

        viewModel.updateNotificationPreferences(newPrefs)
        testScheduler.advanceUntilIdle()

        assertEquals(newPrefs, settingsRepo.currentSettings.value.notifications)
    }

    @Test
    fun setRegion_persistsToUserSettings() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)

        viewModel.setRegion("US")
        testScheduler.advanceUntilIdle()

        assertEquals("US", settingsRepo.currentSettings.value.region)
    }

    @Test
    fun requestEmailChange_validatesEmailFormat() = runTest {
        val authRepo = FakeAuthRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val viewModel = createViewModel(authRepo, settingsRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        // Geçersiz e-posta
        var onSuccessCalled = false
        viewModel.requestEmailChange("not-an-email") { onSuccessCalled = true }
        testScheduler.advanceUntilIdle()

        assertEquals(false, onSuccessCalled)
        assertNotNull(viewModel.uiState.value.emailError)

        // Geçerli e-posta
        viewModel.requestEmailChange("valid@example.com") { onSuccessCalled = true }
        testScheduler.advanceUntilIdle()

        assertEquals(true, onSuccessCalled)
        assertNull(viewModel.uiState.value.emailError)
    }

    private fun createViewModel(
        authRepository: AuthRepository,
        userSettingsRepository: UserSettingsRepository,
    ): SettingsViewModel {
        val backupExporter = object : PersonalBackupExporter {
            override suspend fun export(): String = "{}"
            override suspend fun calculateScope(): BackupScope = BackupScope(0, 0)
        }
        val backupImporter = object : PersonalBackupImporter {
            override suspend fun import(raw: String): BackupImportResult = BackupImportResult.Success(0, 0)
        }
        val observeTransactionsUseCase = ObserveTransactionsUseCase(FakeTransactionRepository())
        val csvExporter = TransactionCsvExporter()
        val observeSyncOverviewUseCase = ObserveSyncOverviewUseCase(FakeSyncRepository())

        return SettingsViewModel(
            authRepository = authRepository,
            userSettingsRepository = userSettingsRepository,
            backupExporter = backupExporter,
            backupImporter = backupImporter,
            observeTransactions = observeTransactionsUseCase,
            csvExporter = csvExporter,
            observeSyncOverviewUseCase = observeSyncOverviewUseCase,
        )
    }

    private class FakeAuthRepository : AuthRepository {
        var updatedFullName: String? = null
        val profile = MutableStateFlow<UserProfile?>(
            UserProfile(
                id = EntityId("user-1"),
                email = "user@example.com",
                fullName = "Mevcut Kullanıcı",
                currency = Currency.TRY,
                themePreference = ThemePreference.SYSTEM,
                language = AppLanguage.TR,
                activeWorkspaceId = null,
                createdAt = Instant.fromEpochMilliseconds(0),
            )
        )
        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = profile
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
        override suspend fun updateFullName(fullName: String?): RepositoryResult<Unit> {
            updatedFullName = fullName
            profile.value = profile.value?.copy(fullName = fullName ?: "")
            return RepositoryResult.Success(Unit)
        }

        var receivedCurrentPassword: String? = null
        var receivedNewPassword: String? = null
        var changePasswordCallCount = 0
        var changePasswordResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var changePasswordGate: CompletableDeferred<Unit>? = null

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): RepositoryResult<Unit> {
            changePasswordCallCount++
            receivedCurrentPassword = currentPassword
            receivedNewPassword = newPassword
            changePasswordGate?.await()
            return changePasswordResult
        }
    }

    private class FakeUserSettingsRepository : UserSettingsRepository {
        val currentSettings = MutableStateFlow(UserSettings())

        override fun observeSettings(): Flow<UserSettings> = currentSettings
        override suspend fun updateTheme(theme: ThemePreference): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(theme = theme)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateCurrency(currency: Currency): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(currency = currency)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateLanguage(language: AppLanguage): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(language = language)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateRegion(region: String): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(region = region)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateDateFormat(format: DateFormatPreference): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(dateFormat = format)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateNumberFormat(format: NumberFormatPreference): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(numberFormat = format)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateFirstDayOfWeek(firstDay: FirstDayOfWeekPreference): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(firstDayOfWeek = firstDay)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateMaskAmounts(mask: Boolean): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(maskAmounts = mask)
            return RepositoryResult.Success(Unit)
        }
        override suspend fun updateNotificationPreferences(preferences: NotificationPreferences): RepositoryResult<Unit> {
            currentSettings.value = currentSettings.value.copy(notifications = preferences)
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeSyncRepository : com.feniqo.mobile.domain.repository.SyncRepository {
        val overviewState = MutableStateFlow(
            SyncOverview(
                phase = SyncPhase.IDLE,
                pendingOperationCount = 0,
                failedOperationCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAt = null,
                lastError = null,
            )
        )
        override fun observeOverview(): Flow<SyncOverview> = overviewState
        override fun observeConflicts(): Flow<List<com.feniqo.mobile.domain.repository.SyncConflict>> = MutableStateFlow(emptyList())
        override suspend fun requestSync(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun retryFailedOperations(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun resolveConflict(
            entityId: EntityId,
            resolution: com.feniqo.mobile.domain.repository.ConflictResolution,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepository : TransactionRepository {
        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = MutableStateFlow(null)
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("t-1"))
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("g-1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
