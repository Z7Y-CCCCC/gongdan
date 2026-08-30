param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [ValidateSet("Auto", "Demo", "Commercial")]
    [string]$Mode = "Auto",
    [switch]$SkipRuntimeChecks
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

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$Message) { $script:failures.Add($Message) }
function Add-Warning([string]$Message) { $script:warnings.Add($Message) }
function Is-Placeholder([string]$Value) {
    if (-not $Value) { return $true }
    $normalized = $Value.Trim().ToLowerInvariant()
    return $normalized.Contains("change_me") -or $normalized.Contains("change-me") -or
        $normalized.Contains("example.com") -or $normalized.Contains("change_me_domain") -or
        $normalized.Contains("change_me_") -or $normalized -eq "wx..." -or $normalized -eq "your factory"
}

$config = Read-DotEnv $EnvFile
$profile = (Get-Value $config "SPRING_PROFILES_ACTIVE" "demo").Trim().ToLowerInvariant()
if ($Mode -eq "Auto") { $Mode = if ($profile -eq "prod") { "Commercial" } else { "Demo" } }

foreach ($relativePath in @("server\app.jar", "www\index.html", "www\mobile\index.html", "scripts\start-backend.ps1", "scripts\backup-database.ps1", "scripts\archive-binlogs.ps1", "scripts\sync-backup-remote.ps1", "scripts\test-restore.ps1", "scripts\test-pitr-restore.ps1")) {
    if (-not (Test-Path -LiteralPath (Join-Path $ReleaseRoot $relativePath))) {
        Add-Failure "Required release file is missing: $relativePath"
    }
}

foreach ($name in @("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "APP_SEED_FACTORY_CODE", "APP_SEED_ADMIN_USERNAME", "APP_SEED_ADMIN_PASSWORD", "APP_SEED_SUPER_ADMIN_PASSWORD")) {
    $value = Get-Value $config $name ""
    if (-not $value -or $value.Contains("CHANGE_ME")) { Add-Failure "$name is not configured" }
}

if ($Mode -eq "Commercial") {
    if ($profile -ne "prod") { Add-Failure "SPRING_PROFILES_ACTIVE must be prod for a commercial deployment" }
    if ((Get-Value $config "SPRING_DATASOURCE_USERNAME" "").Trim().ToLowerInvariant() -eq "root") {
        Add-Failure "SPRING_DATASOURCE_USERNAME must not use root"
    }

    $adminPassword = Get-Value $config "APP_SEED_ADMIN_PASSWORD" ""
    $superPassword = Get-Value $config "APP_SEED_SUPER_ADMIN_PASSWORD" ""
    foreach ($passwordItem in @(@("APP_SEED_ADMIN_PASSWORD", $adminPassword), @("APP_SEED_SUPER_ADMIN_PASSWORD", $superPassword))) {
        if (Is-Placeholder $passwordItem[1] -or $passwordItem[1].Length -lt 12) {
            Add-Failure "$($passwordItem[0]) must be a non-placeholder value of at least 12 characters"
        }
    }
    if ($adminPassword -and $adminPassword -eq $superPassword) {
        Add-Failure "Factory admin and super admin passwords must be different"
    }

    if ((Get-Value $config "APP_WECHAT_MOCK_ENABLED" "false").Trim().ToLowerInvariant() -ne "false") {
        Add-Failure "APP_WECHAT_MOCK_ENABLED must be false"
    }
    foreach ($name in @("APP_WECHAT_APP_ID", "APP_WECHAT_SECRET", "APP_SMS_HTTP_URL", "APP_CORS_ALLOWED_ORIGIN_PATTERNS", "APP_PLUGIN_GATEWAY_SECRET", "APP_PLUGIN_ALLOWED_BACKEND_HOSTS", "APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS")) {
        if (Is-Placeholder (Get-Value $config $name "")) { Add-Failure "$name must be configured with a production value" }
    }
    if ((Get-Value $config "APP_PLUGIN_GATEWAY_SECRET" "").Length -lt 32) {
        Add-Failure "APP_PLUGIN_GATEWAY_SECRET must be at least 32 characters"
    }
    if ((Get-Value $config "APP_SMS_SENDER" "http").Trim().ToLowerInvariant() -ne "http") {
        Add-Failure "APP_SMS_SENDER must be http"
    }
    if (-not (Get-Value $config "APP_SMS_HTTP_URL" "").Trim().ToLowerInvariant().StartsWith("https://")) {
        Add-Failure "APP_SMS_HTTP_URL must use HTTPS"
    }

    $corsOrigins = (Get-Value $config "APP_CORS_ALLOWED_ORIGIN_PATTERNS" "").Split(',')
    foreach ($origin in $corsOrigins) {
        $normalizedOrigin = $origin.Trim().ToLowerInvariant()
        if (-not $normalizedOrigin.StartsWith("https://") -or $normalizedOrigin.Contains("*")) {
            Add-Failure "Every production CORS origin must be an explicit HTTPS origin: $origin"
        }
    }

    foreach ($hostName in (Get-Value $config "APP_PLUGIN_ALLOWED_BACKEND_HOSTS" "").Split(',')) {
        $normalizedHost = $hostName.Trim()
        if (-not $normalizedHost -or $normalizedHost.Contains("*") -or $normalizedHost.Contains("://") -or $normalizedHost.Contains("/")) {
            Add-Failure "Plugin backend allow-list entries must be explicit host names: $hostName"
        }
    }
    foreach ($origin in (Get-Value $config "APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS" "").Split(',')) {
        $normalizedOrigin = $origin.Trim().ToLowerInvariant()
        if (-not $normalizedOrigin.StartsWith("https://") -or $normalizedOrigin.Contains("*")) {
            Add-Failure "Every production plugin frontend origin must be explicit HTTPS: $origin"
        }
    }
    $pluginRequestLimit = 0
    if (-not [int]::TryParse((Get-Value $config "APP_PLUGIN_PROXY_MAX_REQUEST_BYTES" ""), [ref]$pluginRequestLimit) -or $pluginRequestLimit -lt 1024) {
        Add-Failure "APP_PLUGIN_PROXY_MAX_REQUEST_BYTES must be an integer of at least 1024"
    }

    if ((Get-Value $config "SPRING_FLYWAY_ENABLED" "true").Trim().ToLowerInvariant() -ne "true") {
        Add-Failure "SPRING_FLYWAY_ENABLED must be true"
    }
    if ((Get-Value $config "SPRING_JPA_HIBERNATE_DDL_AUTO" "validate").Trim().ToLowerInvariant() -ne "validate") {
        Add-Failure "SPRING_JPA_HIBERNATE_DDL_AUTO must be validate"
    }

    $storageType = (Get-Value $config "APP_UPLOAD_STORAGE_TYPE" "local").Trim().ToLowerInvariant()
    if ($storageType -notin @("local", "s3")) {
        Add-Failure "APP_UPLOAD_STORAGE_TYPE must be local or s3"
    } elseif ($storageType -eq "s3") {
        foreach ($name in @("APP_UPLOAD_S3_BUCKET", "APP_UPLOAD_S3_ACCESS_KEY", "APP_UPLOAD_S3_SECRET_KEY")) {
            if (Is-Placeholder (Get-Value $config $name "")) { Add-Failure "$name is required for S3 storage" }
        }
    } else {
        Add-Warning "Local upload storage is suitable only for a single backend instance"
    }

    $backupDir = Get-Value $config "BACKUP_DIR" ""
    $secondaryDir = Get-Value $config "BACKUP_SECONDARY_DIR" ""
    $remoteUri = Get-Value $config "BACKUP_REMOTE_URI" ""
    if (-not $backupDir) { Add-Failure "BACKUP_DIR must be configured" }
    if (Is-Placeholder $remoteUri) { Add-Failure "BACKUP_REMOTE_URI must point to an immutable bucket outside the production account or host" }
    if ($secondaryDir -and (Is-Placeholder $secondaryDir)) { Add-Failure "Set BACKUP_SECONDARY_DIR to a real second disk/NAS path or leave it empty" }
    if ($backupDir -and $secondaryDir -and $backupDir.TrimEnd('/','\') -eq $secondaryDir.TrimEnd('/','\')) {
        Add-Failure "BACKUP_SECONDARY_DIR must differ from BACKUP_DIR"
    }

    $backupUser = Get-Value $config "BACKUP_MYSQL_USER" ""
    $backupPassword = Get-Value $config "BACKUP_MYSQL_PASSWORD" ""
    if (Is-Placeholder $backupUser -or $backupUser.Trim().ToLowerInvariant() -eq "root") {
        Add-Failure "BACKUP_MYSQL_USER must be a dedicated non-root backup account"
    }
    if (Is-Placeholder $backupPassword -or $backupPassword.Length -lt 16) {
        Add-Failure "BACKUP_MYSQL_PASSWORD must be a non-placeholder value of at least 16 characters"
    }
    $restoreUser = Get-Value $config "RESTORE_MYSQL_USER" ""
    $restorePassword = Get-Value $config "RESTORE_MYSQL_PASSWORD" ""
    if (Is-Placeholder $restoreUser -or $restoreUser.Trim().ToLowerInvariant() -eq "root") {
        Add-Failure "RESTORE_MYSQL_USER must be a dedicated non-root recovery account"
    }
    if (Is-Placeholder $restorePassword -or $restorePassword.Length -lt 16) {
        Add-Failure "RESTORE_MYSQL_PASSWORD must be a non-placeholder value of at least 16 characters"
    }

    if (-not (Is-Placeholder $remoteUri)) {
        if ($remoteUri -notmatch '^s3://(?<bucket>[^/]+)(/.*)?$') {
            Add-Failure "BACKUP_REMOTE_URI must use s3://bucket/prefix syntax"
        } else {
            $backupBucket = $Matches["bucket"]
            if ($storageType -eq "s3" -and $backupBucket -eq (Get-Value $config "APP_UPLOAD_S3_BUCKET" "")) {
                Add-Failure "Backup bucket must differ from the live attachment bucket"
            }
        }
        if ((Get-Value $config "BACKUP_REQUIRE_IMMUTABLE_REMOTE" "true").Trim().ToLowerInvariant() -ne "true") {
            Add-Failure "BACKUP_REQUIRE_IMMUTABLE_REMOTE must be true for commercial deployments"
        }
        foreach ($name in @("BACKUP_REMOTE_ACCESS_KEY", "BACKUP_REMOTE_SECRET_KEY")) {
            if (Is-Placeholder (Get-Value $config $name "")) { Add-Failure "$name is required for the backup-only storage identity" }
        }
    }

    foreach ($retentionItem in @(@("BACKUP_RETENTION_DAYS", 7), @("BINLOG_ARCHIVE_RETENTION_DAYS", 7), @("LOG_RETENTION_DAYS", 7), @("APP_SYSTEM_LOG_RETENTION_DAYS", 30))) {
        $value = 0
        if (-not [int]::TryParse((Get-Value $config $retentionItem[0] "0"), [ref]$value) -or $value -lt $retentionItem[1]) {
            Add-Failure "$($retentionItem[0]) must be at least $($retentionItem[1])"
        }
    }
    $minimumRemoteRetention = 0
    if (-not [int]::TryParse((Get-Value $config "BACKUP_REMOTE_MIN_RETENTION_DAYS" "35"), [ref]$minimumRemoteRetention) -or $minimumRemoteRetention -lt 1) {
        Add-Failure "BACKUP_REMOTE_MIN_RETENTION_DAYS must be a positive integer"
    }
    if ((Get-Value $config "BACKUP_REMOTE_REQUIRED_LOCK_MODE" "COMPLIANCE").Trim().ToUpperInvariant() -notin @("COMPLIANCE", "GOVERNANCE")) {
        Add-Failure "BACKUP_REMOTE_REQUIRED_LOCK_MODE must be COMPLIANCE or GOVERNANCE"
    }

    if (-not (Get-Value $config "NGINX_SERVICE_NAME" "") -and -not (Get-Value $config "NGINX_EXE" "")) {
        Add-Failure "Configure NGINX_SERVICE_NAME or NGINX_EXE for unattended recovery"
    }
}

if (-not $SkipRuntimeChecks) {
    $javaExe = Get-Value $config "JAVA_EXE" ""
    if ($javaExe) {
        if (-not (Test-Path -LiteralPath $javaExe)) { Add-Failure "JAVA_EXE was not found: $javaExe" }
    } elseif (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        Add-Failure "java.exe was not found in PATH and JAVA_EXE is empty"
    }

    $mysqlServiceName = Get-Value $config "MYSQL_SERVICE_NAME" ""
    if ($mysqlServiceName -and -not (Get-Service -Name $mysqlServiceName -ErrorAction SilentlyContinue)) {
        Add-Failure "MySQL Windows service was not found: $mysqlServiceName"
    }

    $nginxExe = Get-Value $config "NGINX_EXE" ""
    if ($nginxExe -and -not (Test-Path -LiteralPath $nginxExe)) {
        Add-Failure "NGINX_EXE was not found: $nginxExe"
    }

    if ($Mode -eq "Commercial") {
        $remoteUri = Get-Value $config "BACKUP_REMOTE_URI" ""
        if (-not (Is-Placeholder $remoteUri)) {
            try {
                & (Join-Path $ReleaseRoot "scripts\sync-backup-remote.ps1") -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile -VerifyOnly
            } catch {
                Add-Failure "Remote backup policy check failed: $($_.Exception.Message)"
            }
        }
    }
}

Write-Host "Gongdan preflight mode: $Mode" -ForegroundColor Cyan
foreach ($warning in $warnings) { Write-Warning $warning }
if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Host "FAIL $failure" -ForegroundColor Red }
    throw "Preflight failed with $($failures.Count) issue(s)."
}

Write-Host "Preflight passed with $($warnings.Count) warning(s)." -ForegroundColor Green
