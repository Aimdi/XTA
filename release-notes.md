## XTA — aimdi164

### The report names what blocks the Android main thread

- Every report since aimdi157 shows the same picture: requests to X finish in about a second while every database call waits for the rest of the session, and the wait only ends when you tap. Networking is pure Dart, but the database, the clipboard and touch all go through the Android main thread, so this is the signature of a main thread that is blocked for minutes at launch. A watchdog now watches that thread from the moment the app starts: when it stops responding for five seconds, the exact stack of what it is doing is written down, and the moment it comes back is noted. Both Settings → Diagnose and the ⏱️ screen's "Bericht kopieren" report show that record under "Android main thread".
- Nothing else changes. Feeds, plugins and every setting behave as in aimdi163.

Includes PR #348 plus all aimdi163 changes.

### Installation

For most Android phones, use **`xta-aimdi164_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001330**, above
every aimdi163 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi163)
