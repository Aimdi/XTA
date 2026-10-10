# Pixiv — novels (batches B2a and B2b)

The novel side of the private Pixiv plugin: Novel mode for the five
sections, the novel feeds, rankings, bookmarks, series pages and the novel
watchlist (B2a), and the reader (B2b). Novel search, the profile Novels tab,
the novel history screen and deep links (B2c) build on them. Part of the PixEz parity plan
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
| `detail` | `GET /v2/novel/detail?novel_id=` | A novel opened by its id alone; withheld is not found |
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
  (`pixiv_novel_open.dart`), which push the reader (`pixivNovelReaderRoute`):
  a card hands over the novel it shows, an id alone makes the reader fetch
  the detail too.

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

## Preferences and files added

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.novel_ranking_modes` | `["day","week","day_male","day_female"]` | Yes (reset with the plugin) |
| `pixiv-history:novels` (a JSON file, not a preference) | empty | No (emptied with the plugin's data) |

The reader adds no preference of its own: appearance and places are the
shared `reading.appearance.v1` and `reading.articles.v1`.

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
| `[[rb:base > ruby]]` (half- or full-width arrow) | `PixivNovelRuby` | The ruby in half-size type over its base, on the line's baseline |
| `[[jumpuri:label > url]]` (http or https) | `PixivNovelLink` | A link (`openPixivNovelLink`): another novel opens in the reader and a novel series on its page, other Pixiv addresses through `openPixivHref`, anything else after "Leave Pixiv to open …?" |
| `[jump:N]` | `PixivNovelPageJump` | "Go to page N", scrolling to that page; literal when the page does not exist (PixEz keeps it literal) |
| `[pixivimage:ID]`, `[pixivimage:ID-N]` | `PixivNovelIllustBlock` (page N from 1) | The work's picture; a tap opens the work, a long press saves |
| `[uploadedimage:ID]` | `PixivNovelUploadBlock` | The picture; a tap opens it full screen, a long press saves |

Anything else, or a tag written wrong, stays as text. A work the page carried
no picture of is fetched once (`/v1/illust/detail` through
`PixivEmbeddedWorksStore`), and its page N is shown. Pictures use
`PixivNetworkImage`, so the image server setting and the Referer apply, and
saves go through `savePixivImage` (the download path every plugin image
takes). `pixivNovelPlainText` is the same blocks without markup: ruby as
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
wide. The place reached is kept under `pixiv-novel:<id>` in the shared
journal as a block and the offset of its top, so it survives a change of
text size, and only while *Remember reading position* is on. A Flutter
reader reports through the new `ArticleReadingStore.receivePoint` (the web
readers keep `receiveProgress`, which now goes through it).
`PixivNovelScrollPosition` reads the first block showing from the
`AutoScrollController`'s tags and puts a place back by landing near its
fraction first, then scrolling the block to its offset. A new size keeps the
passage being read at the top. Reaching the end after reading 12 seconds
marks the novel finished, as articles are.

### Text and menu

- The body sits in a `SelectionArea`, so Copy and Android's text actions
  (translators included) are in the selection toolbar; the caption is
  selectable on its own.
- The bar shows the title over the exact character count; it grows with
  large text. Beside it are the heart (tap: default visibility; long press:
  privately) and the menu.
- The menu: the author (opens the profile on its Novels tab, id
  `pixivNovelReaderAuthorTab` = `novels`, the first tab until that tab
  exists) with a button sharing the profile link; previous and next chapter;
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

- PixEz's scroll-offset bookmark toggle: the shared journal remembers every
  novel's place on its own.
- A muted novel opened by its id is shown, as from a watchlist row; muted
  novels never reach a list, and B2c's deep links can put a notice in front.

## For the batches that follow

- B2c: novel search fills Search in Novel mode (`_novelSections` in
  `pixiv_screen.dart` reuses the illustration Search until then); novel deep
  links route `PixivNovelLinkRef` and `PixivNovelSeriesLinkRef` to
  `openPixivNovelById` and `openPixivNovelSeries`, after which the reader's
  `openPixivNovelLink` can hand every Pixiv link to `openPixivHref` (it routes
  the two novel refs itself until then); the profile Novels tab can use
  `PixivOwnedNovelFeed` and should take the id `novels`
  (`pixivNovelReaderAuthorTab`). Novel visits are already recorded in
  `PixivNovelHistoryStore` (`pixiv-history:novels`, provided in `main.dart`,
  emptied with the plugin's data); its entries carry the cover as
  `thumbUrl`, and a reader opened from history takes the id alone.

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

For the reader: `pixiv_novel_parser_test.dart` (the object after `novel:`
with braces, quotes and escapes in strings, a `novel:` that opens no object,
broken pages; every tag, both arrows, unknown and malformed markup, page
numbers, plain text, the background parse), `pixiv_novel_api_test.dart`
(detail and content requests, a withheld novel, a page without the object),
`pixiv_novel_models_test.dart` (full, missing and reshaped webview content,
the history entry) and `pixiv_novel_reader_test.dart` (header and blocks, a
card opening the reader, opening by id with the history, paused history,
error and retry, selection, the appearance sheet, chapters off when not
viewable and replacing the reader, the menu, comments above and below,
page jumps, the outside-link confirm, a Pixiv link and another novel
opening in XTA,
pictures fetched, opened and saved, export names and both formats, shares
anchored to the button on the reader and the series page, the author row's
tab, the place kept and restored, positions off, large text at 320 dp).
