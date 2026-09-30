## XTA — aimdi152

### Reader tools

- **Per-feed appearance.** Give each feed its own Compact, Gallery or Reading look, with separate choices for counts, link previews and media.
- **Translation.** Tap to translate X posts, plugin posts and articles with DeepL, LibreTranslate or the AI provider set up in Settings, then switch back to the original. Translation stays off until you choose a service, and nothing is sent until you tap Translate. Your API key is never included in settings exports.
- **Reading history.** A searchable record, kept on this device, of up to 500 posts, articles and profiles you actually viewed. You can remove entries, clear it or pause it.
- **Shared filters.** One list of keyword or regex rules for X and every plugin. A rule can hide or fold posts, in timelines, in search or both, and can expire on a date. A slow regex is paused instead of freezing the app, and a feed that filters everything out stops loading until you ask for more.
- **RSS import and export.** Import an OPML file, or save and share your feeds as one, from the RSS settings, the RSS menu or Reader Tools. Folders become tags and duplicates are skipped. Feeds that differ only in their query, such as YouTube channels, no longer collapse into one.
- **Mixed feeds.** Build a named timeline from X, Mastodon, Bluesky, Threads, Reddit, RSS, Substack and Hacker News sources, merged newest first or taking turns. Each post keeps its native card. A mix sits on the Home strip and can be created from the Home picker or Reader Tools.

### Consistent Home headers

- Following, X and Reddit now use the same compact header as Bluesky: source picker, section tabs, search and options in one row, giving that space back to the feed.

Includes PRs #322 and #323 plus all aimdi151 changes.

### Installation

For most Android phones, use **`xta-aimdi152_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001210**, above
every aimdi151 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

Physical-device visual and accessibility testing is still separate from the
automated widget, Android compile and release-integrity checks.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi151)
