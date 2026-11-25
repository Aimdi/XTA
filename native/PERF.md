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
| 235 | `c3257f66` | No Kotlin metadata; libraries' strings in 29 languages | 10,411,798 (−13.7%) | 759 ms | 702 ms | 125,358 kB |

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
  `Intrinsics.check…Parameter/ExpressionValue/ReturnedValue/Field`): result
  pending its first run.
- **No compiler-only files in the APK** (Kotlin's module and builtin tables,
  the coroutines' debug probe, version stamps): result pending its first run.

## Next, in the order the measurements suggest

0. `dex-top.txt` was empty in run 235 (apkanalyzer's lines are not
   tab-separated as the script assumed); fixed to split on blanks.
1. Read `dex-top.txt` of the first run that has it: which packages outside
   the kept list are large (Media3, Commons Compress, navigation's
   internals), and whether the app's own internal code can lose weight.
2. The screens' strings and pictures are 1.3 MB packed in 630 files: what
   the pictures among them are, and whether any is larger than it need be.
3. Memory by kind, once `compare.txt` has it: the picture cache's share, and
   what is made at start that could wait for its first use.
4. Start: what runs before the first frame (`XtaNativeApp.onCreate`,
   `MainActivity.onCreate`, `AppGraph.start`), measured, not guessed.

## Not done, and why

- Narrowing the plugin contract's kept list: see above.
