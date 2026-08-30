# Reddit plugin

Answer to *"Stealth looks awesome — can't you make it a plugin of XTA?"*
Two findings decided the approach; both still hold.

## 1. It cannot be a port. It has to be a reimplementation

| | Stealth | XTA |
|---|---|---|
| Language | Kotlin | Dart |
| UI | Android views | Flutter |
| Storage | Room | sqflite / prefs |
| Licence | **GPLv3** | **MIT** |

Nothing about a Kotlin Android app can be "plugged into" a Flutter app. Copying
or translating Stealth's source would make the combined work GPLv3. This plugin
is therefore written against Reddit's own API / HTML. Stealth was consulted only
to learn *which* routes a modern account-free client uses — not for its
implementation.

## 2. Account-free reading is scrape-first, OAuth optional

Unauthenticated `.json` is often refused (datacenter IPs especially). The
shipped default path scrapes `old.reddit.com` HTML (browser UA + cookies +
over-18 cookie), with public JSON as a secondary try.

Optional credentials, in priority order when source is `auto`:

1. **User OAuth** (`read,identity` only) — reader's own rate limits
2. **App-only** `installed_client` — when a client id is set
3. **Public scrape / JSON** — needs nothing

`source=public` forces the account-free route even when credentials exist.
No write scopes; local follow + local upvote stay on-device.

## What is shipped

| Area | Status |
|---|---|
| Followed feed (merged first pages, newest first) | Yes — parallel fetch, per-subreddit errors isolated |
| Subreddit / user listing screens | Yes |
| Comments / thread screen | Yes (HTML scrape; `more` stubs still skipped) |
| Search (posts / subreddits / users) | Yes |
| Discovery feeds | Yes — Following / Popular / All |
| Sort UI (hot / new / top / rising / controversial) | Yes, including `t=` windows for top / controversial |
| Media galleries, flairs, NSFW / spoiler gates | Yes (JSON path richer than scrape) |
| NSFW display preference | Yes — hide / tap-to-show / always show |
| Local-only upvote | Yes |
| Local-only saved posts | Yes — pref snapshots capped on-device |
| Optional sign-in + client id | Yes |
| Home / group interleave | Yes (opt-in; uses shared read session) |
| Plugin store / tab / settings | Yes |

Code lives under `lib/plugins/reddit/`. Off by default (`plugins.json`).

## Shared read session (P0)

`RedditReadSession.resolve` is the single place that turns prefs into
`clientId` / `preferPublic` / `userToken`. Listing paths that used to omit the
user token (home/group interleave, subreddit listing) now go through it, so
signing in helps every subreddit listing — not only the Reddit tab.

Comment threads use `RedditReadSession` (OAuth JSON when signed in,
old.reddit scrape when anonymous). Search and user posts still scrape
publicly. Community icons read `about.json` (`community_icon` / `icon_img`)
from oauth, then www, then old.reddit, and fall back to HTML. Signed
`?width=&s=` query strings are stripped so the CDN serves the original file.

## Known gaps (not P0)

- Comment `more` expansion / collapse / markdown
- Search / user posts still scrape publicly (OAuth wiring follow-up)
- Scrape/OAuth media parity; gallery pager (same-file preview/i.redd.it width
  variants are collapsed — no more low+high double display)
- User profile beyond submitted posts
- Followed-tab (`RedditFeedStore`) pagination — listing screens paginate via
  `after`; the merged followed feed still loads first pages only

## Done (recent P0)

- For You pull-to-refresh reloads the Reddit mix (`onRefresh: _loadRedditPosts`)
- Subreddit / user listing pagination via trailing Load more (`after` cursor)

## Non-goals

- Posting, reply, Reddit-side vote/subscribe/save, DMs, mod tools
- Expanding OAuth beyond `read` (+ `identity` for display)
- Porting GPL Stealth / Infinity / RedReader / Slide code
- Making Reddit the default home experience
