$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$legacyLimits = @{
    "sharedLogic/src/commonMain/kotlin/com/feniqo/mobile/data/local/dao/LocalMutationDao.kt" = 3889
    "sharedUI/src/commonMain/kotlin/com/feniqo/mobile/presentation/screen/SubscriptionFormScreen.kt" = 2129
    "androidApp/src/main/kotlin/com/feniqo/mobile/navigation/FeniqoNavigation.kt" = 1718
    "sharedLogic/src/commonMain/kotlin/com/feniqo/mobile/data/local/dao/RemoteSyncDao.kt" = 1569
}
$newFileLimit = 2200
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($entry in $legacyLimits.GetEnumerator()) {
    $absolutePath = Join-Path $repositoryRoot $entry.Key
    if (-not (Test-Path -LiteralPath $absolutePath)) {
        $failures.Add("Boyut ratchet dosyası bulunamadı: $($entry.Key)")
        continue
    }
    $lineCount = (Get-Content -LiteralPath $absolutePath).Count
    if ($lineCount -gt $entry.Value) {
        $failures.Add("$($entry.Key): $lineCount satır (izin verilen eski üst sınır: $($entry.Value))")
    }
}

$sourceRoots = @(
    "androidApp/src/main",
    "sharedLogic/src/commonMain",
    "sharedLogic/src/androidMain",
    "sharedUI/src/commonMain"
)
foreach ($sourceRoot in $sourceRoots) {
    Get-ChildItem -LiteralPath (Join-Path $repositoryRoot $sourceRoot) -Recurse -Filter "*.kt" | ForEach-Object {
        $relativePath = $_.FullName.Substring($repositoryRoot.Length + 1).Replace('\', '/')
        if ($legacyLimits.ContainsKey($relativePath)) { return }
        $lineCount = (Get-Content -LiteralPath $_.FullName).Count
        if ($lineCount -gt $newFileLimit) {
            $failures.Add("${relativePath}: $lineCount satır (yeni dosya üst sınırı: $newFileLimit)")
        }
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Kaynak boyutu ratchet denetimi başarılı."
