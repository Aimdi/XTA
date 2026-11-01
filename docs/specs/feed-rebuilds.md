# Posts that did not change are not built again

The feed list builds again far more often than its posts change: every
frame of the home header sliding away, twice per page (loading on, loading
off), once per batch while a group loads or refreshes, for each notice. Each
time it built every post on screen and in the cache window again, and redid
work over the whole loaded history. The audit behind this is in the session
notes of 2026-11-01; measurements are in docs/perf-baseline.md, "Feed list
rebuilds".

## Found

- **The header slide.** `ReadingColumn` is a `LayoutBuilder`, which builds
  again on any change to its constraints. The header's slide changes the
  feed's height on each of its frames, and each build handed back a new list,
  so every post was built again: 72 posts over 12 frames in a widget test.
- **The memos that never matched.** The caught-up line, the interleaved
  buckets, link grouping and boost runs were remembered per list of posts,
  by identity. The paging package copies its pages into new lists for every
  state it makes, and `items` flattens them into another on each read, so
  the identity never matched and all of it was redone on every list build.
- **The posts themselves.** Every list build made a new widget for every
  post it showed, so each was built again: 10 posts per list build in the
  benchmark, the larger part of a list build's cost.

## After

- **The reading column builds again when its width changes**, and not its
  height: its padding depends on the width alone. The header slide builds no
  post again.
- **The memos match on the same posts**: a state whose pages hold the very
  posts already loaded hands back the list the memos know. Checking them
  one by one is a pointer comparison per post.
- **A post that did not change keeps its widget.** The list remembers the
  widget it built for each post, and builds a new one when the post is a new
  copy (a refresh), when the reader's name or a fold reason changes, or when
  any setting changes, so settings still land on the next list build as
  before.

## Code

- `lib/ui/reading_column.dart`: a width-only layout builder.
- `lib/tweet/paginated_tweet_list.dart`: the stable list of posts and the
  per-post widgets.

## Tests

- `test/reading_column_test.dart`: a height change builds none of the posts.
- `test/feed_list_rebuild_test.dart`: a list build that changes no post
  builds none again; a setting changing builds them again; a refresh that
  brings new copies builds them again.
- `test/perf/feed_list_benchmark_test.dart` (opt in with `RUN_BENCH`): time
  per list build with 600 posts.
