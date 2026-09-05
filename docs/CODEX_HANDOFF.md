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
- **Faz 8.2 — Tekrarlayan İşlemler ve Abonelikler [TAMAMLANDI]**:
  - **Tekrarlayan İşlemler**: Vade takvim hesaplayıcı (`RecurrenceScheduleCalculator`), deterministik aday planlayıcı (`PlanDueRecurringOccurrencesUseCase`), Room atomik occurrence ve `LocalMutationDao.generateRecurringOccurrence` CAS & V2 outbox üretimi, WorkManager 24h periyodik işi (`RecurringTransactionWorker`, `RecurringTransactionWorkScheduler`), saf kural CRUD komutları ve tip güvenli `RecurringTransactionValidationRules`, Supabase V2 SQL migration (`20260830000100_sync_write_v2_recurring_transactions.sql`), V2 outbox ACK/pull/conflict senkronizasyonu ve MVI Compose liste/form kullanıcı akışları (`RecurringTransactionsScreen`, `RecurringTransactionFormScreen`, `RecurringTransactionDeleteDialog`) tamamlandı.
  - **Abonelikler**: Abonelik saf domain komutları (`CreateSubscriptionCommand`, `UpdateSubscriptionCommand`, `SetSubscriptionActiveCommand`), `SubscriptionValidationRules`, `SubscriptionRenewalStatusCalculator`, `SubscriptionRenewalProgressionCalculator`, Room v7/v8 tabloları (`subscriptions`, `subscription_payment_reminder_receipts`), Supabase V2 SQL migration (`20260831000100_sync_write_v2_subscriptions.sql`), V2 outbox ACK/pull/conflict senkronizasyonu, MVI Compose liste ve form ekranları (`SubscriptionsScreen`, `SubscriptionFormScreen`, `SubscriptionDeleteDialog`), ödeme hatırlatıcı saf planlayıcı (`PlanSubscriptionPaymentRemindersUseCase`), Room atomik receipt claim (`SubscriptionPaymentReminderReceiptDao.claim`), Android bildirim Worker'ı (`SubscriptionPaymentReminderWorker`), 24h scheduler (`WorkManagerSubscriptionPaymentReminderScheduler`), Android 13+ bildirim izni CTA banner'ı (`SubscriptionsScreenRoute`) ve açılış başlatıcısı tamamlandı.
  - **Kabul**: Android emülatör manuel smoke kabulü kullanıcı tarafından gerçekleştirildi ve başarıyla geçti.
- **Mobil Navigasyon Bilgi Mimarisi [TAMAMLANDI]**:
  - Alt bar `Ana Sayfa` / `İşlemler` / `+` / `Plan` / `Daha Fazla` 5'li kalıcı yapısına dönüştürüldü.
  - `+` (Orta Hızlı Buton): Doğrudan `TransactionFormRoute(null)` açar; bir route veya `TopLevelDestination` değildir; `popUpTo` kullanılmadığı için form sonrası kullanıcı geldiği kaynak ekrana döner.
  - `Plan` Hub (`PlanHubScreen`): Bütçeler, Tekrarlayan İşlemler, Abonelikler, Hedefler ve Borç/Alacak aktif modüllerdir.
  - `Daha Fazla` Hub (`MoreHubScreen`): Kategoriler ve Ayarlar aktif modüllerdir; Varlıklar, Raporlar, Ortak Alanlar, Bankalar ve Bildirimler pasif "Yakında" bilgi kartıdır.
  - `ThemeSettingsPlaceholderScreen`: Eski finansal araçlar ve no-op parametreler temizlenerek yalnız görünüm/ayarlar işlevine odaklandı.
  - Hub'lardan alt modüllere geçiş, geri dönüşler ve `+` eylemi Android emülatör manuel smoke kabulüyle kullanıcı tarafından doğrulandı.
- **Faz 8.3 — Hedefler ve Borçlar (Goals & Debts) [TAMAMLANDI]**:
  - **Hedefler (Goals)**: Birikim/tasarruf hedefi CRUD, hedefe para ekleme/çıkarma hareketleri (`GoalContribution`), ilerleme yüzdesi ve tahmini tamamlanma süresi hesabı, liste ve form MVI Compose akışları (`GoalsScreen`, `GoalFormScreen`, `GoalContributionFormDialog`).
  - **Borç ve Alacak (Debts & Receivables)**: Kişi/kurum bazlı borç ve alacak CRUD, kısmi/tam ödeme ve tahsilat hareketleri (`DebtPayment`), fail-closed reaktif bakiye ve durum hesaplayıcısı (`DebtBalanceCalculator`), liste ve form MVI Compose akışları (`DebtsScreen`, `DebtFormScreen`, `DebtPaymentFormDialog`).
  - **Faizsiz Borç Snowball Planlayıcısı**: Deterministik simülasyon motoru (`DebtSnowballPlanner`), seçili para birimi ve aylık bütçe ile borç kapanış sırası ve aylık tahsis planlama ekranı (`DebtSnowballPlanScreen`).
  - **Veri ve Senkronizasyon**: Room v9/v10/v11 şemaları (`goals`, `goal_contributions`, `debts`, `debt_payments`), V2 outbox/ACK/pull/conflict senkronizasyonu, Staging 15/15 migration (`20260901000100_sync_write_v2_goals_and_debts.sql`, `20260901000200_reconcile_goals_debts_sync_contract.sql`), SQL sözleşme testi (koşulsuz ROLLBACK ile 0 kalıntı).
  - **Kabul**: Android emülatör manuel smoke kabulü kullanıcı tarafından gerçekleştirildi ve başarıyla geçti. Production Supabase'e dokunulmadı.
- **Aktif Aşama — Faz 8.4: Ortak Çalışma Alanları (Workspaces) [BAŞLANACAK]**:
  - Çalışma alanı oluşturma, katılma, ayrılma, aktif alan seçimi, üye listesi, rol tabanlı yetki matrisi (`OWNER`, `EDITOR`, `VIEWER`), ortak işlem ve bütçe görünürlüğü, kimin ne kadar ödediği ve borç dağılımı (split) hesaplama motorunun saf domain modelleri, validation invariant'ları, Room DAO/V2 outbox ve sync altyapısı planlanacaktır.

---

## 5. Tamamlanan Fazlar ve Modüller

- **Faz 8.1**: Bütçeler modülünün tüm domain, Room, outbox, remote, UI ve emülatör kabul adımları.
- **Faz 8.2**: Tekrarlayan işlemler ve abonelikler modüllerinin tüm domain, validation, Room (v6, v7, v8), Supabase V2 migration'ları (12 ve 13), `sync_write_v2` 38 senaryolu sözleşme testi, MVI Compose ekranları, hatırlatıcı altyapısı ve emülatör kabul adımları.
- **Mobil Navigasyon Bilgi Mimarisi**: 5'li kalıcı alt bar, type-safe route'lar, Plan ve Daha Fazla hub ekranları, pasif Yakında modülleri ve Android emülatör kabulü.
- **Faz 8.3**: Hedefler ve borçlar modüllerinin tüm domain/validation modelleri, Room (v9, v10, v11), Supabase V2 migration'ları (14 ve 15), sözleşme testi, MVI Compose liste/form/snowball ekranları ve Android emülatör kabul adımları.

---

## 6. Doğrulama ve Staging Durumu

- **Hedefli Birim/Host Testleri**:
  - Tüm use-case, validation, Room DAO, outbox executor, Worker, WorkScheduler, Initializer, ViewModel, HubRegistry, FeniqoRoutes ve Snowball planner/model testleri: **Tümü başarılı (`BUILD SUCCESSFUL`)**.
- **KMP Platform Derlemesi**:
  - `sharedLogic:testAndroidHostTest`, `sharedLogic:compileKotlinIosSimulatorArm64`, `androidApp:compileDebugKotlin`, `androidApp:assembleDebug`: **`BUILD SUCCESSFUL`**.
- **Staging SQL Sözleşme Doğrulaması**:
  - `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`) üzerinde 15/15 migration günceldir (`20260901000100_sync_write_v2_goals_and_debts.sql` ve `20260901000200_reconcile_goals_debts_sync_contract.sql` dâhil).
  - `supabase/tests/sync_write_v2_contract.sql` çalıştırıldı: **Başarılı (`Exit code 0`)**.
  - Koşulsuz `ROLLBACK` disiplini sayesinde veritabanında test kalıntısı bırakılmadı (`public.goals`, `public.goal_contributions`, `public.debts`, `public.debt_payments`, `public.sync_operations_receipts` = `0` satır kalıntı).
  - **Production Supabase'e kesinlikle dokunulmadı.**

---

## 7. Sıradaki İş: Faz 8.4 — Ortak Çalışma Alanları (Workspaces)

Sıradaki geliştirme diliminde Faz 8.4 için şu adımlar planlanacaktır:
1. **Domain & Validation**:
   - `Workspace` (isim, tip, para birimi, açıklama), `WorkspaceMember` (kullanıcı, rol: `OWNER`, `EDITOR`, `VIEWER`), `WorkspaceInvitation` ve validasyon kuralları.
   - Ortak harcamalar için "kim ödedi", "kimler arasında bölüşülecek" (split) modelleri ve borç mahsuplaşma motoru.
2. **Room Veri Katmanı**:
   - `workspaces`, `workspace_members`, `workspace_invitations` entity'leri, DAO'lar, şema ve migration hazırlığı.
3. **V2 Outbox & Supabase Sync**:
   - `WORKSPACE`, `WORKSPACE_MEMBER` mutation executor'ları, Staging SQL migration'ı ve sözleşme testi.

---

## 8. Test Politikası

- Her küçük dilimde **yalnızca o dilimi ilgilendiren hedefli testler** çalıştırılmalıdır.
- `--rerun-tasks` veya tüm test paketini baştan koşan ağır komutlar çalıştırılmamalıdır.

---

## 9. Güvenlik Kuralları

- `local.properties`, `.env`, API anahtarları, Service Role Key, kullanıcı tokenları ve finansal gerçek veriler hiçbir dokümana veya commit'e yazılmaz.
- Mobil istemci yalnızca publishable/anon key kullanır.

---

## 10. Güncel Git Durumu

- Çalışma ağacında Faz 8.3 tamamlanmış olup `git diff --check` temizdir.
- Proje sahibinin açık onayı olmadan commit veya push yapılmaz.

---

## 11. Yeni Codex Sohbetine Başlangıç Promptu

Aşağıdaki metni yeni Codex sohbetinin ilk mesajı olarak yapıştırabilirsiniz:

```markdown
FeniqoMobil projesinde çalışıyoruz. Lütfen öncelikle kök dizindeki AGENTS.md, PRODUCT.md, ARCHITECTURE.md, DATABASE.md, FEATURES.md, DEVELOPMENT_PLAN.md, FENIQO_MOBIL_YOL_HARITASI.md ve docs/CODEX_HANDOFF.md belgelerini oku.

Mevcut Durum:
- Mobil Navigasyon Bilgi Mimarisi (5'li kalıcı alt bar: Ana Sayfa / İşlemler / + / Plan / Daha Fazla, Plan hub, Daha Fazla hub ve pasif Yakında modülleri) tamamlandı.
- Faz 8.1 (Bütçeler), Faz 8.2 (Tekrarlayan İşlemler ve Abonelikler) ve Faz 8.3 (Hedefler ve Borçlar: Goals, Goal Contributions, Debts, Debt Payments, Debt Snowball Planner) tüm katmanlarıyla tamamlandı ve Android emülatör manuel smoke kabulü başarıyla geçti.
- FeniqoMobil-Staging (ref: rxfaiynkhaxrksosxvxp) üzerinde 15/15 migration günceldir; SQL sözleşme testi başarılıdır (0 kalıntı); Production Supabase'e dokunulmadı.
- Tüm hedefli birim/host testleri ve platform derleme kontrolleri başarılıdır.

Sıradaki Planlanan İş:
- "Faz 8.4 — Ortak Çalışma Alanları (Workspaces)":
  - Çalışma alanı oluşturma, katılma, ayrılma, aktif alan seçimi, üye listesi ve rol tabanlı (`OWNER`, `EDITOR`, `VIEWER`) kurallar.
  - Ortak harcama/işlem ve bütçe görünürlüğü, kimin ne kadar ödediği ve borç dağılımı (split) hesaplama motoru.
  - Domain modelleri, validation invariant'ları, use case'ler ve Room planlaması.

Lütfen Faz 8.4 (Ortak Çalışma Alanları) için ilk küçük, güvenli ve test edilebilir uygulama dilimini (Domain modelleri ve validation kuralları) hazırla.
```

