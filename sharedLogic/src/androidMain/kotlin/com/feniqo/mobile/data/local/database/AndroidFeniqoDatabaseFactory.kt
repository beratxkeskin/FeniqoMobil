package com.feniqo.mobile.data.local.database

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android'de Room'u SQLCipher ile açar.
 *
 * Parola burada üretilmez veya saklanmaz. Bir sonraki katman parolayı Android
 * Keystore ile koruyup bu fabrikaya verir; böylece ortak KMP kodu Android'e bağlanmaz.
 */
class AndroidFeniqoDatabaseFactory(
    context: Context,
    private val databaseName: String = DATABASE_NAME,
) {
    private val applicationContext = context.applicationContext

    fun create(keyManager: AndroidDatabaseKeyManager): FeniqoDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()
        return try {
            create(passphrase)
        } finally {
            // Fabrika SQLCipher için kendi kopyasını alır; çağıranın kopyası bellekte tutulmaz.
            passphrase.fill(0)
        }
    }

    fun create(passphrase: ByteArray): FeniqoDatabase {
        require(passphrase.size >= MINIMUM_PASSPHRASE_BYTES) {
            "Veritabanı parolası en az $MINIMUM_PASSPHRASE_BYTES bayt olmalıdır."
        }

        // SQLCipher AAR içindeki native kütüphane kullanımdan önce yüklenmelidir.
        System.loadLibrary(SQLCIPHER_LIBRARY_NAME)

        val databaseFile = applicationContext.getDatabasePath(databaseName)
        val openHelperFactory = SupportOpenHelperFactory(passphrase.copyOf())

        return Room.databaseBuilder<FeniqoDatabase>(
            context = applicationContext,
            name = databaseFile.absolutePath,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            // setDriver kullanılmaz: SQLCipher, Android SupportSQLite uyumluluk katmanıdır.
            .openHelperFactory(openHelperFactory)
            .addMigrations(ANDROID_MIGRATION_1_2)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    companion object {
        const val DATABASE_NAME = "feniqo.db"
        private const val SQLCIPHER_LIBRARY_NAME = "sqlcipher"
        private const val MINIMUM_PASSPHRASE_BYTES = 32
    }
}
