# TikTok plugin (private, guest)

Read-only TikTok browser. **No TikTok account.** Follows and likes stay on
this device. Inspired by the *approach* of guest HTML + unsigned
`/api/creator/item_list/` used by yt-dlp — **none of their code, and none of
Grayjay’s Python/Playwright sidecar, is copied**.

Grayjay’s community TikTok plugin talks to a LAN FastAPI + Playwright server.
That is not an on-device guest client and is not used here.

## Private

- Listed in `plugins.json` as `{ "id": "tiktok", "available": false }`
- `XtaPlugin.isPrivate == true`; store shows it only with “Show private
  plugins”, or once already installed

## What works without an account

Confirmed live (2026-08-12) from Dart-equivalent `http`:

| Request | Result |
|---|---|
| `GET https://www.tiktok.com/@{handle}` | HTML `__UNIVERSAL_DATA_FOR_REHYDRATION__` → `webapp.user-detail.userInfo.user` (`secUid`, profile) |
| Unsigned `GET /api/creator/item_list/?aid=1988&secUid=…` | `statusCode: 0`, `itemList[]`, `hasMorePrevious` — **no X-Bogus** |
| Cover / avatar CDN | 200 |
| `playAddr` CDN GET | often **403** (Akamai) from datacenter IPs even with `ttwid` + Referer |
| `/api/recommend/item_list/` (For You) | empty without signing — **not implemented** |

## Behaviour

| Piece | Detail |
|---|---|
| Home | Following (local SQLite handles, `AccountPostCache` merge) · Accounts list |
| Open | Guest search: discover people, query suggestions, and profile-HTML handle guesses (signed `/api/search/*/full/` is empty without X-Bogus — not used) |
| Profile | `TikTokProfileStore`; private accounts show a lock, not an error; posts paginate |
| Cards | Cover + play overlay (never inline `TweetVideo` — CDN 403). Photos show cover only |
| Player | Native `media_kit` with Referer + Cookie; on first-frame failure, WebView `embed/v3/{id}` with guest cookies and Android autoplay |
| Settings | Show tab, prefer embed, test connection, clear guest session |
| Storage | `tiktok_subscription` (migration 55) |
| Catalogue | Private / unavailable |

## Not yet / not ever

- TikTok login, For You, following-on-TikTok, like/comment/repost write-back
- DMs, live, slideshow-as-a-reader (covers parse; there is no page-by-page album)
- X-Bogus / X-Gnarly / Playwright sidecar (rotates; not maintainable in Dart)
- Interleaving TikTok into the X home feed (vertical video ≠ tweet cards)

## Hard rules

- Read-oriented only
- Null-safe parsing via `Json` (`/parse-api`)
- Store pattern; ARB for UI strings
- Do not rewrite `lib/client/` / X timeline code
- Optional `httpHeaders` on `TweetVideoUrls` must stay null for X / Reddit
- Optional `onPlaybackError` on `TweetVideo` is a callback only — X cards ignore it
