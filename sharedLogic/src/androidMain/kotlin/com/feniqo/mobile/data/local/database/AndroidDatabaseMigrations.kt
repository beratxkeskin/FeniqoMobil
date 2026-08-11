package com.feniqo.mobile.data.local.database

import androidx.room.migration.Migration

/** SQLCipher SupportSQLite uyumluluk modunda v1 veritabanına kalıcı outbox ekler. */
val ANDROID_MIGRATION_1_2 = Migration(1, 2) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_operations (
            operation_id TEXT NOT NULL,
            entity_type_code TEXT NOT NULL,
            entity_id TEXT NOT NULL,
            operation_type_code TEXT NOT NULL,
            base_version INTEGER,
            status_code TEXT NOT NULL,
            attempt_count INTEGER NOT NULL,
            last_error TEXT,
            next_attempt_at_epoch_ms INTEGER NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(operation_id)
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sync_operations_status_code_next_attempt_at_epoch_ms_created_at_epoch_ms " +
            "ON sync_operations(status_code, next_attempt_at_epoch_ms, created_at_epoch_ms)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sync_operations_entity_type_code_entity_id " +
            "ON sync_operations(entity_type_code, entity_id)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sync_operations_created_at_epoch_ms " +
            "ON sync_operations(created_at_epoch_ms)",
    )
}
