## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Backups stop forgetting things

**Choosing what to export used to lose most of it.** Ticking "subscriptions"
saved your Substack publications and your subreddits, and silently dropped your
followed stocks, Threads, Bluesky and Fediverse accounts, your Reddit upvotes
and your Threads and Bluesky likes. None of those is on a server anywhere — the
backup was their only copy. It saves all of them now.

The reason it kept happening is that each plugin's tables were written out by
hand, in four separate places, and one of them was always a step behind. Every
plugin now says what it owns once, and the backup reads that.

### Groups of anything but X accounts

**A group made only of Threads, Bluesky or Fediverse accounts had no cover** —
just a blank tile, as though it had no members, even though all of them store a
picture.

**Removing the last subreddit from a group left its posts behind.** The
subreddit was gone from the group and its posts stayed in the feed, with
nothing that would clear them.

**Fediverse accounts were left out in two more places:** a group of only them
reported itself empty, and adding one to a group did not fetch its posts.

### Quieter under the hood

The feed no longer keeps a separate copy of "how to read Reddit", "how to read
Threads", and so on. One description per source now drives the subscriptions
list, group membership, group feeds, the home timeline and the backup — which
is what the four fixes above have in common: each was a list that had gone one
network out of date.

Reading position, the "you're caught up" divider and the way a refresh decides
what counts as read are also pinned down by tests now, rather than living
inside the feed where nothing could check them.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
