param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$envFile = Join-Path $ReleaseRoot "config\gongdan.env"
$port = "18080"
$config = @{}
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $config[$name] = $value
    }
    if ($config.ContainsKey("SERVER_PORT") -and $config["SERVER_PORT"]) { $port = $config["SERVER_PORT"] }
}

$pidFile = Join-Path $ReleaseRoot "runtime\backend.pid"
$backendState = "stopped"
if (Test-Path -LiteralPath $pidFile) {
    $backendPid = [int](Get-Content -LiteralPath $pidFile -Raw)
    if (Get-Process -Id $backendPid -ErrorAction SilentlyContinue) {
        $backendState = "running (PID=$backendPid)"
    }
}

$healthState = "unreachable"
try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health/readiness" -TimeoutSec 3
    $healthState = $health.status
} catch {
}

Write-Host "Backend: $backendState"
Write-Host "Health:  $healthState"

$mysqlState = "not configured"
if ($config.ContainsKey("MYSQL_SERVICE_NAME") -and $config["MYSQL_SERVICE_NAME"]) {
    $mysqlService = Get-Service -Name $config["MYSQL_SERVICE_NAME"] -ErrorAction SilentlyContinue
    $mysqlState = if ($mysqlService) { "$($mysqlService.Status) ($($mysqlService.Name))" } else { "service not found ($($config['MYSQL_SERVICE_NAME']))" }
}
Write-Host "MySQL:   $mysqlState"

$backupDirValue = if ($config.ContainsKey("BACKUP_DIR") -and $config["BACKUP_DIR"]) { $config["BACKUP_DIR"] } else { Join-Path $ReleaseRoot "data\backups" }
$backupDir = if ([System.IO.Path]::IsPathRooted($backupDirValue)) {
    [System.IO.Path]::GetFullPath($backupDirValue)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $backupDirValue))
}
$latestBackup = Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$backupState = "none"
if ($latestBackup) {
    $verification = "checksum missing"
    $sidecarPath = "$($latestBackup.FullName).sha256"
    if (Test-Path -LiteralPath $sidecarPath) {
        $expectedHash = ((Get-Content -LiteralPath $sidecarPath -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
        $actualHash = (Get-FileHash -LiteralPath $latestBackup.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $verification = if ($expectedHash -eq $actualHash) { "checksum OK" } else { "CHECKSUM FAILED" }
    }
    $backupState = "$($latestBackup.Name) at $($latestBackup.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')) ($verification)"
}
Write-Host "Backup:  $backupState"

$startupStatePath = Join-Path $ReleaseRoot "runtime\last-startup.json"
if (Test-Path -LiteralPath $startupStatePath) {
    try {
        $startupState = Get-Content -LiteralPath $startupStatePath -Raw | ConvertFrom-Json
        Write-Host "Recovery: $($startupState.status) at $($startupState.completedAt)"
    } catch {
        Write-Host "Recovery: status file unreadable"
    }
}
Write-Host "Admin:   http://SERVER_IP:8088/"
Write-Host "Mobile:  http://SERVER_IP:8088/mobile/"
