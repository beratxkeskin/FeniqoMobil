package com.feniqo.mobile.presentation.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UserAvatarManagerTest {

    private lateinit var context: Context
    private lateinit var avatarManager: UserAvatarManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        avatarManager = UserAvatarManager(context)
        // Temiz bir durum için avatars dizinini sıfırla
        val avatarDir = File(context.filesDir, "avatars")
        if (avatarDir.exists()) {
            avatarDir.deleteRecursively()
        }
        val cameraDir = File(context.cacheDir, "camera")
        if (cameraDir.exists()) {
            cameraDir.deleteRecursively()
        }
    }

    @Test
    fun validateAndSanitizeUserId_rejectsMaliciousPaths() {
        // Path traversal denemeleri
        assertNull(avatarManager.validateAndSanitizeUserId("../../etc/passwd"))
        assertNull(avatarManager.validateAndSanitizeUserId("../avatars"))
        assertNull(avatarManager.validateAndSanitizeUserId("user/123"))
        assertNull(avatarManager.validateAndSanitizeUserId("user\\123"))
        assertNull(avatarManager.validateAndSanitizeUserId(""))
        assertNull(avatarManager.validateAndSanitizeUserId("   "))

        // Geçerli kimlikler
        assertEquals("user-123_abc", avatarManager.validateAndSanitizeUserId("user-123_abc"))
        assertEquals("valid_user-ID", avatarManager.validateAndSanitizeUserId("  valid_user-ID  "))
    }

    @Test
    fun saveAvatarBitmap_rejectsMaliciousUserId_andDoesNotEscapeAvatarsDir() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val maliciousId = "../../etc/passwd"
        val saved = avatarManager.saveAvatarBitmap(maliciousId, bitmap)
        assertFalse(saved)
        assertNull(avatarManager.getAvatarFile(maliciousId))
    }

    @Test
    fun getAvatarFile_withBlankOrEmptyUserId_returnsNull() {
        assertNull(avatarManager.getAvatarFile(""))
        assertNull(avatarManager.getAvatarFile("   "))
    }

    @Test
    fun saveAvatarBitmap_savesAtomicallyViaTmp_andReturnsSuccess() {
        val userId = "user-abc-123"
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val success = avatarManager.saveAvatarBitmap(userId, bitmap)
        assertTrue(success)

        val avatarFile = avatarManager.getAvatarFile(userId)
        assertNotNull(avatarFile)
        assertTrue(avatarFile!!.exists())
        assertTrue(avatarFile.length() > 0)

        // Dosyanın parent'ı kesinlikle avatars dizini olmalı
        val avatarsDir = File(context.filesDir, "avatars").canonicalFile
        assertEquals(avatarsDir, avatarFile.canonicalFile.parentFile)

        // Geçici tmp dosyasının geride kalmadığını doğrula
        val tmpFile = File(avatarFile.parentFile, "${avatarFile.name}.tmp")
        assertFalse(tmpFile.exists())
    }

    @Test
    fun saveAvatarBitmap_overwritesExistingAvatarCleanly() {
        val userId = "user-overwrite-test"
        val bitmap1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)

        avatarManager.saveAvatarBitmap(userId, bitmap1)
        val file1 = avatarManager.getAvatarFile(userId)
        assertNotNull(file1)

        val success = avatarManager.saveAvatarBitmap(userId, bitmap2)
        assertTrue(success)

        val file2 = avatarManager.getAvatarFile(userId)
        assertNotNull(file2)
        assertTrue(file2!!.exists())
    }

    @Test
    fun deleteAvatar_removesTargetFile() {
        val userId = "user-to-delete"
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        avatarManager.saveAvatarBitmap(userId, bitmap)

        val fileBefore = avatarManager.getAvatarFile(userId)
        assertTrue(fileBefore?.exists() == true)

        val deleted = avatarManager.deleteAvatar(userId)
        assertTrue(deleted)
        assertFalse(fileBefore!!.exists())
    }

    @Test
    fun createTempCameraFile_failClosed_neverReturnsExposedFileScheme() {
        val result = avatarManager.createTempCameraFile()
        if (result != null) {
            val (tempFile, contentUri) = result
            assertEquals("content", contentUri.scheme)
            val cameraDir = File(context.cacheDir, "camera").canonicalFile
            assertEquals(cameraDir, tempFile.canonicalFile.parentFile)
        }
        avatarManager.clearTempCameraFiles()
    }

    @Test
    fun saveAvatarBitmap_preservesExistingAvatar_whenUpdateFails() {
        val userId = "user-preserve-test"
        val bitmapOriginal = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        val initialSaved = avatarManager.saveAvatarBitmap(userId, bitmapOriginal)
        assertTrue(initialSaved)

        val originalFile = avatarManager.getAvatarFile(userId)
        assertNotNull(originalFile)
        val originalBytes = originalFile!!.readBytes()

        // Başarısız işlem denemesi
        val failed = avatarManager.saveAvatarBitmap("../traversal", bitmapOriginal)
        assertFalse(failed)

        // Orijinal dosyanın korunduğunu ve baytlarının değişmediğini doğrula
        val postCheckFile = avatarManager.getAvatarFile(userId)
        assertNotNull(postCheckFile)
        assertTrue(postCheckFile!!.readBytes().contentEquals(originalBytes))
    }
}
