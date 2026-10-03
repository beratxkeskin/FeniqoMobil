# FeniqoMobil Mimari Sözleşmesi

> Bu belge mimari kararların ana kaynağıdır. Bir değişiklik bu kurallardan birini değiştirecekse
> uygulamadan önce gerekçesi belgelenmeli ve proje sahibinden onay alınmalıdır.

## 1. Mimari özet

FeniqoMobil, Android-first bir Kotlin Multiplatform projesidir. Temel yaklaşım:

- Clean Architecture + MVVM;
- Room tabanlı offline-first Single Source of Truth;
- repository yönetiminde Supabase senkronizasyonu;
- kalıcı outbox, soft-delete ve optimistic version kontrolü;
- Android'de Jetpack Compose, iOS'ta SwiftUI;
- platformdan bağımsız iş mantığının `sharedLogic/commonMain` içinde tutulmasıdır.

## 2. Modüller

| Modül | Sorumluluk | Platform bağımlılığı |
|---|---|---|
| `androidApp` | Android entry point, Manifest, Hilt graph, Android lifecycle ve build config | Android |
| `sharedLogic` | Domain, use case, repository sözleşmeleri, Room modeli, Supabase erişimi ve sync motoru | KMP ortak + platform adaptörleri |
| `sharedUI` | Compose tema, tasarım token'ları, ortak Android UI kabuğu ve bileşenler | Şu anda Android hedefli Compose |
| `iosApp` | SwiftUI uygulama kabuğu ve iOS platform entegrasyonları | iOS |

Paket/application kimliği `com.feniqo.mobile`, minimum Android sürümü API 26'dır.

## 3. Katmanlar ve bağımlılık yönü

```text
UI / ViewModel
      |
      v
Domain use case ve repository sözleşmeleri
      ^
      |
Data repository implementasyonları
   /                    \
Room / outbox       Supabase / Realtime
```

Kurallar:

1. Domain katmanı Compose, Android, Room, Supabase veya Hilt import etmez.
2. UI yalnız ViewModel/use case/repository sözleşmeleri üzerinden çalışır.
3. UI doğrudan DAO, Supabase client, DTO veya Realtime payload kullanmaz.
4. Repository yerel ve uzak kaynakların koordinasyonundan sorumludur.
5. DTO, Room entity ve domain model birbirinden ayrıdır; dönüşümler mapper katmanındadır.
6. Bağımlılıklar constructor injection ile verilir. Hilt yalnız Android tarafında kalır.

## 4. Source set kuralları

### `commonMain`

Buraya yalnız platformdan bağımsız kod konur:

- domain model ve iş kuralları;
- repository arayüzleri ve ortak implementasyonlar;
- DTO/mapper;
- Room ortak entity/DAO sözleşmeleri;
- Supabase ve sync mantığının KMP uyumlu kısmı;
- Coroutines, Flow, Serialization ve Ktor ortak API'leri.

### `androidMain` ve `androidApp`

- Android Keystore ve SQLCipher açılışı;
- Hilt;
- WorkManager;
- BiometricPrompt;
- CameraX ve ML Kit;
- Android lifecycle ve platform build configuration.

### `iosMain` ve `iosApp`

- Darwin Ktor engine;
- Keychain tabanlı oturum/anahtar saklama;
- iOS veritabanı güvenlik adaptörü;
- SwiftUI ekranları ve Apple platform servisleri.

Android'e özel bir bağımlılık `commonMain` içine eklenemez.

## 5. Offline-first veri akışı

### Okuma

```text
Room Flow -> Repository -> Use case -> ViewModel StateFlow -> UI
```

Supabase yanıtı veya Realtime mesajı doğrudan UI state değildir. Uzak kayıt önce doğrulanır,
Room'a yazılır ve UI mevcut Room `Flow` akışı üzerinden kendiliğinden güncellenir.

Piyasa fiyatı gibi üçüncü taraf veriler mobil istemciden doğrudan alınmaz. Mobil, doğrulanmış
Supabase JWT ile Edge Function'a gider; sağlayıcı anahtarı ve service-role yalnız Function secret
ortamında kalır. Sağlayıcı endpoint'i backend adaptöründe sabittir. Normalize fiyat önce genel
Supabase cache'ine, ardından repository üzerinden Room cache'ine yazılır; UI yalnız Room Flow'u
gözlemler. Kullanıcı Asset kaydı sistem fiyatı tarafından sessizce değiştirilmez.

### Yerel yazma

```text
UI olayı -> ViewModel -> Use case -> Repository
          -> tek Room transaction içinde entity + outbox
          -> Room Flow ile anında UI güncellemesi
```

Yazma işlemi ağın sonucunu beklemez. Entity değişikliği ve outbox operasyonu atomiktir; biri
başarısız olursa ikisi de geri alınır.

### Senkronizasyon

```text
Outbox push -> koşullu RPC(baseVersion) -> başarı/conflict/retry
Incremental pull(updated_at, id) -> doğrulama -> Room transaction
Realtime SUBSCRIBED/değişiklik sinyali -> SyncRepository -> incremental pull -> Room
```

- Outbox işlemleri oluşturulma sırasıyla işlenir.
- Geçici hatalar üssel geri çekilme ile yeniden denenir.
- Başarılı operasyon kuyruktan kaldırılır.
- Eski `baseVersion` iki kopyalı kalıcı conflict üretir.
- Kullanıcı `KEEP_LOCAL` veya `KEEP_REMOTE` seçeneğiyle conflict çözer.
- Realtime yalnız invalidation/telafi sinyalidir; payload veri kaynağı değildir.
- Aynı anda birden fazla senkronizasyon `Mutex` ile seri hale getirilir.
- Room v22 ile `sync_operations`, `sync_cursors` ve `sync_conflicts` kayıtları `sync_scope_key` (`USER:<canonical_uuid>`) ile kullanıcı bazında izole edilir.
- PROFILE cursor sahibi ile aktif oturum kullanıcısı farklıysa senkronizasyon fail-closed durur (`AppError.Authentication("sync.local_data_owner_mismatch")`).
- Farklı hesap durumunda otomatik outbox (`clearAllOperations`) veya cursor (`clearAllCursors`) silinmez; önceki kullanıcının verisi korunur.
- Sahipliği kanıtlanamayan veya workspace kapsamındaki legacy kayıtlar `LEGACY_UNRESOLVED` karantinasında silinmeden fail-closed tutulur; aktif kullanıcıya körlemesine atanmaz veya runtime execution akışlarına dahil edilmez.
- UI senkronizasyon durumunda karantina uyarısı exact sayaç, ID veya payload sızdırmadan salt genel bilgi verir ve üzerinde remote/mutation aksiyonu barındırmaz.
- Oturum yarışında (session race) aktif oturum değişirse in-flight senkronizasyon iptal edilir; uzak çağrı başlamışsa işlem definitive ret veya başarı sayılmayıp aynı ID ve payload ile `AMBIGUOUS_RESULT` olarak kaydedilir.
- WorkManager, Realtime ve manuel senkronizasyon aynı repository ve session guard sözleşmesine tabidir; oturum kapandığında (sign-out) senkronizasyon iptal edilir fakat Room verisi silinmez.
- Kişisel yedek içe aktarma (`PersonalBackupImporter`) aktör bilgisini payload'dan değil oturumdan alır ve enqueue öncesinde oturumu tekrar doğrular.
- Demo repository yalnızca canonical `USER:de000000-0000-4000-8000-000000000001` scope'unu kullanır ve `hasLegacyQuarantinedData = false` bildirir.
- Güvenli hesap değiştirme ve yerel veri yönetimi ayrı ve açık bir kullanıcı kararı/onayı gerektirir.

## 6. Para ve zaman modeli

- Para miktarı `Double` değildir; en küçük para biriminde `Long` tutulur.
- `125,50 TRY`, `amountMinor = 12550` ve `currency = TRY` olarak temsil edilir.
- Para birimleri karıştırılarak toplanmaz; dönüşüm ayrı ve açık bir iş kuralıdır.
- İşlem günü saat diliminden bağımsız ISO `YYYY-MM-DD` yerel tarihtir.
- Sunucu metadata zamanları UTC `Instant`; Room'da epoch-millis olarak tutulur.

## 7. Kimlik, silme ve çakışma

- Kimlikler istemcide üretilebilen UUID tabanlı `EntityId` değerleridir.
- Senkronize kayıtlar `updatedAt`, `deletedAt`, `version`, `baseVersion` metadata'sı taşır.
- Kullanıcı silmesi hard-delete değil soft-delete/tombstone üretir.
- Sunucu version değerinin tek otoritesidir; başarılı update version'ı artırır.
- İstemci mutation sırasında okuduğu sürümü `baseVersion` olarak gönderir.

## 8. Platform servisleri

| Yetkinlik | Android | iOS |
|---|---|---|
| UI | Jetpack Compose | SwiftUI |
| DI | Hilt | Swift composition/root assembly |
| Arka plan sync | WorkManager | Gelecekte BGTaskScheduler adaptörü |
| Güvenli saklama | Android Keystore | Keychain |
| Yerel DB şifreleme | SQLCipher + Keystore zarfı | Üretim öncesi doğrulanacak iOS adaptörü |
| Biyometri | BiometricPrompt | LocalAuthentication |
| OCR | CameraX + ML Kit | Gelecekte iOS platform adaptörü |

Android biyometrik uygulama kilidi bir UI erişim kapısıdır; SQLCipher anahtarının açılmasını
kullanıcı doğrulamasına bağlamaz. Böylece uygulama arka plandayken WorkManager senkronizasyonu
çalışabilir. Kilit tercihi ve süre DataStore'da tutulur, geçici kilit/açık durumu yalnız süreç
belleğinde yaşar; cihaz saati geriye alınırsa politika güvenli tarafta kalıp yeniden doğrulama ister.

Makbuz OCR Android'de cihaz içi ML Kit adaptörüyle çalışır. Ham OCR metni Room, outbox, log veya
uzak servise yazılmaz; ortak katmana yalnız geçici ve güven düzeyli tutar/tarih/işyeri adayları
geçer. Bu adaylar bir `Transaction` değildir ve yalnız kullanıcı işlem formunda açıkça onayladıktan
sonra mevcut repository yazma akışına girebilir. Para birimi OCR metninden tahmin edilmez.

## 9. Hata ve durum yönetimi

- Data/SDK hataları kullanıcı metni olmayan kararlı `AppError` türlerine çevrilir.
- Repository operasyonları tanımlı `RepositoryResult` döndürür.
- Uzun yaşayan UI durumu `StateFlow`, tek seferlik olaylar `SharedFlow` ile sunulur.
- Cancellation yakalanıp normal hata gibi yutulmaz.
- Hassas veri, token, parola, makbuz içeriği veya finansal payload loglanmaz.
- Doğrulanmış parola kurtarma oturumu terk edilirken uzak `signOut` ve arka plan sync iptali
  tamamlanmadan recovery durumu `Idle` olamaz; temizleme başarısızsa navigasyon fail-closed
  biçimde `PasswordRecovery` dalında kalır.

## 10. Test sınırları

### Merchant tanıma sınırı

Merchant normalleştirme, işlem sınıflandırma ve güven puanlı alias eşleştirme saf KMP domain
mantığıdır. Ham açıklama değişmeden korunur; motor yalnız ayrı normalize edilmiş metni işler.
Kullanıcı doğrulaması tahminden üstündür, özel alias genel aliastan önce gelir ve kişisel/sistem
hareketleri katalog taramasından önce elenir. Alias türü ve kişisel/çalışma alanı/genel kapsamı
domain sözleşmesidir; doğrulama kaynakları kişisel, çalışma alanı ve banka kimliği sırasını izler.
Farklı merchant adaylarının puan farkı 10'dan azsa motor fail-closed biçimde belirsiz sonuç verir.
Bu ilk dilimde Room, Supabase, DTO, outbox, sync,
UI veya logo sağlayıcısı bağlantısı yoktur. Gelecekteki logo adaptörü ham finansal açıklama alamaz
ve uygulamanın temel çalışması için zorunlu bağımlılık olamaz.

- Saf domain ve use case kuralları `commonTest` içinde test edilir.
- Sync motoru fake remote/DAO sınırlarıyla deterministik test edilir.
- Room DAO ve migration davranışları Android host/instrumentation testlerinde doğrulanır.
- Android debug APK ve iOS Simulator ARM64 ortak kod derlemesi ana altyapı adımlarında çalıştırılır.
- Production şeması yalnız staging kabul testinden ve açık onaydan sonra değiştirilebilir.

## 11. Mimari değişiklik süreci

Geliştirme demo varyantı (`com.feniqo.mobile.demo`) aynı Room/repository/UI zincirini kullanır.
Yalnız Auth/Sync adaptörleri yereldir; INTERNET izni kaldırılır ve uzak yapılandırma `.invalid`
hedefine sabitlenir. Fixture'lar normal entity + outbox yoluyla yazılır. Ayrıntı ve sandbox
sıfırlama sınırı: [Demo kullanım rehberi](docs/DEMO_KULLANIM.md).

### Veri taşınabilirliği sınırı

- Presentation katmanı yedek codec/exporter/importer sınıflarını doğrudan tüketmez. `PersonalBackupRepository` domain portu kapsam, önizleme, dışa aktarma ve içe aktarma sonuçlarını presentation'a uygun domain modelleriyle sunar; `DefaultPersonalBackupRepository` data adaptörü mevcut codec ve atomik importer/exporter implementasyonlarını bu sınırın arkasında tutar.
- JSON yedekleri açık `format_version` ile sürümlenir; v1 yalnız kişisel kategori ve işlemleri kapsar.
- Token/oturum, owner kimliği, private makbuz yolu, OCR içeriği, sync metadata, outbox ve conflict kayıtları yedeğe girmez.
- Decode ve tüm referans doğrulamaları herhangi bir Room yazmasından önce tamamlanır; bilinmeyen sürüm veya alan fail-closed reddedilir.
- İçe aktarma bütünüyle tek Room transaction'ında entity + V2 outbox üretir; herhangi bir hata hiçbir kalıcı değişiklik bırakmaz.

Yeni araç veya geliştirici:

1. Önce [AGENTS.md](AGENTS.md) ve bu belgeyi okur.
2. Mevcut kodun sınırlarını doğrular; varsayımla yeni katman eklemez.
3. Değişiklik bu sözleşmeyi etkiliyorsa önce kısa bir mimari karar önerir.
4. Onaylanan karar bu belgede ve gerekirse `docs/adr/` altında güncellenir.
5. Kod, test ve ilgili belgeler aynı değişiklik setinde tutulur.

### İşlem durumunun sunumu (2026-09-14)

İşlem domain modeli, Room mapper üzerinden kullanıcıya gösterilecek nullable SyncStatus taşır; version/baseVersion ve outbox teknik alanları UI'a taşınmaz. Çakışma snapshot JSON'u repository katmanında Transaction modeline çevrilir; UI yalnız bu domain karşılaştırmasını tüketir. Bilinmeyen veya okunamayan durum senkronize kabul edilmez.
