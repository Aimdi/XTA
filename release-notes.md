## XTA — aimdi159

### Nothing on the database blocks the first feed page any more

- aimdi158 stopped the feed cache from being deleted in one go. The cleanup that runs at every start still removed the other caches in single statements: week-old pagination cursors, week-old thread and profile pages, and the trim of that page cache to its newest rows. On Android every database statement of the app runs on one worker, so any of these could still hold feeds, Notes and the Diagnose page until it finished. All four purges now proceed a few rows at a time and yield in between.

Includes PR #337 plus all aimdi158 changes.

### Installation

For most Android phones, use **`xta-aimdi159_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001280**, above
every aimdi158 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi158)
