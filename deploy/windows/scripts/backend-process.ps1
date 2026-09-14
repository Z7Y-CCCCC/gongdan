# Shared read-only parsing/identity helpers. Dot-sourcing this file never starts
# or stops a process and does not import environment values.

function ConvertFrom-BackendDotEnv([string[]]$Lines) {
    $values = @{}
    foreach ($line in $Lines) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
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

function Test-BackendProcessIdentity([object]$ProcessInfo, [string]$JarPath) {
    if (-not $ProcessInfo -or $ProcessInfo.Name -notmatch '^java(w)?(\.exe)?$') { return $false }
    $commandLine = [string]$ProcessInfo.CommandLine
    $jarArgument = [regex]::Match($commandLine, '(?i)(?:^|\s)-jar\s+(?:"(?<quoted>[^"]+)"|(?<unquoted>\S+))')
    if (-not $jarArgument.Success) { return $false }
    $argument = if ($jarArgument.Groups['quoted'].Success) { $jarArgument.Groups['quoted'].Value } else { $jarArgument.Groups['unquoted'].Value }
    if (-not [System.IO.Path]::IsPathRooted($argument)) { return $false }
    try {
        $actual = [System.IO.Path]::GetFullPath($argument)
        $expected = [System.IO.Path]::GetFullPath($JarPath)
        return [string]::Equals($actual, $expected, [System.StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}

function Get-VerifiedBackendProcess([int]$ProcessId, [string]$JarPath) {
    if ($ProcessId -le 0) { throw 'Recorded backend PID must be a positive integer.' }
    $info = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
    if (-not $info) { return $null }
    if (-not $info.CommandLine) { throw 'Cannot verify the recorded process command line; no process will be stopped.' }
    if (-not (Test-BackendProcessIdentity $info $JarPath)) { return $null }
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($info.CreationDate -and [Math]::Abs(($process.StartTime.ToUniversalTime() - $info.CreationDate.ToUniversalTime()).TotalSeconds) -ge 1) {
        throw 'Backend PID changed while verifying its identity; retry without stopping any process.'
    }
    return $process
}
