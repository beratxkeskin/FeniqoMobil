package com.feniqo.mobile.data.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FeniqoDigestTest {
    @Test
    fun hashesEmptyStringCorrectly() {
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val hash = FeniqoDigest.sha256(ByteArray(0))
        val hex = bytesToHex(hash)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun hashesAbcCorrectly() {
        // ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val hash = FeniqoDigest.sha256("abc".encodeToByteArray())
        val hex = bytesToHex(hash)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef".toCharArray()
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return result.concatToString()
    }
}
