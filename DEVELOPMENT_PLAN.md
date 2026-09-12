# FeniqoMobil Geliştirme Planı

> Standart araçlar için güncel çalışma giriş noktasıdır. Ayrıntılı alt görevler, checkbox'lar ve
> tarihsel ilerleme notlarında [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)
> tek yetkili kaynaktır; bu dosya aynı yüzlerce checkbox'ı kopyalamaz.

## Güncel durum

- Aktif çalışma: **Hızlı Ekle (+ / Ekle), İşlem Formu ve Başarı Ekranı Yeniden Tasarımı** uygulandı, manuel kabul bekliyor: Alt bar merkez "+" modal sheet (Gider, Gelir, Transfer [Yakında], Borç/Alacak, Tekrarlayan İşlem ve ipucu kartı); sıcak-lüks işlem formu (büyük tutar, zorunlu 100 karakter işlem adı, kategori, ödeme yöntemi, tarih ve isteğe bağlı not/taksit/makbuz/split akordiyonu); Room v14→v15 note kolonu ileri yönlü migration'ı (15.json şeması ve veri koruma testi); Room SSOT'tan gözlemleyen başarı ekranı (özet kartı, yeni işlem ekle, işlemi görüntüle, geri dönüş back-stack); hedefli testler ve debug APK derlemesi başarıyla doğrulandı; staging/production Supabase'e dokunulmadı.
- Önceki çalışma: **Faz 9.2 — Makbuz OCR** temel akışı tamamlandı fakat gerçek makbuzlarda fiyat/toplam algılama doğruluğu yetersiz bulundu; ürün kabulü ve fixture tabanlı doğruluk iyileştirmesi ileri bir dilime ertelendi.
- Ek güvenli domain dilimi: merchant/marka tanımanın platformdan bağımsız sözleşmesi, Türkçe
  normalleştiricisi, alias türü/kapsamı, kişisel→workspace→banka doğrulama önceliği, onaylı
  0–100 güven matrisi, 10 puanlık çakışma eşiği ve temsilî başlangıç kataloğu tamamlandı.
  Room/Supabase/UI/logo sağlayıcısı entegrasyonu yapılmadı.
- Kategori analitiği ve yönetim ekranı: sıcak-lüks görsel hiyerarşi, taşma korumalı tamsayı basis-point motoru (`CategoryAnalyticsCalculator`), fail-closed para birimi ve kategori türü tutarlılığı, filtre kapsam uyumu, kategori ve dönem filtreli İşlemler navigasyonu ve özel kategori yönetimi tamamlandı; tüm birim/host testleri ve APK derlemesi doğrulandı; emülatör görsel ve akış kabulü aşamasında.
- Mobil bilgi mimarisi: alt navigasyon Ana Sayfa / İşlemler / + / Bütçe / Daha Fazla olarak düzenlendi; Daha Fazla yalnız finansal modülleri gruplayan Financial Hub, avatar ise Profil/Hesap girişi olarak ayrıştırıldı.
- Tamamlanan fazlar:
  - Faz 8.5 E15-A — Kişisel Asset offline-first CRUD tamamlandı.
  - Faz 8.5 E15-B — Para birimi bazlı offline-first net değer özeti tamamlandı.
  - Faz 8.5 E15-C — Güvenli piyasa fiyat sözleşmesi, Room cache ve mobil fresh/stale/manual fallback tamamlandı; gerçek Edge runtime/provider smoke kabulü dış ortam bulunana kadar açık.
  - Faz 9.1 — Biyometrik uygulama kilidi, cihaz PIN/desen/parola geri dönüşü ve otomatik kilit süreleri tamamlandı.
  - Faz 8.4 E14-D — Workspace Ödeşme Ekranı ve Transfer Önerileri (MVI UI, `ObserveWorkspaceSettlementUseCase`, net bakiye ve transfer önerileri) tamamlandı.
  - Faz 8.4 E14-C — Ortak Gider Split Formu UI Entegrasyonu tamamlandı.
  - Faz 8.4 E14-B2 — Ortak Gider Split Senkronizasyonu ve Room v12 Altyapısı tamamlandı.
  - Faz 8.4 E12-F — OWNER Üye Çıkarma Akışı (`WORKSPACE_MEMBER DELETE` V2 outbox + UI entegrasyonu) tamamlandı.
  - Faz 8.4 E12-E — Workspace Sahiplik Devri UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-D — Workspace Sahiplik Devri Atomik ve Fail-Closed Altyapısı tamamlandı.
  - Faz 8.4 E12-C — OWNER Workspace Davet Oluşturma ve EDITOR/VIEWER Rol Yönetimi UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-B — Workspace Üye Rol Değişimi (`UPDATE`) ve Alandan Ayrılma (`DELETE`) V2 Outbox Altyapısı tamamlandı.
  - Faz 8.4 E12-A2 — Davet Kodu ile Workspace'e Güvenli Katılma tamamlandı.
  - Faz 8.3 — Hedefler ve Borçlar (Goals & Debts) başarıyla tamamlandı.
  - Mobil Navigasyon Bilgi Mimarisi (Ana Sayfa, İşlemler, + hızlı eylem, Bütçe, Financial Hub) type-safe route'lar ve pasif Yakında kayıtlarıyla tamamlandı.
  - Faz 8.2 — Tekrarlayan İşlemler ve Abonelikler (Recurring Transactions & Subscriptions) başarıyla tamamlandı.
  - Faz 8.1 — Bütçeler (Budgets) başarıyla tamamlandı.
- Sıradaki merchant dilimi: kullanıcı doğrulamalarının ve isteğe bağlı transaction→merchant
  bağlantısının Room SSOT modeli; migration/outbox/sync kapsamı ayrı tasarım ve test dilimi olarak
  ele alınacak. Genel sıradaki teknik iş Faz 10.1 otomatik test envanteridir. Navigation takip
  listesi: gerçek domain/veri/UI akışları tamamlandığında Transfer hızlı eylemi, Reports, Kişisel
  Bilgiler, Hesap, Bildirimler ve Tercihler pasif `Yakında` durumundan aktif route'lara geçirilecek.
- Production Supabase durumu: migration uygulanmadı.
- Staging: `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`); 15/15 migration (`20260901000100_sync_write_v2_goals_and_debts.sql`, `20260901000200_reconcile_goals_debts_sync_contract.sql` dâhil), RLS, `sync_write_v2` RPC ve SQL sözleşme testi doğrulandı; koşulsuz ROLLBACK ile test verisi bırakılmadı (goals, contributions, debts, payments ve sync receipts = 0 kalıntı).

## Tamamlanan fazlar

| Faz | Durum | Çıktı |
|---|---|---|
| 1. Analiz ve kapsam | Tamamlandı | Web envanteri, V1 sınırı, güvenlik ve migration planı |
| 2. Proje omurgası | Tamamlandı | KMP modülleri, bağımlılıklar, tema ve UI kabuğu |
| 3. Domain | Tamamlandı | Temel/ikinci dalga modeller, repository sözleşmeleri ve use case'ler |
| 4. Yerel veri | Tamamlandı | Room v3/v4, DAO/mapper, SQLCipher/Keystore ve outbox |
| 5. Uzak veri | Tamamlandı | Auth, DTO/remote, sync motoru, staging kabulü ve Realtime |
| 6.1 Arka plan sync | Tamamlandı | Hilt CoroutineWorker, BackgroundSyncScheduler, exponential backoff, KEEP / APPEND_OR_REPLACE |
| 6.2 Senkronizasyon gözlemi | Tamamlandı | Room v4 SyncOverview Flow, NetworkConnectivityObserver, ViewModel, SyncStatusIndicator Compose bileşeni |
| 7.1 Navigasyon ve UI durumları | Tamamlandı | Type-safe Navigation Compose rotaları, bağımsız Auth/Main NavHost, RootNavViewModel, stateless FeniqoAppShell |
| 7.2 Giriş ve kayıt ekranları | Tamamlandı | LoginScreen, RegisterScreen, tipli validation/hata eşleme, Login/RegisterViewModel, session tabanlı akış, staging auth |
| 7.4 Dashboard | Tamamlandı | Stateless DashboardScreen, DashboardViewModel, Hilt modülleri, dinamik ay Room Flow SSOT, son işlemler, işlem/düzenleme navigasyonları, geçici MoneyScore kartı ve ön değerlendirme |
| 8.1 Bütçeler | Tamamlandı | Bütçe listesi, dinamik ay gezinimi, %80 uyarı ve %100 aşım, harcama kategorisiyle bütçe ekleme, ID tabanlı Room SSOT form düzenlemesi, onaylı silme ve kopyalama akışları, Room V2 outbox/ACK, Staging V2 SQL migration, sözleşme testi ve Android emülatör manuel smoke kabulü |
| 8.2 Tekrarlayan işlemler ve abonelikler | Tamamlandı | Tekrar vade hesaplayıcı, occurrence idempotency, kural/abonelik CRUD komutları, Room v6/v7/v8, V2 outbox/ACK/pull/conflict, Staging 13/13 migration, 38 senaryolu SQL sözleşme testi, MVI Compose liste ve form ekranları, hatırlatıcı planlayıcı, Room receipt claim, Android bildirim Worker'ı, 24h periyodik scheduler, Android 13+ izin CTA'sı ve Android emülatör manuel smoke kabulü |
| Mobil Navigasyon Bilgi Mimarisi | Tamamlandı | 5'li kalıcı alt navigasyon (Ana Sayfa, İşlemler, + hızlı eylem, Bütçe, Daha Fazla), semantic Financial Hub, avatar tabanlı Profil/Hesap ayrımı, type-safe route'lar ve geliştirilmemiş modüller için pasif Yakında durumu |
| 8.3 Hedefler ve borçlar | Tamamlandı | Goals (birikim CRUD, katkı ekleme/çıkarma, ilerleme/tahmini süre), Debts & Receivables (borç/alacak CRUD, ödeme/tahsilat geçmişi, fail-closed reaktif bakiye hesabı), Borç snowball planlayıcısı ve ekranı, Room v9/v10/v11 tabloları, V2 outbox/ACK/pull/conflict sync, Staging 15/15 migration, SQL sözleşme testi (0 kalıntı) ve Android emülatör manuel smoke kabulü |

5.1'de Android için build configuration ve güvenli oturum saklama uygulanmıştır. iOS `.xcconfig`,
Keychain ve üretim güvenlik adaptörlerinin kalan kısmı Android-first kararı gereği 10.4'te tamamlanır.

## Tamamlanan faz: Faz 9.3 — İçe/dışa aktarma ve gizlilik

İlk dilim (2026-09-10): Aktif Room çalışma alanındaki işlemler için güvenli CSV dışa aktarma
tamamlandı. Android sistem dosya seçicisi kullanılır; küçük para birimi korunur, private makbuz
yolu/owner kimliği dışlanır ve elektronik tablo formül enjeksiyonu etkisizleştirilir. Kişisel
kategori/işlem verisi için fail-closed doğrulanan JSON yedek v1 sözleşmesi ve tek Room
transaction'ında atomik, yeni kimlikli içe aktarma tamamlandı. Android dosya seçici 10 MiB sınırı,
kayıt sayısı önizlemesi ve açık onay uygular. Üretim kaynakları ile Edge Function kodunda hassas
log/ağ gövdesi taraması tamamlandı; doğrudan logger bulunmadı, ham exception mesajlarının
repository ve outbox kalıcı hata alanlarına taşınması güvenli kararlı kodlarla kapatıldı. Yirmi
hedefli test ve Android debug APK derlemesi geçti. Faz 9.3 tamamlandı; sıradaki faz 10.1'dir.

## Ertelenen kabul: Faz 9.2 — Makbuz OCR

Amaç: Makbuz görüntüsünü kamera veya galeriden alıp cihaz içinde metne dönüştüren, bulunan tutar/tarih/işyeri adaylarını kullanıcı onayı olmadan finans kayıtlarına yazmayan güvenli OCR akışı oluşturmak.

İlk dilim (2026-09-09): Ham metni saklamayan geçici aday modeli ve `Double` kullanmayan
deterministik parser tamamlandı. Etiketli toplamlar, geçerli tarihler ve düşük güvenli işyeri adayı
ayrıştırılır; para birimi form bağlamından gelir ve tüm adaylar kullanıcı onayına tabidir.

Android akışı (2026-09-09): CameraX arka kamera ve sistem galeri seçimi, bundled ML Kit tanıma,
izin ret/ayarlar geri dönüşü ve ayrı aday onay diyaloğu işlem formuna bağlandı. Geçici kamera
dosyası OCR sonrasında silinir; “Forma Aktar” seçilmeden hiçbir form alanı veya finans kaydı değişmez.

Faz 9.1 kapanış notu (2026-09-09): Ortak `SecuritySettings`, fail-closed kilit politikası, Android Preferences DataStore/Hilt repository, AndroidX `BiometricPrompt`, biyometri + cihaz PIN/desen/parola geri dönüşü ve navigation'dan önce çalışan lifecycle kilit kapısı tamamlandı. Uygulama kilidi SQLCipher anahtarından ayrı kaldığı için arka plan senkronizasyonu korunur. Otomatik testler, debug APK ve kullanıcı Android manuel kabulü geçti.

## Sonraki fazlar

### 7. Presentation — V1 kullanıcı akışı

1. Type-safe navigasyon ve UI state sözleşmeleri.
2. Splash/oturum, giriş ve kayıt ekranları.
3. İşlem listesi, ekleme/düzenleme/silme ve filtreleme.
4. Kategori yönetimi.
5. Dashboard ve sync durumu.

### 8–9. Genişletmeler ve cihaz özellikleri

Bütçe, tekrarlayan işlemler, hedef/borç, workspace, varlık/rapor, biyometri, OCR ve veri
taşınabilirliği çekirdek V1 kabulünden sonra ele alınır.

### 10. Kalite ve yayın

- otomatik test kapsamının tamamlanması;
- statik analiz ve performans;
- Android release/internal testing;
- iOS Keychain, DB güvenliği ve SwiftUI ürün akışı.

## Her adım için çalışma döngüsü

1. İlgili sözleşme ve mevcut kod okunur.
2. Küçük, geri alınabilir uygulama dilimi seçilir.
3. Kod ve hedefli test birlikte yazılır.
4. Riskle orantılı geniş regresyon çalıştırılır.
5. Tamamlanma ölçütü karşılanırsa checkbox ve ilerleme notu güncellenir.
6. Commit/push yalnız proje sahibinin açık isteğiyle yapılır.

## Doğrulama komutları

Windows proje kökünde:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

Migration için yalnız doğru hedef doğrulandıktan sonra `supabase` CLI kullanılır. Production
komutları ayrı açık onay olmadan çalıştırılmaz.

## Plan güncelleme kuralı

- Ayrıntılı görev durumu ve ilerleme notu `FENIQO_MOBIL_YOL_HARITASI.md` içinde güncellenir.
- Bu dosyada yalnız güncel faz, sıradaki faz ve yüksek seviyeli tamamlanma özeti tutulur.
- Ürün kapsamı değişirse `PRODUCT.md` ve `FEATURES.md` de güncellenir.
- Mimari veya veri sözleşmesi değişirse ilgili ana belge aynı değişiklik setinde güncellenir.
