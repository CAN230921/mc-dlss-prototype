param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9A-Fa-f]{40}$')]
    [string]$CertificateThumbprint,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https://')]
    [string]$TimestampUrl,

    [Parameter(Mandatory = $true)]
    [string[]]$Files
)

$ErrorActionPreference = "Stop"

$signTool = Get-ChildItem `
    "C:\Program Files (x86)\Windows Kits\10\bin" `
    -Recurse -Filter signtool.exe |
    Where-Object { $_.FullName -match '\\x64\\signtool\.exe$' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $signTool) {
    throw "x64 signtool.exe was not found. Install the Windows SDK."
}

$certificate = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert |
    Where-Object { $_.Thumbprint -eq $CertificateThumbprint } |
    Select-Object -First 1
if (-not $certificate) {
    throw "The requested code-signing certificate is not in Cert:\CurrentUser\My."
}
if (-not $certificate.HasPrivateKey) {
    throw "The requested certificate has no accessible private key."
}

foreach ($file in $Files) {
    $resolved = (Resolve-Path -LiteralPath $file).Path
    & $signTool.FullName sign /sha1 $CertificateThumbprint /fd SHA256 `
        /tr $TimestampUrl /td SHA256 /v $resolved
    if ($LASTEXITCODE -ne 0) {
        throw "Signing failed for $resolved with exit code $LASTEXITCODE."
    }

    & $signTool.FullName verify /pa /all /v $resolved
    if ($LASTEXITCODE -ne 0) {
        throw "Signature verification failed for $resolved with exit code $LASTEXITCODE."
    }
}

Write-Host "Signed and verified $($Files.Count) file(s)."
