# Windows Release Signing

The custom PE files must be Authenticode-signed before public distribution:

- `mc_dlss_bootstrap.dll`
- `mc_dlss_native.dll`
- standalone project-authored probe executables, when distributed

Vendor DLLs must retain their original vendor signatures. Never re-sign them.

Use an RSA code-signing identity that chains to a Windows-trusted public root.
Self-signed certificates are suitable only for administrator-managed test
machines and do not make a public release trusted.

The helper accepts a certificate already installed in the current user's
certificate store:

```powershell
.\scripts\sign-native.ps1 `
  -CertificateThumbprint YOUR_CERTIFICATE_THUMBPRINT `
  -TimestampUrl https://your-ca.example/timestamp `
  -Files @(
    ".\build\native-fsr3\Release\mc_dlss_bootstrap.dll"
    ".\build\native-fsr3\Release\mc_dlss_native.dll"
  )
```

Sign before running the Gradle native-bundle packaging task because signing
changes each DLL's size and SHA-256 hash.

Never commit a PFX file, private key, token PIN, cloud signing credential, or
certificate password. CI signing should use the selected signing provider's
short-lived identity mechanism and protected repository environments.

## SignPath Foundation

The prepared SignPath artifact configuration is
[`../.signpath/artifact-configuration.xml`](../.signpath/artifact-configuration.xml).
It signs only the two project-authored DLLs and verifies, without re-signing,
the three AMD FidelityFX DLLs. The manually triggered
`Release signed FSR3 Mod` workflow remains disabled until the SignPath
Foundation application is approved and repository variables are configured.

See the [Chinese setup checklist](SIGNPATH_SETUP.zh-CN.md) for the required
SignPath project slugs, GitHub secret, protected environment, and release flow.
