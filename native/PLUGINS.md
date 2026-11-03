# Downloadable plugins

XTA Native ships as a small app that reads X. Every other network (Reddit,
Bluesky, Mastodon, Threads, Substack, Booru, Pixiv, EhViewer, Instagram,
TikTok, RSS, Hacker News, web sources, Stocks, Immich, Deepmarks, Karakeep)
is a **plugin the reader downloads** from the plugin store, on Android and on
the Linux desktop. Uninstalling removes its code and everything it stored.

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

A Gradle module `native/plugins/<id>/` (Compose Multiplatform library,
android + desktop) holding the network's reading layer (today in
`native/networks/.../<id>/`) and its screens. It implements `XtaPlugin` from
`:plugin-api` and is compiled against the app's libraries without bundling
them: the app provides Compose, Material 3, coroutines, OkHttp, jsoup,
kotlinx-serialization, Coil, `:core`, `:store`, `:services` and the shared
`:ui` components at run time.

Its text comes from Kotlin (a table per language generated at build time from
the plugin's `strings/values*/strings.xml`), not from Android or Compose
resources, so it loads from any class loader.

Each artifact carries `xta-plugin.json`: `id`, `version`, `apiVersion` (the
`:plugin-api` version it was built against) and `entry` (the class
implementing `XtaPlugin`).

## Artifacts and catalogue

CI builds, for each plugin:

| Platform | Artifact | Signed with | Verified by the app |
|---|---|---|---|
| Android | `xta-plugin-<id>-<version>.apk`: code only, never installed, loaded with `DexClassLoader` | the app's signing key | its signing certificate equals the app's own (`PackageManager.getPackageArchiveInfo`) |
| Linux | `xta-plugin-<id>-<version>.jar` | `jarsigner`, the app's key | every entry signed by the app's certificate, whose fingerprint is built into the app |

and publishes them with `plugins.json` (the catalogue) as assets of a rolling
`native-plugins` release:

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
file is made read-only before loading (Android 14 requires it).

## In the app

`:plugin-host` (plain Kotlin) reads the catalogue, downloads, verifies and
keeps plugins under the app's private directory
(`plugins/<id>/<version>/`), knows what is installed, offers updates, and
uninstalls. Loading a class is a platform function it is given (Android:
`DexClassLoader`; desktop: `URLClassLoader`), both with the app's class loader
as parent. A plugin whose `apiVersion` the app does not support is offered as
"needs a newer XTA Native", not loaded.

`:plugin-api` is the contract: `XtaPlugin` (id, category, brand colour, icon,
title and description, home tab and feed-strip screens, client screen,
settings screen, search, the links it opens, its routes, its backup sections,
what it stores, uninstall) and `PluginHost`, what the app gives a plugin
(HTTP, its own `StoreDir`, settings, navigation to posts, profiles, media and
links, the shared post, media and list components, the X session, services,
and XTA's published repairs: the entries of `repairs.json` the app keeps, from
which the plugin's clients take their fragile values, see `core/repairs`).

The store screen lists installed plugins, then what is on offer by category,
with install, update and uninstall, as XTA's store does.

## Release builds

The Android app is shrunk with R8, which would rename what plugins call. The
app keeps, unrenamed, every class a plugin may use (`:plugin-api`, `:ui`'s
shared components, `:core`, `:store`, `:services` and the libraries above).
The APK grows by what that keeps; it is measured in CI.
