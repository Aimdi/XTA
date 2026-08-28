# API response fixtures

Captured live X GraphQL JSON for parser characterization tests. Prefer real
responses over hand-written stubs.

## Present

| Path | Source | Used by |
|---|---|---|
| `UserByScreenName/ok.json` | Guest `UserByScreenName` for `@X` | `UserWithExtra.fromNonLegacyJson` |
| `TweetDetail/tweet_result.json` | Tweet node from guest `UserTweets` | `TweetWithCard.fromGraphqlJson` |
| `UserTweets/add_entries.json` | Slim `TimelineAddEntries` from guest `UserTweets` | `Twitter.createTweetChains` |
| `Retweeters/ok.json` | Hand-shaped `Retweeters` timeline (`user_results` + cursor) | `TimelineParser.retweetersInstructions` / `parseUsersTimeline` |

Guest `TweetDetail` returned 404; tweet shapes were taken from `UserTweets`
instead. No auth tokens are stored in these files.

## Two test layers

`client_parser_test.dart` asserts the parsers read **today's** shapes — the
fixtures as captured.

`parser_resilience_test.dart` asserts they survive **tomorrow's**. It replays
each fixture with one field removed or nulled at a time, which approximates the
renames and drops X actually ships far better than a hand-written stub can.
Per `.claude/skills/parse-api`, a missing field must yield null or a documented
default; a throw in a parser empties a whole timeline.

That layer found three live crashes when it was added:

- `UserWithExtra.fromNonLegacyJson` threw on a response without `legacy` —
  the migration X is currently rolling out — and `_getProfile` calls it
  unguarded, so every profile view would have broken at once.
- `createTweetChains` threw on an entry whose `tweet_results` or `content` had
  moved, losing the entire page rather than the one entry.

## Rules

- Redact auth tokens and personal secrets before committing.
- When a parser change breaks a fixture test, treat it as a compatibility
  signal: either X changed shape, or the parser regressed.
- The fixtures predate the `legacy` → `core` / `avatar` migration. Tests that
  need the new shape reconstruct it from the captured one rather than asserting
  against fields the capture never had.
- Optional next captures: authenticated `TweetDetail`, `SearchTimeline`,
  `HomeTimeline`, plus unavailable/tombstone shapes.
