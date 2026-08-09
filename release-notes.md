## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Reddit reads like a Reddit app now

**Long threads stop ending mid-air.** The app now asks Reddit for a much
deeper page of comments, and where Reddit still holds replies back, the
"load more comments" row survives everywhere — including at the bottom of the
page, where it used to vanish — and actually opens the replies it names.

**Threads got the tools long threads need.** One button folds every top-level
argument so a thousand-comment page becomes the list of discussions it is made
of; the Q&A sort joins the menu, which is the difference between an AMA being
readable and not; and the indent rails now change colour by depth, so a deep
argument stays traceable to its level.

**Every subreddit screen answers "what is this place".** An info button opens
the community's own description with its reader counts — signed in or not —
and a search button searches inside that community, with Reddit's own orders:
relevance, top, new, most commented.

**Feeds behave at the edges.** The next page starts loading as you approach
the bottom; a failed "load more" now says what went wrong and offers Retry
instead of looking like a button that does nothing; and a finished feed says
it is finished.

### Pixiv learned discovery

**The ranking tab has a calendar now** — pick any day back to 2007 and read
that day's board, the way Pixiv-Shaft does it.

**Search opens with Pixiv's trending tags** as a tappable image grid, suggests
tags with translated names while you type, and puts a "most popular" strip
above date-sorted results — the community's answer to popularity sorting being
a paid feature.

**Switching ranking modes no longer lies.** A failed refresh on the new mode
used to quietly leave the previous mode's drawings on screen under the new
label. It clears now.

### Small but launch-critical

**A blank screen on launch was one release away.** The podcast player was
being built before the app's first frame, on an assumption another fix had
just removed. It is now created only when something actually plays.

**The repost-collapse switch works without a restart** — flipping it in
Settings › Posts now takes effect immediately.

---

Everything from [aimdi91](https://github.com/Aimdi/XTA/releases/tag/aimdi91) is
in here too: Reddit galleries that show their pictures without an account,
posts that refused to open, and the home-timeline switch for Bluesky and the
Fediverse.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
