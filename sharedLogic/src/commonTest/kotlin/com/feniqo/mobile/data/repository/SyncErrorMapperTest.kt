package com.feniqo.mobile.data.repository

import androidx.sqlite.SQLiteException
import com.feniqo.mobile.domain.model.AppError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncErrorMapperTest {

    private suspend fun mockResponse(status: HttpStatusCode): HttpResponse {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond(content = "", status = status) }
            }
        }
        return client.get("https://example.com")
    }

    @Test
    fun maps_serialization_exception_to_validation_error() {
        val error = SerializationException("Invalid json format").toSyncAppError()
        assertTrue(error is AppError.Validation)
        assertEquals("sync.serialization_failed", error.code)
    }

    @Test
    fun maps_illegal_argument_exception_to_validation_error() {
        val error = IllegalArgumentException("Field cannot be negative").toSyncAppError()
        assertTrue(error is AppError.Validation)
        assertEquals("sync.invalid_argument", error.code)
    }

    @Test
    fun maps_illegal_state_exception_to_validation_error_without_conflict_string_matching() {
        val errorWithMessage = IllegalStateException("conflict occurred in state").toSyncAppError()
        assertTrue(errorWithMessage is AppError.Validation)
        assertEquals("sync.invalid_state", errorWithMessage.code)

        val stateError = IllegalStateException("Cursor state is invalid").toSyncAppError()
        assertTrue(stateError is AppError.Validation)
        assertEquals("sync.invalid_state", stateError.code)
    }

    @Test
    fun maps_ktor_and_supabase_network_exceptions_to_network_error() {
        val socketError = SocketTimeoutException("Connection timed out").toSyncAppError()
        assertTrue(socketError is AppError.Network)
        assertEquals("sync.network_failed", socketError.code)

        val unresolvedError = UnresolvedAddressException().toSyncAppError()
        assertTrue(unresolvedError is AppError.Network)
        assertEquals("sync.network_failed", unresolvedError.code)

        val ioError = IOException("Pipe broken").toSyncAppError()
        assertTrue(ioError is AppError.Network)
        assertEquals("sync.network_failed", ioError.code)

        val httpReqError = HttpRequestException("Cannot reach server", io.ktor.client.request.HttpRequestBuilder()).toSyncAppError()
        assertTrue(httpReqError is AppError.Network)
        assertEquals("sync.network_unavailable", httpReqError.code)
    }

    @Test
    fun maps_rest_exception_status_codes_according_to_contract() = runTest {
        // 401 & 403 -> Authentication
        val rest401 = RestException(error = "Unauthorized", description = null, response = mockResponse(HttpStatusCode.Unauthorized)).toSyncAppError()
        assertTrue(rest401 is AppError.Authentication)
        assertEquals("sync.unauthorized", rest401.code)

        val rest403 = RestException(error = "Forbidden", description = null, response = mockResponse(HttpStatusCode.Forbidden)).toSyncAppError()
        assertTrue(rest403 is AppError.Authentication)
        assertEquals("sync.unauthorized", rest403.code)

        // 400, 404, 422 -> Validation
        val rest400 = RestException(error = "Bad Request", description = null, response = mockResponse(HttpStatusCode.BadRequest)).toSyncAppError()
        assertTrue(rest400 is AppError.Validation)
        assertEquals("sync.invalid_request", rest400.code)

        val rest404 = RestException(error = "Not Found", description = null, response = mockResponse(HttpStatusCode.NotFound)).toSyncAppError()
        assertTrue(rest404 is AppError.Validation)
        assertEquals("sync.invalid_request", rest404.code)

        val rest422 = RestException(error = "Unprocessable", description = null, response = mockResponse(HttpStatusCode.UnprocessableEntity)).toSyncAppError()
        assertTrue(rest422 is AppError.Validation)
        assertEquals("sync.invalid_request", rest422.code)

        // 409 -> Conflict
        val rest409 = RestException(error = "Conflict", description = null, response = mockResponse(HttpStatusCode.Conflict)).toSyncAppError()
        assertTrue(rest409 is AppError.Conflict)
        assertEquals("sync.remote_conflict", rest409.code)

        // 408, 429, 500..599 -> Network
        val rest408 = RestException(error = "Request Timeout", description = null, response = mockResponse(HttpStatusCode.RequestTimeout)).toSyncAppError()
        assertTrue(rest408 is AppError.Network)
        assertEquals("sync.http_error_408", rest408.code)

        val rest429 = RestException(error = "Too Many Requests", description = null, response = mockResponse(HttpStatusCode.TooManyRequests)).toSyncAppError()
        assertTrue(rest429 is AppError.Network)
        assertEquals("sync.http_error_429", rest429.code)

        val rest500 = RestException(error = "Internal Server Error", description = null, response = mockResponse(HttpStatusCode.InternalServerError)).toSyncAppError()
        assertTrue(rest500 is AppError.Network)
        assertEquals("sync.http_error_500", rest500.code)

        val rest503 = RestException(error = "Service Unavailable", description = null, response = mockResponse(HttpStatusCode.ServiceUnavailable)).toSyncAppError()
        assertTrue(rest503 is AppError.Network)
        assertEquals("sync.http_error_503", rest503.code)

        // Other status -> Unknown
        val rest302 = RestException(error = "Found", description = null, response = mockResponse(HttpStatusCode.Found)).toSyncAppError()
        assertTrue(rest302 is AppError.Unknown)
        assertEquals("sync.http_error_302", rest302.code)
    }

    @Test
    fun maps_auth_rest_exceptions_according_to_auth_error_code() = runTest {
        val dummyResponse = mockResponse(HttpStatusCode.BadRequest)

        val conflictAuth = AuthRestException(errorCode = "user_already_exists", errorDescription = "User already exists", response = dummyResponse)
        val conflictError = conflictAuth.toSyncAppError()
        assertTrue(conflictError is AppError.Conflict)
        assertEquals("sync.remote_conflict", conflictError.code)

        val weakPass = AuthRestException(errorCode = "weak_password", errorDescription = "Weak password", response = dummyResponse).toSyncAppError()
        assertTrue(weakPass is AppError.Validation)
        assertEquals("sync.invalid_request", weakPass.code)

        val rateLimit = AuthRestException(errorCode = "over_request_rate_limit", errorDescription = "Rate limit", response = dummyResponse).toSyncAppError()
        assertTrue(rateLimit is AppError.Network)
        assertEquals("sync.rate_limited", rateLimit.code)

        val invalidCreds = AuthRestException(errorCode = "invalid_credentials", errorDescription = "Invalid credentials", response = dummyResponse).toSyncAppError()
        assertTrue(invalidCreds is AppError.Authentication)
        assertEquals("sync.auth_failed", invalidCreds.code)
    }

    @Test
    fun maps_sqlite_exception_to_storage_error() {
        val sqliteError = SQLiteException("FOREIGN KEY constraint failed").toSyncAppError()
        assertTrue(sqliteError is AppError.Storage)
        assertEquals("sync.storage_failed", sqliteError.code)
    }

    @Test
    fun maps_unknown_exceptions_to_unknown_error() {
        val unknown = RuntimeException("Something arbitrary happened").toSyncAppError()
        assertTrue(unknown is AppError.Unknown)
        assertEquals("sync.unknown_error", unknown.code)
    }
}

