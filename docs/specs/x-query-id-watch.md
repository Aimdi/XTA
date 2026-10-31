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
- Installs save the default address with their settings on first launch,
  so changing the default alone moves no install. At startup, an install
  that saved the `main` (or the older `master`) address is moved to the new
  one and forgets the ids it read from `main`: the shipped ids apply until
  the default branch's file is read, on that launch. An address set by hand
  stays.
- An entry in `endpoints.json` applies to every install that reads it,
  newer builds too. So each entry names the id this repository ships for
  that operation, and a correction changes both together: the file can
  bring an older install up to the current ids, never hold a newer build
  back on an older one, as `main`'s file did. A unit test checks it.
- Installs up to aimdi186 keep reading `main`. Until they have updated, a
  correction is copied to `main` as well; the check below says when the two
  differ. Deleting `main` would not help them: an install keeps applying
  the last file it fetched until it fetches another.

### The guest check judges what installs apply

- `endpoint_canary_test.dart` applies the files installs fetch, the default
  branch's and `main`'s, over the shipped ids, and judges every distinct id
  in force: the shipped ones, and those of each published file and of this
  checkout's file (published once merged). A guest-readable id that no
  longer exists fails it, as before, naming who uses it. A published file
  that cannot be read is named and not judged; the report below fails on
  it.
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
- `lib/utils/published_documents.dart` (new): moves a saved earlier default
  address to today's; run in `lib/main.dart` with the other settings
  migrations, before the cached ids are applied.
- `test/live/support/x_query_id_record.dart` (new, pure): the record's ids
  by operation, the ids in force from shipped ids and a published file, who
  applies each id, and each id's age in the record's history.
- `test/live/support/published_registries.dart` (new): where each kind of
  install reads `endpoints.json`, read as they read it.
- `test/live/x_query_ids_test.dart` (new): the daily report.
- `test/live/endpoint_canary_test.dart`: applies the published files
  installs fetch.
- `test/endpoints_test.dart`: each entry of `endpoints.json` is the id
  this repository ships.
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
- Each entry of `endpoints.json` is the id this repository ships.
- A saved `main` or `master` address moves to the default branch's and
  drops what was read from it; an address set by hand, or one already on
  the default branch, stays with what was read from it. The plugin
  catalogue's address moves the same way.
