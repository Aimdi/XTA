## XTA — aimdi157

### Diagnostics that cannot get stuck

- The Diagnose page waited on the local database before showing anything, so a stuck database left it spinning. Every local step is now limited to five seconds and the report lists each one: package info, the read-only and writable database connections, and the state of the X signing key. A stuck step reads as "still waiting after 5s" in the copied report, which says exactly which layer stops X and Notes from loading.

Includes PR #333 plus all aimdi156 changes.

### Installation

For most Android phones, use **`xta-aimdi157_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001260**, above
every aimdi156 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi156)
