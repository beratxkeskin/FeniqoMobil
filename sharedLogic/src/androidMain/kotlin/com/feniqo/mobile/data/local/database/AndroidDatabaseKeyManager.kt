package com.feniqo.mobile.data.local.database

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SQLCipher parolasını Android Keystore anahtarıyla korur.
 *
 * Keystore'daki AES anahtarı dışarı aktarılamaz. Diskte yalnızca AES-GCM ile
 * şifrelenmiş parola ve rastgele IV bulunur; açık parola kalıcı olarak yazılmaz.
 */
class AndroidDatabaseKeyManager(
    context: android.content.Context,
    private val databaseName: String = AndroidFeniqoDatabaseFactory.DATABASE_NAME,
    private val keyStoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    keyMaterialFileName: String = DEFAULT_KEY_MATERIAL_FILE_NAME,
) {
    private val applicationContext = context.applicationContext
    private val keyMaterialFile = AtomicFile(
        File(applicationContext.noBackupFilesDir, keyMaterialFileName),
    )

    /**
     * Var olan parolayı çözer veya ilk kurulumda kriptografik olarak rastgele parola üretir.
     * Var olan DB'nin anahtarı kayıpsa sessizce yeni parola üretmek veri kaybına yol açacağı için hata verir.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        if (keyMaterialFile.baseFile.exists()) {
            return decryptStoredPassphrase()
        }

        val databaseFile = applicationContext.getDatabasePath(databaseName)
        if (databaseFile.exists()) {
            throw DatabaseKeyUnavailableException(
                reason = DatabaseKeyFailure.EXISTING_DATABASE_WITHOUT_KEY_MATERIAL,
                message = "Var olan yerel veritabanı için anahtar malzemesi bulunamadı.",
            )
        }

        return createAndStorePassphrase()
    }

    private fun createAndStorePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)

        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
            cipher.updateAAD(ASSOCIATED_DATA)
            val encryptedPassphrase = cipher.doFinal(passphrase)

            writeKeyMaterial(
                KeyMaterial(
                    initializationVector = cipher.iv,
                    encryptedPassphrase = encryptedPassphrase,
                ),
            )
            return passphrase
        } catch (error: Exception) {
            passphrase.fill(0)
            if (error is DatabaseKeyUnavailableException) throw error
            throw DatabaseKeyUnavailableException(
                reason = DatabaseKeyFailure.KEY_CREATION_FAILED,
                message = "Veritabanı anahtarı oluşturulamadı.",
                cause = error,
            )
        }
    }

    private fun decryptStoredPassphrase(): ByteArray {
        val wrappingKey = getExistingWrappingKey()
            ?: throw DatabaseKeyUnavailableException(
                reason = DatabaseKeyFailure.KEYSTORE_ENTRY_MISSING,
                message = "Android Keystore veritabanı anahtarı bulunamadı.",
            )

        try {
            val material = readKeyMaterial()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, material.initializationVector),
            )
            cipher.updateAAD(ASSOCIATED_DATA)

            return cipher.doFinal(material.encryptedPassphrase).also { passphrase ->
                if (passphrase.size != PASSPHRASE_BYTES) {
                    passphrase.fill(0)
                    throw DatabaseKeyUnavailableException(
                        reason = DatabaseKeyFailure.CORRUPT_KEY_MATERIAL,
                        message = "Çözülen veritabanı anahtarı beklenen uzunlukta değil.",
                    )
                }
            }
        } catch (error: Exception) {
            if (error is DatabaseKeyUnavailableException) throw error
            throw DatabaseKeyUnavailableException(
                reason = DatabaseKeyFailure.CORRUPT_KEY_MATERIAL,
                message = "Veritabanı anahtar malzemesi okunamadı veya doğrulanamadı.",
                cause = error,
            )
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
                        // Arka plan senkronizasyonu DB'yi açabilmeli; biyometri ayrı bir uygulama kilididir.
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
            }
            .generateKey()

    private fun getExistingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(keyStoreAlias)) return null

        return keyStore.getKey(keyStoreAlias, null) as? SecretKey
            ?: throw DatabaseKeyUnavailableException(
                reason = DatabaseKeyFailure.KEYSTORE_ENTRY_INVALID,
                message = "Android Keystore girdisi geçerli bir AES anahtarı değil.",
            )
    }

    private fun writeKeyMaterial(material: KeyMaterial) {
        val output = keyMaterialFile.startWrite()
        try {
            DataOutputStream(output).apply {
                writeInt(FILE_MAGIC)
                writeInt(FILE_FORMAT_VERSION)
                writeInt(material.initializationVector.size)
                write(material.initializationVector)
                writeInt(material.encryptedPassphrase.size)
                write(material.encryptedPassphrase)
                flush()
            }
            keyMaterialFile.finishWrite(output)
        } catch (error: Exception) {
            keyMaterialFile.failWrite(output)
            throw error
        }
    }

    private fun readKeyMaterial(): KeyMaterial =
        DataInputStream(keyMaterialFile.openRead()).use { input ->
            require(input.readInt() == FILE_MAGIC) { "Anahtar dosyası imzası geçersiz." }
            require(input.readInt() == FILE_FORMAT_VERSION) { "Anahtar dosyası sürümü desteklenmiyor." }

            val ivSize = input.readInt()
            require(ivSize in MINIMUM_IV_BYTES..MAXIMUM_IV_BYTES) { "AES-GCM IV uzunluğu geçersiz." }
            val initializationVector = ByteArray(ivSize).also(input::readFully)

            val encryptedSize = input.readInt()
            require(encryptedSize in MINIMUM_ENCRYPTED_BYTES..MAXIMUM_ENCRYPTED_BYTES) {
                "Şifreli anahtar uzunluğu geçersiz."
            }
            val encryptedPassphrase = ByteArray(encryptedSize).also(input::readFully)

            require(input.read() == -1) { "Anahtar dosyası beklenmeyen ek veri içeriyor." }
            KeyMaterial(initializationVector, encryptedPassphrase)
        }

    private data class KeyMaterial(
        val initializationVector: ByteArray,
        val encryptedPassphrase: ByteArray,
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEYSTORE_ALIAS = "com.feniqo.mobile.database.wrap.v1"
        const val DEFAULT_KEY_MATERIAL_FILE_NAME = "feniqo_database_key_v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val PASSPHRASE_BYTES = 32
        const val FILE_MAGIC = 0x464B5131
        const val FILE_FORMAT_VERSION = 1
        const val MINIMUM_IV_BYTES = 12
        const val MAXIMUM_IV_BYTES = 32
        const val MINIMUM_ENCRYPTED_BYTES = PASSPHRASE_BYTES + (GCM_TAG_LENGTH_BITS / 8)
        const val MAXIMUM_ENCRYPTED_BYTES = 128
        val ASSOCIATED_DATA = "com.feniqo.mobile.database-passphrase.v1".encodeToByteArray()
    }
}

enum class DatabaseKeyFailure {
    EXISTING_DATABASE_WITHOUT_KEY_MATERIAL,
    KEYSTORE_ENTRY_MISSING,
    KEYSTORE_ENTRY_INVALID,
    KEY_CREATION_FAILED,
    CORRUPT_KEY_MATERIAL,
}

class DatabaseKeyUnavailableException(
    val reason: DatabaseKeyFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
