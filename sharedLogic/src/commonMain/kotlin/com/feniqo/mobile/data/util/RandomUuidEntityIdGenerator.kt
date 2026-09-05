package com.feniqo.mobile.data.util

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import kotlin.random.Random

/**
 * RFC 4122 v4 UUID formatında EntityId üreten generator.
 * D1 sync_write_v2 RPC'sinin ve Postgres UUID alanlarının (8-4-4-4-12 hyphenated hex)
 * zorunlu kıldığı canonical UUID sözleşmesine tam uyumludur.
 */
class RandomUuidEntityIdGenerator(
    private val random: Random = Random.Default,
) : EntityIdGenerator {

    override fun nextId(): EntityId {
        val bytes = random.nextBytes(16)
        // RFC 4122 v4: set version to 4 (0100) and variant to IETF (10xx)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80.toInt()).toByte()

        val uuidString = buildString(36) {
            for (i in 0 until 16) {
                if (i == 4 || i == 6 || i == 8 || i == 10) {
                    append('-')
                }
                val v = bytes[i].toInt() and 0xFF
                val high = (v ushr 4).toString(16)
                val low = (v and 0x0F).toString(16)
                append(high)
                append(low)
            }
        }
        return EntityId(uuidString)
    }
}
