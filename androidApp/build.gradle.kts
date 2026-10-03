import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::exists)?.inputStream()?.use(::load)
}

fun configuredSupabaseValue(
    environmentName: String,
    localPropertyName: String,
    legacyEnvironmentName: String? = null,
    legacyLocalPropertyName: String? = null,
): String? = sequenceOf(
    System.getenv(environmentName),
    legacyEnvironmentName?.let(System::getenv),
    localProperties.getProperty(localPropertyName),
    legacyLocalPropertyName?.let(localProperties::getProperty),
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .firstOrNull()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun validateSupabaseProjectUrl(value: String, expectedProjectRef: String, environmentLabel: String) {
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri?.scheme == "https" &&
            uri.host == "$expectedProjectRef.supabase.co" &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/"),
    ) {
        "$environmentLabel Supabase URL beklenen project ref ile eşleşmiyor: $expectedProjectRef"
    }
}

fun validatePublishableKey(value: String, environmentLabel: String) {
    require(value.startsWith("sb_publishable_")) {
        "$environmentLabel Supabase publishable key geçersiz. Yalnızca 'sb_publishable_' ile başlayan mobil istemci anahtarları kullanılabilir."
    }
    require(!value.startsWith("sb_secret_") && !value.contains("service_role")) {
        "$environmentLabel Supabase secret/service-role anahtarı Android uygulamasına eklenemez."
    }
}

fun validateAuthRedirectUrl(value: String) {
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri?.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path == "/auth/callback",
    ) {
        "Release parola kurtarma adresi https://<doğrulanmış-domain>/auth/callback biçiminde olmalıdır."
    }
}

val stagingProjectRef = "rxfaiynkhaxrksosxvxp"
val productionProjectRef = "qgmymavltjnmfuzvfxiq"

val stagingSupabaseUrl = configuredSupabaseValue(
    environmentName = "FENIQO_STAGING_SUPABASE_URL",
    localPropertyName = "feniqo.supabase.staging.url",
    legacyEnvironmentName = "FENIQO_SUPABASE_URL",
    legacyLocalPropertyName = "feniqo.supabase.url",
)
val stagingSupabasePublishableKey = configuredSupabaseValue(
    environmentName = "FENIQO_STAGING_SUPABASE_PUBLISHABLE_KEY",
    localPropertyName = "feniqo.supabase.staging.publishableKey",
    legacyEnvironmentName = "FENIQO_SUPABASE_PUBLISHABLE_KEY",
    legacyLocalPropertyName = "feniqo.supabase.publishableKey",
)
val productionSupabaseUrl = configuredSupabaseValue(
    environmentName = "FENIQO_PRODUCTION_SUPABASE_URL",
    localPropertyName = "feniqo.supabase.production.url",
)
val productionSupabasePublishableKey = configuredSupabaseValue(
    environmentName = "FENIQO_PRODUCTION_SUPABASE_PUBLISHABLE_KEY",
    localPropertyName = "feniqo.supabase.production.publishableKey",
)
val releaseEnvironment = configuredSupabaseValue(
    environmentName = "FENIQO_RELEASE_ENVIRONMENT",
    localPropertyName = "feniqo.release.environment",
)
val configuredReleaseVersionCode = configuredSupabaseValue(
    environmentName = "FENIQO_VERSION_CODE",
    localPropertyName = "feniqo.version.code",
)?.toIntOrNull()
val configuredReleaseVersionName = configuredSupabaseValue(
    environmentName = "FENIQO_VERSION_NAME",
    localPropertyName = "feniqo.version.name",
)
val productionAuthRedirectUrl = configuredSupabaseValue(
    environmentName = "FENIQO_AUTH_REDIRECT_URL",
    localPropertyName = "feniqo.auth.redirectUrl",
)
val developmentAuthRedirectUrl = "feniqo://auth/callback"

val stagingConfigurationError = when {
    stagingSupabaseUrl == null ->
        "Staging Supabase URL eksik. FENIQO_STAGING_SUPABASE_URL veya feniqo.supabase.staging.url tanımlayın."
    stagingSupabasePublishableKey == null ->
        "Staging Supabase publishable key eksik. FENIQO_STAGING_SUPABASE_PUBLISHABLE_KEY veya feniqo.supabase.staging.publishableKey tanımlayın."
    else -> runCatching {
        validateSupabaseProjectUrl(stagingSupabaseUrl, stagingProjectRef, "Staging")
        validatePublishableKey(stagingSupabasePublishableKey, "Staging")
    }.exceptionOrNull()?.message
}

val productionConfigurationError = when {
    releaseEnvironment != "production" ->
        "Release derlemesi için FENIQO_RELEASE_ENVIRONMENT=production veya feniqo.release.environment=production zorunludur."
    productionSupabaseUrl == null ->
        "Production Supabase URL eksik. FENIQO_PRODUCTION_SUPABASE_URL veya feniqo.supabase.production.url tanımlayın."
    productionSupabasePublishableKey == null ->
        "Production Supabase publishable key eksik. FENIQO_PRODUCTION_SUPABASE_PUBLISHABLE_KEY veya feniqo.supabase.production.publishableKey tanımlayın."
    configuredReleaseVersionCode == null || configuredReleaseVersionCode <= 0 ->
        "Release derlemesi için pozitif FENIQO_VERSION_CODE veya feniqo.version.code zorunludur."
    configuredReleaseVersionName.isNullOrBlank() ||
        !configuredReleaseVersionName.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")) ->
        "Release derlemesi için semantik FENIQO_VERSION_NAME veya feniqo.version.name zorunludur."
    productionAuthRedirectUrl == null ->
        "Release derlemesi için FENIQO_AUTH_REDIRECT_URL veya feniqo.auth.redirectUrl zorunludur."
    else -> runCatching {
        validateSupabaseProjectUrl(productionSupabaseUrl, productionProjectRef, "Production")
        validatePublishableKey(productionSupabasePublishableKey, "Production")
        validateAuthRedirectUrl(productionAuthRedirectUrl)
    }.exceptionOrNull()?.message
}

val releaseAuthRedirectUri = runCatching { URI(productionAuthRedirectUrl.orEmpty()) }
    .getOrElse { URI("https://invalid.invalid/auth/callback") }

val validateProductionSupabaseConfiguration = tasks.register("validateProductionSupabaseConfiguration") {
    group = "verification"
    description = "Release derlemesinin doğrulanmış production Supabase projesini kullandığını denetler."

    val validationError = productionConfigurationError
    doLast {
        if (validationError != null) {
            throw GradleException(validationError)
        }
    }
}

val validateStagingSupabaseConfiguration = tasks.register("validateStagingSupabaseConfiguration") {
    group = "verification"
    description = "Debug derlemesinin doğrulanmış staging Supabase projesini kullandığını denetler."

    val validationError = stagingConfigurationError
    doLast {
        if (validationError != null) {
            throw GradleException(validationError)
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":sharedLogic"))
    implementation(project(":sharedUI"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.androidx.camera.camera2)
    debugImplementation(libs.androidx.camera.lifecycle)
    debugImplementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.mlkit.text.recognition)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.compose.ui.test.manifest)

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
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
}

android {
    namespace = "com.feniqo.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.feniqo.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Demo/debug yapılandırılabilsin; release kapısı aşağıdaki doğrulanmış değerleri zorunlu kılar.
        versionCode = configuredReleaseVersionCode ?: 1
        versionName = configuredReleaseVersionName ?: "0.0.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "DEMO", "false")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("String", "SUPABASE_URL", stagingSupabaseUrl.orEmpty().asBuildConfigString())
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", stagingSupabasePublishableKey.orEmpty().asBuildConfigString())
            buildConfigField("String", "AUTH_REDIRECT_URL", developmentAuthRedirectUrl.asBuildConfigString())
            manifestPlaceholders["authRedirectScheme"] = "feniqo"
            manifestPlaceholders["authRedirectHost"] = "auth"
            manifestPlaceholders["authRedirectPath"] = "/callback"
            manifestPlaceholders["authRedirectAutoVerify"] = "false"
        }
        create("demo") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO", "true")
            buildConfigField("String", "ENVIRONMENT", "\"demo\"")
            buildConfigField("String", "SUPABASE_URL", "\"https://demo.invalid\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_demo_offline_0000000000000000\"")
            buildConfigField("String", "AUTH_REDIRECT_URL", developmentAuthRedirectUrl.asBuildConfigString())
            manifestPlaceholders["authRedirectScheme"] = "feniqo"
            manifestPlaceholders["authRedirectHost"] = "auth"
            manifestPlaceholders["authRedirectPath"] = "/callback"
            manifestPlaceholders["authRedirectAutoVerify"] = "false"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
            buildConfigField("String", "SUPABASE_URL", productionSupabaseUrl.orEmpty().asBuildConfigString())
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", productionSupabasePublishableKey.orEmpty().asBuildConfigString())
            buildConfigField("String", "AUTH_REDIRECT_URL", productionAuthRedirectUrl.orEmpty().asBuildConfigString())
            manifestPlaceholders["authRedirectScheme"] = releaseAuthRedirectUri.scheme
            manifestPlaceholders["authRedirectHost"] = releaseAuthRedirectUri.host
            manifestPlaceholders["authRedirectPath"] = releaseAuthRedirectUri.path
            manifestPlaceholders["authRedirectAutoVerify"] = "true"
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
        managedDevices {
            localDevices {
                create("releaseGateApi35") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                }
            }
        }
    }
    sourceSets.getByName("androidTest").assets.directories.add(project(":sharedLogic").file("schemas").path)
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateProductionSupabaseConfiguration)
}

tasks.matching {
    it.name in setOf("preDebugBuild", "preDebugUnitTestBuild", "preDebugAndroidTestBuild")
}.configureEach {
    dependsOn(validateStagingSupabaseConfiguration)
}

tasks.withType<Test>().configureEach {
    // Robolectric/Conscrypt, Türkçe Windows yerel ayarında native kütüphane adını hatalı küçültüyor.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}

androidComponents {
    beforeVariants(selector().withBuildType("demo")) { builder ->
        (builder as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}
