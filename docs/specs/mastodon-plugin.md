# Mastodon (Fediverse) plugin

Read-only browsing of public Fediverse accounts through any Mastodon-compatible
instance’s public REST API. No Mastodon login, no posting, boosting, favouriting,
or follow-on-instance.

## Approach

Mastodon (and many forks) expose public account and status endpoints without a
token. Account IDs are **per-instance**, so the reader picks a **home instance**
URL (settings). Lookups and status fetches go through that instance; remote
`user@other.instance` addresses are resolved via its federation
(`GET /api/v1/accounts/lookup`).

Follows stay on the device in SQLite (`mastodon_subscription`). Home is four
public tabs, the way Tusky / Ivory / Phanpy / Misskey web do it: **Explore**
(trending tags + statuses), **Local**, **Federated**, then **Following**
(merged newest-first timeline of every locally followed acct). Misskey-family
origins that 404 the Mastodon public API fall back to featured / local notes.

## Endpoints used

| Call | Path |
|---|---|
| Instance check | `GET /api/v2/instance` (fallback `/api/v1/instance`) |
| Account lookup | `GET /api/v1/accounts/lookup?acct=` |
| Account statuses | `GET /api/v1/accounts/:id/statuses` |
| Account by id | `GET /api/v1/accounts/:id` |
| Status | `GET /api/v1/statuses/:id` |
| Status context | `GET /api/v1/statuses/:id/context` |
| Public local / federated | `GET /api/v1/timelines/public` (`local=true` for Local) |
| Trending statuses | `GET /api/v1/trends/statuses` |
| Trending tags | `GET /api/v1/trends/tags` |
| Hashtag timeline | `GET /api/v1/timelines/tag/:name` |
| Guest search | `GET /api/v2/search` (accounts + statuses + hashtags) |
| Pinned statuses | `GET /api/v1/accounts/:id/statuses?pinned=true` |
| Misskey fallback | `POST /api/notes/local-timeline`, `POST /api/notes/featured` |
| Resolve status URL (optional) | `GET /api/v2/search?q=&resolve=true&type=statuses` — often 401 without login; not required |

No OAuth app registration. No write methods.

## Thread / replies without search

Opening a post walks [mastodonInstanceCandidates] (origin → reader’s instances →
built-in defaults). On each candidate the client locates the status without
depending on authenticated search:

1. `GET /statuses/:id` using the snowflake in the public URL (and the card’s id)
2. Soft search resolve when the instance still allows it
3. `accounts/lookup` + recent `accounts/:id/statuses`, match `url`, then `context`

So an origin that blocks public search (or a card whose id is from another
host) can still show replies via that origin’s status API, or via any open
instance that has already federated the author.

## Card UI

Public statuses render with a tweet-sized layout: avatar, body text with
tappable `@mentions` and `#tags`, media, a quoted status when present,
PreviewCard link/article preview (`card`), a read-only poll, content-warning
reveal (Tusky/Ivory CW), a “boosted by” / “replying to” line, an edited mark,
and a read-only engagement row. Tapping a post opens an in-app thread.
Search is People / Posts / Hashtags. Profiles show `fields`, bot / locked
labels, pinned posts, and Posts / Media tabs. Lists page with `max_id`.

## Not implemented

- Home / notifications timelines (need a user token)
- Compose, boost, favourite, vote, follow/unfollow on the instance
- Video playback beyond a still card
- A separate Misskey plugin — Misskey notes are mapped onto the same cards
- Picking a different AppView / ActivityPub client library
