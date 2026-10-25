# X content search filters

## Problem

Advanced search can restrict results to media but cannot distinguish photos,
videos, or links, and requires handwritten operators to exclude replies or
reposts. Removing an active filter must retain every other structured choice.

It also cannot pick a language, show only replies, leave out posts with links,
or bring reposts into the results, and operators the reader types into the
search bar (`from:alice lang:fr`) are invisible to the chips and the filters
screen: only trailing `filter:` operators were recognised.

## Scope

- Offer one content choice: All, Media, Photos, or Videos. Selecting one
  replaces the previous choice, avoiding contradictory image/video filters.
- Replies and links each offer Include (X's default, no operator), Only
  (`filter:replies`, `filter:links`) and Hide (`-filter:replies`,
  `-filter:links`), as wrapping chips whose labels name the kind: Include
  replies / Only replies / Hide replies, and Include links / Only links / Hide
  links. Links leave the content choice, so a post type and a link choice
  combine (`filter:images -filter:links`).
- Include reposts (`include:nativeretweets`) and Hide retweets
  (`-filter:retweets`) are switches; turning one on turns the other off.
- A language picker sets `lang:xx`: Any language, then the languages X tags
  posts with, each named in its own language and sorted by that name. A code
  read from a query that is not in the list is still offered, so the picker
  can show it.
- Keep `AdvancedSearchState(onlyMedia: true)`, `onlyMedia` and
  `copyWith(onlyMedia:)` compatible with callers.
- The query X receives stays minimal and stable: a default choice writes
  nothing, each filter writes at most one operator, and the order is fixed —
  words, `lang:`, accounts, engagement, dates, post type, links, replies,
  reposts, retweets. Queries the form produced before keep their exact text.
- Expose each selection as a removable active filter; resetting clears all
  controls along with the existing text, engagement, and date controls.
- Read back the operators the form can show, wherever they stand at the top
  level of a typed, recent, saved or linked query: `lang:`, `from:` and `to:`
  (one account or an `(from:a OR from:b)` group), `min_replies:`,
  `min_faves:`, `min_retweets:`, `since:`, `until:`, `filter:media`,
  `filter:images`, `filter:videos`, `filter:links`, `filter:replies`,
  `-filter:links`, `-filter:replies`, `-filter:retweets` and
  `include:nativeretweets`. Words, quoted phrases, hashtags and mentions stay
  in "All of these words". An operator also stays there as typed when it is
  quoted or inside a group, stands next to `OR`/`AND`/`NOT`, is negated in a
  way the form has no control for, carries a value the form would rewrite
  (`min_faves:0`, `since:2026-02-30`, `from:@alice`), or shares its control
  with another operator of the query (`lang:fr lang:en`,
  `filter:images filter:videos`). Unbalanced quotes or brackets keep the whole
  query as typed.
- A committed query with readable operators shows the same chips and filter
  count the form would; a query of plain words shows none, as before. The text
  sent to X is the text typed; only applying the form or removing a chip
  rebuilds it, in the order above.
- Use flutter_triple state and wrapping chips suitable for narrow screens and
  enlarged text. Label `search_links` means links contained in posts; the new
  labels are translated in every supported locale.

No X write actions, client changes, database changes, or dependency changes.
Search results remain subject to the current X search endpoint and account
availability; the feature sends query operators through the existing reader.

## Operator evidence

The official X [search migration operator table](https://docs.x.com/x-api/posts/search/migrate/overview)
documents the legacy `filter:media`, `filter:images`, `filter:videos`,
`filter:links`, `filter:retweets`, and `filter:replies` syntax. X's
[advanced X Pro guide](https://help.x.com/en/using-x/advanced-postdeck-features)
documents excluding types with `-filter:type`. Existing group feeds already
use the same reply and retweet exclusions and `include:nativeretweets`, and
X's own advanced search writes `lang:xx`. Checked 2026-09-25; do not
substitute API v2 `has:` / `is:` operators into this legacy search client.

## Acceptance checks

- Existing advanced-search queries and only-media callers produce the same text.
- Every content, reply and link choice emits exactly one matching operator, and
  the defaults emit none.
- Switching, clearing, and resetting filters preserves unrelated fields and
  never retains a stale operator.
- Result-store query and active filter chips stay synchronized after clearing,
  for queries built by the form and for typed ones.
- A generated query reads back to the same filters and rebuilds to the same
  text; a typed query in another order is rebuilt once into the fixed order and
  then stays. Quoted operator literals, grouped expressions and unsupported or
  malformed text remain untouched.
- The form applies selected filters, discards canceled changes, and has no layout
  overflow on a narrow screen at large text scale.
