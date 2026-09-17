# Görev 9.2-A Kabul Raporu: Makbuz OCR Toplam/Tutar Doğruluğu (Revize Tur 3 — Dar Düzeltme)

Bu rapor, FeniqoMobil projesi kapsamında deterministik makbuz OCR ayrıştırıcısının (`ReceiptOcrParser`) toplam ve tutar doğruluğunu artırmak, yanlış/belirsiz sonuçları güvenli ele almak (fail-closed `total = null`) ve davranışı kapsamlı sentetik testlerle kanıtlamak amacıyla yürütülen **Görev 9.2-A** çalışmasının teknik denetim ve kabul raporudur.

---

## 1. Çalışma Alanı İzolasyonu ve Başlangıç Manifesti

Ortak proje çalışma alanını (`c:\Users\hp\Desktop\FeniqoMobil`) korumak ve paralel yürütülen diğer geliştirme adımlarının değişiklikleriyle çakışmamak amacıyla bağımsız bir çalışma kopyası kullanılmıştır:

- **Kaynak Proje Dizini:** `c:\Users\hp\Desktop\FeniqoMobil`
- **Bağımsız Çalışma Kopyası:** `c:\Users\hp\Desktop\FeniqoMobil_OcrWork`
- **Kaynak HEAD Commit:** `a5b5982 ui improvements`
- **Kopyalama Yöntemi:** Robocopy (`/E /XD .git build .gradle .idea /XF *.hprof *.lock`) — 985 dosya eksiksiz aktarıldı.
- **Güvenlik ve Çakışma İlkesi:** Ana repo dizininde hiçbir Gradle komutu çalıştırılmamış, dosya değiştirilmemiş, commit/merge yapılmamıştır.

### Başlangıç Dosyaları SHA-256 Doğrulama Manifesti:
| Dosya | Eşleşme Durumu | Başlangıç SHA-256 Özeti |
|---|---|---|
| `sharedLogic/.../ReceiptOcrParser.kt` | **Eşleşti (True)** | `7F83375CE8DCCF1E57576F4F6A82963A2B9C5723E595C7DF18868E8428D8AB26` |
| `sharedLogic/.../ReceiptOcr.kt` | **Eşleşti (True)** | `12067A3DCE7E6E35B8FBB9D48E0CBBED448201F4D059C4FE43EC25BEAB0B870D` |
| `sharedLogic/.../ReceiptOcrParserTest.kt` | **Eşleşti (True)** | `99DB9E25BDE86D312B3E7C24E0C7B83A011D7005F1FAF922E171E2F91B08CABF` |
| `androidApp/.../ReceiptOcrService.kt` | **Eşleşti (True)** | `F6B1FFC1092B24441B7BE0A485D6247C96C3E49869AB48186CC75CF5608676ED` |
| `androidApp/.../MlKitReceiptOcrService.kt` | **Eşleşti (True)** | `83F6439D221CC7B240B7794A8B9F78ADC3875B6F3B0B454CECFF0250FB152A09` |
| `androidApp/.../ReceiptOcrViewModel.kt` | **Eşleşti (True)** | `75B3A5781F3664CF33D065C58F1A8747E8E5220B509E7C561473301CB2C626E7` |
| `androidApp/.../ReceiptOcrViewModelTest.kt` | **Eşleşti (True)** | `A313E3409E8E0FD60E419088AF17C6E066B551DE148FD4EEA933033CC28177E6` |

---

## 2. Fixture Kaynağı Bildirimi

> **Açık Beyan:** Bu görevde kullanılan tüm test fixture'ları **tamamen sentetik** olarak tasarlanmış ve üretilmiştir. Hiçbir gerçek kullanıcı verisi, gerçek kurum bilgisi, gerçek kart numarası veya kişisel veri içermez.

---

## 3. Codex Tur 3 (Dar Düzeltme) Bulguları ve Çözümleri

### Madde 1: Ayrı Satırdaki Eksi İşareti Atlanması (Çok Satırlı Ayırıcı Hatası)
- **Sorun:** `isAllowedSeparatorLine`, ASCII ve Unicode eksi işaretlerini (`-`, `\u2212`, `\u2013`, `\u2014`) silerek bunları atlanabilir ayırıcı satır kabul ediyordu. Bu nedenle `TOPLAM\n-\n150,00` durumunda aradaki eksi satırı atlanıp pozitif 150,00 seçilebiliyordu.
- **Düzeltme:** `isAllowedSeparatorLine` içinden tüm eksi/tire karakteri temizlemeleri kaldırıldı. Eksi veya tire içeren hiçbir satır atlanabilir ayırıcı sayılmadı. Tek eksi işareti veya eksi işaretlerinden oluşan satırlarda arama durur ve belirsizlik durumunda `total = null` döner. İzin verilen `*`, `:`, `_`, `=` ve para birimi sembollerinin ayırıcı davranışı korundu.
- **Önce/Sonra:**
  - `TOPLAM\n-\n150,00`: Önce pozitif `150,00` seçiliyordu → Şimdi `total = null`.
  - `TOPLAM\n−\n150,00`: Önce pozitif `150,00` seçiliyordu → Şimdi `total = null`.
  - `TOPLAM\n–\n150,00`: Önce pozitif `150,00` seçiliyordu → Şimdi `total = null`.
  - `TOPLAM\n—\n150,00`: Önce pozitif `150,00` seçiliyordu → Şimdi `total = null`.

### Madde 2: Para Sembollerinin Sayı İçinden Silinmesi ve Rakamların Birleşmesi
- **Sorun:** `clean.replace("₺", "").replace("€", "").replace("$", "")` işlemi token'ın herhangi bir yerindeki sembolü siliyordu. Bu nedenle `TOPLAM 12₺34` gibi bozuk bir token, `1234` tam sayısına dönüşüyordu.
- **Düzeltme:** `stripCurrencyAffixes` fonksiyonu eklendi; para sembolleri (`₺`, `€`, `$`, `TL`, `TRY`, `EUR`, `USD`) yalnızca açıkça tanımlanan önek (prefix) veya sonek (postfix) konumlarında kabul edildi. Sayı içindeki semboller silinmedi; token bozuk kalarak `parseStrictMinorUnits` tarafından reddedildi ve `hasPoison = true` ile zehirli sayı sayıldı. Zehirli sayı nedeniyle alt satırlardaki tutarlara geçiş engellendi.
- **Önce/Sonra:**
  - `TOPLAM 12₺34`: Önce `123400` kuruş üretiliyordu → Şimdi `total = null`.
  - `TOPLAM 12€34`: Önce `123400` kuruş üretiliyordu → Şimdi `total = null`.
  - `TOPLAM 12$34`: Önce `123400` kuruş üretiliyordu → Şimdi `total = null`.
  - `TOPLAM ₺150,00` (geçerli önek): `15000` kuruş (`MEDIUM`).
  - `TOPLAM 150,00₺` (geçerli sonek): `15000` kuruş (`MEDIUM`).
  - `TOPLAM 12₺34\n150,00`: Önce `150,00`'a atlanıyordu → Şimdi `total = null`.

---

## 4. Test Raporları ve Doğrulama Sonuçları

Tüm testler ve derlemeler `c:\Users\hp\Desktop\FeniqoMobil_OcrWork` dizininde başarıyla tamamlanmıştır:

### 1. Hedefli OCR Testleri (49/49 Başarılı):
```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest --tests "com.feniqo.mobile.domain.validation.ReceiptOcrParserTest"
```
- **Sonuç:** `BUILD SUCCESSFUL in 3m 7s` (49 test, 0 failure, 0 skipped).
- **XML Raporu:** `c:\Users\hp\Desktop\FeniqoMobil_OcrWork\sharedLogic\build\test-results\testAndroidHostTest\TEST-com.feniqo.mobile.domain.validation.ReceiptOcrParserTest.xml`
- **HTML Raporu:** `c:\Users\hp\Desktop\FeniqoMobil_OcrWork\sharedLogic\build\reports\tests\testAndroidHostTest\classes\com.feniqo.mobile.domain.validation.ReceiptOcrParserTest.html`

### 2. Tüm Paylaşılan Mantık Testleri (Tümü Başarılı):
```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
```
- **Sonuç:** `BUILD SUCCESSFUL in 1m 38s` (Tüm sharedLogic testleri eksiksiz geçti).

### 3. Android Debug APK Derlemesi (Başarılı):
```powershell
.\gradlew.bat :androidApp:assembleDebug
```
- **Sonuç:** `BUILD SUCCESSFUL in 2m 55s` (82 task: derleme ve APK paketleme sorunsuz).

### 4. iOS Simulator KMP Derlemesi (Başarılı):
```powershell
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```
- **Sonuç:** `BUILD SUCCESSFUL in 2m 40s` (Kotlin Multiplatform iOS derlemesi sorunsuz).

---

## 5. Değişen Dosyalar ve İzolasyon Beyanı

Yalnızca bağımsız çalışma kopyasındaki dosyalar değiştirilmiştir:
1. `sharedLogic/src/commonMain/kotlin/com/feniqo/mobile/domain/validation/ReceiptOcrParser.kt`
2. `sharedLogic/src/commonTest/kotlin/com/feniqo/mobile/domain/validation/ReceiptOcrParserTest.kt`
3. `docs/OCR_9_2_A_KABUL.md`

- **Ana Proje Güvenlik Durumu:** `c:\Users\hp\Desktop\FeniqoMobil` dizinine hiçbir aktarım, kopyalama, derleme, commit veya merge işlemi yapılmamıştır.

---

## 6. Görev ve Kabul Sınırları

- **Sentetik Doğrulama:** OCR parser ayrıştırma mantığı, çeldirici filtrelemesi ve fail-closed belirsizlik kuralları (K1–K9) 49 adet sentetik metin fixture'ı ile doğrulanmıştır.
- **Açık Cihaz ve Donanım Kabulü:** Bu çalışma CameraX optiği, odaklama kalitesi, düşük ışık/parlama koşulları, termal kağıt deformasyonları ve ML Kit Latin modelinin ham metin tanıma başarısını kanıtlamaz. Gerçek makbuz OCR/ML Kit doğruluğu ve cihaz kabulü açık kalmaya devam etmektedir.
- **Faz Durumu:** Görev 9.2-A deterministik parser ayrıştırma dilimini kapsar; **Faz 9.2 tamamlandı değildir** ve ilgili plan/yol haritası belgelerinde Faz 9.2 kapatılmamıştır.
