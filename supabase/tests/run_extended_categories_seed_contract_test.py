"""
FeniqoMobil - Genişletilmiş Sistem Kategorileri Gerçek Migration SQL Kabul Test Koşucusu

Bu betik:
1. Yalnızca yerel PostgreSQL ortamında (localhost / 127.0.0.1) çalışır.
2. 27 önceki migration'ı uygulayarak temiz bir TEMPLATE baseline veritabanı oluşturur.
3. 9 adet zorunlu senaryonun her biri için izole bir veritabanı kopyası açar.
4. Fixture manipülasyonunu uygular ve doğrudan `20260921000100_seed_extended_canonical_categories.sql`
   dosyasının TAM METNİNİ çalıştırır.
5. Beklenen başarı / hata / rollback durumlarını veritabanı sorgularıyla kanıtlar.
6. Test sonrası tüm geçici veritabanlarını temizler.
"""

import hashlib
import os
import sys
import uuid

HOST = os.environ.get('PGHOST', 'localhost')
ALLOWED_HOSTS = {'localhost', '127.0.0.1', '::1'}
if HOST.lower() not in ALLOWED_HOSTS:
    print(f"[SECURITY ABORT] Yalnızca yerel test hedeflerine ({ALLOWED_HOSTS}) izin verilir. Hedef: '{HOST}'", file=sys.stderr)
    sys.exit(1)

PORT = int(os.environ.get('PGPORT', '5432'))
USER = os.environ.get('PGUSER', 'postgres')
PASSWORD = os.environ.get('PGPASSWORD')

try:
    import psycopg2
    from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
except ImportError:
    print("[ERROR] 'psycopg2' modülü bulunamadı.", file=sys.stderr)
    sys.exit(1)

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
MIGRATIONS_DIR = os.path.join(REPO_ROOT, "supabase", "migrations")
TARGET_MIGRATION_NAME = "20260921000100_seed_extended_canonical_categories.sql"
TARGET_MIGRATION_PATH = os.path.join(MIGRATIONS_DIR, TARGET_MIGRATION_NAME)

if not os.path.isfile(TARGET_MIGRATION_PATH):
    print(f"[ERROR] Hedef migration dosyası bulunamadı: {TARGET_MIGRATION_PATH}", file=sys.stderr)
    sys.exit(1)

with open(TARGET_MIGRATION_PATH, "rb") as f:
    migration_bytes = f.read()
    migration_sha256 = hashlib.sha256(migration_bytes).hexdigest()
    migration_raw_sql = migration_bytes.decode('utf-8')

print("=" * 70)
print(f"[INFO] Hedef Migration: {TARGET_MIGRATION_NAME}")
print(f"[INFO] SHA256 Checksum: {migration_sha256}")
print(f"[INFO] Dosya Boyutu:    {len(migration_bytes)} bayt")
print("=" * 70)

RUN_ID = uuid.uuid4().hex[:10]
BASELINE_DB = f"feniqo_base_{RUN_ID}"
created_databases = []

def get_admin_conn():
    conn = psycopg2.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        dbname='postgres'
    )
    conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    return conn

def terminate_and_drop_db(db_name):
    try:
        conn = get_admin_conn()
        cur = conn.cursor()
        cur.execute(f"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '{db_name}';")
        cur.execute(f"DROP DATABASE IF EXISTS {db_name};")
        cur.close()
        conn.close()
    except Exception as e:
        print(f"[WARN] Veritabanı temizleme uyarısı ({db_name}): {e}", file=sys.stderr)

exit_code = 0

try:
    print(f"[INFO] Yerel PostgreSQL sunucusuna ({HOST}:{PORT}) bağlanılıyor...")
    admin_conn = get_admin_conn()
    admin_cur = admin_conn.cursor()

    for role_name in ['anon', 'authenticated']:
        admin_cur.execute("SELECT 1 FROM pg_roles WHERE rolname = %s;", (role_name,))
        if not admin_cur.fetchone():
            admin_cur.execute(f"CREATE ROLE {role_name};")

    admin_cur.execute(
        f"CREATE DATABASE {BASELINE_DB} TEMPLATE = template0 LOCALE_PROVIDER = 'icu' ICU_LOCALE = 'tr-TR' ENCODING = 'UTF8';"
    )
    created_databases.append(BASELINE_DB)
    admin_cur.close()
    admin_conn.close()
    print(f"[INFO] Temiz baseline veritabanı oluşturuldu: {BASELINE_DB}")

    # Baseline'a bağlan ve Supabase ön koşullarını kur
    base_conn = psycopg2.connect(
        host=HOST, port=PORT, user=USER, password=PASSWORD, dbname=BASELINE_DB
    )
    base_conn.set_client_encoding('UTF8')
    base_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    base_cur = base_conn.cursor()

    base_cur.execute("""
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
        ('00000000-0000-0000-0000-000000000001', 'acceptance_v1_test@feniqo.local', now());
    """)

    all_migrations = sorted([f for f in os.listdir(MIGRATIONS_DIR) if f.endswith('.sql')])
    if TARGET_MIGRATION_NAME not in all_migrations:
        raise RuntimeError(f"[FAIL-FAST] Hedef migration ({TARGET_MIGRATION_NAME}) migrations dizininde bulunamadı!")

    target_idx = all_migrations.index(TARGET_MIGRATION_NAME)
    baseline_migrations = all_migrations[:target_idx]
    print(f"[INFO] Baseline için hedef migration öncesindeki {len(baseline_migrations)} migration sırayla uygulanıyor...")

    for mf in baseline_migrations:
        mf_path = os.path.join(MIGRATIONS_DIR, mf)
        with open(mf_path, 'r', encoding='utf-8') as sql_file:
            sql_content = sql_file.read()
        base_cur.execute(sql_content)

    base_cur.close()
    base_conn.close()
    print(f"[SUCCESS] Baseline ({len(baseline_migrations)} migration) başarıyla hazırlandı.")

    # -------------------------------------------------------------------------
    # Senaryo Koşucu Fonksiyonu
    # -------------------------------------------------------------------------
    executed_scenario_count = 0

    def run_scenario(
        scenario_num,
        title,
        fixture_sql,
        expect_success,
        expected_error_code=None,
        pre_verify_fn=None,
        post_verify_fn=None,
        fixture_verify_fn=None,
        fixture_target_id=None,
    ):
        global executed_scenario_count
        executed_scenario_count += 1
        safe_scen_id = str(scenario_num).replace('.', '_')
        scen_db = f"feniqo_scen{safe_scen_id}_{RUN_ID}"
        print(f"\n[SENARYO {scenario_num}] {title}")

        # Baseline'dan klonla
        admin_conn = get_admin_conn()
        admin_cur = admin_conn.cursor()
        admin_cur.execute(f"CREATE DATABASE {scen_db} TEMPLATE = {BASELINE_DB};")
        created_databases.append(scen_db)
        admin_cur.close()
        admin_conn.close()

        scen_conn = psycopg2.connect(
            host=HOST, port=PORT, user=USER, password=PASSWORD, dbname=scen_db
        )
        scen_conn.set_client_encoding('UTF8')
        scen_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        scen_cur = scen_conn.cursor()

        if fixture_sql:
            scen_cur.execute(fixture_sql)

        if pre_verify_fn:
            pre_verify_fn(scen_cur)

        # Fail senaryolarında migration öncesi tam snapshot al
        pre_fixture_snapshot = None
        if not expect_success and fixture_target_id:
            scen_cur.execute("SELECT row_to_json(c) FROM public.categories c WHERE id = %s;", (fixture_target_id,))
            row = scen_cur.fetchone()
            if row:
                pre_fixture_snapshot = row[0]
            else:
                raise AssertionError(f"Senaryo {scenario_num}: Snapshot alınacak fixture satırı bulunamadı! Target ID: {fixture_target_id}")

        migration_failed = False
        error_msg = ""
        try:
            # GERÇEK MIGRATION DOSYASININ TAM METNİ ÇALIŞTIRILIYOR
            scen_cur.execute(migration_raw_sql)
        except Exception as e:
            migration_failed = True
            error_msg = str(e)
            try:
                scen_cur.close()
                scen_conn.close()
            except Exception:
                pass
            scen_conn = psycopg2.connect(
                host=HOST, port=PORT, user=USER, password=PASSWORD, dbname=scen_db
            )
            scen_conn.set_client_encoding('UTF8')
            scen_conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
            scen_cur = scen_conn.cursor()

        if expect_success:
            if migration_failed:
                raise AssertionError(f"Senaryo {scenario_num} BAŞARILI olmalıydı ancak hata verdi: {error_msg}")
            print(f"  [OK] Gerçek migration başarıyla çalıştı.")
            if post_verify_fn:
                post_verify_fn(scen_cur)
                print(f"  [OK] Durum doğrulamaları başarıyla geçti.")
        else:
            if not migration_failed:
                raise AssertionError(f"Senaryo {scenario_num} HATA VERMELİYDİ ancak başarıyla geçti!")
            print(f"  [OK] Gerçek migration beklenen biçimde FAIL oldu: {error_msg.strip()[:100]}...")
            if expected_error_code and expected_error_code not in error_msg:
                raise AssertionError(f"Beklenen hata kodu '{expected_error_code}' bulunamadı. Gerçek hata: {error_msg}")

            # Tam fixture bütünlüğü doğrulaması:
            if pre_fixture_snapshot is not None:
                scen_cur.execute("SELECT row_to_json(c) FROM public.categories c WHERE id = %s;", (fixture_target_id,))
                post_row = scen_cur.fetchone()
                assert post_row is not None, f"Senaryo {scenario_num}: Fixture satırı migration fail sonrasında yok olmuş!"
                post_fixture_snapshot = post_row[0]
                assert post_fixture_snapshot == pre_fixture_snapshot, (
                    f"Senaryo {scenario_num}: Fixture bütünlüğü bozuldu!\n"
                    f"Öncesi: {pre_fixture_snapshot}\n"
                    f"Sonrası: {post_fixture_snapshot}"
                )
                print(f"  [OK] Fixture satırının TAM İÇERİĞİ (row_to_json snapshot) migration fail sonrasında değişmeden korundu.")

            if fixture_verify_fn:
                fixture_verify_fn(scen_cur)
                print(f"  [OK] Fixture kaydının migration hatasından sonra hâlâ mevcut olduğu doğrulandı.")

            # Rollback güvencesini doğrula: Migration transaction'ı fail olduğu için
            # migration'ın eklemeyi hedeflediği yeni kategoriler (1129 kişisel bakım, 1130 abonelikler)
            # ve legacy 1120/1121 tombstone işlemi commit olmamış olmalı.
            scen_cur.execute("""
            SELECT count(*) FROM public.categories
            WHERE id IN (
                '11111111-1111-4111-8111-111111111129',
                '11111111-1111-4111-8111-111111111130'
            );
            """)
            residual_count = scen_cur.fetchone()[0]
            if residual_count > 0:
                raise AssertionError(f"Rollback başarısız! Veritabanında {residual_count} artık yeni kanonik kayıt kaldı.")

            # Legacy 1120 ve 1121'in tombstone ve version işlemi de rollback olmuş olmalı (hala aktif ve version=1)
            scen_cur.execute("""
            SELECT count(*) FROM public.categories
            WHERE id IN ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121')
              AND (deleted_at IS NOT NULL OR version != 1);
            """)
            invalid_legacy_rollback_count = scen_cur.fetchone()[0]
            if invalid_legacy_rollback_count > 0:
                raise AssertionError(f"Rollback başarısız! Legacy kayıtlar tombstone edilmiş veya version artmış kalmış.")
            print(f"  [OK] Atomik rollback doğrulandı (0 artık kayıt, legacy tombstone ve version geri alındı).")

        scen_cur.close()
        scen_conn.close()

    # -------------------------------------------------------------------------
    # SENARYO 1: Temiz baseline üzerine çalıştırma -> 27 kanonik aktif kayıt
    # -------------------------------------------------------------------------
    def verify_scenario_1(cur):
        cur.execute("""
        SELECT count(*) FROM public.categories
        WHERE is_default IS TRUE AND user_id IS NULL AND workspace_id IS NULL AND deleted_at IS NULL;
        """)
        assert cur.fetchone()[0] == 27, "Toplam aktif sistem kategorisi sayısı 27 olmalı!"

        cur.execute("""
        SELECT count(*) FROM public.categories
        WHERE is_default IS TRUE AND user_id IS NULL AND workspace_id IS NULL AND deleted_at IS NULL AND type = 'income';
        """)
        assert cur.fetchone()[0] == 9, "Aktif gelir kategorisi sayısı 9 olmalı!"

        cur.execute("""
        SELECT count(*) FROM public.categories
        WHERE is_default IS TRUE AND user_id IS NULL AND workspace_id IS NULL AND deleted_at IS NULL AND type = 'expense';
        """)
        assert cur.fetchone()[0] == 18, "Aktif gider kategorisi sayısı 18 olmalı!"

        # 1120 ve 1121 tombstone edilmiş olmalı
        cur.execute("""
        SELECT count(*) FROM public.categories
        WHERE id IN ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121')
          AND deleted_at IS NOT NULL;
        """)
        assert cur.fetchone()[0] == 2, "1120 ve 1121 legacy kategorileri tombstone edilmiş olmalı!"

    run_scenario(
        scenario_num=1,
        title="Temiz baseline üzerine uygulama -> tam 27 kanonik aktif kayıt",
        fixture_sql=None,
        expect_success=True,
        post_verify_fn=verify_scenario_1
    )

    # -------------------------------------------------------------------------
    # SENARYO 2: Hedef migration'ın tamamını ikinci kez çalıştırma -> idempotent
    # -------------------------------------------------------------------------
    def pre_verify_scenario_2(cur):
        cur.execute("""
        SELECT id, version, deleted_at
        FROM public.categories
        WHERE id IN ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121');
        """)
        pre_rows = cur.fetchall()
        assert len(pre_rows) == 2, "Baseline'da 1120 ve 1121 bulunamadı!"
        for r in pre_rows:
            assert r[1] == 1, f"Seed sonrası başlangıç version değeri 1 olmalı! Bulunan: {r[1]}"
            assert r[2] is None, f"Seed sonrası başlangıç deleted_at NULL olmalı! Bulunan: {r[2]}"
        print(f"  [OK] Başlangıç seed version=1 ve deleted_at=null invariantı doğrulandı.")

    def verify_scenario_2(cur):
        # 1. çalışmadan sonra 1120 ve 1121'in version ve durumunu al
        cur.execute("""
        SELECT id, version, deleted_at, is_default, user_id, workspace_id, name, type
        FROM public.categories
        WHERE id IN ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121');
        """)
        rows_v1 = {r[0]: r for r in cur.fetchall()}
        assert len(rows_v1) == 2, "1120 ve 1121 kayıtları bulunamadı!"
        for r in rows_v1.values():
            assert r[1] == 2, f"İlk tombstone sonrası version değeri tam olarak 2 olmalı! Bulunan: {r[1]}"
            assert r[2] is not None, "1. çalıştırma sonrası 1120/1121 tombstone olmalı!"
            assert r[3] is True, "is_default=true olmalı!"
            assert r[4] is None, "user_id null olmalı!"
            assert r[5] is None, "workspace_id null olmalı!"
        print(f"  [OK] İlk çalıştırma sonrası tombstone version=2 invariantı doğrulandı.")

        # 2. kez aynı migration SQL'ini doğrudan çalıştır
        cur.execute(migration_raw_sql)

        # 2. çalıştırmadan sonra version artmamış olmalı (version=2 sabit kalmalı)
        cur.execute("""
        SELECT id, version, deleted_at, is_default, user_id, workspace_id, name, type
        FROM public.categories
        WHERE id IN ('11111111-1111-4111-8111-111111111120', '11111111-1111-4111-8111-111111111121');
        """)
        rows_v2 = {r[0]: r for r in cur.fetchall()}
        for cid, r2 in rows_v2.items():
            r1 = rows_v1[cid]
            assert r2[1] == 2, f"İkinci migration çalıştırmasında version tam olarak 2 kalmalı! Bulunan: {r2[1]}"
            assert r2[1] == r1[1], f"Tekrar çalıştırmada {cid} version gereksiz artmamalı! v1={r1[1]}, v2={r2[1]}"
            assert r2[2] is not None, "Tekrar çalıştırmada da tombstone kalmalı!"
            assert r2[3] is True, "is_default=true kalmalı!"
            assert r2[4] is None, "user_id null kalmalı!"
            assert r2[5] is None, "workspace_id null kalmalı!"
        print(f"  [OK] İkinci çalıştırma sonrası version=2 sabitliği (idempotency) doğrulandı.")

        verify_scenario_1(cur)

    run_scenario(
        scenario_num=2,
        title="Hedef migration'ın tamamını ikinci kez çalıştırma -> idempotent, version korunur ve dar invariantlar sağlanır",
        fixture_sql=None,
        expect_success=True,
        pre_verify_fn=pre_verify_scenario_2,
        post_verify_fn=verify_scenario_2
    )

    # -------------------------------------------------------------------------
    # SENARYO 3: Legacy 17 kayıt sözleşme varyantları -> FAIL ve atomik rollback
    # -------------------------------------------------------------------------
    legacy_contract_variants = [
        ("name bozuk", "UPDATE public.categories SET name = 'Bozuk Maas' WHERE id = '11111111-1111-4111-8111-111111111101';", "11111111-1111-4111-8111-111111111101"),
        ("type bozuk", "UPDATE public.categories SET type = 'expense' WHERE id = '11111111-1111-4111-8111-111111111101';", "11111111-1111-4111-8111-111111111101"),
        ("color bozuk", "UPDATE public.categories SET color = '#000000' WHERE id = '11111111-1111-4111-8111-111111111101';", "11111111-1111-4111-8111-111111111101"),
        ("icon bozuk", "UPDATE public.categories SET icon = 'broken_icon' WHERE id = '11111111-1111-4111-8111-111111111101';", "11111111-1111-4111-8111-111111111101"),
        ("user_id dolu", "SET session_replication_role = 'replica'; UPDATE public.categories SET user_id = '00000000-0000-0000-0000-000000000001' WHERE id = '11111111-1111-4111-8111-111111111101'; SET session_replication_role = 'origin';", "11111111-1111-4111-8111-111111111101"),
        ("workspace_id dolu", "SET session_replication_role = 'replica'; UPDATE public.categories SET workspace_id = '00000000-0000-0000-0000-000000000001' WHERE id = '11111111-1111-4111-8111-111111111101'; SET session_replication_role = 'origin';", "11111111-1111-4111-8111-111111111101"),
        ("aktif kalması gereken legacy kayıt tombstone", "UPDATE public.categories SET deleted_at = now() WHERE id = '11111111-1111-4111-8111-111111111101';", "11111111-1111-4111-8111-111111111101"),
    ]

    for idx, (v_name, v_sql, v_target_id) in enumerate(legacy_contract_variants, 1):
        def make_legacy_fixture_check(target_id):
            def check_fn(cur):
                cur.execute(f"SELECT count(*) FROM public.categories WHERE id = '{target_id}';")
                assert cur.fetchone()[0] == 1, f"Bozuk fixture kaydı ({target_id}) migration fail sonrası mevcut olmalı!"
            return check_fn

        run_scenario(
            scenario_num=f"3.{idx}",
            title=f"Legacy sözleşme varyantı [{idx}/7]: {v_name} -> gerçek migration fail ve atomik rollback",
            fixture_sql=v_sql,
            expect_success=False,
            expected_error_code="SEED_PREFLIGHT_FAILED",
            fixture_verify_fn=make_legacy_fixture_check(v_target_id),
            fixture_target_id=v_target_id,
        )

    # -------------------------------------------------------------------------
    # SENARYO 4: Yeni kanonik UUID altında farklı içerik -> FAIL ve rollback
    # -------------------------------------------------------------------------
    def verify_scenario_4_fixture(cur):
        cur.execute("SELECT count(*) FROM public.categories WHERE id = '11111111-1111-4111-8111-111111111106';")
        assert cur.fetchone()[0] == 1, "Fixture kaydı migration fail sonrası mevcut olmalı!"

    run_scenario(
        scenario_num=4,
        title="Yeni kanonik UUID altında farklı içerik -> gerçek migration fail ve rollback",
        fixture_sql="""
        INSERT INTO public.categories (id, name, slug, type, color, icon, is_default, version)
        VALUES ('11111111-1111-4111-8111-111111111106', 'Farklı İsim', 'farkli-slug', 'income', '#000000', 'other', true, 1);
        """,
        expect_success=False,
        expected_error_code="SEED_PREFLIGHT_FAILED",
        fixture_verify_fn=verify_scenario_4_fixture,
        fixture_target_id='11111111-1111-4111-8111-111111111106',
    )

    # -------------------------------------------------------------------------
    # SENARYO 5: Yeni kanonik slug başka UUID'de -> FAIL ve rollback
    # -------------------------------------------------------------------------
    def verify_scenario_5_fixture(cur):
        cur.execute("SELECT count(*) FROM public.categories WHERE id = '99999999-9999-4999-8999-999999999999';")
        assert cur.fetchone()[0] == 1, "Fixture kaydı migration fail sonrası mevcut olmalı!"

    run_scenario(
        scenario_num=5,
        title="Yeni kanonik slug başka UUID'de -> gerçek migration fail ve rollback",
        fixture_sql="""
        INSERT INTO public.categories (id, name, slug, type, color, icon, is_default, version)
        VALUES ('99999999-9999-4999-8999-999999999999', 'Fake Fuel', 'fuel', 'expense', '#3B82F6', 'fuel_pump', true, 1);
        """,
        expect_success=False,
        expected_error_code="SEED_PREFLIGHT_FAILED",
        fixture_verify_fn=verify_scenario_5_fixture,
        fixture_target_id='99999999-9999-4999-8999-999999999999',
    )

    # -------------------------------------------------------------------------
    # SENARYO 6: Kanonik UUID sahiplik çakışması varyantları -> FAIL ve rollback
    # -------------------------------------------------------------------------
    canonical_uuid_variants = [
        ("user_id çakışması", """
        INSERT INTO public.categories (id, user_id, name, slug, type, color, icon, is_default, version)
        VALUES ('11111111-1111-4111-8111-111111111124', '00000000-0000-0000-0000-000000000001', 'User Care', 'user-care', 'expense', '#111111', 'star', false, 1);
        """, '11111111-1111-4111-8111-111111111124'),
        ("workspace_id çakışması (izole: user_id null, workspace_id dolu, is_default true)", """
        INSERT INTO public.categories (id, user_id, workspace_id, name, slug, type, color, icon, is_default, version)
        VALUES ('11111111-1111-4111-8111-111111111124', null, '00000000-0000-0000-0000-000000000001', 'Workspace Care', 'ws-care', 'expense', '#111111', 'star', true, 1);
        """, '11111111-1111-4111-8111-111111111124'),
    ]

    for idx, (v_name, v_sql, v_target_id) in enumerate(canonical_uuid_variants, 1):
        def make_uuid_fixture_check(target_id):
            def check_fn(cur):
                cur.execute(f"SELECT count(*) FROM public.categories WHERE id = '{target_id}';")
                assert cur.fetchone()[0] == 1, f"Çakışan fixture kaydı ({target_id}) migration fail sonrası mevcut olmalı!"
            return check_fn

        run_scenario(
            scenario_num=f"6.{idx}",
            title=f"Kanonik UUID sahiplik varyantı [{idx}/2]: {v_name} -> gerçek migration fail ve atomik rollback",
            fixture_sql=v_sql,
            expect_success=False,
            expected_error_code="SEED_PREFLIGHT_FAILED",
            fixture_verify_fn=make_uuid_fixture_check(v_target_id),
            fixture_target_id=v_target_id,
        )

    # -------------------------------------------------------------------------
    # SENARYO 7: Önceden tombstone edilmiş yeni kanonik kayıt -> FAIL ve rollback
    # -------------------------------------------------------------------------
    def verify_scenario_7_fixture(cur):
        cur.execute("SELECT count(*) FROM public.categories WHERE id = '11111111-1111-4111-8111-111111111125';")
        assert cur.fetchone()[0] == 1, "Fixture kaydı migration fail sonrası mevcut olmalı!"

    run_scenario(
        scenario_num=7,
        title="Önceden tombstone edilmiş yeni kanonik kayıt -> gerçek migration fail ve rollback",
        fixture_sql="""
        INSERT INTO public.categories (id, name, slug, type, color, icon, is_default, deleted_at, version)
        VALUES ('11111111-1111-4111-8111-111111111125', 'Alışveriş', 'shopping', 'expense', '#D946EF', 'shopping_bag', true, now(), 1);
        """,
        expect_success=False,
        expected_error_code="SEED_PREFLIGHT_FAILED",
        fixture_verify_fn=verify_scenario_7_fixture,
        fixture_target_id='11111111-1111-4111-8111-111111111125',
    )

    # -------------------------------------------------------------------------
    # SENARYO 8: Var olan kullanıcı kategorileri değişmeden kalır
    # -------------------------------------------------------------------------
    USER_CAT_ID = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    def verify_scenario_8(cur):
        verify_scenario_1(cur)
        cur.execute(f"""
        SELECT name, slug, type, color, icon, is_default, user_id, deleted_at
        FROM public.categories WHERE id = '{USER_CAT_ID}';
        """)
        row = cur.fetchone()
        assert row is not None, "Kullanıcı kategorisi silinmiş!"
        assert row[0] == 'Özel Kahve'
        assert row[1] == 'ozel-kahve'
        assert row[2] == 'expense'
        assert row[3] == '#995511'
        assert row[4] == 'coffee'
        assert row[5] is False
        assert row[6] == '00000000-0000-0000-0000-000000000001'
        assert row[7] is None

    run_scenario(
        scenario_num=8,
        title="Var olan kullanıcı kategorileri migration sonrası tüm alanlarıyla korunur",
        fixture_sql=f"""
        INSERT INTO public.categories (id, user_id, name, slug, type, color, icon, is_default, version)
        VALUES ('{USER_CAT_ID}', '00000000-0000-0000-0000-000000000001', 'Özel Kahve', 'ozel-kahve', 'expense', '#995511', 'coffee', false, 1);
        """,
        expect_success=True,
        post_verify_fn=verify_scenario_8
    )

    # -------------------------------------------------------------------------
    # SENARYO 9: Post-check'i bozacak fazladan aktif sistem kategorisi -> FAIL ve rollback
    # -------------------------------------------------------------------------
    def verify_scenario_9_fixture(cur):
        cur.execute("SELECT count(*) FROM public.categories WHERE id = '88888888-8888-4888-8888-888888888888';")
        assert cur.fetchone()[0] == 1, "Fixture kaydı migration fail sonrası mevcut olmalı!"

    run_scenario(
        scenario_num=9,
        title="Post-check'i bozacak katalog dışı aktif sistem kategorisi -> gerçek migration fail ve rollback",
        fixture_sql="""
        INSERT INTO public.categories (id, name, slug, type, color, icon, is_default, version)
        VALUES ('88888888-8888-4888-8888-888888888888', 'Ekstra Sistem', 'ekstra-sistem', 'expense', '#123456', 'box', true, 1);
        """,
        expect_success=False,
        expected_error_code="SEED_POSTCHECK_FAILED",
        fixture_verify_fn=verify_scenario_9_fixture,
        fixture_target_id='88888888-8888-4888-8888-888888888888',
    )

    print("\n" + "=" * 70)
    print(f"[SUCCESS] Toplam {executed_scenario_count} gerçek SQL senaryosu ve varyantı doğrudan migration dosyasıyla GEÇTİ!")
    print("=" * 70)

except Exception as e:
    print(f"\n[FAILURE] Test çalışması sırasında hata oluştu: {e}", file=sys.stderr)
    exit_code = 1

finally:
    print("[INFO] Geçici veritabanları temizleniyor...")
    for db_name in created_databases:
        terminate_and_drop_db(db_name)
    print("[INFO] Tüm geçici veritabanları silindi.")

sys.exit(exit_code)
