# Stocks: explicit crypto identities

Base: Aimdi129, e6551526. Scope: lib/plugins/stocks, focused tests, ARB translations.

## Problem and behavior

The watchlist identifies everything by an uppercased ticker. Different crypto
projects with one symbol therefore overwrite each other; searching a contract
fails validation. Preserve the existing stock/cashtag flow and add a crypto
search mode backed by DEX Screener's documented public search and token-pairs
endpoints (https://docs.dexscreener.com/api/reference, checked 2026-09-09).

A crypto result must show the project name, network, and contract. Select a
result explicitly, and persist a key composed of network and contract. Preserve
case for non-EVM addresses. Deduplicate liquidity pairs of the same token but
never distinct contracts with the same ticker. Price requests must remain bound
to the selected network and base-token contract. Quotes and token details show
prices small enough for low-price tokens, and expose the full copyable contract.

Use the existing stock_subscription id/symbol fields to round-trip versioned
crypto metadata through existing backup handling; no database schema change.
Keep ordinary stored ticker rows readable. Duplicate adds preserve feed settings
and group memberships. X post searches for a token use its literal contract,
not its ambiguous ticker; the UI explains this scope. Stock feeds remain cashtag
searches. DEX Screener availability and indexed tokens limit search coverage;
errors have retry and an empty result is distinct from a failure. No trading.

## Implementation boundaries

Use flutter_triple Store for search/quote state, including request generations to
ignore responses after query changes or disposal. No new pinned dependencies,
no lib/client, lib/database, generated file, or settings changes. Translate new
labels across locales and run l10n.py. Existing Yahoo stock support stays intact.

## Verification

Focused tests cover same-symbol different-network/address identities, EVM versus
case-sensitive addresses, storage round trips, malformed API payloads, duplicate
pair selection, strict contract quote matching, exact contract search syntax,
stale search responses, and tiny token prices. Run targeted Flutter tests and
analysis if the pinned toolchain is available; disclose any environment block.

## Implemented validation status

- Added 12 focused behavior tests in stocks_crypto_test.dart and
  stocks_crypto_storage_test.dart; ready for the pinned CI toolchain.
- Python parsed all ARBs and verified all six new labels in all 29 locales.
  The repository l10n.py missing-key check reports all locales complete.
- git diff --check passes. The full l10n.py sorter and Flutter tests cannot
  run locally because this workspace has no fvm/Flutter SDK; root integration
  will use the existing pinned GitHub Actions gates. No SDK version changed.
- DEX Screener search covers its indexed on-chain markets. Native assets remain
  available through the existing stock/finance search. X contract searches may
  miss posts containing only a ticker and can include another chain's deployment
  with the same literal address; the token quote and watchlist identity remain
  explicitly network-bound. Live service/device validation is still required.
