# Substack discovery and search upgrade

## Current gaps
The publication discovery sheet uses mutable widget state and can display an older category or query after a new search. It drops results on error, stops at the first page, and shows raw exceptions. Add-publication can follow a lookup after its screen has closed, and a failed second lookup can reuse the preceding publication.

## Scope
- Replace the sheet implementation with a full-screen Store-backed route while preserving its public entry point and changed-subscriptions result.
- Browse categories and paginate their publications using the existing category/page API; paginate publication-name search using the existing publication search/page API. Stop when pages add no unique publications.
- Offer article search specifically within locally followed publications, using the existing per-publication archive search API. Label this scope clearly; do not invent a global article endpoint.
- Limit concurrent article searches, preserve partial results, and let failed publications retry without repeating successful ones. Merge articles by publication and canonical identity, newest first.
- Remember recent queries locally with a clear-history control. Show failure, loading, and empty states accessibly at compact widths and enlarged text sizes.
- Resolve pasted publication/article URLs to a preview and preserve recoverable article errors. Local following is an explicit action after preview; reading a pasted post never silently follows its publication.
- Reuse the same guarded search store in the Discover hub. All late completions are ignored after a newer request or disposal.

## Reference
https://support.substack.com/hc/en-us/articles/4406018060692-How-do-I-find-a-Substack-publication

Substack's reader discovery supports categories and search. XTA's implementation uses only its existing public read endpoints and handles their availability honestly. Paid content restrictions remain unchanged.

## Validation
Focused tests cover query/category races, paging deduplication and failed-page retry, partial article search, bounded concurrency, URL preview, disposal, and preview/follow ordering. Widget tests cover compact width with 200% text and readable retry controls.

## Implemented verification
- 32 focused tests pass. Coverage includes categories/search races, page deduplication and retry, empty versus failed results, direct article links, per-publication offsets, bounded concurrency, partial retry, changed followed-publication identities, refreshed overlapping article metadata, and preview/follow lifecycle.
- Search and preview widget tests pass at 320 px width and 200% text.
- Analysis of the four changed/new feature files and focused test reports no issues.
- Article search is explicitly scoped to followed publications; the publication/category APIs use their existing page argument. No global post-search endpoint or authenticated action was added.
