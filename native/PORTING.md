# Porting XTA's networks to XTA Native

XTA Native is XTA's reader rewritten in Kotlin: Android first (Jetpack Compose),
with the same UI built for the Linux desktop through Compose Multiplatform. This
guide is for porting one of XTA's networks (`lib/plugins/<name>/` in the Flutter
app) into `native/networks`.

## What a port is

The network's **reading layer** in plain Kotlin: the client that asks the
network for things, the models it answers with, the parsers between the two,
and their tests. No screens yet: those come once the UI is multiplatform, and
they will be built on what you write here.

Port everything XTA's plugin *reads*: feeds, profiles or channels, single posts
with their comments or replies, search, media and their renditions, and
pagination. Leave out what XTA stores on the phone (saved posts, likes, notes,
settings): that becomes shared later.

## Rules

- **Read-only.** Nothing is posted, liked, voted on, followed, subscribed to or
  messaged on the network. Following happens in XTA Native alone.
- **Plain Kotlin on the JVM.** No Android APIs. Coroutines (`suspend`) for
  anything that goes over the network.
- **Your package only.** Write under
  `networks/src/main/kotlin/com/aimdi/xtanative/networks/<name>/`, tests under
  `networks/src/test/kotlin/com/aimdi/xtanative/networks/<name>/`, fixtures
  under `networks/src/test/resources/<name>/`. Do not edit anything else (core,
  build files, workflows, other packages); if you need a change there, say so in
  your report.
- **HTTP through `XHttp`** (`core/.../x/http/XHttp.kt`): take it as a
  constructor parameter, so tests pass a fake that answers from fixtures.
  `OkHttpXHttp` is the real one. It has GET, an empty POST and a POST with a
  body.
- **Parse without throwing.** Responses are reverse-engineered and change
  without notice. Use `J` (`core/.../json/J.kt`): `J.parse(body)["data"]["x"].string`
  is null when any step is missing. A missing field is null or empty, never a
  crash. For HTML use jsoup.
- **Failures are typed.** A non-success answer is a
  `NetworkRequestException(network, code, message)`; no connection stays the
  `IOException` OkHttp throws. A list read returns `Page<T>(items, next)`.
- **Faithful to XTA.** Same endpoints, headers, query parameters, fallbacks and
  quirks as the Dart code, unless the Dart code is clearly wrong; then fix it
  and say so in a test name and your report.

## Style

Like `native/core`: small immutable data classes, short functions, `when` over
`if` chains, collections over loops. KDoc on public types and functions saying
what they are and why, in plain words; no comments that repeat the code.

## Tests

`kotlin.test` on JUnit 5. For each response shape, a test that parses a fixture
and checks the fields that matter. For each client call, a test with a fake
`XHttp` that checks the URL, parameters and headers sent, and the pagination.
Port the rules XTA's own tests encode (`test/` in the Flutter app; grep for the
plugin's name). Fixtures come from XTA's `test/fixtures/` where they exist;
copy only what you use and keep them small.

A live test, if the network is readable without an account: `<Name>LiveTest`,
skipped unless `RUN_LIVE=true` (see `core/.../live/XLiveTest.kt`). CI's live job
runs every `*LiveTest`; it never blocks a build.

## Checking your work

Gradle cannot reach Maven Central from every machine, so compile and test with
the standalone compiler:

```bash
KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/jvmcheck.sh networks com.aimdi.xtanative.networks.<name>
```

Everything you hand back compiles and its tests pass.

## After the port: the plugin

A ported network becomes a downloadable plugin (`PLUGINS.md`) once its screens
are written: its package moves, unchanged, from `networks/` to
`plugins/<name>/src/commonMain/kotlin/com/aimdi/xtanative/networks/<name>/`
(tests to `plugins/<name>/src/desktopTest/kotlin/...`, fixtures to
`plugins/<name>/src/desktopTest/resources/<name>/`), and the plugin's entry
class and screens come beside it. "Moving a network into a plugin" in
`PLUGINS.md` has the steps; `plugins/bluesky` is the first. There it is
checked with

```bash
KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/jvmcheck.sh plugins/<name>
```

A port keeps to its own package so that the move is a rename of folders.
