package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.domain.model.SyncStatus
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
private val workspacePayloadJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}

@Serializable
private data class WorkspaceCreateWithCreatedAtPayload(
    val id: String,
    val name: String,
    @SerialName("type_code")
    val typeCode: String,
    @SerialName("currency_code")
    val currencyCode: String,
    val description: String?,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
private data class WorkspaceCreatePayload(
    val id: String,
    val name: String,
    @SerialName("type_code")
    val typeCode: String,
    @SerialName("currency_code")
    val currencyCode: String,
    val description: String?,
)

@Serializable
private data class WorkspaceUpdatePayload(
    val id: String,
    val name: String,
    @SerialName("type_code")
    val typeCode: String,
    @SerialName("currency_code")
    val currencyCode: String,
    val description: String?,
)

@Serializable
private data class WorkspaceDeletePayload(
    val id: String,
)

/**
 * Supabase `sync_write_v2` RPC'sinin WORKSPACE dalı için canonical JSON outbound payload üretir.
 * D1 SQL allowlist ve invariant kurallarına tam uyumludur:
 * - CREATE: `id`, `name`, `type_code`, `currency_code`, `description`, (opsiyonel `created_at`)
 * - UPDATE: `id`, `name`, `type_code`, `currency_code`, `description`
 * - DELETE: `id`
 *
 * `owner_id`, `normalized_name`, `version`, `base_version`, `updated_at`, `deleted_at` gibi alanlar
 * remote RPC allowlist dışı olduğu için payload'a eklenmez.
 */
object WorkspacePayloadCodec {

    fun encode(
        entity: WorkspaceEntity,
        operationType: OutboxOperationType,
        includeCreatedAtInCreate: Boolean = true,
    ): String {
        val cleanId = entity.id.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace entity id boş olamaz.")

        val sync = entity.sync
        val status = runCatching { SyncStatus.valueOf(sync.syncStatus) }.getOrNull()
            ?: throw RemoteMappingException("Geçersiz sync status: ${sync.syncStatus}")

        return when (operationType) {
            OutboxOperationType.CREATE -> encodeCreate(entity, cleanId, status, includeCreatedAtInCreate)
            OutboxOperationType.UPDATE -> encodeUpdate(entity, cleanId, status)
            OutboxOperationType.DELETE -> encodeDelete(entity, cleanId, status)
        }
    }

    /**
     * Henüz sunucuya gönderilmemiş PENDING_CREATE durumundaki bir workspace'in
     * yerel iptali (hard-delete cancellation) için canonical `{ "id": ... }` JSON payload'ı üretir.
     * Bu helper normal remote DELETE payload API'sinden ayrıdır ve baseVersion aramaz.
     */
    internal fun encodePendingCreateHardDeletePayload(workspaceId: String): String {
        val cleanId = workspaceId.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace id boş olamaz.")
        val payload = WorkspaceDeletePayload(id = cleanId)
        return workspacePayloadJson.encodeToString(payload)
    }


    private fun encodeCreate(
        entity: WorkspaceEntity,
        id: String,
        status: SyncStatus,
        includeCreatedAt: Boolean,
    ): String {
        if (status != SyncStatus.PENDING_CREATE) {
            throw RemoteMappingException("CREATE payload için syncStatus PENDING_CREATE olmalıdır. Alınan: $status")
        }
        if (entity.sync.baseVersion != null) {
            throw RemoteMappingException("CREATE payload için baseVersion null olmalıdır.")
        }
        if (entity.sync.deletedAtEpochMillis != null) {
            throw RemoteMappingException("CREATE payload için deletedAtEpochMillis null olmalıdır.")
        }

        val name = entity.name.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace name boş olamaz.")
        val typeCode = entity.typeCode.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace type_code boş olamaz.")
        if (typeCode !in setOf("personal", "shared")) {
            throw RemoteMappingException("Desteklenmeyen workspace type_code: $typeCode")
        }
        val currencyCode = entity.currencyCode.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace currency_code boş olamaz.")
        if (currencyCode !in setOf("TRY", "USD", "EUR")) {
            throw RemoteMappingException("Desteklenmeyen workspace currency_code: $currencyCode")
        }
        val description = entity.description?.trim()?.takeIf(String::isNotEmpty)

        return if (includeCreatedAt) {
            if (entity.createdAtEpochMillis <= 0L) {
                throw RemoteMappingException("Geçersiz createdAtEpochMillis: ${entity.createdAtEpochMillis}")
            }
            val createdAtIso = Instant.fromEpochMilliseconds(entity.createdAtEpochMillis).toString()
            val payload = WorkspaceCreateWithCreatedAtPayload(
                id = id,
                name = name,
                typeCode = typeCode,
                currencyCode = currencyCode,
                description = description,
                createdAt = createdAtIso,
            )
            workspacePayloadJson.encodeToString(payload)
        } else {
            val payload = WorkspaceCreatePayload(
                id = id,
                name = name,
                typeCode = typeCode,
                currencyCode = currencyCode,
                description = description,
            )
            workspacePayloadJson.encodeToString(payload)
        }
    }

    private fun encodeUpdate(
        entity: WorkspaceEntity,
        id: String,
        status: SyncStatus,
    ): String {
        if (status != SyncStatus.PENDING_UPDATE) {
            throw RemoteMappingException("UPDATE payload için syncStatus PENDING_UPDATE olmalıdır. Alınan: $status")
        }
        val baseVersion = entity.sync.baseVersion
        if (baseVersion == null || baseVersion < 1L) {
            throw RemoteMappingException("UPDATE payload için baseVersion pozitif bir tamsayı olmalıdır (>= 1). Alınan: $baseVersion")
        }
        if (entity.sync.deletedAtEpochMillis != null) {
            throw RemoteMappingException("UPDATE payload için deletedAtEpochMillis null olmalıdır.")
        }

        val name = entity.name.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace name boş olamaz.")
        val typeCode = entity.typeCode.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace type_code boş olamaz.")
        if (typeCode !in setOf("personal", "shared")) {
            throw RemoteMappingException("Desteklenmeyen workspace type_code: $typeCode")
        }
        val currencyCode = entity.currencyCode.trim().takeIf(String::isNotEmpty)
            ?: throw RemoteMappingException("Workspace currency_code boş olamaz.")
        if (currencyCode !in setOf("TRY", "USD", "EUR")) {
            throw RemoteMappingException("Desteklenmeyen workspace currency_code: $currencyCode")
        }
        val description = entity.description?.trim()?.takeIf(String::isNotEmpty)

        val payload = WorkspaceUpdatePayload(
            id = id,
            name = name,
            typeCode = typeCode,
            currencyCode = currencyCode,
            description = description,
        )
        return workspacePayloadJson.encodeToString(payload)
    }

    private fun encodeDelete(
        entity: WorkspaceEntity,
        id: String,
        status: SyncStatus,
    ): String {
        if (status != SyncStatus.PENDING_DELETE) {
            throw RemoteMappingException("DELETE payload için syncStatus PENDING_DELETE olmalıdır. Alınan: $status")
        }
        val baseVersion = entity.sync.baseVersion
        if (baseVersion == null || baseVersion < 1L) {
            throw RemoteMappingException("DELETE payload için baseVersion pozitif bir tamsayı olmalıdır (>= 1). Alınan: $baseVersion")
        }
        if (entity.sync.deletedAtEpochMillis == null) {
            throw RemoteMappingException("DELETE payload için deletedAtEpochMillis zorunludur.")
        }

        val payload = WorkspaceDeletePayload(
            id = id,
        )
        return workspacePayloadJson.encodeToString(payload)
    }

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Outbox'taki immutable `payloadJson` verisini RemoteWriteOperation ve baseVersion kurallarına göre
     * doğrular ve canonical JsonObject olarak döner.
     */
    fun parseAndValidate(
        operationId: String,
        entityId: String,
        operation: com.feniqo.mobile.data.remote.core.RemoteWriteOperation,
        baseVersion: Long?,
        payloadJson: String,
    ): kotlinx.serialization.json.JsonObject {
        require(payloadJson.isNotBlank()) {
            "Workspace payload_json boş olamaz: $operationId"
        }

        require(entityId.isNotEmpty() && entityId == entityId.trim() && UUID_REGEX.matches(entityId)) {
            "Workspace outbox entityId canonical UUID formatında olmalıdır: $entityId ($operationId)"
        }

        val jsonElement = try {
            workspacePayloadJson.parseToJsonElement(payloadJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Workspace payload_json geçerli bir JSON değil: $operationId", e)
        }

        val jsonObject = jsonElement as? kotlinx.serialization.json.JsonObject
            ?: throw IllegalArgumentException("Workspace payload_json JSON object olmalıdır: $operationId")

        // ID kontrolü
        val idElement = jsonObject["id"]
            ?: throw IllegalArgumentException("Workspace payload 'id' alanı zorunludur: $operationId")
        val idPrimitive = idElement as? kotlinx.serialization.json.JsonPrimitive
            ?: throw IllegalArgumentException("Workspace payload 'id' primitive string olmalıdır: $operationId")
        if (!idPrimitive.isString) {
            throw IllegalArgumentException("Workspace payload 'id' string olmalıdır: $operationId")
        }
        val idContent = idPrimitive.content
        require(idContent.isNotEmpty() && idContent == idContent.trim() && UUID_REGEX.matches(idContent)) {
            "Workspace payload 'id' canonical UUID formatında olmalıdır: $idContent ($operationId)"
        }
        require(idContent == entityId) {
            "Workspace payload 'id' ($idContent) ile outbox entityId ($entityId) uyuşmuyor: $operationId"
        }

        when (operation) {
            com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE -> {
                require(baseVersion == null) {
                    "CREATE işlemi için baseVersion null olmalıdır: $operationId"
                }
                val allowedKeys = setOf("id", "name", "type_code", "currency_code", "description", "created_at")
                val unknownKeys = jsonObject.keys - allowedKeys
                require(unknownKeys.isEmpty()) {
                    "CREATE payload yasak veya bilinmeyen alanlar içeriyor: $unknownKeys ($operationId)"
                }

                // name
                val nameElem = jsonObject["name"]
                    ?: throw IllegalArgumentException("CREATE payload 'name' alanı zorunludur: $operationId")
                val namePrim = nameElem as? kotlinx.serialization.json.JsonPrimitive
                require(namePrim != null && namePrim.isString && namePrim.content.isNotBlank()) {
                    "CREATE payload 'name' string ve dolu olmalıdır: $operationId"
                }

                // type_code
                val typeElem = jsonObject["type_code"]
                    ?: throw IllegalArgumentException("CREATE payload 'type_code' alanı zorunludur: $operationId")
                val typePrim = typeElem as? kotlinx.serialization.json.JsonPrimitive
                require(typePrim != null && typePrim.isString && typePrim.content in setOf("personal", "shared")) {
                    "CREATE payload 'type_code' geçerli değil (personal|shared): $operationId"
                }

                // currency_code
                val currElem = jsonObject["currency_code"]
                    ?: throw IllegalArgumentException("CREATE payload 'currency_code' alanı zorunludur: $operationId")
                val currPrim = currElem as? kotlinx.serialization.json.JsonPrimitive
                require(currPrim != null && currPrim.isString && currPrim.content in setOf("TRY", "USD", "EUR")) {
                    "CREATE payload 'currency_code' geçerli değil (TRY|USD|EUR): $operationId"
                }

                // description: null kabul, string ise trim().isNotEmpty() zorunlu
                if (jsonObject.containsKey("description")) {
                    val descElem = jsonObject["description"]
                    if (descElem !is kotlinx.serialization.json.JsonNull) {
                        val descPrim = descElem as? kotlinx.serialization.json.JsonPrimitive
                        require(descPrim != null && descPrim.isString && descPrim.content.trim().isNotEmpty()) {
                            "CREATE payload 'description' string ise boş/blank olamaz veya null olmalıdır: $operationId"
                        }
                    }
                }

                // created_at (optional): canonical UTC (Z) ISO-8601
                if (jsonObject.containsKey("created_at")) {
                    val createdElem = jsonObject["created_at"]
                    val createdPrim = createdElem as? kotlinx.serialization.json.JsonPrimitive
                    require(createdPrim != null && createdPrim.isString) {
                        "CREATE payload 'created_at' string olmalıdır: $operationId"
                    }
                    val createdStr = createdPrim.content.trim()
                    require(createdStr.endsWith("Z") || createdStr.endsWith("z")) {
                        "CREATE payload 'created_at' canonical UTC ('Z') ISO-8601 formatında olmalıdır: $createdStr ($operationId)"
                    }
                    try {
                        Instant.parse(createdStr)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("CREATE payload 'created_at' geçerli bir ISO-8601 UTC dizesi olmalıdır: $operationId", e)
                    }
                }
            }
            com.feniqo.mobile.data.remote.core.RemoteWriteOperation.UPDATE -> {
                require(baseVersion != null && baseVersion >= 1L) {
                    "UPDATE işlemi için baseVersion pozitif bir tamsayı (>= 1) olmalıdır: $operationId"
                }
                val allowedKeys = setOf("id", "name", "type_code", "currency_code", "description")
                val unknownKeys = jsonObject.keys - allowedKeys
                require(unknownKeys.isEmpty()) {
                    "UPDATE payload yasak veya bilinmeyen alanlar içeriyor: $unknownKeys ($operationId)"
                }

                // name
                val nameElem = jsonObject["name"]
                    ?: throw IllegalArgumentException("UPDATE payload 'name' alanı zorunludur: $operationId")
                val namePrim = nameElem as? kotlinx.serialization.json.JsonPrimitive
                require(namePrim != null && namePrim.isString && namePrim.content.isNotBlank()) {
                    "UPDATE payload 'name' string ve dolu olmalıdır: $operationId"
                }

                // type_code
                val typeElem = jsonObject["type_code"]
                    ?: throw IllegalArgumentException("UPDATE payload 'type_code' alanı zorunludur: $operationId")
                val typePrim = typeElem as? kotlinx.serialization.json.JsonPrimitive
                require(typePrim != null && typePrim.isString && typePrim.content in setOf("personal", "shared")) {
                    "UPDATE payload 'type_code' geçerli değil (personal|shared): $operationId"
                }

                // currency_code
                val currElem = jsonObject["currency_code"]
                    ?: throw IllegalArgumentException("UPDATE payload 'currency_code' alanı zorunludur: $operationId")
                val currPrim = currElem as? kotlinx.serialization.json.JsonPrimitive
                require(currPrim != null && currPrim.isString && currPrim.content in setOf("TRY", "USD", "EUR")) {
                    "UPDATE payload 'currency_code' geçerli değil (TRY|USD|EUR): $operationId"
                }

                // description: null kabul, string ise trim().isNotEmpty() zorunlu
                if (jsonObject.containsKey("description")) {
                    val descElem = jsonObject["description"]
                    if (descElem !is kotlinx.serialization.json.JsonNull) {
                        val descPrim = descElem as? kotlinx.serialization.json.JsonPrimitive
                        require(descPrim != null && descPrim.isString && descPrim.content.trim().isNotEmpty()) {
                            "UPDATE payload 'description' string ise boş/blank olamaz veya null olmalıdır: $operationId"
                        }
                    }
                }
            }
            com.feniqo.mobile.data.remote.core.RemoteWriteOperation.DELETE -> {
                require(baseVersion != null && baseVersion >= 1L) {
                    "DELETE işlemi için baseVersion pozitif bir tamsayı (>= 1) olmalıdır: $operationId"
                }
                val allowedKeys = setOf("id")
                val unknownKeys = jsonObject.keys - allowedKeys
                require(unknownKeys.isEmpty()) {
                    "DELETE payload yalnızca 'id' alanı içermelidir: $unknownKeys ($operationId)"
                }
            }
        }

        return jsonObject
    }
}
