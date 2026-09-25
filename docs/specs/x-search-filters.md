# X content search filters

## Problem

Advanced search can restrict results to media but cannot distinguish photos,
videos, or links, and requires handwritten operators to exclude replies or
reposts. Removing an active filter must retain every other structured choice.

## Scope

- Offer one content choice: All, Media, Photos, Videos, or Links. Selecting one
  replaces the previous choice, avoiding contradictory image/video filters.
- Add independent Hide replies and Hide retweets switches using existing labels.
- Keep `AdvancedSearchState(onlyMedia: true)`, `onlyMedia`, `copyWith(onlyMedia:)`,
  and the existing query-builder arguments compatible with callers.
- Emit `filter:media`, `filter:images`, `filter:videos`, or `filter:links`, and
  optional `-filter:replies` / `-filter:retweets`. Keep raw query editing intact.
- Expose each selection as a removable active filter; resetting clears all new
  controls along with the existing text, engagement, and date controls.
- Recover supported trailing top-level content/exclusion operators when opening
  a raw or recent query in the form, so changing content does not append a
  conflicting operator. Preserve quoted, grouped, unsupported, and malformed
  query text without attempting a full search-language parser.
- Use flutter_triple state and wrapping content chips suitable for narrow
  screens and enlarged text. New label `search_links` means links contained in
  posts and is translated in every supported locale.

No X write actions, client changes, database changes, or dependency changes.
Search results remain subject to the current X search endpoint and account
availability; the feature sends query operators through the existing reader.

## Operator evidence

The official X [search migration operator table](https://docs.x.com/x-api/posts/search/migrate/overview)
documents the legacy `filter:media`, `filter:images`, `filter:videos`,
`filter:links`, `filter:retweets`, and `filter:replies` syntax. X's
[advanced X Pro guide](https://help.x.com/en/using-x/advanced-postdeck-features)
documents excluding types with `-filter:type`. Existing group feeds already
use the same reply and retweet exclusions. Checked 2026-09-25; do not substitute
API v2 `has:` / `is:` operators into this legacy search client.

## Acceptance checks

- Existing advanced-search queries and only-media callers produce the same text.
- Every content choice emits exactly one matching content operator.
- Switching, clearing, and resetting content/exclusion filters preserves unrelated
  fields and never retains a stale content operator.
- Result-store query and active filter chips stay synchronized after clearing.
- Reopening a generated query restores its content and exclusion controls;
  quoted operator literals and grouped expressions remain untouched.
- The form applies selected filters, discards canceled changes, and has no layout
  overflow on a narrow screen at large text scale.
