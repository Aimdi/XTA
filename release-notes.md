## XTA — aimdi146

### Slim Microblogs readers

Mastodon, Bluesky and Threads now share a compact reader layout in Home and
inside their full clients.

- A single 52dp header holds the service mark, section selection, search and
  options. Larger accessibility text can increase the height; touch targets
  remain full-sized.
- Mastodon's extra bottom navigation is removed. Its active server, filters,
  saved posts and account controls remain available in options.
- Threads' followed accounts open on demand, with profile, add and confirmed
  unfollow actions, instead of taking permanent space above the feed.
- Service switching, local filters, lazy sections and reading positions are
  preserved.
- The category is now called **Microblogs**, with shorter labels in all
  supported locales and the existing speech-bubble icon.

This release includes PR #306. Substack's layout is unchanged.

### Installation

For most Android phones, use **`xta-aimdi146_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001162**, above
every aimdi145 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building. It verifies
all four APK variants, versions and signing certificates before publication.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi145)
