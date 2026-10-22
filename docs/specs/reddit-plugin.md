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

## 2. Account-free reading reads the website, OAuth optional

Through 2026 Reddit closed every account-free door the plugin used to read:

| Route | 2026 state |
|---|---|
| Public `.json` (www and old) | 403 for logged-out readers since late May |
| old.reddit.com HTML | Login page for logged-out readers (`/login/?reason=lor2`) since July |
| RSS (`.rss`) | One request per calendar minute per address since June; retired on 2026-11-13 |
| www.reddit.com full pages | Open with a script that checks for a browser (`name="solution"` form) |
| www.reddit.com pieces (`/svc/shreddit/…`) | Served, no check: feeds, comments, search, profiles |
| OAuth API | No new access requests after 2026-10-31; unregistered apps cut off from 2027-01-12, all by March 2027 |

So the default path reads www.reddit.com's pieces — the parts of its pages the
site loads as it scrolls (`reddit_www*.dart`):

| What | Piece |
|---|---|
| A community's feed | `community-more-posts/<sort>/?name=&t=&after=` |
| A thread, a comment's own page | `comments/r/<community>/t3_<post>[/t1_<comment>]?sort=` |
| Search | `search/?q=&type=posts\|communities\|people`, `r/<community>/search/?…&restrict_sr=1` |
| A profile | `profiles/profile_posts-more-posts/new/?name=&after=` |
| A community's picture and description | its own entry in the community search |
| A big thread's next comments | `more-comments/<community>/t3_<post>?top-level=1&…`, by POST with the page's `cursor` and the site's `csrf_token` cookie |
| A post on its own (one from search or a link) | `embed.reddit.com/<r\|user>/<name>/comments/<post>/?embed=true`, the card other sites embed |

Cursors are the fullname (`t3_…`) in base64, which is how the pieces write
them; the client keeps the plain fullname so any route can read the next page.
Each piece is a whole document of about a megabyte (gzipped on the wire), so it
is cut to its body and parsed off the UI thread. A big thread renders its
comments after the fourth inside a `<template>` the site fills in as the reader
scrolls, with the "more comments" partial at its end; templates are cut whole,
except those holding comments, posts or partials, whose content stays.

A thread's first page holds ten top-level comments. The rest are fetched the
way the site fetches them, by POST. That POST could not be checked from here
(no proxy that reaches Reddit passes one), so when Reddit refuses it the row
opens the post on Reddit, as it did before.

A post that arrives with nothing but a title — a search result, a link from
another post — is filled in from its embed card: its text in full, pictures,
video, link, score and comment count. An NSFW post's card is refused (400), and
the post stays as it arrived.

old.reddit HTML and the `.json` endpoints stay behind it as later routes — no
shutdown has been total — and old.reddit's login page refuses only its own
host instead of ending the search.

Not available on this route: pinned posts (the feed piece leaves them out),
subscriber counts (the search shows weekly visitors), and video downloads (the
feed carries a signed HLS stream and no single file with sound). NSFW posts
carry an `nsfw` attribute, posts in NSFW communities `subreddit-nsfw`, and
blurred media a `<shreddit-blurred-container reason="nsfw|spoiler">`; all
three count.

Reddit blocks datacenter addresses (GitHub's runners among them) with "blocked
by network security" on every route. `test/live/reddit_smoke_test.dart` with
`--dart-define=REDDIT_VIA=translate` asks www.reddit.com through Google's
translation proxy, which Reddit serves, to check the client against today's
markup; the fixtures in `test/fixtures/Reddit/` came the same way.

Optional credentials, in priority order when source is `auto`:

1. **User OAuth** (`read,identity` only) — reader's own rate limits
2. **App-only** `installed_client` — when a client id is set
3. **The website** — needs nothing

`source=public` forces the account-free route even when credentials exist.
No write scopes; local follow + local upvote stay on-device.

## What is shipped

| Area | Status |
|---|---|
| Followed feed (merged first pages, newest first) | Yes — parallel fetch, per-subreddit errors isolated |
| Subreddit / user listing screens | Yes |
| Comments / thread screen | Yes (www comments piece; held-back replies open their comment's page) |
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
www.reddit.com's comments piece when anonymous). Search and user posts read
the website. Community icons read `about.json` (`community_icon` / `icon_img`)
over OAuth, else the community search, then the public routes. Signed
`?width=&s=` query strings are stripped so the CDN serves the original file.

## Known gaps (not P0)

- Comment markdown beyond paragraphs, lists, line breaks and code (quotes,
  tables and headings read as plain paragraphs)
- Search / user posts read the website even when signed in (OAuth wiring follow-up)
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
