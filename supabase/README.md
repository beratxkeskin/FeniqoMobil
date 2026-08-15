# FeniqoMobil Supabase migration taslakları

> **Güvenlik uyarısı:** Bu klasördeki SQL dosyaları canlı Supabase projesine uygulanmamıştır.

Bu migration'lar yalnız yerel Supabase CLI ortamında, ardından ayrı bir staging projesinde
doğrulanmak üzere hazırlanır. Production uygulaması için
`docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md` içindeki onay kapısının tamamlanması ve
proje sahibinin ayrıca açık onay vermesi gerekir.

Uygulama sırası:

1. `20260814000000_schema_web_v1_baseline.sql` — yalnız yeni/staging projeler
2. `20260814000100_schema_sync_metadata.sql`
3. `20260814000200_schema_money_expand.sql`
4. `20260814000300_data_money_backfill.sql`
5. `20260814000400_functions_conditional_sync.sql`
6. `20260814000500_rls_v1_personal.sql`
7. `20260815000100_realtime_v1_publication.sql`

Mevcut production veritabanında baseline dosyası çalıştırılmaz. Canlı şema denetlendikten
sonra bu sürüm migration history içinde ayrıca baseline edilmelidir.

İlk üç dosya mevcut web istemcisinin kullandığı `amount` ve `receipt_url` kolonlarını
kaldırmaz. Uyumluluk tetikleyicisi, web'in eski `amount` yazımı ile mobilin yeni
`amount_minor` yazımını geçiş döneminde birlikte destekler.

Bu dosyalar doğrulanmadan mobilde gerçek push/pull açılmaz. Özellikle para backfill
kontrolü, RLS testleri, aynı UUID ile tekrar, eski `base_version`, tombstone ve
`(updated_at, id)` cursor senaryoları staging ortamında geçmelidir.

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
geçmişinin güncel olduğu dry-run ile doğrulandı.

Production'a migration uygulanmamıştır. Production geçişi; mevcut şema/migration
history denetimi, yedek ve ayrı açık onay olmadan yapılmamalıdır.
