Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ruleName = "Gongdan Internal Demo 8088"
if (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue) {
    Write-Host "Firewall rule already exists: $ruleName" -ForegroundColor Yellow
    exit 0
}

New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8088 -Profile Private | Out-Null
Write-Host "Opened TCP 8088 for the Windows Private network profile." -ForegroundColor Green
