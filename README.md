# MC DLSS Prototype

Experimental Windows rendering bridge for Minecraft Java Edition 1.21.1. The
project connects Iris/Sodium render targets to D3D12 upscalers and explores
DLSS Super Resolution, AMD FidelityFX Super Resolution 3, and frame generation.

This is research software, not a finished performance mod. Keep a normal
Minecraft profile available and back up worlds before testing.

## Status

- NeoForge 21.1.x is the active integration target.
- Fabric contains the earlier renderer and temporal-input implementation.
- Iris 1.8.x and Sodium 0.8.x render targets are captured through loader mixins.
- OpenGL/D3D12 shared resources carry color, depth, and motion information.
- NVIDIA Streamline DLSS SR and AMD FidelityFX FSR3 are optional native backends.
- Same-window DXGI presentation is experimental and guarded by
  `-DmcDlss.experimentalDxgiPresentation=true`.
- The custom native DLLs are currently unsigned. Windows App Control may block
  them until release signing is in place.

The current FSR3/DXGI path has passed its standalone same-HWND presentation
probe on an RTX 4070 Laptop GPU. In-game frame generation remains experimental.

## Repository Layout

- `mc-dlss-core`: backend-neutral Java contracts, configuration, and JNI API.
- `mc-dlss-debug`: compact runtime diagnostics and overlay models.
- `mc-dlss-fabric`: Fabric renderer integration and temporal-input work.
- `mc-dlss-neoforge`: active NeoForge/Iris integration and settings UI.
- `mc-dlss-native`: C++ JNI, OpenGL/D3D12 interop, Streamline, FSR3, and probes.
- `scripts`: local build, verification, probe, and signing helpers.
- `docs`: design history, experiments, and current implementation reports.

## Build

Requirements:

- Windows 10 or 11 x64
- JDK 21
- Visual Studio 2022 Build Tools with Desktop development with C++
- CMake

Run the dependency-free Java checks:

```powershell
.\scripts\verify-java.ps1
.\scripts\verify-loader-metadata.ps1
```

Build the SDK-independent native library:

```powershell
.\scripts\configure-native.ps1
```

Loader builds use Gradle:

```powershell
.\gradlew.bat build --console=plain --no-daemon --no-parallel
```

Iris/Sodium development jars and proprietary or separately licensed SDKs are
not committed. See [Building](docs/BUILDING.md) for the optional backend setup.

## Safety And Support

Experimental presentation takeover can produce incorrect frames, flashing, or
driver/device-loss errors. It is disabled unless explicitly enabled. Do not use
experimental builds in photosensitive environments.

Please include Minecraft, loader, Iris/Sodium, GPU, driver, backend, and the
relevant `latest.log` excerpt in bug reports. Remove access tokens, usernames,
world data, and absolute personal paths before posting logs.

## Licensing

Project-authored source is available under the [MIT License](LICENSE).
Third-party SDKs and runtime binaries retain their own licenses and trademarks;
see [Third-party notices](THIRD_PARTY_NOTICES.md). Release artifacts must not be
published until every bundled runtime has been checked for redistribution.

This project is not affiliated with or endorsed by Mojang Studios, Microsoft,
NVIDIA, AMD, the Iris project, or the Sodium project.
