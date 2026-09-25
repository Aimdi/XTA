# XTA 135, current XTA and QuaX: X timeline investigation

Investigated 2026-09-25 against these exact sources:

- XTA `aimdi135`: `9914ba23e21f707dcad41ec064769ca069e593a7`.
- XTA `aimdi136`: `1d1ca2100979e888153858c3c0f42d9138ba2cbf`.
- Latest published XTA `aimdi139`: `d864079722d2a1a50cdf07db09a84a492155fdc2`.
- [QuaX master](https://github.com/Teskann/QuaX/tree/8db43304983ae3e59b34c273102dc34691cc20ce):
  `8db43304983ae3e59b34c273102dc34691cc20ce`.

## Confirmed failures

**136 introduced a recovery regression.** `TwitterHeaders._deriveTransaction`
wrapped connection failures in `TransactionIdUnavailableException`, while
`recoverableReadFailure` only accepts connection, timeout and server failures.
This could leave an empty X feed after a temporary setup failure. Version 139
already preserves transient error types and removes their setup cooldown.
That earlier correction is distinct from the remaining discovery failure below.

**139 still cannot find the signer in the current public X page.** On the
captured page, X serves this dependency chain:

1. `https://abs.twimg.com/x-web/x-web/entry-client-logged-out-DjIH8Of-.js`
2. `https://abs.twimg.com/x-web/x-web/assets/sentry-filter-DKIzveDR.js`
3. `https://abs.twimg.com/x-web/x-web/assets/sign.o-C5ulstet.js`

The entry statically imports the shared chunk; that chunk dynamically imports
the signer with a constant, backtick-quoted filename. Published 139 only scans
HTML-linked bundles for a direct signer reference and only recognizes single
and double quotes. It neither follows the intermediate module nor recognizes
the final backtick import. Its search fallback can receive the same shell.

All authenticated X reads await this initialization in `getHeaders`. When it
fails, the HomeTimeline/SearchTimeline request is never sent. This explains how
a client initialization failure can prevent posts reaching the timeline at all.
It is a reproduced defect, not proof of the precise failure on the user's phone:
the installed Aimdi version and device error report were not available.

## What the three implementations actually differ on

| Area | XTA 135 | XTA 136–138 | XTA 139 | QuaX at compared commit |
| --- | --- | --- | --- | --- |
| Connection setup error | Original type | Wrapped as signing failure | Original transient type restored | Original type |
| Signing discovery | Legacy `ondemand.s` | Legacy; search fallback in 138 | Legacy plus direct modern signer references | Legacy `ondemand.s` |
| Nested module/backtick signer | Unsupported | Unsupported | Unsupported before this patch | Unsupported |
| Signing cache | Shared, expires after six hours | Shared, expires after six hours | Isolated per cookie context, six-hour lifetime | One process-wide future, no expiry |
| HomeTimeline ID | `7zlnp2TxC044W4C1ZUJMHw` | Same | Same | `wp06oo3fRGU4P1sK8rECqQ` |
| SearchTimeline ID | `Yw6L66Pw54NHKuq4Dp7b4Q` | Same | Same | `hyPfJYJ_XAtDYoslQc-Rgg` |

XTA's `client.dart`, `endpoints.dart`, `transport.dart` and `_for_you.dart` have
no changes between 135 and 139. Pinned dependencies are also unchanged. QuaX
uses a broader shared timeline feature map; XTA HomeTimeline still uses its
older, smaller map. The different query IDs and features deserve an authenticated
comparison if requests still fail after initialization, but no live signed-in
response established an endpoint failure in this investigation. They were not
changed speculatively.

Replaying the same captured public shell against a fresh XTA 135 initializer
and QuaX initializer also fails with `Couldn't find ondemand file index`.
Therefore QuaX working on one phone cannot establish that its cold-start path
handles this page. A cached key or a different server response is a possible
explanation, not a verified diagnosis of that device.

## Correction and evidence

`SigningAssets` now follows trusted static and dynamic module imports and accepts
constant backtick literals. It prioritizes actual imports over preload lookup
tables, detects cycles and retains the original 12-second initialization deadline,
four-request concurrency and sixteen-asset ceiling, including one reserved read
for the signer. Import traversal is limited to three levels. Cookies are still
excluded from CDN reads, redirects are not followed, and downloaded JavaScript
is never evaluated.

Validation:

- A reduced fixture matching the observed import structure fails on published
  139 with `X pages did not contain transaction signing data`, then passes with
  the correction.
- A replay of the four captured public files also fails on 139 and derives a
  70-byte transaction ID with the patch. This was an offline replay; unrelated,
  uncaptured assets deliberately returned fixture 404s.
- The integration test exercises the real account-specific HomeTimeline path,
  header construction, parser, merged-home loader and `TweetFeedController`. It
  loads both posts from the existing response fixture without substituting a
  precomputed transaction key. Credentials and API responses are test fixtures.
- 108 tests pass across modern/legacy bootstrap, transaction cache, transport,
  home account filtering and paging recovery. Focused analysis reports no issues.
- Direct live Dart tests before and after the patch time out during X page
  redirects. A traced run received `/home` HTTP 307 after about 8.7 seconds and
  exhausted the remaining initialization budget on `/i/jf/onboarding/web`.
  This prevents a claim of successful live authenticated timeline access.

Re-run the checked-in verification with:

```sh
fvm flutter test --no-pub \
  test/client_transaction_modern_bootstrap_test.dart \
  test/client_transaction_bootstrap_test.dart \
  test/transaction_key_test.dart \
  test/x_request_recovery_test.dart \
  test/home_account_filter_test.dart \
  test/paging_recovery_test.dart
```

The captured public files were 17,069, 23,916, 544,741 and 31,548 bytes, respectively
(page, entry, shared chunk, signer). Their SHA-256 hashes are recorded below to
identify the observation without committing third-party production bundles:

| Capture | SHA-256 |
| --- | --- |
| Page | `6ea08417a852cee75cb9668fd18938cf344d6e65b667ff1db2497da0a769c606` |
| Entry | `1ffef8a6c1912b8c7a9b60a24bb18da78239b7cb5ea038dc256661bec56636cf` |
| Shared chunk | `5560ac092a82b48b0020088a4d913d2b42b5c91f87a3f5681f287f6624c5da83` |
| Signer | `3de678b0c2198da90d6eb8e1321ee62604418c8e89b465e9d4273a2a093af3db` |
