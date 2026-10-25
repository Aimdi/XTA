# Fixing X request signing

X refuses a GraphQL request whose `x-client-transaction-id` header it does not
accept, and answers it with 404, so a broken signer looks like a rotated query
id: every X read fails at once. XTA derives the header itself from the pages
and scripts x.com serves, and X reshapes those every few weeks. This is the
playbook for repairing the derivation, by hand or through the
[auto-fix workflow](#the-auto-fix-workflow).

## How XTA signs

All of it lives in `lib/client/x_client_transaction_id/`, a Dart port of
[XClientTransaction](https://github.com/iSarabjitDhiman/XClientTransaction).

| Step | What happens | Where |
|---|---|---|
| 1. Bootstrap page | Fetches `x.com/home`, then `/search?q=AI&f=live`, then `/i/flow/login` (without the session), and keeps the first page that has a `twitter-site-verification` key and at least four `loading-x-anim` frames. | `_fetchBootstrapPage`, `_hasBootstrapData` in `client_transaction.dart` |
| 2. Find the signer | A script tag naming `ondemand.s.<hash>a.js`, else the runtime's chunk map, else a search of the page's scripts, module preloads and inline entry code, following module imports, importers first (`startup-dynamic`, `client-transaction-id-plugin`), to a `sign.o` or `ondemand.s` bundle. At most 16 assets, 4 at a time, 3 imports deep, only from `abs.twimg.com`, never evaluating JavaScript. | `_getOndemandFileUrl` and `constants.dart`; `SigningAssets` in `signing_assets.dart` |
| 3. Indices | The signer's `(a[N], 16)` calls: the first `N` is the frame row, the rest are key-byte indices. | `_getIndices`, `indicesRegex` in `constants.dart` |
| 4. Key bytes | The base64 `content` of `<meta name="twitter-site-verification">`. | `_getKey`, `_getKeyBytes` |
| 5. Animation key | Frame `keyBytes[5] % 4`, the `d` of its second path cut into rows at `C`, row `keyBytes[row] % 16`, a time from the key-byte indices, then a cubic curve turned into a colour and a rotation, written in hex. | `_computeAnimationKey`, `_get2dArray`, `_animate`, `cubic_curve.dart`, `interpolate.dart`, `rotation.dart`, `utils.dart` |
| 6. Header | SHA-256 of `GET!<path>!<time><keyword><animation key>`, laid out after the key bytes and the time, masked with a random byte, base64 without padding. | `generateTransactionId`; the keyword and extra number in `constants.dart` |

`TwitterHeaders` (`lib/client/headers.dart`) keeps one derived signer per
session for `transactionKeyLifetime`, waits `transactionKeyRetryCooldown`
after a failure, and reports a lasting one as
`TransactionIdUnavailableException`.

## Find what broke

The live check derives the header the way the app does, then asks for @X's
profile with it:

```bash
flutter test test/live/x_bootstrap_smoke_test.dart --dart-define=RUN_LIVE=true
```

x.com is not reachable from every network, and since October 2026 it answers
some datacenter addresses with Cloudflare's check on `/home` and `/search`;
GitHub's runners still get the sign-in page. Read the failure against the
steps:

| Failure | Step |
|---|---|
| `X pages did not contain transaction signing data` | 1 or 2: no page had the key and frames, or no signer was found. |
| `X transaction signing asset limit exceeded` | 2: the signer sits behind more assets than the search allows, or the importer was renamed. |
| `X transaction bootstrap returned HTTP …`, `redirect was refused` | 1: the pages themselves. |
| `Couldn't get KEY_BYTE indices` | 3: the signer no longer has the calls `indicesRegex` reads. |
| A `RangeError` or `FormatException` while deriving | 4 or 5: the key or the frames changed shape. |
| A header was derived, and the profile answered 404 | 3, 5 or 6: X does not accept the id, so something the derivation reads, or the algorithm, changed. |

A 404 whose body names the query (`Query not found`) is a rotated query id,
not signing: that is repaired in `lib/client/endpoints.dart` or the published
`endpoints.json`, and is not this playbook's job. Neither is a 429.

Look at what X serves, with the headers the app sends, and keep the files
outside the repository (`$RUNNER_TEMP` on a runner, any scratch folder
elsewhere):

```bash
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36'
curl -sS --compressed -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' -H 'Referer: https://x.com' -o "$RUNNER_TEMP/x-signing/home.html" https://x.com/home
curl -sS --compressed -A "$UA" -H 'Accept-Language: en-US,en;q=0.9' -H 'Referer: https://x.com' -o "$RUNNER_TEMP/x-signing/login.html" https://x.com/i/flow/login
curl -sS --compressed -o "$RUNNER_TEMP/x-signing/entry.js" https://abs.twimg.com/<path the page names>.js
```

Then follow the chain the way step 2 does: the page's `script[src]`,
`link[rel=modulepreload]` and inline `entryHref`, then each bundle's
`import`/`from` strings, until a `sign.o-…js` or `ondemand.s.…js`. Search the
files rather than reading them: the bundles are minified onto a few long lines.

## Fix and test

- Change only the step that broke. Keep the host checks
  (`SigningAssets.trusted`, `_isSigningBundle`), the asset budget and the rule
  that downloaded JavaScript is never evaluated. X keeps serving older bundles
  beside new ones, so keep the routes the existing tests cover working.
- Add a regression test beside the existing ones:
  `test/client_transaction_bootstrap_test.dart` covers the pages and the
  `ondemand.s` route, `test/client_transaction_modern_bootstrap_test.dart`
  the `sign.o` search through entry scripts and imports, and
  `test/transaction_key_test.dart` the caching in `TwitterHeaders`. They serve
  X through `xHttpClient = MockClient(...)` with pages written in a few lines
  that keep only what the derivation reads: the tags, the file names, the
  import chain. Write the new shape that way, with a comment saying where and
  when it was observed, as in `"As recorded from x.com/home on 2026-10-13"`.
- When the shape is too intricate to write by hand, save a trimmed copy under
  `test/fixtures/XSigning/` (only the elements the derivation reads, no
  cookies, nonces, tokens or tracking ids), list it in
  `test/fixtures/README.md`, and read it from the test with `File(...)` as the
  community tests do.
- When step 5 or 6 changed, check the new derivation against an id a browser
  sent for the same page, or against the reference implementation.

Do not bump or add dependencies, touch `lib/database/`, or change unrelated
client code (query ids, parsers, account rotation); see the hard rules in
`CLAUDE.md`. Never log or commit a cookie, a token or a bearer header.

## Verify

```bash
flutter analyze --no-fatal-infos
flutter test test/client_transaction_bootstrap_test.dart test/client_transaction_modern_bootstrap_test.dart test/transaction_key_test.dart
flutter test test/live/x_bootstrap_smoke_test.dart --dart-define=RUN_LIVE=true
```

Locally, run them through `fvm`. A pull request that touches the signing code
also runs the `X bootstrap check` workflow, which, when the live check fails,
prints what each public route served.

## The auto-fix workflow

`.github/workflows/x-signing-autofix.yml` runs after the daily `api-canary`
fails on the default branch, or by hand:

1. It stops unless a Claude credential is set, the canary's X job did not
   pass, no fix pull request is open or was opened in the last 24 hours, and
   the live check above fails in a way that reached X (a network failure on
   the runner is not a reason to change code).
2. Claude follows this playbook with a read-only token. It may change only
   `lib/client/`, `test/` and `docs/`, and neither commits nor pushes.
3. The workflow runs the three verification commands on the change. Only when
   all pass does it commit the change to a new `fix/x-signing-<run id>`
   branch, open it as a draft pull request against the branch it ran on, and
   start `verify` and `X bootstrap check` on it. Otherwise the run fails and
   its summary shows the diff and the failure.

A person reviews the draft, marks it ready and merges it; the workflow never
does. What the repository owner sets up for it is in `docs/cloud-testing.md`.
