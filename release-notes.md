## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### The Threads tab stops taking forever

**It was slow by design flaws, not by network — three of them, all fixed.**
The feed used to wait for the *slowest* account you follow before showing
anything, every account cost two rate-limit-paced requests where one is
enough, and one hung account stalled every request behind it. Now the first
account's posts are on screen in seconds while the rest fill in, each account
costs one paced request once it is known, and a slow account only delays
itself. The careful request pacing that keeps Meta from flagging the reader
is unchanged.

---

Everything from [aimdi92](https://github.com/Aimdi/XTA/releases/tag/aimdi92) is
in here too: the Reddit overhaul — deep comment pages, collapse-all, Q&A sort,
community info sheets, in-community search — Pixiv's trending tags, ranking
calendar and popularity strip, and the launch-critical podcast-player fix.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
