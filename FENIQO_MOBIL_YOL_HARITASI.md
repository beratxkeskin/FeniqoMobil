# FeniqoMobil — Uçtan Uca Geliştirme Yol Haritası

> **Durum:** Devam ediyor — son tamamlanan adım: 1.2; mevcut adım: 1.3
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

- [ ] SQL şemasını ilk kurulum ve sürümlü migration dosyalarına ayırma planını oluştur.
- [x] `workspace_members` rol sözlüğünü tekleştir: `OWNER`, `EDITOR`, `VIEWER`.
- [ ] Ortak alan kayıtlarında görüntüleme, ekleme, düzenleme ve silme yetkilerini bir matris olarak tanımla.
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

- [ ] Compose + Material 3 bağımlılıklarını ekle.
- [ ] Hilt ve KSP kurulumunu yap.
- [ ] Navigation Compose, Lifecycle ve ViewModel bağımlılıklarını ekle.
- [ ] Coroutines, Serialization ve Ktor bağımlılıklarını ekle.
- [ ] Test bağımlılıklarını ekle: JUnit, coroutine test, Turbine, MockK/Fake yaklaşımı.

**Doğrulama:** Boş uygulama derlenir ve emülatörde açılır.

### 2.3 Tema, tasarım sistemi ve temel UI kabuğu

- [ ] Emerald Phoenix marka renklerini Material 3 color scheme'e dönüştür.
- [ ] Açık, koyu ve sistem teması desteğini kur.
- [ ] Tipografi, boşluk, köşe yarıçapı ve durum renklerini token olarak tanımla.
- [ ] Tekrar kullanılabilir bileşenleri oluştur: yükleniyor, boş durum, hata, onay diyaloğu, snackbar.
- [ ] Bottom navigation ve uygulama iskeletini tasarla.

**Doğrulama:** Tema değişimi uygulama yeniden açıldığında korunur; erişilebilir kontrast kontrol edilir.

---

## 3. Domain katmanı — saf iş kuralları

### 3.1 Ortak temel tipler

- [ ] `Money`, `Currency`, `EntityId`, `LocalDate`, `SyncStatus` ve ortak hata modellerini tasarla.
- [ ] Para formatlama ile para hesaplamasını ayır.
- [ ] Zaman dilimi kurallarını belirle: işlem tarihi yerel tarih, sunucu zamanları UTC.

### 3.2 Çekirdek domain modelleri

- [ ] `UserProfile` modelini oluştur.
- [ ] `Workspace` ve `WorkspaceMember` modellerini oluştur.
- [ ] `Category` modelini oluştur.
- [ ] `Transaction` modelini oluştur; taksit, ödeme yöntemi ve makbuz alanlarını dahil et.
- [ ] `Tag` ve işlem-etiket ilişkisini modelle.
- [ ] `Budget` modelini oluştur.

### 3.3 İkinci dalga domain modelleri

- [ ] `RecurringTransaction` ve tekrar kuralını oluştur.
- [ ] `Goal`, `Debt`, `Subscription` modellerini oluştur.
- [ ] `Asset` ve piyasa fiyatı modellerini oluştur.
- [ ] Dashboard özetleri, MoneyScore ve rapor modellerini oluştur.

**Tamamlanma ölçütü:** Bu sınıflar Android, Room, Supabase veya Compose import etmez.

### 3.4 Repository sözleşmeleri

- [ ] `AuthRepository` arayüzünü tanımla.
- [ ] `TransactionRepository`, `CategoryRepository`, `BudgetRepository` arayüzlerini tanımla.
- [ ] `WorkspaceRepository`, `SyncRepository` ve tercih/güvenlik arayüzlerini tanımla.
- [ ] Her okuma metodunu uygun `Flow` türüyle tasarla.
- [ ] Her yazma metodunun başarılı/başarısız sonucunu tanımlı bir sonuç tipiyle döndürmesini sağla.

### 3.5 Use case'ler

- [ ] İlk use case'ler: işlem ekle, düzenle, sil, filtrele, kategori ekle ve dashboard özetini getir.
- [ ] İşlem doğrulamalarını use case katmanında uygula.
- [ ] MoneyScore ve bütçe hesaplamalarını saf fonksiyon/use case olarak uygula.
- [ ] Her use case için birim testi yaz.

---

## 4. Yerel veri katmanı — Room ve offline-first temel

### 4.1 Room şeması

- [ ] Domain modellerinden ayrı Room entity sınıflarını oluştur.
- [ ] Her entity'ye yerel senkronizasyon alanlarını ekle: `syncStatus`, `updatedAt`, `deletedAt`, `version`.
- [ ] Primary key, foreign key, indeks ve unique kısıtlarını tasarla.
- [ ] `TransactionTagCrossRef` gibi ilişki tablolarını oluştur.
- [ ] Arama, tarih ve çalışma alanı filtreleri için indeksleri tanımla.

### 4.2 DAO'lar ve mapper'lar

- [ ] Her çekirdek model için DAO oluştur.
- [ ] DAO okumalarını `Flow` ile sun.
- [ ] Çok tablolulu yazma işlemlerini Room transaction içinde tut.
- [ ] Entity ↔ domain dönüşümlerini `data.mapper` altında yaz.
- [ ] DAO testlerini in-memory test veritabanında çalıştır.

### 4.3 Şifreli yerel veritabanı

- [ ] Android SQLCipher entegrasyonunu yap.
- [ ] Veritabanı parolasını Android Keystore koruması altında oluştur/sakla.
- [ ] Anahtar kaybı, uygulama kaldırma ve cihaz değişimi senaryolarını belgele.
- [ ] Şifrelenmiş DB'nin gerçekten açıldığını entegrasyon testiyle doğrula.

### 4.4 Offline yazma kuyruğu

- [ ] `sync_operations`/outbox tablosunu tasarla.
- [ ] Ekleme, güncelleme ve silme olaylarını sırayla kuyruğa ekle.
- [ ] İşlemi yerel DB + outbox'a atomik olarak kaydet.
- [ ] Başarısız senkronizasyonda deneme sayısı, son hata ve geri çekilme bilgisini kaydet.

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

Şu an başlanacak adım: **1.1 — Web referansını envantere dönüştürme**.

Bu adımın tamamlanmasından sonra önerilen ilk kod adımı: **3.1 — Ortak temel tipler**. Önce modelleri anlamlandıracak, sonra repository arayüzlerine, Room'a, Supabase'e ve UI'a geçeceğiz. Böylece her katmanın neden var olduğunu uygulayarak öğrenmiş olacaksın.

## İlerleme notları

| Tarih | Adım | Not |
|---|---|---|
| 2026-08-03 | 0 / analiz | Web projesi incelendi; mobil mimari ve risk analizi hazırlandı. |
| 2026-08-03 | 2.1 | KMP proje omurgası oluşturuldu. Android/iOS kimliği `com.feniqo.mobile`; API 26; Android debug derlemesi ve ortak modül host testleri başarılı; Git deposu başlatıldı. |
| 2026-08-03 | 1.1 | Web ekranları, kullanıcı akışları, özellikler, V1 veri sözlüğü ve TypeScript/SQL riskleri `docs/WEB_REFERANS_ENVANTERI.md` içinde tamamlandı. |
| 2026-08-03 | 1.2 | V1 kapsamı, V2/V3 ayrımı ve yedi temel ürün/mimari kararı proje sahibi tarafından onaylandı. |
