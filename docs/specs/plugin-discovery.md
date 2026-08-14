# Plugin discovery (Bluesky, Mastodon, Threads, Pixiv)

Cold-start and browse for social plugins that previously needed a handle you
already knew. Follows stay **local** where the plugin model is local-first;
Pixiv follows the account on Pixiv (already supported). Pattern: Substack’s
Discover sheet and Flare’s Pixiv Discover hub.

## Bluesky

- `app.bsky.actor.getSuggestions` on the public AppView.
- Search sheet People tab shows **Suggested** when the query is empty, with a
  local Follow button on each row (search results and graph lists too).
- Empty Home offers Discover, plus Import following / Import a starter pack.
- Recent searches are remembered on-device.
- Home feed surfaces unfollowed original authors (reposts first, then quotes)
  in a “People in this feed” strip — reposts are the main “who should I
  follow?” signal.
- Post cards show Follow on an author you do not already follow. The
  “X reposted” banner opens the reposter.
- Import a public starter pack (`bsky.app/starter-pack/{handle}/{rkey}` or
  `at://…/app.bsky.graph.starterpack/…`) → `getStarterPack` → list URI →
  existing `getList` pagination. Short `go.bsky.app` links are not followed.

## Mastodon

- Home-instance reads (configured instances / defaults):
  - `GET /api/v1/trends/tags`
  - `GET /api/v2/search?type=accounts` (no `resolve` — guest-safe)
  - `GET /api/v1/timelines/tag/:name`
- Discover sheet: trending tags + account search; tag opens a hashtag timeline.
- AppBar search opens the sheet (exact lookup remains via add / direct acct).

## Threads

- Cookie session: expose Meta `GET /api/v1/users/search/` as multi-result
  search. Cookies alone are enough — `useSessionApis` stays a feed-session
  gate and is not required for people search.
- Guest: submit still opens an exact `@handle` profile (no public search API).
  The empty sheet also offers an Open @handle row, recent searches, and a few
  public starter handles (`zuck`, `mosseri`, `meta`).
- Discover / search sheet replaces the lookup-only dialog from the AppBar search
  icon; empty Home CTA opens it. Result rows have a local Follow button.
- Home feed surfaces unfollowed original authors from the current posts
  (reposts first) so a boost is a follow suggestion. Post cards show Follow
  on an author you do not already follow. The “X reposted” line already opens
  the reposter.

## Pixiv (oriented on Flare)

Reference: [DimensionDev/Flare](https://github.com/DimensionDev/Flare) Pixiv
module (`recommendedIllusts`, `recommendedUsers`, ranking modes, Discover hub).
Approach only — no Flare code copied.

- `GET /v1/illust/recommended` → home **Recommended** tab (Flare Discover status).
- `GET /v1/user/recommended` → search landing **recommended users** strip
  (Flare Discover users), above existing trending-tag grid.
- Ranking chips gain Flare modes `week_original` and `day_manga`.

Keep XTA advantages Flare lacks: ranking date archive, autocomplete,
popular-preview strip, trending-with-illust grid.

## Out of scope

- Compose / like-on-network / follow-on-network write-backs.
- Bluesky custom feed generators.
- Mastodon directory / public timeline tabs (later).
- Threads For You without Bearer (already gated).
- Pixiv comments / manga recommended / bookmark writes.
