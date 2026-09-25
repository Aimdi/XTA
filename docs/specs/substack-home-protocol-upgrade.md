# Substack home reading and public protocol upgrade

## Problems

Home has type/read filters but no loaded-post search or deliberate reading order;
Inbox cannot continue to older unread pages. Local read state cannot be undone
from cards. Library controls use widget state and lose choices on remount.
The client turns malformed or failed archive pages into successful exhaustion,
search is capped at one page, and optional model fields can invalidate a whole
saved library or remote page.

## Changes

- Add Store-backed, session-preserved loaded-post search and stable chronology /
  popularity controls to Home and Inbox; include video in existing type filters.
- Provide visible refresh and pagination failure/retry states, and allow paging
  from empty Inbox/filter results when source pages remain.
- Add accessible individual mark-read/mark-unread card action, keep footer
  controls usable at enlarged text widths, and make Library choices Store-backed.
- Extend existing per-publication archive search with offset; preserve true empty
  pages, propagate complete request failure, and cap network request duration.
- Harden touched post/publication/note/snapshot parsing using Json, and retain
  comment parent identity while flattening bounded, cycle-safe trees.
- Preserve source URLs and existing paywall markers; no login, remote writes,
  dependency/schema changes, or changes under frozen lib/client or lib/database.

## Validation

Fixture tests cover encoding, empty/failure distinctions, unsafe optional fields,
read transitions, filters, ordering and cursor recovery. Run localized compact
widget tests, full suite, analyzer and formatting on pinned Flutter 3.44.4.
