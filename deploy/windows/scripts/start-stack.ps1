param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [switch]$SkipDatabaseServiceCheck
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

function Write-RecoveryLog([string]$Path, [string]$Message) {
    Add-Content -LiteralPath $Path -Value ("{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message) -Encoding UTF8
}

function Wait-ServiceRunning([string]$Name, [int]$TimeoutSeconds = 60) {
    $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if (-not $service) { throw "Windows service was not found: $Name" }
    if ($service.Status -ne "Running") {
        Start-Service -Name $Name
    }
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
        if ($service -and $service.Status -eq "Running") { return }
        Start-Sleep -Seconds 1
    }
    throw "Windows service did not reach Running state: $Name"
}

function Test-Endpoint([string]$Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300)
    } catch {
        return $false
    }
}

function Wait-Endpoint([string]$Url, [int]$TimeoutSeconds = 60) {
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if (Test-Endpoint $Url) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-NginxIfConfigured([hashtable]$Config, [string]$Root) {
    $serviceName = Get-ConfigValue $Config "NGINX_SERVICE_NAME" ""
    if ($serviceName) {
        Wait-ServiceRunning $serviceName
        return
    }

    $nginxExe = Get-ConfigValue $Config "NGINX_EXE" ""
    if (-not $nginxExe) { return }
    if (-not (Test-Path -LiteralPath $nginxExe)) { throw "NGINX_EXE was not found: $nginxExe" }
    $nginxDir = Get-ConfigValue $Config "NGINX_CONFIG_DIR" (Split-Path -Parent $nginxExe)
    Push-Location $nginxDir
    try {
        & $nginxExe -t
        if ($LASTEXITCODE -ne 0) { throw "nginx configuration test failed." }
        Start-Process -FilePath $nginxExe -WorkingDirectory $nginxDir -WindowStyle Hidden | Out-Null
    } finally {
        Pop-Location
    }
}

$logsDir = Join-Path $ReleaseRoot "logs"
$runtimeDir = Join-Path $ReleaseRoot "runtime"
New-Item -ItemType Directory -Path $logsDir, $runtimeDir -Force | Out-Null
$recoveryLog = Join-Path $logsDir "startup-recovery.log"
$statusPath = Join-Path $runtimeDir "last-startup.json"
$config = Read-DotEnv $EnvFile
$startedAt = Get-Date

try {
    $mysqlService = Get-ConfigValue $config "MYSQL_SERVICE_NAME" ""
    if (-not $SkipDatabaseServiceCheck -and $mysqlService) {
        Wait-ServiceRunning $mysqlService
        Write-RecoveryLog $recoveryLog "MySQL service ready: $mysqlService"
    }

    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $backendScript = Join-Path $ReleaseRoot "scripts\start-backend.ps1"
    & $powershell -NoProfile -ExecutionPolicy Bypass -File $backendScript -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile
    if ($LASTEXITCODE -ne 0) { throw "Backend start script failed with exit code $LASTEXITCODE." }
    Write-RecoveryLog $recoveryLog "Backend start command completed."

    Start-NginxIfConfigured $config $ReleaseRoot
    $healthUrl = Get-ConfigValue $config "NGINX_HEALTH_URL" "http://127.0.0.1:8088/health"
    if (-not (Wait-Endpoint $healthUrl 60)) {
        throw "Web entry did not become healthy: $healthUrl"
    }
    Write-RecoveryLog $recoveryLog "Web entry ready: $healthUrl"

    $result = [pscustomobject]@{
        status = "UP"
        startedAt = $startedAt.ToString("o")
        completedAt = (Get-Date).ToString("o")
        backend = "UP"
        web = "UP"
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host "Gongdan stack recovered successfully." -ForegroundColor Green
} catch {
    $result = [pscustomobject]@{
        status = "DOWN"
        startedAt = $startedAt.ToString("o")
        completedAt = (Get-Date).ToString("o")
        error = $_.Exception.Message
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-RecoveryLog $recoveryLog "Stack recovery FAILED: $($_.Exception.Message)"
    throw
}
