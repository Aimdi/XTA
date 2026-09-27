## XTA — aimdi148

### Slim headers across plugins

The compact Substack layout now extends to the other shared plugin headers,
leaving more space for posts and articles.

- A single 52dp bar combines the plugin mark, section navigation, search or the primary action, and options.
- Section icons appear when there is room; narrow layouts use a compact section picker. Standalone readers no longer repeat the title above their navigation.
- Tap the plugin mark in Home to switch sources. Unread indicators remain visible.
- Search, secondary actions, settings and full-client access remain available. The app drawer and Microblogs service selector are in options for docked readers.
- Section menus correctly show no selected entry when viewing content outside the listed sections, including Reddit subreddits.
- Full-size touch targets, light/dark/true-black themes, enlarged text, right-to-left layouts, loaded feeds and reading positions are preserved.

Includes PR #310 and all aimdi147 changes.

### Installation

For most Android phones, use **`xta-aimdi148_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001170**, above
every aimdi147 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi147)
