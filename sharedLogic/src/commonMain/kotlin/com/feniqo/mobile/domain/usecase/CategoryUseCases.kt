package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.CategoryValidationResult
import com.feniqo.mobile.domain.validation.CategoryValidationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

data class AddCategoryCommand(
    val id: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val type: TransactionType,
    val colorHex: String,
    val icon: CategoryIcon?,
)

data class UpdateCategoryCommand(
    val id: EntityId,
    val name: String,
    val colorHex: String,
    val icon: CategoryIcon?,
)

class AddCategoryUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(
        command: AddCategoryCommand,
        createdAt: Instant,
    ): RepositoryResult<EntityId> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        val nameResult = CategoryValidationRules.validateName(command.name)
        val validName = when (nameResult) {
            is CategoryValidationResult.Valid -> nameResult.value
            is CategoryValidationResult.Invalid -> return when (nameResult.error) {
                com.feniqo.mobile.domain.validation.CategoryValidationError.NAME_EMPTY ->
                    RepositoryResult.Failure(AppError.Validation("category_name_required"))
                com.feniqo.mobile.domain.validation.CategoryValidationError.NAME_TOO_LONG ->
                    RepositoryResult.Failure(AppError.Validation("category_name_too_long"))
                com.feniqo.mobile.domain.validation.CategoryValidationError.COLOR_INVALID_FORMAT ->
                    RepositoryResult.Failure(AppError.Validation("category_color_invalid"))
            }
        }

        val colorResult = CategoryValidationRules.validateColor(command.colorHex)
        val validColor = when (colorResult) {
            is CategoryValidationResult.Valid -> colorResult.value
            is CategoryValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation("category_color_invalid"))
        }

        val historyCategories = categoryRepository.observeCategoriesForHistoryLookup(
            workspaceId = command.workspaceId,
        ).first()

        val isDuplicate = historyCategories.any { existing ->
            existing.ownerId == session.userId &&
                existing.workspaceId == command.workspaceId &&
                existing.type == command.type &&
                CategoryValidationRules.normalizeName(existing.name) == CategoryValidationRules.normalizeName(validName)
        }
        if (isDuplicate) {
            return RepositoryResult.Failure(AppError.Validation("category_duplicate_name"))
        }

        return categoryRepository.create(
            Category(
                id = command.id,
                ownerId = session.userId,
                workspaceId = command.workspaceId,
                name = validName,
                type = command.type,
                color = validColor,
                icon = command.icon,
                isDefault = false,
                createdAt = createdAt,
            ),
        )
    }
}

class UpdateCategoryUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(command: UpdateCategoryCommand): RepositoryResult<Unit> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        val existing = categoryRepository.observeCategory(command.id).first()
            ?: return RepositoryResult.Failure(AppError.Validation("category_not_found"))

        if (existing.isDefault || existing.ownerId == null) {
            return RepositoryResult.Failure(AppError.Validation("category_default_cannot_be_modified"))
        }

        if (existing.ownerId != session.userId) {
            return RepositoryResult.Failure(AppError.Authentication("category_owner_mismatch"))
        }

        val nameResult = CategoryValidationRules.validateName(command.name)
        val validName = when (nameResult) {
            is CategoryValidationResult.Valid -> nameResult.value
            is CategoryValidationResult.Invalid -> return when (nameResult.error) {
                com.feniqo.mobile.domain.validation.CategoryValidationError.NAME_EMPTY ->
                    RepositoryResult.Failure(AppError.Validation("category_name_required"))
                com.feniqo.mobile.domain.validation.CategoryValidationError.NAME_TOO_LONG ->
                    RepositoryResult.Failure(AppError.Validation("category_name_too_long"))
                com.feniqo.mobile.domain.validation.CategoryValidationError.COLOR_INVALID_FORMAT ->
                    RepositoryResult.Failure(AppError.Validation("category_color_invalid"))
            }
        }

        val colorResult = CategoryValidationRules.validateColor(command.colorHex)
        val validColor = when (colorResult) {
            is CategoryValidationResult.Valid -> colorResult.value
            is CategoryValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation("category_color_invalid"))
        }

        val historyCategories = categoryRepository.observeCategoriesForHistoryLookup(
            workspaceId = existing.workspaceId,
        ).first()

        val isDuplicate = historyCategories.any { candidate ->
            candidate.id != existing.id &&
                candidate.ownerId == session.userId &&
                candidate.workspaceId == existing.workspaceId &&
                candidate.type == existing.type &&
                CategoryValidationRules.normalizeName(candidate.name) == CategoryValidationRules.normalizeName(validName)
        }
        if (isDuplicate) {
            return RepositoryResult.Failure(AppError.Validation("category_duplicate_name"))
        }

        return categoryRepository.update(
            existing.copy(
                name = validName,
                color = validColor,
                icon = command.icon,
            ),
        )
    }
}

class DeleteCategoryUseCase(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        val existing = categoryRepository.observeCategory(id).first()
            ?: return RepositoryResult.Failure(AppError.Validation("category_not_found"))

        if (existing.isDefault || existing.ownerId == null) {
            return RepositoryResult.Failure(AppError.Validation("category_default_cannot_be_deleted"))
        }

        if (existing.ownerId != session.userId) {
            return RepositoryResult.Failure(AppError.Authentication("category_owner_mismatch"))
        }

        return categoryRepository.softDelete(id)
    }
}

class ObserveCategoriesUseCase(
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(
        type: TransactionType? = null,
        workspaceId: EntityId? = null,
    ): Flow<List<Category>> = categoryRepository.observeCategories(type = type, workspaceId = workspaceId)
}

class ObserveCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(id: EntityId): Flow<Category?> = categoryRepository.observeCategory(id)
}

class ObserveCategoriesForHistoryLookupUseCase(
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(workspaceId: EntityId? = null): Flow<List<Category>> =
        categoryRepository.observeCategoriesForHistoryLookup(workspaceId = workspaceId)
}
