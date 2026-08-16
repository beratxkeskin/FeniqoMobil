import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::exists)?.inputStream()?.use(::load)
}

val referenceWebEnvironment: Map<String, String> = rootProject.file("../Feniqo/.env")
    .takeIf(File::exists)
    ?.readLines()
    ?.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || '=' !in trimmed) return@mapNotNull null
        val (name, value) = trimmed.split('=', limit = 2)
        name.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
    }
    ?.toMap()
    .orEmpty()

fun requiredSupabaseValue(
    environmentName: String,
    localPropertyName: String,
    webEnvironmentNames: List<String>,
): String = System.getenv(environmentName)?.trim()?.takeIf(String::isNotEmpty)
    ?: localProperties.getProperty(localPropertyName)?.trim()?.takeIf(String::isNotEmpty)
    ?: webEnvironmentNames.firstNotNullOfOrNull { referenceWebEnvironment[it]?.takeIf(String::isNotEmpty) }
    ?: error(
        "$localPropertyName eksik. local.properties veya $environmentName ortam değişkeni ile tanımlayın.",
    )

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val supabaseUrl = requiredSupabaseValue(
    environmentName = "FENIQO_SUPABASE_URL",
    localPropertyName = "feniqo.supabase.url",
    webEnvironmentNames = listOf("VITE_SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_URL"),
)
val supabasePublishableKey = requiredSupabaseValue(
    environmentName = "FENIQO_SUPABASE_PUBLISHABLE_KEY",
    localPropertyName = "feniqo.supabase.publishableKey",
    webEnvironmentNames = listOf("VITE_SUPABASE_ANON_KEY", "NEXT_PUBLIC_SUPABASE_ANON_KEY"),
)

require(!supabasePublishableKey.startsWith("sb_secret_")) {
    "Supabase secret/service-role anahtarı Android uygulamasına eklenemez."
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":sharedUI"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

android {
    namespace = "com.feniqo.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.feniqo.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.asBuildConfigString())
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric/Conscrypt, Türkçe Windows yerel ayarında native kütüphane adını hatalı küçültüyor.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
