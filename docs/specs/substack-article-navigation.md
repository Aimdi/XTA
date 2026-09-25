# Substack article navigation and recovery

## Problem

The existing reader has appearance, progress, offline saving and speech controls,
but long articles have no contents or local find. Article anchors currently leave
the reader. Cached bodies cannot be explicitly refreshed, and a WebView main-frame
failure can leave an indefinite spinner.

## Changes

- Build a deterministic, sanitized article index with heading levels and readable
  passages. Retain publisher anchor targets and assign separate collision-free
  targets to searchable blocks.
- Add an accessible contents and find sheet, driven by a flutter_triple Store.
  Search only the body already available to this reader, return matching passages,
  and jump within the document. Never infer or fetch unavailable paid content.
- Keep same-article fragments inside the reader; navigate article links normally.
- Offer explicit refresh. Keep the visible body on a failed refresh and let the
  user retry; stale responses and disposed stores must not overwrite newer state.
- Report main-frame loading failures and preserve the existing public-site fallback.
- Scope the optional article body cache to full origins and exact slugs so custom
  domains with the same label cannot exchange cached bodies. Old cache keys miss
  safely and refetch; saved offline articles remain independent.
- Adapt document direction, lists, tables and quotation borders for RTL and compact
  reading without changing shared article appearance/progress semantics.

## Validation

Fixture tests cover duplicate/missing headings, markup stripping, nested paragraphs,
query matching, fragment resolution and stable anchors. Store tests cover latest
request wins, failed refresh retention and disposal. Widget tests exercise contents,
find, passage selection, no-results and 320px large-text RTL layout. Existing
Substack sanitizer/preview/TTS and shared article control tests remain gates.

No dependency updates, schema changes, frozen clients, write endpoints or new
publisher authentication are involved.

## Implemented and checked

- 24 new regressions across navigation fixtures, refresh/cache stores and navigation
  widgets; the focused run including existing reader, TTS and sanitizer gates passed
  all 61 tests. Owned files have no analyzer diagnostics.
- Contents, search, no-results and precise passage selection are covered. Rendered
  390px light and 320px dark RTL at 200% text scale; the search label wraps outside
  the field to avoid truncation, and keyboard-inset layouts remain usable.
- The shared progress bridge gained an optional `jumpTo` hook: native navigation
  stops initial resize restoration without reporting a user reading scroll. Other
  readers keep their existing behavior.
- Escaped top-level HTML examples stay text after sanitization and indexing.
- Platform WebView navigation and main-frame error callbacks still need a physical
  Android device smoke test; widget coverage exercises the native navigation sheet.
