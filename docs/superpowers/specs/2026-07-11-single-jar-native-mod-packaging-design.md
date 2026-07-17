# Single-JAR Native Mod Packaging Design

## Goal

Ship the verified Fabric 1.21.1 Live DLSS prototype as one mod JAR that can be copied into a launcher instance's `mods` directory without manual JVM arguments, `PATH` edits, or writes outside the Minecraft instance.

The first supported production target is Windows x64 with an NVIDIA RTX GPU. Unsupported operating systems, architectures, GPUs, or missing runtime capabilities must disable Live DLSS and leave Minecraft usable.

## Artifact Layout

The remapped Fabric JAR contains Java classes plus a versioned native bundle under:

```text
/native/windows-x86_64/
  manifest.properties
  mc_dlss_bootstrap.dll
  mc_dlss_native.dll
  sl.interposer.dll
  sl.common.dll
  sl.dlss.dll
  sl.pcl.dll
  nvngx_dlss.dll
```

`manifest.properties` records the package version, file names, byte lengths, and SHA-256 hashes. Gradle generates it from the exact Release binaries included in the JAR.

## Extraction

Before constructing `NativeLibraryBridge`, Java checks `os.name` and `os.arch`, reads the embedded manifest, and selects a cache directory:

```text
<minecraft-run-directory>/mc-dlss/natives/<bundle-sha256>/
```

Extraction uses a temporary sibling file followed by an atomic move. An existing cache is reused only when every length and SHA-256 value matches the manifest. A partially written or mismatched cache is repaired file by file. The loader never deletes unrelated directories and does not modify global environment variables.

Concurrent launch attempts coordinate through a bundle lock file. Failure to create or verify the cache returns an unavailable diagnostic instead of terminating Minecraft.

## Native Bootstrap

`mc_dlss_bootstrap.dll` links only against Windows system libraries. Java loads it by absolute path with `System.load`.

The bootstrap receives the extracted native directory and main JNI DLL path. It:

1. validates both paths are absolute and within the same extracted bundle directory;
2. enables safe default DLL search directories;
3. adds the bundle directory with `AddDllDirectory`;
4. loads `mc_dlss_native.dll` with `LoadLibraryExW` and the user-directory search flag;
5. retains the directory cookie and main module for the process lifetime.

The existing JNI exports remain in `mc_dlss_native.dll`. Loading the main module through the bootstrap makes those native methods visible to the JVM while allowing Windows to resolve Streamline dependencies from the bundle directory.

Repeated initialization is idempotent. A second call with a different bundle path fails with a clear diagnostic instead of replacing a live native runtime.

## Java Startup And Configuration

Native bundle loading happens during Fabric client initialization before `McDlssFabricEntrypoint` probes the JNI bridge.

Configuration lives at:

```text
config/mc-dlss.properties
```

Initial keys:

```properties
enabled=true
mode=quality
debugOverlay=false
```

Missing configuration creates in-memory defaults; the first successful client initialization writes the file. Invalid values fall back to defaults and are reported in the log. Only Quality mode is accepted in this milestone.

Development JVM properties continue to override the file so the existing automated tests and failure injection remain usable. A packaged release enables Live DLSS from configuration rather than requiring `-PmcDlssLivePresentation`.

## Failure Behaviour

Every packaging stage returns a structured status containing availability, stage, and message. Expected stages include platform, manifest, extraction, verification, bootstrap, main-library, and runtime probe.

If any stage fails:

- Live DLSS is not installed into render hooks;
- Minecraft continues with its original framebuffer and viewport;
- one concise log record explains the failure;
- the debug overlay may show the same status when enabled;
- no partially initialized presentation controller is retained.

The loader never falls back to downloading binaries at runtime.

## Build Pipeline

The distribution task depends on:

1. the Streamline-enabled Release native build;
2. bootstrap Release build;
3. native manifest generation and hash verification;
4. Fabric remapped JAR creation;
5. JAR-content verification.

The output name is:

```text
mc-dlss-fabric-0.1.0-windows-x86_64.jar
```

The regular development JAR remains available and does not silently embed stale Debug binaries.

## Verification

Automated checks cover:

- platform selection and unsupported-platform fallback;
- manifest parsing, path traversal rejection, hash mismatch repair, and cache reuse;
- bootstrap path validation and idempotence;
- main JNI load from a directory absent from `PATH` and `java.library.path`;
- distribution JAR inventory and embedded-file hashes;
- existing Java, Gradle, default native, and Streamline native suites.

Final acceptance uses a clean Fabric 1.21.1 instance with only Fabric API and the distribution JAR. No extra JVM arguments or copied DLLs are allowed. The client must enter a world, reach `COMPLETE ready=true`, survive a window resize and session rebuild, and preserve the corrected block-outline and breaking-overlay alignment.

## Scope Boundaries

This milestone does not add NeoForge packaging, Linux support, XeSS, FSR 3, frame generation, an in-game settings screen, automatic updates, or runtime downloads. The bundle interfaces may later host additional vendor backends, but the shipped binary set and acceptance claim are DLSS-only.
