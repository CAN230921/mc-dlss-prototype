$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$out = Join-Path $root "work\verify-java\$runId\core"
$allOut = Join-Path $root "work\verify-java\$runId\all"

function Assert-LastExitCode($label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$label failed with exit code $LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $allOut | Out-Null

javac -d $out `
    (Join-Path $root "mc-dlss-core\src\main\java\dev\mcdlss\core\*.java") `
    (Join-Path $root "mc-dlss-core\src\testSmoke\java\dev\mcdlss\core\*.java")
Assert-LastExitCode "Core javac"

java -cp $out dev.mcdlss.core.CoreContractSelfTest
Assert-LastExitCode "CoreContractSelfTest"

java -cp $out dev.mcdlss.core.NativeProbeCliSelfTest
Assert-LastExitCode "NativeProbeCliSelfTest"

java -cp $out dev.mcdlss.core.NativeAdapterIdentitySelfTest
Assert-LastExitCode "NativeAdapterIdentitySelfTest"

java -cp $out dev.mcdlss.core.NativeReadbackFingerprintSelfTest
Assert-LastExitCode "NativeReadbackFingerprintSelfTest"

java -cp $out dev.mcdlss.core.NativePersistentInteropSessionInfoSelfTest
Assert-LastExitCode "NativePersistentInteropSessionInfoSelfTest"

java -cp $out dev.mcdlss.core.NativePersistentFrameSessionSelfTest
Assert-LastExitCode "NativePersistentFrameSessionSelfTest"

java -cp $out dev.mcdlss.core.NativeTemporalConstantsSelfTest
Assert-LastExitCode "NativeTemporalConstantsSelfTest"

java -cp $out dev.mcdlss.core.NativePersistentMotionFrameSessionSelfTest
Assert-LastExitCode "NativePersistentMotionFrameSessionSelfTest"

java -cp $out dev.mcdlss.core.NativeLiveDlssContractSelfTest
Assert-LastExitCode "NativeLiveDlssContractSelfTest"

java -cp $out dev.mcdlss.core.NativeLiveDlssBridgeSelfTest
Assert-LastExitCode "NativeLiveDlssBridgeSelfTest"

java -cp $out dev.mcdlss.core.NativeBundleExtractorSelfTest
Assert-LastExitCode "NativeBundleExtractorSelfTest"

javac -d $allOut `
    (Join-Path $root "mc-dlss-core\src\main\java\dev\mcdlss\core\*.java") `
    (Join-Path $root "mc-dlss-debug\src\main\java\dev\mcdlss\debug\*.java") `
    (Join-Path $root "mc-dlss-debug\src\testSmoke\java\dev\mcdlss\debug\*.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\McDlssFabricEntrypoint.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftGlInteropSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftGlInteropOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftLiveInteropSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftLiveInteropOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftInteropTestPattern.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftInteropSemaphoreBarriers.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\RgbaFrameFingerprint.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\DepthFrameFingerprint.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftDepthExtractionShaderSource.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\CameraTemporalFrame.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\CameraTemporalReprojection.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftTemporalConstants.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftMotionVectorShaderSource.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MotionFrameFingerprint.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentMotionFrameCaptureSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentMotionFrameCaptureOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssPresentationState.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssResolutionContract.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssFramebufferRedirector.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\Fp16ColorFingerprint.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssOutputComposite.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssPresentationSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssPresentationOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssStartupFramePolicy.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssFrameBoundary.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveDlssViewportDimensions.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveUpscalerSlotScheduler.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\LiveUpscalerModeTracker.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftWorldColorCaptureSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftWorldColorCaptureOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\PersistentFenceValues.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\PersistentColorCaptureState.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentColorCaptureSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentColorCaptureOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\PersistentFrameCaptureState.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentFrameCaptureSnapshot.java") `
    (Join-Path $root "mc-dlss-fabric\src\main\java\dev\mcdlss\fabric\MinecraftPersistentFrameCaptureOverlayLines.java") `
    (Join-Path $root "mc-dlss-fabric\src\testSmoke\java\dev\mcdlss\fabric\*.java") `
    (Join-Path $root "mc-dlss-neoforge\src\main\java\dev\mcdlss\neoforge\McDlssNeoForgeEntrypoint.java") `
    (Join-Path $root "mc-dlss-neoforge\src\testSmoke\java\dev\mcdlss\neoforge\*.java")
Assert-LastExitCode "All-module javac"

java -cp $allOut dev.mcdlss.debug.DlssDebugSnapshotFactorySelfTest
Assert-LastExitCode "DlssDebugSnapshotFactorySelfTest"

java -cp $allOut dev.mcdlss.debug.LoaderStartupDiagnosticsSelfTest
Assert-LastExitCode "LoaderStartupDiagnosticsSelfTest"

java -cp $allOut dev.mcdlss.debug.DlssOverlayLinesSelfTest
Assert-LastExitCode "DlssOverlayLinesSelfTest"

java -cp $allOut dev.mcdlss.fabric.McDlssFabricEntrypointSelfTest
Assert-LastExitCode "McDlssFabricEntrypointSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftGlInteropSnapshotSelfTest
Assert-LastExitCode "MinecraftGlInteropSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftLiveInteropSnapshotSelfTest
Assert-LastExitCode "MinecraftLiveInteropSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.RgbaFrameFingerprintSelfTest
Assert-LastExitCode "RgbaFrameFingerprintSelfTest"

java -cp $allOut dev.mcdlss.fabric.DepthFrameFingerprintSelfTest
Assert-LastExitCode "DepthFrameFingerprintSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftDepthExtractionShaderSourceSelfTest
Assert-LastExitCode "MinecraftDepthExtractionShaderSourceSelfTest"

java -cp $allOut dev.mcdlss.fabric.CameraTemporalReprojectionSelfTest
Assert-LastExitCode "CameraTemporalReprojectionSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftTemporalConstantsSelfTest
Assert-LastExitCode "MinecraftTemporalConstantsSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftMotionVectorShaderSourceSelfTest
Assert-LastExitCode "MinecraftMotionVectorShaderSourceSelfTest"

java -cp $allOut dev.mcdlss.fabric.MotionFrameFingerprintSelfTest
Assert-LastExitCode "MotionFrameFingerprintSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftPersistentMotionFrameCaptureSnapshotSelfTest
Assert-LastExitCode "MinecraftPersistentMotionFrameCaptureSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssResolutionContractSelfTest
Assert-LastExitCode "LiveDlssResolutionContractSelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssFramebufferRedirectorSelfTest
Assert-LastExitCode "LiveDlssFramebufferRedirectorSelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssOutputCompositeSelfTest
Assert-LastExitCode "LiveDlssOutputCompositeSelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssPresentationSnapshotSelfTest
Assert-LastExitCode "LiveDlssPresentationSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.MixinPackageBoundarySelfTest
Assert-LastExitCode "MixinPackageBoundarySelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssStartupFramePolicySelfTest
Assert-LastExitCode "LiveDlssStartupFramePolicySelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssFrameBoundarySelfTest
Assert-LastExitCode "LiveDlssFrameBoundarySelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveDlssViewportDimensionsSelfTest
Assert-LastExitCode "LiveDlssViewportDimensionsSelfTest"

java -cp $allOut dev.mcdlss.fabric.LiveUpscalerSchedulingSelfTest
Assert-LastExitCode "LiveUpscalerSchedulingSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftWorldColorCaptureSnapshotSelfTest
Assert-LastExitCode "MinecraftWorldColorCaptureSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftPersistentColorCaptureSnapshotSelfTest
Assert-LastExitCode "MinecraftPersistentColorCaptureSnapshotSelfTest"

java -cp $allOut dev.mcdlss.fabric.MinecraftPersistentFrameCaptureSnapshotSelfTest
Assert-LastExitCode "MinecraftPersistentFrameCaptureSnapshotSelfTest"

java -cp $allOut dev.mcdlss.neoforge.McDlssNeoForgeEntrypointSelfTest
Assert-LastExitCode "McDlssNeoForgeEntrypointSelfTest"

Write-Host "Java verification passed"
