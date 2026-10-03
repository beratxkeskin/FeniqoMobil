$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$buildFile = Join-Path $repositoryRoot "androidApp/build.gradle.kts"
$buildText = Get-Content -LiteralPath $buildFile -Raw
$aliases = @(
    "libs.androidx.camera.camera2",
    "libs.androidx.camera.lifecycle",
    "libs.androidx.camera.view",
    "libs.mlkit.text.recognition"
)
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($alias in $aliases) {
    if ($buildText -notmatch "debugImplementation\($([regex]::Escape($alias))\)") {
        $failures.Add("Debug OCR bağımlılığı bulunamadı: $alias")
    }
    if ($buildText -match "(?m)^\s*implementation\($([regex]::Escape($alias))\)") {
        $failures.Add("OCR bağımlılığı release'e sızabilecek implementation kapsamında: $alias")
    }
}

$debugOnlyFiles = @(
    "ocr/MlKitReceiptOcrService.kt",
    "ocr/ReceiptCameraCaptureDialog.kt",
    "ocr/ReceiptOcrService.kt",
    "ocr/ReceiptOcrViewModel.kt",
    "di/OcrModule.kt"
)
foreach ($relativeFile in $debugOnlyFiles) {
    $mainPath = Join-Path $repositoryRoot "androidApp/src/main/kotlin/com/feniqo/mobile/$relativeFile"
    $debugPath = Join-Path $repositoryRoot "androidApp/src/debug/kotlin/com/feniqo/mobile/$relativeFile"
    if (Test-Path -LiteralPath $mainPath) {
        $failures.Add("OCR kaynağı main/release kapsamında kalmış: $relativeFile")
    }
    if (-not (Test-Path -LiteralPath $debugPath)) {
        $failures.Add("Debug OCR kaynağı bulunamadı: $relativeFile")
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Release OCR yüzeyi kapalı: CameraX/ML Kit ve OCR Android kaynakları debug-only."
