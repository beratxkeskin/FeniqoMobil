package com.feniqo.mobile.data.remote.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Supabase erişim ve yenileme tokenlarını Android Keystore ile şifreleyerek saklar.
 * Diskte açık token bulunmaz; oturum dosyası Android yedeklerine de dahil edilmez.
 */
class AndroidSupabaseSessionManager(
    context: Context,
    private val keyStoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    sessionFileName: String = DEFAULT_SESSION_FILE_NAME,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {
    private val sessionFile = AtomicFile(File(context.applicationContext.noBackupFilesDir, sessionFileName))
    private val mutex = Mutex()

    override suspend fun saveSession(session: UserSession): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clearSession = json.encodeToString(session).encodeToByteArray()
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
                cipher.updateAAD(ASSOCIATED_DATA)
                writeEncryptedSession(cipher.iv, cipher.doFinal(clearSession))
            } finally {
                clearSession.fill(0)
            }
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(sessionFile.baseFile.exists()) { "Kayıtlı Supabase oturumu bulunamadı." }

            val wrappingKey = getExistingWrappingKey()
                ?: error("Supabase oturumunu koruyan Android Keystore anahtarı bulunamadı.")
            val encryptedSession = readEncryptedSession()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedSession.initializationVector),
            )
            cipher.updateAAD(ASSOCIATED_DATA)

            val clearSession = cipher.doFinal(encryptedSession.cipherText)
            try {
                json.decodeFromString<UserSession>(clearSession.decodeToString())
            } finally {
                clearSession.fill(0)
            }
        }
    }

    override suspend fun deleteSession(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            sessionFile.delete()
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey =
        getExistingWrappingKey() ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(AES_KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        // Arka plan senkronizasyonu oturumu yenileyebilmelidir; biyometri uygulama kilididir.
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
            }
            .generateKey()

    private fun getExistingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(keyStoreAlias)) return null
        return keyStore.getKey(keyStoreAlias, null) as? SecretKey
            ?: error("Supabase oturum Keystore girdisi geçerli bir AES anahtarı değil.")
    }

    private fun writeEncryptedSession(initializationVector: ByteArray, cipherText: ByteArray) {
        val output = sessionFile.startWrite()
        try {
            DataOutputStream(output).apply {
                writeInt(FILE_MAGIC)
                writeInt(FILE_FORMAT_VERSION)
                writeInt(initializationVector.size)
                write(initializationVector)
                writeInt(cipherText.size)
                write(cipherText)
                flush()
            }
            sessionFile.finishWrite(output)
        } catch (error: Exception) {
            sessionFile.failWrite(output)
            throw error
        }
    }

    private fun readEncryptedSession(): EncryptedSession =
        DataInputStream(sessionFile.openRead()).use { input ->
            require(input.readInt() == FILE_MAGIC) { "Supabase oturum dosyası imzası geçersiz." }
            require(input.readInt() == FILE_FORMAT_VERSION) { "Supabase oturum dosyası sürümü desteklenmiyor." }

            val ivSize = input.readInt()
            require(ivSize in MINIMUM_IV_BYTES..MAXIMUM_IV_BYTES) { "AES-GCM IV uzunluğu geçersiz." }
            val initializationVector = ByteArray(ivSize).also(input::readFully)

            val encryptedSize = input.readInt()
            require(encryptedSize in MINIMUM_ENCRYPTED_BYTES..MAXIMUM_ENCRYPTED_BYTES) {
                "Şifreli Supabase oturum uzunluğu geçersiz."
            }
            val cipherText = ByteArray(encryptedSize).also(input::readFully)
            require(input.read() == -1) { "Supabase oturum dosyası beklenmeyen ek veri içeriyor." }

            EncryptedSession(initializationVector, cipherText)
        }

    private data class EncryptedSession(
        val initializationVector: ByteArray,
        val cipherText: ByteArray,
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEYSTORE_ALIAS = "com.feniqo.mobile.supabase.session.v1"
        const val DEFAULT_SESSION_FILE_NAME = "feniqo_supabase_session_v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val FILE_MAGIC = 0x46535131
        const val FILE_FORMAT_VERSION = 1
        const val MINIMUM_IV_BYTES = 12
        const val MAXIMUM_IV_BYTES = 32
        const val MINIMUM_ENCRYPTED_BYTES = GCM_TAG_LENGTH_BITS / 8
        const val MAXIMUM_ENCRYPTED_BYTES = 1_048_576
        val ASSOCIATED_DATA = "com.feniqo.mobile.supabase-session.v1".encodeToByteArray()
    }
}
