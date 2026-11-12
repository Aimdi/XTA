# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**XTA** is a privacy-focused, **read-only** reader for X and other networks,
for Android and the Linux desktop. The app is **XTA Native**, in `native/`:
Kotlin, with one set of Compose Multiplatform screens for both platforms. It
replaced XTA's Flutter app (forked from QuaX, Quacker and Fritter), which was
deleted from the tree; its sources are in git history (the last commit that
has them is `fab7619b`), and many comments under `native/` still name the
Dart file a piece was ported from. Those comments are history, not paths to
open.

Accounts are used to *read*. Follows, likes, saves, notes and groups stay on
the device. There are no trackers; the app talks to reverse-engineered APIs.

## Hard Rules (do not violate)

- **Read-only towards every network.** Never add posting, replying, quoting,
  reposting, liking, voting, following, messaging or account-settings
  write-back on X or any other network. Local actions (device likes, saved
  folders, local follows, notes) are fine and must not be wired to a
  network's write endpoints. The only exceptions, as in XTA: Pixiv bookmarks,
  follows and watchlists once the reader signs in with Pixiv, and sending to
  the reader's own Karakeep, Deepmarks, Immich or WebDAV server.
- **Never bump pinned dependencies** (`native/gradle/libs.versions.toml`:
  Kotlin 2.1.21, Compose Multiplatform 1.8.2, AGP 8.7.3, lifecycle,
  navigation, Coil; the libraries the plain modules name; the voice engine's
  pins in `VoiceEngine.kt`). They are load-bearing.
- **The plugin API is a contract** with plugins already installed (see below).
- **No UI text in Kotlin**: every string the reader sees is a resource,
  translated into all 29 languages (see Translations).
- **The published documents at the root are read by installed apps** from
  `raw.githubusercontent.com/Aimdi/XTA/HEAD/…`: `endpoints.json`,
  `repairs.json`, `sources.json`, `sources/` and `plugins.json`. Keep their
  formats compatible, and never delete them.

## Modules (`native/settings.gradle.kts`)

| Module | What it is |
|---|---|
| `:core` | Plain Kotlin: the X client (`core/x`: transport, endpoints, signing, parsers, account health), JSON (`J`), feeds, the session, backup import, published repairs |
| `:networks` | Plain Kotlin reading layers shared by plugins (RSS, readers) |
| `:store` | Plain Kotlin: saved posts, offline, downloads, reading positions, settings, backup, antennas, follows, recent searches |
| `:services` | Plain Kotlin: articles, links, translation, AI, speech text and the voice engine |
| `:plugin-host` | Plain Kotlin: the plugin catalogue, download, verification, loading |
| `:ui` | Compose Multiplatform screens for both apps (`androidMain`, `desktopMain`, `commonMain`) |
| `:desktop` | The Linux app (Compose Desktop) |
| `:app` | The Android app, a shell around `:ui`; joins the build only where an Android SDK is installed |
| `:plugins:<id>` | Each downloadable plugin, one folder under `native/plugins/` |
| `:mock-x` | `native/tool/mock-x`: a stand-in for X the emulator and desktop checks read; in no app |

The app id is `com.aimdi.xtanative`; JDK 17.

## Guides

- `native/SCREENS.md`: how a screen is built (a `ViewModel` with one immutable
  state; decisions in plain Kotlin, tested in `ui/src/desktopTest`), text and
  languages.
- `native/PLUGINS.md`: the plugin API, a plugin module, the catalogue, CI.
- `native/PORTING.md`: how a network's reading layer is written (`XHttp`,
  `J`, typed failures, fixtures, live tests).
- `native/tool/README.md`: checking without Gradle, where Gradle cannot reach
  Maven (`jvmcheck.sh`, `uilogic.sh`, `uicheck.sh`, `plugincheck.sh`,
  `plugin_abi.py`, `belarusian_latin.py`, `voice_engine.py`).
- `native/RELEASE.md` / `native/PRERELEASE.md`: the notes a release and a
  prerelease publish.
- `native/PARITY/`: the record of the port from the Flutter app.

## Parsing reverse-engineered APIs

Responses change without notice. Parse with `J`
(`core/.../json/J.kt`): `J.parse(body)["data"]["legacy"]["favorite_count"].int`
is null when any step is missing, so the deep path is the safe path. A
missing field is null or empty, never a crash. A failed request is a typed
error (`XErrors.kt`, `NetworkRequestException`); the screens show it with
their own error states.

When X changes something, open `core/.../x/XEndpoints.kt` (a rotated query
id; `endpoints.json` at the root repairs it without a release), the parsers
under `core/.../x/parse/`, or `core/.../x/signing/` (request signing).

## The plugin API

`PLUGIN_API_VERSION` and `OLDEST_PLUGIN_API` live in
`ui/.../ui/plugins/XtaPlugin.kt`. Add to the API (new members with defaults,
new classes) rather than change what is there. Any change a built plugin
would notice, and any addition once a release or prerelease has shipped the
current version, means `PLUGIN_API_VERSION` goes up. A change that leaves the
published plugins behind moves `OLDEST_PLUGIN_API` up with it. CI's desktop
job checks both with `tool/plugin_abi.py`. Plugins are signed with the
app's key and verified before loading; never weaken that.

## Translations

Strings live in `ui/src/commonMain/composeResources/values*/strings_<area>.xml`
and each plugin's `strings/values*/strings.xml`, in 29 languages. Add the
English first, then every other language, naturally worded; run
`python3 -I native/tool/belarusian_latin.py` for Latin Belarusian
(`values-be-rXL`). The `/translate` skill has the details.

## Verifying Changes

```bash
cd native
./gradlew :core:test :networks:test :store:test :services:test :plugin-host:test :plugins:test :mock-x:test
./gradlew :ui:desktopTest :desktop:createDistributable
./gradlew :app:assembleRelease     # with an Android SDK
```

Without Gradle's network, use the scripts in `native/tool/README.md`.

CI is `.github/workflows/native-prototype.yml` ("XTA"): `build` (tests, APK,
Android plugins), `live` (live reads of X and the other networks, also daily),
`screens` (every screen on an emulator against `:mock-x`), `desktop` (the
Linux app, its plugins, tarball and Arch package, run under a virtual
display), `skills`, and the publishing jobs. A commit message with
`[prerelease]` publishes a prerelease; one with `[release]` publishes a
release marked latest, versioned by `native/VERSION`.

## Skills

Skills live in `.claude/skills/`: `translate`, and the design skills
`impeccable` and `ui-ux-pro-max` (read `docs/xta-design-skills.md` first; they
never override a rule above). `bash native/tool/skills/check_skill_sync.sh`
and `python3 native/tool/skills/check_design_skills.py` check them; CI runs
both.

## Coding Style

- Small immutable data classes, pure functions, `when` over `if` chains,
  collections over loops. Keep functions short and responsibilities split.
- KDoc on public types and functions saying what they are and why; no
  comments that repeat the code.
- Before copying code, consider refactoring; ask the owner first.

## Commits

End every commit message with:

```
Co-Authored-By: Claude <noreply@anthropic.com>
```

followed by the `Claude-Session:` line when the session gives one. Never put
a model name in a commit message or trailer, and never put secrets anywhere
in the repository.
