param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("26.3", "26.2", "26.1.2", "1.21.11")]
    [string]$MinecraftVersion
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$overlay = Join-Path $root "versions\$MinecraftVersion.properties"
$gradleProps = Join-Path $root "gradle.properties"

if (-not (Test-Path $overlay)) {
    throw "Missing version overlay: $overlay"
}

Write-Host "Applying version overlay $MinecraftVersion..."
$lines = Get-Content $gradleProps
$overlayMap = @{}
Get-Content $overlay | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $kv = $_.Split('=', 2)
    if ($kv.Length -eq 2) { $overlayMap[$kv[0].Trim()] = $kv[1].Trim() }
}

$updated = foreach ($line in $lines) {
    if ($line -match '^\s*#' -or $line -match '^\s*$' -or $line -notmatch '=') {
        $line
        continue
    }
    $key = ($line.Split('=', 2)[0]).Trim()
    if ($overlayMap.ContainsKey($key)) {
        "$key=$($overlayMap[$key])"
        $overlayMap.Remove($key)
    } else {
        $line
    }
}
Set-Content -Path $gradleProps -Value $updated -Encoding UTF8

Write-Host "Building Fabric + NeoForge for Minecraft $MinecraftVersion..."
& "$root\gradlew.bat" clean build --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$out = Join-Path $root "dist\$MinecraftVersion"
New-Item -ItemType Directory -Force -Path $out | Out-Null
Get-ChildItem "$root\fabric\build\libs\itemrush-fabric-*.jar" |
    Where-Object { $_.Name -notmatch "javadoc|sources" } |
    ForEach-Object { Copy-Item $_.FullName $out -Force; Write-Host " -> $($_.Name)" }
Get-ChildItem "$root\neoforge\build\libs\itemrush-neoforge-*.jar" |
    Where-Object { $_.Name -notmatch "javadoc|sources" } |
    ForEach-Object { Copy-Item $_.FullName $out -Force; Write-Host " -> $($_.Name)" }
