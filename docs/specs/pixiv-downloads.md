# Pixiv — saving works

How the Pixiv plugin saves pages and animations: file names, folders, the
saved-pages index, picking pages, ugoira export, the tile long-press actions
and the app-wide download queue limit. Built in parity batch B6b (see
`pixiv-pixez-gaps.md`). PixEz was read only for behaviour; nothing is copied.

## Ways to save

| Where | What it saves | Path |
|---|---|---|
| Detail / reader download button, page sheet *Download this page* | One page | `savePixivPages(…, [page])` → `PixivDownloader.savePage` (the shared plugin media path: file picker in *Ask* mode, else the download folder) |
| *Download all pages* (page sheet, overview, detail menu) | Every page | `downloadAllPixivPages` → batch into the download folder, or a folder asked for once |
| Overview *Select pages*, page sheet *Select pages* | The ticked pages | `PixivPageChoice.save(pages)` → `savePixivPages` |
| Page sheet of an ugoira (also a long-press on the playing animation) | GIF or frame ZIP | `savePixivUgoira` |
| Grid tile long-press *Download* | Every page | `downloadAllPixivPages` |

`savePixivPages` and `savePixivUgoira` return whether something was saved, so
every entry above runs through `savePixivThenBookmark` (`pixiv_page_actions.dart`)
for bookmark-after-save (`pixiv-bookmarks.md`).

## File names

`pixiv_download_naming.dart`. The template lives in
`plugin.pixiv.file_name_template`, default `{illust_id}_p{part}`.

| Token | Value |
|---|---|
| `{illust_id}` | Work id |
| `{part}` | Page number, from 0 |
| `{title}` | Work title |
| `{user_id}` | Artist id |
| `{user_name}` | Artist name |

- Tokens are filled in one pass, so a title that reads `{user_id}` stays as
  written; unknown `{…}` stays literal.
- Control characters and `/ \ : * ? " < > |` become `_`; runs of white space
  collapse; outer dots and spaces go; the stem is cut to 180 UTF-8 bytes
  between whole characters (Android allows 255 bytes for a name, and adds
  ` (1)` when a name is taken), so a CJK or emoji title is never cut mid-way;
  an empty stem falls back to `<id>_p<page>`.
- The original file's extension is kept (`.jpg` when the URL has none). Ugoira
  exports use page 0 and `.gif` / `.zip`.
- A template without `{part}` is never used: the editor will not save it, and
  a stored one falls back to the default, so pages of one work never share a
  name.

The editor (Pixiv settings → Downloads → File name) has a chip per token that
inserts at the cursor (over a selection), a live preview on a sample work, an
error under the field while `{part}` is missing, and Reset. Separators are
refused as typed.

## Folders

Two switches in the same section, `plugin.pixiv.folder_per_artist` and
`plugin.pixiv.folder_r18`, give a subfolder inside the download folder:
`R-18/` for works flagged R-18 (when the R-18 switch is on), then
`<user name>_<user id>/` (when the artist switch is on), e.g.
`R-18/Mika_42/120_p0.png`. They apply wherever the download folder is used; a
single page saved under *Always ask* goes where the reader picks.

App-wide plumbing:

- `DownloadRequest` and `DownloadEntry` carry an optional `subfolder`, kept in
  download history as `folder`. `safeDownloadFolder` cleans it on the way in
  and when history is read: `\` becomes `/`, characters no file system takes
  become `_`, `.`, `..`, empty parts and leading or trailing dots and spaces go
  (a leading dot would hide the folder from the gallery), at most four levels
  of 120 characters.
- `DownloadDirectory.saveFile` passes `subfolder` to the platform only when
  there is one. The byte-array `DownloadDirectory.save` is unchanged and takes
  no subfolder: it runs on the Android main thread, so nothing Pixiv saves
  goes through it.
- `MainActivity.downloadDestination` walks the levels from the picked tree:
  each is looked up among the children (`DocumentsContract`, case-insensitive,
  directories only) and created with `MIME_TYPE_DIR` when missing. The lookup
  and creation share one lock, so two downloads into a new folder create it
  once. A lost grant still reports `PERMISSION_LOST`.
- `saveFileToDownloadDirectory` copies only files staged by the app. It used to
  accept the cache folder alone, while resumable downloads stage in
  `files/xta-download-staging` (so a partial file survives a cache clear), which
  made every *Save to directory* download fail with `INVALID_SOURCE`; it now
  accepts both.

### Manual check (native)

The tree code needs a device; there is no Android test harness in the repo.

1. Settings → Media → Download handling: *Save to directory*, pick a folder.
2. Pixiv settings → Downloads: turn on *Folder per artist*.
3. Open a work with several pages and *Download all pages* with *Simultaneous
   downloads* at 2 or more.
4. In a file manager: exactly one `<name>_<id>` folder holds every page. Save
   another work by the same artist: the same folder is reused, no `(1)` copy.
5. With *Separate folder for R-18* on, an R-18 work lands in
   `R-18/<name>_<id>/`.
6. *Save as GIF* on an ugoira lands in the same artist folder, and the app
   stays responsive while it is written.

## Saved-pages index

`pixiv_download_index.dart`. `PixivDownloadIndex` is an app-wide store (provided
in `main.dart`) of `<illustId>_p<page>` keys in `plugin.pixiv.download_index`
(JSON list, oldest first, capped at 5000; a re-saved page moves to the end).
It is listed in `secretPrefKeys`: the pages live on this device's storage,
so no export, backup or WebDAV upload carries the list (it would name every
Pixiv work the reader saved), and an import never writes one. Plugin reset
clears it. The store listens to that preference, so a reset shows at once,
and `record` builds on the stored list rather than its own copy.

- A page is recorded when its save completes: one page after
  `PixivDownloader.savePage` reports success, a batch for the requests that
  saved, an ugoira export as page 0.
- Before saving, `choosePixivPagesToSave` asks when any chosen page is in the
  index: *Save again*, *Only new pages* (when some are new) or Cancel. Ugoira
  exports ask the same about page 0, so a second GIF is never encoded without
  asking.
- `PixivDownloadedBadge` (in `pixiv_downloaded_badge.dart`, placed by
  `pixiv_illust_tile.dart` above the heart, clear of the R-18 and AI labels on
  a narrow tile) shows a check once any page of the work is saved; its
  screen-reader label is *Saved on this device*.
- `PixivSavePageButton` is the detail and reader download button; it shows a
  filled icon and *Saved – download again* for a saved page and follows the
  page on screen.
- Pixiv settings → Downloads shows how many pages are remembered, with
  *Forget* behind a confirmation (the files stay).

## Picking pages

The page overview (`pixiv_page_overview.dart`) has *Select pages* in its bar
(multi-page works). A page's long-press sheet has it too and opens the overview
already picking. While picking (`PixivPageSelectionStore` in
`pixiv_page_selection.dart`): a tap ticks a page (accent ring and check, screen
readers hear checked / not checked), a long-press still opens the page, the
header reads *n pages selected* as a live region, and the bar holds Cancel,
All / None and *Save n pages* (disabled at zero). Saving returns the ticked
pages in reading order; one page goes the single-page way. Large text stacks
both bars full width, two actions to a row when the sheet is at least 480dp
wide, and the bar never takes more than 40% of the screen height (the rest
scrolls), so a landscape phone at 2x text keeps room for the pages.

## Ugoira export

`pixiv_ugoira_export.dart` (logic) and `pixiv_ugoira_save.dart` (flow).

- The page sheet of page 0 of an ugoira lists *Save as GIF* (with *Encoding can
  take a minute* under it) and *Save frames as ZIP*. The same sheet opens on a
  long-press of the animation, playing or not.
- Both fetch `/v1/ugoira/metadata` and `zip_urls.medium` through
  `PixivClient.ugoiraArchive`; `pixivUgoiraFrames` (shared with playback)
  reads the frames in metadata order.
- ZIP saves the archive bytes as fetched. GIF decodes each frame and encodes a
  looping GIF with package:image (a direct dependency at the 4.8.0 the lockfile
  already had), each frame keeping its own delay in hundredths of a second
  (rounded, at least 2). Encoding runs on a background isolate
  (`PixivGifEncoder`) that reports each frame and is killed on Cancel.
- The file is written into the download folder (or one asked for), named by
  the template, in the work's subfolder, through `DownloadTransfer.saveBytes`:
  the bytes are staged in the app cache and copied by
  `DownloadDirectory.saveFile` on a background thread, never sent whole
  through the platform channel.
- A snackbar shows *Fetching frames…*, *Encoding frame x of n…* with the slow
  note, then *Saving…*; Cancel works until saving starts, and while fetching
  it ends the export at once (a late response is ignored). The result is
  *Animation saved as GIF*, *Frames saved as ZIP*, *Cancelled* or *Could not
  save the animation*.

## Tile long-press

`showPixivPostActions` passes Pixiv's own entries to the shared post sheet
through `PluginPostExtraAction` (new in `plugin_post_actions.dart`, listed above
the shared entries with a divider): *Download all pages* (or *Download*),
*Bookmark* / *Remove bookmark* through `PixivBookmarkStore.toggle`, *Copy
link*, *Mute author* and *Mute this work* (both confirmed through
`confirmPixivMute`). Save on device, note, share, folder and browser stay. The
*More actions* entry of a work's own sheet leaves the Pixiv entries out, since
that screen already offers them.

## Download queue limit

`DownloadStore` runs up to `concurrency` transfers at once, oldest first;
each one that ends starts the next. `setConcurrency` clamps to 1–4 and starts
queued work at once when raised; lowering it stops nothing that runs. A store
built in code runs one at a time; the app's shared store takes
`download.concurrency` (default 2), set at launch, from Settings → Media →
Download handling → *Simultaneous downloads*, and whenever the preference
changes (a restored backup included). Pixiv batches use the same number for
their own window, so *Downloading page x of n* and Cancel cover every page in
flight.

Downloads without a folder (*Always ask*) fetch side by side but open the
system save dialog one at a time: `flutter_file_dialog` keeps one pending
dialog and fails a second call with `already_active`. `DownloadTransfer`
queues the dialog step; a download waiting its turn stays *Downloading* (so it
can still be cancelled, and then never opens a dialog), and turns *Choose
location* when its dialog opens.

## Preferences

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.file_name_template` | `{illust_id}_p{part}` | yes |
| `plugin.pixiv.folder_per_artist` | false | yes |
| `plugin.pixiv.folder_r18` | false | yes |
| `plugin.pixiv.download_index` | `[]` | yes |
| `download.concurrency` | 2 | yes |

## Tests

`pixiv_download_naming_test`, `pixiv_download_index_test`,
`pixiv_ugoira_export_test`, `pixiv_page_selection_test`,
`pixiv_tile_actions_test`, `pixiv_settings_downloads_test`,
`download_queue_limit_test`, plus updates in `pixiv_download_test`,
`pixiv_page_overview_test`, `pixiv_extras_test`, `pixiv_settings_sections_test`
and `download_directory_test`. The ugoira ZIP fixture builder is shared in
`test/support/pixiv_zip_fixture.dart`.
