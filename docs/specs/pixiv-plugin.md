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

No compose or like-on-X. Following a user (`POST /v1/user/follow/add` /
`delete`) and bookmarking an illust (`POST /v2/illust/bookmark/add` /
`POST /v1/illust/bookmark/delete`) write back to Pixiv so the Bookmarks tab
stays in sync. There is no compose.

The token response's `user.is_premium` is stored as `plugin.pixiv.is_premium`
(`PixivClient.isPremium`) and cleared on sign-out and plugin reset, so
Premium-only search options can be offered only to Premium readers.

## Transport

`PixivClient` (`pixiv_client.dart`) owns every request to Pixiv:

- Each request carries the app identity headers, `X-Client-Time` /
  `X-Client-Hash`, and `Accept-Language` from the active XTA locale (`ja`,
  `ko`, `zh-CN`, `zh-TW`, everything else `en`), so tag translations and other
  localised text arrive in the reader's language.
- The access token refreshes 60 s before expiry. A request Pixiv refuses — HTTP
  401, or HTTP 400 whose `error.message` mentions OAuth, which is how Pixiv
  usually reports an expired or revoked token — refreshes once (concurrent
  requests share one refresh) and is replayed once.
- A connection closed before any header arrived is sent again once.
- The bearer token is only ever sent to `app-api.pixiv.net`.

Feature code adds its endpoints in its own `pixiv_<feature>_api.dart` over the
public transport rather than growing the client: `getJson(path, query:,
auth:)`, `getNextJson(nextUrl)`, `getText(path)` for HTML bodies,
`postForm(path, body)` and `illustPageFrom(json)` (which applies Show R-18 and
Hide AI, or keeps everything for the reader's own lists). Screens read such a
class through `X.of(context)`, which prefers a `Provider` override (tests pass
fakes through `pumpPixiv(extraProviders: …)`) and otherwise builds from
`PixivClient`, as `PixivDownloader.of` does.

## Features

| Feature | Detail |
|---|---|
| Settings | One page of section widgets (`pixivSettingsSections`): account (WebView PKCE sign-in, sign out, refresh-token paste, test), content (Show R-18, Hide AI), bookmarking (default visibility, auto-tag, follow / save after bookmarking, bookmark after saving, haptics), mute review (authors, tags, works, comments, novels) |
| Home | Shell (`pixiv_screen.dart`) over Home (Following / Recommended), Rankings (modes and archive date), Favorites (public / private bookmarks), Search and More (`pixiv_more_pane.dart`) |
| Gallery | Staggered grid; each tile (`pixiv_illust_tile.dart`) has a badge row (pages, ugoira, R-18, AI) and a caption with the bookmark count; `PixivIllustGrid` takes leading slivers |
| Detail | Shell (`pixiv_illust_screen.dart`) over the page viewer, page bar, meta (author, stats, caption, tags), the author's other works, related works and the AppBar actions; overflow entries are a list in `pixiv_detail_menu.dart` |
| Reader | Horizontal / vertical page reader, page overview, page actions, ugoira playback, downloads |
| Search | Illusts and users, trending tags, popular preview, tag autocomplete, recent queries, open by link or ID. Recent queries are one app-wide `PixivSearchHistory` (a `PluginSearchHistoryStore` under `plugin.pixiv.search_history`), so a search made on a pushed screen shows on the one underneath |
| Profile | User detail with works (illustrations + manga), following and My pixiv counts as plural phrases, public follow, works grid |
| User cards | `PixivUserPreviewCard` and `PixivFollowButton` (`pixiv_user_card.dart`) for user lists: avatar, name, three works the reader's filters allow (each one its own screen-reader button), 48 dp follow toggle that moves under the name when the screen is narrow or the text large. `onFollowChanged` lets a list update its copy |
| Follow state | One app-wide `PixivFollowStore` (`pixiv_user_store.dart`): the follows changed this session and the ones in flight, read by every follow button, so a follow survives list rebuilds and recycled rows and shows the same on cards and profiles. Signing out or uninstalling clears it |
| Bookmarks | One write path (`PixivBookmarkActions`) for the heart, the bookmark editor (long press on a heart, or Edit bookmark in the detail menu) and bookmark-after-save, with a Bookmarking settings section; Favorites filters by bookmark tag. See `pixiv-bookmarks.md` |
| Avatars | `PixivAvatar` (`pixiv_avatar.dart`): the round avatar, decoded at its painted size, or initials when Pixiv sends none |
| Local mute | Author ids, tag names, work ids, comment ids and novel ids in prefs; works are filtered from every grid |
| Group Discover | Related creators (`/v1/user/related`), cached unfiltered for 10 minutes; each is shown through a preview work that passes mute, Show R-18 and Hide AI as they are at read time |

Every way into a work goes through `pixiv_link_open.dart`: `openPixivLinkRef`
for links (used by `plugin_links.dart`), `openPixivIllust` and
`openPixivIllustFromList` (what tiles call), all building the route in
`pixivIllustRoute`.

Paged lists use `PixivPagedListStore<T>` (`pixiv_store.dart`): first load,
soft refresh, `next_url` paging with de-duplication, a filter, and skipping
pages the filter empties. Every refresh and `useLoader` starts a new
generation, and a page that lands for an older one is dropped, so a slow page
from the old ranking mode never joins the new list. `PixivIllustListStore` is
its illust case.

## Endpoints

| Call | Path |
|---|---|
| Following | `GET /v2/illust/follow` |
| Recommended | `GET /v1/illust/recommended` |
| Ranking | `GET /v1/illust/ranking?mode=&date=` |
| Bookmarks | `GET /v1/user/bookmarks/illust` |
| Bookmark tags | `GET /v1/user/bookmark-tags/illust` |
| Trending tags | `GET /v1/trending-tags/illust` |
| Popular preview | `GET /v1/search/popular-preview/illust` |
| Autocomplete | `GET /v2/search/autocomplete` |
| Search illust | `GET /v1/search/illust` |
| Search user | `GET /v1/search/user` |
| Recommended users | `GET /v1/user/recommended` |
| Related users | `GET /v1/user/related` |
| Illust detail | `GET /v1/illust/detail` |
| Ugoira metadata | `GET /v1/ugoira/metadata` |
| Related | `GET /v2/illust/related` |
| User detail | `GET /v1/user/detail` |
| User illusts | `GET /v1/user/illusts` |
| Following users | `GET /v1/user/following` |
| Follow add | `POST /v1/user/follow/add` |
| Follow delete | `POST /v1/user/follow/delete` |
| Bookmark add | `POST /v2/illust/bookmark/add` |
| Bookmark delete | `POST /v1/illust/bookmark/delete` |
| Bookmark detail | `GET /v2/illust/bookmark/detail` |

## Not yet

What remains against PixEz — novels, comments, Pixivision, series and
watchlists, richer search and bookmarks, viewing history, multiple accounts and
more — is planned batch by batch in `pixiv-pixez-gaps.md`.
