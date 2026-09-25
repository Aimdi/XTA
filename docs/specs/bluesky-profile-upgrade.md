# Bluesky profile reader upgrade

## Goal

Make a public Bluesky profile useful for reading and recoverable on unreliable
connections, while preserving independent Posts, Replies, Media and local Likes.

## Behavior

- Show the author's pinned post in Posts and identify it with the existing
  localized pinned-post label. Pins remain distinct from reposts; duplicate feed
  appearances render once.
- Retain loaded cards during refresh and metadata failures. Refresh the selected
  feed independently of metadata once identity is known, so a failed header
  request cannot stop timeline reading.
- Retry the operation that failed: a failed refresh retries page one, while a
  failed load-more retries its existing cursor. Reject stale or disposed results
  and stop repeated/cyclic cursors without dropping newly returned posts. A
  changed AppView restarts reading, and a handle that now resolves to another DID
  clears the previous identity's feed cache before loading the new author.
- Make profile biographies' explicit HTTP(S) links tappable, preserve Unicode
  text, expose post counts, and keep long names and metadata readable at large
  text sizes. Follow counts retain navigation and accessible tap targets.
- Open post search directly from the profile with its author filter prefilled.

## Boundaries

Public reads and existing local actions only. Keep Store state, localized labels,
current API transport and persistence. No X client/database or dependency edits.
The integration owner supplies optional pinned metadata/client additions.

## Verification

Focused tests cover pin deduplication and display, refresh failure recovery,
independent metadata/feed failures, stale requests, cursor cycles, biography link
parsing, and narrow/large-text profile cards. Existing profile and reading tests
remain required.

Verified with Flutter 3.44.4: 31 profile/reading tests pass, including 13 new
regressions. Scoped analysis reports no errors or warnings (two existing share
API deprecation infos). The initial profile header still requires one successful
metadata read; retained timelines continue independently on later failures.
