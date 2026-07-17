param(
    [string]$StreamlineRoot = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$workspace = Split-Path -Parent $root
$vswhere = "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"

if ([string]::IsNullOrWhiteSpace($StreamlineRoot)) {
    $StreamlineRoot = Join-Path $workspace "work\upstream\streamline-sdk-v2.12.0"
}
$StreamlineRoot = [System.IO.Path]::GetFullPath($StreamlineRoot)

if (-not (Test-Path (Join-Path $StreamlineRoot "include\sl.h"))) {
    throw "Streamline 2.12 SDK was not found at $StreamlineRoot"
}
if (-not (Test-Path $vswhere)) {
    throw "vswhere.exe was not found. Install Visual Studio Build Tools first."
}

$vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ([string]::IsNullOrWhiteSpace($vsPath)) {
    throw "Visual Studio C++ Build Tools were not found."
}

$vsDevCmd = Join-Path $vsPath "Common7\Tools\VsDevCmd.bat"
$source = Join-Path $root "mc-dlss-native"
$build = Join-Path $root "build\native-streamline"

$configure = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && cmake -S `"$source`" -B `"$build`" -A x64 -DSTREAMLINE_ROOT=`"$StreamlineRoot`""
$compile = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && cmake --build `"$build`" --config Release"
$test = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && ctest --test-dir `"$build`" -C Release --output-on-failure"

cmd.exe /c $configure
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

cmd.exe /c $compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

cmd.exe /c $test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Streamline native build and tests finished"
Write-Host "sdk=$StreamlineRoot"
