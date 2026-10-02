## XTA — aimdi163

### Every database statement in the log, and a stalled connection is replaced

- The aimdi162 report finally named the wait: on the shared read-only database connection one statement stops answering and every later read queues behind it for the rest of the session, which is why feeds, Notes and Diagnose all stall together while X itself is reachable. Every statement on both connections is now timed into the "recent reads" log under its verb and table, so the next report shows the exact statement, including ones the earlier releases could not see. A statement on the shared read-only connection that outlives ten seconds marks that connection as stalled, and the next read opens a fresh connection instead of waiting behind it.

Includes PR #346 (with a fix that rounds a request budget's wait up to whole milliseconds, so a timed-out fan-out never starts one more request) plus all aimdi162 changes.

### Installation

For most Android phones, use **`xta-aimdi163_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001320**, above
every aimdi162 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi162)
