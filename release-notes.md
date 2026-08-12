## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Private guest TikTok

**Browse TikTok without a TikTok account.** Follows and likes stay on this
device. Feed cards are cover + play; the player tries native playback, then
falls back to the embed.

### Chrome, Threads, and faster feeds

**Footers, pinned bars, and empty states** match across plugins and the main
app. **Threads is guest-first** so a pasted session is not spent on every
refresh — turn on “Use my session” only if you want cookie APIs. **Feeds
scroll cheaper**: off-screen tiles stay light, images decode at tile size, and
cached JSON is decoded off the UI isolate.

### Pixiv bookmarks, sharper Booru, custom sites

The **Pixiv heart bookmarks on Pixiv** when you are signed in (grid and
viewer). **Booru catalog tiles** load the sample/large image instead of the
tiny preview. **Rule34 and Xbooru** are presets; **Add site** saves any
Gelbooru / Danbooru / Moebooru / e621 host.

### Plugin store

**Installed plugins sit at the top** in one compact row each. Available
plugins stay grouped by category underneath.

---

Everything from [aimdi96](https://github.com/Aimdi/XTA/releases/tag/aimdi96) is
in here too: Booru, private EH galleries, Discover, and quieter group timelines.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
