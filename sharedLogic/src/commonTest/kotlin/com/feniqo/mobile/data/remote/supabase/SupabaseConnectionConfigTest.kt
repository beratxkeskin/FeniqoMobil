package com.feniqo.mobile.data.remote.supabase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupabaseConnectionConfigTest {
    @Test
    fun accepts_publishable_key_without_exposing_a_real_project_value() {
        val config = SupabaseConnectionConfig(
            projectUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_12345678901234567890",
        )

        assertEquals("https://example.supabase.co", config.projectUrl)
    }

    @Test
    fun rejects_secret_and_legacy_service_role_keys() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseConnectionConfig(
                projectUrl = "https://example.supabase.co",
                publishableKey = "sb_secret_12345678901234567890",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            SupabaseConnectionConfig(
                projectUrl = "https://example.supabase.co",
                // Payload: {"role":"service_role"}; bu gerçek veya imzalı bir anahtar değildir.
                publishableKey = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.fake",
            )
        }
    }
}
