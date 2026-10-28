# Prototype validation

Completed on October 28, 2026:

- Java 8 compilation and Android API 19 DEX/APK generation.
- APK signature verification constrained to API 19.
- Unit tests for repository/HTTPS restrictions, transport destinations, hash and size mismatch, package identity, traversal, USB wrappers, and duplicate metadata.
- Android API 35 emulator loaded a loopback test catalog and verified a module ZIP.
- A corrupt download was rejected before any privileged command.
- Canceling confirmation invoked no installer.
- A failing fixture parked-state check blocked installation.
- Explicit confirmation invoked the fixture installer and the installed descriptor version was checked.
- The fixture installed-package list was refreshed.

[Compact emulator results](emulator-results.json). The fixture does not execute NEMESIS or vehicle-changing code.

Not validated: Android 4.4 TLS certificates/networking, actual NEMESIS command permissions/private-file access, actual Hyundai parked-state getters, physical-head-unit install/update behavior, or other Gen5W models. The Hello package is the intended first parked hardware test.
