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
- Home destinations use `PageView.builder` and a page-index notifier, so unused
  groups / plugins are not constructed on first paint and a tab change does
  not rebuild the visible feed. Feed tiles drop an unused ticker; long posts
  stay capped in the feed; boost-run lengths are remembered per list.
- Home no longer rebuilds keep-alive feeds on unrelated pref writes (nav-bar
  labels listen locally). Plugin panes unmount when left. Feed
  `ScopedBuilder.transition` AnimatedSwitchers are gone. Instagram / TikTok
  cards isolate paints; remaining 20–40px plugin avatars decode at paint size.
  Home-strip unread scans debounce 200ms.
- Feed chunk JSON encodes off the UI isolate. Gallery plugin stores race the
  first frame. Pref listeners register once. Plugin media / Instagram / TikTok
  decode at paint size. Pixiv keeps only the visible masonry grid alive.

## RSS cover decode budget (2026-09-25)

The cloud environment still has no physical Android device, so frame and memory
rows remain unmeasured. Static inspection found that RSS article covers were the
remaining feed thumbnail path without a requested decode size: an 88dp slot
could retain the source-sized bitmap in Flutter's shared image cache.

The RSS cover now requests its actual constrained width multiplied by device
pixel ratio. Widget verification renders the 88dp feed slot at 2x DPR and checks
for a 176px `ExtendedResizeImage` width:

```bash
fvm flutter test test/rss_cover_performance_test.dart \
  test/plugin_card_overflow_test.dart
```

This proves the decode-size contract, not a device-level speedup. A follow-up on
the same mid-range Android device and image-heavy feed must compare three or more
profile-mode scroll runs for build/raster p95 and steady/peak memory.

## Substack reader baseline (2026-10-13)

Base `082bb06`. Full context, bug list and plan: `docs/substack/audit.md`. This
environment had no device and no route to Substack (the egress policy denied
`substack.com`, publication hosts and `substackcdn.com`), so every device row
below is **UNVERIFIED**. Nothing was estimated to fill it.

### Device rows (S1–S7, profile build, warm-up + 3 trials, median / p95)

| Scenario | Median | p95 | Target |
|---|---|---|---|
| S1 cold start → first Substack items | UNVERIFIED | UNVERIFIED | — |
| S2 long post, first paragraph (cached / uncached) | UNVERIFIED | UNVERIFIED | ≤300 ms / ≤1.5 s Wi-Fi |
| S2 fling to end: janky %, build p95, raster p95 | UNVERIFIED | UNVERIFIED | <1 %, under 1000/Hz ms |
| S3 300+ comments: first comment, scroll janky % | UNVERIFIED | UNVERIFIED | <1 %, under 1000/Hz ms |
| S4 refresh of 30 publications: total, requests, 429s | UNVERIFIED | — | ≤5 s Wi-Fi, zero 429s |
| S5 ten long posts, then `RUNNING_CRITICAL` trim | UNVERIFIED | — | no monotonic growth |
| S6 bytes for the long post, requested vs original | UNVERIFIED | — | — |
| S7 offline saved posts: time to readable, missing images | UNVERIFIED | — | zero missing |

Record the commit, device, Android version, refresh rate and network with every run.

**S2 and S3 are scripted.** On a phone over ADB:

```bash
fvm flutter drive --profile \
  --driver=test_driver/substack_perf_test.dart \
  --target=integration_test/substack_perf_test.dart \
  --dart-define=XTA_COMMIT=$(git rev-parse --short HEAD) --dart-define=XTA_NETWORK=wifi \
  --dart-define=SUBSTACK_PERF_POST=https://… \
  --dart-define=SUBSTACK_PERF_COMMENTS=https://…
```

The driver writes `build/substack_perf_summary.json`. For each trial it records frames, build p50/p95, raster p50/p95 and
janky % against `1000 / refresh rate`, then takes medians across the three trials. The article is a WebView, so
`watchPerformance` sees only Flutter's frames; the harness also records in-page `requestAnimationFrame` gaps and which
scroll method reached the page. Without the two URLs the harness reads synthetic content
(`test/fixtures/substack_synthetic.dart`): that checks the harness and the CPU side, not the network or image decoding.

**S1, S4–S7 are manual for now.** The package is `com.aimdi.xta` (`.debug` for debug builds).

- **S1:** run `adb shell am force-stop com.aimdi.xta`, then `adb shell am start -W …`, with Substack as the start tab.
  Read time to first card from a 60 fps screen recording, or from a DevTools timeline in profile mode.
- **S4:** pull to refresh with the 30-publication test set behind an intercepting proxy on Wi-Fi. Count requests and
  429s, and time the last response. The client logs no request count yet; a counter arrives with the transport layer
  (audit plan, Tier 1, first item).
- **S5:** take `adb shell dumpsys meminfo com.aimdi.xta` before and after reading ten long posts from the feed. Then run
  `adb shell am send-trim-memory com.aimdi.xta RUNNING_CRITICAL` and take a third reading.
- **S6:** use the proxy capture of one long-post open. Compare the image URLs and bytes requested with the originals.
- **S7:** retain the long post from the reader, switch to airplane mode and open it from the offline library. Time it
  to readable and count images that do not render.

### Host VM measurements (not device timings)

`fvm flutter test test/perf/substack_vm_benchmark_test.dart --dart-define=RUN_BENCH=true` runs on the host Dart VM in
JIT mode with assertions on. It ranks work and counts bytes; it does not predict phone timings. The numbers below are
median / p95.

| Path | Result |
|---|---|
| S2: open one post (9,223 words, 24 images, 111,930-byte body), parsing before the first await | 92.9 / 119.6 ms total. One parse alone is 14.5 / 36.4 ms; the reader does five. |
| S3: synthetic comments, flatten then rows (medians only) | 300 comments: 2.6 + 2.1 ms; 1,000 comments: 8.4 + 2.9 ms |
| S4: refresh of 30 publications with an instant fake network | 147.6 / 165.8 ms of CPU, 27 publishes, 24 publications reached |
| S7: retained post with 24 images of 256,000 bytes (assumed size) | page 115,476 → 8,274,517 bytes; `renderArticle` 276 / 530 ms |

Under a blanket 429, one `fetchPosts` call makes 12 requests for a `*.substack.com` publication, then reports "not a
Substack publication"; BUG-03 in `test/substack_audit_probes_test.dart` pins that. The same mock against a custom domain
counted 22.

After the transport fix (BUG-03), on the same mocks with a virtual clock, one 30-publication refresh under a blanket 429
sends 7 requests in total when the publications are on `*.substack.com` (one shared bucket; 288 before) and 72 when they
are 24 distinct custom domains (three each; about 528 before). Both end in a rate-limit message rather than "not a
Substack publication".

With the refresh cap lifted as well (BUG-05), a healthy 30-publication refresh sends 30 requests and reaches all 30
(24 before); under a blanket 429 it sends 8 on `*.substack.com` and 90 on 30 custom domains.

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
