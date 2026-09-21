"""
FeniqoMobil - Yerel PostgreSQL Sync V2 Sözleşme Test Koşucusu

Bu betik, yalnız yerel geliştirme ortamında (localhost / 127.0.0.1) çalışmak üzere tasarlanmıştır.
Canlı veya uzak (staging/production) sunuculara bağlantı güvenlik kuralı olarak engellenir.
Parola ortam değişkeninden (PGPASSWORD) okunur; hiçbir loga veya çıktıya yazılmaz.
Her çalıştırmada benzersiz ve izole bir test veritabanı oluşturulur ve `finally` bloğunda tamamen temizlenir.
"""

import os
import sys
import uuid

# Güvenlik Kontrolü 1: Yalnız yerel hedeflere izin ver
HOST = os.environ.get('PGHOST', 'localhost')
ALLOWED_HOSTS = {'localhost', '127.0.0.1', '::1'}
if HOST.lower() not in ALLOWED_HOSTS:
    print(
        f"[SECURITY ABORT] Yalnızca yerel test hedeflerine ({ALLOWED_HOSTS}) izin verilir. "
        f"Uzak/canlı hedef ('{HOST}') kesinlikle yasaktır.",
        file=sys.stderr
    )
    sys.exit(1)

PORT = int(os.environ.get('PGPORT', '5432'))
USER = os.environ.get('PGUSER', 'postgres')
PASSWORD = os.environ.get('PGPASSWORD')

try:
    import psycopg2
    from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
except ImportError:
    print(
        "[ERROR] 'psycopg2' modülü bulunamadı. Lütfen 'pip install psycopg2-binary' ile yükleyin.",
        file=sys.stderr
    )
    sys.exit(1)

# Dosya yollarını betik konumuna göre dinamik tespit et
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
MIGRATIONS_DIR = os.path.join(REPO_ROOT, "supabase", "migrations")
CONTRACT_TEST_PATH = os.path.join(CURRENT_DIR, "sync_write_v2_contract.sql")

if not os.path.isdir(MIGRATIONS_DIR):
    print(f"[ERROR] Migration dizini bulunamadı: {MIGRATIONS_DIR}", file=sys.stderr)
    sys.exit(1)

if not os.path.isfile(CONTRACT_TEST_PATH):
    print(f"[ERROR] Sözleşme test dosyası bulunamadı: {CONTRACT_TEST_PATH}", file=sys.stderr)
    sys.exit(1)

# Çalıştırmaya özel tekil ve izole test veritabanı adı
UNIQUE_SUFFIX = uuid.uuid4().hex[:12]
TEST_DB = f"feniqo_test_{UNIQUE_SUFFIX}"

admin_conn = None
test_conn = None
test_cur = None

created_cluster_roles = []
preexisting_cluster_roles = []

exit_code = 0

try:
    print(f"[INFO] Yerel PostgreSQL sunucusuna ({HOST}:{PORT}) bağlanılıyor...")
    admin_conn = psycopg2.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        dbname='postgres'
    )
    admin_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    admin_cur = admin_conn.cursor()

    # Küme düzeyindeki rolleri kontrol et (önceden mevcut nesnelere veya diğer veritabanlarına dokunulmaz)
    for role_name in ['anon', 'authenticated']:
        admin_cur.execute("SELECT 1 FROM pg_roles WHERE rolname = %s;", (role_name,))
        if admin_cur.fetchone():
            preexisting_cluster_roles.append(role_name)
        else:
            admin_cur.execute(f"CREATE ROLE {role_name};")
            created_cluster_roles.append(role_name)

    print(f"[ROLES REPORT] Önceden mevcut küme rolleri: {preexisting_cluster_roles}")
    if created_cluster_roles:
        print(f"[ROLES REPORT] Yeni oluşturulan küme rolleri: {created_cluster_roles} (Not: Küme rolleri PostgreSQL genelinde saklanır)")

    # ICU tr-TR ve UTF-8 ile izole test veritabanı oluştur
    admin_cur.execute(
        f"CREATE DATABASE {TEST_DB} TEMPLATE = template0 LOCALE_PROVIDER = 'icu' ICU_LOCALE = 'tr-TR' ENCODING = 'UTF8';"
    )
    admin_cur.close()
    admin_conn.close()
    admin_conn = None
    print(f"[INFO] İzole test veritabanı oluşturuldu: {TEST_DB} (UTF-8, ICU tr-TR)")

    # İzole test veritabanına bağlan
    test_conn = psycopg2.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        dbname=TEST_DB
    )
    test_conn.set_client_encoding('UTF8')
    test_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    test_cur = test_conn.cursor()

    # Supabase çekirdek şema ve fonksiyon ön koşulları
    test_cur.execute("""
    create schema if not exists extensions;
    create extension if not exists "uuid-ossp" schema extensions;
    create extension if not exists "pgcrypto" schema extensions;
    create schema if not exists auth;
    create table if not exists auth.users (
        id uuid primary key default extensions.gen_random_uuid(),
        email text,
        raw_user_meta_data jsonb default '{}'::jsonb,
        created_at timestamptz default now()
    );
    create or replace function auth.uid() returns uuid language sql stable as $$
        select coalesce(
            nullif(current_setting('request.jwt.claim.sub', true), '')::uuid,
            (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')::uuid
        );
    $$;
    create or replace function auth.role() returns text language sql stable as $$
        select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), 'authenticated');
    $$;

    create publication supabase_realtime;

    insert into auth.users (id, email, created_at) values
        (extensions.gen_random_uuid(), 'test1@feniqo.local', now() - interval '2 days'),
        (extensions.gen_random_uuid(), 'test2@feniqo.local', now() - interval '1 day');
    """)
    print("[INFO] Supabase çekirdek ön koşulları kuruldu.")

    # Sıralı migration zincirini uygula
    mig_files = sorted([f for f in os.listdir(MIGRATIONS_DIR) if f.endswith('.sql')])
    print(f"[INFO] Uygulanacak migration sayısı: {len(mig_files)}")

    for idx, mf in enumerate(mig_files, 1):
        path = os.path.join(MIGRATIONS_DIR, mf)
        with open(path, "r", encoding="utf-8") as f:
            sql = f.read()
        test_cur.execute(sql)
        print(f"  [{idx}/{len(mig_files)}] OK: {mf}")

    print("\n[SUCCESS] Tüm migration zinciri başarıyla uygulandı!")

    # Sözleşme testini çalıştır
    print(f"\n[INFO] Sözleşme testleri yürütülüyor: {CONTRACT_TEST_PATH} ...")
    with open(CONTRACT_TEST_PATH, "r", encoding="utf-8") as f:
        contract_sql = f.read()

    test_cur.execute(contract_sql)
    print("\n" + "=" * 70)
    print(">>> SÖZLEŞME TESTLERİ TAMAMLANDI VE TÜM ASSERTION'LAR GEÇTİ! <<<")
    print("=" * 70 + "\n")

    for notice in test_conn.notices:
        print(f"[NOTICE] {notice.strip()}")

except Exception as err:
    print(f"\n[ERROR] Test çalıştırma hatası: {err}", file=sys.stderr)
    exit_code = 1

finally:
    if test_cur:
        try:
            test_cur.close()
        except Exception:
            pass
    if test_conn:
        try:
            test_conn.close()
        except Exception:
            pass

    # Yalnız bu çalıştırmada üretilen tekil test veritabanını temizle
    print(f"[CLEANUP] Yalnızca oluşturulan test veritabanı temizleniyor: {TEST_DB}...")
    try:
        cleanup_conn = psycopg2.connect(
            host=HOST,
            port=PORT,
            user=USER,
            password=PASSWORD,
            dbname='postgres'
        )
        cleanup_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        cleanup_cur = cleanup_conn.cursor()
        cleanup_cur.execute(f"DROP DATABASE IF EXISTS {TEST_DB};")
        cleanup_cur.close()
        cleanup_conn.close()
        print(f"[CLEANUP] {TEST_DB} veritabanı başarıyla silindi.")
    except Exception as cleanup_err:
        print(f"[CLEANUP ERROR] Test veritabanı silinemedi ({TEST_DB}): {cleanup_err}", file=sys.stderr)

sys.exit(exit_code)
