param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$TargetUrl = "http://127.0.0.1:8088",
    [string]$CloudflaredExe = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $CloudflaredExe) {
    $bundledPath = Join-Path $ReleaseRoot "tools\cloudflared.exe"
    if (Test-Path -LiteralPath $bundledPath) {
        $CloudflaredExe = $bundledPath
    } else {
        $CloudflaredExe = (Get-Command cloudflared -ErrorAction Stop).Source
    }
}

Write-Host "Starting temporary HTTPS tunnel for $TargetUrl" -ForegroundColor Cyan
Write-Host "Keep this window open. The trycloudflare.com URL changes after restart." -ForegroundColor Yellow
& $CloudflaredExe tunnel --no-autoupdate --url $TargetUrl
