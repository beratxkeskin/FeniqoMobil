package com.feniqo.mobile.data.local.codec

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface TransactionSplitDecodeResult {
    data class Valid(
        val splitMode: TransactionSplitMode,
        val shares: List<TransactionParticipantShare>,
    ) : TransactionSplitDecodeResult

    sealed interface Invalid : TransactionSplitDecodeResult {
        val reason: String

        data class UnknownSplitMode(val rawMode: String) : Invalid {
            override val reason: String = "Bilinmeyen split mode: $rawMode"
        }

        data class MalformedJson(val detail: String) : Invalid {
            override val reason: String = "Bozuk JSON: $detail"
        }

        data class DuplicateUser(val userId: String) : Invalid {
            override val reason: String = "Tekrarlanan katılımcı payı: $userId"
        }

        data class BlankUserId(val index: Int) : Invalid {
            override val reason: String = "Boş veya geçersiz kullanıcı kimliği: index $index"
        }

        data class EmptySharesForCustom(val detail: String = "CUSTOM modunda pay listesi boş olamaz.") : Invalid {
            override val reason: String = detail
        }

        data class NonEmptySharesForEqual(val count: Int) : Invalid {
            override val reason: String = "EQUAL modunda pay listesi boş olmalıdır (bulunan: $count)"
        }

        data class NegativeShare(val userId: String, val amountMinor: Long) : Invalid {
            override val reason: String = "Katılımcı payı negatif olamaz: $userId -> $amountMinor"
        }
    }
}

@Serializable
private data class ParticipantSharePersistenceDto(
    val userId: String,
    val amountMinor: Long,
)

object TransactionSplitPersistenceCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * Katılımcı paylarını deterministik ve kanonik JSON biçiminde kodlar.
     * Paylar kodlamadan önce userId.value artan sırasına göre sıralanır;
     * böylece aynı semantik pay kümesi byte düzeyinde birebir aynı JSON dizesini üretir.
     */
    fun encode(shares: List<TransactionParticipantShare>): String {
        if (shares.isEmpty()) return "[]"
        val canonicalList = shares
            .sortedBy { it.userId.value }
            .map { ParticipantSharePersistenceDto(userId = it.userId.value, amountMinor = it.amountMinor) }
        return json.encodeToString(canonicalList)
    }

    /**
     * Room persistence katmanındaki split_mode ve participant_shares_json alanlarını tipli sonuca çözer.
     * Bilinmeyen modlar, bozuk JSON, tekrarlanan veya boş kullanıcılar ve kural dışı paylar fail-closed olarak Invalid döner.
     */
    fun decode(splitModeCode: String, participantSharesJson: String): TransactionSplitDecodeResult {
        val trimmedJson = participantSharesJson.trim()

        val parsedMode = when (splitModeCode.trim().uppercase()) {
            TransactionSplitMode.EQUAL.name -> TransactionSplitMode.EQUAL
            TransactionSplitMode.CUSTOM.name -> TransactionSplitMode.CUSTOM
            else -> return TransactionSplitDecodeResult.Invalid.UnknownSplitMode(splitModeCode)
        }

        val dtoList = try {
            if (trimmedJson.isEmpty() || trimmedJson == "[]") {
                emptyList()
            } else {
                json.decodeFromString<List<ParticipantSharePersistenceDto>>(trimmedJson)
            }
        } catch (e: SerializationException) {
            return TransactionSplitDecodeResult.Invalid.MalformedJson(e.message ?: "JSON deserialization failure")
        } catch (e: IllegalArgumentException) {
            return TransactionSplitDecodeResult.Invalid.MalformedJson(e.message ?: "Invalid JSON format")
        }

        when (parsedMode) {
            TransactionSplitMode.EQUAL -> {
                if (dtoList.isNotEmpty()) {
                    return TransactionSplitDecodeResult.Invalid.NonEmptySharesForEqual(dtoList.size)
                }
                return TransactionSplitDecodeResult.Valid(
                    splitMode = TransactionSplitMode.EQUAL,
                    shares = emptyList(),
                )
            }

            TransactionSplitMode.CUSTOM -> {
                if (dtoList.isEmpty()) {
                    return TransactionSplitDecodeResult.Invalid.EmptySharesForCustom()
                }

                val seenUserIds = mutableSetOf<String>()
                val shares = ArrayList<TransactionParticipantShare>(dtoList.size)

                for ((index, dto) in dtoList.withIndex()) {
                    if (dto.userId.isBlank()) {
                        return TransactionSplitDecodeResult.Invalid.BlankUserId(index)
                    }
                    if (dto.amountMinor < 0L) {
                        return TransactionSplitDecodeResult.Invalid.NegativeShare(dto.userId, dto.amountMinor)
                    }
                    if (!seenUserIds.add(dto.userId)) {
                        return TransactionSplitDecodeResult.Invalid.DuplicateUser(dto.userId)
                    }
                    shares.add(TransactionParticipantShare(userId = EntityId(dto.userId), amountMinor = dto.amountMinor))
                }

                return TransactionSplitDecodeResult.Valid(
                    splitMode = TransactionSplitMode.CUSTOM,
                    shares = shares,
                )
            }
        }
    }
}
