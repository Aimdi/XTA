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
| Search | Tags as chips (space/Enter finish a tag, backspace removes the last, tap a chip to edit it, "Edit as text" for the raw query); suggestions append and keep a typed `-`/`~`, coloured by tag category with post counts; quick operators and engine-correct `rating:` / score order; every executed search saved to a de-duplicated history (per tag set, swipe or ✕ to delete, clear all); a starred search is saved as one followed entry and feeds Following; related tags from the loaded results; a post's tag can be added to the search it was opened from |
| Grid / viewer | Staggered catalog uses sample/large (~850px), not the ~150px preview; post screen opens host / source / video |
| Subscriptions | `booru_subscription` table; tags join groups via `SubscriptionSource` |
| Interleave | Recent posts per followed tag, provenance strip, fail soft |
| Catalogue | Listed in `plugins.json` |

## Phase 2 — closer to Boorusama

| Piece | Detail |
|---|---|
| Presets | + AIBooru (Danbooru), Sakugabooru (Moebooru), TBIB (Gelbooru v2) |
| Home tabs | Latest · **Popular** · Following. Popular has Day / Week / Month and steps or picks a date (Danbooru `explore/posts/popular`, e621 `popular`, Moebooru `popular_by_*`); Gelbooru keeps no list, so it shows `sort:score` with a note |
| Viewer | Swipes sideways through the list a post was opened from and keeps loading the feed (`BooruPagerStore`); tap the picture for the shared full-screen viewer (pinch / double-tap zoom, download, share); videos and Danbooru's webm-sampled animations play in the app's player; download original, save on this device (same archive id as the feed card), share / copy link, copy tags, open on host / source / file |
| Details | Score, up/down votes, favorites, rating, size, file type and size, posted date, uploader — whichever the host sends |
| Tags | Grouped Artist · Copyright · Character · Species · General · Meta as compact chips tinted in their kind's colour (shared `PluginTagChip`), names with spaces, and a short post count (5.45M). One lookup per post: Danbooru `tags.json?search[name_comma]=`, e621 `tags.json?search[name]=`, Gelbooru `s=tag&names=` (kinds and counts); Moebooru `post.json?include_tags=1` (kinds only — its API cannot count a list of tags). Kinds the post carries stand in if the lookup fails. Sort A–Z or most used first (kept in preferences); followed / hidden markers; full counts for screen readers; 48 dp touch targets |
| Tag actions | Tap a tag: search it, add it to / exclude it from the search the post came from, follow, hide, read its wiki (not Gelbooru), copy |
| Related | "More from {artist}" and parent / child posts as strips; "See all" opens the search |
| Comments | Read-only, loaded when opened (Danbooru, e621, Moebooru; Gelbooru only answers in XML) |
| Blacklist | An entry may hold several tags, hiding posts that have all of them; `-tag`, `~a ~b` and `rating:x` (host letters) work; old single-tag entries load unchanged |
| Display | Grid columns (fit or 2–5), smaller thumbnails, details under thumbnails, blur questionable / explicit thumbnails, original files in full screen |

Pure pieces are unit-tested without HTTP: `booru_endpoints.dart` (URLs and
credential names per engine), `booru_detail_parse.dart` (tag kinds, wiki,
comments), `booru_text.dart` (DText / HTML to plain text), `booru_popular.dart`
(date stepping).

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
  Anime-Pictures, Hydrus, Hybooru, Eshuushuu (Philomena, Zerochan and
  Anime-Pictures write tags with spaces, which the chip field splits on)
- Multiple saved booru profiles with **per-host follows** (custom hosts are saved as extra chips; follows stay global)
- A Booru grid of saved posts (saves land in the app's Saved list today) and bulk download of a search
- Translation notes over the picture, pools and artist pages
- Account login flows beyond pasted API credentials

## Hard rules

- No compose / favorite / upload / comment write-backs to any booru
- Null-safe JSON parsing (`Json` / `as Type?`)
- Store pattern only; ARB for every UI string
- `lib/client/` and X timeline code untouched; DB only via migration 53
