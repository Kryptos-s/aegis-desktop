# Aegis Desktop

A desktop port of [Aegis Authenticator](https://github.com/beemdevelopment/Aegis), the two-factor
authentication app for Android.

**This is an unofficial port.** It is not affiliated with, endorsed by, or supported by Beem
Development, who wrote Aegis. Do not report problems with it to them. The vault format, the crypto
and the importers are their work; this project moves that code to the desktop and adds a new user
interface around it.

It reads and writes the same vault file as the Android app, so one vault works on both. Linux is
the primary target. Windows and macOS build and have platform backends written, but neither has
been run yet.

## What works

Unlock, the entry list with live codes, adding and editing entries, groups, search and filtering,
import from 19 other authenticators, export, backups, preferences, and an optional keychain-backed
unlock slot. TOTP, HOTP, Steam, Yandex and MOTP, from the same OTP code the Android app uses.

## Building

Needs a JDK 21. No Android SDK.

```bash
./gradlew build
```

Run from the source tree:

```bash
./gradlew :desktop:run
```

Native package for the current platform — `.deb`/`.rpm` on Linux, `.msi` on Windows, `.dmg` on
macOS:

```bash
./gradlew :desktop:packageDistributionForCurrentOS
```

`packaging/` has an AppImage script and a Flatpak manifest. On Linux, install the polkit policy if
you want the app to be able to ask for a fingerprint or your account password:

```bash
sudo install -Dm644 packaging/linux/com.beemdevelopment.aegis.policy /usr/share/polkit-1/actions/com.beemdevelopment.aegis.policy
```

Without it the app reports that check as unavailable rather than skipping it.

## Layout

```
core/       Crypto, vault format, OTP algorithms, icon packs, importers. Ported
            from upstream, still plain JVM Java. The parsing and crypto are
            unchanged, so vaults stay byte-compatible.

platform/   OS integration: secret storage, user presence, clipboard, session
            monitoring, autostart, single instance. One interface per capability
            with a Linux, Windows and macOS implementation.

desktop/    Compose Multiplatform UI, lock state machine, preferences, audit log.
```

## Where the vault lives

| | |
|---|---|
| Linux | `$XDG_DATA_HOME/aegis/aegis.json`, default `~/.local/share/aegis/aegis.json` |
| Windows | `%LOCALAPPDATA%\Aegis\aegis.json` |
| macOS | `~/Library/Application Support/Aegis/aegis.json` |

`AEGIS_HOME` overrides it. That is the intended way to keep the vault on an encrypted volume or in
a directory you sync yourself.

There is no built-in sync. The vault is a single encrypted blob, so two devices writing it produce
a conflict no syncing tool can merge. Keep one device authoritative, or move entries with export
and import.

`VaultInteropTest` reads a vault written by the Android app, writes it back out, and checks the
file structure field by field.

## Changes from the Android app

Per section 5 of the GPL, this is a modified version of Aegis. The Android application was removed
in August 2026 and replaced with a desktop one. In summary:

- The Android UI, resources and manifest are gone. The user interface is new, written in Compose
  Multiplatform.
- `core/` keeps upstream's crypto, vault format, OTP algorithms, icon packs and importers, with the
  android, androidx and Guava dependencies replaced by JVM equivalents. Parsing, crypto parameters
  and JSON keys are unchanged.
- Direct import from another app's private storage using root access is gone; there is no desktop
  equivalent. Those importers still read an exported file.
- Biometric unlock became an OS keychain slot with an optional user-presence check.
- QR scanning uses an image file, a region of the screen, or the clipboard instead of a camera.
- Added: auto-lock on session lock and on suspend, a clipboard that clears itself, keyboard
  shortcuts.

The full commit history is in this repository, on top of upstream's.

## Security

[SECURITY.md](SECURITY.md) covers the vault format, what locking does and does not guarantee, and
what this does not protect against.

## Licence

GPL-3.0, the same licence as upstream Aegis. See [LICENSE](LICENSE).

Copyright for the original work belongs to Beem Development and the Aegis contributors.

`core/src/main/java/com/beemdevelopment/aegis/crypto/bc/` is vendored from Bouncy Castle and is
under the Bouncy Castle licence (MIT), with its copyright notice intact.

Third-party dependencies, all compatible with the GPL: Bouncy Castle (MIT), ZXing (Apache-2.0),
org.json (Public Domain), Protocol Buffers (BSD-3-Clause), zip4j (Apache-2.0), SQLite JDBC
(Apache-2.0), SimpleFlatMapper (MIT), JNA (Apache-2.0 or LGPL-2.1-or-later), Compose Multiplatform
and Kotlin (Apache-2.0), JSpecify (Apache-2.0).
