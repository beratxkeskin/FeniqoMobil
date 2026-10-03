# Supabase Mobil Build Configuration

Supabase proje URL'si ve mobil istemcide kullanılabilen publishable key kaynak koda yazılmaz.
Android ortamı build type ile açıkça ayrılır:

| Build type | Ortam | Application ID | Supabase kaynağı |
|---|---|---|---|
| `debug` | Staging | `com.feniqo.mobile.staging` | Staging değişkenleri |
| `demo` | Çevrimdışı demo | `com.feniqo.mobile.demo` | Sabit, geçersiz ağ hedefi |
| `release` | Production | `com.feniqo.mobile` | Production değişkenleri + zorunlu release kapısı |

### Staging (`assembleDebug`)

Android derlemesi staging değerlerini aşağıdaki sırayla arar:

1. CI ve yerel ortam değişkenleri:
   - `FENIQO_STAGING_SUPABASE_URL`
   - `FENIQO_STAGING_SUPABASE_PUBLISHABLE_KEY`
2. Git tarafından izlenmeyen kök `local.properties`:
   - `feniqo.supabase.staging.url`
   - `feniqo.supabase.staging.publishableKey`

Geçiş uyumluluğu için eski `FENIQO_SUPABASE_*` ve `feniqo.supabase.*` adları yalnız staging
fallback'i olarak kabul edilir. URL'nin staging project ref'i `rxfaiynkhaxrksosxvxp` ile tam
eşleşmesi zorunludur.

Staging değerleri Gradle yapılandırması açılırken zorunlu okunmaz; yalnız debug derleme ve test
görevlerinin `validateStagingSupabaseConfiguration` kapısında fail-closed doğrulanır. Böylece
staging erişimi gerektirmeyen varyantlar bağımsız kalır.

### Çevrimdışı demo (`assembleDemo`)

Demo varyantı herhangi bir Supabase ortam değişkeni veya `local.properties` değeri istemez.
`https://demo.invalid` ve yalnız demo için tanımlanan sentetik publishable değer build config'e
doğrudan yazılır. Demo, debug yapılandırmasından türese de staging doğrulama görevine bağlı değildir;
temiz makinede yalnız `:androidApp:assembleDemo` çalıştırılabilir.

### Production (`bundleRelease`)

Release için staging fallback'i yoktur. Aşağıdaki değerler ayrıca tanımlanmalıdır:

```text
FENIQO_RELEASE_ENVIRONMENT=production
FENIQO_PRODUCTION_SUPABASE_URL=https://qgmymavltjnmfuzvfxiq.supabase.co
FENIQO_PRODUCTION_SUPABASE_PUBLISHABLE_KEY=REDACTED
FENIQO_VERSION_CODE=1
FENIQO_VERSION_NAME=1.0.0
```

veya Git dışında kalan `local.properties` içinde:

```properties
feniqo.release.environment=production
feniqo.supabase.production.url=https://qgmymavltjnmfuzvfxiq.supabase.co
feniqo.supabase.production.publishableKey=REDACTED
feniqo.version.code=1
feniqo.version.name=1.0.0
```

`preReleaseBuild`, ortam sözleşmesini, HTTPS host'unu, production project ref'ini ve publishable
key türünü doğrular. Sürüm kodu pozitif tam sayı, sürüm adı semantik sürüm biçiminde olmalıdır.
Eksik, geçersiz veya staging'e bağlı yapılandırmada release üretimi başlamadan durur. Release
varyantı R8 kod küçültme ve resource shrinking ile üretilir; CI, Android unit test kapısından
sonra unsigned AAB'yi ve R8 mapping dosyasını doğrular.

Mobil uygulama yalnız yukarıdaki iki kaynaktan beslenir; referans web `.env` fallback'i bulunmaz.

## Güvenlik sınırı

- Mobil istemcide yalnızca `sb_publishable_` ile başlayan publishable anahtarlar kabul edilir.
- `sb_secret_...` veya service-role anahtarları kesinlikle mobil yapılandırmaya eklenemez.
- Staging ve production anahtarları birbirinin fallback'i değildir.
- Demo varyantı staging veya production yapılandırmasına bağımlı değildir.
- Gradle derleme sürecinde bu değerleri loglamaz veya hata mesajlarında yazdırmaz.
- UI `SupabaseClient` kullanmaz; istemci repository/data-source katmanına Hilt ile verilir.
- iOS build configuration, iOS uygulama kabuğu aktif geliştirilmeye başlandığında
  `.xcconfig` ve `Info.plist` aktarımıyla aynı `SupabaseConnectionConfig` sözleşmesine bağlanacaktır.

## Android oturum saklama

- Supabase erişim ve yenileme tokenları `AndroidSupabaseSessionManager` üzerinden saklanır.
- Oturum JSON'u, dışarı aktarılamayan Android Keystore AES-256-GCM anahtarıyla şifrelenir.
- Diskte yalnızca IV ve şifreli veri bulunur; dosya `noBackupFilesDir` altında tutulur.
- Çıkışta şifreli oturum dosyası silinir. Keystore anahtarı token içermez.
- Supabase istemcisi güvenli depodan otomatik yükleme/kaydetme ve token yenilemeyi etkinleştirir.
- iOS için aynı `SessionManager` sözleşmesinin Keychain uyarlaması 10.4 kapsamında eklenecektir.
