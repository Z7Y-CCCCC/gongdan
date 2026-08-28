param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$BackupPath = "",
    [string]$UploadBackupPath = "",
    [string]$ConfirmDatabase = "",
    [string]$MysqlExe = "",
    [switch]$SkipUploads,
    [switch]$SkipSafetyBackup
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

function Find-MySqlClient([string]$ConfiguredPath) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath)) { throw "Configured mysql.exe was not found: $ConfiguredPath" }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "mysql.exe was not found. Set BACKUP_MYSQL_EXE or pass -MysqlExe."
}

function Verify-Checksum([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Backup file was not found: $Path" }
    $sidecar = "$Path.sha256"
    if (-not (Test-Path -LiteralPath $sidecar)) { throw "Checksum file was not found: $sidecar" }
    $expected = ((Get-Content -LiteralPath $sidecar -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -ne $actual) { throw "Checksum verification failed: $Path" }
}

function New-MySqlOptions([hashtable]$Config) {
    $jdbcUrl = Get-Value $Config "SPRING_DATASOURCE_URL" ""
    $match = [regex]::Match($jdbcUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
    if (-not $match.Success) { throw "SPRING_DATASOURCE_URL is not a supported MySQL JDBC URL: $jdbcUrl" }
    $database = $match.Groups["database"].Value
    if ($database -notmatch '^[A-Za-z0-9_]+$') { throw "Database name contains unsupported characters: $database" }
    if (-not $ConfirmDatabase -or $ConfirmDatabase -ne $database) {
        throw "Destructive restore requires -ConfirmDatabase $database"
    }
    $optionPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-restore-$([guid]::NewGuid().ToString('N')).cnf"
    $content = "[client]`r`nprotocol=tcp`r`nhost=$($match.Groups['host'].Value)`r`nport=$(if ($match.Groups['port'].Success) { $match.Groups['port'].Value } else { '3306' })`r`nuser=$(Get-Value $Config 'SPRING_DATASOURCE_USERNAME' (Get-Value $Config 'MYSQL_USER' ''))`r`npassword=$(Get-Value $Config 'SPRING_DATASOURCE_PASSWORD' (Get-Value $Config 'MYSQL_PASSWORD' ''))`r`n"
    [System.IO.File]::WriteAllText($optionPath, $content, (New-Object System.Text.UTF8Encoding($false)))
    return [pscustomobject]@{ path = $optionPath; database = $database }
}

$config = Read-DotEnv $EnvFile
$backupDirValue = Get-Value $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
$backupDir = if ([System.IO.Path]::IsPathRooted($backupDirValue)) { [System.IO.Path]::GetFullPath($backupDirValue) } else { [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $backupDirValue)) }
if (-not $BackupPath) {
    $latest = Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw "No database backup was found in $backupDir" }
    $BackupPath = $latest.FullName
}
$BackupPath = [System.IO.Path]::GetFullPath($BackupPath)
Verify-Checksum $BackupPath
if (-not $SkipUploads -and $UploadBackupPath) { Verify-Checksum $UploadBackupPath }

$mysqlClient = Find-MySqlClient (if ($MysqlExe) { $MysqlExe } else { Get-Value $config "BACKUP_MYSQL_EXE" "" })
$mysqlOptions = New-MySqlOptions $config
$stopScript = Join-Path $ReleaseRoot "scripts\stop-backend.ps1"
$startScript = Join-Path $ReleaseRoot "scripts\start-stack.ps1"
$restoreLog = Join-Path $ReleaseRoot "logs\restore.log"
New-Item -ItemType Directory -Path (Split-Path -Parent $restoreLog) -Force | Out-Null
$safetyBackup = ""
$uploadTemp = Join-Path $ReleaseRoot ("temp\restore-uploads-{0}" -f [guid]::NewGuid().ToString('N'))

try {
    if (-not $SkipSafetyBackup) {
        & (Join-Path $ReleaseRoot "scripts\backup-database.ps1") -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile
        if ($LASTEXITCODE -ne 0) { throw "Safety backup failed; restore was not started." }
        $safetyBackup = (Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    }

    if (Test-Path -LiteralPath $stopScript) { & $stopScript -ReleaseRoot $ReleaseRoot }

    & $mysqlClient "--defaults-extra-file=$($mysqlOptions.path)" "--execute=DROP DATABASE IF EXISTS ``$($mysqlOptions.database)``; CREATE DATABASE ``$($mysqlOptions.database)`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "Unable to recreate the restore database." }

    $importError = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-restore-import-$([guid]::NewGuid().ToString('N')).err"
    try {
        $import = Start-Process -FilePath $mysqlClient -ArgumentList @("--defaults-extra-file=$($mysqlOptions.path)", "--database=$($mysqlOptions.database)") -RedirectStandardInput $BackupPath -RedirectStandardError $importError -Wait -PassThru -NoNewWindow
        if ($import.ExitCode -ne 0) {
            $detail = if (Test-Path -LiteralPath $importError) { (Get-Content -LiteralPath $importError -Raw).Trim() } else { "" }
            throw "Database restore failed with exit code $($import.ExitCode). $detail"
        }
    } finally {
        Remove-Item -LiteralPath $importError -Force -ErrorAction SilentlyContinue
    }

    if (-not $SkipUploads -and $UploadBackupPath) {
        $uploadRootValue = Get-Value $config "APP_UPLOAD_REPORT_IMAGE_DIR" (Join-Path $ReleaseRoot "data\uploads")
        $uploadRoot = if ([System.IO.Path]::IsPathRooted($uploadRootValue)) { [System.IO.Path]::GetFullPath($uploadRootValue) } else { [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $uploadRootValue)) }
        $releasePrefix = ([System.IO.Path]::GetFullPath($ReleaseRoot)).TrimEnd('\') + '\'
        if (-not $uploadRoot.StartsWith($releasePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Upload restore path must be inside ReleaseRoot for safety: $uploadRoot"
        }
        New-Item -ItemType Directory -Path $uploadTemp -Force | Out-Null
        Expand-Archive -LiteralPath $UploadBackupPath -DestinationPath $uploadTemp -Force
        New-Item -ItemType Directory -Path $uploadRoot -Force | Out-Null
        Get-ChildItem -LiteralPath $uploadRoot -Force | Remove-Item -Recurse -Force
        Get-ChildItem -LiteralPath $uploadTemp -Force | Copy-Item -Destination $uploadRoot -Recurse -Force
    }

    if (Test-Path -LiteralPath $startScript) { & $startScript -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile; if ($LASTEXITCODE -ne 0) { throw "Application did not recover after restore." } }
    Add-Content -LiteralPath $restoreLog -Value ("{0} Restore succeeded: database={1}; backup={2}; safetyBackup={3}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $mysqlOptions.database, $BackupPath, $safetyBackup) -Encoding UTF8
    Write-Host "Restore completed and application recovery passed." -ForegroundColor Green
} catch {
    Add-Content -LiteralPath $restoreLog -Value ("{0} Restore FAILED: {1}; safetyBackup={2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $_.Exception.Message, $safetyBackup) -Encoding UTF8
    throw
} finally {
    if ($mysqlOptions -and (Test-Path -LiteralPath $mysqlOptions.path)) { Remove-Item -LiteralPath $mysqlOptions.path -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $uploadTemp) { Remove-Item -LiteralPath $uploadTemp -Recurse -Force -ErrorAction SilentlyContinue }
}
