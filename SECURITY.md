# Security Policy

This repository is a sanitized public Android-client source release.

Please do not open public issues containing:

- NEU account credentials
- SMS codes or one-time login codes
- session cookies, TGT/ST/login tickets, or `authorization-str` values
- app signing keys or keystores
- backend source, backend deployment details, or backend private config
- private download links
- raw diagnostic logs that may contain personal data

Runtime protocol configuration and gated update metadata are served by maintainer-operated private infrastructure so distributed APKs can work without committing key material, Worker code, or download links to this repository. If you need to report a sensitive issue, redact all secrets first or contact the maintainer privately.
