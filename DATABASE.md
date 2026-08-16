# FeniqoMobil Veri ve Senkronizasyon Sözleşmesi

> Bu belge Room ve Supabase veri modelinin standart giriş noktasıdır. Gerçek SQL davranışında
> sıralı [supabase/migrations](supabase/migrations) dosyaları; ayrıntılı güvenlik tasarımında
> [docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md](docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md)
> daha ayrıntılı teknik kaynaktır.

## 1. Veri sahipliği

- Room uygulamanın tek okuma kaynağıdır.
- Supabase cihazlar arası kalıcılık, kimlik doğrulama ve paylaşım sınırıdır.
- Repository, Room ile Supabase arasındaki tek koordinasyon noktasıdır.
- UI doğrudan Supabase sorgusu veya DTO kullanamaz.
- V1 uzak kapsamı kişisel `profiles`, `categories` ve `transactions` tablolarıdır.
- Collaborative workspace veri erişimi V1 sonrası ayrıca RLS rol matrisiyle açılacaktır.

## 2. Room şeması

Güncel Room şema sürümü **3**'tür. Export edilen şemalar
`sharedLogic/schemas/com.feniqo.mobile.data.local.database.FeniqoDatabase/` altında commit edilir.

### İş verisi tabloları

| Tablo | Amaç | Temel ilişkiler |
|---|---|---|
| `profiles` | Kullanıcı profili ve tercihleri | `id` kullanıcı kimliği |
| `workspaces` | Kişisel/ortak alan modeli | `owner_id` |
| `workspace_members` | Üye ve rol ilişkisi | `(workspace_id, user_id)` |
| `categories` | Gelir/gider kategorileri | owner/workspace, type |
| `transactions` | Finansal hareketler | category, owner/workspace |
| `budgets` | Aylık kategori limiti | category, month, scope |
| `tags` | Kullanıcı etiketleri | owner/workspace |
| `transaction_tags` | İşlem-etiket çoktan çoğa ilişkisi | transaction + tag |

### Senkronizasyon tabloları

| Tablo | Amaç |
|---|---|
| `sync_operations` | Kalıcı, sıralı outbox operasyonları |
| `sync_cursors` | Her entity türü için son `(updated_at, id)` pull cursor'u |
| `sync_conflicts` | Kullanıcı çözümüne kadar yerel ve uzak snapshot'ların korunması |

## 3. Ortak yerel metadata

Senkronize Room entity'leri gömülü `SyncMetadata` taşır:

| Alan | Anlamı |
|---|---|
| `sync_status` | Yerel senkronizasyon durumu |
| `updated_at_epoch_ms` | Bilinen uzak UTC güncelleme zamanı |
| `local_updated_at_epoch_ms` | Yerel değişiklik zamanı |
| `deleted_at_epoch_ms` | Tombstone zamanı; aktif kayıtta null |
| `version` | Bilinen sunucu sürümü |
| `base_version` | Yerel mutation'ın dayandığı uzak sürüm |
| `last_sync_error` | Güvenli, kullanıcı verisi içermeyen son hata kodu |

Domain modelleri bu teknik metadata'nın tamamını bilmek zorunda değildir; dönüşüm mapper'da yapılır.

## 4. Para sözleşmesi

- Tutarlar en küçük para biriminde signed `Long`/PostgreSQL `bigint` ile saklanır.
- İşlem tutarı pozitif büyüklüktür; gelir/gider yönü `type`/`type_code` ile belirlenir.
- Para birimi açık ISO kodudur (`TRY`, `USD`, `EUR` vb.).
- `Double`, binary floating-point veya biçimlendirilmiş metin kalıcı finansal değer olamaz.
- Supabase V1'de hedef alanlar `amount_minor` ve `currency`'dir.
- Eski web `amount numeric` alanı yalnız kontrollü geçiş uyumluluğu içindir.

## 5. Tarih ve zaman sözleşmesi

- `transaction_date`: saat diliminden bağımsız `YYYY-MM-DD` iş günü.
- `created_at`, `updated_at`, `deleted_at`: UTC sunucu zamanları.
- Room, UTC metadata zamanlarını epoch-millis `Long` olarak saklar.
- Artımlı pull sırası yalnız timestamp değildir; `(updated_at, id)` bileşik cursor kullanılır.

## 6. Soft-delete ve sürümleme

- Kullanıcıya ait senkronize finansal kayıt hard-delete edilmez.
- Silme `deleted_at` tombstone'u üretir ve normal sync akışıyla diğer cihazlara taşınır.
- Her uzak kayıt pozitif `version` taşır.
- Update/delete isteği `baseVersion` gönderir.
- Sunucu yalnız mevcut `version == baseVersion` ise mutation uygular.
- Sürüm uyuşmazlığı sessizce overwrite edilmez; conflict olarak yerelde iki snapshot ile saklanır.

## 7. Outbox sözleşmesi

Her yerel mutation, entity ile aynı Room transaction'ında `sync_operations` satırı üretir.

Outbox alanları:

- kararlı `operation_id`;
- `entity_type_code` ve `entity_id`;
- `CREATE`, `UPDATE` veya `DELETE` operasyonu;
- `base_version`;
- durum, deneme sayısı ve güvenli son hata;
- sonraki deneme ve oluşturulma/güncellenme zamanları.

Kurallar:

1. Operasyonlar oluşturulma sırasıyla işlenir.
2. Aynı operasyonun tekrarı idempotent olmalıdır.
3. Başarılı operasyon kuyruktan kaldırılır.
4. Geçici hata retry/backoff üretir.
5. Conflict otomatik overwrite veya sonsuz retry üretmez.
6. Uygulama kapanması `IN_FLIGHT` operasyonunu kalıcı olarak kilitlememelidir.

## 8. Supabase V1 şeması

### `profiles`

- Bir kullanıcının yalnız kendi `auth.uid()` profiline erişimi vardır.
- Kimlik alanı kullanıcı tarafından başka kullanıcıya çevrilemez.
- `updated_at` ve `version` sync metadata'sıdır.

### `categories`

- V1 kişisel satırlarında `user_id = auth.uid()` ve `workspace_id is null` beklenir.
- Sistem/default kategori değişiklikleri kullanıcı mutation'ından ayrıdır.
- `updated_at`, `deleted_at`, `version` alanları bulunur.

### `transactions`

- V1 kişisel satırlarında `user_id = auth.uid()` ve `workspace_id is null` beklenir.
- Para `amount_minor bigint` + `currency` ile taşınır.
- Silme tombstone'dur.
- Makbuz için public URL değil sahiplik kontrollü nesne yolu saklanır.

## 9. RLS ve uzak yazma

- RLS `profiles`, `categories` ve `transactions` üzerinde zorunludur.
- Kullanıcı başka kullanıcının satırını okuyamaz veya değiştiremez.
- Mobil uygulamaya yalnız publishable/legacy anon key konabilir; secret/service-role key konamaz.
- Koşullu uzak yazmalar `sync_write_v1` RPC sözleşmesi üzerinden sahiplik ve `baseVersion`
  denetimiyle yapılır.
- RLS hiçbir hata çözümü için geçici olarak kapatılmaz.
- Piyasa verisi gibi ortak/sunucu yetkili yazımlar gelecekte Edge Function veya sunucu görevidir.

## 10. Realtime

- Publication yalnız `profiles`, `categories` ve `transactions` ile sınırlıdır.
- Realtime mesajı veri olarak uygulanmaz; Room incremental pull tetikleyicisidir.
- İlk `SUBSCRIBED` ve yeniden abonelik sonrası telafi pull'u çalışır.
- Bağlantı kesilmesi UI'daki mevcut Room verisini silmez.

## 11. Makbuz depolama

- Bucket private olmalıdır.
- Önerilen yol: `userId/transactionId/objectId.ext`.
- Veritabanında public URL yerine nesne yolu tutulur.
- Okuma için kısa ömürlü signed URL gerektiği anda üretilir.
- Dosya türü, gerçek boyut, sahiplik ve yol segmentleri doğrulanır.
- Secret/service-role anahtarı istemciye verilmez.

## 12. Migration politikası

1. Var olan migration dosyası uygulandıktan sonra değiştirilmez; yeni sıralı migration eklenir.
2. Her migration tek sorumluluk taşır.
3. Önce yerel/izole test, sonra ayrı staging projesi, en son açık onayla production kullanılır.
4. Production baseline migration'ı körlemesine çalıştırılmaz.
5. Backfill öncesinde yedek ve kontrol toplamı hazırlanır.
6. Genişletme ve kırıcı contract adımları ayrı deployment'larda yapılır.
7. Dashboard SQL Editor ile yapılan değişiklik migration dosyasına aktarılmadan bırakılmaz.

Mevcut sıralı dosyalar:

1. `20260814000000_schema_web_v1_baseline.sql`
2. `20260814000100_schema_sync_metadata.sql`
3. `20260814000200_schema_money_expand.sql`
4. `20260814000300_data_money_backfill.sql`
5. `20260814000400_functions_conditional_sync.sql`
6. `20260814000500_rls_v1_personal.sql`
7. `20260815000100_realtime_v1_publication.sql`

Bu seri `FeniqoMobil-Staging` üzerinde kabul testinden geçmiştir. Production'a uygulanmamıştır.

## 13. Ortam güvenlik kapısı

Production veritabanında işlem yapmadan önce aşağıdakilerin tümü gerekir:

- mevcut production şeması ve migration geçmişi çıkarılmış olmalı;
- web geriye uyumluluğu doğrulanmalı;
- güncel ve geri yüklenebilir yedek alınmalı;
- RLS, para backfill, tombstone, cursor ve conflict senaryoları staging'de geçmeli;
- uygulanacak dosyalar ve hedef proje açıkça belirtilmeli;
- proje sahibi production uygulaması için ayrıca açık onay vermeli.

