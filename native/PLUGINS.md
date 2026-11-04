# Downloadable plugins

XTA Native ships as a small app that reads X. Every other network (Reddit,
Bluesky, Mastodon, Threads, Substack, Booru, Pixiv, EhViewer, Instagram,
TikTok, RSS, Hacker News, web sources, Stocks, Immich, Deepmarks, Karakeep)
is a **plugin the reader downloads** from the plugin store, on Android and on
the Linux desktop. Uninstalling removes its code and everything it stored.
Bluesky is the first (`plugins/bluesky`); the others still read from
`networks/` until each moves (see [Moving a network into a plugin](#moving-a-network-into-a-plugin)).

It keeps the rules of XTA's own plugin store (`lib/plugins/plugin.dart`,
`plugin_catalogue.dart`, `lib/settings/_plugin_store.dart`):

- A **catalogue** published from this repository says what is offered. It can
  withdraw a plugin (`available: false`) without a release; it never takes
  away one that is installed.
- **Installed** plugins stay installed and keep working whatever the
  catalogue says, or when it cannot be reached.
- **Uninstall** erases the plugin's storage, settings, home tab and group
  memberships, as XTA's `uninstall` does.
- Private plugins appear only when "show private plugins" is on.
- Plugins only read their network; nothing is posted there.

## What a plugin is

A Gradle module `native/plugins/<id>/` (a Kotlin Multiplatform library with
Compose, android + desktop) holding the network's reading layer (package
`com.aimdi.xtanative.networks.<id>`, as it was in `networks/`) and its screens
and entry class (package `com.aimdi.xtanative.plugins.<id>`). The entry class
extends `XtaPlugin`, the plugin API in `:ui`'s package
`com.aimdi.xtanative.ui.plugins`. A plugin is compiled against the app's
libraries without bundling them: the app provides Compose, Material 3,
coroutines, OkHttp, jsoup, kotlinx-serialization, Coil, `:core`, `:store`,
`:services`, `:networks`' shared types and `:ui` (the plugin API and the shared
components) at run time.

Its text comes from Kotlin (a table per language generated at build time from
the plugin's `strings/values*/strings.xml`), not from Android or Compose
resources, so it loads from any class loader.

Each artifact carries `xta-plugin.json`: `id`, `version`, `apiVersion` (the
plugin API version it was built against, `PLUGIN_API_VERSION`) and `entry`
(the class extending `XtaPlugin`).

## The plugin API

`PLUGIN_API_VERSION` is 1. Changing anything below in a way a plugin already
built would notice means a new version; the app loads only plugins built
against one it supports.

`XtaPlugin`, what a plugin is:

| Member | What it is |
|---|---|
| `id` | as in the catalogue and `xta-plugin.json`: `bluesky` |
| `brandColor`, `icon` | its mark (an `ImageVector`) and the colour it is drawn in |
| `category`, `isPrivate` | the store's group for it; offered only with private plugins shown |
| `title(language)`, `description(language)` | its name and one line, in a BCP 47 language |
| `attach(host)`, `detach()` | given its `PluginHost` once, before anything else; stopped when uninstalled or updated |
| `homeTab` | its timeline on Home's strip beside For you (a `PluginBody`), or none |
| `clientTab` | what it shows opened on its own from the store, when not its home tab |
| `settings` | its settings screen body, or none |
| `search` | a `PluginSearch` (`hint(language)`, `Results(query, frame)`), or none |
| `routeFor(url)` | the route of its own screen for a link (a post, a profile), or null |
| `screen(route)` | its own whole screen for a route (`PluginScreen`), or null |
| `backupSections` | `PluginBackupSection`s (name, export, import) of what it keeps |
| `footprint()` | what it keeps on the device, counted in the store |

A `PluginBody` is drawn in a `PluginFrame`: `padding` (the list's content
padding, clear of the app's bars), `toTop` (grows when the reader asks for
the top: `ScrollsToTop`) and `refresh` (grows when the reader asks for it to be
read again, the desktop's F5: `RefreshesOn`).

`PluginHost`, what the app gives a plugin:

| Member | What it is |
|---|---|
| `http` | HTTP through the app's client and connection pool |
| `store` | its own `StoreDir` (`plugin-data/<id>/`), erased on uninstall |
| `language` | the reader's language, a BCP 47 tag |
| `online` | whether there is a connection |
| `x`, `session` | the app's X client and the X session (accounts and follows imported from XTA) |
| `repairs` | XTA's published repairs in force (`repairs.json`), which its clients take fragile values from (`core/repairs`) |
| `open(route)`, `back()` | its own screen pushed on the app's back stack; back |
| `openLink(url)` | a link, opened as the app opens links (a plugin's screen, X's, else the web) |
| `openPost(id)`, `openProfile(screenName)` | X's post and profile screens |
| `openMedia(media, start)` | the media viewer for `MediaItem`s |
| `openSearch(query)`, `openSettings()` | its own search and settings |

The shared pieces, in the same package, drawn in the app's theme:
`NetworkPostCard`, `NetworkFocalPost`, `QuotedNetworkPost` (a `NetworkPost`),
`MediaTiles`, `PagedItems` with `PagedList`, `NetworkProblemView`,
`problemOf`, `MessageText`, `PluginScaffold`, `rememberKept` (state kept per
screen across recompositions), and `PluginStrings` with `pluginString` and
`pluginPlural`. `:ui`'s `ui.common` (avatar, hairline, loading row, times) is
there too.

## A plugin module

```
native/plugins/<id>/
  build.gradle.kts        one comment: plugins/build.gradle.kts builds every plugin alike
  plugin.json             {"id": "<id>", "version": "1.0.0", "entry": "<class>", "category": "social", "private": false}
  strings/values/strings.xml       English: plugin_name, plugin_description, the screens' text
  strings/values-de/strings.xml    each other language
  src/commonMain/kotlin/com/aimdi/xtanative/networks/<id>/   the reading layer
  src/commonMain/kotlin/com/aimdi/xtanative/plugins/<id>/    the screens and the entry class
  src/desktopTest/kotlin/...      tests (JUnit 5, plain Kotlin), resources in src/desktopTest/resources/<id>/
```

`settings.gradle.kts` includes every folder of `plugins/` with a build file as
`:plugins:<id>`. `plugins/build.gradle.kts` configures each one: the
multiplatform and Compose plugins, the provided modules as `compileOnly`,
`Strings` generated from `strings/` into the entry class's package
(`text("name")`, `plural("name")`), and these tasks:

| Task | Makes |
|---|---|
| `:plugins:<id>:pluginJar` | `build/xta-plugin/desktop/xta-plugin-<id>-<version>.jar`, `xta-plugin.json` at its root, signed with `jarsigner` |
| `:plugins:<id>:pluginApk` | `build/xta-plugin/android/xta-plugin-<id>-<version>.apk`, code only, `xta-plugin.json` in its assets, signed like the app (only where an Android SDK is installed) |
| `:plugins:<id>:pluginEntry` | `build/xta-plugin/<id>.json`, the plugin's catalogue entry (names and descriptions in each language) |
| `:plugins:desktopPlugins`, `:plugins:androidPlugins` | every plugin's file and entry in `plugins/build/out/desktop` or `android` |
| `:plugins:test` | every plugin's `desktopTest` |

Both files are signed with the release key when `KEY_PATH` (with
`KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) names it, else with the debug
key (`~/.android/debug.keystore`, made by `:signingKey` when missing), as the
app is. `-PpluginBuild=<n>` numbers a build: 1.0.0 becomes 1.0.0.<n>, newer
than every build published before it. A folder without `plugin.json` is a
network whose screens have not come yet: its reading layer builds and is
tested, and nothing is packaged.

## Artifacts and catalogue

CI builds, for each plugin:

| Platform | Artifact | Signed with | Verified by the app |
|---|---|---|---|
| Android | `xta-plugin-<id>-<version>.apk`: code only, never installed, loaded with `DexClassLoader` | the app's signing key | its signing certificates equal the app's own (`PackageManager.getPackageArchiveInfo`) |
| Linux | `xta-plugin-<id>-<version>.jar` | `jarsigner`, the app's key | every entry signed by the certificate whose SHA-256 the desktop build bakes in (`:desktop:pluginCertificate`, from the same key) |

and publishes them with `plugins.json` (the catalogue, made by
`tool/plugin_catalogue.py` from the entries) as assets of a rolling
`native-plugins` release, which is never the latest release:

```json
{"apiVersion": 1, "plugins": [{
  "id": "reddit", "version": "1.0.3", "apiVersion": 1, "available": true,
  "private": false, "category": "social",
  "name": {"en": "Reddit", "de": "Reddit"}, "description": {"en": "..."},
  "android": {"url": "...apk", "sha256": "...", "size": 412345},
  "desktop": {"url": "...jar", "sha256": "...", "size": 398765}
}]}
```

Installing checks the size and SHA-256 from the catalogue, then the
signature; a plugin that fails either is deleted, never loaded. On Android the
file is made read-only before loading (Android 14 requires it). A local build
without the release key bakes in the debug certificate and says so; it loads
only plugins built on the same machine.

## In the app

`:plugin-host` (plain Kotlin) reads the catalogue, downloads, verifies and
keeps plugins under the app's private directory
(`plugins/<id>/<version>/`), knows what is installed, offers updates, and
uninstalls. Loading a class is a platform function it is given (Android:
`DexClassLoader`; desktop: `URLClassLoader`), both with the app's class loader
as parent. A plugin whose `apiVersion` the app does not support is offered as
"needs a newer XTA Native", not loaded.

`AppPlugins` (`:ui`, `data/plugins`) runs them beside the app graph: each shell
makes it with its platform (Android's `ApkSignatureVerifier` and
`DexClassLoaderFactory`, the desktop's `JarSignatureVerifier`), loads what is
installed at start and after each install, casts it to `XtaPlugin`, attaches
its `PluginHost`, and erases what it kept on uninstall (its folder, its place
on Home's strip and its top bar's pins, its accounts' group memberships). In
the screens:

- the **store** is the route `plugins`, from Settings and the account menu:
  installed plugins first, then what is on offer by category, with install,
  update and uninstall in XTA's words, progress, failures in plain words, the
  show-private switch, each plugin's settings, and its pin on Home;
- a plugin's **home tab** sits on Home's strip (`LocalHomeNetworks`) once the
  reader pins it there, in the strip's sheet or the store;
- a **link** goes to a plugin's screen before any other rule
  (`LinkScreens.plugin`, asked by the app's `LinkOpener`);
- its **screens** (client, settings, search, its own routes) are pushed on
  the app's back stack under `plugin/<id>/…`, its media in the viewer under
  `plugin-media/…`;
- its **search** is one of Discover's chips and is offered by the search of
  the reader's library;
- its **backup sections** go in the app's full backup, under
  `pluginSections` by name (`PluginBackups`): a restore hands each to the
  running plugin that keeps it, and one for a plugin that is not running
  waits in `backup/kept.json`, carried by every export, until that plugin
  runs and takes it.

A debug or CI build can read another catalogue: `-PpluginCatalogue=<url>` on
Android, `XTA_NATIVE_PLUGIN_CATALOGUE` on the desktop.

## CI

`native-prototype.yml`:

- **build** tests every plugin (`:plugins:test`) and builds the Android
  plugins, with their sizes and SHA-256 in the summary;
- **desktop** builds the desktop plugins with the certificate the app bakes
  in, then installs the first one from a catalogue the runner serves,
  without a tap: `XTA_NATIVE_PLUGIN_INSTALL`, `_PIN` and `_UNINSTALL` name
  plugins to install, pin on Home and uninstall at start, `XTA_NATIVE_OPEN`
  the screen to open (`plugins`, `plugin:<id>`); a screenshot of each start;
- **screens** builds the APK with `-PpluginCatalogue` naming the runner's
  catalogue and walks the chain on the emulator (`tool/plugin_screens.sh`):
  install from the store, open, pin, Home's strip, uninstall; it fails on a
  crash or a plugin that was not loaded and uninstalled;
- **prerelease** (`[prerelease]`) writes `plugins.json` and uploads it with the
  plugins to the `native-plugins` release (`gh release upload --clobber`),
  creating the release when missing and deleting files no build made.

## Checking without Gradle

Where Gradle cannot reach Maven Central:

- `tool/jvmcheck.sh plugins/<id>` compiles the reading layer and runs its
  tests (and fails on a test JUnit would skip);
- `tool/plugincheck.sh <id>` generates `Strings`, compiles `:ui` and then the
  plugin's commonMain with the Compose compiler (`UI_CLASSES` reuses `:ui`'s
  classes from a `uicheck.sh` run).

Neither builds the Android half; CI does.

## Moving a network into a plugin

A port lands in `networks/src/main/kotlin/com/aimdi/xtanative/networks/<id>/`
first, with its tests under `networks/src/test/`. To make it a plugin:

1. `git mv` the reading layer to
   `plugins/<id>/src/commonMain/kotlin/com/aimdi/xtanative/networks/<id>/`, its
   tests to `plugins/<id>/src/desktopTest/kotlin/com/aimdi/xtanative/networks/<id>/`
   and its fixtures to `plugins/<id>/src/desktopTest/resources/<id>/`. The
   package does not change. Nothing in the app may use it any more: what the
   app shares with every network stays in `networks/` (the package
   `com.aimdi.xtanative.networks` itself).
2. Add `plugins/<id>/build.gradle.kts` (one comment) and, once its screens
   come, `plugin.json` and `strings/values*/strings.xml` with `plugin_name`
   and `plugin_description`.
3. Write the entry class in `com.aimdi.xtanative.plugins.<id>`: it extends
   `XtaPlugin`, keeps what it reads with in `attach`, and draws with the
   shared pieces. Bluesky's (`BlueskyPlugin`) is the example.
4. Check it with `tool/jvmcheck.sh plugins/<id>` and `tool/plugincheck.sh <id>`;
   CI builds, signs and publishes it with the others.

## Release builds

The Android app is shrunk with R8, which would rename what plugins call. The
app keeps, unrenamed, every public class a plugin may use (`:ui` with the
plugin API, its `data` package, `:core`, `:store`, `:services`, the shared
types of `:networks` and the libraries above); a network's own package
(`networks.<id>`) is not kept, so what is a plugin is not in the APK and what
is not yet one goes unless the app uses it. The APK grows by what is kept; it
is measured in CI.
