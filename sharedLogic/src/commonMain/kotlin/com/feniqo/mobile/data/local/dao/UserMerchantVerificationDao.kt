package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room v24: Kişisel merchant doğrulama kararları için salt-okuma DAO.
 * AGENTS.md gereği uygulama write metotları bu aşamada kapalıdır; yerel mutasyon ve
 * outbox senkronizasyonu Faz 8.6-D ile birlikte atomik olarak açılacaktır.
 */
@Dao
interface UserMerchantVerificationDao {
    @Query(
        """
        SELECT * FROM user_merchant_verifications
        WHERE scope_key = :scopeKey
          AND normalized_alias = :normalizedAlias
          AND deleted_at_epoch_ms IS NULL
        LIMIT 1
        """,
    )
    suspend fun getActiveByScopeAndAlias(
        scopeKey: String,
        normalizedAlias: String,
    ): UserMerchantVerificationEntity?

    @Query(
        """
        SELECT * FROM user_merchant_verifications
        WHERE scope_key = :scopeKey
          AND deleted_at_epoch_ms IS NULL
        ORDER BY normalized_alias ASC
        """,
    )
    fun observeActiveByScope(scopeKey: String): Flow<List<UserMerchantVerificationEntity>>

    @Query(
        """
        SELECT * FROM user_merchant_verifications
        WHERE id = :id
          AND scope_key = :scopeKey
          AND deleted_at_epoch_ms IS NULL
        LIMIT 1
        """,
    )
    suspend fun getActiveByIdAndScope(
        id: String,
        scopeKey: String,
    ): UserMerchantVerificationEntity?

    @Query(
        """
        SELECT * FROM user_merchant_verifications
        WHERE scope_key = :scopeKey
          AND normalized_alias = :normalizedAlias
        LIMIT 1
        """,
    )
    suspend fun getByScopeAndAlias(
        scopeKey: String,
        normalizedAlias: String,
    ): UserMerchantVerificationEntity?

    @Query(
        """
        SELECT * FROM user_merchant_verifications
        WHERE id = :id
          AND scope_key = :scopeKey
        LIMIT 1
        """,
    )
    suspend fun getByIdAndScope(
        id: String,
        scopeKey: String,
    ): UserMerchantVerificationEntity?
}
