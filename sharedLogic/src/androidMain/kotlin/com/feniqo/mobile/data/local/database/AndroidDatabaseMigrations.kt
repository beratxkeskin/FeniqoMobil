package com.feniqo.mobile.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.feniqo.mobile.data.util.UuidFormat
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** v13, kişisel varlıklar için Room SSOT tablosunu ekler. */
val ANDROID_MIGRATION_12_13 = Migration(12, 13) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS assets (
            id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            name TEXT NOT NULL,
            type_code TEXT NOT NULL,
            current_value_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            quantity_unscaled INTEGER,
            quantity_scale INTEGER,
            purchase_unit_price_minor INTEGER,
            tracking_symbol TEXT,
            auto_track INTEGER NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id)
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_assets_owner_id ON assets(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_assets_type_code ON assets(type_code)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_assets_deleted_at_epoch_ms ON assets(deleted_at_epoch_ms)")
}

/** v14, sağlayıcıdan bağımsız piyasa fiyatı önbelleğini ekler. */
val ANDROID_MIGRATION_13_14 = Migration(13, 14) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS market_prices (
            asset_type_code TEXT NOT NULL,
            symbol TEXT NOT NULL,
            quote_currency_code TEXT NOT NULL,
            price_unscaled INTEGER NOT NULL,
            price_scale INTEGER NOT NULL,
            observed_at_epoch_ms INTEGER NOT NULL,
            fetched_at_epoch_ms INTEGER NOT NULL,
            expires_at_epoch_ms INTEGER NOT NULL,
            source TEXT NOT NULL,
            PRIMARY KEY(asset_type_code, symbol, quote_currency_code)
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_market_prices_expires_at_epoch_ms ON market_prices(expires_at_epoch_ms)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_market_prices_fetched_at_epoch_ms ON market_prices(fetched_at_epoch_ms)")
}

/** v15, transactions tablosuna isteğe bağlı note kolonunu ekler. */
val ANDROID_MIGRATION_14_15 = Migration(14, 15) { database ->
    database.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT")
}

/** v16, abonelik yaşam döngüsü alanlarını, fiyat geçmişi ve ödeme olayları tablolarını ekler. */
val ANDROID_MIGRATION_15_16 = Migration(15, 16) { database ->
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN lifecycle_status TEXT NOT NULL DEFAULT 'ACTIVE'")
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN trial_end_date TEXT")
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN cancellation_date TEXT")
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN access_end_date TEXT")
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN reminder_enabled INTEGER NOT NULL DEFAULT 1")

    database.execSQL("UPDATE subscriptions SET lifecycle_status = 'PAUSED' WHERE is_active = 0")
    database.execSQL("UPDATE subscriptions SET lifecycle_status = 'ACTIVE' WHERE is_active = 1")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_lifecycle_status ON subscriptions(lifecycle_status)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS subscription_price_histories (
            id TEXT NOT NULL,
            subscription_id TEXT NOT NULL,
            old_amount_minor INTEGER NOT NULL,
            new_amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            changed_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscription_price_histories_subscription_id ON subscription_price_histories(subscription_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscription_price_histories_deleted_at_epoch_ms ON subscription_price_histories(deleted_at_epoch_ms)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS subscription_payments (
            id TEXT NOT NULL,
            subscription_id TEXT NOT NULL,
            amount_minor INTEGER NOT NULL,
            currency_code TEXT NOT NULL,
            payment_date TEXT NOT NULL,
            renewal_due_date TEXT NOT NULL,
            source_type TEXT NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            sync_status TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            local_updated_at_epoch_ms INTEGER NOT NULL,
            deleted_at_epoch_ms INTEGER,
            version INTEGER NOT NULL,
            base_version INTEGER,
            last_sync_error TEXT,
            PRIMARY KEY(id),
            FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_subscription_payments_subscription_id_renewal_due_date ON subscription_payments(subscription_id, renewal_due_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscription_payments_subscription_id ON subscription_payments(subscription_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscription_payments_payment_date ON subscription_payments(payment_date)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_subscription_payments_deleted_at_epoch_ms ON subscription_payments(deleted_at_epoch_ms)")
}

/** v17, subscriptions tablosuna isteğe bağlı website_url ve notes kolonlarını ekler. */
val ANDROID_MIGRATION_16_17 = Migration(16, 17) { database ->
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN website_url TEXT")
    database.execSQL("ALTER TABLE subscriptions ADD COLUMN notes TEXT")
}

/** v18, makbuz dosyaları (receipt_files) ve işlem-makbuz bağlantıları (receipt_linkages) tablolarını ekler. */
val ANDROID_MIGRATION_17_18 = Migration(17, 18) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS receipt_files (
            attachment_id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            content_sha256 TEXT NOT NULL,
            file_size_bytes INTEGER NOT NULL,
            mime_type TEXT NOT NULL,
            remote_path TEXT NOT NULL,
            upload_status TEXT NOT NULL,
            verified_remote_exists INTEGER NOT NULL,
            created_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(attachment_id)
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_files_owner_id ON receipt_files(owner_id)")
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_files_attachment_id_owner_id ON receipt_files(attachment_id, owner_id)")
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_files_remote_path ON receipt_files(remote_path)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS receipt_linkages (
            transaction_id TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            active_attachment_id TEXT,
            generation INTEGER NOT NULL,
            session_epoch INTEGER NOT NULL,
            workspace_id TEXT,
            updated_at_epoch_ms INTEGER NOT NULL,
            PRIMARY KEY(transaction_id),
            FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(active_attachment_id, owner_id) REFERENCES receipt_files(attachment_id, owner_id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_linkages_owner_id ON receipt_linkages(owner_id)")
    database.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_linkages_active_attachment_id_owner_id ON receipt_linkages(active_attachment_id, owner_id)")
}

/** v19, transactions tablosuna split_mode ve participant_shares_json kolonlarını ekler. */
val ANDROID_MIGRATION_18_19 = Migration(18, 19) { database ->
    database.execSQL(
        "ALTER TABLE transactions ADD COLUMN split_mode TEXT NOT NULL DEFAULT 'EQUAL'",
    )
    database.execSQL(
        "ALTER TABLE transactions ADD COLUMN participant_shares_json TEXT NOT NULL DEFAULT '[]'",
    )
}

/** v20, sync_operations tablosuna error_classification kolonunu ekler. */
val ANDROID_MIGRATION_19_20 = Migration(19, 20) { database ->
    database.execSQL(
        "ALTER TABLE sync_operations ADD COLUMN error_classification TEXT DEFAULT NULL",
    )
}

private data class ReminderReceiptUpdate(
    val oldKey: String,
    val newKey: String,
    val newSubscriptionId: String,
)

private fun resolveReminderReceiptTransformation(
    oldKey: String,
    subId: String,
): ReminderReceiptUpdate? {
    if (!UuidHelper.isLegacyCompactHex(subId)) {
        return null
    }
    if (!oldKey.startsWith(subId, ignoreCase = true)) {
        throw IllegalStateException(
            "Migration 20->21 failed for subscription_payment_reminder_receipts: " +
                "compact subscription_id '$subId' is not a prefix of stable_key '$oldKey'"
        )
    }
    val canonicalSubId = UuidHelper.compactToCanonicalUuid(subId)
    val newKey = canonicalSubId + oldKey.substring(subId.length)
    return ReminderReceiptUpdate(
        oldKey = oldKey,
        newKey = newKey,
        newSubscriptionId = canonicalSubId,
    )
}

/**
 * v21, legacy 32-hex compact UUID'leri canonical küçük harf 36 karakter UUID formatına dönüştürür.
 * PK/FK grafiği, outbox immutability güvencesi, snapshot path sözleşmeleri ve sync_cursors tam eşleşmesi.
 */
val ANDROID_MIGRATION_20_21 = Migration(20, 21) { database ->
    val uuidSyncEntityAllowlist = setOf(
        "CATEGORY", "TRANSACTION", "BUDGET", "RECURRING_TRANSACTION",
        "SUBSCRIPTION", "ASSET", "GOAL", "GOAL_CONTRIBUTION", "DEBT", "DEBT_PAYMENT",
    )

    // =========================================================================
    // AŞAMA 1 — Preflight Collision (Çakışma) Denetimi (Salt Okunur)
    // =========================================================================
    val entityPkTables = listOf(
        "categories" to "id",
        "transactions" to "id",
        "budgets" to "id",
        "recurring_transactions" to "id",
        "subscriptions" to "id",
        "subscription_price_histories" to "id",
        "subscription_payments" to "id",
        "assets" to "id",
        "goals" to "id",
        "goal_contributions" to "id",
        "debts" to "id",
        "debt_payments" to "id",
        "tags" to "id",
        "receipt_linkages" to "transaction_id",
    )

    for ((table, col) in entityPkTables) {
        val cursor = database.query("SELECT $col FROM $table")
        try {
            while (cursor.moveToNext()) {
                val rawId = cursor.getString(0) ?: continue
                if (UuidHelper.isLegacyCompactHex(rawId)) {
                    val targetCanonical = UuidHelper.compactToCanonicalUuid(rawId)
                    val checkCursor = database.query("SELECT 1 FROM $table WHERE $col = ?", arrayOf(targetCanonical))
                    val collision = checkCursor.use { it.moveToFirst() }
                    if (collision) {
                        throw IllegalStateException("Migration 20->21 collision detected in table $table: canonical ID $targetCanonical already exists")
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }

    // Composite PKs & Affected Unique Indexes Collision Check
    val conflictCursor = database.query("SELECT entity_type_code, entity_id FROM sync_conflicts")
    conflictCursor.use {
        while (it.moveToNext()) {
            val type = it.getString(0)
            val id = it.getString(1)
            if (type in uuidSyncEntityAllowlist && UuidHelper.isLegacyCompactHex(id)) {
                val targetCanonical = UuidHelper.compactToCanonicalUuid(id)
                val checkCursor = database.query(
                    "SELECT 1 FROM sync_conflicts WHERE entity_type_code = ? AND entity_id = ?",
                    arrayOf(type, targetCanonical),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in sync_conflicts: ($type, $targetCanonical) already exists")
                }
            }
        }
    }

    val seenReminderTargetKeys = mutableSetOf<String>()
    val reminderCursor = database.query("SELECT stable_key, subscription_id FROM subscription_payment_reminder_receipts")
    reminderCursor.use {
        while (it.moveToNext()) {
            val oldKey = it.getString(0)
            val subId = it.getString(1)
            val update = resolveReminderReceiptTransformation(oldKey, subId)
            if (update != null) {
                val targetKey = update.newKey
                if (!seenReminderTargetKeys.add(targetKey)) {
                    throw IllegalStateException("Migration 20->21 collision detected in subscription_payment_reminder_receipts: duplicate target stable_key $targetKey")
                }
                val checkCursor = database.query(
                    "SELECT 1 FROM subscription_payment_reminder_receipts WHERE stable_key = ?",
                    arrayOf(targetKey),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in subscription_payment_reminder_receipts: stable_key $targetKey already exists")
                }
            }
        }
    }

    val ttCursor = database.query("SELECT transaction_id, tag_id FROM transaction_tags")
    ttCursor.use {
        while (it.moveToNext()) {
            val txId = it.getString(0)
            val tagId = it.getString(1)
            val targetTxId = UuidHelper.toCanonicalOrNull(txId) ?: txId
            val targetTagId = UuidHelper.toCanonicalOrNull(tagId) ?: tagId
            if (targetTxId != txId || targetTagId != tagId) {
                val checkCursor = database.query(
                    "SELECT 1 FROM transaction_tags WHERE transaction_id = ? AND tag_id = ?",
                    arrayOf(targetTxId, targetTagId),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in transaction_tags: ($targetTxId, $targetTagId) already exists")
                }
            }
        }
    }

    val rtoCursor = database.query("SELECT recurring_transaction_id, due_date, transaction_id FROM recurring_transaction_occurrences")
    rtoCursor.use {
        while (it.moveToNext()) {
            val recId = it.getString(0)
            val dueDate = it.getString(1)
            val txId = it.getString(2)
            val targetRecId = UuidHelper.toCanonicalOrNull(recId) ?: recId
            val targetTxId = txId?.let { t -> UuidHelper.toCanonicalOrNull(t) ?: t }
            if (targetRecId != recId) {
                val checkCursor = database.query(
                    "SELECT 1 FROM recurring_transaction_occurrences WHERE recurring_transaction_id = ? AND due_date = ?",
                    arrayOf(targetRecId, dueDate),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in recurring_transaction_occurrences PK: ($targetRecId, $dueDate) already exists")
                }
            }
            if (targetTxId != null && targetTxId != txId) {
                val checkCursor = database.query(
                    "SELECT 1 FROM recurring_transaction_occurrences WHERE transaction_id = ?",
                    arrayOf(targetTxId),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in recurring_transaction_occurrences unique transaction_id: $targetTxId already exists")
                }
            }
        }
    }

    val spCursor = database.query("SELECT id, subscription_id, renewal_due_date FROM subscription_payments")
    spCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val subId = it.getString(1)
            val renewalDueDate = it.getString(2)
            val targetSubId = UuidHelper.toCanonicalOrNull(subId) ?: subId
            if (targetSubId != subId) {
                val checkCursor = database.query(
                    "SELECT 1 FROM subscription_payments WHERE subscription_id = ? AND renewal_due_date = ? AND id != ?",
                    arrayOf(targetSubId, renewalDueDate, id),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in subscription_payments unique (subscription_id, renewal_due_date): ($targetSubId, $renewalDueDate) already exists")
                }
            }
        }
    }

    val bgtCursor = database.query("SELECT id, scope_key, category_id, month FROM budgets")
    bgtCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val scopeKey = it.getString(1)
            val catId = it.getString(2)
            val month = it.getString(3)
            val targetCatId = UuidHelper.toCanonicalOrNull(catId) ?: catId
            if (targetCatId != catId) {
                val checkCursor = database.query(
                    "SELECT 1 FROM budgets WHERE scope_key = ? AND category_id = ? AND month = ? AND id != ?",
                    arrayOf(scopeKey, targetCatId, month, id),
                )
                if (checkCursor.use { c -> c.moveToFirst() }) {
                    throw IllegalStateException("Migration 20->21 collision detected in budgets unique (scope_key, category_id, month): ($scopeKey, $targetCatId, $month) already exists")
                }
            }
        }
    }

    // =========================================================================
    // AŞAMA 2 — Dönüşüm ve Bütünlük Doğrulaması
    // =========================================================================
    database.execSQL("PRAGMA defer_foreign_keys = ON;")

    // 1. categories
    val catCursor = database.query("SELECT id FROM categories")
    val catUpdates = mutableListOf<Pair<String, String>>()
    catCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(id)) {
                catUpdates.add(id to UuidHelper.compactToCanonicalUuid(id))
            }
        }
    }
    for ((oldId, newId) in catUpdates) {
        database.execSQL("UPDATE categories SET id = ? WHERE id = ?", arrayOf(newId, oldId))
    }

    // 2. transactions (installment_group_id is preserved as TEXT, untouched)
    val txCursor = database.query("SELECT id, category_id FROM transactions")
    val txUpdates = mutableListOf<Triple<String, String, String?>>()
    txCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val catId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newCatId = catId?.let { c -> UuidHelper.toCanonicalOrNull(c) ?: c }
            if (newId != id || newCatId != catId) {
                txUpdates.add(Triple(id, newId, newCatId))
            }
        }
    }
    for ((oldId, newId, newCatId) in txUpdates) {
        database.execSQL("UPDATE transactions SET id = ?, category_id = ? WHERE id = ?", arrayOf(newId, newCatId, oldId))
    }

    // 3. budgets
    val bgtDbCursor = database.query("SELECT id, category_id FROM budgets")
    val bgtUpdates = mutableListOf<Triple<String, String, String>>()
    bgtDbCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val catId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newCatId = UuidHelper.toCanonicalOrNull(catId) ?: catId
            if (newId != id || newCatId != catId) {
                bgtUpdates.add(Triple(id, newId, newCatId))
            }
        }
    }
    for ((oldId, newId, newCatId) in bgtUpdates) {
        database.execSQL("UPDATE budgets SET id = ?, category_id = ? WHERE id = ?", arrayOf(newId, newCatId, oldId))
    }

    // 4. recurring_transactions
    val recCursor = database.query("SELECT id, category_id FROM recurring_transactions")
    val recUpdates = mutableListOf<Triple<String, String, String>>()
    recCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val catId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newCatId = UuidHelper.toCanonicalOrNull(catId) ?: catId
            if (newId != id || newCatId != catId) {
                recUpdates.add(Triple(id, newId, newCatId))
            }
        }
    }
    for ((oldId, newId, newCatId) in recUpdates) {
        database.execSQL("UPDATE recurring_transactions SET id = ?, category_id = ? WHERE id = ?", arrayOf(newId, newCatId, oldId))
    }

    // 5. recurring_transaction_occurrences
    val rtoDbCursor = database.query("SELECT recurring_transaction_id, due_date, transaction_id FROM recurring_transaction_occurrences")
    val rtoUpdates = mutableListOf<List<Any?>>()
    rtoDbCursor.use {
        while (it.moveToNext()) {
            val recId = it.getString(0)
            val dueDate = it.getString(1)
            val txId = it.getString(2)
            val newRecId = UuidHelper.toCanonicalOrNull(recId) ?: recId
            val newTxId = txId?.let { t -> UuidHelper.toCanonicalOrNull(t) ?: t }
            if (newRecId != recId || newTxId != txId) {
                rtoUpdates.add(listOf(newRecId, newTxId, recId, dueDate))
            }
        }
    }
    for (args in rtoUpdates) {
        database.execSQL(
            "UPDATE recurring_transaction_occurrences SET recurring_transaction_id = ?, transaction_id = ? WHERE recurring_transaction_id = ? AND due_date = ?",
            args.toTypedArray(),
        )
    }

    // 6. subscriptions
    val subCursor = database.query("SELECT id, category_id FROM subscriptions")
    val subUpdates = mutableListOf<Triple<String, String, String?>>()
    subCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val catId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newCatId = catId?.let { c -> UuidHelper.toCanonicalOrNull(c) ?: c }
            if (newId != id || newCatId != catId) {
                subUpdates.add(Triple(id, newId, newCatId))
            }
        }
    }
    for ((oldId, newId, newCatId) in subUpdates) {
        database.execSQL("UPDATE subscriptions SET id = ?, category_id = ? WHERE id = ?", arrayOf(newId, newCatId, oldId))
    }

    // 7. subscription_price_histories
    val sphCursor = database.query("SELECT id, subscription_id FROM subscription_price_histories")
    val sphUpdates = mutableListOf<Triple<String, String, String>>()
    sphCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val subId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newSubId = UuidHelper.toCanonicalOrNull(subId) ?: subId
            if (newId != id || newSubId != subId) {
                sphUpdates.add(Triple(id, newId, newSubId))
            }
        }
    }
    for ((oldId, newId, newSubId) in sphUpdates) {
        database.execSQL("UPDATE subscription_price_histories SET id = ?, subscription_id = ? WHERE id = ?", arrayOf(newId, newSubId, oldId))
    }

    // 8. subscription_payments
    val spDbCursor = database.query("SELECT id, subscription_id FROM subscription_payments")
    val spUpdates = mutableListOf<Triple<String, String, String>>()
    spDbCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val subId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newSubId = UuidHelper.toCanonicalOrNull(subId) ?: subId
            if (newId != id || newSubId != subId) {
                spUpdates.add(Triple(id, newId, newSubId))
            }
        }
    }
    for ((oldId, newId, newSubId) in spUpdates) {
        database.execSQL("UPDATE subscription_payments SET id = ?, subscription_id = ? WHERE id = ?", arrayOf(newId, newSubId, oldId))
    }

    // 9. subscription_payment_reminder_receipts
    val sprDbCursor = database.query("SELECT stable_key, subscription_id FROM subscription_payment_reminder_receipts")
    val sprUpdates = mutableListOf<ReminderReceiptUpdate>()
    sprDbCursor.use {
        while (it.moveToNext()) {
            val oldKey = it.getString(0)
            val subId = it.getString(1)
            val update = resolveReminderReceiptTransformation(oldKey, subId)
            if (update != null) {
                sprUpdates.add(update)
            }
        }
    }
    for (update in sprUpdates) {
        database.execSQL(
            "UPDATE subscription_payment_reminder_receipts SET subscription_id = ?, stable_key = ? WHERE stable_key = ?",
            arrayOf(update.newSubscriptionId, update.newKey, update.oldKey),
        )
    }

    // 10. assets
    val assetCursor = database.query("SELECT id FROM assets")
    val assetUpdates = mutableListOf<Pair<String, String>>()
    assetCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(id)) {
                assetUpdates.add(id to UuidHelper.compactToCanonicalUuid(id))
            }
        }
    }
    for ((oldId, newId) in assetUpdates) {
        database.execSQL("UPDATE assets SET id = ? WHERE id = ?", arrayOf(newId, oldId))
    }

    // 11. goals
    val goalCursor = database.query("SELECT id FROM goals")
    val goalUpdates = mutableListOf<Pair<String, String>>()
    goalCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(id)) {
                goalUpdates.add(id to UuidHelper.compactToCanonicalUuid(id))
            }
        }
    }
    for ((oldId, newId) in goalUpdates) {
        database.execSQL("UPDATE goals SET id = ? WHERE id = ?", arrayOf(newId, oldId))
    }

    // 12. goal_contributions
    val gcCursor = database.query("SELECT id, goal_id FROM goal_contributions")
    val gcUpdates = mutableListOf<Triple<String, String, String>>()
    gcCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val goalId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newGoalId = UuidHelper.toCanonicalOrNull(goalId) ?: goalId
            if (newId != id || newGoalId != goalId) {
                gcUpdates.add(Triple(id, newId, newGoalId))
            }
        }
    }
    for ((oldId, newId, newGoalId) in gcUpdates) {
        database.execSQL("UPDATE goal_contributions SET id = ?, goal_id = ? WHERE id = ?", arrayOf(newId, newGoalId, oldId))
    }

    // 13. debts
    val debtCursor = database.query("SELECT id FROM debts")
    val debtUpdates = mutableListOf<Pair<String, String>>()
    debtCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(id)) {
                debtUpdates.add(id to UuidHelper.compactToCanonicalUuid(id))
            }
        }
    }
    for ((oldId, newId) in debtUpdates) {
        database.execSQL("UPDATE debts SET id = ? WHERE id = ?", arrayOf(newId, oldId))
    }

    // 14. debt_payments
    val dpCursor = database.query("SELECT id, debt_id FROM debt_payments")
    val dpUpdates = mutableListOf<Triple<String, String, String>>()
    dpCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            val debtId = it.getString(1)
            val newId = UuidHelper.toCanonicalOrNull(id) ?: id
            val newDebtId = UuidHelper.toCanonicalOrNull(debtId) ?: debtId
            if (newId != id || newDebtId != debtId) {
                dpUpdates.add(Triple(id, newId, newDebtId))
            }
        }
    }
    for ((oldId, newId, newDebtId) in dpUpdates) {
        database.execSQL("UPDATE debt_payments SET id = ?, debt_id = ? WHERE id = ?", arrayOf(newId, newDebtId, oldId))
    }

    // 15. tags
    val tagCursor = database.query("SELECT id FROM tags")
    val tagUpdates = mutableListOf<Pair<String, String>>()
    tagCursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(id)) {
                tagUpdates.add(id to UuidHelper.compactToCanonicalUuid(id))
            }
        }
    }
    for ((oldId, newId) in tagUpdates) {
        database.execSQL("UPDATE tags SET id = ? WHERE id = ?", arrayOf(newId, oldId))
    }

    // 16. transaction_tags
    val ttDbCursor = database.query("SELECT transaction_id, tag_id FROM transaction_tags")
    val ttUpdates = mutableListOf<List<String>>()
    ttDbCursor.use {
        while (it.moveToNext()) {
            val txId = it.getString(0)
            val tagId = it.getString(1)
            val newTxId = UuidHelper.toCanonicalOrNull(txId) ?: txId
            val newTagId = UuidHelper.toCanonicalOrNull(tagId) ?: tagId
            if (newTxId != txId || newTagId != tagId) {
                ttUpdates.add(listOf(newTxId, newTagId, txId, tagId))
            }
        }
    }
    for (args in ttUpdates) {
        database.execSQL("UPDATE transaction_tags SET transaction_id = ?, tag_id = ? WHERE transaction_id = ? AND tag_id = ?", args.toTypedArray())
    }

    // 17. receipt_linkages
    val rlCursor = database.query("SELECT transaction_id FROM receipt_linkages")
    val rlUpdates = mutableListOf<Pair<String, String>>()
    rlCursor.use {
        while (it.moveToNext()) {
            val txId = it.getString(0)
            if (UuidHelper.isLegacyCompactHex(txId)) {
                rlUpdates.add(txId to UuidHelper.compactToCanonicalUuid(txId))
            }
        }
    }
    for ((oldTxId, newTxId) in rlUpdates) {
        database.execSQL("UPDATE receipt_linkages SET transaction_id = ? WHERE transaction_id = ?", arrayOf(newTxId, oldTxId))
    }

    // =========================================================================
    // sync_operations (V1 vs V2 Karar Tablosu)
    // =========================================================================
    val opCursor = database.query("SELECT operation_id, entity_type_code, entity_id, protocol_version, payload_json FROM sync_operations")
    val opUpdates = mutableListOf<Triple<String, String, String?>>()
    opCursor.use {
        while (it.moveToNext()) {
            val opId = it.getString(0)
            val entityType = it.getString(1)
            val entityId = it.getString(2)
            val protocolVersion = it.getInt(3)
            val payloadJson = it.getString(4)

            if (entityType !in uuidSyncEntityAllowlist) continue

            if (protocolVersion == 1) {
                // V1 Kuralı: payload_json kesinlikle parse/re-encode edilmez veya değiştirilmez.
                // Yalnızca entity_id compact ise canonicalize edilir.
                val newEntityId = UuidHelper.toCanonicalOrNull(entityId) ?: entityId
                if (newEntityId != entityId) {
                    opUpdates.add(Triple(opId, newEntityId, null))
                }
            } else if (protocolVersion == 2) {
                // V2 Karar Tablosu
                if (payloadJson.isNullOrBlank()) {
                    throw IllegalStateException("V2 sync_operation $opId missing payload_json")
                }
                val payloadObj = try {
                    Json.parseToJsonElement(payloadJson).jsonObject
                } catch (e: Exception) {
                    throw IllegalStateException("V2 sync_operation $opId malformed payload_json", e)
                }
                val rawPayloadId = payloadObj["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalStateException("V2 sync_operation $opId missing top-level payload id")

                val entityIdClass = UuidHelper.classify(entityId)
                val payloadIdClass = UuidHelper.classify(rawPayloadId)

                val canonicalEntityId = UuidHelper.toCanonicalOrNull(entityId)
                val canonicalPayloadId = UuidHelper.toCanonicalOrNull(rawPayloadId)

                if (canonicalEntityId == null || canonicalPayloadId == null || canonicalEntityId != canonicalPayloadId) {
                    // Dal c: Mismatch veya geçersiz format -> fail closed
                    throw IllegalStateException("V2 sync_operation $opId ID mismatch or invalid format: entityId=$entityId, payloadId=$rawPayloadId")
                }

                val targetCanonicalId = canonicalEntityId

                when {
                    // Dal a: entity_id compact + payload top-level id compact
                    entityIdClass == UuidFormat.LEGACY_COMPACT_HEX && payloadIdClass == UuidFormat.LEGACY_COMPACT_HEX -> {
                        val updatedObj = JsonObject(payloadObj.toMutableMap().apply {
                            put("id", JsonPrimitive(targetCanonicalId))
                        })
                        opUpdates.add(Triple(opId, targetCanonicalId, updatedObj.toString()))
                    }
                    // Dal b: entity_id compact + payload top-level id canonical
                    entityIdClass == UuidFormat.LEGACY_COMPACT_HEX && payloadIdClass == UuidFormat.CANONICAL -> {
                        opUpdates.add(Triple(opId, targetCanonicalId, null))
                    }
                    // Bağlayıcı Ek Dal 1: entity_id canonical + payload top-level id compact
                    entityIdClass == UuidFormat.CANONICAL && payloadIdClass == UuidFormat.LEGACY_COMPACT_HEX -> {
                        val updatedObj = JsonObject(payloadObj.toMutableMap().apply {
                            put("id", JsonPrimitive(targetCanonicalId))
                        })
                        opUpdates.add(Triple(opId, entityId, updatedObj.toString()))
                    }
                    // Dal d: entity_id canonical + payload top-level id canonical
                    entityIdClass == UuidFormat.CANONICAL && payloadIdClass == UuidFormat.CANONICAL -> {
                        // Byte-for-byte korunur; güncelleme yapılmaz.
                    }
                }
            }
        }
    }
    for ((opId, newEntityId, newPayload) in opUpdates) {
        if (newPayload != null) {
            database.execSQL("UPDATE sync_operations SET entity_id = ?, payload_json = ? WHERE operation_id = ?", arrayOf(newEntityId, newPayload, opId))
        } else {
            database.execSQL("UPDATE sync_operations SET entity_id = ? WHERE operation_id = ?", arrayOf(newEntityId, opId))
        }
    }

    // =========================================================================
    // sync_conflicts (Snapshot Path Sözleşmeleri)
    // =========================================================================
    val scCursor = database.query("SELECT entity_type_code, entity_id, local_payload_json, remote_payload_json FROM sync_conflicts")
    val scUpdates = mutableListOf<List<Any?>>()
    scCursor.use {
        while (it.moveToNext()) {
            val entityType = it.getString(0)
            val entityId = it.getString(1)
            val localJson = it.getString(2)
            val remoteJson = it.getString(3)

            if (entityType !in uuidSyncEntityAllowlist) continue

            val newEntityId = UuidHelper.toCanonicalOrNull(entityId) ?: entityId

            fun canonicalizeSnapshot(type: String, jsonStr: String, isRemote: Boolean): String {
                val root = try {
                    Json.parseToJsonElement(jsonStr).jsonObject
                } catch (e: Exception) {
                    throw IllegalStateException("sync_conflicts ($type, $entityId) malformed snapshot JSON: $jsonStr", e)
                }

                return if (isRemote && type == "GOAL_CONTRIBUTION") {
                    val contrib = root["contribution"]?.jsonObject
                        ?: throw IllegalStateException("GoalContributionSyncRecordDto missing contribution wrapper")
                    val goal = root["goal"]?.jsonObject
                        ?: throw IllegalStateException("GoalContributionSyncRecordDto missing goal wrapper")

                    val updatedContrib = JsonObject(contrib.toMutableMap().apply {
                        this["id"]?.jsonPrimitive?.contentOrNull?.let { idVal ->
                            UuidHelper.toCanonicalOrNull(idVal)?.let { put("id", JsonPrimitive(it)) }
                        }
                        this["goal_id"]?.jsonPrimitive?.contentOrNull?.let { gidVal ->
                            UuidHelper.toCanonicalOrNull(gidVal)?.let { put("goal_id", JsonPrimitive(it)) }
                        }
                    })
                    val updatedGoal = JsonObject(goal.toMutableMap().apply {
                        this["id"]?.jsonPrimitive?.contentOrNull?.let { idVal ->
                            UuidHelper.toCanonicalOrNull(idVal)?.let { put("id", JsonPrimitive(it)) }
                        }
                    })
                    JsonObject(root.toMutableMap().apply {
                        put("contribution", updatedContrib)
                        put("goal", updatedGoal)
                    }).toString()
                } else if (isRemote && type == "DEBT_PAYMENT") {
                    val payment = root["payment"]?.jsonObject
                        ?: throw IllegalStateException("DebtPaymentSyncRecordDto missing payment wrapper")
                    val debt = root["debt"]?.jsonObject
                        ?: throw IllegalStateException("DebtPaymentSyncRecordDto missing debt wrapper")

                    val updatedPayment = JsonObject(payment.toMutableMap().apply {
                        this["id"]?.jsonPrimitive?.contentOrNull?.let { idVal ->
                            UuidHelper.toCanonicalOrNull(idVal)?.let { put("id", JsonPrimitive(it)) }
                        }
                        this["debt_id"]?.jsonPrimitive?.contentOrNull?.let { didVal ->
                            UuidHelper.toCanonicalOrNull(didVal)?.let { put("debt_id", JsonPrimitive(it)) }
                        }
                    })
                    val updatedDebt = JsonObject(debt.toMutableMap().apply {
                        this["id"]?.jsonPrimitive?.contentOrNull?.let { idVal ->
                            UuidHelper.toCanonicalOrNull(idVal)?.let { put("id", JsonPrimitive(it)) }
                        }
                    })
                    JsonObject(root.toMutableMap().apply {
                        put("payment", updatedPayment)
                        put("debt", updatedDebt)
                    }).toString()
                } else {
                    val updatedRoot = JsonObject(root.toMutableMap().apply {
                        this["id"]?.jsonPrimitive?.contentOrNull?.let { idVal ->
                            UuidHelper.toCanonicalOrNull(idVal)?.let { put("id", JsonPrimitive(it)) }
                        }
                        for (fkField in listOf("category_id", "goal_id", "debt_id", "subscription_id")) {
                            this[fkField]?.jsonPrimitive?.contentOrNull?.let { fkVal ->
                                UuidHelper.toCanonicalOrNull(fkVal)?.let { put(fkField, JsonPrimitive(it)) }
                            }
                        }
                    })
                    updatedRoot.toString()
                }
            }

            val newLocalJson = canonicalizeSnapshot(entityType, localJson, isRemote = false)
            val newRemoteJson = canonicalizeSnapshot(entityType, remoteJson, isRemote = true)

            if (newEntityId != entityId || newLocalJson != localJson || newRemoteJson != remoteJson) {
                scUpdates.add(listOf(newEntityId, newLocalJson, newRemoteJson, entityType, entityId))
            }
        }
    }
    for (args in scUpdates) {
        database.execSQL(
            "UPDATE sync_conflicts SET entity_id = ?, local_payload_json = ?, remote_payload_json = ? WHERE entity_type_code = ? AND entity_id = ?",
            args.toTypedArray(),
        )
    }

    // =========================================================================
    // sync_cursors (Tam Çalışan Kod Eşleşmesi)
    // =========================================================================
    val exactCursorKeys = setOf(
        "CATEGORY", "TRANSACTION", "RECURRING_TRANSACTION", "SUBSCRIPTION",
        "ASSET", "GOAL", "GOAL_CONTRIBUTION", "DEBT", "DEBT_PAYMENT",
    )
    val curCursor = database.query("SELECT entity_type_code, entity_id FROM sync_cursors")
    val curUpdates = mutableListOf<Pair<String, String>>()
    curCursor.use {
        while (it.moveToNext()) {
            val code = it.getString(0)
            val entityId = it.getString(1)
            val isExact = code in exactCursorKeys
            val isWorkspace = code.startsWith("CATEGORY:WORKSPACE:") || code.startsWith("TRANSACTION:WORKSPACE:")
            if (isExact || isWorkspace) {
                if (UuidHelper.isLegacyCompactHex(entityId)) {
                    curUpdates.add(code to UuidHelper.compactToCanonicalUuid(entityId))
                }
            }
        }
    }
    for ((code, newEntityId) in curUpdates) {
        database.execSQL("UPDATE sync_cursors SET entity_id = ? WHERE entity_type_code = ?", arrayOf(newEntityId, code))
    }

    // =========================================================================
    // foreign_key_check Doğrulaması
    // =========================================================================
    val fkCheckCursor = database.query("PRAGMA foreign_key_check")
    val violations = mutableListOf<String>()
    fkCheckCursor.use {
        while (it.moveToNext()) {
            val table = it.getString(0)
            val rowid = it.getLong(1)
            val targetTable = it.getString(2)
            val fkid = it.getInt(3)
            violations.add("Table: $table, RowId: $rowid, Target: $targetTable, FkId: $fkid")
        }
    }
    if (violations.isNotEmpty()) {
        throw IllegalStateException("Foreign key check failed after migration 20->21:\n" + violations.joinToString("\n"))
    }
}

/**
 * Room v21 -> v22: P0 Hesap Değişiminde Outbox, Cursor ve Conflict İzolasyonu Temeli.
 *
 * `sync_operations`, `sync_cursors` ve `sync_conflicts` tablolarına `sync_scope_key` eklenir.
 * - sync_operations: operation_id PK, 3 adet index (scope+status+blocked+nextAttempt+createdAt+operationId, scope+type+id, scope+predecessor).
 * - sync_cursors: PK (sync_scope_key, entity_type_code).
 * - sync_conflicts: PK (sync_scope_key, entity_type_code, entity_id), index (scope+detectedAt).
 *
 * Backfill kuralları:
 * - Hiçbir outbox, cursor veya conflict satırı silinmez.
 * - Payload JSON değerleri byte-for-byte korunur; payload actor kanıtı olarak parse edilmez.
 * - PROFILE operasyonu için canonical entity_id güvenilir actor kanıtıdır -> USER:<canonical_uuid>.
 * - Kişisel (workspace_id IS NULL) CATEGORY, TRANSACTION, BUDGET, RECURRING_TRANSACTION,
 *   SUBSCRIPTION, ASSET, GOAL, DEBT ve çocukları (GOAL_CONTRIBUTION, DEBT_PAYMENT)
 *   kanıtlanan owner_id ile USER:<owner_id> scope'una atanır.
 * - Workspace veya actor'ü kanıtlanamayan operasyonlar fail-closed LEGACY_UNRESOLVED olarak kalır.
 * - Conflict bağlı operation_id'nin hesaplanan scope'unu miras alır; bağlı operasyon yoksa veya unresolved ise LEGACY_UNRESOLVED kalır.
 * - Cursor seti ancak geçerli PROFILE cursor kanıtıyla USER:<canonical_user_id>'ye taşınır; kanıt yoksa LEGACY_UNRESOLVED korunur.
 */
val ANDROID_MIGRATION_21_22 = Migration(21, 22) { database ->
    database.execSQL("PRAGMA defer_foreign_keys = ON")

    // 1. Yeni geçici v22 tablolarını oluştur
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_operations_v22` (
            `operation_id` TEXT NOT NULL,
            `sync_scope_key` TEXT NOT NULL,
            `entity_type_code` TEXT NOT NULL,
            `entity_id` TEXT NOT NULL,
            `operation_type_code` TEXT NOT NULL,
            `base_version` INTEGER,
            `payload_json` TEXT,
            `predecessor_operation_id` TEXT,
            `is_blocked` INTEGER NOT NULL DEFAULT 0,
            `protocol_version` INTEGER NOT NULL DEFAULT 1,
            `status_code` TEXT NOT NULL,
            `attempt_count` INTEGER NOT NULL,
            `last_error` TEXT,
            `next_attempt_at_epoch_ms` INTEGER NOT NULL,
            `created_at_epoch_ms` INTEGER NOT NULL,
            `updated_at_epoch_ms` INTEGER NOT NULL,
            `error_classification` TEXT DEFAULT NULL,
            PRIMARY KEY(`operation_id`)
        )
        """.trimIndent(),
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_cursors_v22` (
            `sync_scope_key` TEXT NOT NULL,
            `entity_type_code` TEXT NOT NULL,
            `updated_at_epoch_ms` INTEGER NOT NULL,
            `entity_id` TEXT NOT NULL,
            PRIMARY KEY(`sync_scope_key`, `entity_type_code`)
        )
        """.trimIndent(),
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_conflicts_v22` (
            `sync_scope_key` TEXT NOT NULL,
            `entity_type_code` TEXT NOT NULL,
            `entity_id` TEXT NOT NULL,
            `operation_id` TEXT NOT NULL,
            `local_version` INTEGER NOT NULL,
            `remote_version` INTEGER NOT NULL,
            `local_payload_json` TEXT NOT NULL,
            `remote_payload_json` TEXT NOT NULL,
            `detected_at_epoch_ms` INTEGER NOT NULL,
            PRIMARY KEY(`sync_scope_key`, `entity_type_code`, `entity_id`)
        )
        """.trimIndent(),
    )

    // 2. sync_operations verisini oku, scope belirle ve sync_operations_v22'ye aktar
    val resolvedOpScopes = mutableMapOf<String, String>()

    val curOps = database.query("SELECT operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification FROM sync_operations")
    val opRows = mutableListOf<List<Any?>>()

    curOps.use {
        while (it.moveToNext()) {
            val opId = it.getString(0)
            val typeCode = it.getString(1)
            val entityId = it.getString(2)
            val opTypeCode = it.getString(3)
            val baseVersion = if (!it.isNull(4)) it.getLong(4) else null
            val payloadJson = if (!it.isNull(5)) it.getString(5) else null
            val predecessorOpId = if (!it.isNull(6)) it.getString(6) else null
            val isBlocked = it.getInt(7)
            val protocolVersion = it.getInt(8)
            val statusCode = it.getString(9)
            val attemptCount = it.getInt(10)
            val lastError = if (!it.isNull(11)) it.getString(11) else null
            val nextAttempt = it.getLong(12)
            val createdAt = it.getLong(13)
            val updatedAt = it.getLong(14)
            val errorClass = if (!it.isNull(15)) it.getString(15) else null

            val scope = resolveMigration21To22OperationScope(database, typeCode, entityId)
            resolvedOpScopes[opId] = scope

            opRows.add(
                listOf(
                    opId, scope, typeCode, entityId, opTypeCode, baseVersion,
                    payloadJson, predecessorOpId, isBlocked, protocolVersion,
                    statusCode, attemptCount, lastError, nextAttempt,
                    createdAt, updatedAt, errorClass,
                ),
            )
        }
    }

    val insertOpStmt = database.compileStatement(
        """
        INSERT INTO sync_operations_v22 (
            operation_id, sync_scope_key, entity_type_code, entity_id,
            operation_type_code, base_version, payload_json, predecessor_operation_id,
            is_blocked, protocol_version, status_code, attempt_count,
            last_error, next_attempt_at_epoch_ms, created_at_epoch_ms,
            updated_at_epoch_ms, error_classification
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    )
    for (row in opRows) {
        insertOpStmt.clearBindings()
        insertOpStmt.bindString(1, row[0] as String)
        insertOpStmt.bindString(2, row[1] as String)
        insertOpStmt.bindString(3, row[2] as String)
        insertOpStmt.bindString(4, row[3] as String)
        insertOpStmt.bindString(5, row[4] as String)

        val baseVersion = row[5] as Long?
        if (baseVersion != null) insertOpStmt.bindLong(6, baseVersion) else insertOpStmt.bindNull(6)

        val payloadJson = row[6] as String?
        if (payloadJson != null) insertOpStmt.bindString(7, payloadJson) else insertOpStmt.bindNull(7)

        val predecessorOpId = row[7] as String?
        if (predecessorOpId != null) insertOpStmt.bindString(8, predecessorOpId) else insertOpStmt.bindNull(8)

        insertOpStmt.bindLong(9, (row[8] as Int).toLong())
        insertOpStmt.bindLong(10, (row[9] as Int).toLong())
        insertOpStmt.bindString(11, row[10] as String)
        insertOpStmt.bindLong(12, (row[11] as Int).toLong())

        val lastError = row[12] as String?
        if (lastError != null) insertOpStmt.bindString(13, lastError) else insertOpStmt.bindNull(13)

        insertOpStmt.bindLong(14, row[13] as Long)
        insertOpStmt.bindLong(15, row[14] as Long)
        insertOpStmt.bindLong(16, row[15] as Long)

        val errorClass = row[16] as String?
        if (errorClass != null) insertOpStmt.bindString(17, errorClass) else insertOpStmt.bindNull(17)

        insertOpStmt.executeInsert()
    }

    // 3. sync_conflicts verisini aktar (bağlı outbox operation scope'unu miras al)
    val curConflicts = database.query("SELECT entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms FROM sync_conflicts")
    val conflictRows = mutableListOf<List<Any?>>()
    curConflicts.use {
        while (it.moveToNext()) {
            val typeCode = it.getString(0)
            val entityId = it.getString(1)
            val opId = it.getString(2)
            val localVer = it.getLong(3)
            val remoteVer = it.getLong(4)
            val localJson = it.getString(5)
            val remoteJson = it.getString(6)
            val detectedAt = it.getLong(7)

            val parentScope = resolvedOpScopes[opId]
            val conflictScope = if (parentScope != null && parentScope.startsWith("USER:")) {
                parentScope
            } else {
                "LEGACY_UNRESOLVED"
            }

            conflictRows.add(
                listOf(conflictScope, typeCode, entityId, opId, localVer, remoteVer, localJson, remoteJson, detectedAt),
            )
        }
    }

    val insertConflictStmt = database.compileStatement(
        """
        INSERT INTO sync_conflicts_v22 (
            sync_scope_key, entity_type_code, entity_id, operation_id,
            local_version, remote_version, local_payload_json,
            remote_payload_json, detected_at_epoch_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    )
    for (cRow in conflictRows) {
        insertConflictStmt.clearBindings()
        insertConflictStmt.bindString(1, cRow[0] as String)
        insertConflictStmt.bindString(2, cRow[1] as String)
        insertConflictStmt.bindString(3, cRow[2] as String)
        insertConflictStmt.bindString(4, cRow[3] as String)
        insertConflictStmt.bindLong(5, cRow[4] as Long)
        insertConflictStmt.bindLong(6, cRow[5] as Long)
        insertConflictStmt.bindString(7, cRow[6] as String)
        insertConflictStmt.bindString(8, cRow[7] as String)
        insertConflictStmt.bindLong(9, cRow[8] as Long)
        insertConflictStmt.executeInsert()
    }

    // 4. sync_cursors verisini aktar (PROFILE cursor kanıtı denetimi)
    val curProfile = database.query("SELECT entity_id FROM sync_cursors WHERE entity_type_code = 'PROFILE'")
    val profileCursorUserId = curProfile.use {
        if (it.moveToFirst()) {
            val eid = it.getString(0)
            UuidHelper.toCanonicalOrNull(eid)
        } else {
            null
        }
    }

    val cursorScope = if (profileCursorUserId != null) {
        "USER:$profileCursorUserId"
    } else {
        "LEGACY_UNRESOLVED"
    }

    val curCursors = database.query("SELECT entity_type_code, updated_at_epoch_ms, entity_id FROM sync_cursors")
    val cursorRows = mutableListOf<Triple<String, Long, String>>()
    curCursors.use {
        while (it.moveToNext()) {
            cursorRows.add(Triple(it.getString(0), it.getLong(1), it.getString(2)))
        }
    }

    val insertCursorStmt = database.compileStatement(
        """
        INSERT INTO sync_cursors_v22 (
            sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id
        ) VALUES (?, ?, ?, ?)
        """.trimIndent(),
    )
    for ((typeCode, updatedAt, entityId) in cursorRows) {
        insertCursorStmt.clearBindings()
        insertCursorStmt.bindString(1, cursorScope)
        insertCursorStmt.bindString(2, typeCode)
        insertCursorStmt.bindLong(3, updatedAt)
        insertCursorStmt.bindString(4, entityId)
        insertCursorStmt.executeInsert()
    }

    // 5. Eski tabloları kaldır ve yeni tabloları yeniden adlandır
    database.execSQL("DROP TABLE sync_operations")
    database.execSQL("ALTER TABLE sync_operations_v22 RENAME TO sync_operations")

    database.execSQL("DROP TABLE sync_cursors")
    database.execSQL("ALTER TABLE sync_cursors_v22 RENAME TO sync_cursors")

    database.execSQL("DROP TABLE sync_conflicts")
    database.execSQL("ALTER TABLE sync_conflicts_v22 RENAME TO sync_conflicts")

    // 6. İndeksleri oluştur
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_sync_scope_key_status_code_is_blocked_next_attempt_at_epoch_ms_created_at_epoch_ms_operation_id` ON `sync_operations` (`sync_scope_key`, `status_code`, `is_blocked`, `next_attempt_at_epoch_ms`, `created_at_epoch_ms`, `operation_id`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_sync_scope_key_entity_type_code_entity_id` ON `sync_operations` (`sync_scope_key`, `entity_type_code`, `entity_id`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_sync_scope_key_predecessor_operation_id` ON `sync_operations` (`sync_scope_key`, `predecessor_operation_id`)")

    database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_sync_scope_key_detected_at_epoch_ms` ON `sync_conflicts` (`sync_scope_key`, `detected_at_epoch_ms`)")

    // 7. foreign_key_check doğrulaması
    val fkCheckCursor = database.query("PRAGMA foreign_key_check")
    val violations = mutableListOf<String>()
    fkCheckCursor.use {
        while (it.moveToNext()) {
            val table = it.getString(0)
            val rowid = it.getLong(1)
            val targetTable = it.getString(2)
            val fkid = it.getInt(3)
            violations.add("Table: $table, RowId: $rowid, Target: $targetTable, FkId: $fkid")
        }
    }
    if (violations.isNotEmpty()) {
        throw IllegalStateException("Foreign key check failed after migration 21->22:\n" + violations.joinToString("\n"))
    }
}

private fun resolveMigration21To22OperationScope(
    database: SupportSQLiteDatabase,
    entityTypeCode: String,
    entityId: String,
): String = when (entityTypeCode) {
    "PROFILE" -> {
        val canonical = UuidHelper.toCanonicalOrNull(entityId)
        if (canonical != null) "USER:$canonical" else "LEGACY_UNRESOLVED"
    }
    "CATEGORY" -> resolvePersonalOwner(database, "categories", entityId, hasWorkspace = true)
    "TRANSACTION" -> resolvePersonalOwner(database, "transactions", entityId, hasWorkspace = true)
    "BUDGET" -> resolvePersonalOwner(database, "budgets", entityId, hasWorkspace = true)
    "RECURRING_TRANSACTION" -> resolvePersonalOwner(database, "recurring_transactions", entityId, hasWorkspace = true)
    "SUBSCRIPTION" -> resolvePersonalOwner(database, "subscriptions", entityId, hasWorkspace = true)
    "ASSET" -> resolvePersonalOwner(database, "assets", entityId, hasWorkspace = false)
    "GOAL" -> resolvePersonalOwner(database, "goals", entityId, hasWorkspace = true)
    "GOAL_CONTRIBUTION" -> resolveChildOwner(
        database = database,
        childTable = "goal_contributions",
        parentTable = "goals",
        foreignKey = "goal_id",
        childId = entityId,
    )
    "DEBT" -> resolvePersonalOwner(database, "debts", entityId, hasWorkspace = true)
    "DEBT_PAYMENT" -> resolveChildOwner(
        database = database,
        childTable = "debt_payments",
        parentTable = "debts",
        foreignKey = "debt_id",
        childId = entityId,
    )
    else -> "LEGACY_UNRESOLVED"
}

private fun resolvePersonalOwner(
    database: SupportSQLiteDatabase,
    table: String,
    entityId: String,
    hasWorkspace: Boolean,
): String {
    val query = if (hasWorkspace) {
        "SELECT workspace_id, owner_id FROM $table WHERE id = ?"
    } else {
        "SELECT NULL, owner_id FROM $table WHERE id = ?"
    }
    val cursor = database.query(query, arrayOf(entityId))
    return cursor.use {
        if (it.moveToFirst()) {
            val workspaceId = if (hasWorkspace && !it.isNull(0)) it.getString(0) else null
            val ownerId = if (!it.isNull(1)) it.getString(1) else null
            if (workspaceId == null && ownerId != null) {
                val canonical = UuidHelper.toCanonicalOrNull(ownerId)
                if (canonical != null) "USER:$canonical" else "LEGACY_UNRESOLVED"
            } else {
                "LEGACY_UNRESOLVED"
            }
        } else {
            "LEGACY_UNRESOLVED"
        }
    }
}

private fun resolveChildOwner(
    database: SupportSQLiteDatabase,
    childTable: String,
    parentTable: String,
    foreignKey: String,
    childId: String,
): String {
    val query = "SELECT p.workspace_id, p.owner_id FROM $childTable c JOIN $parentTable p ON p.id = c.$foreignKey WHERE c.id = ?"
    val cursor = database.query(query, arrayOf(childId))
    return cursor.use {
        if (it.moveToFirst()) {
            val workspaceId = if (!it.isNull(0)) it.getString(0) else null
            val ownerId = if (!it.isNull(1)) it.getString(1) else null
            if (workspaceId == null && ownerId != null) {
                val canonical = UuidHelper.toCanonicalOrNull(ownerId)
                if (canonical != null) "USER:$canonical" else "LEGACY_UNRESOLVED"
            } else {
                "LEGACY_UNRESOLVED"
            }
        } else {
            "LEGACY_UNRESOLVED"
        }
    }
}
