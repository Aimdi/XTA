## XTA — aimdi160

### A report you can copy from the error screen

- The Diagnose page waits on the local database before it shows anything, so in the very state it is meant to explain it can stay blank. The "Timed out" screen now has its own "Copy report" button. The copy is built from memory alone: app version, X setup and signing key state, endpoint ids and the log of recent reads with their outcomes and timings, with the database marked as not probed. Paste it into an issue or a chat and it says which layer stalled.

Includes PR #339 plus all aimdi159 changes.

### Installation

For most Android phones, use **`xta-aimdi160_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001290**, above
every aimdi159 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi159)
