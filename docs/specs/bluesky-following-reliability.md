# Bluesky Following reliability

The local Following reader merges public author feeds under a bounded request
budget. Improve that path without changing authentication, the shared X client,
the database, or dependencies.

## Intended behavior

- Show recent repost activity using the feed reason timestamp, while keeping the
  original post timestamp available. Prefer a followed original over a duplicate
  repost so hiding reposts does not hide the author's own post.
- Repaint changed content, counts and labels even when post identities stay the
  same; preserve stable keys and avoid partial-cache list shrinkage.
- Keep successful and last-known author pages when another author fails; expose
  a recoverable refresh error and retry failures on a later entry or refresh.
- A bounded refresh prioritizes failed and unread accounts, then the oldest
  successful read. Large imports must continue making progress.
- Ignore stale responses after another refresh, AppView change, follow-set
  change, or disposal. Reads for group subsets must not publish into Following.
- Successful empty reads remain cached; failed empty reads remain retryable.

## References and design rationale

- Graysky's feed-first design: https://graysky.app/
- deck.blue's independent feeds, lists and filtering:
  https://web-cdn.bsky.app/profile/deck.blue/post/3kj6qx2s2xv2m
- Klearsky's local data, caching and reading customization:
  https://github.com/mimonelu/klearsky
- Official repost reason timestamp schema:
  https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/defs.json

These are interaction and protocol references only; no external code is copied.

## Verification

Unit tests cover reordered repost activity, original/repost deduplication,
changed-content equality, empty success versus retryable failure, partial
failure recovery, pending-account progress, changed AppViews and follows,
overlapping refreshes, group isolation, and disposal while requests are pending.

## Verification completed

- 28 focused Following tests pass, including 14 new recovery/activity cases.
- Focused Dart analysis has no errors or warnings; one new style hint was fixed.
- Widget and complete-suite verification remains an integration gate.
