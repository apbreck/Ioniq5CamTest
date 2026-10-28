# Ioniq5CamTest — Camera Feed Probe 0.1.0

An experimental Nemesis module for identifying camera feeds on the owner's 2023 Ioniq 5 Gen5W head unit. **Built and tested with an emulator; not yet validated on the vehicle.**

## Downloads

Get the [v0.1.0 experimental prerelease](https://github.com/apbreck/Ioniq5CamTest/releases/tag/v0.1.0). Download `camera-probe-0.1.0-usb.zip` for USB installation, or `camera-probe-0.1.0.zip` for ADB installation. Verify downloads against the release `SHA256SUMS.txt`. The APK alone is insufficient.

## What it does

- Enumerates `android.hardware.Camera` and the vendor `android.hardware.RearCamera` interfaces separately, with camera ID, reported facing, and orientation.
- Opens only the feed you explicitly select. Tests last at most 30 seconds and never retry automatically.
- Shows callback-frame count, callback rate, elapsed time since the last frame, image brightness/detail, sampled changes, negotiated size and pixel format, and a JPEG preview where supported (NV21, YUY2, YV12).
- Lets you name each feed after looking at it, mark whether a visible scene is confirmed, and save notes.
- Exports camera metadata, your identifications, session parameters, errors, and observations as JSON for later analysis. The exported report contains no pictures.

The exact interface and ID are the machine identity. Android's FRONT/BACK metadata is not a trustworthy physical-camera name. Different interfaces/IDs can share one source. The app cannot discover an OEM descriptive name if the driver doesn't expose one.

Frame callbacks are evidence of data delivery, not proof of a useful camera view. Uniform buffers, noise, stale images, and a real dark scene can be ambiguous. "Image detail detected" means visually inspect and confirm. "No callback frames" does not prove there is no video: some Gen5W paths use direct hardware display rather than normal callbacks. This version does not map physical camera RAM or force AVM view modes.

## Install

Keep the vehicle parked for testing. Pause recording in the Rear Dashcam app first; the modules may contend for the same camera. The probe does not stop or reconfigure other apps automatically.

USB installation:

1. Download `dist/camera-probe-0.1.0-usb.zip` and extract its contents to the root of a FAT32/exFAT USB drive. Use one Nemesis installer descriptor on the drive at a time.
2. With Nemesis already installed and the head unit fully started, insert the drive and confirm the installation.
3. Open **Camera Feed Probe** from the launcher or Nemesis Manager → Packages → Camera Feed Probe → Launch.

ADB alternative, from the platform-tools folder:

```sh
./adb connect 172.20.10.2:5555
./adb -s 172.20.10.2:5555 push camera-probe-0.1.0.zip /data/local/tmp/
./adb -s 172.20.10.2:5555 shell '/system/xbin/nemesis install --update /data/local/tmp/camera-probe-0.1.0.zip'
./adb -s 172.20.10.2:5555 shell am start -n dev.cameraprobe/.MainActivity
```

Use the Nemesis ZIP for installation. Installing the APK alone does not provide the system helper and will not enable testing.

## First parked test

1. Leave the vehicle in Park with the ignition on. The module requires readable vehicle-state services; it refuses a test if any required check is unavailable.
2. Ensure the factory camera view is closed and Rear Dashcam recording is paused.
3. Start with **Camera #0** and size **auto**. Press **Start test**.
4. Note whether a picture appears, whether frames count upward, and whether the scene matches the camera you expect. Have a stationary observer identify the view without moving the vehicle.
5. Press **Stop**. Enter a descriptive label, choose your visual verdict, and press **Save identification**.
6. Repeat one ID at a time. If an interface fails, save the error instead of assuming that physical camera is absent. Do not repeatedly retry an interface that causes the head unit to misbehave.
7. Press **Export report** after saving the identifications.

The preview is cleared when stopped. Colors and orientation are the raw driver output, without a mirror correction; note differences in the report. A changing image fingerprint can be sensor noise, not physical motion. Camera-test sizes are restricted to supported values no larger than 1280×720.

## Retrieve the report

```sh
./adb -s 172.20.10.2:5555 pull /data/nemesis/camera-probe/report.json ./camera-probe-report.json
./adb -s 172.20.10.2:5555 pull /data/nemesis/camera-probe/sessions.jsonl ./camera-probe-sessions.jsonl
./adb -s 172.20.10.2:5555 pull /data/nemesis/camera-probe-worker.log ./camera-probe-worker.log
```

Reports remain after uninstall so they can be retrieved; UI labels in Android app preferences are removed when the APK is uninstalled. Save/export before uninstalling. No Internet permissions, recording, audio capture, or automatic image upload are included.

## Stop behavior and limitations

The helper starts through Nemesis and idles without opening a camera. It drops root to the system UID used by the head unit's camera services. Each test requires a fresh request from the foreground app, Park/unregulated status, ignition on, and a non-reverse/non-factory-camera gear signal. Required getters are invoked by their named firmware interfaces, not guessed transaction numbers.

A guard polls vehicle state while testing, and a separate watchdog terminates only this helper if it hangs. Leaving the app or losing its request stops preview; pressing the factory camera button or leaving Park is intended to release the camera. A native release call that hangs causes helper termination, with no automatic camera reopening. **These handoff protections still need verification on the car; do not rely on them while driving.**

No camera calibration, guide-line options, firmware files, vehicle controls, CAN frames, or power settings are changed. Camera preview parameters change only for the diagnostic camera session. A buggy vendor driver may still hang or crash its shared camera service; software guards cannot guarantee recovery from a kernel/HAL fault.

If the factory camera becomes unavailable during a parked test, stop and uninstall this module through Nemesis and restart the head unit before further use. Resume Rear Dashcam recording manually after testing if desired.

## Build and verification

Source is in `source/`. Run `python3 source/build.py` with Java, Android SDK platform 35, and build-tools 35.0.0 installed. Set `ANDROID_HOME` for another SDK location. The package targets Android API 19 and uses no downloaded Gradle dependencies. No Hyundai executable files or signing keys are bundled in the source archive.

The build creates a local APK signing key in `source/signing/`; retain that directory privately for compatible updates. Its password is a local build convenience, not a security boundary. Build intermediates are in `source/build/`.

Verified so far: Java compilation and API-19 DEX generation, Android-4.4-compatible APK signature, frame-statistics/conversion tests, emulator camera enumeration, live callback frames and preview, 30-second timeout, and release when leaving the app. Emulator-only testing bypasses vehicle checks only if Android reports `ro.kernel.qemu=1`; it is explicitly marked in reports and is refused on a non-emulator.

Not yet verified: actual Gen5W previews, vendor `RearCamera` behavior, vehicle service permission checks, factory-camera priority, physical feed mapping, and effects of camera driver contention. The emulator runs a newer Android version and is not an Android 4.4 or Hyundai hardware substitute.

Implementation references: [Nemesis package format](https://github.com/xwtk/hmg_nemesis/blob/main/04-Packages.md), [Gen5 RearDashcam](https://github.com/KramerTech/gen5-RearDashcam), and the saved firmware interface snapshot from this head unit.
