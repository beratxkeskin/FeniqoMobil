package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GoalDebtV2OutboxExecutorTest {

    private class RecordingWriter(
        var goalResult: ConditionalRemoteWriteResult<GoalDto>? = null,
        var goalContributionResult: ConditionalRemoteWriteResult<GoalContributionSyncRecordDto>? = null,
        var debtResult: ConditionalRemoteWriteResult<DebtDto>? = null,
        var debtPaymentResult: ConditionalRemoteWriteResult<DebtPaymentSyncRecordDto>? = null,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastGoalDto: GoalDto? = null
        var lastGoalContribDto: GoalContributionDto? = null
        var lastDebtDto: DebtDto? = null
        var lastDebtPaymentDto: DebtPaymentDto? = null

        override suspend fun writeGoal(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: GoalDto,
        ): ConditionalRemoteWriteResult<GoalDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastGoalDto = dto
            return goalResult ?: error("goalResult unset")
        }

        override suspend fun writeGoalContribution(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: GoalContributionDto,
        ): ConditionalRemoteWriteResult<GoalContributionSyncRecordDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastGoalContribDto = dto
            return goalContributionResult ?: error("goalContributionResult unset")
        }

        override suspend fun writeDebt(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: DebtDto,
        ): ConditionalRemoteWriteResult<DebtDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastDebtDto = dto
            return debtResult ?: error("debtResult unset")
        }

        override suspend fun writeDebtPayment(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: DebtPaymentDto,
        ): ConditionalRemoteWriteResult<DebtPaymentSyncRecordDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastDebtPaymentDto = dto
            return debtPaymentResult ?: error("debtPaymentResult unset")
        }
    }

    private fun sampleGoalDto(id: String = "goal-1", version: Long? = 1L): GoalDto = GoalDto(
        id = id,
        userId = "user-1",
        workspaceId = null,
        name = "Hedef",
        targetAmountMinor = 100_000L,
        currentAmountMinor = 20_000L,
        currency = "TRY",
        targetDate = "2026-12-31",
        colorHex = "#2E7D32",
        iconKey = "savings",
        createdAt = "2026-09-01T10:00:00Z",
        updatedAt = "2026-09-01T10:00:01Z",
        deletedAt = null,
        version = version,
    )

    private fun sampleDebtDto(id: String = "debt-1", version: Long? = 1L): DebtDto = DebtDto(
        id = id,
        userId = "user-1",
        workspaceId = null,
        title = "Borç",
        amountMinor = 50_000L,
        currency = "TRY",
        type = "DEBT",
        dueDate = "2026-10-15",
        status = "OPEN",
        description = "Açıklama",
        createdAt = "2026-09-01T10:00:00Z",
        updatedAt = "2026-09-01T10:00:01Z",
        deletedAt = null,
        version = version,
    )

    private fun buildOperation(
        operationId: String,
        entityType: String,
        entityId: String,
        operationType: String,
        baseVersion: Long?,
        payloadJson: String?,
    ) = SyncOperationEntity(
        operationId = operationId,
        entityTypeCode = entityType,
        entityId = entityId,
        operationTypeCode = operationType,
        baseVersion = baseVersion,
        payloadJson = payloadJson,
        predecessorOperationId = null,
        isBlocked = false,
        protocolVersion = 2,
        statusCode = "IN_FLIGHT",
        attemptCount = 1,
        lastError = null,
        nextAttemptAtEpochMillis = 0L,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
    )

    @Test
    fun goal_create_applied_returns_goal_applied() = runTest {
        val remote = sampleGoalDto(version = 1L)
        val writer = RecordingWriter(goalResult = ConditionalRemoteWriteResult.Applied(remote))
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = buildOperation(
            operationId = "op-g1",
            entityType = "GOAL",
            entityId = "goal-1",
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = """{"id":"goal-1","user_id":"user-1","workspace_id":null,"name":"Hedef","target_amount_minor":100000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.GoalApplied>(result)
        assertEquals("goal-1", result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals("op-g1", writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
    }

    @Test
    fun goal_update_conflict_returns_conflict_detected() = runTest {
        val remote = sampleGoalDto(version = 3L)
        val writer = RecordingWriter(goalResult = ConditionalRemoteWriteResult.Conflict(remote))
        val executor = V2OutboxOperationExecutor(writer) { 2000L }

        val op = buildOperation(
            operationId = "op-g2",
            entityType = "GOAL",
            entityId = "goal-1",
            operationType = "UPDATE",
            baseVersion = 1L,
            payloadJson = """{"id":"goal-1","user_id":"user-1","workspace_id":null,"name":"Yeni Ad","target_amount_minor":120000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":"2026-09-01T10:05:00Z","deleted_at":null,"version":1}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals("op-g2", result.conflict.operationId)
        assertEquals("GOAL", result.conflict.entityTypeCode)
        assertEquals("goal-1", result.conflict.entityId)
        assertEquals(1L, result.conflict.localVersion)
        assertEquals(3L, result.conflict.remoteVersion)
    }

    @Test
    fun goal_delete_not_found_returns_missing_delete_acknowledged() = runTest {
        val writer = RecordingWriter(goalResult = ConditionalRemoteWriteResult.NotFound)
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = buildOperation(
            operationId = "op-g3",
            entityType = "GOAL",
            entityId = "goal-1",
            operationType = "DELETE",
            baseVersion = 1L,
            payloadJson = """{"id":"goal-1","user_id":"user-1","workspace_id":null,"name":"Hedef","target_amount_minor":100000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":"2026-09-01T10:10:00Z","version":1}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.MissingDeleteAcknowledged>(result)
    }

    @Test
    fun goal_contribution_create_applied_returns_goal_contribution_applied() = runTest {
        val aggregateResponse = GoalContributionSyncRecordDto(
            contribution = GoalContributionDto(
                id = "c-1",
                goalId = "goal-1",
                amountMinor = 10_000L,
                currency = "TRY",
                direction = "ADD",
                occurredOn = "2026-09-01",
                note = "Not",
                createdAt = "2026-09-01T10:00:00Z",
                deletedAt = null,
                version = 1L,
            ),
            goal = sampleGoalDto(id = "goal-1", version = 2L),
        )
        val writer = RecordingWriter(goalContributionResult = ConditionalRemoteWriteResult.Applied(aggregateResponse))
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = buildOperation(
            operationId = "op-c1",
            entityType = "GOAL_CONTRIBUTION",
            entityId = "c-1",
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = """{"id":"c-1","goal_id":"goal-1","amount_minor":10000,"currency":"TRY","direction":"ADD","occurred_on":"2026-09-01","note":"Not","created_at":"2026-09-01T10:00:00Z","deleted_at":null,"version":null}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.GoalContributionApplied>(result)
        assertEquals("c-1", result.record.contribution.id)
        assertEquals("goal-1", result.record.goal.id)
        assertEquals(2L, result.record.goal.version)
        assertEquals(1L, writer.lastBaseVersion)
    }

    @Test
    fun debt_create_applied_and_debt_payment_applied() = runTest {
        val remoteDebt = sampleDebtDto(version = 1L)
        val writer = RecordingWriter(debtResult = ConditionalRemoteWriteResult.Applied(remoteDebt))
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val debtOp = buildOperation(
            operationId = "op-d1",
            entityType = "DEBT",
            entityId = "debt-1",
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = """{"id":"debt-1","user_id":"user-1","workspace_id":null,"title":"Borç","amount_minor":50000,"currency":"TRY","type":"DEBT","due_date":"2026-10-15","status":"OPEN","description":"Açıklama","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
        )

        val debtResult = executor.execute(debtOp)
        assertIs<OutboxExecutionResult.DebtApplied>(debtResult)
        assertEquals("debt-1", debtResult.record.id)
        assertEquals(1L, debtResult.record.version)

        // Debt Payment
        val paymentAggregateResponse = DebtPaymentSyncRecordDto(
            payment = DebtPaymentDto(
                id = "p-1",
                debtId = "debt-1",
                amountMinor = 50_000L,
                currency = "TRY",
                paidOn = "2026-09-02",
                createdAt = "2026-09-02T10:00:00Z",
                deletedAt = null,
                version = 1L,
            ),
            debt = sampleDebtDto(id = "debt-1", version = 2L).copy(status = "SETTLED"),
        )
        writer.debtPaymentResult = ConditionalRemoteWriteResult.Applied(paymentAggregateResponse)

        val paymentOp = buildOperation(
            operationId = "op-p1",
            entityType = "DEBT_PAYMENT",
            entityId = "p-1",
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = """{"id":"p-1","debt_id":"debt-1","amount_minor":50000,"currency":"TRY","paid_on":"2026-09-02","created_at":"2026-09-02T10:00:00Z","deleted_at":null,"version":null}""",
        )

        val payResult = executor.execute(paymentOp)
        assertIs<OutboxExecutionResult.DebtPaymentApplied>(payResult)
        assertEquals("p-1", payResult.record.payment.id)
        assertEquals("SETTLED", payResult.record.debt.status)
        assertEquals(2L, payResult.record.debt.version)
    }
}
