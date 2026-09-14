function Get-ValidatedReleasePath([string]$ProjectRoot, [string]$ReleaseDir) {
    $project = [System.IO.Path]::GetFullPath($ProjectRoot).TrimEnd([char[]]@('/', '\'))
    $release = [System.IO.Path]::GetFullPath($ReleaseDir).TrimEnd([char[]]@('/', '\'))
    $prefix = $project + [System.IO.Path]::DirectorySeparatorChar
    if (-not $release.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ReleaseDir must stay inside the project: $release"
    }
    $relative = $release.Substring($prefix.Length)
    $firstDirectory = ($relative -split '[\\/]')[0]
    if ($firstDirectory -in @('.git', '.github', 'backend', 'frontend', 'mobile', 'ops', 'plugins', 'deploy', 'docs', 'node_modules')) {
        throw "ReleaseDir must not overwrite a source or configuration directory: $release"
    }
    $ancestor = $release
    while ($ancestor -and -not [string]::Equals($ancestor, $project, [System.StringComparison]::OrdinalIgnoreCase)) {
        if (Test-Path -LiteralPath $ancestor) {
            $item = Get-Item -LiteralPath $ancestor -Force
            if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
                throw "ReleaseDir must not traverse a symbolic link or junction: $ancestor"
            }
        }
        $ancestor = [System.IO.Path]::GetDirectoryName($ancestor)
    }
    return $release
}
