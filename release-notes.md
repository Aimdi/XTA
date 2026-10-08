## XTA — aimdi170

### Compact group header

- A group's member count no longer takes a row of its own under the title. The group's mark, its name and the count now share the top bar, so every group timeline starts higher up the screen.
- The Recent / Popular / Custom / Discover / Media row is slimmer and loses its doubled divider line; every chip keeps a full-size touch target.
- On a pushed group, the whole title (mark, name and count) opens the group switcher.

### Messages leave sooner

- Error and info messages at the bottom now leave after 3 seconds instead of 4, and a new one replaces the one on screen instead of waiting its turn, so a burst of errors no longer holds the bottom of the screen.
- "Changes saved – Undo" no longer stays until you tap it: it leaves after 5 seconds. With TalkBack on it still waits for you. Download progress still stays until the download ends.

Everything from aimdi169 is included: the new icon, the profile filter chips and the diagnostic for saves that stop after adding someone to a group.

### Installation

For most Android phones, use **`xta-aimdi170_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001390**, above
every aimdi169 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi169)
