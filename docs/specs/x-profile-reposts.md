# Reposts on X profiles

## Problem

X split a profile's reposts out of its Posts tab. The web's Posts tab now
reads `UserOriginalsTimeline`, which carries original posts only, and reposts
have their own tab backed by `UserRepostsTimeline`. XTA moved its Posts tab to
`UserOriginalsTimeline` (ab35da49), so:

- "All" shows no reposts at all;
- "Retweets" filters the originals timeline for reposts and always comes back
  empty.

Readers see no reposts on any profile.

## Behaviour

The Posts tab's filter chips keep their meaning:

| Chip     | Reads                                                     |
|----------|-----------------------------------------------------------|
| All      | `UserOriginalsTimeline` and `UserRepostsTimeline`, merged newest first |
| Posts    | `UserOriginalsTimeline`                                   |
| Retweets | `UserRepostsTimeline`, shown as X returns it              |

- "Posts & replies" and "Media" are unchanged.
- The merge interleaves by time across pages. An item is shown only once it
  is newer than the oldest item already fetched from every timeline that has
  more to give, so a later page never brings back something newer than what
  is already on screen. The pinned post stays first.
- A post that appears in both timelines is shown once.
- Each timeline's first page is cached as profile pages already are. Reposts
  use their own cache key (`type` `reposts`).
- Rate limits are tracked per operation, so a rate-limited
  `UserRepostsTimeline` reports as that operation.

## Code

- `lib/client/endpoints.dart`: a new `userRepostsTimeline` registry key, so an
  `endpoints.json` override can repair its query id without a release. The
  starting id `CnNg1YiKG7bRgi-RoIfKsw` comes from the public
  TwitterInternalAPIDocument registry.
- `lib/client/client.dart`: `getTweets(..., reposts: true)` reads it with the
  Posts tab's variables and features.
- `lib/profile/merged_timeline.dart`: a pure merge of two newest-first paged
  timelines, tested on its own.
- `lib/profile/_tweets.dart`: picks the source per chip.

## Verification

- Unit tests for the merge: interleaving, carry-over between pages, one side
  running out, a pinned post, duplicates.
- The live endpoint canary probes every registered endpoint, so it reports
  whether the new query id resolves (404 means rotated).
