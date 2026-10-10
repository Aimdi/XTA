# Pixiv — novels (batches B2a, B2b and B2c)

The novel side of the private Pixiv plugin: Novel mode for the five
sections, the novel feeds, rankings, bookmarks, series pages and the novel
watchlist (B2a); the reader (B2b); novel search and its landing, the profile
Novels tab, novel reading history, in-app novel links and the novels'
comments entry (B2c). Part of the PixEz parity plan
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
| `detail` | `GET /v2/novel/detail?novel_id=` | One novel's card fields, for a novel opened by its id alone; `notFound` when Pixiv withholds it or sends none |
| `search` | `GET /v1/search/novel?word=&search_target=&sort=&start_date=&end_date=&merge_plain_keyword_results=true&filter=for_android` | Query from `pixivSearchQuery(kind: novels)`; Hide AI applies on the device |
| `trendingTags` | `GET /v1/trending-tags/novel?filter=for_android` | Parsed by the works' `parsePixivTrendTags` |
| `userNovels` | `GET /v1/user/novels?user_id=&filter=for_android` | The reader's own keep R-18 and AI novels |
| `content` | `GET /webview/v2/novel?id=` (HTML) | The object after `novel:` in the page: text, series neighbours, pictures |

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
  (`pixiv_novel_open.dart`), which push the reader (`pixivNovelReaderRoute`).
  The first hands over the novel a card shows; the second asks
  `/v2/novel/detail` for the card fields first, then opens the reader with
  them, and answers false when Pixiv does not have the novel. Every way in —
  a card, a series page, the watchlist, a link, an id typed into search —
  goes through them. The history opens its entries with
  `openRememberedPixivNovel`, the id alone, so the reader fetches the detail
  and the header is whole rather than the few fields an entry keeps.

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
bookmarks. The reader records each novel the first time it loads
(`recordPixivNovelVisit`), under the history's pause switch; reopening moves
the novel to the top.

The history screen (More › Viewing history, opening on Novels in Novel
mode: `PixivMorePane` is handed the sections' mode and passes it to every
More entry) has an Illustrations / Novels switch (`PixivContentModeSwitch`, also the profile Bookmarks tab's): Novels
lists the cards newest first, filtered by title or author with the field the
works use (its words stay when the switch flips). A tap opens the novel in
the reader by its id (`openRememberedPixivNovel`), a long press forgets it
after asking, and Clear all empties the history shown,
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
View latest goes the same way and says so when the chapter is gone. Links
in a novel's text take the same way through `openPixivHref`.

## Comments (B2c)

A novel's long press offers *View comments (N)* (or *View comments* when the
novel came without a count, the label `pixivCommentsLabel` shared with the
artwork detail), opening `PixivCommentsScreen` on
`PixivCommentTarget.novel(id)`. The reader has its own, above and below the
text.

## Preferences and files added

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.novel_ranking_modes` | `["day","week","day_male","day_female"]` | Yes (reset with the plugin) |
| `plugin.pixiv.novel_search_filters` | `''` (none remembered) | Yes (reset with the plugin) |
| `plugin.pixiv.novel_search_history` | `[]` | Yes (reset with the plugin) |
| `pixiv-history:novels` (a JSON file, not a preference) | empty | No (emptied with the plugin's data) |
| `plugin.pixiv.novel_reading` (the reader's places) | empty | No (in `secretPrefKeys`; emptied with the plugin's data) |

Appearance is the article readers' shared `reading.appearance.v1`. The places
reached are a journal of the novels read and when, so they are viewing history
and never go into a settings export, a WebDAV backup or a crash report.

## The reader (B2b)

`PixivNovelReaderScreen` (`pixiv_novel_reader_screen.dart`) is the one way
into a novel's text.

### Loading

- `loadPixivNovelReading` (`pixiv_novel_reader_store.dart`) asks for the
  text and, for a novel opened by its id, the detail at once; the first
  failure is what the reader reports, with `FullPageErrorWidget` and Retry.
  The state is a `PixivFetchStore<PixivNovelReading?>`, let go once its load
  settles (`PixivLoads`).
- The webview page writes the novel as a JavaScript object after `novel:`.
  `pixivNovelObjectSources` (`pixiv_novel_parser.dart`) cuts each `{…}` after
  a `novel:` at its balancing brace, skipping braces and escaped quotes inside
  strings, and `pixivNovelJsonFromHtml` takes the first that decodes to an
  object. No regex. `PixivNovelContent` (`pixiv_novel_content.dart`) reads it
  with `Json`: the text, `seriesNavigation` (previous and next, with
  `viewable`, `contentOrder` and title), `images` (uploaded pictures by id:
  `1200x1200`, else `480mw`, `original`, `240mw`, `128x128`; `original` is
  saved) and `illusts` (works by `ID` or `ID-N`: `medium`, else `original`,
  `small`). The header's other fields come from the novel object the list
  sent or the detail, so the webview's rating, tags and caption are not read.
- Pages over 20,000 characters are decoded and tokenized in a background
  isolate (`pixivNovelParse`).
- The first time a novel loads it joins the novel history
  (`recordPixivNovelVisit`), unless the history is paused.

### Markup

`parsePixivNovelMarkup` is pure and never throws. Every line is a block, so
blank lines stay as the author left them; a block tag sharing a line with
text splits it, and space beside a block tag is dropped.

| Markup | Block or span | Drawn as |
|---|---|---|
| `[newpage]` | `PixivNovelPageBreak(n)`, numbered from 2 | A rule with "Page n" |
| `[chapter:…]` | `PixivNovelHeading` (ruby inside kept) | A larger bold heading |
| `[[rb:base > ruby]]` (half- or full-width arrow) | `PixivNovelRuby` | The ruby in half-size type over its base, on the line's baseline; its `WidgetSpan` scales it with the text, so its own texts do not scale again |
| `[[jumpuri:label > url]]` (http or https) | `PixivNovelLink` | A link (`openPixivNovelLink`): Pixiv addresses through `openPixivHref` (another novel in the reader, a novel series on its page, the browser for a novel Pixiv does not have), anything else after "Leave Pixiv to open …?" |
| `[jump:N]` | `PixivNovelPageJump` | "Go to page N", scrolling to that page; literal when the page does not exist (PixEz keeps it literal) |
| `[pixivimage:ID]`, `[pixivimage:ID-N]` | `PixivNovelIllustBlock` (page N from 1) | The work's picture; a tap opens the work, a long press saves |
| `[uploadedimage:ID]` | `PixivNovelUploadBlock` | The picture; a tap opens it full screen, a long press saves |

Anything else, or a tag written wrong, stays as text. A work the page carried
no picture of is fetched once (`/v1/illust/detail` through
`PixivEmbeddedWorksStore`), and its page N is shown. Pictures use
`PixivNetworkImage`, so the image server setting and the Referer apply, and
saves go through `savePixivImage` (the download path every plugin image
takes), from the image server the reader picked. A work fetched on its own is
saved as its page (`savePixivPages`), so the naming template, folders and the
download index apply as on the work's screen. `pixivNovelPlainText` is the same blocks without markup: ruby as
`base(ruby)`, links as `label (url)`, a page break as a blank line, pictures
and page jumps left out.

### Header and footer

Cover, title, the author (opens the profile), the series link (opens the
novel series page), bookmarks (following the session's heart), views, date,
length, R-18 / R-18G and AI labels, tags (a tap searches, a long press offers
Mute, Favourite or Copy, the works' own sheet), the caption with its links
(`PixivHtmlText`) and *View comments (N)* opening `PixivCommentsScreen` for
`PixivCommentTarget.novel`. After the text come *View comments* again and the
previous and next chapters, each named (title, else `#n`) and off when Pixiv
says the reader cannot open it; opening one replaces the reader.

### Appearance and place

The reader reuses the article readers' `ArticleReadingStore` and
`ArticleReaderControls` / `ArticleAppearanceSheet` (`lib/reading/`): text
size 16–28 and line spacing 1.4–2.2, shared with the RSS and Substack
readers; the app theme and true black apply. The text runs at most 680 dp
wide. The place reached is kept under `pixiv-novel:<id>` as a block and the
offset of its top, so it survives a change of text size. It goes in a journal
of its own (`ArticleReadingStore.journalKey` =
`plugin.pixiv.novel_reading`), which keeps it out of backups and leaves the
RSS and Substack places their 300 entries. A novel joins it only once the
reader scrolls, jumps to a page or finishes it (`journalOnOpen: false`), so
opening one, or one that fails to load, writes nothing. Places are kept only
while *Remember reading position* is on and the Pixiv history is not paused
(`pixivNovelRemembersPlace`); *Forget loaded data* empties the journal
(`ArticleReadingStore.forget`). A Flutter
reader reports through the new `ArticleReadingStore.receivePoint` (the web
readers keep `receiveProgress`, which now goes through it).
`PixivNovelScrollPosition` reads the first block showing from the
`AutoScrollController`'s tags and puts a place back by landing near its
fraction first, then scrolling the block to its offset. A new size keeps the
passage being read at the top: restores are queued, only the last of a burst
(a slider dragged across several steps) moves the list, and a change made
while one is under way keeps the place being restored rather than reading the
list half way there. Reaching the end after reading 12 seconds
marks the novel finished, as articles are.

### Text and menu

- The body sits in a `SelectionArea`, so Copy and Android's text actions
  (translators included) are in the selection toolbar; the caption is
  selectable on its own.
- The bar shows the title over the exact character count; it grows with
  large text. Beside it are the heart (tap: default visibility; long press:
  privately) and the menu.
- The menu: the author (opens the profile on its Novels tab, id
  `pixivNovelReaderAuthorTab` = `novels`, the first tab on a profile
  without one) with a button sharing the profile link; previous and next chapter;
  Reading appearance; Export as text; Share link; Share series link (in a
  series); Open on Pixiv. Shares are anchored to the menu button
  (`sharePositionOrigin`); the series page's share button
  (`PixivShareLinkButton`) is anchored to itself.
- Export asks for plain text or the text with Pixiv's markup and saves
  `<title>.txt` through the system save dialog (`PixivNovelExporter`, swapped
  in tests). The title loses what no file system accepts through the same
  `pixivSafeFileStem` the download names use; an empty one becomes
  `pixiv-novel-<id>.txt`.

### Not built

- PixEz's scroll-offset bookmark toggle: the journal remembers every novel
  scrolled on its own, under the app-wide *Remember reading position*.
- A muted novel opened by its id is shown, as from a watchlist row; muted
  novels never reach a list, and B2c's deep links can put a notice in front.

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
R-18 and AI chips, muted novels, a tap opening the reader by id, More
opening on the mode's kind, large text,
and forgetting the plugin's data emptying the novel history and reloading
the novel searches. `pixiv_novel_profile_test.dart`:
the Novels tab and when it is offered, a muted creator shown anyway, large
text, the preview novel picks, and the comments entry.
`pixiv_link_wiring_test.dart`: novel links open the novel and join the
history, a missing one answers false, novel series links open the page, and
one that cannot be read still offers its page on pixiv.net.
`pixiv_novel_screens_test.dart` also checks View latest saying so when the
chapter is gone.

For the reader: `pixiv_novel_parser_test.dart` (the object after `novel:`
with braces, quotes and escapes in strings, a `novel:` that opens no object,
broken pages; every tag, both arrows, unknown and malformed markup, page
numbers, plain text, the background parse), `pixiv_novel_api_test.dart`
(detail and content requests, a withheld novel, a page without the object),
`pixiv_novel_models_test.dart` (full, missing and reshaped webview content,
the history entry), `pixiv_history_store_test.dart` (a novel without a cover
across a restart) and `pixiv_novel_reader_test.dart` (header and blocks, a
card opening the reader, opening by id with the history, paused history,
error and retry, selection, the appearance sheet, chapters off when not
viewable and replacing the reader, the menu, comments above and below,
page jumps, the outside-link confirm, a Pixiv link and another novel
opening in XTA,
pictures fetched, opened and saved, export names and both formats, shares
anchored to the button on the reader and the series page, the author row's
tab, the place kept and restored, positions off, nothing journaled on open,
on a failed load or with the history paused, the journal kept out of backups
and emptied with the plugin's data, the place kept across a text size dragged
over several steps, large text and ruby at 320 dp and 2x, mirrored saves).
`article_reading_test.dart` covers `journalKey`, `journalOnOpen` and
`forget`.
