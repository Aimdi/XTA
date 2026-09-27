## XTA — aimdi150

### Quieter recovery

- Temporary connection failures, timeouts and selected server errors retry quietly, with at most two extra attempts shared across nested reads and source fallbacks.
- Keep loaded posts visible while recovering. Cancel pending retries when a read ends, respect existing deadlines and Retry-After, and preserve Threads request spacing.
- Login, missing-content and rate-limit errors remain actionable. Uploads, saves and login/token-refresh writes are not automatically repeated.

### Privacy protections

- Opt-in crash reports omit arbitrary exception text, request details, local paths and OS build fingerprints while retaining useful app and code-location information.
- App-owned release logs are filtered, and copied diagnostics use account numbers instead of account names.
- Explicit Android backup and device-transfer exclusions protect local app data. Manual exports and WebDAV remain available.

### Compact feeds and recognizable sources

- Subscription counts sit beside the group title, and repost attribution uses a slim single-line credit. Full details remain available through tooltips and accessibility labels.
- X and Threads use their proper brand marks. Microblogs shows the Bluesky, Threads and Mastodon logos beside the available services.

Includes PRs #314, #315, #316 and #317, plus all aimdi149 changes, including the slim plugin headers and app-wide gestures.

### Installation

For most Android phones, use **`xta-aimdi150_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001190**, above
every aimdi149 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Long-session behavior, Android backup/transfer, and tactile gesture behavior
still require physical-device testing.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi149)
