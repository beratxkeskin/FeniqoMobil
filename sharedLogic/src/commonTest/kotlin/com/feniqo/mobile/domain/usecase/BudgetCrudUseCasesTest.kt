package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DeleteBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BudgetCrudUseCasesTest {

    private class FakeCategoryRepository : CategoryRepository {
        var categoryToReturn: Category? = null
        var throwCancellation: Boolean = false

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(emptyList())

        override fun observeCategory(id: EntityId): Flow<Category?> = flow {
            if (throwCancellation) {
                throw CancellationException("Category observation cancelled")
            }
            emit(categoryToReturn)
        }

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(emptyList())

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("cat_created"))

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeBudgetRepository : BudgetRepository {
        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("budget_123"))
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var softDeleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastCreateCommand: CreateBudgetCommand? = null
        var lastUpdateCommand: UpdateBudgetCommand? = null
        var lastDeletedId: EntityId? = null

        override fun observeBudgets(month: YearMonth, workspaceId: EntityId?): Flow<List<Budget>> =
            flowOf(emptyList())

        override fun observeBudget(id: EntityId): Flow<Budget?> =
            flowOf(null)

        override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> {
            lastCreateCommand = command
            return createResult
        }

        override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return softDeleteResult
        }

        override suspend fun copyBudgets(command: com.feniqo.mobile.domain.model.CopyBudgetsCommand): RepositoryResult<com.feniqo.mobile.domain.model.CopyBudgetsResult> =
            RepositoryResult.Success(com.feniqo.mobile.domain.model.CopyBudgetsResult(0))
    }

    private val categoryRepo = FakeCategoryRepository()
    private val budgetRepo = FakeBudgetRepository()

    private val createUseCase = CreateBudgetUseCase(categoryRepo, budgetRepo)
    private val updateUseCase = UpdateBudgetUseCase(budgetRepo)
    private val deleteUseCase = DeleteBudgetUseCase(budgetRepo)

    private fun sampleCategory(type: TransactionType) = Category(
        id = EntityId("cat_market"),
        ownerId = EntityId("user_1"),
        workspaceId = null,
        name = "Market",
        type = type,
        color = CategoryColor("#FF0000"),
        icon = null,
        isDefault = false,
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(1700000000000L),
    )

    @Test
    fun create_with_expense_category_passes_exact_command_to_repository() = runTest {
        categoryRepo.categoryToReturn = sampleCategory(TransactionType.EXPENSE)
        val command = CreateBudgetCommand(
            categoryId = EntityId("cat_market"),
            month = YearMonth("2026-08"),
            limit = Money(250000L, Currency.TRY),
        )

        val result = createUseCase(command)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(EntityId("budget_123"), result.value)
        assertEquals(command, budgetRepo.lastCreateCommand)
    }

    @Test
    fun create_with_missing_category_returns_validation_failure_and_does_not_call_repository() = runTest {
        categoryRepo.categoryToReturn = null
        val command = CreateBudgetCommand(
            categoryId = EntityId("cat_unknown"),
            month = YearMonth("2026-08"),
            limit = Money(250000L, Currency.TRY),
        )

        val result = createUseCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Validation("budget_category_not_found"), result.error)
        assertNull(budgetRepo.lastCreateCommand)
    }

    @Test
    fun create_with_income_category_returns_validation_failure_and_does_not_call_repository() = runTest {
        categoryRepo.categoryToReturn = sampleCategory(TransactionType.INCOME)
        val command = CreateBudgetCommand(
            categoryId = EntityId("cat_market"),
            month = YearMonth("2026-08"),
            limit = Money(250000L, Currency.TRY),
        )

        val result = createUseCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Validation("budget_category_must_be_expense"), result.error)
        assertNull(budgetRepo.lastCreateCommand)
    }

    @Test
    fun create_repository_failure_is_propagated_unchanged() = runTest {
        categoryRepo.categoryToReturn = sampleCategory(TransactionType.EXPENSE)
        val failureError = AppError.Validation("budget_already_exists")
        budgetRepo.createResult = RepositoryResult.Failure(failureError)

        val command = CreateBudgetCommand(
            categoryId = EntityId("cat_market"),
            month = YearMonth("2026-08"),
            limit = Money(250000L, Currency.TRY),
        )

        val result = createUseCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(failureError, result.error)
        assertEquals(command, budgetRepo.lastCreateCommand)
    }

    @Test
    fun update_passes_exact_command_to_repository() = runTest {
        val command = UpdateBudgetCommand(
            id = EntityId("budget_99"),
            limit = Money(300000L, Currency.TRY),
        )

        val result = updateUseCase(command)

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals(command, budgetRepo.lastUpdateCommand)
    }

    @Test
    fun update_repository_failure_is_propagated_unchanged() = runTest {
        val failureError = AppError.Validation("budget_not_found")
        budgetRepo.updateResult = RepositoryResult.Failure(failureError)

        val command = UpdateBudgetCommand(
            id = EntityId("budget_99"),
            limit = Money(300000L, Currency.TRY),
        )

        val result = updateUseCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(failureError, result.error)
        assertEquals(command, budgetRepo.lastUpdateCommand)
    }

    @Test
    fun delete_passes_correct_id_to_repository_softDelete() = runTest {
        val command = DeleteBudgetCommand(id = EntityId("budget_delete_1"))

        val result = deleteUseCase(command)

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals(EntityId("budget_delete_1"), budgetRepo.lastDeletedId)
    }

    @Test
    fun delete_repository_failure_is_propagated_unchanged() = runTest {
        val failureError = AppError.Validation("budget_not_found")
        budgetRepo.softDeleteResult = RepositoryResult.Failure(failureError)

        val command = DeleteBudgetCommand(id = EntityId("budget_delete_1"))

        val result = deleteUseCase(command)

        assertIs<RepositoryResult.Failure>(result)
        assertEquals(failureError, result.error)
        assertEquals(EntityId("budget_delete_1"), budgetRepo.lastDeletedId)
    }

    @Test
    fun category_observation_cancellation_exception_is_not_swallowed() = runTest {
        categoryRepo.throwCancellation = true
        val command = CreateBudgetCommand(
            categoryId = EntityId("cat_market"),
            month = YearMonth("2026-08"),
            limit = Money(250000L, Currency.TRY),
        )

        assertFailsWith<CancellationException> {
            createUseCase(command)
        }
        assertNull(budgetRepo.lastCreateCommand)
    }
}
