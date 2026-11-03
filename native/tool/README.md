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
  plain modules.

What it cannot check:

- `androidMain` and anything Android-only: there is no Android SDK or Google
  Maven here. Build the APK in CI for that.
- `commonMain` code that uses a JVM-only API: both fragments see the JVM
  classpath. For `:ui` that is by design, since its only targets are JVM ones.
- Behaviour. The generated `Res` finds no files and the stand-in androidx jars
  are only close to Google's, so the output is for compiling against, not for
  shipping or running.
