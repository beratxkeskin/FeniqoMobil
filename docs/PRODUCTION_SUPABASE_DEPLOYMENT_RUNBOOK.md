# FeniqoMobil — Production Supabase Deployment Runbook ve Onay Paketi

> **Hedef Ortam:** `FeniqoMobil-Production`
> **Project Ref:** `qgmymavltjnmfuzvfxiq`
> **Mevcut Durum:** `BACKEND: PRODUCTION UYGULANDI VE DOĞRULANDI (MOBİL/WEB ROLLOUT YAPILMADI)`
> **Yedek Durumu:** `DOĞRULANDI — APPLICATION-SCOPE SCHEMA-ONLY LOGICAL BACKUP`
> **Tarih:** 2026-09-27
> **Yetkili Doküman:** Bu belge, FeniqoMobil üretim veritabanı kurulumunun tek resmi operasyon ve kabul kaynağıdır.

---

## 1. Durum Çerçevesi ve Yaşam Döngüsü

Production veritabanı yaşam döngüsü aşağıdaki 5 kesin aşamadan oluşur:

1. **Production read-only denetlendi:** `[TAMAMLANDI — 2026-09-27]`
   PostgreSQL 17.6 metadata, şema ve migration history salt-okunur denetlendi. Public uygulama şemasının yeni/boş olduğu kanıtlandı.
2. **Production uygulamasına hazırlık tamamlandı:** `[TAMAMLANDI — 2026-09-27]`
   32 migration analiz edildi, runbook ve post-check kontrolleri yazıldı. Application-scope schema-only backup ve local restore testi doğrulandı.
3. **Production uygulamasına hazır:** `[TAMAMLANDI — 2026-09-27]`
   Yedek doğrulaması, bakım penceresi ve proje sahibinin açık mutation onayı sağlandı.
4. **Production uygulandı:** `[TAMAMLANDI — 2026-09-27]`
   Onaylanan 32 migration sırasıyla Supabase CLI üzerinden başarıyla uygulandı (`ExitCode = 0`). Remote migration history: 32/32 (Son timestamp: `20260927000100`).
5. **Production sonrası doğrulandı:** `[TAMAMLANDI — 2026-09-27]`
   Salt-okunur metadata post-check matrisi %100 geçti (19/19 tablo, RLS aktif, 16/16 trigger etkin, RPC/ACL kontrolleri temiz, 0 para drift, 0 geçersiz index, 0 doğrulanmamış constraint, upToDate=true dry-run). CLI bağlantısı derhal staging ref'ine (`rxfaiynkhaxrksosxvxp`) geri döndürüldü.

> **Önemli Ayrım (Backend vs Rollout):** Backend veritabanı durumu kesin olarak **`PRODUCTION UYGULANDI VE DOĞRULANDI`** aşamasındadır. Mobil ve web istemcilerinin canlıya çıkışı (production rollout) henüz **`YAPILMAMIŞTIR`**; hiçbir istemci production veritabanına yönlendirilmemiştir.

---

## 2. Doğrulanmış Sistem Gerçekliği

### 2.1 Staging Referansı (`FeniqoMobil-Staging` / `rxfaiynkhaxrksosxvxp`)
* **Migration Durumu:** 32 / 32 başarıyla uygulandı (`20260927000100` dahil).
* **Doğrulanan Sözleşmeler:** RLS güvenlik sözleşmesi, davet güvenlik sözleşmesi, `sync_write_v2` RPC sözleşmesi, para/web-mobil uyumluluk testi ve iki gerçek Supabase Auth kullanıcısıyla uçtan uca PostgREST/RPC izolasyon testi %100 geçti.
* **Residual Durumu:** 0 test kullanıcısı, 0 test kaydı.

### 2.2 Production Doğrulanmış Durumu (`FeniqoMobil-Production` / `qgmymavltjnmfuzvfxiq`)
* **Uygulama Zamanı:** `2026-09-27`
* **PostgreSQL Engine:** Postgres 17.6 (`17.6.1.166` / `eu-central-1`).
* **Remote Migration History:** **32 / 32** (Tamamı başarıyla uygulandı; son timestamp: `20260927000100`).
* **Public Uygulama Şeması:** **Tam 19 tablo**, tamamında RLS aktif (`relrowsecurity = true`), 19 primary key, 0 geçersiz index, 0 doğrulanmamış constraint.
* **Tetikleyiciler:** 16/16 senkronizasyon sunucu metadata tetikleyicisi (`*_set_server_metadata`) aktif (`pg_trigger.tgenabled = 'O'`); para uyumluluk, workspace rol kontrolü, profil oluşturma ve kimlik koruma trigger'ları aktif.
* **Fonksiyon ve RPC Güvenliği:** `sync_write_v2`, `redeem_workspace_invitation_v1`, `transfer_workspace_ownership_v1`, `claim_market_price_request` imza, `search_path` ve yetki (`GRANT EXECUTE TO authenticated`) kontrolleri tam geçti. `workspace_invitations.token_hash` kolonu `authenticated` ve `anon` için SELECT yetkisine kapalı (yalnız SECURITY DEFINER RPC okuyabilir).
* **Yetki İzolasyonu:** Anon veya PUBLIC için hiçbir tablo mutasyon yetkisi (INSERT, UPDATE, DELETE, TRUNCATE) yoktur (sayı: **0**).
* **Realtime:** `supabase_realtime` publication üye tabloları tam olarak 3 tablodur: `categories`, `profiles`, `transactions`.
* **Katalog ve Sistem Verisi:** `categories` tablosunda tam 27 aktif varsayılan sistem kategorisi + 2 tombstone (`is_default = true`).
* **Para Uyumluluğu:** `amount_minor` / `amount` para uyumluluğunda null, negatif veya drift sayısı: **0**.
* **Storage ve Edge:** Storage bucket sayısı **0**, dağıtılmış Edge Function sayısı **0** (V1 Core kapsamına uygun).
* **Son Dry-Run:** `npx supabase db push --linked --dry-run` çıktısı `upToDate = true` döndü (0 bekleyen, 0 remote-only, 0 drift).
* **CLI Bağlantı İzolasyonu:** Uygulama ve post-check tamamlandıktan hemen sonra CLI bağlantısı Staging ref'ine (`rxfaiynkhaxrksosxvxp`) geri döndürüldü.
* **Kullanıcı ve Test İzolasyonu:** Production üzerinde hiçbir test kullanıcısı oluşturulmadı, hiçbir negatif mutation testi çalıştırılmadı. Mobil/web canlı rollout henüz yapılmadı.

---

## 3. Kapsam ve Mimari Kararları

1. **Makbuz ve Depolama (Storage):** V1 core mobil sürümünde makbuz depolama kapsam dışıdır. Production'da makbuz bucket'ı veya storage policy'si oluşturulmayacaktır.
2. **Edge Functions:** Piyasa fiyatı (`market-prices`) Edge Function'ı V1 core kapsamı dışındadır; deploy edilmeyecektir.
3. **Web Uygulaması Uyumluluğu:** Web istemcisinin canlıda aktif olup olmadığı kesin bilinmediğinden en güvenli varsayım kabul edilir: *Web uygulaması aktif olabilir*. Bu nedenle `amount`, `amount_minor`, `receipt_url`, `receipt_path` iki yönlü trigger uyumluluk katmanı korunacaktır.
4. **Para Birimi Sınırı:** Production veritabanında GBP desteklenmemektedir; V1 çekirdek para birimleri `TRY`, `USD`, `EUR`'dur.
5. **İstemci Bağlantı Yasağı:** Web veya mobil istemciler, production veritabanında tüm 32 migration uygulanıp metadata post-check başarıyla geçmeden kesinlikle production ortamına yönlendirilmeyecektir.

---

## 4. Kesin 32 Migration Analiz Matrisi

Aşağıdaki 32 dosya, repository'nin `supabase/migrations/` dizinindeki timestamp sırasına göre kaynak koddan doğrulanarak listelenmiştir:

| # | Timestamp / Dosya | Sorumluluk Özeti | DDL Kapsamı | Veri / Backfill | Lock Etkisi | Tekrar Çalışabilirlik | Rollback / Restore Riski | Post-Check Kontrolü |
|---|---|---|---|---|---|---|---|---|
| 1 | `20260814000000_schema_web_v1_baseline.sql` | Web V1 taban şeması (profiles, categories, transactions) | `CREATE TABLE` (3 başlangıç tablosu), types, indexes, `handle_new_user_v1`, trigger | Yok | Düşük (boş şema) | Hayır (yalnız temiz DB) | Tabloları drop etme riski | 3 başlangıç tablosu oluşumu |
| 2 | `20260814000100_schema_sync_metadata.sql` | Sync kolonları (`updated_at`, `version`, `deleted_at`, `slug`, `receipt_path`) | `ALTER TABLE ADD COLUMN` (yalnız `profiles`, `categories`, `transactions`), sync index'leri | Kolon default'ları | Düşük | Hayır (`IF NOT EXISTS` yok) | Kolon silinmesi | 3 tabloda sync kolonları ve cursor index'leri |
| 3 | `20260814000200_schema_money_expand.sql` | `transactions` tablosuna `amount_minor` (Long kuruş) ve `currency` kolonları ekleme | `ALTER TABLE public.transactions ADD COLUMN amount_minor bigint, currency text`, check constraints | Yok | Düşük | Hayır (`IF NOT EXISTS` yok) | Constraint ihlali | `transactions.amount_minor` ve `transactions.currency` |
| 4 | `20260814000300_data_money_backfill.sql` | Eski `amount` (Double) verilerini `amount_minor`'a taşıma ve para uyumluluk trigger'ı | `CREATE FUNCTION public.sync_transactions_money_compat()`, `CREATE TRIGGER transactions_money_compat_before_write` | `UPDATE` (0 satır) | Düşük (0 satır) | Evet (idempotent UPDATE, `OR REPLACE`) | Hatalı hesaplama | Checksum ve trigger doğrulaması |
| 5 | `20260814000400_functions_conditional_sync.sql` | Server metadata trigger fonksiyonu (`sync_set_server_metadata`), 3 tablo trigger'ı ve `sync_write_v1` RPC'si | `CREATE OR REPLACE FUNCTION public.sync_set_server_metadata()`, 3 trigger, `CREATE OR REPLACE FUNCTION public.sync_write_v1(...)` | Yok | Düşük | Evet (`OR REPLACE`) | RPC imza kaybı | Trigger'lar ve `sync_write_v1` RPC imza/ACL testi |
| 6 | `20260814000500_rls_v1_personal.sql` | Kişisel tablolar için RLS, kimlik koruma trigger'ları ve temel policy'ler | `ALTER TABLE ENABLE RLS`, `CREATE POLICY`, 3 protect_identity trigger'ı | Yok | Düşük | Evet (DROP/CREATE) | Yanlışlıkla tablo kilitleme | RLS=true, trigger'lar ve policy sayısı |
| 7 | `20260815000100_realtime_v1_publication.sql` | Realtime publication'a tabloların eklenmesi (`profiles`, `categories`, `transactions`) | `ALTER PUBLICATION ADD TABLE` | Yok | Düşük | Evet | Senkronizasyon kesintisi | Publication üye listesi (3 tablo) |
| 8 | `20260816000100_seed_default_categories_v1.sql` | Temel sistem kategorileri seed verisi | Yok | `INSERT ON CONFLICT` | Düşük | Evet (idempotent) | Eksik kategori | 17 temel kategori |
| 9 | `20260826000100_sync_write_v2_receipts.sql` | `sync_operations_receipts` tablosu | `CREATE TABLE`, RLS, index | Yok | Düşük | Evet (`IF NOT EXISTS`) | Receipt kaybı | Tablo ve RLS varlığı |
| 10 | `20260826000200_sync_write_v2_rpc.sql` | Ana V2 sync RPC motoru (`sync_write_v2`) | `CREATE FUNCTION public.sync_write_v2(...)` | Yok | Düşük | Evet (`OR REPLACE`) | Atomik yazma hatası | RPC imza/ACL doğrulaması |
| 11 | `20260829000100_sync_write_v2_budgets.sql` | Bütçe tablosu ve V2 sync entegrasyonu | `CREATE TABLE public.budgets`, RLS, trigger, RPC update | Yok | Düşük | Evet | Bütçe kısıt hatası | `budgets` tablosu |
| 12 | `20260830000100_sync_write_v2_recurring_transactions.sql` | Tekrarlayan işlemler tablosu ve RPC | `CREATE TABLE public.recurring_transactions`, RLS, trigger, RPC update | Yok | Düşük | Evet | Periyot kısıt hatası | `recurring_transactions` tablosu |
| 13 | `20260831000100_sync_write_v2_subscriptions.sql` | Abonelikler tablosu ve RPC | `CREATE TABLE public.subscriptions`, RLS, trigger, RPC update | Yok | Düşük | Evet | Tarih/tutar kısıt hatası | `subscriptions` tablosu |
| 14 | `20260901000100_sync_write_v2_goals_and_debts.sql` | Hedefler ve borçlar tabloları ve RPC | `CREATE TABLE` (4 tablo: goals, goal_contributions, debts, debt_payments), RLS, triggers, RPC | Yok | Düşük | Evet | FK ilişki hatası | `goals`, `debts` tabloları |
| 15 | `20260901000200_reconcile_goals_debts_sync_contract.sql` | Hedef/borç sync sözleşmesi uyumlaştırması | RPC güncelleme (`sync_write_v2`) | Yok | Düşük | Evet (`OR REPLACE`) | Payload ayrıştırma | RPC doğrulaması |
| 16 | `20260906000100_sync_write_v2_workspaces.sql` | Ortak alanlar tablosu ve RPC | `CREATE TABLE` (2 tablo: workspaces, workspace_members), RLS, triggers, RPC update | Yok | Düşük | Evet | Rol yetki hatası | `workspaces`, `workspace_members` |
| 17 | `20260907000100_sync_write_v2_workspace_members_and_invitations.sql` | Workspace davet tablosu ve `redeem_workspace_invitation_v1` RPC'si | `CREATE TABLE public.workspace_invitations`, RLS, `CREATE FUNCTION public.redeem_workspace_invitation_v1(text)`, RPC update | Yok | Düşük | Evet (`IF NOT EXISTS`, `OR REPLACE`) | Token sızıntısı | `workspace_invitations` ve `redeem_workspace_invitation_v1` |
| 18 | `20260908000100_transfer_workspace_ownership_v1.sql` | Workspace sahiplik devri RPC'si (`transfer_workspace_ownership_v1`) | `CREATE FUNCTION public.transfer_workspace_ownership_v1(...)` | Yok | Düşük | Evet (`OR REPLACE`) | Yetkisiz devir | RPC imza/güvenlik |
| 19 | `20260908000200_sync_write_v2_workspace_member_removal.sql` | Workspace üye çıkarma RLS v2 ve `sync_write_v2` WORKSPACE_MEMBER DELETE akışı | `DROP/CREATE POLICY workspace_members_select_v2`, RPC update (`sync_write_v2`) | Yok | Düşük | Evet (`OR REPLACE`) | Yanlış üye silme | `workspace_members_select_v2` ve `sync_write_v2` |
| 20 | `20260908000300_sync_write_v2_transaction_split.sql` | İşlem bölüştürme (split): `transactions` kolonları (`paid_by_user_id`, `participant_user_ids`) | `ALTER TABLE public.transactions ADD COLUMN...`, 2 index, policy update, RPC update | `UPDATE` (0 satır) | Düşük | Evet (`IF NOT EXISTS`, `OR REPLACE`) | Toplam tutar uyuşmazlığı | `transactions` split kolonları ve `sync_write_v2` |
| 21 | `20260908000400_sync_write_v2_assets.sql` | Varlıklar (Assets) tablosu ve RPC | `CREATE TABLE public.assets`, RLS, trigger, RPC update | Yok | Düşük | Evet | Değerleme hatası | `assets` tablosu |
| 22 | `20260909000100_market_prices_read_model.sql` | Piyasa fiyatları okuma modeli tablosu | `CREATE TABLE public.market_prices`, RLS, grants | Yok | Düşük | Evet | Cache kirlenmesi | `market_prices` tablosu |
| 23 | `20260909000200_market_price_rate_limit.sql` | Piyasa fiyatı rate limiting tablosu ve atomik kota RPC'si (`claim_market_price_request`) | `CREATE TABLE public.market_price_rate_limits`, RLS, `CREATE FUNCTION public.claim_market_price_request()`, ACL | Yok | Düşük | Evet | API kotası aşımı | `market_price_rate_limits` ve `claim_market_price_request()` |
| 24 | `20260912000100_add_transaction_note.sql` | İşlemlere `note` kolonu ekleme | `ALTER TABLE public.transactions ADD COLUMN note text` | Yok | Düşük | Evet (`IF NOT EXISTS`) | Kolon kaybı | `transactions.note` kolonu |
| 25 | `20260913000100_subscription_lifecycle_and_history.sql` | Abonelik yaşam döngüsü alanları, `subscription_price_histories` ve `subscription_payments` tabloları | `ALTER TABLE public.subscriptions ADD COLUMN...`, `CREATE TABLE public.subscription_price_histories`, `CREATE TABLE public.subscription_payments`, RLS, triggers | Yok | Düşük | Evet (`IF NOT EXISTS`) | Statü geçiş hatası | `subscription_price_histories`, `subscription_payments` |
| 26 | `20260913000200_subscription_website_and_notes.sql` | Aboneliklere web sitesi ve not kolonları ekleme | `ALTER TABLE public.subscriptions ADD COLUMN website_url, notes` | Yok | Düşük | Evet (`IF NOT EXISTS`) | Kolon kaybı | `subscriptions.website_url`, `notes` |
| 27 | `20260918000100_sync_write_v2_custom_split.sql` | Özel split oranları ve doğrulama kuralı | RPC güncelleme (`sync_write_v2`) | Yok | Düşük | Evet (`OR REPLACE`) | Kuruş yuvarlama hatası | Split RPC doğrulaması |
| 28 | `20260921000100_seed_extended_canonical_categories.sql` | 27 kanonik kategori seed ve 2 legacy tombstone | Yok | `INSERT/UPDATE ON CONFLICT` | Düşük | Evet (idempotent) | Kategori ID değişimi | 27 aktif + 2 tombstone sistem kategorisi |
| 29 | `20260926000100_harden_workspace_invitation_token_hash_visibility.sql` | Davet token hash gizleme (kolon bazlı ACL) | `REVOKE/GRANT` kolon yetkileri | Yok | Düşük | Evet | Hash sızıntısı | Kolon yetki matrisi |
| 30 | `20260926000200_harden_workspace_transaction_viewer_mutations.sql` | VIEWER rolünün işlem değiştirmesini önleme | Trigger fonksiyonu güncelleme (`transactions_enforce_workspace_mutation_role_v1`) | Yok | Düşük | Evet (`OR REPLACE`) | VIEWER yetkisiz yazma | Trigger varlığı ve kuralı |
| 31 | `20260926000300_harden_workspace_table_privileges.sql` | Workspace tablolarında authenticated ACL sıkılaştırma | `REVOKE TRUNCATE, TRIGGER on workspaces, workspace_members` | Yok | Düşük | Evet | Geniş yetki kalması | Tablo yetki matrisi |
| 32 | `20260927000100_harden_money_compatibility_trigger.sql` | `transactions_money_compat_before_write` tetikleyicisi ve fonksiyon sıkılaştırma | Trigger fonksiyonu güncelleme, trigger drop/create, katalog pre/post check | Yok | Düşük | Evet (`OR REPLACE`) | Para drift/yuvarlama | Trigger varlığı ve fonksiyonu |

### 4.1 Baseline Stratejisi
* **Karar Gerekçesi:** Yalnızca production ortamının `public` uygulama şemasının tamamen boş olduğu ve migration history'nin `0` olduğu kesin metadata denetimiyle kanıtlandığı için tam 32 migration zincirinin uygulanması uygundur.
* **Genellenemezlik Uyarısı:** Bu karar dolu veya kısmen migrate edilmiş veritabanlarına genellenemez.
* **Uygulama Şekli:** Baseline (`20260814000000`) asla tek başına veya manuel SQL olarak çalıştırılmayacaktır; tüm zincir Supabase CLI üzerinden sıralı olarak işletilecektir.

---

## 5. Veri ve Kilit (Lock) Değerlendirmesi

1. **Satır Sayısı:** Public uygulama tabloları boş olduğu için veri backfill migration'ı (`20260814000300`) 0 satır üzerinde çalışacaktır.
2. **Kullanıcı Verisi Durumu:** Public uygulama şeması tamamen boştur. `auth.users` gizlilik sınırı gereği taranmamıştır; istemcilerin production'a henüz yönlendirilmediği operasyon öncesinde proje sahibi tarafından ayrıca doğrulanmalıdır.
3. **Tablo Rewrite / Lock Davranışı:** Uygulama tabloları boş olduğu için düşük lock ve kısa işlem süresi beklenir. Ancak gerçek süre ve lock beklemesi deployment anındaki açık veritabanı oturumlarına ve bağlantılara bağlıdır. Dry-run kesin süre veya lock garantisi vermez.
4. **Kullanıcı ve Bakım Etkisi:** Canlı istemciler (mobil/web) henüz üretim veritabanına yönlendirilmemişse son kullanıcı açısından kesinti etkisi beklenmez. İstemcilerin yönlendirilmediği operasyon öncesinde teyit edilmelidir.
5. **Atomisite:** Migration zinciri çok sayıda DDL, fonksiyon ve politika oluşturduğundan dosya bazlı atomik sınırlar ve hata anında fail-closed durma prensibi korunmalıdır.

---

## 6. Yedek Kapısı (Backup Gate) — ZORUNLU ÖN KOŞUL

> ### ✅ GÜNCEL DURUM: `DOĞRULANDI — APPLICATION-SCOPE SCHEMA-ONLY LOGICAL BACKUP`
> **Production Mutation Durumu:** `TAMAMLANDI — 32/32 MIGRATION UYGULANDI VE DOĞRULANDI`

### 6.1 Doğrulanan Yedek ve Güvenlik Kanıtı (2026-09-27)

* **Yedek Türü ve Sınıflandırması:** `Application-Scope Schema-Only Logical Backup`.
  *Önemli Sınır ve Uyarı:* Bu yedek bir **fiziksel Supabase platform yedeği veya Point-in-Time Recovery (PITR) yedeği DEĞİLDİR**. Supabase Free Plan kapsamında platform düzeyinde zamanlanmış/fiziksel snapshot özelliği bulunmamaktadır.
* **Kurtarma Kapsamı:** Bu yedek yalnızca üretim ortamının migration öncesi mevcut **boş public uygulama şemasını ve sıfır migration durumunu** geri oluşturma kabiliyetine sahiptir.
* **Hariç Bırakılan Alanlar:** `auth.users`, `storage.objects` veya Vault secret payload'ı içermez. (Proje sahibi tarafından Supabase Dashboard üzerinden üretim projesindeki Auth kullanıcı sayısının tam olarak **0** olduğu doğrulanmıştır.)
* **Dosya ve Manifest Ayrıntıları:**
  * **Yedek Dizini:** `C:\Users\hp\FeniqoBackups\FeniqoMobil-Production\pre-migration-2026-09-27\`
  * **Yedek Dosyası:** `production_application_schema_pre_migration.sql`
  * **Manifest:** `backup_manifest.txt`
  * **Oluşturulma Zamanı (UTC):** `2026-09-27T09:56:30Z`
  * **Boyut:** `874` bayt
  * **SHA-256 Checksum:** `C484AE65A76CDC82CDB96C07F2C10A9E1810ECF5981F8D6EDE6E3E6DFBADB4E7` (Doğrulandı)
* **Production Preflight Durumu:**
  * Public tablo: `0`, fonksiyon: `0`, trigger: `0`, policy: `0`
  * Migration şeması (`supabase_migrations`): `yok`, tablo: `yok`, migration satırı: `0`
  * Storage bucket sayısı: `0`
* **Güvenlik ve Parola Taraması:**
  * Script ve yedek dosyası parola, JWT veya secret sızıntısı içermez (Temiz).
* **Yerel PostgreSQL 18 Restore Kanıtı:**
  * Yerel PostgreSQL 18.3 üzerinde tek kullanımlık (disposable) geçici veritabanına restore başarıyla uygulandı (`ExitCode = 0`).
  * Restore sonrası katalog denetimi (post-check): `0` tablo, `0` fonksiyon, `0` trigger, `0` policy.
  * Disposable DB temizliği: Veritabanı başarıyla silindi; residual DB sayısı: `0`.
  * Kilit / geçici dosya temizliği: Partial/lock residual sayısı: `0`.
* **Sonuç:** Yedek kapısı ön koşulları eksiksiz karşılanarak production migration aşamasına geçiş izni verilmiştir.

### 6.2 Yedek ve Operasyonel Kontrol Matrisi

| Kontrol Alanı | Gerçekleşen Değer / Durum |
|---|---|
| **Supabase Dashboard Yolu** | `Dashboard -> Project (qgmymavltjnmfuzvfxiq) -> Database -> Backups` (Free Plan: Platform snapshot/PITR aktif değil) |
| **Backup Zamanı (UTC)** | `2026-09-27T09:56:30Z` |
| **Backup Türü** | Application-Scope Schema-Only Logical Backup (`pg_dump` public şeması) |
| **Yedek Durumu** | `COMPLETED` & `VERIFIED` (874 bayt, SHA-256 doğrulandı) |
| **Geri Yüklenebilirlik Doğrulaması**| Yerel PostgreSQL 18 üzerinde disposable veritabanında restore testi %100 başarılı; post-check 0 nesne; residual 0 |
| **Doğrulayan Sorumlu** | Proje Sahibi & Güvenli Otomasyon Scripti |
| **Restore Hedefi ve Prosedürü** | Temiz/boş projeye SQL restore; sıfır şema durumuna geri dönüş |
| **Restore İçin Gereken Yetki** | Supabase Organization Admin / Owner / Veritabanı Süper Kullanıcısı |
| **Tahmini Restore Etkisi** | Public şemadaki tanımları sıfırlar; Auth/Storage payload içermez |
| **Kanıt / Referans Ekranı** | `backup_manifest.txt` ve SHA-256 hash doğrulaması |

### 6.3 Operasyonel Sonuç ve Kalan Adımlar

Yedek kapısı doğrulandıktan ve operasyonel onay sağlandıktan sonra, 32 migration sırayla production ortamına uygulanmış ve katalog post-check kontrolleri %100 başarıyla geçmiştir.

* **Backend Veritabanı Durumu:** **`PRODUCTION UYGULANDI VE DOĞRULANDI`** (Tamamlandı).
* **Kalan Operasyonel Adım:** Mobil ve web istemcilerinin canlıya çıkışı (production rollout) henüz yapılmamıştır.

---

## 7. Geri Alma (Rollback), Geri Yükleme ve Forward-Fix İlkeleri

1. **Down Migration Yasağı:** Repository'de geriye dönük "down migration" dosyaları yazılmayacak ve çalıştırılmayacaktır.
2. **Değiştirilemezlik (Immutability):** Uygulanmış bir migration dosyası asla geriye dönük düzenlenmeyecektir.
3. **Transaction İçi Hata:** Bir migration dosyası çalışırken hata verirse, dosya düzeyindeki PostgreSQL transaction'ı nedeniyle o dosyanın değişiklikleri otomatik olarak geri alınır (rollback).
4. **Kısmi Tamamlanma:** Önceki migration'lar commit edilmişken sonraki bir dosyada hata oluşursa, zorla devam edilmeyecek; sistem olduğu yerde durdurulacaktır.
5. **Düzeltme Yolu:** Karşılaşılan hata durumunda proje sahibinin kararıyla ya yedekten geri dönülecek ya da yeni timestamp'li bir **forward-only** migration dosyası hazırlanacaktır.
6. **History Repair Yasağı:** `migration repair` komutu ile geçmiş elle manipüle edilmeyecektir.

---

## 8. Uygulama Komut Planı (2026-09-27'de Başarıyla Yürütüldü ve Doğrulandı)

Aşağıdaki adımlar, yedek onaylandıktan ve açık mutation izni alındıktan sonra işletilmiş ve doğrulanmıştır:

```powershell
# A. Başlangıç Durum Doğrulaması [TAMAMLANDI]
git status --short --branch
npx supabase projects list
Get-Content supabase/.temp/project-ref

# B. Production'a Geçici Olarak Bağlanma [TAMAMLANDI]
npx supabase link --project-ref qgmymavltjnmfuzvfxiq

# C. Production Preflight Denetimi [TAMAMLANDI]
npx supabase migration list
# Doğrulandı: Remote 0, Local 32

# D. Dry-Run Simülasyonu [TAMAMLANDI]
npx supabase db push --linked --dry-run
# Doğrulandı: Tam olarak 32 onaylanan migration listelendi

# E. GERÇEK UYGULAMA [TAMAMLANDI]
npx supabase db push --linked --yes
# Doğrulandı: 32 migration sırasıyla hatasız uygulandı

# F. Salt-Okunur Post-Check Denetimi [TAMAMLANDI]
# (Runbook Bölüm 10'daki tüm katalog ve yetki kontrolleri %100 geçti)

# G. Repository Link'ini Derhal Staging'e Geri Döndürme [TAMAMLANDI]
npx supabase link --project-ref rxfaiynkhaxrksosxvxp
Get-Content supabase/.temp/project-ref
npx supabase projects list
```

---

## 9. Kesin Durdurma (Stop) Koşulları

Aşağıdaki durumlardan herhangi biri tespit edildiğinde operasyon derhal durdurulmalıdır:

1. Bağlı proje ref'i `qgmymavltjnmfuzvfxiq` dışında herhangi bir değerse.
2. Production migration listesinde remote sayısı 0 dışında herhangi bir değerse.
3. `public` şemasında önceden oluşmuş herhangi bir uygulama tablosu veya fonksiyonu tespit edilirse.
4. `db push --dry-run` çıktısı tam olarak 32 onaylı dosyadan farklı bir liste gösterirse.
5. Remote-only (uzakta olup yerelde olmayan) herhangi bir migration görülürse.
6. Yedek durumu doğrulanmamışsa veya restore prosedürü eksikse.
7. Bakım sorumlusu atanmamışsa.
8. Canlı web veya mobil istemcinin önceden production veritabanına bağlandığı tespit edilirse.
9. Herhangi bir migration dosyasının uygulanması sırasında hata (exit code non-zero) alınırsa.
10. RLS veya yetki matrisi post-check denetiminde tek bir tutarsızlık çıkarsa.
11. Beklenmedik bir Storage bucket'ı veya Edge function'ın deploy edildiği görülürse.
12. Kullanıcının o anki açık, yazılı mutation onayı bulunmuyorsa.

---

## 10. Salt-Okunur Post-Check Denetim Listesi (Doğrulandı: %100 BAŞARILI)

Uygulama sonrasında çalıştırılan ve hiçbir veri mutasyonu yapmayan kesin katalog kontrolleri Codex tarafından doğrulanmıştır:

### 10.1 Migration History ve Tablolar
* [x] `npx supabase migration list` çıktısında remote sayısı: **32 / 32** (Son timestamp: `20260927000100`)
* [x] Public şemadaki tablo sayısı: **Tam 19 tablo**
  * `profiles`, `categories`, `transactions` (3 başlangıç tablosu)
  * `sync_operations_receipts`
  * `budgets`
  * `recurring_transactions`
  * `subscriptions`
  * `goals`, `goal_contributions`, `debts`, `debt_payments` (4 hedef/borç tablosu)
  * `workspaces`, `workspace_members`, `workspace_invitations` (3 workspace tablosu)
  * `assets`
  * `market_prices`, `market_price_rate_limits`
  * `subscription_price_histories`, `subscription_payments`
* [x] 19 tablonun tamamında: `relrowsecurity = true` (RLS aktif)
* [x] 19 primary key mevcut, 0 geçersiz index, 0 doğrulanmamış constraint

### 10.2 Kolon ve Kısıt Doğrulamaları
* [x] `transactions.paid_by_user_id` (uuid) ve `transactions.participant_user_ids` (jsonb) kolonları mevcut
* [x] `workspace_invitations.token_hash` kolonu authenticated ve anon için SELECT yetkisine kapalı (yalnız SECURITY DEFINER RPC okuyabilir)
* [x] Anon/PUBLIC için tablo mutasyon yetkisi (INSERT/UPDATE/DELETE/TRUNCATE) sayısı: **0**

### 10.3 Tetikleyiciler (Triggers)
* [x] `transactions_money_compat_before_write` trigger'ı aktif (`before insert or update on public.transactions`)
* [x] `transactions_enforce_workspace_mutation_role_v1` trigger'ı aktif (`before insert or update on public.transactions`)
* [x] `on_auth_user_created_feniqo_v1` trigger'ı aktif (`after insert on auth.users`)
* [x] `profiles_protect_identity_v1`, `categories_protect_identity_v1`, `transactions_protect_identity_v1` trigger'ları aktif
* [x] Senkronizasyon sunucu metadata tetikleyicileri (`*_set_server_metadata`) tam 16 adetlik kesin allowlist ile birebir eşleşir:
  - Denetim kuralı: Küme tam eşleşmiştir (Eksik trigger = 0, beklenmeyen ekstra trigger = 0).
  - Durum kuralı: 16 tetikleyicinin tamamının `pg_trigger.tgenabled` değeri `'O'` (origin/enabled).
  - Kesin allowlist (16 tetikleyici):
    1. `profiles_set_server_metadata` (`public.profiles`)
    2. `categories_set_server_metadata` (`public.categories`)
    3. `transactions_set_server_metadata` (`public.transactions`)
    4. `budgets_set_server_metadata` (`public.budgets`)
    5. `recurring_transactions_set_server_metadata` (`public.recurring_transactions`)
    6. `subscriptions_set_server_metadata` (`public.subscriptions`)
    7. `goals_set_server_metadata` (`public.goals`)
    8. `goal_contributions_set_server_metadata` (`public.goal_contributions`)
    9. `debts_set_server_metadata` (`public.debts`)
    10. `debt_payments_set_server_metadata` (`public.debt_payments`)
    11. `workspaces_set_server_metadata` (`public.workspaces`)
    12. `workspace_members_set_server_metadata` (`public.workspace_members`)
    13. `workspace_invitations_set_server_metadata` (`public.workspace_invitations`)
    14. `assets_set_server_metadata` (`public.assets`)
    15. `sub_price_histories_set_server_metadata` (`public.subscription_price_histories`)
    16. `sub_payments_set_server_metadata` (`public.subscription_payments`)

### 10.4 Kritik Fonksiyon, RPC, Güvenlik ve `search_path` (`proconfig`) Matrisi
* [x] `public.sync_write_v2(text, text, text, bigint, jsonb)`:
  * Güvenlik: `SECURITY DEFINER`
  * Beklenen search_path: `search_path = ''` (Katalog proconfig: `{search_path=}`)
  * Dönüş tipi: `jsonb`
  * ACL: `grant execute to authenticated` (public ve anon için revoke edilmiş)
* [x] `public.redeem_workspace_invitation_v1(text)`:
  * Güvenlik: `SECURITY DEFINER`
  * Beklenen search_path: `search_path = public, extensions, pg_temp` (Katalog proconfig: `{search_path="public, extensions, pg_temp"}`)
  * Dönüş tipi: `jsonb`
  * ACL: `grant execute to authenticated` (public ve anon için revoke edilmiş)
* [x] `public.transfer_workspace_ownership_v1(uuid, uuid, bigint, bigint, bigint)`:
  * Güvenlik: `SECURITY DEFINER`
  * Beklenen search_path: `search_path = public, extensions, pg_temp` (Katalog proconfig: `{search_path="public, extensions, pg_temp"}`)
  * Dönüş tipi: `jsonb`
  * ACL: `grant execute to authenticated` (public ve anon için revoke edilmiş)
* [x] `public.claim_market_price_request()`:
  * Güvenlik: `SECURITY DEFINER`
  * Beklenen search_path: `search_path = ''` (Katalog proconfig: `{search_path=}`)
  * Dönüş tipi: `boolean`
  * ACL: `grant execute to authenticated` (public, anon ve service_role için revoke edilmiş)
* [x] `public.sync_transactions_money_compat()`:
  * Güvenlik: `SECURITY INVOKER`
  * Beklenen search_path: `search_path = ''` (Katalog proconfig: `{search_path=}`)
  * Dönüş tipi: `trigger`
  * ACL: `revoke execute from public, anon, authenticated` (doğrudan RPC olarak çağrılamaz)
* [x] `public.sync_set_server_metadata()`:
  * Güvenlik: `SECURITY INVOKER`
  * Beklenen search_path: `search_path = ''` (Katalog proconfig: `{search_path=}`)
  * Dönüş tipi: `trigger`
* [x] `public.handle_new_user_v1()`:
  * Güvenlik: `SECURITY DEFINER`
  * Beklenen search_path: `search_path = ''` (Katalog proconfig: `{search_path=}`)
  * Dönüş tipi: `trigger`

### 10.5 Sistem Verisi, Realtime ve Altyapı
* [x] `categories` tablosunda: 27 aktif kanonik kategori + 2 tombstone (`is_default = true`) mevcut
* [x] `supabase_realtime` publication üyeleri: Yalnızca `categories`, `profiles`, `transactions` (3 tablo)
* [x] Storage'da `receipts` bucket'ı **yok** (0 bucket)
* [x] Dağıtılmış Edge Function **yok** (0 function)
* [x] Para null/negatif/compatibility drift sayacı: **0**
* [x] Negatif mutation veya test kullanıcısı production üzerinde **çalıştırılmadı** (0 artık)
* [x] Son production dry-run: `upToDate = true` (0 bekleyen)
* [x] CLI bağlantısı operasyon bitiminde tekrar staging ref'ine (`rxfaiynkhaxrksosxvxp`) döndürüldü

---

## 11. Canlıya Çıkış (Rollout) Sırası ve Durumu

1. **Yedek Doğrulaması:** `[TAMAMLANDI — 2026-09-27]` (Application-scope schema-only logical backup + yerel PostgreSQL 18 restore testi %100 geçti).
2. **Production Migration:** `[TAMAMLANDI — 2026-09-27]` (32 migration sırasıyla hatasız uygulandı).
3. **Salt-Okunur Post-Check:** `[TAMAMLANDI — 2026-09-27]` (Katalog metadata denetimi %100 geçti).
4. **Backend Kabulü:** `[TAMAMLANDI — 2026-09-27]` (Veritabanı durumu: `PRODUCTION UYGULANDI VE DOĞRULANDI`).
5. **Ortam Değişkenleri:** `[BEKLENİYOR — Henüz yapılmadı]` (Production publishable anon key ve URL, güvenli mobil build yapılandırmasına eklenecek).
6. **Mobil Release Build:** `[BEKLENİYOR — Henüz yapılmadı]` (İmzalı release paketi AAB/IPA üretilecek).
7. **Kontrollü Dağıtım:** `[BEKLENİYOR — Henüz yapılmadı]` (Mobil mağaza test kanallarına veya kademeli dağıtıma sunulacak).
8. **Web Uyumluluk Smoke Testi:** `[BEKLENİYOR — Henüz yapılmadı]` (Canlı web istemcisi mevcutsa temel uyumluluk doğrulanacak).
9. **Sistem İzleme:** `[BEKLENİYOR — Henüz yapılmadı]` (Hata logları ve sync receipt performansı izlenecek).

---

## 12. Onay Kaydı

* **Operasyon Zamanı:** `2026-09-27`
* **Hedef Proje:** `FeniqoMobil-Production` (`qgmymavltjnmfuzvfxiq`)
* **Uygulanan Migration:** 32 adet sıralı migration (`20260814000000` -> `20260927000100`)
* **Sonuç:** `[x] ONAYLANDI, UYGULANDI VE DOĞRULANDI`
* **Backend Durumu:** `PRODUCTION UYGULANDI VE DOĞRULANDI`
* **Rollout Durumu:** Mobil ve Web production rollout henüz yapılmadı.
