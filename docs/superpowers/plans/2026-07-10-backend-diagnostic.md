# Backend Diagnostic Implementation Plan

**Goal:** Add a small, tested core diagnostic that truthfully reports the current Iris/Sodium OpenGL backend as blocked for direct Streamline DLSS SR, then document the next renderer-backend spike criteria.

**Architecture:** Keep all runtime-facing contracts inside `mc-dlss-core`, with no loader or Minecraft dependencies. Use smoke tests under `src/testSmoke` because the project already verifies core contracts that way.

**Tech Stack:** Java records/enums, existing PowerShell verification scripts, existing Gradle local wrapper script.

## Constraints

- Do not implement Minecraft renderer hooks in this step.
- Do not pretend OpenGL GL texture IDs are Streamline-compatible resources.
- Do not add external dependencies.
- Keep Fabric and NeoForge adapters untouched unless compile verification requires otherwise.

## Tasks

- [x] Add failing smoke-test coverage for the OpenGL blocked diagnostic.
- [x] Add core backend/status enums and diagnostic record.
- [x] Update status docs with the new diagnostic contract and next spike criteria.
- [x] Run Java, Gradle, and native probe verification.
