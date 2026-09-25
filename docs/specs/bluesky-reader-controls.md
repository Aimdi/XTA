# Bluesky timeline reading controls and metadata

## Goal

Bring the same deliberate reading controls and recovery to Bluesky while
respecting its custom feed ranking, stable AT URIs and UTF-8 facets.

## Scope

- Loaded-post search, media/link selection, hide replies/reposts, stable sort
  choices and reset on Following and local Likes.
- Bounded, AppView-scoped Following reading snapshots and position recovery;
  retain the unfiltered source page so hidden items remain available offline.
- Reuse the existing sliver anchor behavior through a generic reading view,
  keeping Mastodon's public adapter and behavior unchanged.
- Add visible refresh failure and remaining-account actions to Following.
- Improve entry to settings and preserve plugin session selection/navigation.
- Parse repost time separately from creation time, profile pinned post URI,
  and preserve exact text bytes for facets. Harden optional snapshot fields.
- Add documented public read methods for paginated actor search, post lookup
  and post search ranking/author/hashtag options.

No login, remote write, schema or dependency changes. Frozen X client and
database stay untouched. Local follows, likes and existing custom feeds remain
in their current stores. Designs use patterns from Bluesky, Graysky, deck.blue
and Klearsky without copying their implementations.

## Validation

Unit and widget cases cover ranking, malformed saved data, repost times,
Unicode facets, request encoding, filter recovery and reading snapshots.
Run new regressions and the complete repository suite with Flutter 3.44.4;
inspect compact/large-text/RTL rendered fixtures. No physical-device claim.

References: https://github.com/bluesky-social/atproto/tree/main/lexicons/app/bsky
and https://docs.bsky.app/docs/advanced-guides/posts .
