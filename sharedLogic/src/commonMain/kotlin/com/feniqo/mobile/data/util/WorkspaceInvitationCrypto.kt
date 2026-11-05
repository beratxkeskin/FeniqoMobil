package com.feniqo.mobile.data.util

import kotlin.random.Random

/**
 * Workspace davetleri için kriptografik yardımcılar.
 *
 * Güvenlik kuralları:
 * 1. Ham davet tokenı (raw token) kriptografik olarak tahmin edilemez (256-bit entropi).
 * 2. Sunucuda veya kalıcı depolamada ASLA ham token saklanmaz; yalnızca SHA-256 hash saklanır.
 * 3. Token hash 64 karakterli küçük harfli onaltılık (hexadecimal) dizedir.
 */
object WorkspaceInvitationCrypto {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private val HEX_64_REGEX = Regex("^[0-9a-f]{64}$")

    /**
     * 32 rastgele bayttan (256-bit) oluşan 64 karakterli güvenli ham davet tokenı üretir.
     */
    fun generateSecureInvitationToken(random: Random = Random.Default): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    /**
     * Verilen ham davet tokenının SHA-256 özetini (hex digest) hesaplar.
     * Metin başındaki ve sonundaki boşluklar normalize edilir.
     */
    fun hashInvitationToken(token: String): String {
        val cleanToken = token.trim()
        require(cleanToken.isNotEmpty()) { "Davet tokenı boş olamaz." }
        val bytes = cleanToken.encodeToByteArray()
        val digest = sha256(bytes)
        return bytesToHex(digest)
    }

    /**
     * Bir dize değerinin geçerli bir SHA-256 token_hash formatında (64 hex karakter) olup olmadığını doğrular.
     */
    fun isValidTokenHash(tokenHash: String): Boolean {
        return HEX_64_REGEX.matches(tokenHash.trim())
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[v ushr 4]
            result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return result.concatToString()
    }

    private fun sha256(data: ByteArray): ByteArray = FeniqoDigest.sha256(data)
}
