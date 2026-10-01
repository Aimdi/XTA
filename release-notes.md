## XTA — aimdi155

### Signed-in X feeds no longer time out

- When you are logged in, XTA used to load X's signing page with your session, and X answered with a slow redirect that ran out the time allowed, so every X request failed with "Timed out". The page is now loaded without the session, as the X web app does; your account's requests still carry your login.

Includes PR #329 plus all aimdi154 changes.

### Installation

For most Android phones, use **`xta-aimdi155_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001240**, above
every aimdi154 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi154)
