# Milestone 3B Loader Dev Skeleton Design

## Goal

Turn the dependency-free Milestone 3A diagnostic surface into real Fabric and NeoForge mod development skeletons for Minecraft `1.21.1`.

This step proves that each loader module can be configured as a loader-aware Gradle subproject, package mod metadata, and initialize a tiny runtime entrypoint that reports the current `debugSnapshot()` status. It does not attempt DLSS rendering, Iris/Sodium hooks, or an in-game HUD yet.

## Sources Used

- Fabric docs for Minecraft `1.21.1` say Fabric provides Fabric Loader, Fabric API, and Fabric Loom, and the 1.21.1 project page says versions for Minecraft, mappings, Loader, and Loom should be queried from Fabric's develop site/meta services.
- Fabric 1.21.1 HUD docs show `HudRenderCallback` is the future path for HUD rendering, but that requires Fabric API and Minecraft client classes.
- NeoForge 1.21.1 docs describe `gradle.properties`, `neoforge.mods.toml`, the `javafml` loader, and `@Mod`-based mod setup; the docs also note 1.21 - 1.21.1 is no longer the latest maintained doc set.
- NeoForge getting-started docs recommend ModDevGradle or NeoGradle and warn first Gradle setup downloads Minecraft/dependencies.

## Version Choices

- Minecraft: `1.21.1`
- Fabric Loom: `1.17.13` from Fabric Maven metadata.
- Fabric Loader: `0.19.3`, latest stable entry returned for `1.21.1`.
- Yarn mappings: `1.21.1+build.3`, latest entry returned for `1.21.1`.
- NeoForge: `21.1.234`, matching the Airship Survival handoff.
- ModDevGradle: `2.0.141`, latest entry returned from NeoForge Maven metadata.

## Selected Approach

Use real loader plugins and metadata, but keep runtime code minimal:

- `mc-dlss-fabric` applies Fabric Loom, depends on Minecraft, Yarn, and Fabric Loader, packages `fabric.mod.json`, and adds a Fabric `ModInitializer` class.
- `mc-dlss-neoforge` applies ModDevGradle, depends on NeoForge `21.1.234`, packages `META-INF/neoforge.mods.toml`, and adds an `@Mod` class.
- Both runtime classes call the existing placeholder adapter's `debugSnapshot()` and print a concise startup diagnostic line.

The actual overlay is deferred to Milestone 3C because it needs client-side rendering APIs and must be tested against a launched dev client. This 3B step is the loader-aware build/install foundation.

## Non-Goals

- No Iris or Sodium runtime dependency in Gradle yet.
- No mixins.
- No HUD overlay or command UI.
- No resource capture or DLSS evaluation.
- No renderer-backend experiment.
- No direct dependency from `mc-dlss-core` or `mc-dlss-debug` on loader APIs.

## Testing

- Add a loader metadata verification script that fails if `fabric.mod.json` or `neoforge.mods.toml` are absent or do not point to the expected entrypoint/mod id.
- Keep dependency-free Java smoke tests for core/debug/adapter contracts.
- Run Gradle loader build tasks after network dependency resolution.
- Run native probe to ensure the JNI boundary still works.
