package com.feniqo.mobile.data.remote.auth

import com.feniqo.mobile.domain.model.AppError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseAuthErrorMapperTest {

    @Test
    fun maps_auth_codes_to_stable_domain_errors() {
        assertEquals(
            AppError.Authentication("auth_invalid_credentials"),
            AuthErrorCode.InvalidCredentials.toAppError(),
        )
        assertEquals(
            AppError.Conflict("auth_email_already_registered"),
            AuthErrorCode.UserAlreadyExists.toAppError(),
        )
        assertEquals(
            AppError.Validation("auth_invalid_input"),
            AuthErrorCode.WeakPassword.toAppError(),
        )
        assertEquals(
            AppError.Network("auth_rate_limited"),
            AuthErrorCode.RequestTimeout.toAppError(),
        )
    }
}
