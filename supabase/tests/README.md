# FeniqoMobil - Supabase Yerel Testleri

Bu dizindeki testler, Supabase PostgreSQL migration ve RPC sözleşmelerini yerel PostgreSQL üzerinde izole ve tekrarlanabilir biçimde doğrular.

## Güvenlik ve İzolasyon Kuralları
- Bu test koşucuları yalnızca yerel hedefte (`localhost`, `127.0.0.1`, `::1`) çalışır. Uzak veya staging/production veritabanlarına bağlanmayı engeller.
- Gizli anahtarlar, parolalar veya staging/production bağlantı bilgileri bu dosyalara yazılmaz.
- Ortam değişkenleri üzerinden yerel bağlantı parametreleri sağlanır (`PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`).

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

## Testlerin Çalıştırılması

Yerel PostgreSQL sunucusu çalışırken:

```powershell
python supabase/tests/run_extended_categories_seed_contract_test.py
```
