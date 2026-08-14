# Instagram plugin (private)

Read-only Instagram browser. **No posting, like-on-IG, follow-on-IG, or DMs.**
Follows and likes stay on this device.

TikTok-shaped home (Following + Accounts). Threads-shaped **optional session**
(same Instagram cookie names). Guest is attempted first; it works on many
phones and is blocked from typical datacenter IPs.

## Private

- Listed in `plugins.json` as `{ "id": "instagram", "available": false }`
- `XtaPlugin.isPrivate == true`; store shows it only with “Show private
  plugins”, or once already installed

## Why not the official API / a scraper farm

| Approach | Verdict |
|---|---|
| Instagram Graph API | Wrong product: app review, Business/Creator login, only accounts you own. Basic Display shut down Dec 2024. |
| Guest HTML scrape | Dead in 2026. `instagram.com/{user}` is a login shell; no OG tags, no `_sharedData`. |
| Signed / Playwright sidecar | Rotates; not maintainable in Dart (same hard rule as TikTok). |
| Third-party scrape APIs | Extra tracker; against the “talk to the site” style of every other plugin. |
| Reuse Threads as Instagram | Threads already calls `i.instagram.com`, but only `/feed/text_post_app_timeline/` (Threads posts). Parsers require `text_post_app_info`. |

## What still answers (probed 2026-08-14)

From this Cloud VM (datacenter egress):

| Request | Result |
|---|---|
| `GET www.instagram.com/` | 200, mints `csrftoken` + `mid`, LSD in HTML |
| `GET www.instagram.com/{user}/` | 200 login-shell HTML, **no** profile JSON |
| `GET i.instagram.com/api/v1/users/web_profile_info/?username=` | **429** empty (IP flagged) |
| `GET www.instagram.com/api/v1/users/web_profile_info/` | **429** |
| GraphQL / topsearch without a session | HTML app shell, not JSON |
| oembed | 429 |

Community clients in 2026 still treat **`web_profile_info`** as the guest
profile+first-12-posts endpoint **when the exit IP looks like a phone** and
the session is warmed (`csrftoken`/`mid` + `X-IG-App-ID: 936619743392459`).
A real device is that case. This VM is not.

Cookie session (same names Threads already pastes: `sessionid`, `csrftoken`,
`ds_user_id`, `mid`, `ig_did`) unlocks the same host reliably.

## Auth

1. **Guest (default)** — warm `www.instagram.com/`, then
   `GET i.instagram.com/api/v1/users/web_profile_info/?username=`.
   No Instagram login. Local follows only.
2. **Cookies (optional)** — paste a Cookie header, or **copy the Threads
   session** already on the device. Used when guest returns 401/429, and for
   people search / pagination.
3. **No in-app password.** No Bloks login. No Bearer `IGT:2` on this plugin
   (Threads already spends that token on For You; a second plugin would
   double the ban surface).

Separate pref keys from Threads. Same cookie *values* are fine; do **not**
share a client instance, cooldown, or device id.

## Behaviour

| Piece | Detail |
|---|---|
| Home | Following (local SQLite usernames, `AccountPostCache` merge) · Accounts |
| Open | Exact `@handle` + people search when the session answers `topsearch` |
| Profile | Public grid from `web_profile_info` / `feed/user/{pk}`; private accounts show a lock |
| Cards | Image / carousel covers; video/reel is cover + open-on-site (CDN often 403 without cookies) |
| Settings | Show tab, paste cookies, copy Threads cookies, test `@instagram`, clear session |
| Storage | `instagram_subscription` (migration 57) |
| Catalogue | Private / unavailable |
| Groups / X home feed | **Not interleaved** — photo grid ≠ tweet cards (same call as TikTok) |

## Endpoints (read-only)

| Call | Need |
|---|---|
| `GET /` (warm) | Guest cookies |
| `GET /api/v1/users/web_profile_info/?username=` | Guest or session |
| `GET /api/v1/feed/user/{pk}/` | Session; guest may 401 |
| `GET /api/v1/web/search/topsearch/?query=` | Usually session |
| `GET /api/v1/media/{id}/info/` | Session; later |

`X-IG-App-ID` is the public web id `936619743392459`. Referer/Origin
`https://www.instagram.com/`. Browser UA, not Barcelona.

## Not yet / not ever

- Compose, like/comment/follow write-back, DMs, live, story posting
- Official Graph API / Facebook Login
- Sharing `ThreadsDirectClient` or its cooldown
- Interleaving Instagram into the X home feed
- Residential-proxy sidecar

## Hard rules

- Read-oriented only
- Null-safe parsing via `Json` (`/parse-api`)
- Store pattern; ARB for UI strings
- Do not rewrite `lib/client/` / X timeline code
- Schema only via `sqflite_migration_plan` (migration 57)
