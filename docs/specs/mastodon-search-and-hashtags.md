# Direct post search and resilient hashtag reading

## Problem

Mastodon status URLs entered into search can be interpreted as account addresses
or sent to guest search, which cannot resolve unknown URLs on ordinary servers.
Hashtag pages use local widget state, replace readable content during refresh,
and silently stop loading after a page failure.

## Changes

- Recognize public Mastodon status URLs in search before account lookup. Read
  the numeric status ID directly from its origin with the existing public
  `getStatus` API. Keep the normal results and conversation navigation, request
  ordering, and retry behavior. Require an HTTP(S) origin, no credentials, and
  an exact Mastodon status path before making this request.
- Manage hashtag pages in a `flutter_triple` Store. Keep posts visible while
  refreshing and when refresh fails. Show actionable initial, refresh, and
  pagination errors, plus an explicit Load more control.
- Separate the server paging cursor from rendered/deduplicated posts. Use the
  boost wrapper cursor supplied by the shared model and stop repeated pages.
- Ignore results from older refreshes, old pagination requests, and closed
  routes. Preserve the existing public, account-free reading boundary.

## Inspiration and constraints

Pachli's search UI and Phanpy's direct post links favor intent-aware search.
Mastodon's public API documentation states that full-text post search is not
available to unauthenticated clients and URL resolution requires a user token.
Origin status reads avoid adding an account requirement or misleading advanced
server search controls.

- https://pachli.app/pachli/2024/07/29/2.7.0-release.html
- https://github.com/cheeaun/phanpy/blob/main/CHANGELOG.md
- https://docs.joinmastodon.org/methods/search/

## Validation

Use mocked clients to cover direct URL and ordinary search routing, invalid URL
shapes, stale search results, refresh/pagination races, disposal, retained posts
after failures, deduplication, and non-advancing cursors. Widget journeys cover
hashtag retry and manual pagination. Use Flutter 3.44.4 and existing dependencies.

Implemented verification: all 10 tests in `mastodon_search_hashtag_test.dart`
pass. Targeted analysis of the search screen, search store, hashtag store, and
regression tests reports no issues. These checks use fixtures, not live servers.
