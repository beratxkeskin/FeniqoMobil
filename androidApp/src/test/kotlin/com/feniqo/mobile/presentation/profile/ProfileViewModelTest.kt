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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun profile_summary_uses_room_backed_domain_profile() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = ProfileViewModel(repository, ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        repository.profile.value = profile(fullName = "  Berat Keskin  ", email = "berat@feniqo.com")

        assertEquals("Berat Keskin", viewModel.uiState.value.displayName)
        assertEquals("berat@feniqo.com", viewModel.uiState.value.email)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun sign_out_delegates_to_existing_auth_repository_flow() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = ProfileViewModel(repository, ObserveActiveWorkspaceUseCase(FakeWorkspaceRepository()))

        viewModel.signOut()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.signOutCalls)
    }

    private fun profile(fullName: String?, email: String) = UserProfile(
        id = EntityId("user-1"), email = email, fullName = fullName, currency = Currency.TRY,
        themePreference = ThemePreference.SYSTEM, language = AppLanguage.TR, activeWorkspaceId = null,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private class FakeAuthRepository : AuthRepository {
        val profile = MutableStateFlow<UserProfile?>(null)
        var signOutCalls = 0
        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = profile
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> { signOutCalls++; return RepositoryResult.Success(Unit) }
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
