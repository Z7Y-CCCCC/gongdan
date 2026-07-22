param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$BackupPath = "",
    [string]$MysqlExe = "",
    [switch]$KeepDatabase
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) { $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env" }

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing config file: $Path" }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
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

function Find-MySqlClient([string]$ConfiguredPath) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath)) { throw "Configured mysql.exe was not found: $ConfiguredPath" }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.1\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    )) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "mysql.exe was not found. Set BACKUP_MYSQL_EXE to the MySQL bin directory or pass -MysqlExe."
}

$config = Read-DotEnv $EnvFile
$backupDir = Get-ConfigValue $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
if (-not $BackupPath) {
    $latest = Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw "No database backup was found in $backupDir" }
    $BackupPath = $latest.FullName
}
if (-not (Test-Path -LiteralPath $BackupPath)) { throw "Backup file was not found: $BackupPath" }
$BackupPath = (Resolve-Path -LiteralPath $BackupPath).Path

$sidecarPath = "$BackupPath.sha256"
if (-not (Test-Path -LiteralPath $sidecarPath)) { throw "Checksum file was not found: $sidecarPath" }
$expectedHash = ((Get-Content -LiteralPath $sidecarPath -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
$actualHash = (Get-FileHash -LiteralPath $BackupPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expectedHash -ne $actualHash) { throw "Backup checksum verification failed: $BackupPath" }

$jdbcUrl = Get-ConfigValue $config "SPRING_DATASOURCE_URL" ""
$jdbcMatch = [regex]::Match($jdbcUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
if (-not $jdbcMatch.Success) { throw "SPRING_DATASOURCE_URL is not a supported MySQL JDBC URL: $jdbcUrl" }
$dbHost = $jdbcMatch.Groups["host"].Value
$dbPort = if ($jdbcMatch.Groups["port"].Success) { $jdbcMatch.Groups["port"].Value } else { "3306" }
$dbUser = Get-ConfigValue $config "SPRING_DATASOURCE_USERNAME" (Get-ConfigValue $config "MYSQL_USER" "")
$dbPassword = Get-ConfigValue $config "SPRING_DATASOURCE_PASSWORD" (Get-ConfigValue $config "MYSQL_PASSWORD" "")
if (-not $dbUser) { throw "Database username is required." }

$configuredMysql = if ($MysqlExe) { $MysqlExe } else {
    $dumpPath = Get-ConfigValue $config "BACKUP_MYSQL_EXE" ""
    if ($dumpPath) { Join-Path (Split-Path -Parent $dumpPath) "mysql.exe" } else { "" }
}
$mysqlClient = Find-MySqlClient $configuredMysql
$tempDatabase = "gongdan_restore_drill_{0}" -f (Get-Date -Format "yyyyMMddHHmmss")
$optionPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-mysql-$([guid]::NewGuid().ToString('N')).cnf"
$errorPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-restore-$([guid]::NewGuid().ToString('N')).err"
$created = $false

try {
    $optionContent = "[client]`r`nprotocol=tcp`r`nhost=$dbHost`r`nport=$dbPort`r`nuser=$dbUser`r`npassword=$dbPassword`r`n"
    [System.IO.File]::WriteAllText($optionPath, $optionContent, (New-Object System.Text.UTF8Encoding($false)))
    $baseArguments = @("--defaults-extra-file=$optionPath", "--batch", "--skip-column-names")

    & $mysqlClient @baseArguments "--execute=CREATE DATABASE ``$tempDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "Unable to create restore drill database." }
    $created = $true

    $process = Start-Process -FilePath $mysqlClient `
        -ArgumentList @("--defaults-extra-file=$optionPath", "--database=$tempDatabase") `
        -RedirectStandardInput $BackupPath -RedirectStandardError $errorPath -Wait -PassThru -NoNewWindow
    if ($process.ExitCode -ne 0) {
        $details = if (Test-Path -LiteralPath $errorPath) { (Get-Content -LiteralPath $errorPath -Raw).Trim() } else { "" }
        throw "Restore drill import failed with exit code $($process.ExitCode). $details"
    }

    $tableCountText = & $mysqlClient @baseArguments "--database=$tempDatabase" "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect restored database." }
    $tableCount = [int](($tableCountText | Select-Object -Last 1).Trim())
    $criticalCountText = & $mysqlClient @baseArguments "--database=$tempDatabase" "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('factories','factory_users','work_orders')"
    $criticalCount = [int](($criticalCountText | Select-Object -Last 1).Trim())
    if ($tableCount -lt 10 -or $criticalCount -ne 3) {
        throw "Restore drill validation failed: tableCount=$tableCount, criticalTables=$criticalCount"
    }

    Write-Host "Restore drill passed: database=$tempDatabase, tables=$tableCount, checksum=$actualHash" -ForegroundColor Green
} finally {
    if ($created -and -not $KeepDatabase) {
        & $mysqlClient "--defaults-extra-file=$optionPath" "--execute=DROP DATABASE IF EXISTS ``$tempDatabase``" | Out-Null
    }
    Remove-Item -LiteralPath $optionPath, $errorPath -Force -ErrorAction SilentlyContinue
}
