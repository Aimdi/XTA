## XTA — aimdi154

### X requests no longer time out

- X changed how its pages load the script that signs API requests. XTA crawled the wrong bundles looking for it and ran out of time, so every X request failed with "Timed out". The signer is now reached directly, in three requests, the way the X web app loads it.

Includes PR #327 plus all aimdi153 changes.

### Installation

For most Android phones, use **`xta-aimdi154_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001230**, above
every aimdi153 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi153)
