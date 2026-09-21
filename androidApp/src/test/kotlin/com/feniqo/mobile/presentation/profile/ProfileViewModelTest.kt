package com.feniqo.mobile.presentation.profile

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun profile_summary_uses_room_backed_domain_profile() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        repository.profile.value = profile(fullName = "  Berat Keskin  ", email = "berat@feniqo.com")

        assertEquals("Berat Keskin", viewModel.uiState.value.displayName)
        assertEquals("berat@feniqo.com", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isProfileEmpty)
    }

    @Test
    fun profile_null_falls_back_to_session_email_and_stops_loading() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        repository.session.value = AuthSession(
            userId = EntityId("user-1"),
            email = "session-user@feniqo.com",
            expiresAt = Instant.fromEpochMilliseconds(1000),
        )
        repository.profile.value = null

        assertEquals("session-user@feniqo.com", viewModel.uiState.value.displayName)
        assertEquals("session-user@feniqo.com", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isProfileEmpty)
    }

    @Test
    fun profile_and_session_null_produces_empty_state_without_fake_name() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        repository.session.value = null
        repository.profile.value = null

        assertEquals("", viewModel.uiState.value.displayName)
        assertEquals("", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isProfileEmpty)
    }

    @Test
    fun language_and_currency_preferences_produce_dynamic_label() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        userSettingsRepository.settings.value = UserSettings(
            language = AppLanguage.EN,
            currency = Currency.USD,
        )

        assertEquals("English · USD", viewModel.uiState.value.languageRegionLabel)
        assertEquals("USD", viewModel.uiState.value.currencyCode)

        userSettingsRepository.settings.value = UserSettings(
            language = AppLanguage.TR,
            currency = Currency.TRY,
        )

        assertEquals("Türkçe · TRY", viewModel.uiState.value.languageRegionLabel)
        assertEquals("TRY", viewModel.uiState.value.currencyCode)
    }

    @Test
    fun sign_out_delegates_to_existing_auth_repository_flow() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )

        viewModel.signOut()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun profile_flow_error_then_retry_triggers_successful_reload() = runTest {
        val repository = FakeAuthRepository()
        val userSettingsRepository = FakeUserSettingsRepository()
        val viewModel = ProfileViewModel(
            authRepository = repository,
            userSettingsRepository = userSettingsRepository,
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        // 1. Akış fırlatılan bir hatayla karşılaşıyor
        repository.profileFlowOverride = kotlinx.coroutines.flow.flow {
            throw IllegalStateException("Veritabanı okuma hatası")
        }
        viewModel.retryProfile()
        testScheduler.advanceUntilIdle()

        assertEquals("Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.", viewModel.uiState.value.profileErrorMessage)
        assertTrue(viewModel.uiState.value.isProfileEmpty)
        assertFalse(viewModel.uiState.value.isLoading)

        // 2. Depo düzeliyor ve geçerli profil akışı sağlıyor
        repository.profileFlowOverride = null
        repository.profile.value = profile(fullName = "Deniz Yılmaz", email = "deniz@feniqo.com")

        // 3. Kullanıcı "Tekrar Dene" tetikliyor
        viewModel.retryProfile()
        testScheduler.advanceUntilIdle()

        // 4. Akış yeniden başlatılarak veriler başarıyla yükleniyor
        assertEquals(null, viewModel.uiState.value.profileErrorMessage)
        assertEquals("Deniz Yılmaz", viewModel.uiState.value.displayName)
        assertEquals("deniz@feniqo.com", viewModel.uiState.value.email)
        assertFalse(viewModel.uiState.value.isProfileEmpty)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun profile(fullName: String?, email: String) = UserProfile(
        id = EntityId("user-1"), email = email, fullName = fullName, currency = Currency.TRY,
        themePreference = ThemePreference.SYSTEM, language = AppLanguage.TR, activeWorkspaceId = null,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private class FakeAuthRepository : AuthRepository {
        val profile = MutableStateFlow<UserProfile?>(null)
        val session = MutableStateFlow<AuthSession?>(null)
        var profileFlowOverride: Flow<UserProfile?>? = null
        var signOutCalls = 0
        override fun observeSession(): Flow<AuthSession?> = session
        override fun observeCurrentProfile(): Flow<UserProfile?> = profileFlowOverride ?: profile
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> { signOutCalls++; return RepositoryResult.Success(Unit) }
    }

    private class FakeUserSettingsRepository : UserSettingsRepository {
        val settings = MutableStateFlow(UserSettings())
        override fun observeSettings(): Flow<UserSettings> = settings
        override suspend fun updateTheme(theme: ThemePreference) = RepositoryResult.Success(Unit)
        override suspend fun updateMaskAmounts(mask: Boolean) = RepositoryResult.Success(Unit)
        override suspend fun updateCurrency(currency: Currency) = RepositoryResult.Success(Unit)
        override suspend fun updateLanguage(language: AppLanguage) = RepositoryResult.Success(Unit)
        override suspend fun updateDateFormat(format: com.feniqo.mobile.domain.model.DateFormatPreference) = RepositoryResult.Success(Unit)
        override suspend fun updateNumberFormat(format: com.feniqo.mobile.domain.model.NumberFormatPreference) = RepositoryResult.Success(Unit)
        override suspend fun updateFirstDayOfWeek(firstDay: com.feniqo.mobile.domain.model.FirstDayOfWeekPreference) = RepositoryResult.Success(Unit)
        override suspend fun updateRegion(region: String) = RepositoryResult.Success(Unit)
        override suspend fun updateNotificationPreferences(preferences: com.feniqo.mobile.domain.model.NotificationPreferences) = RepositoryResult.Success(Unit)
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        override fun observeWorkspaces(): Flow<List<Workspace>> = MutableStateFlow(emptyList())
        override fun observeActiveWorkspace(): Flow<Workspace?> = MutableStateFlow(null)
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = MutableStateFlow(emptyList())
        override suspend fun create(name: String) = RepositoryResult.Success(EntityId("workspace"))
        override suspend fun createWorkspace(command: com.feniqo.mobile.domain.model.CreateWorkspaceCommand) = RepositoryResult.Success(EntityId("workspace"))
        override suspend fun updateWorkspace(command: com.feniqo.mobile.domain.model.UpdateWorkspaceCommand) = RepositoryResult.Success(Unit)
        override suspend fun deleteWorkspace(id: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun setActive(workspaceId: EntityId?) = RepositoryResult.Success(Unit)
        override suspend fun createInvite(workspaceId: EntityId) = RepositoryResult.Success(WorkspaceInviteCode("code"))
        override suspend fun join(inviteCode: WorkspaceInviteCode) = RepositoryResult.Success(EntityId("workspace"))
        override suspend fun changeMemberRole(workspaceId: EntityId, userId: EntityId, role: WorkspaceRole) = RepositoryResult.Success(Unit)
        override suspend fun leave(workspaceId: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId) = RepositoryResult.Success(Unit)
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId) = RepositoryResult.Success(Unit)
    }
}
