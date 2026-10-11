# X requests: least-used account first

Approved by the repository owner as an exception to the `lib/client/` freeze.

## Problem

`AccountSelector.pick` chooses at random among accounts not rate-limited on
the endpoint. An account with 2 requests left in the window is as likely to be
picked as one with 140. So one account reaches zero while others sit idle,
and a request that lands on it near the limit is refused and retried on
another account, costing a round trip.

## Behaviour

- Among the accounts a request may use (healthy first, as before), prefer the
  one with the most requests left on that endpoint, per `RateLimitTracker`'s
  quota. X reports the quota with every answer, and XTA counts it down as
  requests are sent.
- An account whose quota is not known yet, because it has not served that
  endpoint in the current window, counts as fresh and is preferred. That
  spreads first use over all accounts.
- Ties are broken at random, so equal accounts still share the load.
- Nothing else changes:
  - flagged accounts remain a fallback;
  - a 429 still retries on another account;
  - errors surface as before;
  - one-shot callers without an endpoint (`pickAuthHeader`) stay random.

## Code

- `RateLimitTracker.remaining(accountId, endpoint, now)`: the known quota, or
  null.
- `AccountSelector`: an optional injected `remainingQuota`, kept pure and
  clock-injected.
- `QuackerTwitterClient._fetch`: passes the tracker's quota for the request's
  endpoint.

## Verification

Selector unit tests:

- the most quota wins;
- unknown beats known;
- ties spread;
- healthy still beats flagged;
- `exclude` still applies.

Tracker unit test for `remaining`.
