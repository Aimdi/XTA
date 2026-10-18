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

A twin listing is rejected if its publication names a different custom domain, or a different subdomain from the one stored. Resolving a pasted URL keeps its probing, because the user previews the result before following; a twin that names another custom domain is rejected there too. §6 narrows the guess to apex domains. A publication that moved to a new custom domain while its old one went dark is no longer read through its twin: showing nothing is safer than showing someone else's posts, and following the new domain fixes it.

### 4. A paid post without a body (BUG-11)

The reader shows the paid pane: lock, excerpt and an "Open in browser" button. It no longer loads the publisher's live page. A pure `substackReaderBodyFor(post)` decides between the article, the paid pane, the live page and empty.

### 5. Small fixes

- **BUG-09:** the article cache keeps the video flag.
- **BUG-06:** replaced by §6, which found the same overwrite on a second path.
- **BUG-14:** speech skips footnote markers.
- **BUG-15:** Beehiiv JSON-LD fields are read through `Json`, so an unexpected shape falls through to the next parser instead of throwing.

### 6. Follow identity (BUG-16, BUG-06)

A publication built from a post (`post.publication`, `publicationForPost`) has no subdomain of its own, so it takes the first label of its host. That label is often enough someone else's subdomain. A page opened from a `newsletter.pragmaticengineer.com` post counts as followed when `newsletter.substack.com` is, and opening it rewrites that follow to the other publication (BUG-16). Following a second `newsletter.*` site resolved without JSON replaces the first (BUG-06). Both come from one cause: a follow is found by its id alone, and `add` replaces by id.

- **One identity rule**, `substackFollowOf(follows, publication)`. A publication is a follow when their hosts are the same, ignoring `www.`. Otherwise it is a follow when their ids are equal and neither id is a guess. A guess is `www`, empty, or the first label of a host deeper than an apex domain (`newsletter` for `newsletter.example.com`). Every "is this followed" decision uses the rule: the follow button, pin, the publication page's metadata write-back, search results, notes, groups and the checks after a follow.
- **Following never overwrites.** `SubstackPublicationsStore.add` updates the matching follow in place, under that follow's id. A publication that matches no follow, but whose id another follow holds, is stored under its host instead.
- **Ids guessed from a deep host are the host.** On a host deeper than an apex domain, RSS, homepage and Beehiiv resolution store the whole host without `www.` (BUG-06 at its source), so two `newsletter.*` sites never share an id. Apex domains keep their first label: `garbageday.email` stays `garbageday`, which is usually the publication's Substack subdomain and is pinned by existing tests. Existing follows keep their ids: there is no migration, and reading progress keeps its keys.
- **Twins.** A host deeper than an apex domain never guesses its first label as a twin, whether pasted or followed, and its first label is no twin hint either: on such hosts it is a prefix like `newsletter` or `blog`, not the publication's subdomain. A host-shaped id is not a handle, so it is no hint. Apex domains guess as before, checked as in §3.

Trade-offs, kept on purpose:

- The same publication followed at `{subdomain}.substack.com` and at its apex custom domain still matches by id, as before.
- Two follows of different apex sites that share a label (`example.com` found without JSON, and `example.substack.com`) still match by id, so following the second updates the first. Host ids would avoid that, but on an apex domain the first label is usually the publication's own Substack subdomain, which the twin lookup and existing tests rely on. The case needs a non-Substack apex site whose label is another publication's subdomain.
- When the rule cannot tell, a publication is shown as not followed. Following it again adds a second follow rather than replacing one.

### 7. The article cache: bound and freshness (BUG-09, rest)

The video flag is fixed (§5). Two parts were left:

- **Bound.** FFCache keeps one file per entry in the OS temporary directory, with a 14-day expiry, and cannot list
  its entries. The article cache therefore keeps its own index: each entry's key, size and time of writing, oldest
  first, under a key no article can have. A write moves its entry to the end and drops the oldest entries until at
  most 200 articles and 16 MiB remain, always keeping the newest. Opening an article writes it again, so the bound
  drops the least recently read. Entries older than the expiry leave the index. The OS may still clear everything;
  a missing index starts a new one.
- **Freshness.** A cached copy is used only while it matches the post the reader was opened with: the same date and
  the same title, wherever both are known. A different date or title means the post was edited or rescheduled, so
  the reader fetches it again and the new copy replaces the old. An edit to the body alone does not show in the
  listing; a manual refresh or the expiry replaces that copy.

The 200 articles and 16 MiB are a choice: the synthetic long post is 112 KB, so the bound holds over a hundred long
reads. Real sizes are UNVERIFIED (no route to Substack).

## Tests

- **Flipped probes.** The probes for BUG-03, 04, 05, 06 and 09 in `test/substack_audit_probes_test.dart` flip into regression tests.
- **Article cache:** the bound keeps the newest entries within both limits and drops the oldest files; a copy whose date or title changed is not used.
- **Follow identity:** `test/substack_follow_identity_test.dart` covers the publication page (BUG-16), two `newsletter.*` follows (BUG-06), following the same site again, and the same publication on two hosts. Each case fails before §6 except the last, which pins behaviour kept.
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

### 8. Text selection in articles (BUG-12; decided while finishing Tier 1)

Long-press read-aloud blocked selection and copy. The least disruptive change: long-press selects text as usual, and
**Listen** reads from the selected paragraph when text is selected; with nothing selected it reads from the top, as
before. The page no longer cancels `contextmenu` or disables the touch callout. On a device, whether tapping Listen
keeps the WebView selection is UNVERIFIED; if it is lost, Listen falls back to reading from the top.

### 9. Blocks drawn only on the web (BUG-08, first part)

LaTeX, galleries, polls and native audio embeds are empty elements that Substack's own scripts fill in; the sanitizer
removes empty elements, so they vanish without a trace. Rendering each one needs its recorded markup (HAR). Until
then, an empty element carrying Substack's `data-component-name` marker is kept as a placeholder instead of removed,
and the page shows "This part of the post shows only on the web." with the existing "Open in browser" link to the
post. Empty elements without the marker are still removed. New string: `plugin_substack_web_only_block`, all locales.
