$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$trackedArtifacts = @(git -C $repositoryRoot ls-files -- "artifacts/**")
$allowedExtensions = @(".png", ".jpg", ".jpeg", ".json", ".md", ".txt", ".xml")
$textExtensions = @(".json", ".md", ".txt", ".xml")
$failures = [System.Collections.Generic.List[string]]::new()
$sensitivePatterns = @(
    '(?i)authorization\s*[:=]\s*bearer\s+',
    '(?i)(access_token|refresh_token|service_role|sb_secret_)\s*[:=]',
    '(?i)(password|passwd|pwd)\s*[:=]\s*["'']?(?!false\b|true\b|null\b)[^\s"''<>]{4,}',
    'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}',
    '(?i)\b[A-Z0-9._%+-]+@(?!example\.(com|org|net)\b)[A-Z0-9.-]+\.[A-Z]{2,}\b'
)

foreach ($relativePath in $trackedArtifacts) {
    $absolutePath = Join-Path $repositoryRoot $relativePath
    $extension = [IO.Path]::GetExtension($absolutePath).ToLowerInvariant()
    if ($extension -notin $allowedExtensions) {
        $failures.Add("İzin verilmeyen artifact türü: $relativePath")
        continue
    }
    $maxBytes = if ($extension -in $textExtensions) { 256KB } else { 2MB }
    if ((Get-Item -LiteralPath $absolutePath).Length -gt $maxBytes) {
        $failures.Add("Artifact boyut sınırını aşıyor: $relativePath")
    }
    if ($extension -in $textExtensions) {
        $content = Get-Content -LiteralPath $absolutePath -Raw
        foreach ($pattern in $sensitivePatterns) {
            if ($content -match $pattern) {
                $failures.Add("Artifact hassas veri kalıbı içeriyor: $relativePath")
                break
            }
        }
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Takip edilen $($trackedArtifacts.Count) artifact politika denetiminden geçti."
