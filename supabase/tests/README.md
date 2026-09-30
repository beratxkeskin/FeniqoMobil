# FeniqoMobil - Supabase Yerel Testleri

Bu dizindeki testler, Supabase PostgreSQL migration ve RPC sözleşmelerini yerel PostgreSQL üzerinde izole ve tekrarlanabilir biçimde doğrular.

## Güvenlik ve İzolasyon Kuralları
- Bu test koşucuları yalnızca yerel hedefte (`localhost`, `127.0.0.1`, `::1`) çalışır. Uzak veya staging/production veritabanlarına bağlanmayı engeller.
- Gizli anahtarlar, parolalar veya staging/production bağlantı bilgileri bu dosyalara yazılmaz veya ekrana basılmaz.
- Ortam değişkenleri üzerinden yerel bağlantı parametreleri sağlanır (`PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`).
- Her çalıştırmada tekil bir disposable veritabanı oluşturulur ve test bitiminde `finally` bloğuyla silinir.
- Eksik küme rolleri (`anon`, `authenticated`, `service_role`) kontrollü oluşturulur; var olan rollere dokunulmaz, yeni oluşturulanlar ise yalnız test veritabanı başarıyla silindikten sonra bağımlılık kontrolü (`pg_shdepend`) yapılarak güvenle temizlenir. Herhangi bir rol silinemezse süreç başarısız kabul edilir.

## Bağımlılık Kurulumu

İzole bir Python sanal ortamında (virtual environment) bağımlılıkları yükleyin:

```bash
python -m venv .venv
# Windows PowerShell:
.venv\Scripts\Activate.ps1
# veya Bash:
source .venv/bin/activate

pip install -r supabase/tests/requirements.txt
```

## Sözleşme Testlerinin Hedefli Çalıştırılması

Yerel PostgreSQL sunucusu çalışırken, `run_local_sync_contract_test.py` koşucusuna `--contract` parametresiyle allowlist'te yer alan bir sözleşme belirtilebilir (positional argüman kabul edilmez):

### İzin Verilen Sözleşmeler (Allowlist):
1. `sync_write_v2_contract.sql` (Varsayılan hedef, ana 72 senaryolu Sync V2 RPC testi)
2. `sync_write_v2_assets_contract.sql` (Asset yönetimi, version çakışmaları ve RLS testi)
3. `market_prices_contract.sql` (Piyasa fiyatları tablo, constraint, anon engeli ve service_role testleri)
4. `market_price_rate_limit_contract.sql` (Piyasa fiyatı sliding window kota ve anon engeli testleri)
5. `workspace_invitation_security_contract.sql` (Davet token_hash görünürlüğü, column-level izinler ve redeem testi)
6. `rls_security_contract.sql` (Çekirdek RLS negatif matrisi, rol ve workspace izolasyonu, grant metadata denetimi)

### Örnek Komutlar:

```powershell
# 1. Varsayılan (sync_write_v2_contract.sql) çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py

# 2. Asset sözleşmesini hedefli çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py --contract sync_write_v2_assets_contract.sql

# 3. Market prices sözleşmesini hedefli çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py --contract market_prices_contract.sql

# 4. Market price rate limit sözleşmesini hedefli çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py --contract market_price_rate_limit_contract.sql

# 5. Workspace invitation security sözleşmesini hedefli çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py --contract workspace_invitation_security_contract.sql

# 6. Çekirdek RLS negatif güvenlik sözleşmesini hedefli çalıştırma:
python -B supabase/tests/run_local_sync_contract_test.py --contract rls_security_contract.sql
```

## Sistem Varsayılan Kategori Tohum Testi

`run_extended_categories_seed_contract_test.py` koşucusu, migration checkpoint gereksinimleri sebebiyle genel koşucudan ayrıdır.
Bu koşucu:
- 9 üst düzey senaryo ve alt varyantlarla toplam **16 ayrı izole execution/clone veritabanı** üzerinde çalışır.
- Doğrudan `20260921000100_seed_extended_canonical_categories.sql` migration dosyasını ve atomik rollback güvencelerini test eder.

```powershell
python -B supabase/tests/run_extended_categories_seed_contract_test.py
```

## Para Backfill ve Eski Web - Yeni Mobil Uyumluluk Testi

`run_money_backfill_web_compat_test.py` koşucusu, para dönüşümünün (amount -> amount_minor) ve çift yönlü web-mobil uyumluluk tetikleyicisinin (`sync_transactions_money_compat`) doğrulanması için özel bir test suite'idir:
- Pozitif backfill (TRY, USD, EUR tam sayı, tek ondalık, iki ondalık ve kontrol toplamları).
- Negatif fail-closed senaryoları (sıfır/negatif, ikiden fazla ondalık, BIGINT taşması, desteklenmeyen currency, orphan satır, NOT NULL denetimi).
- `20260927000100_harden_money_compatibility_trigger.sql` ile güçlendirilmiş çift yönlü senkronizasyon (Eski Web UPDATE, Yeni Mobil UPDATE, tutarsız yazım engeli).
- Veri drift'i simülasyonunda migration pre-check'inin fail-closed duruşu ve fonksiyon değişmezliği.
- Mobil `Currency` enum (GBP) ile DB kısıtları uyumsuzluk analizi.

```powershell
python -B supabase/tests/run_money_backfill_web_compat_test.py
```
