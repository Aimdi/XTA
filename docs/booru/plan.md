# Booru plugin: ranked plan

Status: **approved 2026-10-12; tiers 1 and 2 done except the live checks and phone measurements named below; tier 3 next.** Ranks marked **Done**
name the commit that fixed them.
Evidence for every item is in `docs/booru/audit.md` (IDs B*, P*, §4 rows);
baseline scenarios are in `docs/perf-baseline.md`.

## Decisions (2026-10-12)

Read-only towards every booru; no Boorusama code copied; credentials stay in
the app's private settings for now; the video target follows the shared
pool's rule. Details in the audit's "Decisions" section.

## How items are ranked

Tiers follow the agreed priority order. A tier starts only when the one above
it has no open items, unless you approve otherwise. Within a tier, items are
ranked by impact first, then lower effort, then lower risk.

- **Impact:** H = credentials, data loss or a feature that stops working; M =
  wrong results or wasted requests a reader will notice; L = cosmetic or rare.
- **Effort:** S = one file and its test; M = a few files or a new Store; L =
  new screens, migrations or native code.
- **Risk:** L = behaviour change confined to the bug; M = touches shared code,
  preferences or site behaviour not verified live; H = schema or dependency
  change.

Every item is one hypothesis and one commit: a characterization or regression
test first, then the smallest change, then `fvm flutter analyze` and the
targeted tests, then the full suite. Performance items also re-run their
baseline scenario on the same phone and are kept only if their metric improves
with no regression elsewhere.

## Tier 1: bugs in existing features

IDs are the audit's. Items marked "live check" depend on how a site answers,
which could not be observed from this environment. They were meant to start
by recording real responses as test fixtures. The hosts stayed unreachable,
so on your go-ahead they were fixed against answers built from the shapes the
audit read in Boorusama instead, each change kept to what holds whatever the
host sends, and each is unverified until checked live.

| Rank | Item | Fixes | Impact | Effort | Risk | Test first |
|---|---|---|---|---|---|---|
| 1 | **Done** (`dba4687f`). Strip credentials from Booru error text, including network failures that wrap the URL | B1 | H | S | L | A 401, and a socket failure, with a key set: the exception text has no key |
| 2 | **Done** (`ce934ebc`). Send credentials only to the host they were entered for. Save the host with the login and key; other hosts get none, and settings say which site the key belongs to | B2 | H | S–M | M (preferences) | Switch preset: the request carries no key. Switch back: it does |
| 3 | **Done** (`a9ef31c1`, `63fb05ba`). Reset Home's feeds when the site, rating cap or blacklist changes, and look up a post's details on its own host and engine | B3 | H | M | L | Change host with a feed loaded: the next page starts from page 1 of the new host; an old post's lookups go to its own host |
| 4 | **Done** (`7825d39a`). Attach credentials only when both login and key are set | B4 | M | S | L | Login without key: no credential parameters are sent |
| 5 | **Done** (`afd18acb`). Back off after a failed load-more and show it: the failure and `loadingMore` become Store state, with a retry footer and an end-of-feed marker; no automatic retry until the reader taps Retry or refreshes | B5 | M | S | L | A failing loader and repeated near-end notifications make one request; the footer offers retry |
| 6 | **Done** (`e5315b74`). Show Following's failures: an error with Retry when every followed query failed, and a notice when some did | B6 | M | S | L | All queries fail: the error view shows. One fails: the rest show with a notice |
| 7 | **Done** (`56ddcba9`). Show the skeleton, not the empty state, until a feed's first load has finished | B7 | M | S | L | A new feed renders the skeleton before its first result |
| 8 | **Done** (`cc31597e`). Drop a load-more that finishes after a refresh: a generation counter in `BooruFeedStore`, also guarding an older refresh's page counter, paging state and Following's failure count | B8 | M | S | L | Refresh while a load-more is pending: its posts never appear. Two overlapping refreshes: only the newer one's state remains |
| 9 | **Done** (`6ec2f4bd`). Name Booru downloads from the decoded file name, keeping the start of long names | B9 | M | S | M (shared helper) | A Moebooru URL gives a readable `booru-…` name under 240 characters |
| 10 | **Done** (`18989036`). Drop posts with no usable file; every listed post now has a file, so no download comes up empty | B10 | M | S | M (live check) | Fixtures of a banned Danbooru post and a deleted e621 post |
| 11 | **Done** (`94fb4cdb`, `6803168b`). Let a feed emptied by the rating cap keep paging, and send the cap to Gelbooru-family hosts in their own rating syntax | B16 | M | S | M (live check) | Four filtered-empty pages, then a full one: the reader reaches it |
| 12 | **Done** (`2926ce05`). Read errors sent as HTTP 200: Rule34's missing authentication becomes "needs credentials"; Gelbooru's "Too deep" and a 410 end the feed | B11 | M | S | M (live check) | Recorded Rule34 and Gelbooru bodies |
| 13 | **Done** (`bd92c6c7`) for the 422 message. Open: a warning when a search past the limit is saved, which needs Danbooru's free-metatag list | B13 | M | S | M (live check) | Recorded 422 body |
| 14 | **Done** (`b503f6a4`). GIFs play from the original file, on every engine | B14 | M | S | M (live check) | A recorded e621 GIF post: its display URL is the `.gif` |
| 15 | **Done** (`033da183`). Decode HTML entities in Gelbooru tags | B15 | M | S | M (live check) | A recorded Gelbooru post: `girls&#039;_frontline` parses to `girls'_frontline` |
| 16 | **Done** (`864e395f`). Gelbooru 0.2 forks: parse the tag endpoint's XML reply | B12 | M | M | M (live check) | Recorded Safebooru tag replies |
| 17 | **Done** (`d259a6a4`). Stop unfollowing a booru tag from removing other sources' group rows: prefix Booru members and migrate existing rows (migration 62, approved) | B17 | M | M | H (migration in frozen `lib/database/`) | Needs your approval for the migration |
| 18 | **Done** (`20d4f4a8`). Honour "load media only on tap" for Booru videos through the existing gate | B18 | L | S | L | With the setting on, no player is built until a tap |
| 19 | **Done** (`add22b17`). Distinct messages for a 403 without credentials and for 503 | B20 | L | S | L | Each status maps to its own message |
| 20 | **Done** (`5a3b9819`). Guess `hypnohub.net` as Gelbooru 0.2 | B19 | L | S | L | Engine guess test |
| 21 | **Done** (`0d10bd5d`). `mounted` checks after awaits in Booru settings | B26 | L | S | L | Leave the screen mid-write: no exception |
| 22 | **Done** (`ec2cf187`). Label the Gelbooru 0.2 date as "updated", or leave it out | B24 | L | S | L | Facts test |
| 23 | **Done** (`006f364f`). Download ugoira as its webm, or offer both | B25 | L | S | L | Download source test |
| 24 | **Partly done** (`0f88f167`): the exact title is picked from up to 100 search results; an exact lookup is a live check | B21 | L | S | M (live check) | Recorded wiki reply |
| 25 | **Done** (`972d8d6e`). Popular's default date: the host's day, not the device's | B22 | L | S | M | Date test at UTC+9 |
| 26 | **Done** (`14851203`). Ignore results after a store is destroyed, and stop its remaining pages | B27 | L | M | L | Destroy mid-load: no late update, no further request |
| 27 | **Partly done** (`e39aa081`): a numeric `directory`, and a video's missing preview. Open, live check: the `api-cdn` rewrite and a `Referer` | B23 | L | M | M (live check) | Recorded Rule34 posts |

## Tier 2: performance of existing screens

Started on your go-ahead before the baseline could run on a phone. Each item
names the scenario it must improve; every metric stays UNVERIFIED until that
scenario runs, and an item that does not improve its scenario then is
reverted. The tests check what can be counted without a device: requests
made, the picture chosen, rebuilds and when the first request goes out.

| Rank | Item | From | Scenario | Impact | Effort | Risk |
|---|---|---|---|---|---|---|
| 1 | **Done** (`f8d57c09`). Throttle requests per site and back off exponentially on 429 and 5xx; cap the Following fan-out | §3 X3, X4 | Request counts; no 429 | H | M | L |
| 2 | **Done** (`5a9c7b02`) for Danbooru, the one engine that lists variants. Pick the tile image by size: the smallest variant at least as wide as the tile × device pixel ratio (Danbooru's 360/720 px variants; sample or preview elsewhere). Originals only on request | P1 | S7, S2 | H | M | M |
| 3 | **Done** (`1536ac45`). Start Latest's first request without waiting for the tag and mute stores, and without `execute`'s 50 ms delays on a cold open | P3 | S1 | M | S | L |
| 4 | **Done** (`5ffeeb8e`); related posts are asked for once per viewer as well. Viewer: rebuild only the app bar on a page change; cache each post's tag lookup by post key | P7 | S3 | M | M | L |
| 5 | **Precaching done** (`a2993c50`). Open: cancelling tile downloads, since the image library shares one load per picture between widgets. Cancel tile downloads that scroll away; precache the next and previous post at sample size, cancelled on fast swipes, never for video | P5 | S7, S3 | M | M | M |
| 6 | **Waiting for S6 on a phone**: it trades zoom detail in a viewer X shares for memory. Cap the full-screen viewer's decode size (shared `TweetPhoto`: X's viewer too) | P6 | S6 | M | M | M |
| 7 | **Done** (`d732c7cc`) as a 30-day age bound with the size and a clear button in Settings > Data; the task's size budget is open. Bound the image disk cache, with its size and a clear button in settings. The folder is app-wide, shared with X media, so this is an app-wide policy | P8 | Storage, S7 | M | M | M |
| 8 | **Dropped**: on the browsing path only a search writes preferences, so the rebuilds it would save are rare. Narrow the grid's preference dependency to the five display preferences | P4 | S2 rebuild counts | L–M | S | L |
| 9 | **Not started**: it waits for an S2/S3 trace that shows parse cost. Decode responses off the UI isolate with `compute`, as `lib/group/feed_cache.dart:41` already does, if the S2/S3 traces show parse cost | P2 | S2, S3 | Unknown until traced | S | L |

## Tier 3: finishing partial features (§4)

Ranked: per-site profiles with their own credentials, rating cap, blacklist
and follows (1a, 1b, 3b; credentials stay in app settings for now); semantic labels
on thumbnails (14a, also a quality gate); blacklist metatags such as `score:`
(3c); an explicit opt-in step for the Explicit cap (4); metatag help and
autocomplete per engine (2b); artist and character pages (5e); keeping the
last page through a failed refresh and across restarts (10c); one-tap tag
search and long-press tag actions (5b, 5c); host-provided related tags (5g);
a download progress notification (8e, needs native work); "copy tags as
prompt" options (12).

## Tier 4: missing must-haves (§4)

Ranked: bulk download with filename templates and skip-if-exists by MD5 (8b,
8c, 8d); hidden-post count with tap-to-reveal (3d, 3e); notes and translation
overlay (5i); pools reader (5f); history of viewed posts (9a, needs a
migration). Favourites and votes synced with the site (7a) are out of scope:
the plugin stays read-only, and local favourites already exist.

## Tier 5: differentiators

Reverse image search from X media (SauceNAO needs an API key; IQDB's terms
need checking; this environment's network policy also refuses both hosts), booru links
in X posts opened natively, and "new since last visit" on followed tags.

## Dependencies

Tiers 1 and 2 need no new dependency. A background download notification
(tier 3) would need one, and will come with a written justification for your
approval first. Secure credential storage is deferred until per-site profiles.

## Proposed next

Tier 1 is done apart from the live checks named in its table, and tier 2
apart from rank 5's tile cancelling and rank 6, which need a phone, and rank
9, which needs a trace. Every tier 2 metric is UNVERIFIED until S1–S7 run on
a phone.

Tier 3 is next, starting with the items that need no live host, no new
dependency and no change to the read-only rule: semantic labels on
thumbnails (14a), "copy tags as prompt" options (12), one-tap tag search
with long-press actions (5b, 5c), and keeping the last page through a failed
refresh and across restarts (10c).
