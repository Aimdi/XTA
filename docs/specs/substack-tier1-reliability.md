# Substack Tier 1 reliability

Base: `eaf5f25`. This batch implements the Tier 1 queue from `docs/substack/audit.md` §9.

These Tier 1 items are left out, each for a stated reason:

| Bug | Waiting on |
|---|---|
| BUG-01 (deep replies) | a decision |
| BUG-12 (text selection) | a decision; it changes the long-press interaction |
| BUG-07, BUG-08, BUG-10 | recorded responses (a HAR) |
| BUG-02 (one identity across API and RSS) | the next batch; it migrates stored read, like and save state |

## Changes

### 1. One transport for every Substack request (BUG-03)

New file `lib/plugins/substack/substack_transport.dart`. Every request the client makes goes through it, on the one `http.Client` the plugin already shares.

- **Rate buckets.** `substack.com` and every `*.substack.com` host share one bucket. Any other host is its own bucket; `www.` is ignored when naming it.
- **Concurrency.** At most 4 requests are in flight per bucket.
- **429.** A 429, or a 503 that carries `Retry-After`, blocks the whole bucket for the wait it asks for:
  - `Retry-After` in seconds or as an HTTP date, if present;
  - otherwise an exponential delay with jitter: 1 s doubling per consecutive rate limit, between half and all of that, capped at 5 min.

  A request whose wait would exceed 10 s fails at once with `SubstackRateLimitedException` instead of waiting. A request retries at most twice. A successful response clears the bucket's strike count.
- **5xx.** A 500, 502, 503 or 504 without `Retry-After` is retried once, after 0.5–1 s.
- **No cascade.** A rate limit stops the client's fallback chains. After one, there are no archive, Beehiiv, RSS, `www` or twin requests; the rate limit is what the caller sees.
- **Honest message.** A rate-limited Substack screen shows the existing translated label "The host is rate limiting — try again later". It no longer shows the raw exception or "This is not a Substack publication". No new strings.

### 2. A refresh reads every followed publication (BUG-05)

Refresh fetches the first page of every followed publication, still three at a time. Load-more passes stay bounded to 24 publications and keep rotating failed publications, so older pages are not starved.

### 3. Twin hosts on read paths (BUG-04)

For a followed custom domain, posts, post bodies, comments and archive search use `{stored subdomain}.substack.com`. The guess `{first host label}.substack.com` is used only when the stored subdomain is unknown (`www` or empty). That is the legacy follow that needs it.

A twin listing is rejected if its publication names a different custom domain, or a different subdomain from the one stored. Resolving a pasted URL keeps its probing, because the user previews the result before following.

### 4. A paid post without a body (BUG-11)

The reader shows the paid pane: lock, excerpt and an "Open in browser" button. It no longer loads the publisher's live page. A pure `substackReaderBodyFor(post)` decides between the article, the paid pane, the live page and empty.

### 5. Small fixes

- **BUG-09:** the article cache keeps the video flag.
- **BUG-06:** a follow resolved without Substack's JSON on a host deeper than an apex domain (`newsletter.example.com`) keeps the whole host as its id. Apex domains keep their first label, so `garbageday.email` stays `garbageday`.
- **BUG-14:** speech skips footnote markers.
- **BUG-15:** Beehiiv JSON-LD fields are read through `Json`, so an unexpected shape falls through to the next parser instead of throwing.

## Tests

- **Flipped probes.** The probes for BUG-03, 04, 05, 06 and 09 in `test/substack_audit_probes_test.dart` flip into regression tests.
- **Transport unit tests**, with a fake clock:
  - bucket sharing;
  - `Retry-After` in seconds and as a date;
  - jitter bounds;
  - the fail-fast cap;
  - the 5xx retry;
  - the concurrency cap.
- **Client and screen tests:**
  - client cascade-stop tests;
  - reader-body decision tests;
  - the speech footnote test;
  - the JSON-LD shape test.
- **Updated test.** `substack_store_recovery_test.dart` pinned the 24-publication refresh pass. It is updated on purpose: refresh covers all sources, and paging still rotates.

## Measurement

Host VM: the requests one 30-publication refresh makes under a blanket 429, before and after the change. Device S4 stays UNVERIFIED until a phone run.

No dependency, schema, frozen-client or localization changes.
