param(
    [Parameter(Mandatory = $true)][string] $Origin,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string] $Session = 'security-resume-real',
    [string] $Output = 'output/security-resume-ureport-browser-final.json'
)
$ErrorActionPreference = 'Stop'
$uri = [Uri]$Origin
if ($uri.Scheme -ne 'http' -or $uri.Host -ne '127.0.0.2' -or $uri.Port -lt 1024 -or $uri.AbsolutePath -ne '/' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'Only the explicitly started loopback functional-browser-fixture origin is allowed.'
}
$Origin = $uri.GetLeftPart([UriPartial]::Authority)
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$log = Join-Path $workspace 'output/security-resume-ureport-cli-steps.log'
[IO.File]::WriteAllText($log, '', (New-Object Text.UTF8Encoding($false)))
$checks = [Collections.Generic.List[string]]::new()
$messages = [Collections.Generic.List[object]]::new()
$originJson = ConvertTo-Json -InputObject $Origin -Compress
$guard = "if(new URL(page.url()).origin!==$originJson)throw Error('Wrong fixture');"
function Invoke-UReportBrowserCli([string[]] $Arguments) {
    $text = & npx.cmd --yes --package '@playwright/cli' playwright-cli "-s=$Session" @Arguments 2>&1 | Out-String
    [IO.File]::AppendAllText($log, $text, (New-Object Text.UTF8Encoding($false)))
    if ($LASTEXITCODE -ne 0 -or $text -match '(?m)^### Error') { throw $text }
    return $text
}
function Invoke-UReportBrowserCode([string] $Body) {
    # A .cmd shim reparses embedded double quotes. A UTF-8 source file avoids
    # corrupting JavaScript URL literals or regexp syntax in native arguments.
    $stepPath = Join-Path $workspace 'output/security-resume-ureport-step.mjs'
    [IO.File]::WriteAllText($stepPath, "async(page)=>{$guard`n$Body`n}", (New-Object Text.UTF8Encoding($false)))
    return Invoke-UReportBrowserCli @('run-code', '--filename', $stepPath)
}
function Read-UReportBrowserResult([string] $Text) {
    if ($Text -notmatch '(?m)^### Result\r?\n([^\r\n]+)') { throw 'The browser step did not finish with a result.' }
    return $Matches[1] | ConvertFrom-Json
}
function Add-UReportBrowserCheck([bool] $Condition, [string] $Label) {
    if (-not $Condition) { throw $Label }
    $checks.Add($Label)
}
function Invoke-UReportConfirmStep([string] $Body, [string] $Decision, [string] $Label) {
    $text = Invoke-UReportBrowserCode $Body
    Add-UReportBrowserCheck ($text -match '"confirm" dialog with message') $Label
    $messages.Add([ordered]@{ action = $Label; decision = $Decision })
    $null = Invoke-UReportBrowserCli @("dialog-$Decision")
}
$readEditor = @'
const f=page.frameLocator('.ureport-frame');
const state=await(await page.request.get(new URL('/__functional-fixture',page.url()).href)).json();
return {signature:await page.locator('.ureport-frame').getAttribute('src'),
 draft:await f.locator('textarea.form-control:visible').first().inputValue(),
 logouts:state.requests.filter(r=>r.method==='POST'&&r.path.startsWith('/api/auth/logout')).length,
 templateWrites:state.requests.filter(r=>r.method!=='GET'&&/\/ureport\/designer\/(?:save|delete)/i.test(r.path)).length};
'@
Push-Location -LiteralPath $workspace
try {
    $initial = Read-UReportBrowserResult (Invoke-UReportBrowserCli @('run-code', '--filename', 'frontend/scripts/security-ureport-browser-scenario.mjs'))
    Add-UReportBrowserCheck ($initial.fixture -eq 'real-h2-api' -and $initial.dirty) 'Actual native property editing enables the unsaved-state signal'
    Add-UReportBrowserCheck ($initial.cell -eq $initial.draft) 'Actual vendor grid renders the edited text'
    $before = Read-UReportBrowserResult (Invoke-UReportBrowserCode $readEditor)
    foreach ($step in @(
        @{ body = "await page.getByRole('button',{name:'Home',exact:true}).click();"; label = 'Workspace navigation prompts before discarding the native draft' },
        @{ body = "await page.getByRole('button',{name:'Close Advanced reports',exact:true}).click();"; label = 'Tab closure prompts before discarding the native draft' },
        @{ body = "await page.locator('.ureport-actions').getByRole('button',{name:'Refresh',exact:true}).click();"; label = 'Native refresh prompts before overwriting the native draft' },
        @{ body = "await page.getByRole('button',{name:'Account menu',exact:true}).click();await page.getByRole('button',{name:'Sign out',exact:true}).click();"; label = 'Logout prompts about the native draft before revoking a session' }
    )) {
        Invoke-UReportConfirmStep $step.body 'dismiss' $step.label
        $current = Read-UReportBrowserResult (Invoke-UReportBrowserCode $readEditor)
        Add-UReportBrowserCheck ($current.signature -eq $initial.signature -and $current.draft -eq $initial.draft) "Cancellation preserves the same native document: $($step.label)"
    }
    Add-UReportBrowserCheck ($current.logouts -eq $before.logouts) 'Cancelling logout dispatches no server revocation request'
    Invoke-UReportConfirmStep "await page.getByRole('button',{name:'Home',exact:true}).click();" 'accept' 'Explicit discard acceptance permits leaving the native workspace'
    $left = Read-UReportBrowserResult (Invoke-UReportBrowserCode "await page.locator('.ureport-frame').waitFor({state:'detached'});return {home:await page.locator('[data-home-view=task-first]').isVisible()};")
    Add-UReportBrowserCheck $left.home 'Confirmed departure deactivates the original native iframe'
    $null = Invoke-UReportBrowserCode "if(!await page.getByRole('button',{name:'Sign out',exact:true}).isVisible())await page.getByRole('button',{name:'Account menu',exact:true}).click();await page.getByRole('button',{name:'Sign out',exact:true}).click();await page.getByRole('textbox',{name:'Factory code',exact:true}).waitFor();return {loggedOut:true};"
    $after = Read-UReportBrowserResult (Invoke-UReportBrowserCode "const s=await(await page.request.get(new URL('/__functional-fixture',page.url()).href)).json();const r=await page.request.get(new URL('/ureport/designer',page.url()).href);return {status:r.status(),templateWrites:s.requests.filter(r=>r.method!=='GET'&&/\/ureport\/designer\/(?:save|delete)/i.test(r.path)).length};")
    Add-UReportBrowserCheck ($after.status -eq 401) 'Successful UI logout immediately invalidates the browser native-report cookie'
    Add-UReportBrowserCheck ($after.templateWrites -eq $before.templateWrites) 'No native template was saved or deleted during scratch testing'
    $result = [ordered]@{ fixture = 'real-h2-api'; completed = $true; passed = $checks.Count; checks = $checks; dialogs = $messages;
        scope = 'Real platform API login/native vendor property editor/actual App guards; not all report widgets or physical printing'; templateWrites = 0 }
    $path = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path $workspace $Output }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllText($path, ($result | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
    [pscustomobject]@{ Passed = $checks.Count; TemplateWrites = 0; Output = $path } | ConvertTo-Json -Compress
} finally { Pop-Location }
