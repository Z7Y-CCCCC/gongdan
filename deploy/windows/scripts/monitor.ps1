param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) { $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env" }

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing config file: $Path" }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function Get-Value([hashtable]$Config, [string]$Name, [string]$Default = "") {
    if ($Config.ContainsKey($Name) -and $null -ne $Config[$Name]) { return [string]$Config[$Name] }
    return $Default
}

function Test-HttpEndpoint([string]$Name, [string]$Url) {
    $started = Get-Date
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 8
        return [pscustomobject]@{
            name = $Name
            status = if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { "UP" } else { "DOWN" }
            detail = "HTTP $($response.StatusCode)"
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
        }
    } catch {
        return [pscustomobject]@{
            name = $Name
            status = "DOWN"
            detail = $_.Exception.Message
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
        }
    }
}

$config = Read-DotEnv $EnvFile
$runtimeDir = Join-Path $ReleaseRoot "runtime"
$logsDir = Join-Path $ReleaseRoot "logs"
New-Item -ItemType Directory -Path $runtimeDir, $logsDir -Force | Out-Null
$checks = New-Object System.Collections.Generic.List[object]

$checks.Add((Test-HttpEndpoint "frontend" (Get-Value $config "MONITOR_FRONTEND_URL" "http://127.0.0.1:8088/")))
$checks.Add((Test-HttpEndpoint "mobile" (Get-Value $config "MONITOR_MOBILE_URL" "http://127.0.0.1:8088/mobile/")))
$checks.Add((Test-HttpEndpoint "backend-readiness" (Get-Value $config "MONITOR_HEALTH_URL" "http://127.0.0.1:8088/health")))

$mysqlServiceName = Get-Value $config "MYSQL_SERVICE_NAME" ""
if ($mysqlServiceName) {
    $mysqlService = Get-Service -Name $mysqlServiceName -ErrorAction SilentlyContinue
    $checks.Add([pscustomobject]@{
        name = "mysql-service"
        status = if ($mysqlService -and $mysqlService.Status -eq "Running") { "UP" } else { "DOWN" }
        detail = if ($mysqlService) { "$($mysqlService.Status) ($mysqlServiceName)" } else { "service not found ($mysqlServiceName)" }
        durationMs = 0
    })
}

$backupDirValue = Get-Value $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
$backupDir = if ([System.IO.Path]::IsPathRooted($backupDirValue)) { [System.IO.Path]::GetFullPath($backupDirValue) } else { [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $backupDirValue)) }
$maxBackupAgeHours = [Math]::Max(1, [int](Get-Value $config "MONITOR_BACKUP_MAX_AGE_HOURS" "30"))
$latestBackup = Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latestBackup) {
    $checks.Add([pscustomobject]@{ name = "database-backup"; status = "DOWN"; detail = "no backup found in $backupDir"; durationMs = 0 })
} else {
    $backupAgeHours = [Math]::Round(((Get-Date) - $latestBackup.LastWriteTime).TotalHours, 2)
    $checksumStatus = "missing"
    $sidecarPath = "$($latestBackup.FullName).sha256"
    if (Test-Path -LiteralPath $sidecarPath) {
        $expectedHash = ((Get-Content -LiteralPath $sidecarPath -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
        $actualHash = (Get-FileHash -LiteralPath $latestBackup.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $checksumStatus = if ($expectedHash -eq $actualHash) { "ok" } else { "failed" }
    }
    $backupUp = $backupAgeHours -le $maxBackupAgeHours -and $checksumStatus -eq "ok"
    $checks.Add([pscustomobject]@{
        name = "database-backup"
        status = if ($backupUp) { "UP" } else { "DOWN" }
        detail = "$($latestBackup.Name); ageHours=$backupAgeHours; checksum=$checksumStatus"
        durationMs = 0
    })
}

$root = [System.IO.Path]::GetPathRoot([System.IO.Path]::GetFullPath($ReleaseRoot))
$minFreeGb = [Math]::Max(1, [int](Get-Value $config "MONITOR_MIN_FREE_GB" "5"))
$driveInfo = New-Object System.IO.DriveInfo($root)
if ($driveInfo.IsReady) {
    # DriveInfo also works for SYSTEM scheduled tasks where PowerShell PSDrive mappings differ.
    $freeGb = [Math]::Round($driveInfo.AvailableFreeSpace / 1073741824, 2)
    $checks.Add([pscustomobject]@{
        name = "disk-space"
        status = if ($freeGb -ge $minFreeGb) { "UP" } else { "DOWN" }
        detail = "drive=$root; freeGb=$freeGb; minimumGb=$minFreeGb"
        durationMs = 0
    })
}

$failedChecks = @($checks | Where-Object { $_.status -ne "UP" })
$result = [pscustomobject]@{
    status = if ($failedChecks.Count -eq 0) { "UP" } else { "DOWN" }
    checkedAt = (Get-Date).ToString("o")
    host = $env:COMPUTERNAME
    checks = $checks
}
$statusPath = Join-Path $runtimeDir "monitor-status.json"
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statusPath -Encoding UTF8

if ($failedChecks.Count -gt 0) {
    $message = "Gongdan monitor DOWN on $($env:COMPUTERNAME): " + (($failedChecks | ForEach-Object { $_.name }) -join ", ")
    Add-Content -LiteralPath (Join-Path $logsDir "maintenance.log") -Value ("{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $message) -Encoding UTF8

    $webhook = Get-Value $config "ALERT_WEBHOOK_URL" ""
    $cooldownMinutes = [Math]::Max(1, [int](Get-Value $config "MONITOR_ALERT_COOLDOWN_MINUTES" "30"))
    $alertStatePath = Join-Path $runtimeDir "monitor-last-alert.json"
    $canAlert = $true
    if (Test-Path -LiteralPath $alertStatePath) {
        try {
            $lastAlert = Get-Content -LiteralPath $alertStatePath -Raw | ConvertFrom-Json
            $canAlert = ((Get-Date) - [datetime]$lastAlert.sentAt).TotalMinutes -ge $cooldownMinutes
        } catch {
            $canAlert = $true
        }
    }
    if ($webhook -and $canAlert) {
        try {
            Invoke-RestMethod -Uri $webhook -Method Post -ContentType "application/json" -Body (@{ text = $message } | ConvertTo-Json -Compress) -TimeoutSec 8 | Out-Null
            @{ sentAt = (Get-Date).ToString("o"); message = $message } | ConvertTo-Json | Set-Content -LiteralPath $alertStatePath -Encoding UTF8
        } catch {
            Add-Content -LiteralPath (Join-Path $logsDir "maintenance.log") -Value ("{0} Monitor webhook failed: {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $_.Exception.Message) -Encoding UTF8
        }
    }
    Write-Host $message -ForegroundColor Red
    exit 1
}

Write-Host "Gongdan monitor passed. Evidence: $statusPath" -ForegroundColor Green
