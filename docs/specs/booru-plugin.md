# Booru plugin

Read-only multi-booru gallery inspired by the *approach* of
[Boorusama](https://github.com/khoadng/Boorusama) — **none of their code is
copied or translated**. Engines and response parsing are written fresh against
the well-known public JSON shapes (Danbooru, Moebooru, Gelbooru v2).

## Goals

- Browse posts from a configured booru host in a Pixiv-style staggered grid
- Search by tags; open a post viewer with tags, score, source, full image
- Follow tags as subscriptions that join XTA groups / optional Following mix
- Stay read-oriented: no favorites, uploads, comments, or account write-back

## Phase 1 (this PR)

| Piece | Detail |
|---|---|
| Engines | `danbooru`, `moebooru`, `gelbooru_v2` |
| Presets | Danbooru, Yande.re, Konachan, Safebooru (+ custom host) |
| Settings | Engine, host URL, optional login/API key, max rating, home-feed toggle, tab |
| Home tabs | Latest · Following (union of followed tags) · Search |
| Grid / viewer | Staggered thumbnails → post screen (sample/full, tags, open source) |
| Subscriptions | `booru_subscription` table; tags join groups via `SubscriptionSource` |
| Interleave | Recent posts per followed tag, provenance strip, fail soft |
| Catalogue | Listed in `plugins.json` |

## Engines (Phase 1)

| Engine | Endpoint shape | Notes |
|---|---|---|
| Danbooru | `GET /posts.json?tags=&page=&limit=` | Optional `login` + `api_key` query params |
| Moebooru | `GET /post.json?tags=&page=&limit=` | yande.re / konachan; unix `created_at` |
| Gelbooru v2 | `GET /index.php?page=dapi&s=post&q=index&json=1` | Optional `user_id` + `api_key`; Safebooru works guest |

Ratings are normalised to `g` / `s` / `q` / `e`. The reader picks a **maximum**
rating (default `g`); stricter posts are filtered client-side after fetch, and
Danbooru-family queries also append `rating:…` when the host supports it.

## Not yet (later phases)

- More engines from Boorusama’s set: e621, Philomena, Sankaku, Shimmie2,
  Szurubooru, Zerochan, Nozomi, Anime-Pictures, Hydrus, Hybooru, Eshuushuu
- Multiple saved booru profiles (switch hosts without losing per-host follows)
- Tag autocomplete / blacklists / metatags UI
- Local favorites / downloads / bulk save
- Video / animated-GIF playback beyond opening the file URL
- Notes / comments / pools / artists screens
- Account login flows beyond pasted API credentials

## Hard rules

- No compose / favorite / upload / comment write-backs to any booru
- Null-safe JSON parsing (`Json` / `as Type?`)
- Store pattern only; ARB for every UI string
- `lib/client/` and X timeline code untouched; DB only via migration 53
