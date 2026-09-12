package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * E15-A'da varlıklar yalnız kişisel kapsamda tutulur. Workspace sahipliği ileride ayrı
 * bir sözleşmeyle açılmalıdır; bu tablo bu nedenle workspace_id taşımaz.
 */
@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["type_code"]),
        Index(value = ["deleted_at_epoch_ms"]),
    ],
)
data class AssetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "type_code")
    val typeCode: String,
    @ColumnInfo(name = "current_value_minor")
    val currentValueMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "quantity_unscaled")
    val quantityUnscaled: Long?,
    @ColumnInfo(name = "quantity_scale")
    val quantityScale: Int?,
    @ColumnInfo(name = "purchase_unit_price_minor")
    val purchaseUnitPriceMinor: Long?,
    @ColumnInfo(name = "tracking_symbol")
    val trackingSymbol: String?,
    @ColumnInfo(name = "auto_track")
    val autoTrack: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
    @Embedded
    val sync: SyncMetadata,
)
