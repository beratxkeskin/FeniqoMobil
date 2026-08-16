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

## 9. Hata ve durum yönetimi

- Data/SDK hataları kullanıcı metni olmayan kararlı `AppError` türlerine çevrilir.
- Repository operasyonları tanımlı `RepositoryResult` döndürür.
- Uzun yaşayan UI durumu `StateFlow`, tek seferlik olaylar `SharedFlow` ile sunulur.
- Cancellation yakalanıp normal hata gibi yutulmaz.
- Hassas veri, token, parola, makbuz içeriği veya finansal payload loglanmaz.

## 10. Test sınırları

- Saf domain ve use case kuralları `commonTest` içinde test edilir.
- Sync motoru fake remote/DAO sınırlarıyla deterministik test edilir.
- Room DAO ve migration davranışları Android host/instrumentation testlerinde doğrulanır.
- Android debug APK ve iOS Simulator ARM64 ortak kod derlemesi ana altyapı adımlarında çalıştırılır.
- Production şeması yalnız staging kabul testinden ve açık onaydan sonra değiştirilebilir.

## 11. Mimari değişiklik süreci

Yeni araç veya geliştirici:

1. Önce [AGENTS.md](AGENTS.md) ve bu belgeyi okur.
2. Mevcut kodun sınırlarını doğrular; varsayımla yeni katman eklemez.
3. Değişiklik bu sözleşmeyi etkiliyorsa önce kısa bir mimari karar önerir.
4. Onaylanan karar bu belgede ve gerekirse `docs/adr/` altında güncellenir.
5. Kod, test ve ilgili belgeler aynı değişiklik setinde tutulur.

