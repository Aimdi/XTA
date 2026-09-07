# Mastodon as a dedicated reader

## Decision

Use Moshidon as the main design reference for XTA's Android Mastodon reader.
The official [app directory](https://joinmastodon.org/apps) is the starting
point; compare [Moshidon](https://github.com/LucasGGamerM/moshidon#readme),
[Pachli](https://pachli.app), [Tusky](https://tusky.app),
[Fedilab](https://fedilab.app), [Ivory](https://tapbots.com/ivory), and
[Phanpy](https://github.com/cheeaun/phanpy#readme). Moshidon's Material design,
clear timeline choices, and reading controls are the best fit for this Android
project. This is a design judgment from product documentation and source,
not a claim to have installed and tested every app in the directory. Adapt
interaction patterns, without copying their code or branding.

## Problem

The reader has public feeds, search, profiles, threads and local follows, but
crowds them into four tabs, three toolbar actions and a passive server caption.
Search occupies a short modal and disappears when a result opens. Managing
followed people is buried in settings. Text and media lose 60dp to an avatar
gutter throughout each post.

## Implementation

- Add a dedicated client-screen hook with the existing home-screen fallback.
  Mastodon uses it for an app bar and four labelled bottom destinations:
  Following, Explore, Local and Federated. Existing Home placement uses a
  single compact section picker instead of another bottom navigation bar.
- Hide compact Home controls during reading; show them again only at the top.
  Keep full-client navigation available. Preserve each timeline's scroll
  position, lazy loading and selection across routes and Home remounts.
- Make Explore's hashtags one horizontal row instead of a large wrapping wall.
  Keep posts prominent and the server configuration accessible from the header.
- Add Posts / Accounts within Following. Show followed people here with
  profile navigation and an Add account action, rather than requiring settings.
- Replace the search sheet with a full-screen route. Keep query, result type
  and position when returning from a profile, hashtag or thread. Use a Store
  with latest-request-wins handling and retryable errors.
- Lay out post identity above full-width text/media. Preserve content warnings,
  polls, quotes, links, navigation, and read-only engagement counts.
- Reuse translated labels, Material touch targets and the current themes.
  No new dependencies, database changes, OAuth or remote writing actions.

## Verification

Use Flutter 3.44.4 CI for analyze, meaningful widget journeys and the full test
suite, plus a debug APK. Exercise standalone and embedded layouts, timeline
position restoration, search -> profile -> back, request races, Following
accounts, narrow/large-text/RTL layouts, and existing content-warning tests.
Capture populated before/after widget renders. Label fixtures clearly; no
claim of physical-device or live-account testing.

This branch is stacked on the reviewed Home timeline branch so the preview
APK includes that work without merging either change.
