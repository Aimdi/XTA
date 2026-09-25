# Mastodon profile reader

## Problem

Profiles currently offer Posts and Media, flatten profile fields into plain text,
and tie both feeds to one loading/error flag. Readers cannot browse replies, open
verified profile links, or share a profile directly. Pinned cards contaminate the
normal timeline pagination count and cursor.

## Changes

- Add Posts & Replies alongside Posts and Media, retaining a separate cache,
  loading state, pagination cursor, error, and scroll position for each view.
- Fetch each tab with its matching public account-status filter. A tab change
  starts its own request immediately; stale refreshes and disposed stores cannot
  overwrite current data. Retry stays scoped to the failed tab.
- Preserve pinned cards in Posts while deriving pagination from the normal
  timeline. Stop repeated/duplicate pages from creating an endless load loop.
- Present account banner, joined date, accessible tab selection, selectable bio,
  and linkable metadata with verified-link indicators where supplied by server.
- Add read-only profile browser and share actions. No federation writes, login
  changes, database changes, dependency changes, or client rewrites.

## Validation

Use fixture clients to exercise concurrent tabs, replies query, pagination with
pinned entries, repeated pages, refresh races, and failure recovery. Widget tests
cover compact/enlarged text layouts and useful accessible labels. Run focused
Mastodon tests and analyzer with the repository's pinned Flutter SDK.

## Result

Implemented three independently loaded tabs, corrected pinned/boost cursor use,
selectable bios with clickable URLs and hashtags, profile actions, banner/joined
metadata, verified linked fields, and accessible count labels. Existing media tab
API compatibility is retained. A short page always has an explicit Load more
action when another page is available.

Validation: 18 focused profile/reading-experience tests passed, including 10 new
profile tests. Profile screen/store/card plus new test analyze without issues.
Compact 320px, doubled text, and RTL metadata are covered by widget checks.
