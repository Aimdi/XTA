## XTA — aimdi151

### Cleaner Home source picker

- Group Pixiv, Booru and E-Hentai into a compact **Art** row while keeping every source individually selectable and searchable.
- Group Substack and RSS into a compact **Reading** row with the same direct source navigation.
- Normalize source-icon sizing throughout the picker and replace the oversized X mark with a cleaner, properly scaled X glyph.
- Preserve existing pins, reader IDs, source availability, selected/unread state and Add behavior.

### Accessibility and layout

- Group rows now expose their unread state to accessibility services.
- Keep grouped sources usable on narrow screens, large text and RTL layouts, with full 48dp touch targets.
- Includes the CI fixes that caught lower-sheet source selection and semantics regressions before release.

Includes PR #320 plus all aimdi150 changes.

### Installation

For most Android phones, use **`xta-aimdi151_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001200**, above
every aimdi150 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi150)
