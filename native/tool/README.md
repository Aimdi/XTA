# native/tool

Scripts for checking XTA Native on machines where Gradle cannot reach Maven
Central (its parallel downloads get HTTP 429) or Google Maven.

## Compiling the Compose UI without Gradle: `uicheck.sh`

`uicheck.sh` compiles a Compose Multiplatform module's `commonMain` and
`desktopMain` for the JVM with the standalone `kotlinc` 2.1.21 and its Compose
compiler plugin, against the desktop jars of the libraries the module uses and
the classes of the plain modules it names with `project(":x")` (`:core` for
`:ui`). Errors come out as `file:line:column: error: message`, and the script
exits 1.

**1. Fetch the jars** (once per set of versions; re-runs fetch only what changed):

```bash
KOTLINC=<kotlinc home> COMPOSE_LIBS_HOME=<dir to keep them in> \
  bash native/tool/compose-libs.sh --scan native/gradle/libs.versions.toml
```

`--scan` takes the versions from the catalog (Compose 1.8.2, lifecycle 2.9.1,
navigation 2.9.0-beta03, Coil 3.2.0, icons 1.7.3 today; they are also the
defaults). The script reads Gradle module metadata as Gradle does and downloads
one file at a time from repo1.maven.org, slowing down whenever it answers 429.
It reads `~/.gradle` and `~/.m2` first. A first run is about 140 requests, so
it takes minutes to an hour, depending on how throttled Maven Central is. The
jars land in `$COMPOSE_LIBS_HOME/1.8.2/`, listed in `resolved.txt`.

`classpath.txt` lists every jar, as the module runs. `compile-classpath.txt`
lists only the jars Gradle puts on the module's compile classpath: each
library's API variant, and a POM's compile scope. A library that another one
needs only at run time is left off it. `ui-backhandler` is one, reached only
through navigation; so is `jsoup`, which `:core` declares with
`implementation`. uicheck compiles the module against that list, so an import
that Gradle cannot see fails here too, as it does in CI. Folders filled by an
older compose-libs.sh have no list: uicheck says so and uses every jar.

For the desktop, JetBrains' lifecycle, savedstate, collection and annotation
modules only point at Google's own builds, which are on Google Maven alone.
With `KOTLINC` set, compose-libs.sh builds those 7 jars from the sources
JetBrains publishes on Maven Central for the same androidx code
(`*-from-sources.jar`, about a minute and a half, once). Without `KOTLINC` it
reports them as missing and exits 3.

**2. Compile:**

```bash
KOTLINC=<kotlinc home> COMPOSE_LIBS=$COMPOSE_LIBS_HOME/1.8.2 bash native/tool/uicheck.sh [module dir, default native/ui]
```

`:ui` (38 files) takes 35 to 45 s the first time, with `:core`, and 17 to 25 s
after that, depending on the machine's load: `:core` is kept in
`$COMPOSE_LIBS/.uicheck-cache` until its sources change.

- `commonMain` and `desktopMain` go into one multiplatform compilation with a
  fragment each, as Gradle compiles the desktop target, so `expect`/`actual`
  pairs are checked. K2 reports a missing `actual` only once there are no other
  errors. `SOURCE_SETS="commonMain jvmMain desktopMain"` names another chain.
- `src/*/composeResources` becomes a generated `Res` (strings, plurals, string
  arrays, drawables, fonts), so `stringResource(Res.string.x)` compiles. Its
  package is `RES_PACKAGE` if set, else `packageOfResClass` from the module's
  `build.gradle.kts`.
- The kotlinx.serialization plugin is loaded too, for `@Serializable` routes.
- `KOTLINC_ARGS` adds compiler arguments (`-opt-in=...`), `WARNINGS=1` shows
  warnings, `OUT=<dir>` keeps the classes, `DEPS="core store"` overrides the
  plain modules, `DEPS_OUT=<file>` writes the folders of those plain modules'
  classes there.

What it cannot check:

- `androidMain` and anything Android-only: there is no Android SDK or Google
  Maven here. Build the APK in CI for that.
- `commonMain` code that uses a JVM-only API: both fragments see the JVM
  classpath. For `:ui` that is by design, since its only targets are JVM ones.
- Behaviour. The generated `Res` finds no files and the stand-in androidx jars
  are only close to Google's, so the output is for compiling against, not for
  shipping or running.

## Latin Belarusian: `belarusian_latin.py`

```bash
python3 -I native/tool/belarusian_latin.py [--dry-run]
```

fills `values-be-rXL` (XTA's `be_Latn`, `SCREENS.md`) in the app's resources
and every plugin's strings with each string of `values/` it lacks: XTA's own
Latin where XTA has the string (the same English in `intl_en.arb` and the
same Cyrillic in `intl_be.arb` as `values-be`), its misspelt words written
again from the Cyrillic; otherwise `values-be`'s text in Latin letters, as
XTA's file spells each word's family. It only adds, so run it after strings
come into the other folders, then read what it lists: strings kept as they
are (a name, or English in `values-be`), Russian in `values-be`, and XTA's
Latin set aside for words its Cyrillic lacks (`XTA_OWN_WORDS` keeps one that
is good Belarusian). Correct the result by hand where it needs it: a later run
leaves it as it is.

## The downloadable plugins (`PLUGINS.md`)

`plugincheck.sh` compiles a plugin's Compose code as Gradle would, against
`:ui`: it generates the plugin's `Strings` from `plugins/<id>/strings/` with
the build's own generator functions (run with `kotlinc -script`), compiles
`:ui` with `uicheck.sh` (or takes `UI_CLASSES`, the classes an
`OUT=<dir> uicheck.sh` run left), then the plugin's `commonMain` with
`:core`, `:networks`, `:store` and `:services`:

```bash
KOTLINC=<kotlinc home> COMPOSE_LIBS=$COMPOSE_LIBS_HOME/1.8.2 bash native/tool/plugincheck.sh bluesky
```

The plugin sees what Gradle gives it: `:ui`'s `api` libraries, `:core`'s and
jsoup (compose-libs.sh's `plugin-compile-classpath.txt`), not what `:ui`
keeps to itself (lifecycle, navigation, its resources): a plugin that imports
those fails here as in CI. The jars sit in a folder named after the `:ui`
compiled, so worktrees checking at once do not share one, and a `:ui` that
has not changed reuses the compiled `:core` and the others.

`jvmcheck.sh plugins/<id>` compiles its reading layer (`networks.<id>`) with
`:networks` (and `:store`, for a reading layer that imports it, as Stocks'
keeps XTA's rows) and runs its tests, as `jvmcheck.sh networks` does for a network
still in `networks/`.

`plugin_catalogue.py` writes `plugins.json` from the entries and files the
plugins build made (`--android`, `--desktop`, `--base-url`, `--copy-to`):
CI's prerelease publishes it, and the emulator and desktop checks serve one of
their own. `plugin_screens.sh` is the emulator's walk through the store
(install, open, Home's strip, uninstall), after `screens.sh`.

`plugin_abi.py` says whether plugins built before still link against this
tree: every class, method and field a plugin uses from the app's modules has
to be there with the same descriptor, and every abstract method it takes on
from the app's types (an interface member added since, which Kotlin 2.1
compiles abstract) has to have a body. The app may keep loading an older
`PLUGIN_API_VERSION` only while its published plugins pass:

```bash
OUT=/tmp/ui DEPS_OUT=/tmp/deps.txt KOTLINC=… COMPOSE_LIBS=… bash native/tool/uicheck.sh
python3 -I native/tool/plugin_abi.py --app "/tmp/ui:$(cat /tmp/deps.txt)" xta-plugin-*.jar
```

The JARs are the desktop builds of the `native-plugins` release (the APKs
hold the same classes, dexed); it reads them and runs nothing of them.

## Downloaded voices (`voice_engine.py`, `voice_screens.sh`)

Downloaded voices read with sherpa-onnx's C API through the app's JNI shim
(`services/src/main/c/xta_sherpa.c`), as XTA's `lib/speech/` does. `:app`
builds the shim with the NDK's ndk-build where one is installed; the engine
itself is never in the APK and downloads with the first voice
(`docs/specs/downloadable-voice-engine.md`).

```bash
python3 -I native/tool/voice_engine.py desktop native/desktop/build/voice-engine-resources/linux-x64/voice-engine
python3 -I native/tool/voice_engine.py check <folder of the Android archives>
```

`desktop` puts the Linux engine there (pub.dev's `sherpa_onnx_linux`, from
the pub cache or pub.dev's archive checked against `pubspec.lock`, each
library against `VoiceEngines.LINUX_X64`'s pins) with the shim built by `cc`
against `$JAVA_HOME`'s headers, needing no newer glibc than the engine; the
desktop build carries that folder in `lib/app/resources/voice-engine`, and
`XTA_NATIVE_VOICE_ENGINE=<folder>` points a Gradle run at one. `check` says
whether the archives `scripts/voice_engine_archives.py` makes (as XTA's
releases publish them) hold what `VoiceEngines.PACKAGES` pins.

`voice_screens.sh` is the emulator's voice check, after `screens.sh`, for an
APK built with `-PvoiceServer=<url>` naming a server of the engine's archives
and the English voice: the app's `VoiceCheck` downloads and installs both,
reads a sentence into a clip, reads aloud through the app, and shows a
podcast's media session, whose controls the script presses from the keys, the
lock screen and the notification. The desktop job runs the same check with
`XTA_NATIVE_VOICE_SERVER` and `XTA_NATIVE_VOICE_CHECK=1`.
