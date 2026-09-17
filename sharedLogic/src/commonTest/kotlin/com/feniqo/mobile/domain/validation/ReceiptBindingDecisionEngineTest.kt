package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CurrentSessionSnapshot
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ReceiptBindingDecision
import com.feniqo.mobile.domain.model.ReceiptFileDescriptor
import com.feniqo.mobile.domain.model.ReceiptMimeType
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.ReceiptUploadOutcome
import com.feniqo.mobile.domain.model.ReconcileIndeterminateUploadDecision
import com.feniqo.mobile.domain.model.TransactionReceiptLinkage
import com.feniqo.mobile.domain.model.TransactionValidationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReceiptBindingDecisionEngineTest {

    private val defaultOwnerId = EntityId("user-123")
    private val defaultTxId = EntityId("tx-001")
    private val defaultAttachmentIdA = EntityId("att-aaa")
    private val defaultAttachmentIdB = EntityId("att-bbb")
    private val validSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val validSizeBytes = 1024L * 50L // 50 KB
    private val defaultRemotePathA = ReceiptPath("user-123/tx-001/att-aaa.jpg")
    private val defaultRemotePathB = ReceiptPath("user-123/tx-001/att-bbb.jpg")

    private fun createDefaultSession(
        userId: EntityId = defaultOwnerId,
        sessionEpoch: Long = 1L,
    ) = CurrentSessionSnapshot(
        userId = userId,
        sessionEpoch = sessionEpoch,
    )

    private fun createDefaultFileDescriptor(
        attachmentId: EntityId = defaultAttachmentIdA,
        ownerId: EntityId = defaultOwnerId,
        remotePath: ReceiptPath = defaultRemotePathA,
        sha256: String = validSha256,
        sizeBytes: Long = validSizeBytes,
    ) = ReceiptFileDescriptor(
        attachmentId = attachmentId,
        ownerId = ownerId,
        contentSha256 = sha256,
        fileSizeBytes = sizeBytes,
        mimeType = ReceiptMimeType.JPEG,
        remotePath = remotePath,
    )

    private fun createDefaultLinkage(
        activeAttachmentId: EntityId? = defaultAttachmentIdA,
        ownerId: EntityId = defaultOwnerId,
        generation: Long = 1L,
        sessionEpoch: Long = 1L,
        workspaceId: EntityId? = null,
        transactionId: EntityId = defaultTxId,
    ) = TransactionReceiptLinkage(
        transactionId = transactionId,
        ownerId = ownerId,
        activeAttachmentId = activeAttachmentId,
        generation = generation,
        sessionEpoch = sessionEpoch,
        workspaceId = workspaceId,
    )

    private fun createDefaultOutcome(
        attachmentId: EntityId = defaultAttachmentIdA,
        ownerId: EntityId = defaultOwnerId,
        transactionId: EntityId = defaultTxId,
        generation: Long = 1L,
        sessionEpoch: Long = 1L,
        remotePath: ReceiptPath = defaultRemotePathA,
        sha256: String = validSha256,
        sizeBytes: Long = validSizeBytes,
    ) = ReceiptUploadOutcome(
        attachmentId = attachmentId,
        ownerId = ownerId,
        transactionId = transactionId,
        generation = generation,
        sessionEpoch = sessionEpoch,
        remotePath = remotePath,
        contentSha256 = sha256,
        fileSizeBytes = sizeBytes,
    )

    private fun createDefaultTxState(
        transactionId: EntityId = defaultTxId,
        exists: Boolean = true,
        isSoftDeleted: Boolean = false,
        ownerId: EntityId = defaultOwnerId,
        workspaceId: EntityId? = null,
    ) = TransactionValidationState(
        transactionId = transactionId,
        exists = exists,
        isSoftDeleted = isSoftDeleted,
        ownerId = ownerId,
        workspaceId = workspaceId,
    )

    @Test
    fun validUploadOutcome_withMatchingSessionAndIds_evaluatesToApplicable() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.Applicable>(decision)
        assertEquals(defaultTxId, decision.transactionId)
        assertEquals(defaultAttachmentIdA, decision.attachmentId)
        assertEquals(defaultRemotePathA, decision.remotePath)
    }

    @Test
    fun whenCurrentSessionIsNull_decisionReturnsSessionMismatch() {
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = null,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenCurrentSessionUserDiffersFromLinkageOrOutcome_decisionReturnsSessionMismatch() {
        val differentUserSession = createDefaultSession(userId = EntityId("user-different-999"))
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = differentUserSession,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenUserLogsOutAndBackIn_currentSessionEpochDiffersFromOutcome_decisionReturnsSessionMismatch() {
        // Yeni oturumun epoch'u 2L
        val newSession = createDefaultSession(sessionEpoch = 2L)
        // Eski oturumdan kalan linkage ve outcome (epoch 1L)
        val oldLinkage = createDefaultLinkage(sessionEpoch = 1L)
        val file = createDefaultFileDescriptor()
        val oldOutcome = createDefaultOutcome(sessionEpoch = 1L)
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = oldLinkage,
            outcome = oldOutcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = newSession,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenOutcomeTransactionIdDiffersFromLinkage_decisionReturnsTransactionMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage(transactionId = defaultTxId)
        val file = createDefaultFileDescriptor()
        val otherTxOutcome = createDefaultOutcome(transactionId = EntityId("tx-other-999"))
        val txState = createDefaultTxState(transactionId = defaultTxId)

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = otherTxOutcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.TransactionMismatch>(decision)
        assertEquals("target_transaction_mismatch", decision.reason)
    }

    @Test
    fun whenTransactionStateIdDiffersFromLinkage_decisionReturnsTransactionMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage(transactionId = defaultTxId)
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome(transactionId = defaultTxId)
        val otherTxState = createDefaultTxState(transactionId = EntityId("tx-other-888"))

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = otherTxState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.TransactionMismatch>(decision)
        assertEquals("target_transaction_mismatch", decision.reason)
    }

    @Test
    fun whenExpectedFileAttachmentIdDiffersFromOutcome_decisionReturnsIntegrityMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        // Beklenen dosyanın ID'si farklı
        val fileWithDifferentId = createDefaultFileDescriptor(attachmentId = EntityId("att-different"))
        val outcome = createDefaultOutcome(attachmentId = defaultAttachmentIdA)
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = fileWithDifferentId,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.IntegrityMismatch>(decision)
        assertEquals("attachment_id_mismatch", decision.reason)
    }

    @Test
    fun whenExpectedFileOwnerDiffersFromSession_decisionReturnsSessionMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val fileWithDifferentOwner = createDefaultFileDescriptor(ownerId = EntityId("user-alien"))
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = fileWithDifferentOwner,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenLinkageOwnerDiffersFromSession_decisionReturnsSessionMismatch() {
        val session = createDefaultSession()
        val linkageWithAlienOwner = createDefaultLinkage(ownerId = EntityId("user-alien"))
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkageWithAlienOwner,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenOutcomeOwnerDiffersFromSession_decisionReturnsSessionMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcomeWithAlienOwner = createDefaultOutcome(ownerId = EntityId("user-alien"))
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcomeWithAlienOwner,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenTransactionStateOwnerDiffersFromSession_decisionReturnsSessionMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txStateWithAlienOwner = createDefaultTxState(ownerId = EntityId("user-alien"))

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txStateWithAlienOwner,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.SessionMismatch>(decision)
    }

    @Test
    fun whenUserSelectsB_whileAIsUploading_outcomeAIsRejectedAsStaleGeneration() {
        val session = createDefaultSession()
        // 1. Kullanıcı A'yı seçti (Gen 1)
        val initialLinkage = createDefaultLinkage(activeAttachmentId = defaultAttachmentIdA, generation = 1L)
        // 2. A yüklenirken kullanıcı B'yi seçti (Gen 2)
        val updatedLinkage = initialLinkage.withNewAttachment(defaultAttachmentIdB)
        assertEquals(2L, updatedLinkage.generation)
        assertEquals(defaultAttachmentIdB, updatedLinkage.activeAttachmentId)

        // 3. A'nın upload işi sonradan başarılı bitti ve sonuç döndü (Gen 1 ile)
        val outcomeA = createDefaultOutcome(
            attachmentId = defaultAttachmentIdA,
            generation = 1L,
            remotePath = defaultRemotePathA,
        )
        val fileA = createDefaultFileDescriptor(attachmentId = defaultAttachmentIdA, remotePath = defaultRemotePathA)
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = updatedLinkage,
            outcome = outcomeA,
            transactionState = txState,
            expectedFile = fileA,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.StaleGeneration>(decision)
        assertEquals(2L, decision.expectedGeneration)
        assertEquals(1L, decision.incomingGeneration)
    }

    @Test
    fun whenUserRemovesAttachment_whileUploading_outcomeIsRejectedAsStaleGeneration() {
        val session = createDefaultSession()
        // 1. Kullanıcı A'yı seçti (Gen 1)
        val initialLinkage = createDefaultLinkage(activeAttachmentId = defaultAttachmentIdA, generation = 1L)
        // 2. Yükleme sürerken kullanıcı makbuzu kaldırdı
        val removedLinkage = initialLinkage.withRemovedAttachment()
        assertEquals(2L, removedLinkage.generation)
        assertEquals(null, removedLinkage.activeAttachmentId)

        // 3. A'nın sonucu gecikmeli geldi
        val outcomeA = createDefaultOutcome(attachmentId = defaultAttachmentIdA, generation = 1L)
        val fileA = createDefaultFileDescriptor()
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = removedLinkage,
            outcome = outcomeA,
            transactionState = txState,
            expectedFile = fileA,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.StaleGeneration>(decision)
        assertEquals(2L, decision.expectedGeneration)
        assertEquals(1L, decision.incomingGeneration)
    }

    @Test
    fun whenTransactionIsSoftDeleted_decisionReturnsTransactionUnavailable() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState(isSoftDeleted = true)

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.TransactionUnavailable>(decision)
        assertEquals("transaction_soft_deleted", decision.reason)
    }

    @Test
    fun whenTransactionDoesNotExist_decisionReturnsTransactionUnavailable() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState(exists = false)

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.TransactionUnavailable>(decision)
        assertEquals("transaction_not_found", decision.reason)
    }

    @Test
    fun whenFileSizeDoesNotMatch_decisionReturnsIntegrityMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor(sizeBytes = 5000L)
        val outcomeWrongSize = createDefaultOutcome(sizeBytes = 9999L)
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcomeWrongSize,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.IntegrityMismatch>(decision)
        assertEquals("file_size_mismatch", decision.reason)
    }

    @Test
    fun whenSha256DoesNotMatch_decisionReturnsIntegrityMismatch() {
        val session = createDefaultSession()
        val linkage = createDefaultLinkage()
        val file = createDefaultFileDescriptor(sha256 = validSha256)
        val otherHash = "a".repeat(64)
        val outcomeWrongHash = createDefaultOutcome(sha256 = otherHash)
        val txState = createDefaultTxState()

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage,
            outcome = outcomeWrongHash,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.IntegrityMismatch>(decision)
        assertEquals("sha256_hash_mismatch", decision.reason)
    }

    @Test
    fun sharedWorkspaceTransaction_isRejectedAsUnsupportedScope() {
        val session = createDefaultSession()
        val workspaceLinkage = createDefaultLinkage(workspaceId = EntityId("ws-team-1"))
        val file = createDefaultFileDescriptor()
        val outcome = createDefaultOutcome()
        val txState = createDefaultTxState(workspaceId = EntityId("ws-team-1"))

        val decision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = workspaceLinkage,
            outcome = outcome,
            transactionState = txState,
            expectedFile = file,
            currentSession = session,
        )

        assertIs<ReceiptBindingDecision.UnsupportedScope>(decision)
    }

    @Test
    fun sharedInstallmentTransactions_evaluatesDecisionEngineIndependently() {
        val session = createDefaultSession()
        val sharedAttachmentId = EntityId("shared-att-001")
        val tx1Id = EntityId("tx-installment-1")
        val tx2Id = EntityId("tx-installment-2")
        val remotePath1 = ReceiptPath("user-123/tx-installment-1/shared-att-001.jpg")
        val remotePath2 = ReceiptPath("user-123/tx-installment-2/shared-att-001.jpg")

        val file1 = createDefaultFileDescriptor(attachmentId = sharedAttachmentId, remotePath = remotePath1)
        val file2 = createDefaultFileDescriptor(attachmentId = sharedAttachmentId, remotePath = remotePath2)

        // İki farklı taksit işlemi aynı attachment ID'ye bağlı başlatılır
        val linkage1 = createDefaultLinkage(transactionId = tx1Id, activeAttachmentId = sharedAttachmentId, generation = 1L)
        val linkage2 = createDefaultLinkage(transactionId = tx2Id, activeAttachmentId = sharedAttachmentId, generation = 1L)

        val outcome1 = createDefaultOutcome(attachmentId = sharedAttachmentId, transactionId = tx1Id, remotePath = remotePath1)
        val outcome2 = createDefaultOutcome(attachmentId = sharedAttachmentId, transactionId = tx2Id, remotePath = remotePath2)

        val tx1State = createDefaultTxState(transactionId = tx1Id)
        val tx2State = createDefaultTxState(transactionId = tx2Id)

        // Taksit 1'den ek kaldırılır
        val updatedLinkage1 = linkage1.withRemovedAttachment()

        // 1. Taksit 1 için gecikmiş outcome1 değerlendirildiğinde reddedilmeli (StaleGeneration)
        val decision1 = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = updatedLinkage1,
            outcome = outcome1,
            transactionState = tx1State,
            expectedFile = file1,
            currentSession = session,
        )
        assertIs<ReceiptBindingDecision.StaleGeneration>(decision1)

        // 2. Taksit 2'nin kendi sonucu değerlendirildiğinde uygulanabilir olmalı (Applicable)
        val decision2 = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage2,
            outcome = outcome2,
            transactionState = tx2State,
            expectedFile = file2,
            currentSession = session,
        )
        assertIs<ReceiptBindingDecision.Applicable>(decision2)
        assertEquals(tx2Id, decision2.transactionId)
        assertEquals(sharedAttachmentId, decision2.attachmentId)

        // 3. Taksit 1'in sonucu Taksit 2 için kullanılmaya çalışılırsa reddedilmeli (TransactionMismatch)
        val crossDecision = ReceiptBindingDecisionEngine.evaluateBindingDecision(
            linkage = linkage2,
            outcome = outcome1, // outcome1'in transactionId'si tx1Id'dir
            transactionState = tx2State,
            expectedFile = file2,
            currentSession = session,
        )
        assertIs<ReceiptBindingDecision.TransactionMismatch>(crossDecision)
    }

    @Test
    fun generationOverflow_throwsIllegalStateException() {
        val maxGenLinkage = createDefaultLinkage(generation = Long.MAX_VALUE)

        assertFailsWith<IllegalStateException> {
            maxGenLinkage.withNewAttachment(EntityId("att-overflow"))
        }

        assertFailsWith<IllegalStateException> {
            maxGenLinkage.withRemovedAttachment()
        }
    }

    @Test
    fun reconcileIndeterminateUpload_withMatchingPathHashAndSize_evaluatesToMatch() {
        val file = createDefaultFileDescriptor(sha256 = validSha256, sizeBytes = validSizeBytes)

        val result = ReceiptBindingDecisionEngine.reconcileIndeterminateUpload(
            expectedFile = file,
            remotePath = file.remotePath,
            remoteFileSizeBytes = validSizeBytes,
            remoteContentSha256 = validSha256,
        )

        assertIs<ReconcileIndeterminateUploadDecision.Match>(result)
        assertEquals(file.remotePath, result.verifiedRemotePath)
    }

    @Test
    fun reconcileIndeterminateUpload_withDifferingRemotePath_evaluatesToConflict_evenIfContentMatches() {
        val file = createDefaultFileDescriptor(sha256 = validSha256, sizeBytes = validSizeBytes)
        val differentPath = ReceiptPath("user-123/tx-other/diff.jpg")

        val result = ReceiptBindingDecisionEngine.reconcileIndeterminateUpload(
            expectedFile = file,
            remotePath = differentPath,
            remoteFileSizeBytes = validSizeBytes,
            remoteContentSha256 = validSha256,
        )

        assertIs<ReconcileIndeterminateUploadDecision.Conflict>(result)
        assertEquals("remote_path_mismatch", result.reason)
    }

    @Test
    fun reconcileIndeterminateUpload_withMismatchingHashOrSize_evaluatesToConflict() {
        val file = createDefaultFileDescriptor(sha256 = validSha256, sizeBytes = validSizeBytes)

        // Boyut uyuşmazlığı
        val sizeMismatch = ReceiptBindingDecisionEngine.reconcileIndeterminateUpload(
            expectedFile = file,
            remotePath = file.remotePath,
            remoteFileSizeBytes = validSizeBytes + 1L,
            remoteContentSha256 = validSha256,
        )
        assertIs<ReconcileIndeterminateUploadDecision.Conflict>(sizeMismatch)
        assertEquals("remote_object_exists_with_differing_content", sizeMismatch.reason)

        // Hash uyuşmazlığı
        val hashMismatch = ReceiptBindingDecisionEngine.reconcileIndeterminateUpload(
            expectedFile = file,
            remotePath = file.remotePath,
            remoteFileSizeBytes = validSizeBytes,
            remoteContentSha256 = "b".repeat(64),
        )
        assertIs<ReconcileIndeterminateUploadDecision.Conflict>(hashMismatch)
        assertEquals("remote_object_exists_with_differing_content", hashMismatch.reason)
    }
}
