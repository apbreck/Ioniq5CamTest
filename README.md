# Ioniq5CamTest — Camera Feed Probe 0.1.2

**Diagnostic-only interim release: vehicle camera capture is disabled.** Camera inventory and USB reports remain available. This release does not verify whether any physical feed contains video.

## Why capture is disabled

The first owner test reported three entries without video, with the screen apparently still trying to connect. No report was available, so successful camera open or frame delivery was not confirmed.

A subsequent review of [Gen5 RearDashcam’s current camera implementation](https://github.com/KramerTech/gen5-RearDashcam/blob/main/src/dev/skramer/dashcam/RearCam.java) documents two compatibility problems with the original probe: helper-process camera open needs an explicit system package identity, and the vendor camera HAL can hang when a preview texture is attached before preview starts. The original probe used that unsupported path.

**Do not run camera previews in v0.1.0 or v0.1.1.** If the factory camera becomes unavailable, stop testing and restart the head unit before further use. A blank preview does not establish that a physical feed lacks video.

Version 0.1.2 blocks all camera-open/preview attempts on vehicle hardware, including requests from a stale UI. The texture/callback implementation is available only in an explicitly selected emulator test mode, guarded by `ro.kernel.qemu=1`. A compatible vehicle capture implementation still needs development and parked hardware validation.

## Download and install

Get the [v0.1.2 diagnostic-only prerelease](https://github.com/apbreck/Ioniq5CamTest/releases/tag/v0.1.2).

- USB: extract `camera-probe-0.1.2-usb.zip` to the root of a FAT32/exFAT drive and install through the already-installed NEMESIS runtime.
- ADB: install `camera-probe-0.1.2.zip` with `nemesis install --update`.
- Verify downloads against the release `SHA256SUMS.txt`. Updates use the same APK signing key.
- Open **Camera Feed Probe** from the launcher or NEMESIS Manager. The APK alone does not provide the helper.

Keep the vehicle parked. Export existing identifications before updating/uninstalling if possible, since some NEMESIS update paths may remove Android app preferences.

## What can be collected now

- Separate inventories of `android.hardware.Camera` and `android.hardware.RearCamera`, exact IDs, reported facing/orientation, and interface failures or zero-camera results.
- Your saved physical-camera labels and notes. Treat them as unconfirmed unless verified independently.
- Existing session history and setup events. Reports distinguish camera-open confirmation, preview-start confirmation, and received frames; these are separate facts.

The app now displays interface errors instead of silently hiding failed interfaces. Three standard Camera entries are not equivalent to three physical cameras, nor do they describe the separate RearCamera interface.

## Export to USB

Save your identifications, insert a USB drive, and tap **Export report**. A new JSON file is written inside the drive’s `CameraFeedProbe/` folder, for example `camera-probe-20261028-143000-a1b2c3d4.json`. The app confirms the full saved path or displays a missing/unwritable-drive error. Earlier reports are preserved. It checks mounted writable drives at `/storage/usb0`, `/storage/usb1`, and `/storage/usb2` in that order.

Wait for USB activity to finish before removing the drive. No images, audio, firmware binaries, or firmware keys are included in the exported report. Logs and user notes can still contain information you may wish to review before sharing.

An internal cache and helper diagnostics can also be retrieved over ADB from `/data/nemesis/camera-probe/` and `/data/nemesis/camera-probe-worker.log`. No upload or Internet permissions are included.

## Build and validation

Run `python3 source/build.py` with Java, Android SDK platform 35, and build-tools 35.0.0. Set `ANDROID_HOME` for another SDK location. The package targets Android API 19; no Gradle downloads are needed. Build/signing locations can be overridden with `CAMERAPROBE_BUILD_DIR` and `CAMERAPROBE_SIGNING_DIR`. Keep the signing key private and reuse it for compatible updates; signing directories and build artifacts are ignored by Git.

See [validation history](docs/VALIDATION.md). Emulator tests are not Gen5W hardware tests. Actual camera access, factory-camera handoff, and physical feed mapping are **not validated**. The known unsupported path is disabled rather than retried on a car.

MIT license; see [third-party notices](THIRD_PARTY_NOTICES.md). NEMESIS is a separate AGPL-3.0 runtime prerequisite. No Hyundai firmware, vehicle snapshots, or signing keys are distributed.
