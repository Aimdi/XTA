# Booru plugin

Read-only multi-booru gallery inspired by the *approach* of
[Boorusama](https://github.com/khoadng/Boorusama) — **none of their code is
copied or translated**. Engines and response parsing are written fresh against
the well-known public JSON shapes (Danbooru, Moebooru, Gelbooru v2, e621).

## Goals

- Browse posts from a configured booru host in a Pixiv-style staggered grid
- Search by tags (with autocomplete); open a post viewer with tags, score, source
- Follow tags as subscriptions that join XTA groups / optional Following mix
- Local mute list for tags; max rating filter
- Stay read-oriented: no favorites, uploads, comments, or account write-back

## Phase 1

| Piece | Detail |
|---|---|
| Engines | `danbooru`, `moebooru`, `gelbooru_v2`, `e621` |
| Presets | Danbooru, Yande.re, Konachan, Safebooru, Gelbooru, Rule34, Xbooru, e926, e621 |
| Settings | Engine, host, **Add site** for any Gelbooru/Danbooru/Moebooru/e621 host, credentials, max rating, muted tags, home-feed, tab |
| Home tabs | Latest · Following (followed tags) · Search (+ autocomplete) |
| Grid / viewer | Staggered catalog uses sample/large (~850px), not the ~150px preview; post screen opens host / source / video |
| Subscriptions | `booru_subscription` table; tags join groups via `SubscriptionSource` |
| Interleave | Recent posts per followed tag, provenance strip, fail soft |
| Catalogue | Listed in `plugins.json` |

## Engines

| Engine | Endpoint shape | Notes |
|---|---|---|
| Danbooru | `GET /posts.json` | Optional `login` + `api_key`; `s` = sensitive |
| Moebooru | `GET /post.json` | yande.re / konachan; `s` = **safe**; optional password_hash |
| Gelbooru v2 | `GET /index.php?page=dapi&s=post&q=index&json=1` | Optional user_id + api_key |
| e621 | `GET /posts.json` → `{posts:[…]}` | Nested file/preview/sample; `s` = safe; e926 preset |

Ratings are normalised to general / sensitive / questionable / explicit. Moebooru
and e621 map wire `s` to general (safe). Client-side filters apply after fetch;
Danbooru-family / Moebooru / e621 queries also append rating metatags.

## Not yet (later phases)

- More engines: Philomena, Sankaku, Shimmie2, Szurubooru, Zerochan, Nozomi,
  Anime-Pictures, Hydrus, Hybooru, Eshuushuu
- Multiple saved booru profiles with **per-host follows** (custom hosts are saved as extra chips; follows stay global)
- In-app video / GIF playback (currently opens the file URL)
- Local favorites / downloads / bulk save
- Notes / comments / pools / artists screens
- Account login flows beyond pasted API credentials

## Hard rules

- No compose / favorite / upload / comment write-backs to any booru
- Null-safe JSON parsing (`Json` / `as Type?`)
- Store pattern only; ARB for every UI string
- `lib/client/` and X timeline code untouched; DB only via migration 53
