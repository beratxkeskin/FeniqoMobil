# FeniqoMobil Supabase migration dosyaları

> **Güvenlik ve Durum Bildirimi:** Bu klasördeki 32 migration'ın tamamı `FeniqoMobil-Production` (`qgmymavltjnmfuzvfxiq`) üzerine sırasıyla uygulanmıştır (son timestamp: `20260927000100`). Salt-okunur katalog post-check denetimleri %100 geçmiş olup backend tarafında durum **PRODUCTION UYGULANDI VE DOĞRULANDI** olarak tescillenmiştir; mobil ve web canlı rollout henüz yapılmamıştır. Bundan sonraki tüm veritabanı değişiklikleri uygulanmış dosyaları kesinlikle değiştirmeden yeni timestamp'li **forward-only** migration olmalıdır.
>
> **Baseline Güvenlik Uyarısı:** Baseline (`20260814000000_schema_web_v1_baseline.sql`) yalnız bu production ortamının public uygulama şemasının ve migration geçmişinin tamamen boş olduğu kesin metadata denetimiyle kanıtlandıktan sonra sıralı zincir kapsamında uygulanmıştır. Başka mevcut veya dolu production şemalarına kesinlikle körlemesine uygulanamaz. Operasyon adımları ve runbook için `docs/PRODUCTION_SUPABASE_DEPLOYMENT_RUNBOOK.md` belgesine bakınız.

Uygulama sırası (Kesin 32 sıralı migration zinciri):

1. `20260814000000_schema_web_v1_baseline.sql`
2. `20260814000100_schema_sync_metadata.sql`
3. `20260814000200_schema_money_expand.sql`
4. `20260814000300_data_money_backfill.sql`
5. `20260814000400_functions_conditional_sync.sql`
6. `20260814000500_rls_v1_personal.sql`
7. `20260815000100_realtime_v1_publication.sql`
8. `20260816000100_seed_default_categories_v1.sql`
9. `20260826000100_sync_write_v2_receipts.sql`
10. `20260826000200_sync_write_v2_rpc.sql`
11. `20260829000100_sync_write_v2_budgets.sql`
12. `20260830000100_sync_write_v2_recurring_transactions.sql`
13. `20260831000100_sync_write_v2_subscriptions.sql`
14. `20260901000100_sync_write_v2_goals_and_debts.sql`
15. `20260901000200_reconcile_goals_debts_sync_contract.sql`
16. `20260906000100_sync_write_v2_workspaces.sql`
17. `20260907000100_sync_write_v2_workspace_members_and_invitations.sql`
18. `20260908000100_transfer_workspace_ownership_v1.sql`
19. `20260908000200_sync_write_v2_workspace_member_removal.sql`
20. `20260908000300_sync_write_v2_transaction_split.sql`
21. `20260908000400_sync_write_v2_assets.sql`
22. `20260909000100_market_prices_read_model.sql`
23. `20260909000200_market_price_rate_limit.sql`
24. `20260912000100_add_transaction_note.sql`
25. `20260913000100_subscription_lifecycle_and_history.sql`
26. `20260913000200_subscription_website_and_notes.sql`
27. `20260918000100_sync_write_v2_custom_split.sql`
28. `20260921000100_seed_extended_canonical_categories.sql`
29. `20260926000100_harden_workspace_invitation_token_hash_visibility.sql`
30. `20260926000200_harden_workspace_transaction_viewer_mutations.sql`
31. `20260926000300_harden_workspace_table_privileges.sql`
32. `20260927000100_harden_money_compatibility_trigger.sql`

## Baseline ve Güvenlik İlkeleri

- **Yeni/Boş Ortam Kuralı:** Baseline migration'ı (`20260814000000_schema_web_v1_baseline.sql`) yalnız yeni ve boş veritabanı ortamları içindir.
- **Production Uygulaması:** `FeniqoMobil-Production` (`qgmymavltjnmfuzvfxiq`) ortamında public uygulama şeması (0 tablo, 0 fonksiyon, 0 trigger) ve migration geçmişi kesin olarak boş doğrulandığı için baseline, 32 dosyalık zincirin ilk migration'ı olarak başarıyla uygulanmıştır.
- **Dolu/Mevcut Şema Yasağı:** Dolu veya halihazırda tabloları bulunan mevcut bir şemaya körlemesine baseline uygulanamaz.
- **Farklı/Mevcut Canlı Ortamlar:** İleride mevcut ve dolu başka bir production ortamı ile karşılaşılırsa sahte baseline veya `migration repair` gibi geçmişi manipüle eden yöntemler kesinlikle kullanılmaz; önce salt-okunur reconciliation denetimi yapılır ve gerekirse yeni **forward-only** migration hazırlanır.
- **Web-Mobil Uyumluluk:** İlk migration'lar mevcut web istemcisinin kullandığı `amount` ve `receipt_url` kolonlarını kaldırmaz. `transactions_money_compat_before_write` tetikleyicisi web'in `amount` (Double) yazımı ile mobilin `amount_minor` (Long kuruş) yazımını iki yönlü destekler.
- **Test ve Doğrulama Şartı:** Bu dosyalar doğrulanmadan mobilde gerçek push/pull açılmaz. Para backfill, RLS testleri, aynı UUID ile tekrar, eski `base_version`, tombstone ve `(updated_at, id)` cursor senaryoları doğrulanmıştır.

## Yerel ve staging doğrulaması — 14 Ağustos 2026

Dört migration geçici ve uzak bağlantısız PostgreSQL-WASM ortamında sırayla başarıyla
çalıştırıldı. Para backfill ve kontrol toplamı; koşullu CREATE/UPDATE; eski
`base_version` conflict'i; aynı UUID ile tekrar; soft-delete tombstone; NOT_FOUND;
sahiplik reddi ve uygunsuz para verisinde migration'ın durması doğrulandı.

Altı migration ayrıca yalnız `FeniqoMobil-Staging` projesine uygulandı. Gerçek Supabase
Auth JWT'leriyle profil trigger/RLS; kullanıcılar arası profil, kategori ve işlem
izolasyonu; hard-delete reddi; profil/kategori/işlem koşullu RPC; eski `base_version`;
aynı UUID; `amount_minor` uyumluluğu; `(updated_at, id)` sorgusu ve soft-delete
tombstone senaryoları geçti. Geçici test kullanıcıları temizlendi ve staging migration
geçmişinin güncel olduğu dry-run ile doğrulandı. 32 migration'ın tamamı staging ortamında
uygulanmış; RLS, davet, sync_write_v2, para uyumluluğu ve gerçek Auth/PostgREST uçtan uca kabul
testleri başarıyla geçmiştir.

Production ortamında (`FeniqoMobil-Production` / `qgmymavltjnmfuzvfxiq`) onaylanan 32 migration'ın
tamamı sırasıyla uygulanmış ve salt-okunur katalog post-check kontrolleri %100 başarıyla geçmiştir.
Backend tarafında durum **`PRODUCTION UYGULANDI VE DOĞRULANDI`** olarak tescillenmiştir; mobil ve web canlı
rollout henüz yapılmamıştır. Operasyon adımları, doğrulanan yedek ve post-check denetimleri için
`docs/PRODUCTION_SUPABASE_DEPLOYMENT_RUNBOOK.md` belgesine bakınız.
