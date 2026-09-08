package com.feniqo.mobile.di

import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.usecase.ChangeWorkspaceMemberRoleUseCase
import com.feniqo.mobile.domain.usecase.CreateWorkspaceInviteUseCase
import com.feniqo.mobile.domain.usecase.CreateWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.DeleteWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.JoinWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.LeaveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspacesUseCase
import com.feniqo.mobile.domain.usecase.RemoveWorkspaceMemberUseCase
import com.feniqo.mobile.domain.usecase.SetActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.TransferWorkspaceOwnershipUseCase
import com.feniqo.mobile.domain.usecase.UpdateWorkspaceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkspaceUseCaseModule {

    @Provides
    @Singleton
    fun provideObserveWorkspacesUseCase(
        workspaceRepository: WorkspaceRepository,
    ): ObserveWorkspacesUseCase = ObserveWorkspacesUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideObserveActiveWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): ObserveActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideCreateWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): CreateWorkspaceUseCase = CreateWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideUpdateWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): UpdateWorkspaceUseCase = UpdateWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideDeleteWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): DeleteWorkspaceUseCase = DeleteWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideSetActiveWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): SetActiveWorkspaceUseCase = SetActiveWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideJoinWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): JoinWorkspaceUseCase = JoinWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideObserveWorkspaceMembersUseCase(
        workspaceRepository: WorkspaceRepository,
    ): ObserveWorkspaceMembersUseCase = ObserveWorkspaceMembersUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideLeaveWorkspaceUseCase(
        workspaceRepository: WorkspaceRepository,
    ): LeaveWorkspaceUseCase = LeaveWorkspaceUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideCreateWorkspaceInviteUseCase(
        workspaceRepository: WorkspaceRepository,
    ): CreateWorkspaceInviteUseCase = CreateWorkspaceInviteUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideChangeWorkspaceMemberRoleUseCase(
        workspaceRepository: WorkspaceRepository,
    ): ChangeWorkspaceMemberRoleUseCase = ChangeWorkspaceMemberRoleUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideTransferWorkspaceOwnershipUseCase(
        workspaceRepository: WorkspaceRepository,
    ): TransferWorkspaceOwnershipUseCase = TransferWorkspaceOwnershipUseCase(workspaceRepository)

    @Provides
    @Singleton
    fun provideRemoveWorkspaceMemberUseCase(
        workspaceRepository: WorkspaceRepository,
    ): RemoveWorkspaceMemberUseCase = RemoveWorkspaceMemberUseCase(workspaceRepository)
}
