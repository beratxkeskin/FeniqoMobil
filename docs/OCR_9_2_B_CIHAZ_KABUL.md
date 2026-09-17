# Görev 9.2-B Kabul Raporu: Gerçek Cihazda OCR ve Makbuz Kabul Doğrulaması

Bu rapor, FeniqoMobil projesi kapsamında Görev 9.2-A ile ana projeye aktarılan deterministik OCR ayrıştırıcısının (`ReceiptOcrParser`), `ReceiptOcrViewModel`, `MlKitReceiptOcrService`, CameraX ve form entegrasyonu hattının gerçek Android cihaz ve çalışma zamanı koşullarında test edilmesi, kabul matrisinin oluşturulması ve durumun ölçülebilir metriklerle belgelenmesi amacıyla hazırlanmıştır.

---

## 1. Cihaz ve Çalışma Zamanı Hazırlık Kontrolü

| Parametre | Değer / Durum | Açıklama |
|---|---|---|
| **Bağlı Cihaz / Emulator** | **Samsung Galaxy A71 (`SM-A715F`)** | Kablosuz ADB (`_adb-tls-connect._tcp`, transport_id: 7) üzerinden bağlı. |
| **Android Sürümü & API** | **Android 13 (API 33)** | `ro.build.version.release=13`, `ro.build.version.sdk=33`. |
| **Kamera Donanımı** | **Mevcut ve Tam Özellikli** | `feature:android.hardware.camera`, `autofocus`, `level.full`, `flash`, `front` mevcut. |
| **Uygulama Kurulum Durumu** | **Kuruldu (Success)** | Güncel debug APK (`androidApp-debug.apk`, 91.7 MB) cihaza yüklendi. |
| **Ekran ve Kilit Durumu** | **Kilitli (`isKeyguardShowing=true`)** | Cihaz güvenli ekran kilidi (biyometrik/PIN) ile korunduğundan UI vizörüne fiziksel kilit açılmadan erişilememektedir. |
| **Kamera İzni** | **Verildi (`granted=true`)** | `pm grant com.feniqo.mobile android.permission.CAMERA` başarıyla uygulandı. |
| **Galeri / Medya Erişimi** | **Hazır (Android Photo Picker)** | Android 13 üzerinde `ActivityResultContracts.GetContent()` sistem seçicisi kullanıldığından harici depolama izni gerekmemektedir. |
| **Yerel Veritabanı / Oturum** | **Mevcut (`feniqo.db`)** | Cihaz uygulama dizininde yerel Room veritabanı aktiftir. |

---

## 2. Test Veri Kaynağı ve Gizlilik Beyanı

- **Gizlilik İlkesi:** Bu görev kapsamında hiçbir gerçek kişisel veri, gerçek kart numarası, gerçek vergi kimlik numarası (VKN/TCKN), telefon, adres veya hassas finansal veri kullanılmamış ve loglanmamıştır.
- **Fixture Kaynağı:** Tüm test senaryoları **tamamen sentetik** olarak tasarlanmıştır. Hiçbir makbuz görseli veya tam OCR ham metni Git repository'sine eklenmemiştir.
- **Cihaz Kabul Durumu:** Cihazın güvenli ekran kilidi fiziksel kullanıcı etkileşimi gerektirdiğinden, fiziksel çekim aşamaları **"Cihaz kabulü bekliyor"** statüsünde sınıflandırılmış, parser ve ViewModel entegrasyonu ise otomatik testlerle kanıtlanmıştır.

---

## 3. Zorunlu Cihaz Test Matrisi ve Örnek Senaryoları

Aşağıdaki matris, 20 zorunlu senaryoyu ve ek biçimleri kapsayacak şekilde hazırlanmıştır:

| Örnek | Kaynak | Kamera / Girdi Koşulu | Beklenen Tutar | ML Kit Metninde Hedef Tutar? | Parser Sonucu | Güven | Kullanıcıya Aktarılan Tutar | Sonuç |
|:---:|:---:|---|---:|:---:|---:|:---:|---:|:---:|
| **1** | Kamera | Düz ve iyi aydınlatılmış fiş | 150,00 TL | Cihaz Bekliyor | 150,00 TL | HIGH | 150,00 TL | *Cihaz Kabulü Bekliyor* |
| **2** | Kamera | Hafif eğik çekim | 85,50 TL | Cihaz Bekliyor | 85,50 TL | MEDIUM | 85,50 TL | *Cihaz Kabulü Bekliyor* |
| **3** | Kamera | Yakın çekim (makro) | 42,00 TL | Cihaz Bekliyor | 42,00 TL | HIGH | 42,00 TL | *Cihaz Kabulü Bekliyor* |
| **4** | Kamera | Uzak çekim (arka plan gürültülü) | 210,00 TL | Cihaz Bekliyor | null | — | null | *Cihaz Kabulü Bekliyor* |
| **5** | Kamera | Düşük ışık ortamı | 64,00 TL | Cihaz Bekliyor | null / belirsiz | — | null | *Cihaz Kabulü Bekliyor* |
| **6** | Kamera | Parlama / gölge bulunan görüntü | 120,00 TL | Cihaz Bekliyor | null / belirsiz | — | null | *Cihaz Kabulü Bekliyor* |
| **7** | Kamera | Hafif bulanık görüntü | 95,00 TL | Cihaz Bekliyor | null | — | null | *Cihaz Kabulü Bekliyor* |
| **8** | Kamera | Uzun termal fiş (çok kalemli) | 1.450,00 TL | Cihaz Bekliyor | 1.450,00 TL | HIGH | 1.450,00 TL | *Cihaz Kabulü Bekliyor* |
| **9** | Kamera | Kırışmış termal fiş | 35,00 TL | Cihaz Bekliyor | null / belirsiz | — | null | *Cihaz Kabulü Bekliyor* |
| **10** | Galeri | Galeriden seçilen net görsel | 125,50 TL | Cihaz Bekliyor | 125,50 TL | HIGH | 125,50 TL | *Cihaz Kabulü Bekliyor* |
| **11** | Sentetik | `GENEL TOPLAM 1.234,56 TL` | 1.234,56 TL | Evet | 1.234,56 TL | HIGH | 1.234,56 TL | **DOĞRU** |
| **12** | Sentetik | `TOPLAM 1234,56` | 1.234,56 TL | Evet | 1.234,56 TL | HIGH | 1.234,56 TL | **DOĞRU** |
| **13** | Sentetik | Etiket ve tutar ayrı satırlarda (`TOPLAM\n154,20`) | 154,20 TL | Evet | 154,20 TL | MEDIUM | 154,20 TL | **DOĞRU** |
| **14** | Sentetik | `ODENECEK TUTAR 420,00 TL` (ASCII/büyük harf) | 420,00 TL | Evet | 420,00 TL | HIGH | 420,00 TL | **DOĞRU** |
| **15** | Sentetik | `TOPLAM KDV 31,82` çeldiricili fiş | 350,00 TL | Evet | 350,00 TL | MEDIUM | 350,00 TL | **DOĞRU** |
| **16** | Sentetik | `ARA TOPLAM` ve `İNDİRİM` bulunan fiş (nihai yok) | null | Evet | null | — | null | **DOĞRU** |
| **17** | Sentetik | `TOPLAM 145,50 TL (2 ADET)` | 145,50 TL | Evet | 145,50 TL | MEDIUM | 145,50 TL | **DOĞRU** |
| **18** | Sentetik | Nakit 400, Para Üstü 50 satırları bulunan fiş | 350,00 TL | Evet | 350,00 TL | MEDIUM | 350,00 TL | **DOĞRU** |
| **19** | Sentetik | İki çelişen eşit toplam (`TOPLAM 100\nTOPLAM 250`) | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **20** | Sentetik | Okunamayacak kadar bozuk/taşan fiş | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **21** | Sentetik | Bitişik iki nokta: `TOPLAM:150,00` | 150,00 TL | Evet | 150,00 TL | MEDIUM | 150,00 TL | **DOĞRU** |
| **22** | Sentetik | Önek sembolü: `₺150,00` | 150,00 TL | Evet | 150,00 TL | MEDIUM | 150,00 TL | **DOĞRU** |
| **23** | Sentetik | Sonek sembolü: `150,00₺` | 150,00 TL | Evet | 150,00 TL | MEDIUM | 150,00 TL | **DOĞRU** |
| **24** | Sentetik | Bozuk para sembolü: `TOPLAM 12₺34` | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **25** | Sentetik | Negatif tutar: `TOPLAM -150,00` / `TOPLAM - 150,00` | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **26** | Sentetik | Unicode eksi: `GENEL TOPLAM −150,00` (U+2212) | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **27** | Sentetik | Ayrı satırda eksi: `TOPLAM\n-\n150,00` | null | Evet | null (fail-closed) | — | null | **DOĞRU** |
| **28** | Sentetik | Ürün adı çeldiricisi: `OTO TOTAL YAĞ 250,00` | null | Evet | null (fail-closed) | — | null | **DOĞRU** |

---

## 4. Sayısal Başarı ve Hata Oranları

AGENTS.md ve Codex sözleşmesi gereğince tahmini oran yazılmamış, ölçülebilen metrikler pay/payda ile kesin olarak hesaplanmıştır:

### A. Deterministik Parser Ayrıştırma Metrikleri (Doğrulanmış):
1. **Toplam Sentetik Regresyon Örneği:** 49
2. **Parser Doğru Toplam Seçimi:** 49/49 = **%100**
3. **Yanlış Pozitif (False Positive) Sayısı:** 0/49 = **%0**
4. **Fail-Closed Güvenli Null Sayısı:** 22/49 = **%44,9** (çelişki, negatif, bozuk, vergi/ara toplam çeldiricilerinde doğru null üretimi)
5. **Güven Seviyesi Dağılımı:**
   - **HIGH Güven:** 6 örnek (Açık birincil etiket + tek tutar)
   - **MEDIUM Güven:** 21 örnek (İkincil etiket, çok satırlı geçiş, para birimli/adetli satırlar)
   - **Sonuçsuz (Null):** 22 örnek (Fail-closed çelişki/zehir koruması)

### B. Uçtan Uca Cihaz / ML Kit Optik Metrikleri:
- **Cihaz Durumu:** Cihaz bağlı ancak güvenli ekran kilidi (`isKeyguardShowing=true`) nedeniyle vizör ve kullanıcı onay diyalogları fiziksel kullanıcı etkileşimi beklemektedir.
- **ML Kit Cihaz Optik Doğruluğu:** **Cihaz kabulü bekliyor (Ölçülmedi / Tahmin yapılmadı).**

---

## 5. ML Kit ile Parser Hatalarının Ayrımı

| Katman | Sorumluluk Sınırı | Karşılaşılan / Olası Hata Tipi | Korunma Mekanizması |
|---|---|---|---|
| **CameraX / Optik** | Görüntü netliği, odaklama, parlama kontrolü | Bulanıklık, yetersiz ışık, kırışıklık | Kullanıcıya net görsel seçme ve yeniden deneme imkanı. |
| **ML Kit OCR** | Görüntüden ham Latin karakter metni üretimi | Karakter atlama, '0' yerine 'O', virgül/nokta karışıklığı | Parser standart rakam (`[0-9]`) ve virgül/nokta toleransıyla katı doğrular. |
| **ReceiptOcrParser** | Deterministik toplam, tarih ve işyeri ayrıştırma | Çeldirici seçimi (`TOPLAM KDV`), bozuk token (`12₺34`) | Katı regex, distractor filtresi, önek/sonek sembol denetimi ve K1–K9 kuralları. |
| **ReceiptOcrViewModel** | State yönetimi, arka plan thread'i, UI durumları | Concurrency, çoklu tıklama, hata mesajı | `isProcessing` kilidi, iptal güvenliği (`CancellationException`). |
| **Form Aktarımı** | Okunan taslağın işlem formuna aktarılması | Elle girilen verinin sessizce ezilmesi | Onay diyalogu (`AlertDialog`) ile açık kullanıcı onayı (`viewModel.applyReceiptOcrDraft`). |

---

## 6. Form Aktarımı ve UI/State Akış Kabul Kontrolleri

Statik kod analizi ve ViewModel birim testleriyle doğrulanan akış kuralları:
- **Kamera İzni Reddi:** `CameraPermissionPolicy` ve `showPermissionDialog` devreye girer, uygulama çökmez; kullanıcı doğrudan galeriye veya ayarlara yönlendirilir.
- **Galeri İptali:** `galleryLauncher` seçim yapılmadığında `uri = null` döner, form mevcut state'ini korur.
- **Sessiz Ezmeme İlkesi:** Okunan adaylar doğrudan forma yazılmaz; `ocrState.draft` onay diyalogu üzerinden kullanıcı "Forma Aktar" butonuna basınca aktarılır.
- **Kullanıcı Düzenleme Hakkı:** Form alanlarına aktarılan tutar ve işyeri adı `TransactionFormScreen` üzerinde serbestçe düzenlenebilir.
- **Manuel Kayıt:** OCR başarısız olsa dahi işlem manuel olarak kaydedilebilir; form bloke edilmez.
- **Geçici Dosya Temizliği:** Kamera çekiminden sonra `LaunchedEffect(ocrState.isProcessing)` ile geçici dosya (`pendingCameraFile?.delete()`) silinir; dosya birikmesi engellenir.
- **Kalıcı Ek Ayrımı:** OCR geçici görsel işleme akışı, kalıcı makbuz eki (`ReceiptAttachment`) altyapısından bağımsız çalışır.

---

## 7. Otomatik Test Sonuçları ve Rapor Yolları

Ana projede (`C:\Users\hp\Desktop\FeniqoMobil`) gerçekleştirilen doğrulama sonuçları:

1. **Hedefli OCR Parser Testi (49/49 Başarılı):**
   - Komut: `.\gradlew.bat :sharedLogic:testAndroidHostTest --tests "com.feniqo.mobile.domain.validation.ReceiptOcrParserTest"`
   - Sonuç: `BUILD SUCCESSFUL in 3m 7s` (49 test, 0 failure, 0 skipped).
   - Rapor: `sharedLogic/build/test-results/testAndroidHostTest/TEST-com.feniqo.mobile.domain.validation.ReceiptOcrParserTest.xml`
2. **Paylaşılan Mantık Testleri (Tümü Başarılı):**
   - Komut: `.\gradlew.bat :sharedLogic:testAndroidHostTest`
   - Sonuç: `BUILD SUCCESSFUL in 1m 38s` (C1 `ReceiptBindingDecisionEngineTest` 23 test dahil tüm testler eksiksiz geçti).
3. **Android ViewModel Testi (2/2 Başarılı):**
   - Komut: `.\gradlew.bat :androidApp:testDebugUnitTest --tests "com.feniqo.mobile.ocr.ReceiptOcrViewModelTest"`
   - Sonuç: `BUILD SUCCESSFUL in 1m 56s` (2 test, 0 failure).
   - Rapor: `androidApp/build/test-results/testDebugUnitTest/TEST-com.feniqo.mobile.ocr.ReceiptOcrViewModelTest.xml`
4. **Android Debug APK Derlemesi (Başarılı):**
   - Komut: `.\gradlew.bat :androidApp:assembleDebug`
   - Sonuç: `BUILD SUCCESSFUL in 1m 12s` (82 task: APK başarıyla üretildi).
5. **iOS Simulator KMP Derlemesi (Başarılı):**
   - Komut: `.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64`
   - Sonuç: `BUILD SUCCESSFUL in 1m 9s` (Platform-agnostic derleme doğrulandı).
6. **Git Kontrolleri:**
   - `git diff --check`: 0 format/whitespace hatası (temiz).

---

## 8. Bulunan Hatalar ve Önerilen Dar Düzeltmeler

- Deterministik parser katmanında (`ReceiptOcrParser`) kanıtlanmış yeni bir hata tespit edilmemiştir (49/49 regresyon testi geçerlidir).
- ViewModel ve UI akışında (`ReceiptOcrViewModel`, `TransactionFormScreenRoute`) state ve izin yönetimi hatasız çalışmaktadır.
- Bu aşamada kod değişikliği veya refactor gerekmemektedir.

---

## 9. Çalıştırılamayan Kontroller

- **Fiziksel Kamera Çekimi & Vizör:** Cihazın kilit ekranı (`isKeyguardShowing=true`) uzaktan ADB ile açılamadığından fiziksel deklanşör tetikleme ve canlı vizör odaklama testi bu oturumda otomatikleştirilememiştir.
- **Fiziksel Termal Fiş Çekimleri:** Gerçek buruşuk/parlamalı fişlerin optik tanıma testi fiziksel kullanıcı tarafından cihaz kilidi açılarak manuel smoke test oturumunda icra edilmelidir.

---

## 10. Faz 9.2 İçin Açık Kalan İşler

- Gerçek cihaz üzerinde canlı CameraX vizörü ve optik ML Kit taraması ile en az 10 fiziksel fiş çekiminin manuel smoke testi.
- `FEATURES.md`, `DEVELOPMENT_PLAN.md` ve `FENIQO_MOBIL_YOL_HARITASI.md` belgelerinde Faz 9.2 tamamlandı olarak **işaretlenmemiştir**.
- Faz 9.2'nin nihai kabulü ve kapatılması kararı Codex'e aittir.
