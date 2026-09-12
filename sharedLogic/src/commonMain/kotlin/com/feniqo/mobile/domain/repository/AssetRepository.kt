package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import kotlinx.coroutines.flow.Flow

/** Kişisel varlıkların Room SSOT üzerinden okunup offline-first yazılması için sözleşme. */
interface AssetRepository {
    fun observeAssets(): Flow<List<Asset>>
    fun observeAsset(id: EntityId): Flow<Asset?>
    suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId>
    suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit>
    suspend fun softDelete(id: EntityId): RepositoryResult<Unit>
}
