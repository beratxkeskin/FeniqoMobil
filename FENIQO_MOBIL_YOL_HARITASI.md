# FeniqoMobil — Uçtan Uca Geliştirme Yol Haritası

> 2026-09-15 — İşlemler 01–18 tasarım uyarlamasının mevcut mimarinin desteklediği kapsamı tamamlandı ve doğrulandı: [İşlemler kabul kaydı](docs/ISLEMLER_TASARIM_KABUL.md). Kalıcı makbuz dosya yaşam döngüsü, tutarla ortak dağıtım sözleşmesi ve oturumlu cihaz kabulü açık altyapı işleridir.
> 2026-09-16 — Görev 7.3-C1 (Kalıcı Makbuz Yaşam Döngüsü ve Saf Karar Kuralları): Değişmez dosya kimliği (`ReceiptFileDescriptor`), işlem bağlantısı (`TransactionReceiptLinkage`), monoton revizyon/sessionEpoch, koşullu bağlama karar motoru (`ReceiptBindingDecisionEngine`) ve belirsiz upload uzlaştırma kuralları saf domain katmanında modellendi ve test edildi. Domain kuralları uygulandı; kalıcı makbuz özelliği henüz kullanılamıyor.
> 2026-09-16 — Görev 7.3-D2 (Custom Split Saf Domain, Doğrulama ve Settlement): Platformdan bağımsız domain modeli (`TransactionSplitMode`, `TransactionParticipantShare`), `TransactionCommand` ve `Transaction` geriye uyumlu entegrasyonu, tipli `TransactionValidationRules` doğrulama ve normalizasyon kuralları, `EqualSplitCalculator` saf kuruş remainder motoru, `WorkspaceSettlementCalculator` ve `ObserveWorkspaceSettlementUseCase` fail-closed dışlama (`hasExcludedExpenses = true`) kuralları tamamlandı. 7.3-D2 saf domain ve settlement kuralları tamamlandı; Custom Split henüz Room, sync veya UI üzerinden kullanılamıyor.
> **Durum:** Devam ediyor — son tamamlanan Android-first ana adım: Faz 8.3 (Hedefler ve Borçlar); sonraki adım: Faz 8.4 (Ortak Çalışma Alanları / Workspaces).
> **Ana hedef:** Feniqo web uygulamasını referans alarak, Android'de native çalışan; offline-first; Supabase ile güvenli biçimde senkronize olan ve gelecekte iOS'a Kotlin Multiplatform (KMP) ile taşınabilen profesyonel bir mobil uygulama geliştirmek.

**İlerleme notu (2026-09-17 — Onaylı Tasarımlarla Tam Kimlik Doğrulama ve Güvenli Parola Kurtarma Akışı):** Onaylı tasarım panolarına (Pano A-01..04, Pano B-05..08, Pano C-09..14) sadık tam auth akışı uygulandı: Hoş Geldiniz (`WelcomeScreen`), Giriş Yap (`LoginScreen`), Kayıt Ol (`RegisterScreen`), E-posta Doğrulama (`AuthEmailVerificationScreen`), Parolamı Unuttum (`ForgotPasswordScreen`), E-posta Gönderildi (`PasswordResetSentScreen`), Yeni Parola Belirle (`ResetPasswordScreen`) ve Başarı (`PasswordResetSuccessScreen`). Güvenli deep link (`feniqo://auth/callback`) yalnızca Supabase Auth Kotlin SDK üzerinden doğrulanarak (`importAuthToken` / `exchangeCodeForSession`) `AuthRecoveryState.Verified` oturumuna dönüştürülür. Parola kurtarma oturumu `AppAuthState.PasswordRecovery` ile izole edilerek kullanıcının Dashboard'a sızması engellenir; parola güncelleme başarısında oturum ve recovery state tamamen temizlenir. Tipli hata ayrımı (rate limit, ağ hatası, süresi dolmuş/geçersiz link) uygulandı. Token, URL ve parolalar hiçbir navigation argümanına, log'a veya kalıcı state'e yazılmaz. Android debug APK derlemesi ve 624 birim testi başarıyla tamamlandı.

**İlerleme notu (2026-09-17 — Gerçek Supabase Parola Değiştirme Akışı):** Parola değiştirme gerçek Supabase Auth çağrısına bağlandı (`AuthRemoteDataSource.changePassword`, `SupabaseAuthRemoteDataSource`, `OfflineFirstAuthRepository.changePassword`, `SupabaseAuthErrorMapper`, `SettingsViewModel.changePassword`, `ChangePasswordScreenRoute`). Mevcut parola `signInWith(Email)` ile yeniden doğrulanır; başarısız doğrulamada `updateUser` çağrılmaz ve oturum silinmez. Başarı yalnız uzak `updateUser` tamamlandıktan sonra UI'a iletilir. `SettingsViewModel` içinde yerel doğrulama sırası, fail-closed hata eşleme, `try/finally` ile loading garantisi, eşzamanlı çift gönderim koruması ve `clearPasswordStatus()` tek seferlik tüketim eklendi. Parolalar veya SDK hata gövdeleri loglanmaz. Staging/production Supabase üzerinde veri mutation'ı yapılmadı, migration üretilmedi; tüm hedefli testler doğrulandı, cihaz/staging adımları manuel kabul bekliyor.

**İlerleme notu (2026-09-13 — Profil ve Ayarlar görsel yenilemesi):** Profil özeti Room SSOT akışını `ProfileViewModel` üzerinden tüketir; Android Material 3 sıcak-lüks kart hiyerarşisi, semantik ikon yüzeyleri, erişilebilir Yakında durumu ve mevcut alt navigasyonla uyumlu type-safe Ortak Alanlar geçişi eklendi. Tema, uygulama kilidi/otomatik kilit, CSV dışa aktarma, JSON yedek içe aktarma ve güvenli çıkış mevcut servisleri üzerinden erişilebilir kaldı. Derleme ve manuel kabul, çalışma ortamındaki Gradle süreç kesintisi nedeniyle bekliyor; ürün kapsamı dışındaki profil düzenleme/fotoğraf, premium, banka/entegrasyon, bildirim, gizlilik ve yardım satırları aktifleştirilmedi.

**İlerleme notu (2026-09-13 — Abonelikler Gerçek Altyapı, Form, Detay Ekranı ve Ödeme Kalıcılığı İyileştirmeleri):** Abonelik yaşam döngüsü (`ACTIVE`, `PAUSED`, `CANCELLED`, `TRIAL`, `EXPIRED`), atomik fiyat geçmişi kaydı ve artış rozetleri, atomik gerçekleşen ödeme olayları ve takvim ayı/yılı harcama hesabı, saf Kotlin `SubscriptionAnalyticsCalculator`; Room v15→v16→v17 ileri migration'ları (`16.json`, `17.json`, `website_url` ve `notes` sütunları); forward-only Supabase migration'ları (`20260913000100` ve `20260913000200`); `LocalMutationDao` içindeki Room `@Upsert` gövdeli metot hatasının giderilmesi (ödemelerin ve fiyat geçmişlerinin SQLite'a başarıyla yazılıp aylık grafik ve ödeme listesini reaktif tetiklemesi sağlandı); telefon ekranlarındaki alt/üst beyaz boşlukların (insets) ve taşmaların giderilmesi, alt butonların akışa alınması; Abonelik Detayları kartına semantik ikonların eklenmesi; 4'lü aksiyon satırının detay kartının hemen altına taşınması; dikey boşlukların sıkılaştırılarak kaydırmaya gerek kalmadan tüm bilgilerin tek ekranda görünür kılınması; **Görsel 1: Abonelik Ekle/Düzenle Formu** ve **Görsel 2: Abonelik Detay Ekranı** tamamlandı. Hedefli birim/host testleri (Room, ViewModel, UseCase, UI), iOS simülatör derlemesi ve Android debug APK derlemesi başarıyla geçti; manuel emülatör kabulü bekleniyor.

**İlerleme notu (2026-09-12 — Hedef Detay ilk dilim):** Hedef kartlarından ayrı `GoalDetailRoute` akışı, Room SSOT hedef/katkı gözlemi, ADD/REMOVE yönlü katkı navigasyonu, onaylı soft-delete, sıcak-lüks detay hiyerarşisi, taşma/para-birimi kontrollü ilerleme ve katkı grafiği, yaklaşık aylık gereksinim, güven koşullu katkı-hızı tahmini ve yalnız hedef katkılarından oluşan Son Hareketler uygulandı. Otomatik hedefli hesaplama testi ve debug APK doğrulandı; manuel emülatör kabulü bekleniyor. Açıklama ve kapak medyası zinciri uygulanmadığından ana kapsam tamamlandı işaretlenmedi; Room v15 ve uzak Supabase şeması değiştirilmedi.

Bu dosya projenin çalışma sözleşmesidir. Bir adım tamamlandığında ilgili kutu işaretlenir ve kısa bir not eklenir. Sohbette yalnızca örneğin **"2.3'te kalmıştık"** demen, aynı noktadan devam etmemiz için yeterlidir.

---

## 0. Çalışma ilkeleri ve mimari kararlar

### 0.1 Değişmez teknik ilkeler

- [x] Uygulama dili tamamen Kotlin olacak.
- [x] Minimum Android sürümü API 26 (Android 8.0) olacak.
- [x] UI Jetpack Compose ve Material 3 ile geliştirilecek.
- [x] Clean Architecture + MVVM uygulanacak.
- [x] UI, yalnızca ViewModel'den gelen `StateFlow`/`SharedFlow` verisini tüketecek.
- [x] Room, uygulamanın **tek okuma kaynağı** (Single Source of Truth) olacak.
- [x] Repository, yerel veritabanı ve Supabase arasındaki senkronizasyonun tek sorumlusu olacak.
- [x] Her yazma işlemi önce yerel veritabanına güvenli olarak kaydedilecek; ağ yoksa senkronizasyon kuyruğuna alınacak.
- [x] Tüm bağımlılıklar constructor injection ile Hilt üzerinden sağlanacak.
- [x] Para tutarları `Double` ile değil, en küçük para birimi cinsinden `Long` ile tutulacak. Örnek: `125,50 TRY` → `12550` kuruş.

### 0.2 KMP stratejisi

- [x] Proje Android-first başlayacak fakat Gradle yapısı KMP uyumlu tutulacak.
- [x] Platformdan bağımsız modeller, iş kuralları, use case'ler, DTO'lar ve senkronizasyon sözleşmesi `commonMain` için tasarlanacak.
- [x] Android'e bağımlı parçalar (`Hilt`, `WorkManager`, `BiometricPrompt`, `ML Kit`, Android Keystore) `androidMain`/`androidApp` içinde kalacak.
- [x] `iosApp` SwiftUI kabuğu eklendi; güvenlik, veritabanı açma ve cihaz servisleri platform adaptörleri ile soyutlanacak.
- [x] İlk iOS UI stratejisi SwiftUI + `sharedLogic` ortak iş mantığı olarak belirlendi.

### 0.3 Paket düzeni

```text
com.feniqo.mobile/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── local/
│   ├── remote/
│   ├── repository/
│   ├── mapper/
│   └── sync/
├── presentation/
│   ├── theme/
│   ├── navigation/
│   ├── component/
│   └── screens/
└── di/
```

**Tamamlanma ölçütü:** Bu ilkeler sonraki kararlar ve kod incelemelerinde referans alınır; gerekçesiz istisna eklenmez.

---

## 1. Analiz ve ürün sınırının netleştirilmesi

### 1.1 Web referansını envantere dönüştürme

- [x] Web uygulamasındaki ekranları ve kullanıcı akışlarını listele.
- [x] Supabase tablolarını, ilişkilerini ve RLS politikalarını ayrı bir veri sözlüğünde belgele.
- [x] TypeScript modelleri ile SQL şeması arasındaki uyumsuzlukları kaydet.
- [x] Mevcut demo verilerini yalnızca geliştirme/test amacıyla ayır.

**Beklenen çıktı:** Mobilde geliştirilecek modüllerin kesin listesi ve bir veri sözlüğü.

### 1.2 V1 kapsamını belirleme

- [x] V1'e dahil modülleri onayla: kimlik doğrulama, kişisel çalışma alanı, kategori, işlem, dashboard ve offline sync.
- [x] V1 sonrası modülleri sırala: bütçe, hedef, tekrarlayan işlem, abonelik, borç, ortak alan, rapor, varlık, OCR.
- [x] Demo modu için ürün kararı ver: yalnızca test fixture/fake repository; kullanıcıya açık demo V1 dışında.
- [x] Türkçe ilk dil ve İngilizce desteğe hazır i18n stratejisini belirle.

**Tamamlanma ölçütü:** V1 dışındaki bir özellik, çekirdek akışı geciktirmez.

### 1.3 Supabase ve güvenlik denetimi

- [x] SQL şemasını ilk kurulum ve sürümlü migration dosyalarına ayırma planını oluştur.
- [x] `workspace_members` rol sözlüğünü tekleştir: `OWNER`, `EDITOR`, `VIEWER`.
- [x] Ortak alan kayıtlarında görüntüleme, ekleme, düzenleme ve silme yetkilerini bir matris olarak tanımla.
- [x] Makbuz deposunu public yerine sahiplik denetimli private bucket olarak planla.
- [x] Piyasa fiyatı gibi ortak veriler için doğrudan istemci yazımı yerine Edge Function/sunucu görevi yaklaşımını seç.
- [x] Her senkronize tablo için `updated_at`, `deleted_at` ve sürümleme gereksinimini kesinleştir.

**Tamamlanma ölçütü:** Mobil istemci için güvenli API ve veri sözleşmesi açıkça tanımlıdır.

---

## 2. Proje omurgası ve geliştirme ortamı

### 2.1 Yeni Android/KMP proje kurulumu

- [x] Android Studio'da Kotlin tabanlı yeni proje oluştur.
- [x] Paket adı, uygulama adı, API 26 ve Git düzenini belirle.
- [x] KMP hedefleri için boş ama derlenebilir temel yapı oluştur: Android; gelecek için iOS hedefleri.
- [x] Gradle Version Catalog (`libs.versions.toml`) kullanımını ayarla.
- [x] Debug ve release uygulama kimliklerini belirle.

**Öğrenme odağı:** Gradle modülleri, source set'ler (`commonMain`, `androidMain`, `iosMain`) ve bağımlılık yönetimi.

### 2.2 Temel Android bağımlılıkları

- [x] Compose + Material 3 bağımlılıklarını ekle.
- [x] Hilt ve KSP kurulumunu yap.
- [x] Navigation Compose, Lifecycle ve ViewModel bağımlılıklarını ekle.
- [x] Coroutines, Serialization ve Ktor bağımlılıklarını ekle.
- [x] Test bağımlılıklarını ekle: JUnit, coroutine test, Turbine, MockK/Fake yaklaşımı.

**Doğrulama:** Boş uygulama derlenir ve emülatörde açılır.

### 2.3 Tema, tasarım sistemi ve temel UI kabuğu

- [x] Emerald Phoenix marka renklerini Material 3 color scheme'e dönüştür.
- [x] Açık, koyu ve sistem teması desteğini kur.
- [x] Tipografi, boşluk, köşe yarıçapı ve durum renklerini token olarak tanımla.
- [x] Tekrar kullanılabilir bileşenleri oluştur: yükleniyor, boş durum, hata, onay diyaloğu, snackbar.
- [x] Bottom navigation ve uygulama iskeletini tasarla.

**Doğrulama:** Tema değişimi uygulama yeniden açıldığında korunur; erişilebilir kontrast kontrol edilir.

**İlerleme notu (2026-09-05 görsel yenileme):** Production UI tasarımının ilk dilimi mevcut Compose mimarisi korunarak uygulandı. Açık tema `#F4FAF7`/`#FBFDFC`, koyu tema `#0B1410`/`#16281E`, resmi emerald ve durum renkleriyle genişletildi; 20 dp boşluk, 48/56 dp dokunma hedefleri ve tabular sayı stili eklendi. Alt navigasyondaki metin/emoji sembolleri ortak Canvas çizgi ikonlarına çevrildi, merkez `Ekle` eylemi 56 dp yapıldı ve son hedef Profil olarak sunuldu. `sharedUI` testleri ve Android debug APK derlemesi başarılıdır.

**İlerleme notu (2026-09-05 görsel yenileme, dilim 2):** Mevcut Room SSOT, ViewModel ve navigasyon sözleşmeleri değiştirilmeden Dashboard finans özeti net bakiye odaklı hero düzene taşındı; gelir/gider/tasarruf ikincil metrikleri sadeleştirildi, işlem gider tutarları renk yanında eksi işaretiyle de ayrıştırıldı ve Plan/Profil modül menülerindeki emoji dili nötr çizgisel semboller ile düz yüzeylere dönüştürüldü. Android ortak UI derlemesi başarılıdır.

---

## 3. Domain katmanı — saf iş kuralları

### 3.1 Ortak temel tipler

- [x] `Money`, `Currency`, `EntityId`, `LocalDate`, `SyncStatus` ve ortak hata modellerini tasarla.
- [x] Para formatlama ile para hesaplamasını ayır.
- [x] Zaman dilimi kurallarını belirle: işlem tarihi yerel tarih, sunucu zamanları UTC.

### 3.2 Çekirdek domain modelleri

- [x] `UserProfile` modelini oluştur.
- [x] `Workspace` ve `WorkspaceMember` modellerini oluştur.
- [x] `Category` modelini oluştur.
- [x] `Transaction` modelini oluştur; taksit, ödeme yöntemi ve makbuz alanlarını dahil et.
- [x] `Tag` ve işlem-etiket ilişkisini modelle.
- [x] `Budget` modelini oluştur.

### 3.3 İkinci dalga domain modelleri

- [x] `RecurringTransaction` ve tekrar kuralını oluştur.
- [x] `Goal`, `Debt`, `Subscription` modellerini oluştur.
- [x] `Asset` ve piyasa fiyatı modellerini oluştur.
- [x] Dashboard özetleri, MoneyScore ve rapor modellerini oluştur.

**Tamamlanma ölçütü:** Bu sınıflar Android, Room, Supabase veya Compose import etmez.

### 3.4 Repository sözleşmeleri

- [x] `AuthRepository` arayüzünü tanımla.
- [x] `TransactionRepository`, `CategoryRepository`, `BudgetRepository` arayüzlerini tanımla.
- [x] `WorkspaceRepository`, `SyncRepository` ve tercih/güvenlik arayüzlerini tanımla.
- [x] Her okuma metodunu uygun `Flow` türüyle tasarla.
- [x] Her yazma metodunun başarılı/başarısız sonucunu tanımlı bir sonuç tipiyle döndürmesini sağla.

### 3.5 Use case'ler

- [x] İlk use case'ler: işlem ekle, düzenle, sil, filtrele, kategori ekle ve dashboard özetini getir.
- [x] İşlem doğrulamalarını use case katmanında uygula.
- [x] MoneyScore ve bütçe hesaplamalarını saf fonksiyon/use case olarak uygula.
- [x] Her use case için birim testi yaz.

---

## 4. Yerel veri katmanı — Room ve offline-first temel

### 4.1 Room şeması

- [x] Domain modellerinden ayrı Room entity sınıflarını oluştur.
- [x] Her entity'ye yerel senkronizasyon alanlarını ekle: `syncStatus`, `updatedAt`, `deletedAt`, `version`.
- [x] Primary key, foreign key, indeks ve unique kısıtlarını tasarla.
- [x] `TransactionTagCrossRef` gibi ilişki tablolarını oluştur.
- [x] Arama, tarih ve çalışma alanı filtreleri için indeksleri tanımla.

### 4.2 DAO'lar ve mapper'lar

- [x] Her çekirdek model için DAO oluştur.
- [x] DAO okumalarını `Flow` ile sun.
- [x] Çok tablolulu yazma işlemlerini Room transaction içinde tut.
- [x] Entity ↔ domain dönüşümlerini `data.mapper` altında yaz.
- [x] DAO testlerini in-memory test veritabanında çalıştır.

### 4.3 Şifreli yerel veritabanı

- [x] Android SQLCipher entegrasyonunu yap.
- [x] Veritabanı parolasını Android Keystore koruması altında oluştur/sakla.
- [x] Anahtar kaybı, uygulama kaldırma ve cihaz değişimi senaryolarını belgele.
- [x] Şifrelenmiş DB'nin gerçekten açıldığını entegrasyon testiyle doğrula.

### 4.4 Offline yazma kuyruğu

- [x] `sync_operations`/outbox tablosunu tasarla.
- [x] Ekleme, güncelleme ve silme olaylarını sırayla kuyruğa ekle.
- [x] İşlemi yerel DB + outbox'a atomik olarak kaydet.
- [x] Başarısız senkronizasyonda deneme sayısı, son hata ve geri çekilme bilgisini kaydet.

**Tamamlanma ölçütü:** İnternet kapalıyken eklenen bir işlem kapanıp açıldıktan sonra da görünür.

---

## 5. Uzak veri katmanı — Supabase

### 5.1 Supabase istemcisi ve oturum

- [x] Supabase Kotlin SDK ve hedefe uygun Ktor istemcisini ekle.
- [ ] URL ve publishable key'i güvenli build configuration üzerinden sağla; gizli service-role anahtarını uygulamaya koyma.
- [x] E-posta/şifre ile kayıt, giriş, oturum yenileme ve çıkış akışlarını oluştur.
- [ ] Oturum bilgisini platforma özel güvenli depoda tut.
- [x] Hata kodlarını kullanıcı dostu domain hatalarına dönüştür.

### 5.2 DTO ve remote data source

- [x] Her çekirdek tablo için `@Serializable` DTO oluştur.
- [x] DTO ↔ domain dönüşümlerini yaz.
- [x] Sayfalama, tarih aralığı ve çalışma alanı filtrelerini destekle.
- [x] Supabase Storage'a private makbuz yükleme/indirme sözleşmesini oluştur.

### 5.3 Senkronizasyon motoru

- [x] İlk girişte buluttan yerel DB'ye başlangıç indirmesini uygula.
- [x] Outbox'taki işlemleri sırayla Supabase'e gönder.
- [x] Başarılı gönderimleri kuyruktan sil veya tamamlandı olarak işaretle.
- [x] Sunucudan değişen kayıtları Room'a upsert et.
- [x] `baseVersion` çakışmalarında iki kopyayı koru; V1 için kullanıcıya yerel veya uzak sürümü seçtir.
- [x] Hata durumunda güvenli tekrar deneme ve kullanıcıya görünür sync durumu oluştur.
- [x] Migration, RLS ve gerçek PostgREST/RPC akışını ayrı staging Supabase projesinde kabul testinden geçir.

### 5.4 Realtime

- [x] V1 cihazlar arası Realtime yayınını `profiles`, `categories` ve `transactions` ile sınırla.
- [x] Realtime olayı geldiğinde UI'ı değil, Room verisini güncelle.
- [x] Bağlantı kaybı ve yeniden bağlanma davranışını test et.

---

## 6. Arka plan işleri ve güvenilir senkronizasyon

### 6.1 WorkManager

- [x] Hilt destekli `CoroutineWorker` kur.
- [x] Uygulama başlangıcında benzersiz ilk senkronizasyon işi planla.
- [x] Ağ bağlantısı koşulu ile outbox senkronizasyonu çalıştır.
- [x] Hata durumunda `Result.retry()` ve exponential backoff uygula.
- [ ] Periyodik abonelik/bütçe kontrolünü planla. (Bütçe/abonelik modülüne ertelendi)

### 6.2 Senkronizasyon gözlemi

- [x] Kullanıcıya son senkronizasyon zamanı ve bekleyen işlem sayısını göster.
- [x] Bağlantı durumunu izleyip müdahaleci olmayan bir offline göstergesi sun.
- [x] Kullanıcının manuel senkronizasyon başlatabilmesini sağla.

**İlerleme notu (Faz 6.2):** Room v4 migration ile `SyncOverview` (son başarılı sync, pending, failed, conflict sayıları) Flow'u, platformlar arası `NetworkConnectivityObserver` sözleşmesi ve Android `ConnectivityManager` implementasyonu, use case'ler (`ObserveSyncOverviewUseCase`, `RequestManualSyncUseCase`, `RetryFailedSyncOperationsUseCase`), `SyncStatusViewModel`, erişilebilir ve açık/koyu temada okunabilirliği iyileştirilmiş `SyncStatusIndicator` Compose bileşeni ile `FeniqoAppShell` entegrasyonu tamamlandı. Toplam 148 test (99 sharedLogic, 12 sharedUI, 37 androidApp) ile doğrulandı.

**Tamamlanma ölçütü:** Uygulama kapalıyken oluşan ağ dönüşünde bekleyen yazılar güvenle gönderilir.

---

## 7. Presentation katmanı — ilk ürün akışı

### 7.1 Navigasyon ve UI durumları

- [x] Type-safe Navigation Compose rotalarını oluştur.
- [x] `DashboardUiState`, `TransactionUiState` gibi sealed UI state'leri tasarla.
- [x] Tek seferlik olayları `SharedFlow` ile yönet: snackbar, navigasyon, izin isteği.
- [x] Her ViewModel'i Hilt constructor injection ile oluştur.

**İlerleme notu (Faz 7.1):** Android'e özel type-safe Navigation Compose rotaları (`FeniqoRoute`), oturum durumuna göre bağımsız `AuthNavHost` ve `MainNavHost` dalları, session tabanlı `RootNavViewModel`, stateless `FeniqoAppShell`, dört ana sekme (Özet, İşlemler, Kategoriler, Ayarlar) için type-safe `hasRoute<T>()` eşlemesi ve placeholder içerikleri uygulandı. Auth ekranlarında bottom bar ve `SyncStatusIndicator` izolasyonu sağlandı. Manuel smoke testler ve toplam 162 test (100 sharedLogic, 12 sharedUI, 50 androidApp) ile doğrulandı. Placeholder ekranlar sonraki adımlarda gerçek kullanıcı ekranlarıyla değiştirilecektir.

### 7.2 Kimlik doğrulama ekranları

- [x] Açılış/splash ve oturum kontrolü (`WelcomeScreen`, `AppAuthState`).
- [x] Giriş ekranı (Pano A-02 ve Pano C-09/11/13/14).
- [x] Kayıt ekranı (Pano A-03 ve Pano C-10).
- [x] E-posta doğrulama ekranı (Pano A-04, 60s cooldown ve Pano C-14).
- [x] Parolamı unuttum ekranı (Pano B-05).
- [x] E-posta gönderildi bilgilendirme ekranı (Pano B-06).
- [x] Doğrulanmış recovery deep link ile yeni parola belirleme ekranı (Pano B-07 ve Pano C-12).
- [x] Parola başarıyla güncellendi ekranı ve temiz çıkış (Pano B-08).
- [x] Şifre görünürlüğü, doğrulama, hata ve yüklenme durumları.
- [x] Supabase Auth SDK ile doğrulanmış recovery oturumu (`AuthRecoveryState.Verified`), PKCE code exchange, fragment token import ve `PasswordRecovery` izole oturumu.

**İlerleme notu (Faz 7.2):** Stateless Compose `LoginScreen`, `RegisterScreen`, `WelcomeScreen`, `AuthEmailVerificationScreen`, `ForgotPasswordScreen`, `PasswordResetSentScreen`, `ResetPasswordScreen`, `PasswordResetSuccessScreen` onaylı tasarım panolarına (Pano A/B/C) birebir sadık kalınarak uygulandı. Tipli validation (`AuthValidationRules`), `AuthUiMessage` hata ve durum eşlemesi, `LoginViewModel`, `RegisterViewModel`, `ForgotPasswordViewModel`, `ResetPasswordViewModel`, `EmailVerificationViewModel`, `AuthUseCaseModule` Hilt bağlantıları, parola bilgilerinin kalıcılaştırılmaması (RAM/geçici state izolasyonu), `RootNavViewModel` ile session tabanlı otomatik navigasyon ve doğrulanmış kurtarma oturumu izolasyonu tamamlandı. Tüm birim/Compose testleri ve debug APK derlemesi başarıyla doğrulandı.

### 7.3 İşlem ve kategori akışı

- [ ] İşlem listesi: tarih gruplama, arama, filtreleme ve boş durum.
- [ ] İşlem ekleme/düzenleme ekranı.
- [x] Kategori seçimi ve kategori yönetimi (Onaylı 01–12 panelleri birebir uygulandı: açık özet kartı, dönem/tür seçici, modern liste, özel/sistem ayrımı, 20 simgeli ve 12 renkli canlı önizlemeli form, 4x3 ay seçici, silme diyaloğu, boş filtre durumu).
- [x] Custom Split (Tutara göre özel ortak dağıtım) Room v19 yerel kalıcılığı: `TransactionEntity` split_mode ve participant_shares_json sütunları, kanonik deterministik codec, fail-closed mapper sentinel'i, ANDROID_MIGRATION_18_19, 19.json schema export, DB close/reopen ve bozuk JSON dayanıklılık testleri (DTO/outbox/Supabase ve UI entegrasyonu henüz yapılmadı).
- [ ] Taksitli işlem oluşturma ve silme davranışı.
- [ ] Makbuz bağlama için UI hazırlığı.

**İlerleme notu (2026-09-17 — Görev 7.3-D3 Custom Split Room V19 Kalıcılığı ve Migration):** Custom Split Room v19 yerel kalıcılığı tamamlandı; DTO/outbox/Supabase ve UI entegrasyonu henüz yapılmadı.
1. `TransactionEntity` içine `@ColumnInfo(name = "split_mode", defaultValue = "'EQUAL'") val splitMode: String = TransactionSplitMode.EQUAL.name` ve `@ColumnInfo(name = "participant_shares_json", defaultValue = "'[]'") val participantSharesJson: String = "[]"` eklendi.
2. `TransactionSplitPersistenceCodec` ortak modülde (`commonMain`) oluşturuldu; userId artan sıralı deterministik kanonik JSON kodlaması ve fail-closed tipli çözümleme uygulandı.
3. `FinanceMappers` içinde bozuk/bilinmeyen JSON için kontrollü `splitMode = CUSTOM`, `participantShares = emptyList()` fail-closed sentinel'i üretildi; böylece Room Flow çökmeden settlement doğrulamasında `CUSTOM_SPLIT_SHARES_REQUIRED` hatasıyla `hasExcludedExpenses = true` olarak dışlanması sağlandı.
4. `FeniqoDatabase` sürümü 19'a yükseltildi, `ANDROID_MIGRATION_18_19` factory zincirine eklendi ve KSP tarafından `19.json` şeması üretildi (v18 şeması korundu).
5. Tüm codec, mapper, 18→19 migration, 17→19 zincir migration, DB close/reopen kalıcılığı ve bozuk JSON dayanıklılık testleri başarıyla doğrulandı.

**İlerleme notu (2026-09-16 — Görev 7.3-A İşlem Akışları Doğrulama ve Hata Giderme):** İşlemler modülünün mevcut akışları denetlendi ve kanıtlanmış hatalar giderildi:
1. `TransactionSuccessViewModel`: Boş (`""`) veya yalnızca boşluk (`"   "`) içeren transactionId'lerin `EntityId` çağrısında çökmesi engellendi; tipli hata mesajı (`"Geçersiz işlem parametresi."`) ve use case çağırmama davranışı `TransactionSuccessViewModelTest` ile 5 farklı senaryoda doğrulandı.
2. Bütçe Detayı Tarih Filtresi: `onViewAllTransactions` çağrısında atılan `month` değeri `CategoryAnalyticsCalculator.calculateReportPeriods(month)` ile startDate ve endDate olarak `TransactionsRoute`'a aktarıldı. `FeniqoRoutesTest` içinde normal aylar (30/31 gün), standart Şubat (28 gün), artık yıl Şubat (29 gün) ve Aralık (31 gün) sınırları test edildi.
3. Çakışma Diyaloğu Sızıntı Koruması: Detay ekranı kapandığında (`onBack`) veya `selectedId` değiştiğinde `showConflict = false` sıfırlanarak başka işlemde açık kalması önlendi.
4. Taksit ve Offline Kanıtları: `THIS_AND_FOLLOWING`, aralıklı taksitler (1, 2, 4), atomik rollback ve Room yeniden açılma kalıcılığı testleri (`InstallmentUseCasesTest`, `TransactionsViewModelTest`, `RoomDaoTest`, `OfflineFirstTransactionRepositoryTest`) doğrulandı.
Tüm testler (`:sharedLogic:testAndroidHostTest` 1265 test, `:androidApp:testDebugUnitTest` 601 test, `:sharedUI:allTests` 292 test, `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64`) %100 başarıyla geçti; manuel oturumlu cihaz kabulü bekleniyor.

**İlerleme notu (2026-09-15 — Kategoriler Onaylı Tasarım Panelleri 01–12 Birebir UI Uygulaması):** Kullanıcının ilettiği onaylı tasarım panellerine (01 Liste, 02 Gider Kategorisi Oluşturma, 03 Gelir Kategorisi Düzenleme, 04 Simge Seçimi Modalı, 05 Renk Seçimi Modalı, 06 Dönem Seçimi Modalı, 07 Özel Kategori Eylemleri Menüsü, 08 Silme Onayı Diyaloğu, 09 Filtreli İşlemler Geçişi, 10 Boş Filtre Durumu, 11 Form Doğrulama Hataları, 12 Ağ Hatası ve Yükleme Durumu) tam sadık kalınarak; ana ekranın mevcut beğenilen omurgası korunup açık özet kartı (`SummaryStatsRow`, `SummaryTopCategorySection`), kompakt liste (`CategoryAnalyticsListItem`), mor `Özel` rozeti, giderde eksi kırmızı, gelirde artı yeşil, 0 işlemde nötr tutar formatı, kilitli tür gösterimi, 20 simge ve 12 renk paleti, canlı dinamik önizleme kartı, 4x3 kısa Türkçe ay seçici ve boş filtre yönlendirme kartı entegre edildi. Testler ve Kotlin derlemesi doğrulandı.

### 7.4 Dashboard

- [x] Aylık gelir, gider, net bakiye ve tasarruf oranı.
- [x] Son işlemler ve hızlı işlem ekleme.
- [x] Bütçe uyarı alanı için hazırlık.
- [x] MoneyScore kartı ve açıklanabilir hesaplama sonucu.

**İlerleme notu (Faz 7.4):** Stateless Compose `DashboardScreen`, `DashboardViewModel`, Hilt modül bağlantıları (`CalculateDashboardSummaryUseCase`, `ObserveDashboardSummaryUseCase`, `CalculateMoneyScoreUseCase`), `DashboardDisplayModelBuilder` ve type-safe Android route entegrasyonu tamamlandı. Room Flow SSOT üzerinden dinamik ay seçimi (`CurrentDateProvider`), TRY formatlama, net bakiye durum renkleri, en yüksek gider kategorisi, son 5 işlem listesi, hızlı "+ İşlem Ekle" FAB'ı ve tüm işlemleri görüntüleme/düzenleme navigasyonları bağlandı. MoneyScore için V1 şeffaflığıyla "Ön değerlendirme" rozeti, 4 alt puan çubuğu ve nötr başlangıç açıklaması gösterildi. Manuel smoke test ve birim testleriyle doğrulandı. (Not: 3 çakışma banner'ı ertelenmiş bilinen V1 conflict/outbox konusu olarak korunmaktadır; Faz 7.3/B5A işlem akışı kapsamındadır).

**İlerleme notu (2026-09-15 — Ana Sayfa Onaylı Tasarım Panelleri 01 & 02 Uygulaması):** Kullanıcının ilettiği onaylı tasarım görseline ("01 Ana Sayfa" ve "02 Aşağı kaydırınca") uygun olarak; modern sans-serif "feniqo" logosu, kullanıcı avatarı (profil navigasyonu), "Merhaba, $userName" ve "Ayına bir bakış" hiyerarşisi; koyu grafit "Dönem neti" kartı (tabular net tutar, döviz kodu, bu ayın işlem özeti, eşit 2 sütunlu Gelir ve Gider kutucukları); ince yeşil tasarruf oranı çubuğu; kategori ikonlu bütçe özeti (aşım/yaklaşma rozetleri); son işlemlerde onaylı renk kuralı (giderler eksi işaretli okunaklı kırmızı, gelirler yeşil, açıklamalar normal); Feniqo İçgörü kartı; yaklaşan ödemeler (gün/kısa ay rozetli); birikim hedefi; MoneyScore dairesel arc gauge ve ön değerlendirme kartı; farklı para birimi kapsam uyarısı; gerçek rotalara (Bütçeler, Abonelikler, Hedefler, İşlemler, Profil) bağlantılar uygulandı. `:sharedUI:testAndroidHostTest`, `:androidApp:testDebugUnitTest`, `:sharedLogic:testAndroidHostTest`, `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` %100 başarıyla doğrulandı.

**Tamamlanma ölçütü:** Kullanıcı offline iken işlem ekleyebilir, listeleyebilir; ağ geldiğinde senkronizasyonu görebilir.

---

## 8. Finans modülleri

### 8.1 Bütçeler

- [x] Aylık kategori bütçesi oluşturma/düzenleme/silme.
- [x] Harcama ilerleme hesabı.
- [x] %80 uyarı ve %100 aşım durumları.
- [x] Önceki ay bütçelerini kopyalama.

**İlerleme notu (Faz 8.1):** Dilim 1A–4J tamamlandı. Budget domain modelleri, validation kuralları, use-case'ler, Room DAO/V2 outbox/ACK, Staging V2 SQL migration (`20260829000100_sync_write_v2_budgets.sql`), 18 senaryolu SQL sözleşme testi ve Compose UI/MVI presentation akışı (`BudgetsScreen`, `BudgetProgressCard`, `BudgetFormScreen`, `BudgetDeleteDialog`, `BudgetCopyDialog`, `BudgetViewModel`) birim/host testleriyle (27 sharedUI, 71 androidApp) doğrulandı. Android emülatör manuel smoke testi kullanıcı tarafından gerçekleştirilerek onaylandı ve Faz 8.1 başarıyla tamamlandı.

**İlerleme notu (2026-09-15 — Bütçe Onaylanmış Tasarım Panelleri 01–03 & B04–B12 Uçtan Uca Uygulama):** Onaylanan 12 panel tasarımına (01, 02, 03, B04, B05, B06, B07, B08, B09, B10, B11, B12) birebir uygun olarak; 3 sütunlu özet kartı, aşım uyarısı, kategori bütçe kartları, B07 arama/kategori modalı, B08 dönem/para birimi modalı, B09 silme modalı, B10 boş dönem, B02 oluşturma, B04 canlı dinamik önizlemeli düzenleme ekranı, B03 & B06 normal ve aşım durumlu bütçe detay ekranı, son 5 harcama dökümü, B05 kopyalama ekranı, `BudgetDetailRoute` navigasyonu ve ViewModel bağlandı. `:sharedLogic:testAndroidHostTest`, `:sharedUI:testAndroidHostTest`, `:androidApp:testDebugUnitTest` ve `:androidApp:assembleDebug` testleri ve APK derlemesi %100 başarıyla doğrulandı.

### 8.2 Tekrarlayan işlemler ve abonelikler

- [x] Tekrar kurallarını modelle ve doğrula.
- [x] WorkManager ile vadesi gelen işlemleri idempotent üret.
- [x] Abonelik yenileme, duraklatma ve yaklaşan ödeme bildirimi.
- [x] Abonelik yaşam döngüsü (ACTIVE, PAUSED, CANCELLED, TRIAL, EXPIRED) ve fail-closed doğrulama.
- [x] Fiyat değişim geçmişi ve basis-point fiyat artış oranı hesabı.
- [x] Gerçekleşen ödeme olayları (atomik subscription payments ve renewal ilerletme).
- [x] Çoklu para birimi izolasyonlu saf analitik hesaplayıcı (tahmini maliyet vs. gerçekleşen harcama/trend).
- [x] Abonelik bazlı bildirim tercihi ve deneme süresi uyarısı (`TRIAL_ENDING_SOON`).
- [x] Sıcak-lüks Abonelikler ekranı yeniden tasarımı (özet kartı, gerçekleşen dönem, trend, yaklaşan/gecikmiş, 7 filtre çipi, semantik kategori vektör ikonları, içgörü kartları, yeni ekleme kartı).
- [ ] Abonelik ekranı ve yaşam döngüsü Android emülatör manuel kabulü.

**İlerleme notu (Faz 8.2 — Abonelik Altyapısı ve Ekran Yeniden Tasarımı — 2026-09-13):** Abonelik yaşam döngüsü (`ACTIVE`, `PAUSED`, `CANCELLED`, `TRIAL`, `EXPIRED`), atomik fiyat geçmişi kaydı ve artış rozetleri (`hasPriceIncrease`, basis-point artış), atomik gerçekleşen ödeme olayları ve takvim dönemi harcama/trend hesabı, saf Kotlin `SubscriptionAnalyticsCalculator` (minor-unit Long, basis-point, para birimi izolasyonu, tahmini maliyet vs. gerçekleşen harcama ayrımı), abonelik bazlı hatırlatıcı tercihi ve deneme süresi uyarısı (`TRIAL_ENDING_SOON`); Room v15→v16 ileri migration'ı (`16.json`, `subscription_price_histories`, `subscription_payments` ve subscriptions yaşam döngüsü sütunları); forward-only Supabase migration'ı (`20260913000100_subscription_lifecycle_and_history.sql`); sıcak-lüks Compose ekranı (Serif display başlık, özet/hero kartı, gerçekleşen dönem harcaması ve trend kartı, yaklaşan/gecikmiş bölümleri, 7 durum filtre çipi, semantik kategori vektör ikonlu abonelik kartları, içgörü kartları, yeni abonelik ekleme kartı) tamamlandı. Hedefli birim/host testleri (RoomDaoTest v15->v16, OfflineFirstSubscriptionRepositoryTest, PlanSubscriptionPaymentRemindersUseCaseTest, SubscriptionAnalyticsCalculatorTest, SubscriptionsViewModelTest, SubscriptionPaymentReminderWorkerTest), iOS simülatör derlemesi ve Android debug APK derlemesi başarıyla geçti; manuel emülatör kabulü bekleniyor.

**İlerleme notu (2026-09-15 — Abonelikler Onaylı Tasarım Panelleri 01–15 Birebir UI Yenilemesi):** Kullanıcının ilettiği 3 onaylı tasarım görseline (Görsel 1: Liste ve Detay; Görsel 2: 04, 05 Oluşturma ve 06 Düzenleme Formu; Görsel 3: 07 Fatura Dönemi sayaçlı modalı, 08 Kategori/Tarih modalı, 09 Para Birimi ve Şablonlar modalı, 10 Hatırlatma İzni, 11 Ödeme Onayı, 12 Durum Yönetimi, 13 Silme Onayı, 14 Boş ve Doğrulama Hataları, 15 Yükleme ve Sonuç Durumları) sadık kalınarak UI yenilemesi tamamlandı. Sıcak kırık beyaz zemin (`#F7F5F0`), beyaz kartlar, grafit özet kartları ve Feniqo yeşili (`#2D5A43`) korundu. Detay ekranı ve devamı tek doğal dikey kaydırılabilir akış (`verticalScroll`) ile birleştirildi. Fatura dönemi modalında pozitif tam sayı sayaç `[-] 1 [+]` ve 4 sıklık çipi uygulandı. Doğrulama hataları alan altında kırmızı uyarı ve ünlemle gösterildi. `:sharedLogic:testAndroidHostTest`, `:sharedUI:testAndroidHostTest`, `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` %100 başarıyla doğrulandı.

**İlerleme notu (2026-09-15 — Tekrarlayan İşlemler Onaylı Tasarım Panelleri 01–12 Birebir UI Yenilemesi):** Kullanıcının ilettiği onaylı tasarım panellerine (01 Liste, 02 Yeni Kural Oluşturma, 03 Mevcut Kuralı Düzenleme, 04 Tekrar Düzeni modalı, 05 Takvim/Tarih modalı, 06 Kategori Seçim modalı, 07 Ödeme Yöntemi modalı, 08 Düzenleme ekranının Duraklatılmış kural durumu, 09 Silme Onay Diyaloğu, 10 Takvim illüstrasyonlu Boş Durum, 11 Form ekranı, 12 İskelet Yükleme/Hata durumları ve Başarı Bildirim Çubuğu) tam sadık kalınarak UI yenilemesi tamamlandı. Sıcak kırık beyaz zemin (`#F7F5F0`), koyu grafit özet kartı ("Düzenini bir kez kur."), beyaz ayar kartları ve Feniqo yeşili (`#2D5A43`) korundu. Gider tutarları kırmızı (`−₺499,00`), gelir tutarları yeşil (`+₺45.000,00`) ve duraklatılan gider tutarı kırmızı renkte tabular stilde gösterildi. Aktif/Duraklatıldı grup ayrımı, 500 karakter sayaçlı açıklama alanı, dinamik sıklık/bilgi banner'ı, durum rozetleri ve tam uyumlu modal sheet'ler uygulandı. `:sharedLogic:testAndroidHostTest`, `:sharedUI:testAndroidHostTest`, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` testleri %100 başarıyla doğrulandı.

**İlerleme notu (Faz 8.2):** Tekrarlayan işlemler (domain/validation, Room occurrence idempotency, V2 outbox/ACK/pull/conflict, Supabase migration/RLS/RPC, liste-form UI, düzenleme/duraklatma/silme, Worker ve scheduler) ve abonelikler (CRUD, yenileme/duraklatma, V2 sync, liste-form UI, ödeme hatırlatıcı planlayıcı, Room receipt claim, Android notification Worker, scheduler ve Android 13+ izin CTA'sı) kod, hedefli birim/host testleri, platform derlemeleri ve Staging SQL kabulüyle tamamlandı. Android emülatör manuel smoke kabulü kullanıcı tarafından başarıyla gerçekleştirildi.

### 8.3 Hedefler ve borçlar

- [x] Birikim hedefleri ve hedefe para ekleme.
- [x] Borç/alacak ekleme, vade ve ödendi işareti.
- [x] Borç ödeme planı / snowball raporu.

**İlerleme notu (Faz 8.3 — Borç / Alacak Ekranı Yeniden Tasarımı — 2026-09-13):** "Borç / Alacak" ekranı, referans tasarımla uyumlu "warm-luxury" görsel hiyerarşisiyle yeniden geliştirildi. Ekran başlığı ("Borçlar ve Alacaklar"), marka rozeti ("Feniqo" serif), 3'lü finansal özet kartları ("Toplam borcum", "Toplam alacağım", "Net durum" / minor-unit Long ve aktif çalışma alanı para birimi güvenliği), yaklaşan vadeler ("Yaklaşan Vadeler" / 7 gün içinde vadesi gelen veya geciken kayıtlar), ayrı "Borçlarım" ve "Alacaklarım" bölümleri, kapanan kayıtlar akordiyonu, açıklanabilir Feniqo içgörü kartı (`DebtInsightBuilder`), baş harf tabanlı avatar (`deriveAvatarInitial`), Canvas tabanlı köşegen durum okları (↗, ↘), 48dp erişilebilirlik ve TalkBack semantik açıklamaları tamamlandı. `DebtsViewModel` fail-closed mapping koruması ve `CurrentDateProvider` ile güçlendirildi. Hedefli birim/host testleri (`DebtDisplayModelMapperTest`, `DebtsViewModelTest`), Android debug APK derlemesi ve iOS Simulator derlemesi başarıyla doğrulandı.

**İlerleme notu (Faz 8.3):** Dilim 1A–2H tamamlandı. Goals (birikim/hedef CRUD, hedefe katkı ekleme/çıkarma, ilerleme/tahmini süre hesabı), Debts & Receivables (borç/alacak CRUD, ödeme/tahsilat geçmişi, reaktif kalan bakiye gösterimi, fail-closed `DebtBalanceCalculator`), borç snowball planlayıcısı (`DebtSnowballPlanner`, deterministik simülasyon, bütçe tahsisi ve kapanış sırası) ve MVI Compose ekranları (`GoalsScreen`, `GoalFormScreen`, `GoalContributionFormDialog`, `DebtsScreen`, `DebtFormScreen`, `DebtPaymentFormDialog`, `DebtSnowballPlanScreen`) tamamlandı. Room v9/v10/v11 tabloları (`goals`, `goal_contributions`, `debts`, `debt_payments`), V2 outbox/ACK/pull/conflict senkronizasyonu, Staging 15/15 migration (`20260901000100_sync_write_v2_goals_and_debts.sql`, `20260901000200_reconcile_goals_debts_sync_contract.sql`), SQL sözleşme testi (koşulsuz ROLLBACK ile 0 kalıntı: goals, contributions, debts, payments, sync receipts = 0) ve Android emülatör manuel smoke kabulü kullanıcı tarafından başarıyla gerçekleştirildi. Production'a dokunulmadı.

### 8.4 Ortak çalışma alanları

- [x] Çalışma alanı oluşturma, katılma, ayrılma ve aktif alan seçimi.
- [x] Üye listesi ve rol tabanlı UI.
- [ ] Ortak işlem ve bütçe görünürlüğü.
- [x] Kimin ne kadar ödediği ve borç dağılımı hesaplaması.

**İlerleme notu (Faz 8.4, Ortak Alanlar UI Yenilemesi — 2026-09-15):** Onaylı tasarım görsellerine (Görsel 01, 02 ve 03) sadık kalınarak `WorkspacePickerScreen`, `WorkspaceCreateScreen`, `WorkspaceJoinScreen`, `WorkspaceDetailsScreen` ve `WorkspaceSettlementScreen` Jetpack Compose arayüzleri baştan sona modernize edildi. Kişisel mod ayrımı, radyo göstergeleri, adaçayı aktif rozet, koyu grafit `#1E2822` hero kartı, taçlı rol rozetleri (Sahip, Düzenleyici, İzleyici), gerçek davet kodu paylaşımı, rol değiştirme, üye çıkarma, sahiplik devri ve alandan ayrılma diyalogları, net bakiye renklendirmeleri (`+`/`-`), dinamik yön şemalı transfer önerileri kartları (`[Can ₺400] → [Ayşe]`), dengeli hesap ve gider dışlama uyarıları Room SSOT ve Clean Architecture mimarisi korunarak tamamlandı. Birim testleri, host testleri, Android debug APK (`assembleDebug`) ve iOS simulator derlemesi başarıyla doğrulandı.

**İlerleme notu (Faz 8.4, E14-C & E14-D):** Ortak gider split yönetimi ve ödeşme ekranı uçtan uca tamamlandı. E14-C ile işlem ekleme/düzenleme formunda aktif shared workspace ve EXPENSE için dinamik "Kim Ödedi?" (tekil seçim) ve "Kimler Katılıyor?" (çoklu seçim) alanları eklendi. E14-D ile `ObserveWorkspaceSettlementUseCase`, `WorkspaceSettlementViewModel` ve `WorkspaceSettlementScreen` MVI ekranı geliştirildi: aktif shared workspace'deki EXPENSE işlemleri ve üyeler Room SSOT Flow'ları üzerinden dinlenerek her üyenin net bakiyesini ("Alacaklı", "Borçlu", "Dengede") ve deterministik transfer önerilerini ("A, B kişisine ₺X ödesin") gösterir. Workspace silinmişse veya mevcut kullanıcı aktif üye değilse fail-closed koruma uygulanır; geçersiz/üye dışı split harcamaları hesaplamadan dışlanır ve kullanıcıya bilgi başlığı sunulur. Workspace detay ekranına "Ödeşme ve Transferler" butonu ve type-safe route entegrasyonu sağlandı. Kapsamlı birim, viewmodel ve UI testleri başarıyla doğrulandı.

### 8.5 Varlıklar, net değer ve raporlar

- [x] Varlık CRUD: nakit, metal, kripto, hisse, gayrimenkul vb.
- [x] Net değer hesabı.
- [ ] Piyasa fiyat servisinin güvenli backend sözleşmesi.
- [ ] Harcama analizi, trend, ısı haritası ve tahmin raporları.

#### E15-A — Asset offline-first altyapısı

- [x] Asset domain command, repository/use case sözleşmeleri ve doğrulama kurallarını oluştur.
- [x] Room `assets` tablosu, DAO, mapper, v12→v13 migration ve şema testlerini tamamla.
- [x] Asset DTO/remote mapper ve cursor tabanlı remote query sözleşmesini ekle.
- [x] Asset CREATE/UPDATE/DELETE için atomik Room V2 outbox, coalesce ve ACK/rebase davranışını tamamla.
- [x] `OfflineFirstAssetRepository` kişisel owner izolasyonu, doğrulama ve cancellation davranışını tamamla.
- [x] Initial/incremental Asset pull, cursor ve pending yerel değişikliği ezmeyen conflict davranışını tamamla.
- [x] Asset repository ve sync bağımlılıklarını Android Hilt DI'a bağla.
- [x] Asset odaklı repository, pull/conflict ve V2 executor/ACK testlerini çalıştır; Android debug derlemesini doğrula.
- [x] Asset liste/form ekranları için type-safe rota ve fail-closed kimlik ayrıştırma sözleşmesini oluştur.
- [x] Asset form girdileri için `Double` kullanmayan para/miktar normalizasyonu ve alan bazlı doğrulamayı tamamla.
- [x] Asset liste display model/UI state, deterministik mapper ve retry destekli Room Flow ViewModel'ini tamamla.
- [x] Stateless Asset liste ekranını, Hilt route adaptörünü ve `Daha Fazla → Varlıklar` navigasyonunu etkinleştir.
- [x] Asset form ViewModel create/update/delete, edit-load, double-submit ve fail-closed route state sözleşmesini tamamla.
- [x] Asset Compose formu, onaylı silme diyaloğu ve liste↔form navigation akışını tamamla.
- [x] Forward-only Asset Supabase migration ve rollback'li SQL sözleşme testini izole yerel PostgreSQL/Supabase ortamında çalıştır.
- [x] Asset CRUD kullanıcı arayüzü ve Android manuel smoke kabulünü tamamla.

**İlerleme notu (Faz 8.5, E15-A — 2026-09-09):** Kişisel kapsamlı Asset domain/validation, Room v13, DTO/mapper, atomik V2 outbox ve ACK/rebase, offline-first repository, initial/incremental pull ile pending-conflict koruması, Android DI ve Asset CRUD kullanıcı arayüzü tamamlandı. Asset odaklı Android host testleri ile `:androidApp:assembleDebug` doğrulandı; kullanıcı manuel Android smoke kabulünü de tamamladı. İleri yönlü `20260908000400_sync_write_v2_assets.sql` migration'ı tüm önceki migration'ların ardından izole yerel PostgreSQL veritabanına uygulandı; bağımsız rollback'li Asset SQL sözleşmesinde CREATE/UPDATE/DELETE, stale-version conflict, doğrulama retleri ve owner mutation/RLS izolasyonu geçti. Birleşik legacy contract, Asset senaryosuna ulaşmadan eski Subscription RPC'nin `billing_cycle` ile mevcut `frequency` şeması arasındaki uyumsuzlukta kalmaktadır. Staging/production'a dokunulmadı.

#### E15-B — Offline-first net değer özeti

- [x] Room SSOT Asset akışından para birimi bazlı net değer hesaplayan saf domain use case'i ekle.
- [x] Farklı para birimlerini kur dönüşümü olmadan ayrı tut ve toplam taşmasında fail-closed davran.
- [x] Asset liste ViewModel ve ekranına reaktif net değer kartını bağla.
- [x] Net değer hesaplayıcı, display mapper ve ViewModel için hedefli testleri; Android debug derlemesini doğrula.
- [x] Net değer kartının Android manuel smoke kabulünü tamamla.

**İlerleme notu (Faz 8.5, E15-B — 2026-09-09):** Net değer özeti aktif kişisel Asset kayıtlarının `currentValue` alanlarını Room Flow üzerinden reaktif olarak para birimi bazında toplar. Güvenilir kur servisi bulunmadığından farklı para birimleri sessizce çevrilmez veya tek toplamda birleştirilmez; kartta ayrı gösterilir. Güvenli `Money` sınırını aşan toplam fail-closed hata durumuna dönüşür. Asset/NetWorth odaklı sharedLogic, sharedUI ve androidApp testleri ile `:androidApp:assembleDebug` geçti; kullanıcı Android manuel smoke kabulünü de başarıyla tamamladı.

#### E15-C — Güvenli piyasa fiyat servisi

- [x] Sağlayıcıdan bağımsız ölçekli fiyat, istek, kullanılabilirlik ve hata domain sözleşmelerini oluştur.
- [x] Sembol allowlist normalizasyonunu ve `Double` kullanmayan miktar × fiyat HALF_UP/overflow hesaplamasını tamamla.
- [x] Forward-only `market_prices` Supabase tablosu, salt-okunur RLS ve rollback'li SQL sözleşmesini oluştur.
- [x] JWT doğrulamalı, secret tabanlı, sabit sağlayıcı endpoint'li Edge Function çekirdeğini ve izole sözleşme testlerini tamamla.
- [x] Kullanıcı başına atomik piyasa fiyatı istek kotası ve doğrudan tablo erişimi kapalı SQL sözleşmesini tamamla.
- [ ] Edge Function'ı gerçek Deno/Supabase runtime'ında test secret'ı ile başlatıp sağlayıcı smoke kabulünü tamamla.
- [x] Sağlayıcıdan bağımsız Room market-price cache, DAO, v13→v14 migration ve şema doğrulamasını tamamla.
- [x] Market-price repository yenileme/Flow zincirini ve mobil Edge Function istemcisini tamamla.
- [x] Fresh/stale/unavailable durumlarını ve manuel değer fallback'ini Asset/net değer UI'ına bağla.
- [x] Edge Function erişilemezken hata gösterimi, manuel değer/net değer koruması ve yeniden deneme Android manuel kabulünü tamamla.
- [ ] E15-C hedefli testleri, Android debug derlemesini ve manuel smoke kabulünü tamamla.

**İlerleme notu (Faz 8.5, E15-C — 2026-09-09):** Güvenli domain, uzak read-model ve Edge Function çekirdek dilimleri tamamlandı. `CRYPTO`, `STOCKS` ve `PRECIOUS_METALS` istekleri sağlayıcıdan bağımsız modellenir; semboller allowlist ile normalize edilir ve ölçekli hesap taşmada fail-closed davranır. `market_prices` cache'i authenticated için salt-okunur, anon için kapalı, service-role için kontrollü yazılabilirdir. Function gateway JWT'ye ek olarak Auth `/user` doğrulaması, 20 sembollük batch sınırı, 5 saniye timeout, sabit `https://api.twelvedata.com/quote` endpoint'i, fresh cache ve stale fallback uygular. `TWELVE_DATA_API_KEY` yalnız Edge secret'tır. Kullanıcı başına dakikada 10 istek atomik `claim_market_price_request` RPC'siyle sınırlandırılır; kota tablosuna istemci erişemez. Sekiz izole Function testi ile her iki rollback'li SQL contract geçti. Mobilde sembol+tür+kotasyon para birimi bileşik anahtarlı, stale fallback satırlarını koruyan sağlayıcıdan bağımsız Room cache'i eklendi; v13→v14 migration ve 14.json doğrulandı. Supabase Functions istemcisi, istek/yanıt DTO'ları ve offline-first repository zinciri eklendi; geçersiz batch remote'a çıkmaz, yalnız fiyatlı sonuçlar cache'e yazılır, unavailable/ağ hatası eski cache'i silmez ve cancellation yutulmaz. Fresh ve unavailable Function JSON yanıtlarının mobil DTO'ya kayıpsız dönüşümü ayrıca sözleşme testleriyle kapatıldı; toplam 16 MarketPrice testi geçti. Asset ekranında kullanıcı kontrollü fiyat yenileme, fresh/stale/manüel kaynak etiketi ve miktar × fiyat ile net değer güncellemesi eklendi; fiyat ya da güvenli hesap yoksa manuel değer korunur. Asset ViewModel hedefli testleri ile Android debug derlemesi geçti. Edge Function erişilemezken hata gösterimi ve manuel değer/net değer koruması Android'de kullanıcı tarafından doğrulandı. Bu makinede Deno/Supabase CLI bulunmadığı için gerçek fresh/stale Edge runtime/provider smoke kabulü açık bırakıldı; hiçbir uzak ortama deploy yapılmadı.

### 8.6 Merchant/marka tanıma ve kategori ikonları

- [x] Platformdan bağımsız merchant, alias, negatif alias, işlem sınıfı ve eşleşme kaynağı sözleşmelerini oluştur.
- [x] Türkçe uyumlu deterministik açıklama normalleştiricisini ve 0–100 güven puanlı eşleştirme motorunu oluştur.
- [x] Temsilî başlangıç kataloğu ile özel/genel alias önceliğini ve kişisel/sistem hareketi dışlamasını test et.
- [x] Alias türü/kapsamı, kişisel→workspace→banka doğrulama sırası, onaylı güven matrisi ve 10 puanlık çakışma eşiğini uygula.
- [x] 18 gider kategorisinin semantik anahtar, Türkçe/İngilizce ad, ikon anlamı, renk ve kapsam sözlüğünü tanımla.
- [x] 9 gelir kategorisinin semantik anahtar, Türkçe/İngilizce ad, ikon anlamı, renk ve kapsam sözlüğünü tanımla.
- [x] 7 sistem hareketinin semantik anahtar, Türkçe/İngilizce ad, ikon anlamı ve amaç sözlüğünü tanımla; merchant eşleştirmesinden çıkar.
- [x] Eski 5 gelir/12 gider Room seed'ini referansları koruyarak kanonik 9 gelir/18 gider sözlüğüne taşı.
- [x] Semantik kategori anahtarlarını kategori listesi ve işlem formunda gerçek Compose vektör ikonlarına ve %12 tonal renk kaplarına bağla.
- [ ] Kullanıcı doğrulamalarını ve isteğe bağlı transaction merchant bağlantısını Room SSOT'a ekle.
- [ ] UI logo fallback zincirini ve opsiyonel, gizlilik korumalı logo adaptörünü uygula.

**İlerleme notu (Faz 8.6, ilk domain dilimi — 2026-09-11):** `sharedLogic/commonMain`
içinde kategori modelinden ayrı merchant sözleşmeleri, ham açıklamayı koruyan Türkçe uyumlu
normalleştirici, özel alias önceliği, negatif alias, işlem sınıfı filtresi ve 0–100 güven puanlı
deterministik motor tamamlandı. Starbucks, Migros, Shell, Spotify, Netflix, Trendyol/Yemek,
Getir/GetirYemek, Amazon/Prime, Uber, THY, Turkcell ve Apple Services başlangıç kataloğuna
eklendi. 75 altındaki eşleşmeler logo için uygun değildir; kullanıcı doğrulaması tahminden önce
gelir. Room, Supabase, DTO, outbox, sync, UI ve logo sağlayıcısı bu dilimin dışındadır.

**İlerleme notu (Faz 8.6, güven sözleşmesi devamı — 2026-09-11):** Alias türleri ve
kişisel/çalışma alanı/genel kapsamları modellendi. Kişisel doğrulama 100, çalışma alanı doğrulaması
100, banka kimliği 98, tam alias 95, yasal ad 92, marka+hizmet 90, şube/POS 86, güçlü marka 80
ve kısa alias 65 puan sözleşmesine bağlandı. Farklı merchant adayları arasındaki puan farkı
10'dan azsa otomatik merchant ataması yapılmaması hedefli testlerle doğrulandı.

**İlerleme notu (Faz 8.6, Room + görsel dilim — 2026-09-11):** Mevcut `categories`
şeması semantik `icon_key` ve `color_hex` alanlarını zaten taşıdığı için gereksiz bir şema sürümü
artışı yapılmadı. `DefaultCategorySeeder` 9 gelir ve 18 gider kaydını sabit UUID'lerle idempotent
uzlaştıracak şekilde genişletildi; eşdeğer eski kategoriler kimlik ve sync metadata'sını korur.
Anlamı belirsiz `Tasarruf & Yatırım` ile `Kredi Ödemeleri` başka kategoriye çevrilmeden tarihsel
referansları korunarak aktif seçimden gizlenir. Tüm 27 semantik anahtar kategori listesi ve işlem
formunda gerçek Compose vektörü, kategori rengi ve %12 tonal dairesel zeminle gösterilir.

---

## 9. Cihaz özellikleri, güvenlik ve veri taşınabilirliği

### 9.1 Biyometrik uygulama kilidi

- [x] Kilit tercihini ve otomatik kilit süresini oluştur.
- [x] `BiometricPrompt` ile uygulama açılışında doğrulama uygula.
- [x] Biyometri kullanılamadığında güvenli cihaz kimlik doğrulama geri dönüşünü tasarla.
- [x] Biyometrik kilit, süre seçenekleri ve cihaz kimlik bilgisi geri dönüşü için Android manuel smoke kabulünü tamamla.

**İlerleme notu (Faz 9.1 — 2026-09-09):** Biyometrik kilit tercihi ile anında, 30 saniye, 1 dakika ve 5 dakika otomatik kilit seçenekleri ortak domain sözleşmesine taşındı. Saat geri alınırsa kilit gerektiren fail-closed `AppLockPolicy` eklendi. Android tercihleri uygulamaya özel Preferences DataStore'da saklanır ve Hilt üzerinden `SecurityRepository` olarak sunulur. `MainActivity` kökündeki lifecycle kapısı doğrulama gerekirken finans navigation ağacını composition dışına çıkarır; kilidi etkinleştirme de tercih yazılmadan önce başarılı sistem doğrulaması ister. AndroidX `BiometricPrompt`, biyometriyle birlikte cihaz PIN/desen/parolasını güvenli geri dönüş olarak kabul eder ve sistem doğrulaması yoksa kilit etkinleştirilemez. Bu uygulama kilidi, arka plan senkronizasyonunun çalışabilmesi için kullanıcı doğrulamasına bağlanmayan mevcut SQLCipher/Keystore anahtarından bilinçli olarak ayrıdır. 5 politika, 2 repository ve 4 lifecycle/controller testi ile Android debug derlemesi geçti; biyometrik kilit, süre ve cihaz kimlik bilgisi geri dönüşü kullanıcı tarafından Android'de başarıyla doğrulandı. Faz 9.1 tamamlandı.

### 9.2 Makbuz OCR

- [x] CameraX kamera akışını kur.
- [x] ML Kit Text Recognition ile metni al.
- [x] Toplam tutar, tarih ve işyeri adı için güvenilir ayrıştırma kuralları oluştur.
- [x] OCR sonucunu doğrudan kaydetme; kullanıcı onaylı işlem formuna aktar.
- [x] Kamera ve görsel izin reddi senaryolarını ele al.
- [ ] Kamera, galeri, izin reddi ve kullanıcı onaylı OCR form aktarımı için Android manuel smoke kabulünü tamamla.
- [ ] Gerçek makbuz örnekleriyle toplam/fiyat satırı algılama doğruluğunu iyileştir ve regresyon fixture'ları ekle.

**İlerleme notu (Faz 9.2, ilk dilim — 2026-09-09):** OCR çıktısını kalıcı finans
kaydından ayıran `ReceiptOcrResult`/`ReceiptOcrDraft` aday sözleşmesi ve deterministik
`ReceiptOcrParser` tamamlandı. Parser etiketli genel toplam/ödenecek/toplam satırlarını güven
düzeyiyle değerlendirir, tutarı `Double` kullanmadan küçük para birimine çevirir, geçersiz veya
taşan tutarı ve takvim dışı tarihi reddeder; para birimini makbuzdan tahmin etmek yerine aktif form
bağlamından alır. İşyeri adını düşük güvenli aday olarak sunar. 50.000 karakter üzerindeki girdi
fail-closed reddedilir; ham OCR metni modelde, Room'da veya outbox'ta tutulmaz. Altı hedefli parser
testi geçti. CameraX, ML Kit ve kullanıcı onaylı form aktarımı sonraki dilimlerdir.

**İlerleme notu (Faz 9.2, Android temel — 2026-09-09):** Resmî sürümlerle CameraX 1.6.1
ve ağdan model indirmeyen bundled Latin ML Kit Text Recognition 16.0.1 yalnız Android modülüne
eklendi. `MlKitReceiptOcrService` bir `content://`/dosya URI'sini cihaz içinde işler, ham metni
yalnız geçici olarak parser'a verir ve yalnız `ReceiptOcrResult` döndürür. Hilt bağı kuruldu.
Kamera donanımı opsiyonel ilan edildi; ilk istek, gerekçe, kalıcı ret/ayarlar ve izin verilmiş
durumlarını fail-closed ayıran `CameraPermissionPolicy` dört testle doğrulandı. Parser testleri ve
Android debug derlemesi geçti. Kamera önizleme/yakalama ekranı ile işlem formu entegrasyonu henüz
açık olduğu için CameraX ve ML Kit üst seviye checkbox'ları işaretlenmedi.

**İlerleme notu (Faz 9.2, kullanıcı akışı — 2026-09-09):** Yeni işlem formundaki
“Makbuz Tara” akışı CameraX arka kamera önizleme/yakalama ve Android sistem galeri seçicisini
sunar. Kamera çıktısı yalnız uygulama cache'inde geçici dosyadır ve OCR tamamlanınca silinir;
galeri için geniş depolama izni istenmez. Kamera izninde ilk istek, gerekçe, tekrar isteme,
kalıcı ret sonrası uygulama ayarları ve izin vermeden galeriye devam yolları bağlandı. ML Kit
sonucu ayrı kontrol diyaloğunda gösterilir; “Kullanma” hiçbir form alanını değiştirmez, yalnız
“Forma Aktar” tutar/tarih/işyeri adaylarını mevcut form state'ine uygular ve yine otomatik kayıt
oluşturmaz. İki OCR ViewModel, dört izin ve bir açık form aktarımı testi ile Android debug APK
derlemesi geçti. Android cihaz manuel smoke kabulü açık bırakıldı.

**Manuel değerlendirme (2026-09-09):** Kamera/galeri ve güvenli kullanıcı onayı akışı çalışıyor;
ancak gerçek makbuzlarda fiyat/toplam adayı her zaman okunamadığı için ürün kabulü tamamlanmadı.
OCR doğruluk iyileştirmesi, anonimleştirilmiş farklı makbuz fixture'larıyla daha sonraki ayrı bir
dilime ertelendi. Mevcut sonuçlar kullanıcı onayı olmadan kaydedilmediği için güvenli fail-closed
davranış korunuyor.

### 9.3 İçe/dışa aktarma ve gizlilik

- [x] İşlemleri CSV olarak dışa aktar.
- [x] Yedek formatı ve sürümünü tanımla.
- [x] JSON yedek içe aktarmada doğrulama ve geri alınabilirlik uygula.
- [x] Hassas verilerin loglara yazılmadığını doğrula.

**İlerleme notu (Faz 9.3, CSV dışa aktarma — 2026-09-10):** Aktif çalışma alanının Room
SSOT üzerinden görünen işlemleri Android sistem dosya seçicisiyle UTF-8 CSV olarak dışa aktarılır.
Şema yerelden bağımsız ve deterministiktir; para `amount_minor` olarak kalır. Owner kimliği ve
private makbuz yolu dışarı verilmez, metin alanlarında CSV kaçışı ve elektronik tablo formül
enjeksiyonu koruması uygulanır. Üç hedefli sözleşme testi ve Android debug APK derlemesi geçti;
cihazda dosya seçme/açma manuel kabulü açıktır.

**İlerleme notu (Faz 9.3, JSON yedek sözleşmesi — 2026-09-10):** Kişisel kategori ve
işlemler için `format_version = 1` sürümlü, 10 MiB giriş sınırına sahip JSON sözleşmesi tanımlandı.
Owner/oturum bilgisi, token, private makbuz yolu, OCR içeriği, Room sync metadata'sı,
outbox ve conflict kayıtları format dışında tutulur. Bilinmeyen alan/sürüm, desteklenmeyen scope,
bozuk tarih/tutar, tekrar kimlik, eksik kategori referansı ve kayıt sınırı Room'a yazmadan önce
fail-closed reddedilir. Üç hedefli sözleşme testi geçti.

**İlerleme notu (Faz 9.3, JSON yedek içe aktarma — 2026-09-10):** Doğrulanan kişisel
kategori ve işlemler yeni yerel kimliklerle, kategori/taksit referansları korunarak tek Room
transaction'ında entity + V2 outbox olarak içe aktarılır; herhangi bir satır veya outbox yazımı
başarısız olursa tüm işlem geri alınır. Android sistem dosya seçicisi girdiyi 10 MiB ile sınırlar,
kayıt sayılarını yazmadan önce gösterir ve açık kullanıcı onayı ister. Altı hedefli format,
planlayıcı ve Room atomiklik testi ile Android debug APK derlemesi başarıyla tamamlandı.

**İlerleme notu (Faz 9.3, gizlilik denetimi ve tamamlanma — 2026-09-10):** Android/KMP
üretim kaynakları ve Edge Function kodu token, parola, davet kodu, OCR metni, finansal payload,
doğrudan log çağrısı ve HTTP body logger açısından tarandı. Doğrudan uygulama loglaması bulunmadı.
Ham exception mesajlarının repository `AppError` ve Room outbox `last_error` alanlarına taşındığı
noktalar kararlı güvenli kodlarla değiştirildi. Hassas bearer/payload içeren hata fixture'ı dâhil
20 hedefli test ve Android debug APK derlemesi geçti. Faz 9.3 tamamlandı.

---

## 10. Test, kalite, yayın hazırlığı

### 10.1 Otomatik testler

- [ ] Domain/use case birim testleri.
- [ ] DAO ve migration testleri.
- [ ] Repository + fake remote senkronizasyon testleri.
- [ ] ViewModel state testleri.
- [ ] Kritik Compose ekranları için UI testleri.
- [ ] Offline, tekrar deneme, çakışma ve soft-delete uçtan uca senaryoları.

### 10.2 Kod kalitesi ve performans

- [ ] Kotlin biçimlendirme ve statik analiz aracı seçimi.
- [ ] Büyük listelerde sayfalama ve LazyColumn performansı.
- [ ] StrictMode / sızıntı / ana iş parçacığı kontrolleri.
- [ ] Crash raporlama ve gizlilik politikası kararı.
- [ ] Erişilebilirlik: content description, ekran okuyucu ve kontrast kontrolü.

### 10.3 Android yayın paketi

- [ ] Uygulama simgesi, splash, paket adı ve sürümleme.
- [ ] Release signing ve güvenli anahtar saklama.
- [ ] R8/ProGuard ve release testleri.
- [ ] Play Store gizlilik beyanı ve ekran görüntüleri.
- [ ] Internal testing → closed testing → production yayın kontrol listesi.

### 10.4 iOS'a geçiş hazırlığı

- [ ] `commonMain` derlemesini iOS hedefiyle doğrula.
- [ ] iOS güvenli depolama ve biyometri adaptörlerini uygula.
- [ ] iOS veritabanı/şifreleme stratejisini üretim öncesi doğrula.
- [ ] Xcode uygulama kabuğunu ekle.
- [ ] SwiftUI ekranlarını `sharedLogic` ortak iş mantığına bağla.

---

## Başlangıç sırası

Sonraki teknik adım: **9.2 — Makbuz OCR**.

Sıradaki dilim: CameraX kamera/galeri giriş sınırı, izin akışı ve ML Kit metin tanıma için
cihazda kalan, finans kayıtlarına otomatik yazmayan güvenli OCR sözleşmesi.

## İlerleme notları

| Tarih | Adım | Not |
|---|---|---|
| 2026-09-13 | Abonelikler Gerçek Altyapı, Form, Detay Ekranı ve Ödeme Kalıcılığı | Uygulandı, manuel kabul bekliyor: Yaşam döngüsü durumları, atomik fiyat geçmişi/ödeme kayıtları, saf Kotlin analitik motoru, Room v16/v17 (17.json) ve forward-only Supabase migration'ları. LocalMutationDao Room @Upsert gövdeli metot hatası düzeltildi (gerçekleşen ödemeler SQLite'a kalıcı yazılıyor ve Flow gözlemiyle grafiğe yansıyor). Ekran alt/üst insets ve taşmaları giderildi. Abonelik Detayları kartına semantik ikonlar (döngü, tarih, oto-yenileme, kategori, hatırlatıcı, workspace) eklendi; 4'lü aksiyon satırı detay kartının altına taşındı; ekran sıkılaştırılarak kaydırmasız tek ekranda görünürlük sağlandı. Görsel 1: Form, Görsel 2: Detay Ekranı. Hedefli testler, debug APK ve iOS simülatör derlemesi başarıyla geçti. |
| 2026-09-12 | Hızlı Ekle (+ / Ekle), İşlem Formu ve Başarı Ekranı Yeniden Tasarımı | Uygulandı, manuel kabul bekliyor: Alt navigasyon merkez "+" eyleminden modal sheet olarak açılan Hızlı Ekle ekranı (Gider, Gelir, Transfer [Yakında], Borç/Alacak, Tekrarlayan İşlem ve ipucu kartı); sıcak-lüks işlem formu (büyük tutar, zorunlu 100 kar. işlem adı, kategori, ödeme yöntemi, tarih ve isteğe bağlı not/taksit/makbuz/split akordiyonu); Room v14→v15 note kolonu ileri yönlü migration'ı (15.json ve veri koruma testi); Room SSOT'tan gözlemleyen başarı ekranı (özet kartı, yeni işlem ekle, işlemi görüntüle, geri dönüş back-stack); hedefli unit/host testleri ve debug APK derlemesi başarıyla doğrulandı; staging/production Supabase'e dokunulmadı. |
| 2026-09-11 | 8.6 (ilk domain dilimi) | Merchant/marka modeli, semantik kategori ikon bağlantısı, Türkçe normalleştirici, alias/negatif alias ve işlem sınıfı sözleşmeleri, sistem hareketi filtresi, 0–100 güven motoru ve temsilî katalog saf KMP olarak tamamlandı; hedefli testler geçti. Kalıcılık, sync, UI ve logo sağlayıcısı sonraki dilimdir. |
| 2026-08-03 | 0 / analiz | Web projesi incelendi; mobil mimari ve risk analizi hazırlandı. |
| 2026-08-03 | 2.1 | KMP proje omurgası oluşturuldu. Android/iOS kimliği `com.feniqo.mobile`; API 26; Android debug derlemesi ve ortak modül host testleri başarılı; Git deposu başlatıldı. |
| 2026-08-03 | 1.1 | Web ekranları, kullanıcı akışları, özellikler, V1 veri sözlüğü ve TypeScript/SQL riskleri `docs/WEB_REFERANS_ENVANTERI.md` içinde tamamlandı. |
| 2026-08-03 | 1.2 | V1 kapsamı, V2/V3 ayrımı ve yedi temel ürün/mimari kararı proje sahibi tarafından onaylandı. |
| 2026-08-03 | 1.3 | V1 RLS ve ilerideki workspace rol matrisleri, güvenli migration sırası, para dönüşümü, soft-delete/version sync ve test/onay kapısı `docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md` içinde tamamlandı; canlı veritabanına henüz SQL uygulanmadı. |
| 2026-08-03 | 2.2 | Compose/Material 3, Hilt/KSP, Navigation Compose, Lifecycle/ViewModel, Coroutines, Serialization, Ktor ve test bağımlılıkları Version Catalog ile kuruldu. Android debug derlemesi doğrulandı; ortak testler için KMP source set yerleşimi hazırlandı. |
| 2026-08-05 | 2.3 | Emerald Phoenix Material 3 teması, açık/koyu/sistem modu, Android DataStore ile kalıcı tema seçimi, ortak tasarım token'ları ve geri bildirim bileşenleri, bottom navigation kabuğu tamamlandı. Debug APK emülatörde doğrulandı; koyu tema uygulama yeniden açıldıktan sonra korundu. Temel kontrast oranları 6,45:1 ve üzeri ölçüldü. |
| 2026-08-05 | 3.1 | `sharedLogic/commonMain` içinde `Money` (Long küçük birim), `Currency`, `EntityId`, KMP uyumlu `LocalDate`, `SyncStatus`, `AppError` ve işlem tarihi politikası oluşturuldu. Para hesaplaması gösterimden ayrıldı; işlem tarihi yerel tarih ve sunucu zamanları UTC sözleşmesi belgelendi. Ortak modül testleri ve Android debug APK derlemesi doğrulandı. |
| 2026-08-05 | 3.2 | `UserProfile`, çalışma alanı/üyelik/rol, kategori, işlem, taksit, ödeme yöntemi, private makbuz yolu, etiket ilişkisi ve aylık bütçe modelleri `sharedLogic/commonMain` içinde oluşturuldu. Webdeki serbest metin ve `number` riskleri kararlı enum, normalize ilişki ve `Money` ile giderildi; ortak testler başarıyla geçti. |
| 2026-08-05 | 3.3 | Tekrarlayan işlem ve tekrar kuralı; hedef, borç/ödeme geçmişi, abonelik; ölçekli varlık miktarı, piyasa fiyatı; dashboard, MoneyScore ve dönem raporu modelleri ortak domain katmanında oluşturuldu. Negatif net sonuçlar `MoneyDelta`, oranlar baz puan ile modellendi. Android host ortak testleri temiz derlemeyle başarıyla geçti; platform importu bulunmadı. |
| 2026-08-05 | 3.4 | Auth, işlem, kategori, bütçe, çalışma alanı, senkronizasyon, tercih ve güvenlik repository sözleşmeleri ortak domain katmanında tanımlandı. Tüm okumalar `Flow`, tüm mutasyonlar `RepositoryResult` ve `AppError` kullanır. Coroutines public sözleşmede kullanıldığı için `api` olarak açıldı; fake repository sözleşme testleri ve ortak Android host testleri başarıyla geçti. |
| 2026-08-05 | 3.5 | İşlem ekleme, güncelleme, soft-delete, filtreleme, kategori ekleme ve dashboard gözlem use case'leri oluşturuldu. Sahiplik aktif oturumdan atanır; tutar, gelecek tarih, açıklama ve kategori türü doğrulamaları use case katmanındadır. Bütçe ilerlemesi ve 30/30/20/20 ağırlıklı MoneyScore saf KMP hesaplarıyla, para ve oranlarda `Double` kullanmadan geliştirildi. Dokuz yeni use case testi dâhil tüm ortak Android host testleri geçti. |
| 2026-08-05 | 4.1 | Room KMP 2.8.4 ve bundled SQLite 2.6.2 kuruldu. Profil, workspace/üyelik, kategori, işlem, bütçe, etiket ve transaction-tag için domain'den ayrı 8 entity; ortak sync metadata, primary/foreign key, unique ve filtre indeksleri tanımlandı. Room v1 JSON şeması üretildi; Android host testleri 26/26 ve iOS Simulator ARM64 derlemesi başarılı oldu. iOS kontrolünde bulunan JVM'e özel value class işaretleri KMP uyumlu data class sözleşmelerine çevrildi. |
| 2026-08-05 | 4.2 | Profil, workspace/üyelik, kategori, işlem, bütçe ve etiket DAO'ları oluşturuldu; normal okumalar soft-delete kayıtlarını dışlayan `Flow` sorguları olarak sunuldu. İşlem, etiket ve ilişki kayıtları tek Room transaction içinde yazıldı. Entity-domain mapper'ları ve round-trip testleri eklendi. Robolectric üzerindeki in-memory Room testleri dâhil Android host testleri 30/30, iOS Simulator ARM64 derlemesi başarılı oldu. |
| 2026-08-09 | 4.3 | SQLCipher for Android 4.17.0, Room SupportSQLite uyumluluk modu ve Hilt singleton DB grafiği kuruldu. Rastgele 32 bayt DB parolası Android Keystore AES-256-GCM anahtarıyla korunup `noBackupFilesDir` altında atomik zarf olarak saklandı; Android backup kapatıldı ve anahtar yaşam döngüsü belgelendi. Gerçek emülatörde şifreli dosya başlığı, doğru anahtarla yeniden açma ve yanlış anahtarı reddetme testi 1/1 geçti. |
| 2026-08-09 | 4.4 | Room şeması v2'ye yükseltilerek `sync_operations` outbox tablosu, indeksleri, DAO ve v1→v2 migration eklendi. Profil, workspace, kategori, bütçe ve işlem mutasyonları entity + outbox olarak tek Room transaction içinde yazılıyor. Create/update/delete sırası, benzersiz işlem kimliği, deneme sayısı, hata ve 15 saniye–6 saat üssel geri çekilme kalıcı tutuluyor. Kapanıp açılma kalıcılığı ve rollback testleri dâhil Android host testleri 33/33, iOS Simulator ARM64 derlemesi başarılı oldu. |
| 2026-08-11 | 5.1 (devam ediyor) | Supabase Auth e-posta/şifre kayıt, giriş, güvenli otomatik oturum yenileme ve çıkış akışları `AuthRemoteDataSource` arkasında kuruldu. Offline-first `AuthRepository`, oturumu Supabase'ten ve profili Room SSOT'tan sunuyor; SDK hata kodları UI metninden bağımsız domain hatalarına çevriliyor. Android Hilt grafiği, debug APK ve iOS Simulator ARM64 ortak kod derlemesi doğrulandı. iOS build configuration ve Keychain oturum adaptörü 10.4 kapsamında bekliyor. |
| 2026-08-13 | 5.2 | Profiles, workspace/üyelik, kategori, işlem, bütçe, etiket ve işlem-etiket için KMP `@Serializable` DTO'lar ve çift yönlü domain mapper'ları tamamlandı. PostgREST remote data source; en fazla 100 kayıtlık sayfalama, tarih aralığı ve kişisel/workspace filtreleri ile güvenli upsert sözleşmelerini sunuyor. Para alanları yalnız `Long` küçük birim kabul ediyor; legacy `NUMERIC` reddediliyor. Private `receipts` bucket yükleme/kimlik doğrulamalı indirme/silme sözleşmesi, 6 MB sınırı ve güvenli nesne yolu doğrulamasıyla kuruldu. 61/61 Android host testi, Android debug APK ve iOS Simulator ARM64 derlemesi başarılı oldu; canlı Supabase'e istek gönderilmedi. |
| 2026-08-14 | 5.3 (ilk dilim) | V1 kişisel profil, kategori ve işlem başlangıç indirmesi `InitialRemoteSync` sınırında kuruldu. Kayıtlar en fazla 50'lik sayfalarla alınır; DTO doğrulaması sonrası kategori foreign key'lerini koruyacak sırayla tek Room transaction'ında upsert edilir. Uzak `updated_at`, `deleted_at` tombstone ve `version` metadata'sı `SYNCED` durumuyla yerelde korunur; Hilt grafiğine bağlandı ancak Worker henüz çağırmaz. Fake remote testi eklendi; 62/62 Android host testi, Android debug APK ve iOS Simulator ARM64 ortak kod derlemesi başarılı oldu; canlı Supabase'e istek gönderilmedi. |
| 2026-08-14 | 5.3 (yerel motor) | Sıralı outbox push, başarılı işlem temizliği, üssel retry ve kesilen `IN_FLIGHT` işlemleri kurtarma; `(updated_at, id)` cursor ile artımlı pull; Room v3 cursor/conflict tabloları; `baseVersion` kontrollü RPC ve kullanıcı çözümlemeli iki kopyalı conflict akışı tamamlandı. Dört migration geçici yerel PostgreSQL-WASM ortamında; backfill, CREATE/UPDATE, eski sürüm conflict, aynı UUID, soft-delete, NOT_FOUND, sahiplik reddi ve güvensiz para verisini durdurma senaryolarıyla geçti. 69/69 Android host testi, debug APK ve iOS Simulator ARM64 derlemesi başarılıdır. Canlı Supabase'e bağlanılmadı; staging/RLS kabul testi açıktır. |
| 2026-08-14 | 5.3 (tamamlandı) | Yeni `FeniqoMobil-Staging` projesi doğrulandı ve yalnız bu projeye altı sıralı migration uygulandı. Gerçek Supabase Auth JWT'leriyle profil trigger/RLS; profil, kategori ve işlem için koşullu RPC; doğru/eski `baseVersion`; aynı UUID; kullanıcılar arası izolasyon; hard-delete reddi; `(updated_at, id)` sorgusu; `amount_minor` uyumluluğu ve soft-delete tombstone kabul testleri geçti. Geçici kullanıcılar temizlendi, staging migration geçmişi günceldir. Production projesine dokunulmadı. |
| 2026-08-15 | 5.4 (ilk dilim) | KMP `realtime-kt` modülü ortak Supabase client'a eklendi; Android ve iOS Simulator derlemeleri geçti. Realtime kapsamı `profiles`, `categories`, `transactions` tablolarıyla sınırlandı ve yalnız `FeniqoMobil-Staging` üzerindeki `supabase_realtime` publication'a sıralı migration ile eklendi. Olaylar veri payload'ı olarak değil, Room incremental pull tetikleyicisi olarak kullanılacaktır. |
| 2026-08-15 | 5.4 (Room bağlantısı) | Realtime olaylarını yalnız senkronizasyon sinyali olarak yayımlayan ortak `RealtimeInvalidationSource` ve bu sinyalleri mevcut `SyncRepository` üzerinden artımlı pull'a yönlendiren `RealtimeSyncCoordinator` eklendi. Android'de Hilt ve Activity yaşam döngüsüne bağlandı; yalnız uygulama ön plandayken çalışır ve UI doğrudan uzak payload tüketmez. Hedefli ortak test, Android debug APK ve iOS Simulator ARM64 derlemesi başarılı oldu. |
| 2026-08-15 | 5.4 (tamamlandı) | Supabase kanalının `SUBSCRIBED` durumu ilk bağlantı ve yeniden bağlantı telafi sinyaline dönüştürüldü; böylece çevrimdışıyken kaçırılmış olabilecek kayıtlar repository üzerinden yeniden Room'a çekilir. WebSocket yeniden bağlantısı foreground süresince 5 saniyelik aralıkla, kanal yeniden katılımı 2 saniyelik aralıkla sürer; beklenmeyen kaynak hatasında koordinatör akışı kontrollü olarak yeniden başlatır. Kopma, yeniden katılma ve kaynak hatası senaryoları test edildi; 73/73 ortak test, Android debug APK ve iOS Simulator ARM64 derlemesi başarılı oldu. Production ve staging veritabanlarına yeni işlem uygulanmadı. |
| 2026-08-16 | 6.1 | WorkManager 2.11.2 ve Hilt Work 1.3.0 entegrasyonu tamamlandı. `BackgroundSyncScheduler` KMP ortak sözleşmesi ve Android `WorkManagerSyncScheduler` oluşturuldu. `SyncWorker` yalnızca `SyncRepository` tüketir; ağ hatalarında `Result.retry()`, oturumsuzluk ve conflict durumlarında `Result.success()`, kalıcı hatalarda `Result.failure()` döner. Tekil `"feniqo_one_time_sync"` iş adı altında açılışta `KEEP`, outbox mutasyonlarında `APPEND_OR_REPLACE` politikası bağlandı. Default WorkManager initializer kaldırılıp Hilt `Configuration.Provider` devreye alındı. `OfflineFirstSyncRepository` hata ayrıştırması geliştirildi; 84/84 ortak test, 12/12 androidApp testleri, Android debug APK ve iOS Simulator ARM64 derlemesi başarılı oldu. |
| 2026-08-27 | 7.4 | Stateless Compose `DashboardScreen`, `DashboardViewModel`, Hilt modülleri, `DashboardDisplayModelBuilder` ve type-safe Android route entegrasyonu tamamlandı. Room Flow SSOT dinamik ay özeti, son işlemler listesi, işlem/düzenleme/ekleme navigasyonları, geçici MoneyScore kartı ve ön değerlendirme şeffaflığı manuel smoke test ve otomatik birim/host testleriyle doğrulandı. (Not: 3 çakışma banner'ı ertelenmiş bilinen V1 conflict/outbox konusu olarak korunmaktadır). |
| 2026-08-29 | 8.1 (Dilim 1A-3B) | Bütçe domain modelleri, validation invariant'ları, use case'ler, reaktif ilerleme, Room DAO/V2 mutation state machine, toplu kopyalama, Hilt DI, V2 outbox executor, Room ACK ve Staging'de uygulanan `20260829000100_sync_write_v2_budgets.sql` migration'ı ile 18 senaryolu SQL sözleşme testi tamamlandı (13/13 hedefli test başarılı, Staging SQL sözleşme testi başarılı). Production veritabanına dokunulmadı. |
| 2026-08-29 | 8.1 (tamamlandı) | Bütçeler kullanıcı arayüzü ve kabulü (Dilim 1A–4J) tamamlandı: `BudgetsScreen`, `BudgetProgressCard` (%80 uyarı/%100 aşım), `BudgetHeader` ay gezinimi, `BudgetFormScreen` güvenli seed ve ID-based SSOT edit yüklemesi, `BudgetDeleteDialog` onaylı silme, `BudgetCopyDialog` dinamik ay seçimli önceki aydan bütçe kopyalama ve tek seferlik sonuç mesajları; `BudgetViewModel` MVI state akışı ve type-safe Navigation Compose bağlantıları doğrulandı (27/27 sharedUI, 71/71 androidApp testleri başarılı). Android emülatör manuel smoke kabulü kullanıcı tarafından doğrulandı. |
| 2026-08-30 | 8.2 (Dilim 1A–1E) | Tekrarlayan işlem takvim periyot hesaplaması (`RecurrenceScheduleCalculator`), deterministik aday planlayıcı (`PlanDueRecurringOccurrencesUseCase`), `RecurringOccurrenceKey` idempotency anahtarı, Room atomik occurrence ve `LocalMutationDao.generateRecurringOccurrence` CAS & V2 outbox üretimi, `OfflineFirstRecurringTransactionRepository`, `GenerateDueRecurringTransactionsUseCase`, Android `RecurringTransactionWorker`, 24 saatlik `RecurringTransactionWorkScheduler` ve açılış entegrasyonu tamamlandı. Hedefli birim ve host testleri başarıyla geçti. |
| 2026-08-30 | 8.2 (Dilim 2A–2B) | Tekrar kuralı saf domain komutları (`CreateRecurringTransactionCommand`, `UpdateRecurringTransactionCommand`, `SetRecurringTransactionActiveCommand`), `RecurringTransactionValidationRules` ve `applyRecurringRuleUpdate` sözleşmesi tamamlandı. Supabase `RECURRING_TRANSACTION` V2 SQL migration'ı (`20260830000100_sync_write_v2_recurring_transactions.sql`), `public.recurring_transactions` fail-closed tablosu, RLS politikası, `sync_operations_receipts` constraint'i ve 28 senaryolu SQL sözleşme testi (`sync_write_v2_contract.sql`) `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`) üzerinde uygulandı ve doğrulandı (12/12 migration güncel). Koşulsuz ROLLBACK ile test verisi bırakılmadı; Production'a dokunulmadı. |
| 2026-09-01 | 8.2 (tamamlandı) | Tekrarlayan işlemler ve abonelikler modülü (domain/validation, Room v6/v7/v8, V2 outbox/ACK/pull, Supabase 13/13 migration, `sync_write_v2` RPC, MVI Compose UI, hatırlatıcı planlayıcı, Room receipt claim, Android 13+ izin CTA'sı ve WorkManager teslimatı) tamamlandı. Staging 38 senaryolu SQL sözleşme testi ve Android emülatör manuel smoke kabulü başarıyla geçti. |
| 2026-09-01 | Mobil Navigasyon Bilgi Mimarisi (tamamlandı) | 5’li kalıcı alt bar (Ana Sayfa, İşlemler, + hızlı işlem eylemi, Plan hub, Daha Fazla hub) ve type-safe route mimarisi tamamlandı. Plan altında Bütçeler, Tekrarlayanlar, Abonelikler (aktif) / Hedefler, Borç/Alacak (Yakında); Daha Fazla altında Kategoriler, Ayarlar (aktif) / Varlıklar, Raporlar, Ortak Alanlar, Bankalar, Bildirimler (Yakında) yapılandırıldı. Hub geçişleri, geri dönüşler ve + eylemi Android emülatör manuel smoke kabulüyle kullanıcı tarafından doğrulandı. |
| 2026-09-10 | Mobil Financial Hub revizyonu | Alt bar Ana Sayfa / İşlemler / + / Bütçe / Daha Fazla olarak düzenlendi. Daha Fazla; Varlık Yönetimi, Para Yönetimi, Ortak Kullanım ve İçgörüler gruplarına ayrıldı; Profil/Hesap avatar üzerinden ayrı tutuldu. Transfer, Reports, Kişisel Bilgiler, Hesap, Bildirimler ve Tercihler gerçek domain/veri/UI akışları tamamlanana kadar pasif `Yakında` kalacak ve tamamlandıklarında aynı kayıtlar type-safe route'lara bağlanarak aktifleştirilecek. |
| 2026-09-04 | 8.4-A (ilk dilim) | Workspace saf domain command (`CreateWorkspaceCommand`, `UpdateWorkspaceCommand`), `WorkspaceInvitation` güvenli metadata sözleşmesi, `WorkspacePermissionPolicy` rol matrisi (`OWNER`, `EDITOR`, `VIEWER`), üyelikten türetilen fail-closed ön kontrol kuralları (`WorkspaceValidationRules`: ad trim/blank, açıklama normalizasyonu, davet parametreleri, üye rol değişimi/OWNER transfer zorunluluğu, üye çıkarma, alandan ayrılma, sahiplik devri) ve hedefli KMP host birim testleri başarıyla tamamlandı. Room/DAO, outbox, remote sync, SQL/RLS ve UI kapsamı henüz uygulanmadı. |
| 2026-09-05 | 8.4-B (Room şema & DAO) | Room şeması v9→v10 yükseltildi; `workspaces` tablosu `type_code` (default 'personal'), `currency_code` (default 'TRY') ve `description` alanlarıyla genişletildi; `workspace_invitations` güvenli metadata cache tablosu, indeksleri ve `WorkspaceEntity` CASCADE FK ilişkisi oluşturuldu (`ANDROID_MIGRATION_9_10`, `10.json`). `WorkspaceDao`ya salt-okunur `observeInvitations` ve remote pull hazırlığı `upsertInvitation` eklendi; Robolectric migration/backfill ve DAO testleri (`WorkspaceDaoTest`) başarıyla doğrulandı. V2 outbox, remote sync, SQL/RLS ve UI kapsamı henüz uygulanmadı. |
| 2026-09-08 | 8.4 (E12-A2) | Davet kodunu atomik redeem ederek Workspace'e güvenli katılma (`redeem_workspace_invitation_v1` RPC) tamamlandı. JOIN generic V2 outbox olarak çalıştırılmadı; token/hash Room, outbox, DTO ve loglara sızdırılmadı; fail-closed doğrulamalar (boş kod, oturumsuzluk, workspace/user id eşleşmesi), `RemoteSyncDao.applyRedeemedWorkspaceMembershipSnapshot` atomik snapshot yazımı, yerel senkronize olmayan veriler için çakışma koruması, `WorkspaceJoinViewModel` hata/loading/success akışları ve kapsamlı testler başarıyla doğrulandı. |
| 2026-09-08 | 8.4 (E12-B) | Workspace üye rol değişimi (`UPDATE`) ve alandan ayrılma (`DELETE`) için gerçek V2 outbox altyapısı tamamlandı. `WORKSPACE_MEMBER CREATE` generic outbox fail-closed engellendi; OWNER rol değişimi yapabilir, kendi rolünü veya hedefi OWNER yapamaz; OWNER alandan ayrılamaz (önce ownership transfer şartı fail-closed); EDITOR/VIEWER yalnız kendi üyeliğini bırakabilir; leave işlemi sırasında üye soft-delete tombstone yapılır, `DELETE` outbox satırı yazılır ve yalnızca aktif oturum profilinin `active_workspace_id` seçimi temizlenir (diğer profiller korunur); optimistic `baseVersion > 0` doğrulanır; canonical entityId `workspaceId:userId` biçiminde tutulur; payload'a token/token_hash sızması fail-closed engellenir; V2 outbox executor, `MissingDeleteAcknowledged`, successor unblock, UI hata eşlemeleri ve kapsamlı birim/host testleri başarıyla doğrulandı. |
| 2026-09-08 | 8.4 (E12-C) | OWNER için Workspace davet oluşturma ve EDITOR/VIEWER üye rol yönetimi UI entegrasyonu tamamlandı. `CreateWorkspaceInviteUseCase` ve `ChangeWorkspaceMemberRoleUseCase` domain delegasyonları, Hilt modülü, `WorkspaceDetailsUiState`, ViewModel re-entrancy ve double-submit koruması; geçici raw invite code izolasyonu (SavedStateHandle/Room/log/analytics/navigation yazımı yok, diyalog kapatılınca temizlenir, yalnız manuel kopyalama); OWNER/non-OWNER yetki kısıtları, Compose diyalogları ve onay akışları, erişilebilir semantics ve kapsamlı testler başarıyla doğrulandı. |
| 2026-09-08 | 8.4 (E12-D) | Workspace sahiplik devri için atomik ve fail-closed altyapı tamamlandı. Sahiplik devri generic V2 outbox'a bölünmedi ve yerel optimistic mutasyon üretilmedi; dar ve tek-transaction authenticated RPC (`transfer_workspace_ownership_v1`), ileri yönlü migration, SQL sözleşme testi eklendi; `WorkspaceRepository.transferOwnership` ve `TransferWorkspaceOwnershipUseCase` oluşturuldu; oturum, canlı workspace, actor OWNER, hedef aktif non-owner, optimistic version doğrulamaları; SYNCED olmayan yerel değişikliklerde fail-closed ret (`local_uncommitted_changes_prevent_ownership_transfer`); `RemoteSyncDao.applyOwnershipTransferSnapshot` ile atomik Room snapshot yazımı; kapsamlı birim, host ve DAO testleri başarıyla doğrulandı. Staging/production'a dokunulmadı. |
| 2026-09-08 | 8.4 (E12-E) | Workspace sahiplik devri UI entegrasyonu tamamlandı. `WorkspaceDetailsUiState` içine `pendingOwnershipTransferTarget`, `isTransferringOwnership`, `eligibleOwnershipTransferTargets` (`role in setOf(EDITOR, VIEWER)` filtresi) ve `canTransferOwnership` eklendi; `WorkspaceDetailsViewModel` içine `TransferWorkspaceOwnershipUseCase` enjekte edildi; `requestOwnershipTransfer`, `dismissOwnershipTransferConfirmation` ve `confirmOwnershipTransfer` aksiyonları yazıldı; re-entrancy/double-submit engellendi, Flow yarış durumu için confirm öncesi hedefin hâlâ güncel listede olduğu doğrulandı (hedef yoksa stale hata ve fail-closed koruma); hata durumunda detay ekranı ve onay açık tutularak `FinanceUiMessage` gösterildi; başarı durumunda onay kapatılıp Room Flow snapshot'ına güvenildi ve eski owner için "Alandan Ayrıl" akışı otomatik etkinleşti; Compose onay modalı ve erişilebilir semantics bağlandı; `FinanceUiMessage` transfer hata kodları eşlendi; birim/host ve ViewModel testleri başarıyla doğrulandı. |
| 2026-09-08 | 8.4 (E12-F) | OWNER'ın aktif EDITOR/VIEWER üyeyi çıkarma akışı tamamlandı. Self-leave ve owner-removal kuralları fail-closed ayrıştırıldı (OWNER kendini 'üye çıkar' ile çıkaramaz, başka OWNER çıkarılamaz, EDITOR/VIEWER başka üyeyi çıkaramaz, hedef aktif EDITOR/VIEWER ve SYNCED olmalıdır); `WorkspaceValidationRules.validateMemberRemoval` güncellendi; `LocalMutationDao.mutateWorkspaceMemberRemovalV2` atomik Room mutasyonu eklendi (aktörün `active_workspace_id` seçimi korunur, `WORKSPACE_MEMBER DELETE` V2 outbox yazılır); `sync_write_v2` migration'ı (`20260908000200_sync_write_v2_workspace_member_removal.sql`) ve SQL sözleşme testi eklendi; inbound tombstone sync sırasında çıkarılan kullanıcının `active_workspace_id` seçimi temizlenir; `WorkspaceDetailsUiState` / `WorkspaceDetailsViewModel` re-entrancy, Flow race koruması ve onay modalı; Compose "Üyeyi Çıkar" butonu ve semantics; kapsamlı KMP, DAO, ViewModel ve UI testleri başarıyla doğrulandı. |
| 2026-09-08 | 8.4 (E13–E14-A) | Finans repository'leri aktif workspace Room SSOT kapsamına bağlandı ve tüm finans ekran/formlarında aktif alan göstergesi eklendi. Ortak gider ödeşmesi için `WorkspaceSettlementCalculator` saf KMP sözleşmesi tamamlandı: `Long` küçük para birimi hesabı, deterministik kuruş artığı dağıtımı, net bakiye ve transfer önerileri test edildi. Payer/katılımcıların kalıcı transaction modeli ile outbox/sync entegrasyonu sonraki dilimdedir. |
| 2026-09-08 | 8.4 (E13-D1) | Transaction, Budget ve Category Room sorguları doğrulanmış ortak workspace kapsamındayken tüm üyelerin aktif satırlarını döndürecek şekilde ayrıştırıldı; kişisel kapsam yalnız aktif kullanıcının kayıtlarıyla sınırlı kalır. Repository/Room testleri hem ortak görünürlük hem kişisel veri izolasyonunu doğruladı. Uzak ortak finans kayıtlarının pull kapsamı sonraki E13-D2 dilimidir. |
| 2026-09-08 | 8.4 (E13-D2) | Ortak workspace kategori ve işlem kayıtlarının uzak incremental pull'u eklendi. Her workspace için bağımsız `CATEGORY:WORKSPACE:<id>` ve `TRANSACTION:WORKSPACE:<id>` cursor'ları kullanılır; bu sayede eski kişisel cursor'lar paylaşılan kayıtları atlatmaz. Workspace üyelik sync'i tamamlandıktan sonra pull çalışır ve mevcut Room conflict/pull kurallarını kullanır. |
| 2026-09-08 | 8.4 (E14-B2) | Ortak gider split alanlarının (`paid_by_user_id`, `participant_user_ids_json`) Room v12, V2 outbox, incremental pull, ACK, equivalent conflict ve fail-closed domain/server normalizasyonu tamamlandı. Kişisel ve gelir işlemleri owner/owner'a normalize edilir; ortak gider işlemlerinde payer ve tüm katılımcıların aktif workspace üyesi olması zorunludur. İleri yönlü Supabase migration'ı `20260908000300_sync_write_v2_transaction_split.sql` ve SQL sözleşme testleri (Senaryo 65-69) eklendi; UI/settlement ekranı kapsam dışı bırakıldı ve canlı Supabase ortamına dokunulmadı. |
| 2026-09-08 | 8.4 (E14-C) | Ortak gider split formu UI entegrasyonu tamamlandı: Yalnız aktif shared workspace ve `EXPENSE` türünde dinamik "Kim Ödedi?" (tekil seçim) ve "Kimler Katılıyor?" (çoklu seçim) alanları gösterilir; kişisel modda veya gelir işlemlerinde gizlenir; üyeler Room SSOT Flow üzerinden reaktif izlenir; fail-closed domain ve repository validasyonu korunur. |
| 2026-09-08 | 8.4 (E14-D) | Workspace Ödeşme Ekranı ve Transfer Önerileri tamamlandı: `ObserveWorkspaceSettlementUseCase` ile workspace varlığı, aktif üyelik ve geçerli harcama filtreleri fail-closed doğrulanır; `WorkspaceSettlementViewModel` ve `WorkspaceSettlementScreen` ile net üye bakiyeleri ("Alacaklı", "Borçlu", "Dengede") ve deterministik transfer önerileri hesaplanıp listelenir; geçersiz split harcamalar için uyarı gösterilir; `WorkspaceDetailsScreen` üzerinden type-safe navigasyon rotası bağlandı. |
| 2026-09-12 | Kategoriler Ekranı Yeniden Tasarımı (tamamlandı) | Kategoriler ekranı sıcak-lüks (warm-luxury) tasarım diline kavuşturuldu: Serif display başlık token'ı, ay/yıl seçici (`CurrentDateProvider` bazlı, gelecek ay engelli dialog), dinamik kategori özet kartı (toplam/özel kategori sayıları, en yüksek gider kategorisi ve tutarı, tamsayı baz puanlı mini-bar grafik), Tümü/Gider/Gelir filtre çipleri, semantik ikonlu kategori satırları, önceki aya göre `CategoryTrend` rozeti, özel kategoriler için 3 noktalı taşma menüsü (Düzenle/Sil), Room SSOT üzerinden filtrelenmiş İşlemler ekranı navigasyonu (`TransactionsRoute(categoryId, startDate, endDate)`), Feniqo içgörü kartı ve yeni kategori oluşturma eylem kartı tamamlandı. Sıfır Float/Double finansal model, fail-closed taşma (`safeAdd`) ve çoklu para birimi güvenliği 52 hedefli test ile doğrulandı. |
| 2026-09-13 | Ana Sayfa ve İşlemler hata düzeltmesi | Tek bir USD/EUR işlemin TRY özetini ve tüm ekranı genel hataya düşürmesi giderildi. Özetler aktif workspace para birimine göre hesaplanıyor; diğer para birimleri toplama karıştırılmadan dışlanıyor, işlem listesi görünür kalıyor ve dışlanan kayıt sayısı açıklanıyor. Dashboard'daki sabit/sahte trend yüzdeleri, fark tutarı ve Ağustos grafiği kaldırıldı. Regresyon testleri, tüm ortak/Android/SharedUI testleri, debug APK ve iOS Simulator ARM64 derlemesi başarılı oldu; Supabase ortamlarına dokunulmadı. |
| 2026-09-15 | Daha Fazla Ekranı Onaylı Tasarım Uygulaması (tamamlandı) | Daha Fazla ekranı ("01 Daha Fazla" ve "02 Aşağı kaydırınca" tek dikey akışı) onaylı görsel referansına tam sadakatle Jetpack Compose ile uygulandı: Üst logo, profil ikonu, sans-serif başlık/alt metin ve aktif çalışma alanı rozeti; 2x2 hücreli, bağımsız durumları korunan tek grafit genel bakış kartı (`#1F262B`, dekoratif kavisler); tek beyaz gruplanmış kartlar içinde Varlık Yönetimi ve Para Yönetimi satırları; belirgin açık adaçayı Ortak Alanlar kartı ve iki kişi vektörel çizimi; pasif/Yakında Raporlar kartı; Ayarlar kartı; profil bilgilendirme satırı; alt bar 3 noktalı More ikonu, pie chart Budget ikonu ve seçili sekme alt göstergesi tamamlandı. Hedefli birim testleri ve Android debug APK derlemesi başarıyla doğrulandı. |
| 2026-09-15 | Hedefler Modülü Onaylı Tasarım Uygulaması (Paneller 01–13) | Onaylı tasarım görsellerine (Paneller 01–13) tam sadakatle Jetpack Compose UI yenilemesi tamamlandı: Sıcak kırık beyaz (`#F7F5F0`) arka plan, beyaz yüzeyler, grafit hero kartları (`#303536`), Feniqo adaçayı yeşili (`#2D5A43`) ve ölçülü kırmızı (`#C0392B`). 01 Hedefler ekranı (Tümü/Aktif/Tamamlanan sekmeleri, dinamik grafit hero kartı, çoklu para birimi güvenliği, hedef kartları, alt sabit "+ Yeni hedef"); 02/03/12 Hedef detayı (grafit hero kartı, gerçek hareketlerden beslenen çizgi grafiği, Feniqo içgörü kartı, hareket geçmişi açma/daraltma, tamamlanmış hedef görünümü, alt sabit "+ Para ekle"); 04/05 Yeni hedef ve düzenleme formu (hedef adı, tutar, modal para birimi seçici sheet'i [TRY/USD/EUR], editte kilitli para birimi ve 'Mevcut birikim' kartından hareket ekleme akışı, hedef tarihi, renk seçici paleti); 06/07/13 Para ekle/çıkar formu (kompakt üst hedef kartı, yeşil 'Para ekle' / kırmızı 'Para çıkar' segment seçimi, limit kontrolü ve aşımda kırmızı '◆ Çıkarılacak tutar mevcut birikimi aşamaz.' uyarısıyla devre dışı kalan buton); 08/09/10/11 Tarih/para birimi seçimleri, onaylı GoalDeleteDialog ve boş durum ekranı bağlandı. Clean Architecture, Room SSOT ve Long küçük para birimi korundu. sharedLogic, sharedUI ve androidApp testleri, debug APK ve iOS simulator derlemesi başarıyla doğrulandı. |
| 2026-09-16 | Borç ve Alacaklar Modülü Onaylı Tasarım Uygulaması (Paneller 01–14) | Onaylı tasarım panellerine (01–14) tam sadakatle Jetpack Compose UI/UX yenilemesi tamamlandı: Sıcak kırık beyaz (`#F7F5F0`) arka plan, beyaz yüzeyler, grafit hero kartları (`#303536`), adaçayı yeşili (`#2D5A43`), pozitif yeşil (`#16A34A`) ve negatif kırmızı (`#DC2626`). 01 Borç ve Alacaklar ana ekranı (üst bar geri ve dairesel '+' butonu, grafit özet kartı [kalan net durum, borçlar/alacaklar sütunları, çoklu para birimi güvenliği], yaklaşan vadeler bölümü, gruplanmış aktif borçlar ve alacaklar kartları, tamamlananlar kartı, borç kapatma planı giriş kartı ve alt sabit '+ Yeni kayıt' butonu); 02/03 Kaydı düzenle / detay formu (üst kart, grafit bakiye durumu [#303536] ve ilerleme çubuğu, '+ Ödeme/Tahsilat ekle' butonu, kayıt bilgileri segmenti [Borç/Alacak], editte kilitli para birimi, vade tarihi, ödeme geçmişi listesi, kırmızı 'Kaydı sil' ve '✓ Değişiklikleri kaydet'); 04 Yeni kayıt formu (segment seçimi, başlık, tutar, para birimi sheet seçicisi, vade tarihi, '+ Kaydı oluştur'); 05/06/14 Ödeme/Tahsilat formu (üst kayıt kartı, grafit bakiye özeti, büyük tutar girişi, maksimum limit rehberi, Panel 14 bakiye aşım uyarı kartı ve bilgilendirme notu); 07/08 Borç kapatma planı (bütçe girişi, grafit simülasyon sonuç kartı, borç kapanış sırası ve aylık dağılım listesi); 09 Vade tarihi seçim modal sheet'i (özel takvim ızgarası ve hızlı seçim); 10 Para birimi seçim modal sheet'i (TRY/USD/EUR custom radio); 11 Kayıt adı içeren DebtDeleteDialog; 12 Boş durum ekranı (DebtEmptyState); 13 Kapanmış kayıt banner'ı (DebtSettledBanner). Clean Architecture, Room SSOT ve Long kuruş modeli korundu. Hedefli unit/host testleri, debug APK ve iOS simulator derlemesi başarıyla doğrulandı. |
| 2026-09-16 | Ayarlar ve Profil Modülü Onaylı Tasarım Uygulaması (Paneller 01–24) | Onaylı tasarım panellerine (01–24) tam sadakatle Jetpack Compose ve Clean Architecture uygulaması tamamlandı: 01 Ayarlar ana ekranı; 02–05 Profil, Hesap ve Kişisel Bilgiler (tek görünen ad, AuthValidationRules ile doğrulama, izole UserAvatarManager dahili depolama); 06–09 Bildirimler (NotificationPolicy gecelik saat aralığı erteleme, SubscriptionPaymentReminderWorker at-least-once claim, bildirimlerde tutar gizleme, kategori bazlı bildirim altyapısı); 10–13 Dil, Bölge ve Biçimler (Preferences DataStore kalıcı saklama, MoneyFormatter amount masking '••••' ve TalkBack erişilebilirlik gizlemesi, ilk gün, tarih/sayı formatları); 14–17 Güvenlik & Gizlilik (Biyometrik kilit entegrasyonu, AuthValidationRules ile parola değişikliği, e-posta doğrulama akışları); 18–21 Veri Yönetimi & Senkronizasyon (Room SSOT SyncOverview, WorkspaceConflictResolutionDialog 'İncele' bağlantısı, JSON/CSV dışa aktarma, FeniqoBackupV1 içe aktarma preview/onay); 22–24 Yardım, Hakkında ve Çıkış modalı (SignOutConfirmDialog, sans-serif feniqo marka standardı). sharedLogic testleri, androidApp birim testleri ve assembleDebug derlemesi başarıyla doğrulandı. |
| 2026-09-16 | Varlıklar Modülü Onaylı Tasarım Uygulaması (Paneller 01–13) | Onaylı tasarım panellerine (01–13) tam sadakatle Jetpack Compose UI/UX yenilemesi ve Clean Architecture entegrasyonu tamamlandı: Sıcak kırık beyaz (`#F7F5F0`) arka plan, beyaz yüzeyler, grafit hero/özet kartları (`#303536`), Feniqo adaçayı yeşili (`#2D5A43`), pozitif yeşil (`#16A34A`) ve negatif kırmızı (`#DC2626`). 01/08/11 Varlıklar ana ekranı (üst bar geri ve dairesel '+' butonu, grafit özet kartı [tek ve çoklu para birimi kırılımı], dağılım önizleme çubuğu ve 'Detay' butonu, semantik tip ikonlu varlık kartları [miktar, maliyet ve anlık değer], filtre çipleri [Tümü, Nakit/Banka, Gayrimenkul, Araç, Hisse/Fon, Altın/Emtia, Kripto, Diğer], boş durum ekranı ve alt sabit '+ Yeni varlık'); 02/09/12 Varlık detayı (üst tip başlığı ve düzenle/sil aksiyonları, grafit anlık değer kartı, miktar ve alış birim fiyatı kartları, güvenli Long kuruş aritmetik hesaplamalı [Double kullanılmadan] toplam maliyet ve net kâr/zarar farkı [+/%25, -/%8], piyasa fiyatı doğrulanamadığında bilgi diyaloğu bağlantılı sarı uyarı hapı, AssetDeleteConfirmationModal); 03 Varlık dağılım ekranı (para birimi filtresi hapları, grafit toplam portföy kartı, Compose Canvas ile çizilmiş donut grafik, renk kodlu varlık türü dağılım listesi [oran % ve toplam tutar]); 04/05/06/07/10/13 Varlık formu (düzenlenebilir grafit anlık değer kartı, tip seçim modal sheet'i [Panel 07 semantik ikonlar], para birimi modal sheet'i [Panel 13 TRY/USD/EUR], anlık maliyet önizleme kartı [Panel 05], sembol format doğrulama uyarıları [Panel 10], güncelle/ekle akışları). `AssetFinancialCalculator` saf KMP finansal hesaplayıcısı eklendi. `:sharedLogic:testAndroidHostTest`, `:sharedUI:allTests`, `:androidApp:testDebugUnitTest`, `:sharedLogic:compileKotlinIosSimulatorArm64` ve `:androidApp:assembleDebug` ile tüm test ve derleme süreçleri başarıyla doğrulandı. |
| 2026-09-16 | 7.3-D2 | Custom Split saf domain, doğrulama ve settlement kuralları tamamlandı: `TransactionSplitMode` (EQUAL/CUSTOM), `TransactionParticipantShare`, fail-closed tipli doğrulama ve normalizasyon kuralları, `EqualSplitCalculator` deterministik kuruş artığı fonksiyonu, `WorkspaceSettlementCalculator` ve `ObserveWorkspaceSettlementUseCase` fail-closed dışlama (`hasExcludedExpenses = true`) kuralları uygulandı. 7.3-D2 saf domain ve settlement kuralları tamamlandı; Custom Split henüz Room, sync veya UI üzerinden kullanılamıyor. |
