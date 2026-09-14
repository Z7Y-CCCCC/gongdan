# Pure regression checks. No backend process, service, backup or restore is run.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$project = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $project 'deploy/windows/scripts/backend-process.ps1')
. (Join-Path $PSScriptRoot 'release-path.ps1')

$script:checks = 0
function Assert-Guard([bool]$Condition, [string]$Message) {
    $script:checks++
    if (-not $Condition) { throw $Message }
}

$jarPath = Join-Path $project 'release qa/server/app.jar'
$correct = [pscustomobject]@{ Name = 'java.exe'; CommandLine = "java -Xmx512m -jar `"$jarPath`" --spring.profiles.active=prod" }
Assert-Guard (Test-BackendProcessIdentity $correct $jarPath) 'Quoted paths with spaces must match the exact release JAR.'
Assert-Guard (-not (Test-BackendProcessIdentity ([pscustomobject]@{ Name = 'notepad.exe'; CommandLine = $correct.CommandLine }) $jarPath)) 'A reused PID for a non-Java program must not match.'
Assert-Guard (-not (Test-BackendProcessIdentity $correct (Join-Path $project 'different-release/server/app.jar'))) 'Another release must not match.'
Assert-Guard (-not (Test-BackendProcessIdentity ([pscustomobject]@{ Name = 'java.exe'; CommandLine = 'java -jar app.jar' }) $jarPath)) 'Unverifiable relative JAR paths must fail closed.'
Assert-Guard (-not (Test-BackendProcessIdentity ([pscustomobject]@{ Name = 'java.exe'; CommandLine = "java -Ddescription=$jarPath OtherProgram" }) $jarPath)) 'The expected path outside a -jar argument must not match.'

$parsed = ConvertFrom-BackendDotEnv @('# comment', 'EMPTY=', 'PASSWORD="value with spaces=and equals"', "SINGLE='quoted value'", 'PLAIN=value', 'INVALID')
Assert-Guard ($parsed.PASSWORD -eq 'value with spaces=and equals') 'Double-quoted dotenv values must be unwrapped consistently.'
Assert-Guard ($parsed.SINGLE -eq 'quoted value') 'Single-quoted dotenv values must be unwrapped consistently.'
Assert-Guard ($parsed.EMPTY -eq '') 'Explicit empty dotenv values must be preserved.'
Assert-Guard ($parsed.PLAIN -eq 'value' -and $parsed.Count -eq 4) 'Comments and malformed dotenv lines must be ignored.'

# External source values must be imported as literal strings, including Base64 padding and scoped lists.
# These are public parser-only fixtures; the test never sets process variables or connects to a source.
$externalParsed = ConvertFrom-BackendDotEnv @(
    'APP_EXTERNAL_DATA_KEYS="ci-render-only:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="',
    'APP_EXTERNAL_DATA_ACTIVE_KEY=ci-render-only',
    'APP_EXTERNAL_DATA_ALLOWED_TARGETS=12@mes.example.invalid:443,13@mes.example.invalid:443',
    'APP_EXTERNAL_DATA_ALLOWED_ADDRESSES=12@192.0.2.12,13@192.0.2.13',
    'APP_EXTERNAL_DATA_PLAINTEXT_TARGETS=',
    'APP_EXTERNAL_DATA_SQLITE_ROOT="C:\External Snapshots"',
    'APP_EXTERNAL_DATA_SQLITE_FILES=12@equipment_snapshot,13@equipment_snapshot'
)
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_KEYS -eq 'ci-render-only:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=') 'External data key Base64 padding must remain intact.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_ACTIVE_KEY -eq 'ci-render-only') 'External data active key IDs must remain intact.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_ALLOWED_TARGETS -eq '12@mes.example.invalid:443,13@mes.example.invalid:443') 'Factory-scoped target lists must remain intact.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_ALLOWED_ADDRESSES -eq '12@192.0.2.12,13@192.0.2.13') 'Factory-scoped address lists must remain intact.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_PLAINTEXT_TARGETS -eq '') 'Empty external plaintext allowlists must remain empty.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_SQLITE_ROOT -eq 'C:\External Snapshots') 'Quoted SQLite directories with spaces must remain intact.'
Assert-Guard ($externalParsed.APP_EXTERNAL_DATA_SQLITE_FILES -eq '12@equipment_snapshot,13@equipment_snapshot' -and $externalParsed.Count -eq 7) 'Factory-scoped SQLite registration lists must remain intact.'

$outputPath = Join-Path $project 'release-regression-output'
Assert-Guard ((Get-ValidatedReleasePath $project $outputPath) -eq $outputPath) 'A dedicated release directory must be accepted.'
foreach ($unsafe in @($project, (Join-Path $project '../outside'), (Join-Path $project 'frontend'), (Join-Path $project 'backend/target'), (Join-Path $project '.git'))) {
    $rejected = $false
    try { Get-ValidatedReleasePath $project $unsafe | Out-Null } catch { $rejected = $true }
    Assert-Guard $rejected "Unsafe release directory was accepted: $unsafe"
}
# Evaluate only pure validation expressions, never the preflight operational body.
# Without parentheses, PowerShell treats -or as another argument to the function.
$parseTokens = $null
$parseErrors = $null
$preflightAst = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $project 'deploy/windows/scripts/preflight.ps1'), [ref]$parseTokens, [ref]$parseErrors)
Assert-Guard ($parseErrors.Count -eq 0) 'Preflight must parse successfully.'
$placeholderFunction = $preflightAst.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Is-Placeholder' }, $true)
. ([scriptblock]::Create($placeholderFunction.Extent.Text))
$conditions = $preflightAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.IfStatementAst] }, $true) | ForEach-Object { $_.Clauses[0].Item1.Extent.Text }
$passwordItem = @('ADMIN', 'short')
$backupUser = 'root'
$backupPassword = 'short'
$restoreUser = 'root'
$restorePassword = 'short'
foreach ($variable in @('$passwordItem[1]', '$backupUser', '$backupPassword', '$restoreUser', '$restorePassword')) {
    $condition = $conditions | Where-Object { $_.Contains('Is-Placeholder') -and $_.Contains($variable) -and $_.Contains('-or') } | Select-Object -First 1
    Assert-Guard ([bool]$condition) "Missing preflight condition for $variable"
    $rejected = & ([scriptblock]::Create($condition))
    Assert-Guard ([bool]$rejected) "Preflight must reject short passwords/root identities: $variable"
}
Write-Host "Windows operational guard regressions passed: $script:checks checks; no services or data were modified."
