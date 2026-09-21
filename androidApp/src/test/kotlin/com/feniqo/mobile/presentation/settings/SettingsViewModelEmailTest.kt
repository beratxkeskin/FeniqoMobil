package com.feniqo.mobile.presentation.settings

import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.BackupScope
import com.feniqo.mobile.data.backup.PersonalBackupExporter
import com.feniqo.mobile.data.backup.PersonalBackupImporter
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelEmailTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialEmailVerificationStatus_whenSessionIsNull_isUnknown() = runTest {
        val authRepo = EmailTestFakeAuthRepository(initialSession = null)
        val viewModel = createViewModel(authRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        // Asla false/pending varsayılmamalı, UNKNOWN olmalı
        assertEquals(EmailVerificationStatus.UNKNOWN, viewModel.uiState.value.emailVerificationStatus)
    }

    @Test
    fun emailVerificationStatus_updatesToVerified_whenSessionBecomesVerified() = runTest {
        val authRepo = EmailTestFakeAuthRepository(
            initialSession = AuthSession(
                userId = EntityId("user-1"),
                email = "test@example.com",
                expiresAt = Instant.fromEpochMilliseconds(1000),
                emailVerificationStatus = EmailVerificationStatus.PENDING_VERIFICATION,
            )
        )
        val viewModel = createViewModel(authRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        assertEquals(EmailVerificationStatus.PENDING_VERIFICATION, viewModel.uiState.value.emailVerificationStatus)

        // Oturum doğrulandı olarak güncellendiğinde
        authRepo.sessionFlow.value = AuthSession(
            userId = EntityId("user-1"),
            email = "test@example.com",
            expiresAt = Instant.fromEpochMilliseconds(2000),
            emailVerificationStatus = EmailVerificationStatus.VERIFIED,
        )
        testScheduler.advanceUntilIdle()

        assertEquals(EmailVerificationStatus.VERIFIED, viewModel.uiState.value.emailVerificationStatus)
    }

    @Test
    fun refreshEmailVerificationStatus_callsAuthRepositoryRefreshSession() = runTest {
        val authRepo = EmailTestFakeAuthRepository(initialSession = null)
        val viewModel = createViewModel(authRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.refreshEmailVerificationStatus()
        testScheduler.advanceUntilIdle()

        assertEquals(1, authRepo.refreshSessionCallCount)
    }

    @Test
    fun resendEmailVerification_withValidEmail_callsRepositoryAndSetsSuccess() = runTest {
        val authRepo = EmailTestFakeAuthRepository(initialSession = null)
        val viewModel = createViewModel(authRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.resendEmailVerification("valid@example.com")
        testScheduler.advanceUntilIdle()

        assertEquals("valid@example.com", authRepo.lastResentEmail)
        assertTrue(viewModel.uiState.value.emailVerificationSent)
        assertFalse(viewModel.uiState.value.isSendingEmailVerification)
    }

    @Test
    fun resendEmailVerification_withBlankEmail_setsErrorWithoutCallingRepository() = runTest {
        val authRepo = EmailTestFakeAuthRepository(initialSession = null)
        val viewModel = createViewModel(authRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.resendEmailVerification("")
        testScheduler.advanceUntilIdle()

        assertEquals(null, authRepo.lastResentEmail)
        assertFalse(viewModel.uiState.value.emailVerificationSent)
        assertEquals("Geçerli bir e-posta adresi bulunamadı.", viewModel.uiState.value.emailError)
    }

    private fun createViewModel(authRepo: EmailTestFakeAuthRepository): SettingsViewModel {
        val dummySettingsRepo = object : UserSettingsRepository {
            override fun observeSettings(): Flow<UserSettings> = MutableStateFlow(UserSettings())
            override suspend fun updateTheme(theme: ThemePreference) = RepositoryResult.Success(Unit)
            override suspend fun updateCurrency(currency: Currency) = RepositoryResult.Success(Unit)
            override suspend fun updateLanguage(language: AppLanguage) = RepositoryResult.Success(Unit)
            override suspend fun updateRegion(region: String) = RepositoryResult.Success(Unit)
            override suspend fun updateDateFormat(format: com.feniqo.mobile.domain.model.DateFormatPreference) = RepositoryResult.Success(Unit)
            override suspend fun updateNumberFormat(format: com.feniqo.mobile.domain.model.NumberFormatPreference) = RepositoryResult.Success(Unit)
            override suspend fun updateFirstDayOfWeek(firstDay: com.feniqo.mobile.domain.model.FirstDayOfWeekPreference) = RepositoryResult.Success(Unit)
            override suspend fun updateMaskAmounts(mask: Boolean) = RepositoryResult.Success(Unit)
            override suspend fun updateNotificationPreferences(preferences: com.feniqo.mobile.domain.model.NotificationPreferences) = RepositoryResult.Success(Unit)
        }
        val dummyBackupExporter = object : PersonalBackupExporter {
            override suspend fun export(): String = "{}"
            override suspend fun calculateScope(): BackupScope = BackupScope(0, 0)
        }
        val dummyBackupImporter = object : PersonalBackupImporter {
            override suspend fun import(raw: String): BackupImportResult = BackupImportResult.Success(0, 0)
        }
        val dummyTransactionRepo = object : com.feniqo.mobile.domain.repository.TransactionRepository {
            override fun observeTransactions(filter: com.feniqo.mobile.domain.repository.TransactionFilter) = MutableStateFlow(emptyList<com.feniqo.mobile.domain.model.Transaction>())
            override fun observeTransaction(id: EntityId) = MutableStateFlow(null)
            override fun observeInstallmentGroup(groupId: EntityId) = MutableStateFlow(emptyList<com.feniqo.mobile.domain.model.Transaction>())
            override suspend fun create(transaction: com.feniqo.mobile.domain.model.Transaction) = RepositoryResult.Success(EntityId("t-1"))
            override suspend fun createInstallmentGroup(transactions: List<com.feniqo.mobile.domain.model.Transaction>) = RepositoryResult.Success(EntityId("g-1"))
            override suspend fun update(transaction: com.feniqo.mobile.domain.model.Transaction) = RepositoryResult.Success(Unit)
            override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
            override suspend fun softDeleteInstallments(ids: Set<EntityId>) = RepositoryResult.Success(Unit)
        }
        val dummySyncRepo = object : com.feniqo.mobile.domain.repository.SyncRepository {
            override fun observeOverview() = MutableStateFlow(SyncOverview(SyncPhase.IDLE, 0, 0, 0, null, null))
            override fun observeConflicts() = MutableStateFlow(emptyList<com.feniqo.mobile.domain.repository.SyncConflict>())
            override suspend fun requestSync() = RepositoryResult.Success(Unit)
            override suspend fun retryFailedOperations() = RepositoryResult.Success(Unit)
            override suspend fun resolveConflict(entityId: EntityId, resolution: com.feniqo.mobile.domain.repository.ConflictResolution) = RepositoryResult.Success(Unit)
        }

        return SettingsViewModel(
            authRepository = authRepo,
            userSettingsRepository = dummySettingsRepo,
            backupExporter = dummyBackupExporter,
            backupImporter = dummyBackupImporter,
            observeTransactions = ObserveTransactionsUseCase(dummyTransactionRepo),
            csvExporter = TransactionCsvExporter(),
            observeSyncOverviewUseCase = ObserveSyncOverviewUseCase(dummySyncRepo),
        )
    }

    private class EmailTestFakeAuthRepository(
        initialSession: AuthSession? = null,
    ) : AuthRepository {
        val sessionFlow = MutableStateFlow(initialSession)
        val profileFlow = MutableStateFlow<UserProfile?>(
            UserProfile(
                id = EntityId("user-1"),
                email = "test@example.com",
                fullName = "Test User",
                currency = Currency.TRY,
                themePreference = ThemePreference.SYSTEM,
                language = AppLanguage.TR,
                activeWorkspaceId = null,
                createdAt = Instant.fromEpochMilliseconds(0),
            )
        )

        var refreshSessionCallCount = 0
        var lastResentEmail: String? = null

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = profileFlow
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun signOut() = RepositoryResult.Success(Unit)
        override suspend fun updateFullName(fullName: String?) = RepositoryResult.Success(Unit)
        override suspend fun changePassword(currentPassword: String, newPassword: String) = RepositoryResult.Success(Unit)

        override suspend fun refreshSession(): RepositoryResult<Unit> {
            refreshSessionCallCount++
            return RepositoryResult.Success(Unit)
        }

        override suspend fun resendEmailConfirmation(email: String): RepositoryResult<Unit> {
            lastResentEmail = email
            return RepositoryResult.Success(Unit)
        }
    }
}
