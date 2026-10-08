## XTA — aimdi170

### Compact group header

- A group's member count no longer takes a row of its own under the title. The group's mark, its name and the count now share the top bar, so every group timeline starts higher up the screen.
- The Recent / Popular / Custom / Discover / Media row is slimmer and loses its doubled divider line; every chip keeps a full-size touch target.
- On a pushed group, the whole title (mark, name and count) opens the group switcher.

### X and Reddit share the compact Home header

- In Home, X and Reddit now open with the same single row as Pixiv, Substack and Hacker News: the service logo (tap it to switch source), Reddit's Following / Popular / All, search, and the options button. Everything else (X's refresh, loaded-post search, account filter, Subscriptions, Saved, Accounts; Reddit's sort, communities, saved, reading route and settings) is in the options sheet. Following is unchanged.
- The options button shows a dot while an X account or group is filtered out of Home.
- Home's Reddit tab now opens on the section you last chose (Following, Popular, All or a community) instead of always the merged Following list, and changing Reddit's sort or reading route in Home now refetches straight away instead of up to ten minutes later.

### Messages leave sooner

- Error and info messages at the bottom now leave after 3 seconds instead of 4, and a new one replaces the one on screen instead of waiting its turn, so a burst of errors no longer holds the bottom of the screen.
- "Changes saved – Undo" no longer stays until you tap it: it leaves after 5 seconds. With TalkBack on it still waits for you. Download progress still stays until the download ends.

### Mastodon & Bluesky

- The "Microblogs" section is now called "Mastodon & Bluesky", with a logo of the two marks stacked: Bluesky's butterfly at the top left, Mastodon's mark at the bottom right. Threads stays in the section, it just isn't named.

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
