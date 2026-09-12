package com.feniqo.mobile.data.backup

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val FENIQO_BACKUP_FORMAT_VERSION = 1
const val FENIQO_BACKUP_MAX_BYTES = 10 * 1024 * 1024

@Serializable
data class FeniqoBackupV1(
    @SerialName("format_version") val formatVersion: Int = FENIQO_BACKUP_FORMAT_VERSION,
    @SerialName("created_at") val createdAt: String,
    val scope: String = "PERSONAL",
    val categories: List<BackupCategoryV1>,
    val transactions: List<BackupTransactionV1>,
)

@Serializable
data class BackupCategoryV1(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String,
)

@Serializable
data class BackupTransactionV1(
    val id: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val type: String,
    @SerialName("category_id") val categoryId: String,
    val description: String? = null,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("created_at") val createdAt: String,
    val installment: BackupInstallmentV1? = null,
    val note: String? = null,
)

@Serializable
data class BackupInstallmentV1(
    @SerialName("group_id") val groupId: String,
    val number: Int,
    val total: Int,
)

sealed interface BackupDecodeResult {
    data class Valid(val backup: FeniqoBackupV1) : BackupDecodeResult
    data class Invalid(val reason: String) : BackupDecodeResult
}

/** Strict version and validation boundary used before any future Room write transaction. */
object FeniqoBackupCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }

    fun encode(backup: FeniqoBackupV1): String {
        require(validate(backup) == null) { "Geçersiz yedek üretilemez." }
        return json.encodeToString(backup)
    }

    fun decode(raw: String): BackupDecodeResult {
        if (raw.encodeToByteArray().size > FENIQO_BACKUP_MAX_BYTES) return invalid("backup_too_large")
        if (raw.isBlank()) return invalid("backup_invalid_json")
        return try {
            val version = json.parseToJsonElement(raw).jsonObject["format_version"]
                ?.jsonPrimitive?.content?.toIntOrNull() ?: return invalid("backup_version_missing")
            if (version != FENIQO_BACKUP_FORMAT_VERSION) return invalid("backup_version_unsupported")
            val backup = json.decodeFromString<FeniqoBackupV1>(raw)
            validate(backup)?.let(::invalid) ?: BackupDecodeResult.Valid(backup)
        } catch (_: Exception) {
            invalid("backup_invalid_json")
        }
    }

    private fun invalid(reason: String) = BackupDecodeResult.Invalid(reason)

    private fun validate(backup: FeniqoBackupV1): String? {
        if (backup.formatVersion != 1) return "backup_version_unsupported"
        if (backup.scope != "PERSONAL") return "backup_scope_unsupported"
        if (runCatching { Instant.parse(backup.createdAt) }.isFailure) return "backup_created_at_invalid"
        if (backup.categories.size > 5_000 || backup.transactions.size > 100_000) return "backup_record_limit_exceeded"
        if (backup.categories.map { it.id }.distinct().size != backup.categories.size) return "backup_duplicate_category"
        if (backup.transactions.map { it.id }.distinct().size != backup.transactions.size) return "backup_duplicate_transaction"
        val categoryIds = backup.categories.mapTo(mutableSetOf()) { it.id }
        backup.categories.forEach {
            if (!validId(it.id) || it.name.isBlank() || it.name.length > 100 ||
                it.type !in TYPES || it.color.isBlank() || it.icon.isBlank()
            ) return "backup_category_invalid"
        }
        backup.transactions.forEach {
            if (!validId(it.id) || it.amountMinor !in 1..922_337_203_685_477L ||
                it.currency !in CURRENCIES || it.type !in TYPES || it.categoryId !in categoryIds ||
                it.paymentMethod !in PAYMENT_METHODS ||
                (it.description != null && (it.description.isBlank() || it.description.length > 500)) ||
                (it.note != null && (it.note.isBlank() || it.note.length > 500)) ||
                runCatching { LocalDate.parse(it.transactionDate) }.isFailure ||
                runCatching { Instant.parse(it.createdAt) }.isFailure
            ) return "backup_transaction_invalid"
            it.installment?.let { installment ->
                if (!validId(installment.groupId) || installment.total !in 2..60 || installment.number !in 1..installment.total) {
                    return "backup_installment_invalid"
                }
            }
        }
        return null
    }

    private fun validId(value: String) = value.isNotBlank() && value.length <= 128 && value == value.trim()
    private val TYPES = setOf("INCOME", "EXPENSE")
    private val CURRENCIES = setOf("TRY", "USD", "EUR")
    private val PAYMENT_METHODS = setOf("CASH", "CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "OTHER")
}
