# Following feed: parallel searches scale with accounts

## Problem

A Following feed is built from X searches of 16 followed users each. Three run
at a time (`feedChunkFetchConcurrency`), whatever the number of accounts. The
limit came after opening every search at once cascaded into 404s and flagged
accounts (#165 / #170). With several accounts that cap leaves capacity unused:
a feed of 500 subscriptions (about 32 searches) loads in about 11 waves of
three.

## Behaviour

In-flight searches = twice the usable accounts, at least 3 and at most 6:

| Usable accounts | In flight |
|-----------------|-----------|
| none (guest) or 1 | 3 (as before) |
| 2 | 4 |
| 3 or more | 6 |

- A usable account is on for home feeds (`AccountFetchGate`) and its sign-in
  is not flagged broken (`isNotFoundFlagged`). When every account is off for
  home feeds, the transport falls back to all of them, so they are counted.
- Each account carries about two searches at once at most, close to what one
  account carried before. The cap of 6 keeps a large account list from
  recreating the burst that #165 / #170 fixed.
- The count is taken once per feed load, so it never changes mid-load.

## Code

- `lib/group/feed_chunk_concurrency.dart`: the rule as pure functions.
- `lib/group/_feed.dart`: uses it for the chunk batch.
- `lib/client/` is unchanged; account selection stays as it is.

## Verification

Unit tests for the rule: guest, one account, two, many, disabled and flagged
accounts, and the all-disabled fallback.

## Follow-up (aimdi177)

- An account currently rate-limited on `SearchTimeline` no longer counts as
  usable, so a load with one working account and two limited ones stays at
  three searches instead of six on that one account.
- With the selector now spreading requests in flight, each usable account
  carries about two searches at once.
