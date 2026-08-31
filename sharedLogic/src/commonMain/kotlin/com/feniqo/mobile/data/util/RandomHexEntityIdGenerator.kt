package com.feniqo.mobile.data.util

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import kotlin.random.Random

class RandomHexEntityIdGenerator(
    private val random: Random = Random.Default,
) : EntityIdGenerator {
    override fun nextId(): EntityId {
        val bytes = random.nextBytes(16)
        val hex = buildString(32) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                val high = (v ushr 4).toString(16)
                val low = (v and 0x0F).toString(16)
                append(high)
                append(low)
            }
        }
        return EntityId(hex)
    }
}
