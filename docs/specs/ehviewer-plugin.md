# EhViewer-inspired EH plugin (private)

Read-only E-Hentai / ExHentai gallery browser inspired by the *approach* of
[FooIbar/EhViewer](https://github.com/FooIbar/EhViewer) — **none of their code
is copied or translated**. List/detail/reader parsing is written fresh against
the public HTML shapes and the documented `api.e-hentai.org` `gdata` method
([EHWiki API](https://ehwiki.org/wiki/API)).

## Private

- Listed in `plugins.json` as `{ "id": "ehviewer", "available": false }`
- `XtaPlugin.isPrivate == true`; store shows it only with “Show private
  plugins”, or once already installed

## Goals

- Browse Popular / Front page galleries
- Search by query (+ category filter)
- Open a gallery detail (tags, cover, page count, rating)
- Read pages in-app (fetch each image page URL)
- Local favorites (device-only SQLite) — no write-back to EH favorites
- Optional ExHentai via pasted cookies (`ipb_member_id` / `ipb_pass_hash`)

## Phase 1 (this PR)

| Piece | Detail |
|---|---|
| Sites | `e-hentai.org` (default), `exhentai.org` when cookies are set |
| Home tabs | Popular · Front · Favorites |
| Search | Query + category chips (Doujinshi, Manga, …) |
| Detail | Cover, titles, uploader, tags, open on site, favorite toggle |
| Previews | Paginated preview grid (`?p=N` sheets), tap to open reader |
| Reader | Sequential viewer, jump-to-page, next-page image prefetch |
| Settings | Site base, cookies, test connection, clear cookies |
| Storage | `eh_favorite` table (migration 54) |
| Catalogue | Private / unavailable |

## Phase 2 — catching up with modern EH clients

| Piece | Detail |
|---|---|
| Tags | Read from the gallery page's tag links, so names keep their spaces; namespace kept; dashed (`gtl`/`gtw`) tags marked weak. `EhTag.query` is the site's exact search (`female:"big breasts$"`). Shown grouped by namespace in the site's order with the shared `PluginTagChip`, coloured after the booru kinds (artist/group red, parody purple, character/cosplayer green, male/female/mixed/other blue, language/reclass yellow); weak tags dashed and unfilled. Tap searches exactly, long press copies |
| Gallery page | `EhGalleryStore`; compact header (120 dp cover beside the titles, uploader → their galleries, category badge); stars with average and count, pages, language (+ translated mark), file size, favorited count, posted date — each only when the site sends it, each with a spoken label; Read and Continue from page N; previews in a grid that gains columns with width, sprite tiles cut in sheet pixels, more sheets appended as you scroll; first three comments with the uploader marked, the rest in a sheet; skeleton while loading, pull to refresh |
| Search | `EhSearchStore`; the site's `tagsuggest` as you type, in namespace colours. Tag names have spaces, so the term being typed runs from the last finished term (`$` or a closing quote); the whole term is asked first, then its last two words, then its last word, and a picked tag replaces exactly the span it was found for, keeping `-`/`~`. Aliases give way to their master tag. Category chips in their colours, one-tap minimum rating, a changed filter reruns the search, history with per-entry delete |
| Gallery cards | Rating read off the list row's star sprite (16 px a star, the lower row half a star less) and the row's tags: `★ 4.5 · 24 pages` and a language badge (EN, JA, ZH…) |

## Not yet

- Watched tags / My Tags sync (needs account write-back for changes)
- EH site favorites / ratings write-back
- Downloads / archives / torrents
- Advanced search (min rating, file size, disable filters)
- Offline archive reader / download manager like EhViewer

## Hard rules

- Read-oriented only — no upload, favorite-on-site, comment, or torrent upload
- Null-safe parsing; Store pattern; ARB for UI strings
- Do not rewrite `lib/client/` / X timeline code
