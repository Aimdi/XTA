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

## Phase 1 (this PR)

| Piece | Detail |
|---|---|
| Home | Following (local SQLite handles) · Accounts list |
| Open | Exact `@handle` lookup (no fuzzy search — that API is WAF’d) |
| Profile | Avatar, stats, posts, local follow |
| Player | Native `media_kit` with Referer + Cookie headers; WebView `embed/v3/{id}` fallback |
| Settings | Show tab, prefer embed, test connection, clear guest session |
| Storage | `tiktok_subscription` (migration 55) |
| Catalogue | Private / unavailable |

## Not yet / not ever

- TikTok login, For You, following-on-TikTok, like/comment/repost write-back
- DMs, live, slideshow-only posts as a first-class reader
- X-Bogus / X-Gnarly / Playwright sidecar (rotates; not maintainable in Dart)
- Interleaving TikTok into the X home feed (vertical video ≠ tweet cards)

## Hard rules

- Read-oriented only
- Null-safe parsing via `Json` (`/parse-api`)
- Store pattern; ARB for UI strings
- Do not rewrite `lib/client/` / X timeline code
- Optional `httpHeaders` on `TweetVideoUrls` must stay null for X / Reddit
