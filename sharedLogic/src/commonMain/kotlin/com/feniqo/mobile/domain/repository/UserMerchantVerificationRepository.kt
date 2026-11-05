package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import kotlinx.coroutines.flow.Flow

/**
 * Exact alias sorgusu için fail-closed sonuç tipi.
 * Room'da kayıt bulunduğu halde bozuksa (invalid schema/invariant),
 * sistem kataloğuna sessizce düşmemesi için [InvalidStoredVerification] üretilir.
 */
sealed interface UserMerchantVerificationLookupResult {
    data object NotFound : UserMerchantVerificationLookupResult

    data class Decision(
        val decision: UserMerchantDecision,
    ) : UserMerchantVerificationLookupResult

    data class InvalidStoredVerification(
        val id: String? = null,
    ) : UserMerchantVerificationLookupResult
}

/**
 * Kullanıcıların kişisel merchant doğrulama/engelleme kuralları için repository sözleşmesi.
 * Hiçbir public metot ownerId/userId parametresi almaz; kullanıcı kimliği aktif oturumdan türetilir.
 */
interface UserMerchantVerificationRepository {
    /**
     * Aktif oturumun kişisel merchant doğrulama kurallarını Flow olarak gözlemler.
     * Oturum kapalıysa (sign-out) boş liste üretir.
     */
    fun observeActiveVerifications(): Flow<List<UserMerchantVerification>>

    /**
     * Verilen ham veya normalize alias için exact kullanıcı kararını sorgular.
     */
    suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult

    /**
     * Verilen alias için ASSIGN kararı oluşturur veya günceller.
     */
    suspend fun assignMerchant(
        alias: String,
        merchantId: String,
    ): RepositoryResult<UserMerchantVerification>

    /**
     * Verilen alias için BLOCK kararı oluşturur veya günceller.
     */
    suspend fun blockMerchant(alias: String): RepositoryResult<UserMerchantVerification>

    /**
     * Verilen alias için mevcut kuralı soft-delete eder (tombstone).
     */
    suspend fun deleteVerification(alias: String): RepositoryResult<Unit>
}
