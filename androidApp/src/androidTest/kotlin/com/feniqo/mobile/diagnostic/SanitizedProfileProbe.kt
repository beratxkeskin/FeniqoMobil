package com.feniqo.mobile.diagnostic

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanitizedProfileProbe {

    @Test
    fun executeProbe() {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId")) { "RUN_ID_REQUIRED" }
        require(runId.matches(Regex("^V1ACC-[A-Z0-9-]{8,80}$"))) { "INVALID_RUN_ID" }
        val expectedName = requireNotNull(args.getString("expectedName")) { "EXPECTED_NAME_REQUIRED" }
        require(expectedName.startsWith(runId)) { "EXPECTED_NAME_SCOPE_MISMATCH" }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProbeDatabaseEntryPoint::class.java,
        ).database()
        val readableDb = database.openHelper.readableDatabase

        var profileRowCount = 0
        var profileIdHash: String? = null
        var fullNameMatch = false
        var fullNameHash: String? = null
        var syncStatus: String? = null
        var version: Long? = null
        var baseVersion: Long? = null
        var deletedAtPresent = false
        var profileId: String? = null

        readableDb.query(
            SimpleSQLiteQuery(
                "SELECT id, full_name, sync_status, version, base_version, deleted_at_epoch_ms FROM profiles",
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                profileRowCount++
                if (profileRowCount == 1) {
                    profileId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val fullName = if (cursor.isNull(cursor.getColumnIndexOrThrow("full_name"))) {
                        null
                    } else {
                        cursor.getString(cursor.getColumnIndexOrThrow("full_name"))
                    }
                    profileIdHash = sha256Short(profileId)
                    fullNameMatch = fullName == expectedName
                    fullNameHash = sha256Short(fullName)
                    syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))
                    version = cursor.getLong(cursor.getColumnIndexOrThrow("version"))
                    baseVersion = if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) {
                        null
                    } else {
                        cursor.getLong(cursor.getColumnIndexOrThrow("base_version"))
                    }
                    deletedAtPresent = !cursor.isNull(cursor.getColumnIndexOrThrow("deleted_at_epoch_ms"))
                }
            }
        }

        val operations = JSONArray()
        if (profileId != null) {
            readableDb.query(
                SimpleSQLiteQuery(
                    "SELECT operation_id, operation_type_code, status_code, base_version, attempt_count, is_blocked, payload_json " +
                        "FROM sync_operations WHERE entity_type_code = 'PROFILE' AND entity_id = ? ORDER BY created_at_epoch_ms",
                    arrayOf(profileId),
                ),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    operations.put(
                        JSONObject().apply {
                            put("operationId", cursor.getString(cursor.getColumnIndexOrThrow("operation_id")))
                            put("operationType", cursor.getString(cursor.getColumnIndexOrThrow("operation_type_code")))
                            put("status", cursor.getString(cursor.getColumnIndexOrThrow("status_code")))
                            put(
                                "baseVersion",
                                if (cursor.isNull(cursor.getColumnIndexOrThrow("base_version"))) JSONObject.NULL
                                else cursor.getLong(cursor.getColumnIndexOrThrow("base_version")),
                            )
                            put("attemptCount", cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count")))
                            put("isBlocked", cursor.getInt(cursor.getColumnIndexOrThrow("is_blocked")) != 0)
                            val payload = if (cursor.isNull(cursor.getColumnIndexOrThrow("payload_json"))) null
                            else cursor.getString(cursor.getColumnIndexOrThrow("payload_json"))
                            put("payloadSha256Short", sha256Short(payload) ?: JSONObject.NULL)
                            put("payloadContainsProfileId", payload?.contains(profileId) == true)
                        },
                    )
                }
            }
        }

        var totalPendingOperationCount = 0
        readableDb.query(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM sync_operations WHERE status_code IN ('PENDING', 'IN_FLIGHT')"),
        ).use { cursor ->
            if (cursor.moveToFirst()) totalPendingOperationCount = cursor.getInt(0)
        }

        var totalConflictCount = 0
        readableDb.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM sync_conflicts")).use { cursor ->
            if (cursor.moveToFirst()) totalConflictCount = cursor.getInt(0)
        }

        var profileCursorCount = 0
        val profileCursorParts = mutableListOf<String>()
        readableDb.query(
            SimpleSQLiteQuery(
                "SELECT entity_id, updated_at_epoch_ms FROM sync_cursors WHERE entity_type_code = 'PROFILE' ORDER BY entity_id",
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                profileCursorCount++
                profileCursorParts += "${cursor.getString(0)}|${cursor.getLong(1)}"
            }
        }

        val result = JSONObject().apply {
            put("runId", runId)
            put("profileRowCount", profileRowCount)
            put("profileIdSha256Short", profileIdHash ?: JSONObject.NULL)
            put("fullNameMatch", fullNameMatch)
            put("fullNameSha256Short", fullNameHash ?: JSONObject.NULL)
            put("syncStatus", syncStatus ?: JSONObject.NULL)
            put("version", version ?: JSONObject.NULL)
            put("baseVersion", baseVersion ?: JSONObject.NULL)
            put("deletedAtPresent", deletedAtPresent)
            put("profileOperationCount", operations.length())
            put("operations", operations)
            put("totalPendingOperationCount", totalPendingOperationCount)
            put("totalConflictCount", totalConflictCount)
            put("profileCursorCount", profileCursorCount)
            put("profileCursorSha256Short", sha256Short(profileCursorParts.joinToString("\n")) ?: JSONObject.NULL)
        }

        val line = "V1ACC_PROFILE_PROBE_JSON=$result"
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
        private const val TAG = "SanitizedProfileProbe"
    }
}
