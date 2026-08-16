## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Plugins stay in the app

**Tap a plugin link** and you stay in XTA. Bluesky, Threads, Instagram, TikTok,
Reddit, Mastodon, Pixiv, and Substack URLs from the feed, a card, a profile
site, or a deep link open the native screen when that plugin is on. “Open on
site” still leaves for the website.

**Search** the Discover tab for an enabled plugin, not only X. Switching
there from a plugin tab preselects that plugin. Find people and Antennas
are chips on the hub; empty trends offer a location instead of a blank page.

**Reddit** uses the same icon chrome as Threads and the rest — Following,
Popular, and All on one row, no second title bar.

### A timeline that does not hitch

When a plugin account answers or a source finishes, the list you are already
reading stays on screen. Partials land in batches instead of rebuilding the
whole feed on every arrival. Groups, Following, and For You show a dot when
cached posts are newer than your last read. Mastodon no longer refetches
every public timeline just because you installed or removed the plugin.

### Likes and saves stay here

Hearts and bookmarks are local. The Saved library says so on the empty
states and under the folder strip. X cannot see them.

### Still in from aimdi99

Paste a Grok key in Settings → Advanced → AI provider, import public followings
as local subscriptions, and sort ungrouped accounts into groups.

---

Everything from [aimdi99](https://github.com/Aimdi/XTA/releases/tag/aimdi99) is
in here too.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
