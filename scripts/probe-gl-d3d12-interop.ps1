param(
    [string]$ReportPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$executable = Join-Path $root "build\native\Release\mc_dlss_gl_d3d12_interop_probe.exe"

if (-not (Test-Path $executable)) {
    throw "OpenGL-D3D12 interop probe was not found at $executable. Run scripts/configure-native.ps1 first."
}

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root "outputs\gl-d3d12-interop-probe-2026-07-11.json"
} elseif (-not [System.IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $root $ReportPath
}

$ReportPath = [System.IO.Path]::GetFullPath($ReportPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReportPath) | Out-Null

& $executable `
    --width 64 `
    --height 64 `
    --report $ReportPath | Out-Host

if ($LASTEXITCODE -ne 0) {
    throw "OpenGL-D3D12 interop probe exited with code $LASTEXITCODE."
}

$report = Get-Content -Raw $ReportPath | ConvertFrom-Json
$checks = [ordered]@{
    schemaVersion = $report.schemaVersion -eq 1
    success = $report.success -eq $true
    adapter = -not [string]::IsNullOrWhiteSpace($report.adapterName)
    openGlVendor = -not [string]::IsNullOrWhiteSpace($report.openGlVendor)
    openGlRenderer = -not [string]::IsNullOrWhiteSpace($report.openGlRenderer)
    openGlVersion = -not [string]::IsNullOrWhiteSpace($report.openGlVersion)
    memoryObjectExtension = $report.requiredExtensions.GL_EXT_memory_object -eq $true
    memoryObjectWin32Extension = $report.requiredExtensions.GL_EXT_memory_object_win32 -eq $true
    semaphoreExtension = $report.requiredExtensions.GL_EXT_semaphore -eq $true
    semaphoreWin32Extension = $report.requiredExtensions.GL_EXT_semaphore_win32 -eq $true
    entryPointsLoaded = $report.entryPointsLoaded -eq $true
    sharedTextureCreated = $report.sharedTextureCreated -eq $true
    sharedFenceCreated = $report.sharedFenceCreated -eq $true
    memoryImported = $report.memoryImported -eq $true
    semaphoreImported = $report.semaphoreImported -eq $true
    openGlWriteSubmitted = $report.openGlWriteSubmitted -eq $true
    d3d12WaitCompleted = $report.d3d12WaitCompleted -eq $true
    readbackMatched = $report.readbackMatched -eq $true
    d3d12SignalCompleted = $report.d3d12SignalCompleted -eq $true
    openGlWaitCompleted = $report.openGlWaitCompleted -eq $true
}

$failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value })
if ($failedChecks.Count -gt 0) {
    $names = ($failedChecks | ForEach-Object { $_.Key }) -join ", "
    throw "OpenGL-D3D12 interop report validation failed: $names"
}

Write-Host "OpenGL-D3D12 interop probe passed"
Write-Host "adapter=$($report.adapterName)"
Write-Host "opengl=$($report.openGlRenderer)"
Write-Host "version=$($report.openGlVersion)"
Write-Host "report=$ReportPath"
