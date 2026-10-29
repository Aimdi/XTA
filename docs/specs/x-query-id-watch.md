# Watching X's query ids

The owner asked for XTA to catch X changing its request ids before readers
hit it. Checks from CI on 2026-10-29 shaped this spec. Nothing is sent to
X but the reads XTA already makes.

## What the checks found

- Every X read names its operation by a query id
  (`/i/api/graphql/<id>/TweetDetail`). X changes most of them with every
  web release, every 3 to 6 days in October 2026, and keeps old ones
  answering for months: ids X's web dropped before June 21 still answered
  on October 29.
- Installs fetch `endpoints.json` once a day and let its ids replace the
  shipped ones, so a rotated id can be repaired without a release. They
  fetch it from the `main` branch, which has not changed since August 28,
  while every release merges into `claude/main`, the default branch. Its
  entries put seven operations back on ids X's web dropped before June 21,
  over the newer ones each release ships: threads (`TweetDetail`), search,
  groups and Following (`SearchTimeline`), For you (`HomeTimeline`), a
  profile's replies, profile lookup, and following and followers lists. A
  correction published in this repository never reaches an install.
- The daily check applies this repository's copy of `endpoints.json`, not
  the one installs fetch, so it judges ids that no install uses.
- It can judge only the five operations a guest may call. For the others X
  answers a guest 404 whatever the id.
- X's logged-out web is now a different app, built on Relay, whose scripts
  do not name these operations, so the current ids cannot be read from
  x.com without an account.
- fa0311/TwitterInternalAPIDocument publishes the web's operations with
  their current ids every day (`docs/json/API.json`, about 930 operations)
  and keeps every version in its history.

## After

### Installs read what this repository publishes

- Builds read `endpoints.json` and `plugins.json` from the repository's
  default branch (`HEAD`), so a correction published there reaches them.
- Installs up to aimdi186 keep reading `main`. Until they have updated, a
  correction is copied to `main` as well; the check below says when the two
  differ.

### The guest check judges what installs apply

- `endpoint_canary_test.dart` applies the files installs fetch, the default
  branch's and `main`'s, over the shipped ids, and judges every distinct id
  in force. A guest-readable id that no longer exists fails it, as before.
- Guest-readable ids in force are as old as the oldest others (they came
  from the same releases), so a purge of old ids shows there too.

### A daily report of every id's age

A new live check, "X query ids" (`test/live/x_query_ids_test.dart`, its own
row in the daily checks), lists for every operation:

- the id builds of this repository use, and the one installs up to aimdi186
  use;
- whether X's web uses it today, or how many days ago it stopped, from the
  record's history (newest first, at most 40 versions, read until every id
  is found);
- X's current id.

It fails, and so opens an issue, when:

- a published `endpoints.json` that installs read cannot be read; or
- `main` publishes ids that differ from the default branch's, because
  installs up to aimdi186 would apply them.

Ids X's web dropped more than 90 days ago are marked old. That alone does
not fail the check: X still answers ids older than that, and moving one
needs a check with an account (below). When the record cannot be read, the
report says so and judges only the published files.

The record is only read. Nothing from it reaches an install unless someone
publishes it.

### No id moves blindly

No shipped id changes here. The shipped ones answer, and a new id can come
with other fields and switches, which cannot be checked without an account.
Moving to the current ids is a separate step, checked on a device or
against recorded answers.

## Code

- `lib/constants.dart`: `githubPublishBranch` is `HEAD`.
- `test/live/support/x_query_id_record.dart` (new, pure): the record's ids
  by operation, the ids in force from shipped ids and a published file, and
  each id's age in the record's history.
- `test/live/x_query_ids_test.dart` (new): the daily report.
- `test/live/endpoint_canary_test.dart`: applies the published files
  installs fetch.
- `.github/workflows/api-canary.yml`: the new row; the GitHub token for the
  record's history.

## Tests

- The record's ids are read from its JSON; entries without an id are left
  out.
- The ids in force: an override replaces a shipped id; an unknown
  operation or a malformed id is ignored, as the app does.
- An id's age: today when the newest version has it, days since the newest
  version that has it otherwise, unknown when no version read has it.
- The published files must match: a `main` that differs is named.
