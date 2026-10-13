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

B1–B6 (`dba4687f` to `e5315b74`; docs/booru/plan.md lists each) change error
text, credentials, feed resets, paging after a failure and the Following tab.
The scripted run uses Danbooru with no credentials on a working network, so
two of them are on a timed path: B3 checks whether a feed is stale (site,
rating cap, blacklist) before each page request, and B5 adds a footer under
every grid that observes the paging state and shows a spinner while the next
page loads. S1 and S2 cover both. Requests after a failed page are not a
scenario here; a unit test checks them (five near-end scroll notifications
after a failure make one request).
