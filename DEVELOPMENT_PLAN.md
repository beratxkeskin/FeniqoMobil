# FeniqoMobil Geliştirme Planı

> Standart araçlar için güncel çalışma giriş noktasıdır. Ayrıntılı alt görevler, checkbox'lar ve
> tarihsel ilerleme notlarında [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)
> tek yetkili kaynaktır; bu dosya aynı yüzlerce checkbox'ı kopyalamaz.

## Güncel durum

- Aktif çalışma: **Faz 8.4 — Ortak Çalışma Alanları (Workspaces)** (Domain/validation, Room v9→v10 şema/DAO, E12-A2 davet koduyla güvenli katılma, E12-B üye rol değişimi/ayrılma V2 outbox, E12-C OWNER davet oluşturma / üye rol yönetimi UI entegrasyonu, E12-D Workspace sahiplik devri atomik altyapısı, E12-E Workspace sahiplik devri UI entegrasyonu ve E12-F OWNER üye çıkarma akışı tamamlandı; Workspace CRUD V2 outbox/sync tasarımı sıradadır).
- Görsel yenileme: Feniqo production UI tasarımının ikinci dilimi tamamlandı; tema ve alt navigasyona ek olarak Dashboard net bakiye hero hiyerarşisi, düz finans metrikleri, gelir/gider semantiği ve Plan/Profil modül menülerinin sade yüzey dili güncellendi.
- Tamamlanan fazlar:
  - Faz 8.4 E12-F — OWNER Üye Çıkarma Akışı (`WORKSPACE_MEMBER DELETE` V2 outbox + UI entegrasyonu) tamamlandı.
  - Faz 8.4 E12-E — Workspace Sahiplik Devri UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-D — Workspace Sahiplik Devri Atomik ve Fail-Closed Altyapısı tamamlandı.
  - Faz 8.4 E12-C — OWNER Workspace Davet Oluşturma ve EDITOR/VIEWER Rol Yönetimi UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-B — Workspace Üye Rol Değişimi (`UPDATE`) ve Alandan Ayrılma (`DELETE`) V2 Outbox Altyapısı tamamlandı.
  - Faz 8.4 E12-A2 — Davet Kodu ile Workspace'e Güvenli Katılma tamamlandı.
  - Faz 8.3 — Hedefler ve Borçlar (Goals & Debts) başarıyla tamamlandı.
  - Mobil Navigasyon Bilgi Mimarisi (Ana Sayfa, İşlemler, + hızlı eylem, Plan hub [Bütçeler, Tekrarlayanlar, Abonelikler], Daha Fazla hub [Kategoriler, Ayarlar]) 5'li kalıcı alt bar, type-safe route'lar, pasif Yakında modülleri ve Android emülatör manuel smoke kabulü başarıyla tamamlandı.
  - Faz 8.2 — Tekrarlayan İşlemler ve Abonelikler (Recurring Transactions & Subscriptions) başarıyla tamamlandı.
  - Faz 8.1 — Bütçeler (Budgets) başarıyla tamamlandı.
- Sıradaki teknik iş: Faz 8.4 Ortak Çalışma Alanları (Workspaces) CRUD V2 outbox mutasyonları ve Supabase senkronizasyon altyapısı tasarımı.
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
| Mobil Navigasyon Bilgi Mimarisi | Tamamlandı | 5'li kalıcı alt navigasyon kabuğu (Ana Sayfa, İşlemler, + hızlı eylem, Plan hub [Bütçeler, Tekrarlayanlar, Abonelikler], Daha Fazla hub [Kategoriler, Ayarlar]), type-safe route'lar, pasif Yakında modülleri ve Android emülatör manuel smoke kabulü |
| 8.3 Hedefler ve borçlar | Tamamlandı | Goals (birikim CRUD, katkı ekleme/çıkarma, ilerleme/tahmini süre), Debts & Receivables (borç/alacak CRUD, ödeme/tahsilat geçmişi, fail-closed reaktif bakiye hesabı), Borç snowball planlayıcısı ve ekranı, Room v9/v10/v11 tabloları, V2 outbox/ACK/pull/conflict sync, Staging 15/15 migration, SQL sözleşme testi (0 kalıntı) ve Android emülatör manuel smoke kabulü |

5.1'de Android için build configuration ve güvenli oturum saklama uygulanmıştır. iOS `.xcconfig`,
Keychain ve üretim güvenlik adaptörlerinin kalan kısmı Android-first kararı gereği 10.4'te tamamlanır.

## Aktif faz: Faz 8.4 — Ortak Çalışma Alanları (Workspaces)

Amaç: Ortak çalışma alanları (çalışma alanı oluşturma, katılma, ayrılma, aktif alan seçimi, üye listesi, rol tabanlı yetki matrisi, ortak işlem ve bütçe görünürlüğü, kimin ne kadar ödediği ve borç dağılımı hesaplaması) modülünün saf domain modelleri, validation invariant'ları, Room DAO/V2 outbox ve sync altyapısının planlanması ve geliştirilmesi.

Planlanan Kapsam:
- Workspace CRUD, davet, üyelik ve rol tabanlı (`OWNER`, `EDITOR`, `VIEWER`) kurallar.
- Ortak harcama/işlem ve bütçe görünürlüğü.
- Kimin ne kadar ödediği ve borç dağılımı (split) hesaplama motoru.
- V2 Outbox ve Supabase senkronizasyonu.

İlerleme notu (2026-09-08): E13-A–C ile finans kayıtları aktif workspace Room SSOT kapsamına bağlandı; kişisel ve ortak alan verileri repository seviyesinde fail-closed ayrışır ve tüm ilgili ekran/formlarda aktif alan bağlamı görünür. E13-D1 ile Room sorguları ortak alanda tüm üye işlem/bütçe/kategorilerini, kişisel alanda yalnız aktif kullanıcının kayıtlarını döndürür. E13-D2 ile ortak kategori ve işlem kayıtları üyelik pull'undan sonra workspace'e özel bağımsız cursor'larla Room'a alınır; kişisel cursor'lar korunur. E14-A ile henüz kalıcı veri modeline bağlanmayan, kuruş artığını deterministik dağıtan ve transfer önerileri üreten saf ortak gider ödeşme hesaplayıcısı tamamlandı. E14-B2 ile `paidByUserId` ve `participantUserIds` split alanlarının Room v12 şeması, V2 outbox yazımı, incremental pull'u, ACK'ı, equivalent conflict tespiti ve domain/PostgreSQL normalizasyon kuralları tamamlandı.

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
