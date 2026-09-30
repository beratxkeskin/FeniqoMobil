"""
FeniqoMobil - Yerel PostgreSQL Sync V2 ve Güvenlik Sözleşme Test Koşucusu

Bu betik, yalnız yerel geliştirme ortamında (localhost / 127.0.0.1 / ::1) çalışmak üzere tasarlanmıştır.
Canlı veya uzak (staging/production) sunuculara bağlantı güvenlik kuralı olarak engellenir.
Parola veya connection string loga veya konsol çıktısına yazılmaz.
Her çalıştırmada benzersiz ve izole bir test veritabanı oluşturulur ve finally bloğunda tamamen temizlenir.
"""

import argparse
import os
import sys
import uuid

# Güvenlik Kontrolü 1: İzin verilen yerel hedefler
ALLOWED_HOSTS = {'localhost', '127.0.0.1', '::1'}

# Güvenlik Kontrolü 2: İzin verilen hedef sözleşmeler (Allowlist)
# Path traversal veya rastgele dosya yolu engellenir; yalnızca bu liste kabul edilir.
ALLOWED_CONTRACTS = {
    "sync_write_v2_contract.sql": "sync_write_v2_contract.sql",
    "sync_write_v2_assets_contract.sql": "sync_write_v2_assets_contract.sql",
    "market_prices_contract.sql": "market_prices_contract.sql",
    "market_price_rate_limit_contract.sql": "market_price_rate_limit_contract.sql",
    "workspace_invitation_security_contract.sql": "workspace_invitation_security_contract.sql",
    "rls_security_contract.sql": "rls_security_contract.sql",
}
DEFAULT_CONTRACT = "sync_write_v2_contract.sql"

REQUIRED_CLUSTER_ROLES = ['anon', 'authenticated', 'service_role']


def validate_host(host: str) -> None:
    """Yalnızca yerel hedeflere izin verildiğini doğrular; aksi halde çıkış yapar."""
    if host.lower() not in ALLOWED_HOSTS:
        print(
            f"[SECURITY ABORT] Yalnızca yerel test hedeflerine ({ALLOWED_HOSTS}) izin verilir. "
            f"Uzak/canlı hedef ('{host}') kesinlikle yasaktır.",
            file=sys.stderr
        )
        sys.exit(1)


def parse_args(args=None):
    """CLI argümanlarını parse eder. Yalnızca --contract bayrağını destekler, positional argüman kabul etmez."""
    parser = argparse.ArgumentParser(
        description="FeniqoMobil İzole Yerel PostgreSQL SQL Sözleşme Test Koşucusu"
    )
    parser.add_argument(
        "--contract",
        dest="contract",
        choices=sorted(ALLOWED_CONTRACTS.keys()),
        default=DEFAULT_CONTRACT,
        help=(
            f"Çalıştırılacak SQL sözleşme testi. İzin verilenler: {sorted(ALLOWED_CONTRACTS.keys())} "
            f"(Varsayılan: {DEFAULT_CONTRACT})"
        )
    )
    parsed = parser.parse_args(args)
    return parsed.contract


def resolve_contract_path(contract_filename: str) -> str:
    """Allowlist'ten çözülen güvenli yerel dosya yolunu döner."""
    if contract_filename not in ALLOWED_CONTRACTS:
        raise ValueError(f"Geçersiz sözleşme hedefi: '{contract_filename}'. İzin verilenler: {list(ALLOWED_CONTRACTS.keys())}")
    current_dir = os.path.dirname(os.path.abspath(__file__))
    safe_file = ALLOWED_CONTRACTS[contract_filename]
    contract_path = os.path.join(current_dir, safe_file)
    if not os.path.isfile(contract_path):
        raise FileNotFoundError(f"Sözleşme dosyası mevcut değil: {contract_path}")
    return contract_path


def should_cleanup_roles(db_created: bool, db_dropped: bool) -> bool:
    """
    Rol cleanup karar mantığı:
    1. Test veritabanı hiç oluşturulmadıysa (örneğin CREATE DATABASE aşamasında hata alındıysa),
       oluşturulmuş roller temizlenmeyi denemelidir (True).
    2. Test veritabanı oluşturuldu ve başarıyla silindiyse, roller temizlenmelidir (True).
    3. Test veritabanı oluşturuldu fakat silinememişse, veritabanı bağımlılıkları devam edeceğinden
       roller silinmeye çalışılmamalı ve işlem başarısız sayılmalıdır (False).
    """
    if not db_created:
        return True
    return db_dropped


def evaluate_role_drop(cursor, role_name: str) -> tuple[bool, str]:
    """
    Verilen küme rolünün bağımlılıklarını kontrol eder ve güvenliyse siler.
    Başarılı silinirse: (True, "Rol güvenle silindi")
    Bağımlılık varsa veya hata oluşursa: (False, hata mesajı)
    """
    try:
        cursor.execute("""
            SELECT count(*) FROM pg_shdepend
            WHERE refobjid = (SELECT oid FROM pg_roles WHERE rolname = %s);
        """, (role_name,))
        row = cursor.fetchone()
        dep_count = row[0] if row else 0
        if dep_count > 0:
            return False, f"Aktif bağımlılık mevcut ({dep_count} adet nesne bu role bağlı)"

        cursor.execute(f'DROP ROLE IF EXISTS "{role_name}";')
        return True, "Rol güvenle silindi"
    except Exception as err:
        return False, f"Rol silinirken istisna oluştu: {err}"


def main():
    selected_contract = parse_args()
    contract_path = resolve_contract_path(selected_contract)

    host = os.environ.get('PGHOST', 'localhost')
    validate_host(host)

    port = int(os.environ.get('PGPORT', '5432'))
    user = os.environ.get('PGUSER', 'postgres')
    password = os.environ.get('PGPASSWORD')

    try:
        import psycopg2
        from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
    except ImportError:
        print(
            "[ERROR] 'psycopg2' modülü bulunamadı. Lütfen 'pip install psycopg2-binary' ile yükleyin.",
            file=sys.stderr
        )
        sys.exit(1)

    current_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(current_dir, "..", ".."))
    migrations_dir = os.path.join(repo_root, "supabase", "migrations")

    if not os.path.isdir(migrations_dir):
        print(f"[ERROR] Migration dizini bulunamadı: {migrations_dir}", file=sys.stderr)
        sys.exit(1)

    # Çalıştırmaya özel tekil, izole ve dışarıdan müdahale edilemeyen test veritabanı adı
    unique_suffix = uuid.uuid4().hex[:12]
    test_db = f"feniqo_test_{unique_suffix}"

    admin_conn = None
    admin_cur = None
    test_conn = None
    test_cur = None

    created_cluster_roles = []
    preexisting_cluster_roles = []

    exit_code = 0
    db_created = False
    db_dropped = False

    try:
        print(f"[INFO] Yerel PostgreSQL sunucusuna ({host}:{port}) bağlanılıyor...")
        admin_conn = psycopg2.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            dbname='postgres'
        )
        admin_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        admin_cur = admin_conn.cursor()

        # Küme düzeyindeki rolleri kontrol et: anon, authenticated, service_role
        for role_name in REQUIRED_CLUSTER_ROLES:
            admin_cur.execute("SELECT 1 FROM pg_roles WHERE rolname = %s;", (role_name,))
            if admin_cur.fetchone():
                preexisting_cluster_roles.append(role_name)
            else:
                admin_cur.execute(f"CREATE ROLE {role_name};")
                created_cluster_roles.append(role_name)

        print(f"[ROLES REPORT] Önceden mevcut küme rolleri: {preexisting_cluster_roles}")
        if created_cluster_roles:
            print(f"[ROLES REPORT] Bu çalıştırma için oluşturulan küme rolleri: {created_cluster_roles}")

        # ICU tr-TR ve UTF-8 ile izole test veritabanı oluştur
        admin_cur.execute(
            f"CREATE DATABASE {test_db} TEMPLATE = template0 LOCALE_PROVIDER = 'icu' ICU_LOCALE = 'tr-TR' ENCODING = 'UTF8';"
        )
        db_created = True
        print(f"[INFO] İzole test veritabanı oluşturuldu: {test_db} (UTF-8, ICU tr-TR)")

    except Exception as setup_err:
        print(f"\n[ERROR] Kurulum veya test veritabanı oluşturma hatası: {setup_err}", file=sys.stderr)
        exit_code = 1
    finally:
        if admin_cur:
            try:
                admin_cur.close()
            except Exception:
                pass
        if admin_conn:
            try:
                admin_conn.close()
            except Exception:
                pass
            admin_conn = None

    if db_created and exit_code == 0:
        try:
            # İzole test veritabanına bağlan
            test_conn = psycopg2.connect(
                host=host,
                port=port,
                user=user,
                password=password,
                dbname=test_db
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
                ('00000000-0000-0000-0000-000000000001', 'test1@feniqo.local', now() - interval '2 days'),
                ('00000000-0000-0000-0000-000000000002', 'test2@feniqo.local', now() - interval '1 day')
            on conflict (id) do nothing;

            -- Supabase staging default privilege davranışını emüle et (yalnız disposable test DB):
            -- public şemasında postgres tarafından oluşturulan yeni tablolar
            -- anon, authenticated ve service_role rollerine default grant alır.
            alter default privileges for role postgres in schema public grant all on tables to postgres, anon, authenticated, service_role;
            alter default privileges for role postgres in schema public grant all on sequences to postgres, anon, authenticated, service_role;
            alter default privileges for role postgres in schema public grant all on functions to postgres, anon, authenticated, service_role;
            """)
            print("[INFO] Supabase çekirdek ön koşulları ve default privileges kuruldu.")

            # Sıralı migration zincirini uygula
            mig_files = sorted([f for f in os.listdir(migrations_dir) if f.endswith('.sql')])
            print(f"[INFO] Uygulanacak migration sayısı: {len(mig_files)}")

            for idx, mf in enumerate(mig_files, 1):
                path = os.path.join(migrations_dir, mf)
                with open(path, "r", encoding="utf-8") as f:
                    sql = f.read()
                test_cur.execute(sql)
                print(f"  [{idx}/{len(mig_files)}] OK: {mf}")

            print("\n[SUCCESS] Tüm migration zinciri başarıyla uygulandı!")

            # Seçilen sözleşme testini çalıştır
            print(f"\n[INFO] Hedef sözleşme testi yürütülüyor: {selected_contract} ({contract_path}) ...")
            with open(contract_path, "r", encoding="utf-8") as f:
                contract_sql = f.read()

            test_cur.execute(contract_sql)
            print("\n" + "=" * 70)
            print(f">>> SÖZLEŞME TESTİ ({selected_contract}) TAMAMLANDI VE TÜM ASSERTION'LAR GEÇTİ! <<<")
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

    # -------------------------------------------------------------------------
    # CLEANUP AŞAMASI
    # -------------------------------------------------------------------------
    # Cleanup Adım 1: Yalnız bu çalıştırmada üretilen tekil test veritabanını temizle
    if db_created:
        print(f"[CLEANUP] Yalnızca oluşturulan test veritabanı temizleniyor: {test_db}...")
        cleanup_conn = None
        cleanup_cur = None
        try:
            cleanup_conn = psycopg2.connect(
                host=host,
                port=port,
                user=user,
                password=password,
                dbname='postgres'
            )
            cleanup_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
            cleanup_cur = cleanup_conn.cursor()
            cleanup_cur.execute(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = %s AND pid <> pg_backend_pid();",
                (test_db,)
            )
            cleanup_cur.execute(f'DROP DATABASE IF EXISTS "{test_db}";')
            db_dropped = True
            print(f"[CLEANUP] {test_db} veritabanı başarıyla silindi.")
        except Exception as cleanup_err:
            print(f"[CLEANUP ERROR] Test veritabanı silinemedi ({test_db}): {cleanup_err}", file=sys.stderr)
            exit_code = 1
        finally:
            if cleanup_cur:
                try:
                    cleanup_cur.close()
                except Exception:
                    pass
            if cleanup_conn:
                try:
                    cleanup_conn.close()
                except Exception:
                    pass

    # Cleanup Adım 2: Yalnız runner tarafından yeni oluşturulan cluster rollerini güvenle temizle
    if created_cluster_roles:
        if should_cleanup_roles(db_created, db_dropped):
            print(f"[CLEANUP] Yalnızca runner tarafından oluşturulan küme rolleri temizleniyor: {created_cluster_roles}...")
            role_cleanup_conn = None
            role_cleanup_cur = None
            any_role_cleanup_failed = False
            try:
                role_cleanup_conn = psycopg2.connect(
                    host=host,
                    port=port,
                    user=user,
                    password=password,
                    dbname='postgres'
                )
                role_cleanup_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
                role_cleanup_cur = role_cleanup_conn.cursor()

                for role_to_drop in created_cluster_roles:
                    success, message = evaluate_role_drop(role_cleanup_cur, role_to_drop)
                    if success:
                        print(f"[CLEANUP] {message}: {role_to_drop}")
                    else:
                        print(f"[CLEANUP ERROR] Rol temizlenemedi ({role_to_drop}): {message}", file=sys.stderr)
                        any_role_cleanup_failed = True

            except Exception as role_conn_err:
                print(f"[CLEANUP ERROR] Küme rolleri temizliği bağlantı hatası: {role_conn_err}", file=sys.stderr)
                any_role_cleanup_failed = True
            finally:
                if role_cleanup_cur:
                    try:
                        role_cleanup_cur.close()
                    except Exception:
                        pass
                if role_cleanup_conn:
                    try:
                        role_cleanup_conn.close()
                    except Exception:
                        pass

            if any_role_cleanup_failed:
                print("[CLEANUP FAILURE] Oluşturulan cluster rollerinden en az biri temizlenemedi!", file=sys.stderr)
                exit_code = 1
        else:
            print(
                f"[CLEANUP SKIPPED] Test veritabanı ({test_db}) başarıyla silinemediği için "
                f"oluşturulan küme rolleri ({created_cluster_roles}) temizlenmedi.",
                file=sys.stderr
            )
            exit_code = 1

    sys.exit(exit_code)


if __name__ == '__main__':
    main()
