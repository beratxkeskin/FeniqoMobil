import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

val localPropertiesFile = rootProject.file("local.properties")
val localPropertiesContent = localPropertiesFile.takeIf(File::exists)?.readText()
val localProperties = Properties().apply {
    localPropertiesFile.takeIf(File::exists)?.inputStream()?.use(::load)
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

val brandfetchClientId = configuredSupabaseValue(
    environmentName = "FENIQO_BRANDFETCH_CLIENT_ID",
    localPropertyName = "feniqo.brandfetch.clientId",
)
val brandfetchRefererOrigin = configuredSupabaseValue(
    environmentName = "FENIQO_BRANDFETCH_REFERER_ORIGIN",
    localPropertyName = "feniqo.brandfetch.refererOrigin",
)

val remoteLogoProvider = sequenceOf(
    System.getenv("FENIQO_REMOTE_LOGO_PROVIDER"),
    localProperties.getProperty("feniqo.remoteLogo.provider"),
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull() ?: "none"

// ÖNEMLİ: Logo.dev publishable key Properties.load() ile okunmaz; Properties.getProperty() leading whitespace'i
// sildiği için ham local.properties metni üzerinden exact prefix ile ayrıştırılır.
abstract class VerifyLogoDevRawKeyParserTask : DefaultTask() {
    companion object {
        fun parseRawLogoDevPublishableKeyFromText(text: String?): String? {
            if (text == null) return null
            val targetPrefix = "feniqo.logodev.publishableKey="
            val ambiguousKeyPrefix = "feniqo.logodev.publishableKey"

            var foundValue: String? = null
            var foundCount = 0

            val lines = text.lines()
            for (line in lines) {
                val trimmedLeading = line.trimStart()
                if (trimmedLeading.startsWith("#") || trimmedLeading.startsWith("!")) {
                    continue
                }
                if (trimmedLeading.isEmpty()) {
                    continue
                }

                if (line.startsWith(targetPrefix)) {
                    val rawValue = line.substring(targetPrefix.length)
                    if (rawValue.contains('\\')) {
                        return null // Continuation veya escape formatı belirsizdir -> fail-closed
                    }
                    if (rawValue.isEmpty()) {
                        return null // Değer boş -> fail-closed
                    }
                    foundValue = rawValue
                    foundCount++
                } else if (trimmedLeading.startsWith(ambiguousKeyPrefix)) {
                    val remainder = trimmedLeading.removePrefix(ambiguousKeyPrefix)
                    if (remainder.isEmpty() || remainder.first() in charArrayOf('=', ':', ' ', '\t')) {
                        return null // Key çevresinde boşluk veya alternatif ayraç -> fail-closed
                    }
                }
            }

            if (foundCount != 1) {
                return null // 0 eşleşme veya mükerrer (duplicate > 1) key -> fail-closed
            }

            return foundValue
        }

        fun resolveLogoDevPublishableKey(
            envValue: String?,
            localPropertiesContent: String?,
        ): String? {
            if (!envValue.isNullOrEmpty()) {
                return envValue
            }
            return parseRawLogoDevPublishableKeyFromText(localPropertiesContent)
        }
    }

    @TaskAction
    fun runVerification() {
        // 1. key=pk_valid -> pk_valid
        val standard = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey=pk_valid")
        check(standard == "pk_valid") { "standard key parse basarisiz" }

        // 2. key= pk_valid -> bastaki bosluk korunur
        val leadingSpace = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey= pk_valid")
        check(leadingSpace == " pk_valid") { "leading space korunmadi" }

        // 3. key=pk_valid  -> sondaki bosluk korunur
        val trailingSpace = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey=pk_valid ")
        check(trailingSpace == "pk_valid ") { "trailing space korunmadi" }

        // 4. internal whitespace korunur
        val internalSpace = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey=pk_val id")
        check(internalSpace == "pk_val id") { "internal space korunmadi" }

        // 5. duplicate exact key -> fail-closed (null)
        val duplicate = parseRawLogoDevPublishableKeyFromText(
            "feniqo.logodev.publishableKey=pk_first\nfeniqo.logodev.publishableKey=pk_second",
        )
        check(duplicate == null) { "duplicate key fail-closed olmali" }

        // 6. key =pk_valid -> fail-closed (null)
        val spaceBeforeEq = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey =pk_valid")
        check(spaceBeforeEq == null) { "space before equals fail-closed olmali" }

        val colonSeparator = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey:pk_valid")
        check(colonSeparator == null) { "colon separator fail-closed olmali" }

        val leadingSpaceKey = parseRawLogoDevPublishableKeyFromText(" feniqo.logodev.publishableKey=pk_valid")
        check(leadingSpaceKey == null) { "leading space before key fail-closed olmali" }

        val continuation = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey=pk_val\\")
        check(continuation == null) { "continuation fail-closed olmali" }

        // 7. empty value -> null / fail-closed
        val emptyVal = parseRawLogoDevPublishableKeyFromText("feniqo.logodev.publishableKey=")
        check(emptyVal == null) { "empty value fail-closed olmali" }

        // 8. environment leading/trailing whitespace korunur ve environment onceliklidir
        val envLeading = resolveLogoDevPublishableKey(" pk_env", "feniqo.logodev.publishableKey=pk_local")
        check(envLeading == " pk_env") { "env leading space korunmadi" }

        val envTrailing = resolveLogoDevPublishableKey("pk_env ", "feniqo.logodev.publishableKey=pk_local")
        check(envTrailing == "pk_env ") { "env trailing space korunmadi" }

        val envInternal = resolveLogoDevPublishableKey("pk_ env", "feniqo.logodev.publishableKey=pk_local")
        check(envInternal == "pk_ env") { "env internal space korunmadi" }

        val envEmptyFallsBackToLocal = resolveLogoDevPublishableKey("", "feniqo.logodev.publishableKey=pk_local")
        check(envEmptyFallsBackToLocal == "pk_local") { "empty env local fallback basarisiz" }

        val envNullFallsBackToLocal = resolveLogoDevPublishableKey(null, "feniqo.logodev.publishableKey=pk_local")
        check(envNullFallsBackToLocal == "pk_local") { "null env local fallback basarisiz" }

        logger.lifecycle("Logo.dev raw key parser dogrulama kapisi basariyla gecti.")
    }
}

val logodevPublishableKey = VerifyLogoDevRawKeyParserTask.resolveLogoDevPublishableKey(
    envValue = System.getenv("FENIQO_LOGODEV_PUBLISHABLE_KEY"),
    localPropertiesContent = localPropertiesContent,
)

val logodevUsageAuthorizedRaw = sequenceOf(
    System.getenv("FENIQO_LOGODEV_USAGE_AUTHORIZED"),
    localProperties.getProperty("feniqo.logodev.usageAuthorized"),
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()

val logodevUsageAuthorized =
    logodevUsageAuthorizedRaw?.equals("true", ignoreCase = true) == true

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

val releaseAuthRedirectUri = productionAuthRedirectUrl
    ?.let { runCatching { URI(it) }.getOrNull() }
    ?.takeIf { uri ->
        !uri.scheme.isNullOrBlank() &&
            !uri.host.isNullOrBlank() &&
            !uri.path.isNullOrBlank()
    }
    ?: URI("https://invalid.invalid/auth/callback")

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
    testImplementation(libs.coil.compose)
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
        buildConfigField("String", "BRANDFETCH_CLIENT_ID", brandfetchClientId.orEmpty().asBuildConfigString())
        buildConfigField("String", "BRANDFETCH_REFERER_ORIGIN", brandfetchRefererOrigin.orEmpty().asBuildConfigString())
        buildConfigField("String", "REMOTE_LOGO_PROVIDER", remoteLogoProvider.asBuildConfigString())
        buildConfigField("String", "LOGODEV_PUBLISHABLE_KEY", logodevPublishableKey.orEmpty().asBuildConfigString())
        buildConfigField("boolean", "LOGODEV_USAGE_AUTHORIZED", logodevUsageAuthorized.toString())
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

val verifyLogoDevRawKeyParser = tasks.register<VerifyLogoDevRawKeyParserTask>("verifyLogoDevRawKeyParser") {
    group = "verification"
    description = "Logo.dev ham local.properties ve environment anahtar ayrıştırma kurallarını doğrular."
}

tasks.matching {
    it.name in setOf("preDebugBuild", "preDebugUnitTestBuild", "preDemoBuild")
}.configureEach {
    dependsOn(verifyLogoDevRawKeyParser)
}

androidComponents {
    beforeVariants(selector().withBuildType("demo")) { builder ->
        (builder as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}
