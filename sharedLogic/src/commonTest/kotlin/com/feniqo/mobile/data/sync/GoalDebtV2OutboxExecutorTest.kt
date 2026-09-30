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

    private companion object {
        const val GOAL_ID = "11111111-1111-4111-8111-111111111111"
        const val CONTRIB_ID = "22222222-2222-4222-8222-222222222222"
        const val DEBT_ID = "33333333-3333-4333-8333-333333333333"
        const val PAYMENT_ID = "44444444-4444-4444-8444-444444444444"
        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val OP_G1 = "00000000000000000000000000000001"
        const val OP_G2 = "00000000000000000000000000000002"
        const val OP_G3 = "00000000000000000000000000000003"
        const val OP_C1 = "00000000000000000000000000000004"
        const val OP_D1 = "00000000000000000000000000000005"
        const val OP_P1 = "00000000000000000000000000000006"
    }

    private fun sampleGoalDto(id: String = GOAL_ID, version: Long? = 1L): GoalDto = GoalDto(
        id = id,
        userId = USER_ID,
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

    private fun sampleDebtDto(id: String = DEBT_ID, version: Long? = 1L): DebtDto = DebtDto(
        id = id,
        userId = USER_ID,
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
        syncScopeKey = "USER:test-user",
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
            operationId = OP_G1,
            entityType = "GOAL",
            entityId = GOAL_ID,
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = """{"id":"$GOAL_ID","user_id":"$USER_ID","workspace_id":null,"name":"Hedef","target_amount_minor":100000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.GoalApplied>(result)
        assertEquals(GOAL_ID, result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(OP_G1, writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
    }

    @Test
    fun goal_update_conflict_returns_conflict_detected() = runTest {
        val remote = sampleGoalDto(version = 3L)
        val writer = RecordingWriter(goalResult = ConditionalRemoteWriteResult.Conflict(remote))
        val executor = V2OutboxOperationExecutor(writer) { 2000L }

        val op = buildOperation(
            operationId = OP_G2,
            entityType = "GOAL",
            entityId = GOAL_ID,
            operationType = "UPDATE",
            baseVersion = 1L,
            payloadJson = """{"id":"$GOAL_ID","user_id":"$USER_ID","workspace_id":null,"name":"Yeni Ad","target_amount_minor":120000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":"2026-09-01T10:05:00Z","deleted_at":null,"version":1}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals(OP_G2, result.conflict.operationId)
        assertEquals("GOAL", result.conflict.entityTypeCode)
        assertEquals(GOAL_ID, result.conflict.entityId)
        assertEquals(1L, result.conflict.localVersion)
        assertEquals(3L, result.conflict.remoteVersion)
    }

    @Test
    fun goal_delete_not_found_returns_missing_delete_acknowledged() = runTest {
        val writer = RecordingWriter(goalResult = ConditionalRemoteWriteResult.NotFound)
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = buildOperation(
            operationId = OP_G3,
            entityType = "GOAL",
            entityId = GOAL_ID,
            operationType = "DELETE",
            baseVersion = 1L,
            payloadJson = """{"id":"$GOAL_ID","user_id":"$USER_ID","workspace_id":null,"name":"Hedef","target_amount_minor":100000,"current_amount_minor":20000,"currency":"TRY","target_date":"2026-12-31","color_hex":"#2E7D32","icon_key":"savings","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":"2026-09-01T10:10:00Z","version":1}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.MissingDeleteAcknowledged>(result)
    }

    @Test
    fun goal_contribution_create_applied_returns_goal_contribution_applied() = runTest {
        val aggregateResponse = GoalContributionSyncRecordDto(
            contribution = GoalContributionDto(
                id = CONTRIB_ID,
                goalId = GOAL_ID,
                amountMinor = 10_000L,
                currency = "TRY",
                direction = "ADD",
                occurredOn = "2026-09-01",
                note = "Not",
                createdAt = "2026-09-01T10:00:00Z",
                deletedAt = null,
                version = 1L,
            ),
            goal = sampleGoalDto(id = GOAL_ID, version = 2L),
        )
        val writer = RecordingWriter(goalContributionResult = ConditionalRemoteWriteResult.Applied(aggregateResponse))
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val op = buildOperation(
            operationId = OP_C1,
            entityType = "GOAL_CONTRIBUTION",
            entityId = CONTRIB_ID,
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = """{"id":"$CONTRIB_ID","goal_id":"$GOAL_ID","amount_minor":10000,"currency":"TRY","direction":"ADD","occurred_on":"2026-09-01","note":"Not","created_at":"2026-09-01T10:00:00Z","deleted_at":null,"version":null}""",
        )

        val result = executor.execute(op)
        assertIs<OutboxExecutionResult.GoalContributionApplied>(result)
        assertEquals(CONTRIB_ID, result.record.contribution.id)
        assertEquals(GOAL_ID, result.record.goal.id)
        assertEquals(2L, result.record.goal.version)
        assertEquals(1L, writer.lastBaseVersion)
    }

    @Test
    fun debt_create_applied_and_debt_payment_applied() = runTest {
        val remoteDebt = sampleDebtDto(version = 1L)
        val writer = RecordingWriter(debtResult = ConditionalRemoteWriteResult.Applied(remoteDebt))
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val debtOp = buildOperation(
            operationId = OP_D1,
            entityType = "DEBT",
            entityId = DEBT_ID,
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = """{"id":"$DEBT_ID","user_id":"$USER_ID","workspace_id":null,"title":"Borç","amount_minor":50000,"currency":"TRY","type":"DEBT","due_date":"2026-10-15","status":"OPEN","description":"Açıklama","created_at":"2026-09-01T10:00:00Z","updated_at":null,"deleted_at":null,"version":null}""",
        )

        val debtResult = executor.execute(debtOp)
        assertIs<OutboxExecutionResult.DebtApplied>(debtResult)
        assertEquals(DEBT_ID, debtResult.record.id)
        assertEquals(1L, debtResult.record.version)

        // Debt Payment
        val paymentAggregateResponse = DebtPaymentSyncRecordDto(
            payment = DebtPaymentDto(
                id = PAYMENT_ID,
                debtId = DEBT_ID,
                amountMinor = 50_000L,
                currency = "TRY",
                paidOn = "2026-09-02",
                createdAt = "2026-09-02T10:00:00Z",
                deletedAt = null,
                version = 1L,
            ),
            debt = sampleDebtDto(id = DEBT_ID, version = 2L).copy(status = "SETTLED"),
        )
        writer.debtPaymentResult = ConditionalRemoteWriteResult.Applied(paymentAggregateResponse)

        val paymentOp = buildOperation(
            operationId = OP_P1,
            entityType = "DEBT_PAYMENT",
            entityId = PAYMENT_ID,
            operationType = "CREATE",
            baseVersion = 1L,
            payloadJson = """{"id":"$PAYMENT_ID","debt_id":"$DEBT_ID","amount_minor":50000,"currency":"TRY","paid_on":"2026-09-02","created_at":"2026-09-02T10:00:00Z","deleted_at":null,"version":null}""",
        )

        val payResult = executor.execute(paymentOp)
        assertIs<OutboxExecutionResult.DebtPaymentApplied>(payResult)
        assertEquals(PAYMENT_ID, payResult.record.payment.id)
        assertEquals("SETTLED", payResult.record.debt.status)
        assertEquals(2L, payResult.record.debt.version)
    }
}
