# Downloadable on-device voice for reading aloud

Status: **implemented** (branch `offline-voice`) with `sherpa_onnx` 1.13.8 and
two Piper voices. The maintainer accepted the GPL-3.0 espeak-ng in the APK
("Fine with GPL and you have the option inside the app to download it").
Section 7 describes what was built; section 8 lists what still needs an APK
build or a phone.

Reader request: "TTS in Substack doesn't work. Can't I just download a good TTS
into XTA and it just uses this one?"

## 1. Why reading aloud went quiet (fixed separately)

The Substack reader is the only caller of `SpeechStore.speak`. Its text
extraction was fine (title, byline and plain body text, with a fallback to the
live page's `innerText`), and the manifest already declares the
`TTS_SERVICE` `<queries>` entry. The failures were in the speech layer and in
flutter_tts 4.2.5's Android plugin:

| Defect | Effect |
|---|---|
| The store awaited `speak()` with `awaitSpeakCompletion(true)`. On Android the plugin never answers that call when `TextToSpeech.speak` returns `ERROR` (it parks the call until a re-init that never comes), when the utterance reports `onError` (`speakResult` is never completed), or when no engine is bound. | "Reading aloud" bar with silence, forever, and no voice-settings snackbar. |
| Every `FlutterTts()` sets the shared `flutter_tts` channel's handler. Settings → Accessibility → Speech and `TtsSettingsScreen` each made a new one. | After opening the voice settings once, the store stopped getting callbacks for the rest of the session. |
| `setEngine` ran on every Listen, even for the system default. The plugin creates a new `TextToSpeech` each time and never shuts the old one down. | Leaked service connections and a cold engine start on each tap (slow with Sherpa, which loads a model). |
| `setVoice` came before `setLanguage`. Android's `setLanguage` picks that language's default voice. | The voice the reader chose was ignored. |
| The language came from the app locale, not from the article. | A German-UI phone read English Substack posts with a German voice, which sounds broken. |
| No engine installed at all (common on de-Googled ROMs, a likely setup for XTA readers) | Same silent hang as the first row. |

The fix is in `lib/speech/system_speech_engine.dart`. Each utterance is
followed through its start, done and error callbacks. A start watchdog (20 s)
turns a parked or refused utterance into a reported failure. The engine fails
fast when there is no TTS engine, takes the channel callbacks back before
speaking, binds an engine only when the choice changes, sets the voice after
the language, and detects the article's language (`text_language.dart`).
Tests are in `test/speech_engine_test.dart`, which drives a fake flutter_tts
platform channel.

## 2. What a "downloadable voice" must satisfy

- Fully offline synthesis on Android, called from Flutter. No cloud.
- Good German and English voices. Other languages are a bonus.
- Downloaded only when the reader taps Download. Pinned URL, SHA-256 checked,
  stored in app-private storage, deletable.
- Licences that let an MIT, non-commercial, open-source app use (and ideally
  redistribute) both the runtime and the voices.
- Reasonable APK growth, since XTA ships both a universal APK and
  `--split-per-abi` APKs.
- Fast enough on a mid-range phone to keep ahead of playback, sentence by
  sentence.

## 3. Options

### Runtimes

| Option | Package / source | Licence | Maturity | Notes |
|---|---|---|---|---|
| **sherpa-onnx** (k2-fsa) | [`sherpa_onnx` 1.13.8](https://pub.dev/packages/sherpa_onnx) on pub.dev, published 2026-09-11; Android libs from `sherpa_onnx_android_{arm64,armeabi,x86,x86_64}` | Apache-2.0 (runtime), onnxruntime MIT, **espeak-ng GPL-3.0-or-later statically linked** (confirmed: `libsherpa-onnx-c-api.so` contains espeak-ng phonemizer strings and needs an `espeak-ng-data` dir) | Very active. Releases are frequent, and the Flutter API is documented in `lib/src/tts.dart`. | One API (`OfflineTts`) covers VITS/Piper, Matcha, Kokoro, Kitten, ZipVoice, Pocket TTS and Supertonic. `generate()` is synchronous FFI and must run in a background isolate. It returns `Float32List` samples plus a sample rate, and `writeWave` is included. |
| Piper alone | [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl). The original rhasspy/piper was archived in Oct 2025. | GPL-3.0 (new), MIT (archived) | No maintained Flutter/Android binding | Piper voices run in sherpa-onnx anyway, so there is no reason to bind Piper directly. |
| Sherpa "TTS Engine" APK | [k2-fsa APK builds](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html), one APK per voice and ABI | Apache-2.0 + voice licence | Works today, and **XTA already supports it** (engine picker, "Install Sherpa" action) | No code needed. But it is a separate app install of about 80–130 MB per voice ([APK listing](https://huggingface.co/csukuangfj/sherpa-onnx-apk)), and the reader leaves XTA to get it. |
| Android system engines (Google, Samsung, RHVoice, eSpeak) | Installed by the user | Varies | — | Google's engine is not private. eSpeak sounds robotic. This is what XTA uses now. |

Measured native-library cost of `sherpa_onnx_android_arm64` 1.13.8, unpacked
from pub.dev:

| File | Size |
|---|---|
| `libonnxruntime.so` | 22.2 MB |
| `libsherpa-onnx-c-api.so` | 4.5 MB |
| `libsherpa-onnx-cxx-api.so` | 0.4 MB |
| **arm64-v8a total** | **≈ 27 MB uncompressed** (10.5 MB as a .tgz) |

`armeabi-v7a`, `x86` and `x86_64` each add a comparable amount. A universal
APK would grow by roughly 80–100 MB (estimate; only arm64 was measured). An
arm64 split APK grows by about 27 MB, or about 11 MB if the libraries are
stored compressed. The package declares `minSdk 21`, which is compatible.

### Voices (sherpa-onnx packaged models)

All sizes below are from the
[`tts-models` GitHub release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models).
They are `.tar.bz2` archives that include `tokens.txt` and `espeak-ng-data`.

| Voice | Lang | Archive size | Licence notes |
|---|---|---|---|
| `vits-piper-de_DE-thorsten-medium` | de | 64.1 MB (fp32; no int8 build seen in the truncated listing) | Thorsten-Voice dataset **CC0** ([model card](https://huggingface.co/rhasspy/piper-voices/blob/main/de/de_DE/thorsten/medium/MODEL_CARD)). Fine-tuned from the en_US *lessac* checkpoint. |
| `vits-piper-de_DE-thorsten_emotional-medium-int8` | de | 22.4 MB (fp16 40.1, fp32 76.5) | Thorsten-Voice, CC0 dataset |
| `vits-piper-de_DE-karlsson-low-int8` | de | 20.1 MB | Check the model card |
| `vits-piper-de_DE-eva_k-x_low-int8` | de | 12.7 MB | Check the model card; x_low quality |
| `vits-mms-deu` | de | 103 MB | **CC-BY-NC-4.0** (Meta MMS). Lower quality. Reject. |
| `vits-piper-en_US-libritts_r-medium-int8` | en | 22.3 MB | LibriTTS-R dataset (CC-BY-4.0). Multi-speaker. |
| `vits-piper-en_GB-alan-medium-int8` | en | 20.1 MB | Check the model card |
| `vits-piper-en_US-lessac-medium-int8` | en | 20.0 MB (high-int8 33.4) | Lessac/Blizzard 2013 data is **non-commercial only** ([licence](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html), [discussion](https://github.com/rhasspy/piper/discussions/271)) |
| `kokoro-int8-multi-lang-v1_1` | zh, en | 140 MB (fp32 348) | Apache-2.0. Best English quality here. **No German.** |
| `kokoro-int8-en-v0_19` | en | 98.5 MB | Apache-2.0 |

Other models seen but not adopted:

- **Kokoro-82M v1.0** covers en, es, fr, hi, it, ja, pt, zh. It has no
  German. A community German fine-tune exists,
  [Thorsten-Voice/Kokoro](https://huggingface.co/Thorsten-Voice/Kokoro)
  (Apache-2.0), but it is not packaged for sherpa-onnx.
- **Supertonic 3** lists 31 languages including German. Its model licence is
  OpenRAIL-M, which carries use restrictions. sherpa-onnx 1.13.8 has a config
  class for it (`OfflineTtsSupertonicModelConfig`). The size of a packaged
  model was not confirmed. Worth re-evaluating once a packaged build exists.
- **Pocket TTS** (Kyutai) is supported by sherpa-onnx 1.13.8. Language
  coverage beyond English is unconfirmed (only forum reports).

### Speed

No trustworthy phone RTF numbers were found for these exact models. Piper
"medium" (VITS) is consistently described as faster than real time on phones.
Kokoro is reported to be slow on budget hardware (for example the
[VoxSherpa README](https://github.com/CodeBySonu95/VoxSherpa-TTS)). Reading
works sentence by sentence (synthesise the next sentence while the current
one plays), so anything with RTF < 1 is enough. Measure this on a real
mid-range device before choosing defaults.

## 4. Recommendation

**sherpa-onnx (`sherpa_onnx` 1.13.8) with Piper/VITS voices downloaded on
demand.** Start with one German and one English voice:

- `vits-piper-de_DE-thorsten_emotional-medium-int8` (22.4 MB), or
  `thorsten-medium` (64.1 MB) if its quality is clearly better in a listening
  test.
- `vits-piper-en_US-libritts_r-medium-int8` (22.3 MB).

Kokoro (`kokoro-int8-en-v0_19`, 98.5 MB) is an optional "higher quality
English" voice once the speed on mid-range phones has been measured.

Reasons:

- It is the only option that is fully offline, has good German, is
  maintained, and has a first-party Flutter package.
- A voice costs about 20–25 MB, downloaded only on tap, from a stable release
  (`…/releases/download/tts-models/<name>.tar.bz2`) and pinned by SHA-256
  because release assets can be replaced. Hugging Face mirrors (`csukuangfj/…`)
  can be pinned to a commit hash.
- XTA does not redistribute any voice: the reader downloads it directly from
  k2-fsa. That keeps the per-voice licence question (the lessac base
  checkpoint) with the reader's own non-commercial use, and the app shows the
  licence next to each voice.

Decisions needed from the maintainer before merging:

1. **GPL-3.0 espeak-ng in the APK.** XTA's source can stay MIT, but the
   distributed APK then contains GPL-3.0 code, so the binary must be offered
   under GPL-3.0 terms. XTA is open source, so this is doable, but it is a
   licensing change that needs an explicit decision.
2. **APK growth.** About +27 MB for an arm64 split APK, and about 80–100 MB
   for the universal APK. Possible mitigations:
   - ship the offline voice only in split APKs;
   - restrict `abiFilters`;
   - move the voice into a separate optional "XTA Voice" companion. That last
     one is effectively the Sherpa TTS Engine APK, which XTA already supports.
3. Meanwhile, the zero-cost path already works: install k2-fsa's "TTS Engine:
   Next-gen Kaldi" German (Thorsten) APK and choose it in XTA → Settings →
   Speech.

## 5. Design (as first planned; section 7 has what was built)

The seam already exists: `SpeechEngine` in `lib/speech/tts_engines.dart`
(`prepare`, `say`, `stop`, `maxChunkChars`). `SpeechStore` takes
`preferred: [...]` engines ahead of the system engine. It uses the first one
whose `prepare()` succeeds within the timeout, so the system engine remains
the fallback.

Remaining work:

1. **Dependency.** Add `sherpa_onnx: 1.13.8` (exact pin). Declare `archive`
   directly (it is already transitive) for tar.bz2 extraction. `crypto` and
   `media_kit` are already direct dependencies.
2. **`lib/speech/voice_catalog.dart`.** A const list of `OfflineVoice` entries:
   id, language, display name, archive URL, SHA-256, byte size, licence label,
   model kind (vits/kokoro) and file names inside the archive. It is pure and
   tested.
3. **`lib/speech/voice_download_store.dart`.** A `Store<Map<String,
   VoiceInstall>>` with `notInstalled | downloading(progress) | verifying |
   installed | failed`. It streams to a temp file with progress (same pattern
   as `utils/downloads.dart` / the download center), verifies SHA-256 and
   size, then extracts to `getApplicationSupportDirectory()/voices/<id>/`
   (atomic rename). Delete removes the folder. No network access until the
   reader taps Download.
4. **`lib/speech/offline_voice_engine.dart`.** `implements SpeechEngine`:
   - `prepare()` picks the installed voice for `textLanguage` (or the one the
     reader chose) and starts a long-lived isolate holding
     `OfflineTts(OfflineTtsConfig(model: OfflineTtsModelConfig(vits: …
     dataDir: <voice>/espeak-ng-data), numThreads: 2))`.
   - `maxChunkChars` is about 300, so `chunkForSpeech` hands it single
     sentences.
   - `say()` asks the isolate for samples, writes a WAV (`writeWave`) to the
     cache, plays it with a `media_kit` `Player` and completes on
     `stream.completed`. Synthesis of the next chunk is pipelined while this
     one plays.
   - `stop()` stops the player and drops queued work.
5. **Settings.** Add a "Downloaded voices" section to `TtsSettingsScreen`:
   - per language, the voice name, size and licence;
   - Download (with progress) and Delete;
   - an "Use downloaded voice" engine option next to "System default" and
     "Sherpa".
   It is a Store-driven section (no `setState`), and all strings go into the
   29 ARB files.
6. **Wiring.** In `main.dart`: `SpeechStore(preferred:
   [OfflineVoiceEngine(...)])`. Add a pref `tts.offline_voice`.
7. **Tests.** Cover:
   - catalog integrity (unique ids, https URLs on the pinned host, 64-hex
     checksums);
   - download store (progress, checksum mismatch → failed and file removed,
     delete);
   - offline engine with a fake synthesiser and player (order, stop,
     fallback to the system engine when `prepare` fails).

## 6. Build verification

No Android SDK was present in the environment (`/opt/android-sdk`,
`~/android-sdk` and `ANDROID_HOME` were all absent), so neither
`flutter build apk` nor Gradle resolution could run. The plugin ships prebuilt
`.so` files in `jniLibs`, so it does not need an NDK build. It still has to be
checked for:

- AGP/Gradle compatibility with `compileSdk 37`;
- duplicate `libc++_shared.so` with media_kit;
- the real APK size.

Do that before adding the dependency, using the steps in `CLAUDE.md`
("Verifying Changes"): `flutter build apk --debug`, then a
`--split-per-abi` release build, then compare sizes.

## 7. Implementation

### Voices (pinned)

Archives were downloaded from the URLs below on 2026-10-09 and hashed with
`sha256sum`; sizes are the downloaded byte counts. Each one was then unpacked
and synthesised with the app's own code (`installVoiceArchive` and
`SherpaVoiceSynthesizer`) against sherpa-onnx 1.13.8's Linux x64 libraries.

| Id | Archive (`https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/…`) | Bytes | SHA-256 | Licence | Speaker |
|---|---|---|---|---|---|
| `de-thorsten-emotional` | `vits-piper-de_DE-thorsten_emotional-medium-int8.tar.bz2` | 23479221 | `e522bea5bb42d8f572b85a39ccb2d7ec114d6c2838c198827aadaeea3d31822a` | CC0-1.0 (Thorsten-Voice dataset, per `MODEL_CARD`) | 4 = "neutral" (0 is "amused") |
| `en-libritts-r` | `vits-piper-en_US-libritts_r-medium-int8.tar.bz2` | 23398348 | `7e4552e239988f4896872822b56e99e0e9e00958164e3f6bdf5ee14391fbe829` | CC-BY-4.0 (LibriTTS-R; fine-tuned from lessac) | 0 |
| (not offered) Kokoro | `kokoro-int8-en-v0_19.tar.bz2` | 103248205 | `c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd` | Apache-2.0 | 0 |

Synthesis speed on a 4-core x64 desktop (2 threads, one 6 s sentence):
the Piper voices took about 0.18–0.22× real time, but Kokoro took 1.0×. On a
phone Kokoro would leave pauses between sentences, so it is not in the
catalog. The loader supports it (`VoiceModelKind.kokoro`); adding the row
above to `offlineVoiceCatalog` is all that is needed once a phone measurement
shows it keeps up.

Unpacked, each Piper voice takes about 40 MB (the model plus the full
`espeak-ng-data`); unpacking took about 4 s on the desktop.

### Code

| File | Role |
|---|---|
| `lib/speech/offline_voice_catalog.dart` | The pinned catalog (pure data) and `pickOfflineVoice`. |
| `lib/speech/voice_archive.dart` | SHA-256 check, streaming bzip2 → tar extraction into `<voices>/.<id>.partial`, path checks (no `..`, absolute paths or links), a required-file check, then an atomic rename to `<voices>/<id>`. It runs in `Isolate.run`. |
| `lib/speech/voice_download_store.dart` | `VoiceDownloadStore` (flutter_triple). Its states are absent, downloading (bytes), installing, ready (bytes on disk) and failed (network, checksum or archive). It downloads through the app's `DownloadTransfer` into `<appSupport>/xta-download-staging/` and supports cancel and delete. It never downloads on its own. |
| `lib/speech/offline_speech_engine.dart` | `OfflineSpeechEngine implements SpeechEngine, SpeechLookahead`. It reads only when "Use a downloaded voice" is on and an installed voice matches the article language (or the app language if that is unknown). Otherwise `prepare` returns false and `SpeechStore` falls back to the system engine, which also happens when a voice fails to load. |
| `lib/speech/sherpa_voice_synthesizer.dart` | A long-lived isolate holds `OfflineTts` and answers `(text, speed)` with a WAV path (`generate` + `writeWave`). Its `sherpaConfigFor` builds the VITS/Kokoro config. |
| `lib/speech/clip_player.dart` | Plays clips through the podcast player's `ContinuityPlayer` port (media_kit), with a start watchdog. |
| `lib/speech/offline_voices_section.dart` | The "Downloaded voices" section at the top of the read-aloud settings. It has the switch, each voice with its language, size and licence, Download (progress + cancel) and Delete (with a confirmation), and a note on where the voices come from. |
| `lib/speech/offline_speech_setup.dart` | Production wiring (pref, clip folder in the cache dir). |

`SpeechStore` calls `upcoming(next chunk)` right after `say(chunk)`, so the
next sentence is synthesised while the current one plays. The offline engine
uses 300-character chunks. The reader's speed (flutter_tts scale, 0.5 =
normal) maps to sherpa `speed = rate × 2`, clamped to 0.5–2.

### Licences

- `sherpa_onnx` (Apache-2.0) appears on Flutter's licence page from its
  package `LICENSE`, like every other package.
- `libsherpa-onnx-c-api.so` statically links espeak-ng. Its strings include
  `phontab`, `phondata` and "Wrong version of espeak-ng-data", and it has no
  `NEEDED` entry for a separate espeak library. `main.dart` therefore
  registers `assets/licenses/espeak-ng.txt` (a short note plus espeak-ng's
  `COPYING`, GPL-3.0) with `LicenseRegistry`, and the README says the APK is
  distributed under the GPL-3.0's terms. The "Released under the MIT licence"
  line on the About page still describes the source only; the maintainer may
  want to reword it.
- Voices are not bundled. Each one's licence is shown next to it.

## 8. Still to verify (CI / device)

- `flutter build apk --debug` and the `--split-per-abi` release build with
  the new plugin. `sherpa_onnx_android_*` ship only prebuilt `jniLibs` (no
  NDK build, no `libc++_shared.so`, so there is no clash with media_kit). Each
  declares `minSdk 21` (below `flutter.minSdkVersion`) and `compileSdk 34`,
  and puts AGP 7.3.0 on its own buildscript classpath. No change to
  `android/app/build.gradle` was needed. Release builds pass
  `--target-platform android-x64,android-arm,android-arm64`, which leaves out
  the x86 libraries.
- APK size growth (estimated: about +27 MB arm64 split, about 80 MB
  universal).
- On a phone: download → install → read a German and an English Substack
  post. Check the sentence gaps, stop from the speech bar, falling back to
  the system engine for a French post, and media_kit playback of the WAV
  clips next to a paused podcast.

