## XTA — aimdi156

### X loads again on slow connections

- The key that signs every X request is derived from X's public page and three script bundles, more than half a megabyte in all. XTA derived it once for the startup warm-up and once more for every account, all at the same time, each copy limited to twelve seconds. On a mobile link the copies starved each other, every one timed out, and because a timeout is never remembered the next attempt started over, so every X screen ended in "Timed out". The key is now derived once for the whole app and shared by every account, with a single 45-second limit; a feed that gives up sooner joins the derivation still running on its next try.
- Signed-in X requests now carry the same session marker the X web client and Squawker send.

Includes PR #331 plus all aimdi155 changes.

### Installation

For most Android phones, use **`xta-aimdi156_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001250**, above
every aimdi155 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi155)
