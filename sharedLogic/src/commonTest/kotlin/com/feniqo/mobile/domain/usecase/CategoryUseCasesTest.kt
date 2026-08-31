package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class CategoryUseCasesTest {

    private val userSession = AuthSession(USER_ID, "user@feniqo.com", NOW)

    @Test
    fun addCategory_requiresActiveSession() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(null)
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("auth_session_required", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_withBlankName_returnsNameRequiredError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "   "), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_name_required", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_withInvalidColor_returnsColorInvalidError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(colorHex = "#12ZZ99"), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_color_invalid", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_withValidData_createsCategoryWithUserOwnership() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "  Market  ", colorHex = "#4CAF50"), NOW)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        val saved = categoryRepository.observeCategory(CATEGORY_ID).first()
        assertEquals(USER_ID, saved?.ownerId)
        assertEquals("Market", saved?.name)
        assertEquals(CategoryColor("#4CAF50"), saved?.color)
        assertEquals(false, saved?.isDefault)
        assertEquals(NOW, saved?.createdAt)
    }

    @Test
    fun addCategory_whenNameTooLong_returnsNameTooLongError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "A".repeat(51)), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_name_too_long", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_whenNameAlreadyExistsForSameUserWorkspaceAndType_returnsDuplicateError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val existing = userCategory(name = "Market", type = TransactionType.EXPENSE)
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(existing))
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "  market  ", type = TransactionType.EXPENSE), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_duplicate_name", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_whenNameExistsInSoftDeletedCategory_returnsDuplicateError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val softDeleted = userCategory(name = "Market", type = TransactionType.EXPENSE)
        // active list is empty, but history contains the soft-deleted category
        val categoryRepository = CategoryTestFakeCategoryRepository(
            initial = emptyList(),
            initialHistory = listOf(softDeleted),
        )
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "Market", type = TransactionType.EXPENSE), NOW)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_duplicate_name", failure.error.code)
        assertNull(categoryRepository.lastCreatedCategory)
    }

    @Test
    fun addCategory_whenNameExistsInSystemCategory_succeeds() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val systemCategory = Category(
            id = EntityId("system-market"),
            ownerId = null,
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#EF4444"),
            icon = null,
            isDefault = true,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(systemCategory))
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "Market", type = TransactionType.EXPENSE), NOW)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("Market", categoryRepository.lastCreatedCategory?.name)
    }

    @Test
    fun addCategory_whenNameExistsForDifferentType_succeeds() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val existingExpense = userCategory(name = "Danışmanlık", type = TransactionType.EXPENSE)
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(existingExpense))
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "Danışmanlık", type = TransactionType.INCOME), NOW)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(TransactionType.INCOME, categoryRepository.lastCreatedCategory?.type)
    }

    @Test
    fun addCategory_whenNameExistsForDifferentWorkspace_succeeds() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val existingPersonal = userCategory(name = "Özel", workspaceId = null)
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(existingPersonal))
        val useCase = AddCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(addCommand(name = "Özel", workspaceId = EntityId("ws-2")), NOW)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(EntityId("ws-2"), categoryRepository.lastCreatedCategory?.workspaceId)
    }

    @Test
    fun updateCategory_requiresActiveSession() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(null)
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(userCategory()))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand())

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("auth_session_required", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_whenCategoryNotFound_returnsNotFoundError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository(emptyList())
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = EntityId("non-existent")))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_not_found", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_whenDefaultCategory_returnsCannotBeModifiedError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val defaultCat = Category(
            id = EntityId("default-cat"),
            ownerId = null,
            workspaceId = null,
            name = "Gıda",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#FF0000"),
            icon = null,
            isDefault = true,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(defaultCat))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = defaultCat.id))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_default_cannot_be_modified", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_whenNullOwnerIdAndNotDefault_returnsCannotBeModifiedError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val corruptedCat = Category(
            id = EntityId("corrupted-cat"),
            ownerId = null,
            workspaceId = null,
            name = "Bozuk Kategori",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#FF0000"),
            icon = null,
            isDefault = false,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(corruptedCat))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = corruptedCat.id))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_default_cannot_be_modified", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_whenAnotherUserCategory_returnsOwnerMismatchError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val otherUserCat = Category(
            id = EntityId("other-cat"),
            ownerId = EntityId("other-user"),
            workspaceId = null,
            name = "Özel",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#00FF00"),
            icon = null,
            isDefault = false,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(otherUserCat))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = otherUserCat.id))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_owner_mismatch", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_withBlankName_returnsNameRequiredError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory()
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = category.id, name = "   "))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_name_required", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_withInvalidColor_returnsColorInvalidError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory()
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = category.id, colorHex = "123456"))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_color_invalid", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_withValidData_updatesAndPreservesInvariants() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory(name = "Eski Ad")
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(
            updateCommand(
                id = category.id,
                name = "  Yeni Ad  ",
                colorHex = "#0000FF",
                icon = CategoryIcon("new_icon"),
            ),
        )

        assertIs<RepositoryResult.Success<Unit>>(result)
        val updated = categoryRepository.observeCategory(category.id).first()
        assertEquals(category.id, updated?.id)
        assertEquals(USER_ID, updated?.ownerId)
        assertEquals(category.workspaceId, updated?.workspaceId)
        assertEquals("Yeni Ad", updated?.name)
        assertEquals(category.type, updated?.type) // Type değişmez
        assertEquals(CategoryColor("#0000FF"), updated?.color)
        assertEquals(CategoryIcon("new_icon"), updated?.icon)
        assertEquals(false, updated?.isDefault)
        assertEquals(NOW, updated?.createdAt)
    }

    @Test
    fun updateCategory_whenNameTooLong_returnsNameTooLongError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory()
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = category.id, name = "A".repeat(51)))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_name_too_long", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun updateCategory_whenKeepingSameName_succeeds() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory(name = "Market")
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = category.id, name = "Market", colorHex = "#00FF00"))

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals("Market", categoryRepository.lastUpdatedCategory?.name)
        assertEquals(CategoryColor("#00FF00"), categoryRepository.lastUpdatedCategory?.color)
    }

    @Test
    fun updateCategory_whenRenamingToAnotherExistingCategory_returnsDuplicateError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val cat1 = userCategory(id = EntityId("cat-1"), name = "Market")
        val cat2 = userCategory(id = EntityId("cat-2"), name = "Fatura")
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(cat1, cat2))
        val useCase = UpdateCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(updateCommand(id = cat2.id, name = "  market  "))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_duplicate_name", failure.error.code)
        assertNull(categoryRepository.lastUpdatedCategory)
    }

    @Test
    fun deleteCategory_requiresActiveSession() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(null)
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(userCategory()))
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(CATEGORY_ID)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("auth_session_required", failure.error.code)
        assertNull(categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun deleteCategory_whenCategoryNotFound_returnsNotFoundError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val categoryRepository = CategoryTestFakeCategoryRepository(emptyList())
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(EntityId("non-existent"))

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_not_found", failure.error.code)
        assertNull(categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun deleteCategory_whenDefaultCategory_returnsCannotBeDeletedError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val defaultCat = Category(
            id = EntityId("default-cat"),
            ownerId = null,
            workspaceId = null,
            name = "Maaş",
            type = TransactionType.INCOME,
            color = CategoryColor("#00FF00"),
            icon = null,
            isDefault = true,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(defaultCat))
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(defaultCat.id)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_default_cannot_be_deleted", failure.error.code)
        assertNull(categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun deleteCategory_whenNullOwnerIdAndNotDefault_returnsCannotBeDeletedError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val corruptedCat = Category(
            id = EntityId("corrupted-cat"),
            ownerId = null,
            workspaceId = null,
            name = "Bozuk Kategori",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#00FF00"),
            icon = null,
            isDefault = false,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(corruptedCat))
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(corruptedCat.id)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_default_cannot_be_deleted", failure.error.code)
        assertNull(categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun deleteCategory_whenAnotherUserCategory_returnsOwnerMismatchError() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val otherUserCat = Category(
            id = EntityId("other-cat"),
            ownerId = EntityId("other-user"),
            workspaceId = null,
            name = "Gizli",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#333333"),
            icon = null,
            isDefault = false,
            createdAt = NOW,
        )
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(otherUserCat))
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(otherUserCat.id)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_owner_mismatch", failure.error.code)
        assertNull(categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun deleteCategory_withValidOwnership_callsSoftDeleteSuccessfully() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val category = userCategory()
        val categoryRepository = CategoryTestFakeCategoryRepository(listOf(category))
        val useCase = DeleteCategoryUseCase(authRepository, categoryRepository)

        val result = useCase(category.id)

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals(category.id, categoryRepository.lastSoftDeletedId)
    }

    @Test
    fun categoryUseCases_propagateRepositoryFailureDirectly() = runTest {
        val authRepository = CategoryTestFakeAuthRepository(userSession)
        val failingRepo = CategoryTestFailingCategoryRepository(AppError.Storage("disk_full"))

        val addUseCase = AddCategoryUseCase(authRepository, failingRepo)
        val addResult = addUseCase(addCommand(), NOW)
        val addFailure = assertIs<RepositoryResult.Failure>(addResult)
        assertEquals("disk_full", addFailure.error.code)

        val updateUseCase = UpdateCategoryUseCase(authRepository, failingRepo)
        val updateResult = updateUseCase(updateCommand())
        val updateFailure = assertIs<RepositoryResult.Failure>(updateResult)
        assertEquals("disk_full", updateFailure.error.code)

        val deleteUseCase = DeleteCategoryUseCase(authRepository, failingRepo)
        val deleteResult = deleteUseCase(CATEGORY_ID)
        val deleteFailure = assertIs<RepositoryResult.Failure>(deleteResult)
        assertEquals("disk_full", deleteFailure.error.code)
    }

    @Test
    fun categoryUseCases_rethrowCancellationException() = runTest {
        val authRepository = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(USER_ID)
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val categoryRepository = CategoryTestFakeCategoryRepository()
        val addUseCase = AddCategoryUseCase(authRepository, categoryRepository)

        assertFailsWith<CancellationException> {
            addUseCase(addCommand(), NOW)
        }
    }

    @Test
    fun observeCategories_andObserveCategory_delegateFiltersCorrectly() = runTest {
        val cat1 = userCategory(id = EntityId("c1"), type = TransactionType.EXPENSE)
        val cat2 = userCategory(id = EntityId("c2"), type = TransactionType.INCOME)
        val repository = CategoryTestFakeCategoryRepository(listOf(cat1, cat2))

        val observeAllUseCase = ObserveCategoriesUseCase(repository)
        val all = observeAllUseCase(type = TransactionType.EXPENSE, workspaceId = null).first()
        assertEquals(TransactionType.EXPENSE, repository.lastObservedType)
        assertNull(repository.lastObservedWorkspaceId)

        val observeOneUseCase = ObserveCategoryUseCase(repository)
        val one = observeOneUseCase(cat1.id).first()
        assertEquals(cat1, one)

        val historyLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(repository)
        val historyList = historyLookupUseCase(workspaceId = null).first()
        assertEquals(2, historyList.size)
    }

    private fun addCommand(
        id: EntityId = CATEGORY_ID,
        workspaceId: EntityId? = null,
        name: String = "Market",
        type: TransactionType = TransactionType.EXPENSE,
        colorHex: String = "#4CAF50",
        icon: CategoryIcon? = CategoryIcon("shopping"),
    ) = AddCategoryCommand(id, workspaceId, name, type, colorHex, icon)

    private fun updateCommand(
        id: EntityId = CATEGORY_ID,
        name: String = "Süpermarket",
        colorHex: String = "#2196F3",
        icon: CategoryIcon? = CategoryIcon("store"),
    ) = UpdateCategoryCommand(id, name, colorHex, icon)

    private fun userCategory(
        id: EntityId = CATEGORY_ID,
        workspaceId: EntityId? = null,
        name: String = "Market",
        type: TransactionType = TransactionType.EXPENSE,
    ) = Category(
        id = id,
        ownerId = USER_ID,
        workspaceId = workspaceId,
        name = name,
        type = type,
        color = CategoryColor("#4CAF50"),
        icon = CategoryIcon("shopping"),
        isDefault = false,
        createdAt = NOW,
    )

    private companion object {
        val USER_ID = EntityId("user-100")
        val CATEGORY_ID = EntityId("category-100")
        val NOW = Instant.parse("2026-08-21T00:00:00Z")
    }

    private class CategoryTestFakeAuthRepository(session: AuthSession?) : AuthRepository {
        private val sessionFlow = MutableStateFlow(session)
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class CategoryTestFakeCategoryRepository(
        initial: List<Category> = emptyList(),
        initialHistory: List<Category>? = null,
    ) : CategoryRepository {
        private val categories = MutableStateFlow(initial)
        private val historyCategories = MutableStateFlow(initialHistory ?: initial)
        var lastCreatedCategory: Category? = null
            private set
        var lastUpdatedCategory: Category? = null
            private set
        var lastSoftDeletedId: EntityId? = null
            private set
        var lastObservedType: TransactionType? = null
            private set
        var lastObservedWorkspaceId: EntityId? = null
            private set

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> {
            lastObservedType = type
            lastObservedWorkspaceId = workspaceId
            return categories.map { list ->
                list.filter { cat ->
                    (type == null || cat.type == type) &&
                    (workspaceId == null && cat.workspaceId == null || cat.workspaceId == workspaceId)
                }
            }
        }

        override fun observeCategory(id: EntityId): Flow<Category?> =
            categories.map { list -> list.firstOrNull { it.id == id } }

        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            historyCategories.map { list ->
                list.filter { cat ->
                    workspaceId == null && cat.workspaceId == null || cat.workspaceId == workspaceId
                }
            }

        override suspend fun create(category: Category): RepositoryResult<EntityId> {
            lastCreatedCategory = category
            categories.value = categories.value + category
            historyCategories.value = historyCategories.value + category
            return RepositoryResult.Success(category.id)
        }

        override suspend fun update(category: Category): RepositoryResult<Unit> {
            lastUpdatedCategory = category
            categories.value = categories.value.map { if (it.id == category.id) category else it }
            historyCategories.value = historyCategories.value.map { if (it.id == category.id) category else it }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastSoftDeletedId = id
            categories.value = categories.value.filterNot { it.id == id }
            return RepositoryResult.Success(Unit)
        }
    }

    private class CategoryTestFailingCategoryRepository(private val error: AppError) : CategoryRepository {
        private val category = Category(
            id = EntityId("category-100"),
            ownerId = EntityId("user-100"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#4CAF50"),
            icon = null,
            isDefault = false,
            createdAt = Instant.parse("2026-08-21T00:00:00Z"),
        )
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = flowOf(listOf(category))
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(category)
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Failure(error)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Failure(error)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Failure(error)
    }
}
