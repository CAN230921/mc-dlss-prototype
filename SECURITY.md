# Security Policy

## Supported Versions

Only the latest source revision is supported while the project is experimental.

## Reporting

Do not open a public issue for a vulnerability involving arbitrary code
execution, unsafe native loading, path traversal, signature verification, or
credential exposure. Contact the repository maintainer privately through the
security-reporting channel configured on the eventual hosting service.

Never attach complete launcher command lines or raw launcher configuration:
they can contain account access tokens. Redact usernames and absolute paths
from logs whenever they are not essential to reproduction.

Unsigned development DLLs may be rejected by Windows App Control. Do not turn
off operating-system security features to run this project. Use properly signed
release artifacts or an administrator-managed development policy.
