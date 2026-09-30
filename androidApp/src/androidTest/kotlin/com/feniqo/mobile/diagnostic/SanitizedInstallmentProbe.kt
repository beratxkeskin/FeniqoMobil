package com.feniqo.mobile.diagnostic

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanitizedInstallmentProbe {

    @Test
    fun executeProbe() {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId")) { "RUN_ID_REQUIRED" }
        require(runId.matches(Regex("^V1ACC-[A-Z0-9-]{8,80}$"))) { "INVALID_RUN_ID" }
        val expectedTitle = requireNotNull(args.getString("expectedTitle")) { "EXPECTED_TITLE_REQUIRED" }
        require(expectedTitle.startsWith(runId)) { "EXPECTED_TITLE_SCOPE_MISMATCH" }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProbeDatabaseEntryPoint::class.java,
        ).database()
        val readableDb = database.openHelper.readableDatabase

        val rows = JSONArray()
        val entityIds = mutableListOf<String>()
        val groupIds = mutableSetOf<String>()
        var totalAmountMinor = 0L
        var activeCount = 0
        var tombstoneCount = 0

        readableDb.query(
            SimpleSQLiteQuery(
                "SELECT id, amount_minor, currency_code, transaction_date, installment_number, " +
                    "total_installments, installment_group_id, sync_status, version, base_version, " +
                    "deleted_at_epoch_ms FROM transactions WHERE description = ? ORDER BY installment_number",
                arrayOf(expectedTitle),
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val groupId = cursor.getString(cursor.getColumnIndexOrThrow("installment_group_id"))
                val deleted = !cursor.isNull(cursor.getColumnIndexOrThrow("deleted_at_epoch_ms"))
                val amountMinor = cursor.getLong(cursor.getColumnIndexOrThrow("amount_minor"))
                entityIds += id
                groupIds += groupId
                totalAmountMinor += amountMinor
                if (deleted) tombstoneCount++ else activeCount++
                rows.put(
                    JSONObject().apply {
                        put("entityId", id)
                        put("amountMinor", amountMinor)
                        put("currency", cursor.getString(cursor.getColumnIndexOrThrow("currency_code")))
                        put("transactionDate", cursor.getString(cursor.getColumnIndexOrThrow("transaction_date")))
                        put("installmentNumber", cursor.getInt(cursor.getColumnIndexOrThrow("installment_number")))
                        put("totalInstallments", cursor.getInt(cursor.getColumnIndexOrThrow("total_installments")))
                        put("groupId", groupId)
                        put("syncStatus", cursor.getString(cursor.getColumnIndexOrThrow("sync_status")))
                        put("version", cursor.getLong(cursor.getColumnIndexOrThrow("version")))
                        put(
                            "baseVersion",
                            if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) JSONObject.NULL
                            else cursor.getLong(cursor.getColumnIndexOrThrow("base_version")),
                        )
                        put("deletedAtPresent", deleted)
                    },
                )
            }
        }

        val operations = JSONArray()
        if (entityIds.isNotEmpty()) {
            val placeholders = entityIds.joinToString(",") { "?" }
            readableDb.query(
                SimpleSQLiteQuery(
                    "SELECT operation_id, entity_id, operation_type_code, status_code, base_version, " +
                        "attempt_count, is_blocked, payload_json FROM sync_operations " +
                        "WHERE entity_type_code = 'TRANSACTION' AND entity_id IN ($placeholders) " +
                        "ORDER BY entity_id, created_at_epoch_ms",
                    entityIds.toTypedArray(),
                ),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id"))
                    val payload = if (cursor.isNull(cursor.getColumnIndexOrThrow("payload_json"))) null
                    else cursor.getString(cursor.getColumnIndexOrThrow("payload_json"))
                    operations.put(
                        JSONObject().apply {
                            put("operationId", cursor.getString(cursor.getColumnIndexOrThrow("operation_id")))
                            put("entityId", entityId)
                            put("operationType", cursor.getString(cursor.getColumnIndexOrThrow("operation_type_code")))
                            put("status", cursor.getString(cursor.getColumnIndexOrThrow("status_code")))
                            put(
                                "baseVersion",
                                if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) JSONObject.NULL
                                else cursor.getLong(cursor.getColumnIndexOrThrow("base_version")),
                            )
                            put("attemptCount", cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count")))
                            put("isBlocked", cursor.getInt(cursor.getColumnIndexOrThrow("is_blocked")) != 0)
                            put("payloadSha256Short", sha256Short(payload) ?: JSONObject.NULL)
                            put("payloadContainsEntityId", payload?.contains(entityId) == true)
                        },
                    )
                }
            }
        }

        val result = JSONObject().apply {
            put("runId", runId)
            put("titleSha256Short", sha256Short(expectedTitle))
            put("physicalRowCount", rows.length())
            put("activeCount", activeCount)
            put("tombstoneCount", tombstoneCount)
            put("distinctGroupCount", groupIds.size)
            put("totalAmountMinor", totalAmountMinor)
            put("rows", rows)
            put("operationCount", operations.length())
            put("operations", operations)
        }
        val line = "V1ACC_INSTALLMENT_PROBE_JSON=$result"
        println(line)
        Log.i(TAG, line)
    }

    private fun sha256Short(value: String?): String? {
        if (value == null) return null
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }

    companion object {
        private const val TAG = "SanitizedInstallmentProbe"
    }
}
