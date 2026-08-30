param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$manifestPath = Join-Path $ReleaseRoot "RELEASE-MANIFEST.json"
if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Release manifest was not found: $manifestPath" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$failures = New-Object System.Collections.Generic.List[string]

foreach ($entry in $manifest.files) {
    $relative = ([string]$entry.path).Replace('/', '\')
    if ($relative.Contains('..') -or [System.IO.Path]::IsPathRooted($relative)) {
        $failures.Add("Unsafe manifest path: $relative")
        continue
    }
    $filePath = [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $relative))
    $releasePrefix = ([System.IO.Path]::GetFullPath($ReleaseRoot)).TrimEnd('\') + '\'
    if (-not $filePath.StartsWith($releasePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        $failures.Add("Manifest path escapes release root: $relative")
        continue
    }
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        $failures.Add("Missing release file: $relative")
        continue
    }
    $actual = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne ([string]$entry.sha256).ToLowerInvariant()) {
        $failures.Add("Checksum mismatch: $relative")
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "FAIL $_" -ForegroundColor Red }
    throw "Release verification failed with $($failures.Count) issue(s)."
}

Write-Host "Release verified: version=$($manifest.version), commit=$($manifest.gitCommit), files=$($manifest.files.Count)" -ForegroundColor Green
