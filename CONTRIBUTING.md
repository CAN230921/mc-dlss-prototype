# Contributing

Thanks for helping with the renderer experiment.

## Development Rules

1. Keep experimental presentation paths behind explicit runtime gates.
2. Do not commit Minecraft instances, saves, screenshots, logs, SDK archives,
   native binaries, signing keys, launcher files, or account tokens.
3. Do not include patched or unofficial vendor runtimes.
4. Keep loader-specific hooks in their loader module and shared contracts in
   `mc-dlss-core`.
5. Add focused tests for resource ownership, frame ordering, reset behavior,
   and configuration changes.

## Before A Pull Request

```powershell
.\scripts\verify-java.ps1
.\scripts\verify-loader-metadata.ps1
.\gradlew.bat build --console=plain --no-daemon --no-parallel
```

Native changes should also pass the SDK-independent native build and relevant
GPU probes on real Windows hardware. State which tests were not run.

Bug reports and logs must be scrubbed of tokens, personal paths, usernames, and
world data before upload.
