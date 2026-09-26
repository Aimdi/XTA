## XTA — aimdi147

### Compact Substack reader

This release delivers the compact Substack layout that was missing from aimdi146.

- A single 52dp bar holds the actual Substack logo, Home, Inbox, Notes and Library icons, plus Search and Options.
- At narrow widths Search moves into Options, preserving full-size section buttons and Back.
- Tap the logo in Home to switch sources. Unread indicators remain visible.
- Following publications, search, sorting, filters, retry, discovery and other actions stay available in Options.
- Filter badges and publication-error warnings now update correctly after changing controls or retrying.
- Light, dark and true-black themes, enlarged text, right-to-left layouts, local state and reading positions are preserved.

Includes PR #308 and all aimdi146 Microblogs changes.

### Installation

For most Android phones, use **`xta-aimdi147_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001166**, above
every aimdi146 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi146)
