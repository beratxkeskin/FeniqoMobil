# FeniqoMobil

> Aktif geliştirme aşamasında Android-first, offline-first kişisel finans uygulaması.
> Son tamamlanan ana adım **5.4 Realtime**, sıradaki adım **6.1 WorkManager**'dır.

FeniqoMobil; Android'de Jetpack Compose, iOS'ta SwiftUI ve ortak iş mantığında Kotlin
Multiplatform kullanan bir mobil finans projesidir. Uygulama Room'u tek okuma kaynağı olarak
kullanır; Supabase ile senkronizasyon repository, kalıcı outbox ve optimistic version kontrolü
üzerinden yürütülür.

## Temel kararlar

- Paket/application kimliği: `com.feniqo.mobile`
- Minimum Android SDK: API 26
- Mimari: Clean Architecture + MVVM
- Android UI: Jetpack Compose + Material 3
- iOS UI: SwiftUI
- Yerel veri: Room + Android'de SQLCipher/Keystore
- Uzak servis: Supabase Auth, PostgREST, Storage ve Realtime
- Para: en küçük para biriminde `Long`; `Double` kullanılmaz
- Senkronizasyon: Room SSOT + outbox + soft-delete + `version/baseVersion`

## Modüller

```text
FeniqoMobil/
├── androidApp/   Android uygulaması, Hilt ve platform servisleri
├── sharedLogic/  KMP domain, data, Room ve senkronizasyon mantığı
├── sharedUI/     Compose tema, bileşenler ve Android UI kabuğu
├── iosApp/       SwiftUI iOS uygulama kabuğu
├── supabase/     Sıralı SQL migration'ları ve yerel CLI ayarları
└── docs/         Ayrıntılı analiz, güvenlik ve platform planları
```

Bağımlılık yönü ve source set kuralları için [ARCHITECTURE.md](ARCHITECTURE.md) dosyasını okuyun.

## Belge merkezi

Yeni geliştirici veya kodlama aracı şu sırayla başlamalıdır:

1. [AGENTS.md](AGENTS.md) — güvenlik ve çalışma sözleşmesi
2. [PRODUCT.md](PRODUCT.md) — ürün amacı ve V1 kapsamı
3. [ARCHITECTURE.md](ARCHITECTURE.md) — mimari sınırlar
4. [DATABASE.md](DATABASE.md) — veri ve senkronizasyon sözleşmesi
5. [FEATURES.md](FEATURES.md) — özelliklerin gerçek durumu
6. [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) — güncel faz ve uygulama sırası
7. [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md) — ayrıntılı checkbox ve ilerleme geçmişi

Ayrıntılı uzman belgeleri:

- [Web referans envanteri](docs/WEB_REFERANS_ENVANTERI.md)
- [Supabase V1 güvenlik ve migration planı](docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md)
- [Android yerel veritabanı güvenlik planı](docs/ANDROID_YEREL_VERITABANI_GUVENLIK_PLANI.md)
- [Supabase mobil build configuration](docs/SUPABASE_MOBIL_BUILD_CONFIG.md)
- [Supabase migration çalışma notları](supabase/README.md)

## Gereksinimler

- Android Studio ve Android SDK 36
- JDK 17
- Git
- iOS derlemesi için macOS/Xcode ortamı veya yapılandırılmış Kotlin/Native hedefi
- Migration geliştirmesi için gerektiğinde Supabase CLI

Repository Gradle Wrapper içerir; ayrı Gradle kurulumu gerekmez.

## Yerel Supabase yapılandırması

Gerçek değerleri Git'e eklemeyin. Android build aşağıdaki kaynaklardan değer okuyabilir:

1. Ortam değişkenleri:

```text
FENIQO_SUPABASE_URL
FENIQO_SUPABASE_PUBLISHABLE_KEY
```

2. Git dışında kalan kök `local.properties`:

```properties
feniqo.supabase.url=https://PROJECT_REF.supabase.co
feniqo.supabase.publishableKey=YOUR_PUBLISHABLE_OR_ANON_KEY
```

Mobil uygulamada `sb_secret_...` veya service-role key kullanılamaz. Ayrıntı için
[docs/SUPABASE_MOBIL_BUILD_CONFIG.md](docs/SUPABASE_MOBIL_BUILD_CONFIG.md) dosyasına bakın.

## Derleme ve test

Windows PowerShell'de proje kökünden:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

Güncel doğrulama durumu: 73/73 ortak Android host testi, Android debug APK ve iOS Simulator
ARM64 ortak kod derlemesi başarılıdır.

## Veri güvenliği

- Room, UI'ın tek okuma kaynağıdır.
- UI doğrudan Supabase veya Realtime payload tüketmez.
- RLS kişisel veri tablolarında zorunludur.
- Makbuzlar private bucket sözleşmesiyle yönetilir.
- Production Supabase'e migration uygulanmamıştır.
- Production SQL/migration işlemi staging kabulü, yedek ve proje sahibinin ayrıca açık onayı
  olmadan yapılamaz.

## Güncel geliştirme adımı

Sıradaki adım `6.1 WorkManager`:

- Hilt destekli `CoroutineWorker`;
- benzersiz ilk/outbox senkronizasyon işi;
- ağ bağlantısı constraint'i;
- `Result.retry()` ve exponential backoff;
- worker davranış testleri.

Ayrıntılı sıra ve tamamlanma ölçütü [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) dosyasındadır.
