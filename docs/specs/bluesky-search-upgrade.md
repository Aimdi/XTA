# Bluesky search upgrade

## Problem
The current search sheet stops after twenty results, shares loading/error state between two tabs, and lets an older response overwrite a newer search. A post URL is mistaken for its author's profile. Small screens lose most of the available reading area to the sheet and keyboard.

## Design
- Preserve `showBlueskySearchSheet` call compatibility while presenting a full-screen reading route.
- Move search state to `flutter_triple`, retain independent People and Posts pages, and guard every async completion against changed queries or disposal.
- Paginate both result kinds with deduplication, repeated-cursor protection, and retry that retains already loaded content.
- Offer Latest/Top ordering and structured author/hashtag filters supported by the public AppView. Keep hashtag search exact through the API tag parameter.
- Resolve canonical Bluesky post URLs and AT URIs to a post result; do not treat post URLs as profile links.
- Keep suggestions, local subscription controls, recent search chips, imports, and a clear-history action. Never perform a server write.
- Make loading, empty, failure and end-of-results states readable and actionable. Use wrapping controls and a full-width search field at enlarged text sizes.

## References
- https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/searchPosts.json
- https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/actor/searchActors.json

The stable post search lexicon supports `sort`, `author`, repeated `tag`, and `cursor`; it does not offer a media filter. Cursor availability is server-controlled and does not promise every search hit can be traversed.

## Validation
Cover independent tabs, stale request suppression, disposal, pagination deduplication/cursor cycles, errors preserving results, structured parameters, strict direct-link parsing, direct lookup, and compact/enlarged-text layout. Run focused Flutter tests and analyzer with the pinned toolchain.

## Implemented verification
- 28 focused tests pass, including 320 px width at 200% text, profile-scoped search, independent pages, stale success/error handling, configured-AppView changes, cursor cycles, direct URL/AT URI reads, and retry preserving content.
- Search state, widget, and test analysis report no issues.
- Pagination failures are shown beside the load-more controls; refresh failures remain above retained results.
- Profile toolbar integration can seed `initialAuthor` while leaving the search field ready for a query.
