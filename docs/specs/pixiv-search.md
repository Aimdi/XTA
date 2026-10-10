# Pixiv search

How the Pixiv plugin searches works, creators and images, and how readers keep
the searches they like. Built in parity batch B1 (see `pixiv-pixez-gaps.md`).
PixEz was used only to learn what each feature does and which endpoints and
parameters exist; the code is written fresh.

## Files

| File | Holds |
|---|---|
| `pixiv_search_filters.dart` | Pure pieces: `PixivSearchTarget`, `PixivSearchSort` (with its Premium flag), `PixivDatePreset` and `pixivPresetRange`, users入り thresholds, Premium bookmark brackets, `PixivUgoiraFilter`, the immutable `PixivSearchFilter` (JSON, `forAccount`), `pixivSearchQuery`, `pixivPopularPreviewQuery`, last-word editing and `pixivNumericQuery` |
| `pixiv_search_api.dart` | `PixivSearchApi` over the client transport: illust search, user search with previews, the popular preview (paged), trending tags, suggested creators, autocomplete. `PixivSearchApi.of(context)` prefers a provided fake |
| `pixiv_search_store.dart` | `PixivSearchStore` and its immutable `PixivSearchState`; `pixivVisibleTrendTags` |
| `pixiv_fetch_store.dart` | `PixivFetchStore<T>`, a one-call fetch with its own loading, error and retry (each landing list, each SauceNAO row's work) |
| `pixiv_confirm.dart` | `confirmPixivAction`, the shared question-and-action dialog |
| `pixiv_search_screen.dart` | The field, tabs and body switch; the app-wide `PixivSearchHistory` |
| `pixiv_search_landing.dart` | Recent searches, suggested creators, trending tags |
| `pixiv_search_results.dart` | Works under the filter bar, the popular strip, `PixivSearchPreviewNote`, creator cards |
| `pixiv_search_filter_sheet.dart` | `PixivSearchFilterBar`, `showPixivSearchFilterSheet` and the label helpers |
| `pixiv_search_shortcuts.dart` | `pixivNumericShortcuts` (the list of id shortcut builders) and the picker over the results |
| `pixiv_detail_tags.dart` | Tag chips on a work and the long-press tag sheet |
| `pixiv_favorite_tags_store.dart`, `pixiv_favorite_tags_screen.dart` | Favourite tags; one `PixivFavoriteTagsStore` is provided app-wide in `main.dart` |
| `pixiv_saucenao.dart`, `pixiv_saucenao_sheet.dart` | SauceNAO parsing, upload and sheet |
| `lib/plugins/plugin_query_words.dart` | Word splitting shared with booru tag search |

## State

`PixivSearchStore` (flutter_triple) holds the submitted word, the field text,
the filter and whether it is remembered, Premium, the suggestions, the popular
strip and whether recent searches are unfolded. It owns:

- `results`, a `PixivIllustListStore` whose filter is the mute filter followed
  by the ugoira choice, so the store's empty-page advance keeps paging when a
  page filters to nothing;
- `users`, a `PixivPagedListStore<PixivUserPreview>` keyed by user id, leaving
  out muted creators;
- `trending` and `creators`, each a `PixivFetchStore` with its own loading,
  error and retry.

Every search and filter change installs a fresh loader (`useLoader`), so a slow
page for an older query never lands in the new list, and puts the grid in its
loading state at once, so no "no results" flashes before the new page. A new
word fetches works, creators and the popular strip; a filter change fetches
the works only, and the strip only when what it shows changes (target, Hide AI,
ugoira, or a sort that brings it back). With `illustsOnly` the store fetches
works only and keeps no history, which is how a favourite tag's tab uses it.

## Filters

- **Bar over the results:** Filters (opens the sheet; tinted while the sheet's
  choices differ from a fresh filter), posting date, popularity, and for
  Premium the bookmark bracket. Each control is a 48 dp pill, tinted while it
  narrows the search; the bar scrolls sideways rather than overflow.
- **Sheet:** target (partial tags, exact tags, title and caption), sort
  (newest, popular; oldest, popular with men, popular with women for Premium),
  ugoira (all, only, none), Hide AI and Remember. Novel search can pass its own
  target list.
- **Dates:** any time, past day, week, month, 6 months, year, or a custom range
  picked between 2007-09-13 and today. Presets resolve on each search, so a
  remembered "past week" always means the week before it; months clamp to the
  shorter month (31 March less a month is 28 or 29 February).
- **Popularity:** `<N>users入り` is appended to the word sent to Pixiv only;
  the field and the history keep what the reader typed.
- **Premium:** `isPremium` comes from the stored token response. Without it a
  remembered Premium order falls back to newest and the bookmark bracket goes.
  Popular without Premium fills the grid from `/v1/search/popular-preview/illust`
  with a note that full popular sorting needs Premium. The preview takes the
  users入り word but no dates, so the date menu is hidden there and the note
  says dates do not narrow it; a favourite tag's tab under such a remembered
  filter shows the same note. Date sorts show one page of that preview as a
  strip above the grid; scrolling the strip sideways never pages the grid.
- **AI:** `search_ai_type=1` hides AI works, `0` includes them; a new filter
  starts from the plugin's Hide AI setting, and the page filter follows the
  search's choice.
- **Remember:** the whole filter is saved as JSON in
  `plugin.pixiv.search_filters` (empty for none). While remembered, every
  change is saved; turning Remember off clears it. Unknown or reshaped fields
  fall back to defaults.

## Field, suggestions and shortcuts

- Suggestions (`/v2/search/autocomplete`, debounced) complete the word after
  the last space. A tap replaces that word when earlier words exist, or
  searches when it is the only one; a long press copies the tag. Enter
  searches the raw text.
- A query of digits shows the tiles from `pixivNumericShortcuts` (Open artwork,
  Open user, Open Pixivision article) above the suggestions instead of opening
  anything. Enter searches the number as a keyword. A later feature adds its
  tile to that list. A
  screen opened with a bare number (from XTA's global search or a shared id)
  shows those tiles, with the landing loaded behind them, instead of
  searching the digits.
- Links still go through `parsePixivLink` and `openPixivLinkRef`.
- Paste puts plain text at the cursor, replacing any selection. A Pixiv link
  or id takes the whole field instead and opens at once (a bare id opens as an
  artwork; the shortcuts stay in the field for opening it as a user).

## Landing

Pull to refresh reloads trending tags and suggested creators, each on its own:
one failing shows its error and Retry while the other stays. Recent searches
fold after twelve behind *Show all (n)*; a long press forgets one and *Clear
recent searches* asks first. Trending tiles show the translated name under the
tag; a tap searches the tag and a long press opens the work Pixiv picked for
it. Muted tags leave the grid and a muted picture leaves its tag bare.

## Tags on a work

A tap searches the tag. A long press opens a sheet titled with the tag and its
translation: mute (with the usual confirmation, then leaving the work's
screen as the Mute sheet does), add to or remove from favourite tags, and copy
(with a snackbar).

## Favourite tags

An ordered JSON list of `{name, translated_name}` in
`plugin.pixiv.favorite_tags` (a bare list of names also reads). Names match
without regard to case. The settings backup carries it like other preferences.
The screen (More → Favorite tags) has a scrollable tab per tag, each a search
under the remembered filter, kept alive while switching tabs. Edit mode
reorders by drag handle and removes by swipe or the delete button, both after a
confirmation. The screen and the tag sheet share the app's one store, so a tag
pinned from a work opened from a tab shows as a tab on the way back, and a
later edit cannot write an older list over it. Removing the plugin's data
reloads it with the other Pixiv lists.

## SauceNAO

The image-search button in the field opens a sheet that says the chosen image
goes to saucenao.com. The image is picked with `file_picker`, decoded with
`dart:ui`, scaled to at most 1000 px wide and sent as PNG in a multipart POST
to `https://saucenao.com/search.php` through the Pixiv client's HTTP client; no
Pixiv token travels with it. The answer is read as JSON (`results[].data.pixiv_id`
or `ext_urls`) when it is JSON, else as the HTML page (`.result` blocks with
`.resultsimilarityinfo`, `.resulttitle`, and links to `artworks/<id>` or
`illust_id=`). Each Pixiv work appears once, with similarity, title and
artist; its thumbnail loads through `/v1/illust/detail` when the row shows,
respecting mutes, Show R-18 and Hide AI, and a refused token shows a sign-in
hint instead. A tap opens the work. The whole exchange, upload included, times
out after 45 seconds as a network error with Retry.

## Endpoints

| Call | Path and parameters |
|---|---|
| Search works | `GET /v1/search/illust` `word, search_target, sort, search_ai_type, start_date, end_date, bookmark_num_min, bookmark_num_max, merge_plain_keyword_results, filter` |
| Popular preview | `GET /v1/search/popular-preview/illust` `word, search_target, include_translated_tag_results, merge_plain_keyword_results, filter` |
| Search creators | `GET /v1/search/user` `word, filter` |
| Autocomplete | `GET /v2/search/autocomplete` `word, merge_plain_keyword_results` |
| Trending tags | `GET /v1/trending-tags/illust` |
| Suggested creators | `GET /v1/user/recommended` |
| Work preview | `GET /v1/illust/detail` `illust_id` |
| SauceNAO | `POST https://saucenao.com/search.php` multipart `file` |
