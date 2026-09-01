# Threads plugin — public site first

The plugin’s default path is the same public pages a browser shows **without
logging in**. Add local Accounts; posts load via guest GraphQL on
`www.threads.com`. Cookies, Bearer, RSSHub and Xy are optional upgrades.

Approach for the optional session path inspired by public clients such as
[threads-go](https://github.com/teslashibe/threads-go) — **no code copied**.

## What each source unlocks

| Source | Host | What it unlocks |
|---|---|---|
| **Guest (default)** | `www.threads.com` | Public account posts (`BarcelonaProfileThreadsTabQuery`) + profile card from OG tags |
| Cookies (`sessionid`, …) | `www.threads.com` | Optional richer REST profile / text_feed (falls back to guest) |
| Bearer `IGT:2:…` | `i.instagram.com` | Meta For you when local Accounts are empty |
| RSSHub | your instance | JSON Feed proxy; if empty/fails → guest |
| Xy | your server | Optional richer profile API |

## Auth UX

Settings lead with public reading + Accounts. Optional sections follow:

1. **Direct session** — Cookie header and/or Bearer `IGT:2:…` (disposable account).
2. **RSSHub** — self-hosted proxy; not required.
3. **Xy** — optional profile helper; public OG tags already cover name/bio/avatar.

No in-app password / Bloks login. No follow, repost, or compose on Meta.
Likes on cards are **local only** (device SQLite) — never sent to Threads.
The local Liked library and tab polish are specified in
`docs/specs/threads-polish.md`.

## Card UI

Meta post JSON can include `like_count`, `direct_reply_count`, `repost_count`,
and `link_preview_attachment`. Cards show reply/repost counts when present, a
large link preview when present, and a local like heart (`threads_local_like`).
The shown like total is Meta’s count plus one when liked on-device.

Tapping a post opens an in-app thread screen. Replies come from a guest scrape
of the public post URL (`parseThreadsSsrThread` over `data-sjs` `thread_items`).
Open-in-browser and article link previews still leave the app.

Pure **reposts** arrive as an empty outer shell with the original under
`text_post_app_info.share_info.reposted_post`. Parsers unwrap that, keep the
original author/content, and mark `repostedByHandle` so cards show
“{name} reposted” and SSR profile scrapes still match the profile owner.

## Feed priority

1. Local Accounts non-empty → merge those handles via:
   - **guest GraphQL** (default), even when cookies are pasted
   - cookies → `GET /api/v1/text_feed/{id}/profile/` only if
     `optionPluginThreadsUseSessionApis` is on (on failure/empty → guest)
   - else RSSHub instance → JSON Feed (on failure/empty → guest)
2. Else Bearer configured → Meta home/For You
   (`GET i.instagram.com/api/v1/feed/text_post_app_timeline/` with
   `feed_type=for_you`, `reason=cold_start_fetch`, `client_session_id`, …)
3. Else cookies/RSSHub with no Accounts → empty list (add handles in the tab)
4. Else → not configured

Cookie/Bearer `login_required` still parks *session* calls for 30 minutes.
Guest GraphQL/SSR ignores that cooldown so Accounts keep loading.

Profile lookup prefers guest HTML (OG tags + `user_id`), then cookies, then Xy.
The profile screen also loads that handle’s public posts (same guest / cookie /
RSSHub route as the feed) — a header-only screen was reading as empty.

## Throttle & risk

Followed accounts use **guest GraphQL by default**, even if cookies are
pasted. Cookie REST (`text_feed`, people search) is off until Settings →
“Use my session for followed accounts”. That toggle is the one control that
can cost a Threads login.

- Cookie web calls send a browser User-Agent (not the official Barcelona app
  UA). Barcelona UA is only used on `i.instagram.com` Bearer For you.
- Session departures keep a 3s floor + jitter, one at a time; guest stays at
  `threadsGuestMinGap`.
- A refresh asks at most `threadsMaxAccountsPerLoad` (20) handles as a guest,
  or `threadsSessionMaxAccountsPerLoad` (6) when session APIs are on.
- Stop that credential set for 30 minutes on `429`, “Please wait…”, or
  `login_required` / `logout_reason: 8`.
- Sessions may die; accounts may be checkpointed. Documented in settings copy.
- Guest GraphQL `doc_id`s can rotate; SSR remains the fallback.
- Soft refresh keeps prior posts on screen; failures do not blank the feed.
- Concurrent guest profile + posts for one handle share a single in-flight
  `/@handle` HTML GET (fewer Meta hits, not more).
- Image CDN timeouts only — no image prefetch storms.

## MVP

- Guest public posts + OG profiles without login
- Optional Cookie + Bearer paste, test, clear
- Local likes; reply/repost counts + link previews when Meta sends them
- Unit tests with `MockClient` fixtures (no live credentials)

## Not in MVP

- In-app login WebView
- Write actions to Meta (boost / remote like / follow)
- Video carousel playback beyond image URLs already on cards

## Time-to-content (guest)

Guest GraphQL has no session to ban, so departures use a shorter floor
(`threadsGuestMinGap`, ~550ms + jitter) than cookie/Bearer traffic (`minGap`,
2s). The LSD token is reused across accounts and persisted briefly so a cold
open does not re-fetch profile HTML for every handle. Soft refresh and
pull-to-refresh paint each account as it answers (stale-while-revalidate when
cached), with a pending-accounts note while more are still loading.
