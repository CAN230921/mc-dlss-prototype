# Building

## Base Toolchain

Install JDK 21, CMake, and Visual Studio 2022 Build Tools with the x64 C++
toolchain. The repository's Gradle wrapper supplies Gradle.

Run source-only checks with:

```powershell
.\scripts\verify-java.ps1
.\scripts\verify-loader-metadata.ps1
```

The SDK-independent native configuration is:

```powershell
.\scripts\configure-native.ps1
```

## Loader Dependencies

The NeoForge integration currently compiles against the exact Iris 1.8.14
beta and Sodium 0.8.12 beta jars used during development. Place these files in
`work/` using the filenames referenced by `mc-dlss-neoforge/build.gradle`.
The directory is intentionally ignored by Git.

Use `-PmcDlssUseIrisRuntime` only for a development client that should load
those jars at runtime.

## NVIDIA Streamline

Obtain a compatible Streamline SDK directly from NVIDIA, accept its terms, and
keep it outside the repository. Configure with:

```powershell
.\scripts\configure-native-streamline.ps1 -StreamlineRoot C:\path\to\streamline
```

No NVIDIA header, library, plugin, or runtime is downloaded by this repository.

## AMD FidelityFX

Obtain the FidelityFX SDK directly from AMD and point CMake at its FidelityFX
kit directory:

```powershell
cmake -S mc-dlss-native -B build/native-fsr3 -A x64 `
  -DFIDELITYFX_ROOT=C:\path\to\FidelityFX
cmake --build build/native-fsr3 --config Release
```

The expected kit contains `api/include`, `upscalers/include`,
`framegeneration/include`, and `signedbin`.

## Experimental NeoForge Client

```powershell
.\gradlew.bat :mc-dlss-neoforge:runClient `
  -PmcDlssUseIrisRuntime -PmcDlssUseFsr3Native `
  --console=plain --no-daemon --no-parallel
```

The same-window DXGI path is enabled for the Gradle development client. For a
normal launcher it additionally requires this JVM argument:

```text
-DmcDlss.experimentalDxgiPresentation=true
```

Do not enable that flag unless the signed native bundle is installed and the
fallback path has been tested.
