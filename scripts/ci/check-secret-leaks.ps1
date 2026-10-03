$ErrorActionPreference = 'Stop'

$forbiddenPathNames = @(
    'local.properties',
    '.env'
)
$forbiddenSuffixes = @(
    '.jks',
    '.keystore',
    '.p12',
    '.pfx'
)
$secretPatterns = @(
    @{ Label = 'Supabase secret key'; Pattern = 'sb_secret_[A-Za-z0-9_-]{16,}' },
    @{ Label = 'JWT'; Pattern = 'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}' },
    @{ Label = 'private key'; Pattern = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' },
    @{ Label = 'AWS access key'; Pattern = '(?:AKIA|ASIA)[A-Z0-9]{16}' },
    @{ Label = 'GitHub token'; Pattern = '(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}' },
    @{ Label = 'Google API key'; Pattern = 'AIza[0-9A-Za-z_-]{30,}' }
)

# Bu değer yalnız secret-prefix reddini sınayan, tamamen sayısal ve imzasız test fixture'ıdır.
$allowedFixtures = @{
    'sharedLogic/src/commonTest/kotlin/com/feniqo/mobile/data/remote/supabase/SupabaseConnectionConfigTest.kt' = @(
        ('sb_' + 'secret_' + '12345678901234567890')
    )
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$failures = [System.Collections.Generic.List[string]]::new()
$trackedPaths = @(& git -C $repositoryRoot ls-files)

if ($LASTEXITCODE -ne 0) {
    throw 'Tracked dosya listesi Git üzerinden okunamadı.'
}

foreach ($relativePath in $trackedPaths) {
    $normalizedPath = $relativePath.Replace('\', '/')
    $name = [IO.Path]::GetFileName($normalizedPath).ToLowerInvariant()
    $suffix = [IO.Path]::GetExtension($normalizedPath).ToLowerInvariant()
    $pathParts = $normalizedPath.ToLowerInvariant().Split('/')
    $isForbiddenPath =
        $forbiddenPathNames.Contains($name) -or
        ($name.StartsWith('.env.') -and $name -ne '.env.example') -or
        $forbiddenSuffixes.Contains($suffix) -or
        ($pathParts.Contains('supabase') -and $pathParts.Contains('.temp'))

    if ($isForbiddenPath) {
        $failures.Add("${normalizedPath}: local-only veya anahtar deposu dosyası Git tarafından izleniyor")
        continue
    }

    $absolutePath = Join-Path $repositoryRoot $relativePath
    try {
        $bytes = [IO.File]::ReadAllBytes($absolutePath)
    } catch {
        $failures.Add("${normalizedPath}: dosya okunamadı ($($_.Exception.GetType().Name))")
        continue
    }

    $probeLength = [Math]::Min(8192, $bytes.Length)
    if ($probeLength -gt 0 -and [Array]::IndexOf($bytes, [byte]0, 0, $probeLength) -ge 0) {
        continue
    }

    $content = [Text.Encoding]::UTF8.GetString($bytes)
    if ($allowedFixtures.ContainsKey($normalizedPath)) {
        foreach ($fixture in $allowedFixtures[$normalizedPath]) {
            $content = $content.Replace($fixture, '')
        }
    }

    if ($normalizedPath.StartsWith('.github/workflows/') -and $suffix -in @('.yml', '.yaml')) {
        $actionReferences = [regex]::Matches($content, '(?m)^\s*uses:\s*(?!\./)([^@\s]+)@([^\s#]+)')
        foreach ($actionReference in $actionReferences) {
            $reference = $actionReference.Groups[2].Value
            if ($reference -notmatch '^[0-9a-f]{40}$') {
                $line = ($content.Substring(0, $actionReference.Index).Split("`n")).Count
                $failures.Add("${normalizedPath}:${line}: harici action tam commit SHA ile sabitlenmemiş")
            }
        }
    }

    foreach ($secretPattern in $secretPatterns) {
        $match = [regex]::Match($content, $secretPattern.Pattern)
        if ($match.Success) {
            $line = ($content.Substring(0, $match.Index).Split("`n")).Count
            $failures.Add("${normalizedPath}:${line}: olası $($secretPattern.Label)")
        }
    }
}

if ($failures.Count -gt 0) {
    [Console]::Error.WriteLine('Secret/local-file güvenlik kontrolü başarısız:')
    foreach ($failure in $failures) {
        [Console]::Error.WriteLine("- $failure")
    }
    exit 1
}

Write-Output 'Secret/local-file güvenlik kontrolü geçti.'
