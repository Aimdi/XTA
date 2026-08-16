# Performance baseline (Phase 1)

Captured before the Phase 2 tweet/perf + Phase 3 X-look theme work.
Without a baseline you cannot prove "smoother."

## Environment notes

| Item | Value |
|---|---|
| App version | 4.12.0+400001040 |
| Flutter (FVM) | 3.44.4 |
| Date | 2026-07-23 |
| Host | Cursor Cloud Linux VM (no `/dev/kvm`, no physical device) |

**Device scroll / cold-start traces cannot run in this VM** (Android-only app,
no emulator acceleration, no attached phone). Numbers that need a mid-range
phone are marked **TBD — device**. Re-run those locally with:

```bash
fvm flutter run --profile --trace-startup
fvm flutter build apk --analyze-size
```

The scroll numbers no longer need a hand-driven DevTools session. On a phone
that is already signed in (`scripts/adb_wireless_connect.sh` for a wireless
one):

```bash
fvm flutter drive \
  --driver=test_driver/scroll_perf_test.dart \
  --target=integration_test/feed_scroll_perf_test.dart \
  --profile
```

It flings the feed twenty times and writes `build/feed_scroll_summary.json`
with build and rasterizer frame times plus missed-budget counts — the numbers
the two tables below want. Profile mode is not optional; a debug build's frame
times say nothing about what a reader experiences.

## Cold start (`--profile --trace-startup`)

| Metric | Baseline | After Phase 2 |
|---|---|---|
| Time to first frame | TBD — device | |
| Time to first meaningful feed paint | TBD — device | |

## Scroll jank (feed of ~200 posts, 10 s)

| Metric | Baseline | After Phase 2 |
|---|---|---|
| Dropped frames | TBD — device | |
| Frames over 16 ms | TBD — device | |

## APK size

Recorded on this VM after a clean debug build (see Phase 0 gate):

| Artifact | Baseline bytes | Notes |
|---|---|---|
| `app-debug.apk` | 222344320 (~212 MiB) | debug build 2026-07-23 on this branch after icons + Inter |
| `--analyze-size` summary | TBD — device / local | prefer release / profile for fair comparison |

## Prior hot-path work already on mainline

These landed before this baseline doc and are **not** double-counted as Phase 2 wins:

- Memoized `tweetCardColor` / footer tint (`9dc41c4`)
- Avatar `cacheWidth` decode cap (`9dc41c4`)
- Reverted feed `cacheExtent` bump — GIF tiles spin native players on build (`d66b60b`)

## App-wide pass (feeds / plugins / tiles)

Mechanism work after the tweet-module pass. Device rows above stay TBD.

- Group first-page cache reads cap at `maxCachedChunkRows` (same as the
  preview path) so a heavy group does not decode every stored page before
  paint.
- Plugin interleave on For You / groups batches into one `setState` and skips
  empty-for-empty slot writes (`replacePluginSlot`).
- `AccountPostCache.onPartial` paints the first account immediately, then
  coalesces later paints (`kAccountPostsPartialThrottle`).
- Mastodon / Bluesky following feeds paint progressively; Mastodon tabs are
  lazy; first-wave account caps are 12 / 16.
- Feed tiles drop the unused ticker mixin; Community Notes in the feed use
  `Text.rich`; long posts stay capped in the feed even when “always show
  full” is on (the pref still applies on the opened status).
- Boost-run lengths are remembered per items list, not walked per tile.
- Instagram / TikTok post lists use `FeedListView`; Instagram images decode
  at paint size.

## Later pass (feeds / plugins / startup)

Mechanism work after the tweet-module pass. Device rows above stay TBD.

- Plugin timelines share `FeedListView` (`kFeedListCacheExtent`, no keep-alives) and wrap cards in `RepaintBoundary`.
- Home/group shells no longer rebuild the feed on every pref write; subscribe badges / Substack read / Reddit votes use `distinct:`.
- Non-sensitive media skips the hide-sensitive `Consumer`.
- Cached chunk JSON decodes on a background isolate; plugin interleave waits until after the first frame and skips disabled plugins.
- Audio service bind moved to the same post-frame callback as MediaKit.
- Home-strip remounts reuse plugin feeds inside `kAccountPostsCacheTtl` (Substack
  included) and skip the Triple loading flash when posts are already on screen.
  Inbox / Notes / Liked panes are built the first time they are opened
  (`PluginLazyTabs`), not on the first home paint. TikTok's following cache
  lives with the other plugin stores so a swipe away no longer drops it.

## Phase 2 targets (tweet module)

1. Cap timeline photo decode via `extended_image` `cacheWidth` (not fullscreen).
2. `RepaintBoundary` around media / shimmer / skeleton.
3. ~~Keep `ListView.builder` / `PagedListView`; do **not** raise `cacheExtent`
   until video creation is visibility-gated.~~ **Gated.** `TweetVideo` now waits
   for the tile to be on screen before allocating a player
   (`lib/tweet/video_playback_policy.dart`); off-screen autoplay videos and
   looping GIFs allocate nothing. The `cacheExtent` bump reverted in `d66b60b`
   can be retried, but measure it — a larger window still builds more tiles and
   decodes more images.
4. Skeleton first-page indicator (perceived speed).
5. Prefer `const` on eligible tweet chrome widgets.

## Gate

Phase 4 compares the same phone + same flows as the TBD device rows above.
Requirement: fewer dropped frames, cold start not worse, APK not materially bigger.
If a module does not improve → revert that worktree.
