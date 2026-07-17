# Code Signing Policy

Free code signing provided by [SignPath.io](https://signpath.io/), certificate by [SignPath Foundation](https://signpath.org/).

## Scope

The signing scope is limited to project-authored Windows PE files built from this repository:

- `mc_dlss_bootstrap.dll`
- `mc_dlss_native.dll`
- project-authored diagnostic executables when explicitly included in a release

Upstream binaries, including AMD FidelityFX and NVIDIA runtime files, must retain their upstream signatures and must never be re-signed with this project's certificate.

## Build And Approval

- Release inputs must originate from this public repository and a tagged commit.
- Release builds must run through the repository's GitHub Actions release workflow.
- Dependencies must be pinned to immutable commits or verified hashes.
- Every signing request requires manual approval.
- Signed outputs must be published with SHA-256 checksums and source provenance.

## Team Roles

- Committer and reviewer: [CAN230921](https://github.com/CAN230921)
- Signing approver: [CAN230921](https://github.com/CAN230921)

The project currently has one maintainer. Contributions from other people are accepted through pull requests and reviewed before merge. A maintainer must not approve a signing request for an artifact that differs from the corresponding tagged source build.

## Privacy

See the project [privacy policy](PRIVACY.md). This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.
