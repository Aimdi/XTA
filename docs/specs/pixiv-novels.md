# Pixiv — novels (batch B2a)

The novel side of the private Pixiv plugin, first part: Novel mode for the
five sections, the novel feeds, rankings, bookmarks, series pages and the
novel watchlist. The reader (B2b) and novel search, the profile Novels tab,
history and deep links (B2c) build on it. Part of the PixEz parity plan
(`pixiv-pixez-gaps.md`); the plugin as a whole is described in
`pixiv-plugin.md`.

PixEz was read only to learn what these features do and which endpoints and
fields exist. None of its code, widget trees or file layout is reused.

## Endpoints

Every call lives in `PixivNovelApi` (`pixiv_novel_api.dart`) over
`PixivClient`'s public transport. Screens read it through
`PixivNovelApi.of(context)`, which prefers a `Provider` override (tests use
`FakePixivNovelApi` from `test/support/pixiv_novel_fakes.dart`, whose
`providers` also give the novel bookmark store).

| Call | Request | Notes |
|---|---|---|
| `recommended` | `GET /v1/novel/recommended?include_privacy_policy=true&filter=for_android&include_ranking_novels=true` | |
| `following` | `GET /v1/novel/follow?restrict=public\|private` | |
| `ranking` | `GET /v1/novel/ranking?mode=&date=&filter=for_android` | AI boards keep their AI novels with Hide AI on |
| `bookmarks` / `ownBookmarks` | `GET /v1/user/bookmarks/novel?user_id=&restrict=` | The reader's own keep R-18 and AI novels |
| `addBookmark` | `POST /v2/novel/bookmark/add` form `novel_id`, `restrict` | |
| `deleteBookmark` | `POST /v1/novel/bookmark/delete` form `novel_id` | |
| `watchlist` | `GET /v1/watchlist/novel` | Same row shape as the manga watchlist |
| `addToWatchlist` / `removeFromWatchlist` | `POST /v1/watchlist/novel/add\|delete` form `series_id` | |
| `series` | `GET /v2/novel/series?series_id=` | Header, first and newest chapter, chapters, `next_url` |

Next pages follow `next_url` through `PixivClient.getPage`, the one-line
first-or-next helper the discovery API and the client can fold their own
copies into. The only writes are bookmarks and the watchlist.

## Models

`pixiv_novel_models.dart` parses with `Json`, so a missing or reshaped field
is an empty value, never a throw:

- `PixivNovel`: id, title, caption (plain and HTML), cover (`medium`, else
  `square_medium`, else `large`), the author as a `PixivUser`, tags, series,
  text length, pages, date, bookmarks, views, comments, bookmarked,
  `x_restrict` (R-18, R-18G) and `novel_ai_type == 2` (AI). A novel without
  an id, or one Pixiv withholds (`visible: false`), is skipped.
- `parsePixivNovelList` applies Show R-18 and Hide AI the way
  `illustPageFrom` does for works.
- `PixivNovelSeries`: title, caption, author, concluded, chapter count,
  total characters, original, on the watchlist.
- `parsePixivNovelSeriesPage` numbers the chapters within the page over
  every chapter Pixiv listed, so a chapter a filter hides keeps the next
  one's number, and drops a first or newest chapter the filters hide.
- Tag and series parsing is shared with works (`pixivTagsFromJson`,
  `pixivSeriesRefFromJson`).

## Novel mode

- `PixivHomeChrome` takes the mode and a callback; the screen shows one 48 dp
  button beside the five tabs that names the mode it switches to (Switch to
  novels / Switch to illustrations) and is lit while novels show. One icon
  button rather than a two-part control takes the least room, and Home's dock
  lists it in its sheet like any icon action. On the standalone bar (Pixiv as
  a root tab, or the full client opened from Home) the 48 dp it takes would
  fold the five icon tabs into the section picker at 320 dp, and at 360 dp
  beside a back button, so there the Pixiv mark gives way to it
  (`PluginHomeChrome.markGivesWay`): the mark goes only when that alone keeps
  every tab an icon.
- `PixivViewState.mode` is session state like the section, and Novel mode's
  own choices sit in `PixivViewState.novel` (`PixivNovelView`: Home source,
  follow visibility, ranking board and date, bookmark visibility), so each
  mode comes back as it was left. Its section bodies sit under a page
  storage key of their own, so a novel list never opens at the offset its
  illustration counterpart was left at, and each comes back where it was.
- `PixivNovelSession` (`pixiv_novel_session.dart`) obtains Novel mode's
  lists from the Home session and holds what its controls do. Every loader
  reads the session's choices when it runs, so a session restored from page
  storage needs nothing put back. Account switches and sign-outs empty these
  lists with the others.
- Home: Recommended, Following and Watchlist chips; Following adds a Public /
  Private switch (`PixivFollowRestrictSwitch`, also the Following list's
  switch). Rankings: the novel boards as pinned chips with Edit and the
  archive date (`PixivRankingSection` now takes its feed). Favorites: the
  reader's own novel bookmarks under Public / Private chips, the same chips
  (`pixivBookmarkRestrictChips`) as illustration Favorites, so the section
  keeps its layout when the mode flips. Search and More are the illustration
  ones until B2c brings novel search.
- Tapping the source, visibility or board already shown scrolls its list to
  the top. For the segmented Public / Private switch this goes through
  `PixivSegmentedSwitch.onReselect`, since a segmented button reports a tap on
  the segment shown only as that segment being unselected.

## Novel rankings

`pixivNovelRankingModes` (`pixiv_ranking_modes.dart`) lists daily, weekly,
male, female, AI (weekly), R-18 daily, R-18 AI, R-18 weekly and R-18G
weekly; the AI and R-18 labels are the illustration ones. Pins are a JSON
list in `plugin.pixiv.novel_ranking_modes` (default: every board that is
neither R-18 nor AI, as for illustrations: an AI board shows AI novels
whatever Hide AI says, so it is pinned through Edit), read by the shared
`PixivRankingPinsStore`. R-18 boards are
offered and shown only while Show R-18 is on; when the shown board loses its
chip, the section moves to the first chip and reloads, as the illustration
rankings do.

## Lists and cards

- `PixivNovelFeed` (`pixiv_novel_list.dart`) is `PixivPagedFeed` with novel
  cards: skeleton first, soft refresh, failed appends kept, retry, empty pane.
  `PixivNovelSliver` filters by the mutes as it draws, so a mute made from a
  card hides the novel at once. `PixivOwnedNovelFeed` is a list a screen part
  owns and lets go once its loads settle (the profile tab); it and the works
  grid of a profile tab (`PixivProfileFeed`) are both `PixivOwnedFeed`
  (`pixiv_loads.dart`) over a tracked store.
- `PixivNovelCard` (`pixiv_novel_card.dart`): an 80 dp cover, the title on up
  to three lines, the author, the length (`12.3K characters`), R-18 / R-18G
  and AI labels (the AI badge setting applies), the series as a 48 dp link to
  the novel series page, the tags, and the heart with its count. On a series
  page it carries its chapter number and no series link.
- A long press on a card opens the shared post sheet with Bookmark, Copy link,
  Mute author and Mute this novel on top (`pixiv_novel_actions.dart`). The
  sheet and its Bookmark, Copy link and mute entries are the works' own
  (`showPixivPageSheet`, `pixivBookmarkEntry`, `pixivCopyLinkEntry`,
  `pixivMuteEntry` in `pixiv_post_actions.dart`).
- Opening a novel goes through `openPixivNovel` / `openPixivNovelById`
  (`pixiv_novel_open.dart`), which open it the way a novel link does: the
  browser until B2b's reader routes those links. The reader batch swaps these
  two functions and nothing else changes.

## Bookmarking a novel

`PixivNovelBookmarkButton` (`pixiv_novel_bookmark_button.dart`): a tap
bookmarks with the default visibility (B5b's Bookmark privately setting,
`pixivDefaultBookmarkRestrict`) or removes the bookmark; a long press always
files it privately, a public bookmark included, with the medium haptic. The
count moves by one with it. Writes go through `PixivNovelBookmarkActions`
(`pixiv_novel_store.dart`) and `PixivNovelBookmarkStore`, the novels' app-wide
session overrides. That store and the illustrations' `PixivBookmarkStore` are
both `PixivBookmarkOverrides` (one write per novel at a time, counts adjusted
by one). A landed write buzzes lightly; a failed one says why in a snack bar
and leaves the heart as it was. Switching accounts clears the overrides. The
heart itself is the works' `PixivHeart` and `PixivHeartIcon`
(`pixiv_bookmark_button.dart`: label, long-press hint and buzz, 48 dp target,
faded while busy), with the count under it, and both hearts report through
the one `runPixivBookmarkWrite`.

## Series and watchlist

- `PixivNovelSeriesScreen` (`openPixivNovelSeries(context, id)`): title,
  author, Completed or Ongoing with the chapter count and total characters,
  the caption with links, Start from #1 (the series' first chapter), View
  latest (`novel_series_latest_novel`, the real newest chapter rather than the
  last one loaded), the watchlist toggle, share and open on Pixiv
  (`https://www.pixiv.net/novel/series/{id}`), then the numbered chapters as
  cards with their hearts. Start or View latest is off when the filters hide
  that chapter. The header sits outside the chapter list, so it stays when
  every chapter is filtered out or a page fails.
- The watchlist toggle is shared with illustration series:
  `PixivWatchedSeriesStore<S>` (add or remove, busy state, rolled back on
  failure), `togglePixivSeriesWatchlist` and `PixivWatchlistButton` in
  `pixiv_series_screen.dart`. A page asked for before a watchlist change
  lands keeps the flag the change left, so a slow page cannot undo the
  header. Both series pages let their stores go only once the pages and
  watchlist writes on their way have landed (`destroyWhenSettled`), so
  closing a page mid-load writes to nothing that is gone.
- Home › Watchlist in Novel mode is `PixivNovelWatchlistFeed`, B4's
  `PixivWatchlistFeed` and rows with novel openers: a row opens the series
  (and refreshes the list after a watchlist change there), View latest opens
  `latest_content_id`, and rows that do not parse are skipped.

## Muting

`PixivMuteState.hidesNovel` / `filterNovels` / `filterNovelsOf` hide a novel
by muted author, muted tag (names and `r'pattern'` rules, joined tags
included) or muted novel id (`plugin.pixiv.muted_novels`, listed and unmuted
on the Mute page). Show R-18 and Hide AI apply to novels too, unlike PixEz.

## Profiles

A profile's Bookmarks tab has an Illustrations / Novels switch; Novels lists
the creator's public novel bookmarks under the reader's filters, with the
creator's own author mute lifted as the works lists do. The Novels tab of a
creator's own novels is B2c's.

## Preferences added

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.novel_ranking_modes` | `["day","week","day_male","day_female"]` | Yes (reset with the plugin) |

## For the batches that follow

- B2b: replace `openPixivNovel` / `openPixivNovelById` with the reader; the
  card, series page and watchlist already call them.
- B2c: novel search fills Search in Novel mode (`_novelSections` in
  `pixiv_screen.dart` reuses the illustration Search until then); novel deep
  links route `PixivNovelLinkRef` and `PixivNovelSeriesLinkRef` to the reader
  and `openPixivNovelSeries`; the profile Novels tab can use
  `PixivOwnedNovelFeed`.

## Tests

`pixiv_novel_models_test.dart` (full, missing and reshaped payloads, list
filters, series numbering, mutes), `pixiv_novel_api_test.dart` (MockClient:
paths, parameters, `next_url`, form bodies, filters),
`pixiv_novel_store_test.dart` (overrides, bookmark actions, series numbering
across pages, a late page after a watchlist change, a page landing after its
screen closed, the watchlist toggle, the session's loaders and ranking sync),
`pixiv_novel_screens_test.dart` (the mode button swapping every section, each
mode keeping its own place on Rankings and Favorites, tapping the source,
visibility or board shown to go back to the top, Home sources, View latest,
R-18 boards, an account switch emptying and reloading the novel lists, the
card, its heart and long press, large text, the series page, the profile
switch) and `pixiv_home_chrome_test.dart` (the mode button keeping the five
icon tabs at 320 and 360 dp).
