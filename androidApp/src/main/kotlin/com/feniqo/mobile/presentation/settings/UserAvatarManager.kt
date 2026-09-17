package com.feniqo.mobile.presentation.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hesap bazlı, cihazla sınırlı yerel avatar dosya yöneticisi.
 * Geçici content:// URI'lerine güvenmez; seçilen görseli uygulamanın güvenli iç depolama alanına
 * (context.filesDir/avatars/{userId}.jpg) kopyalar.
 * Böylece:
 * 1. Uygulama yeniden açıldığında avatar kalıcı olarak korunur.
 * 2. Hesaplar arasında tam izolasyon sağlanır; bir kullanıcının avatarı diğerinde gösterilmez.
 * 3. Fotoğraf kaldırıldığında yalnızca ilgili kullanıcının yerel dosyası silinir.
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

    fun getAvatarFile(userId: String): File? {
        if (userId.isBlank()) return null
        val file = File(avatarsDir, "${userId.trim()}.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun saveAvatarFromUri(userId: String, sourceUri: Uri): Boolean {
        if (userId.isBlank()) return false
        return runCatching {
            val targetFile = File(avatarsDir, "${userId.trim()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            targetFile.exists() && targetFile.length() > 0
        }.getOrDefault(false)
    }

    fun removeAvatar(userId: String): Boolean {
        if (userId.isBlank()) return false
        val file = File(avatarsDir, "${userId.trim()}.jpg")
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
