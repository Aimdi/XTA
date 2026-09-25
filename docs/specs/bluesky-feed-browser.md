# Bluesky feed and list browser upgrade

## Goal
Make public custom feeds and lists dependable places to read, with discoverable
feed descriptions and independent recovery when the catalog or a page fails.

## Changes
- Keep a bounded page cache per AppView and source URI. Switching sources selects
  immediately and never shows another source's posts under the selected heading.
- Remember the selected feed/list on this device, scoped to the chosen AppView.
- Preserve custom-feed generator ranking instead of sorting ranked results by date.
- Guard stale, overlapping, and disposed requests; stop repeated-cursor loops;
  preserve cached pages on failed refresh and expose separate pagination retry.
- Browse and search the public feed catalog with names, creators, descriptions,
  local pin controls, paginated results, empty feedback, and retry.
- Load and page public lists by actor with independent catalog recovery.
- Keep pins usable and stable while catalog reads or persistence are in flight.
- Retain the read-only client and existing Store architecture. Do not alter X
  networking, database, authentication, pinned dependencies, or server settings.

## Validation
Fixture HTTP tests exercise switching, overlapping pages, stale responses, repeated
cursors, ranked order, retry, cached selectors, server changes, and persistence.
Widget tests exercise an empty source and accessible explicit paging controls.

## Implemented verification
- 40 focused tests pass (`bluesky_feed_browser_test.dart` plus existing
  `bluesky_feeds_test.dart`), including 29 new regression and widget cases.
- Selected-source persistence and catalog completion are independent: a restored
  feed can finish while the public catalog is still loading.
- Catalog and list cursor ownership is checked separately from post selection,
  including changing AppView before ever opening a list.
- Requests use the existing read-only client. Live AppView and Android device
  behavior were not exercised by these fixture tests.
