package com.feniqo.mobile.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class FeniqoBackupFormatTest {
    private val uncheckedJson = Json { encodeDefaults = true }

    @Test
    fun validV1_roundTripsAndContainsNoPrivateOrSyncFields() {
        val encoded = FeniqoBackupCodec.encode(sample())
        assertEquals(sample(), assertIs<BackupDecodeResult.Valid>(FeniqoBackupCodec.decode(encoded)).backup)
        listOf("owner_id", "receipt_path", "sync_status", "base_version", "access_token", "ocr").forEach {
            assertFalse(encoded.contains(it))
        }
    }

    @Test
    fun unsupportedVersionAndUnknownFieldsAreRejected() {
        assertEquals("backup_version_unsupported", invalid("""{"format_version":2}"""))
        val unknown = FeniqoBackupCodec.encode(sample()).dropLast(1) + ",\"token\":\"secret\"}"
        assertEquals("backup_invalid_json", invalid(unknown))
    }

    @Test
    fun duplicateIdsAndBrokenReferencesAreRejected() {
        val duplicate = sample().let { it.copy(transactions = it.transactions + it.transactions.first()) }
        assertEquals("backup_duplicate_transaction", invalid(uncheckedJson.encodeToString(duplicate)))
        val broken = sample().let { it.copy(transactions = listOf(it.transactions.first().copy(categoryId = "missing"))) }
        assertEquals("backup_transaction_invalid", invalid(uncheckedJson.encodeToString(broken)))
    }

    @Test
    fun legacyBackupWithoutNote_decodesWithNullNote() {
        val legacyJson = """
            {
                "format_version": 1,
                "created_at": "2026-09-10T12:00:00Z",
                "categories": [
                    {"id": "cat-1", "name": "Market", "type": "EXPENSE", "color": "#112233", "icon": "cart"}
                ],
                "transactions": [
                    {
                        "id": "tx-1",
                        "amount_minor": 12550,
                        "currency": "TRY",
                        "type": "EXPENSE",
                        "category_id": "cat-1",
                        "description": "Eski İşlem",
                        "payment_method": "CREDIT_CARD",
                        "transaction_date": "2026-09-10",
                        "created_at": "2026-09-10T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()
        val result = assertIs<BackupDecodeResult.Valid>(FeniqoBackupCodec.decode(legacyJson))
        assertEquals(null, result.backup.transactions.first().note)
    }

    @Test
    fun backupWithNote_roundTripsCorrectly() {
        val withNote = sample().let {
            it.copy(transactions = listOf(it.transactions.first().copy(note = "Önemli not")))
        }
        val encoded = FeniqoBackupCodec.encode(withNote)
        val decoded = assertIs<BackupDecodeResult.Valid>(FeniqoBackupCodec.decode(encoded)).backup
        assertEquals("Önemli not", decoded.transactions.first().note)
    }

    private fun invalid(raw: String) = assertIs<BackupDecodeResult.Invalid>(FeniqoBackupCodec.decode(raw)).reason

    private fun sample() = FeniqoBackupV1(
        createdAt = "2026-09-10T12:00:00Z",
        categories = listOf(BackupCategoryV1("cat-1", "Market", "EXPENSE", "#112233", "cart")),
        transactions = listOf(
            BackupTransactionV1(
                id = "tx-1", amountMinor = 12_550, currency = "TRY", type = "EXPENSE",
                categoryId = "cat-1", description = "Haftalık", paymentMethod = "CREDIT_CARD",
                transactionDate = "2026-09-10", createdAt = "2026-09-10T10:00:00Z",
            ),
        ),
    )
}
