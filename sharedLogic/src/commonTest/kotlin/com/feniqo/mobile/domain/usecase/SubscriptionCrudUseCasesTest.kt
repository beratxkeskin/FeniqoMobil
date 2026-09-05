package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SubscriptionCrudUseCasesTest {

    @Test
    fun crudUseCases_delegateOriginalInputsAndResults() = runTest {
        val repository = RecordingRepository()
        val create = CreateSubscriptionUseCase(repository)
        val update = UpdateSubscriptionUseCase(repository)
        val setActive = SetSubscriptionActiveUseCase(repository)
        val advance = AdvanceSubscriptionRenewalUseCase(repository)
        val delete = DeleteSubscriptionUseCase(repository)

        val createCmd = createCommand()
        val updateCmd = updateCommand()
        val activeCmd = SetSubscriptionActiveCommand(EntityId("sub-1"), false)
        val expectedProgression = SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 3, 1))
        repository.advanceResult = RepositoryResult.Success(expectedProgression)

        val createResult = create(createCmd)
        val updateResult = update(updateCmd)
        val setActiveResult = setActive(activeCmd)
        val advanceResult = advance(EntityId("sub-1"))
        val deleteResult = delete(EntityId("sub-1"))

        val createdId = assertIs<RepositoryResult.Success<EntityId>>(createResult)
        assertEquals(EntityId("sub-created-1"), createdId.value)
        assertIs<RepositoryResult.Success<Unit>>(updateResult)
        assertIs<RepositoryResult.Success<Unit>>(setActiveResult)
        val advancedSuccess = assertIs<RepositoryResult.Success<SubscriptionRenewalProgressionResult>>(advanceResult)
        assertEquals(expectedProgression, advancedSuccess.value)
        assertIs<RepositoryResult.Success<Unit>>(deleteResult)

        assertEquals(createCmd, repository.lastCreateCommand)
        assertEquals(updateCmd, repository.lastUpdateCommand)
        assertEquals(activeCmd, repository.lastSetActiveCommand)
        assertEquals(EntityId("sub-1"), repository.lastAdvancedId)
        assertEquals(EntityId("sub-1"), repository.lastDeletedId)
    }


    @Test
    fun crudUseCases_carryFailureDirectlyWithoutTransformation() = runTest {
        val repository = RecordingRepository()
        val failure = RepositoryResult.Failure(AppError.Storage("DB Error"))
        repository.createResult = failure
        repository.updateResult = failure
        repository.setActiveResult = failure
        repository.advanceResult = failure
        repository.deleteResult = failure

        val create = CreateSubscriptionUseCase(repository)
        val update = UpdateSubscriptionUseCase(repository)
        val setActive = SetSubscriptionActiveUseCase(repository)
        val advance = AdvanceSubscriptionRenewalUseCase(repository)
        val delete = DeleteSubscriptionUseCase(repository)

        assertEquals(failure, create(createCommand()))
        assertEquals(failure, update(updateCommand()))
        assertEquals(failure, setActive(SetSubscriptionActiveCommand(EntityId("sub-1"), true)))
        assertEquals(failure, advance(EntityId("sub-1")))
        assertEquals(failure, delete(EntityId("sub-1")))
    }

    @Test
    fun observeSubscriptions_emitsRepositoryListDirectly() = runTest {
        val repository = RecordingRepository()
        val observeList = ObserveSubscriptionsUseCase(repository)
        val expected = listOf(sampleSubscription("sub-1"), sampleSubscription("sub-2"))
        repository.listToEmit = expected

        val actual = observeList().first()
        assertEquals(expected, actual)
    }

    @Test
    fun observeSubscription_delegatesIdAndEmitsNullableResult() = runTest {
        val repository = RecordingRepository()
        val observeSingle = ObserveSubscriptionUseCase(repository)
        val expected = sampleSubscription("sub-1")
        repository.singleToEmit = expected

        val actual = observeSingle(EntityId("sub-1")).first()
        assertEquals(expected, actual)
        assertEquals(EntityId("sub-1"), repository.lastObservedId)

        repository.singleToEmit = null
        val nullResult = observeSingle(EntityId("sub-nonexistent")).first()
        assertNull(nullResult)
        assertEquals(EntityId("sub-nonexistent"), repository.lastObservedId)
    }

    @Test
    fun observe_rethrowsCancellationDirectly() = runTest {
        val repository = RecordingRepository().apply { throwCancellation = true }
        val observeList = ObserveSubscriptionsUseCase(repository)
        val observeSingle = ObserveSubscriptionUseCase(repository)

        assertFailsWith<CancellationException> {
            observeList().first()
        }

        assertFailsWith<CancellationException> {
            observeSingle(EntityId("sub-1")).first()
        }
    }

    private class RecordingRepository : SubscriptionRepository {
        var lastCreateCommand: CreateSubscriptionCommand? = null
        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("sub-created-1"))

        var lastUpdateCommand: UpdateSubscriptionCommand? = null
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastSetActiveCommand: SetSubscriptionActiveCommand? = null
        var setActiveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastAdvancedId: EntityId? = null
        var advanceResult: RepositoryResult<SubscriptionRenewalProgressionResult> =
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 3, 1)))

        var lastDeletedId: EntityId? = null
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var listToEmit: List<Subscription> = emptyList()
        var singleToEmit: Subscription? = null
        var lastObservedId: EntityId? = null
        var throwCancellation: Boolean = false

        override fun observeSubscriptions(): Flow<List<Subscription>> = flow {
            if (throwCancellation) {
                throw CancellationException("List observation cancelled")
            }
            emit(listToEmit)
        }

        override fun observeSubscription(id: EntityId): Flow<Subscription?> = flow {
            lastObservedId = id
            if (throwCancellation) {
                throw CancellationException("Single observation cancelled")
            }
            emit(singleToEmit)
        }

        override suspend fun create(command: CreateSubscriptionCommand): RepositoryResult<EntityId> {
            lastCreateCommand = command
            return createResult
        }

        override suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult
        }

        override suspend fun setActive(command: SetSubscriptionActiveCommand): RepositoryResult<Unit> {
            lastSetActiveCommand = command
            return setActiveResult
        }

        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> {
            lastAdvancedId = id
            return advanceResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {


            lastDeletedId = id
            return deleteResult
        }
    }

    private fun createCommand() = CreateSubscriptionCommand(
        name = "Spotify",
        amount = Money(5999L, Currency.TRY),
        categoryId = EntityId("cat-music"),
        renewalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        ),
        nextRenewalDate = LocalDate(2026, 2, 1),
    )

    private fun updateCommand() = UpdateSubscriptionCommand(
        id = EntityId("sub-1"),
        name = "Spotify Duo",
        amount = Money(7999L, Currency.TRY),
        categoryId = EntityId("cat-music"),
        renewalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        ),
    )

    private fun sampleSubscription(id: String) = Subscription(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = EntityId("ws-1"),
        name = "Abonelik $id",
        amount = Money(4999L, Currency.TRY),
        categoryId = EntityId("cat-1"),
        renewalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        ),
        nextRenewalDate = LocalDate(2026, 2, 1),
        isActive = true,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
