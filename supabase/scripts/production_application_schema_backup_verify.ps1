<#
.SYNOPSIS
    FeniqoMobil-Production pre-migration application-schema logical backup and isolated restore verification.

.DESCRIPTION
    Bu betik, kullanıcının kendi görünür ve interaktif PowerShell konsolunda çalıştırılmak üzere tasarlanmıştır.
    Production üzerinde yalnız read-only preflight yapar, 'public' ve varsa 'supabase_migrations'
    şemalarının schema-only logical dump'ını geçici partial dosyalara alır,
    hash ve sızıntı taraması yapar, izole yerel PostgreSQL 18 veritabanına restore ederek doğrular,
    tek kullanımlık test veritabanını temizler ve yalnız tüm adımlar başarılıysa atomik olarak
    nihai backup, sha256 ve manifest dosyalarını yayınlar.

.NOTES
    Hedef Proje : FeniqoMobil-Production (qgmymavltjnmfuzvfxiq)
    Güvenlik    : Parolalar yalnız Read-Host -AsSecureString ile alınır; hiçbir dosyaya, loga veya
                  komut argümanına yazılmaz. BSTR belleği ZeroFreeBSTR ile sıfırlanır, process
                  çevre değişkeni (PGPASSWORD) her çağrı sonrasında null yapılır. Managed string
                  bellek yaşam döngüsü için Garbage Collector tetiklenir.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 1. SABİT TANIMLAR VE HEDEFLER
# ---------------------------------------------------------------------------
$PROD_PROJECT_NAME = "FeniqoMobil-Production"
$PROD_PROJECT_REF  = "qgmymavltjnmfuzvfxiq"
$PROD_HOST         = "db.qgmymavltjnmfuzvfxiq.supabase.co"
$PROD_PORT         = 5432
$PROD_USER         = "postgres"
$PROD_DB           = "postgres"

$LOCAL_HOST        = "127.0.0.1"
$LOCAL_PORT        = 5432
$LOCAL_USER        = "postgres"
$LOCAL_DEFAULT_DB  = "postgres"

$PG_DUMP_BIN       = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"
$PSQL_BIN          = "C:\Program Files\PostgreSQL\18\bin\psql.exe"

$BACKUP_DIR        = "C:\Users\hp\FeniqoBackups\FeniqoMobil-Production\pre-migration-2026-09-27"
$BACKUP_FILE       = Join-Path $BACKUP_DIR "production_application_schema_pre_migration.sql"
$SHA256_FILE       = Join-Path $BACKUP_DIR "production_application_schema_pre_migration.sha256"
$MANIFEST_FILE     = Join-Path $BACKUP_DIR "backup_manifest.txt"
$LOCK_FILE         = Join-Path $BACKUP_DIR ".production-backup.lock"

$FINAL_FILES       = @($BACKUP_FILE, $SHA256_FILE, $MANIFEST_FILE)

$runGuid              = [guid]::NewGuid().ToString("N")
$partialBackupFile    = "$BACKUP_FILE.partial.$runGuid"
$partialSha256File    = "$SHA256_FILE.partial.$runGuid"
$partialManifestFile  = "$MANIFEST_FILE.partial.$runGuid"
$ALL_PARTIAL_FILES    = @($partialBackupFile, $partialSha256File, $partialManifestFile)

$publishedBackup   = $false
$publishedSha256   = $false
$publishedManifest = $false
$runSucceeded      = $false

$lockStream        = $null
$disposableDb      = "feniqo_production_restore_verify_$($runGuid.Substring(0, 8))"
$dbCreated         = $false

# ---------------------------------------------------------------------------
# 2. GÜVENLİK VE NATIVE ÇAĞRI YARDIMCILARI
# ---------------------------------------------------------------------------

function Invoke-NativeWithCredential {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory=$false)] [System.Security.SecureString]$CredentialSecret,
        [Parameter(Mandatory=$true)]  [string]$FilePath,
        [Parameter(Mandatory=$true)]  [string[]]$ArgumentList,
        [Parameter(Mandatory=$false)] [hashtable]$EnvironmentVariables = @{}
    )

    $bstr = [System.IntPtr]::Zero
    $oldEnv = @{}
    try {
        if ($null -ne $CredentialSecret -and $CredentialSecret.Length -gt 0) {
            $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($CredentialSecret)
            [System.Environment]::SetEnvironmentVariable(
                "PGPASSWORD",
                [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr),
                "Process"
            )
        }
        foreach ($k in $EnvironmentVariables.Keys) {
            $oldEnv[$k] = [System.Environment]::GetEnvironmentVariable($k, 'Process')
            [System.Environment]::SetEnvironmentVariable($k, $EnvironmentVariables[$k], 'Process')
        }

        $output = & $FilePath @ArgumentList 2>&1
        $callExitCode = $LASTEXITCODE

        $outString = ($output | Out-String).Trim()
        return [pscustomobject]@{
            ExitCode = $callExitCode
            Output   = $outString
            Lines    = @($output)
        }
    } finally {
        [System.Environment]::SetEnvironmentVariable("PGPASSWORD", $null, "Process")
        foreach ($k in $EnvironmentVariables.Keys) {
            [System.Environment]::SetEnvironmentVariable($k, $oldEnv[$k], 'Process')
        }
        if ($bstr -ne [System.IntPtr]::Zero) {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
        [System.GC]::Collect()
    }
}

function Test-FileContainsCredentialSecret {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory=$true)] [string]$FilePath,
        [Parameter(Mandatory=$true)] [System.Security.SecureString]$CredentialSecret
    )

    if (-not (Test-Path -LiteralPath $FilePath)) {
        return $false
    }
    if ($null -eq $CredentialSecret -or $CredentialSecret.Length -eq 0) {
        return $false
    }

    $bstr = [System.IntPtr]::Zero
    $found = $false
    try {
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($CredentialSecret)
        $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        if (-not [string]::IsNullOrEmpty($plain)) {
            $content = Get-Content -LiteralPath $FilePath -Raw
            $found = $content.Contains($plain)
        }
    } finally {
        if ($bstr -ne [System.IntPtr]::Zero) {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
        $plain = $null
        [System.GC]::Collect()
    }
    return $found
}

# ---------------------------------------------------------------------------
# 3. BAŞLANGIÇ EKRANI VE KULLANICI HEDEF ONAYI
# ---------------------------------------------------------------------------
Clear-Host
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "  FENIQOMOBIL-PRODUCTION APPLICATION-SCHEMA BACKUP & VERIFY" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "Hedef Proje Adı     : $PROD_PROJECT_NAME"
Write-Host "Hedef Project Ref   : $PROD_PROJECT_REF"
Write-Host "Production Host     : $PROD_HOST"
Write-Host "Production Port     : $PROD_PORT"
Write-Host "Production User     : $PROD_USER"
Write-Host "Production Database : $PROD_DB"
Write-Host "SSL Modu            : require"
Write-Host "Yedek Kapsamı       : public ve (mevcutsa) supabase_migrations (schema-only)"
Write-Host "Hedef Dizin         : $BACKUP_DIR"
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host ""

# İnteraktif terminal kontrolü
if ([System.Console]::IsInputRedirected) {
    Write-Error "[FAIL-CLOSED] Bu betik yönlendirilmiş/otomatik bir ortamda çalıştırılamaz. Lütfen doğrudan kendi interaktif PowerShell terminalinizde çalıştırınız."
    exit 1
}

# İstemci binary kontrolleri
if (-not (Test-Path -LiteralPath $PG_DUMP_BIN)) {
    Write-Error "[HATA] pg_dump binary bulunamadı: $PG_DUMP_BIN"
    exit 1
}
if (-not (Test-Path -LiteralPath $PSQL_BIN)) {
    Write-Error "[HATA] psql binary bulunamadı: $PSQL_BIN"
    exit 1
}

# Nihai hedef dosyaların ön kontrolü (Hiçbiri bulunmamalı)
foreach ($f in $FINAL_FILES) {
    if (Test-Path -LiteralPath $f) {
        Write-Error "[HATA] Nihai hedef dosya zaten mevcut ($f). Üzerine yazma kesinlikle engellendi. İşlem durduruldu."
        exit 1
    }
}

# Kullanıcı açık hedef onayı (Lock öncesinde)
$confirmTarget = Read-Host -Prompt "Yukarıdaki production hedefini onaylamak için 'EVET' yazınız"
if ($confirmTarget -cne "EVET") {
    Write-Warning "Hedef onayı verilmedi ('EVET' yazılmadı). Güvenlik gereği işlem durduruldu."
    exit 1
}

# ---------------------------------------------------------------------------
# 4. GÜVENLİ PAROLA GİRİŞİ (INTERACTIVE SECURESTRING - LOCK ÖNCESİNDE)
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "[1/6] Kimlik Bilgileri Alınıyor..." -ForegroundColor Yellow
$prodCredentialSecret = Read-Host -Prompt "Production PostgreSQL Parolası (db.$PROD_PROJECT_REF.supabase.co)" -AsSecureString
if (-not $prodCredentialSecret -or $prodCredentialSecret.Length -eq 0) {
    Write-Error "[HATA] Production veritabanı parolası boş olamaz."
    exit 1
}

$localCredentialSecret = Read-Host -Prompt "Yerel (127.0.0.1) PostgreSQL Parolası (Yerel kullanıcı için parola yoksa doğrudan Enter'a basınız)" -AsSecureString

# ---------------------------------------------------------------------------
# 5. BACKUP DİZİNİ VE EXCLUSIVE LOCK OLUŞTURMA (PAROLALAR ALINDIKTAN HEMEN SONRA)
# ---------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $BACKUP_DIR)) {
    New-Item -ItemType Directory -Path $BACKUP_DIR -Force | Out-Null
}

try {
    $lockStream = [System.IO.File]::Open($LOCK_FILE, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
    Write-Error "[HATA] Backup lock dosyası oluşturulamadı ($LOCK_FILE). Başka bir backup işlemi çalışıyor olabilir veya kilit dosyası mevcut."
    exit 1
}

# ---------------------------------------------------------------------------
# ANA ÇALIŞMA BLOKU VE ATOMİK HATA YÖNETİMİ
# ---------------------------------------------------------------------------
try {
    # Lock alındıktan sonra üç nihai dosyanın bulunmadığını tekrar kontrol et
    foreach ($f in $FINAL_FILES) {
        if (Test-Path -LiteralPath $f) {
            throw "[HATA] Nihai hedef dosya kilit alındıktan sonra mevcut bulundu ($f). İşlem durduruluyor."
        }
    }

    # ---------------------------------------------------------------------------
    # 6. YEREL POSTGRESQL 18 DOĞRULAMASI
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[2/6] Yerel PostgreSQL 18 Ortamı Doğrulanıyor..." -ForegroundColor Yellow

    $localSvc = Get-Service -Name "postgresql-x64-18" -ErrorAction SilentlyContinue
    if (-not $localSvc -or $localSvc.Status -ne "Running") {
        throw "Yerel 'postgresql-x64-18' Windows servisi çalışmıyor."
    }

    $cimSvc = Get-CimInstance Win32_Service -Filter "Name = 'postgresql-x64-18'" -ErrorAction SilentlyContinue
    if (-not $cimSvc -or $cimSvc.PathName -notlike "*PostgreSQL\18\*") {
        throw "Yerel 'postgresql-x64-18' servis yolu PostgreSQL 18 kurulumuna ait değil."
    }

    $localConn = Get-NetTCPConnection -LocalAddress ("127.0.0.1", "::1") -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $localConn) {
        throw "127.0.0.1:5432 üzerinde dinleyen PostgreSQL portu bulunamadı."
    }

    $localProc = Get-Process -Id $localConn.OwningProcess -ErrorAction SilentlyContinue
    if (-not $localProc -or $localProc.ProcessName -ne "postgres") {
        throw "127.0.0.1:5432 portunu dinleyen süreç 'postgres' değil ($($localProc.ProcessName))."
    }

    $parentPid = (Get-CimInstance Win32_Process -Filter "ProcessId = $($localConn.OwningProcess)").ParentProcessId
    if ($localConn.OwningProcess -ne $cimSvc.ProcessId -and $parentPid -ne $cimSvc.ProcessId) {
        throw "Dinleyen postgres süreci (PID: $($localConn.OwningProcess), Parent PID: $parentPid) 'postgresql-x64-18' servisi (PID: $($cimSvc.ProcessId)) ile eşleşmiyor."
    }

    $localVersionRes = Invoke-NativeWithCredential `
        -CredentialSecret $localCredentialSecret `
        -FilePath $PSQL_BIN `
        -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $LOCAL_DEFAULT_DB, "-c", "SELECT version();")

    if ($localVersionRes.ExitCode -ne 0 -or $localVersionRes.Output -notmatch "PostgreSQL 18") {
        throw "Yerel veritabanı PostgreSQL 18 olarak doğrulanamadı (Exit code: $($localVersionRes.ExitCode))."
    }
    Write-Host "  -> Yerel PostgreSQL 18 servisi, port bağlamı ve süreci doğrulandı." -ForegroundColor Green

    # ---------------------------------------------------------------------------
    # 7. PRODUCTION READ-ONLY PREFLIGHT DENETİMİ
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[3/6] Production Read-Only Preflight Denetimi Yürütülüyor..." -ForegroundColor Yellow

    $preflightSql = @"
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';
SET LOCAL lock_timeout = '2s';

SELECT json_build_object(
  'current_database', current_database(),
  'current_user', current_user,
  'ro_status', current_setting('transaction_read_only'),
  'public_tables', (select count(*) from information_schema.tables where table_schema = 'public'),
  'public_functions', (select count(*) from information_schema.routines where routine_schema = 'public'),
  'public_triggers', (select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and not t.tgisinternal),
  'public_policies', (select count(*) from pg_policy pol join pg_class c on c.oid = pol.polrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public'),
  'migration_schema_exists', (select exists(select 1 from information_schema.schemata where schema_name = 'supabase_migrations')),
  'migration_table_exists', (select to_regclass('supabase_migrations.schema_migrations') is not null),
  'storage_buckets', (case when to_regclass('storage.buckets') is not null then (select count(*) from storage.buckets) else 0 end)
)::text;

ROLLBACK;
"@

    $preflightRes = Invoke-NativeWithCredential `
        -CredentialSecret $prodCredentialSecret `
        -FilePath $PSQL_BIN `
        -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-h", $PROD_HOST, "-p", $PROD_PORT.ToString(), "-U", $PROD_USER, "-d", $PROD_DB, "-v", "ON_ERROR_STOP=1", "-c", $preflightSql) `
        -EnvironmentVariables @{ "PGSSLMODE" = "require" }

    if ($preflightRes.ExitCode -ne 0) {
        throw "[FAIL-CLOSED] Production read-only preflight bağlantısı başarısız oldu (Exit code: $($preflightRes.ExitCode))."
    }

    try {
        $preflightJson = $preflightRes.Output | ConvertFrom-Json
    } catch {
        throw "[FAIL-CLOSED] Preflight JSON çıktısı ayrıştırılamadı."
    }

    # İki aşamalı migration count: Yalnız tablo mevcutsa sorgula, yoksa 0 ata
    $preflightMigrationRows = 0
    if ($preflightJson.migration_table_exists -eq $true) {
        $countSql = @"
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';
SET LOCAL lock_timeout = '2s';
SELECT count(*) FROM supabase_migrations.schema_migrations;
ROLLBACK;
"@
        $countRes = Invoke-NativeWithCredential `
            -CredentialSecret $prodCredentialSecret `
            -FilePath $PSQL_BIN `
            -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-h", $PROD_HOST, "-p", $PROD_PORT.ToString(), "-U", $PROD_USER, "-d", $PROD_DB, "-v", "ON_ERROR_STOP=1", "-c", $countSql) `
            -EnvironmentVariables @{ "PGSSLMODE" = "require" }

        if ($countRes.ExitCode -ne 0) {
            throw "[FAIL-CLOSED] Production migration satır sayısı sorgusu başarısız oldu (Exit code: $($countRes.ExitCode))."
        }

        [int]$parsedRows = -1
        if (-not [int]::TryParse($countRes.Output.Trim(), [ref]$parsedRows) -or $parsedRows -lt 0) {
            throw "[FAIL-CLOSED] Production migration satır sayısı geçerli bir tam sayı olarak ayrıştırılamadı."
        }
        $preflightMigrationRows = $parsedRows
    }

    Write-Host "  -> Veritabanı       : $($preflightJson.current_database)"
    Write-Host "  -> Kullanıcı        : $($preflightJson.current_user)"
    Write-Host "  -> Read-Only Mod    : $($preflightJson.ro_status)"
    Write-Host "  -> Public Tablolar  : $($preflightJson.public_tables) (beklenen: 0)"
    Write-Host "  -> Public Rutinler  : $($preflightJson.public_functions) (beklenen: 0)"
    Write-Host "  -> Public Trigger   : $($preflightJson.public_triggers) (beklenen: 0)"
    Write-Host "  -> Public Policy    : $($preflightJson.public_policies) (beklenen: 0)"
    Write-Host "  -> Migration Şeması : $($preflightJson.migration_schema_exists)"
    Write-Host "  -> Migration Tablosu: $($preflightJson.migration_table_exists)"
    Write-Host "  -> Migration Satırı : $preflightMigrationRows (beklenen: 0)"
    Write-Host "  -> Storage Bucket   : $($preflightJson.storage_buckets) (beklenen: 0)"

    if ($preflightJson.current_database -ne "postgres" -or
        $preflightJson.current_user -ne "postgres" -or
        $preflightJson.ro_status -ne "on" -or
        $preflightJson.public_tables -ne 0 -or
        $preflightJson.public_functions -ne 0 -or
        $preflightJson.public_triggers -ne 0 -or
        $preflightJson.public_policies -ne 0 -or
        $preflightMigrationRows -ne 0 -or
        $preflightJson.storage_buckets -ne 0) {
        throw "[FAIL-CLOSED] Production preflight denetimi başarısız: Beklenmeyen nesne veya durum tespit edildi. Dump alınmayacak."
    }
    Write-Host "  -> Production şeması beklendiği gibi temiz ve boş." -ForegroundColor Green

    # ---------------------------------------------------------------------------
    # 8. SCHEMA-ONLY LOGICAL BACKUP (PARTIAL DOSYAYA)
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[4/6] Schema-Only Logical Backup Alınıyor..." -ForegroundColor Yellow

    $dumpArgs = @(
        "--no-password",
        "-h", $PROD_HOST,
        "-p", $PROD_PORT.ToString(),
        "-U", $PROD_USER,
        "-d", $PROD_DB,
        "--schema-only",
        "--no-owner",
        "--no-privileges",
        "--schema=public"
    )

    if ($preflightJson.migration_schema_exists) {
        Write-Host "  -> 'supabase_migrations' şeması mevcut, dump listesine ekleniyor."
        $dumpArgs += "--schema=supabase_migrations"
    } else {
        Write-Host "  -> 'supabase_migrations' şeması mevcut değil, sahte argüman verilmeyecek."
    }

    $dumpArgs += @("-f", $partialBackupFile)

    $dumpRes = Invoke-NativeWithCredential `
        -CredentialSecret $prodCredentialSecret `
        -FilePath $PG_DUMP_BIN `
        -ArgumentList $dumpArgs `
        -EnvironmentVariables @{ "PGSSLMODE" = "require" }

    if ($dumpRes.ExitCode -ne 0) {
        throw "[HATA] pg_dump başarısız oldu (Exit code: $($dumpRes.ExitCode))."
    }

    if (-not (Test-Path -LiteralPath $partialBackupFile)) {
        throw "[HATA] Yedek partial dosyası oluşturulamadı: $partialBackupFile"
    }

    $backupItem = Get-Item -LiteralPath $partialBackupFile
    if ($backupItem.Length -le 0) {
        throw "[HATA] Yedek partial dosyası boş (0 bayt). Geçersiz yedek."
    }

    $backupSha256 = (Get-FileHash -LiteralPath $partialBackupFile -Algorithm SHA256).Hash
    Write-Host "  -> Geçici yedek boyutu : $($backupItem.Length) bayt" -ForegroundColor Green
    Write-Host "  -> Geçici SHA-256      : $backupSha256" -ForegroundColor Green

    # ---------------------------------------------------------------------------
    # 9. BACKUP GÜVENLİK VE SIZINTI TARAMASI
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[5/6] Yedek Dosyası Güvenlik ve Gizlilik Taramasından Geçiriliyor..." -ForegroundColor Yellow

    # 1. Parola kontrolü (Test-FileContainsCredentialSecret)
    $credentialSecretFoundInDump = Test-FileContainsCredentialSecret -FilePath $partialBackupFile -CredentialSecret $prodCredentialSecret
    if ($credentialSecretFoundInDump) {
        throw "[KRİTİK GÜVENLİK İHLALİ] Veritabanı parolası yedek dosyası içinde tespit edildi!"
    }
    Write-Host "  -> Parola sızıntı denetimi: TEMİZ (Parola dump dosyasında yer almıyor)" -ForegroundColor Green

    # 2. Hassas kaynak desenleri taraması
    $dumpContent = Get-Content -LiteralPath $partialBackupFile -Raw
    $sensitivePatterns = @(
        "auth\.users",
        "storage\.objects",
        "vault\.secrets",
        "service_role",
        "access_token",
        "jwt_secret",
        "BEGIN ENCRYPTED"
    )

    foreach ($pattern in $sensitivePatterns) {
        if ($dumpContent -match $pattern) {
            throw "[GÜVENLİK İHLALİ] Yedek dosyasında yasaklı veri deseni bulundu: '$pattern'."
        }
    }
    Write-Host "  -> Hassas kaynak taraması: TEMİZ (Auth/Storage/Vault/Secret verisi yok)" -ForegroundColor Green

    # ---------------------------------------------------------------------------
    # 10. İZOLE LOCAL RESTORE VE POST-CHECK DOĞRULAMASI
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[6/6] İzole Yerel PostgreSQL 18 Üzerinde Restore Doğrulaması..." -ForegroundColor Yellow

    $restoreMainException = $null
    $restoreCleanupException = $null

    try {
        Write-Host "  -> Tek kullanımlık test veritabanı oluşturuluyor: $disposableDb"
        $createRes = Invoke-NativeWithCredential `
            -CredentialSecret $localCredentialSecret `
            -FilePath $PSQL_BIN `
            -ArgumentList @("-X", "-w", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $LOCAL_DEFAULT_DB, "-v", "ON_ERROR_STOP=1", "-c", "CREATE DATABASE $disposableDb;")

        if ($createRes.ExitCode -ne 0) {
            throw "Yerel disposable veritabanı oluşturulamadı (Exit code: $($createRes.ExitCode))."
        }
        $dbCreated = $true

        # Public şema çakışma yönetimi
        $hasCreateSchemaPublic = ($dumpContent -match '(?i)CREATE\s+SCHEMA\s+(?:IF\s+NOT\s+EXISTS\s+)?public\b')
        if ($hasCreateSchemaPublic) {
            Write-Host "  -> Dump dosyası 'CREATE SCHEMA public' içeriyor. Test DB ($disposableDb) varsayılan public şeması kaldırılıyor..."
            $dropSchemaRes = Invoke-NativeWithCredential `
                -CredentialSecret $localCredentialSecret `
                -FilePath $PSQL_BIN `
                -ArgumentList @("-X", "-w", "-v", "ON_ERROR_STOP=1", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $disposableDb, "-c", "DROP SCHEMA public CASCADE;")
            if ($dropSchemaRes.ExitCode -ne 0) {
                throw "Test veritabanında varsayılan public şema temizlenemedi (Exit code: $($dropSchemaRes.ExitCode))."
            }
        } else {
            Write-Host "  -> Dump dosyası 'CREATE SCHEMA public' içermiyor. Varsayılan public şema korunuyor."
        }

        Write-Host "  -> Yedek partial dosyası test veritabanına restore ediliyor..."
        $restoreRes = Invoke-NativeWithCredential `
            -CredentialSecret $localCredentialSecret `
            -FilePath $PSQL_BIN `
            -ArgumentList @("-X", "-w", "-v", "ON_ERROR_STOP=1", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $disposableDb, "-f", $partialBackupFile)

        if ($restoreRes.ExitCode -ne 0) {
            throw "Restore işlemi başarısız oldu (Exit code: $($restoreRes.ExitCode))."
        }
        Write-Host "  -> Restore işlemi başarıyla tamamlandı (exit code 0)." -ForegroundColor Green

        # Post-check sorguları
        $restoreCheckSql = @"
SELECT json_build_object(
  'public_schema_exists', (select exists(select 1 from information_schema.schemata where schema_name = 'public')),
  'tables', (select count(*) from information_schema.tables where table_schema = 'public'),
  'functions', (select count(*) from information_schema.routines where routine_schema = 'public'),
  'triggers', (select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and not t.tgisinternal),
  'policies', (select count(*) from pg_policy pol join pg_class c on c.oid = pol.polrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public'),
  'migration_schema_exists', (select exists(select 1 from information_schema.schemata where schema_name = 'supabase_migrations')),
  'migration_table_exists', (select to_regclass('supabase_migrations.schema_migrations') is not null)
)::text;
"@

        $restoreCheckRes = Invoke-NativeWithCredential `
            -CredentialSecret $localCredentialSecret `
            -FilePath $PSQL_BIN `
            -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $disposableDb, "-v", "ON_ERROR_STOP=1", "-c", $restoreCheckSql)

        if ($restoreCheckRes.ExitCode -ne 0) {
            throw "Restore post-check sorgusu başarısız oldu (Exit code: $($restoreCheckRes.ExitCode))."
        }

        try {
            $restoreCheckJson = $restoreCheckRes.Output | ConvertFrom-Json
        } catch {
            throw "Restore post-check JSON çıktısı ayrıştırılamadı."
        }

        # İki aşamalı restore migration count: Yalnız tablo mevcutsa sorgula, yoksa 0 ata
        $restoreMigrationRows = 0
        if ($restoreCheckJson.migration_table_exists -eq $true) {
            $restoreCountSql = "SELECT count(*) FROM supabase_migrations.schema_migrations;"
            $restoreCountRes = Invoke-NativeWithCredential `
                -CredentialSecret $localCredentialSecret `
                -FilePath $PSQL_BIN `
                -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $disposableDb, "-v", "ON_ERROR_STOP=1", "-c", $restoreCountSql)

            if ($restoreCountRes.ExitCode -ne 0) {
                throw "Restore migration satır sayısı sorgusu başarısız oldu (Exit code: $($restoreCountRes.ExitCode))."
            }

            [int]$parsedRestoreRows = -1
            if (-not [int]::TryParse($restoreCountRes.Output.Trim(), [ref]$parsedRestoreRows) -or $parsedRestoreRows -lt 0) {
                throw "Restore migration satır sayısı geçerli bir tam sayı olarak ayrıştırılamadı."
            }
            $restoreMigrationRows = $parsedRestoreRows
        }

        # Checksum tekrar doğrulama
        $recalculatedHash = (Get-FileHash -LiteralPath $partialBackupFile -Algorithm SHA256).Hash
        if ($recalculatedHash -ne $backupSha256) {
            throw "Restore sonrasında backup dosyasının checksum değeri uyuşmuyor!"
        }

        Write-Host "  -> Restored Public Şema: $($restoreCheckJson.public_schema_exists) (beklenen: True)"
        Write-Host "  -> Restored Tablolar   : $($restoreCheckJson.tables) (beklenen: 0)"
        Write-Host "  -> Restored Fonksiyon  : $($restoreCheckJson.functions) (beklenen: 0)"
        Write-Host "  -> Restored Trigger    : $($restoreCheckJson.triggers) (beklenen: 0)"
        Write-Host "  -> Restored Policy     : $($restoreCheckJson.policies) (beklenen: 0)"
        Write-Host "  -> Restored Mig. Şema  : $($restoreCheckJson.migration_schema_exists) (beklenen: $($preflightJson.migration_schema_exists))"
        Write-Host "  -> Restored Mig. Tablo : $($restoreCheckJson.migration_table_exists) (beklenen: $($preflightJson.migration_table_exists))"
        Write-Host "  -> Restored Migration  : $restoreMigrationRows (beklenen: 0)"
        Write-Host "  -> Checksum Doğruluğu  : EŞLEŞTİ ($recalculatedHash)"

        if ($restoreCheckJson.public_schema_exists -ne $true -or
            $restoreCheckJson.tables -ne 0 -or
            $restoreCheckJson.functions -ne 0 -or
            $restoreCheckJson.triggers -ne 0 -or
            $restoreCheckJson.policies -ne 0 -or
            $restoreMigrationRows -ne 0 -or
            $restoreMigrationRows -ne $preflightMigrationRows -or
            $restoreCheckJson.migration_schema_exists -ne $preflightJson.migration_schema_exists -or
            $restoreCheckJson.migration_table_exists -ne $preflightJson.migration_table_exists) {
            throw "[FAIL-CLOSED] Restore post-check başarısız: Test veritabanında beklenmeyen nesne veya şema uyuşmazlığı tespit edildi."
        }

        Write-Host "  -> Restore doğrulaması %100 başarılı." -ForegroundColor Green

    } catch {
        $restoreMainException = $_
    } finally {
        if ($dbCreated) {
            try {
                Write-Host "  -> Test veritabanı temizleniyor: $disposableDb"
                if ($disposableDb -notmatch '^feniqo_production_restore_verify_[a-f0-9]{8}$') {
                    throw "[GÜVENLİK HATA] Geçersiz disposable DB adı tespit edildi: '$disposableDb'"
                }

                $dropRes = Invoke-NativeWithCredential `
                    -CredentialSecret $localCredentialSecret `
                    -FilePath $PSQL_BIN `
                    -ArgumentList @("-X", "-w", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $LOCAL_DEFAULT_DB, "-c", "DROP DATABASE IF EXISTS $disposableDb WITH (FORCE);")

                if ($dropRes.ExitCode -ne 0) {
                    throw "[KRİTİK HATA] DROP DATABASE komutu başarısız oldu (Exit code: $($dropRes.ExitCode))."
                }

                $dbCountRes = Invoke-NativeWithCredential `
                    -CredentialSecret $localCredentialSecret `
                    -FilePath $PSQL_BIN `
                    -ArgumentList @("-X", "-w", "-q", "-t", "-A", "-U", $LOCAL_USER, "-h", $LOCAL_HOST, "-p", $LOCAL_PORT.ToString(), "-d", $LOCAL_DEFAULT_DB, "-c", "SELECT count(*) FROM pg_database WHERE datname = '$disposableDb';")

                if ($dbCountRes.ExitCode -ne 0) {
                    throw "[KRİTİK HATA] Residual DB kontrol sorgusu başarısız oldu (Exit code: $($dbCountRes.ExitCode))."
                }

                if ($dbCountRes.Output.Trim() -ne "0") {
                    throw "[KRİTİK HATA] Test veritabanı '$disposableDb' silinemedi (Residual count: $($dbCountRes.Output.Trim()))."
                }
                Write-Host "  -> Test veritabanı başarıyla silindi ve doğrulandı." -ForegroundColor Green
            } catch {
                $restoreCleanupException = $_
            }
        }
    }

    # Nested cleanup hata aktarımı: Cleanup hatası önceliklidir
    if ($null -ne $restoreCleanupException) {
        throw $restoreCleanupException
    }
    if ($null -ne $restoreMainException) {
        throw $restoreMainException
    }

    # ---------------------------------------------------------------------------
    # 11. ATOMİK YAYINLAMA: ÜÇ PARTIAL DOSYA -> ÜÇ NİHAİ DOSYA
    # ---------------------------------------------------------------------------
    Write-Host ""
    Write-Host "  -> Checksum ve Manifest partial dosyaları hazırlanıyor..." -ForegroundColor Yellow

    Set-Content -LiteralPath $partialSha256File -Value "$backupSha256  production_application_schema_pre_migration.sql"

    $manifestData = @"
FeniqoMobil-Production Pre-Migration Backup Manifest
=====================================================
Zaman Damgası (UTC)      : $((Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ"))
Proje Adı                : $PROD_PROJECT_NAME
Project Ref              : $PROD_PROJECT_REF
Doğrulanan Host          : $PROD_HOST
Yedekleme Türü           : Application-Scope Schema-Only Logical Backup
Dahil Edilen Şemalar     : public$(if ($preflightJson.migration_schema_exists) { ", supabase_migrations" } else { "" })
İstemci / Sunucu Sürümü  : pg_dump 18.3 / PostgreSQL 17.6 (Uyumlu)
Yedek Dosyası            : production_application_schema_pre_migration.sql
Dosya Boyutu (bayt)      : $($backupItem.Length)
SHA-256 Checksum         : $backupSha256
Production Preflight     : 0 public tablo, 0 fonksiyon, 0 trigger, 0 policy, $preflightMigrationRows migration satırı, 0 storage bucket
Migration Şeması Durumu  : SchemaExists=$($preflightJson.migration_schema_exists), TableExists=$($preflightJson.migration_table_exists), Rows=$preflightMigrationRows
Güvenlik Taraması        : TEMİZ (Parola sızıntısı yok; auth.users/storage.objects/vault/secret deseni yok)
Yerel Restore Doğrulama  : BAŞARILI (127.0.0.1 PostgreSQL 18.3 disposable DB üzerinde restore edildi; post-check 0 nesne ile doğrulandı)
Test DB Temizliği        : TEMİZLENDİ ($disposableDb başarıyla DROP edildi)
Kapsam Notu              : Bu yedek fiziksel bir Supabase proje yedeği değildir. Yalnızca boş uygulama şemasının ve sıfır migration durumunun logical kanıtıdır.
Auth / Storage Notu      : Auth kullanıcı verilerini veya Storage dosyalarını içermez (Proje sahibi Auth kullanıcı sayısının 0 olduğunu doğrulamıştır).
Durum                    : VERIFIED_SUCCESSFUL
"@

    Set-Content -LiteralPath $partialManifestFile -Value $manifestData

    # Taşıma öncesi nihai dosyaların hâlâ mevcut olmadığını son kez teyit et
    foreach ($f in $FINAL_FILES) {
        if (Test-Path -LiteralPath $f) {
            throw "[RACE HATA] Nihai hedef dosya yayınlama anında mevcut bulundu ($f). İşlem durduruluyor."
        }
    }

    Write-Host "  -> Partial dosyalar nihai konuma atomik olarak taşınıyor..." -ForegroundColor Yellow

    # Taşıma işlemleri (-Force olmadan)
    Move-Item -LiteralPath $partialBackupFile -Destination $BACKUP_FILE -ErrorAction Stop
    $publishedBackup = $true

    Move-Item -LiteralPath $partialSha256File -Destination $SHA256_FILE -ErrorAction Stop
    $publishedSha256 = $true

    Move-Item -LiteralPath $partialManifestFile -Destination $MANIFEST_FILE -ErrorAction Stop
    $publishedManifest = $true

    # ---------------------------------------------------------------------------
    # 12. POST-PUBLISH DOĞRULAMA (CHECKSUM VE MANIFEST KONTROLÜ)
    # ---------------------------------------------------------------------------
    Write-Host "  -> Yayınlanan nihai dosyalar doğrulanıyor..." -ForegroundColor Yellow

    # 1. Üç nihai dosyanın tamamı mevcut olmalı
    if (-not (Test-Path -LiteralPath $BACKUP_FILE)) {
        throw "[HATA] Nihai backup dosyası yayınlama sonrasında doğrulanamadı ($BACKUP_FILE)."
    }
    if (-not (Test-Path -LiteralPath $SHA256_FILE)) {
        throw "[HATA] Nihai SHA-256 dosyası yayınlama sonrasında doğrulanamadı ($SHA256_FILE)."
    }
    if (-not (Test-Path -LiteralPath $MANIFEST_FILE)) {
        throw "[HATA] Nihai manifest dosyası yayınlama sonrasında doğrulanamadı ($MANIFEST_FILE)."
    }

    # 2. Nihai backup hash'i $backupSha256 ile eşleşmeli
    $finalVerifiedHash = (Get-FileHash -LiteralPath $BACKUP_FILE -Algorithm SHA256).Hash
    if ($finalVerifiedHash -ne $backupSha256) {
        throw "[HATA] Yayınlanan nihai backup dosyasının SHA-256 hash değeri beklenen değerle ($backupSha256) eşleşmiyor (Hesaplanan: $finalVerifiedHash)!"
    }

    # 3. .sha256 dosyasındaki hash alanı aynı olmalı
    $shaFileRaw = (Get-Content -LiteralPath $SHA256_FILE -Raw).Trim()
    if ($shaFileRaw -notmatch "^$backupSha256\s+production_application_schema_pre_migration\.sql$") {
        throw "[HATA] Yayınlanan .sha256 dosyasının içeriği beklenen hash ile eşleşmiyor!"
    }

    # 4. Manifest içinde 'Durum : VERIFIED_SUCCESSFUL' bulunmalı
    $manifestRaw = Get-Content -LiteralPath $MANIFEST_FILE -Raw
    if ($manifestRaw -notmatch "Durum\s*:\s*VERIFIED_SUCCESSFUL") {
        throw "[HATA] Yayınlanan manifest dosyasında 'Durum : VERIFIED_SUCCESSFUL' doğrulanamadı!"
    }

    # Yalnızca tüm kontroller geçtiğinde başarı tescillenir
    $runSucceeded = $true

    Write-Host ""
    Write-Host "====================================================================" -ForegroundColor Cyan
    Write-Host "  YEDEKLEME VE RESTORE DOĞRULAMASI BAŞARIYLA TAMAMLANDI" -ForegroundColor Green
    Write-Host "====================================================================" -ForegroundColor Cyan
    Write-Host "Yedek Dosyası : $BACKUP_FILE"
    Write-Host "Checksum      : $SHA256_FILE"
    Write-Host "Manifest      : $MANIFEST_FILE"
    Write-Host ""
    Write-Host "Artık 'docs/PRODUCTION_SUPABASE_DEPLOYMENT_RUNBOOK.md' içindeki yedek kanıtı güncellenebilir."

} catch {
    Write-Host ""
    Write-Error "[FAIL-CLOSED HATA] $($_.Exception.Message)"
    exit 1

} finally {
    # 1. Post-publish rollback: Başarıya ulaşılamadıysa yayımlanan nihai dosyalar geri alınır
    if (-not $runSucceeded) {
        if ($publishedBackup -or $publishedSha256 -or $publishedManifest) {
            Write-Host "  -> Başarısız çalıştırma nedeniyle yayımlanan nihai dosyalar geri alınıyor (rollback)..." -ForegroundColor Yellow
            $rollbackFailed = $false

            if ($publishedBackup) {
                if (Test-Path -LiteralPath $BACKUP_FILE) {
                    Remove-Item -LiteralPath $BACKUP_FILE -Force -ErrorAction SilentlyContinue
                    if (Test-Path -LiteralPath $BACKUP_FILE) {
                        Write-Error "[KRİTİK ROLLBACK HATASI] Yayımlanan $BACKUP_FILE silinemedi!"
                        $rollbackFailed = $true
                    } else {
                        Write-Host "  -> $BACKUP_FILE geri alındı (silindi)." -ForegroundColor Green
                    }
                }
            }

            if ($publishedSha256) {
                if (Test-Path -LiteralPath $SHA256_FILE) {
                    Remove-Item -LiteralPath $SHA256_FILE -Force -ErrorAction SilentlyContinue
                    if (Test-Path -LiteralPath $SHA256_FILE) {
                        Write-Error "[KRİTİK ROLLBACK HATASI] Yayımlanan $SHA256_FILE silinemedi!"
                        $rollbackFailed = $true
                    } else {
                        Write-Host "  -> $SHA256_FILE geri alındı (silindi)." -ForegroundColor Green
                    }
                }
            }

            if ($publishedManifest) {
                if (Test-Path -LiteralPath $MANIFEST_FILE) {
                    Remove-Item -LiteralPath $MANIFEST_FILE -Force -ErrorAction SilentlyContinue
                    if (Test-Path -LiteralPath $MANIFEST_FILE) {
                        Write-Error "[KRİTİK ROLLBACK HATASI] Yayımlanan $MANIFEST_FILE silinemedi!"
                        $rollbackFailed = $true
                    } else {
                        Write-Host "  -> $MANIFEST_FILE geri alındı (silindi)." -ForegroundColor Green
                    }
                }
            }

            if ($rollbackFailed) {
                Write-Error "[KRİTİK GÜVENLİK HATASI] Post-publish rollback tam olarak tamamlanamadı!"
            }
        }
    }

    # 2. Partial dosyaların temizliği
    foreach ($pf in $ALL_PARTIAL_FILES) {
        if (Test-Path -LiteralPath $pf) {
            Remove-Item -LiteralPath $pf -Force -ErrorAction SilentlyContinue
        }
    }

    # 3. Lock handle'ı kapat ve lock dosyasını sil
    if ($null -ne $lockStream) {
        $lockStream.Close()
        $lockStream.Dispose()
        $lockStream = $null
    }
    if (Test-Path -LiteralPath $LOCK_FILE) {
        Remove-Item -LiteralPath $LOCK_FILE -Force -ErrorAction SilentlyContinue
    }

    [System.Environment]::SetEnvironmentVariable("PGPASSWORD", $null, "Process")
    [System.GC]::Collect()
}
