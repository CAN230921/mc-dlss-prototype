param(
    [Parameter(Mandatory = $true)]
    [string]$UnsignedDirectory,

    [Parameter(Mandatory = $true)]
    [string]$SignedDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedVersion
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectFiles = @("mc_dlss_bootstrap.dll", "mc_dlss_native.dll")
$upstreamFiles = @(
    "amd_fidelityfx_loader_dx12.dll",
    "amd_fidelityfx_upscaler_dx12.dll",
    "amd_fidelityfx_framegeneration_dx12.dll"
)

function Resolve-SingleFile([string]$Directory, [string]$Name) {
    $matches = @(Get-ChildItem -LiteralPath $Directory -Recurse -File -Filter $Name)
    if ($matches.Count -ne 1) {
        throw "Expected exactly one $Name under $Directory, found $($matches.Count)."
    }
    return $matches[0]
}

foreach ($name in $projectFiles) {
    $file = Resolve-SingleFile $SignedDirectory $name
    $signature = Get-AuthenticodeSignature -LiteralPath $file.FullName
    if ($signature.Status -ne "Valid") {
        throw "$name does not have a valid Authenticode signature: $($signature.StatusMessage)"
    }
    if ($signature.SignerCertificate.Subject -notmatch "SignPath Foundation") {
        throw "$name was not signed by SignPath Foundation: $($signature.SignerCertificate.Subject)"
    }
    if ($file.VersionInfo.ProductName -ne "MC DLSS Prototype") {
        throw "$name has unexpected ProductName metadata."
    }
    if ($file.VersionInfo.ProductVersion -ne $ExpectedVersion) {
        throw "$name has version '$($file.VersionInfo.ProductVersion)', expected '$ExpectedVersion'."
    }
}

foreach ($name in $upstreamFiles) {
    $unsigned = Resolve-SingleFile $UnsignedDirectory $name
    $signed = Resolve-SingleFile $SignedDirectory $name
    $before = (Get-FileHash -LiteralPath $unsigned.FullName -Algorithm SHA256).Hash
    $after = (Get-FileHash -LiteralPath $signed.FullName -Algorithm SHA256).Hash
    if ($before -ne $after) {
        throw "Upstream file $name changed during signing."
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $signed.FullName
    if ($signature.Status -ne "Valid") {
        throw "Upstream file $name lost its valid Authenticode signature."
    }
    if ($signature.SignerCertificate.Subject -match "SignPath Foundation") {
        throw "Upstream file $name was incorrectly re-signed by this project."
    }
}

Write-Output "Verified SignPath signatures, product metadata, and unchanged upstream binaries."
