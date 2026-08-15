# FeniqoMobil — Supabase V1 Güvenlik ve Migration Planı

> **Durum:** Tasarım tamamlandı; SQL uygulaması henüz yapılmadı  
> **Kapsam:** V1 kişisel profil, kategori, işlem ve offline senkronizasyon  
> **Referans:** `docs/WEB_REFERANS_ENVANTERI.md`

Bu belge canlı veritabanında çalıştırılacak SQL değildir. Amaç, migration dosyaları yazılmadan önce tablo değişikliklerinin, RLS yetkilerinin, veri geçişinin ve test kapılarının sırasını kesinleştirmektir.

---

## 1. Hedefler

1. Mobil istemcinin yalnız yetkili olduğu satırlara erişmesini sağlamak.
2. Offline yazma, soft-delete ve çakışma çözümü için gerekli metadata’yı eklemek.
3. Para değerlerini platformlar arasında hassasiyet kaybetmeden taşımak.
4. Web istemcisi çalışmaya devam ederken kontrollü veri migration’ı yapmak.
5. Migration’ları tekrarlanabilir, sıralı ve test edilebilir hâle getirmek.
6. Service-role ve üçüncü taraf gizli anahtarların mobil istemciye girmesini engellemek.

## 2. Kapsam Dışı

- V2 ortak çalışma alanının canlıya alınması
- Canlı fiyat, döviz veya açık bankacılık backend’i
- Makbuz OCR uygulaması
- V2/V3 finans tablolarının tamamının migration’ı
- Üretim veritabanında bu aşamada SQL çalıştırılması

Workspace yetki matrisi gelecekteki şemanın güvenli tasarlanması için bu belgede bulunur; V1 mobil UI’da workspace özelliği açılmaz.

---

## 3. Güvenlik İlkeleri

- RLS tüm kullanıcı verisi tablolarında zorunludur.
- Mobil uygulamada yalnız publishable/anon key bulunabilir.
- Service-role key yalnız güvenilir backend veya Edge Function ortamında tutulur.
- UI veya istemci tarafından gönderilen `user_id` değerine güvenilmez; RLS her zaman `auth.uid()` ile doğrular.
- `USING`, mevcut satıra erişimi; `WITH CHECK`, ekleme/güncelleme sonrasındaki yeni satırı doğrular.
- UPDATE politikalarında hem `USING` hem `WITH CHECK` tanımlanır.
- Finans kayıtlarında istemciye hard-delete izni verilmez.
- RLS politikası aynı RLS tablosunu doğrudan alt sorguda okuyarak recursion oluşturmamalıdır.
- `SECURITY DEFINER` fonksiyonlarında `search_path` sabitlenir ve yalnız gereken yetki verilir.
- Tüm politika değişiklikleri negatif testlerle doğrulanır; yalnız başarılı kullanım testi yeterli değildir.

---

## 4. V1 Kişisel Veri Yetki Matrisi

### 4.1 `profiles`

| İşlem | Kullanıcı | İzin | Kural |
|---|---|---|---|
| SELECT | Satır sahibi | Evet | `id = auth.uid()` |
| SELECT | Başka kullanıcı | Hayır | V1’de profil paylaşımı yok |
| INSERT | Mobil istemci | Hayır | Profil auth trigger tarafından oluşturulur |
| UPDATE | Satır sahibi | Evet | Eski ve yeni `id = auth.uid()` |
| UPDATE | Başka kullanıcı | Hayır | RLS engeller |
| DELETE | Mobil istemci | Hayır | Hesap silme ayrı güvenli sunucu akışıdır |

Profil e-postasının asıl kaynağı Supabase Auth’tur. Profil tablosundaki e-posta gösterim/cache amacı taşır; yetkilendirme kararı için kullanılmaz.

### 4.2 `categories`

| İşlem | Sistem kategorisi | Kullanıcının özel kategorisi | Başka kullanıcının kategorisi |
|---|---|---|---|
| SELECT | Evet | Evet | Hayır |
| INSERT | Hayır | `user_id = auth.uid()` ise evet | Hayır |
| UPDATE | Hayır | Sahibi ve `is_default = false` ise evet | Hayır |
| Soft-delete | Hayır | Sahibi ve `is_default = false` ise evet | Hayır |
| Hard-delete | Hayır | Hayır | Hayır |

V1 özel kategori satırında `workspace_id IS NULL` zorunludur. Sistem kategorileri yalnız migration/service-role tarafından yönetilir.

### 4.3 `transactions`

| İşlem | Satır sahibi | Başka kullanıcı |
|---|---|---|
| SELECT | Evet | Hayır |
| INSERT | `user_id = auth.uid()` ve `workspace_id IS NULL` ise evet | Hayır |
| UPDATE | Sahibi ise evet | Hayır |
| Soft-delete | UPDATE üzerinden evet | Hayır |
| Hard-delete | Hayır | Hayır |

UPDATE sonrasında `user_id`, `workspace_id`, `id` ve `created_at` alanlarının değişmediği veritabanı kuralı/trigger veya sınırlı RPC ile garanti edilir.

---

## 5. V2 Workspace Rol ve Yetki Matrisi

Onaylanan tek rol sözlüğü:

- `OWNER`: Alan ve üyelik yönetimi dâhil tam yetki
- `EDITOR`: Finansal kayıt oluşturma/düzenleme; üye ve alan ayarı yönetemez
- `VIEWER`: Yalnız görüntüleme

| Yetki | OWNER | EDITOR | VIEWER |
|---|---:|---:|---:|
| Workspace görüntüle | Evet | Evet | Evet |
| Üye listesini görüntüle | Evet | Evet | Evet |
| Finansal kayıt görüntüle | Evet | Evet | Evet |
| Finansal kayıt oluştur | Evet | Evet | Hayır |
| Finansal kayıt düzenle | Evet | Evet | Hayır |
| Finansal kayıt soft-delete | Evet | Evet | Hayır |
| Davet oluştur/iptal et | Evet | Hayır | Hayır |
| Üye rolü değiştir | Evet | Hayır | Hayır |
| Üye çıkar | Evet | Hayır | Hayır |
| Workspace adını/ayarını değiştir | Evet | Hayır | Hayır |
| Workspace sil | Evet | Hayır | Hayır |
| Sahipliği devret | Evet | Hayır | Hayır |

Ek kurallar:

1. Son `OWNER` çalışma alanından ayrılamaz veya çıkarılamaz.
2. Kullanıcı kendi rolünü yükseltemez.
3. `EDITOR`, başka kullanıcı adına `created_by` yazamaz.
4. Finans kayıtlarında `created_by` değişmez; `updated_by` son düzenleyeni gösterir.
5. Workspace üyeliği kontrolü istemci tarafında değil veritabanında yapılır.

### 5.1 Güvenli Davet Akışı

1. `OWNER`, backend/RPC üzerinden rastgele yüksek entropili davet üretir.
2. Veritabanında düz davet kodu değil hash, son kullanım tarihi ve kullanım sınırı saklanır.
3. Katılma işlemi tek bir atomik `SECURITY DEFINER` fonksiyonuyla yapılır.
4. Fonksiyon daveti, süreyi, kullanım sayısını ve mevcut üyeliği doğrular.
5. Başarılı katılımda davet kullanım sayısı ve üyelik aynı transaction içinde güncellenir.
6. İstemci doğrudan `workspace_members` INSERT yapamaz.

---

## 6. Hedef V1 Şema Metadata’sı

### 6.1 `profiles`

- `updated_at TIMESTAMPTZ NOT NULL`
- `version BIGINT NOT NULL DEFAULT 1`

### 6.2 `categories`

- `updated_at TIMESTAMPTZ NOT NULL`
- `deleted_at TIMESTAMPTZ NULL`
- `version BIGINT NOT NULL DEFAULT 1`
- Kararlı sistem kategorisi `slug` veya önceden belirlenmiş UUID
- Kullanıcı kategorileri için normalize isim unique kuralı

### 6.3 `transactions`

- `amount_minor BIGINT`
- `currency TEXT`
- `updated_at TIMESTAMPTZ NOT NULL`
- `deleted_at TIMESTAMPTZ NULL`
- `version BIGINT NOT NULL DEFAULT 1`
- `receipt_path TEXT NULL`

V1 geçiş süresinde eski `amount` ve `receipt_url` alanları hemen kaldırılmaz.

---

## 7. Para Migration Stratejisi

Para dönüşümü tek deployment içinde kırıcı biçimde yapılmayacaktır.

### Aşama A — Genişlet

1. `amount_minor BIGINT NULL` alanını ekle.
2. `currency TEXT NULL` alanını ekle.
3. Mevcut kayıtları profil para birimiyle backfill etmeye hazırlan.
4. Dönüşüm öncesi desteklenmeyen para birimi ve fazla ondalık basamak raporu üret.

### Aşama B — Backfill

1. İki ondalıklı para birimleri için kontrollü `ROUND(amount * 100)` dönüşümü yap.
2. Kaynak `amount`, hedef `amount_minor` ve geri dönüşüm değerlerini karşılaştır.
3. Hatalı/şüpheli kayıtları otomatik düzeltme yerine inceleme listesine al.
4. Toplam gelir/gider kontrol toplamlarını migration öncesi ve sonrası karşılaştır.

### Aşama C — Uyumluluk Dönemi

1. Web istemcisini hem eski hem yeni alanları doğru okuyacak/yazacak şekilde güncelle.
2. Mobil istemci yalnız yeni para sözleşmesini kullanır.
3. Telemetri/SQL doğrulamasıyla yeni alanı boş kalan kayıt olup olmadığını izle.

### Aşama D — Sıkılaştır

1. `amount_minor IS NOT NULL` ve `amount_minor > 0` kısıtını etkinleştir.
2. `currency IS NOT NULL` ve izin verilen kod kontrolünü etkinleştir.
3. Eski `amount` alanını ancak tüm istemciler taşındıktan sonra ayrı migration ile kaldır.

Not: JPY gibi sıfır, bazı para birimleri için üç ondalık basamak gerekir. V1 yalnız `TRY`, `USD`, `EUR` ile iki basamak kullanır; `Currency` modeli gelecekte minor-unit basamak bilgisini taşımalıdır.

---

## 8. Version ve Çakışma Protokolü

1. Yerel entity sunucudan aldığı `version` değerini `base_version` olarak saklar.
2. Offline güncelleme outbox’a kayıt kimliği, operasyon ve `base_version` ile yazılır.
3. Senkronizasyon UPDATE işlemini `id`, sahiplik ve mevcut `version = base_version` koşuluyla yapar.
4. Güncelleme atomik olarak `version = version + 1` ve sunucu `updated_at` değeri üretir.
5. Etkilenen satır yoksa istemci son uzak sürümü çeker.
6. İçerik gerçekten değişmişse yerel kayıt `CONFLICT` olur ve iki snapshot korunur.
7. Kullanıcı seçimi yeni bir mutation olarak gönderilir; eski sürüm sessizce ezilmez.

Yeni kayıt aynı UUID ile tekrar gönderildiğinde ikinci satır oluşturmamalıdır. Idempotency yalnız ağ hataları için değil Worker tekrarları için de zorunludur.

### 8.1 Pull Cursor

- Artımlı çekim sunucunun `updated_at` zamanına dayanır.
- Aynı timestamp’e sahip kayıtları kaçırmamak için cursor `(updated_at, id)` çifti olur.
- Sync başarıyla Room’a yazılmadan cursor ilerletilmez.
- `deleted_at` dolu tombstone kayıtları da pull sonucuna dâhildir.
- Tombstone saklama süresi tüm desteklenen cihazların çevrimdışı kalma süresinden uzun olmalıdır.

---

## 9. Private Receipt Storage Planı

- Bucket adı: `receipts`
- Public özelliği: `false`
- Önerilen nesne yolu: `{user_id}/{transaction_id}/{random_uuid}.{extension}`
- Veritabanında public URL değil yalnız nesne yolu saklanır.
- Okuma için kısa ömürlü signed URL gerektiği anda üretilir.
- Dosya türü, boyut ve mümkünse gerçek içerik imzası doğrulanır.
- EXIF içindeki gereksiz konum bilgisi istemcide temizlenir.
- Transaction soft-delete olduğunda dosya hemen silinmez; kontrollü temizlik işi kullanılır.
- Workspace sürümünde path sahipliği üyelik fonksiyonu üzerinden doğrulanır.

---

## 10. Migration Dosya Planı

Önerilen ileri yönlü dosyalar:

| Sıra | Önerilen dosya | Sorumluluk |
|---:|---|---|
| 001 | `schema_sync_metadata.sql` | `updated_at`, `deleted_at`, `version` alanları |
| 002 | `schema_money_expand.sql` | `amount_minor`, `currency`, geçici uyumluluk alanları |
| 003 | `data_money_backfill.sql` | Kontrollü para backfill ve doğrulama sorguları |
| 004 | `functions_updated_at_version.sql` | Sunucu timestamp/version fonksiyonları |
| 005 | `rls_v1_personal.sql` | Profil, kategori ve işlem V1 politikaları |
| 006 | `storage_receipts_private.sql` | Private bucket ve storage politikaları |
| 007 | `constraints_v1_validate.sql` | NOT NULL/CHECK/unique kısıtlarını doğrulama |
| 008 | `schema_money_contract.sql` | Yeni para sözleşmesini zorunlu yapma |

Dosya adlarının başına gerçek Supabase CLI timestamp’i eklenecektir. Her dosya tek sorumluluk taşımalı; ilk kurulum şeması ile geçmiş migration’lar aynı dosyada birleştirilmemelidir.

### 10.1 Ortam Sırası

1. Yerel Supabase geliştirme ortamı
2. Otomatik SQL/RLS testleri
3. Staging kopyası ve temsili veri
4. Web geriye uyumluluk testi
5. Android entegrasyon testi
6. Yedek/geri dönüş kontrolü
7. Üretim bakım penceresi

Dashboard SQL Editor üzerinden yapılan manuel değişiklik, daha sonra migration dosyasına yazılmadan bırakılmayacaktır.

---

## 11. Geri Dönüş ve Veri Güvenliği

- Şema genişletme migration’ları eski kolonları hemen silmez.
- Backfill öncesi doğrulanmış veritabanı yedeği alınır.
- Kırıcı contract migration ayrı deployment’ta yapılır.
- Policy hatasında uygulama erişimi geçici kapatılabilir; RLS devre dışı bırakılmaz.
- Başarısız backfill yeni kolonları temizlemek yerine durdurulur ve hata kayıtları incelenir.
- Migration işlemleri tekrar çalıştırmaya karşı mümkün olduğunca güvenli tasarlanır; fakat hatayı gizleyen kontrolsüz `IF NOT EXISTS` kullanımına güvenilmez.

---

## 12. Test Matrisi

### 12.1 RLS Negatif Testleri

- [ ] Kullanıcı A, kullanıcı B profilini okuyamaz/güncelleyemez.
- [x] Kullanıcı A, kullanıcı B özel kategorisini okuyamaz/değiştiremez.
- [ ] Kullanıcı A, kullanıcı B işlemini okuyamaz/değiştiremez.
- [ ] Kullanıcı sistem kategorisi ekleyemez/değiştiremez.
- [x] Kullanıcı hard-delete yapamaz.
- [ ] Mobil/anon istemci service-role işlemi yapamaz.
- [ ] Davet olmadan workspace üyeliği oluşturulamaz.
- [ ] VIEWER finansal mutation yapamaz.
- [ ] EDITOR rol/üyelik değiştiremez.
- [ ] Yetkisiz kullanıcı receipt signed URL alamaz.

### 12.2 Sync Testleri

- [x] Aynı UUID ile iki insert denemesi tek satır üretir.
- [x] Doğru `base_version` güncellemeyi ve version artışını sağlar.
- [x] Eski `base_version` güncellemeyi reddeder ve conflict üretir.
- [ ] Soft-delete başka cihazda tombstone olarak çekilir.
- [ ] Worker tekrarında aynı outbox operasyonu yinelenmez.
- [ ] Cursor aynı timestamp’teki farklı UUID kayıtlarını kaçırmaz.

### 12.3 Para Migration Testleri

- [x] `125.50` değeri `12550` olur.
- [ ] `0` ve negatif tutarlar yeni constraint tarafından reddedilir.
- [x] Migration öncesi/sonrası gelir ve gider toplamları eşleşir.
- [ ] TRY, USD ve EUR currency kodları doğru taşınır.
- [ ] Null veya desteklenmeyen currency kayıtları raporlanır.

---

## 13. Uygulama Öncesi Onay Kapısı

SQL yazmaya/çalıştırmaya başlamadan önce:

- [ ] Mevcut Supabase projesinin migration geçmişi çıkarıldı.
- [ ] Web uygulamasının canlı ortam kullanıp kullanmadığı doğrulandı.
- [ ] Geriye uyumluluk süresi belirlendi.
- [ ] Temsili veriyle staging ortamı hazırlandı.
- [ ] Yedekleme ve geri dönüş sorumlusu belirlendi.
- [ ] RLS test kullanıcıları ve senaryoları hazırlandı.
- [ ] Para backfill kontrol toplamı sorguları onaylandı.
- [ ] Production’da doğrudan manuel SQL yerine migration akışı onaylandı.

Bu kapı tamamlanmadan canlı Supabase projesinde şema veya RLS değişikliği yapılmayacaktır.

---

## 14. Sonraki Teknik Adım

1. Supabase ortamının canlılık ve migration geçmişi bilgilerini doğrula.
2. Bu plandaki dosya sırasına göre yalnız yerel/staging migration taslaklarını oluştur.
3. RLS testlerini SQL test senaryoları olarak yaz.
4. Canlı uygulama yapılmadan önce web uyumluluk planını onayla.
