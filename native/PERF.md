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
| 236 | `ca644027` | No parameter null checks; no compiler-only files | 10,288,232 (−14.7%) | 798 ms | 779 ms | 119,770 kB |

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
  strings in 30 folders). Each table is now one packed line. Result pending
  its first run.

## Next, in the order the measurements suggest

1. Run 236's code by package (dex bytes): the app 7.04 MB, of which
   `ui` 5.87 MB (`ui.generated.resources` 2.19 MB before packing, `ui.home`
   0.41, `ui.subscriptions` 0.31, `ui.plugins` 0.31, `ui.settings` 0.29,
   `ui.saved` 0.28, `ui.post` 0.27), `store` 0.45, `core` 0.35; Compose 4.69
   MB (kept whole by the plugin contract); Media3 0.84; Kotlin 0.59; kotlinx
   0.53; OkHttp 0.25; navigation 0.23; Coil 0.21; jsoup 0.19.
2. `androidx.recyclerview` (99 kB) and Media3's own player controls are in
   the APK only because the offline library plays a kept video in Media3's
   `PlayerView` (`ui/offline/OfflineVideo.android.kt`): playing it in the
   app's own player would drop them, their drawables and their layouts.
3. Memory by kind, once `compare.txt` has it: the picture cache's share, and
   what is made at start that could wait for its first use.
4. Start: what runs before the first frame (`XtaNativeApp.onCreate`,
   `MainActivity.onCreate`, `AppGraph.start`), measured, not guessed.

## Not done, and why

- Narrowing the plugin contract's kept list: see above.
