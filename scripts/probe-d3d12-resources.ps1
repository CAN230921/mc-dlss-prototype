param(
    [string]$ReportPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$executable = Join-Path $root "build\native\Release\mc_dlss_resource_probe.exe"

if (-not (Test-Path $executable)) {
    throw "D3D12 resource probe was not found at $executable. Run scripts/configure-native.ps1 first."
}

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root "outputs\d3d12-resource-probe-2026-07-10.json"
} elseif (-not [System.IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $root $ReportPath
}

$ReportPath = [System.IO.Path]::GetFullPath($ReportPath)
$reportDirectory = Split-Path -Parent $ReportPath
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null

& $executable `
    --input-width 1280 `
    --input-height 720 `
    --output-width 1920 `
    --output-height 1080 `
    --report $ReportPath | Out-Host

if ($LASTEXITCODE -ne 0) {
    throw "D3D12 resource probe exited with code $LASTEXITCODE."
}

$report = Get-Content -Raw $ReportPath | ConvertFrom-Json
$checks = [ordered]@{
    schemaVersion = $report.schemaVersion -eq 1
    success = $report.success -eq $true
    backend = $report.backend -eq "D3D12"
    hardwareAdapter = $report.adapter.software -eq $false
    deviceAvailable = $report.deviceAvailable -eq $true
    commandContextAvailable = $report.commandContextAvailable -eq $true
    inputColor = $report.resources.inputColor -eq $true
    depth = $report.resources.depth -eq $true
    motionVectors = $report.resources.motionVectors -eq $true
    outputColor = $report.resources.outputColor -eq $true
    commandSubmissionCompleted = $report.commandSubmissionCompleted -eq $true
    resolutionsDiffer =
        $report.inputResolution.width -ne $report.outputResolution.width -or
        $report.inputResolution.height -ne $report.outputResolution.height
}

$failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value })
if ($failedChecks.Count -gt 0) {
    $names = ($failedChecks | ForEach-Object { $_.Key }) -join ", "
    throw "D3D12 report validation failed: $names"
}

Write-Host "D3D12 resource probe passed"
Write-Host "adapter=$($report.adapter.name)"
Write-Host "input=$($report.inputResolution.width)x$($report.inputResolution.height)"
Write-Host "output=$($report.outputResolution.width)x$($report.outputResolution.height)"
Write-Host "report=$ReportPath"
