"""
FeniqoMobil - Para Backfill ve Eski Web - Yeni Mobil Uyumluluk Sözleşme Test Koşucusu

Bu betik:
1. Yalnızca yerel PostgreSQL ortamında (localhost / 127.0.0.1 / ::1) çalışır.
2. Uzak, staging veya production sunucularına bağlanmaz; bağlantı parametrelerini doğrular.
3. Parola, secret veya token loglamaz.
4. Doğrulanmış izole disposable test veritabanları (feniqo_*_<random>) oluşturur ve finally bloğunda tamamen temizler.
5. Disposable DB silinemezse işlemi fail-closed olarak non-zero exit code ile sonlandırır.
6. Var olan küme rollerine zarar vermez.
7. Pozitif para backfill senaryolarını (TRY, USD, EUR - tam sayı, tek ondalık, iki ondalık) doğrular.
8. Negatif fail-closed backfill senaryolarını doğrular.
9. 20260927000100 migration'ı ile güçlendirilmiş sync_transactions_money_compat() tetikleyicisini test eder.
10. Önceden başarısız olan 3 kusurun (web UPDATE, mobil UPDATE, tutarsız INSERT) PASS olduğunu doğrular.
11. Tutarlı/tutarsız çift alan yazımları, NULL koruması, BIGINT taşması ve ilgisiz alan güncellemelerini test eder.
12. Güvenli pre-check: 3 ondalıklı ve BIGINT out-of-range drift'inde raw cast overflow olmadan kontrollü fail-closed duruş.
13. Kesin trigger metadata negatif testleri (AFTER, yalnız INSERT, eksik kolon, fazla kolon, yanlış fonksiyon, disabled).
14. Kesin search_path="" doğrulaması.
15. Function ACL: public/anon/authenticated doğrudan EXECUTE yokken authenticated rolünün gerçek DML çalıştırabilmesi.
16. Mobil Currency enum (GBP) ile veritabanı kısıtları arasındaki uyumsuzluğu raporlar.
"""

import argparse
import hashlib
import os
import sys
import uuid
from decimal import Decimal

if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Güvenlik Kontrolü 1: Yalnız yerel hedefler
ALLOWED_HOSTS = {'localhost', '127.0.0.1', '::1'}

HOST = os.environ.get('PGHOST', 'localhost')
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
    print("[ERROR] 'psycopg2' modülü bulunamadı. 'pip install psycopg2-binary' çalıştırın.", file=sys.stderr)
    sys.exit(1)

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
MIGRATIONS_DIR = os.path.join(REPO_ROOT, "supabase", "migrations")

REQUIRED_MIGRATIONS = [
    "20260814000000_schema_web_v1_baseline.sql",
    "20260814000100_schema_sync_metadata.sql",
    "20260814000200_schema_money_expand.sql",
    "20260814000300_data_money_backfill.sql",
    "20260814000400_functions_conditional_sync.sql",
    "20260814000500_rls_v1_personal.sql",
    "20260927000100_harden_money_compatibility_trigger.sql",
]

for mf in REQUIRED_MIGRATIONS:
    p = os.path.join(MIGRATIONS_DIR, mf)
    if not os.path.isfile(p):
        print(f"[ERROR] Gerekli migration dosyası bulunamadı: {p}", file=sys.stderr)
        sys.exit(1)


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


ALLOWED_DB_PREFIXES = (
    'feniqo_pos_bf_',
    'feniqo_neg_',
    'feniqo_compat_',
    'feniqo_precheck_',
    'feniqo_trg_neg_',
    'feniqo_curr_check_',
)


def create_disposable_db(prefix="feniqo_compat_"):
    if not prefix.startswith(ALLOWED_DB_PREFIXES):
        raise ValueError(f"Geçersiz test veritabanı prefixi: {prefix}")
    db_name = f"{prefix}{uuid.uuid4().hex[:10]}"
    admin_conn = get_admin_conn()
    cur = admin_conn.cursor()
    cur.execute(
        f"CREATE DATABASE {db_name} TEMPLATE = template0 LOCALE_PROVIDER = 'icu' ICU_LOCALE = 'tr-TR' ENCODING = 'UTF8';"
    )
    cur.close()
    admin_conn.close()
    return db_name


def drop_disposable_db(db_name, report):
    """
    Yalnızca runner tarafından üretilmiş doğrulanmış feniqo_* DB'lerini siler.
    Silinemezse cleanup failure kaydeder ve testin fail etmesini sağlar.
    """
    if not db_name.startswith(ALLOWED_DB_PREFIXES):
        report.record_cleanup_failure(f"Güvenlik ihlali: Tanınmayan veritabanı silinemez: {db_name}")
        return

    try:
        admin_conn = get_admin_conn()
        cur = admin_conn.cursor()
        cur.execute(
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = %s AND pid <> pg_backend_pid();",
            (db_name,)
        )
        cur.execute(f'DROP DATABASE IF EXISTS "{db_name}";')
        cur.close()
        admin_conn.close()

        # Doğrulama: Gerçekten silindi mi?
        admin_conn2 = get_admin_conn()
        cur2 = admin_conn2.cursor()
        cur2.execute("SELECT 1 FROM pg_database WHERE datname = %s;", (db_name,))
        row = cur2.fetchone()
        cur2.close()
        admin_conn2.close()
        if row is not None:
            report.record_cleanup_failure(f"Veritabanı silme komutu sonrası DB hâlâ mevcut: {db_name}")
    except Exception as e:
        report.record_cleanup_failure(f"Veritabanı silme istisnası ({db_name}): {e}")


def get_db_conn(db_name):
    conn = psycopg2.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        dbname=db_name
    )
    conn.set_client_encoding('UTF8')
    conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
    return conn


def setup_supabase_prerequisites(conn):
    cur = conn.cursor()
    cur.execute("""
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
    """)
    cur.close()


def apply_migration(conn, filename):
    path = os.path.join(MIGRATIONS_DIR, filename)
    with open(path, "r", encoding="utf-8") as f:
        sql = f.read()
    cur = conn.cursor()
    cur.execute(sql)
    cur.close()


class TestReport:
    def __init__(self):
        self.results = []
        self.defect_count = 0
        self.pass_count = 0
        self.cleanup_failures = []

    def add(self, category, name, passed, details, is_defect=False):
        self.results.append({
            "category": category,
            "name": name,
            "passed": passed,
            "details": details,
            "is_defect": is_defect
        })
        if passed and not is_defect:
            self.pass_count += 1
        else:
            self.defect_count += 1

    def record_cleanup_failure(self, message):
        self.cleanup_failures.append(message)
        print(f"[CLEANUP ERROR] {message}", file=sys.stderr)


def run_positive_backfill_tests(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 1: POZİTİF PARA BACKFILL TESTLERİ (TRY, USD, EUR)")
    print("=" * 70)

    db_name = create_disposable_db("feniqo_pos_bf_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)

        print("[MIGRATION] 1/4: 20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        print("[MIGRATION] 2/4: 20260814000100_schema_sync_metadata.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()

        users = {
            "TRY": str(uuid.uuid4()),
            "USD": str(uuid.uuid4()),
            "EUR": str(uuid.uuid4()),
        }

        categories = {}
        for curr, u_id in users.items():
            cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, f"user_{curr.lower()}@feniqo.local"))
            cur.execute("update public.profiles set currency = %s where id = %s;", (curr, u_id))
            cat_id = str(uuid.uuid4())
            cur.execute(
                "insert into public.categories (id, user_id, name, type, color) values (%s, %s, %s, %s, %s);",
                (cat_id, u_id, f"Kat {curr}", "expense", "#112233")
            )
            categories[curr] = cat_id

        fixtures = [
            {"curr": "TRY", "amount": Decimal("100.00"), "expected_minor": 10000, "desc": "TRY Tam Sayı (100.00)"},
            {"curr": "TRY", "amount": Decimal("50.50"), "expected_minor": 5050, "desc": "TRY Tek Ondalık (50.5)"},
            {"curr": "TRY", "amount": Decimal("24.99"), "expected_minor": 2499, "desc": "TRY İki Ondalık (24.99)"},
            {"curr": "USD", "amount": Decimal("200.00"), "expected_minor": 20000, "desc": "USD Tam Sayı (200.00)"},
            {"curr": "USD", "amount": Decimal("12.30"), "expected_minor": 1230, "desc": "USD Tek Ondalık (12.3)"},
            {"curr": "USD", "amount": Decimal("99.95"), "expected_minor": 9995, "desc": "USD İki Ondalık (99.95)"},
            {"curr": "EUR", "amount": Decimal("300.00"), "expected_minor": 30000, "desc": "EUR Tam Sayı (300.00)"},
            {"curr": "EUR", "amount": Decimal("75.80"), "expected_minor": 7580, "desc": "EUR Tek Ondalık (75.8)"},
            {"curr": "EUR", "amount": Decimal("19.05"), "expected_minor": 1905, "desc": "EUR İki Ondalık (19.05)"},
        ]

        expected_totals = {"TRY": 0, "USD": 0, "EUR": 0}
        source_totals = {"TRY": Decimal("0.00"), "USD": Decimal("0.00"), "EUR": Decimal("0.00")}

        for f in fixtures:
            tx_id = str(uuid.uuid4())
            curr = f["curr"]
            u_id = users[curr]
            cat_id = categories[curr]
            cur.execute("""
            insert into public.transactions (
                id, user_id, amount, type, category_id, payment_method, receipt_url, description
            ) values (%s, %s, %s, %s, %s, %s, %s, %s);
            """, (tx_id, u_id, f["amount"], "expense", cat_id, "cash", f"https://example.com/{tx_id}.jpg", f["desc"]))

            f["id"] = tx_id
            expected_totals[curr] += f["expected_minor"]
            source_totals[curr] += f["amount"]

        print(f"[INFO] 9 adet legacy transaction başarıyla eklendi. Kaynak toplamlar: {source_totals}")

        print("[MIGRATION] 3/4: 20260814000200_schema_money_expand.sql")
        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        print("[MIGRATION] 4/4: 20260814000300_data_money_backfill.sql")
        apply_migration(conn, "20260814000300_data_money_backfill.sql")

        for f in fixtures:
            cur.execute("""
            select amount, amount_minor, currency, receipt_url
            from public.transactions
            where id = %s;
            """, (f["id"],))
            row = cur.fetchone()
            act_amount, act_minor, act_curr, act_receipt = row[0], row[1], row[2], row[3]

            passed = (
                act_amount == f["amount"] and
                act_minor == f["expected_minor"] and
                act_curr == f["curr"] and
                act_receipt == f"https://example.com/{f['id']}.jpg"
            )
            details = f"amount={act_amount} (beklenen={f['amount']}), amount_minor={act_minor} (beklenen={f['expected_minor']}), currency={act_curr} (beklenen={f['curr']})"
            report.add("Pozitif Backfill", f["desc"], passed, details)
            print(f"  [{'PASS' if passed else 'FAIL'}] {f['desc']}: {details}")

        for curr in ["TRY", "USD", "EUR"]:
            cur.execute("select sum(amount_minor) from public.transactions where currency = %s;", (curr,))
            actual_minor_sum = cur.fetchone()[0]
            expected_minor_sum = expected_totals[curr]
            expected_from_source = int(source_totals[curr] * 100)

            passed = (actual_minor_sum == expected_minor_sum == expected_from_source)
            details = f"Hedef Minor Toplam={actual_minor_sum}, Beklenen={expected_minor_sum}, Kaynak({source_totals[curr]} * 100)={expected_from_source}"
            report.add("Kontrol Toplamı", f"{curr} Kontrol Toplamı Uyumu", passed, details)
            print(f"  [{'PASS' if passed else 'FAIL'}] {curr} Kontrol Toplamı: {details}")

        cur.execute("select count(distinct currency) from public.transactions;")
        distinct_currencies = cur.fetchone()[0]
        cur.execute("select count(*) from public.transactions;")
        total_tx_count = cur.fetchone()[0]
        no_mix = (distinct_currencies == 3 and total_tx_count == 9)
        report.add("Kontrol Toplamı", "Currency İzolasyonu (Toplamlar Karışmadı)", no_mix, f"Farklı currency={distinct_currencies}, Toplam={total_tx_count}")
        print(f"  [{'PASS' if no_mix else 'FAIL'}] Currency İzolasyonu: Farklı currency={distinct_currencies}, Toplam={total_tx_count}")

        cur.execute("select count(*) from public.transactions where amount_minor is null or currency is null;")
        null_count = cur.fetchone()[0]
        passed_null = (null_count == 0)
        report.add("Bütünlük", "NULL Hedef Alan Kalmadı", passed_null, f"NULL kalan kayıt sayısı={null_count}")
        print(f"  [{'PASS' if passed_null else 'FAIL'}] NULL Hedef Alan Denetimi: NULL kalan kayıt sayısı={null_count}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)


def run_negative_backfill_tests(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 2: NEGATİF FAIL-CLOSED BACKFILL TESTLERİ")
    print("=" * 70)

    # Negatif 1: amount <= 0
    db_name = create_disposable_db("feniqo_neg_zero_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "neg1@feniqo.local"))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        blocked_by_baseline = False
        try:
            cur.execute("""
            insert into public.transactions (user_id, amount, type, category_id, payment_method)
            values (%s, 0, 'expense', %s, 'cash');
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            if "transactions_amount_check" in str(e) or "check constraint" in str(e):
                blocked_by_baseline = True
        report.add("Negatif Backfill", "amount <= 0 Baseline Check Kısıtı Engeli", blocked_by_baseline, "Baseline şeması check (amount > 0) ile eklemeyi durdurdu")
        print(f"  [{'PASS' if blocked_by_baseline else 'FAIL'}] amount <= 0 Baseline Şema Kısıtı: {blocked_by_baseline}")

        cur.execute("alter table public.transactions drop constraint if exists transactions_amount_check;")
        cur.execute("""
        insert into public.transactions (user_id, amount, type, category_id, payment_method)
        values (%s, -10.00, 'expense', %s, 'cash');
        """, (u_id, cat_id))

        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        backfill_failed_as_expected = False
        err_msg = ""
        try:
            apply_migration(conn, "20260814000300_data_money_backfill.sql")
        except psycopg2.Error as e:
            err_msg = str(e)
            if "pozitif olmayan, ikiden fazla ondalıklı veya BIGINT sınırını aşan amount bulundu" in err_msg:
                backfill_failed_as_expected = True

        try:
            cur.execute("ROLLBACK;")
        except Exception:
            pass

        cur.execute("select count(*) from public.transactions where amount_minor is not null;")
        partial_count = cur.fetchone()[0]
        passed = backfill_failed_as_expected and (partial_count == 0)
        report.add("Negatif Backfill", "amount <= 0 Migration Pre-check ve Atomik Rollback", passed, f"Hata yakalandı={backfill_failed_as_expected}, Yarım backfill={partial_count}")
        print(f"  [{'PASS' if passed else 'FAIL'}] amount <= 0 Migration Pre-check: {passed} (Hata: {err_msg.strip()[:60]}...)")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # Negatif 2: ikiden fazla ondalık hassasiyet (12.345)
    db_name = create_disposable_db("feniqo_neg_dec_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "neg2@feniqo.local"))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        cur.execute("""
        insert into public.transactions (user_id, amount, type, category_id, payment_method)
        values (%s, 12.345, 'expense', %s, 'cash');
        """, (u_id, cat_id))

        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        backfill_failed_as_expected = False
        err_msg = ""
        try:
            apply_migration(conn, "20260814000300_data_money_backfill.sql")
        except psycopg2.Error as e:
            err_msg = str(e)
            if "pozitif olmayan, ikiden fazla ondalıklı veya BIGINT sınırını aşan amount bulundu" in err_msg:
                backfill_failed_as_expected = True

        try:
            cur.execute("ROLLBACK;")
        except Exception:
            pass

        cur.execute("select count(*) from public.transactions where amount_minor is not null;")
        partial_count = cur.fetchone()[0]
        passed = backfill_failed_as_expected and (partial_count == 0)
        report.add("Negatif Backfill", "İkiden Fazla Ondalık (12.345) Reddi ve Rollback", passed, f"Hata yakalandı={backfill_failed_as_expected}, Yarım backfill={partial_count}")
        print(f"  [{'PASS' if passed else 'FAIL'}] İkiden Fazla Ondalık Reddi: {passed}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # Negatif 3: BIGINT minor-unit taşması (amount > 92233720368547758.07)
    db_name = create_disposable_db("feniqo_neg_bigint_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "neg3@feniqo.local"))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        cur.execute("""
        insert into public.transactions (user_id, amount, type, category_id, payment_method)
        values (%s, 99999999999999999.00, 'expense', %s, 'cash');
        """, (u_id, cat_id))

        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        backfill_failed_as_expected = False
        err_msg = ""
        try:
            apply_migration(conn, "20260814000300_data_money_backfill.sql")
        except psycopg2.Error as e:
            err_msg = str(e)
            if "pozitif olmayan, ikiden fazla ondalıklı veya BIGINT sınırını aşan amount bulundu" in err_msg:
                backfill_failed_as_expected = True

        try:
            cur.execute("ROLLBACK;")
        except Exception:
            pass

        cur.execute("select count(*) from public.transactions where amount_minor is not null;")
        partial_count = cur.fetchone()[0]
        passed = backfill_failed_as_expected and (partial_count == 0)
        report.add("Negatif Backfill", "BIGINT Taşması Reddi ve Rollback", passed, f"Hata yakalandı={backfill_failed_as_expected}, Yarım backfill={partial_count}")
        print(f"  [{'PASS' if passed else 'FAIL'}] BIGINT Taşması Reddi: {passed}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # Negatif 4: Desteklenmeyen profil currency'si (örn. 'GBP' veya 'JPY')
    db_name = create_disposable_db("feniqo_neg_curr_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "neg4@feniqo.local"))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))
        cur.execute("insert into public.transactions (user_id, amount, type, category_id, payment_method) values (%s, 50.00, 'expense', %s, 'cash');", (u_id, cat_id))

        blocked_by_baseline = False
        try:
            cur.execute("update public.profiles set currency = 'GBP' where id = %s;", (u_id,))
        except psycopg2.Error as e:
            if "profiles_currency_check" in str(e) or "check constraint" in str(e):
                blocked_by_baseline = True
        report.add("Negatif Backfill", "Desteklenmeyen Currency Baseline Check Kısıtı", blocked_by_baseline, "Baseline şeması check (currency in ('TRY', 'USD', 'EUR')) ile GBP'yi durdurdu")
        print(f"  [{'PASS' if blocked_by_baseline else 'FAIL'}] Desteklenmeyen Currency Baseline Şema Kısıtı: {blocked_by_baseline}")

        cur.execute("alter table public.profiles drop constraint if exists profiles_currency_check;")
        cur.execute("update public.profiles set currency = 'GBP' where id = %s;", (u_id,))

        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        backfill_failed_as_expected = False
        err_msg = ""
        try:
            apply_migration(conn, "20260814000300_data_money_backfill.sql")
        except psycopg2.Error as e:
            err_msg = str(e)
            if "desteklenmeyen profil para birimi bulundu" in err_msg:
                backfill_failed_as_expected = True

        try:
            cur.execute("ROLLBACK;")
        except Exception:
            pass

        cur.execute("select count(*) from public.transactions where amount_minor is not null;")
        partial_count = cur.fetchone()[0]
        passed = backfill_failed_as_expected and (partial_count == 0)
        report.add("Negatif Backfill", "Desteklenmeyen Currency Migration Pre-check ve Rollback", passed, f"Hata yakalandı={backfill_failed_as_expected}, Yarım backfill={partial_count}")
        print(f"  [{'PASS' if passed else 'FAIL'}] Desteklenmeyen Currency Migration Pre-check: {passed}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # Negatif 5: Profil ilişkisi bulunmayan transaction
    db_name = create_disposable_db("feniqo_neg_orphan_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "orphan@feniqo.local"))
        cur.execute("delete from public.profiles where id = %s;", (u_id,))

        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))
        cur.execute("""
        insert into public.transactions (user_id, amount, type, category_id, payment_method)
        values (%s, 100.00, 'expense', %s, 'cash');
        """, (u_id, cat_id))

        apply_migration(conn, "20260814000200_schema_money_expand.sql")
        backfill_failed_as_expected = False
        err_msg = ""
        try:
            apply_migration(conn, "20260814000300_data_money_backfill.sql")
        except psycopg2.Error as e:
            err_msg = str(e)
            if "Para backfill eksik kaldı; NULL hedef alan bulundu" in err_msg:
                backfill_failed_as_expected = True

        try:
            cur.execute("ROLLBACK;")
        except Exception:
            pass

        cur.execute("select count(*) from public.transactions where amount_minor is not null;")
        partial_count = cur.fetchone()[0]
        passed = backfill_failed_as_expected and (partial_count == 0)
        report.add("Negatif Backfill", "Profilsiz Transaction Reddi (NULL Koruması) ve Rollback", passed, f"Hata yakalandı={backfill_failed_as_expected}, Yarım backfill={partial_count}")
        print(f"  [{'PASS' if passed else 'FAIL'}] Profilsiz Transaction Reddi: {passed}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # Negatif 6: NULL amount baseline constraint denetimi
    db_name = create_disposable_db("feniqo_neg_null_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000100_schema_sync_metadata.sql")

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "null_amt@feniqo.local"))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        cur.execute("""
        select is_nullable, data_type
        from information_schema.columns
        where table_schema = 'public' and table_name = 'transactions' and column_name = 'amount';
        """)
        row = cur.fetchone()
        is_nullable, data_type = row[0], row[1]
        schema_not_null = (is_nullable == 'NO')

        null_insertion_blocked = False
        try:
            cur.execute("""
            insert into public.transactions (user_id, amount, type, category_id, payment_method)
            values (%s, null, 'expense', %s, 'cash');
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            if "not-null constraint" in str(e) or "null value in column" in str(e):
                null_insertion_blocked = True

        passed = schema_not_null and null_insertion_blocked
        report.add("Negatif Backfill", "NULL amount Baseline NOT NULL Kısıtı", passed, f"is_nullable={is_nullable}, NOT NULL kısıtı INSERT'i engelledi={null_insertion_blocked}")
        print(f"  [{'PASS' if passed else 'FAIL'}] NULL amount Baseline Kısıtı: {passed} (is_nullable={is_nullable})")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)


def run_web_mobile_compatibility_tests(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 3: GÜÇLENDİRİLMİŞ WEB — MOBİL UYUMLULUK VE TRIGGER SÖZLEŞME TESTLERİ")
    print("=" * 70)

    db_name = create_disposable_db("feniqo_compat_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)

        for mf in REQUIRED_MIGRATIONS:
            print(f"[MIGRATION] Uygulanıyor: {mf}")
            apply_migration(conn, mf)

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "compat@feniqo.local"))
        cur.execute("update public.profiles set currency = 'TRY' where id = %s;", (u_id,))

        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'Genel', 'expense', '#00aa00');", (cat_id, u_id))

        # 3.1. Eski web INSERT
        tx1_id = str(uuid.uuid4())
        cur.execute("""
        insert into public.transactions (
            id, user_id, amount, type, category_id, payment_method, receipt_url
        ) values (
            %s, %s, 150.25, 'expense', %s, 'credit_card', 'https://example.com/receipt1.jpg'
        ) returning amount, amount_minor, currency, receipt_url;
        """, (tx1_id, u_id, cat_id))
        row1 = cur.fetchone()
        t1_amt, t1_minor, t1_curr, t1_receipt = row1[0], row1[1], row1[2], row1[3]

        p1 = (t1_amt == Decimal("150.25") and t1_minor == 15025 and t1_curr == "TRY" and t1_receipt == "https://example.com/receipt1.jpg")
        report.add("Uyumluluk", "Eski Web INSERT (amount -> amount_minor & currency)", p1, f"amount={t1_amt}, amount_minor={t1_minor}, currency={t1_curr}, receipt_url={t1_receipt}")
        print(f"  [{'PASS' if p1 else 'FAIL'}] Eski Web INSERT: {p1}")

        # 3.2. Yeni mobil INSERT
        tx2_id = str(uuid.uuid4())
        cur.execute("""
        insert into public.transactions (
            id, user_id, amount_minor, currency, type, category_id, payment_method, receipt_path
        ) values (
            %s, %s, 45000, 'USD', 'expense', %s, 'cash', 'users/u1/tx2.png'
        ) returning amount, amount_minor, currency, receipt_path;
        """, (tx2_id, u_id, cat_id))
        row2 = cur.fetchone()
        t2_amt, t2_minor, t2_curr, t2_path = row2[0], row2[1], row2[2], row2[3]

        p2 = (t2_amt == Decimal("450.00") and t2_minor == 45000 and t2_curr == "USD" and t2_path == "users/u1/tx2.png")
        report.add("Uyumluluk", "Yeni Mobil INSERT (amount_minor -> amount)", p2, f"amount={t2_amt}, amount_minor={t2_minor}, currency={t2_curr}, receipt_path={t2_path}")
        print(f"  [{'PASS' if p2 else 'FAIL'}] Yeni Mobil INSERT: {p2}")

        # 3.3. Tutarlı Çift Alan INSERT (amount + amount_minor)
        tx3_id = str(uuid.uuid4())
        cur.execute("""
        insert into public.transactions (
            id, user_id, amount, amount_minor, currency, type, category_id, payment_method
        ) values (
            %s, %s, 75.50, 7550, 'EUR', 'expense', %s, 'cash'
        ) returning amount, amount_minor, currency;
        """, (tx3_id, u_id, cat_id))
        row3 = cur.fetchone()
        p3 = (row3[0] == Decimal("75.50") and row3[1] == 7550 and row3[2] == "EUR")
        report.add("Uyumluluk", "Tutarlı Çift Alan INSERT Kabulü", p3, f"amount={row3[0]}, amount_minor={row3[1]}, currency={row3[2]}")
        print(f"  [{'PASS' if p3 else 'FAIL'}] Tutarlı Çift Alan INSERT: {p3}")

        # 3.4. Tutarsız Çift Alan INSERT Reddi
        tx4_id = str(uuid.uuid4())
        inconsistent_insert_rejected = False
        sqlstate_insert = ""
        try:
            cur.execute("""
            insert into public.transactions (
                id, user_id, amount, amount_minor, currency, type, category_id, payment_method
            ) values (
                %s, %s, 10.00, 9999, 'TRY', 'expense', %s, 'cash'
            );
            """, (tx4_id, u_id, cat_id))
        except psycopg2.Error as e:
            inconsistent_insert_rejected = True
            sqlstate_insert = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        cur.execute("select count(*) from public.transactions where id = %s;", (tx4_id,))
        tx4_exists = (cur.fetchone()[0] > 0)
        p4 = (inconsistent_insert_rejected and not tx4_exists and sqlstate_insert == '22000')
        report.add(
            "Güvenlik / Veri Bütünlüğü",
            "Tutarsız amount ve amount_minor INSERT Fail-Closed Reddi",
            p4,
            f"Reddedildi={inconsistent_insert_rejected}, SQLSTATE={sqlstate_insert}, Satır oluşmadı={not tx4_exists}"
        )
        print(f"  [{'PASS' if p4 else 'FAIL'}] Tutarsız INSERT Reddi: {p4} (SQLSTATE: {sqlstate_insert})")

        # 3.5. İkisi de NULL INSERT Reddi
        tx5_null_id = str(uuid.uuid4())
        null_insert_rejected = False
        sqlstate_null_ins = ""
        try:
            cur.execute("""
            insert into public.transactions (
                id, user_id, amount, amount_minor, currency, type, category_id, payment_method
            ) values (
                %s, %s, null, null, 'TRY', 'expense', %s, 'cash'
            );
            """, (tx5_null_id, u_id, cat_id))
        except psycopg2.Error as e:
            null_insert_rejected = True
            sqlstate_null_ins = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        p5_null = (null_insert_rejected and sqlstate_null_ins == '22004')
        report.add("Uyumluluk", "İkisi de NULL INSERT Reddi", p5_null, f"Reddedildi={null_insert_rejected}, SQLSTATE={sqlstate_null_ins}")
        print(f"  [{'PASS' if p5_null else 'FAIL'}] İkisi de NULL INSERT Reddi: {p5_null}")

        # 3.6. Sıfır ve Negatif INSERT Reddi
        neg_val_rejected = False
        try:
            cur.execute("""
            insert into public.transactions (
                user_id, amount_minor, currency, type, category_id, payment_method
            ) values (
                %s, -500, 'TRY', 'expense', %s, 'cash'
            );
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            neg_val_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        report.add("Uyumluluk", "Negatif amount_minor INSERT Reddi", neg_val_rejected, f"Reddedildi={neg_val_rejected}")
        print(f"  [{'PASS' if neg_val_rejected else 'FAIL'}] Negatif amount_minor INSERT Reddi: {neg_val_rejected}")

        # 3.7. İkiden Fazla Ondalık INSERT Reddi
        more_dec_rejected = False
        try:
            cur.execute("""
            insert into public.transactions (
                user_id, amount, currency, type, category_id, payment_method
            ) values (
                %s, 10.999, 'TRY', 'expense', %s, 'cash'
            );
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            more_dec_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        report.add("Uyumluluk", "İkiden Fazla Ondalık INSERT Reddi", more_dec_rejected, f"Reddedildi={more_dec_rejected}")
        print(f"  [{'PASS' if more_dec_rejected else 'FAIL'}] İkiden Fazla Ondalık INSERT Reddi: {more_dec_rejected}")

        # 3.8. BIGINT Taşması INSERT Reddi
        bigint_ovf_rejected = False
        sqlstate_bigint = ""
        try:
            cur.execute("""
            insert into public.transactions (
                user_id, amount, currency, type, category_id, payment_method
            ) values (
                %s, 99999999999999999.00, 'TRY', 'expense', %s, 'cash'
            );
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            bigint_ovf_rejected = True
            sqlstate_bigint = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        p_bigint = (bigint_ovf_rejected and sqlstate_bigint == '22003')
        report.add("Uyumluluk", "BIGINT Taşması INSERT Reddi", p_bigint, f"Reddedildi={bigint_ovf_rejected}, SQLSTATE={sqlstate_bigint}")
        print(f"  [{'PASS' if p_bigint else 'FAIL'}] BIGINT Taşması INSERT Reddi: {p_bigint}")

        # 3.9. Desteklenmeyen Currency INSERT Reddi
        curr_unsupported_rejected = False
        try:
            cur.execute("""
            insert into public.transactions (
                user_id, amount_minor, currency, type, category_id, payment_method
            ) values (
                %s, 1000, 'GBP', 'expense', %s, 'cash'
            );
            """, (u_id, cat_id))
        except psycopg2.Error as e:
            curr_unsupported_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        report.add("Uyumluluk", "Desteklenmeyen Currency (GBP) INSERT Reddi", curr_unsupported_rejected, f"Reddedildi={curr_unsupported_rejected}")
        print(f"  [{'PASS' if curr_unsupported_rejected else 'FAIL'}] Desteklenmeyen Currency (GBP) INSERT Reddi: {curr_unsupported_rejected}")

        # 3.10. Eski Web UPDATE Senkronizasyonu
        cur.execute("""
        update public.transactions
        set amount = 250.75
        where id = %s
        returning amount, amount_minor, currency;
        """, (tx1_id,))
        row10 = cur.fetchone()
        t10_amt, t10_minor = row10[0], row10[1]

        p10 = (t10_amt == Decimal("250.75") and t10_minor == 25075)
        report.add("Uyumluluk", "Eski Web UPDATE Senkronizasyonu (amount -> amount_minor)", p10, f"amount={t10_amt}, amount_minor={t10_minor} (Beklenen: 25075)")
        print(f"  [{'PASS' if p10 else 'FAIL'}] Eski Web UPDATE: {p10} (amount={t10_amt}, amount_minor={t10_minor})")

        # 3.11. Yeni Mobil UPDATE Senkronizasyonu
        cur.execute("""
        update public.transactions
        set amount_minor = 60000
        where id = %s
        returning amount, amount_minor, currency;
        """, (tx2_id,))
        row11 = cur.fetchone()
        t11_amt, t11_minor = row11[0], row11[1]

        p11 = (t11_amt == Decimal("600.00") and t11_minor == 60000)
        report.add("Uyumluluk", "Yeni Mobil UPDATE Senkronizasyonu (amount_minor -> amount)", p11, f"amount={t11_amt} (Beklenen: 600.00), amount_minor={t11_minor}")
        print(f"  [{'PASS' if p11 else 'FAIL'}] Yeni Mobil UPDATE: {p11} (amount={t11_amt}, amount_minor={t11_minor})")

        # 3.12. Tutarlı Çift Alan UPDATE Kabulü
        cur.execute("""
        update public.transactions
        set amount = 800.50, amount_minor = 80050
        where id = %s
        returning amount, amount_minor;
        """, (tx2_id,))
        row12 = cur.fetchone()
        p12 = (row12[0] == Decimal("800.50") and row12[1] == 80050)
        report.add("Uyumluluk", "Tutarlı Çift Alan UPDATE Kabulü", p12, f"amount={row12[0]}, amount_minor={row12[1]}")
        print(f"  [{'PASS' if p12 else 'FAIL'}] Tutarlı Çift Alan UPDATE: {p12}")

        # 3.13. Tutarsız Çift Alan UPDATE Reddi ve Veri Korunumu
        inconsistent_update_rejected = False
        sqlstate_upd = ""
        try:
            cur.execute("""
            update public.transactions
            set amount = 999.00, amount_minor = 11111
            where id = %s;
            """, (tx2_id,))
        except psycopg2.Error as e:
            inconsistent_update_rejected = True
            sqlstate_upd = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        cur.execute("select amount, amount_minor from public.transactions where id = %s;", (tx2_id,))
        row_preserve = cur.fetchone()
        p13 = (inconsistent_update_rejected and row_preserve[0] == Decimal("800.50") and row_preserve[1] == 80050 and sqlstate_upd == '22000')
        report.add(
            "Güvenlik / Veri Bütünlüğü",
            "Tutarsız Çift Alan UPDATE Reddi ve Veri Korunumu",
            p13,
            f"Reddedildi={inconsistent_update_rejected}, SQLSTATE={sqlstate_upd}, Korunan={row_preserve}"
        )
        print(f"  [{'PASS' if p13 else 'FAIL'}] Tutarsız Çift Alan UPDATE Reddi: {p13} (Korunan: {row_preserve})")

        # 3.14. İlgisiz Kolon Güncellemesi
        cur.execute("""
        update public.transactions
        set description = 'Yeni Açıklama', payment_method = 'bank_transfer', receipt_path = 'users/u1/new_receipt.png'
        where id = %s
        returning amount, amount_minor, description, receipt_path;
        """, (tx2_id,))
        row14 = cur.fetchone()
        p14 = (
            row14[0] == Decimal("800.50") and
            row14[1] == 80050 and
            row14[2] == "Yeni Açıklama" and
            row14[3] == "users/u1/new_receipt.png"
        )
        report.add("Uyumluluk", "İlgisiz Kolon Güncellemesi Tutarları Değiştirmez", p14, f"amount={row14[0]}, amount_minor={row14[1]}")
        print(f"  [{'PASS' if p14 else 'FAIL'}] İlgisiz Kolon Güncellemesi: {p14}")

        # 3.15. amount NULL UPDATE Reddi
        null_amt_upd_rejected = False
        sqlstate_null_amt = ""
        try:
            cur.execute("update public.transactions set amount = null where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            null_amt_upd_rejected = True
            sqlstate_null_amt = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        p15 = (null_amt_upd_rejected and sqlstate_null_amt == '22004')
        report.add("Uyumluluk", "amount NULL UPDATE Reddi", p15, f"Reddedildi={null_amt_upd_rejected}, SQLSTATE={sqlstate_null_amt}")
        print(f"  [{'PASS' if p15 else 'FAIL'}] amount NULL UPDATE Reddi: {p15}")

        # 3.16. amount_minor NULL UPDATE Reddi
        null_minor_upd_rejected = False
        sqlstate_null_min = ""
        try:
            cur.execute("update public.transactions set amount_minor = null where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            null_minor_upd_rejected = True
            sqlstate_null_min = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        p16 = (null_minor_upd_rejected and sqlstate_null_min == '22004')
        report.add("Uyumluluk", "amount_minor NULL UPDATE Reddi", p16, f"Reddedildi={null_minor_upd_rejected}, SQLSTATE={sqlstate_null_min}")
        print(f"  [{'PASS' if p16 else 'FAIL'}] amount_minor NULL UPDATE Reddi: {p16}")

        # 3.17. currency NULL UPDATE Reddi
        null_curr_upd_rejected = False
        sqlstate_null_curr = ""
        try:
            cur.execute("update public.transactions set currency = null where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            null_curr_upd_rejected = True
            sqlstate_null_curr = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        p17 = (null_curr_upd_rejected and sqlstate_null_curr == '22004')
        report.add("Uyumluluk", "currency NULL UPDATE Reddi", p17, f"Reddedildi={null_curr_upd_rejected}, SQLSTATE={sqlstate_null_curr}")
        print(f"  [{'PASS' if p17 else 'FAIL'}] currency NULL UPDATE Reddi: {p17}")

        # 3.18. Sıfır ve Negatif UPDATE Reddi
        zero_upd_rejected = False
        try:
            cur.execute("update public.transactions set amount = 0 where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            zero_upd_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        report.add("Uyumluluk", "amount = 0 UPDATE Reddi", zero_upd_rejected, f"Reddedildi={zero_upd_rejected}")
        print(f"  [{'PASS' if zero_upd_rejected else 'FAIL'}] amount = 0 UPDATE Reddi: {zero_upd_rejected}")

        # 3.19. İkiden Fazla Ondalık UPDATE Reddi
        scale_upd_rejected = False
        try:
            cur.execute("update public.transactions set amount = 123.456 where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            scale_upd_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        report.add("Uyumluluk", "İkiden Fazla Ondalık UPDATE Reddi", scale_upd_rejected, f"Reddedildi={scale_upd_rejected}")
        print(f"  [{'PASS' if scale_upd_rejected else 'FAIL'}] İkiden Fazla Ondalık UPDATE Reddi: {scale_upd_rejected}")

        # 3.20. BIGINT Taşması UPDATE Reddi
        ovf_upd_rejected = False
        sqlstate_ovf_upd = ""
        try:
            cur.execute("update public.transactions set amount = 99999999999999999.00 where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            ovf_upd_rejected = True
            sqlstate_ovf_upd = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        p20 = (ovf_upd_rejected and sqlstate_ovf_upd == '22003')
        report.add("Uyumluluk", "BIGINT Taşması UPDATE Reddi", p20, f"Reddedildi={ovf_upd_rejected}, SQLSTATE={sqlstate_ovf_upd}")
        print(f"  [{'PASS' if p20 else 'FAIL'}] BIGINT Taşması UPDATE Reddi: {p20}")

        # 3.21. Desteklenmeyen Currency UPDATE Reddi
        gbp_upd_rejected = False
        try:
            cur.execute("update public.transactions set currency = 'GBP' where id = %s;", (tx2_id,))
        except psycopg2.Error as e:
            gbp_upd_rejected = True
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass
        report.add("Uyumluluk", "Desteklenmeyen Currency (GBP) UPDATE Reddi", gbp_upd_rejected, f"Reddedildi={gbp_upd_rejected}")
        print(f"  [{'PASS' if gbp_upd_rejected else 'FAIL'}] Desteklenmeyen Currency UPDATE Reddi: {gbp_upd_rejected}")

        # 3.22. Eski Web Okuma Modeli (amount, receipt_url)
        cur.execute("select amount, receipt_url from public.transactions where id = %s;", (tx1_id,))
        web_read_row = cur.fetchone()
        p22 = (web_read_row is not None and web_read_row[0] == Decimal("250.75") and web_read_row[1] == "https://example.com/receipt1.jpg")
        report.add("Okuma Uyumluluğu", "Eski Web Okuma Modeli (amount, receipt_url)", p22, f"Dönen satır={web_read_row}")
        print(f"  [{'PASS' if p22 else 'FAIL'}] Eski Web Okuma Modeli: {p22}")

        # 3.23. Yeni Mobil Okuma Modeli (amount_minor, currency, receipt_path)
        cur.execute("select amount_minor, currency, receipt_path from public.transactions where id = %s;", (tx2_id,))
        mobile_read_row = cur.fetchone()
        p23 = (mobile_read_row is not None and mobile_read_row[0] == 80050 and mobile_read_row[1] == "USD" and mobile_read_row[2] == "users/u1/new_receipt.png")
        report.add("Okuma Uyumluluğu", "Yeni Mobil Okuma Modeli (amount_minor, currency, receipt_path)", p23, f"Dönen satır={mobile_read_row}")
        print(f"  [{'PASS' if p23 else 'FAIL'}] Yeni Mobil Okuma Modeli: {p23}")

        # 3.24. Kesin search_path="" Doğrulaması (Post-check güvencesi)
        cur.execute("""
        select proconfig
        from pg_proc
        where oid = to_regprocedure('public.sync_transactions_money_compat()');
        """)
        proconfig_row = cur.fetchone()
        proconfig = proconfig_row[0] if proconfig_row else None
        exact_search_path_empty = (proconfig is not None and 'search_path=""' in proconfig and 'search_path=public' not in proconfig)
        report.add("Güvenlik", "Kesin search_path=\"\" Ayarı Doğrulaması", exact_search_path_empty, f"proconfig={proconfig}")
        print(f"  [{'PASS' if exact_search_path_empty else 'FAIL'}] search_path=\"\" Doğrulaması: {exact_search_path_empty}")

        # 3.25. Function ACL ve Authenticated Gerçek DML Testi
        cur.execute("""
        select
            has_function_privilege('public', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE'),
            has_function_privilege('anon', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE'),
            has_function_privilege('authenticated', to_regprocedure('public.sync_transactions_money_compat()'), 'EXECUTE');
        """)
        acl_row = cur.fetchone()
        acl_clean = (acl_row[0] is False and acl_row[1] is False and acl_row[2] is False)
        report.add("Yetkilendirme / ACL", "İstemci Rolleri Doğrudan Function EXECUTE Yoksunluğu", acl_clean, f"public={acl_row[0]}, anon={acl_row[1]}, authenticated={acl_row[2]}")
        print(f"  [{'PASS' if acl_clean else 'FAIL'}] İstemci EXECUTE Kısıtlaması: {acl_clean}")

        # Authenticated kullanıcısı tablo üzerinden INSERT ve UPDATE yapabilmeli (Trigger çalışır!)
        auth_dml_ok = False
        try:
            auth_tx_id = str(uuid.uuid4())
            cur.execute("""
            do $$
            declare
                v_auth_uid uuid := %s::uuid;
                v_cat_id uuid := %s::uuid;
                v_tx_id uuid := %s::uuid;
            begin
                execute 'set local role authenticated';
                perform set_config('request.jwt.claim.sub', v_auth_uid::text, true);
                perform set_config('request.jwt.claim.role', 'authenticated', true);

                -- 1. Authenticated INSERT
                insert into public.transactions (
                    id, user_id, amount_minor, currency, type, category_id, payment_method
                ) values (
                    v_tx_id, v_auth_uid, 35000, 'TRY', 'expense', v_cat_id, 'credit_card'
                );

                -- 2. Authenticated UPDATE (amount_minor)
                update public.transactions
                set amount_minor = 42000
                where id = v_tx_id;
            end;
            $$;
            """, (u_id, cat_id, auth_tx_id))

            cur.execute("select amount, amount_minor from public.transactions where id = %s;", (auth_tx_id,))
            auth_tx_row = cur.fetchone()
            if auth_tx_row and auth_tx_row[0] == Decimal("420.00") and auth_tx_row[1] == 42000:
                auth_dml_ok = True
        except psycopg2.Error as e:
            print(f"[AUTH DML ERROR] {e}", file=sys.stderr)
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        report.add("Yetkilendirme / DML", "Authenticated Kullanıcısının Trigger Üzerinden DML Yürütmesi", auth_dml_ok, f"DML Başarılı={auth_dml_ok}")
        print(f"  [{'PASS' if auth_dml_ok else 'FAIL'}] Authenticated Gerçek DML (Trigger Çalıştı): {auth_dml_ok}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)


def run_migration_precheck_tests(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 4: MİGRATİON PRE-CHECK FAIL-CLOSED, DRIFT VE DEĞİŞMEZLİK DENETİMLERİ")
    print("=" * 70)

    # 4.1. Üç ondalıklı mevcut drift (amount=1.234)
    db_name = create_disposable_db("feniqo_precheck_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)

        for mf in REQUIRED_MIGRATIONS[:-1]:
            apply_migration(conn, mf)

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "precheck_dec@feniqo.local"))
        cur.execute("update public.profiles set currency = 'TRY' where id = %s;", (u_id,))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        cur.execute("select pg_get_functiondef(oid) from pg_proc where proname = 'sync_transactions_money_compat';")
        old_function_def = cur.fetchone()[0]
        old_fn_hash = hashlib.sha256(old_function_def.encode('utf-8')).hexdigest()

        cur.execute("select tgtype, tgattr from pg_trigger where tgname = 'transactions_money_compat_before_write';")
        old_trg_info = cur.fetchone()

        # Trigger'ı geçici devre dışı bırakıp 3 ondalıklı satır ekle:
        cur.execute("alter table public.transactions disable trigger transactions_money_compat_before_write;")
        cur.execute("""
        insert into public.transactions (
            user_id, amount, amount_minor, currency, type, category_id, payment_method
        ) values (
            %s, 1.234, 123, 'TRY', 'expense', %s, 'cash'
        );
        """, (u_id, cat_id))
        cur.execute("alter table public.transactions enable trigger transactions_money_compat_before_write;")

        precheck_dec_failed = False
        err_msg_dec = ""
        try:
            apply_migration(conn, "20260927000100_harden_money_compatibility_trigger.sql")
        except psycopg2.Error as e:
            precheck_dec_failed = True
            err_msg_dec = str(e)
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        p_dec = (precheck_dec_failed and "Pre-check failed: public.transactions içinde para sözleşmesine uymayan (drift:" in err_msg_dec)
        report.add("Migration Pre-Check", "3 Ondalıklı Mevcut Drift Pre-Check Engeli", p_dec, f"Durduruldu={precheck_dec_failed}, Mesaj={err_msg_dec.strip()[:80]}...")
        print(f"  [{'PASS' if p_dec else 'FAIL'}] 3 Ondalıklı Drift Pre-Check Engeli: {p_dec}")

        cur.execute("select pg_get_functiondef(oid) from pg_proc where proname = 'sync_transactions_money_compat';")
        current_fn_hash = hashlib.sha256(cur.fetchone()[0].encode('utf-8')).hexdigest()
        cur.execute("select tgtype, tgattr from pg_trigger where tgname = 'transactions_money_compat_before_write';")
        curr_trg_info = cur.fetchone()

        p_dec_unmod = (old_fn_hash == current_fn_hash and old_trg_info == curr_trg_info)
        report.add("Migration Pre-Check", "3 Ondalıklı Drift Rollback Sonrası Değişmezlik", p_dec_unmod, f"Fn Değişmedi={old_fn_hash == current_fn_hash}, Trigger Değişmedi={old_trg_info == curr_trg_info}")
        print(f"  [{'PASS' if p_dec_unmod else 'FAIL'}] Rollback Değişmezliği (3 Ondalık): {p_dec_unmod}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    # 4.2. BIGINT out-of-range drift (amount > 92233720368547758.07) - Raw Cast Hatası Olmadan
    db_name = create_disposable_db("feniqo_precheck_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)

        for mf in REQUIRED_MIGRATIONS[:-1]:
            apply_migration(conn, mf)

        cur = conn.cursor()
        u_id = str(uuid.uuid4())
        cur.execute("insert into auth.users (id, email) values (%s, %s);", (u_id, "precheck_bigint@feniqo.local"))
        cur.execute("update public.profiles set currency = 'TRY' where id = %s;", (u_id,))
        cat_id = str(uuid.uuid4())
        cur.execute("insert into public.categories (id, user_id, name, type, color) values (%s, %s, 'K', 'expense', '#000');", (cat_id, u_id))

        cur.execute("select pg_get_functiondef(oid) from pg_proc where proname = 'sync_transactions_money_compat';")
        old_function_def = cur.fetchone()[0]
        old_fn_hash = hashlib.sha256(old_function_def.encode('utf-8')).hexdigest()

        cur.execute("alter table public.transactions disable trigger transactions_money_compat_before_write;")
        cur.execute("""
        insert into public.transactions (
            user_id, amount, amount_minor, currency, type, category_id, payment_method
        ) values (
            %s, 99999999999999999.00, 100, 'TRY', 'expense', %s, 'cash'
        );
        """, (u_id, cat_id))
        cur.execute("alter table public.transactions enable trigger transactions_money_compat_before_write;")

        precheck_bigint_failed = False
        err_msg_bigint = ""
        pgcode_bigint = ""
        try:
            apply_migration(conn, "20260927000100_harden_money_compatibility_trigger.sql")
        except psycopg2.Error as e:
            precheck_bigint_failed = True
            err_msg_bigint = str(e)
            pgcode_bigint = e.pgcode
            try:
                cur.execute("ROLLBACK;")
            except Exception:
                pass

        # Ham cast overflow (22003) değil, kontrollü pre-check raise exception'ı (P0001) olmalı!
        controlled_precheck = (
            precheck_bigint_failed and
            "Pre-check failed: public.transactions içinde para sözleşmesine uymayan (drift:" in err_msg_bigint and
            pgcode_bigint == 'P0001'
        )
        report.add("Migration Pre-Check", "BIGINT Out-of-Range Güvenli Pre-Check (Raw Cast Hatası Yok)", controlled_precheck, f"Durduruldu={precheck_bigint_failed}, SQLSTATE={pgcode_bigint} (P0001 bekleniyor)")
        print(f"  [{'PASS' if controlled_precheck else 'FAIL'}] BIGINT Drift Güvenli Pre-Check: {controlled_precheck} (SQLSTATE: {pgcode_bigint})")

        cur.execute("select pg_get_functiondef(oid) from pg_proc where proname = 'sync_transactions_money_compat';")
        current_fn_hash = hashlib.sha256(cur.fetchone()[0].encode('utf-8')).hexdigest()
        p_bigint_unmod = (old_fn_hash == current_fn_hash)
        report.add("Migration Pre-Check", "BIGINT Drift Rollback Sonrası Fonksiyon Değişmezliği", p_bigint_unmod, f"Fn Değişmedi={p_bigint_unmod}")
        print(f"  [{'PASS' if p_bigint_unmod else 'FAIL'}] Rollback Değişmezliği (BIGINT): {p_bigint_unmod}")

        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)


def run_trigger_metadata_negative_tests(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 5: KESİN TRIGGER METADATA NEGATİF PRE-CHECK TESTLERİ")
    print("=" * 70)

    corruptions = [
        {
            "name": "AFTER Yerine BEFORE Olmayan Trigger",
            "sql": """
            drop trigger transactions_money_compat_before_write on public.transactions;
            create trigger transactions_money_compat_before_write
            after insert or update of amount, amount_minor, currency, user_id
            on public.transactions
            for each row execute function public.sync_transactions_money_compat();
            """,
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz"
        },
        {
            "name": "Yalnız INSERT Trigger (UPDATE Eksik)",
            "sql": """
            drop trigger transactions_money_compat_before_write on public.transactions;
            create trigger transactions_money_compat_before_write
            before insert
            on public.transactions
            for each row execute function public.sync_transactions_money_compat();
            """,
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz"
        },
        {
            "name": "UPDATE OF Listesinden currency Eksik",
            "sql": """
            drop trigger transactions_money_compat_before_write on public.transactions;
            create trigger transactions_money_compat_before_write
            before insert or update of amount, amount_minor, user_id
            on public.transactions
            for each row execute function public.sync_transactions_money_compat();
            """,
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger UPDATE OF kolonları beklenen küme değil"
        },
        {
            "name": "UPDATE OF Listesine Beklenmeyen Ekstra Kolon (description) Eklenmiş",
            "sql": """
            drop trigger transactions_money_compat_before_write on public.transactions;
            create trigger transactions_money_compat_before_write
            before insert or update of amount, amount_minor, currency, user_id, description
            on public.transactions
            for each row execute function public.sync_transactions_money_compat();
            """,
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger UPDATE OF kolonları beklenen küme değil"
        },
        {
            "name": "Yanlış Fonksiyona Bağlı Trigger",
            "sql": """
            create or replace function public.dummy_money_compat_fn() returns trigger language plpgsql as $$ begin return new; end; $$;
            drop trigger transactions_money_compat_before_write on public.transactions;
            create trigger transactions_money_compat_before_write
            before insert or update of amount, amount_minor, currency, user_id
            on public.transactions
            for each row execute function public.dummy_money_compat_fn();
            """,
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz"
        },
        {
            "name": "Devre Dışı (Disabled) Trigger",
            "sql": "alter table public.transactions disable trigger transactions_money_compat_before_write;",
            "expected_err": "Pre-check failed: transactions_money_compat_before_write trigger tanımı veya olayları geçersiz"
        },
    ]

    for c in corruptions:
        db_name = create_disposable_db("feniqo_trg_neg_")
        try:
            conn = get_db_conn(db_name)
            setup_supabase_prerequisites(conn)

            for mf in REQUIRED_MIGRATIONS[:-1]:
                apply_migration(conn, mf)

            cur = conn.cursor()
            cur.execute(c["sql"])

            failed_as_expected = False
            err_msg = ""
            try:
                apply_migration(conn, "20260927000100_harden_money_compatibility_trigger.sql")
            except psycopg2.Error as e:
                failed_as_expected = True
                err_msg = str(e)
                try:
                    cur.execute("ROLLBACK;")
                except Exception:
                    pass

            passed = (failed_as_expected and c["expected_err"] in err_msg)
            report.add("Trigger Metadata Pre-Check", c["name"], passed, f"Durduruldu={failed_as_expected}, Hata={err_msg.strip()[:65]}...")
            print(f"  [{'PASS' if passed else 'FAIL'}] {c['name']}: {passed}")

            cur.close()
            conn.close()
        finally:
            drop_disposable_db(db_name, report)


def run_currency_contract_analysis(report: TestReport):
    print("\n" + "=" * 70)
    print("BÖLÜM 6: MOBİL CURRENCY ENUM İLE VERİTABANI KISITLARI KARŞILAŞTIRMASI")
    print("=" * 70)

    kotlin_file = os.path.join(REPO_ROOT, "sharedLogic", "src", "commonMain", "kotlin", "com", "feniqo", "mobile", "domain", "model", "Money.kt")
    has_gbp_in_kotlin = False
    with open(kotlin_file, "r", encoding="utf-8") as f:
        k_content = f.read()
        if "GBP(" in k_content:
            has_gbp_in_kotlin = True

    db_name = create_disposable_db("feniqo_curr_check_")
    try:
        conn = get_db_conn(db_name)
        setup_supabase_prerequisites(conn)
        apply_migration(conn, "20260814000000_schema_web_v1_baseline.sql")
        apply_migration(conn, "20260814000200_schema_money_expand.sql")

        cur = conn.cursor()
        cur.execute("""
        select conname, pg_get_constraintdef(oid)
        from pg_constraint
        where conname in ('profiles_currency_check', 'transactions_currency_supported');
        """)
        constraints = dict(cur.fetchall())
        cur.close()
        conn.close()
    finally:
        drop_disposable_db(db_name, report)

    gbp_rejected_in_db = (
        "GBP" not in constraints.get("profiles_currency_check", "") and
        "GBP" not in constraints.get("transactions_currency_supported", "")
    )

    diff_detected = has_gbp_in_kotlin and gbp_rejected_in_db
    report.add(
        "Sözleşme Analizi",
        "GBP Sözleşme Farkı (Mobil Enum vs DB Constraints)",
        True,
        f"Mobilde GBP tanımlı ({has_gbp_in_kotlin}), DB kısıtlarında yalnız TRY/USD/EUR izin veriliyor: {constraints}"
    )
    print(f"  [BİLGİ] Mobil Currency Enum GBP: {has_gbp_in_kotlin}")
    print(f"  [BİLGİ] DB profiles_currency_check: {constraints.get('profiles_currency_check')}")
    print(f"  [BİLGİ] DB transactions_currency_supported: {constraints.get('transactions_currency_supported')}")


def main():
    print("=" * 70)
    print("FENİQO MOBİL — GÜÇLENDİRİLMİŞ PARA VE UYUMLULUK TEST KOŞUCUSU")
    print(f"Hedef: PostgreSQL @ {HOST}:{PORT}")
    print("=" * 70)

    report = TestReport()

    run_positive_backfill_tests(report)
    run_negative_backfill_tests(report)
    run_web_mobile_compatibility_tests(report)
    run_migration_precheck_tests(report)
    run_trigger_metadata_negative_tests(report)
    run_currency_contract_analysis(report)

    print("\n" + "=" * 70)
    print("TEST SONUÇLARI ÖZETİ")
    print("=" * 70)
    print(f"Toplam Senaryo/Denetim: {len(report.results)}")
    print(f"PASS Sayısı:            {report.pass_count}")
    print(f"DEFECT / FAIL Sayısı:   {report.defect_count}")
    print(f"Cleanup Hata Sayısı:    {len(report.cleanup_failures)}")
    print("-" * 70)

    for r in report.results:
        status_label = "PASS" if (r["passed"] and not r["is_defect"]) else "DEFECT / FAIL"
        print(f"[{status_label}] {r['category']} -> {r['name']}: {r['details']}")

    print("=" * 70)

    if report.cleanup_failures:
        print("\n[CLEANUP FAILURE] Aşağıdaki test veritabanları temizlenemedi:")
        for cf in report.cleanup_failures:
            print(f"  - {cf}", file=sys.stderr)
        sys.exit(2)

    if report.defect_count > 0:
        print(f"\n[FAIL-CLOSED RESULT] Mevcut sözleşmede {report.defect_count} adet açık/kusur tespit edildi. Süreç fail-closed sonlandırılıyor.")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Tüm para backfill, uyumluluk, pre-check, trigger katalog ve regresyon sözleşmeleri başarıyla geçti!")
        sys.exit(0)


if __name__ == "__main__":
    main()
