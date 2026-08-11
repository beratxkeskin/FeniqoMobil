package com.feniqo.mobile.data.local.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val suffix = System.nanoTime().toString()
    private val databaseName = "feniqo-encryption-test-$suffix.db"
    private val keyStoreAlias = "com.feniqo.mobile.test.database.$suffix"
    private val keyMaterialFileName = "feniqo_database_test_key_$suffix"

    @Before
    fun prepare() = cleanup()

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
        File(context.noBackupFilesDir, keyMaterialFileName).delete()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(keyStoreAlias)) deleteEntry(keyStoreAlias)
        }
    }

    @Test
    fun database_is_encrypted_and_reopens_only_with_the_keystore_protected_passphrase() {
        val keyManager = AndroidDatabaseKeyManager(
            context = context,
            databaseName = databaseName,
            keyStoreAlias = keyStoreAlias,
            keyMaterialFileName = keyMaterialFileName,
        )
        val databaseFactory = AndroidFeniqoDatabaseFactory(context, databaseName)

        val firstPassphrase = keyManager.getOrCreatePassphrase()
        val firstDatabase = databaseFactory.create(firstPassphrase)
        try {
            firstDatabase.openHelper.writableDatabase
        } finally {
            firstDatabase.close()
        }

        val databaseFile = context.getDatabasePath(databaseName)
        val sqlitePlaintextHeader = "SQLite format 3\u0000".encodeToByteArray()
        val actualHeader = databaseFile.inputStream().use { it.readNBytes(sqlitePlaintextHeader.size) }
        assertFalse("SQLCipher dosyası düz SQLite başlığı taşımamalıdır.", actualHeader.contentEquals(sqlitePlaintextHeader))

        val restoredPassphrase = keyManager.getOrCreatePassphrase()
        assertArrayEquals(firstPassphrase, restoredPassphrase)
        val reopenedDatabase = databaseFactory.create(restoredPassphrase)
        try {
            reopenedDatabase.openHelper.writableDatabase
        } finally {
            reopenedDatabase.close()
        }

        val wrongPassphrase = ByteArray(32) { 0x5A }
        assertThrows(Exception::class.java) {
            val wrongDatabase = databaseFactory.create(wrongPassphrase)
            try {
                wrongDatabase.openHelper.writableDatabase
            } finally {
                wrongDatabase.close()
            }
        }

        firstPassphrase.fill(0)
        restoredPassphrase.fill(0)
        wrongPassphrase.fill(0)
    }
}
