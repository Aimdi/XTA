## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### aimdi115

**XTA, built on aimdi114. Not QuaX.**

Install **`xta-aimdi115_arm64-v8a.apk`**. Same app id (`com.aimdi.xta`), updates over 114.

Medien → Livestreams was empty because it asked X for photos and clips. Broadcasts are posts with `x.com/i/broadcasts/…` (or Spaces). That filter now reads posts.

### aimdi114

**XTA, built on aimdi113. Not QuaX.**

Install **`xta-aimdi114_arm64-v8a.apk`**. Same app id (`com.aimdi.xta`), updates over 113.

- Long-press a paragraph in a Substack article to start Vorlesen from there
- Retweets no longer stick on a spinner; lists across the app stop freezing on the first page
- Reply to your own notes. They stay on this device
- Medien has a Livestreams filter for broadcasts (`x.com/i/broadcasts/…`) and Spaces (`x.com/i/spaces/…`)
- Profile Archive chevron: All / Likes / Bookmarks

### aimdi113

**XTA, built on aimdi111. Not QuaX.**

Install **`xta-aimdi113_arm64-v8a.apk`**. Same app id (`com.aimdi.xta`), updates over 111.

Vorlesen was silent because Android 11 hid Next-gen Kaldi from XTA, and the app
forced German onto an English-only voice. This build declares TTS engines, binds
the preferred module, and picks a language that engine can actually speak.

### aimdi111

**Install this build, not aimdi110.** Six open feature PRs landed on main.

**Reddit.** Search follows a subreddit (the plus next to the lens is gone).
Tap a picture for fullscreen; long-press or the viewer download saves it
without Twitter's `:orig` suffix, which 404s on i.redd.it. Settings → Show
spoilers. Drive / X / other links in comments and selftext are short, blue,
and tappable on their own. The communities sheet lists icons and subscriber
counts; tap a row to open it, delete stays on the row, Add subreddit is
pinned at the bottom. Sign-in stays in Reddit settings.

**Plugin logos.** The timelines sheet uses each plugin's real mark instead
of a generic icon.

**Quotes, retweets, local notes.** Quotes and retweets show on the tweet.
Local notes under Saved look like tweets. They never leave this device
unless you back up or sync to Nextcloud.

**Profile.** The private note is smaller and actually saves. Profile posts
can be filtered. Broadcast tiles no longer paint a white bar.

**Links.** Settings picks the browser that opens a link. Tracking junk is
stripped from those URLs.

**Substack + Sherpa.** Unchanged from 110 and still in this build. Listen
on an article uses the on-device engine under Settings → Read aloud.
Install the Sherpa ONNX TTS Engine app, then choose Sherpa. Substack
publication logos that fail to load fall back to a coloured initial.

Existing databases keep working. No settings reset.

### aimdi110

**Install this build, not aimdi109.** Group feeds no longer go blank when X
rate-limits SearchTimeline — they fall back to the other endpoints that still
work.

Notes live under Saved. They never leave this device unless you back up or
sync to Nextcloud. X cannot see them.

On Start, the people icon still filters logins. Turning one off now also
drops that login's own posts from Für dich, so overlapping HomeTimelines
do not make the toggle look broken. A new Groups section on the same sheet
hides members of a group from Folgt; the group's own tab is unchanged.

The globe next to Folgt / Für dich / Reddit is gone. Every pinned plugin
stays on the strip (it scrolls). Plus adds a plugin timeline, removes it,
or drags to reorder. Tapping plus and pinning a plugin switches to it.

Existing databases keep working. No settings reset.

### Videos not playing after aimdi108

**Install this build, not aimdi108.** aimdi108 stopped extra libmpv players
from being created past the pool cap, which was the right idea, but two
bugs meant videos did not play:

The tile stored `pool.acquire`'s inner future in the same field as
`_acquire()`'s own future. Dart's `x ??= asyncFn()` overwrites that field
at the first `await`, so the "is this still the in-flight acquire?" check
failed on every first paint. The tile released the player, skipped the
first-frame listener, and the poster never lifted — play button, then a
spinner that never went away.

The pool also refused a new player when it was already at capacity even
if unused cached players could be dropped. After a few clips, every later
video stayed a still with no play button.

This build keeps the cap, evicts unused players to make a slot, attaches
listeners on the first paint, and retries when a slot is busy.

Existing databases keep working. No settings reset.

### Still crashing after aimdi107

**Install this build, not aimdi107.** aimdi107 stopped filled Pixiv and Booru
from attaching every board to the home NestedScrollView *inner* controller.
Following and For you on the X home tab were never wrapped in PluginEmbedded,
so they still froze then crashed: first-page loading painted a PagedListView
*and* a skeleton ListView (two inner attachments → `Too many elements`); a
cold empty Following painted a `Center` (zero inner attachments → `No
element`); Saved passed the outer home controller into the inner list; the
Following restore loop retried forever while `positions.length != 1`;
switching Für dich / Following remounted the whole NestedScrollView on the
same outer controller; video players that could not evict still created past
the pool cap and disposed while painted.

This build makes the first-page skeleton / empty / error the *only* inner
scrollable until items exist, keeps Saved on the inner controller, bounds the
restore loop, keeps the NestedScrollView alive across tab-controller epochs,
and refuses a video player when the pool is full.

Plugin feeds now share one card row, paint a post-shaped skeleton instead of
a blank spinner, write counts in the reader's language, say "content warning"
where Mastodon does, and no longer overflow a Substack title.

Existing databases keep working. No settings reset.

### Still crashing after aimdi106

**Install this build, not aimdi106.** aimdi106 stopped empty and one-item
plugin lists from attaching the home strip's *outer* scroll controller.
Filled Pixiv and Booru homes still mounted every board at once. Each
masonry attached the NestedScrollView *inner* controller, and the first
filled frame threw `Bad state: Too many elements` — freeze, then
"XTA has stopped". Those boards now mount one at a time. A corrupt
Following cache or an uncaught async error no longer aborts the isolate.

Existing databases keep working. No settings reset.

We could not reproduce a native SIGSEGV on this VM (no usable emulator).
If a leftover kill remains after 107, it is not the Pixiv/Booru inner
controller trap and not an uncaught Dart isolate error.

### Still crashing after aimdi105

**Install this build, not aimdi105.** aimdi105 only fixed empty Reddit.
Empty RSS, Substack, Threads, and Stocks homes — and one-item RSS / EH /
Booru lists — still attached the home strip's outer scroll controller, which
freezes then crashes. Add-account, RSS tag, and Reddit client-id dialogs
could dispose the text field while the sheet was still closing. The EH
page-jump dialog had the same dispose-while-closing bug. This build
routes every plugin list through the inner NestedScrollView controller, owns
those fields in a State, and does not spin empty RSS / Substack on a remount
from Für dich.

Existing databases keep working. No settings reset.

### Reddit home freeze

aimdi105 stopped empty Reddit Following from freezing: the empty list was
attached to the home strip's outer scroll controller, and tapping Add
disposed the text field while the sheet was still closing. That pane stays
on the inner scroller, the Add field is owned in a State, and a swipe back
from Für dich does not refetch an empty following list.

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

Everything from [aimdi110](https://github.com/Aimdi/XTA/releases/tag/aimdi110) is
in here too.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
