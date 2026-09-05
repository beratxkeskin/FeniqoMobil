package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import kotlinx.coroutines.flow.Flow


class ObserveSubscriptionsUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(): Flow<List<Subscription>> = repository.observeSubscriptions()
}

class ObserveSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    operator fun invoke(id: EntityId): Flow<Subscription?> = repository.observeSubscription(id)
}

class CreateSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(command: CreateSubscriptionCommand) = repository.create(command)
}

class UpdateSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(command: UpdateSubscriptionCommand) = repository.update(command)
}

class SetSubscriptionActiveUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(command: SetSubscriptionActiveCommand) = repository.setActive(command)
}

class AdvanceSubscriptionRenewalUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> = repository.advanceRenewal(id)
}


class DeleteSubscriptionUseCase(
    private val repository: SubscriptionRepository,
) {
    suspend operator fun invoke(id: EntityId) = repository.softDelete(id)
}

