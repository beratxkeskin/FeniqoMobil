package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.AssetDto
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
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import kotlinx.coroutines.CancellationException

/** Outbox işleminin sunucuda yürütülmesi sonrası dönen tip güvenli sonuç. */
sealed interface OutboxExecutionResult {
    data object V1Completed : OutboxExecutionResult
    data class ProfileApplied(val record: ProfileDto) : OutboxExecutionResult
    data class CategoryApplied(val record: CategoryDto) : OutboxExecutionResult
    data class TransactionApplied(val record: TransactionDto) : OutboxExecutionResult
    data class BudgetApplied(val record: BudgetDto) : OutboxExecutionResult
    data class AssetApplied(val record: AssetDto) : OutboxExecutionResult
    data class RecurringTransactionApplied(val record: RecurringTransactionDto) : OutboxExecutionResult
    data class SubscriptionApplied(val record: SubscriptionDto) : OutboxExecutionResult
    data class GoalApplied(val record: GoalDto) : OutboxExecutionResult
    data class GoalContributionApplied(val record: GoalContributionSyncRecordDto) : OutboxExecutionResult
    data class DebtApplied(val record: DebtDto) : OutboxExecutionResult
    data class DebtPaymentApplied(val record: DebtPaymentSyncRecordDto) : OutboxExecutionResult
    data class WorkspaceApplied(val record: WorkspaceDto) : OutboxExecutionResult
    data class WorkspaceMemberApplied(val record: WorkspaceMemberDto) : OutboxExecutionResult
    data class WorkspaceInvitationApplied(val record: WorkspaceInvitationDto) : OutboxExecutionResult
    data object MissingDeleteAcknowledged : OutboxExecutionResult
    data class ConflictDetected(val conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity) : OutboxExecutionResult
}




/** Outbox'ın kalıcı sırasını koruyarak tek tek gönderilmesini sağlayan ortak senkronizasyon çekirdeği. */
class OutboxProcessor(
    private val queue: OutboxQueue,
    private val executor: OutboxOperationExecutor,
) {
    suspend fun processReadyOperations(limit: Int = DEFAULT_BATCH_SIZE): OutboxProcessResult {
        var succeeded = 0
        var failedOperationId: String? = null
        var conflictOperationId: String? = null
        var lastError: Throwable? = null

        for (candidate in queue.readyOperations(limit)) {
            val claimed = queue.claimOperation(candidate.operationId) ?: continue
            try {
                val result = executor.execute(claimed)
                when (result) {
                    is OutboxExecutionResult.V1Completed -> {
                        if (queue.markSucceeded(claimed.operationId)) succeeded++
                    }
                    is OutboxExecutionResult.ConflictDetected -> {
                        queue.recordV2Conflict(result.conflict)
                        conflictOperationId = claimed.operationId
                        break
                    }
                    else -> {
                        if (queue.ackV2Execution(claimed.operationId, result)) succeeded++
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (conflict: OutboxConflictException) {
                queue.markConflict(claimed.operationId, SAFE_CONFLICT_CODE)
                conflictOperationId = claimed.operationId
                break
            } catch (error: Exception) {
                val classification = error.classifyOutboxError().name
                queue.recordFailure(claimed.operationId, SAFE_FAILURE_CODE, classification)
                failedOperationId = claimed.operationId
                lastError = error
                break
            }
        }
        return OutboxProcessResult(succeeded, failedOperationId, conflictOperationId, lastError)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val SAFE_CONFLICT_CODE = "sync_conflict"
        const val SAFE_FAILURE_CODE = "sync_operation_failed"
    }
}

data class OutboxProcessResult(
    val succeededCount: Int,
    val failedOperationId: String?,
    val conflictOperationId: String? = null,
    val lastError: Throwable? = null,
)

class OutboxConflictException(message: String) : IllegalStateException(message)

fun interface OutboxOperationExecutor {
    /** İşlem başarılı sayılmadan önce uzak kaynağın isteği kabul ettiğini doğrular ve yürütme sonucunu döner. */
    suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult
}

/** İşleyiciyi Room uygulamasından ayırır; testte yan etkisiz sahte kuyruk kullanılabilir. */
interface OutboxQueue {
    suspend fun readyOperations(limit: Int): List<SyncOperationEntity>
    suspend fun claimOperation(operationId: String): SyncOperationEntity?
    suspend fun markSucceeded(operationId: String): Boolean
    suspend fun ackV2Execution(operationId: String, result: OutboxExecutionResult): Boolean
    suspend fun recordV2Conflict(conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean
    suspend fun recordFailure(operationId: String, errorMessage: String): Boolean
    suspend fun recordFailure(operationId: String, errorMessage: String, errorClassification: String?): Boolean =
        recordFailure(operationId, errorMessage)
    suspend fun markConflict(operationId: String, errorMessage: String): Boolean
}

class RoomOutboxQueue(
    private val delegate: OfflineWriteQueue,
) : OutboxQueue {
    override suspend fun readyOperations(limit: Int): List<SyncOperationEntity> = delegate.getReadyOperations(limit)
    override suspend fun claimOperation(operationId: String): SyncOperationEntity? = delegate.claimOperation(operationId)
    override suspend fun markSucceeded(operationId: String): Boolean = delegate.markSucceeded(operationId)
    override suspend fun ackV2Execution(operationId: String, result: OutboxExecutionResult): Boolean =
        delegate.ackV2Execution(operationId, result)
    override suspend fun recordV2Conflict(conflict: com.feniqo.mobile.data.local.entity.SyncConflictEntity): Boolean =
        delegate.recordV2Conflict(conflict)
    override suspend fun recordFailure(operationId: String, errorMessage: String): Boolean =
        delegate.recordFailure(operationId, errorMessage, null)
    override suspend fun recordFailure(operationId: String, errorMessage: String, errorClassification: String?): Boolean =
        delegate.recordFailure(operationId, errorMessage, errorClassification)
    override suspend fun markConflict(operationId: String, errorMessage: String): Boolean =
        delegate.markConflict(operationId, errorMessage)
}

/**
 * Outbox işleminin uzak sunucuya gönderilmeden önce yerel kontrollerde (şema, DTO decode, ID eşleşmesi vb.)
 * veya sunucu tarafında kesin olarak reddedildiğini ve uzak veritabanında hiçbir mutation kalıntısı
 * oluşmadığını doğrulayan tipli hata.
 */
open class DefinitiveOutboxFailureException(
    message: String,
    val statusCode: Int? = null,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Sunucunun (PostgreSQL RPC / PostgREST) işlemi kesin olarak reddettiğini,
 * rollback uygulandığını ve uzak veritabanında hiçbir kalıntı oluşmadığını
 * doğrulayan tipli exception.
 */
class ServerRejectedMutationException(
    message: String,
    statusCode: Int? = null,
    errorCode: String? = null,
    cause: Throwable? = null,
) : DefinitiveOutboxFailureException(message, statusCode, errorCode, cause)

fun Throwable.classifyOutboxError(): com.feniqo.mobile.data.local.entity.OutboxErrorClassification {
    return when (this) {
        is DefinitiveOutboxFailureException -> {
            com.feniqo.mobile.data.local.entity.OutboxErrorClassification.DEFINITIVE_REJECTION
        }
        is io.github.jan.supabase.exceptions.RestException -> {
            // PostgREST RPC çağrılarında 4xx kodları (400, 401, 403, 404, 422),
            // PostgreSQL transaction'ının EXCEPTION fırlatarak ROLLBACK ile bittiğini
            // ve veritabanı kaydı oluşturulmadığını garanti eder.
            // 5xx (500, 502, 503, 504) ise ağ geçidi timeout veya belirsiz sunucu hatası olabilir.
            val statusCode = try { response.status.value } catch (_: Exception) { 0 }
            when (statusCode) {
                400, 401, 403, 404, 422 -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.DEFINITIVE_REJECTION
                else -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.AMBIGUOUS_RESULT
            }
        }
        is io.github.jan.supabase.auth.exception.AuthRestException -> when (errorCode) {
            io.github.jan.supabase.auth.exception.AuthErrorCode.WeakPassword,
            io.github.jan.supabase.auth.exception.AuthErrorCode.EmailAddressInvalid,
            io.github.jan.supabase.auth.exception.AuthErrorCode.ValidationFailed -> {
                com.feniqo.mobile.data.local.entity.OutboxErrorClassification.DEFINITIVE_REJECTION
            }
            else -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.AMBIGUOUS_RESULT
        }
        // SerializationException ve genel IllegalArgumentException sunucu işlemi başarıyla
        // uyguladıktan sonra response decode aşamasında da oluşabileceğinden KESİNLİKLE AMBIGUOUS_RESULT olmalıdır.
        is kotlinx.serialization.SerializationException -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.AMBIGUOUS_RESULT
        is IllegalArgumentException -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.AMBIGUOUS_RESULT
        else -> com.feniqo.mobile.data.local.entity.OutboxErrorClassification.AMBIGUOUS_RESULT
    }
}
