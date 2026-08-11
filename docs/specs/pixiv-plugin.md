# Pixiv plugin (private)

Read-only Pixiv gallery inspired by the *approach* of
[pixez-flutter](https://github.com/Notsfsssf/pixez-flutter) (GPL-3.0) —
**none of their code is copied or translated**. Auth and `app-api.pixiv.net`
calls are written fresh in Dart against the well-known unofficial API shape.

## Private

- Listed in `plugins.json` as `{ "id": "pixiv", "available": false }` so the
  public catalogue does not offer it.
- `XtaPlugin.isPrivate == true`; the store only shows it when “Show private
  plugins” is on, or once already installed.

## Auth

Pixiv no longer accepts password login on the app API. XTA supports the same
**browser OAuth (PKCE)** flow community clients like Pixez use:

1. Generate `code_verifier` (random URL-safe string) and `code_challenge` =
   base64url(SHA-256(verifier)) without padding.
2. Open `https://app-api.pixiv.net/web/v1/login?code_challenge=…&code_challenge_method=S256&client=pixiv-android`
   in a WebView — the reader enters username, password, and 2FA on Pixiv’s form.
3. Intercept redirect to
   `https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=…` or
   `pixiv://account?code=…`.
4. POST `https://oauth.secure.pixiv.net/auth/token` with
   `grant_type=authorization_code`, the code, verifier, redirect URI, and the
   public Android app client id/secret.
5. Persist `refresh_token` (and short-lived `access_token` + `user_id`) in
   preferences.

**Advanced fallback:** paste a refresh token manually in settings (same storage).

Constants and implementation: `lib/plugins/pixiv/pixiv_auth.dart`,
`lib/plugins/pixiv/pixiv_login_webview.dart`.

No compose, bookmark, or like write-backs to Pixiv. Following a user from
their profile is supported (`POST /v1/user/follow/add` / `delete`).

## Features

| Feature | Detail |
|---|---|
| Settings | Sign in with Pixiv (WebView PKCE), sign out, advanced refresh-token paste, show R-18 toggle (off by default), test connection, local muted author/tag/work review |
| Home tabs | Following (`/v2/illust/follow`), Ranking (`/v1/illust/ranking`), public/private Bookmarks (`/v1/user/bookmarks/illust`) |
| Gallery | Pixez-style staggered grid (`flutter_staggered_grid_view`) with page-count / R-18 / ugoira chips |
| Viewer | In-app illust screen — multi-page manga, caption, tags, bookmark/view counts, related works, local mute actions |
| Search | Illusts (`/v1/search/illust`) and users (`/v1/search/user`); recent queries; Pixiv artwork/user link open; target and sort controls; tag chips open search |
| Profile | User detail + staggered grid of their illusts; Follow / Unfollow |
| Local mute | Preference-backed author ids, tag names, and work ids filter following, ranking, bookmarks, search, related, and profile grids |

## Endpoints

| Call | Path |
|---|---|
| Following | `GET /v2/illust/follow` |
| Ranking | `GET /v1/illust/ranking?mode=` |
| Bookmarks | `GET /v1/user/bookmarks/illust` |
| Search illust | `GET /v1/search/illust` |
| Search user | `GET /v1/search/user` |
| Illust detail | `GET /v1/illust/detail` |
| Related | `GET /v2/illust/related` |
| User detail | `GET /v1/user/detail` |
| User illusts | `GET /v1/user/illusts` |
| Follow add | `POST /v1/user/follow/add` |
| Follow delete | `POST /v1/user/follow/delete` |

## Not yet

- Ugoira frame playback
- Novel API
- Local download manager
- Bookmark / like **write** APIs on Pixiv
- Comments
- Proxy / mirror modes
