## XTA — aimdi162

### The diagnostics log names what waits

- The aimdi161 report showed a small, healthy database and still a 15 second wait on the group lookup. Every entry in the "recent reads" log now carries a name: the group lookup, each store's snapshot, the account lookup, the cache previews, the unread queries, the launch-time cache bound and each purge. Database steps are timed as well: a step shows as pending while it runs and stays in the log when it took 100 ms or more or failed, so the next report says which statement the app waited for.
- A test teardown that occasionally raced the JSON store's background writes no longer fails the release build.

Includes PRs #343 and #344 plus all aimdi161 changes.

### Installation

For most Android phones, use **`xta-aimdi162_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001310**, above
every aimdi161 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi161)
