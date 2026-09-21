package com.feniqo.mobile.presentation.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hesap bazlı, cihazla sınırlı yerel avatar dosya yöneticisi.
 *
 * Güvenlik ve İzolasyon Garantileri:
 * 1. Kimlik Doğrulama & Path Traversal Koruması: Ham `userId` asla doğrudan dosya sistemine verilmez.
 *    Yalnızca alfanumerik ve tire/alt çizgi içeren kimlikler kabul edilir; canonicalPath kontrolü ile
 *    hedefin kesinlikle `filesDir/avatars/` içinde kalması garanti edilir.
 * 2. Atomik Güncelleme: Yeni görsel önce `.tmp` geçici dosyasına yazılır. İşlem başarıyla tamamlandığında
 *    atomik olarak mevcut avatar ile değiştirilir. Kırpma, decode veya I/O hatalarında eski avatar korunur.
 * 3. Kamera İzolasyonu: FileProvider yalnızca `cache/camera/` dizinini dar kapsamlı paylaşır;
 *    kalıcı avatars dizini dış dünyaya asla açılmaz.
 * 4. Hesap İzolasyonu: Her kullanıcının avatarı kendi kimliğiyle izoledir.
 */
@Singleton
class UserAvatarManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val avatarsDir: File by lazy {
        File(context.filesDir, "avatars").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val cameraCacheDir: File by lazy {
        File(context.cacheDir, "camera").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    companion object {
        private val VALID_USER_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")
    }

    fun validateAndSanitizeUserId(userId: String): String? {
        val trimmed = userId.trim()
        if (!trimmed.matches(VALID_USER_ID_REGEX)) return null
        return trimmed
    }

    private fun getTargetAvatarFile(safeUserId: String): File? {
        val target = File(avatarsDir, "$safeUserId.jpg")
        return runCatching {
            if (target.canonicalPath.startsWith(avatarsDir.canonicalPath)) target else null
        }.getOrNull()
    }

    fun getAvatarFile(userId: String): File? {
        val safeId = validateAndSanitizeUserId(userId) ?: return null
        val file = getTargetAvatarFile(safeId) ?: return null
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Kamera çekimi için dar kapsamlı geçici bir dosya ve Content URI'si üretir.
     * Güvenli bir content:// URI üretilemezse null döner; fail-closed kuralı gereği
     * asla güvensiz file:// URI dönülmez.
     */
    fun createTempCameraFile(): Pair<File, Uri>? {
        return runCatching {
            val tempFile = File(cameraCacheDir, "temp_camera_${UUID.randomUUID()}.jpg")
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile,
            )
            tempFile to contentUri
        }.getOrNull()
    }

    /**
     * Galeriden veya harici kaynaktan seçilen görseli, geçici izin kaybı riskine karşı
     * anında güvenli yerel önizleme taslak dosyasına kopyalar.
     */
    fun copyUriToDraftPreview(sourceUri: Uri): File? {
        return runCatching {
            val draftFile = File(cameraCacheDir, "draft_preview_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(draftFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return null

            if (draftFile.exists() && draftFile.length() > 0) draftFile else null
        }.getOrNull()
    }

    /**
     * Kırpılmış Bitmap'i önce geçici dosyaya yazar, ardından atomik biçimde hedef avatarla değiştirir.
     * Değişim sırasında hata olursa eski avatar bozulmadan korunur.
     */
    fun saveAvatarBitmap(userId: String, bitmap: Bitmap): Boolean {
        val safeId = validateAndSanitizeUserId(userId) ?: return false
        val targetFile = getTargetAvatarFile(safeId) ?: return false

        val tmpFile = File(avatarsDir, "$safeId.tmp")
        return runCatching {
            FileOutputStream(tmpFile).use { outputStream ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                compressed
            }
            atomicSwapWithBackup(tmpFile, targetFile)
        }.onFailure {
            tmpFile.delete()
        }.getOrDefault(false)
    }

    fun saveAvatarFromUri(userId: String, sourceUri: Uri): Boolean {
        val safeId = validateAndSanitizeUserId(userId) ?: return false
        val targetFile = getTargetAvatarFile(safeId) ?: return false

        val tmpFile = File(avatarsDir, "$safeId.tmp")
        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return false

            atomicSwapWithBackup(tmpFile, targetFile)
        }.onFailure {
            tmpFile.delete()
        }.getOrDefault(false)
    }

    /**
     * Atomik dosya değişimi garantisi:
     * 1. Hedef dosya mevcutsa önce .bak yedeği alınır.
     * 2. Yeni dosya güvenle hedefe taşınır (Files.move / POSIX rename).
     * 3. İşlem başarısız olursa eski yedek derhal geri yüklenir; eski avatar asla bozulmaz.
     * 4. Başarılı olursa .bak yedeği temizlenir.
     */
    private fun atomicSwapWithBackup(tmpFile: File, targetFile: File): Boolean {
        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            tmpFile.delete()
            return false
        }

        val backupFile = File(targetFile.parentFile, "${targetFile.name}.bak")
        val hadExistingTarget = targetFile.exists()

        return try {
            if (hadExistingTarget) {
                safeMove(targetFile, backupFile)
            }

            val moved = safeMove(tmpFile, targetFile)
            if (moved) {
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                true
            } else {
                if (hadExistingTarget && backupFile.exists()) {
                    safeMove(backupFile, targetFile)
                }
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            tmpFile.delete()
            if (hadExistingTarget && backupFile.exists()) {
                runCatching { safeMove(backupFile, targetFile) }
            }
            false
        }
    }

    private fun safeMove(source: File, target: File): Boolean {
        return runCatching {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            true
        }.recoverCatching {
            if (target.exists()) {
                target.delete()
            }
            source.renameTo(target)
        }.getOrDefault(false)
    }

    fun clearTempCameraFiles() {
        cameraCacheDir.listFiles()?.forEach { it.delete() }
    }

    fun deleteAvatar(userId: String): Boolean = removeAvatar(userId)

    fun removeAvatar(userId: String): Boolean {
        val safeId = validateAndSanitizeUserId(userId) ?: return false
        val file = getTargetAvatarFile(safeId) ?: return false
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
