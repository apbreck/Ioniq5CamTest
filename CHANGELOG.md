# Changelog

## 0.1.1 — 2026-10-28

- Export report now saves a timestamped JSON file in `CameraFeedProbe/` on the first mounted, writable USB drive at `/storage/usb0`, `/storage/usb1`, or `/storage/usb2`.
- The app confirms the saved USB path or displays a missing/unwritable-drive error. Existing reports are preserved.
- Preview stops before export so the saved report includes the final camera session state.
- The system helper uses Gen5W’s USB storage group; writes run separately from camera/guard handling.
- Added file-I/O tests and emulator coverage for successful, repeated, missing-drive and read-only-drive exports.
- Still experimental; vehicle camera access, handoff, and physical USB behavior remain unverified.

## 0.1.0 — 2026-10-28

Initial experimental camera identification and diagnostic module, tested in an emulator only.
