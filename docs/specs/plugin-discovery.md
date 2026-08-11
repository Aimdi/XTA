# Plugin discovery (Bluesky, Mastodon, Threads, Pixiv)

Cold-start and browse for social plugins that previously needed a handle you
already knew. Follows stay **local** where the plugin model is local-first;
Pixiv follows the account on Pixiv (already supported). Pattern: Substack’s
Discover sheet and Flare’s Pixiv Discover hub.

## Bluesky

- `app.bsky.actor.getSuggestions` on the public AppView.
- Search sheet People tab shows **Suggested** when the query is empty.
- Empty Home offers a Discover CTA that opens that sheet.

## Mastodon

- Home-instance reads (configured instances / defaults):
  - `GET /api/v1/trends/tags`
  - `GET /api/v2/search?type=accounts` (no `resolve` — guest-safe)
  - `GET /api/v1/timelines/tag/:name`
- Discover sheet: trending tags + account search; tag opens a hashtag timeline.
- AppBar search opens the sheet (exact lookup remains via add / direct acct).

## Threads

- Cookie session: expose Meta `GET /api/v1/users/search/` as multi-result search.
- Guest: submit still opens an exact `@handle` profile (no public search API).
- Discover / search sheet replaces the lookup-only dialog from the AppBar search
  icon; empty Home CTA opens it.

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

- Compose / like-on-network write-backs.
- Bluesky custom feed generators / starter packs (later).
- Mastodon directory / public timeline tabs (later).
- Threads For You without Bearer (already gated).
- Pixiv comments / manga recommended / bookmark writes.
