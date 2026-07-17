param(
    [string]$DllPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "work\probe-native"

New-Item -ItemType Directory -Force -Path $out | Out-Null

javac -d $out (Join-Path $root "mc-dlss-core\src\main\java\dev\mcdlss\core\*.java")

if ([string]::IsNullOrWhiteSpace($DllPath)) {
    java -cp $out dev.mcdlss.core.NativeProbeCli
} else {
    java -cp $out dev.mcdlss.core.NativeProbeCli $DllPath
}

exit $LASTEXITCODE
