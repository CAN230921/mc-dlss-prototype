param(
    [string]$ReportPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root "build\native-streamline\Release"
$executable = Join-Path $runtime "mc_dlss_streamline_probe.exe"
$logDirectory = Join-Path $root "outputs\streamline-logs"
$logFile = Join-Path $logDirectory "sl.log"

if (-not (Test-Path $executable)) {
    throw "Streamline DLSS probe was not found at $executable. Run scripts/configure-native-streamline.ps1 first."
}

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root "outputs\streamline-dlss-probe-2026-07-11.json"
} elseif (-not [System.IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $root $ReportPath
}
$ReportPath = [System.IO.Path]::GetFullPath($ReportPath)

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReportPath) | Out-Null
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
if (Test-Path $logFile) {
    Remove-Item -LiteralPath $logFile
}

& $executable `
    --plugin-path $runtime `
    --log-path $logDirectory `
    --report $ReportPath | Out-Host

if ($LASTEXITCODE -ne 0) {
    throw "Streamline DLSS probe exited with code $LASTEXITCODE."
}

$report = Get-Content -Raw $ReportPath | ConvertFrom-Json
$checks = [ordered]@{
    schemaVersion = $report.schemaVersion -eq 1
    success = $report.success -eq $true
    streamlineVersion = $report.streamlineVersion -eq "2.12.0"
    projectId = $report.projectId -eq "7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e"
    dlssSupported = $report.dlssSupported -eq $true
    streamlineInitialized = $report.streamlineInitialized -eq $true
    d3dDeviceAccepted = $report.d3dDeviceAccepted -eq $true
    optionsAccepted = $report.optionsAccepted -eq $true
    constantsAccepted = $report.constantsAccepted -eq $true
    resourcesTagged = $report.resourcesTagged -eq $true
    evaluationCalled = $report.evaluationCalled -eq $true
    evaluationSucceeded = $report.streamlineDlssEvaluationSucceeded -eq $true
    commandSubmissionCompleted = $report.commandSubmissionCompleted -eq $true
    resourcesFreed = $report.resourcesFreed -eq $true
    shutdownSucceeded = $report.streamlineShutdownSucceeded -eq $true
    resultOk = $report.lastResult -eq "eOk"
    resolutionsDiffer =
        $report.optimalRenderResolution.width -ne $report.outputResolution.width -or
        $report.optimalRenderResolution.height -ne $report.outputResolution.height
}

$failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value })
if ($failedChecks.Count -gt 0) {
    $names = ($failedChecks | ForEach-Object { $_.Key }) -join ", "
    throw "Streamline report validation failed: $names"
}

if (-not (Test-Path $logFile)) {
    throw "Streamline log was not created at $logFile"
}
$log = Get-Content -Raw $logFile
foreach ($requiredText in @(
    "Loaded plugin 'sl.dlss'",
    "Created DLSSContext feature (1280,720)(optimal) -> (1920,1080)",
    "projectID 7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e"
)) {
    if (-not $log.Contains($requiredText)) {
        throw "Streamline log is missing evidence: $requiredText"
    }
}
if ($log.Contains("[streamline][error]")) {
    throw "Streamline log contains an error-level entry."
}

Write-Host "Streamline DLSS probe passed"
Write-Host "adapter=$($report.adapterName)"
Write-Host "input=$($report.optimalRenderResolution.width)x$($report.optimalRenderResolution.height)"
Write-Host "output=$($report.outputResolution.width)x$($report.outputResolution.height)"
Write-Host "report=$ReportPath"
Write-Host "log=$logFile"
