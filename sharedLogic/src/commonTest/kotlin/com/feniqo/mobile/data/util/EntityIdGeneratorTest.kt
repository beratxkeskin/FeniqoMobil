package com.feniqo.mobile.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EntityIdGeneratorTest {

    @Test
    fun nextId_generates32CharLowercaseHex() {
        val generator = RandomHexEntityIdGenerator()
        val id = generator.nextId()

        assertEquals(32, id.value.length)
        assertTrue(id.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun nextId_consecutiveGenerationsAreUniqueAndNotBlank() {
        val generator = RandomHexEntityIdGenerator()
        val id1 = generator.nextId()
        val id2 = generator.nextId()

        assertTrue(id1.value.isNotBlank())
        assertTrue(id2.value.isNotBlank())
        assertNotEquals(id1, id2)
    }
}
