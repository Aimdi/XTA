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

## Not yet

- Watched tags / My Tags sync
- EH site favorites / ratings write-back
- Downloads / archives / torrents
- Comments
- Advanced search (min rating, file size, disable filters)
- Full sprite-perfect preview cropping across all display modes
- Offline archive reader / download manager like EhViewer

## Hard rules

- Read-oriented only — no upload, favorite-on-site, comment, or torrent upload
- Null-safe parsing; Store pattern; ARB for UI strings
- Do not rewrite `lib/client/` / X timeline code
