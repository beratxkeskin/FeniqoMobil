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

/** v7, abonelikleri (subscriptions) ekler. */
val ANDROID_MIGRATION_6_7 = Migration(6, 7) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS subscriptions (
            id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            workspace_id TEXT,
            name TEXT NOT NULL,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            category_id TEXT,
            frequency_code TEXT NOT NULL,
            `interval` INTEGER NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT,
            next_renewal_date TEXT NOT NULL,
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
            FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL,
            FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_owner_id ON subscriptions(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_workspace_id ON subscriptions(workspace_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_category_id ON subscriptions(category_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_is_active ON subscriptions(is_active)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_next_renewal_date ON subscriptions(next_renewal_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_deleted_at_epoch_ms ON subscriptions(deleted_at_epoch_ms)")
}

/** v8, abonelik ödeme hatırlatıcıları için yerel idempotency kayıtlarını (receipts) ekler. */
val ANDROID_MIGRATION_7_8 = Migration(7, 8) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS subscription_payment_reminder_receipts (
            stable_key TEXT NOT NULL,
            subscription_id TEXT NOT NULL,
            next_renewal_date TEXT NOT NULL,
            reminder_kind TEXT NOT NULL,
            claimed_at_epoch_millis INTEGER NOT NULL,
            PRIMARY KEY(stable_key)
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS index_subscription_payment_reminder_receipts_subscription_id " +
            "ON subscription_payment_reminder_receipts(subscription_id)",
    )
}

/** v9, birikim hedefleri (goals, goal_contributions) ve borç/alacakları (debts, debt_payments) ekler. */
val ANDROID_MIGRATION_8_9 = Migration(8, 9) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS goals (
            id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            workspace_id TEXT,
            name TEXT NOT NULL,
            target_amount_minor INTEGER NOT NULL,
            current_amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            target_date TEXT NOT NULL,
            color_hex TEXT NOT NULL,
            icon_key TEXT,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goals_owner_id ON goals(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goals_workspace_id ON goals(workspace_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goals_target_date ON goals(target_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goals_deleted_at_epoch_ms ON goals(deleted_at_epoch_ms)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS goal_contributions (
            id TEXT NOT NULL,
            goal_id TEXT NOT NULL,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            direction_code TEXT NOT NULL,
            occurred_on TEXT NOT NULL,
            note TEXT,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(goal_id) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_goal_id ON goal_contributions(goal_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_occurred_on ON goal_contributions(occurred_on)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_deleted_at_epoch_ms ON goal_contributions(deleted_at_epoch_ms)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS debts (
            id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            workspace_id TEXT,
            title TEXT NOT NULL,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            type_code TEXT NOT NULL,
            due_date TEXT NOT NULL,
            status_code TEXT NOT NULL,
            description TEXT,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_owner_id ON debts(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_workspace_id ON debts(workspace_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_type_code ON debts(type_code)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_status_code ON debts(status_code)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_due_date ON debts(due_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debts_deleted_at_epoch_ms ON debts(deleted_at_epoch_ms)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS debt_payments (
            id TEXT NOT NULL,
            debt_id TEXT NOT NULL,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            paid_on TEXT NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(debt_id) REFERENCES debts(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_debt_id ON debt_payments(debt_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_paid_on ON debt_payments(paid_on)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_deleted_at_epoch_ms ON debt_payments(deleted_at_epoch_ms)")
}

/** v10, workspaces tablosunu type/currency/description alanlarıyla genişletir ve workspace_invitations tablosunu ekler. */
val ANDROID_MIGRATION_9_10 = Migration(9, 10) { database ->
    database.execSQL("ALTER TABLE workspaces ADD COLUMN type_code TEXT NOT NULL DEFAULT 'personal'")
    database.execSQL("ALTER TABLE workspaces ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'TRY'")
    database.execSQL("ALTER TABLE workspaces ADD COLUMN description TEXT")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS workspace_invitations (
            id TEXT NOT NULL,
            workspace_id TEXT NOT NULL,
            inviter_id TEXT NOT NULL,
            role_code TEXT NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            expires_at_epoch_ms INTEGER NOT NULL,
            max_uses INTEGER NOT NULL,
            uses_count INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_invitations_workspace_id ON workspace_invitations(workspace_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_invitations_expires_at_epoch_ms ON workspace_invitations(expires_at_epoch_ms)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_invitations_deleted_at_epoch_ms ON workspace_invitations(deleted_at_epoch_ms)")
}

/** v11, workspace_invitations tablosuna token_hash alanını ve indeksini ekler. */
val ANDROID_MIGRATION_10_11 = Migration(10, 11) { database ->
    database.execSQL("ALTER TABLE workspace_invitations ADD COLUMN token_hash TEXT")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_invitations_token_hash ON workspace_invitations(token_hash)")
}

/** v12, ortak gider ödeşmesi için ödeme yapan ve katılımcı snapshot'ını transaction ile saklar. */
val ANDROID_MIGRATION_11_12 = Migration(11, 12) { database ->
    database.execSQL("ALTER TABLE transactions ADD COLUMN paid_by_user_id TEXT")
    database.execSQL("ALTER TABLE transactions ADD COLUMN participant_user_ids_json TEXT")
    database.execSQL("UPDATE transactions SET paid_by_user_id = owner_id WHERE paid_by_user_id IS NULL")
    database.execSQL(
        "UPDATE transactions SET participant_user_ids_json = '[\"' || owner_id || '\"]' " +
            "WHERE participant_user_ids_json IS NULL",
    )
}
