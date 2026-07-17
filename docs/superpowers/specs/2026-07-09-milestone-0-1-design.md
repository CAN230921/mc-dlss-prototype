# Minecraft DLSS Prototype Milestone 0/1 Design

## Goal

Create a clean Windows-first research prototype named `mc-dlss-prototype` that can validate project structure, Java/native boundary shape, and NVIDIA Streamline baseline requirements before any Minecraft renderer hooks are attempted.

## Scope

This design covers Milestone 0 and Milestone 1 only.

- Minecraft target: `1.21.1`
- Iris target: `1.8.x`
- Sodium target: `0.8.x`
- Loaders: Fabric and NeoForge
- OS target: Windows 10/11 x64
- GPU target: RTX 40 series, first test RTX 4070 Laptop
- DLSS feature target: Super Resolution only
- Deferred feature: Frame Generation

No renderer hook, shaderpack hook, OpenGL interop experiment, or in-game resource capture belongs in this first pass.

## Recommended Approach

Use a Gradle multi-project repository with shared Java contracts and loader-specific placeholder modules, plus a separate CMake native project. The Java/native boundary starts with a deliberately small probe API instead of trying to pass graphics resources. This keeps the first milestone focused on buildability, DLL loading, and capability-report plumbing.

The alternative would be to start from a Fabric or NeoForge template, but that risks loader-specific decisions leaking into the shared design. A third option would be a native-first Streamline playground, but that would delay proving the Java JNI boundary that the mod will need anyway.

## Architecture

`mc-dlss-core` owns shared Java contracts: quality modes, native probe result objects, lifecycle-facing bridge interfaces, and a small command-line self-test.

`mc-dlss-native` owns the C++ DLL surface. For Milestone 0 it exposes version and probe strings through JNI. Streamline integration remains documented but not linked until Milestone 1 confirms SDK location, driver support, and sample behavior.

`mc-dlss-fabric` and `mc-dlss-neoforge` are placeholder adapter modules. They compile as plain Java modules initially and define where loader entrypoints will live later, without pulling Loom or NeoGradle until the clean probe path exists.

`mc-dlss-debug` owns shared debug snapshot models that both loader adapters can display once Minecraft clients are introduced.

## Data Flow

For Milestone 0, Java calls a native probe function and receives a structured result. No graphics resource handle crosses the boundary yet.

For Milestone 1, the developer runs the official NVIDIA Streamline/DLSS sample outside Minecraft, records GPU, driver, Windows, Streamline, and DLSS SR support details, and stores the outcome in a local note.

## Error Handling

The core layer treats the native bridge as optional. Missing DLLs, architecture mismatch, and native exceptions should produce structured probe failures rather than crashing the Java process during normal diagnostics.

The native layer returns conservative status strings until Streamline is linked. Once Streamline is introduced, errors should be mapped to stable Java-facing codes instead of raw SDK-specific values.

## Testing

The first automated test is a dependency-free Java self-test that verifies core contracts. It can be compiled and run with `javac`/`java` even if Gradle is not installed.

Native build verification is documented through CMake commands because Visual Studio and CMake may need to be installed outside this workspace.

## Open Questions For Later Milestones

- Whether Iris/Sodium 1.21.1 exposes compatible native graphics resources for DLSS SR.
- Whether a Vulkan backend, renderer fork, or interop layer is required.
- Whether camera-only motion vectors are useful enough for a first SR evaluation.
- Where exactly UI/HUD composition can be kept out of the low-resolution scene target.

## Self-Review

- No placeholder implementation work is hidden in this design.
- Scope is limited to Milestone 0 and Milestone 1.
- The design keeps Fabric and NeoForge as adapters, not divergent implementations.
- The design explicitly defers renderer hooks until the Java/native and NVIDIA baseline are known.
