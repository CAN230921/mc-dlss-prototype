# Third-Party Notices

This repository contains integration code for third-party projects. Those
projects are not relicensed by this repository's MIT license.

## Minecraft, NeoForge, Fabric, Iris, And Sodium

Minecraft is distributed under Mojang/Microsoft terms. NeoForge, Fabric, Iris,
and Sodium are independent projects with their own licenses. Their jars are
development dependencies and must not be committed to this repository unless
their applicable licenses and notices are preserved.

## NVIDIA Streamline And DLSS

Streamline headers, import libraries, plugins, DLSS runtime files, and NGX
components are obtained separately from NVIDIA. They are not project-authored
code and are excluded from source control. Anyone building or distributing a
Streamline-enabled artifact is responsible for complying with the NVIDIA SDK
and redistribution terms that apply to the exact SDK/runtime version used.

## AMD FidelityFX SDK

FidelityFX headers and signed runtime binaries are obtained separately from
AMD. They are excluded from source control. Anyone building or distributing an
FSR3-enabled artifact is responsible for preserving AMD's license and notices
and confirming that the selected signed runtime binaries are redistributable.

## Gradle Wrapper

The Gradle wrapper files are included to make builds reproducible and remain
subject to the Gradle project's license.

Before publishing a binary release, record the exact versions, source URLs,
licenses, signatures, and SHA-256 hashes of every bundled third-party binary.
