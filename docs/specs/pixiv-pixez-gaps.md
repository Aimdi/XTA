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
| B1 | Search: filters, shortcuts, favourite tags, SauceNAO | Built |
| B2a | Novels: models, API, feeds, rankings, bookmarks, series, watchlist | Built |
| B2b | Novels: reader | Built |
| B2c | Novels: search, profiles, history, deep links | Built |
| B3 | Comments (reading only) | Built |
| B4 | Discovery: rankings, manga, Pixivision, series, watchlist | Built |
| B5a | Profiles and follows | Built |
| B5b | Bookmarks and bookmark organisation | Built |
| B6a | Viewing: mirror, quality, columns, loading, zoom, swipe, split layout | Built |
| B6b | Saving: naming, folders, index, multi-select, ugoira export | Built |
| B7 | Settings, accounts, history, mute, links | Built |
| B8 | Wave-1 follow-ups: link wiring and de-duplication | Built |

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

### B1 — search (built)

Search state lives in `PixivSearchStore`. A filter bar and sheet cover target,
sort (oldest first for everyone; the audience sorts for Premium), posting-date presets and a
custom range, users入り popularity, the Premium bookmark bracket, AI
(`search_ai_type`) and ugoira, and Remember keeps the filter. Popular without
Premium shows Pixiv's free preview as the grid; date sorts keep it as a strip.
Creator results use the preview card with follow. Suggestions complete the last
word; digits offer Open artwork / Open user tiles from `pixivNumericShortcuts`
(a later batch adds its own tile there); paste opens a pasted link or id.
Recent searches fold past twelve and clear after a confirmation. The landing
pulls to refresh and loads trending tags and creators each with its own retry;
trending tiles show the translation and open their work on long press. Tags on
a work have a long-press sheet (mute, favourite, copy); favourite tags get a
tabbed screen with an edit mode; SauceNAO searches by image. Details in
`pixiv-search.md`.

### B2a–B2c — novels

- **B2a (built):** Novel mode for Home, Rankings and Favorites behind one
  mode button; recommended novels; novels from followed authors (public or
  private); the novel card with its heart (long press bookmarks privately);
  muting novels by author, tag, pattern or id; novel rankings with pins, R-18
  gates and the archive date; own novel bookmarks and a profile's public ones;
  the novel series page with the watchlist toggle; the novel watchlist.
  Details in `pixiv-novels.md`.
- **B2b (built):** the in-app reader, opened from every card, series page and
  watchlist row and by id; header with comments above and below the text;
  Pixiv markup (pages, chapters, ruby, links, page jumps, pictures); text size,
  spacing and reading position shared with the article readers; selectable
  text with Translate; menu with the author, previous / next chapter, share
  links anchored to their button, export as .txt and Open on Pixiv; each open
  joins the novel history. Details in `pixiv-novels.md`.
- **B2c (built):** novel search with the shared filters (novel places to
  look, popular for Premium); its landing (own recent searches, trending novel
  tags, novel / series / author id shortcuts); the profile Novels tab and
  novel covers in creator cards; novel reading history on the device behind
  the history screen's switch; novel and novel series links opening in the
  app; View comments on a novel's long press. Details in `pixiv-novels.md`.

### B3 — comments (reading only) (built)

Artwork comments; reply threads; Pixiv emoji and stickers; comment mute and
spam handling; selectable comment text with Translate; novel comments and
replies. Described in `pixiv-comments.md`. Novels open theirs from a card's
long press (B2c) and, with B2b, from the reader, through
`PixivCommentTarget.novel`; Report is not offered.

### B4 — discovery (built)

Recommended manga; recommended users page; Pixivision carousel, list and
article reader; illustration ranking with all modes, pinned tabs and date;
Following feed with public / private filter; follow hub (new works,
bookmarks, watchlist, followed); illust / manga series page and series
context; manga watchlist; signed-out preview; artwork ID and resolution.
Described in `pixiv-discovery.md`. Series and Pixivision links and the
Pixivision-ID search tile were wired in B8.

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

### B6b — saving (built)

Ugoira export as a GIF (per-frame delays, encoded off the UI thread with
progress and Cancel) or the frame ZIP; a file-name template with insert chips
and a preview; per-artist and R-18 subfolders through the app-wide download
path and a find-or-create folder walk in `MainActivity`; a saved-pages index
with a re-save question, a tile check and filled save buttons; picking pages
to save in the overview; Download, Bookmark, Copy link and Mute on a tile's
long-press; a simultaneous-downloads limit for the whole download queue.
Bookmark-after-download moved to B5b. Details in `pixiv-downloads.md`.

### B7 — settings, accounts, history, mute, links (built)

Viewing history on the device with a pause switch; mute page with typed tags,
artist names and `r'pattern'` rules (single tags and `#a#b` combinations);
muted-work notice with *Show this time*; the account's AI setting, read only
with a link to pixiv.net (the AI badge toggle moved to B6a); multiple accounts
kept out of backups; caption with tappable links and selection; Copy info with
a template; every Pixiv link form routed (series and pixivision open their
screens since B8, novels and novel series since B2c); pixiv links shared to
or opened by default in XTA; the More hub; start section and tap-again-to-top.
Details in `pixiv-settings.md`.

### B8 — wave-1 follow-ups (built)

pixivision.net links open the article screen and illust series links the
series screen; search's numeric shortcuts offer the Pixivision article; Home's
people icon opens the reader's following list (`PixivUserListScreen`). One
paged feed (`PixivPagedFeed`, with `PixivIllustFeed` on top), one ranking call
(`PixivDiscoveryApi.ranking`) and one `/v1/user/illusts` request
(`PixivClient.userIllusts`, which `PixivSocialApi.userWorks` calls). Works
grids measure their columns outside the scroll view, so scrolling rebuilds no
tile. Described in `pixiv-discovery.md` and `pixiv-profiles.md`.

## Already on par

- **R-18 hiding** drops R-18 works everywhere (feeds, previews, Discover)
  rather than masking them, and the app-wide secure window covers the privacy
  screen.
- **Hide AI works** drops `illust_ai_type == 2` everywhere except the reader's
  own bookmarks and the AI ranking boards, which the reader opens by name.
