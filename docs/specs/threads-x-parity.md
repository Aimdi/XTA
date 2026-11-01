# Threads, read as well as X

Read-only, like every Threads pass before it (`threads-direct.md`,
`threads-polish.md`, `threads-app-like.md`, `threads-reader-upgrade.md`):
nothing here writes to Meta. Likes, saves and follows stay on the device.

The owner asked for Threads to be as good as the X plugin. This pass closes
the gaps a reader meets most that a guest can close, from a map of what X
offers against what Threads does (2026-11-01) and a live look at what
Threads serves a logged-out browser (api-canary run 41291398417).

## What a guest can reach

- **Search pages carry posts.** `www.threads.com/search?q=…&serp_type=default`
  (top), the same with `&filter=recent` (recent) and `serp_type=tags` (a
  tag) answered a logged-out request with 15–19 posts each, in the same
  `thread_items` the post pages carry, which `threadsSsrChains` already
  reads.
- **A profile's older posts are out of reach.** The profile query answers
  25 posts and no cursor anywhere in its answer, and the page names no query
  that pages further. Older posts stay a gap.

## After

### Search

- A query that is not an @handle or a post link searches posts, where it
  did nothing: **Top** and **Recent**, one page each, as Threads cards that
  open, save and like as anywhere else.
- **A topic tag opens its posts in the app** (Top and Recent), where it
  opened Threads in the browser: the "› Tag" under a post and a #tag in a
  caption.
- An @handle still opens the profile, a link still opens the post, and
  with cookies the people list stays.
- The hint says what can be searched.
- Each result is the post the page found: in a result's chain, the post
  whose text holds the query (or whose topic is the tag), else the chain's
  first.

### Saving

- **A bookmark in each post's actions**, as on X, Bluesky and Mastodon;
  long press keeps the folder sheet.
- **Saved posts keep the whole post** (text, media, quote, link card,
  time), where only a link was kept, and show as Threads cards in Saved.
  The id is the one a link save used, so a post saved before still shows as
  saved.
- **Threads has its own filter in Saved**, holding old link saves from
  Threads too, and the Threads menu opens it.

### Profiles

- A guest profile no longer says "0 Following": Meta does not tell a guest,
  so the count is left out. Nor does it link to itself (the bar already
  opens the profile in the browser). A profile pieced together from its
  posts, when the page could not be read, shows no count at all, where it
  said 0 for each.
- The bio's @mentions and links work.

### Following

- **Pull to refresh reaches every followed account.** A pull asks at most 20
  accounts (6 with a session); it asked the same first 20 each time, so the
  rest never refreshed. It now asks the ones never asked first, then those
  asked longest ago, the rotation Discover already samples its members with.

### Reliability

- In a mixed timeline, a Threads failure retries by what Meta said, where
  every failure was retried after 2, 5, 15, 30 and 60 seconds:
  - a throttle waits for its cooldown: the 30 minutes a session's throttle
    parks it for, and for a guest one, which parks nothing, the 15 minutes
    the app waits for any limit without a stated reset; then it retries
    once;
  - a missing setup, a refused session or a lost one (`login_required`)
    waits for the reader;
  - a host that did not answer keeps the usual backoff.
- What a failure says on screen is unchanged. The shared failure taxonomy
  does not learn Meta's errors: a failure can say how it may be retried
  (`ReadRetryAdvice`), and the Threads one does.

### Conversations

- **Replies can be sorted**: Relevant (Meta's own order), Recent or Oldest,
  as Bluesky's and Mastodon's can. Each reply thread moves whole, placed by
  its first reply. The choice holds for the session, shared with the other
  networks; an order Threads does not offer (Most liked, chosen on Bluesky)
  shows Meta's.
- The replies header, the author filter and the order share one line,
  which wraps on a narrow phone.

## Not in this pass

- Older pages of a profile, a search or a tag: no cursor a guest can follow.
- Translation: X's comes from X for X posts only; another service would
  send posts to a third party.
- Quotes and reposts lists, likers, For you without a session: no guest
  endpoint.

## Code

- `lib/plugins/threads/threads_archive.dart` (new), `lib/saved/*`: saving.
- `lib/plugins/threads/threads_direct_client.dart`, `threads_parse.dart`,
  `threads_search_screen.dart` (new), `threads_search_sheet.dart`: search
  and tags.
- `threads_models.dart`, `threads_parse.dart`, `threads_profile_screen.dart`:
  profiles.
- `threads_store.dart`: the refresh order.
- `lib/ui/read_failure_kind.dart` (`ReadRetryAdvice`),
  `lib/ui/rate_limit_retry.dart`, `threads_client.dart`,
  `threads_direct_client.dart`: retrying by what Meta said.
- `lib/ui/conversation_sort.dart`, `threads_thread_store.dart`,
  `threads_thread_screen.dart`: reply order.

## Tests

- Search results from a page shaped like Threads' (`thread_items` in
  `data-sjs` scripts): the matched post per chain; top and recent ask the
  right pages; a tag asks the tag page; a non-handle query searches posts.
- Saving: the archive round-trips a post; an old link save and a new save
  share the id; Saved shows a Threads card and files it under Threads.
- Profiles (`test/threads_profile_card_test.dart`): a guest profile shows
  no following count and no self link; one from posts shows no count; the
  bio's mention and link can be tapped.
- Refresh order (`test/threads_refresh_order_test.dart`): two pulls with 25
  accounts reach all 25, the unreached five first.
- Retry (`test/threads_retry_test.dart`, `test/read_recovery_backoff_test.dart`):
  a session throttle and the cooldown after it wait for its end, a guest
  throttle for 15 minutes; a lost session, no setup or a refused session
  wait for the reader; an unreachable host keeps the backoff; on screen a
  throttle retries once at its end and not in seconds.
- Replies (`test/threads_conversation_test.dart`,
  `test/threads_reader_widget_test.dart`): each order sorts as named and
  keeps reply threads whole; the choice is shared for the session; the
  header fits 320 dp in German.
