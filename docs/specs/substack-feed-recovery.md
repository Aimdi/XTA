# Substack feed recovery and local library consistency

## Scope

Keep the existing public read-only client methods and Store state shapes. Do not
change the X client, database schema, dependencies, or publication archive store.

## Reader behavior

- Bound publication reads to three concurrent requests and 24 publications per
  pass. Continue untouched publications before older pages, so imports progress.
  Rotate attempted sources so permanently failing publications cannot starve
  readable older pages.
- Keep offsets per publication. Advance only successful pages; retries resume
  the failed offset and keep already readable articles on screen.
- Cache successful empty results, surface refresh and paging errors, and ignore
  results from replaced requests, removed publications, changed hosts, or closed
  stores. Preserve updated metadata on duplicate posts without duplicate rows.
- Keep Notes pagination on the host that issued the cursor; only a fresh refresh
  chooses another discovery host. Stop repeated/cyclic cursors and retain Notes
  when a refresh or subsequent page fails.
- Serialize local read, liked, saved, and publication pin preference writes. Rapid actions must
  preserve every requested change, and a failed write must not poison later work.
- Support marking individual or multiple articles unread; deduplicate read IDs
  before applying the existing history cap.

## Inspiration

Substack describes a chronological subscriptions inbox, a saved reading queue,
and previously read articles: https://substack.com/features
Readwise Reader describes a unified reading library and flexible organization:
https://readwise.io/read

These guide interaction choices only. No external client code is copied, and no
new undocumented network method or remote write is introduced.

## Verification

Focused tests exercise per-publication offset retry, bounded concurrency, large
follow-set progress, successful empty cache behavior, stale refresh/paging/source
responses, Notes host and cursor isolation, metadata deduplication, disposal,
rapid local writes, failed persistence recovery, and read/unread transitions.

## Implemented and verified

The store exposes separate refresh and paging errors/busy flags, pending-source
counts, and explicit paging retry. Successful empty responses also notify the
final idle state so retry controls cannot remain disabled. Main-feed post identity
is normalized publication URL plus post ID, retaining distinct posts across
publications and preserving custom publication paths.

The focused recovery suite covers 24 scenarios, including delayed network and
preference writes, 27-publication failure rotation, duplicate page count updates,
failed storage writes, and read/unread persistence. Existing feed freshness, local
library, and publication pin regressions remain in the focused verification run.
