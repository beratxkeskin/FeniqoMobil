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

Güncel Room şema sürümü **22**'dir. Export edilen şemalar
`sharedLogic/schemas/com.feniqo.mobile.data.local.database.FeniqoDatabase/` altında commit edilir.

### Room v21 -> v22 Kullanıcı-Scope İzolasyonu ve Outbox/Cursor/Conflict Kalıcılık Sözleşmesi

Room v22 (`ANDROID_MIGRATION_21_22`), oturum değişimlerinde outbox, cursor ve conflict verilerinin farklı kullanıcılar arasında izole edilmesi için kalıcı şema temelini kurar:
- **`sync_operations.sync_scope_key`:** Her outbox işlemine `sync_scope_key` sütunu eklenir. İndeksler `(sync_scope_key, status_code, is_blocked, next_attempt_at_epoch_ms, created_at_epoch_ms, operation_id)`, `(sync_scope_key, entity_type_code, entity_id)` ve `(sync_scope_key, predecessor_operation_id)` bileşimleriyle kullanıcı kapsamlı sorguları destekler.
- **`sync_cursors` Composite Primary Key:** Birincil anahtar `(sync_scope_key, entity_type_code)` olarak genişletilmiştir. Bu sayede farklı kullanıcıların (USER:A ve USER:B) aynı entity tipi için bağımsız cursor kayıtları v22 şemasında birlikte bulunabilir.
- **`sync_conflicts` Composite Primary Key & İndeks:** Birincil anahtar `(sync_scope_key, entity_type_code, entity_id)` olarak genişletilmiş ve `(sync_scope_key, detected_at_epoch_ms)` indeksi eklenmiştir. Farklı kullanıcıların aynı entity için conflict snapshot'ları silinmeden bir arada saklanabilir.
- **Kapsam Formatı ve `USER:<canonical_uuid>`:** Normal kullanıcı kapsamı `USER:<canonical-user-uuid>` biçimindedir. Canonical küçük harf UUID doğrulaması fail-closed çalışır.
- **`LEGACY_UNRESOLVED` Karantinası:** Actor otoritesi kesin kanıtlanamayan veya workspace kapsamında olan operasyonlar silinmez; fail-closed biçimde `LEGACY_UNRESOLVED` karantina anahtarıyla korunur. Karantina kayıtları aktif oturumun runtime enqueue, claim, retry, recovery ve outbox execution akışlarından kesin olarak dışlanır; aktif kullanıcıya körlemesine sahiplendirilmez ve otomatik olarak silinmez.
- **Kayıpsız Geriye Dönük Uyumluluk:** Workspace/kişisel ayrımında veya actor otoritesi kanıtlanamayan hiçbir outbox, cursor veya conflict satırı silinmez. Payload JSON değerleri byte-for-byte korunur; payload içeriği actor otoritesi olarak kabul edilmez.
- **Güvenli UI Bilgilendirmesi:** Karantinaya alınan eski veri varlığı kullanıcıya yalnız genel bir bilgilendirme kartı olarak gösterilir (`hasLegacyQuarantinedData`). Exact sayaç, kayıt ID, entity türü veya payload gösterilmez; kart üzerinde uzaktan mutasyon veya sahiplenme aksiyonu bulunmaz.

### Room v20 -> v21 UUID Canonicalization ve Geriye Uyumluluk Karar Sözleşmesi

Room v21 (`ANDROID_MIGRATION_20_21`), eski 32-karakter compact hex UUID'leri güvenli ve kayıpsız biçimde 36-karakter canonical UUID biçimine dönüştürür:
- **Foreign Key Bütünlüğü:** Migration süresince `PRAGMA defer_foreign_keys = ON` kullanılır ve tüm PK/FK dönüşümleri tamamlandıktan sonra `PRAGMA foreign_key_check` çalıştırılır. İhlal tespit edilirse açıklayıcı `IllegalStateException` fırlatılır.
- **Fail-Closed Preflight:** Mutation öncesinde entity PK, FK, composite PK (`transaction_tags`, `recurring_transaction_occurrences`), unique index (`budgets`, `subscription_payments`) ve reminder target stable_key çakışmaları taranır. Olası çakışmada veritabanı değiştirilmeden işlem durdurulur ve atomik rollback sağlanır.
- **V1 Outbox Dokunulmazlığı:** V1 outbox operasyonlarında `payload_json` değerine dokunulmaz, parse/re-encode edilmez. Yalnız yerel entity PK/FK grafiği ve `sync_operations.entity_id` canonicalize edilir.
- **V2 Outbox Karar Tablosu:** Yalnız allowlist (`CATEGORY`, `TRANSACTION`, `BUDGET`, `RECURRING_TRANSACTION`, `SUBSCRIPTION`, `ASSET`, `GOAL`, `GOAL_CONTRIBUTION`, `DEBT`, `DEBT_PAYMENT`) kapsamındaki V2 operasyonlarında:
  - (a) `entity_id` compact + payload top-level `id` aynı compact UUID -> her ikisi canonicalize edilir.
  - (b) `entity_id` compact + payload `id` aynı UUID'nin canonical biçimi -> yalnız `entity_id` güncellenir; payload ham stringi byte-for-byte korunur.
  - (c) Normalized `entity_id` ve payload `id` farklı, payload `id` eksik veya geçersiz -> fail-closed `IllegalStateException`.
  - (d) `entity_id` ve payload `id` zaten canonical/eşleşiyor -> payload ham stringi byte-for-byte korunur.
  - (e) `entity_id` canonical + payload `id` aynı UUID'nin compact biçimi -> yalnız top-level `payload.id` canonicalize edilir.
  - Bütün V2 durumlarında outbox metadata (`operation_id`, `predecessor_operation_id`, `status_code`, `attempt_count`, `base_version`, `error_classification`, `created_at_epoch_ms`, `updated_at_epoch_ms`, `next_attempt_at_epoch_ms`) korunur.
- **Abonelik Hatırlatıcı Makbuzları (`subscription_payment_reminder_receipts`):**
  - `subscription_id` yalnız `UuidHelper.isLegacyCompactHex(subId)` true ise canonicalize edilir.
  - `stable_key` yalnız doğrulanmış prefix sözleşmesiyle (`oldKey.startsWith(subId)`) dönüştürülür (`canonicalSubId + oldKey.substring(subId.length)`); suffix (`_`, `#`, `:`) byte-for-byte korunur.
  - Compact `subscription_id` taşıyan satırın `stable_key` değeri bu ID ile başlamıyorsa fail-closed `IllegalStateException` fırlatılır ve işlem rollback edilir.
  - Canonical, geçersiz veya UUID dışı metinler byte-for-byte korunur.
  - Preflight collision kontrolü de hedef canonical `stable_key` üzerinde çalışır; hedef çakışmasında fail-closed davranılır ve kayıt silinmez.
- **Çakışma Eşdeğerliği (`sync_conflicts`):**
  - Allowlist kapsamındaki entity'lerin compact ID'leri canonicalize edilirken semantik `JsonElement` ağacı ve wrapper yapıları (`GOAL_CONTRIBUTION`, `DEBT_PAYMENT`) korunur.
  - Local/remote snapshot'lar `EquivalentConflictResolver.isEquivalent(...)` ile doğrulanabilirliğini korur.


### İş verisi tabloları

Canonical sistem kategorileri sabit UUID'lerle uygulama başlangıcında Room'a idempotent olarak
uzlaştırılır. Kanonik sözlük 9 gelir ve 18 gider kategorisidir; eşdeğer eski kayıtlar UUID'sini ve
sync metadata'sını korurken ad, renk ve semantik ikon anahtarı güncellenir. Anlamı belirsiz eski
`Tasarruf & Yatırım` ile `Kredi Ödemeleri` kayıtları başka kategoriye otomatik çevrilmez; seçimden
gizlenirken tarihsel yabancı anahtarları korunur. Bu bir veri uzlaştırmasıdır, tablo şeması
değişmediği için yeni Room sürümü gerektirmez.

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
| `subscriptions` | Abonelikler ve yaşam döngüsü (`ACTIVE`, `PAUSED`, `CANCELLED`, `TRIAL`, `EXPIRED`, hatırlatıcı tercihi) | category, owner/workspace |
| `subscription_price_histories` | Abonelik fiyat değişim geçmişi | subscription_id, owner/workspace |
| `subscription_payments` | Gerçekleşen abonelik ödeme olayları | subscription_id, renewal_due_date, owner/workspace |

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

### Piyasa fiyatı read model'i

- `market_prices`, kullanıcı Asset kaydından ayrı, genel ve sağlayıcıdan bağımsız bir uzak cache'tir.
- Kararlı anahtar `(asset_type, symbol, quote_currency)` bileşimidir.
- Birim fiyat `price_unscaled` + `price_scale` ile tutulur; `Double` kullanılmaz ve ölçek `0..12` aralığındadır.
- Yalnız `CRYPTO`, `STOCKS` ve `PRECIOUS_METALS` türleri otomatik fiyat kapsamındadır.
- `authenticated` rolü yalnız okuyabilir; `anon` erişemez ve yazma yalnız güvenilir backend `service_role` akışına açıktır.
- `observed_at`, `fetched_at`, `expires_at` ve `source` alanları fiyatın kaynağını ve tazeliğini görünür tutar.
- Mobil istemci üçüncü taraf servis anahtarı veya sağlayıcı endpoint'i taşımaz.
- Edge Function isteği authenticated kullanıcı başına atomik, dakikalık kota RPC'sinden geçer; istemci kota tablosunu doğrudan okuyamaz veya değiştiremez.

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

- `sync_scope_key`: Oturum sahibi actor'ü (`USER:<authenticatedUserId>`) veya fail-closed karantina kapsamı (`LEGACY_UNRESOLVED`);
- kararlı 32-hex `operation_id`;
- `entity_type_code` ve `entity_id`;
- `CREATE`, `UPDATE` veya `DELETE` operasyonu;
- `base_version`;
- `predecessor_operation_id`, `is_blocked` ve `protocol_version` (V2 ardıl/öncül zincir disiplini);
- durum (`PENDING`, `IN_FLIGHT`, `FAILED`, `CONFLICT`), deneme sayısı ve güvenli son hata;
- `error_classification` (`DEFINITIVE_REJECTION`, `AMBIGUOUS_RESULT`);
- sonraki deneme ve oluşturulma/güncellenme zamanları.

Kurallar:

1. **Actor İzolasyonu:** Outbox işlemleri `sync_scope_key` bazında izole edilir; Kullanıcı A asla Kullanıcı B'nin outbox operasyonlarını okuyamaz, claim edemez, silemez veya retry edemez.
2. **Karantina:** Kesin actor kanıtı bulunmayan veya oturum dışı kalan operasyonlar silinmez, fail-closed `LEGACY_UNRESOLVED` karantinasında korunur.
3. Operasyonlar oluşturulma sırasıyla işlenir.
4. Aynı operasyonun tekrarı idempotent olmalıdır.
5. Başarılı operasyon kuyruktan kaldırılır.
6. Geçici hata retry/backoff üretir.
7. Conflict otomatik overwrite veya sonsuz retry üretmez.
8. Uygulama kapanması `IN_FLIGHT` operasyonunu kalıcı olarak kilitlememelidir.

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
- Ortak harcamalar için `split_mode` (`EQUAL` | `CUSTOM`) ve `participant_shares jsonb` alanları desteklenir (`20260918000100_sync_write_v2_custom_split.sql`).
  - `CUSTOM` modunda `participant_shares` dizisi boş olamaz; her pay elemanı pozitif tam sayı `amount_minor` taşımalı ve paylar toplamı işlem tutarına birebir eşit olmalıdır.
  - Pay sahipleri `participant_user_ids` kümesiyle birebir örtüşmelidir (`CUSTOM_SPLIT_PARTICIPANT_SET_MISMATCH`).
  - Eski istemcilerden gelen split alanı içermeyen UPDATE çağrılarında mevcut CUSTOM dağılımı korunur.

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
...
- `20260913000100_subscription_lifecycle_and_history.sql` (Hazırlandı: subscription lifecycle sütunları, `subscription_price_histories`, `subscription_payments` ve RLS politikaları. Henüz staging veya production'a uygulanmamıştır).
- `20260913000200_subscription_website_and_notes.sql` (Hazırlandı: `subscriptions` tablosuna `website_url` ve `notes` sütunları eklendi. Room v17 ile eşleşir. Henüz staging veya production'a uygulanmamıştır).

Bu seri `FeniqoMobil-Staging` üzerinde kabul testinden geçmiştir. Production'a uygulanmamıştır.
Yerel Room şeması v17'ye yükseltilmiş; `website_url` ve `notes` alanları eklenmiş, migration testleri (`RoomDaoTest`) doğrulanmıştır.

## 13. Ortam güvenlik kapısı

Production veritabanında işlem yapmadan önce aşağıdakilerin tümü gerekir:

- mevcut production şeması ve migration geçmişi çıkarılmış olmalı;
- web geriye uyumluluğu doğrulanmalı;
- güncel ve geri yüklenebilir yedek alınmalı;
- RLS, para backfill, tombstone, cursor ve conflict senaryoları staging'de geçmeli;
- uygulanacak dosyalar ve hedef proje açıkça belirtilmeli;
- proje sahibi production uygulaması için ayrıca açık onay vermeli.
