package com.feniqo.mobile.domain.model

/**
 * Yeni tekil kişisel gider oluşturulurken kullanıcının verdiği tipli merchant kararıdır.
 * UI seçim durumundan bağımsız, domain command ve repository sınırında taşınır.
 */
sealed interface TransactionMerchantCreateDecision {
    /**
     * Otomatik katalog ve mevcut kişisel ASSIGN/BLOCK öncelikli tanıma akışı.
     */
    data object Auto : TransactionMerchantCreateDecision

    /**
     * Kullanıcının katalogdan açıkça seçtiği bilinen merchant.
     * Transaction'a USER_VERIFIED / 100 güven atar ve aynı alias için PERSONAL ASSIGN verification oluşturur.
     */
    data class KnownMerchant(
        val merchantId: EntityId,
    ) : TransactionMerchantCreateDecision {
        constructor(merchantId: String) : this(EntityId(merchantId))

        init {
            require(merchantId.value.isNotBlank()) { "KnownMerchant merchantId boş olamaz." }
        }
    }

    /**
     * Kullanıcının "İş yeri adı kullan / merchant bağlama" kararı.
     * Transaction merchant attribution alanları tamamen null tutulur ve aynı alias için
     * kullanıcı+normalized-alias kapsamında PERSONAL BLOCK verification oluşturulur.
     */
    data object UseTransactionName : TransactionMerchantCreateDecision
}
