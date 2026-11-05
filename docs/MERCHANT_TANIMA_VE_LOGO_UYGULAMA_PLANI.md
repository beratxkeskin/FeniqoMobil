# Merchant Tanıma ve Logo Uygulama Planı

> Durum: Proje sahibi tarafından 2026-10-13 tarihinde onaylanan uygulama planıdır.
> Bu belge Faz 8.6 merchant/marka tanıma işinin ayrıntılı ana kaynağıdır. Ürün sınırı için
> [PRODUCT.md](../PRODUCT.md), mimari sınır için [ARCHITECTURE.md](../ARCHITECTURE.md), gerçek
> şema durumu için [DATABASE.md](../DATABASE.md) ve ilerleme checkbox'ları için
> [FENIQO_MOBIL_YOL_HARITASI.md](../FENIQO_MOBIL_YOL_HARITASI.md) geçerlidir.

## 1. Amaç

Kullanıcı işlem adını `Netflix`, `Netflix ücreti`, `Migros alışverişi` veya `Trendyol siparişi`
gibi yazdığında uygulama ilgili merchant'ı güvenli ve deterministik biçimde tanır. Yeterli güven
varsa işlem listesi, işlem detayı, dashboard son işlemler ve uygun diğer işlem yüzeyleri ortak bir
logo bileşeniyle merchant logosunu gösterir.

Bu özellik kategori tahmini değildir. Kategori ile merchant ayrı kalır; merchant bağlantısı
isteğe bağlıdır ve kullanıcının seçtiği kategoriyi sessizce değiştirmez.

## 2. Ürün davranışı ve kabul örnekleri

| Kullanıcı girdisi | Beklenen merchant | Beklenen davranış |
|---|---|---|
| `Netflix` | Netflix | Logo gösterilir. |
| `Netflix ücreti` | Netflix | `NETFLIX` güçlü marka alias'ı eşleşir; özgün metin korunur. |
| `Amazon Prime üyeliği` | Amazon Prime | Amazon alışverişten önce hizmete özel merchant seçilir. |
| `Amazon siparişi` | Amazon | Prime eşleşmez; Amazon alışveriş seçilir. |
| `Trendyol Yemek` | Trendyol Yemek | Genel Trendyol adayından önce marka+hizmet seçilir. |
| `Trendyol alışveriş` | Trendyol | Trendyol Yemek seçilmez. |
| `Apple.com/bill` | Apple Services | Doğrulanmış hizmet kalıbı eşleşir. |
| `Apple` | — | Fazla genel olduğu için tek başına otomatik eşleşmez. |
| `Market alışverişi` | — | Genel sözcük merchant alias'ı olamaz; kategori ikonu kullanılır. |
| İki adayın puan farkı `< 10` | — | Belirsiz sonuç; otomatik logo gösterilmez. |
| Kullanıcı merchant'ı değiştirdi | Kullanıcı seçimi | Kişisel doğrulama sistem tahmininden üstün ve kalıcıdır. |
| Kullanıcı `Merchant yok` dedi | — | Aynı kişisel doğrulama kapsamında sistem tekrar merchant dayatmaz. |

İşlem adı değiştirilirse yalnız sistem tarafından atanmış eşleşme yeniden hesaplanır. Kullanıcının
doğruladığı seçim sessizce değiştirilmez. Ham kullanıcı metni ve ileride içe aktarılabilecek ham
banka açıklaması birbirinden ayrılır ve hiçbir eşleştirme ikisini yerinde değiştirmez.

## 3. Değişmez güven ve mimari kuralları

- Tanıma ve puanlama saf KMP domain mantığıdır.
- UI, merchant kataloğunu, Room DAO'yu veya uzak DTO'yu doğrudan kullanmaz.
- Transaction ve merchant doğrulaması Room SSOT üzerinden repository sınırında sunulur.
- Yerel transaction değişikliği ile ilgili outbox kaydı aynı Room transaction'ında yazılır.
- Kullanıcı doğrulaması otomatik katalog tahmininden üstündür.
- Sistem eşleşmesi kategoriyi, tutarı, işlem tipini veya kullanıcı metnini değiştirmez.
- Ham işlem adı, banka açıklaması, not veya finansal payload üçüncü taraf logo servisine gönderilemez.
- Logo ağı başarısız olduğunda işlem listesi ve temel finans akışları çalışmaya devam eder.
- Kullanıcıya görünen yeni metinler aynı değişiklikte TR/EN Compose kataloglarına eklenir.
- Logo marka varlıkları rastgele web sayfalarından kopyalanmaz; resmi marka kiti, açık lisanslı
  kaynak veya kullanım izni doğrulanan kontrollü kaynak gerekir.
- Production Supabase'e migration veya veri mutation'ı bu planın hiçbir adımında otomatik olarak
  uygulanmaz. Staging için de proje kimliği doğrulanır ve proje sahibinden ayrıca açık onay alınır.

## 4. Tanıma girdileri ve öncelik

Tanıma motoru kaynakları birbirinden ayırır:

1. Kullanıcının transaction için açık merchant seçimi.
2. Kullanıcının daha önce doğruladığı kişisel alias veya `merchant yok` kararı.
3. Çalışma alanında doğrulanmış alias.
4. Banka tarafından sağlanmış doğrulanabilir merchant kimliği.
5. Kullanıcının yazdığı işlem adı/açıklaması üzerinde sistem kataloğu.
6. Ham banka açıklaması üzerinde sistem kataloğu.

Mevcut güven matrisi korunur: kullanıcı ve workspace doğrulaması 100, banka kimliği 98, tam alias
95, yasal ad 92, marka+hizmet 90, şube/POS kalıbı 86, güçlü marka 80 ve kısa alias 65. Logo için
asgari eşik 75'tir. Kısa alias banka/POS gibi bağlamsal bir metin içinde tek başına 65 puanda
kalır. Ancak `BİM`, `ŞOK`, `A101`, `TOD`, `Gain` veya `MUBI` gibi katalogda açıkça doğrulanmış kısa
bir marka kullanıcının yazdığı işlem adının tamamıyla birebir eşleşiyorsa `EXACT` kabul edilebilir;
bu istisna giriş kaynağına duyarlıdır ve ham banka açıklamasındaki kısa token'ı yükseltmez. `market`,
`premium`, `apple` gibi genel sözcükler hiçbir giriş kaynağında tek başına logo üretmez.

Kişisel transfer, maaş, kredi kartı ödemesi, hesaplar arası transfer, yatırım/birikim aktarımı,
nakit çekme/yatırma ve bakiye düzeltmesi katalog taramasından önce elenir. İade işlemleri, kaynak
merchant güvenle bulunabiliyorsa merchant bağlantısını koruyabilir; işlem yönü ayrıca modellenir.

## 5. Merchant kataloğu

Her katalog kaydı kararlı ve dil bağımsız bir `merchantId`, gösterim adı, alias'lar, negatif
alias'lar, varsayılan kategori ikonu, isteğe bağlı doğrulanmış logo anahtarı ve katalog sürümü
taşır. Alias'lar kullanıcıya gösterilen çevrilebilir metin değildir.

İlk Türkiye kataloğu küçük ve denetlenebilir paketler halinde genişletilir:

- **Dijital abonelik:** Netflix, Spotify, YouTube Premium/Music, Amazon Prime/Prime Video,
  Disney+, Max (`BluTV` tarihsel alias), Exxen, Gain, MUBI, TOD/beIN Connect, tabii, Apple
  Services, Google One, Microsoft 365, Adobe, Canva, PlayStation Plus ve Xbox Game Pass.
- **Alışveriş:** Trendyol, Hepsiburada, Amazon.com.tr, n11, ÇiçekSepeti, Pazarama, Boyner,
  LC Waikiki, DeFacto, Teknosa ve MediaMarkt.
- **Market ve hızlı teslimat:** Migros, Migros Sanal Market, Macrocenter, CarrefourSA, A101, BİM,
  ŞOK, File Market, Tarım Kredi, Getir ve Yemeksepeti Market.
- **Yemek:** Yemeksepeti, Trendyol Yemek, GetirYemek ve Migros Yemek.
- **Telekom:** Turkcell, Vodafone, Türk Telekom ve Superonline.
- **Akaryakıt:** Shell, OPET, Petrol Ofisi, TotalEnergies ve Aytemiz.
- **Ulaşım:** THY, Pegasus, SunExpress, TCDD, Uber, BiTaksi ve Martı.

Bu liste bir kullanım sıralaması iddiası değildir. Katalog girdisi eklemek için marka/hizmetin
Türkiye'de aktif olduğunun, alias'ın yeterince özgül olduğunun, çakışma ve negatif alias
senaryolarının test edildiğinin ve logo kaynağının kullanım koşulunun doğrulanması gerekir.

### Güncel katalog snapshot zeyli — 2026-11-02

1. **Tarihsel Snapshot ile Güncel Durum Ayrımı:** Bu belgenin 8.6-A bölümünde yer alan "61 merchant" ifadesi, Faz 8.6-A teslim anındaki tarihsel başlangıç gerçeğidir ve arşiv doğruluğu için korunmuştur. Ardından yürütülen H3 kategori genişletme paketleri (H3-A…H3-F) ile `StarterMerchantCatalog` sürümü 7'ye (`CATALOG_VERSION = 7`), toplam benzersiz merchant kayıt sayısı ise **151**'e ulaşmıştır.
2. **H3-F Ulaşım ve Seyahat Paketi:** Son paket olan H3-F ile Ulaşım (`transportation`) kategorisine 10 (`istanbulkart`, `ispark`, `tiktak`, `getir_arac`, `kamil_koc`, `metro_turizm`, `pamukkale_turizm`, `varan_turizm`, `ido`, `budo`) ve Seyahat (`travel`) kategorisine 10 (`ajet`, `corendon`, `obilet`, `enuygun`, `ets_tur`, `jolly_tur`, `tatilbudur`, `setur`, `airbnb`, `booking`) olmak üzere toplam 20 yeni kayıt eklenmiştir.
3. **Öneri ve Aranabilir Profil Sayıları:**
   - Ulaşım: 8 önerilen (`istanbulkart`, `tcdd`, `uber`, `bitaksi`, `marti`, `ispark`, `tiktak`, `kamil_koc`), 21 aranabilir kayıt.
   - Seyahat: 8 önerilen (`thy`, `pegasus`, `ajet`, `sunexpress`, `obilet`, `enuygun`, `booking`, `airbnb`), 13 aranabilir kayıt.
4. **Yönlü ve Fail-Closed Kategori Uyumluluğu:**
   - Ulaşım kategorisi genişletilmiş seyahat ve havayolu platformlarını (`ajet`, `corendon`, `sunexpress`, `pegasus`, `thy`, `obilet`, `enuygun`) arayabilir.
   - Seyahat kategorisi, Ulaşım-özel yerel kart/belediye kayıtlarını (ör. `istanbulkart`, `ispark`) arayamaz ve kabul etmez.
   - Market ve diğer ilgisiz kategoriler, Ulaşım ve Seyahat kayıtlarını arama sonuçlarında fail-closed gizler.
5. **Logo Durumu ve Kategori İkonu Fallback'i:** H3-F ile eklenen 20 kaydın tamamında `verifiedLogoKey = null`'dır. Bu kayıtlar için uzak logo isteği yapılmaz ve uzak logo desteği iddia edilmez; görsel arayüzde doğrudan güvenli `CATEGORY_ICON` fallback'i kullanılır.
6. **Atomik V2 Outbox Yazımı:** Yeni işlem oluşturmada `KnownMerchant` seçimi yapıldığında `LocalMutationDao.createTransactionWithMerchantDecisionV2` tek `@Transaction` sınırında transaction satırı, `USER_VERIFIED` verification satırı ve iki adet `protocolVersion = 2` outbox satırını atomik yazar. SQLite trigger abort testleriyle rollback güvenceye alınmıştır.
7. **Presentation Uyumluluğu:** Sunum katmanı (`TransactionsDisplayModelBuilder`) v1..v7 attribution sürümlerini destekler; henüz tanımlanmamış gelecek v8 kayıtlarını fail-closed gizler (`merchant = null`, `canShowMerchantLogo = false`).
8. **Emülatör Kabulü:** Pixel_8 AVD emülatöründe Demo APK ile yapılan H3-F3-A canlı kabulünde; öneri sıraları, yerel arama, kategori değişiminde uyumsuz seçimin temizlenmesi, İstanbulkart ve AJet için kategori ikonu fallback render'ı ve ilgisiz kategori fail-closed davranışı doğrulanmıştır (PASS). Kabul sırasında veritabanına hiçbir işlem submit edilmemiştir.

## 6. Planlanan veri sözleşmesi

Uygulama anındaki gerçek şema sürümü tekrar doğrulanır. Güncel sürüm hâlâ 22 ise yeni Room sürümü
23 olur; eşzamanlı başka migration varsa bir sonraki boş sürüm kullanılır. Uygulanmış migration
dosyaları değiştirilmez.

### Transaction merchant bağlantısı

`transactions` için planlanan nullable alanlar:

- `merchant_id`: kararlı katalog veya ileride kullanıcı merchant kimliği;
- `merchant_match_source_code`: tipli eşleşme kaynağı;
- `merchant_confidence`: `0..100` veya merchant yoksa `null`;
- `merchant_catalog_version`: eşleşmenin üretildiği katalog sürümü.

`merchant_id` yokken diğer merchant metadata'sının hangi durumlarda null olması gerektiği açık
invariant ve migration testiyle korunur. Yerel Room SSOT (Faz 8.6-B) ile uzak Supabase/DTO/outbox sözleşmesi (Faz 8.6-D) birbirinden ayrılmıştır; yerel 8.6-B diliminde DAO katmanı saf koruma/yazma politikasıyla (`mergePreservingLocalMerchant` — trim sonrası birebir aynı açıklamada yerel attribution'ı koruma, açıklama değiştiğinde üst katmandan gelen adayı yazma) tamamlanmıştır. Uzak DTO ve outbox payload sözleşmesi 8.6-D'de güncellenecektir.

### Kullanıcı doğrulamaları

Yeni Room modeli sistem kataloğunu kopyalamaz; yalnız kullanıcı kararını saklar. Planlanan kayıt:

- kararlı doğrulama kimliği;
- `owner_id` ve isteğe bağlı `workspace_id`;
- normalize edilmiş, uzunluk ve genel-sözcük kurallarıyla doğrulanmış alias;
- `ASSIGN` veya `BLOCK` kararı;
- `ASSIGN` için `merchant_id`;
- `PERSONAL` veya yetki doğrulanmış `WORKSPACE` kapsamı;
- soft-delete, version/baseVersion ve standart sync metadata'sı.

İlk kalıcılık dilimi kişisel kapsamla başlayabilir. Workspace kapsamı ancak rol/yetki, RLS ve
çatışma davranışı aynı dilimde tamamlanabiliyorsa açılır. `BLOCK` kararı aynı alias için otomatik
merchant önerisini engeller ancak özgün işlem metnini değiştirmez.

## 7. Logo sistemi

Ortak UI, yalnız domain/presentation modelindeki `merchantId`, `verifiedLogoKey`, güven durumu ve
kategori ikonunu tüketen `MerchantLogo` benzeri tek bileşen kullanır. Ekranlar marka anahtarlarını
ayrı `when` bloklarıyla çoğaltmaz.

Fallback hedef zinciri:

1. gelecekte uygulanacak, kullanıcının açıkça seçtiği ve güvenli yerel yolu doğrulanan merchant görseli;
2. kullanım hakkı doğrulanmış paketli veya kontrollü cache merchant logosu;
3. kategori ikonu;
4. merchant/işlem adının locale-aware ilk harfi;
5. genel işlem ikonu.

Kullanıcının seçtiği merchant görseli veri modelinde bugün henüz uygulanmamıştır; bu nedenle mevcut Faz 8.6-E efektif
fallback zinciri: `doğrulanmış paketli logo → kategori ikonu → baş harf → genel işlem ikonu`dur.
Resmî marka kılavuzları ve dağıtım izinlerinin ayrıntılı kanıt takibi için bkz: [docs/MERCHANT_LOGO_KAYNAK_VE_IZIN_ENVANTERI.md](MERCHANT_LOGO_KAYNAK_VE_IZIN_ENVANTERI.md).

İlk yayın diliminde doğrulanmış ve uygulamayla paketlenmiş küçük logo seti tercih edilir. Uzak logo
adaptörü ayrı dilimdir; yalnız allowlist edilmiş `merchantId -> HTTPS URL + hash + version`
manifesti alabilir, boyut/MIME/hash doğrular, özel app cache'ine atomik yazar ve ağ yokken fallback
kullanır. URL'ler transaction, kullanıcı veya workspace verisi içeremez.

## 8. Uygulama dilimleri ve bitiş kapıları

### 8.6-A — Girdi sözleşmesi ve genişletilmiş Türkiye kataloğu (Tamamlandı — 2026-10-13)

- **Durum:** Tamamlandı (2026-10-13).
- **Gerçekleşen kapsam:**
  - `MerchantRecognitionInputSource` (`USER_ENTERED_DESCRIPTION`, `BANK_DESCRIPTION`) tipli girdi sözleşmesi eklendi; özgün metin ve kaynak sonuçta byte-for-byte korunur.
  - Kaynağa duyarlı kısa marka kuralı uygulandı: kullanıcı tam girdisinde BİM exact 95 / banka veya genel girdide kısa alias 65 puan ve logo gizli.
  - Genel sözcük koruması (`DISALLOWED_STANDALONE_GENERIC_TOKENS`: `market`, `premium`, `apple`, `shop`, `store`, `yemek`) uygulandı; genel ifadeler merchant eşleşmesi üretmez.
  - Başlangıç kataloğu dijital abonelik (18), alışveriş (11), market/teslimat (11), yemek (4), telekom (4), akaryakıt (5), ulaşım (7) ve kafe (1) olmak üzere 61 merchant ile genişletildi; tüm kategoriler kanonik `ExpenseCategoryVisualCatalog` anahtarlarını kullanır; doğrulanmamış sahte logo anahtarı taşınmaz (`verifiedLogoKey = null`).
  - Hizmete özel öncelik (Amazon Prime vs Amazon, Trendyol Yemek vs Trendyol, GetirYemek vs Getir, Migros Yemek/Sanal Market vs Migros) negatif alias ve priority ile deterministik ayrıştırıldı. BluTV tarihsel alias'ı Max merchant kimliğine bağlandı.
  - Bu dilimde Room, Supabase, UI ve logo dosyası değişikliği yapılmadı.
- **Doğrulama:** 30 hedefli `MerchantRecognitionEngineTest`, `:sharedLogic:testAndroidHostTest` (BUILD SUCCESSFUL) ve `:sharedLogic:compileKotlinIosSimulatorArm64` (BUILD SUCCESSFUL) ile doğrulandı.

### 8.6-B — Room SSOT ve transaction merchant bağlantısı (Tamamlandı — 2026-10-14)

- Domain transaction modeline `TransactionMerchantInfo` tipli value object (merchantId, matchSource, confidence 0..100, catalogVersion > 0) eklendi; secondary constructor ve invariant kontrolleri ile kısmi/geçersiz durumlar engellendi.
- Room v22 -> v23 forward-only migration (`ANDROID_MIGRATION_22_23`), Android database factory kaydı ve `sharedLogic/schemas/.../23.json` export şeması tamamlandı (`merchant_id`, `merchant_match_source_code`, `merchant_confidence`, `merchant_catalog_version`).
- `FinanceMappers` içinde domain<->entity fail-closed mapper dönüşümleri yazıldı; remote `TransactionDto` ve remote mapper'lara dokunulmadı.
- Repository sınırında `TransactionMerchantAttributionResolver` ile 5 işlem üretim yolu bağlandı:
  1. `OfflineFirstTransactionRepository.create` (USER_ENTERED_DESCRIPTION, confidence >= 75)
  2. `OfflineFirstTransactionRepository.update` (yalnız açıklama değiştiğinde yeniden tanıma, miktar/kategori değişiminde koruma)
  3. `OfflineFirstTransactionRepository.createInstallmentGroup` (tüm taksit entity'lerine tanıma, atomik batch)
  4. `OfflineFirstRecurringTransactionRepository` (oluşturulan occurrence transaction'larında tanıma)
  5. `PersonalBackupImporter` (V1 backup formatını değiştirmeden import sırasında yeniden tanıma)
- Inbound ACK, pull ve conflict recovery yollarında (`V2OutboxOperationExecutor`, `V1OutboxOperationExecutor`, `IncrementalRemoteSync`, `InitialRemoteSync`, `OfflineFirstSyncRepository` keep-remote, `ConflictRecoveryService`, `ackTransactionWriteV2`, `ackTransactionGroupV2`, `recoverEquivalentTransactionUnits`) `TransactionMerchantAttributionResolver` sync coordinator/repository sınırında çalışır; adayı açık ve zorunlu parametre olarak iletir. `FinanceMappers` içindeki `mergePreservingLocalMerchant` ise DAO/mapper seviyesinde herhangi bir resolver bağımlılığı taşımayan saf, trim sonrası birebir eşitlik korumasıdır (açıklama trim sonrası birebir eşitse yerel attribution korunur, farklıysa veya yerel kayıt yoksa üst katmandan gelen aday yazılır).
- `OfflineFirstTransactionRepository.create` ve `createInstallmentGroup` içinde çağıranın sağladığı merchantInfo'ya güvenilmeyip güvenilir attribution description üzerinden zorunlu hesaplandı (create güven sınırı).
- `OfflineWriteQueueTest` içindeki indent ihlali düzeltildi.
- **8.6-D Sınır Netleştirmesi:** Supabase SQL/migration, `TransactionDto` merchant alanları, uzak RPC (`sync_write_v2`), outbox payload JSON ve remote conflict equivalence kesin olarak 8.6-D kapsamına bırakılmıştır. Outbox remote JSON payload'unun merchant alanı taşımadığı testle kanıtlanmıştır.
- **Doğrulama:** 11 hedefli `TransactionMerchantCoordinatorSyncTest`, 19 hedefli `TransactionMerchantSyncTest`, 1 migration testi (`TransactionMerchantMigrationTest`), 14 domain attribution testi (`TransactionMerchantAttributionTest`), 30 motor testi (`MerchantRecognitionEngineTest`) ve 65 repository testi (`OfflineFirstTransactionRepositoryTest`) olmak üzere 140 hedefli test (0 hata), tam `testAndroidHostTest` (1780 test, 0 failure, 0 skipped), iOS Simulator Arm64 derlemesi ve Android debug assemble ile doğrulandı.

### 8.6-C — Kişisel doğrulama ve düzeltme kalıcılığı

- `ASSIGN`/`BLOCK` kişisel doğrulama modelini, DAO ve repository sözleşmesini ekle.
- Genel sözcüklerin kişisel alias olarak öğrenilmesini fail-closed engelle.
- Kullanıcı seçimi 100 puanla sistem tahmininden üstün olmalı.
- Silme tombstone ve conflict iki kopyalı çözüm kuralını korumalı.
- Kapı: atomik Room testleri, repository öncelik testleri ve oturum izolasyonu testleri.

**İlerleme Notu (8.6-C1 ve 8.6-C2 — Devam Ediyor):**
- **8.6-C1 (Saf Domain Karar Sözleşmesi):** `UserMerchantDecision` (`Assign`, `Block`), `MerchantMatchStatus.BLOCKED_BY_USER`, `INVALID_VERIFICATION` kapalı tipleri eklendi; `MerchantRecognitionRequest` tek karar kaynağına bağlandı; transfer/hariç tutulan hareket koruması ve fail-closed bilinmeyen merchant davranışı 35 hedefli `MerchantRecognitionEngineTest` ve 18 `TransactionMerchantAttributionTest` ile doğrulandı.
- **8.6-C2 (Room v24 Salt-Okuma Temeli):** `user_merchant_verifications` tablosu (`id`, `owner_id`, `workspace_id`, `scope_key`, `normalized_alias`, `decision_code`, `merchant_id`, `scope_code`, `created_at_epoch_ms` ve gömülü `SyncMetadata`), `UNIQUE(scope_key, normalized_alias)` ve kapsam indeksleri tanımlandı; `ANDROID_MIGRATION_23_24` ve `24.json` export şeması üretildi; `UserMerchantVerificationDao` salt-okuma (mutation metotsuz) olarak açıldı; fail-closed `toDomainOrNull` mapper'ı uygulandı; 18 mapper, 5 DAO ve 1 migration testiyle doğrulandı.
- **Kritik Mimari Sınır:** Bu aşamada DAO veya repository'de mutasyon/yazma API'si açılmamıştır. Uygulama kaynaklı mutation ve uzak sync 8.6-D dikey diliminde entity+outbox atomik olarak açılacaktır.
- **Bozuk Kayıt Fail-Closed Sözleşmesi:** 8.6-D’de exact alias için Room satırı bulunduğu halde entity→domain decode başarısızsa bu durum ‘karar yok’ sayılarak sistem kataloğuna düşülmeyecek; tipli invalid-verification sonucu ile fail-closed ele alınacaktır. Bu sayede bozulmuş bir BLOCK kaydının sessizce yok sayılıp sistem logosunun yeniden gösterilmesine yol açması engellenecektir.
- **8.6-C3 (Personal Merchant Decision Wiring ve Öncelik Kapanışı — Tarihsel İlk Teslim — 2026-10-18):**
  - **Üretim Wiring Noktaları:** `OfflineFirstTransactionRepository.create`, `OfflineFirstTransactionRepository.createInstallmentGroup`, `OfflineFirstTransactionRepository.update` (yalnızca açıklama değiştiğinde; açıklama değişmediğinde mevcut attribution korunur), `OfflineFirstRecurringTransactionRepository.generateDueTransactions` ve `BackupImportPlanner.plan` akışlarına `UserMerchantVerificationRepository` exact alias kararı bağlandı.
  - **Tek Karar Kaynağı & Çözümleyici:** `TransactionMerchantAttributionResolver.resolveWithDecision` ortak suspend yardımcısı ile `getDecisionForAlias` çağrısı yapıldı. Karar matrisi: `ASSIGN` -> sistem kataloğundan önce uygulanır, `USER_VERIFIED` ve %100 güven üretir; `BLOCK` -> kesin bastırma, `merchantInfo = null`, sistem kataloğuna düşmez; `NotFound` -> sistem kataloğu davranışı aynen sürer.
  - **Bozuk Kayıt Fail-Closed Davranışı:** `InvalidStoredVerification` durumunda hiçbir zaman sessiz `NotFound` dönüşümü yapılmaz ve sistem kataloğuna düşülmez. Transaction mutation yollarında (`create`, `createInstallmentGroup`, `update`) işlem `AppError.Storage("invalid_stored_merchant_verification")` ile derhal durdurulur ve kısmi Room/outbox yazımı engellenir; recurring materialization'da kural `skippedRecurringIds` listesine eklenerek atlanır ve hatalı transaction üretimi önlenir; backup import'ta ise `merchantInfo = null` atanarak sistem tahmini kesin olarak engellenir.
  - **Transfer/Excluded Önceliği:** `MerchantRecognitionEngine` içindeki excluded classification kontrolü (transfer, maaş, nakit çekim vb.) kişisel karardan önce çalışır ve bunları kişisel karar olsa dahi merchant'a dönüştürmez.
  - **Doğrulama ve Tarihsel Durum:** 16 hedefli Robolectric integration testi (`TransactionMerchantPersonalDecisionIntegrationTest`), 2 hedefli backup import testi (`BackupImportPlannerTest`), 31 kullanıcı doğrulama testi (`UserMerchantVerificationRepositoryTest`), 65 işlem testi (`OfflineFirstTransactionRepositoryTest`), 29 tekrarlayan işlem testi (`OfflineFirstRecurringTransactionRepositoryTest`), 35 tanıma motoru testi (`MerchantRecognitionEngineTest`), 18 domain attribution testi (`TransactionMerchantAttributionTest`), 2092 genel Android host testi (175 test suite, 0 failure, 0 error, 0 skipped), iOS simülatör derlemesi (`:sharedLogic:compileKotlinIosSimulatorArm64`), Android debug derlemesi (`:androidApp:assembleDebug`), localization policy kontrolü ve `git diff --check` ilk teslim kanıtı olarak kaydedildi. Bu kayıt R1/R2 öncesindeki ara teslim olup nihai kabul durumu hemen sonraki C3-R1-R2 bloğunda 18 integration / 2100 test ile kaydedilmiştir. Güncel durum: Faz 8.6-C ve C3/R1/R2 denetimden geçti, kabul edildi ve ana 8.6-C checkbox'ı kapatıldı.
- **8.6-C3-R1-R2 (Working Tree Bütünlüğü ve Gerçek Unknown Merchant Test Kapanışı — Faz 8.6-C tamamlandı ve kabul edildi — 2026-10-18):**
  - **Working Tree Bütünlüğü ve Kanıt Sınırlaması:** Önceki `ktlintFormat` geri almasında hedeflenen 46 dosyanın checkout sonrasında HEAD durumuna döndüğü doğrulanmış; somut kayıp kanıtı bulunmamaktadır; checkout öncesi kayıt truncated olduğu için sıfır kayıp kesin kanıtlanamamış, proje sahibi bu kanıt sınırlamasını bilerek kabul etmiştir; `git checkout`, `git restore`, `git reset` ve `ktlintFormat` yasaklandı.
  - **Zorunlu Decision Repository (Sıfır Null Fallback):** `OfflineFirstTransactionRepository`, `OfflineFirstRecurringTransactionRepository`, `BackupImportPlanner` ve `TransactionMerchantAttributionResolver.resolveWithDecision` içindeki `UserMerchantVerificationRepository` bağımlılığı non-null ve zorunlu yapıldı; varsayılan `= null` değeri ve `verificationRepository == null` fail-open fallback yolu tamamen kaldırıldı. Üretim koduna sahte no-op eklenmeden tüm testler açık test repository'leriyle güncellendi.
  - **Excluded-First Kapısı (Sıfır Lookup):** Transfer, maaş, nakit çekim, kredi kartı ödemesi gibi `EXCLUDED_TRANSACTION` sınıflandırmalarında `getDecisionForAlias` **kesinlikle çağrılmaz** (lookup sayısı = 0); sonuç doğrudan `Success(null)` döner. Bozuk veya ASSIGN/BLOCK kişisel kayıtlar excluded sonucunu değiştiremez. Excluded durumu repository sorgusundan önce çalışır ve aynı sonuç NotFound yolunda yeniden kullanılır.
  - **Gerçek Unknown Merchant Test Kapanışı:** `TransactionMerchantPersonalDecisionIntegrationTest` içinde Room'a eklenen ASSIGN kaydı deterministik UUIDv8 (`UuidHelper.deterministicMerchantVerificationUuid(userAId, "BILINMEYEN MARKET")`) ile yazıldı; testin mapper'daki invalid ID yolundan değil doğrudan motorun `MerchantMatchStatus.INVALID_VERIFICATION` yolundan geçtiği `verificationRepository.getDecisionForAlias` sonucunun `Decision(Assign)` olduğu ön koşul assert'ü ile kanıtlandı; create (storage failure, sıfır outbox), update (eski açıklama ve outbox snapshot koruması), installment (sıfır kısmi transaction/outbox), recurring (occurrence üretilmez/skip) ve CancellationException yutulmazlığı doğrulandı.
  - **Ayrı Backup Unknown-ASSIGN Testi:** `BackupImportPlannerTest` içine `plan_whenPersonalAssignHasUnknownMerchantId_suppressesCatalogFallbackAndSetsMerchantInfoNull` testi eklenerek katalog alias'ında dahi bilinmeyen ASSIGN kararında katalog fallback yapılmaksızın `merchantInfo = null` atandığı bağımsız test edildi.
  - **Doğrulama:** 18 hedefli Robolectric integration testi (`TransactionMerchantPersonalDecisionIntegrationTest`), 23 domain attribution testi (`TransactionMerchantAttributionTest`), 4 backup import testi (`BackupImportPlannerTest`), 65 işlem testi (`OfflineFirstTransactionRepositoryTest`), 15 tekrarlayan işlem testi (`OfflineFirstRecurringTransactionRepositoryTest`), 35 tanıma motoru testi (`MerchantRecognitionEngineTest`), 11 kullanıcı doğrulama testi (`UserMerchantVerificationRepositoryTest`), 2100 genel Android host testi (175 test suite, 0 failure, 0 error, 0 skipped), iOS simülatör derlemesi (`:sharedLogic:compileKotlinIosSimulatorArm64`), Android debug derlemesi (`:androidApp:assembleDebug`), localization policy kontrolü ve `git diff --check` başarıyla doğrulandı. D1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. Faz 8.6-C tamamlandı ve kabul edildi; ana 8.6-C checkbox'ı kapatıldı; Faz 8.6-E'ye geçilmedi.



### 8.6-D — Outbox, Supabase ve sync (denetimden geçti ve tamamlandı — 2026-10-18)

- Forward-only migration, RLS, V2 yazma sözleşmesi, DTO/payload codec, ACK/pull ve conflict
  eşdeğerliğini tamamla.
- Kişisel doğrulamalar yalnız sahip kullanıcı tarafından okunup değiştirilebilmeli.
- Staging uygulaması ve SQL kabulü ayrıca açık onay gerektirir; production kapsam dışıdır.
- Kapı: rollback'li SQL contract, sync testleri ve staging öncesi güvenlik incelemesi.

**İlerleme Notu (8.6-D1 — Deterministik Kimlik, Remote DTO, Payload Codec ve Yerel Supabase Migration Sözleşmesi):**
- **Ortak SHA-256 Altyapısı:** `sharedLogic/.../FeniqoDigest.kt` ortak, internal FIPS 180-4 SHA-256 modülü eklendi; `WorkspaceInvitationCrypto.hashInvitationToken` yeni yardımcıya delege edildi ve mevcut davet testleri regresyon kapısı olarak korundu.
- **Deterministik RFC 9562 UUIDv8:** `UuidHelper.deterministicMerchantVerificationUuid(userId, normalizedAlias)` eklendi. Girdi sözleşmesi: `UTF8("feniqo:user-merchant-verification:v1\u0000" + canonicalUserId + "\u0000" + normalizedAlias)`. Byte 6 version 8, byte 8 variant 1 kuralıyla NETFLIX (`fddf797b-6b74-89ea-a969-f30df71d5921`) ve KAHVE DUNYASI (`ac077b4d-9255-813b-abd2-0893d4b1c26a`) iki golden vector üzerinde Kotlin ve PostgreSQL tarafında %100 birebir eşitlikle kanıtlandı.
- **Remote DTO ve Payload Codec:** `UserMerchantVerificationDto` (`toDomainOrNull`, `toDomain`, `toEntity`, `toRemoteSyncMetadata`) ve `UserMerchantVerificationPayloadCodec` (`encodeCreate`, `encodeUpdate`, `encodeDelete`, `parseAndValidate`) fail-closed sözleşmeyle yazıldı.
- **Forward-only Supabase Migration (`20261015144943_sync_write_v2_user_merchant_verifications.sql`):** `public.user_merchant_verifications` tablosu, RLS, 15 entity type (`USER_MERCHANT_VERIFICATION`) receipt constraint genişletmesi ve `sync_write_v2` dar wrapper'ı oluşturuldu. Sunucu tarafı deterministik UUID'yi NUL bytea (`decode('00', 'hex')`) ile hesaplayarak doğrular, uyuşmazlıkta definitive rejection üretir.
- **İzole Yerel SQL Sözleşme Testleri:** 33 migration sıralı apply, rollback atomikliği, 15 entity receipt constraint, authenticated SELECT, anon engeli, cross-user RLS izolasyonu, doğrudan INSERT/UPDATE/DELETE engeli, RPC authenticated erişimi, iki golden vector, yanlış deterministik ID reddi, generic alias reddi, ASSIGN/BLOCK invariantları, idempotent replay, fingerprint mismatch reddi, stale baseVersion conflict, CREATE yarışı CONFLICT, UPDATE/DELETE NOT_FOUND, soft-delete ve eski 14 entity RPC delegasyon regresyonu `supabase/tests/run_local_sync_contract_test.py` ile %100 başarıyla doğrulandı.
- **Sınır ve Güvenlik:** Bu dilimde Room mutation, outbox enqueue, ACK uygulaması, pull motoru, `SyncEntityType` enum eklemesi, repository mutation ve UI açılmamıştır (8.6-D2'ye bırakılmıştır). Staging veya production ortamına kesinlikle hiçbir SQL/migration uygulanmamıştır.

**İlerleme Notu (8.6-D2-A — Atomik Room Mutation, V2 Outbox, Executor ve ACK/Conflict Dikey Dilimi — 2026-10-15):**
- **Domain & Repository Sözleşmesi:** `UserMerchantVerificationRepository` interface'i (`observeActiveVerifications`, `getDecisionForAlias`, `assignMerchant`, `blockMerchant`, `deleteVerification`) ve fail-closed `UserMerchantVerificationLookupResult` (`NotFound`, `Decision`, `InvalidStoredVerification`) eklendi. Hiçbir public metod `ownerId`/`userId` almaz; kullanıcı kimliği `AuthRepository.observeSession()` üzerinden doğrulanır; oturumsuz istekler `AppError.Authentication("auth_session_required")` ile reddedilir.
- **Alias & Karar Doğrulaması:** `TurkishDescriptionNormalizer`, 500 karakter sınırı, `DISALLOWED_STANDALONE_GENERIC_TOKENS` kümesi, ASSIGN için stable merchant ID ve BLOCK için null merchantId invariantları repository sınırında fail-closed doğrulandı. Deterministik UUIDv8 `UuidHelper.deterministicMerchantVerificationUuid(userId, normalizedAlias)` ile üretildi.
- **Atomik Room Mutation & Outbox Coalescing:** `LocalMutationDao.mutateUserMerchantVerificationV2` tek `@Transaction` sınırında entity + `SyncOperationEntity` (V2) atomikliğini sağladı. Yeni kayıt CREATE (v0, baseVersion=null), senkronize kayıt UPDATE (baseVersion=remote applied version), DELETE (tombstone + PENDING_DELETE), pending CREATE->UPDATE coalescing, pending CREATE->DELETE hard-delete, IN_FLIGHT predecessor varken successor blocked outbox ve unblock rebase zinciri doğrulandı.
- **V2 Executor & ACK/Conflict:** `V2OutboxOperationExecutor` içine `USER_MERCHANT_VERIFICATION` dalı eklendi. APPLIED (successor yoksa synced entity + conflict temizleme; successor varsa local kararı ezmeden version/baseVersion rebase + successor unblock), CONFLICT (SyncConflictEntity'ye iki ayrı kopya yazımı, successor yoksa sync_status CONFLICT; sessiz last-write-wins yoktur), DELETE NOT_FOUND (MissingDeleteAcknowledged) fail-closed işlendi.
- **Sınır ve Kapsam:** D2-B (remote pull, cursor, initial/incremental sync), D1 SQL dosyaları, Supabase staging/production ve UI/localization kesinlikle bu dilimin dışındadır.

**İlerleme Notu (8.6-D2A-R1 — Canonical Outbox, Remote Response ve Successor Güvenliği — 2026-10-15):**
- **Canonical Payload:** `UserMerchantVerificationPayloadCodec` içindeki default değerler kaldırıldı; `workspace_id: null`, `scope_code: "PERSONAL"`, `decision_code` ve `merchant_id` anahtarlarının açık varlığı zorunlu kılındı. Eksik alanlar, string `"null"` ve bilinmeyen alanlar fail-closed reddedildi.
- **Raw Payload Bypass Koruması:** `OfflineWriteQueue` raw overload'u `private` yapıldı; `LocalMutationDao.mutateUserMerchantVerificationV2` girişinde codec doğrulaması ve entity ile payload alanlarının tam eşleşmesi zorunlu kılındı.
- **Fail-Closed Remote APPLIED / CONFLICT:** `V2OutboxOperationExecutor` içinde remote `UserMerchantVerificationDto` doğrulandı (id, userId, workspace null, scope PERSONAL, alias, deterministik UUIDv8, version >= 1, updated_at). `remoteRecord.version ?: 0L` kaldırıldı; geçersiz remote kayıtta entity/conflict yazımı durdurulup `DefinitiveOutboxFailureException` fırlatılır.
- **DELETE NOT_FOUND Successor CREATE Dönüşümü:** Predecessor DELETE NOT_FOUND döndüğünde successor UPDATE'in snapshot'ı korunarak atomik olarak CREATE'e dönüştürüldü (`baseVersion = null`, `predecessorOperationId = null`, `isBlocked = false`, entity `PENDING_CREATE`, `version = 0L`).
- **Repository Fail-Closed Mutation:** `OfflineFirstUserMerchantVerificationRepository` mutation/delete öncesi Room kaydını doğrulayacak şekilde korundu (owner, scope, deterministik UUID, invariant); bozuk kayıtta `AppError.Storage("invalid_stored_merchant_verification")` döner. `MerchantMappers.toDomainOrNull` deterministik UUIDv8 doğrulamasıyla güncellendi.
- **Kuyruk İlerleme Güvenliği:** OutboxProcessor testi ile hatalı merchant payload definitive failure olduğunda kuyruğun sonraki operasyonları kilitlemediği kanıtlandı.
- **Doğrulama:** 1891 test (`:sharedLogic:testAndroidHostTest`), `:sharedLogic:compileKotlinIosSimulatorArm64` ve `:androidApp:assembleDebug` ile tam doğrulandı. D1 dosya hash'leri korundu. D2-B'ye geçilmedi.

**İlerleme Notu (8.6-D2-B1, B1-R1 & B1-R2 — Fail-Closed Merchant Pull Room Primitifleri ve Version/BaseVersion Kapanışı — 2026-10-16):**
- **Room Pull/Apply Atomikliği:** `RemoteSyncDao.applyUserMerchantVerificationPull` tek `@Transaction` sınırında actor scope'u (`SyncScopeKey.requireUserScope`), cursor invariantlarını (`syncScopeKey`, `entityTypeCode == USER_MERCHANT_VERIFICATION`, `entityId == remoteEntity.id`, `cursor.updatedAt == remote.updatedAt`), deterministik RFC 9562 UUIDv8'i (`UuidHelper.deterministicMerchantVerificationUuid`), canonical alias'ı (boşluk/büyük harf, uzunluk, yasaklı generic token denetimi), ASSIGN/BLOCK invariantlarını ve remote SYNCED metadata'sını fail-closed doğrulayarak atomik uygular; başarılı apply durumunda ilgili çakışma satırını temizler ve cursor'ı ilerletir.
- **Yerel Pending/Conflict Koruması ve 15-Alan Eşdeğerliği:** Yerel kayıt `PENDING_CREATE`, `PENDING_UPDATE`, `PENDING_DELETE` veya `CONFLICT` durumundaysa remote pull sessizce kaydı ezemez; transaction rollback ile sonuçlanır. `SYNCED` yerel kayıtlarda remote versiyon yerel versiyondan düşükse fail-closed reddedilir; aynı versiyon durumunda `localUpdatedAtEpochMillis` hariç tüm 15 authoritative alan (`id`, `ownerId`, `workspaceId`, `scopeKey`, `normalizedAlias`, `decisionCode`, `merchantId`, `scopeCode`, `createdAtEpochMillis`, `syncStatus`, `version`, `baseVersion`, `updatedAtEpochMillis`, `deletedAtEpochMillis`, `lastSyncError`) karşılaştırılır; birebir eşdeğer ise entity yeniden yazımı atlanarak idempotent cursor/conflict temizliği yapılır, herhangi bir authoritative alan farklıysa fail-closed rollback uygulanır. Remote tombstone standart soft-delete (`deletedAtEpochMillis`) ile Room'a yazılır, hard-delete yapılmaz.
- **Scoped Pull Conflict Primitifi, Canlı Outbox ve Version/BaseVersion Sözleşmesi (B1-R2):** `RemoteSyncDao.recordUserMerchantVerificationPullConflict` tek `@Transaction` sınırında yerel kaydın açık allow-list (`PENDING_CREATE`, `PENDING_UPDATE`, `PENDING_DELETE`, `CONFLICT`) durumunda olduğunu teyit eder (`FAILED` ve bozuk status'ler reddedilir); dar `getUserMerchantVerificationOutboxRow` sorgusuyla canlı V2 outbox operasyonunun varlığını (`protocolVersion == 2`, `entity_type_code = 'USER_MERCHANT_VERIFICATION'`, canlı status allow-list, yerel duruma uygun `operationTypeCode`, `UserMerchantVerificationPayloadCodec` doğrulaması ve yerel entity ile birebir payload alan eşleşmesi) fail-closed doğrular; yerel entity ve outbox version/baseVersion durumu operasyon türüne göre açıkça denetlenir (CREATE için `liveOp.baseVersion == null`, `local.baseVersion == null`, `local.version == 0L` ve local status `PENDING_CREATE` veya CREATE tabanlı `CONFLICT`; UPDATE/DELETE için `liveOp.baseVersion >= 1L`, `local.baseVersion >= 1L`, `liveOp.baseVersion == local.baseVersion`, `local.baseVersion <= local.version` ve `local.version >= 1L`; CONFLICT durumunda operasyon türüne göre ilgili invariantlar aranır); ortak `validateRemoteUserMerchantVerification` ile doğrulanmış `remoteEntity` invariantlarını (`remoteEntity.id == conflict.entityId`, `conflict.localVersion == local.sync.version`, `conflict.remoteVersion == remoteEntity.sync.version`, `remoteVersion > localBaseVersion`, `cursor.updatedAt == remote.updatedAt`, non-blank payload'lar) denetler; yerel kararı ve outbox payload'ını koruyarak `SyncConflictEntity` yazar, satırı `sync_status = 'CONFLICT'` ve tipli hata ile işaretler ve cursor'ı ilerletir.
- **Kapsam ve Sınırlar:** Ayrı `applyInitialMerchantVerificationSnapshot` metodu eklenmemiştir (B2'de `applyInitialSnapshot`'a parametre olarak eklenecektir); Initial/Incremental sync wiring (D2-B2), Conflict recovery/equivalence (D2-B3), Supabase staging/production ve UI/localization kesinlikle bu dilimin dışındadır.
- **Doğrulama:** 44 hedefli DAO testi (`UserMerchantVerificationPullDaoTest`), 1948 test (`:sharedLogic:testAndroidHostTest`), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. D2-B2'ye geçilmedi.

**İlerleme Notu (8.6-D2-B2-A — Incremental Merchant Verification Pull Wiring — 2026-10-16):**
- **Incremental Remote Pull Wiring:** `IncrementalRemoteSync.pullFor` içine `USER_MERCHANT_VERIFICATION` akışı bağlandı. Cursor anahtarı kesin olarak `"USER_MERCHANT_VERIFICATION"` olarak kullanıldı; `syncStateDao.getCursor(syncScopeKey, USER_MERCHANT_VERIFICATION)` ile okundu.
- **Fail-Closed Uzak Fetch ve Sıralama:** `CoreRemoteDataSource.fetchMerchantVerifications(UserMerchantVerificationRemoteQuery(page, updatedAfter = cursor?.toRemoteCursor()))` mevcut `fetchAll` mekanizması üzerinden çağrıldı. Uzak DTO `updated_at` alanı zorunlu fail-closed tutuldu; `created_at` fallback'i eklenmedi.
- **Oturum ve Sahiplik İzolasyonu:** Her merchant DTO için herhangi bir Room veya cursor işleminden önce `dto.userId == actorUserId`, `workspaceId == null`, `scopeCode == "PERSONAL"`, canonical alias, deterministik UUIDv8 (`UuidHelper.deterministicMerchantVerificationUuid`), `version >= 1` ve geçerli `updatedAt/deletedAt` fail-closed doğrulandı. Kullanıcı B'ye ait DTO kullanıcı A scope'unda Room mutation, conflict veya cursor oluşturamaz.
- **Apply/Conflict/Preserve Karar Matrisi:**
  - *Temiz veya SYNCED:* `RemoteSyncDao.applyUserMerchantVerificationPull` ile remote entity yazılır, çakışma temizlenir ve cursor ilerletilir. Düşük versiyon veya aynı versiyon / farklı içerik fail-closed reddedilir; tombstone soft-delete olarak korunur.
  - *Pending / CONFLICT ve `remoteVersion > localBaseVersion`:* Canlı V2 outbox operasyonu çözülür. Yerel kararı, versiyonu ve tombstone durumunu yansıtan yerel DTO snapshot'ı ile gelen tam remote DTO snapshot'ı `SyncConflictEntity` içine kaydedilir; `RemoteSyncDao.recordUserMerchantVerificationPullConflict` ile yerel karar ve outbox korunarak çakışma yazılır ve cursor ilerletilir.
  - *Pending / CONFLICT ve `remoteVersion <= localBaseVersion`:* Yerel karar, entity ve outbox korunur; conflict oluşturulmaz. Bu yol için yeni eklenen fail-closed transaction primitifi `RemoteSyncDao.advanceUserMerchantVerificationPullCursor(syncScopeKey, remoteEntity, cursor)` çağrılır. Bu primitif yerel entity, outbox veya conflict'e dokunmaksızın yalnızca cursor'ı atomik ilerletir.
- **Sonuç Sayacı:** `IncrementalSyncResult` içine geriye uyumlu `receivedMerchantVerificationCount: Int = 0` eklendi; çekilen toplam kayıt sayısı, uygulanan kayıtlar (`appliedCount`) ve çakışmalar (`conflictCount`) sonuç sayacına dahil edildi.
- **Açık Kalan Kapsam ve Sınırlar:** Initial snapshot (`InitialRemoteSync` ve `RemoteSyncDao.applyInitialSnapshot`) entegrasyonu D2-B2-B olarak açık bırakıldı; dokunulmadı. Conflict recovery/equivalence (D2-B3), Supabase staging/production, Room şema sürümü (v24), SQL dosyaları ve UI/localization kesinlikle bu dilimin dışındadır.
- **Doğrulama:** 21 hedefli incremental sync testi (`UserMerchantVerificationIncrementalSyncTest`), 15 gerileme testi (`IncrementalRemoteSyncTest`), 1969 test (`:sharedLogic:testAndroidHostTest`), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri korundu.

**İlerleme Notu (8.6-D2-B2-A-R1 — Incremental Pull Fail-Closed Kapanışı — 2026-10-16):**
- **CoreRemoteDataSource Fail-Closed Sözleşmesi:** `CoreRemoteDataSource.fetchMerchantVerifications` varsayılan açık hata fırlatan (`error("fetchMerchantVerifications(query) is not implemented in this CoreRemoteDataSource implementation")`) fail-closed sözleşmesine geri alındı; test kolaylığı için boş sayfa dönen fail-open varsayılan kaldırıldı. Incremental pull kullanan merchant dışı test fake'lerinde boş sonuç ihtiyacı açıkça `RemotePage(emptyList(), query.page, totalCount = 0)` ile override edildi.
- **Canlı Outbox ve Version/BaseVersion Doğrulamalı Cursor İlerleme Primitifi:** `RemoteSyncDao.advanceUserMerchantVerificationPullCursor` içine `operationId: String` parametresi eklendi ve `IncrementalRemoteSync` preserve yolunda mevcut outbox zincirinden `requiredOutboxOperationId` çözüldü. Conflict ve preserve yollarında ortak `validateLiveUserMerchantVerificationOutbox` helper'ı çıkarıldı: canlı outbox varlığı, `syncScopeKey`, `USER_MERCHANT_VERIFICATION`, entityId, `protocolVersion == 2`, terminal FAILED reddi, canlı status allow-list, operasyona göre status eşleşmesi, CREATE/UPDATE/DELETE version & baseVersion kuralları, non-blank payloadJson, `UserMerchantVerificationPayloadCodec` doğrulaması ve yerel entity ile 7 alanın (id, ownerId, alias, decision, merchant, scope, workspace) birebir eşleşmesi fail-closed doğrulandı. Başarılı preserve yolunda entity, outbox ve conflict snapshot'ları korunarak yalnızca cursor atomik ilerletilir.
- **Zorunlu Preserve Regresyon Testleri:** Pending UPDATE outbox yok (fail-closed, cursor ilerlemez), terminal FAILED outbox (fail-closed), protocolVersion != 2 (fail-closed), payload uyuşmazlığı (fail-closed), baseVersion > version bozukluğu (fail-closed), PENDING_CREATE bozuk version/baseVersion (fail-closed), geçerli pending UPDATE (yalnız cursor ilerler), geçerli CONFLICT (entity, outbox ve conflict snapshot'ı korunur, yalnız cursor ilerler) senaryoları eklendi. Negatif testlerde 4 tablo snapshot'ı (`user_merchant_verifications`, `sync_operations`, `sync_conflicts`, `sync_cursors`) karşılaştırıldı.
- **Gerçek Zamanlı Fetch Cancellation Testi:** `scenario20` fetch sırasında kontrollü `CompletableDeferred` suspension noktası ve coroutine iptaliyle güncellendi; `CancellationException` kanıtlandı ve aktif remote fetch sırasında cancellation'ın hiçbir Room yazımı üretmediği doğrulandı.
- **Doğrulama:** 29 hedefli incremental sync testi (`UserMerchantVerificationIncrementalSyncTest`), 15 gerileme testi (`IncrementalRemoteSyncTest`), 1977 test (`:sharedLogic:testAndroidHostTest`), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri korundu.

**İlerleme Notu (8.6-D2-B2-A-R2 — Merchant Active Tail Outbox Seçimi — 2026-10-16):**
- **Genel En Eski Outbox Sorgusunun Kaldırılması:** Incremental pull akışındaki (`IncrementalRemoteSync`) iki merchant çağrısında (`applyMerchantVerification` altındaki conflict ve preserve yolları) genel `getFirstOutboxOperationId` kullanımı tamamen kaldırıldı. Diğer entity akışlarına dokunulmadı.
- **Merchant'a Özel Aktif Tail Sorgusu:** `RemoteSyncDao` içine `LocalMutationDao.getActiveTailCandidates` deseniyle uyumlu `getActiveMerchantVerificationTailCandidates(syncScopeKey, entityId)` sorgusu eklendi (`sync_scope_key`, `entity_type_code = 'USER_MERCHANT_VERIFICATION'`, `entity_id` filtreli; `predecessor_operation_id` alt kümesinde bulunmayan satırları seçer; birden fazla aktif tail bozukluğunu tespit etmek için `LIMIT 2` taşır).
- **Transaction İçinde Tail Çözümü ve TOCTOU Koruması:** Preserve yolu (`advanceUserMerchantVerificationPullCursor`) dışarıdan `operationId` almaz; aktif tail'i `@Transaction` içinde çözer ve doğrular. Conflict yolu (`recordUserMerchantVerificationPullConflict`) dışarıdan aldığı `conflict.operationId` değerinin `@Transaction` içinde çözülen tek aktif tail ile birebir aynı olduğunu fail-closed doğrular.
- **Topoloji ve Yaşam Döngüsü Invariantları:** `validateLiveUserMerchantVerificationOutbox` içinde:
  - Root tail için `predecessorOperationId == null` ve `isBlocked == false` (boolean alan).
  - Successor tail için `predecessorOperationId != null` ve `isBlocked == true` (boolean alan). Successor'ın predecessor satırı aynı scope/entity/type altında bulunmalı, `protocolVersion == 2`, `statusCode == "IN_FLIGHT"` ve `isBlocked == false` olmalı. Predecessor terminal `FAILED` olamaz.
  - Orphan successor, blocked root, predecessor'ı olduğu halde unblocked successor ve birden fazla aktif tail fail-closed reddedilir.
  - Canlı outbox `statusCode` allow-list'i: `"PENDING"`, `"IN_FLIGHT"`, `"CONFLICT"` (modelde RETRY veya BLOCKED adında statusCode yoktur; bloklama `isBlocked` boolean alanıyla temsil edilir). Terminal `"FAILED"` kesinlikle reddedilir.
- **Doğrulama:** 32 hedefli test (`UserMerchantVerificationIncrementalSyncTest`), 52 hedefli DAO testi (`UserMerchantVerificationPullDaoTest`), 15 gerileme testi (`IncrementalRemoteSyncTest`), tüm `:sharedLogic:testAndroidHostTest`, `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. B2-A/R2 teknik denetimden geçti ve tamamlandı.

**İlerleme Notu (8.6-D2-B3-A-R1 — Equivalent Recovery Transaction Binding — denetimden geçti ve tamamlandı — 2026-10-17):**
- **Canlı Conflict Metadata Doğrulaması:** `LocalMutationDao.recoverEquivalentUserMerchantVerification` içinde canlı transaction verileriyle metadata doğrulaması: `conflict.localVersion == decodedLocalSnapshot.version == 0L`, `conflict.remoteVersion == decodedRemoteSnapshot.version == remoteRecord.version >= 1L`.
- **Predecessor CREATE Payload Bağı:** `op.payloadJson` non-null ve non-blank olmalı. `UserMerchantVerificationPayloadCodec.parseAndValidate(op.payloadJson, "CREATE", null)` ile protocolVersion=2 CREATE / baseVersion=null katı codec doğrulamasından geçirilir. Decode edilen predecessor payload'ın 7 iş alanı (`id`, `userId`, `workspaceId`, `alias`, `decision`, `merchantId`, `scope`) `conflictLocalDto` ile birebir eşleşmeli.
- **Transaction İçi 7 Alan Eşdeğerliği ve Tombstone Reddi:** `conflictLocalDto` ile `conflictRemoteDto` arasındaki 7 iş alanı eşdeğerliği transaction içinde yeniden doğrulanır; her iki tarafta da tombstone (`deletedAt != null`) anında fail-closed reddedilir.
- **Successor Yoksa (`successor == null`):** `conflictLocalDto` mevcut local entity'ye bağlı kalır (`require(conflictLocalDto matches local)`). Tek aktif root tail ve diğer invariantlar fail-closed korunur.
- **Successor Varsa (`successor != null`):**
  - `successor.operationTypeCode == "UPDATE"` kesinlikle zorunlu; `CREATE` veya `DELETE` fail-closed `IllegalStateException` fırlatarak tam Room transaction rollback üretir.
  - Predecessor CREATE root ve unblocked olmalı.
  - Successor `PENDING`, `attemptCount == 0`, `isBlocked == true`, `protocolVersion == 2`, `predecessorOperationId == conflictOp.operationId` ve pre-ACK `baseVersion == null` olmalı.
  - Successor pre-ACK payload biçimi codec düzeyinde `UserMerchantVerificationPayloadCodec.parseAndValidate(successor.payloadJson, "CREATE", null)` ile doğrulanır (çünkü codec `UPDATE` işlemi için `baseVersion >= 1` zorunlu kılar; henüz sunucu ACK almamış ve remote versiyon atanmamış successor payload'ı schema düzeyinde `baseVersion = null` taşıyan CREATE biçimindedir); buna karşılık successor operasyon türü (`operationTypeCode`) kesinlikle `UPDATE` olarak kalır ve Room entity'sinin tüm 7 iş alanına bağlanır.
  - Recovery sonrasında successor `UPDATE` olarak kalır, `baseVersion = remoteRecord.version` olarak atanır, `unblockSuccessor` ile serbest bırakılır ve yerel karar korunur.
- **Cancellation Güvenliği (`ConflictRecoveryService`):** Suspend `recoverEquivalentUserMerchantVerification` çağrısı genel `runCatching` ile sarılmaz. `CancellationException` koşulsuz yeniden fırlatılır; yalnızca beklenen validation/stale decode hataları (`IllegalArgumentException`, `IllegalStateException`) yakalanıp `false` döner; `Error`/`OOM` fatal throwable'ları kesinlikle yutulmaz.
- **Doğrulama:** 38 DAO testi (`UserMerchantVerificationLocalMutationDaoTest` — 5 yeni regresyon testi: predecessor CREATE payload karar/merchant uyuşmazlığı rollback, localVersion snapshot uyuşmazlığı rollback, remoteVersion remote snapshot/record uyuşmazlığı rollback, blocked successor CREATE rollback, blocked successor DELETE rollback; her negatif senaryoda entity, predecessor, successor, conflict ve cursor snapshot'larının değişmeden kaldığı kanıtlandı), 19 test (`ConflictRecoveryServiceTest` — CancellationException'ın false'a çevrilmeden yeniden fırlatıldığını kanıtlayan test dahil), 20 test (`EquivalentConflictResolverTest`), 53 DAO testi (`UserMerchantVerificationPullDaoTest`), 30 hedefli senaryo (`UserMerchantVerificationInitialSyncTest`), 32 incremental sync testi (`UserMerchantVerificationIncrementalSyncTest`), 1 test (`InitialRemoteSyncTest`), 2048 test (`:sharedLogic:testAndroidHostTest` — 173 test dosyası, 0 failure, 0 error, 0 skipped), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint ve `git diff --check` denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. B3-A-R1 ve Faz 8.6-D2-B3-A denetimden geçti ve tamamlandı olarak kaydedildi. Ana 8.6-D checkbox'ı açık tutuldu; B3-B'ye geçilmedi.

**İlerleme Notu (8.6-D2-B3-A — User Merchant Verification Equivalent CREATE Recovery — denetimden geçti ve tamamlandı — 2026-10-17):**
- **Equivalent Conflict Resolver Genişletmesi:** `EquivalentConflictResolver` içine `SyncEntityType.USER_MERCHANT_VERIFICATION` dalı eklendi. Yalnızca `operationTypeCode == "CREATE"` durumu değerlendirilir (`UPDATE` ve `DELETE` non-equivalent kabul edilir). `localPayloadJson` ve `remotePayloadJson` `UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot` ile sıkı decode edilir; tombstones (`deletedAt != null`) anında reddedilir; sunucu mutable alanları (`version`, `createdAt`, `updatedAt`) karşılaştırmadan hariç tutularak yetkili alanlar (`id`, `ownerId`/`userId`, `workspaceId`, `scopeCode`, `normalizedAlias`, `decisionCode`, `merchantId`) birebir eşleştiğinde `isEquivalent = true` üretilir.
- **Conflict Recovery Service Entegrasyonu:** `ConflictRecoveryService.recoverSingleEquivalentCreate` içine `USER_MERCHANT_VERIFICATION` dalı eklendi. Codec ile doğrulanmış remote DTO üretilerek `LocalMutationDao.recoverEquivalentUserMerchantVerification` atomik transaction'ına devredilir. `recoverAllPendingConflicts` tarama döngüsüne merchant çakışmaları dahil edildi.
- **Fail-Closed LocalMutationDao Recovery Transaction:** `LocalMutationDao.recoverEquivalentUserMerchantVerification` tek `@Transaction` sınırında:
  - Actor scope doğrulaması (`SyncScopeKey.requireUserScope`),
  - Çakışma satırının varlığı, scoped key, entity type ve conflict status kontrolü,
  - Conflict local/remote snapshot'larının `UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot` ile doğrulanması,
  - Successor-free durumda (`successor == null`): Güncel Room entity'sinin `localPayloadJson` ile birebir eşleştiği teyit edilir, remote entity Room'a upsert edilir, çakışma ve outbox CREATE operasyonu atomik silinir.
  - Blocked successor durumunda (`successor != null`): Successor outbox'ının `protocolVersion == 2`, `isBlocked == true`, `predecessorOperationId == conflictOp.operationId` ve payload'ının güncel Room entity'si ile birebir eşleştiği doğrulanır; local entity sürümü remote versiyona rebase edilir (`version = remote.sync.version`, `baseVersion = remote.sync.version`), predecessor CREATE outbox'ı silinir, successor `unblockSuccessor` ile `is_blocked = 0` ve `base_version = remote.sync.version` yapılarak serbest bırakılır, çakışma satırı silinir.
- **Doğrulama:** 33 DAO testi (`UserMerchantVerificationLocalMutationDaoTest`), 20 test (`EquivalentConflictResolverTest`), 18 test (`ConflictRecoveryServiceTest`), 53 DAO testi (`UserMerchantVerificationPullDaoTest`), 30 hedefli senaryo (`UserMerchantVerificationInitialSyncTest`), 32 incremental sync testi (`UserMerchantVerificationIncrementalSyncTest`), 1 test (`InitialRemoteSyncTest`), 2042 test (`:sharedLogic:testAndroidHostTest` — 173 test dosyası, 0 failure, 0 error, 0 skipped), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. B3-A ve B3-A-R1 denetimden geçti ve tamamlandı. B3-B'ye geçilmedi; 8.6-D ana checkbox'ı açık tutuldu. Sıradaki dilim: **Faz 8.6-D2-B3-B — User Merchant Verification Manual Resolution & Interactive Conflict Handling**.

**İlerleme Notu (8.6-D2-B2-B-R2 — Zorunlu Expected Local Entity Kapanışı — denetimden geçti ve tamamlandı — 2026-10-16):**
- **Zorunlu Non-Null Sözleşme:** `RemoteSyncDao.recordUserMerchantVerificationPullConflict` ve `InitialMerchantVerificationCommand.Conflict` içindeki `expectedLocalEntity: UserMerchantVerificationEntity` parametresi zorunlu (non-null) yapıldı ve varsayılan `= null` değeri kaldırıldı. Nullable/default overload veya parametre ile kontrolü atlayan hiçbir ikinci yol bırakılmadı.
- **Fail-Closed Transaction İçi Eşitlik:** Transaction içinde `require(local == expectedLocalEntity)` doğrulaması korunarak `localUpdatedAtEpochMillis` dahil tam eşitlik sağlandı. `InitialRemoteSync` ve `IncrementalRemoteSync` çağrıları ile tüm DAO testleri, transaction öncesinde okunan gerçek entity snapshot'ını açıkça iletir.
- **Doğrudan DAO Regresyon Testi:** `UserMerchantVerificationPullDaoTest` içine Room'daki güncel entity ile `expectedLocalEntity`'nin yalnızca `localUpdatedAtEpochMillis` alanında farklı olduğu durumda `IllegalArgumentException` fırlatan ve Room entity, outbox, conflict, cursor snapshot'larının hiçbirinin değişmediğini kanıtlayan `recordConflict_expectedLocalEntityLocalUpdatedAtMismatch_rejectsAndRollsBack` eklendi.
- **Doğrulama:** 53 DAO testi (`UserMerchantVerificationPullDaoTest`), 30 hedefli senaryo (`UserMerchantVerificationInitialSyncTest`), 32 incremental sync testi (`UserMerchantVerificationIncrementalSyncTest`), 1 test (`InitialRemoteSyncTest`), 2019 test (`:sharedLogic:testAndroidHostTest` — 173 test dosyası, 0 failure, 0 error, 0 skipped), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug` ve ktlint denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korundu. B2-B/R2 denetimden geçti ve tamamlandı olarak kaydedildi. D2-B3 conflict recovery'ye geçilmedi; 8.6-D ana checkbox'ı açık tutuldu.

**İlerleme Notu (8.6-D2-B2-B-R1 — Conflict Snapshot Transaction Binding — 2026-10-16):**
- **Snapshot TOCTOU ve Transaction İçi Semantik Eşdeğerlik:** `recordUserMerchantVerificationPullConflict` içinde hem `localPayloadJson` hem `remotePayloadJson` `UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot` ile decode edilip yetkili alanlar (`id`, `ownerId`/`userId`, `workspaceId` null, `normalizedAlias`, `decisionCode`, `merchantId`, `scopeCode` PERSONAL, `version`, `createdAt`, `updatedAt`, `deletedAt`) epoch/Instant semantiğiyle transaction içinde yeniden okunan entity ile doğrulandı.
- **Coalesce Yarışı Engeli ve Local Concurrency Token:** Transaction dışından gelen entity'nin transaction içinde coalesce edilmesi riskine karşı `expectedLocalEntity` parametresi eklendi; transaction içinde Room'dan yeniden okunan güncel `local` entity ile tam eşitlik (`local == expectedLocalEntity`) ve DTO'da bulunmayan yerel concurrency alanı `localUpdatedAtEpochMillis` bağlandı. Versiyon değişmese dahi yerel karar, merchant veya timestamp değiştiğinde stale snapshot fail-closed reddedilir.
- **Remote Snapshot Bağı:** `remotePayloadJson`'ın `remoteEntity` ile aynı authoritative alanları taşıdığı doğrulandı. Başka merchant'a, başka karara veya başka timestamp'e ait remote snapshot reddedilir.
- **Gerçek Çoklu-Command In-Transaction Rollback Kanıtı:** İlk merchant command transaction içinde başarıyla Apply edildikten sonra ikinci merchant command transaction içinde (outbox/topoloji/stale snapshot) hata verdiğinde, tüm 7 tabloda (`profiles`, `categories`, `transactions`, `user_merchant_verifications`, `sync_operations`, `sync_conflicts`, `sync_cursors`) tam rollback kanıtlandı (`firstMerchantValidApply_secondMerchantTransactionTopologyFailure_rollsBackAllWrites`).
**İlerleme Notu (8.6-D2-B3-B — User Merchant Verification Manual KEEP_LOCAL / KEEP_REMOTE Conflict Resolution — denetimden geçti ve tamamlandı — 2026-10-18):**
- **Room @Transaction Primitifleri:** `RemoteSyncDao` üzerinde tipli precondition taşıyan `resolveUserMerchantVerificationKeepRemote` ve `resolveUserMerchantVerificationKeepLocal` metotları oluşturuldu. Generic veya doğrulamasız resolution yolları merchant için tamamen dışlandı.
- **Fail-Closed Ortak Precondition:** Her iki çözüm akışı için transaction içinde: `SyncScopeKey.requireUserScope`, aktör `userId` ile entity `ownerId` ve snapshot `userId` tam eşitliği, `workspaceId == null`, `scopeCode == PERSONAL`, canonical lowercase UUID'ler, deterministik UUIDv8 eşitliği, canlı `SyncConflictEntity` ile beklenen conflict snapshot eşitliği, `UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot` ile local/remote snapshot katı doğrulaması, `remoteVersion >= 1`, canlı entity ile `expectedLocalEntity` arasında `localUpdatedAtEpochMillis` dahil tam eşitlik, canlı outbox satırları ile beklenen snapshot'lar arasında sıra ve topoloji eşitliği, tek aktif V2 tail ve geçerli IN_FLIGHT CREATE predecessor + blocked UPDATE successor zinciri fail-closed doğrulandı.
- **KEEP_REMOTE Davranışı:** Doğrulanmış remote snapshot (aktif veya soft-delete tombstone) `SYNCED` metadata ile Room'a uygulandı (`deletedAtEpochMillis` korunur, hard-delete yapılmaz). Varlığa ait outbox zinciri ve çakışma satırı aynı Room transaction'ında atomik silinir; diğer scope ve entity operasyonları korunur.
- **KEEP_LOCAL Davranışı:** Güncel Room entity'sindeki yerel karar korunur (eski `conflict.localPayloadJson` sessizce üzerine yazılmaz). Remote versiyon authoritative base versiyon olarak alınır. Yerel aktif kayıt `PENDING_UPDATE`, tombstone ise `PENDING_DELETE` durumuna rebase edilir. CREATE çakışmasında successor yoksa retained işlem güncel karardan türetilen kanonik payload ile UPDATE retry'a dönüştürülür. IN_FLIGHT CREATE predecessor + blocked UPDATE successor topolojisinde predecessor kaldırılır, successor güncel payload ve remote versiyonla unblock edilip `PENDING` UPDATE olarak kuyruğa alınır. Çakışma satırı silinir.
- **Repository Entegrasyonu:** `OfflineFirstSyncRepository.resolveConflict` içinde `SyncEntityType.USER_MERCHANT_VERIFICATION` için açık KEEP_LOCAL ve KEEP_REMOTE dalları bağlandı. Oturum sahibi dışındaki çakışmalar fail-closed reddedilir. `UserMerchantVerificationConflictStaleResolutionException` ve strict decode hataları tipli `AppError.Conflict("sync.conflict_resolution_stale")` sonucuna eşlenir. `CancellationException` koşulsuz yeniden fırlatılır; fatal `Error`/`OOM` kesinlikle yutulmaz.
- **Doğrulama:** 20 zorunlu senaryonun tamamını kapsayan hedefli `UserMerchantVerificationConflictResolutionDaoTest` (20 test, 0 failure, 0 error), 55 test (`OfflineFirstSyncRepositoryTest`), tüm `:sharedLogic:testAndroidHostTest` testleri (27 actionable task, BUILD SUCCESSFUL), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, `sharedLogic` ktlint ve `git diff --check` denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) başlangıç ve bitişte birebir doğrulandı. B3-B ve B3-B-R1 denetimden geçti ve tamamlandı. Ana Faz 8.6-D checkbox'ı açık tutuldu; Faz 8.6-E'ye geçilmedi. Sıradaki iş: 8.6-D kapanış envanteri ve kabul denetimi.

**İlerleme Notu (8.6-D2-B3-B-R1 — User Merchant Verification Real 2-Op Topology ve Test Seam Temizliği — denetimden geçti ve tamamlandı — 2026-10-18):**
- **Gerçek 2-Operasyon Topolojisi Sözleşmesi:** `RemoteSyncDao.validateMerchantVerificationConflictPrecondition` ve `OfflineFirstSyncRepository.resolveConflict` akışlarında tek operasyonlu ve iki operasyonlu topolojiler ayrı, açık ve fail-closed ayrıştırıldı. İki satırlı zincirde (`root CREATE + blocked successor UPDATE`):
  - Root/predecessor operasyon `CREATE`, `statusCode == CONFLICT`, `baseVersion == null`, `predecessorOperationId == null` ve unblocked olmalı.
  - Canlı çakışmanın `conflict.operationId` değeri doğrudan root/predecessor operasyon ID'si ile eşleşmeli (`conflictOpId == root.operationId`).
  - Blocked successor operasyon `UPDATE`, `statusCode == PENDING`, `isBlocked == true`, `attemptCount == 0`, `protocolVersion == 2`, `predecessorOperationId == root.operationId` ve pre-ACK `baseVersion == null` olmalı.
  - Successor payload'ı pre-ACK sözleşmesine uygun olarak `UserMerchantVerificationPayloadCodec.parseAndValidate(successor.payloadJson, "CREATE", null)` ile doğrulanır (codec UPDATE için `baseVersion >= 1` aradığından, henüz uzak versiyon atanmamış successor payload'ı schema düzeyinde CREATE biçimindedir); buna karşılık operasyon türü kesinlikle `UPDATE` olarak kalır ve payload güncel Room entity'sinin yedi iş alanına (`id`, `ownerId`/`userId`, `workspaceId`, `scopeCode`, `normalizedAlias`, `decisionCode`, `merchantId`) birebir bağlanır.
- **KEEP_LOCAL Davranışı:** Retained operation successor `UPDATE` olarak seçilir; predecessor `CREATE` atomik silinir; successor `UPDATE` olarak kalır, `predecessorOperationId = null`, `isBlocked = false`, `statusCode = PENDING`, `attemptCount = 0`, `baseVersion = remoteVersion` yapılır ve güncel yerel entity'den yeni kanonik UPDATE payload'ı üretilir; yerel entity `remoteVersion` üzerine `PENDING_UPDATE` olarak rebase edilir; çakışma silinir ve yerel kullanıcı kararı korunur.
- **KEEP_REMOTE Davranışı:** Doğrulanmış uzak snapshot (`SYNCED`) yerel entity'ye uygulanır; tek Room transaction'ında hem predecessor `CREATE` hem successor `UPDATE` atomik silinir; çakışma silinir.
- **Test-Only Üretim Seam'inin Kaldırılması:** `RemoteSyncDao` içindeki yapay `executeUserMerchantVerificationAtomicMutationWithFailureForTest` metodu ve yorumları üretim kodundan tamamen kaldırıldı.
- **Gerçek Transaction Rollback Kanıtı:** Üretim koduna hiçbir test seam eklemeden, test veritabanında SQLite trigger (`CREATE TEMP TRIGGER ... BEFORE DELETE ON sync_operations ... RAISE(ABORT, 'mid-transaction simulated error')`) kullanılarak gerçek `resolveUserMerchantVerificationKeepRemote` transaction'ının ortasındaki SQL adımında hata üretildi ve entity, predecessor outbox, successor outbox ve conflict satırlarının byte-for-byte rollback olduğu kanıtlandı (`test20`).
- **Doğrulama ve Kapsam:** `UserMerchantVerificationConflictResolutionDaoTest` içindeki test sayısı 20'den 27'ye çıkarıldı (tüm pozitif senaryolar, gerçek zincirde KEEP_LOCAL/KEEP_REMOTE, conflict.operationId successor'a bağlı fail-closed ret, predecessor IN_FLIGHT ret, successor CONFLICT ret, successor baseVersion non-null ret, successor payload UPDATE / baseVersion=null ret, successor payload yerel entity ile uyuşmazlık ret ve byte-for-byte rollback kanıtları; 27 test, 0 failure, 0 error). Toplam 2075 Android host testi (`:sharedLogic:testAndroidHostTest` — 174 suite, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint ve `git diff --check` denetimleri tam başarıyla tamamlandı. D1 dosya hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) başlangıçta ve bitişte birebir korundu. B3-B-R1 ve B3-B denetimden geçti ve tamamlandı; ana Faz 8.6-D checkbox'ı açık tutuldu; Faz 8.6-E'ye geçilmedi. Sıradaki iş: 8.6-D kapanış envanteri ve kabul denetimi.

**İlerleme Notu (8.6-D Kapanış Envanteri ve Kabul Denetimi — denetimden geçti ve tamamlandı — 2026-10-18):**
- **Nihai Karar:** Faz 8.6-D KABUL (P0/P1/P2 bulgu yoktur; tüm alt dilim sözleşmeleri ve kapıları eksiksizdir).
- **Kanıt Matrisi Özeti:**
  - *D1 (Supabase & Remote):* `20261015144943_sync_write_v2_user_merchant_verifications.sql`, RLS, BOLA engeli, deterministik RFC 9562 UUIDv8, canonical lowercase UUID, 15 entity type receipt constraint ve `sync_write_v2` RPC; izole yerel SQL contract runner ile 33 migration sıralı apply, tüm testler %100 PASS, tam ROLLBACK atomikliği ve D1 SHA-1 hash bütünlüğü (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) kanıtlandı.
  - *D2-A (Atomik Mutation & Outbox & ACK):* Room v24 `LocalMutationDao.mutateUserMerchantVerificationV2` tek `@Transaction` sınırında entity + V2 outbox atomikliği; CREATE/UPDATE/DELETE coalescing ve unblock rebase; `V2OutboxOperationExecutor` APPLIED, CONFLICT ve DELETE NOT_FOUND akışları fail-closed tamamlandı (38 DAO testi, 12 executor testi).
  - *D2-B1 (Pull DAO Primitifleri):* `RemoteSyncDao.applyUserMerchantVerificationPull` ve conflict/cursor primitifleri; actor scope, cursor invariantları, deterministik UUIDv8, 15 authoritative alan eşdeğerliği ve pending/conflict koruması (53 DAO testi).
  - *D2-B2-A (Incremental Pull Wiring):* Active-tail outbox seçimi (TOCTOU engeli), cross-user fail-closed izolasyon, `CancellationException` propagation ve fetch iptali sonrası sıfır Room yazımı kanıtlandı (32 incremental sync testi).
  - *D2-B2-B (Initial Snapshot Wiring):* Tek global `@Transaction` sınırında 7 tablo bütünlüğü ve multi-command rollback kanıtı (30 initial sync testi).
  - *D2-B3-A (Equivalent CREATE Recovery):* Yalnızca CREATE işlemi için 7 authoritative alan birebir eşleştiğinde recovery; blocked UPDATE successor unblock ve cancellation güvenliği (20 resolver, 19 recovery testi).
  - *D2-B3-B & R1 (Manual KEEP_LOCAL / KEEP_REMOTE):* Gerçek 2-operasyon topolojisi (`root CREATE conflict + blocked successor UPDATE`), `conflict.operationId` doğrulaması, pre-ACK codec kuralı ve SQLite trigger ile test seam'siz gerçek transaction rollback kanıtlandı (27 DAO testi, 2075 Android host testi).
- **Test ve Bütünlük:** Toplam 174 test suite, 2075 test (%100 PASS, 0 failure, 0 error, 0 skipped), iOS Simulator Arm64 derlemesi, Android debug APK derlemesi ve temiz `git diff --check`. Uzak Supabase etkisi kesin olarak SIFIR'dır; hiçbir migration uygulanmamış, commit/push yapılmamıştır.
- **Faz 8.6-C Değerlendirmesi:** Saf domain (C1), Room v24 SSOT modelleri (C2) ve Faz 8.6-C3 üretim wiring'i (transaction create/installment/update, recurring materialization, backup import) nihai kabul denetiminden geçti ve tamamlandı; ana 8.6-C checkbox'ı kapatıldı.
- **Sıradaki Güvenli Adım:** Faz 8.6-E — Paketli logo varlıkları ve ortak MerchantLogo UI bileşeni.

### 8.6-E — Paketli logo varlıkları ve ortak UI bileşeni

- Kullanım hakkı doğrulanmış ilk logo paketini ekle; kaynağı/lisansı envanterle.
- `MerchantLogo` ortak bileşeni ve tam fallback zincirini uygula.
- İşlem listesi, işlem detayı, dashboard son işlemler ve başarı ekranına bağla.
- 75 altı, belirsiz, logo eksik ve koyu/açık tema durumlarını test et.
- Tüm yeni kullanıcı metinlerini aynı değişiklikte TR/EN kataloglarına ekle.
- Kapı: sharedUI testleri, Compose runtime TR->EN testi, erişilebilirlik semantics testi ve APK.

**İlerleme Notu (8.6-E1 — MerchantLogo Fallback Çekirdeği, Lisans Envanteri ve Sözleşme Senkronizasyonu — 2026-10-18):**
- **Fallback Önceliği Senkronizasyonu:** Proje sahibinin açık onayıyla fallback hedef sırası: 1) gelecekte kullanıcı seçili güvenli yerel görsel; 2) kullanım hakkı doğrulanmış paketli logo; 3) kategori ikonu; 4) locale-aware baş harf; 5) genel işlem ikonu olarak senkronize edildi. Kullanıcı görseli veri modelinde henüz yer almadığından, güncel efektif zincir: `doğrulanmış paketli logo → kategori ikonu → baş harf → genel işlem ikonu` olarak sabitlendi.
- **Lisans ve Yeniden Dağıtım İzin Envanteri:** Kamuya açık birincil marka kaynakları taranarak `docs/MERCHANT_LOGO_KAYNAK_VE_IZIN_ENVANTERI.md` belgesi oluşturuldu. Başlangıçtaki 7 kurumsal marka incelendiğinde; mobil APK/IPA içinde offline paketlenerek dağıtılabileceği kanıtlanmış (`VERIFIED_FOR_BUNDLING`) logo sayısı **0 (sıfır)** olarak kaydedildi.
- **Sahte Varlık Engeli ve Boş Registry:** Hiçbir telif riski taşıyan veya yeniden çizilmiş ticari asset eklenmedi; `MerchantLogoRegistry` boş olarak kuruldu.
- **Fallback Çekirdeği:** `CategorySemanticIconResolver.resolveOrNull` API'si ve saf `MerchantLogoFallbackResolver` ile çökmeyen tipli fallback zinciri (`PACKAGED_LOGO`, `CATEGORY_ICON`, `INITIAL`, `GENERIC_TRANSACTION_ICON`) ile Türkçe duyarlı `MerchantInitialHelper` oluşturuldu.
- **Kapsam Sınırı:** İşlem listesi, detay, dashboard ve başarı ekranı wiring'ine geçilmedi; ana 8.6-E checkbox'ı açık bırakıldı. Sıradaki iş: 8.6-E2/E3 display model ve dört yüzey wiring'i.

**İlerleme Notu (8.6-E1-R2 — Unicode Code Point ve Fallback Kanıtı Kapanışı — tamamlandı, denetim bekliyor — 2026-10-19):**
- **Tam Unicode Code Point Desteği:** `MerchantInitialHelper.extractInitial` UTF-16 surrogate taraması yerine supplementary plane code point'lerini (`Character.toCodePoint` / bit manipulation ve UTF-16 pair desteği) destekleyecek şekilde güncellendi. Deseret (`U+10400..U+1044F`), Osage ve Adlam gibi BMP dışı harfler parçalanmadan tanınır, Deseret küçük harfleri büyük harfe dönüştürülür ve sonraki Latin harfe atlama yapılmaz; baştaki rakam/sembol/noktalama atlanmaya devam eder, Türkçe `i/ı/İ/I` davranışı korunur.
- **Accessibility Görsel vs Semantik Kanıtı:** `MerchantLogoComposeTest` içinde INITIAL dalı için `useUnmergedTree = true` ile 'İ' görsel harfinin ekranda çizildiği ve `assertIsDisplayed()` olduğu; normal merged tree'de (`useUnmergedTree = false`) ise `assertDoesNotExist()` ile temizlendiği kanıtlandı. Bağımsız modda yalnız caller `contentDescription` doğrulanırken dekoratif modda merged tree'de hiçbir metin veya açıklamanın kalmadığı çift anons riski olmadan doğrulandı.
- **Registry Resolution Failure Test Kapanışı:** Paketli logo seçilip runtime'da registry `null` döndürdüğünde tam fallback zinciri (`PACKAGED_LOGO -> CATEGORY_ICON -> INITIAL -> GENERIC_TRANSACTION_ICON`) saf `MerchantLogoFallbackResolver.resolveWithRegistryFallback` fonksiyonuyla test edilebilir hale getirildi; sahte marka varlığı eklenmeden 3 zorunlu negatif/fallback durumu saf birim testleriyle kapatıldı.
- **Kapsam ve Sınırlar:** Üretim logo registry'si boş tutuldu, yeni asset eklenmedi. Dört UI yüzeyi wiring'ine ve E2/E3'e başlanmadı. Ana Faz 8.6-E checkbox'ı açık bırakıldı.

**İlerleme Notu (8.6-E1 — MerchantLogo Fallback Çekirdeği, Lisans Envanteri ve R1/R2/R3 Kapanışı — tamamlandı ve denetimden geçti — 2026-10-19):**
- **E1 Teknik Kapanışı:** E1 çekirdeği, E1-R1 (accessibility & generic receipt icon), E1-R2 (tam Unicode code-point desteği) ve E1-R3 (Unicode kategori doğruluğu expect/actual ve tek-aşamalı seam'siz logo resolution) başarıyla denetlenmiş ve **Faz 8.6-E1 tamamlanmıştır**.
- **Mevcut Durum:** E2/E3 başarıyla tamamlanmıştır.

**İlerleme Notu (8.6-E2/E3 — Transaction Merchant Display Mapping ve Dört Yüzey MerchantLogo Wiring — tamamlandı — 2026-10-19):**
- **Tipli Display Modeli:** `TransactionDisplayModel` içine tipli `merchant: TransactionMerchantDisplayModel?` alanı eklendi (`merchantId`, `displayName`, `verifiedLogoKey`, `canShowMerchantLogo`). Domain veya ham Room nesnesi UI'ya sızdırılmaz.
- **Fail-Safe Güven ve Sürüm Karar Matrisi:** `confidence >= 75 (MIN_LOGO_CONFIDENCE)`, katalog merchant'ı bulunmuş ve `transaction catalogVersion <= current merchant catalogVersion` olduğunda çözümlenir (`canShowMerchantLogo = true`). Güven < 75 ise tahmin adı veya kimliği asla presentation'a sızmaz (`merchant = null`). `catalogVersion > current` (gelecek katalog) durumunda fail-safe fallback (`null`) uygulanır.
- **Merkezi Mapping Yolu:** `TransactionsDisplayModelBuilder.buildItem` ve `resolveMerchantDisplay` tek merkez yapıldı; Liste, Detay, Dashboard (`DashboardDisplayModelBuilder`) ve Başarı Ekranı (`TransactionSuccessViewModel`) aynı mapping yoluna bağlandı; elle model üretimleri kaldırıldı. Kategori bulunamadığında fallback korundu.
- **Dört Yüzey MerchantLogo Wiring:**
  1. İşlem listesi (`TransactionListItem`): Sol 42dp avatar `MerchantLogo` ile değiştirildi; dekoratif kalıp çift anons engellendi.
  2. İşlem detayı (`TransactionDetailScreen`): Üst 88dp ikon `MerchantLogo` ile değiştirildi; detay kontratı korundu.
  3. Dashboard son işlemler (`HomeTransactionItemRow`): 40dp ikon `MerchantLogo` ile değiştirildi; merchant dışı widget'lara dokunulmadı.
  4. İşlem başarı ekranı (`TransactionSuccessScreen`): Kategori rozetindeki 36dp ikon `MerchantLogo` ile değiştirildi.
- **Doğrulama ve Testler:**
  - `sharedUI:testAndroidHostTest`: 56 test suite, 416 test (%100 PASS, 0 failure, 0 error). Unicode sınıflandırma sözleşmesi `commonMain` expect + Android `java.lang.Character` actual ile `sharedUI`'nin mevcut Android hedefinde doğrulandı (`sharedUI` Android hedeflidir, iOS actual'ı yoktur).
  - `androidApp:testDebugUnitTest`: 16 test suite, 179 test (%100 PASS, 0 failure, 0 error, 10 hedefli Compose testi dahil).
  - `sharedLogic:testAndroidHostTest`: 175 test suite, 2100 test (%100 PASS, 0 failure, 0 error).
  - `sharedUI` ve `androidApp` ktlint denetimleri eksiksiz temiz (BUILD SUCCESSFUL).
  - `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` başarılı (ortak mantık katmanı iOS hedefi).
  - `check-localization-policy.ps1`: 2478 TR/EN XML, 4 iOS catalog eşleşmesi tam uyumlu.
  - D1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) birebir korundu.
  - Üretim registry'si boş tutuldu, hiçbir sahte/lisanssız logo varlığı eklenmedi.
- **Faz 8.6-E Kapanışı:** Dört yüzey gerçekten `MerchantLogo` kullanıyor, mapping tek merkezli, 75 eşiği fail-safe, fallback/accessibility/localization testleri yeşil, gerçek lisanslı logo olmaması belgelenmiş ve registry boş. **Faz 8.6-E başarıyla tamamlandı ve kapatıldı**. Faz 8.6-F'ye geçilmedi.

### 8.6-F — Kullanıcı düzeltme deneyimi

**İlerleme Notu (8.6-F1 & 8.6-F1-R1 — Fail-Closed Katalog, Sınıflandırma ve Pre-ACK Topoloji Kapanışı — tamamlandı — 2026-10-19):**
- **Atomik Mutation & Attribution Sınırı:**
  - `TransactionDto` remote şemada merchant attribution alanı taşımadığından transaction için yeni V2 outbox üretilmez; transaction sync metadata'sı (`syncStatus`, `version`, `baseVersion`, `updatedAt`, `localUpdatedAt`) ve mevcut TRANSACTION outbox zinciri tamamen korunur.
  - Atomik Room transaction'ı (`LocalMutationDao.applyTransactionMerchantCorrectionV2`) tek transaction sınırında şunları uygular:
    1. `user_merchant_verifications` entity mutasyonu,
    2. bunun `USER_MERCHANT_VERIFICATION` V2 outbox mutasyonu,
    3. aynı satırdaki yerel `TransactionEntity` attribution alanlarının güncellemesi (`merchant_id`, `merchant_match_source_code`, `merchant_confidence`, `merchant_catalog_version`).
- **Fail-Closed Katalog ve DAO Doğrulaması (R1):**
  - Katalog doğrulaması repo seviyesinde bırakılmayıp `LocalMutationDao.applyTransactionMerchantCorrectionV2` içine taşındı: `StarterMerchantCatalog.merchants` fail-closed aranır; `catalogVersion >= 1` ve `assignedCatalogVersion == found.catalogVersion` doğrulanır; bulunamazsa veya sürüm uyumsuzsa `IllegalArgumentException` ile transaction anında rollback olur.
  - Transaction yerel attribution alanları (`merchantId`, `"USER_VERIFIED"`, `100`, `merchantCatalogVersion`) çağıranın parametresine güvenilmeden doğrudan doğrulanmış katalog nesnesinden (`found.id`, `found.catalogVersion`) yazılır.
- **Tek Doğruluk Kaynağı: `TransactionMerchantEligibility` (R1):**
  - `TransactionMerchantEligibility` (`classify`, `isExcluded`, `EXCLUDED_CLASSIFICATIONS`) tek ortak model olarak oluşturuldu.
  - `MerchantRecognitionEngine` içindeki elle kopyalanmış regex/şablon listesi kaldırıldı, tek kaynak olarak `TransactionMerchantEligibility`'e bağlandı.
  - Repository (`OfflineFirstTransactionRepository.correctMerchant`) ve DAO (`applyTransactionMerchantCorrectionV2`) içindeki ad-hoc kontroller kaldırıldı; doğrudan `TransactionMerchantEligibility.isExcluded` kullanılarak tam semantik uyum sağlandı.
- **Pre-ACK Successor ve Outbox Topolojisi (R1):**
  - `mutateUserMerchantVerificationV2` içine katı topoloji kontrolleri eklendi (protocolVersion 2, syncScopeKey, entityId, orphan successor, predecessor protocol 2, terminal/FAILED predecessor kontrolü, root isBlocked kontrolü).
  - Pre-ACK successor zinciri: `tail.baseVersion == null` ve `(tail.statusCode == "IN_FLIGHT" || (tail.predecessorOperationId != null && tail.isBlocked))` durumunda `isPreAckSuccessorChain` ile UPDATE successor ekleme ve mevcut denenmemiş blocked successor varsa coalesce etme topolojisi tam oturtuldu. Kök CREATE in-flight iken ardıl düzeltmeler unblocked kalmaz, `isBlocked = true` ile zincire eklenir.
- **Tipli Hata Sözleşmesi:**
  - `MerchantCorrectionRejectionReason` kapalı enum'ı eklendi (`toAppError()`, `fromCode()`).
  - `TransactionRepository` içindeki generic hata ve `not_implemented` kaldırıldı; dar `TransactionMerchantCorrectionRepository` arayüzü ile tipli `AppError` akışı bağlandı.
- **Doğrulama ve Rollback Kanıtı:**
  - 33 hedefli Robolectric testi (`TransactionMerchantCorrectionDaoTest`): ASSIGN, BLOCK, unknown merchant, blank description, 6 generic token, alias limit, workspace restriction, excluded transaction, owner/scope mismatch, tombstone, coalescing CREATE/UPDATE, IN_FLIGHT blocked successor, multiple tail rollback, SQLite trigger abort ile verification rollback kanıtı, transaction outbox korunumu, transaction domain alanlarının korunumu, 5 Pre-ACK topoloji pozitif testi (in-flight then block/assign, coalesce blocked, rebase/unblock on ACK, ACK failure rollback), 9 DAO topoloji negatif testi ve CancellationException yutulmazlığı.
  - Tüm negatif testlerde `DatabaseStateSnapshot` ile byte-for-byte tam veritabanı snapshot eşitliği doğrulanarak sıfır yazım kanıtlandı.
  - 2133 Android host testi (%100 PASS, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint check ve localization politika kontrolleri başarıyla geçmiştir.
  - D1 SHA-1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korunmuştur.
- **Kapsam:** Yalnızca domain/data/Room atomik mutation katmanı tamamlanmıştır; UI/ViewModel/Compose katmanına geçilmemiş, ana Faz 8.6-F checkbox'ı açık bırakılmıştır (`[ ]`). Sıradaki adım: Faz 8.6-F2 — ViewModel & UI Akışları.

**İlerleme Notu (8.6-F1-R2 — Canonical Merchant Verification Outbox Topology ve Typed Rejection Kapanışı — denetimden geçti ve tamamlandı — 2026-10-19):**
- **Canonical Successor ve Rebased Unblocked Tail Topolojisi:**
  - `mutateUserMerchantVerificationV2` topoloji sınıflandırıcısı `CanonicalMerchantVerificationTailTopology` kapalı durum makinesine dönüştürüldü (`CleanNoTail`, `EligiblePendingRootCreate`, `InFlightRootCreate`, `BlockedPreAckUpdateSuccessor`, `RebasedUnblockedUpdateTail`, `RebasedUnblockedDeleteTail`).
  - **Seçilen Canonical Predecessor Kuralı:** ACK sonrasında predecessor silindiğinde outbox tablosundaki satırın `predecessor_operation_id` referansı silinmiş predecessor'a işaret eden geçmiş bir referans olarak korunur (diğer domain'lerle uyumlu olarak genel `unblockSuccessor` sorgusuna dokunulmamıştır).
  - Topoloji ayrımı `tail.isBlocked` temelinde yapılır: `tail.isBlocked == true` ise kesinlikle `BlockedPreAckUpdateSuccessor` kabul edilir, outbox'ta canlı predecessor zorunlu aranır ve kök CREATE/IN_FLIGHT/unblocked invariantları doğrulanır; `tail.isBlocked == false` ve `baseVersion >= 1L` ise rebased UPDATE tail kabul edilir ve silinmiş predecessor aranmaz (geçmiş referans kalsa dahi orphan sayılmaz). Bu sayede ACK sonrası ardıl düzeltmelerin haksız yere reddedilmesi (Denetim Bulgusu 1) fail-closed ve tüm repoyla %100 uyumlu olarak çözülmüştür.
- **Entity Sync Status Rebase:**
  - `rebaseUserMerchantVerificationVersion` metodu, successor unblock edildiğinde entity'nin `sync_status` alanını da atomik olarak `'PENDING_UPDATE'` durumuna günceller (artık `PENDING_CREATE` kalmaz).
- **Temiz Paket Ayrımı ve Tipli Rejection:**
  - Model paketindeki `RepositoryResult` ve repository bağımlılıkları temizlendi; `domain.repository.TransactionMerchantCorrectionRepository` arayüzü izole edildi.
  - `AUTH_SESSION_REQUIRED("auth_session_required")` hata nedeni eklendi. `toAppError()` eşlemesi: `AUTH_SESSION_REQUIRED, OWNER_MISMATCH -> AppError.Authentication`, `STORAGE_FAILURE -> AppError.Storage`, diğerleri (`WORKSPACE_RESTRICTED`, `EXCLUDED_TRANSACTION`, `INVALID_MERCHANT`, `INVALID_ALIAS`, `GENERIC_MERCHANT_ALIAS`, `TOMBSTONE_CONFLICT`) -> `AppError.Validation` olarak fail-closed bağlandı.
  - `OfflineFirstTransactionRepository.correctMerchant` null session durumunda generic validation yerine tipli `AUTH_SESSION_REQUIRED.toAppError()` döner.
- **Doğrulama ve Kapsam:**
  - 14 yeni hedefli regresyon testi eklendi (`TransactionMerchantCorrectionDaoTest` toplam 47 test): ACK sonrası ardıl düzeltme, unblock sonrası entity sync_status PENDING_UPDATE doğrulaması, blocked predecessor yokluğunda rollback, unblocked root CREATE üzerine successor reddi, blocked CREATE/DELETE successor reddi, birden fazla aktif tail rollback, tipli auth_session_required, owner mismatch, storage failure, workspace restricted validation ve unblocked UPDATE tail üzerine blocked successor ekleme.
  - 38 hedefli DAO testi (`UserMerchantVerificationLocalMutationDaoTest`), 2147 Android host testi (%100 PASS, 176 suite, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint check ve localization politika kontrolleri tam başarıyla tamamlandı.
  - Room şema sürümü (v24) ve D1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korunmuştur.
  - Ana Faz 8.6-F checkbox'ı açık tutuldu (`[ ]`); Faz 8.6-F2 ViewModel/UI kapsamına geçilmedi. Sıradaki adım: Faz 8.6-F2 — ViewModel & UI Akışları.

**İlerleme Notu (8.6-F1-R4 — Merchant Verification ACK Response/Operation Binding Kapanışı — denetimden geçti ve tamamlandı — 2026-10-20):**
- **Remote DTO Tam Doğrulaması:**
  - `LocalMutationDao.ackUserMerchantVerificationWriteV2` içine her Room yazımından önce üçüncü bir paralel validator üretilmeksizin mevcut `UserMerchantVerificationDto.toDomainOrNull()` ve `SyncScopeKey.requireUserScope` sözleşmesi bağlandı.
  - Doğrulanan kurallar: canonical UUID id ve userId, yetkili user eşleşmesi (`record.userId == userScope.userId`), `workspaceId == null`, `scopeCode == "PERSONAL"`, canonical `normalizedAlias` (Türkçe normalizasyon ve generic token kontrolü), alias uzunluğu (`MIN_ALIAS_LENGTH..MAX_ALIAS_LENGTH`), deterministik UUID eşleşmesi (`deterministicMerchantVerificationUuid(userId, normalizedAlias) == id`), ASSIGN => `merchantId` non-blank UUID, BLOCK => `merchantId == null`, parse edilebilir `createdAt`, zorunlu ve parse edilebilir `updatedAt`, varsa parse edilebilir `deletedAt` ve `version >= 1L`.
- **ACK Operation Doğrulaması & Allow-List:**
  - Ortak kurallar: `syncScopeKey == operation.syncScopeKey`, `entityTypeCode == "USER_MERCHANT_VERIFICATION"`, `entityId == record.id`, `protocolVersion == 2`, `statusCode == "IN_FLIGHT"`, `attemptCount > 0`, `isBlocked == false`, non-blank `payloadJson`, canlı predecessor olamaz (`predecessorOperationId != null` ise outbox'ta satırı bulunamaz, yani tarihsel referans olmalıdır), `operationTypeCode` yalnız `CREATE`, `UPDATE` veya `DELETE` olabilir (allow-list).
  - CREATE: `baseVersion == null`, payload `CREATE/null` codec ile doğrulanır, `remoteVersion == 1L`, `remote deletedAt == null`.
  - UPDATE: `baseVersion != null && baseVersion >= 1L && baseVersion < Long.MAX_VALUE`, payload `UPDATE/baseVersion` codec ile doğrulanır, `remoteVersion == baseVersion + 1L`, `remote deletedAt == null`.
  - DELETE: `baseVersion != null && baseVersion >= 1L && baseVersion < Long.MAX_VALUE`, payload `DELETE/baseVersion` codec ile doğrulanır, `remoteVersion == baseVersion + 1L`, `remote deletedAt != null` (tombstone zorunlu).
- **Operation Payload ↔ Remote Record 7 Authoritative Alan Bağı:**
  - Decoded operation payload ile remote DTO'nun 7 authoritative alanı birebir eşleşir: `id`, `userId`, `workspaceId`, `normalizedAlias`, `decisionCode`, `merchantId`, `scopeCode`. Sunucu yönetimindeki `version`, `createdAt`, `updatedAt`, `deletedAt` değerleri payload ile değil kendi remote sözleşmeleriyle doğrulanır.
- **Local Entity Bağı ve Atomik Rollback:**
  - **Successor Yokken (`successor == null`):** Outbox tek aktif tail olmalıdır (`getActiveTailCandidates == 1`). Yerel entity zorunludur. Local entity'nin kimlik ve sahiplik alanları (`id`, `ownerId`, `scopeKey`, `scopeCode`, `workspaceId`) doğrulanır. Operation türüne göre yerel durum kontrol edilir: CREATE için `PENDING_CREATE + version=0 + baseVersion=null`; UPDATE için `PENDING_UPDATE + version >= 1 + baseVersion == operation.baseVersion == version`; DELETE için `PENDING_DELETE + deletedAtEpochMillis != null + version >= 1 + baseVersion == operation.baseVersion == version`. Decoded operation payload ile local entity'nin 7 alanı birebir eşleşmeden remote entity Room'a upsert edilmez; doğrulama sonrası `upsertUserMerchantVerificationRow`, `deleteConflictRow` ve outbox silme işlemi atomik tamamlanır.
  - **Successor Varken (`successor != null`):** F1-R3 kuralları korunur (successor tek aktif tail, UPDATE, PENDING, attemptCount=0, blocked, baseVersion CREATE pre-ACK zincirinde null, UPDATE pre-ACK zincirinde >= 1). ACK predecessor payload remote record ile tam eşleşir; local latest entity successor payload ile tam eşleşir. Remote predecessor kararı local latest karar üzerine yazılmaz; yalnız `rebaseUserMerchantVerificationVersion`, conflict silme, predecessor outbox silme ve `unblockSuccessor` çalıştırılır.
  - Tek bir kural ihlalinde tüm Room transaction'ı anında rollback olur; entity, outbox, transaction attribution, conflict ve cursor tabloları byte-for-byte korunur.
- **Doğrulama ve Kapsam:**
  - 25 zorunlu hedefli test eklendi (`TransactionMerchantCorrectionDaoTest` toplam 86 test, %100 PASS): 1. unknown operationType, 2. CREATE baseVersion non-null, 3. UPDATE baseVersion null/0, 4. DELETE baseVersion null/0, 5. CREATE remoteVersion!=1, 6. UPDATE remoteVersion mismatch (<base, ==base, >base+1), 7. DELETE remoteVersion!=base+1, 8. baseVersion Long.MAX_VALUE, 9. malformed deterministic remote ID, 10. non-canonical remote alias, 11. ASSIGN+null merchant, 12. BLOCK+non-null merchant, 13. malformed createdAt, 14. null/malformed updatedAt, 15. malformed deletedAt, 16. CREATE/UPDATE remote tombstone, 17. DELETE remote tombstone eksikliği, 18. malformed operation payload, 19. payload mismatch, 20. successor yokken local entity mismatch, 21. successor varken predecessor payload/remote mismatch, 22. geçerli CREATE ACK, 23. geçerli UPDATE ACK, 24. geçerli DELETE ACK, 25. geçerli predecessor ACK + canonical successor (latest local karar korunur). Tüm negatif testlerde `DatabaseStateSnapshot` ile rollback doğrulandı.
  - `UserMerchantVerificationLocalMutationDaoTest` 38 testinin tamamı yeşil (%100 PASS).
  - Toplam 2186 Android host testi (%100 PASS, 176 suite, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint kontrolleri ve localization politikası sıfır hata ile geçti.
  - D1 SHA-1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) birebir korunmuştur.
  - Ana Faz 8.6-F checkbox'ı açık tutuldu (`[ ]`); Faz 8.6-F2 ViewModel/UI kapsamına geçilmedi. Durum: F1-R4 denetimden geçti ve tamamlandı. Sıradaki adım: Faz 8.6-F2 — ViewModel & UI Akışları.

**İlerleme Notu (8.6-F1-R3 — ACK Transaction Binding, Historical Predecessor Ayrımı ve Entity Invariant Kapanışı — denetimden geçti ve tamamlandı — 2026-10-19):**
- **Fail-Closed ACK Successor Doğrulaması:**
  - `LocalMutationDao.ackUserMerchantVerificationWriteV2` içinde successor tespit edildiğinde, herhangi bir entity veya outbox yazımından önce transaction içinde 15 katı kural fail-closed doğrulanır:
    1. `successor.syncScopeKey == syncScopeKey`,
    2. `successor.entityTypeCode == "USER_MERCHANT_VERIFICATION"`,
    3. `successor.entityId == ACK operation.entityId == remote record.id`,
    4. `successor.protocolVersion == 2`,
    5. `successor.operationTypeCode == "UPDATE"` (DELETE veya CREATE fail-closed reddedilir),
    6. `successor.statusCode == "PENDING"`,
    7. `successor.attemptCount == 0`,
    8. `successor.isBlocked == true`,
    9. `successor.predecessorOperationId == ACK operation.operationId`,
    10. `successor.baseVersion == null` (pre-ACK CREATE zincirinde null olmalı),
    11. ACK operation canlı bir predecessor'a bağlı olamaz (`predecessorOperationId != null` ise outbox'ta satırı bulunamaz, yani tarihsel referans olmalıdır),
    12. `successor.payloadJson` null veya blank olamaz,
    13. pre-ACK successor payload'ı codec seviyesinde CREATE/null sözleşmesiyle ayrıştırılıp doğrulanır,
    14. decoded successor payload'ın 7 authoritative alanı (`id`, `userId`, `workspaceId`, `normalizedAlias`, `decisionCode`, `merchantId`, `scopeCode`) transaction içindeki güncel local entity ile birebir eşleşir,
    15. successor tek aktif tail adayıdır (`getActiveTailCandidates == 1`).
  - Tek bir doğrulama bozulursa hiçbir entity veya outbox yazımı yapılmaz, predecessor silinmez, successor unblock edilmez, transaction attribution ve cursor tabloları değişmez, tüm Room transaction anında rollback olur.
- **Historical Predecessor ile Canlı Predecessor Ayrımı:**
  - Seçilen canonical modelde rebased unblocked UPDATE tail'in `predecessorOperationId` alanı geçmiş referansı korur.
  - Ancak `getOutboxById(scope, predecessorOperationId)` canlı bir satır döndürüyorsa bu tarihsel referans değildir; unblocked tail + canlı predecessor kombinasyonu fail-closed reddedilir ve transaction rollback olur.
  - Predecessor satırı outbox'ta bulunmuyorsa (silinmişse) geçmiş referans olarak meşru kabul edilir.
  - Blocked successor için ise predecessor kesinlikle outbox'ta canlı olmalı ve canonical canlı predecessor kurallarını sağlamalıdır.
- **Entity/Outbox Invariantlarının Kapanışı & UPDATE/DELETE Rebase:**
  - Canonical sınıflandırmada `EligiblePendingRootCreate`, `InFlightRootCreate`, `BlockedPreAckUpdateSuccessor`, `RebasedUnblockedUpdateTail` ve `RebasedUnblockedDeleteTail` invariantları tamamlandı.
  - Kuyrukta unblocked UPDATE tail varken yerel entity'nin de `PENDING_UPDATE` durumunda olması ve `baseVersion == version` katılığı uygulandı.
  - Kuyrukta unblocked DELETE tail varken `type == DELETE` ise `entity.sync.syncStatus == PENDING_DELETE` ve `deletedAt != null`; `type == UPDATE` (silinmiş verifikasyonu canlandırma) ise `entity.sync.syncStatus == PENDING_UPDATE` doğrulanır.
  - Pre-ACK zincirinde blocked DELETE successor üretimi desteklenmediğinden ACK yolunda successor `DELETE` geldiğinde fail-closed reddedilir.
- **Test Adı ve Belge Çelişkilerinin Temizlenmesi:**
  - `unblockedSuccessorWithPredecessor_rejectedFailClosedAndRolledBack` testi gerçek bulguyu ölçecek şekilde düzeltildi (baseVersion >= 1, entity valid, canlı predecessor outbox'ta mevcut, tek ret nedeni unblocked tail + canlı predecessor bozukluğu).
  - Test 24 adı ve yorumlarındaki "predecessorOperationId null olur" çelişkisi temizlendi; seçilen canonical modelin silinmiş predecessor'a ait `predecessorOperationId` tarihsel referansını koruduğu açıkça assert ve test adına yansıtıldı (`createInFlight_blockedUpdateSuccessor_createAck_deletesPredecessorAndPreservesHistoricalRefOnUnblockedSuccessor`).
- **Doğrulama ve Kapsam:**
  - 14 yeni hedefli regresyon testi eklendi (`TransactionMerchantCorrectionDaoTest` toplam 61 test): ACK + successor başka entityId/entityType/CREATE/DELETE/IN_FLIGHT/attemptCount>0/isBlocked=false/predecessorMismatch/baseVersionNonNull/malformedPayload/authoritativeMismatch rollback, unblocked UPDATE + canlı predecessor rollback, unblocked UPDATE + silinmiş predecessor kabul, rebased UPDATE baseVersion>version rollback, entity syncStatus!=PENDING_UPDATE rollback, in-flight root CREATE syncStatus!=PENDING_CREATE rollback.
  - `UserMerchantVerificationLocalMutationDaoTest` 38 testinin tamamı yeşil (%100 PASS).
  - Toplam 2161 Android host testi (%100 PASS, 176 suite, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint check ve localization politika kontrolleri tam başarıyla tamamlandı.
  - D1 SHA-1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korunmuştur.
  - Ana Faz 8.6-F checkbox'ı açık tutuldu (`[ ]`); Faz 8.6-F2 ViewModel/UI kapsamına geçilmedi. Durum: F1-R3 denetimden geçti ve tamamlandı. Sıradaki adım: Faz 8.6-F2 — ViewModel & UI Akışları.

- **Faz 8.6-F1-R5 — Successor ACK Metadata ve Mutation/ACK Topoloji Tutarlılığı Kapanışı (denetimden geçti ve tamamlandı — 2026-10-20):**
  - **Successor ACK Transaction Binding:**
    - `LocalMutationDao.ackUserMerchantVerificationWriteV2` içinde successor bulunduğunda, herhangi bir rebase/outbox silme/unblock öncesinde yerel entity varlığı ve sahiplik alanları (`id`, `ownerId`, `workspaceId == null`, `scopeKey == "user:$userId"`, `scopeCode == "PERSONAL"`) ve tek aktif tail adayı (`getActiveTailCandidates.size == 1`) doğrulanır.
    - **CREATE Predecessor:** `operation.baseVersion == null`, pre-ACK yerel durum `local.syncStatus == "PENDING_CREATE"`, `local.version == 0L`, `local.baseVersion == null`, `successor.operationTypeCode == "UPDATE"`, `successor.baseVersion == null` ve successor payload `CREATE/null` codec formatında doğrulanır.
    - **UPDATE Predecessor (B >= 1L):** `local.syncStatus == "PENDING_UPDATE"`, `local.version == B`, `local.baseVersion == B`, `successor.operationTypeCode == "UPDATE"`, `successor.baseVersion == B` (predecessor taban sürümüyle birebir eşleşme) ve successor payload `UPDATE/B` codec formatında doğrulanır. Entity ve successor B+1 sürümüne rebase/unblock edilir.
    - **DELETE Predecessor + UPDATE Resurrection Successor (B >= 1L):** `local.syncStatus == "PENDING_UPDATE"`, `local.version == B`, `local.baseVersion == B`, `local.deletedAtEpochMillis == null` (yerel canlandırma kararı), `successor.operationTypeCode == "UPDATE"`, `successor.baseVersion == B` ve successor payload `UPDATE/B` formatında doğrulanır. Remote DELETE tombstone yerel kararı ezmez; entity ve successor B+1 sürümüne rebase/unblock edilir.
    - Successor payload ile güncel yerel entity arasında 7 authoritative alan (`id`, `userId`, `workspaceId`, `normalizedAlias`, `decisionCode`, `merchantId`, `scopeCode`) eşitliği doğrulanır. Tek bir kural ihlalinde sıfır yazım yapılır ve tüm Room transaction anında rollback olur.
  - **Fail-Closed DELETE Successor Reddi (Mutation/ACK Tutarlılığı):**
    - Mevcut canonical sözleşme blocked successor için yalnızca UPDATE kabul ettiğinden, `mutateUserMerchantVerificationV2` içinde IN_FLIGHT predecessor (`InFlightRootCreate`, `RebasedUnblockedUpdateTail` (IN_FLIGHT), `RebasedUnblockedDeleteTail` (IN_FLIGHT)) üzerine DELETE successor üretimi herhangi bir entity/outbox yazımından önce (`upsertUserMerchantVerificationRow` öncesinde) fail-closed `IllegalArgumentException` ile reddedilir.
  - **Doğrulama ve Kapsam:**
    - 12 yeni hedefli DAO testi eklendi (`TransactionMerchantCorrectionDaoTest` 86'dan 98 teste çıktı, %100 PASS). Negatif testlerde tam `DatabaseStateSnapshot` eşitliği ile byte-for-byte sıfır yazım kanıtlandı.
    - `UserMerchantVerificationLocalMutationDaoTest` 38 testi yeşil (%100 PASS).
    - Toplam 2198 Android host testi (%100 PASS, 176 suite, 0 failure, 0 error), `:sharedLogic:compileKotlinIosSimulatorArm64`, `:androidApp:assembleDebug`, ktlint check ve localization politika kontrolleri (2478 TR/EN eşliği) tam başarıyla geçti.
    - D1 SHA-1 hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) korunmuştur.
    - Ana Faz 8.6-F checkbox'ı açık tutuldu (`[ ]`); Faz 8.6-F2 ViewModel/UI kapsamına geçilmedi. Durum: F1-R5 denetimden geçti ve tamamlandı. Sıradaki adım: Faz 8.6-F2 — ViewModel & UI Akışları.

- **Faz 8.6-F — Kullanıcı Düzeltme Deneyimi (Teknik uygulama ve otomatik doğrulama tamamlandı — Tarihsel Not: 2026-10-28 tarihli açık karar ile aşağıdaki manuel cihaz/işitsel TalkBack kabul bekleyişi SUPERSEDED edilmiş ve proje sahibi tarafından waive edilerek kapatılmıştır):**
  - **Bağımsız Denetim ve Uygulama Durumu:** F1 (Domain sözleşmesi, Room transaction, V2 outbox, katalog doğrulama, topoloji makinesi, pre/post ACK rebase), F2 (ViewModel state, reentrancy, singleton concrete binding, Compose akışları) ve F2-R1 (Block hata görünürlüğü, hata izolasyonu, retry, deterministik arama, dürüst cancellation sözleşmesi) bağımsız olarak denetlendi. P0/P1/P2 açık teknik bulgu bulunmuyor.
  - **Manuel Kabul Durumu (Tarihsel Kayıt — 2026-10-28'de waive edilmiştir):**
    - **Senaryo A (Assign):** Kişisel ve uygun işlemde "İş yeri seç" / "Değiştir" akışı, arama, seçim ve onaylama sonrası alt sayfa kapanışı ve Room emit'iyle kart güncellemesi — *2026-10-28 itibarıyla otomatik testlerle kabul edilmiş ve TalkBack manuel kontrolü waive edilmiştir*.
    - **Senaryo B (Block):** Tanınmış merchant bulunan işlemde "Doğru değil" onay diyaloğu, dialog kapanışı ve Room SSOT emit'iyle eşleşmenin kaldırılması — *2026-10-28 itibarıyla otomatik testlerle kabul edilmiş ve TalkBack manuel kontrolü waive edilmiştir*.
    - **Senaryo C (Failure / Retry):** Offline-first mimaride yerel mutasyon Room'a çevrimdışı da başarıyla yazıldığından uçak modu hata üretmez; üretim koduna suni test seam'i eklenmeksizin bu senaryonun güvencesi `TransactionDetailViewModelTest` ve Compose testleriyle (Senaryo 19-22) tam olarak kanıtlandı; **manuel testte suni hata enjekte edilmemiştir**.
    - **Senaryo D (İzolasyon):** Workspace veya transfer/maaş gibi exclusion listesindeki işlemlerde merchant correction kartının görünmemesi ve arama iptalinde mutation yazılmaması — *otomatik testlerle tam olarak kanıtlanmıştır*.
    - **Senaryo E (Localization & Erişilebilirlik):** TR ve EN çalışma zamanı dil geçişleri ve TalkBack `MerchantLogo` dekoratif semantiği (çift anons olmaması) — *otomatik Compose testleriyle kanıtlanmış, işitsel kontrol proje sahibi tarafından waive edilmiştir*.
  - **Nihai Kabul Envanteri:**
    - F1 Domain & Storage: `TransactionMerchantCorrection.Assign` / `Block`, `applyTransactionMerchantCorrectionV2` ([LocalMutationDao.kt:3728](file:///c:/Users/hp/Desktop/FeniqoMobil/sharedLogic/src/commonMain/kotlin/com/feniqo/mobile/data/local/dao/LocalMutationDao.kt#L3728)) ile tek transaction'da verification + V2 outbox + transaction yerel attribution atomik yazımı, transaction için remote outbox üretilmeme, kişisel işlem sınırı (`workspaceId == null`), `TransactionMerchantEligibility` ([MerchantRecognition.kt](file:///c:/Users/hp/Desktop/FeniqoMobil/sharedLogic/src/commonMain/kotlin/com/feniqo/mobile/domain/model/MerchantRecognition.kt)), `StarterMerchantCatalog` fail-closed kuralları, `CanonicalMerchantVerificationTailTopology`, ACK B+1 rebase ve unblock davranışı, snapshot tabanlı sıfır yazım doğrulaması kanıtlandı.
    - F2 ViewModel & UI: `TransactionDetailViewModel.merchantCorrection` tipli state'i, tek singleton `OfflineFirstTransactionRepository` concrete binding'i, stable ID çağrıları, reentrancy engeli, `CancellationException` rethrow ve progress temizliği, Room SSOT observe akışına tam dayanma (elle display model patch edilmemesi), tanınmış merchant kartı, Değiştir / Doğru değil / İş yeri seç akışları, `MerchantBlockConfirmDialog` görünür hata metni ve retry, `MerchantSearchFilter` deterministik sıralaması, 22 yeni TR/EN lokalize anahtar ve dekoratif logo erişilebilirliği kanıtlandı.
  - **Doğrulama ve Kapsam:**
    - 36 hedefli testin %100'ü geçti (18 ViewModel testi, 13 Compose testi, 5 SearchFilter testi; 0 failure, 0 error).
    - 421 sharedUI host testi, 2198 sharedLogic host testi, debug APK derlemesi ve iOS Simulator Arm64 derlemesi tam yeşil.
    - 4 source set için ktlint check ve localization policy (`check-localization-policy.ps1`, 2500 TR / 2500 EN eşliği) tam başarıyla geçti.
    - D1 Git blob hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) birebir korundu.
    - Ana Faz 8.6-F checkbox'ı 2026-10-28 tarihli waiver kararıyla kapatıldı (`[x]`).
  - **Durum:** Tamamlandı (2026-10-28 tarihli proje sahibi kararı ile TalkBack işitsel kontrolü waive edilmiş ve faz başarıyla kapatılmıştır).

- **Faz 8.6-F2-R1 — Block Hata Görünürlüğü, Deterministik Merchant Araması ve Doğrulama Kapanışı (tamamlandı, denetimden geçti — 2026-10-20):**
  - **P1 — Block Hatası Görünürlüğü ve Hata İzolasyonu:**
    - `MerchantBlockConfirmDialog` bileşenine tipli `error: MerchantCorrectionUiError? = null` parametresi eklendi; bloklama başarısız olduğunda dialog açık kalarak `error.toLocalizedText()` ile Türkçe/İngilizce lokalize hata render eder.
    - `TransactionDetailScreenRoute`, `state.merchantCorrection.error` durumunu block diyaloğuna da iletir.
    - Bloklama başarısızlığında dialog açık kalır, progress temizlenir, lokalize hata görünür ve kullanıcı onay butonuna tekrar basarak retry yapabilir. İkinci deneme başarılı olduğunda dialog kapanır.
    - Dialog dismiss edildiğinde (`onDismiss`) hata temizlenir; selector ve block diyalogları arasında hata sızması engellendi.
    - Kullanılmayan ve ölü kod durumundaki `consumeCorrectionError` fonksiyonu temizlendi.
  - **P2 — Deterministik Merchant Arama Sırası:**
    - `MerchantSearchFilter` sonuçları giriş katalog sırasından bağımsız, deterministik `merchantComparator` ile sıralandı: birincil anahtar normalize edilmiş `displayName` (büyük/küçük harf duyarsız), ikincil tie-breaker ise stable `merchant.id.value`. Ardından `MerchantSelectorItem`'a dönüştürülür.
    - 5 saf birim testi (`MerchantSearchFilterTest`) ile shuffled katalog sırası, tie-breaker ayrımı, alias araması, boş sorgu ve boş sonuç sözleşmesi kanıtlandı.
  - **P3 — Dürüstleştirilmiş Cancellation Sözleşmesi:**
    - `TransactionDetailViewModelTest` içinde "rethrows" iddiası yerine kanıtlanan gerçek sözleşme adlandırıldı: `cancellationException_doesNotMapToError_emitsNoEvent_andCleansUpProgress`.
    - Cancellation durumunda generic hataya dönüştürülmeme, başarı eventi üretilmeme, finally ile progress temizliği ve ikinci mutasyon tetiklenmeme kuralları doğrulandı.
  - **Doğrulama ve Kapsam:**
    - 18 ViewModel Unit Testi (`TransactionDetailViewModelTest`, 0 failure, 0 error).
    - 13 Compose & Localization Testi (`TransactionDetailMerchantCorrectionComposeTest`, 0 failure, 0 error).
    - 5 SearchFilter Birim Testi (`MerchantSearchFilterTest`, 0 failure, 0 error).
    - Toplam 36 hedefli test tam başarıyla geçti. `:sharedLogic:testAndroidHostTest` (2198 test), `:sharedUI:testAndroidHostTest` (421 test), `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` tam yeşil.
    - 4 source set için ktlint check ve localization policy (`check-localization-policy.ps1`, 2500 TR / 2500 EN eşliği) tam başarıyla geçti.
    - D1 Git blob hash'leri (`00c4ffbc3811960fd9a9cf897c5f58bf684c522f`, `0119fe9633ab32cd28bfa6eda3ec32a8610ee349`) birebir korundu.
  - **Durum:** Tamamlandı.

- **Faz 8.6-F2 — Merchant Correction ViewModel, UI State ve Jetpack Compose Akışları (tamamlandı, denetimden geçti — 2026-10-20):**
  - **İşlem Detayı Merchant Correction Kartı:**
    - Tanınan merchant varsa: "Merchant olarak tanındı" kartı, `MerchantLogo` + merchant adı, "Değiştir" ve "Doğru değil" butonları.
    - Merchant yoksa ve işlem kişisel ve uygun açıklamaya sahipse (`canCorrectMerchant == true`): "İş yeri seç" butonu.
    - Uygun olmayan işlemler (workspace transaction veya exclusion listesindeki transfer/maaş/nakit/vs.): düzeltme kartı gösterilmez.
  - **Aranabilir Merchant Seçici BottomSheet:**
    - `StarterMerchantCatalog` üzerinden saf ve deterministik filtreleme (`MerchantSearchFilter`), stable `merchantId` ile seçim; `confirmAssign()` ile `TransactionMerchantCorrection.Assign(transactionId, merchantId)`.
  - **Doğrulama / Bloklama Onay Diyaloğu:**
    - "Doğru değil" tıklandığında `MerchantBlockConfirmDialog` ile onay alınır; `confirmBlock()` ile `TransactionMerchantCorrection.Block(transactionId)`.
  - **ViewModel Mimarisi ve State Yönetimi:**
    - Tek aktif job (`activeCorrectionJob`), reentrancy engeli (`isInProgress`), `CancellationException` koşulsuz rethrow, finally progress cleanup, saf tipli durum (`MerchantCorrectionUiError`).
    - Mutation başarılı olduğunda ekran durumu elle yamalanmaz; yalnızca Room SSOT observe akışına (`observeTransactionWithCategory`) dayanır.
  - **Hilt Singleton Concrete Binding:**
    - `RepositoryModule.kt` içinde `provideOfflineFirstTransactionRepository` concrete singleton olarak sunuldu; `provideTransactionRepository` ve `provideTransactionMerchantCorrectionRepository` aynı singleton instance'a bağlandı.
  - **Localization & Erişilebilirlik:**
    - TR ve EN strings.xml kataloglarına 22 yeni `transaction_merchant_*` anahtarı eklendi (`check-localization-policy.ps1` 2500 TR / 2500 EN %100 PASS).
    - `MerchantLogo` dekoratiftir (`contentDescription = null`), çift anons engellendi. Runtime TR→EN geçişi doğrulandı.
  - **Doğrulama ve Kapsam:**
    - 14 ViewModel Unit Testi (`TransactionDetailViewModelTest`, %100 PASS).
    - 8 Compose & Localization Testi (`TransactionDetailMerchantCorrectionComposeTest`, %100 PASS).
    - Toplam 22 test sıfır hata ile geçti. `:sharedLogic:testAndroidHostTest`, `:sharedUI:testAndroidHostTest`, `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` tam yeşil. D1 hash'leri korundu.
  - **Durum:** Tamamlandı.

### 8.6-F Kapanış Kararı (2026-10-28 — Proje Sahibi Açık Kararı)

- **TalkBack Manuel Kontrolü Durumu:** Proje sahibi tarafından 2026-10-28 tarihinde alınan açık karar doğrultusunda, fiziksel cihaz/işitsel TalkBack manuel testi artık F/F3/G1 geçiş kapısı değildir; bu kontrol proje sahibi tarafından açıkça kapsam dışı bırakılmış (waive edilmiş) ve doğrudan uzak merchant logo aşamasına (Faz 8.6-G) geçiş onaylanmıştır.
- **Erişilebilirlik Koruması:** Mevcut otomatik semantikler (`clearAndSetSemantics`, dekoratif `contentDescription = null`, çift anons engeli) korunmuş, geri alınmamış veya değiştirilmemiştir. "TalkBack manuel geçti" şeklinde asılsız raporlama yapılmaz; işitsel manuel kontrolün proje sahibi tarafından waive edildiği dürüstçe kayıt altına alınmıştır.
- **Sonuç:** Faz 8.6-F ve Faz 8.6-F3 teknik ve otomatik kapsamı (A–I kabul matrisi, Robolectric, Compose ve ViewModel testleri) başarıyla tamamlanmış kabul edilerek kapatılmıştır.

### 8.6-G — Uzak logo adaptörü ve güvenli CDN entegrasyonu (Tamamlandı — 2026-10-30)

#### 1. Aktif Sağlayıcı ve Entegrasyon Mimarisi
- **Aktif Sağlayıcı:** Logo.dev (`feniqo.remoteLogo.provider=logodev`). Logo.dev Community planı kapsamında zorunlu public attribution `https://beratxkeskin.github.io/feniqo-site/` adresinde yayınlanmış ve doğrulanmıştır (Verified).
- **Brandfetch Adaptörü:** Data katmanında hazır ve pasif durumda tutulmuştur; ileride alternatif veya yedek sağlayıcı olarak kullanılabilir.
- **Factory ve Sözleşme:** `RemoteMerchantLogoResolverFactory`, yapılandırmaya göre `LogoDevRemoteMerchantLogoResolver` veya `BrandfetchRemoteMerchantLogoResolver` üretir; konfigürasyon eksik veya yetkisiz ise fail-closed davranır.

#### 2. Domain Allowlist’i ve Gizlilik Garantisi
- `MerchantLogoDomainAllowlist` merkezi ve değişmez sözleşmedir. Tarihsel H4-G1 ile katalog v8'de 22, H4-G2-A ile katalog v9'da 28 doğrulanmış stable key bulunurken, güncel H4-G2-B sonrasında katalog v10 ile allowlist 44 doğrulanmış stable key → kanonik domain eşlemesine ulaşmıştır; 107 fallback kayıt (özellikle `turk_telekom`, `istanbulkart` gibi kapsam dışı kayıtlar ile `migros_sanal_market`, `yemeksepeti_market`, `trendyol_yemek`, `getiryemek`, `migros_yemek` gibi hizmet varyantları) için çözüm `null` kalır.
- Yalnızca doğrulanmış kanonik domainler için istek üretilir.
- Serbest işlem adları, fiş metinleri veya kullanıcı özel adları (`UseTransactionName`) hiçbir koşulda uzak logo sağlayıcısına gönderilmez; doğrudan yerel kategori ikonu / baş harf / genel işlem ikonu fallback zincirinde çözülür.

#### 3. Coil ImageLoader Yaşam Döngüsü ve No-Cache Politikası
- `AndroidRemoteMerchantLogoRenderer`, `rememberAsyncImagePainter` painter yaşam döngüsünü `Box` yapısı içinde korur:
  - `Loading`, `Empty` ve `Error` durumlarında `Image` composable'ı `alpha=0f` olarak composition/draw ağacında tutulur; kullanıcıya yalnızca ortalanmış `fallbackContent` gösterilir.
  - `Success` durumunda `Image` `alpha=1f` ile görünür hale gelir ve `fallbackContent` ağaçtan kaldırılır.
  - Subcomposition (`SubcomposeAsyncImage`) kullanılmamış, LazyColumn/LazyRow liste performans sözleşmesi korunmuştur.
- Katı No-Cache: Coil isteklerinde bellek ve disk önbellekleri devre dışıdır (`Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`). Logolar yerel cihaz belleğine veya kalıcı dosyalara yazılmaz.

#### 4. Canlı Emülatör Görsel Kabulü ve Kanıt Ayrımı
- Gerçek demo APK (`androidApp-demo.apk`) ile Pixel_8 AVD üzerinde canlı kabul başarıyla tamamlanmıştır:
  - Migros gerçek logosunun ekranda görünmesi: PASS
  - CarrefourSA gerçek logosunun ekranda görünmesi: PASS
  - BİM gerçek logosunun ekranda görünmesi: PASS
  - UseTransactionName işleminin ekranda yerel kategori ikonu fallback'inde kalması: PASS
- **Kanıt Ayrımı:** Pixel_8 ekran görüntüleri Migros/CarrefourSA/BİM logolarının ve UseTransactionName kategori fallback'inin ekrandaki görsel görünürlüğünü kanıtlar. UseTransactionName ve serbest metinler için sıfır uzak istek yapıldığı ise resolver/request-factory otomatik testleri ve `verifiedLogoKey` allowlist domain mimarisiyle kanıtlanmıştır. Ekran görüntüleri doğrudan ağ trafiği kanıtı olarak sunulmaz.

#### 5. Doğrulama ve Kapanış
- 40 hedefli uzaktan logo testi (%100 PASS), full `:sharedLogic:testAndroidHostTest` (2321 test), `:sharedUI:testAndroidHostTest`, assembleDebug/assembleDemo, iOS simulator derlemesi, CI localization (2512 TR / 2512 EN) ve `git diff --check` başarıyla geçti.
- Faz 8.6-G tamamlandı ve ana checkbox kapatıldı.

#### 6. H4-G1 Allowlist Genişletmesi ve H4-G1-R1 Kapanışı (2026-11-04)
- Beş pilot market kaydına 17 doğrulanmış merchant eklenmiş, katalog sürümü v8'e yükseltilmiş ve toplam 22 uzak-logo destekli / 129 fallback kayıt sözleşmesi kurulmuştur.
- Room v24, Supabase ve migration kapsamı değişmemiştir. Kullanıcı girdisi, işlem açıklaması veya özel merchant adı domain üretiminde kullanılmaz.
- **H4-G1-R1 Yemeksepeti Düzeltmesi (Saf Domain Sözleşmesi):** Jenerik "Yemek" işlem başlığıyla `KnownMerchant("yemeksepeti")` seçildiğinde genel token istisnası nedeniyle kaydetmenin başarısız olması hatası, saf domain düzeyinde merkezi `TransactionMerchantVerificationAliasResolver` ile giderilmiştir. İşlem açıklaması "Yemek" olarak korunurken, arka plandaki kullanıcı doğrulama kaydına katalogdan deterministik kanonik alias ("YEMEKSEPETI") atanır; tek başına jenerik token'lar fail-closed reddedilmeye devam eder. 10 saf use case testi (`TransactionMerchantVerificationAliasResolverTest`), 37 repository karar testi (`OfflineFirstTransactionRepositoryDecisionTest`), 36 DAO atomiklik/rollback testi (`TransactionMerchantCreateAtomicDaoTest`) ve ViewModel testi ile teknik otomatik doğrulama tamamlanmıştır.
- **Canlı Cihaz Kabulü (Samsung SM-A715F):** 2026-11-04 tarihinde güncel `com.feniqo.mobile.demo` paketiyle Samsung SM-A715F fiziksel cihazında yapılan canlı manuel kabulde: `food_dining` kategorisinde `Yemek` başlıklı harcama için `KnownMerchant("yemeksepeti")` seçildiğinde işlemin "Beklenmeyen bir hata oluştu" hatası vermeden başarıyla kaydedildiği, işlem adının `Yemek` olarak korunduğu ve ekranda Yemeksepeti adı ile logosunun düzgün göründüğü proje sahibi tarafından açıkça doğrulanmıştır (PASS).
- **Kanıt Ayrımı:** Ekrandaki başarılı kayıt ve logo görünümü doğrudan ağ trafiğini kanıtlamaz; kullanıcının girdiği serbest metinden ("Yemek") kesinlikle uzak istek yapılmadığı ve sıfır kullanıcı-metni uzak istek garantisi, merkezi `MerchantLogoDomainAllowlist` sözleşmesi, saf resolver ve otomatik test mimarisinden gelmektedir.
- **Kapanış:** H4-G1-R1 teknik ve canlı cihaz kabulüyle tamamlandı olarak kaydedilmiştir.

#### 7. H4-G2-A Market ve Hızlı Teslimat Logo Genişletmesi Kapanışı (2026-11-05)
- **Katalog Sürümü ve Dağılım:** `StarterMerchantCatalog` sürümü v9'a yükseltilmiş (`CATALOG_VERSION = 9`), toplam 151 kayıt korunurken uzak logo anahtarı taşıyan merchant sayısı 22'den 28'e çıkarılmıştır. Kalan 123 kayıt için `verifiedLogoKey = null` korunur ve yerel `CATEGORY_ICON` fallback'i uygulanır. 151 markanın tümünde uzak logo olduğu iddiası bulunmaz.
- **Eklenen 6 Uzak Logo ve Kanonik Domainler:**
  - `macrocenter` → `macrocenter.com.tr`
  - `file_market` → `file.com.tr`
  - `tarim_kredi` → `tkkoop.com.tr` (Resmî Tarım Kredi TK Koop perakende şirketi web sitesidir; kooperatif birlik portalı `tarimkredi.org.tr` market logosu için kullanılmaz)
  - `getir` → `getir.com` (Tarihsel not: H4-G1 diliminde kapsam dışı tutulmuştu; proje sahibinin güncel açık kararı ve resmî kanonik domain doğrulamasıyla H4-G2-A'da kapsama alındı)
  - `bizim_toptan` → `bizimtoptan.com.tr`
  - `metro_turkiye` → `metro-tr.com`
- **Fallback'te Kalan İki Hizmet Varyantı:** `migros_sanal_market` ve `yemeksepeti_market` kayıtlarında `verifiedLogoKey = null` bilinçli olarak korunmuştur; ana Migros veya ana Yemeksepeti uzak logosunun teslimat/market hizmet varyantına yanlış marka kimliği olarak gösterilmemesi sağlanmıştır.
- **Canlı Cihaz Kabulü (Samsung Galaxy A71 / SM-A715F):** 2026-11-05 tarihinde güncel `com.feniqo.mobile.demo` paketiyle Samsung Galaxy A71 (SM-A715F) fiziksel cihazında proje sahibi tarafından yapılan canlı manuel kabulde: Macrocenter, FİLE Market, Tarım Kredi/TK Koop, Getir, Bizim Toptan ve Metro Türkiye için gerçek uzak logoların hatasız ve doğru göründüğü (PASS); Migros Sanal Market ve Yemeksepeti Market hizmet varyantlarının ise ana marka logosu yerine güvenli kategori ikonu fallback'i gösterdiği (PASS) açıkça doğrulanmıştır.
- **Kanıt Ayrımı:** Ekrandaki başarılı logo ve fallback görünümü doğrudan ağ trafiğini kanıtlamaz; kullanıcının girdiği serbest metinden asla uzak istek yapılmadığı ve sıfır kullanıcı-metni uzak istek garantisi, merkezi `MerchantLogoDomainAllowlist` / `AndroidRemoteMerchantLogoRequestFactory` mimarisi ve otomatik testlerden gelmektedir. Form üzerinde hiçbir "Kaydet" işlemi yapılmamış, veritabanı veya outbox mutasyonu üretilmemiştir.
- **Kapanış:** H4-G2-A teknik uygulama, fresh testler, ktlint, fiziksel cihaz kabulü ve belge adımlarıyla tamamlanmıştır. Room v24, Supabase ve D1 hash'leri korunmuştur; H4-G2-B aşamasına geçiş proje sahibinin açık onayıyla gerçekleştirilmiştir.

#### 8. H4-G2-B Yemek ve Kafe Logo Genişletmesi Kapanışı (2026-11-05)
- **Katalog Sürümü ve Dağılım:** `StarterMerchantCatalog` sürümü v10'a yükseltilmiş (`CATALOG_VERSION = 10`), toplam 151 kayıt korunurken uzak logo anahtarı taşıyan merchant sayısı 28'den 44'e çıkarılmıştır. Kalan 107 kayıt için `verifiedLogoKey = null` korunur ve yerel `CATEGORY_ICON` fallback'i uygulanır. 151 markanın tümünde uzak logo olduğu iddiası bulunmaz.
- **Eklenen 16 Yeni Bağımsız Marka ve Kanonik Domainler:**
  - `starbucks` → `starbucks.com` (İlk eşlemedeki yerel yazılı varyant yerine proje sahibinin canlı kabul bulgusu doğrultusunda resmî global sade yeşil-beyaz Siren amblemine dönüştürülmüştür; `starbucks.com.tr` allowlist dışı ve fail-closed'dır)
  - `mcdonalds` → `mcdonalds.com.tr`
  - `burger_king` → `burgerking.com.tr`
  - `kfc` → `kfcturkiye.com`
  - `popeyes` → `popeyes.com.tr`
  - `dominos` → `dominos.com.tr`
  - `little_caesars` → `littlecaesars.com.tr`
  - `pizza_lazza` → `pizzalazza.com.tr`
  - `tavuk_dunyasi` → `tavukdunyasi.com`
  - `baydoner` → `baydoner.com`
  - `kofteci_yusuf` → `kofteciyusuf.com`
  - `kahve_dunyasi` → `kahvedunyasi.com`
  - `mado` → `mado.com.tr`
  - `espressolab` → `espressolab.com`
  - `simit_sarayi` → `simitsarayi.com`
  - `bigchefs` → `bigchefs.com.tr`
- **Food & Dining Gerçek Logo ve Üç Hizmet Varyantı:** Önceden mevcut `yemeksepeti` (`yemeksepeti.com`) ile birlikte `food_dining` kategorisinde gerçek logolu marka sayısı 17'ye ulaşmıştır. Üç yemek hizmeti varyantında (`trendyol_yemek`, `getiryemek`, `migros_yemek`) `verifiedLogoKey = null` bilinçli olarak korunmuştur; ana pazar yeri/teslimat markasının logosu yerine güvenli yemek kategorisi ikonu fallback'i gösterilir.
- **Canlı Cihaz Kabulü (Samsung Galaxy A71 / SM-A715F):** 2026-11-05 tarihinde güncel `com.feniqo.mobile.demo` paketiyle Samsung Galaxy A71 (SM-A715F) fiziksel cihazında proje sahibi tarafından yapılan canlı manuel kabulde: 17 yemek/kafe markasının gerçek logolarının doğru ve hatasız görüntülendiği (PASS); Trendyol Yemek, GetirYemek ve Migros Yemek hizmet varyantlarının kategori ikonu fallback'inde kaldığı (PASS); düzeltilen Starbucks sade yeşil-beyaz Siren logosunun alt metinsiz/dengeli görüntülendiği (PASS) açıkça doğrulanmıştır.
- **Kanıt Ayrımı:** Ekrandaki başarılı logo ve fallback görünümü doğrudan ağ trafiğini kanıtlamaz; kullanıcının girdiği serbest metinden asla uzak istek yapılmadığı ve sıfır kullanıcı-metni uzak istek garantisi, merkezi `MerchantLogoDomainAllowlist` / `AndroidRemoteMerchantLogoRequestFactory` mimarisi ve otomatik testlerden gelmektedir.
- **Kapanış:** H4-G2-B teknik uygulama, otomatik testler (2442 test, %100 PASS), ktlint temizliği, fiziksel cihaz kabulü ve belge adımlarıyla tamamlanmıştır. Room v24, Supabase ve D1 hash'leri korunmuştur; bir sonraki logo kategorisi henüz başlatılmamıştır.

## 9. Genel doğrulama ve teslim koşulları

Her dilimde kapsamla orantılı hedefli testlere ek olarak aşağıdakiler değerlendirilir:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :sharedUI:allTests
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
powershell -ExecutionPolicy Bypass -File scripts/ci/check-localization-policy.ps1
```

Room değişikliğinde migration testi ve export schema zorunludur. Supabase değişikliğinde sıralı
önceki migration'lar, SQL sözleşme testleri ve hedef proje doğrulaması zorunludur. Checkbox ancak
ilgili dilimin kodu, testi ve gerekli belge güncellemesi birlikte tamamlandığında işaretlenir.
