package com.feniqo.mobile.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Kaynak koda anahtar koymadan platform build configuration tarafından sağlanır. */
data class SupabaseConnectionConfig(
    val projectUrl: String,
    val publishableKey: String,
) {
    init {
        require(projectUrl == projectUrl.trim()) { "Supabase URL başında/sonunda boşluk olamaz." }
        require(projectUrl.startsWith("https://")) { "Supabase URL HTTPS kullanmalıdır." }
        require(!projectUrl.endsWith('/')) { "Supabase URL sonunda / bulunmamalıdır." }
        require(publishableKey == publishableKey.trim()) { "Supabase anahtarı boşluk içeremez." }
        requirePublishableKey(publishableKey)
    }
}

/** Supabase istemcisini UI'dan uzakta, ortak data katmanında oluşturur. */
object FeniqoSupabaseClientFactory {
    fun create(
        config: SupabaseConnectionConfig,
        sessionManager: SessionManager? = null,
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = config.projectUrl,
        supabaseKey = config.publishableKey,
    ) {
        install(Auth) {
            minimalConfig()
            if (sessionManager != null) {
                this.sessionManager = sessionManager
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
        }
        install(Postgrest)
        install(Storage)
    }
}

private fun requirePublishableKey(key: String) {
    require(key.isNotBlank()) { "Supabase publishable key boş olamaz." }
    require(!key.startsWith(SECRET_KEY_PREFIX)) { "Supabase secret/service-role anahtarı mobil uygulamaya konulamaz." }

    if (key.startsWith(PUBLISHABLE_KEY_PREFIX)) {
        require(key.length > PUBLISHABLE_KEY_PREFIX.length + 16) { "Supabase publishable key geçersiz görünüyor." }
        return
    }

    val legacyRole = readLegacyJwtRole(key)
    require(legacyRole == LEGACY_ANON_ROLE) {
        "Mobil uygulama yalnızca publishable veya legacy anon key kullanabilir."
    }
}

private fun readLegacyJwtRole(key: String): String? = runCatching {
    val payload = key.split('.').getOrNull(1) ?: return null
    val json = decodeBase64Url(payload).decodeToString()
    Json.parseToJsonElement(json).jsonObject["role"]?.jsonPrimitive?.contentOrNull
}.getOrNull()

/** Legacy JWT payload'ını imza doğrulaması için değil, yanlış key türünü reddetmek için okur. */
private fun decodeBase64Url(value: String): ByteArray {
    val output = ArrayList<Byte>((value.length * 3) / 4)
    var bitBuffer = 0
    var bitCount = 0

    value.trimEnd('=').forEach { character ->
        val index = BASE64_URL_ALPHABET.indexOf(character)
        require(index >= 0) { "Geçersiz base64url karakteri." }
        bitBuffer = (bitBuffer shl 6) or index
        bitCount += 6
        if (bitCount >= 8) {
            bitCount -= 8
            output += ((bitBuffer shr bitCount) and 0xFF).toByte()
        }
    }
    return output.toByteArray()
}

private const val PUBLISHABLE_KEY_PREFIX = "sb_publishable_"
private const val SECRET_KEY_PREFIX = "sb_secret_"
private const val LEGACY_ANON_ROLE = "anon"
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
