# Pixiv — novels (batches B2a and B2c)

The novel side of the private Pixiv plugin: Novel mode for the five
sections, the novel feeds, rankings, bookmarks, series pages and the novel
watchlist (B2a); novel search and its landing, the profile Novels tab, novel
reading history, in-app novel links and the novels' comments entry (B2c).
The reader (B2b) builds on both. Part of the PixEz parity plan
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
| `detail` | `GET /v2/novel/detail?novel_id=` | One novel's card fields; `notFound` when Pixiv withholds it or sends none |
| `search` | `GET /v1/search/novel?word=&search_target=&sort=&start_date=&end_date=&merge_plain_keyword_results=true&filter=for_android` | Query from `pixivSearchQuery(kind: novels)`; Hide AI applies on the device |
| `trendingTags` | `GET /v1/trending-tags/novel?filter=for_android` | Parsed by the works' `parsePixivTrendTags` |
| `userNovels` | `GET /v1/user/novels?user_id=&filter=for_android` | The reader's own keep R-18 and AI novels |

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
  keeps its layout when the mode flips. Search is novel search (below); More
  is the illustration one.
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
  to three lines, the author, the length (`12.3K characters`, left out when
  Pixiv sent none), R-18 / R-18G
  and AI labels (the AI badge setting applies), the series as a 48 dp link to
  the novel series page, the tags, and the heart with its count. On a series
  page it carries its chapter number and no series link.
- A long press on a card opens the shared post sheet with Bookmark, View
  comments, Copy link, Mute author and Mute this novel on top
  (`pixiv_novel_actions.dart`); a list can give the card its own long press
  instead, as the history does to forget an entry. The
  sheet and its Bookmark, Copy link and mute entries are the works' own
  (`showPixivPageSheet`, `pixivBookmarkEntry`, `pixivCopyLinkEntry`,
  `pixivMuteEntry` in `pixiv_post_actions.dart`).
- Opening a novel goes through `openPixivNovel` / `openPixivNovelById`
  (`pixiv_novel_open.dart`). The first records the novel in the reading
  history and opens its page, which is the browser until B2b's reader; the
  second asks `/v2/novel/detail` for the card fields first and answers false
  when Pixiv does not have the novel. Every way in — a card, a series page,
  the watchlist, history, a link, an id typed into search — goes through
  them, so the reader batch swaps their bodies and nothing else changes.

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
  (`https://www.pixiv.net/novel/series/{id}`, worked out from the id, so a
  series Pixiv cannot hand over still offers its page beside Retry; the two
  buttons are `pixivSeriesPageActions`, shared with illustration series),
  then the numbered chapters as
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
creator's own author mute lifted as the works lists do.

The Novels tab (B2c, `PixivProfileNovels`) is offered when the profile counts
`total_novels`, or on the reader's own profile, between Works and Bookmarks.
A Works / Bookmarks switch shows the creator's novels (`userNovels`) or the
same public novel bookmarks as Bookmarks › Novels
(`PixivProfileNovelBookmarks`), both with the creator's author mute lifted.
The Works, Bookmarks and Novels tabs are each a `PixivSwitchedList`: the
switch, when there is a choice, over one list per choice, each list keyed so
it keeps its own store and scroll position.

Creator cards (`PixivUserPreviewCard`) take `previews: PixivContentMode.novel`
in novel contexts (novel search's Users tab): up to three of the
`user_previews[].novels` Pixiv sent, as covers with their titles under them,
picked by `pixivVisiblePreviewNovels` (entries that do not parse skipped,
mutes, Show R-18 and Hide AI applied). A creator with no novel to show keeps
the works row.

## Novel search (B2c)

Novel mode's Search section is `PixivNovelSearchScreen`, which is
`PixivSearchScreen(kind: PixivSearchKind.novels)`: the same field, store,
filter bar, sheet, suggestions and landing as the works' search (see
`pixiv-search.md`), with what novels need:

- **Places to look:** tags (partial), tags (exact), body text (`text`), and
  tags, title and caption (`keyword`).
- **Order:** newest and oldest for every account; popular for Premium only
  (the sheet leaves it out otherwise and says so). Pixiv has no free popular
  preview for novels, so there is no preview grid or strip.
- **Filters:** posting dates and users入り as for works; no bookmark bracket
  and no ugoira choice. Hide AI drops AI novels from each page on the device;
  `/v1/search/novel` is sent no AI parameter.
- **Remembered** filters live in `plugin.pixiv.novel_search_filters`, apart
  from the works', and `forAccount(kind: novels)` puts a carried-over place to
  look or order the novels lack back to the first one.
- **Results:** Novels and Users tabs. Novels is the novel feed under the
  filter bar; Users is the creators `/v1/search/user` found, with novel
  previews.
- **Landing:** novel searches of its own (`PixivNovelSearchHistory`,
  `plugin.pixiv.novel_search_history`, fold, forget and clear as for works),
  the trending novel tags in the three-column grid (a tap searches, a long
  press opens the work Pixiv picked) and no suggested creators.
- **A number** typed or handed in offers Open novel, Open novel series and
  Open user (`pixivNovelNumericShortcuts`); a pasted number opens as a novel.
  There is no image search.

## Reading history (B2c)

`PixivNovelHistoryStore` keeps the opened novels in the on-device JSON file
under `pixiv-history:novels`, beside the works' history and like it never in
preferences, so settings backups never carry it (the design's "the backup
carries it" gives way to that rule). An entry is the works' history entry
with the cover as its thumbnail (a novel without a cover is kept and shows a
plain book), the length in characters and the R-18 / R-18G and AI marks,
so the card keeps its chips: id, title, author, cover, tags, time,
bookmarks. `openPixivNovel` records each opening under the history's
pause switch; reopening moves the novel to the top.

The history screen (More › Viewing history, opening on Novels in Novel
mode: `PixivMorePane` is handed the sections' mode and passes it to every
More entry) has an Illustrations / Novels switch (`PixivContentModeSwitch`, also the profile Bookmarks tab's): Novels
lists the cards newest first, filtered by title or author with the field the
works use (its words stay when the switch flips). A tap opens the novel, a
long press forgets it after asking, and Clear all empties the history shown,
asking "Clear the novel history?" or "Clear the illustration history?". The
one pause switch stops both histories, and says so.
Muted novels stay listed, as muted works do. Removing the plugin's data
empties both histories.

## Links (B2c)

`openPixivLinkRef` routes `PixivNovelLinkRef` (`novel/show.php?id=`,
`/n/<id>`, `pixiv://novels/<id>`) to `openPixivNovelById` and
`PixivNovelSeriesLinkRef` (`/novel/series/<id>`) to the novel series page. A
novel Pixiv does not have answers false, so a link from outside falls back
to the browser and a typed id says it could not be found. The watchlist's
View latest goes the same way and says so when the chapter is gone.

## Comments (B2c)

A novel's long press offers *View comments (N)* (or *View comments* when the
novel came without a count, the label `pixivCommentsLabel` shared with the
artwork detail), opening `PixivCommentsScreen` on
`PixivCommentTarget.novel(id)`. The reader adds its own button in B2b.

## Preferences added

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.novel_ranking_modes` | `["day","week","day_male","day_female"]` | Yes (reset with the plugin) |
| `plugin.pixiv.novel_search_filters` | `''` (none remembered) | Yes (reset with the plugin) |
| `plugin.pixiv.novel_search_history` | `[]` | Yes (reset with the plugin) |

The novel reading history is a file, not a preference (`pixiv-history:novels`).

## For the batches that follow

- B2b: push the reader from `openPixivNovel` in place of `openUri`, keeping
  its `recordPixivNovelVisit`; `openPixivNovelById` already fetches the
  detail the reader's header needs. Cards, the series page, the watchlist,
  history, links and the search shortcuts already call these two.

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

B2c: `pixiv_novel_api_test.dart` also covers search (place to look, order,
dates, users入り, `next_url`, the search's own AI choice), trending tags (with
a reshaped answer), a creator's novels and the detail (withheld, reshaped and
missing novels are not found). `pixiv_novel_search_test.dart`: novel
targets, orders and Premium, the novel query, carried-over filters, the
separate remembered filter, pasted numbers, and the store's search, filter
change, Hide AI reaching the search and leaving AI novels out, and the
landing. `pixiv_novel_search_screen_test.dart`: the landing's own
history and trending tags, results and creator cards with novel covers, the
sheet's novel choices with and without Premium, the id shortcuts opening a
series, a novel id Pixiv does not have saying so, large text at 320 dp, and
Novel mode's Search section.
`pixiv_novel_history_test.dart`: entries without covers and with their
ratings, the novel history file, recording and the pause, and the history
screen's switch, filter, forget, Clear all and its question per kind, the
R-18 and AI chips, muted novels, More opening on the mode's kind, large text,
and forgetting the plugin's data emptying the novel history and reloading
the novel searches. `pixiv_novel_profile_test.dart`:
the Novels tab and when it is offered, a muted creator shown anyway, large
text, the preview novel picks, and the comments entry.
`pixiv_link_wiring_test.dart`: novel links open the novel and join the
history, a missing one answers false, novel series links open the page, and
one that cannot be read still offers its page on pixiv.net.
`pixiv_novel_screens_test.dart` also checks View latest saying so when the
chapter is gone.
