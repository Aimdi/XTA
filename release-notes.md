## XTA — aimdi149

### Consistent reader gestures

- Swipe left or right in Home to change sources, with a preview before release.
- Swipe compact plugin section icons or the section picker to move between sections. Full plugin readers also support section swipes over their content.
- Use the same controls in profiles, search results, quotes and Subscriptions. Swipe the Saved source filter to change sources.
- Deliberate releases select once, with subtle haptic feedback. Short or cancelled gestures, an extra finger, direction reversals and system gesture edges do not navigate.
- Photo zoom, video seeking, horizontal carousels, text selection, long-press menus and pull-to-refresh retain their gestures.
- Reading positions, lazy loading, visible buttons and menus, right-to-left layouts and reduced-motion settings are preserved.
- Swiping at the end of Subscriptions or across a photo no longer unexpectedly changes the main app tab.

Includes PR #312 and all aimdi148 changes, including the slim plugin headers.

### Installation

For most Android phones, use **`xta-aimdi149_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001180**, above
every aimdi148 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Android back/quick-switch, TalkBack and tactile haptic quality still need a
physical-device check.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi148)
