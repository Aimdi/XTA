# Pixiv — discovery (batch B4)

The browse surfaces of the private Pixiv plugin: Home's sources, rankings,
Pixivision, series, the manga watchlist, the signed-out preview and the
work's ID and size. Part of the PixEz parity plan (`pixiv-pixez-gaps.md`);
the plugin as a whole is described in `pixiv-plugin.md`.

PixEz was read only to learn what these features do and which endpoints and
fields exist. None of its code, widget trees or file layout is reused.

## Endpoints

Every call lives in `PixivDiscoveryApi` (`pixiv_discovery_api.dart`) over
`PixivClient`'s public transport. Screens read it through
`PixivDiscoveryApi.of(context)`, which prefers a `Provider` override (tests
use `FakePixivDiscoveryApi` from `test/support/pixiv_discovery_fakes.dart`).

| Call | Request | Notes |
|---|---|---|
| `mangaRecommended` | `GET /v1/manga/recommended?include_ranking_label=true&filter=for_android` | Feed filters apply |
| `following` | `GET /v2/illust/follow?restrict=all\|public\|private` | |
| `ranking` | `GET /v1/illust/ranking?mode=&date=&filter=for_android` | AI boards pass `includeAi: true` |
| `recommendedUsers` | `GET /v1/user/recommended?filter=for_android` | Previews follow Show R-18 / Hide AI |
| `spotlightArticles` | `GET /v1/spotlight/articles?filter=for_android&category=all` | |
| `illustSeries` | `GET /v1/illust/series?illust_series_id=&filter=for_android` | Header repeated on every page |
| `seriesContext` | `GET /v1/illust-series/illust?illust_id=` | Neighbours the filters hide are dropped |
| `addToWatchlist` / `removeFromWatchlist` | `POST /v1/watchlist/manga/add\|delete` form `series_id` | The only writes this batch adds |
| `mangaWatchlist` | `GET /v1/watchlist/manga` | |
| `walkthrough` | `GET /v1/walkthrough/illusts` | Sent without a token (`getJson(auth: false)`), next pages too |
| `pixivisionArticle` | `GET https://www.pixivision.net/{lang}/a/{id}` (HTML) | Desktop User-Agent, pixivision Referer, `Accept-Language`; no token |

Parsing is null-safe through `Json`: series, contexts, watchlist rows and
spotlight articles without an id are skipped rather than failing the page
(`pixiv_discovery_models.dart`).

## Home

`PixivHomeSection` shows four source chips — Following, Recommended, Manga,
Watchlist — with the people icon (followed creators) kept beside Following.
Each source's list is a session store (`PixivHomeStores.obtain`), so switching
back keeps its scroll and data; Following is the app-wide `PixivFeedStore`.

- **Following** carries an icon-only segmented control (all / public /
  private follows, each with a tooltip) while it is the chosen source. The
  choice is session state (`PixivViewState.followRestrict`); the shell swaps
  the feed's loader to `PixivDiscoveryApi.following(restrict)` and puts it
  back to every follow when the Home session ends, so the app-wide feed is
  never left narrowed.
- **Recommended** heads its works (through `PixivIllustGrid.leadingSlivers`)
  with a Pixivision carousel and a strip of suggested creators, each with
  See all. A part with nothing to show is left out.
- **Manga** is `/v1/manga/recommended` in the same grid, mute and filters.
- **Watchlist** lists watched manga series (below).

## Rankings

`pixiv_ranking_modes.dart` holds one ordered table of boards
(`pixivIllustRankingModes`) with labels and gates: daily, weekly, monthly,
male, female, original, rookie, `day_manga` (an XTA extra), AI, R-18 daily,
R-18 AI, R-18 weekly and R-18G weekly.

- The Rankings section shows the pinned boards as choice chips followed by an
  Edit chip, beside the archive date picker. Edit opens a sheet of filter
  chips; each tap saves at once, and the last visible pin cannot be removed.
- Pins are a JSON list in `plugin.pixiv.ranking_modes` (default: the eight
  boards XTA always had; reset with the plugin). Unknown or repeated ids are
  dropped on read.
- R-18 boards are offered and shown only while Show R-18 works is on; their
  pins stay saved while hidden. When the shown board loses its chip
  (unpinned, or R-18 turned off) the section moves to the first chip and
  reloads.
- AI boards keep their AI works even with Hide AI on: the reader asked for
  that board by name. Every other board still drops them.
- `PixivRankingModeChips`, `PixivRankingPinsStore` and
  `showPixivRankingModeSheet` take their table, pref key and defaults, so
  novel rankings reuse them.

## Pixivision

- `PixivisionArticleCard` (thumbnail, two-line title, date) appears in the
  Home carousel and in `PixivisionListScreen`, a paged grid sized by
  `pluginGalleryColumns` with pull-to-refresh. More › Pixivision articles
  opens the list too.
- `PixivisionArticleScreen` fetches the article page in the reader's language
  (`pixivisionLanguage`: `ja`, `ko`, `zh`, `zh-tw`, else `en`). Its app bar
  carries the picture and title (black with white text over the picture in
  every theme), with share and open in browser. The intro is a selectable
  card, then each featured work is a card that opens the work through
  `openPixivLinkRef`; the artist row opens the profile.
- `pixivision_parser.dart` is a pure `package:html` parser. It finds works by
  the links a block carries, not by class names: each work is the widest
  element around an artwork link that features no other work and also links
  the artist. That reads both the English and the Chinese layouts, lazy
  `data-src` images, tracking queries and the older `member.php` /
  `member_illust.php` links, and never lends one work's artist to another.
  The intro is the header description, else the paragraphs before the first
  work. Fixtures: `test/fixtures/Pixivision/`.

## Series

- `PixivIllust.series` drives "Series: <title>" under the detail's title
  (`PixivDetailSeries`) and a one-line, 48 dp link under a tile's title.
- The detail asks `/v1/illust-series/illust` for the work's place and shows
  "#n of N" between previous and next buttons; moving replaces the work
  rather than stacking another.
- `PixivSeriesScreen` (`openPixivSeries(context, id)`) shows the cover, title,
  author, caption, work count and start date, a watchlist toggle with its own
  busy state, the paged works grid, share and open on Pixiv. Its URL is
  `https://www.pixiv.net/user/{uid}/series/{sid}` (`pixivSeriesUrl`).

## Watchlist

`PixivWatchlistFeed` lists `PixivWatchlistRow`s: cover with the published
count, title, author, when it last updated and View latest. A row opens the
series; View latest fetches `latest_content_id` and opens the work. The feed
takes its store and openers, so the novel watchlist reuses it.

## Signed-out preview

Without a refresh token every section but More shows `PixivSignInBody`: the
sign-in button over a masonry grid of `/v1/walkthrough/illusts`. Tapping a
preview work says to sign in, because the detail needs an account.

## Detail info

`PixivDetailStats` adds the artwork ID (tapping copies it, 48 dp target with
a copy hint for screen readers) and the size as "W × H px". Stat labels wrap
rather than overflow at large text sizes.

## Shared pieces

`PixivPagedFeed<T>` (`pixiv_paged_feed.dart`) is the paged list for anything
that is not a works grid — creators, articles, watchlist rows: placeholder,
soft refresh, failed appends kept, retry, and the next page asked for near
the end.

## Left for later batches

- pixivision.net/<lang>/a/<id> and pixiv.net/user/<uid>/series/<sid> links:
  B7 owns `pixiv_links.dart` and `pixiv_link_open.dart`; wave 2 routes those
  refs to `openPixivisionArticle(context, id)` and `openPixivSeries(context, id)`.
- The Pixivision-ID tile in search's numeric shortcuts: the shortcut list is
  B1's, which has not landed; it calls `openPixivisionArticle`.
