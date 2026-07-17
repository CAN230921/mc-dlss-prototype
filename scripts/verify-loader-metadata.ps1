$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$fabricMetadata = Join-Path $root "mc-dlss-fabric\src\main\resources\fabric.mod.json"
$fabricBuild = Join-Path $root "mc-dlss-fabric\build.gradle"
$neoForgeMetadata = Join-Path $root "mc-dlss-neoforge\src\main\resources\META-INF\neoforge.mods.toml"

if (-not (Test-Path $fabricMetadata)) {
    throw "Missing Fabric metadata: $fabricMetadata"
}

if (-not (Test-Path $neoForgeMetadata)) {
    throw "Missing NeoForge metadata: $neoForgeMetadata"
}

if (-not (Test-Path $fabricBuild)) {
    throw "Missing Fabric build file: $fabricBuild"
}

$fabric = Get-Content -Raw $fabricMetadata | ConvertFrom-Json
if ($fabric.id -ne "mc_dlss") {
    throw "Fabric mod id must be mc_dlss"
}
if ($fabric.name -ne "Minecraft DLSS Prototype") {
    throw "Fabric mod name must be Minecraft DLSS Prototype"
}
if (-not $fabric.entrypoints.main -or $fabric.entrypoints.main[0] -ne "dev.mcdlss.fabric.McDlssFabricMod") {
    throw "Fabric main entrypoint must be dev.mcdlss.fabric.McDlssFabricMod"
}
if (-not $fabric.entrypoints.client -or $fabric.entrypoints.client[0] -ne "dev.mcdlss.fabric.McDlssFabricClientMod") {
    throw "Fabric client entrypoint must be dev.mcdlss.fabric.McDlssFabricClientMod"
}

$fabricBuildText = Get-Content -Raw $fabricBuild
if ($fabricBuildText -notmatch 'rootProject\.file\("build/native/Release"\)') {
    throw "Fabric dev client must default to build/native/Release"
}
if ($fabricBuildText -notmatch 'vmArg\s+"-Djava\.library\.path=\$\{nativeDirectory\.absolutePath\}"') {
    throw "Fabric dev client must add the selected native directory to java.library.path"
}

$neoForge = Get-Content -Raw $neoForgeMetadata
if ($neoForge -notmatch 'modLoader\s*=\s*"javafml"') {
    throw "NeoForge metadata must use javafml"
}
if ($neoForge -notmatch 'modId\s*=\s*"mc_dlss"') {
    throw "NeoForge mod id must be mc_dlss"
}
if ($neoForge -notmatch 'displayName\s*=\s*"Minecraft DLSS Prototype"') {
    throw "NeoForge display name must be Minecraft DLSS Prototype"
}

Write-Host "Loader metadata verification passed"
