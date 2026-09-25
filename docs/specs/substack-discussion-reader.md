# Substack Notes and discussion reader

## Current gaps
Article comments keep feature state in the widget, have no local navigation
controls, and replace already loaded discussion content on refresh failure.
Nested comments are indented by source depth but cannot be collapsed or searched.
Notes have plain untappable links and images, duplicate publication actions, and
an author fallback that can dereference a missing name.

## Changes
- Use a flutter_triple Store for loading, retry, query, order and collapse state.
  Retain loaded comments on failed refreshes and ignore stale/disposed results.
- Rebuild nested comment rows from parent IDs (depth fallback for old fixtures),
  preserving orphans, deduplicating IDs and safely breaking cycles. Sort siblings
  by original order, newest or oldest; search loaded comments while retaining
  connecting parent context, and expand/collapse branches individually or together.
- Keep article context visible with a browser action and pull-to-refresh. The
  existing all-comments endpoint supplies discussion data; do not invent paging
  or Note reply endpoints.
- Share Note rendering between the card and detail: robust author fallback,
  readable/linkable text, shared fullscreen media viewer and accessible counts.
  Show local publication follow state and reuse publication navigation/actions.
- Use existing localized labels where their meanings match and translate the two
  new comment-search labels through the repository localization workflow.

## Verification
Test topology, deep trees, sibling sorting, filtered context, request overlap,
retained errors and disposal. Exercise compact/enlarged RTL comment controls,
Note missing metadata, image-viewer entry and live local-follow state using fakes.

## Validation result
The 17 new discussion/Note tests and 9 existing Substack feature tests pass.
Three rendered layouts were visually checked: normal comments, 320px enlarged
RTL comments, and enlarged RTL Note details. The fixture image opens the shared
fullscreen viewer. No live Substack or device test is implied by these fixtures.
