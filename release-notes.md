## XTA — aimdi145

### Reddit-style compact Home

Home plugins now share one slim header, with the source on the left and
options on the right. More of the feed is visible immediately.

- Section selection, Alt Microblogging service switching and reading controls
  open from the options button, without reserving another navigation row.
- Existing search and primary actions stay directly accessible. Secondary
  actions and full-client entry remain available through the menus.
- The source chooser supports local search, groups and unread indicators.
  Opening controls or switching sources preserves loaded readers and their
  saved reading positions.
- Alt Microblogging grouping remains reversible. People suggestions appear
  below initial posts so they do not displace the first content.
- Narrow screens and enlarged text keep readable source labels and full-sized
  tap targets. Each plugin retains its identity and existing functions.

This includes the completed work from PR #304. The older Home PR #240 was
already included in the app's history and has been closed as integrated.

### Installation

For most Android phones, use **`xta-aimdi145_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001158**, above
every aimdi144 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi144)
