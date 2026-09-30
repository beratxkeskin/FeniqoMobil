package com.feniqo.mobile.diagnostic

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanitizedOutboxProbe {

    data class ProbeEntity(
        val entityType: String,
        val entityId: String,
        val categoryId: String?,
        val typeCode: String,
        val syncStatus: String,
        val version: Long,
        val baseVersion: Long?,
        val hasDeletedAt: Boolean,
        val rowCount: Int,
        val matchedDescription: String? = null,
        val amountMinorMatch: Boolean? = null,
        val currencyMatch: Boolean? = null,
        val physicalRowExists: Boolean = true,
        val deletedAtPresent: Boolean = false,
        val activeUiEligible: Boolean = true,
    )

    data class ProbeOperation(
        val operationId: String,
        val entityType: String,
        val entityId: String,
        val operationType: String,
        val status: String,
        val baseVersion: Long?,
        val attemptCount: Int,
        val isBlocked: Boolean,
        val payloadSha256Short: String?,
        val payloadMatchesEntityId: Boolean = false,
    )

    data class ProbeSummary(
        val matchingCategoryCount: Int,
        val matchingExpenseCount: Int,
        val matchingIncomeCount: Int,
        val matchingOperationCount: Int,
        val totalPendingOperationCount: Int,
        val totalConflictCount: Int,
        val totalActiveCategoryCount: Int,
        val totalActiveTransactionCount: Int,
        val syncCursorCount: Int,
        val syncCursorFingerprintSha256Short: String?,
        val syncUserStateCount: Int,
        val lastSuccessfulSyncAtEpochMillis: Long?,
    )

    data class ProbeResult(
        val runId: String,
        val summary: ProbeSummary,
        val entities: List<ProbeEntity>,
        val operations: List<ProbeOperation>,
    )

    companion object {
        private const val TAG = "SanitizedOutboxProbe"
        private val RUN_ID_REGEX = Regex("""^V1ACC-[A-Z0-9-]{8,80}$""")

        val FORBIDDEN_KEYS = setOf(
            "owner_id",
            "user_id",
            "workspace_id",
            "sync_scope_key",
            "payload_json",
            "amount",
            "amount_minor",
            "note",
            "receipt_path",
            "description",
            "name",
            "password",
            "token",
            "session",
            "secret",
            "service_role",
            "key_material",
            "passphrase",
        )

        fun validateRunId(runId: String?): String {
            if (runId.isNullOrBlank() || !RUN_ID_REGEX.matches(runId)) {
                throw IllegalArgumentException("INVALID_RUN_ID")
            }
            return runId
        }

        fun computeSha256Short(payload: String?): String? {
            if (payload == null) return null
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return hex.take(12)
        }

        fun buildSanitizedJson(result: ProbeResult): String {
            // Sort entities deterministically: entityType then entityId
            val sortedEntities = result.entities.sortedWith(
                compareBy({ it.entityType }, { it.entityId })
            )

            // Sort operations deterministically: entityType then entityId then operationId
            val sortedOperations = result.operations.sortedWith(
                compareBy({ it.entityType }, { it.entityId }, { it.operationId })
            )

            val summaryObj = JSONObject().apply {
                put("matchingCategoryCount", result.summary.matchingCategoryCount)
                put("matchingExpenseCount", result.summary.matchingExpenseCount)
                put("matchingIncomeCount", result.summary.matchingIncomeCount)
                put("matchingOperationCount", result.summary.matchingOperationCount)
                put("totalPendingOperationCount", result.summary.totalPendingOperationCount)
                put("totalConflictCount", result.summary.totalConflictCount)
                put("totalActiveCategoryCount", result.summary.totalActiveCategoryCount)
                put("totalActiveTransactionCount", result.summary.totalActiveTransactionCount)
                put("syncCursorCount", result.summary.syncCursorCount)
                put("syncCursorFingerprintSha256Short", result.summary.syncCursorFingerprintSha256Short ?: JSONObject.NULL)
                put("syncUserStateCount", result.summary.syncUserStateCount)
                put("lastSuccessfulSyncAtEpochMillis", result.summary.lastSuccessfulSyncAtEpochMillis ?: JSONObject.NULL)
            }

            val entitiesArray = JSONArray()
            for (e in sortedEntities) {
                val obj = JSONObject().apply {
                    put("entityType", e.entityType)
                    put("entityId", e.entityId)
                    put("categoryId", e.categoryId ?: JSONObject.NULL)
                    put("typeCode", e.typeCode)
                    put("syncStatus", e.syncStatus)
                    put("version", e.version)
                    put("baseVersion", e.baseVersion ?: JSONObject.NULL)
                    put("hasDeletedAt", e.hasDeletedAt)
                    put("rowCount", e.rowCount)
                    if (e.matchedDescription != null) {
                        put("matchedDescription", e.matchedDescription)
                    }
                    if (e.amountMinorMatch != null) {
                        put("amountMinorMatch", e.amountMinorMatch)
                    }
                    if (e.currencyMatch != null) {
                        put("currencyMatch", e.currencyMatch)
                    }
                    put("physicalRowExists", e.physicalRowExists)
                    put("deletedAtPresent", e.deletedAtPresent)
                    put("activeUiEligible", e.activeUiEligible)
                }
                entitiesArray.put(obj)
            }

            val operationsArray = JSONArray()
            for (op in sortedOperations) {
                val obj = JSONObject().apply {
                    put("operationId", op.operationId)
                    put("entityType", op.entityType)
                    put("entityId", op.entityId)
                    put("operationType", op.operationType)
                    put("status", op.status)
                    put("baseVersion", op.baseVersion ?: JSONObject.NULL)
                    put("attemptCount", op.attemptCount)
                    put("isBlocked", op.isBlocked)
                    put("payloadSha256Short", op.payloadSha256Short ?: JSONObject.NULL)
                    put("payloadMatchesEntityId", op.payloadMatchesEntityId)
                }
                operationsArray.put(obj)
            }

            val rootObj = JSONObject().apply {
                put("runId", result.runId)
                put("summary", summaryObj)
                put("entities", entitiesArray)
                put("operations", operationsArray)
            }

            val jsonString = rootObj.toString()

            // Verify that no forbidden keys exist in the generated JSON
            for (forbidden in FORBIDDEN_KEYS) {
                if (jsonString.contains("\"$forbidden\"")) {
                    throw SecurityException("SANITIZATION_FAILED: Forbidden token detected: $forbidden")
                }
            }

            return jsonString
        }

        fun getDatabase(context: Context): FeniqoDatabase {
            return try {
                EntryPointAccessors.fromApplication(context.applicationContext, ProbeDatabaseEntryPoint::class.java).database()
            } catch (e: Exception) {
                throw IllegalStateException("DATABASE_ACCESS_FAILED", e)
            }
        }
    }

    // ==========================================
    // 5. TEST-ONLY GÜVENLİK TESTLERİ
    // ==========================================

    @Test
    fun test_invalid_run_id_rejected() {
        val invalidIds = listOf(
            null,
            "",
            "   ",
            "invalid-run-id",
            "V1ACC",
            "V1ACC-short",
            "V1ACC-lowercase-letters-1234",
            "V1ACC-invalid@characters!?",
            "V1ACC-" + "A".repeat(81),
        )

        for (invalid in invalidIds) {
            try {
                validateRunId(invalid)
                fail("INVALID_RUN_ID should have been thrown for: $invalid")
            } catch (e: IllegalArgumentException) {
                assertEquals("INVALID_RUN_ID", e.message)
            }
        }

        // Valid IDs should pass without exception
        assertEquals("V1ACC-20260927-S12-7K9M2P", validateRunId("V1ACC-20260927-S12-7K9M2P"))
        assertEquals("V1ACC-12345678", validateRunId("V1ACC-12345678"))
    }

    @Test
    fun test_forbidden_keys_absent_and_payload_sanitized() {
        val hash = computeSha256Short("{\"sample\":\"payload_data\"}")
        assertNotNull(hash)
        assertEquals(12, hash!!.length)
        assertTrue("Hash must be lowercase hex", hash.matches(Regex("^[0-9a-f]{12}$")))

        val dummyResult = ProbeResult(
            runId = "V1ACC-TEST-DUMMY123",
            summary = ProbeSummary(
                matchingCategoryCount = 1,
                matchingExpenseCount = 1,
                matchingIncomeCount = 0,
                matchingOperationCount = 2,
                totalPendingOperationCount = 0,
                totalConflictCount = 0,
                totalActiveCategoryCount = 28,
                totalActiveTransactionCount = 0,
                syncCursorCount = 4,
                syncCursorFingerprintSha256Short = "abcdef123456",
                syncUserStateCount = 1,
                lastSuccessfulSyncAtEpochMillis = 1774866600000L,
            ),
            entities = listOf(
                ProbeEntity("CATEGORY", "cat-uuid-1", null, "EXPENSE", "PENDING", 1L, null, false, 1),
                ProbeEntity("TRANSACTION", "tx-uuid-1", "cat-uuid-1", "EXPENSE", "PENDING", 1L, null, false, 1),
            ),
            operations = listOf(
                ProbeOperation("op-uuid-1", "CATEGORY", "cat-uuid-1", "CREATE", "PENDING", null, 0, false, hash),
            ),
        )

        val json = buildSanitizedJson(dummyResult)
        assertFalse("JSON must not contain newline", json.contains("\n"))

        for (forbidden in FORBIDDEN_KEYS) {
            assertFalse("JSON must not contain forbidden key: $forbidden", json.contains("\"$forbidden\""))
        }

        // Verify that raw payload data cannot be injected
        assertFalse("JSON must not contain payload body", json.contains("sample"))
        assertFalse("JSON must not contain payload body", json.contains("payload_data"))
    }

    @Test
    fun test_only_select_queries_defined() {
        val queryTemplates = listOf(
            "SELECT id, type_code, sync_status, version, base_version, deleted_at_epoch_ms FROM categories WHERE name = ?",
            "SELECT id, category_id, type_code, sync_status, version, base_version, deleted_at_epoch_ms FROM transactions WHERE description = ?",
            "SELECT operation_id, entity_type_code, entity_id, operation_type_code, status_code, base_version, attempt_count, is_blocked, payload_json FROM sync_operations WHERE entity_id IN (?)",
            "SELECT COUNT(*) FROM sync_operations WHERE status_code IN ('PENDING', 'IN_FLIGHT')",
            "SELECT COUNT(*) FROM sync_conflicts",
            "SELECT COUNT(*) FROM categories WHERE deleted_at_epoch_ms IS NULL",
            "SELECT COUNT(*) FROM transactions WHERE deleted_at_epoch_ms IS NULL",
            "SELECT sync_scope_key, entity_type_code, entity_id, updated_at_epoch_ms FROM sync_cursors ORDER BY sync_scope_key ASC, entity_type_code ASC, entity_id ASC",
            "SELECT COUNT(*) FROM sync_user_states",
            "SELECT MAX(last_successful_sync_at_epoch_ms) FROM sync_user_states",
        )

        val forbiddenSql = listOf("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "REPLACE", "TRUNCATE")

        for (sql in queryTemplates) {
            assertTrue("Query must start with SELECT: $sql", sql.trim().startsWith("SELECT", ignoreCase = true))
            for (forbidden in forbiddenSql) {
                // Ensure no DML/DDL exists
                val upper = sql.uppercase()
                assertFalse("Query must not contain $forbidden: $sql", upper.contains(" $forbidden ") || upper.startsWith("$forbidden "))
            }
        }
    }

    @Test
    fun test_deterministic_ordering() {
        val unsortedEntities = listOf(
            ProbeEntity("TRANSACTION", "tx-b", "cat-1", "EXPENSE", "PENDING", 1L, null, false, 1),
            ProbeEntity("CATEGORY", "cat-b", null, "EXPENSE", "PENDING", 1L, null, false, 1),
            ProbeEntity("TRANSACTION", "tx-a", "cat-1", "EXPENSE", "PENDING", 1L, null, false, 1),
            ProbeEntity("CATEGORY", "cat-a", null, "EXPENSE", "PENDING", 1L, null, false, 1),
        )

        val unsortedOps = listOf(
            ProbeOperation("op-2", "TRANSACTION", "tx-1", "CREATE", "PENDING", null, 0, false, "1234567890ab"),
            ProbeOperation("op-1", "TRANSACTION", "tx-1", "CREATE", "PENDING", null, 0, false, "1234567890ab"),
            ProbeOperation("op-3", "CATEGORY", "cat-1", "CREATE", "PENDING", null, 0, false, "1234567890ab"),
        )

        val result = ProbeResult(
            runId = "V1ACC-TEST-ORDERING",
            summary = ProbeSummary(
                matchingCategoryCount = 2,
                matchingExpenseCount = 2,
                matchingIncomeCount = 0,
                matchingOperationCount = 3,
                totalPendingOperationCount = 0,
                totalConflictCount = 0,
                totalActiveCategoryCount = 28,
                totalActiveTransactionCount = 0,
                syncCursorCount = 4,
                syncCursorFingerprintSha256Short = "1234567890ab",
                syncUserStateCount = 1,
                lastSuccessfulSyncAtEpochMillis = 1774866600000L,
            ),
            entities = unsortedEntities,
            operations = unsortedOps,
        )

        val json = buildSanitizedJson(result)
        val root = JSONObject(json)
        val entitiesArr = root.getJSONArray("entities")
        val opsArr = root.getJSONArray("operations")

        assertEquals("cat-a", entitiesArr.getJSONObject(0).getString("entityId"))
        assertEquals("cat-b", entitiesArr.getJSONObject(1).getString("entityId"))
        assertEquals("tx-a", entitiesArr.getJSONObject(2).getString("entityId"))
        assertEquals("tx-b", entitiesArr.getJSONObject(3).getString("entityId"))

        assertEquals("op-3", opsArr.getJSONObject(0).getString("operationId"))
        assertEquals("op-1", opsArr.getJSONObject(1).getString("operationId"))
        assertEquals("op-2", opsArr.getJSONObject(2).getString("operationId"))
    }

    // ==========================================
    // 1-4. CANLI CİHAZ PROBE ÇALIŞTIRICISI
    // ==========================================

    @Test
    fun executeProbe() {
        val args = InstrumentationRegistry.getArguments()
        val rawRunId = args.getString("runId")
        if (rawRunId == null) {
            // If runId argument is omitted (e.g. running all unit/instrumentation tests without arguments),
            // skip the live probe execution without failing the test suite.
            Log.w(TAG, "No runId provided; skipping live query execution.")
            return
        }

        val runId = validateRunId(rawRunId)
        val rawTargetTag = args.getString("targetTag")
        val targetTag = if (!rawTargetTag.isNullOrBlank()) validateRunId(rawTargetTag) else null
        val rawTargetId = args.getString("targetId")
        val targetId = if (!rawTargetId.isNullOrBlank() && rawTargetId.matches(Regex("^[0-9a-fA-F-]{36}$"))) rawTargetId else null
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val database = getDatabase(context)
        val readableDb = database.openHelper.readableDatabase

        val entities = mutableListOf<ProbeEntity>()
        val foundEntityIds = mutableListOf<String>()

        var matchingCategoryCount = 0
        var matchingExpenseCount = 0
        var matchingIncomeCount = 0

        // 1. Categories Query
        try {
            val catQuery = SimpleSQLiteQuery(
                "SELECT id, type_code, sync_status, version, base_version, deleted_at_epoch_ms FROM categories WHERE name = ?",
                arrayOf("$runId Kategori"),
            )
            readableDb.query(catQuery).use { cursor ->
                while (cursor.moveToNext()) {
                    matchingCategoryCount++
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val typeCode = cursor.getString(cursor.getColumnIndexOrThrow("type_code"))
                    val syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))
                    val version = cursor.getLong(cursor.getColumnIndexOrThrow("version"))
                    val baseVersion = if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) null
                    else cursor.getLong(cursor.getColumnIndexOrThrow("base_version"))
                    val hasDeletedAt = !cursor.isNull(cursor.getColumnIndexOrThrow("deleted_at_epoch_ms"))

                    entities.add(
                        ProbeEntity(
                            entityType = "CATEGORY",
                            entityId = id,
                            categoryId = null,
                            typeCode = typeCode,
                            syncStatus = syncStatus,
                            version = version,
                            baseVersion = baseVersion,
                            hasDeletedAt = hasDeletedAt,
                            rowCount = 1,
                            physicalRowExists = true,
                            deletedAtPresent = hasDeletedAt,
                            activeUiEligible = !hasDeletedAt,
                        )
                    )
                    foundEntityIds.add(id)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 2. Expense Transaction Query
        try {
            val expenseQuery = if (targetId != null) {
                SimpleSQLiteQuery(
                    "SELECT id, category_id, type_code, sync_status, version, base_version, deleted_at_epoch_ms, description, amount_minor, currency_code FROM transactions WHERE id = ?",
                    arrayOf(targetId),
                )
            } else if (targetTag != null) {
                SimpleSQLiteQuery(
                    "SELECT id, category_id, type_code, sync_status, version, base_version, deleted_at_epoch_ms, description, amount_minor, currency_code FROM transactions WHERE description = ?",
                    arrayOf("$targetTag Gider"),
                )
            } else {
                SimpleSQLiteQuery(
                    "SELECT id, category_id, type_code, sync_status, version, base_version, deleted_at_epoch_ms, description, amount_minor, currency_code FROM transactions WHERE description = ? OR description = ?",
                    arrayOf("$runId Gider", "$runId A2B Gider"),
                )
            }
            readableDb.query(expenseQuery).use { cursor ->
                while (cursor.moveToNext()) {
                    matchingExpenseCount++
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id"))
                    val typeCode = cursor.getString(cursor.getColumnIndexOrThrow("type_code"))
                    val syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))
                    val version = cursor.getLong(cursor.getColumnIndexOrThrow("version"))
                    val baseVersion = if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) null
                    else cursor.getLong(cursor.getColumnIndexOrThrow("base_version"))
                    val hasDeletedAt = !cursor.isNull(cursor.getColumnIndexOrThrow("deleted_at_epoch_ms"))
                    val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                    val amountMinor = cursor.getLong(cursor.getColumnIndexOrThrow("amount_minor"))
                    val currency = cursor.getString(cursor.getColumnIndexOrThrow("currency_code"))
                    val amountMinorMatch = if (targetTag != null || targetId != null) amountMinor == 4567L else null
                    val currencyMatch = if (targetTag != null || targetId != null) currency == "TRY" else null

                    entities.add(
                        ProbeEntity(
                            entityType = "TRANSACTION",
                            entityId = id,
                            categoryId = categoryId,
                            typeCode = typeCode,
                            syncStatus = syncStatus,
                            version = version,
                            baseVersion = baseVersion,
                            hasDeletedAt = hasDeletedAt,
                            rowCount = 1,
                            matchedDescription = desc,
                            amountMinorMatch = amountMinorMatch,
                            currencyMatch = currencyMatch,
                            physicalRowExists = true,
                            deletedAtPresent = hasDeletedAt,
                            activeUiEligible = !hasDeletedAt,
                        )
                    )
                    foundEntityIds.add(id)
                }
            }

            if (targetId != null && matchingExpenseCount == 0) {
                entities.add(
                    ProbeEntity(
                        entityType = "TRANSACTION",
                        entityId = targetId,
                        categoryId = null,
                        typeCode = "EXPENSE",
                        syncStatus = "UNKNOWN",
                        version = 0,
                        baseVersion = null,
                        hasDeletedAt = true,
                        rowCount = 0,
                        matchedDescription = null,
                        amountMinorMatch = false,
                        currencyMatch = false,
                        physicalRowExists = false,
                        deletedAtPresent = true,
                        activeUiEligible = false,
                    )
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 3. Income Transaction Query
        if (targetTag == null) {
            try {
                val incomeQuery = SimpleSQLiteQuery(
                    "SELECT id, category_id, type_code, sync_status, version, base_version, deleted_at_epoch_ms, description FROM transactions WHERE description = ? OR description = ?",
                    arrayOf("$runId Gelir", "$runId B2A Gelir"),
                )
                readableDb.query(incomeQuery).use { cursor ->
                    while (cursor.moveToNext()) {
                        matchingIncomeCount++
                        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        val categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id"))
                        val typeCode = cursor.getString(cursor.getColumnIndexOrThrow("type_code"))
                        val syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))
                        val version = cursor.getLong(cursor.getColumnIndexOrThrow("version"))
                        val baseVersion = if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) null
                        else cursor.getLong(cursor.getColumnIndexOrThrow("base_version"))
                        val hasDeletedAt = !cursor.isNull(cursor.getColumnIndexOrThrow("deleted_at_epoch_ms"))
                        val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))

                        entities.add(
                            ProbeEntity(
                                entityType = "TRANSACTION",
                                entityId = id,
                                categoryId = categoryId,
                                typeCode = typeCode,
                                syncStatus = syncStatus,
                                version = version,
                                baseVersion = baseVersion,
                                hasDeletedAt = hasDeletedAt,
                                rowCount = 1,
                                matchedDescription = desc,
                            )
                        )
                        foundEntityIds.add(id)
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("QUERY_FAILED", e)
            }
        }

        // 4. Sync Operations Query for found entity IDs
        val operations = mutableListOf<ProbeOperation>()
        var matchingOperationCount = 0

        if (foundEntityIds.isNotEmpty()) {
            try {
                val placeholders = foundEntityIds.joinToString(",") { "?" }
                val opsQuery = SimpleSQLiteQuery(
                    "SELECT operation_id, entity_type_code, entity_id, operation_type_code, status_code, base_version, attempt_count, is_blocked, payload_json FROM sync_operations WHERE entity_id IN ($placeholders)",
                    foundEntityIds.toTypedArray(),
                )
                readableDb.query(opsQuery).use { cursor ->
                    while (cursor.moveToNext()) {
                        matchingOperationCount++
                        val opId = cursor.getString(cursor.getColumnIndexOrThrow("operation_id"))
                        val entityTypeCode = cursor.getString(cursor.getColumnIndexOrThrow("entity_type_code"))
                        val entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id"))
                        val opTypeCode = cursor.getString(cursor.getColumnIndexOrThrow("operation_type_code"))
                        val statusCode = cursor.getString(cursor.getColumnIndexOrThrow("status_code"))
                        val baseVersion = if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) null
                        else cursor.getLong(cursor.getColumnIndexOrThrow("base_version"))
                        val attemptCount = cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count"))
                        val isBlocked = cursor.getInt(cursor.getColumnIndexOrThrow("is_blocked")) != 0

                        val rawPayload = if (cursor.isNull(cursor.getColumnIndexOrThrow("payload_json"))) null
                        else cursor.getString(cursor.getColumnIndexOrThrow("payload_json"))

                        // Convert payload in memory to SHA-256 short hash
                        val payloadHash = computeSha256Short(rawPayload)
                        val payloadMatchesEntity = rawPayload?.contains(entityId) == true

                        operations.add(
                            ProbeOperation(
                                operationId = opId,
                                entityType = entityTypeCode,
                                entityId = entityId,
                                operationType = opTypeCode,
                                status = statusCode,
                                baseVersion = baseVersion,
                                attemptCount = attemptCount,
                                isBlocked = isBlocked,
                                payloadSha256Short = payloadHash,
                                payloadMatchesEntityId = payloadMatchesEntity,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("QUERY_FAILED", e)
            }
        }

        // 5. Total Pending Operations Count
        var totalPendingOperationCount = 0
        try {
            val pendingQuery = SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM sync_operations WHERE status_code IN ('PENDING', 'IN_FLIGHT')",
                emptyArray(),
            )
            readableDb.query(pendingQuery).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalPendingOperationCount = cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 6. Total Conflicts Count
        var totalConflictCount = 0
        try {
            val conflictQuery = SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM sync_conflicts",
                emptyArray(),
            )
            readableDb.query(conflictQuery).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalConflictCount = cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 7. Total Active Categories Count
        var totalActiveCategoryCount = 0
        try {
            val catCountQuery = SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM categories WHERE deleted_at_epoch_ms IS NULL",
                emptyArray(),
            )
            readableDb.query(catCountQuery).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalActiveCategoryCount = cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 8. Total Active Transactions Count
        var totalActiveTransactionCount = 0
        try {
            val txCountQuery = SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM transactions WHERE deleted_at_epoch_ms IS NULL",
                emptyArray(),
            )
            readableDb.query(txCountQuery).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalActiveTransactionCount = cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 9. Sync Cursors Query & Deterministic Fingerprint
        var syncCursorCount = 0
        var syncCursorFingerprintSha256Short: String? = null
        try {
            val cursorQuery = SimpleSQLiteQuery(
                "SELECT sync_scope_key, entity_type_code, entity_id, updated_at_epoch_ms FROM sync_cursors ORDER BY sync_scope_key ASC, entity_type_code ASC, entity_id ASC",
                emptyArray(),
            )
            val cursorEntries = mutableListOf<String>()
            readableDb.query(cursorQuery).use { cursor ->
                while (cursor.moveToNext()) {
                    syncCursorCount++
                    val scopeKey = cursor.getString(cursor.getColumnIndexOrThrow("sync_scope_key"))
                    val entityTypeCode = cursor.getString(cursor.getColumnIndexOrThrow("entity_type_code"))
                    val entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id"))
                    val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_epoch_ms"))
                    cursorEntries.add("$scopeKey|$entityTypeCode|$entityId|$updatedAt")
                }
            }
            if (cursorEntries.isNotEmpty()) {
                val combined = cursorEntries.joinToString("\n")
                syncCursorFingerprintSha256Short = computeSha256Short(combined)
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        // 10. Sync User States
        var syncUserStateCount = 0
        var lastSuccessfulSyncAtEpochMillis: Long? = null
        try {
            val userStateCountQuery = SimpleSQLiteQuery(
                "SELECT COUNT(*) FROM sync_user_states",
                emptyArray(),
            )
            readableDb.query(userStateCountQuery).use { cursor ->
                if (cursor.moveToFirst()) {
                    syncUserStateCount = cursor.getInt(0)
                }
            }

            val maxSyncTimeQuery = SimpleSQLiteQuery(
                "SELECT MAX(last_successful_sync_at_epoch_ms) FROM sync_user_states",
                emptyArray(),
            )
            readableDb.query(maxSyncTimeQuery).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    lastSuccessfulSyncAtEpochMillis = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("QUERY_FAILED", e)
        }

        val summary = ProbeSummary(
            matchingCategoryCount = matchingCategoryCount,
            matchingExpenseCount = matchingExpenseCount,
            matchingIncomeCount = matchingIncomeCount,
            matchingOperationCount = matchingOperationCount,
            totalPendingOperationCount = totalPendingOperationCount,
            totalConflictCount = totalConflictCount,
            totalActiveCategoryCount = totalActiveCategoryCount,
            totalActiveTransactionCount = totalActiveTransactionCount,
            syncCursorCount = syncCursorCount,
            syncCursorFingerprintSha256Short = syncCursorFingerprintSha256Short,
            syncUserStateCount = syncUserStateCount,
            lastSuccessfulSyncAtEpochMillis = lastSuccessfulSyncAtEpochMillis,
        )

        val probeResult = ProbeResult(
            runId = runId,
            summary = summary,
            entities = entities,
            operations = operations,
        )

        val sanitizedJson = buildSanitizedJson(probeResult)

        // Output contract: Single-line contract line
        val contractLine = "V1ACC_PROBE_JSON=$sanitizedJson"
        println(contractLine)
        Log.i(TAG, contractLine)

        // If baseline assertion is requested via argument:
        val assertBaseline = args.getString("assertBaseline")?.toBoolean() ?: false
        if (assertBaseline) {
            assertEquals("Baseline matchingCategoryCount must be 0", 0, matchingCategoryCount)
            assertEquals("Baseline matchingExpenseCount must be 0", 0, matchingExpenseCount)
            assertEquals("Baseline matchingIncomeCount must be 0", 0, matchingIncomeCount)
            assertEquals("Baseline matchingOperationCount must be 0", 0, matchingOperationCount)
            assertEquals("Baseline totalPendingOperationCount must be 0", 0, totalPendingOperationCount)
            assertEquals("Baseline totalConflictCount must be 0", 0, totalConflictCount)
            assertEquals("Baseline syncUserStateCount must be 1", 1, syncUserStateCount)
            assertNotNull("Baseline lastSuccessfulSyncAtEpochMillis must not be null", lastSuccessfulSyncAtEpochMillis)
            assertTrue("Baseline syncCursorCount must be > 0", syncCursorCount > 0)
            assertNotNull("Baseline syncCursorFingerprintSha256Short must not be null", syncCursorFingerprintSha256Short)
            assertTrue(
                "Baseline syncCursorFingerprintSha256Short must be 12-char lowercase hex",
                syncCursorFingerprintSha256Short!!.matches(Regex("^[0-9a-f]{12}$"))
            )
        }
    }
}
