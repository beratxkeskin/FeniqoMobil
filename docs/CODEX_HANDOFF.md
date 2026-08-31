# FeniqoMobil — Codex Devir ve Durum Belgesi (CODEX_HANDOFF)

Bu belge, FeniqoMobil projesinin yeni Codex oturumuna eksiksiz ve pürüzsüz biçimde devredilmesi için hazırlanmıştır.

---

## 1. Proje Yolları ve Modül Yapısı

- **`androidApp/`**: Android uygulama kabuğu, Hilt DI modülleri (`RepositoryModule`, `FinanceUseCaseModule` vb.), Jetpack Compose Activity ve Navigation.
- **`sharedLogic/`**: Kotlin Multiplatform çekirdek iş mantığı:
  - `domain/`: Saf domain modelleri (`Budget`, `Money`, `YearMonth`), Use Case'ler, Repository sözleşmeleri, Validation kuralları.
  - `data/local/`: Room veritabanı (`FeniqoDatabase`), DAO'lar (`BudgetDao`, `LocalMutationDao`, `SyncOperationDao`), Entity'ler ve `OfflineWriteQueue`.
  - `data/remote/`: Supabase KMP istemcisi, DTO'lar, Remote Writer (`IdempotentConditionalRemoteWriter`), Remote DataSource.
  - `data/repository/`: `OfflineFirstBudgetRepository`, `OfflineFirstTransactionRepository`, `OfflineFirstCategoryRepository`.
  - `data/sync/`: Outbox Processor, V2 Outbox İşlem Yürütücüleri (`V2OutboxOperationExecutor`), Realtime Coordinator.
- **`sharedUI/`**: Ortak UI bileşenleri, MVI ViewModel'lar, Ekran tasarımları.
- **`supabase/`**: Migration SQL dosyaları (`supabase/migrations/`), RPC fonksiyonları (`sync_write_v2`), RLS politikaları, sözleşme testleri (`supabase/tests/`).
- **`docs/`**: Mimari, yol haritası ve faz planlama belgeleri.
- ***(Referans)* `../Feniqo`**: Yalnızca referans amaçlı salt-okunur Next.js web projesidir. **Kesinlikle dosya değiştirilmez.**

---

## 2. Değiştirilemez Temel Mimari Kurallar

1. **Android-First KMP**: Android UI Jetpack Compose, ilk iOS UI SwiftUI'dır.
2. **Clean Architecture + MVVM/MVI**: Katmanlar arası sınırlar katıdır.
3. **Room = Single Source of Truth (SSoT)**: UI doğrudan ağ/Supabase çağırmaz. Ağ cevabı önce Room'a yazılır, UI Room'dan reaktif olarak beslenir.
4. **Para Formatı**: Para asla `Double` tutulmaz; en küçük para biriminde `Long` (`Money.amountMinor`) olarak saklanır.
5. **Silme Disiplini**: Silme işlemleri soft-delete (tombstone) ile yürütülür (`deleted_at_epoch_ms`).
6. **V2 Outbox & Sync Protokolü**: Optimistic concurrency (`version`, `base_version`) kullanılır. Yerel mutation (Entity + Outbox kaydı) aynı Room transaction'ında atomik yazılır.
7. **Çakışma Güvenliği**: Çakışmalar sessizce ezilmez (`last-write-wins` yasaktır). İki kopya korunur veya fail-closed rollback yapılır.
8. **Platform Ayrımı**: Android kütüphaneleri (Hilt, Room Android API, Biometric vb.) `commonMain` içine eklenemez.

---

## 3. Codex ile Antigravity Görev Ayrımı

- **Codex (Mimar & Stratejist)**:
  - Mimari kararları verir, iş kurallarını belirler, adımları küçük ve güvenli dilimlere böler.
  - Geliştirme kurallarına (`AGENTS.md`) tam uyumlu, net kapsamlı promptlar üretir.
- **Antigravity (Uygulayıcı & Doğrulayıcı)**:
  - Verilen prompt doğrultusunda kodları yazar, refactor eder, dosya değişikliklerini uygular.
  - Hedefli testleri ve derleme kontrollerini çalıştırarak sonuçları raporlar.

---

## 4. Güncel Aktif Aşama

- **Faz 8.1 — Bütçeler (Budgets) [TAMAMLANDI]**:
  - Kişisel bütçe CRUD, ay bazlı bütçe limitleri, önceki aydan kopyalama, V2 outbox/sync mutasyonları, reaktif harcama/ilerleme takibi, %80 uyarı ve %100 aşım gösterimleri, ID tabanlı Room SSOT form düzenlemesi, onaylı silme ve kopyalama kullanıcı akışlarının kod, hedefli birim/host testleri ve Android emülatör manuel smoke kabulü tamamlandı.
- **Faz 8.2 — Tekrarlayan İşlemler ve Abonelikler (Recurring Transactions) [DEVAM EDİYOR]**:
  - Dilim 1A: `RecurrenceScheduleCalculator` saf takvim/periyot hesaplaması.
  - Dilim 1B: `PlanDueRecurringOccurrencesUseCase` ve `RecurringOccurrenceKey` deterministik aday planlayıcı.
  - Dilim 1C: Room atomik tekrar vadesi yazma (`RecurringTransactionEntity`, `RecurringTransactionOccurrenceEntity`, `LocalMutationDao.generateRecurringOccurrence` CAS & V2 outbox).
  - Dilim 1D: `OfflineFirstRecurringTransactionRepository.generateDueTransactions`, `GenerateDueRecurringTransactionsUseCase` ve V2 snapshot Json serileştirme hizalaması.
  - Dilim 1E: Android `RecurringTransactionWorker`, `RecurringTransactionWorkScheduler` (24h KEEP, NOT_REQUIRED, 15s backoff), `RecurringTransactionStartupInitializer` ve uygulama açılış entegrasyonu.
  - Dilim 2A: Tekrar kuralı saf domain komutları (`CreateRecurringTransactionCommand`, `UpdateRecurringTransactionCommand`, `SetRecurringTransactionActiveCommand`) ve `RecurringTransactionValidationRules` (tip güvenli `applyRecurringRuleUpdate` ve fail-closed regresyon korumaları).
  - Dilim 2B: Supabase `RECURRING_TRANSACTION` V2 SQL migration'ı (`20260830000100_sync_write_v2_recurring_transactions.sql`), `public.recurring_transactions` fail-closed şeması, RLS politikası, `sync_operations_receipts` constraint'i ve 28 senaryolu SQL sözleşme testi (`sync_write_v2_contract.sql`).

---

## 5. Tamamlanan Dilimler (Faz 8.1 ve Faz 8.2)

- **Faz 8.1 Dilim 1A–4J**: Bütçeler modülünün tüm domain, Room, outbox, remote, UI ve emülatör kabul adımları.
- **Faz 8.2 Dilim 1A–1E**: Tekrar vade hesaplama, deterministik aday planlama, Room atomik occurrence yazma, repository/use-case ve Android WorkManager üretim altyapısı.
- **Faz 8.2 Dilim 2A**: Tekrarlayan işlem kuralı saf domain komutları, tip güvenli doğrulama kuralları ve `applyRecurringRuleUpdate` sözleşmesi.
- **Faz 8.2 Dilim 2B**: Supabase V2 `RECURRING_TRANSACTION` şeması, RLS, `sync_write_v2` RPC genişletmesi, 28 senaryolu SQL sözleşme kabulü ve Staging veritabanı uygulaması.

---

## 6. Doğrulama ve Staging Durumu

- **Hedefli Birim/Host Testleri**:
  - `RecurrenceScheduleCalculatorTest`, `PlanDueRecurringOccurrencesUseCaseTest`, `RecurringOccurrenceDaoTest`, `OfflineFirstRecurringTransactionRepositoryTest`, `GenerateDueRecurringTransactionsUseCaseTest`, `RecurringTransactionWorkerTest`, `RecurringTransactionWorkSchedulerTest`, `RecurringTransactionStartupTest`, `RecurringTransactionValidationRulesTest`: **Tümü başarılı (`BUILD SUCCESSFUL`)**.
- **KMP Platform Derlemesi**:
  - `sharedLogic:testAndroidHostTest`, `sharedLogic:compileKotlinIosSimulatorArm64`, `androidApp:assembleDebug`: **`BUILD SUCCESSFUL`**.
- **Staging SQL Sözleşme Doğrulaması**:
  - `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`) üzerinde `20260830000100_sync_write_v2_recurring_transactions.sql` başarıyla uygulandı (`12/12` migration senkronize).
  - `supabase/tests/sync_write_v2_contract.sql` (28 sözleşme senaryosu) çalıştırıldı: **Başarılı (`Exit code 0`)**.
  - Koşulsuz `ROLLBACK` disiplini sayesinde veritabanında test kalıntısı bırakılmadı (`0` satır).
  - **Production Supabase'e kesinlikle dokunulmadı.**

---

## 7. Açık Bağımlılık Durumu

- Faz 8.2 aktif ve devam ediyor.
- Kullanıcı arayüzü ve abonelik akışları henüz başlatılmadı.

---

## 8. Kesin Sıradaki Adım

1. **Faz 8.2 — Tekrarlayan İşlemler V2 İstemci Entegrasyonu**:
   - Recurring rule için Android/KMP V2 istemci entegrasyonu:
     - `RecurringTransactionDto` ve domain mapper'ları,
     - `SyncEntityType.RECURRING_TRANSACTION` desteği,
     - `V2OutboxOperationExecutor` remote write & Room ACK akışı,
     - Recurring transaction kural CRUD repository ve use-case akışları.
   - UI (ekranlar/formlar) ve abonelikler bu teknik temelden sonra ele alınacaktır.

---

## 9. Ertelenen Bilinen Problem

- **"3 çakışma mevcut" Banner'ı**:
  - Eski V1 outbox / conflict tablolarındaki artık verilerden kaynaklanmaktadır.
  - Faz 7.3 / B5A (Conflict Recovery / Migration cleanup) henüz tam olarak tamamlandı sayılmamalıdır; V2 geçişleri ve temizlik mantığı oturduğunda ele alınacaktır.

---

## 10. Test Politikası

- Her küçük dilimde **yalnızca o dilimi ilgilendiren hedefli testler** çalıştırılmalıdır (Örn: `--tests "*Recurring*"`).
- `--rerun-tasks` veya tüm test paketini baştan koşan ağır komutlar faz sonuna kadar çalıştırılmamalıdır.

---

## 11. Güvenlik Kuralları

- `local.properties`, `.env`, API anahtarları, Service Role Key, kullanıcı tokenları ve finansal gerçek veriler hiçbir dokümana veya commit'e yazılmaz.
- Mobil istemci yalnızca publishable/anon key kullanır.

---

## 12. Güncel Git Durumu

- Çalışma ağacında Faz 8.2 Dilim 2B tamamlanmış olup `git diff --check` temizdir.
- Proje sahibinin açık onayı olmadan commit veya push yapılmaz.

---

## 13. Yeni Codex Sohbetine Başlangıç Promptu

Aşağıdaki metni yeni Codex sohbetinin ilk mesajı olarak yapıştırabilirsiniz:

```markdown
FeniqoMobil projesinde çalışıyoruz. Lütfen öncelikle kök dizindeki AGENTS.md, PRODUCT.md, ARCHITECTURE.md, DATABASE.md, FEATURES.md, DEVELOPMENT_PLAN.md, FENIQO_MOBIL_YOL_HARITASI.md ve docs/CODEX_HANDOFF.md belgelerini oku.

Mevcut Durum:
- Faz 8.2 (Tekrarlayan İşlemler) Dilim 1A–1E (vade hesaplama, aday planlama, Room atomik occurrence, repository/use-case, Android WorkManager), Dilim 2A (saf command/validation sözleşmesi) ve Dilim 2B (Supabase V2 migration & 28 senaryolu Staging SQL sözleşme testi) başarıyla tamamlandı.
- FeniqoMobil-Staging (ref: rxfaiynkhaxrksosxvxp) üzerinde 12/12 migration günceldir; Production Supabase'e dokunulmadı.
- Tüm hedefli birim/host testleri ve derleme kontrolleri başarılıdır.

Lütfen sıradaki teknik adım olan "Recurring rule için Android/KMP V2 istemci entegrasyonu (DTO/mapper, SyncEntityType, outbox ACK/pull ve CRUD akışı)" için planlama ve dilim adımlarını hazırla.
```

