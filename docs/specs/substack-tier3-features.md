# Substack Tier 3: finishing partial features

Status: **done** (2026-10-17) and **shipped in aimdi179** (#371), one commit per item, each with its tests. The
owner asked for the remaining tiers; Tier 2's open items each wait on a device or a HAR (`docs/substack/audit.md`
§9). Two choices made on the way: mark-read-on-scroll does not re-filter the feed while scrolling (an Unread list
would jump), and the custom-domain probe runs only after every other plugin has declined a link, never for
Instagram's `/p/` links.

Each item below is one commit with its tests. All are local: no new endpoint, no write to Substack, no new
dependency. Every new UI string goes into all 29 ARB files.

## 1. Paid and Saved filters

`SubstackFeedFilter` gains `paid` (the opposite of `free`) and `saved` (posts in the local Saved library, matched
with `sameSubstackPost`). The feed store takes the saved test with the read ids, and the screen syncs it whenever
Saved changes, so unsaving a post removes it from a Saved-filtered feed at once.

## 2. Read-time estimate

`substackReadMinutes(post)`: the body's words when there is a body, else the API's `wordcount` field (read through
`Json`, UNVERIFIED until a HAR shows it), at 230 words a minute, rounded up, at least 1. None for a podcast or video
post, or when neither count is known. Shown in the card's meta line and the reader header.

## 3. Podcast speed

`PodcastStore.setSpeed` calls the player's existing `setRate`. Speeds 0.8×, 1×, 1.25×, 1.5×, 1.75× and 2×. The
choice is one preference and applies to the next episode too. A menu on the audio player picks it.

## 4. Per-episode resume

Beside the single checkpoint, a small journal keeps the last 50 episodes by URL, newest first, each with its
position. Opening an episode starts where that episode stopped, and finishing one removes it. The checkpoint still
restores the current episode on a cold start and joins the journal when another episode opens, so no place is lost
on upgrade.

## 5. Font and column width

Two Substack reader settings: font (serif, the current Georgia stack, or sans, the system stack) and column width
(narrow 36rem, medium 42rem, the current one, or wide 52rem). The web page wrapper and the native reader both read
them. The defaults keep today's look.

## 6. Mark read when scrolled past

A setting, off by default, since it changes what Unread means: a post whose card scrolls fully off the top of the
Home feed is marked read. Batched, at most once per second, through `SubstackReadStore.markPostsRead`.

## 7. Local full-text search of kept posts

The library search also matches the bodies of posts kept offline and in the article cache, through the plain text
the parser already produces. The search runs off the UI isolate above the same threshold as parsing. Results show
the matching passage.

## 8. In-app opening for unknown custom domains

A link of the shape `https://{host}/p/{slug}` on a host that is not followed opens in the reader, which asks
`{host}/api/v1/posts/{slug}`. Only a response that parses as a Substack post is shown; anything else opens the
browser, as today. No cookie is attached: the client has none (§0.1 of the audit).

## Not here

Login, subscription import, entitled paid bodies, comment reply and like, like and restack sync (Tier 4) need the
sign-in decision. Notifications and Wi-Fi downloads need new dependencies. Voiceovers need the `audio_items` shape
from a HAR. See `docs/specs/substack-tier4-5.md` for the Tier 4 and 5 items that can be done without them.
