# FeniqoMobil — Uçtan Uca Geliştirme Yol Haritası

> **Durum:** Devam ediyor — son tamamlanan adım: 4.4; sonraki adım: 5.1
> **Ana hedef:** Feniqo web uygulamasını referans alarak, Android'de native çalışan; offline-first; Supabase ile güvenli biçimde senkronize olan ve gelecekte iOS'a Kotlin Multiplatform (KMP) ile taşınabilen profesyonel bir mobil uygulama geliştirmek.

Bu dosya projenin çalışma sözleşmesidir. Bir adım tamamlandığında ilgili kutu işaretlenir ve kısa bir not eklenir. Sohbette yalnızca örneğin **"2.3'te kalmıştık"** demen, aynı noktadan devam etmemiz için yeterlidir.

---

## 0. Çalışma ilkeleri ve mimari kararlar

### 0.1 Değişmez teknik ilkeler

- [ ] Uygulama dili tamamen Kotlin olacak.
- [ ] Minimum Android sürümü API 26 (Android 8.0) olacak.
- [ ] UI Jetpack Compose ve Material 3 ile geliştirilecek.
- [ ] Clean Architecture + MVVM uygulanacak.
- [ ] UI, yalnızca ViewModel'den gelen `StateFlow`/`SharedFlow` verisini tüketecek.
- [ ] Room, uygulamanın **tek okuma kaynağı** (Single Source of Truth) olacak.
- [ ] Repository, yerel veritabanı ve Supabase arasındaki senkronizasyonun tek sorumlusu olacak.
- [ ] Her yazma işlemi önce yerel veritabanına güvenli olarak kaydedilecek; ağ yoksa senkronizasyon kuyruğuna alınacak.
- [ ] Tüm bağımlılıklar constructor injection ile Hilt üzerinden sağlanacak.
- [ ] Para tutarları `Double` ile değil, en küçük para birimi cinsinden `Long` ile tutulacak. Örnek: `125,50 TRY` → `12550` kuruş.

### 0.2 KMP stratejisi

- [ ] Proje Android-first başlayacak fakat Gradle yapısı KMP uyumlu tutulacak.
- [ ] Platformdan bağımsız modeller, iş kuralları, use case'ler, DTO'lar ve senkronizasyon sözleşmesi `commonMain` için tasarlanacak.
- [ ] Android'e bağımlı parçalar (`Hilt`, `WorkManager`, `BiometricPrompt`, `ML Kit`, Android Keystore) `androidMain`/`androidApp` içinde kalacak.
- [ ] iOS için ileride `iosApp` eklenebilecek; güvenlik, veritabanı açma ve cihaz servisleri platform adaptörleri ile soyutlanacak.
- [ ] iOS UI stratejisi, Android V1 tamamlandıktan sonra seçilecek: Compose Multiplatform veya SwiftUI + ortak iş mantığı.

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

- [ ] Supabase Kotlin SDK ve hedefe uygun Ktor istemcisini ekle.
- [ ] URL ve publishable key'i güvenli build configuration üzerinden sağla; gizli service-role anahtarını uygulamaya koyma.
- [ ] E-posta/şifre ile kayıt, giriş, oturum yenileme ve çıkış akışlarını oluştur.
- [ ] Oturum bilgisini platforma özel güvenli depoda tut.
- [ ] Hata kodlarını kullanıcı dostu domain hatalarına dönüştür.

### 5.2 DTO ve remote data source

- [ ] Her çekirdek tablo için `@Serializable` DTO oluştur.
- [ ] DTO ↔ domain dönüşümlerini yaz.
- [ ] Sayfalama, tarih aralığı ve çalışma alanı filtrelerini destekle.
- [ ] Supabase Storage'a private makbuz yükleme/indirme sözleşmesini oluştur.

### 5.3 Senkronizasyon motoru

- [ ] İlk girişte buluttan yerel DB'ye başlangıç indirmesini uygula.
- [ ] Outbox'taki işlemleri sırayla Supabase'e gönder.
- [ ] Başarılı gönderimleri kuyruktan sil veya tamamlandı olarak işaretle.
- [ ] Sunucudan değişen kayıtları Room'a upsert et.
- [ ] Çakışma politikası uygula: V1 için sürüm/zaman damgalı son yazan kazanır.
- [ ] Hata durumunda güvenli tekrar deneme ve kullanıcıya görünür sync durumu oluştur.

### 5.4 Realtime

- [ ] Ortak alan tabloları için gerekli Realtime yayınlarını belirle.
- [ ] Realtime olayı geldiğinde UI'ı değil, Room verisini güncelle.
- [ ] Bağlantı kaybı ve yeniden bağlanma davranışını test et.

---

## 6. Arka plan işleri ve güvenilir senkronizasyon

### 6.1 WorkManager

- [ ] Hilt destekli `CoroutineWorker` kur.
- [ ] Uygulama başlangıcında benzersiz ilk senkronizasyon işi planla.
- [ ] Ağ bağlantısı koşulu ile outbox senkronizasyonu çalıştır.
- [ ] Hata durumunda `Result.retry()` ve exponential backoff uygula.
- [ ] Periyodik abonelik/bütçe kontrolünü planla.

### 6.2 Senkronizasyon gözlemi

- [ ] Kullanıcıya son senkronizasyon zamanı ve bekleyen işlem sayısını göster.
- [ ] Bağlantı durumunu izleyip müdahaleci olmayan bir offline göstergesi sun.
- [ ] Kullanıcının manuel senkronizasyon başlatabilmesini sağla.

**Tamamlanma ölçütü:** Uygulama kapalıyken oluşan ağ dönüşünde bekleyen yazılar güvenle gönderilir.

---

## 7. Presentation katmanı — ilk ürün akışı

### 7.1 Navigasyon ve UI durumları

- [ ] Type-safe Navigation Compose rotalarını oluştur.
- [ ] `DashboardUiState`, `TransactionUiState` gibi sealed UI state'leri tasarla.
- [ ] Tek seferlik olayları `SharedFlow` ile yönet: snackbar, navigasyon, izin isteği.
- [ ] Her ViewModel'i Hilt constructor injection ile oluştur.

### 7.2 Kimlik doğrulama ekranları

- [ ] Açılış/splash ve oturum kontrolü.
- [ ] Giriş ekranı.
- [ ] Kayıt ekranı.
- [ ] Şifre görünürlüğü, doğrulama, hata ve yüklenme durumları.

### 7.3 İşlem ve kategori akışı

- [ ] İşlem listesi: tarih gruplama, arama, filtreleme ve boş durum.
- [ ] İşlem ekleme/düzenleme ekranı.
- [ ] Kategori seçimi ve kategori yönetimi.
- [ ] Taksitli işlem oluşturma ve silme davranışı.
- [ ] Makbuz bağlama için UI hazırlığı.

### 7.4 Dashboard

- [ ] Aylık gelir, gider, net bakiye ve tasarruf oranı.
- [ ] Son işlemler ve hızlı işlem ekleme.
- [ ] Bütçe uyarı alanı için hazırlık.
- [ ] MoneyScore kartı ve açıklanabilir hesaplama sonucu.

**Tamamlanma ölçütü:** Kullanıcı offline iken işlem ekleyebilir, listeleyebilir; ağ geldiğinde senkronizasyonu görebilir.

---

## 8. Finans modülleri

### 8.1 Bütçeler

- [ ] Aylık kategori bütçesi oluşturma/düzenleme/silme.
- [ ] Harcama ilerleme hesabı.
- [ ] %80 uyarı ve %100 aşım durumları.
- [ ] Önceki ay bütçelerini kopyalama.

### 8.2 Tekrarlayan işlemler ve abonelikler

- [ ] Tekrar kurallarını modelle ve doğrula.
- [ ] WorkManager ile vadesi gelen işlemleri idempotent üret.
- [ ] Abonelik yenileme, duraklatma ve yaklaşan ödeme bildirimi.

### 8.3 Hedefler ve borçlar

- [ ] Birikim hedefleri ve hedefe para ekleme.
- [ ] Borç/alacak ekleme, vade ve ödendi işareti.
- [ ] Borç ödeme planı / snowball raporu.

### 8.4 Ortak çalışma alanları

- [ ] Çalışma alanı oluşturma, katılma, ayrılma ve aktif alan seçimi.
- [ ] Üye listesi ve rol tabanlı UI.
- [ ] Ortak işlem ve bütçe görünürlüğü.
- [ ] Kimin ne kadar ödediği ve borç dağılımı hesaplaması.

### 8.5 Varlıklar, net değer ve raporlar

- [ ] Varlık CRUD: nakit, metal, kripto, hisse, gayrimenkul vb.
- [ ] Net değer hesabı.
- [ ] Piyasa fiyat servisinin güvenli backend sözleşmesi.
- [ ] Harcama analizi, trend, ısı haritası ve tahmin raporları.

---

## 9. Cihaz özellikleri, güvenlik ve veri taşınabilirliği

### 9.1 Biyometrik uygulama kilidi

- [ ] Kilit tercihini ve otomatik kilit süresini oluştur.
- [ ] `BiometricPrompt` ile uygulama açılışında doğrulama uygula.
- [ ] Biyometri kullanılamadığında güvenli cihaz kimlik doğrulama geri dönüşünü tasarla.

### 9.2 Makbuz OCR

- [ ] CameraX kamera akışını kur.
- [ ] ML Kit Text Recognition ile metni al.
- [ ] Toplam tutar, tarih ve işyeri adı için güvenilir ayrıştırma kuralları oluştur.
- [ ] OCR sonucunu doğrudan kaydetme; kullanıcı onaylı işlem formuna aktar.
- [ ] Kamera ve görsel izin reddi senaryolarını ele al.

### 9.3 İçe/dışa aktarma ve gizlilik

- [ ] İşlemleri CSV olarak dışa aktar.
- [ ] Yedek formatı ve sürümünü tanımla.
- [ ] JSON yedek içe aktarmada doğrulama ve geri alınabilirlik uygula.
- [ ] Hassas verilerin loglara yazılmadığını doğrula.

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
- [ ] Seçilen UI stratejisine göre Compose Multiplatform veya SwiftUI ekranlarını uygula.

---

## Başlangıç sırası

Sonraki teknik adım: **5.1 — Supabase istemcisi ve oturum**.

Bu adımın tamamlanmasından sonra önerilen ilk kod adımı: **5.1 — Supabase istemcisi ve oturum**. Publishable key kullanan KMP istemci yapılandırmasını kurup güvenli oturum yaşam döngüsünü repository sınırının arkasına alacağız.

## İlerleme notları

| Tarih | Adım | Not |
|---|---|---|
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
