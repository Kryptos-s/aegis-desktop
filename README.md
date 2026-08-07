# Aegis Desktop

A desktop port of [Aegis Authenticator](https://github.com/beemdevelopment/Aegis), the two-factor
authentication app for Android.

It reads and writes the same vault file as the Android app — same format, same crypto, same
importers — so one vault works on both. Linux is the primary target. Windows and macOS build and
have platform backends written, but have not been run on either yet.

Aegis Desktop is an unofficial port. It is not affiliated with Beem Development, who wrote the
original app and everything security-critical in it.

## What works

Unlock, the entry list with live codes, adding and editing entries, groups, search and filtering,
import from 19 other authenticators, export, backups, preferences, and an optional keychain-backed
unlock slot.

Codes come from the same OTP code the Android app uses: TOTP, HOTP, Steam, Yandex and MOTP.

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

`AEGIS_HOME` overrides it. That is the intended way to keep the vault on an encrypted volume or in a
directory you sync yourself.

There is no built-in sync. The vault is a single encrypted blob, so two devices writing it produce a
conflict no syncing tool can merge. Keep one device authoritative, or move entries with export and
import.

`VaultInteropTest` reads a vault written by the Android app, writes it back out, and checks the file
structure field by field.

## Differences from the Android app

Direct import from another app's private storage using root access is gone; there is no desktop
equivalent. Those importers still read an exported file, and several can read the source app's
internal database if you extract it yourself.

Biometric unlock becomes an OS keychain slot with an optional user-presence check. QR scanning uses
an image file, a region of the screen, or the clipboard instead of a camera. Android's `FLAG_SECURE`
screenshot blocking has no desktop equivalent and is not claimed.

Added: auto-lock on session lock and on suspend, a clipboard that clears itself, and keyboard
shortcuts.

## Security

[SECURITY.md](SECURITY.md) covers the vault format, what locking does and does not guarantee, and
what this does not protect against.

## Licence

GPL-3.0-or-later, as upstream. See [LICENSE](LICENSE). The crypto, vault format and importers are
Beem Development's work.
