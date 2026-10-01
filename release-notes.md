## XTA — aimdi158

### The feed cache no longer freezes the app

- Every feed load saved a page of posts per block of followed accounts and never removed the old ones, although only the newest eight per block are ever read. On a large feed that grew to hundreds of megabytes, and the weekly cleanup and pull-to-refresh then deleted it all in one go, holding the single database worker for minutes: feeds, Notes and the Diagnose page all ended in "Timed out" while X itself answered fine. Each save now keeps only the rows a read uses, and both deletions run a few rows at a time so nothing else has to wait.

Includes PR #335 plus all aimdi157 changes.

### Installation

For most Android phones, use **`xta-aimdi158_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001270**, above
every aimdi157 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi157)
