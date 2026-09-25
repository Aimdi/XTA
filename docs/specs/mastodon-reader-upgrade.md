# Mastodon reader upgrade

## Product direction

Make public reading comfortable across a busy feed, a person's profile and a
long conversation. This builds on the existing dedicated Mastodon client,
local subscriptions, bookmarks and offline reading snapshots.

The useful patterns from established clients are Phanpy's filtering and
threaded reading, Tusky's independent reply/boost visibility controls,
Moshidon's configurable reading presentation and Pachli's clear filter state.
These are design references, not claims of installed-device comparisons or
copied implementations.

## Implemented

| Area | Result |
| --- | --- |
| Timelines | Local loaded-post search, All/Media/Links filters, hide boosts/replies, active-filter badge, feed/newest/oldest order, per-timeline saved choices and reset |
| Reading recovery | Filtered views retain the underlying snapshot; followed boosters survive restart, including Misskey renotes; duplicates retain original posts when boosts are hidden |
| Profiles | Independent Posts, Posts & Replies and Media caches, banner, joined date, tappable verified fields, selectable bio, profile sharing/browser actions |
| Conversations | Author-thread focus with necessary reply context, expand/collapse all, return to selected post, accurate ancestor order, cycle-safe deep reply traversal |
| Search and hashtags | Direct origin lookup for recognizable post URLs; hashtag Store, explicit older-page loading and retry, retained content during refresh failure |
| Post reading | Sensitive attachments absent until reveal, warning state tied to post identity, quote warnings retained, clickable HTTP(S) text links and Unicode hashtags |
| Polls and accessibility | Correct multiple-choice percentages, unavailable-result state, vote totals and closing information; ALT access and engagement semantics |
| Reliability | Latest-request/disposal guards, visible retryable errors, preserved source/cursor on failed refresh, boost-envelope paging IDs |
| Localization | 22 new labels across 29 locales; search hints explain post URL lookup |

Filters and sorting operate on loaded content. Search text stays in the current
session while filter/sort choices persist locally. Native video/audio playback,
server-side follow/filter/list editing and OAuth are not part of this change.

## Next improvements, in priority order

1. Preserve named HTML anchor destinations through parsing and saved snapshots;
   the current text reader links explicit HTTP(S) URLs only.
2. Add dedicated video/audio playback with lifecycle, reduced-motion and
   bandwidth controls, then bring normal video/audio attachments into the model.
3. Add local saved hashtag shortcuts and a source picker for communities.
4. Consider optional long-post collapse and compact boost grouping after testing
   reading-position behavior with changing row heights.
5. Isolate fallback-walk timeout context between concurrent client reads and
   allow optional pinned fetches to fail independently of a readable profile.

Authenticated lists, notifications and server actions would be a separate
product decision; the current reader does not imply those capabilities.

## Evidence and verification

Primary references:

- https://phanpy.app/
- https://github.com/cheeaun/phanpy
- https://tusky.app/faq/
- https://github.com/LucasGGamerM/moshidon
- https://pachli.app/pachli/2024/07/29/2.7.0-release.html
- https://docs.joinmastodon.org/methods/search/
- https://docs.joinmastodon.org/entities/Status/
- https://docs.joinmastodon.org/entities/Poll/
- https://docs.joinmastodon.org/entities/Account/

All changes retain Flutter 3.44.4, pinned dependencies, Store state management,
and the existing SQLite schema. `lib/client/` and `lib/database/` are unchanged.
Validation uses deterministic fixtures and mocked HTTP plus the repository
suite. Populated phone, compact, large-text and RTL widget renders are inspected.
There is no physical-device, authenticated-account or APK validation in this run.

Implementation specs: `mastodon-reading-controls.md`, `mastodon-profile-reader.md`,
and the conversation, card, hashtag/search and feed-reliability specs alongside it.


## Completed validation

On Flutter 3.44.4 / Dart 3.12.2:

- Full repository suite: **2,716 passed**, **5 opt-in live tests skipped**.
- Analyzer: **0 errors, 0 warnings**, 137 informational lints across the repo.
- New Dart file formatting gate: 13 files checked, no changes required.
- ARB validator, mirrored-skill check and git whitespace check passed.
- All 22 added localization keys exist in all 29 locales; unrelated pre-existing
  missing translations remain unchanged.
- Verified that the frozen X client, database, dependency manifests and Flutter
  pin are unchanged. Existing X reader work is preserved on this branch.
- Both incremental Mastodon and combined X+Mastodon patches reverse-apply cleanly.

Source is committed locally. Public GitHub publication was not performed because
it still requires explicit user approval after the earlier automatic review.
