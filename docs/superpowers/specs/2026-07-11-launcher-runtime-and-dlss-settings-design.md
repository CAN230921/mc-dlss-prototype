# Formal Launcher Runtime and DLSS Settings Design

## Goal

Make the packaged NeoForge mod work from a normal Minecraft launcher and give the player a Chinese DLSS settings page inside Sodium video settings. A failed DLSS initialization must not retry every tick or make the game unusable.

## Confirmed Root Cause

The packaged bootstrap extracts and loads `mc_dlss_native.dll`, then records its absolute path in `mcDlss.packagedNativePath`. The live session controller currently ignores that property and passes the first entry of `java.library.path` to Streamline. In a development run that entry is the project Streamline directory; in a normal launcher it is the launcher's native directory. Streamline therefore searches the wrong directory and the Java bridge receives invalid session data.

## Runtime Design

Resolve the Streamline plugin directory in this order:

1. Parent directory of `mcDlss.packagedNativePath` when the packaged bundle is active.
2. First valid directory in `java.library.path` for development runs.
3. A Chinese diagnostic error when neither source is usable.

Session-open failures enter a retry cooldown instead of reopening every client tick. A target-generation change, output-size change, configuration change, or explicit user action clears the cooldown. Disabling DLSS closes all shared resources immediately and stops frame submission.

## Settings Design

Add a `DLSS` page to the Sodium video settings navigation. All visible labels and descriptions are Simplified Chinese.

Controls:

- `启用 DLSS`: master toggle, disabled by default for the first packaged build.
- `质量模式`: `质量`, `平衡`, `性能`, `超级性能`.
- `运行状态`: read-only Chinese status derived from the live session controller.

Applying changed settings persists them in the game configuration directory and requests one controlled session rebuild. Quality mode is passed to the native session/evaluation path; it is not a display-only preference.

## Alternatives Considered

- Independent mod configuration screen: simpler dependency boundary, but harder to discover and disconnected from Iris/Sodium controls.
- Keybind-only controls: fast to implement, but gives no clear state or quality selection.
- Sodium-integrated page: selected because the user already manages Iris and rendering options there.

## Error Handling

- Missing shader pack: show `请先启用 Iris 光影包`; do not open a session.
- Missing packaged native directory: show a specific Chinese path error and enter cooldown.
- Invalid native session data: include the returned shape and dimensions in logs, close partial resources, and enter cooldown.
- Unsupported hardware/runtime: keep vanilla/Iris rendering active and leave DLSS disabled without crashing Minecraft.

## Verification

- Core tests cover packaged-directory precedence, development fallback, invalid paths, configuration defaults, Chinese quality labels, persistence values, and retry cooldown behavior.
- NeoForge compile verifies the Sodium integration against the exact Iris/Sodium versions.
- A clean distribution probe verifies extraction and native loading with an empty `java.library.path`.
- Final acceptance uses a normal launcher: enable a shader pack, enable DLSS from the Chinese page, apply settings, and verify the overlay reaches `ACTIVE` without repeated session opens or severe frame stalls.
