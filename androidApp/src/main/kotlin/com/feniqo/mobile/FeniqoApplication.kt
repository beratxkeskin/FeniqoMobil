package com.feniqo.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt'in uygulama seviyesindeki bağımlılık grafiğini başlatır.
 * Uygulama boyunca yaşayan bağımlılıklar ileride burada yönetilecektir.
 */
@HiltAndroidApp
class FeniqoApplication : Application()
