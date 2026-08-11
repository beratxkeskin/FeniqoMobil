package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

data class AddCategoryCommand(
    val id: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val type: TransactionType,
    val color: CategoryColor,
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
        val normalizedName = command.name.trim()
        if (normalizedName.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("category_name_required"))
        }

        return categoryRepository.create(
            Category(
                id = command.id,
                ownerId = session.userId,
                workspaceId = command.workspaceId,
                name = normalizedName,
                type = command.type,
                color = command.color,
                icon = command.icon,
                isDefault = false,
                createdAt = createdAt,
            ),
        )
    }
}
