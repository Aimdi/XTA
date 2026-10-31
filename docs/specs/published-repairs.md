# Published repairs: fixing networks without a release

The owner asked for the networks' fragile values to be published, so a
change on a service can be repaired for every install without an update
(2026-10-29). X already has this for its request ids (`endpoints.json`,
docs/specs/x-query-id-watch.md); this does the same for the other
networks, from a survey of every plugin's hard-coded values.

## What can be repaired, and what cannot

A published value can only replace a value of the same kind in the same
request to the same host:

- **ids and numbers** (`digits`): request document ids, app ids;
- **tokens** (`token`, letters, digits, `_` and `-`): request names, app
  client ids;
- **versions** (`version`, numbers with dots): app versions;
- **header text** (`header`, printable ASCII on one line): user agents.

Nothing that could send a request, a cookie or a token somewhere else is
repairable: no hosts, no addresses, no paths. No pattern or selector is
either, for now: a pattern can hang the app on a page built for it.
Those still need a release, as do changes to what the code reads out of an
answer.

## Before

- A rotated Threads or Instagram request id, an app id or version a
  service stops accepting, or a user agent a site starts refusing, each
  needed a release, and every install stayed broken until it updated.

## After

- `repairs.json`, next to `endpoints.json`, is read from the repository's
  default branch at most once a day, kept, and applied at startup before
  any request, as `endpoints.json` is. A value is used only when its key is
  one this build knows and it fits its kind; anything else changes nothing.
- Each entry names the value this repository ships, as for
  `endpoints.json`: a repair changes both, so the file can bring an older
  install up to date and never hold a newer build back. A unit test checks
  it.
- Diagnostics' shared report names how many repairs are in force and when
  the file was last read.

## The values (v1)

From the survey, those that have rotated or that a service checks:

| Key | Kind | Used for |
|---|---|---|
| `threads.guest_doc_id` | digits | the logged-out profile posts request (`doc_id`), which rotates |
| `threads.guest_query_name` | token | its `X-FB-Friendly-Name`, paired with the id |
| `threads.app_id` | digits | `X-IG-App-ID` on every Threads request |
| `threads.asbd_id` | digits | `X-ASBD-ID` |
| `threads.app_user_agent` | header | the Threads app's user agent (signed-in For You) |
| `threads.web_user_agent` | header | the browser user agent (pages, guest requests) |
| `instagram.app_id` | digits | `X-IG-App-ID` on every Instagram request |
| `instagram.user_agent` | header | Instagram's browser user agent |
| `tiktok.aid` | digits | `aid` on every TikTok request |
| `tiktok.user_agent` | header | TikTok's browser user agent, also its player's |
| `pixiv.app_version` | version | `App-Version`, on every Pixiv request and the login |
| `pixiv.user_agent` | header | the Pixiv app's user agent, the same two places |
| `pixiv.client_id` | token | the Pixiv app's OAuth client id |
| `pixiv.client_secret` | token | the Pixiv app's OAuth client secret (the public one every community client ships, not a reader's) |
| `reddit.web_user_agent` | header | the browser user agent Reddit's website requires |
| `stocks.user_agent` | header | the quote host's user agent |

## Code

- `lib/plugins/plugin_repairs.dart` (new): the slots, their kinds and
  checks, the values in force, and the file's reader.
- `lib/utils/published_documents.dart`: a published document fetched at
  most daily and kept in preferences (the repairs file is the first user;
  `lib/client/endpoint_overrides.dart` is frozen and stays as it is).
- Each network reads its value from its slot where it used a constant.
- `lib/main.dart`: applies the kept file at startup and reads it again.
- `repairs.json` (new), `lib/constants.dart` (its address and keys).
- `lib/settings/diagnostics_*`: the report's line.

## Tests

- Each kind takes what it should and refuses the rest (a host, a path, a
  line break in a header, an empty value).
- A published file: known keys that fit are used; unknown keys and values
  that do not fit change nothing; a file that does not read changes
  nothing.
- The kept file is applied at startup; the file is read again at most once
  a day.
- `repairs.json` names only known keys, each with the value this build
  ships.
- Each network sends the value in force (one request per network).
- A daily live check reads the default branch's `repairs.json` and fails
  when it cannot be read or holds an entry this build would refuse.
