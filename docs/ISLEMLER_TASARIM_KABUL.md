# İşlemler tasarım uyarlaması — kabul kaydı

15 Eylül 2026. Referans: kullanıcı tarafından onaylanan yeşil–grafit 01–18 panoları.

## Uygulanan dilim

- Liste: eşit genişlikte gelir/gider/işlem özeti, Dönem neti, sürekli grafit arama, kategori semantik ikonları, kaydırılabilir kontroller ve gruplu liste.
- Dönem özeti arama/tür/kategori/ödeme filtresinden bağımsız olarak seçili zaman aralığından hesaplanır. Workspace para birimi dışındaki kayıtlar açıklamayla dışlanır.
- Filtreler taslak seçimleri uygulamaya kadar korur; iptal değişiklik yapmaz. Tarih aralığı takvim veya doğrulanan tarih alanlarıyla seçilir; sıralama ve temizleme vardır.
- Listeden detay, detaydan düzenleme ve mevcut soft-delete onayı. Gerçek Room sync durumu gösterilir.
- Kategori arama/seçim görünümü ve mevcut yeni kategori bağlantısı; domain ödeme seçenekleri için uygulama/iptal destekli seçim görünümü.
- Düzenlemede mevcut dirty tracking ve Vazgeç; gelecekteki günler tarih seçicisinde kapalıdır.
- Yerel kayıt başarısı ile sync durumu ayrı gösterilir. OCR bilgi aktarımı artık kalıcı makbuz varmış gibi işaretlenmez. Mevcut makbuz yolunun kaldırılması kayda yansır.
- Çakışma: repository Room snapshot'larını domain değerlerine çevirir; tutar, işlem adı, tarih, ödeme, not, taksit, makbuz ve katılımcı sayısı karşılaştırılır. Açık KEEP_LOCAL/KEEP_REMOTE seçimi mevcut çözüm motorunu çağırır; okunamayan snapshot seçimleri kapatır.
- Mevcut hata/boş/yükleniyor, uygulama kabuğunun çevrimdışı göstergesi, hızlı ekle, taksit üretimi ve eşit ortak paylaşım akışları korunur.
- Ayrıntılar tam ekran ve taslaklıdır; Vazgeç ana formu değiştirmez. Ortak harcama yalnız ortak çalışma alanında OWNER/EDITOR rolüne açılır.
- Gider/gelir formu merkezî tutar, kart biçimli alanlar ve katalogdan gelen kategori renk/ikonlarıyla referans görsel hiyerarşisine uyarlandı. Açık ve koyu tema render edildi.

## Açık kapsam ve somut eksikler

- [ ] Büyük yazı ve farklı küçük ekran cihazlarında oturumlu erişilebilirlik kabulü.
- [ ] Makbuz dosyası seçme/çekme, kalıcı ekleme ve önizleme. Mevcut bağlantı OCR'dır; private Storage veri kaynağı bulunsa da form→repository dosya yaşam döngüsü, yerel bekleyen dosya kuyruğu ve preview bağlantısı yoktur. Uzak bucket politikasına bu görevde dokunulmadı.
- [ ] Tutara göre ortak dağıtım. Mevcut domain yalnız payer + katılımcı listesiyle eşit paylaşımı saklar. Katılımcı tutarlarını kalıcı/senkronize eden sözleşme yoktur; sahte tutarlı dağıtım eklenmedi.
- [ ] Bağımsız kalıcı makbuz önizleme ve tutara göre ortak dağıtım ekranları (aşağıdaki veri sözleşmeleri eksik).
- [ ] Çakışmada kategori adı, kişi bazlı dağılım ve değişiklik zamanlarının tam karşılaştırması.
- [ ] Liste→detay→düzenle→kaydet, yeni gelir/gider, filtre, iptal, silme ve çevrimdışı akışlarının gerçek oturumla cihaz kabulü.

Taksit altyapısı yalnız metadata değildir: mevcut AddInstallmentGroupUseCase işlem grubu üretir. Çalışan özellik kaldırılmadı; metadata örneğindeki açıklama bu akışa kopyalanmadı.

## Doğrulama

Android debug APK; Transactions, TransactionForm, TransactionSuccess ve TransactionConflict ViewModel testleri; sharedUI/sharedLogic host testleri ve iOS Simulator ARM64 ortak kod derlemesi geçti. Çakışma snapshot ve açık çözüm seçimi testleri dahildir.

Pixel_8 emülatöründe gerçek Compose bileşenleriyle 4 cihaz testi geçti; açık/koyu liste, gider ekleme, kategori seçimi, düzenleme ve taslaklı ayrıntılar için 6 görüntü `artifacts/transactions/transaction-visual-tests/` altında üretildi ve incelendi. Bunlar sabit test fixture'larıyla render edildi; oturumlu uçtan uca kabul değildir. Oturum bulunmadığından uygulama kabuğundaki gerçek veriyle kaydetme/silme kabulü yapılmadı.

Commit/push yapılmadı. Staging/production migration, SQL veya veri değişikliği uygulanmadı.
