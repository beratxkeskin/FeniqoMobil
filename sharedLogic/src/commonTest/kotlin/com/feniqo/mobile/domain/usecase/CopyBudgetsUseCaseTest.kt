package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CopyBudgetsUseCaseTest {

    @Test
    fun invoke_forwards_command_and_returns_repository_success_result_unmodified() = runTest {
        val command = CopyBudgetsCommand(
            sourceMonth = YearMonth("2026-07"),
            targetMonth = YearMonth("2026-08"),
        )
        val expectedResult = CopyBudgetsResult(
            copiedCount = 3,
            skippedCategoryIds = listOf(EntityId("c-skip-1")),
        )
        val repository = object : FakeBaseBudgetRepository() {
            override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> {
                assertEquals(YearMonth("2026-07"), command.sourceMonth)
                assertEquals(YearMonth("2026-08"), command.targetMonth)
                return RepositoryResult.Success(expectedResult)
            }
        }

        val useCase = CopyBudgetsUseCase(repository)
        val result = useCase(command)

        assertIs<RepositoryResult.Success<CopyBudgetsResult>>(result)
        assertEquals(expectedResult, result.value)
    }

    @Test
    fun invoke_forwards_repository_failure_result_unmodified() = runTest {
        val command = CopyBudgetsCommand(
            sourceMonth = YearMonth("2026-07"),
            targetMonth = YearMonth("2026-08"),
        )
        val repository = object : FakeBaseBudgetRepository() {
            override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> {
                return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            }
        }

        val useCase = CopyBudgetsUseCase(repository)
        val result = useCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertIs<AppError.Authentication>(result.error)
        assertEquals("auth_session_required", result.error.code)
    }

    private open class FakeBaseBudgetRepository : BudgetRepository {
        override fun observeBudgets(month: YearMonth, workspaceId: EntityId?): Flow<List<Budget>> = emptyFlow()
        override fun observeBudget(id: EntityId): Flow<Budget?> = emptyFlow()
        override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> = RepositoryResult.Failure(AppError.Unknown("not_implemented"))
        override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> = RepositoryResult.Failure(AppError.Unknown("not_implemented"))
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Failure(AppError.Unknown("not_implemented"))
        override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> = RepositoryResult.Failure(AppError.Unknown("not_implemented"))
    }
}
