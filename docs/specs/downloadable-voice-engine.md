# The voice engine downloads with the first voice

The owner asked for an app that does not carry everything by default, while
keeping every feature. The largest part of the APK that most readers never
use is the engine that runs downloaded voices. This pass takes it out of the
APK and downloads it with the first voice, as the voices already are
(`docs/specs/downloadable-tts.md`).

## Found

The arm64 APK of aimdi188 is 84.5 MB. Its native libraries:

| Library | Bytes | Used by |
| --- | --- | --- |
| `libapp.so` | 28,246,928 | all Dart code |
| `libonnxruntime.so` | 22,249,552 | the voice engine |
| `libmpv.so` | 12,369,680 | video, X included |
| `libflutter.so` | 11,580,816 | everything |
| `libsherpa-onnx-c-api.so` | 4,499,112 | the voice engine |

The two engine libraries are 26.7 MB of the 84.5 MB (32 %), and the only
code that loads them is `lib/speech/sherpa_voice_synthesizer.dart`, which
runs a downloaded voice. Without a voice they are never loaded.

- They come from pub.dev's `sherpa_onnx_android_*` 1.13.8 packages, which
  are pure FFI plugins: prebuilt `jniLibs`, no Java or Kotlin. Nothing loads
  them at startup.
- `libsherpa-onnx-c-api.so` needs `libonnxruntime.so` and nothing else
  outside Android's own libraries. `libonnxruntime.so` needs only Android's.
- `sherpa_onnx`'s `initBindings(dir)` opens `dir/libsherpa-onnx-c-api.so`;
  with no argument it opens the copy in the APK.
- Packed as `.tar.xz`, the engine is 6.0 MB for arm64 (gzip: 9.9 MB).
  Unpacking it with the `archive` package took about 2.2 s on the desktop.

## After

### What the reader sees

- The APK no longer carries the engine: about 58 MB for arm64, where it was
  84.5 MB. The other APKs lose their own copy of the same libraries.
- "Downloaded voices" in the read-aloud settings gains a row, **Voice
  engine**: its version, download size and licences (Apache-2.0, MIT,
  GPL-3.0), and the same states and actions as a voice (Download, progress
  and Cancel, checking and unpacking, on this device with its size and
  Delete, failed with Retry).
- **Downloading a voice brings the engine too** when it is not on the device:
  one tap, the engine first, then the voice. Nothing is downloaded without a
  tap, as before.
- A voice on the device while the engine is not (a reader who downloaded
  voices before this change) says it needs the voice engine, and the engine
  row offers the download. Until then, reading aloud uses the system engine,
  as it does whenever a voice cannot load.
- Deleting the engine asks first and says that downloaded voices cannot read
  until it is downloaded again.
- On a phone whose processor has no published engine (32-bit x86), the
  engine row says it is not available there, and voices cannot be
  downloaded.
- The note under the voices says the engine comes from XTA's GitHub
  releases.

### Where it comes from

- Each release publishes the engine beside its APKs, one archive per
  processor: `xta-voice-engine-sherpa-onnx-1.13.8-<abi>.tar.xz` for
  `arm64-v8a`, `armeabi-v7a` and `x86_64`, each holding
  `libonnxruntime.so` and `libsherpa-onnx-c-api.so` exactly as the pub.dev
  package ships them.
- The app downloads its own release's archive
  (`releases/download/<tag>/…`), and the latest release's when it has no
  tag (a local build) or its own does not answer (`releases/latest/download/…`).
- **The app pins the SHA-256 of each library**, per processor. A library
  that does not match is discarded, whatever served it. The archive itself
  is not pinned: xz's output can differ between versions of xz; the files
  inside cannot.
- `scripts/voice_engine_archives.py` makes the archives from the packages
  pubspec.lock names, checks the libraries against the app's pins, and
  fails the release when they differ. Every workflow that publishes a
  release runs it.

### On the device

- The engine unpacks in a background isolate into
  `<app support>/voice-engine/sherpa-onnx-1.13.8/<abi>/`, through a staging
  folder renamed into place only when both libraries match their pins, as a
  voice does.
- A voice loads it by path: `libonnxruntime.so` first, then
  `initBindings` on the folder, so the second finds the first already
  loaded.
- The Linux build keeps the engine in its bundle; nothing changes there.

## Not in this pass

- The Linux packages: Flutter bundles the engine there, and they are 23–33
  MB.
- Plugin code that downloads on Install: next, measured first
  (`docs/specs/downloadable-plugins.md`).

## Code

- `android/app/build.gradle`: the two libraries left out of every APK.
- `scripts/check_apk_contents.py`: a release APK that carries them fails.
- `scripts/voice_engine_archives.py` (new) and the publishing workflows:
  the archives.
- `lib/speech/voice_engine.dart` (new): the pinned engine per processor and
  where it installs.
- `lib/speech/voice_engine_store.dart` (new): download, check, unpack,
  delete.
- `lib/speech/voice_download_store.dart`: a voice brings the engine; a voice
  without the engine is not offered to read.
- `lib/speech/sherpa_voice_synthesizer.dart`: loading by path.
- `lib/speech/offline_voices_section.dart`: the engine row and the voice
  that needs it.

## Tests

- The engine for each processor, and none for 32-bit x86; the URL for a
  release build and for a local one.
- Installing an archive: both libraries matching their pins install; one
  that does not is refused and nothing is left behind; a damaged archive is
  refused.
- The store: download, cancel, failure, delete; a voice download fetches
  the engine first; a voice without the engine is not offered.
- The settings row in each state, and a voice that needs the engine.
- `scripts/tests`: the archives match the pins; every publisher ships them;
  an APK carrying the engine fails the content check.
- On the CI emulator (Android 14, x86_64),
  `integration_test/voice_engine_test.dart`, run by the voice-engine job of
  `.github/workflows/emulator-qa.yml`: the APK has no engine, the engine
  downloads from a local server, installs, loads from app storage and reads
  a sentence with a downloaded voice.
