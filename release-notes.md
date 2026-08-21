## XTA

A read-only fork of [QuaX](https://github.com/Teskann/QuaX). Same idea — read X
without posting, keep what you follow on your own device — with the plugins and
fixes below on top. Nothing here adds compose, reply, quote, or like-on-X.

### Hacker News is in the store

The Plugin store used to hide compiled-in plugins that `main`'s catalogue had
not named yet, which is why Hacker News vanished after the first refresh. This
build keeps any plugin this APK already contains unless the catalogue names it
and marks it unavailable. Search the store by name, initials, or id (`hn`,
`hacker news`). Available starts open.

### Faster HN threads

Story lists, comment trees, and user pages use the same lazy list as the X
feed. Collapsing a comment skips its children instead of building them.

### Still in from aimdi101

Guest Hacker News (Top / New / Best / Ask / Show / Jobs, search, local likes).
Home tabs build on demand. Cheaper tweet tiles and paint-size plugin images.

---

Everything from [aimdi101](https://github.com/Aimdi/XTA/releases/tag/aimdi101) is
in here too.

---

First download? Install it with Obtainium 👇

[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

XTA has its own application id (`com.aimdi.xta`), so it installs alongside
upstream QuaX and alongside earlier builds of this fork rather than over them.
Nothing carries across from an older install — export a backup first if you want
your subscriptions, groups and saved posts.
