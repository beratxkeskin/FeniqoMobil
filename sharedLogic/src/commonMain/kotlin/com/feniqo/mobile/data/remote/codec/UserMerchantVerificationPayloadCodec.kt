package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kişisel merchant doğrulama/engelleme mutasyonları için kanonik JSON payload kodlayıcı ve doğrulayıcı.
 * İstemci tarafı authoritative timestamp veya version göndermez; tüm zaman ve sürüm yönetimi sunucu otoritesindedir.
 */
object UserMerchantVerificationPayloadCodec {
    private val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            isLenient = false
        }

    val snapshotJson =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = true
            isLenient = false
        }

    fun encodeSnapshot(dto: UserMerchantVerificationDto): String =
        snapshotJson.encodeToString(UserMerchantVerificationDto.serializer(), dto)

    fun decodeAndValidateSnapshot(jsonString: String): UserMerchantVerificationDto {
        require(jsonString.isNotBlank()) { "Snapshot JSON boş olamaz." }
        val dto = runCatching {
            snapshotJson.decodeFromString(UserMerchantVerificationDto.serializer(), jsonString)
        }.getOrElse { e ->
            throw IllegalArgumentException("Geçersiz snapshot JSON formatı", e)
        }
        validateSnapshotDto(dto)
        return dto
    }

    private fun validateSnapshotDto(dto: UserMerchantVerificationDto) {
        require(UuidHelper.isCanonicalUuid(dto.id)) { "Geçersiz snapshot id: ${dto.id}" }
        require(UuidHelper.isCanonicalUuid(dto.userId)) { "Geçersiz snapshot userId: ${dto.userId}" }
        require(dto.workspaceId == null) { "Snapshot workspaceId null olmalıdır: ${dto.workspaceId}" }
        require(dto.scopeCode == "PERSONAL") { "Snapshot scopeCode PERSONAL olmalıdır: ${dto.scopeCode}" }
        require(dto.normalizedAlias.isNotBlank() && dto.normalizedAlias.length <= Transaction.MAX_DESCRIPTION_LENGTH) {
            "Geçersiz snapshot alias uzunluğu: ${dto.normalizedAlias.length}"
        }
        val canonicalAlias = TurkishDescriptionNormalizer.normalize(dto.normalizedAlias)
        require(dto.normalizedAlias == canonicalAlias) {
            "Snapshot alias normalize değil: ${dto.normalizedAlias}"
        }
        require(dto.normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Snapshot alias yasaklı genel kelime içeriyor: ${dto.normalizedAlias}"
        }
        val expectedId = UuidHelper.deterministicMerchantVerificationUuid(dto.userId, dto.normalizedAlias)
        require(dto.id == expectedId) {
            "Snapshot id deterministik UUID ile eşleşmiyor: beklenen $expectedId, gelen ${dto.id}"
        }
        when (dto.decisionCode) {
            "ASSIGN" -> require(!dto.merchantId.isNullOrBlank()) { "ASSIGN kararı için merchantId zorunludur." }
            "BLOCK" -> require(dto.merchantId == null) { "BLOCK kararı için merchantId null olmalıdır: ${dto.merchantId}" }
            else -> throw IllegalArgumentException("Desteklenmeyen decisionCode: ${dto.decisionCode}")
        }
        requireNotNull(dto.version) { "Snapshot version zorunludur: id=${dto.id}" }
        require(dto.version >= 0L) { "Snapshot version negatif olamaz: ${dto.version}" }

        require(dto.createdAt.isNotBlank()) { "Snapshot createdAt boş olamaz: id=${dto.id}" }
        val createdInstant = runCatching { Instant.parse(dto.createdAt) }.getOrNull()
        requireNotNull(createdInstant) { "Snapshot createdAt geçersiz ISO-8601 formatı: ${dto.createdAt}" }

        require(!dto.updatedAt.isNullOrBlank()) { "Snapshot updatedAt zorunludur: id=${dto.id}" }
        val updatedInstant = runCatching { Instant.parse(dto.updatedAt) }.getOrNull()
        requireNotNull(updatedInstant) { "Snapshot updatedAt geçersiz ISO-8601 formatı: ${dto.updatedAt}" }

        if (dto.deletedAt != null) {
            val deletedInstant = runCatching { Instant.parse(dto.deletedAt) }.getOrNull()
            requireNotNull(deletedInstant) { "Snapshot deletedAt geçersiz ISO-8601 formatı: ${dto.deletedAt}" }
        }
    }

    fun validateLocalSnapshotMatchesEntity(
        snapshot: UserMerchantVerificationDto,
        local: UserMerchantVerificationEntity,
    ) {
        require(snapshot.id == local.id) {
            "Local snapshot id (${snapshot.id}) yerel id (${local.id}) ile eşleşmiyor."
        }
        require(snapshot.userId == local.ownerId) {
            "Local snapshot userId (${snapshot.userId}) yerel ownerId (${local.ownerId}) ile eşleşmiyor."
        }
        require(snapshot.workspaceId == local.workspaceId) {
            "Local snapshot workspaceId (${snapshot.workspaceId}) yerel workspaceId (${local.workspaceId}) ile eşleşmiyor."
        }
        require(snapshot.normalizedAlias == local.normalizedAlias) {
            "Local snapshot normalizedAlias (${snapshot.normalizedAlias}) yerel alias (${local.normalizedAlias}) ile eşleşmiyor."
        }
        require(snapshot.decisionCode == local.decisionCode) {
            "Local snapshot decisionCode (${snapshot.decisionCode}) yerel decisionCode (${local.decisionCode}) ile eşleşmiyor."
        }
        require(snapshot.merchantId == local.merchantId) {
            "Local snapshot merchantId (${snapshot.merchantId}) yerel merchantId (${local.merchantId}) ile eşleşmiyor."
        }
        require(snapshot.scopeCode == local.scopeCode) {
            "Local snapshot scopeCode (${snapshot.scopeCode}) yerel scopeCode (${local.scopeCode}) ile eşleşmiyor."
        }
        require(snapshot.version == local.sync.version) {
            "Local snapshot version (${snapshot.version}) yerel version (${local.sync.version}) ile eşleşmiyor."
        }
        val snapshotCreatedMs = Instant.parse(snapshot.createdAt).toEpochMilliseconds()
        require(snapshotCreatedMs == local.createdAtEpochMillis) {
            "Local snapshot createdAt ($snapshotCreatedMs) yerel createdAt (${local.createdAtEpochMillis}) ile eşleşmiyor."
        }
        val snapshotUpdatedMs = Instant.parse(snapshot.updatedAt!!).toEpochMilliseconds()
        require(snapshotUpdatedMs == local.sync.updatedAtEpochMillis) {
            "Local snapshot updatedAt ($snapshotUpdatedMs) yerel updatedAt (${local.sync.updatedAtEpochMillis}) ile eşleşmiyor."
        }
        if (local.sync.deletedAtEpochMillis == null) {
            require(snapshot.deletedAt == null) {
                "Local snapshot deletedAt (${snapshot.deletedAt}) yerelde null beklenirken dolu."
            }
        } else {
            requireNotNull(snapshot.deletedAt) {
                "Local snapshot deletedAt null; yerelde ${local.sync.deletedAtEpochMillis} bekleniyordu."
            }
            val snapshotDeletedMs = Instant.parse(snapshot.deletedAt).toEpochMilliseconds()
            require(snapshotDeletedMs == local.sync.deletedAtEpochMillis) {
                "Local snapshot deletedAt ($snapshotDeletedMs) yerel deletedAt (${local.sync.deletedAtEpochMillis}) ile eşleşmiyor."
            }
        }
    }

    fun validateRemoteSnapshotMatchesEntity(
        snapshot: UserMerchantVerificationDto,
        remoteEntity: UserMerchantVerificationEntity,
    ) {
        require(snapshot.id == remoteEntity.id) {
            "Remote snapshot id (${snapshot.id}) remoteEntity id (${remoteEntity.id}) ile eşleşmiyor."
        }
        require(snapshot.userId == remoteEntity.ownerId) {
            "Remote snapshot userId (${snapshot.userId}) remoteEntity ownerId (${remoteEntity.ownerId}) ile eşleşmiyor."
        }
        require(snapshot.workspaceId == remoteEntity.workspaceId) {
            "Remote snapshot workspaceId (${snapshot.workspaceId}) remoteEntity workspaceId (${remoteEntity.workspaceId}) ile eşleşmiyor."
        }
        require(snapshot.normalizedAlias == remoteEntity.normalizedAlias) {
            "Remote snapshot normalizedAlias (${snapshot.normalizedAlias}) remoteEntity alias (${remoteEntity.normalizedAlias}) ile eşleşmiyor."
        }
        require(snapshot.decisionCode == remoteEntity.decisionCode) {
            "Remote snapshot decisionCode (${snapshot.decisionCode}) remoteEntity decisionCode (${remoteEntity.decisionCode}) ile eşleşmiyor."
        }
        require(snapshot.merchantId == remoteEntity.merchantId) {
            "Remote snapshot merchantId (${snapshot.merchantId}) remoteEntity merchantId (${remoteEntity.merchantId}) ile eşleşmiyor."
        }
        require(snapshot.scopeCode == remoteEntity.scopeCode) {
            "Remote snapshot scopeCode (${snapshot.scopeCode}) remoteEntity scopeCode (${remoteEntity.scopeCode}) ile eşleşmiyor."
        }
        require(snapshot.version == remoteEntity.sync.version) {
            "Remote snapshot version (${snapshot.version}) remoteEntity version (${remoteEntity.sync.version}) ile eşleşmiyor."
        }
        requireNotNull(snapshot.version)
        require(snapshot.version >= 1L) {
            "Remote snapshot version >= 1 olmalıdır: ${snapshot.version}"
        }
        val snapshotCreatedMs = Instant.parse(snapshot.createdAt).toEpochMilliseconds()
        require(snapshotCreatedMs == remoteEntity.createdAtEpochMillis) {
            "Remote snapshot createdAt ($snapshotCreatedMs) remoteEntity createdAt (${remoteEntity.createdAtEpochMillis}) ile eşleşmiyor."
        }
        val snapshotUpdatedMs = Instant.parse(snapshot.updatedAt!!).toEpochMilliseconds()
        require(snapshotUpdatedMs == remoteEntity.sync.updatedAtEpochMillis) {
            "Remote snapshot updatedAt ($snapshotUpdatedMs) remoteEntity updatedAt (${remoteEntity.sync.updatedAtEpochMillis}) ile eşleşmiyor."
        }
        if (remoteEntity.sync.deletedAtEpochMillis == null) {
            require(snapshot.deletedAt == null) {
                "Remote snapshot deletedAt (${snapshot.deletedAt}) remoteEntity içinde null beklenirken dolu."
            }
        } else {
            requireNotNull(snapshot.deletedAt) {
                "Remote snapshot deletedAt null; remoteEntity içinde ${remoteEntity.sync.deletedAtEpochMillis} bekleniyordu."
            }
            val snapshotDeletedMs = Instant.parse(snapshot.deletedAt).toEpochMilliseconds()
            require(snapshotDeletedMs == remoteEntity.sync.deletedAtEpochMillis) {
                "Remote snapshot deletedAt ($snapshotDeletedMs) remoteEntity deletedAt (${remoteEntity.sync.deletedAtEpochMillis}) ile eşleşmiyor."
            }
        }
    }

    @Serializable
    private data class CanonicalVerificationPayload(
        val id: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("workspace_id")
        val workspaceId: String?,
        @SerialName("normalized_alias")
        val normalizedAlias: String,
        @SerialName("decision_code")
        val decisionCode: String,
        @SerialName("merchant_id")
        val merchantId: String?,
        @SerialName("scope_code")
        val scopeCode: String,
    )

    data class ValidatedVerificationPayload(
        val id: String,
        val userId: String,
        val workspaceId: String?,
        val normalizedAlias: String,
        val decisionCode: String,
        val merchantId: String?,
        val scopeCode: String,
    )

    fun encodeCreate(entity: UserMerchantVerificationEntity): String {
        validateEntityInvariant(entity)
        val payload =
            CanonicalVerificationPayload(
                id = entity.id,
                userId = entity.ownerId,
                workspaceId = null,
                normalizedAlias = entity.normalizedAlias,
                decisionCode = entity.decisionCode,
                merchantId = entity.merchantId,
                scopeCode = "PERSONAL",
            )
        return json.encodeToString(CanonicalVerificationPayload.serializer(), payload)
    }

    fun encodeUpdate(entity: UserMerchantVerificationEntity): String {
        validateEntityInvariant(entity)
        val payload =
            CanonicalVerificationPayload(
                id = entity.id,
                userId = entity.ownerId,
                workspaceId = null,
                normalizedAlias = entity.normalizedAlias,
                decisionCode = entity.decisionCode,
                merchantId = entity.merchantId,
                scopeCode = "PERSONAL",
            )
        return json.encodeToString(CanonicalVerificationPayload.serializer(), payload)
    }

    fun encodeDelete(entity: UserMerchantVerificationEntity): String {
        validateEntityInvariant(entity)
        val payload =
            CanonicalVerificationPayload(
                id = entity.id,
                userId = entity.ownerId,
                workspaceId = null,
                normalizedAlias = entity.normalizedAlias,
                decisionCode = entity.decisionCode,
                merchantId = entity.merchantId,
                scopeCode = "PERSONAL",
            )
        return json.encodeToString(CanonicalVerificationPayload.serializer(), payload)
    }

    fun parseAndValidate(
        jsonString: String,
        operation: String,
        baseVersion: Long?,
    ): ValidatedVerificationPayload {
        require(jsonString.isNotBlank()) { "Payload boş olamaz." }

        when (operation) {
            "CREATE" -> require(baseVersion == null) { "CREATE operasyonu için baseVersion null olmalıdır." }
            "UPDATE", "DELETE" -> {
                requireNotNull(baseVersion) { "$operation operasyonu için baseVersion zorunludur." }
                require(baseVersion >= 1L) { "$operation operasyonu için baseVersion en az 1 olmalıdır." }
            }
            else -> error("Desteklenmeyen senkronizasyon operasyonu: $operation")
        }

        val jsonElement =
            try {
                json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                throw IllegalArgumentException("Bozuk veya şemaya uymayan merchant verification payload'ı: ${e.message}", e)
            }
        val jsonObject =
            jsonElement as? kotlinx.serialization.json.JsonObject
                ?: throw IllegalArgumentException("Bozuk veya şemaya uymayan merchant verification payload'ı: Payload bir JSON nesnesi olmalıdır.")

        require("workspace_id" in jsonObject) { "workspace_id anahtarı mutlaka bulunmalıdır." }
        val workspaceValue = jsonObject["workspace_id"]
        require(workspaceValue is kotlinx.serialization.json.JsonNull) {
            "workspace_id değeri açıkça JSON null olmalıdır."
        }

        require("scope_code" in jsonObject) { "scope_code anahtarı mutlaka bulunmalıdır." }
        val scopePrimitive = jsonObject["scope_code"] as? kotlinx.serialization.json.JsonPrimitive
        require(scopePrimitive != null && scopePrimitive.isString && scopePrimitive.content == "PERSONAL") {
            "scope_code anahtarı açıkça 'PERSONAL' olmalıdır."
        }

        if (operation in listOf("CREATE", "UPDATE")) {
            require("decision_code" in jsonObject) { "decision_code anahtarı bulunmalıdır." }
            require("merchant_id" in jsonObject) { "merchant_id anahtarı bulunmalıdır." }
        }

        val parsed =
            try {
                json.decodeFromJsonElement(CanonicalVerificationPayload.serializer(), jsonObject)
            } catch (e: Exception) {
                throw IllegalArgumentException("Bozuk veya şemaya uymayan merchant verification payload'ı: ${e.message}", e)
            }

        require(UuidHelper.isCanonicalUuid(parsed.id)) { "Geçersiz kanonik ID: ${parsed.id}" }
        require(UuidHelper.isCanonicalUuid(parsed.userId)) { "Geçersiz kanonik user_id: ${parsed.userId}" }
        require(parsed.workspaceId == null) { "Kişisel doğrulamada workspace_id null olmalıdır." }
        require(parsed.scopeCode == "PERSONAL") { "Yalnızca PERSONAL scope desteklenir: ${parsed.scopeCode}" }

        val canonicalAlias = TurkishDescriptionNormalizer.normalize(parsed.normalizedAlias)
        require(parsed.normalizedAlias.isNotBlank()) { "normalized_alias boş olamaz." }
        require(parsed.normalizedAlias.length <= Transaction.MAX_DESCRIPTION_LENGTH) {
            "normalized_alias ${Transaction.MAX_DESCRIPTION_LENGTH} karakteri geçemez."
        }
        require(parsed.normalizedAlias == canonicalAlias) {
            "normalized_alias kanonik normalizasyon biçiminde olmalıdır (beklenen: '$canonicalAlias', gelen: '${parsed.normalizedAlias}')."
        }
        require(parsed.normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Genel sözcük merchant alias olamaz: ${parsed.normalizedAlias}"
        }

        val expectedId = UuidHelper.deterministicMerchantVerificationUuid(parsed.userId, parsed.normalizedAlias)
        require(parsed.id == expectedId) {
            "Deterministik ID eşleşmiyor (beklenen: $expectedId, gelen: ${parsed.id})"
        }

        when (parsed.decisionCode) {
            "ASSIGN" ->
                require(!parsed.merchantId.isNullOrBlank()) {
                    "ASSIGN kararında merchant_id boş olamaz."
                }
            "BLOCK" ->
                require(parsed.merchantId == null) {
                    "BLOCK kararında merchant_id null olmalıdır."
                }
            else -> error("Geçersiz karar kodu: ${parsed.decisionCode}")
        }

        return ValidatedVerificationPayload(
            id = parsed.id,
            userId = parsed.userId,
            workspaceId = parsed.workspaceId,
            normalizedAlias = parsed.normalizedAlias,
            decisionCode = parsed.decisionCode,
            merchantId = parsed.merchantId,
            scopeCode = parsed.scopeCode,
        )
    }

    private fun validateEntityInvariant(entity: UserMerchantVerificationEntity) {
        require(UuidHelper.isCanonicalUuid(entity.id)) { "Entity id kanonik UUID olmalıdır: ${entity.id}" }
        require(UuidHelper.isCanonicalUuid(entity.ownerId)) { "Entity owner_id kanonik UUID olmalıdır: ${entity.ownerId}" }
        require(entity.workspaceId == null) { "Entity workspace_id null olmalıdır." }
        require(entity.scopeCode == "PERSONAL") { "Entity scope_code PERSONAL olmalıdır." }

        val canonicalAlias = TurkishDescriptionNormalizer.normalize(entity.normalizedAlias)
        require(entity.normalizedAlias == canonicalAlias) { "Entity normalized_alias kanonik olmalıdır." }
        require(entity.normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Entity normalized_alias genel sözcük olamaz."
        }

        val expectedId = UuidHelper.deterministicMerchantVerificationUuid(entity.ownerId, entity.normalizedAlias)
        require(entity.id == expectedId) {
            "Entity deterministik ID eşleşmiyor (beklenen: $expectedId, gelen: ${entity.id})"
        }

        when (entity.decisionCode) {
            "ASSIGN" -> require(!entity.merchantId.isNullOrBlank()) { "ASSIGN için merchant_id boş olamaz." }
            "BLOCK" -> require(entity.merchantId == null) { "BLOCK için merchant_id null olmalıdır." }
            else -> error("Geçersiz decision_code: ${entity.decisionCode}")
        }
    }
}
