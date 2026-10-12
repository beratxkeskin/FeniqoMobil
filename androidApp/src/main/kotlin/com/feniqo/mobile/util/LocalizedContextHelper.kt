package com.feniqo.mobile.util

import android.content.Context
import android.content.res.Configuration
import com.feniqo.mobile.domain.model.AppLanguage
import java.util.Locale

/**
 * Verilen [AppLanguage] tercihine göre yapılandırılmış Android [Context] üretir.
 * Bu sayede sistem varsayılan locale'inden bağımsız olarak uygulamanın seçili diliyle
 * Android platform kaynakları (R.string) çözümlenir.
 */
fun Context.localizedContext(appLanguage: AppLanguage): Context {
    val locale = when (appLanguage) {
        AppLanguage.TR -> Locale.forLanguageTag("tr")
        AppLanguage.EN -> Locale.forLanguageTag("en")
    }
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(config)
}
