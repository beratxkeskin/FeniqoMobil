package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.SetSubscriptionLifecycleCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    fun observeSubscriptions(): Flow<List<Subscription>>

    fun observeSubscription(id: EntityId): Flow<Subscription?>

    fun observePriceHistories(subscriptionId: EntityId? = null): Flow<List<SubscriptionPriceHistory>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun observePayments(subscriptionId: EntityId? = null): Flow<List<SubscriptionPayment>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun create(command: CreateSubscriptionCommand): RepositoryResult<EntityId>

    suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit>

    suspend fun setActive(command: SetSubscriptionActiveCommand): RepositoryResult<Unit>

    suspend fun setLifecycle(command: SetSubscriptionLifecycleCommand): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult>

    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>
}


