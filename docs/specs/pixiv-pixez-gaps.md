# Pixiv — PixEz parity plan

Index of the work that brings XTA's private Pixiv plugin up to what
[pixez-flutter](https://github.com/Notsfsssf/pixez-flutter) offers a reader.
PixEz is GPL-3.0 and XTA is MIT: PixEz is read only to learn *what* a feature
does and *which* endpoints and fields exist. No PixEz code, widget tree or file
layout is copied or translated.

XTA stays a reader. The only writes to Pixiv are follow / unfollow (public or
private), bookmark add / delete (with tags and visibility) and watchlist add /
delete. Posting comments, replies, reports, uploads and account or profile
edits are out of scope. Viewing history stays on the device.

The plugin as it stands is described in `pixiv-plugin.md`; gallery speed work
is in `pixiv-performance.md`.

## Batches

B0 lands first and every other batch builds on its seams. B1, B3, B4, B5a/b,
B6a/b and B7 can then proceed side by side; the novel batches (B2a–c) follow
once those are in.

| Batch | Area | Status |
|---|---|---|
| B0 | Shared seams: transport, models, user card, file splits | Built |
| B1 | Search: filters, shortcuts, favourite tags, SauceNAO | Planned |
| B2a | Novels: models, API, feeds, rankings, bookmarks, series, watchlist | Planned |
| B2b | Novels: reader | Planned |
| B2c | Novels: search, profiles, history, deep links | Planned |
| B3 | Comments (reading only) | Planned |
| B4 | Discovery: rankings, manga, Pixivision, series, watchlist | Planned |
| B5a | Profiles and follows | Built |
| B5b | Bookmarks and bookmark organisation | Planned |
| B6a | Viewing: mirror, quality, columns, loading, zoom, swipe, split layout | Built |
| B6b | Saving: naming, folders, index, multi-select, ugoira export | Planned |
| B7 | Settings, accounts, history, mute, links | Built |

### B0 — shared seams (built)

- **App-API transport.** A public transport on `PixivClient` (`getJson` with an
  auth flag, `getNextJson`, `getText`, `postForm`, `illustPageFrom`). Pixiv's
  expired-token reply (HTTP 400, `error.message` about the OAuth process)
  refreshes once, single-flight, and replays. A connection dropped before any
  header is retried once. The bearer token only goes to `app-api.pixiv.net`.
- **Translated tags follow the app language.** Every request sends
  `Accept-Language` from the active XTA locale (`ja`, `ko`, `zh-CN`, `zh-TW`,
  else `en`).
- **User preview cards.** `PixivUserPreviewCard` (avatar, name, @account, three
  works, follow) and `PixivFollowButton` for user search, recommended users and
  follow lists. Follow state lives in the app-wide `PixivFollowStore`, so a
  follow holds through list reloads and recycled rows and matches the profile.
- **R-18 and AI filtering in previews.** User previews follow Show R-18 and
  Hide AI like the feeds, which also fixes group Discover suggesting creators
  through works the reader hid. Discover applies both at read time, so a
  change shows on the next scan rather than after its cache expires.
- **Profile counts.** `/v1/user/detail` has no follower count; the profile
  shows works (illustrations + manga), following and My pixiv friends, each a
  plural phrase in every language.
- **Seams for the other batches:** model fields (series, comment count,
  author followed, raw caption HTML, sanity level, premium), the generic paged
  list store (which drops pages that land after a refresh or source swap),
  comment and novel mutes, the follow-restrict / Home-source / bookmark-tag
  view state, one app-wide search history, the shared `PixivAvatar`, the
  `pixiv_link_open.dart` routes, a leading-slivers grid, `pumpPixiv`
  `extraProviders`, and the home, settings, tile and detail screens split into
  section files.

### B1 — search

Search filter sheet (target, sort, remembered); posting-date range with
presets; popularity filter (users入り); Premium bookmark-count range; AI and
ugoira filters; popular preview for non-Premium readers; user search with
preview cards and follow; tag autocomplete with multi-word editing; open by ID
or URL; search history management (clear all); tag chips with long-press
menu, copy and favourite; favourite tags; SauceNAO reverse image search.

### B2a–B2c — novels

- **B2a:** novel section; recommended novels; novel list card; muting novels;
  novel rankings with modes and date; new novels from followed authors; novel
  bookmarks (own and others'); bookmark a novel; series watchlist and series
  page.
- **B2b:** reader loading (and open by ID); header; markup rendering; embedded
  illustrations and uploaded images; font size and spacing; reading position;
  selectable text with Translate; menu with author and previous / next
  chapter; share links; export as .txt.
- **B2c:** novel search with filters; novel search landing (trending, ID jump,
  history); profile Novels tab and novel author cards; novel reading history;
  novel deep links.

### B3 — comments (reading only)

Artwork comments; reply threads; Pixiv emoji and stickers; comment mute and
spam handling; selectable comment text with Translate; novel comments and
replies.

### B4 — discovery

Recommended manga; recommended users page; Pixivision carousel, list and
article reader; illustration ranking with all modes, pinned tabs and date;
Following feed with public / private filter; follow hub (new works,
bookmarks, watchlist, followed); illust / manga series page and series
context; manga watchlist; signed-out preview; artwork ID and resolution.

### B5a — profiles and follows (built)

Profile header, info and actions; profile works (illustrations and manga);
following lists for any user; followers list; follow privately and the
follow-detail dialog; mute users from profiles with a muted-profile
placeholder. Described in `pixiv-profiles.md`.

### B5b — bookmarks (built)

Bookmark heart with default visibility; bookmark editor with tags and
visibility; tag picker with suggestions; bookmark lists with tag filter for
any user; auto-tag, follow-author and download after bookmarking; bookmark
after saving; haptic feedback. Described in `pixiv-bookmarks.md`; another
user's public bookmarks are B5a's profile Bookmarks tab.

### B6a — viewing (built)

Image server (`i.pximg.net`, the `i.pixiv.re` mirror or a custom address)
for every image, prefetch, ugoira archive and download; grid, work-page and
full-screen image sizes; portrait and landscape column counts; tap-to-retry
on tiles and detail pages and a progress ring in the reader; Share image and
an original-quality toggle in the reader bar; zoom in the vertical reader
with full-size decoding when zoomed or in HD; ugoira paused while covered and
recent archives kept in memory; swipe between works (off by default) with
next-page loading and edge turns for multi-page works; side-by-side detail on
wide screens with a remembered divider; the AI badge switch. Details in
`pixiv-viewing.md`.

### B6b — saving

Ugoira export as GIF or ZIP; file-name template; per-artist and R-18
subfolders; already-downloaded check and badge; pick pages to save; grid tile
long-press actions; bookmark when downloading.

### B7 — settings, accounts, history, mute, links (built)

Viewing history on the device with a pause switch; mute page with typed tags,
artist names and `r'pattern'` rules (single tags and `#a#b` combinations);
muted-work notice with *Show this time*; the account's AI setting, read only
with a link to pixiv.net (the AI badge toggle moved to B6a); multiple accounts
kept out of backups; caption with tappable links and selection; Copy info with
a template; every Pixiv link form routed (series, novels and pixivision open in
the browser until B2 and B4); pixiv links shared to or opened by default in
XTA; the More hub; start section and tap-again-to-top. Details in
`pixiv-settings.md`.

## Already on par

- **R-18 hiding** drops R-18 works everywhere (feeds, previews, Discover)
  rather than masking them, and the app-wide secure window covers the privacy
  screen.
- **Hide AI works** drops `illust_ai_type == 2` everywhere except the reader's
  own bookmarks; the AI-ranking exemption arrives with B4's rankings.
