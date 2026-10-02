## XTA — aimdi161

### An oversized feed cache is emptied at launch

- The aimdi160 report showed the app never reaching X: every read of the local database waited behind scans of the feed cache table, which on a long-used install had grown to hundreds of megabytes that nothing reads back. Deleting it piece by piece, as aimdi158 and aimdi159 did, still scanned the whole table for every piece. At launch the table is now counted and, past 400 rows, emptied in one statement that SQLite runs without visiting rows, and its indexes are created again if they are missing. Cached pages are fetched fresh from X on the next load; read positions are kept.
- Both diagnostics reports gain a "database at launch" line: file size, cached chunk rows and the indexes on that table.

Includes PR #341 plus all aimdi160 changes.

### Installation

For most Android phones, use **`xta-aimdi161_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001300**, above
every aimdi160 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi160)
