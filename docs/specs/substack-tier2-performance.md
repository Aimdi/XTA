# Substack Tier 2: performance

Base: `324c9dd`. Implements the Tier 2 queue from `docs/substack/audit.md` §9, in order. Tier 1 items still open
(BUG-01, BUG-12, BUG-07, BUG-08, BUG-10) wait on a decision or on recorded responses; Tier 2 starts with the
owner's go-ahead. Device scenarios stay UNVERIFIED here (no device); each item is measured on the host VM against
its base commit in the same session.

## 1. Parse each body once, off the UI isolate (S2)

Opening a post parses its body five times on the UI isolate: the sanitizer and the navigation index parse it
(`SubstackArticleDocument.parse`), the page wrapper sanitizes the already clean body again (`wrapSubstackHtml`), and
the speech text sanitizes and parses it twice more (`substackHtmlToPlainText`).

- `SubstackArticleDocument.parse` also produces the speech text, walking the fragment it already has.
- `wrapSubstackHtml` takes `bodySanitized: true` for a body that came out of that parse, and skips the second
  sanitize. Every other caller still has its body sanitized.
- The reader runs the parse through the shared background helper, which uses `compute` for bodies over 20,000
  characters (the Pixiv novel reader's threshold, moved to `lib/utils/background_parse.dart`; Pixiv keeps its names).
  The theme, text scale and strings are read before the await; the result is dropped if a newer load started.
- Unchanged: sanitizer rules, the page markup, what speech reads.

Measure: the S2 benchmark's open pipeline follows the reader, so the base and HEAD each measure their own pipeline.
The output must stay the same: a test compares the page body and speech text of the one-parse path with the old
functions' output for the synthetic long post.

## 2. Feed publish cost (S4; plan item 3, taken before item 2)

Every arriving publication publishes the feed: all pages are merged and the whole list re-sorted, and each comparison
parses two post dates (`publishedAt`) and, on ties, rebuilds two URL keys. A 30-publication refresh publishes 33
times, so the work grows with the square of the feed (BUG-05 made it reach all 30).

- Each post's sort time and feed key are computed once per post object and remembered (an `Expando`, like the post
  keys), so a comparison is two integer reads.
- Order is unchanged: newest first, ties by feed key, undated posts last.
- Coalescing publishes per frame is the other half of the plan item. It is done only if the S4 measurement after the
  first change still shows the publish path dominating, because it delays the first visible page and complicates
  the store's tests.

Measure: S4 refresh CPU against the base commit, same session, alternating. A test pins the order on posts with equal,
missing and differently formatted dates.

Result: S4 refresh CPU fell from a median of 210.5 to 25.8 ms on the host, so coalescing was not done.

## 3. Feed memory across restarts; refresh only stale publications (S1, S4; plan item 2)

The feed lives in memory only. A cold start shows nothing until the network answers, and the screen then forces a
refresh of every followed publication, even one read a minute earlier.

- **Memory.** After each load, the store keeps each followed publication's first page (8 posts, without bodies) and
  when it answered, in `plugin.substack.feed_snapshot`, the way the Threads tab does (`threads_feed_snapshot.dart`).
  Tolerant decoding: a missing, old or malformed value is an empty memory.
- **Restore.** When the screen opens on an empty feed, the store paints the remembered pages of publications still
  followed, before any request. A publication that answered within the existing 10-minute window
  (`kAccountPostsCacheTtl`) counts as loaded; the others are shown and refreshed behind them.
- **Stale only.** A non-forced refresh fetches only publications not answered within the window. Pull to refresh,
  retry and following something new stay forced and fetch everything, as now.
- **Forget.** Resetting the plugin clears the memory; an unfollowed publication is never restored.

Measure: requests sent by a refresh right after a restart (mock client, host), and the restore itself. S1 on a device
stays UNVERIFIED.

Result: on the mock client, a restart inside the window sends 0 requests where it sent one per followed publication, and a stale publication alone is asked again. A failed page is never fresh, so a normal refresh retries it at once.

## 4. Click-to-load embeds (S2, privacy; plan item 6)

An embedded YouTube video, Spotify or Apple player, or other iframe in a post loads its third-party page, with its
scripts and requests, the moment the post opens.

- The reader's one parse (§1) puts each `<iframe>` inside an inert `<template>` in a placeholder that records the
  iframe's host. Nothing inside a template loads.
- The page script turns each placeholder into a real button, "Load embedded content · youtube.com", with the label
  from l10n (`plugin_substack_load_embed`, all locales). Tapping it puts the original iframe back unchanged.
- Navigation, search and speech are unchanged: an iframe has no text. The offline page already drops iframes.

Measure: requests to embed hosts when a post opens go from one per embed to zero until tapped (the template is inert
by HTML's definition; on-device confirmation is UNVERIFIED). Tests: the parse output has no live iframe, the
template keeps the original iframe, and the page carries the escaped label.

Result: in headless Chromium (Playwright 1.56.1), a page with one held embed made 0 requests to the embed host before the tap and 1 after; the button showed the label and host and was replaced by the original iframe. On an Android WebView it is UNVERIFIED.

## 5. Parsed-article cache (S2; rest of plan item 1)

Reopening a post in the same session parses its body again. The reader keeps the last 16 parsed documents in memory,
keyed by the post's key (BUG-02), its date and the body itself, so an edited body never reuses an old parse. Nothing
is written to disk; the cache holds the sanitized body and the passage list, under a few MB for long posts.

Measure: a reopen of the synthetic long post skips the parse entirely (host benchmark of the lookup against the
parse). Test: a hit returns the same document, a changed body or date misses, and the oldest entry goes first.
