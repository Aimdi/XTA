# Smaller and faster: the record

What XTA Native weighs and how fast it starts on Android, measured the same
way each time, with what was tried and what it did. Each round reads this
file first, changes one thing, and writes its row.

## How it is measured

CI ("XTA", `.github/workflows/native-prototype.yml`) measures every push:

- **build › Size** runs `tool/apk_report.sh` on the signed release APK: its
  bytes by kind, and the code by package (`apkanalyzer`, names read back
  through R8's mapping). The whole report is the run's `apk-report`
  artifact; the top of it is in the job's log and summary.
- **screens › Start, memory and size** runs `tool/compare.sh` on the
  emulator: first Android's own Settings started cold five times, a
  reference for that runner's speed (a start of the app is read as a ratio
  to it); then the median of five cold starts as installed, the same after
  Android has compiled the app (`speed-profile`), and memory once the first
  screen is up, in total (PSS) and by where it is (Java heap, native heap,
  code, graphics). The app's own marks of each cold start (`StartMarks`,
  milliseconds after its process began) are copied from the log. Last, the
  start with every method compiled (`speed`), the most compiling can give.

The emulator is slower than a phone and its numbers move a few percent from
run to run: a change smaller than about 3% in start time or memory is noise
until a second run agrees. APK bytes are exact.

## Measurements

| Run | Commit | What changed | APK bytes | Cold start | Compiled start | Memory (PSS) |
|---|---|---|---|---|---|---|
| 230 | `e76d1efc` | Baseline | 12,067,121 | 749 ms | 708 ms | 125,108 kB |
| 235 | `c3257f66` | No Kotlin metadata; libraries' strings in 29 languages | 10,411,798 (−13.7%) | 759 ms | 702 ms | 125,358 kB |
| 236 | `ca644027` | No parameter null checks; no compiler-only files | 10,288,232 (−14.7%) | 798 ms | 779 ms | 119,770 kB |
| 237 | `7ba79455` | The string tables packed | 10,140,776 (−16.0%) | 798 ms | 752 ms | 117,511 kB |
| 238 | `28ab943b` | Kept videos in the app's own player | 9,955,126 (−17.5%) | 974 ms | 858 ms | 118,338 kB |
| 240 | `09bc6fc9` | Measurement only (the same app as 238) | 9,955,126 | 630 ms | 643 ms | 118,434 kB |
| 241 | `a88b50e1` | Measurement only | 9,955,126 | 611 ms (2.14 × Settings' 285) | 529 ms | not comparable (read after a full compile) |
| 249 | `5484bb87` | Measurement only | 9,955,126 | 931 ms (2.00 × Settings' 465) | 854 ms | 116,944 kB |
| 250 | `0dc8e15c` | The account menu made when its drawer first opens | 9,955,126 | 917 ms (2.12 × Settings' 433) | 875 ms | 113,779 kB |
| 251 | `2ce906fa` | Measurement only | 9,955,126 | 568 ms (1.64 × Settings' 346) | 580 ms | 114,985 kB |
| 253 | `c27f11d7` | Measurement only | 9,955,126 | 815 ms (2.24 × Settings' 364) | — | — |

At the baseline the two dex files are 9,383,959 bytes packed (78% of the
APK) and the resource table 659,364.

Run 235 by kind, packed: code 8,187,687 · the screens' strings and pictures
(Compose resources, 630 files) 1,323,054 · resource table 223,848 · other
165,356 · Android resources 81,372 · native code 63,108 · META-INF 20,120 ·
Kotlin builtins 11,134.

Run 235's memory after start, PSS: code 60,332 kB (the app's own dex and
compiled code mapped in: nearly half of the total, so less code is also less
memory) · native heap 32,564 · private other 20,304 · system 6,771 · Java
heap 6,192 · graphics 0 on the emulator.

## The owner's decision: what the kept libraries weigh

Run 253 built the same app a second time, for measuring only, with the
libraries the plugin contract keeps whole (Kotlin, kotlinx, Compose,
lifecycle, navigation, OkHttp, okio, jsoup, Coil) kept only as far as the
app itself uses them:

| | APK | code (dex), packed |
|---|---|---|
| As released (libraries kept whole) | 9,955,126 bytes | 7,884,325 |
| Libraries kept as far as the app uses them | 7,006,002 bytes | 4,972,823 |

So that part of the contract costs 2,949,124 bytes, 30% of the APK. A
build like the second is not shippable as it is: an installed plugin
calling a library method the app does not use would fail. What would make
it shippable is the owner's to choose, not a round's:

- keep, of the libraries, what the app uses **and** what any published
  plugin references (a list a tool can make from the plugins' own classes,
  as `tool/plugin_abi.py` reads them), raising `OLDEST_PLUGIN_API` so that
  only plugins built against that list load; a plugin needing more of a
  library then needs a newer app, as one needing more of the plugin API
  does today; or
- leave the contract as it is, and the APK near ten megabytes.

`-PkeepRules=<file>` on `:app:assembleRelease` builds with other rules than
the app's, to measure such a list before deciding.

## What holds the size up

The plugin contract (CLAUDE.md, `app/proguard-rules.pro`): installed plugins
are compiled against the app's modules and its libraries and call them by
name, so every public class and member of `com.aimdi.xtanative.{core,store,
services,ui,data}`, Kotlin, kotlinx, Compose, lifecycle, navigation, OkHttp,
okio, jsoup and Coil is kept whole. R8 can still shrink what is private or
internal, and everything in libraries outside that list (Media3, Commons
Compress, xz). Narrowing the kept list is the owner's decision, not a
round's.

## Tried

- **No Kotlin metadata** (`!kotlin.Metadata` in the kept list): R8 drops the
  annotation from every class. Run 235: the dex went from 9,383,959 to
  8,187,687 bytes packed (−1,196,272). Plugins still install and load on the
  emulator; start and memory did not move beyond noise.
- **The libraries' strings in the app's 29 languages only**
  (`resourceConfigurations`): the resource table went from 659,364 to
  223,848 bytes (−435,516).

- **No parameter null checks** (`-assumenosideeffects` on Kotlin's
  `Intrinsics.check…Parameter/ExpressionValue/ReturnedValue/Field`): run 236,
  the dex 8,187,687 → 8,078,494 bytes packed (−109,193); code in memory
  60,332 → 56,376 kB.
- **No compiler-only files in the APK** (Kotlin's module and builtin tables,
  the coroutines' debug probe, version stamps): run 236, about 14 kB.
- Run 236's starts read slower (798 and 779 ms against 759 and 702) with one
  launch of 1,011 ms among them; nothing in the round adds work at start, so
  this is taken for the emulator's noise until a later run says otherwise.
- **The string tables packed** (`gradle/pack-resources.gradle.kts`,
  `ui/common/PackedResources.kt`): run 236's report showed
  `ui.generated.resources` alone at 2,188,441 bytes of dex, the tables
  Compose's generator writes as code (40,561 objects built for 1,353
  strings in 30 folders). Each table is now one packed line. Run 237: that
  package is 217,483 bytes of dex (−1,970,958), the dex packed 8,078,494 →
  7,938,410 (the old tables were repetitive and packed well, so the APK
  gains 147 kB while the code the phone maps in loses 1.6 MB: code in
  memory 56,376 → 53,924 kB). 1,491 tables packed, none left; every screen
  of the emulator's pass still found its words.
- Run 237's desktop job failed at "Videos play in the app": the podcast
  player stood at 0 ms on a runner whose sound output would not open
  (PipeWire, ALSA and JACK all refusing). The video itself played to its
  end; nothing of the round touches the desktop's players. Taken for the
  runner's, to be confirmed by the next run.
- **Kept videos in the app's own player**
  (`ui/offline/OfflineVideo.android.kt`), not Media3's `PlayerView`: run
  238, `androidx.recyclerview` and `androidx.media3.ui` are gone from the
  dex (−97 kB of it), the Android resources 81,372 → 19,677 bytes and the
  resource table 223,020 → 165,256: 186 kB of APK. The desktop job passed
  again, so run 237's podcast failure was the runner's.
- **The starts were the runners'.** Run 240 is the same app as run 238 and
  starts in 630 ms where 238 took 974; Android's own Settings, started the
  same way, took 368 ms there (the app is 1.71 times it). Read every later
  start as that ratio.
- **Where a start goes** (run 240, `StartMarks`, four launches after the
  first): the application is made by 12 ms, the graph by 17, the activity
  is in `onCreate` by 22 and has read the settings by 25; the first frame
  is drawn at 506 to 580 ms. So nothing before `setContent` is worth
  moving: the half second is the first composition, layout and draw. A
  start compiled by its profile (`speed-profile`) is no faster than one as
  installed (643 against 630), which asks whether a profile is applied at
  all; the next run compiles everything (`speed`) to see what compiled code
  can give, and marks the end of the first composition.
- **What compiled code gives** (run 241; Settings 285 ms): as installed
  611 ms, compiled by its profile 529, with every method compiled 532. So
  the profile already has all that compiling ahead can give, about 13%,
  and a fuller profile would add nothing. (That run read the memory after
  the full compile, 130 MB with 73 of code: not the app's, the
  measurement's; the comparison now reads memory first.)
- **The first composition is the placeholder** (run 241): `composed` is
  marked at 65 ms, because the root shows only the theme's background until
  the session is read; the screens themselves are composed, laid out and
  drawn inside the first frame, which ends at about 500 ms. The next run
  marks the root past its wait, Home, the first post and the frame's
  beginning (`ui/StartSteps.kt`), to see which of them the 435 ms is.
- **The first frame is Home being laid out** (run 249, a slow runner:
  Settings 465 ms, the app 931, twice it; all compiled 813). The frame
  begins at about 50 ms, the root is past its wait at 71, Home begins at 87
  and the composition is over at 96; the frame ends at about 850 ms, with
  no post composed in it. Home's shell stands in a `BoxWithConstraints`, so
  everything under it is composed while the frame is measured, after the
  composition's own end: the three quarters of a second are Home's shell,
  its bars, its page and the drawer.
- **The account menu made only once the drawer is asked to open**
  (`DrawerMenu`, `HomeScreen.kt`): the closed drawer's whole menu was composed
  and measured in that first frame. Run 250: the start did not move (2.12
  times Settings against 2.00, within the runs' spread), memory 113.8 MB
  from 116.9. The menu was not where the time is.
- **Nor is Home's composition** (run 250): the shell begins at 101 ms, its
  body at 105, the bottom bar at 110, the page at 118, the feed page at
  120, the group feed at 152 and its skeleton at 154; the frame ends at
  about 850. All of Home is composing within 100 ms, and 700 ms pass after
  the skeleton begins with nothing marked: either the skeleton's rows, the
  frame's layout, or its drawing (the emulator draws in software). The
  next run marks the skeleton's end, the end of layout and the start of
  drawing.
- **The start is the emulator drawing its first frame** (run 251; Settings
  346 ms, the app 568, 1.64 times it). Of a cold start the app's own part
  is small and now known step by step: the application at 11 ms, the
  activity at 26, the first frame beginning at 31, Home composing from 56,
  its skeleton composed at 99, everything measured and laid out at 122,
  drawing begun at 124. The frame then ends at about 500 ms: some 375 ms
  in which the emulator, which draws in software, sets up and draws that
  first frame, as it does for Android's own Settings. With every method
  compiled the layout ends at 105 ms instead of 122 and the frame ends
  where it did. So about 100 ms of a start is the app's, and nothing in it
  is worth a round; on a phone, which draws on its GPU, the second part is
  short. The start is left here.
- Run 251's desktop job failed again where the podcast player is checked
  (also run 237; five runs between passed): the file was open, 4,000 ms
  long, and stood at 0 ms not playing. Twice in seven runs is not the
  runner's sound alone any more. The check now writes down how long it
  waited and what mpv says of itself (pause, idle, end of file, the sound
  output), to tell a player that never started from one that was slow.
- **What the kept libraries weigh**: measured in run 253, see "The owner's
  decision" above. The measuring step is out of CI again.
- Run 253's podcast check passed (1,585 ms in), so its new lines said
  nothing yet; they stay for the next time it does not.
- Before that was known: **the starts could not be read.** Installed and compiled, run by run:
  749/708, 759/702, 798/779, 798/752, 974/858 ms. Nothing in rounds 2 to 4
  adds work before the first frame, and run 238's five launches agree with
  each other (925 to 1,048), which is what a slower runner looks like, not a
  slower app; but five runs rising is not proof either way. From the next
  run the comparison starts Android's own Settings cold the same way, as a
  reference for how fast that runner's emulator is, and the app writes down
  where its start went (`StartMarks`: application, graph made, application
  ready, activity, settings read, first frame).

## Next

The rounds a round can decide are done: what is left of the size is the
kept libraries above (the owner's), the app's own screens (3.9 MB of dex,
kept by the same contract's `ui.**`) and the screens' strings (1.3 MB
packed, in the generator's own format). Of the memory after start, about
115 MB, 54 is code mapped in, 31 native heap, 20 private other; less code
is the way to less of the first. The start is the emulator's first draw.

A round worth opening again: when the owner decides on the kept libraries;
when a run's `dex-top.txt` shows a package grown; when the podcast check
fails with its new lines.

## Not done, and why

- Narrowing the plugin contract's kept list: see above.
