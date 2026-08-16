# FeniqoMobil Ürün Tanımı

> Bu belge ürün amacının ve kapsam sınırının ana kaynağıdır. Teknik uygulama kararları için
> [ARCHITECTURE.md](ARCHITECTURE.md), veri sözleşmesi için [DATABASE.md](DATABASE.md) okunmalıdır.

## 1. Ürün vizyonu

FeniqoMobil; bireylerin gelir, gider ve finansal durumlarını güvenli, anlaşılır ve internet
bağlantısından bağımsız biçimde yönetmesini sağlayan bir mobil finans uygulamasıdır. Mevcut
Feniqo web uygulaması ürün ve veri referansıdır; mobil uygulama web kodunun bire bir kopyası
değil, mobil kullanım ve offline-first çalışma için yeniden tasarlanmış sürümüdür.

Ana hedefler:

- Kullanıcı internet yokken finansal kayıtlarını görmeye ve düzenlemeye devam edebilsin.
- Yerel değişiklikler bağlantı geldiğinde güvenli ve tekrarlanabilir biçimde senkronize olsun.
- Para hesapları kayan nokta hatası üretmesin.
- Kullanıcının finansal verileri başka kullanıcılar tarafından okunamasın veya değiştirilemesin.
- Android V1 hızlı geliştirilsin; ortak iş mantığı daha sonra iOS SwiftUI istemcisince kullanılabilsin.

## 2. Hedef kullanıcı

V1 öncelikle kişisel finansını tek başına takip eden kullanıcıyı hedefler. Kullanıcı:

- e-posta ve şifre ile hesap oluşturur veya giriş yapar;
- gelir ve gider kategorilerini yönetir;
- gelir/gider işlemi ekler, düzenler ve siler;
- aylık özetini ve son işlemlerini görür;
- bağlantı olmadığında çalışmaya devam eder;
- başka cihazdaki değişiklikleri Realtime sinyali sonrası yerel veritabanında görür;
- çakışma oluştuğunda yerel veya uzak kopyayı seçebilir.

## 3. V1 kapsamı

V1 için ürün kapsamı:

1. E-posta/şifre tabanlı kimlik doğrulama ve oturum yönetimi.
2. Kişisel profil ve tercihlerin temeli.
3. Kişisel gelir/gider kategorileri.
4. Gelir/gider işlemleri; tarih, açıklama, ödeme yöntemi, taksit metadata'sı ve private makbuz yolu.
5. Aylık gelir, gider, net bakiye ve son işlemlerden oluşan dashboard.
6. Room tabanlı offline-first okuma ve yerel yazma.
7. Outbox tabanlı Supabase push/pull senkronizasyonu.
8. Soft-delete, sürüm denetimi ve kullanıcı çözümlü çakışma.
9. `profiles`, `categories` ve `transactions` için cihazlar arası Realtime invalidation.
10. Son senkronizasyon, bekleyen işlem ve çevrimdışı durumunun kullanıcıya gösterilmesi.

V1 başarı tanımı: kullanıcı Android uygulamasında çevrimdışıyken işlem ekleyebilmeli, uygulamayı
kapatıp açtığında kaydı görmeye devam etmeli ve ağ geri geldiğinde kayıt güvenli biçimde
Supabase ile senkronize olmalıdır.

## 4. V1 dışında kalanlar

Aşağıdakiler çekirdek V1'i geciktirmez:

- ortak çalışma alanı, davet ve `OWNER`/`EDITOR`/`VIEWER` rolleri;
- gelişmiş bütçe ekranları ve bütçe uyarıları;
- hedef, borç/alacak, abonelik ve tekrarlayan işlem otomasyonu;
- varlık, piyasa fiyatı, net değer ve gelişmiş raporlar;
- makbuz OCR, CameraX ve ML Kit;
- biyometrik uygulama kilidi;
- CSV/JSON içe ve dışa aktarma;
- son kullanıcıya açık demo modu;
- production Supabase migration'ı;
- tamamlanmış iOS ürün arayüzü.

Bu alanların domain modelleri veya altyapı hazırlıkları kodda bulunabilir. Bir modelin varlığı,
özelliğin kullanıcıya hazır olduğu anlamına gelmez; güncel durum [FEATURES.md](FEATURES.md)
dosyasında tutulur.

## 5. Ürün ilkeleri

- **Offline-first:** Ağ, temel kullanım için ön koşul değildir.
- **Yerel güven:** UI her zaman Room'dan okur; uzak payload ekrana doğrudan verilmez.
- **Açıklanabilirlik:** Finansal hesaplar ve çakışma kararları kullanıcıya anlaşılır sunulur.
- **Güvenli varsayılanlar:** Private veri, RLS ve sahiplik kontrolleri gevşetilmez.
- **Doğru para hesabı:** Tutarlar en küçük para biriminde `Long` olarak tutulur.
- **Kademeli kapsam:** Önce kişisel finans çekirdeği tamamlanır, sonra gelişmiş modüllere geçilir.
- **Türkçe önceliği:** İlk ürün dili Türkçedir; metin yapısı İngilizce desteğine uygun tutulur.
- **Erişilebilir tasarım:** Kontrast, okunabilirlik ve ekran okuyucu uyumu ürün kalitesinin parçasıdır.

## 6. Platform stratejisi

- Android ilk üretim platformudur ve Jetpack Compose kullanır.
- iOS uygulama kabuğu SwiftUI kullanır; ortak domain, veri ve senkronizasyon mantığını
  `sharedLogic` üzerinden tüketir.
- Android'e özel servisler iOS mimarisini belirlemez; platform sınırları adaptörlerle korunur.

## 7. Referanslar

- Ayrıntılı web ekranı ve kullanıcı akışları: [docs/WEB_REFERANS_ENVANTERI.md](docs/WEB_REFERANS_ENVANTERI.md)
- Güncel özellik durumu: [FEATURES.md](FEATURES.md)
- Uygulama sırası: [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)
- Ayrıntılı tarihsel yol haritası: [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)

