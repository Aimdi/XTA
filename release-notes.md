## XTA — aimdi153

### X feeds load again

- Following and For you no longer stay on their loading placeholders, or show a cancelled read as an error, without asking X for anything. The first page used to be started three ways at once and each start cancelled the one before it.
- A feed that has just opened loads its first page before it knows it is on screen, so a cold start no longer cancels itself.
- Following someone, or changing a group's filters, reloads the feed instead of leaving it empty.

Includes PR #325 plus all aimdi152 changes.

### Installation

For most Android phones, use **`xta-aimdi153_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001220**, above
every aimdi152 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi152)
