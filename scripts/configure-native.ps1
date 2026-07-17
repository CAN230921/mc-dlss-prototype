$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$vswhere = "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"

if (-not (Test-Path $vswhere)) {
    throw "vswhere.exe was not found. Install Visual Studio Build Tools first."
}

$vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ([string]::IsNullOrWhiteSpace($vsPath)) {
    throw "Visual Studio C++ Build Tools were not found."
}

$vsDevCmd = Join-Path $vsPath "Common7\Tools\VsDevCmd.bat"
if (-not (Test-Path $vsDevCmd)) {
    throw "VsDevCmd.bat was not found at $vsDevCmd"
}

$source = Join-Path $root "mc-dlss-native"
$build = Join-Path $root "build\native"

$configure = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && cmake -S `"$source`" -B `"$build`" -A x64 -DSTREAMLINE_ROOT="
$compile = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && cmake --build `"$build`" --config Release"
$test = "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && ctest --test-dir `"$build`" -C Release --output-on-failure"

cmd.exe /c $configure
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

cmd.exe /c $compile
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

cmd.exe /c $test
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Native build and tests finished"
