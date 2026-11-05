# Feniqo Demo

Geliştirme ve ekran inceleme için cihazda çalışan, kurgusal verilerle dolu ayrı Android uygulaması.
Paket kimliği `com.feniqo.mobile.demo`; normal Feniqo ile yan yana kurulabilir.
Hesap oluşturma, e-posta ve parola gerekmez. İlk açılışta **Deniz Demo** hesabı hazırlanır.

## Kullanım

1. `androidApp/build/outputs/apk/demo/androidApp-demo.apk` dosyasını kur ve **Feniqo Demo** uygulamasını aç.
2. Ana Sayfa, İşlemler, Kategoriler, Bütçe ve Daha Fazla altındaki modülleri gez.
3. Kayıt ekleyebilir, düzenleyebilir ve silebilirsin. Değişikliklerin uygulama kapanınca korunur.
4. Baştan başlamak için üstteki **Demo seçenekleri → Demoyu sıfırla → Sıfırla ve kapat** yolunu kullan.
   Uygulama kapanır; yeniden açıldığında o güne göre yeni örnek veriler oluşturulur.
5. Çıkış yaptıysan **Demo seçenekleri → Hesabı aç** ile aynı yerel hesaba dönebilirsin.

Sıfırlama yalnız demo paketinin tüm yerel verisini ve ayarlarını temizler. Normal Feniqo'nun
verilerine dokunmaz. Kurulum yarıda kesilirse eksik veri tamamlanmış sayılmaz; sıfırlama sunulur.

## Örnek senaryo

- İçinde bulunulan ay ve önceki beş ay: maaş, serbest çalışma, kira, market, yeme/içme,
  ulaşım, fatura, sağlık, eğitim, etkinlik; üç taksit ve ayrı EUR/USD işlemleri.
- Her ay beş kategori bütçesi; normal, limite yaklaşan ve dönem ilerledikçe limiti aşan örnekler.
- Üç hedef ve dokuz birikim hareketi; tamamlanmış hedef dahil.
- Üç borç/alacak ve ödeme geçmişi; kapanmış borç dahil.
- Beş abonelik: aktif, duraklatılmış ve deneme; 18 ödeme ve üç fiyat değişimi.
- Üç tekrarlayan işlem; beş varlık ve farklı para birimi örneği.
- Üç kurgusal üyeli ortak alan ve üç eşit paylaşımlı gider. Ortak Alanlar'dan alanı seçerek incelenir.

Tüm tutarlar kurgusaldır; canlı fiyat veya tavsiye değildir. Tarihler ilk kurulum/sıfırlama
gününe göre hesaplanır; sonraki açılışlar veriyi yeniden üretmez. Ay değişince güncel senaryoya
dönmek için sıfırla. Varlıklardaki banka/nakit kayıtları mevcut varlık modülünü kullanır.

## Sınırlar

Demo varyantı finansal veri, Auth, Sync ve Supabase açısından çevrimdışı kalır; Supabase
yapılandırması gerçek proje yerine `.invalid` alanını kullanır; Auth ve Sync yerel demo adaptörleridir.
Realtime ve otomatik başlangıç işleri çalışmaz; WorkManager outbox gönderilmez.
Çevrimdışı/bekleyen kayıt göstergesi bu nedenle beklenir.

Uzak logo ve ağ sözleşmesi: Demo manifestinde standart `android.permission.INTERNET` izni bulunur. Android platformunda `INTERNET` izninin kendisi host bazında bir allowlist uygulamaz ve işletim sistemi düzeyinde yalnızca Logo.dev trafiği garantisi verilmez. Uygulama düzeyindeki kanıtlanabilir sınırlar şunlardır:
- `BuildConfig.DEMO = true`.
- Supabase URL `https://demo.invalid` hedefine ayarlıdır.
- Auth ve Sync adaptörleri yereldir (`DemoAuthRepository`, `DemoSyncRepository`); bulut senkronizasyonu tetiklemez.
- Başlangıç işleri, WorkManager outbox ve Realtime demo kapılarıyla devre dışıdır.
- Uzak logo çözümleyicisi yalnızca `MerchantLogoDomainAllowlist` anahtarlarından Logo.dev HTTPS isteği üretir (`feniqo.remoteLogo.provider=logodev` ve `feniqo.logodev.usageAuthorized=true` yapılandırılmışsa).
- `TransactionMerchantCreateDecision.UseTransactionName` ve serbest metin adlar çözümleyiciye doğrulanmış logo anahtarı sağlamaz; yerel fallback kategori ikonuna düşer.

Bulut giriş/kayıt/parola, davet gönderme/katılma, cihazlar arası senkronizasyon ve canlı piyasa
verisi bu demo ile test edilemez. Henüz uygulamada olmayan özellikler demo tarafından eklenmez.
Abonelik ödemeleri mevcut ödeme geçmişini doldurur; mevcut ürün davranışı gereği ayrıca gelir/gider
işlemi oluşturmaz. Makbuz görüntüsü veya yapay sync conflict eklenmez.

## Geliştirici

```powershell
.\gradlew.bat :androidApp:assembleDemo
.\gradlew.bat :androidApp:testDemoUnitTest --tests "com.feniqo.mobile.demo.*"
```

Normal `assembleDebug` ve release paketleri demo oturumunu veya veri yükleyicisini çalıştırmaz.
Room şeması değişmez. Fixture yazıları repository/mapper üzerinden atomik entity + outbox
yolunu kullanır. Sıfırlama Android'in yalnız mevcut uygulamaya ait sandbox sıfırlama işlemidir;
normal kayıtlarda soft-delete ve sürüm kuralları korunur. Production/staging mutation veya migration yoktur.
