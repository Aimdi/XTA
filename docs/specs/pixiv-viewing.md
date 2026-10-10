# Pixiv — viewing

What batch B6a of the PixEz parity plan (`pixiv-pixez-gaps.md`) built. The
plugin as a whole is described in `pixiv-plugin.md`; gallery speed work is in
`pixiv-performance.md`.

## Settings

A **Viewing** section (`pixiv_settings_viewing.dart`) sits between Content and
Mute in `pixivSettingsSections`. Every key below has its default in
`pixivViewingDefaults` (`pixiv_viewing_prefs.dart`), which `main.dart`
registers and the plugin reset writes back. None of them is a secret, so
settings backups carry them.

| Setting | Pref | Default | Choices |
|---|---|---|---|
| Image server | `plugin.pixiv.image_host` | `i.pximg.net` | Pixiv's own, the `i.pixiv.re` mirror, or a custom address |
| Grid images | `plugin.pixiv.quality_feed` | `medium` | medium, large |
| Work pages | `plugin.pixiv.quality_detail` | `large` | medium, large, original (single works and manga alike) |
| Full-screen pages | `plugin.pixiv.quality_reader` | `large` | large, original |
| Grid columns in portrait / landscape | `plugin.pixiv.grid_columns_portrait` / `_landscape` | `0` (automatic) | automatic, 2, 3, 4 |
| Work layout | `plugin.pixiv.detail_layout` | `auto` | auto, vertical, split |
| Divider position | `plugin.pixiv.detail_split` | `0.64` | set by dragging |
| Swipe between works | `plugin.pixiv.swipe_between_works` | off | |
| Show AI badge | `plugin.pixiv.ai_badge` | on | |

Choices open in a dialog (`PixivPrefChoice`, next to `PixivPrefSwitch` in
`pixiv_settings_content.dart`); the subtitle shows the current one.

Widgets read these through `pixivPrefsOf(context)`, which finds the
`PrefService` without depending on it, so tests and previews built without one
get the defaults. A works grid wraps itself in `PixivPrefsBuilder`, which
listens to just the keys a grid reads (`pixivGridPrefKeys`), so a changed
setting shows at once without the grid repainting on every token or position
the app saves.

## Image server

`pixiv_image_source.dart`:

- `pixivImageUrl(url, host)` changes only URLs on `i.pximg.net`, keeping the
  path (and query). `s.pximg.net` and every other host are never touched, nor
  is anything when the host is Pixiv's own or unusable.
- `parsePixivImageHost` accepts a bare host or an `http(s)` address with port
  and path prefix (`https://example.com:8443/pixiv` serves
  `/pixiv/img-master/…`). Whitespace inside, other schemes, credentials,
  queries and fragments are refused.
- The dialog offers Pixiv, the public mirror and a custom address with the
  error shown under the field and Save disabled until it parses; *Reset to
  default* goes back to Pixiv's own server.
- Applied in `PixivNetworkImage` (so tiles, the viewer, the reader, avatars,
  the page overview and group Discover), in thumb and next-page prefetch, in
  `PixivClient.ugoiraArchive`, in `pixivPageMedia` / `pixivPageRequests`
  (downloads) and in Share image. Archived posts keep Pixiv's own URLs.
- The Pixiv Referer is still sent with each image request; the shared download
  transfer adds it only for `pximg.net` hosts, so a mirror never receives it
  from there.

## Image sizes

`pixiv_quality.dart` is a pure pick over the URLs a `PixivIllust` already
carries: `pixivTileUrl(illust, quality)` (medium preview or `largeUrl`) and
`pixivPageUrl(illust, page, quality)` (`thumbUrlAt`, `viewerUrls`,
`downloadUrlAt`). Each falls back to what Pixiv did send. `pixivQuality(prefs,
slot)` ignores a stored size the slot does not offer. Downloads always save
the original.

The detail's first page still paints the grid's own picture underneath (now
the grid-size URL, so it is the cached one) until the page loads.

## Grid columns

`pixivGridColumns(width, orientation, textScaler, prefs)`: automatic is the
shared `pluginGalleryColumns`; a picked count applies to its orientation and
never squeezes tiles under 96dp. `PixivIllustGrid` (every feed, search and,
after B5a, profile tabs), similar works and the profile grid all use it.

## Loading states

- A tile or detail page whose image failed shows a retry button
  (`pixivRetryLoadState`, `PixivImageRetry`) instead of ExtendedImage's
  untranslated text. On a tile the rest of the card still opens the work.
- Reader pages load with `handleLoadingProgress` and show a determinate ring
  (`pixivLoadProgress`) once the size is known.

## Reader bar and zoom

`PixivReaderBar` (`pixiv_reader_bar.dart`) is always shown:

- **Share image** stays disabled until the page on screen has loaded
  (`PixivReaderState.loaded`). `PixivImageSharer` copies the picture the image
  cache holds (`getCachedImageFile`), else downloads it, into
  `<temp>/pixiv_share/<id>_p<page>.<ext>` and hands that to the share sheet.
  A failure says so in a snackbar. `PixivImageSharer.of` takes a provider
  override, as `PixivDownloader.of` does.
- **Original quality** toggles the reader between large and original files
  for this visit; it starts on when Full-screen pages is set to original.
- Many pages add the slider and the page counter, on the same line when there
  is room (300dp in text-sized units) and on a line of their own otherwise.

The vertical reader is now zoomable too (pinch, and double-tap where tapped),
with the whole list in one `PixivZoomable`. `PixivZoomable.onZoomChanged`
feeds `PixivReaderStore.setZoomed`; while zoomed or in HD, pages decode at
three times the screen's width, never past 4096px
(`PixivNetworkImage.fullResolution`, `pixivFullResolutionWidth`), with
`gaplessPlayback`, so the picture stays up while the sharper decode loads. The
cap keeps a few built pages of an 8000px original from exhausting a phone's
memory. Turning a page in the horizontal reader, by swipe, slider or page
overview, lets go of the zoom.

## Ugoira

- `PixivUgoiraView` follows `TickerMode`: a route pushed over it (or anything
  else that stops tickers) holds playback, and it plays on when uncovered.
  The reader's own pause is never undone. Covering it while frames download
  leaves it paused once they arrive.
- `PixivClient.ugoiraArchive` keeps the last four archives in memory
  (`PixivLruCache`), sharing a fetch in flight; a failed fetch is dropped so
  the next attempt goes to the network. B6b's export can reuse them.

## Swipe between works

`openPixivIllustFromList(context, illusts, index, source:)` (the B0 seam)
opens a single work as before unless Swipe between works is on. Then it pushes
`PixivIllustPager` (`pixiv_illust_pager.dart`):

- Each page is `pixivIllustPage(illust)` — the same builder `pixivIllustRoute`
  uses, so whatever every opening does applies to swiped-to works too.
- Tiles pass the list they show and its store (`PixivIllustTile.source`, set
  by `PixivIllustGrid`/`PixivIllustFeed`, similar works, the profile and
  search). `PixivIllustPagerStore` keeps its own copy and only appends the
  store's later pages, passed through the store's own filter (else the
  reader's mutes), so the work on screen never moves and a profile shown
  anyway keeps its muted creator's works.
- Two works from the end it asks the store for its next page. Pixiv's feeds
  repeat works across pages, so a page that adds nothing new is followed by
  the next, up to `pixivEmptyPageAdvanceLimit` pages. The page after the last
  work shows a spinner, *Couldn't load more works* with Retry (also when those
  pages brought only repeats), or *No more works*.
  `PixivPagedListStore.loadMore` now returns the load already in flight and
  records `loadMoreFailed`, which a refresh clears.
- A work with several pages keeps its own page swipe. Its viewer reports a
  drag pushed past its first or last page (`PixivPageEdgeNotification`, from
  clamping and bouncing physics); after 56dp the pager turns to the
  neighbouring work, once per drag. The finger lifting anywhere over the pager
  ends the drag, since a turn can carry the dragged work off screen first.
  Other horizontal lists in the detail do not turn works.
- A work swiped back into view reopens on the page it was left at, and its
  counter follows.

## Side-by-side detail

`pixiv_detail_split.dart`: with layout `auto` the detail splits from 840dp;
`split` splits wherever 240dp of pictures, a 16dp gutter and 320dp of details
fit (a landscape phone), `vertical` never does. Pictures and the page bar sit
on the left, filling the height; author, stats, caption, tags, the author's
works and similar works scroll on the right with pull-to-refresh. The divider
has a 48dp drag target and screen-reader increase/decrease steps; its position
is kept in `plugin.pixiv.detail_split` when let go. Pages decode at the
screen's width rather than the pane's, so dragging the divider never decodes
them again, and `gaplessPlayback` keeps them up through a rotation. The
viewer's page survives a rotation between the two layouts.

## Merge notes

- B7 gates muted works and records the viewing history inside
  `pixivIllustRoute`; on merge both moved into `pixivIllustPage`, so
  swiped-to works pass the gate and join the history as well.
- B6b owns the download pipeline; this batch only passes the image server into
  `pixivPageMedia` / `pixivPageRequests`.
