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
  429s, and time the last response. The transport layer (`substack_transport.dart`) exists now but counts nothing
  itself, so count at the proxy.
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

### Tier 1 batch: same-session host comparison

Host numbers move between sessions: the S2 open path, which this batch did not touch, measured 92.9 ms in the baseline
session above and 32–39 ms in this one. So the base commit `eaf5f25` and HEAD were measured back to back in one session,
alternating, two runs each (median ms per run). Only differences larger than that spread count.

| Path | `eaf5f25` | HEAD | Reading |
|---|---|---|---|
| S2 open pipeline | 32.1, 38.9 | 34.0, 33.1 | Unchanged; the reader's parsing path was not touched. |
| S2 speech text | 15.7, 15.8, 17.2, 15.8 | 15.2, 17.0 | Unchanged once the footnote check looks at links only (`317a321`). |
| S3 flatten, 300 / 1,000 comments | 1.37 / 2.16, 1.62 / 2.57 | 1.70 / 2.25, 1.33 / 2.20 | Unchanged. |
| S4 refresh CPU, 30 follows | 64.1, 67.6; 27 publishes; 24 publications | 102.9, 104.2; 33 publishes; 30 publications | +57 % CPU for 25 % more publications (BUG-05). Each publish re-merges and re-sorts every post loaded so far (`substack_store.dart:500-506`), once per arriving publication, so the total grows with the square of the publications: (30/24)² is +56 %. Tier 2, item 3 targets it. |
| S7 retained page, online / offline | 115,476 / 8,274,517 bytes | 115,895 / 8,274,936 bytes | +419 bytes: the read-aloud script now skips footnote numbers. |
| S7 `renderArticle` | 93.4, 105.9 | 97.8, 87.2 | Unchanged. |

The device rows stay UNVERIFIED: no device, and no route to Substack from this environment.

### Batch 2 (post identity, article cache): same-session host comparison

Batch 2 changed the feed's merge, which now keys each post by its canonical URL. The previous HEAD `645f288` and
`d53183b` were measured alternately in one session. That session was noisier: the untouched S2 path ranged 37–50 ms.

| Path | `645f288` | `d53183b` | Reading |
|---|---|---|---|
| S4 refresh CPU, 30 follows, five runs each | 130.3, 126.1, 120.8, 122.0, 128.3 (median 126.1) | 123.8, 144.3, 136.1, 123.5, 123.8 (median 123.8) | No measurable change; 33 publishes in both. |
| S2 open, S3, S7 render | two runs each | two runs each | Code untouched; differences within the session's noise. S7 page bytes identical (8,274,936). |

### Tier 2, item 1: one parse per open (S2), same-session host comparison

`431bbc7` (five parses) against HEAD (one parse), alternating, three runs each, the synthetic 9,223-word post. The
benchmark's open pipeline follows each commit's reader. This session was slower overall than earlier ones.

| Path | `431bbc7` | HEAD | Reading |
|---|---|---|---|
| S2 open pipeline (median ms per run) | 85.1, 69.3, 64.7 | 54.5, 42.3, 39.2 | About 39 % less CPU (medians of runs 69.3 → 42.3); every HEAD run is below every base run. |
| S2 navigation index | 47.6, 30.1, 35.3 | 54.8, 53.7, 45.9 | Higher on purpose: it now also produces the speech text, so the separate speech parse is gone. |

Bodies over 20,000 characters are now parsed off the UI isolate. What that saves in frames is device-only: UNVERIFIED.

### Tier 2, item 3: feed sort keys computed once (S4), same-session host comparison

`cebf43c` against HEAD, alternating, four runs each, one 30-publication refresh with an instant fake network.

| Path | `cebf43c` | HEAD | Reading |
|---|---|---|---|
| S4 refresh CPU (median ms per run) | 228.3, 212.5, 208.5, 191.6 (median 210.5) | 27.7, 27.4, 24.1, 23.1 (median 25.8) | −88 %. Each comparison parsed two dates; now each post's sort time and key are worked out once. |

The order is pinned by `test/substack_feed_order_test.dart`. Coalescing publishes per frame, the other half of the plan
item, is not done: the publish path no longer dominates S4. The base's 210 ms against 124 ms earlier today is the
session's host load; only the alternating pairs compare.

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

## Booru baseline S1–S7 (2026-10-12)

**Every number in this section is UNVERIFIED: none has been measured yet.**
This cloud environment has no Android device (no adb, no `/dev/kvm`), and its
network policy refuses every booru host, so the scenarios cannot run here
against a real site. The script was run end to end on the Linux desktop build
against a local stand-in for Danbooru, only to prove that its flows and
reporting work. Those runs measured the harness on a desktop VM, so their
numbers are not a baseline and are not recorded. docs/booru/audit.md has the
static findings each scenario is meant to confirm or refute.

### How to run

On a phone attached over USB or `scripts/adb_wireless_connect.sh`, with Wi-Fi
on, battery saver off and the screen kept awake:

```bash
fvm flutter drive \
  --driver=test_driver/booru_perf_driver.dart \
  --target=integration_test/booru_perf_test.dart \
  --profile
```

It writes `build/booru_perf_summary.json`. Against a local server on the
Linux desktop build the scenarios took about 7 minutes after the build; a
phone also downloads every picture over the network, so allow longer.
The driver fills in the commit, phone model and Android version; the test adds
the refresh rate and device pixel ratio. Check `env.refresh_rate` in the
summary: the frame budget is 1000 ms divided by it, and where the display
reports none (0, as on the Linux desktop build) the statistics assume 60 Hz.
Record the network by hand: Wi-Fi or mobile, and a speed test result. For the duration of the run the test switches
Booru to Danbooru with no login or API key, then puts the reader's site and
credentials back. Each timed scenario runs once as a warm-up, then three
trials; the summary gives each trial, the median and p95.
`--dart-define=BOORU_HOST=<url>` measures another Danbooru-compatible host.

### Scenarios

| # | What is measured | How | Summary key | Target | Baseline |
|---|---|---|---|---|---|
| S1 | Open Booru (Latest) until the first thumbnail paints | In the test: push the Booru home and time it to the first decoded tile. Each push builds its own stores, so every trial fetches page 1. `s1_main_to_navigator_ms` times `main()` to the first route. True cold start is manual, below | `s1_open_to_thumbnail_ms` | ≤ 1.0 s warm on Wi-Fi | UNVERIFIED |
| S2 | Danbooru `order:rank`, fling-scroll the grid through 300 posts | `watchPerformance` over flings from the top to post 300; RSS after each trial | `s2_grid_fling`, `s2_rss_mb_after_trial` | < 1 % janky frames; p95 build and p95 raster each under 1000 ms / refresh rate | UNVERIFIED |
| S3 | Open a post until its picture shows, then swipe through 20 posts | Tap the first picture tile and time it to the decoded picture; `watchPerformance` over 20 swipes | `s3_open_to_picture_ms`, `s3_viewer_swipes` | Same frame targets as S2 | UNVERIFIED |
| S4 | Keystroke to rendered tag suggestions | Type a prefix through the field's `onChanged`, then time it until the suggestions are on screen. The figure includes the 250 ms debounce (`booru_search_store.dart:76`). A second pass repeats the prefixes | `s4_keystroke_to_suggestions_ms` (`first`, `repeat`) | ≤ 150 ms after the debounce for cached prefixes. Nothing caches suggestions today (audit §2.4), so `repeat` shows the uncached cost | UNVERIFIED |
| S5 | Open and close 10 video or animated posts | Query `animated`. For each post: tap it, start it if it waits for a tap, count the shared pool's players while it plays and after closing. Then signal memory pressure | `s5_videos` | Never more players than the pool limit (`kVideoPoolSize` = 3); none in use after the viewer closes; every idle player freed under memory pressure. Revised 2026-10-12 from "none left after close": the shared pool keeps up to 3 idle players by design | UNVERIFIED |
| S6 | Memory after S2+S3, then after memory pressure | RSS, decoded-image cache size and video players, before and after the framework's memory-pressure message. That message clears the image cache and makes `VideoMemoryObserver` release idle players | `s6_memory` | No monotonic growth over 1,000 scrolled posts; bounded decoded-image cache (96 MiB, `main.dart:464`) | UNVERIFIED |
| S7 | Bytes per grid screenful and per viewer post | Size of each shown picture's file in extended_image's disk cache (`<tmp>/cacheimage/md5(url)`). Pictures only: API JSON and video streams are not counted. With the default settings a tile and the viewer fetch the same sample (`booru_models.dart:139-157`), so viewer bytes are often already paid by the grid | `s7_grid_screenful`, `s7_viewer_post` | None set; the baseline sets it | UNVERIFIED |

S2 flings back through the same 300 posts three times after the warm-up, so
`s2_rss_mb_after_trial` covers about 1,200 scrolled posts. That is the "no
monotonic growth over 1,000 posts" check.

### Manual parts

The integration test cannot kill its own process or call adb, so two things
are measured by hand. Use the package of the build you installed
(`adb shell pm list packages xta`).

True cold start to the first frame (S1). Run it three times after a warm-up
and record `TotalTime`:

```bash
adb shell am force-stop <pkg>
adb shell am start -W -n <pkg>/com.aimdi.xta.MainActivity
```

Then open Booru and time the first thumbnails from a 120 fps screen recording.

Trim memory as Android sends it (S6). Open Booru search for `order:rank`,
scroll through about 300 posts, open a post and swipe through 20, then:

```bash
adb shell dumpsys meminfo <pkg> | head -40   # TOTAL PSS, Graphics, Native Heap
adb shell am send-trim-memory <pkg> RUNNING_CRITICAL
adb shell dumpsys meminfo <pkg> | head -40
```

The in-test S6 sends only the framework's memory-pressure message. A real trim
from Android also asks the Dart VM to collect garbage and lets the renderer
trim (`FlutterActivityAndFragmentDelegate.onTrimMemory`, in the Flutter SDK's
engine sources, lines 1059–1073).

### Tier 1 fixes since this section was written

B1–B9, B18–B20 and B22–B27 (`dba4687f` to `14851203`; docs/booru/plan.md
lists each) change error text, credentials, feed resets, paging, the first
frame of a feed, the Following tab, download names, tap-to-load for videos,
the engine guessed for a custom host, settings after leaving them, the date
label on Gelbooru 0.2 posts, what an animation downloads, Popular's default
date and what a closed store does with a late answer. The scripted run uses Danbooru with no
credentials on a working network, so four of them are on a timed path: B3
checks whether a feed is stale (site, rating cap, blacklist) before each page
request; B5 adds a footer under every grid that observes the paging state and
shows a spinner while the next page loads; B7 shows the skeleton until a
feed's first page arrives, where an empty state used to flash; and B8 checks
that a returning page still belongs to the latest refresh. S1 and S2 cover
them. Requests after a failed page are not a scenario here; a unit test checks
them (five near-end scroll notifications after a failure make one request).
Since B18 the run also turns "load media only on tap" off, and puts the
reader's value back, so S5 starts each video with the player's own button.
