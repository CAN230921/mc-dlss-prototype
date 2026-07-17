param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root "tools\gradle-9.6.1\bin\gradle.bat"

if (-not (Test-Path $gradle)) {
    throw "Local Gradle was not found at $gradle. Reinstall it or run gradlew.bat when the Gradle distribution service is reachable."
}

& $gradle @GradleArgs
exit $LASTEXITCODE
