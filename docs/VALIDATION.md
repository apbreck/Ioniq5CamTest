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
