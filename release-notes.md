## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Grok, import followings, sort groups

**Paste a Grok key** in Settings → Advanced → AI provider. The Grok chip fills
xAI's server and model so you only paste the key from console.x.ai. An OpenAI
chip is there too. Empty fields keep AI off. The key stays on this device.

**Import followings** from one or more public accounts: paste `@handles`, names,
or x.com links. Each account's public following becomes local subscriptions.
Nothing is followed on X.

**Sort ungrouped** looks at subscriptions that are not in any group, puts them
with similar accounts, and suggests a new group when none of yours fit. With a
key it asks Grok first; without one it uses names only.

### Still in from aimdi98

Private guest Instagram (For You and people-to-follow), Mastodon Explore /
Local / Federated / Following, Threads and Bluesky suggestions, TikTok search,
calmer settings, and group plugin chips.

---

Everything from [aimdi98](https://github.com/Aimdi/XTA/releases/tag/aimdi98) is
in here too.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
