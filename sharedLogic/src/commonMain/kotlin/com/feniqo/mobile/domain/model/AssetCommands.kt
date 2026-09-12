package com.feniqo.mobile.domain.model

/** Kullanıcının oluşturduğu kişisel varlık girdisi. Sahiplik ve zaman bilgisi repository'de atanır. */
data class CreateAssetCommand(
    val name: String,
    val type: AssetType,
    val currentValue: Money,
    val quantity: AssetQuantity? = null,
    val purchaseUnitPrice: Money? = null,
    val trackingSymbol: String? = null,
    val autoTrack: Boolean = false,
)

/** Mevcut varlığın kullanıcı tarafından değiştirilebilen alanları. */
data class UpdateAssetCommand(
    val id: EntityId,
    val name: String,
    val type: AssetType,
    val currentValue: Money,
    val quantity: AssetQuantity? = null,
    val purchaseUnitPrice: Money? = null,
    val trackingSymbol: String? = null,
    val autoTrack: Boolean = false,
)
