param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [switch]$Login,
    [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) { $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env" }
if (-not $OutputPath) {
    $OutputPath = Join-Path $ReleaseRoot ("runtime\acceptance-smoke-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing config file: $Path" }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $values
}

function Test-Page([string]$Name, [string]$Url, [string]$ExpectedText) {
    $started = Get-Date
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        $content = if ($response.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
        $passed = $response.StatusCode -eq 200 -and $content.Contains($ExpectedText)
        return [pscustomobject]@{ name = $Name; passed = $passed; statusCode = $response.StatusCode; durationMs = [int]((Get-Date) - $started).TotalMilliseconds; detail = if ($passed) { "expected content found" } else { "expected content missing" } }
    } catch {
        return [pscustomobject]@{ name = $Name; passed = $false; statusCode = 0; durationMs = [int]((Get-Date) - $started).TotalMilliseconds; detail = $_.Exception.Message }
    }
}

$config = Read-DotEnv $EnvFile
$base = $BaseUrl.TrimEnd('/')
$checks = New-Object System.Collections.Generic.List[object]
$checks.Add((Test-Page "admin-web" "$base/" '<div id="app"></div>'))
$checks.Add((Test-Page "mobile-h5" "$base/mobile/" '<div id="app"></div>'))

try {
    $health = Invoke-RestMethod -Uri "$base/health" -TimeoutSec 10
    $checks.Add([pscustomobject]@{ name = "backend-readiness"; passed = $health.status -eq "UP"; statusCode = 200; durationMs = 0; detail = "status=$($health.status)" })
} catch {
    $checks.Add([pscustomobject]@{ name = "backend-readiness"; passed = $false; statusCode = 0; durationMs = 0; detail = $_.Exception.Message })
}

if ($Login) {
    $loginBody = @{
        factoryCode = $config["APP_SEED_FACTORY_CODE"]
        username = $config["APP_SEED_ADMIN_USERNAME"]
        password = $config["APP_SEED_ADMIN_PASSWORD"]
    } | ConvertTo-Json
    try {
        $auth = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 10
        $factory = Invoke-RestMethod -Uri "$base/api/factory" -Headers @{ Authorization = "Bearer $($auth.token)" } -TimeoutSec 10
        $expectedCode = [string]$config["APP_SEED_FACTORY_CODE"]
        $checks.Add([pscustomobject]@{ name = "admin-login-and-factory-scope"; passed = $factory.code -eq $expectedCode; statusCode = 200; durationMs = 0; detail = "factoryCode=$($factory.code)" })
    } catch {
        $checks.Add([pscustomobject]@{ name = "admin-login-and-factory-scope"; passed = $false; statusCode = 0; durationMs = 0; detail = $_.Exception.Message })
    }
}

$failed = @($checks | Where-Object { -not $_.passed })
$evidence = [pscustomobject]@{
    status = if ($failed.Count -eq 0) { "PASS" } else { "FAIL" }
    testedAt = (Get-Date).ToString("o")
    baseUrl = $base
    gitCommit = if (Test-Path -LiteralPath (Join-Path $ReleaseRoot "BUILD-INFO.txt")) { (Get-Content -LiteralPath (Join-Path $ReleaseRoot "BUILD-INFO.txt") | Where-Object { $_ -like "GitCommit=*" }) -replace '^GitCommit=', '' } else { "unknown" }
    checks = $checks
}
New-Item -ItemType Directory -Path (Split-Path -Parent $OutputPath) -Force | Out-Null
$evidence | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputPath -Encoding UTF8

if ($failed.Count -gt 0) {
    Write-Host "Smoke test failed. Evidence: $OutputPath" -ForegroundColor Red
    exit 1
}
Write-Host "Smoke test passed. Evidence: $OutputPath" -ForegroundColor Green
