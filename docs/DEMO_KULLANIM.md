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

Demo APK'sının INTERNET izni yoktur. Supabase yapılandırması gerçek proje yerine `.invalid`
alanını kullanır; Auth ve Sync yerel demo adaptörleridir. Realtime ve otomatik başlangıç işleri
çalışmaz; outbox gönderilmez. Çevrimdışı/bekleyen kayıt göstergesi bu nedenle beklenir.
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
