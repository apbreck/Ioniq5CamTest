> **Compatibility correction:** The original vehicle preview path is unsupported. v0.1.0/v0.1.1 camera previews should not be run on a car. v0.1.2 disables vehicle capture. Earlier emulator results below are historical and do not establish hardware compatibility.

# Validation for 0.1.0

Status: experimental; not yet tested on a car. Target inferred from a read-only snapshot of a 2023 Ioniq 5 Limited running NEEV23.USA.S5W_M.V015.012.260507 (Android 4.4.2 / API 19). This does not establish compatibility with other Gen5W systems.

## Completed

- Java compilation, API-19 DEX generation (DEX 035).
- APK signature verification constrained to Android API 19 (v1/JAR signature).
- Unit checks: blank/grey buffers, YUY2 luma, changing samples, chroma-only noise, short buffers, unknown formats, YV12 stride and VU conversion.
- Android API 35 emulator: camera enumeration and 720×480 NV21 callback preview using three reusable buffers.
- Confirmed no automatic capture on helper startup.
- Confirmed a 30-second session stopped automatically (341 callbacks observed).
- Confirmed leaving the app stopped the camera session.
- Confirmed the temporary thumbnail was deleted on stop.
- Confirmed saved camera identification and JSON report export.

Compact results: [emulator-test-results.json](emulator-test-results.json). The [preview image](emulator-preview.png) shows an emulator’s synthetic image, not a car feed.

## Not established

- Runtime operation on Android 4.4 hardware (the emulator is newer Android).
- Hyundai RearCamera callback behavior or usable physical feed mappings.
- Actual vehicle-state/foreground service permissions.
- Release timing and factory-camera priority on the head unit.
- Recovery from native camera-service or driver faults.

The emulator-only flag requires ro.kernel.qemu=1 and marks results as emulator data. Vehicle checks were not exercised by emulator tests. No automatic car-camera probing or raw CAN commands were used.

## 0.1.1 USB report export

- File-I/O unit tests cover intact JSON, committed temporary files, missing/unmounted roots, duplicate filenames, path validation, and secondary USB selection.
- Android API 35 emulator tested with a tmpfs volume mounted at `/storage/usb0` to simulate removable storage. The emulator storage parent was made traversable to model the older head unit; this is test setup only, never done by the app.
- Verified the helper runs as UID 1000 with storage GID 1028.
- Verified missing-drive error, successful app-driven USB export after a live preview, exported final session state/history, distinct filenames preserving earlier exports, and a read-only-drive error.
- Compact results: [usb-export-test-results.json](usb-export-test-results.json).

These are simulated-storage tests, not a physical USB or vehicle test. Actual Gen5W mount layout and removable-drive behavior still require a parked hardware check.

## 0.1.2 diagnostic-only correction

- First owner feedback: three listed entries, no visible video, apparently still attempting connection. No diagnostic report was available; successful open/preview/frame delivery cannot be inferred.
- Current RearDashcam source documents a vendor HAL hang for the texture/preview approach used by the original probe, and different package handling for app_process camera open. This is a confirmed implementation mismatch, not a confirmed diagnosis of the owner’s exact failure.
- All vehicle camera-open and preview attempts are now refused. An emulator-only inventory mode exercises this same refusal branch with modern emulator context initialization.
- Emulator checks: stale/legacy start request refused; no camera-open or preview-start confirmation; disabled preview button; missing vendor-interface error visible; USB report includes setup events and interface errors.
- [Compact results](inventory-hotfix-test-results.json). USB storage in this test was simulated; camera capture was not attempted.
- Actual compatible vehicle capture remains unimplemented and unvalidated.
