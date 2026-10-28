> **Moved:** This prototype is now **[RepoMan](https://github.com/apbreck/RepoMan)**. Use that repository for current source, catalog, and [installers](https://github.com/apbreck/RepoMan/releases/tag/v0.1.1). The content below documents the original 0.1.0 prototype.

# Nemesis Module Browser 0.1.0

Experimental on-car module browser using `apbreck/Ioniq5CamTest` as its public test catalog. **Not yet tested on vehicle hardware.** The catalog is at the repository root, `module-catalog.json`.

## Install the browser once

Download `module-browser-0.1.0-usb.zip` from the [browser prerelease](https://github.com/apbreck/Ioniq5CamTest/releases/tag/browser-v0.1.0), extract it to a FAT32/exFAT drive's root, then install through the already-installed NEMESIS runtime. Alternatively install `module-browser-0.1.0.zip` with NEMESIS over ADB. Installing the APK alone does not supply the parked-state helper.

Open **Nemesis Module Browser**, connect the head unit to home Wi-Fi or a phone hotspot, and tap **Refresh catalog**. Wireless CarPlay may need to be disconnected while the head unit uses Wi-Fi as a client. This app does not enable or reconfigure the Wi-Fi radio.

## First test

1. Select **Catalog Hello Test** and read its description.
2. Tap **Download & verify**. The browser checks SHA-256, file size, ZIP paths, and the root package ID/version before enabling installation.
3. While parked with ignition on, tap **Install module** and explicitly confirm. The fixed helper checks Hyundai's regulation/ignition getters immediately before invoking NEMESIS. Unknown or unreadable state blocks installation.
4. Inspect the result and **View log**. **Check installed** refreshes NEMESIS's package list and the installed descriptor version.

The Hello package writes only `/data/nemesis/apps/catalog.hello/installed.txt` and has a launchable script that emits a log message. It has no persistent service and changes no vehicle settings. Remove it through NEMESIS Manager after testing.

The second catalog entry is the **diagnostic-only** Camera Inventory 0.1.2, with its declared firmware restriction. It does not provide live camera views. A matching declaration is not a hardware compatibility guarantee.

## Trust and limits

- Public catalog and package URLs are restricted to this repository. Package redirects may use GitHub's asset hosts; redirects to other destinations or HTTP are rejected.
- HTTPS uses system certificate/hostname verification, with TLS 1.2 enabled for older Android. No trust-all certificates or unverified HTTP fallback are used. Android 4.4 certificate compatibility and the actual Wi-Fi connection still require head-unit testing; secure-connection failures are shown as errors.
- A catalog hash checks integrity against the repository; it is not an independent publisher signature. Anyone controlling this repository can publish installable root-level code. Review modules and protect the GitHub account.
- Modules run installation hooks with root privileges through NEMESIS. There are no automatic installs, updates, arbitrary repository settings, or self-update support in this prototype.
- There is no browser background service. Downloads are capped at 64 MiB; package inspection caps expanded data at 128 MiB. Nothing is extracted by this app before handing a verified ZIP to NEMESIS.
- Failed checks do not invoke the installer. An installation that times out or cannot be verified is marked as unconfirmed; it is not automatically retried. The app does not kill an in-progress root installation on timeout. Keep the unit powered during installation.
- Installed version confirmation reads the module's installed `NEMESIS.data`. If that cannot be read, success is not claimed. Inspect NEMESIS Manager and the operation log.
- Firmware/park permission checks and actual NEMESIS installation remain unverified on the car. This prototype must not be described as generally compatible with Gen5W models.

Logs stay inside the app's private files (`browser.log`). No vehicle logs, contacts, credentials, firmware binaries, or keys are uploaded.

## Build and test

Run `python3 module-browser/build.py` with Java, Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_HOME`, `BROWSER_BUILD_DIR`, and `BROWSER_SIGNING_DIR` as needed. Keep and reuse the private signing key for updates. Builds target API 19 and use no Gradle downloads. `dist/`, build intermediates, and signing files are not committed.

An emulator-only fixture mode requires `ro.kernel.qemu=1` and an explicit launch extra. It can use a loopback test catalog and `/data/local/tmp/nemesis-catalog-fixture`; it never activates on a real head unit. Simulated installer tests do not establish actual NEMESIS behavior.

NEMESIS is a separate AGPL-3.0 runtime prerequisite; none of its executable code is bundled. This browser is MIT licensed under the repository license. [NEMESIS package interface](https://github.com/xwtk/hmg_nemesis/blob/main/04-Packages.md).
