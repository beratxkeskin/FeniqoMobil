# FeniqoMobil Özellik Durumu

> 2026-09-15 — İşlemler 01–18 tasarım uyarlamasının mevcut mimarinin desteklediği kapsamı tamamlandı ve doğrulandı: [İşlemler kabul kaydı](docs/ISLEMLER_TASARIM_KABUL.md). Yeni veri/senkronizasyon sözleşmesi gerektiren kalıcı makbuz eki ve tutarla ortak dağıtım ile oturumlu cihaz kabulü açık tutuldu.

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
| Tema ve uygulama kabuğu | Tamamlandı | Material 3, açık/koyu/sistem, Feniqo Emerald/obsidyen yüzey token'ları, 12/16/24 dp radius, 4–32 dp spacing, 48/56 dp dokunma hedefleri, type-safe Navigation Compose, bağımsız Auth/Main NavHost, Ana Sayfa/İşlemler/+/Bütçe/Daha Fazla alt navigasyonu, Financial Hub ve avatar tabanlı Profil girişi | Transfer hızlı eylemi, Reports, Kişisel Bilgiler, Hesap, Bildirimler ve Tercihler gerçek özellikleri tamamlandığında mevcut pasif kayıtları aktifleştirilecek |
| Kimlik doğrulama | Tamamlandı | Onaylı tasarımlara (Pano A, Pano B, Pano C) sadık tam akış (Hoş Geldiniz, Giriş Yap, Kayıt Ol, E-posta Doğrulama, Parolamı Unuttum, E-posta Gönderildi, Parolayı Sıfırla, Parola Güncellendi); Supabase kayıt/giriş/yenileme/çıkış; güvenli deep link (`feniqo://auth/callback`) ve SDK üzerinden doğrulanmış recovery oturumu (`AuthRecoveryState`, PKCE/token import, `PasswordRecovery` izole oturum ve navigasyon dalı); tipli hata ayrımı (rate limit, ağ hatası, geçersiz/süresi dolmuş bağlantı, yetkisiz kurtarma) | Biyometrik giriş (kapsam dışı / sonraki fazlar) |
| Profil ve ayarlar | Devam ediyor | Room SSOT profil/aktif alan özeti, gerçek sync durumu içgörüsü, sıcak-lüks bölüm kartları, tema/kilit/veri yönetimi yönlendirmeleri, build sürümü, güvenli çıkış; gerçek Supabase parola değiştirme akışı (mevcut parola yeniden doğrulanıyor, başarı yalnız uzak güncellemeden sonra gösteriliyor) | Parola değiştirme Android emülatör/staging manuel kabulü; kişisel bilgi düzenleme, fotoğraf, premium, banka/entegrasyon/bildirim/gizlilik/yardım kapsam dışı veya Yakında |
| Kategoriler | Devam ediyor | Sıcak-lüks analiz ve yönetim ekranı (Kategoriler), Room SSOT, basis points tamsayı grafiği, trend rozeti, dönem ve filtre bazlı işlem navigasyonu, 3 noktalı özel kategori yönetimi, fail-closed çoklu para birimi ve taşma güvenliği | Android emülatör görsel ve akış kabulü; uzak seed sözleşmesini yeni kanonik UUID'lerle eşleme, sistem kategorisini kullanıcı tercihine göre gizleme kontrolü ve ayrı sistem hareketi türleri |
| Merchant/marka tanıma | Altyapı hazır | Saf KMP merchant/alias türü ve kapsamı/negatif alias sözleşmeleri, kişisel→workspace→banka doğrulama önceliği, onaylı güven matrisi, çakışma eşiği, işlem sınıfı filtresi ve başlangıç kataloğu testleri | Transaction/Room bağlantısı, kullanıcı düzeltme kalıcılığı, UI fallback ve opsiyonel logo adaptörü |
| Gelir/gider işlemleri | Devam ediyor | Liste ve yenilenen filtreleme; sıcak-lüks Hızlı Ekle (+ modal sheet); sıcak-lüks işlem formu (büyük tutar, zorunlu 100 kar. işlem adı, kategori, ödeme yöntemi, tarih, isteğe bağlı not/taksit/makbuz/split akordiyonu); Room SSOT başarı ekranı ve geçersiz ID güvenliği; bütçe/kategori filtreli navigasyon; çakışma ekranı izolasyonu; taksit/offline Room SSOT kalıcılığı; hedefli testler ve debug APK derlemesi başarılı | Android emülatör manuel smoke kabulü (uygulandı, manuel kabul bekliyor); kalıcı makbuz depolama dosya yaşam döngüsü ve tutarla ortak dağıtım |
| Dashboard ve MoneyScore | Tamamlandı | Stateless DashboardScreen, DashboardViewModel, dinamik ay Room Flow SSOT, aktif workspace para birimine göre aylık gelir/gider/bakiye/tasarruf kartları, farklı para birimli kayıtlar için açıklamalı dışlama, en yüksek harcama kategorisi, son işlemler, navigasyonlar, geçici MoneyScore kartı ve ön değerlendirme şeffaflığı; gerçek veriye dayanmayan sabit trend/grafik kaldırıldı | Gerçek önceki dönem trendi ancak açıklanabilir dönem karşılaştırma motoru tamamlandığında eklenecek |
| Offline-first okuma/yazma | Tamamlandı | Room SSOT, atomik entity + outbox, kapanıp açılma kalıcılığı | UI üzerinden uçtan uca kullanıcı kabulü |
| Senkronizasyon motoru | Tamamlandı | Initial pull, sıralı push, incremental pull, retry, cursor ve conflict | WorkManager ile arka plan planlama |
| Realtime | Tamamlandı | Sınırlı publication, invalidation, reconnect ve Room telafi sync'i | Kullanıcıya sync durumunun sunulması |
| Makbuz depolama altyapısı | Altyapı hazır | Private storage sözleşmesi, güvenli yol ve 6 MB sınırı | UI, görsel seçme/kamera ve staging bucket politikası |
| Senkronizasyon gözlemi | Tamamlandı | SyncOverview Flow, ağ gözlemcisi, ViewModel, SyncStatusIndicator ve manuel sync | V1 ekranlarıyla son kabul testleri |
| Arka plan senkronizasyonu | Tamamlandı | Hilt CoroutineWorker, BackgroundSyncScheduler, exponential backoff ve unique work | UI üzerinden uçtan uca kabul |
| Bütçeler | Tamamlandı | Ay bazlı bütçe listesi, dinamik ay gezinimi, reaktif harcama/ilerleme takibi, %80 uyarı ve %100 aşım gösterimi, harcama kategorisiyle bütçe ekleme, ID tabanlı Room SSOT form düzenlemesi, onaylı silme ve dinamik ay seçimli önceki aydan kopyalama akışları (Android emülatör manuel smoke kabulü yapıldı) | Workspace bütçeleri (Faz 8.4) |
| Tekrarlayan işlemler | Tamamlandı | Vade takvim hesaplayıcı, deterministik aday planlayıcı, Room atomik occurrence ve V2 outbox/ACK/sync/pull, WorkManager 24h periyodik işi, saf kural komutları ve doğrulama sözleşmesi, Staging V2 SQL migration, 38 senaryolu sözleşme kabulü, kural listesi ve ekleme/düzenleme/duraklatma/silme MVI Compose ekranları (Android emülatör manuel smoke kabulü yapıldı) | — |
| Abonelikler | Devam ediyor | Yaşam döngüsü (`ACTIVE`, `PAUSED`, `CANCELLED`, `TRIAL`, `EXPIRED`), atomik fiyat geçmişi ve artış rozeti, atomik ödeme olayları ve gerçekleşen dönem harcaması/trendi, saf analitik hesaplayıcı, çoklu para birimi izolasyonu, abonelik bazlı hatırlatıcı ve deneme süresi uyarısı, sıcak-lüks Compose ekranı (özet kartı, gerçekleşen dönem, trend, yaklaşan/gecikmiş, 7 filtre çipi, semantik kategori ikonları, içgörü kartları, yeni ekleme kartı); Room v16; hedefli testler ve debug APK doğrulandı | Android emülatör manuel smoke kabulü (uygulandı, manuel kabul bekliyor) |
| Hedefler ve borçlar | Tamamlandı | Goals (birikim CRUD, katkı ekleme/çıkarma, ilerleme/tahmini süre), Debts & Receivables (borç/alacak CRUD, ödeme/tahsilat geçmişi, fail-closed reaktif bakiye hesabı, sıcak-lüks 3'lü özet kartları, yaklaşan vadeler, borç/alacak gruplaması, açıklanabilir içgörü kartı), Borç snowball planlayıcısı ve ekranı, Room v9/v10/v11 tabloları, V2 outbox/ACK/pull/conflict sync, Staging 15/15 migration, SQL sözleşme testi (0 kalıntı) ve Android emülatör manuel smoke kabulü | Workspace borç dağılımı (Faz 8.4) |
| Varlıklar | Tamamlandı | Kişisel Asset CRUD, Room v13 SSOT, atomik V2 outbox/ACK, initial/incremental pull ve conflict koruması, owner RLS/RPC, liste/form UI, para birimi bazlı reaktif net değer özeti, hedefli testler ve Android manuel smoke kabulü | Güvenli piyasa fiyat servisi |
| Ortak çalışma alanları (Workspaces) | Devam ediyor | Saf domain modelleri/komutları (`CreateWorkspaceCommand`, `UpdateWorkspaceCommand`, `WorkspaceInvitation`), rol yetki matrisi (`WorkspacePermissionPolicy`), fail-closed ön kontroller (`WorkspaceValidationRules`), Room v9→v10 şeması, `workspaces` genişletme/backfill ve `workspace_invitations` salt-okunur/pull cache DAO altyapısı (`WorkspaceDao.observeInvitations`, `upsertInvitation`) | Room LocalMutationDao/V2 outbox, sync motoru, remote writer, Supabase SQL/RLS/RPC, üye listesi ve workspace yönetim MVI Compose ekranları |

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
- tekrarlayan işlemler ve abonelikler (Abonelik listesi, analitik motoru, sıcak-lüks şablonlu ekleme/düzenleme formu ve 6 aylık harcama grafiği/yönetimli detay ekranı tamamlandı);
- hedefler ve borç/alacak yönetimi;
- varlıklar, net değer ve piyasa fiyatları;
- gelişmiş raporlar ve harcama analizi.

### Ortak kullanım

- workspace oluşturma, davet ve üyelik;
- `OWNER`, `EDITOR`, `VIEWER` yetki matrisi;
- ortak işlem/bütçe görünürlüğü ve ödeme dağılımı.

### Cihaz ve veri özellikleri

- Android biyometri veya cihaz PIN/desen/parolasıyla uygulama kilidi;
- makbuz OCR (temel CameraX/galeri ve kullanıcı onaylı akış var; gerçek makbuzlarda toplam/fiyat doğruluğu iyileştirmesi ertelendi);
- bildirimler;
- CSV işlem dışa aktarma ve sürümlü JSON v1 yedek içe aktarma (doğrulanan, atomik ve kişisel kapsamlı) tamamlandı; JSON yedek dışa aktarma devam edecek;
- hassas uygulama verileri için üretim loglaması ve ham exception mesajı denetimi tamamlandı; kalıcı hata alanları yalnız güvenli kod taşır;
- tamamlanmış SwiftUI iOS istemcisi.

## Kapsam değişikliği kuralı

Yeni özellik eklenmeden önce:

1. [PRODUCT.md](PRODUCT.md) kapsamına etkisi yazılır.
2. Mimari/veri değişikliği varsa ilgili sözleşme güncellenir.
3. Bu dosyada durum ve kabul kriteri eklenir.
4. [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) içinde sırası belirlenir.
5. Çekirdek V1'i geciktiriyorsa proje sahibinden açık öncelik onayı alınır.
