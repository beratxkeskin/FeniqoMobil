package com.feniqo.mobile.di

import com.feniqo.mobile.data.util.RandomUuidEntityIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceUseCaseModuleTest {

    @Test
    fun provideEntityIdGenerator_returnsRandomUuidEntityIdGenerator() {
        val module = FinanceUseCaseModule
        val generator = module.provideEntityIdGenerator()

        assertTrue("Expected RandomUuidEntityIdGenerator but was ${generator::class}", generator is RandomUuidEntityIdGenerator)

        val canonicalLowerRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val ids = (1..100).map { generator.nextId().value }

        assertEquals("All 100 generated IDs should be unique", 100, ids.toSet().size)
        for (id in ids) {
            assertEquals(36, id.length)
            assertTrue("ID $id must be canonical RFC-4122 v4 lowercase UUID", canonicalLowerRegex.matches(id))
        }
    }
}
