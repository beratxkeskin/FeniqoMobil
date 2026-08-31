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

/** v3, artımlı pull cursor'larını ve kullanıcı kontrollü çakışma snapshot'larını kalıcılaştırır. */
val ANDROID_MIGRATION_2_3 = Migration(2, 3) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_cursors (
            entity_type_code TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            entity_id TEXT NOT NULL,
            PRIMARY KEY(entity_type_code)
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_conflicts (
            entity_type_code TEXT NOT NULL,
            entity_id TEXT NOT NULL,
            operation_id TEXT NOT NULL,
            local_version INTEGER NOT NULL,
            remote_version INTEGER NOT NULL,
            local_payload_json TEXT NOT NULL,
            remote_payload_json TEXT NOT NULL,
            detected_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(entity_type_code, entity_id)
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sync_conflicts_detected_at_epoch_ms " +
            "ON sync_conflicts(detected_at_epoch_ms)",
    )
}

/** v4, kullanıcı bazında son başarılı senkronizasyon zamanını kalıcılaştırır. */
val ANDROID_MIGRATION_3_4 = Migration(3, 4) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_user_states (
            user_id TEXT NOT NULL,
            last_successful_sync_at_epoch_ms INTEGER,
            updated_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(user_id)
        )
        """.trimIndent(),
    )
}

/** v5, operation-level idempotency için değişmez snapshot, predecessor zinciri ve protocol_version ekler. */
val ANDROID_MIGRATION_4_5 = Migration(4, 5) { database ->
    database.execSQL("ALTER TABLE sync_operations ADD COLUMN payload_json TEXT")
    database.execSQL("ALTER TABLE sync_operations ADD COLUMN predecessor_operation_id TEXT")
    database.execSQL("ALTER TABLE sync_operations ADD COLUMN is_blocked INTEGER NOT NULL DEFAULT 0")
    database.execSQL("ALTER TABLE sync_operations ADD COLUMN protocol_version INTEGER NOT NULL DEFAULT 1")
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sync_operations_predecessor_operation_id " +
            "ON sync_operations(predecessor_operation_id)",
    )
}

/** v6, tekrarlayan işlem kurallarını ve atomik tekrar vadeleri için idempotency tablosunu ekler. */
val ANDROID_MIGRATION_5_6 = Migration(5, 6) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS recurring_transactions (
            id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            workspace_id TEXT,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            type_code TEXT NOT NULL,
            category_id TEXT NOT NULL,
            description TEXT,
            payment_method_code TEXT NOT NULL,
            frequency_code TEXT NOT NULL,
            `interval` INTEGER NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT,
            last_generated_date TEXT,
            is_active INTEGER NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_owner_id ON recurring_transactions(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_workspace_id ON recurring_transactions(workspace_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_category_id ON recurring_transactions(category_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_is_active ON recurring_transactions(is_active)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_start_date ON recurring_transactions(start_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_deleted_at_epoch_ms ON recurring_transactions(deleted_at_epoch_ms)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS recurring_transaction_occurrences (
            recurring_transaction_id TEXT NOT NULL,
            due_date TEXT NOT NULL,
            transaction_id TEXT NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(recurring_transaction_id, due_date),
            FOREIGN KEY(recurring_transaction_id) REFERENCES recurring_transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_transaction_occurrences_transaction_id ON recurring_transaction_occurrences(transaction_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transaction_occurrences_recurring_transaction_id ON recurring_transaction_occurrences(recurring_transaction_id)")
}
