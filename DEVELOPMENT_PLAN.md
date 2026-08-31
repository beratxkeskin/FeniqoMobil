# FeniqoMobil Geliştirme Planı

> Standart araçlar için güncel çalışma giriş noktasıdır. Ayrıntılı alt görevler, checkbox'lar ve
> tarihsel ilerleme notlarında [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)
> tek yetkili kaynaktır; bu dosya aynı yüzlerce checkbox'ı kopyalamaz.

## Güncel durum

- Aktif çalışma: **8.2 — Tekrarlayan İşlemler ve Abonelikler (Recurring Transactions)** (Devam ediyor).
- Tamamlanan dilimler: Dilim 1A–1E (vade hesaplama, aday planlama, Room atomik occurrence üretimi, repository/use-case, Android WorkManager), Dilim 2A (saf command/validation kuralları) ve Dilim 2B (Supabase V2 migration ve 28 senaryolu SQL sözleşme testi).
- Sıradaki teknik adım: Recurring rule için Android/KMP V2 istemci entegrasyonu (DTO/mapper, `SyncEntityType.RECURRING_TRANSACTION`, outbox ACK/pull ve CRUD akışı). UI ve abonelikler daha sonra.
- Production Supabase durumu: migration uygulanmadı.
- Staging: `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`); 12/12 migration (`20260830000100_sync_write_v2_recurring_transactions.sql` dâhil), RLS, `sync_write_v2` RPC ve 28 senaryolu SQL sözleşme testi doğrulandı; koşulsuz ROLLBACK ile test verisi bırakılmadı.

## Tamamlanan fazlar

| Faz | Durum | Çıktı |
|---|---|---|
| 1. Analiz ve kapsam | Tamamlandı | Web envanteri, V1 sınırı, güvenlik ve migration planı |
| 2. Proje omurgası | Tamamlandı | KMP modülleri, bağımlılıklar, tema ve UI kabuğu |
| 3. Domain | Tamamlandı | Temel/ikinci dalga modeller, repository sözleşmeleri ve use case'ler |
| 4. Yerel veri | Tamamlandı | Room v3/v4, DAO/mapper, SQLCipher/Keystore ve outbox |
| 5. Uzak veri | Tamamlandı | Auth, DTO/remote, sync motoru, staging kabulü ve Realtime |
| 6.1 Arka plan sync | Tamamlandı | Hilt CoroutineWorker, BackgroundSyncScheduler, exponential backoff, KEEP / APPEND_OR_REPLACE |
| 6.2 Senkronizasyon gözlemi | Tamamlandı | Room v4 SyncOverview Flow, NetworkConnectivityObserver, ViewModel, SyncStatusIndicator Compose bileşeni |
| 7.1 Navigasyon ve UI durumları | Tamamlandı | Type-safe Navigation Compose rotaları, bağımsız Auth/Main NavHost, RootNavViewModel, stateless FeniqoAppShell |
| 7.2 Giriş ve kayıt ekranları | Tamamlandı | LoginScreen, RegisterScreen, tipli validation/hata eşleme, Login/RegisterViewModel, session tabanlı akış, staging auth |
| 7.4 Dashboard | Tamamlandı | Stateless DashboardScreen, DashboardViewModel, Hilt modülleri, dinamik ay Room Flow SSOT, son işlemler, işlem/düzenleme navigasyonları, geçici MoneyScore kartı ve ön değerlendirme |
| 8.1 Bütçeler | Tamamlandı | Bütçe listesi, dinamik ay gezinimi, %80 uyarı ve %100 aşım, harcama kategorisiyle bütçe ekleme, ID tabanlı Room SSOT form düzenlemesi, onaylı silme ve kopyalama akışları, Room V2 outbox/ACK, Staging V2 SQL migration, sözleşme testi ve Android emülatör manuel smoke kabulü |

5.1'de Android için build configuration ve güvenli oturum saklama uygulanmıştır. iOS `.xcconfig`,
Keychain ve üretim güvenlik adaptörlerinin kalan kısmı Android-first kararı gereği 10.4'te tamamlanır.

## Aktif faz: 8.2 Tekrarlayan İşlemler ve Abonelikler (Devam Ediyor)

Amaç: Tekrarlayan işlem ve abonelik domain modelleri, Room V2 outbox desteği, Supabase V2 migration ve kullanıcı arayüzü akışlarını tamamlamak. Dilim 1A–2B tamamlandı; V2 istemci entegrasyonu (DTO/mapper/outbox/CRUD) ile devam ediliyor.

## Sonraki fazlar

### 7. Presentation — V1 kullanıcı akışı

1. Type-safe navigasyon ve UI state sözleşmeleri.
2. Splash/oturum, giriş ve kayıt ekranları.
3. İşlem listesi, ekleme/düzenleme/silme ve filtreleme.
4. Kategori yönetimi.
5. Dashboard ve sync durumu.

### 8–9. Genişletmeler ve cihaz özellikleri

Bütçe, tekrarlayan işlemler, hedef/borç, workspace, varlık/rapor, biyometri, OCR ve veri
taşınabilirliği çekirdek V1 kabulünden sonra ele alınır.

### 10. Kalite ve yayın

- otomatik test kapsamının tamamlanması;
- statik analiz ve performans;
- Android release/internal testing;
- iOS Keychain, DB güvenliği ve SwiftUI ürün akışı.

## Her adım için çalışma döngüsü

1. İlgili sözleşme ve mevcut kod okunur.
2. Küçük, geri alınabilir uygulama dilimi seçilir.
3. Kod ve hedefli test birlikte yazılır.
4. Riskle orantılı geniş regresyon çalıştırılır.
5. Tamamlanma ölçütü karşılanırsa checkbox ve ilerleme notu güncellenir.
6. Commit/push yalnız proje sahibinin açık isteğiyle yapılır.

## Doğrulama komutları

Windows proje kökünde:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

Migration için yalnız doğru hedef doğrulandıktan sonra `supabase` CLI kullanılır. Production
komutları ayrı açık onay olmadan çalıştırılmaz.

## Plan güncelleme kuralı

- Ayrıntılı görev durumu ve ilerleme notu `FENIQO_MOBIL_YOL_HARITASI.md` içinde güncellenir.
- Bu dosyada yalnız güncel faz, sıradaki faz ve yüksek seviyeli tamamlanma özeti tutulur.
- Ürün kapsamı değişirse `PRODUCT.md` ve `FEATURES.md` de güncellenir.
- Mimari veya veri sözleşmesi değişirse ilgili ana belge aynı değişiklik setinde güncellenir.
