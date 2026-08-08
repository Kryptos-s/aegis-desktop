# Security

Aegis Desktop is an unofficial port of [Aegis Authenticator](https://github.com/beemdevelopment/Aegis).
Problems in the vault format, crypto or importers are upstream's; problems in the desktop
integration, the lock behaviour or the UI are this project's.

## Vault format

Unchanged from Aegis for Android:

| | |
|---|---|
| Content | AES-256-GCM, 96-bit nonce, 128-bit tag |
| Password KDF | scrypt, N=2^15, r=8, p=1, 256-bit salt |
| Slots | AES-256-GCM under a per-slot key |

The master key is random, not derived from the password. The password derives a slot key, which
encrypts the master key. That is what makes changing the password, or adding a second way in,
possible without re-encrypting the vault.

`VaultInteropTest` reads a vault written by the Android app, writes it back, and checks the
structure field by field.

## On disk

`VaultStore` is the only thing that writes the vault. It creates the file 0600 before writing
anything into it, writes to a temporary file in the same directory, fsyncs, moves it into place
atomically, then fsyncs the directory. An interrupted save cannot leave a truncated vault.

Temporary files holding plaintext — an importer's SQLite copy, an export staged for backup — are
created 0600 and overwritten before deletion. On a copy-on-write filesystem or on flash with wear
levelling the old blocks may survive; full-disk encryption is the answer there, not this.

## Locking

| Trigger | Default | Where |
|---|---|---|
| Idle timeout | 5 minutes | Everywhere. Uses session idle time where the platform reports it, otherwise the app's own last-interaction time |
| Session lock or screensaver | On | Linux, over D-Bus |
| Suspend | On | Linux, via logind |
| Window minimized | Off | Everywhere |
| Manual (Ctrl+L) | — | Everywhere |

Locking destroys the credentials and drops every piece of UI state derived from the vault.

It cannot guarantee the key bytes leave the heap. `SecretKeySpec` does not implement `Destroyable`,
a moving collector may already have copied the array, and a JVM cannot mlock its heap out of swap.
Keeping the unlocked window short is the actual defence, which is why the idle default is five
minutes rather than off.

Passwords are held in `char[]` and zeroed. So are derived keys, decoded secrets, and buffers crossing
into a native keychain.

## Keychain unlock

Optional, off by default. A random key encrypts the master key into a slot; that key goes in the OS
secret store.

| | |
|---|---|
| Linux | Secret Service through libsecret, called directly rather than through `secret-tool` |
| Windows | DPAPI, scoped to the user |
| macOS | Keychain, using the data protection keychain when a presence check is required |

This is convenience, not a second secret. On Linux and Windows anything running as you can ask the
same store for the same key. It defends against another user on the machine and against offline disk
access, not against malware running as you. The master password always keeps working.

The slot written to disk is the existing biometric slot type. Aegis for Android rejects vaults
containing slot types it does not recognise, so a new type would make the vault unreadable on the
phone. The biometric type also already has the right semantics: device-local, ignored when its key
is missing, stripped on export.

### User presence

- **Linux** — polkit, so the desktop's own agent decides whether that means a fingerprint or your
  password. Needs `packaging/linux/com.beemdevelopment.aegis.policy` installed; without it the app
  reports the capability as unavailable rather than skipping the check.
- **macOS** — a keychain read protected by `kSecAccessControlUserPresence`. Either macOS
  authenticated you and produced the bytes or it did not.
- **Windows** — not implemented. Hello needs a WinRT shim.

## Clipboard

A copied code is readable by every process in the session, and clipboard managers persist history by
default. Two mitigations: the transferable carries `x-kde-passwordManagerHint: secret`, which some
managers honour, and the clipboard is cleared after a timeout if it still holds what was put there.

Neither is enforceable. Settings and the copy confirmation both state which one is actually in effect
on the running platform.

## Network

There is no HTTP client, socket or URL fetch in the source. The packaged runtime is built with
`jlink` from an explicit module list that omits `java.net.http`, `jdk.httpserver` and `java.rmi`, so
code using them would not link. The Flatpak manifest does not request `--share=network`.

The only thing that reads from outside is an importer, from a file you pick.

## Process

| | |
|---|---|
| Core dumps | `ulimit -c 0` in the launcher, `prlimit` at runtime |
| Heap dumps | `-XX:-HeapDumpOnOutOfMemoryError` |
| Debugger attach | `-XX:+DisableAttachMechanism`; `jdk.attach` is not in the packaged runtime |
| ptrace | Cannot be restricted per-process. The launcher warns when `kernel.yama.ptrace_scope` is 0 |
| Single instance | A locked file, so two processes cannot race each other's saves |

## Untrusted input

`otpauth://` URIs are parsed by a reimplementation of `android.net.Uri` that matches AOSP's
behaviour, so a malformed URI resolves the same way on both. Base32 is decoded strictly.

Importer SQLite files are opened read-only from a private copy. XML is parsed with DTDs and external
entities disabled. Icon packs unpack into their own directory, and SVG icons are not rendered —
running an SVG parser over icon-pack data is not worth an icon. Entry names reaching the HTML export
are escaped.

## What this does not protect against

- Malware running as your user. It can read this process's memory, ask the keyring for the same key,
  and read your clipboard.
- A compromised desktop environment.
- An attacker with your vault file and your password. scrypt makes guessing expensive, not
  impossible.
- Secrets reaching swap.
- Forensic recovery from the storage device.
- Screen capture while unlocked.

## Reporting

Problems in the vault format, crypto or importers affect the Android app too and belong upstream:
<https://github.com/beemdevelopment/Aegis/security>.
