# Downloadable on-device voice for reading aloud

Status: research done and engine seam in place (`SpeechEngine`). The native
dependency is **not added yet** because no Android SDK was available to build
and check it (see "Build verification").

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

## 5. Design (prepared, not yet implemented)

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

Do that before adding the dependency, using the steps in `AGENTS.md`
("Verifying the environment"): `flutter build apk --debug`, then a
`--split-per-abi` release build, then compare sizes.
