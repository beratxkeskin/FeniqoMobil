param(
    [string]$BaseRef = $env:LOCALIZATION_DIFF_BASE,
    [string]$HeadRef = $(if ($env:LOCALIZATION_DIFF_HEAD) { $env:LOCALIZATION_DIFF_HEAD } else { 'HEAD' }),
    [switch]$SkipDiffScan
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$catalogPairs = @(
    @{
        Scope = 'Compose'
        tr = 'sharedUI/src/commonMain/composeResources/values/strings.xml'
        en = 'sharedUI/src/commonMain/composeResources/values-en/strings.xml'
    },
    @{
        Scope = 'Android Platform'
        tr = 'androidApp/src/main/res/values/strings.xml'
        en = 'androidApp/src/main/res/values-en/strings.xml'
    }
)
$failures = [System.Collections.Generic.List[string]]::new()

function Get-PlaceholderSignature {
    param([string]$Text)

    $formatText = $Text.Replace('%%', '')
    $matches = [regex]::Matches(
        $formatText,
        '%(?:(?<position>\d+)\$[-#+ 0,(<]*\d*(?:\.\d+)?(?<type>[A-Za-z])|(?<implicitType>[sSdDfF]))'
    )
    $ordinal = 0
    $allExplicit = $true
    $signature = foreach ($match in $matches) {
        $ordinal++
        $position = if ($match.Groups['position'].Success) {
            $match.Groups['position'].Value
        } else {
            $allExplicit = $false
            $ordinal.ToString()
        }
        $type = if ($match.Groups['type'].Success) {
            $match.Groups['type'].Value
        } else {
            $match.Groups['implicitType'].Value
        }
        "${position}:${type}"
    }
    if ($allExplicit) {
        $signature = @($signature | Sort-Object)
    }
    return ($signature -join ',')
}

function Read-LocalizationCatalog {
    param(
        [string]$Locale,
        [string]$RelativePath
    )

    $absolutePath = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $absolutePath)) {
        $failures.Add("${RelativePath}: localization kataloğu bulunamadı")
        return @{}
    }

    try {
        [xml]$document = Get-Content -LiteralPath $absolutePath -Raw -Encoding UTF8
    } catch {
        $failures.Add("${RelativePath}: geçerli XML olarak okunamadı ($($_.Exception.Message))")
        return @{}
    }

    $entries = @{}
    $nameKinds = @{}
    foreach ($node in $document.resources.ChildNodes) {
        if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        if ($node.LocalName -notin @('string', 'plurals')) { continue }

        $name = $node.GetAttribute('name')
        if ([string]::IsNullOrWhiteSpace($name)) {
            $failures.Add("${RelativePath}: adsız $($node.LocalName) kaynağı")
            continue
        }

        $entryKey = "$($node.LocalName):${name}"
        if ($entries.ContainsKey($entryKey)) {
            $failures.Add("${RelativePath}: yinelenen kaynak $entryKey")
            continue
        }
        if ($nameKinds.ContainsKey($name) -and $nameKinds[$name] -ne $node.LocalName) {
            $failures.Add("${RelativePath}: ${name} hem $($nameKinds[$name]) hem $($node.LocalName) olarak tanımlı")
            continue
        }
        $nameKinds[$name] = $node.LocalName

        $variants = @{}
        if ($node.LocalName -eq 'string') {
            $variants['default'] = Get-PlaceholderSignature $node.InnerText
        } else {
            foreach ($item in $node.ChildNodes) {
                if ($item.NodeType -ne [System.Xml.XmlNodeType]::Element -or $item.LocalName -ne 'item') {
                    continue
                }
                $quantity = $item.GetAttribute('quantity')
                if ([string]::IsNullOrWhiteSpace($quantity)) {
                    $failures.Add("${RelativePath}: plurals:${name} içinde quantity eksik")
                    continue
                }
                if ($variants.ContainsKey($quantity)) {
                    $failures.Add("${RelativePath}: plurals:${name} içinde yinelenen quantity=${quantity}")
                    continue
                }
                $variants[$quantity] = Get-PlaceholderSignature $item.InnerText
            }
        }

        $entries[$entryKey] = [PSCustomObject]@{
            Locale = $Locale
            Path = $RelativePath
            Name = $name
            Kind = $node.LocalName
            Variants = $variants
        }
    }
    return $entries
}

$totalTrCount = 0
$totalEnCount = 0

foreach ($pair in $catalogPairs) {
    $trEntries = Read-LocalizationCatalog 'tr' $pair.tr
    $enEntries = Read-LocalizationCatalog 'en' $pair.en
    $totalTrCount += $trEntries.Count
    $totalEnCount += $enEntries.Count

    $allEntryKeys = @($trEntries.Keys + $enEntries.Keys | Sort-Object -Unique)
    foreach ($entryKey in $allEntryKeys) {
        if (-not $trEntries.ContainsKey($entryKey)) {
            $failures.Add("$($pair.Scope) TR kataloğunda eksik kaynak: $entryKey")
            continue
        }
        if (-not $enEntries.ContainsKey($entryKey)) {
            $failures.Add("$($pair.Scope) EN kataloğunda eksik kaynak: $entryKey")
            continue
        }

        $trVariants = $trEntries[$entryKey].Variants
        $enVariants = $enEntries[$entryKey].Variants
        $variantKeys = @($trVariants.Keys + $enVariants.Keys | Sort-Object -Unique)
        foreach ($variantKey in $variantKeys) {
            if (-not $trVariants.ContainsKey($variantKey)) {
                $failures.Add("$($pair.Scope) TR kataloğunda eksik varyant: ${entryKey}[${variantKey}]")
                continue
            }
            if (-not $enVariants.ContainsKey($variantKey)) {
                $failures.Add("$($pair.Scope) EN kataloğunda eksik varyant: ${entryKey}[${variantKey}]")
                continue
            }
            if ($trVariants[$variantKey] -cne $enVariants[$variantKey]) {
                $failures.Add(
                    "$($pair.Scope) placeholder uyumsuzluğu ${entryKey}[${variantKey}]: " +
                    "TR='$($trVariants[$variantKey])', EN='$($enVariants[$variantKey])'"
                )
            }
        }
    }
}

function Test-GitRef {
    param([string]$Ref)

    if ([string]::IsNullOrWhiteSpace($Ref) -or $Ref -match '^0+$') { return $false }
    & git -C $repositoryRoot rev-parse --verify --quiet "${Ref}^{commit}" *> $null
    return $LASTEXITCODE -eq 0
}

function Get-AddedKotlinText {
    $sourceRoots = @(
        'androidApp/src/main',
        'sharedLogic/src/commonMain',
        'sharedLogic/src/androidMain',
        'sharedUI/src/commonMain',
        'iosApp'
    )
    $diffArgs = @('-C', $repositoryRoot, 'diff', '--unified=0', '--no-ext-diff')
    if (Test-GitRef $BaseRef) {
        $diffArgs += "${BaseRef}...${HeadRef}"
    } else {
        $workingDiff = & git -C $repositoryRoot status --porcelain --untracked-files=no
        if ($workingDiff) {
            $diffArgs += 'HEAD'
        } elseif (Test-GitRef 'HEAD^') {
            $diffArgs += @('HEAD^', 'HEAD')
        } else {
            return @{}
        }
    }
    $diffArgs += @('--') + $sourceRoots
    $diff = & git @diffArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'Localization hardcode denetimi için Git diff alınamadı.'
    }

    $addedByPath = @{}
    $currentPath = $null
    foreach ($line in $diff) {
        if ($line -match '^\+\+\+ b/(.+)$') {
            $currentPath = $Matches[1]
            if (-not $addedByPath.ContainsKey($currentPath)) {
                $addedByPath[$currentPath] = [System.Text.StringBuilder]::new()
            }
            continue
        }
        if ($currentPath -and $line.StartsWith('+') -and -not $line.StartsWith('+++')) {
            [void]$addedByPath[$currentPath].AppendLine($line.Substring(1))
        }
    }

    $untracked = @(& git -C $repositoryRoot ls-files --others --exclude-standard -- $sourceRoots)
    foreach ($relativePath in $untracked) {
        if (-not $relativePath.EndsWith('.kt') -and -not $relativePath.EndsWith('.swift')) { continue }
        $absolutePath = Join-Path $repositoryRoot $relativePath
        $addedByPath[$relativePath] = [System.Text.StringBuilder]::new()
        [void]$addedByPath[$relativePath].Append((Get-Content -LiteralPath $absolutePath -Raw -Encoding UTF8))
    }
    return $addedByPath
}

function Test-UserFacingLiteral {
    param([string]$Literal)

    $trimmed = $Literal.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { return $false }
    if ($trimmed -in @('Feniqo', 'UTF-8')) { return $false }
    if ($trimmed -match '^(?:https?://|[a-z]+/[a-z0-9.+-]+$|[A-Z0-9_./:-]+$|[a-z0-9_.-]+\.[a-z0-9_.-]+$)') {
        return $false
    }
    if ($trimmed -match '^[yMdHhmsSEZaXx:/.,#0%+\- ]+$') { return $false }
    if ($trimmed -match '^(?:file://|android\.resource://)') { return $false }
    if ($trimmed -match '(?:\$\{[^}]+\}|\$[a-zA-Z_]\w*)') {
        if ($trimmed -notmatch '^[\w.-]+:\s*\$' -and $trimmed -notmatch '^/|^\w+://') {
            return $true
        }
    }
    return $trimmed -match '[\p{L}]' -or $trimmed -match '[A-Za-z]'
}

if (-not $SkipDiffScan) {
    $hardcodePatterns = @(
        @{ Label = 'Text literal'; Pattern = '\bText\s*\(\s*(?:text\s*=\s*)?"(?<literal>[^"\r\n]+)"' },
        @{ Label = 'Text multiline parameter'; Pattern = '\bText\s*\([^)]*?\btext\s*=\s*"(?<literal>[^"\r\n]+)"' },
        @{ Label = 'Text parameter interpolation'; Pattern = '(?m)^\s*text\s*=\s*"(?<literal>[^"\r\n]*(?:\$\{[^}]+\}|\$[a-zA-Z_]\w*)[^"\r\n]*)"' },
        @{ Label = 'contentDescription literal'; Pattern = '\bcontentDescription\s*=\s*"(?<literal>[^"\r\n]+)"' },
        @{ Label = 'Snackbar literal'; Pattern = '\bshowSnackbar\s*\(\s*(?:message\s*=\s*)?"(?<literal>[^"\r\n]+)"' },
        @{ Label = 'Toast literal'; Pattern = '\bmakeText\s*\([^\)]*?"(?<literal>[^"\r\n]+)"' }
    )
    $addedKotlinText = Get-AddedKotlinText
    foreach ($relativePath in $addedKotlinText.Keys) {
        if ($relativePath -notmatch '\.(?:kt|swift)$') { continue }
        $content = $addedKotlinText[$relativePath].ToString()
        $content = [regex]::Replace($content, '(?s)/\*.*?\*/', '')
        $content = [regex]::Replace($content, '(?m)^\s*//.*$', '')
        foreach ($hardcodePattern in $hardcodePatterns) {
            foreach ($match in [regex]::Matches($content, $hardcodePattern.Pattern)) {
                $literal = $match.Groups['literal'].Value
                if (Test-UserFacingLiteral $literal) {
                    $failures.Add("${relativePath}: yeni $($hardcodePattern.Label): '$literal'")
                }
            }
        }

        if ($relativePath -match '(?:ViewModel|/domain/|\\domain\\)') {
            $readyTextPattern = '\b(?:error|message|title|subtitle|description|label|statusText)\w*\s*=\s*"(?<literal>[^"\r\n]+)"'
            foreach ($match in [regex]::Matches($content, $readyTextPattern)) {
                $literal = $match.Groups['literal'].Value
                if ((Test-UserFacingLiteral $literal) -and ($literal -match '\s|[^\x00-\x7F]')) {
                    $failures.Add("${relativePath}: ViewModel/domain katmanında hazır kullanıcı metni: '$literal'")
                }
            }
        }
    }
}

if ($failures.Count -gt 0) {
    [Console]::Error.WriteLine('Localization politika kontrolü başarısız:')
    foreach ($failure in $failures) {
        [Console]::Error.WriteLine("- $failure")
    }
    exit 1
}

Write-Output (
    "Localization politika kontrolü geçti: " +
    "Toplam $totalTrCount TR ve $totalEnCount EN kaynak eş, placeholder sözleşmeleri uyumlu."
)
