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
  emulator: the median of five cold starts as installed, the same after
  Android has compiled the app (`speed-profile`), and memory once the first
  screen is up, in total (PSS) and by where it is (Java heap, native heap,
  code, graphics).

The emulator is slower than a phone and its numbers move a few percent from
run to run: a change smaller than about 3% in start time or memory is noise
until a second run agrees. APK bytes are exact.

## Measurements

| Run | Commit | What changed | APK bytes | Cold start | Compiled start | Memory (PSS) |
|---|---|---|---|---|---|---|
| 230 | `e76d1efc` | Baseline | 12,067,121 | 749 ms | 708 ms | 125,108 kB |

At the baseline the two dex files are 9,383,959 bytes packed (78% of the
APK) and the resource table 659,364.

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
  annotation from every class. Result: pending its first run.
- **The libraries' strings in the app's 29 languages only**
  (`resourceConfigurations`): result pending its first run.

## Next, in the order the measurements suggest

1. Read `dex-top.txt` of the first run that has it: which packages outside
   the kept list are large (Media3, Commons Compress, navigation's
   internals), and whether the app's own internal code can lose weight.
2. Kotlin's parameter null checks (`-assumenosideeffects` on
   `Intrinsics.checkNotNullParameter` and its kin, never `checkNotNull`,
   which is `!!`): smaller code and fewer calls, at the price of a later,
   vaguer crash where Java hands Kotlin a null.
3. Memory by kind, once `compare.txt` has it: the picture cache's share, and
   what is made at start that could wait for its first use.
4. Start: what runs before the first frame (`XtaNativeApp.onCreate`,
   `MainActivity.onCreate`, `AppGraph.start`), measured, not guessed.

## Not done, and why

- Narrowing the plugin contract's kept list: see above.
