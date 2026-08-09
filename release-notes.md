## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Reddit galleries are albums again

**A post with several pictures showed a link back to Reddit** instead of the
pictures. The swipeable album was already there — it just never got any images,
because without a Reddit client ID the app reads old.reddit's HTML, and that
page does not contain them. The post's own public feed does, needs no account,
and is where they come from now. The link stays if Reddit will not answer.

### Posts you could tap and nothing happened

**Some posts simply would not open.** A post whose author arrived without a
handle — a quoted post, or any post with author names hidden — threw the moment
you tapped it, and the app quietly swallowed it. Tapping again did nothing
again. They open now.

**Adding somebody to a group no longer throws you back to the top.** The feed
still refreshes to take in the change; it now waits until you are back at the
top, where that costs you nothing.

### Bluesky and the Fediverse in your home timeline

**They have the switch Reddit and Threads already had.** Until now their posts
could only be read on their own tab or inside a group, which is the one place
you would least look for them. Off by default, in each plugin's settings —
turning a plugin on should get you its tab, not a different Following feed.

Turning it on mixes the posts of the accounts you follow there into Following
and For you, alongside your X posts, in date order.

---

Everything from [aimdi89](https://github.com/Aimdi/XTA/releases/tag/aimdi89) is
in here too: backups that stop dropping your stocks, Threads, Bluesky and
Fediverse accounts and your device-only upvotes and likes; covers for groups
made of non-X accounts; and a group that lets go of a subreddit's posts when
you remove it.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
