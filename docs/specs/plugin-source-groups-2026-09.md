# Group membership across account plugins

Base: Aimdi129 (`e6551526`).

## Requested behavior

People followed in Pixiv, Instagram, TikTok and Hacker News can join the same local groups as
X, Threads, Bluesky and Mastodon accounts. Existing subreddit, publication, RSS
and Booru subscription grouping remains available. Account identity stays scoped
to its source even when two platforms share the same handle or numeric ID.

## Implementation

- Extend the subscription source contract with source-owned loading so a source
  need not claim a nonexistent SQLite table. Keep all database schema/client
  files unchanged; Pixiv local group subscriptions use JSON preferences carried by settings backups.
- Register Instagram and TikTok existing follow tables as subscription sources,
  provide their group timeline cards, navigation and local unfollow cleanup.
- Register Pixiv selected authors as local subscriptions, with an Add to group
  action beside its existing remote follow button. Group actions never change
  remote follow state; opening or cancelling a picker writes nothing.
- Add a grouped following list entry for Pixiv, including paginated remote
  following discovery and explicit local Add to group actions.
- Add consistent group actions to Instagram/TikTok profiles. Shared subscriptions
  and group editors list all supported followable account sources.
- Replace membership sheet feature state with flutter_triple Store. Keep existing
  localized labels and add translated labels only if existing context does not fit.
- Load only explicitly selected private plugin members into groups; do not enable
  these plugins in the home timeline by default.

## Verification

Pure tests exercise namespaced IDs, storage serialization, member splitting and
membership selection cancellation/identity. Run focused analysis/tests and the
repository localization generator where tooling is available. Confirm no frozen
client/database edits, generated code commits or dependency pin changes.
