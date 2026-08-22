## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Reddit home freeze

**Install this build, not aimdi104.** Home → Start → Reddit with no followed
communities showed the empty "Add a subreddit" pane, then froze and crashed.
The empty list was attached to the home strip's outer scroll controller, and
tapping Add disposed the text field while the sheet was still closing. This
build keeps that pane on the inner scroller, owns the Add field in a State,
and does not refetch on every swipe back from Für dich.

Existing databases keep working. No settings reset.

### Crash on the first home frame

aimdi104 stopped the first home frame from crashing: a missing
`rss_subscription` table after a swallowed migrate, empty or restored
`home.pages`, and string-list prefs stored as JSON. The table is created if
it is still missing. Default Home / Subscriptions / Discover / Saved tabs
stay when nothing usable is selected. IconLabel, Networks → Add timeline,
RSS enable, and the feed strip stay hardened.

### RSS, groups, and Substack leftovers

**RSS** is in the Plugin store (search `rss`). Paste a site or feed URL; XTA
finds the public RSS or Atom feed. Home merges items newest-first with an
unread chip. Follows stay on this device and can be added to a group.

**Group feeds** now show those plugin posts next to X. Add a Reddit community,
Substack publication, or RSS feed to a group and they appear in that group's
timeline instead of an X-only list.

**Substack** custom-domain follows (garbageday and other leftover hosts) load
again. Tap the publication name or logo on a card to open the profile, not
only the article.

**Sherpa** is an explicit on-device engine under Settings → Read aloud. Install
the Sherpa ONNX TTS Engine app, then choose Sherpa when you listen to an
article.

### Bluesky lists and custom feeds

Bluesky now has **Following**, **Discover**, **Lists**, and **Liked**. Discover
opens custom feeds (algos); Lists opens list timelines. Paste a feed or list
AT-URI or bsky.app link. Following no longer rebuilds from the first account
when you swipe away and back.

The group **image** tab reuses tweets already on the list instead of firing a
second Search, which was 429ing larger groups.

Reddit home can switch among the communities you follow without leaving the
tab. Discover plugin chips own the search results for that plugin.

### Home strip, not Groups

Switching networks lives on the home strip. The globe opens **Networks** for
plugins that are not pinned. Hiding a plugin tab pins it there; it is not a
Groups-board chip. Plugin and section icons sit beside the labels.

### Still in from aimdi102

Hacker News is in the Plugin store — search `hn` or `hacker news`. A stale
catalogue on `main` cannot hide a plugin this APK already contains. Available
starts open. HN threads and user pages build rows on demand.

---

Everything from [aimdi104](https://github.com/Aimdi/XTA/releases/tag/aimdi104) is
in here too.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
