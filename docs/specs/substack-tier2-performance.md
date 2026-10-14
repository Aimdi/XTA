# Substack Tier 2: performance

Base: `324c9dd`. Implements the Tier 2 queue from `docs/substack/audit.md` §9, in order. Tier 1 items still open
(BUG-01, BUG-12, BUG-07, BUG-08, BUG-10) wait on a decision or on recorded responses; Tier 2 starts with the
owner's go-ahead. Device scenarios stay UNVERIFIED here (no device); each item is measured on the host VM against
its base commit in the same session.

## 1. Parse each body once, off the UI isolate (S2)

Opening a post parses its body five times on the UI isolate: the sanitizer and the navigation index parse it
(`SubstackArticleDocument.parse`), the page wrapper sanitizes the already clean body again (`wrapSubstackHtml`), and
the speech text sanitizes and parses it twice more (`substackHtmlToPlainText`).

- `SubstackArticleDocument.parse` also produces the speech text, walking the fragment it already has.
- `wrapSubstackHtml` takes `bodySanitized: true` for a body that came out of that parse, and skips the second
  sanitize. Every other caller still has its body sanitized.
- The reader runs the parse through the shared background helper, which uses `compute` for bodies over 20,000
  characters (the Pixiv novel reader's threshold, moved to `lib/utils/background_parse.dart`; Pixiv keeps its names).
  The theme, text scale and strings are read before the await; the result is dropped if a newer load started.
- Unchanged: sanitizer rules, the page markup, what speech reads.

Measure: the S2 benchmark's open pipeline follows the reader, so the base and HEAD each measure their own pipeline.
The output must stay the same: a test compares the page body and speech text of the one-parse path with the old
functions' output for the synthetic long post.
