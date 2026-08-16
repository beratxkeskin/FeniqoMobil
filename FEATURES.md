# FeniqoMobil Özellik Durumu

> Bu belge kullanıcı özelliklerinin durumunu özetler. Ayrıntılı adım geçmişi ve checkbox'lar için
> [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md) esas alınır.

## Durum açıklaması

| Durum | Anlamı |
|---|---|
| Tamamlandı | Kod, ilgili test ve platform derlemesi doğrulandı |
| Altyapı hazır | Domain/veri temeli var; kullanıcı akışı henüz tamamlanmadı |
| Devam ediyor | Aktif geliştirme aşaması |
| Planlandı | Onaylı kapsamda, henüz başlanmadı |
| V1 sonrası | Çekirdek V1'i geciktirmeyecek sonraki kapsam |

Bir domain modelinin, DTO'nun veya Room tablosunun bulunması özelliğin kullanıcıya hazır olduğu
anlamına gelmez. Özellik ancak ekran, iş akışı, hata durumları ve kabul testleri tamamlandığında
“Tamamlandı” sayılır.

## V1 özellik matrisi

| Özellik | Durum | Tamamlanan kısım | Kalan kullanıcı işi |
|---|---|---|---|
| Tema ve uygulama kabuğu | Tamamlandı | Material 3, açık/koyu/sistem, token'lar, temel bileşenler, bottom navigation kabuğu | Ürün ekranlarının bağlanması |
| Kimlik doğrulama altyapısı | Altyapı hazır | Supabase kayıt/giriş/yenileme/çıkış, domain hata eşleme, Android güvenli oturum adaptörü | Splash, giriş ve kayıt ekranları |
| Profil | Altyapı hazır | Domain, Room, DTO, RLS, başlangıç ve artımlı sync | Profil ayarları ekranı |
| Kategoriler | Altyapı hazır | Domain/use case, Room/DAO, offline mutation, DTO, RLS ve sync | Liste, seçim ve yönetim ekranları |
| Gelir/gider işlemleri | Altyapı hazır | Domain/use case, Room/DAO, outbox, koşullu RPC, conflict ve sync | Liste, ekleme, düzenleme, silme ve filtre UI'ı |
| Dashboard hesapları | Altyapı hazır | Özet ve MoneyScore domain/use case modelleri | Gerçek Room akışına bağlı dashboard ekranı |
| Offline-first okuma/yazma | Tamamlandı | Room SSOT, atomik entity + outbox, kapanıp açılma kalıcılığı | UI üzerinden uçtan uca kullanıcı kabulü |
| Senkronizasyon motoru | Tamamlandı | Initial pull, sıralı push, incremental pull, retry, cursor ve conflict | WorkManager ile arka plan planlama |
| Realtime | Tamamlandı | Sınırlı publication, invalidation, reconnect ve Room telafi sync'i | Kullanıcıya sync durumunun sunulması |
| Makbuz depolama altyapısı | Altyapı hazır | Private storage sözleşmesi, güvenli yol ve 6 MB sınırı | UI, görsel seçme/kamera ve staging bucket politikası |
| Senkronizasyon gözlemi | Planlandı | Repository overview sözleşmesi mevcut | Son sync, bekleyen işlem, offline göstergesi ve manuel sync |
| Arka plan senkronizasyonu | Tamamlandı | Hilt CoroutineWorker, BackgroundSyncScheduler, exponential backoff ve unique work | UI üzerinden uçtan uca kabul |

## Teknik temel

| Alan | Durum | Not |
|---|---|---|
| KMP proje omurgası | Tamamlandı | `androidApp`, `sharedLogic`, `sharedUI`, `iosApp` |
| Domain modelleri | Tamamlandı | Çekirdek ve ikinci dalga modeller platform bağımsız |
| Repository sözleşmeleri | Tamamlandı | Flow tabanlı okuma ve tanımlı sonuç tipleri |
| Room şeması | Tamamlandı | Şema v3, DAO, indeks, ilişki ve export edilen JSON |
| Android DB güvenliği | Tamamlandı | SQLCipher + Android Keystore zarfı |
| Supabase V1 staging | Tamamlandı | Migration, RLS ve RPC kabul testleri geçti |
| Production migration | Başlatılmadı | Ayrı güvenlik kapısı ve açık onay gerekir |
| iOS ortak kod derlemesi | Tamamlandı | Simulator ARM64 doğrulandı |
| iOS güvenli saklama ve DB şifreleme | Planlandı | 10.4 kapsamında |

## V1 kabul senaryoları

V1 tamamlanmadan önce aşağıdaki kullanıcı senaryoları geçmelidir:

1. Yeni kullanıcı hesap oluşturup oturum açabilir.
2. Kullanıcı çevrimdışıyken kategori ve işlem oluşturabilir.
3. Uygulama kapanıp açıldığında yerel kayıt görünür kalır.
4. Ağ geldiğinde outbox kaybı veya tekrar kayıt oluşturmadan gönderilir.
5. İkinci cihazdaki değişiklik Realtime sonrası Room'a çekilir.
6. Soft-delete diğer cihazda kaydı görünümden kaldırır.
7. Aynı kayıt iki cihazda değiştirilirse iki kopya korunur ve kullanıcı seçim yapar.
8. Kullanıcı başka kullanıcının profil, kategori veya işlemini okuyamaz/değiştiremez.
9. Para toplamları küçük birim `Long` üzerinden doğru hesaplanır.
10. Kullanıcı son senkronizasyonu, bekleyen operasyonu ve çevrimdışı durumunu görebilir.

## V1 sonrası özellikler

Öncelik sırası ürün ihtiyacına göre yeniden değerlendirilebilir; mimari hazırlık uygulama taahhüdü değildir.

### Finans genişletmeleri

- kategori bazlı aylık bütçeler ve uyarılar;
- tekrarlayan işlemler ve abonelikler;
- hedefler ve borç/alacak yönetimi;
- varlıklar, net değer ve piyasa fiyatları;
- gelişmiş raporlar ve harcama analizi.

### Ortak kullanım

- workspace oluşturma, davet ve üyelik;
- `OWNER`, `EDITOR`, `VIEWER` yetki matrisi;
- ortak işlem/bütçe görünürlüğü ve ödeme dağılımı.

### Cihaz ve veri özellikleri

- biyometrik uygulama kilidi;
- makbuz OCR;
- bildirimler;
- CSV/JSON dışa ve içe aktarma;
- tamamlanmış SwiftUI iOS istemcisi.

## Kapsam değişikliği kuralı

Yeni özellik eklenmeden önce:

1. [PRODUCT.md](PRODUCT.md) kapsamına etkisi yazılır.
2. Mimari/veri değişikliği varsa ilgili sözleşme güncellenir.
3. Bu dosyada durum ve kabul kriteri eklenir.
4. [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) içinde sırası belirlenir.
5. Çekirdek V1'i geciktiriyorsa proje sahibinden açık öncelik onayı alınır.
