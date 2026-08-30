param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [int]$LogRetentionDays = 0,
    [int]$BackupRetentionDays = 0,
    [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) {
    $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env"
}

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

function Get-ConfigValue([hashtable]$Config, [string]$Name, [string]$Default = "") {
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($null -ne $processValue -and $processValue -ne "") { return $processValue }
    if ($Config.ContainsKey($Name) -and $null -ne $Config[$Name]) { return [string]$Config[$Name] }
    return $Default
}

function Get-AbsolutePath([string]$Path) { return [System.IO.Path]::GetFullPath($Path) }

function Remove-ExpiredFiles([string]$Directory, [string[]]$Patterns, [datetime]$Cutoff, [switch]$Preview) {
    if (-not (Test-Path -LiteralPath $Directory)) { return 0 }
    $removed = 0
    foreach ($pattern in $Patterns) {
        foreach ($file in Get-ChildItem -LiteralPath $Directory -Filter $pattern -File -ErrorAction SilentlyContinue) {
            if ($file.LastWriteTime -lt $Cutoff) {
                if ($Preview) {
                    Write-Host "Would remove: $($file.FullName)" -ForegroundColor DarkYellow
                } else {
                    Remove-Item -LiteralPath $file.FullName -Force
                }
                $removed++
            }
        }
    }
    return $removed
}

$config = Read-DotEnv $EnvFile
$logDays = if ($LogRetentionDays -gt 0) { $LogRetentionDays } else { [int](Get-ConfigValue $config "LOG_RETENTION_DAYS" "30") }
$backupDays = if ($BackupRetentionDays -gt 0) { $BackupRetentionDays } else { [int](Get-ConfigValue $config "BACKUP_RETENTION_DAYS" "30") }
$logDays = [Math]::Max(1, $logDays)
$backupDays = [Math]::Max(1, $backupDays)
$logsDir = Get-AbsolutePath (Join-Path $ReleaseRoot "logs")
$backupDirValue = Get-ConfigValue $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
$backupDir = if ([System.IO.Path]::IsPathRooted($backupDirValue)) { Get-AbsolutePath $backupDirValue } else { Get-AbsolutePath (Join-Path $ReleaseRoot $backupDirValue) }
New-Item -ItemType Directory -Path $logsDir, $backupDir -Force | Out-Null

$logCutoff = (Get-Date).AddDays(-$logDays)
$backupCutoff = (Get-Date).AddDays(-$backupDays)
$logPatterns = @("*.log", "*.log.*", "*.out.log", "*.err.log")
$backupPatterns = @("mysql_*.sql", "mysql_*.sql.sha256", "uploads_*.zip", "uploads_*.zip.sha256", "manifest_*.json")
$removedLogs = Remove-ExpiredFiles $logsDir $logPatterns $logCutoff -Preview:$WhatIf
$removedBackups = Remove-ExpiredFiles $backupDir $backupPatterns $backupCutoff -Preview:$WhatIf

$maintenanceLog = Join-Path $logsDir "maintenance.log"
$summary = "logFiles=$removedLogs, backupFiles=$removedBackups, logRetentionDays=$logDays, backupRetentionDays=$backupDays, whatIf=$WhatIf"
if ($WhatIf) {
    Write-Host "Cleanup preview: $summary" -ForegroundColor Cyan
} else {
    Add-Content -LiteralPath $maintenanceLog -Value ("{0} Log cleanup completed: {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $summary) -Encoding UTF8
    Write-Host "Log cleanup completed: $summary" -ForegroundColor Green
}
