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
| `feed` | a `PluginFeed`: its followed accounts' posts for Home's timelines and the groups ([below](#posts-in-homes-timelines)), or none |
| `postActions` | where its posts lead where the app draws them (Home, groups, Saved), with their bookmark and menu, or none |
| `archived(content)` | one of its posts the archive kept, read back as a `NetworkPost` for Saved ([below](#saved-notes-and-likes)), or null |
| `cashtagStrip` | what it draws under an X post that names tickers (Stocks' price strip), or none |
| `routeForCashtag(symbol)` | its own screen for a cashtag tapped in an X post (Stocks' ticker), or null: the tap searches X |

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
| `settings` | the app's settings, all to read and its own (`plugin.<id>.*`) to change; `inHomeFeed()`, `setInHomeFeed(on)` |
| `saved`, `toggleSaved(post)`, `chooseFolder(post)`, `editNote(post)`, `setLiked(post, liked)` | the reader's archive: what is in Saved, the bookmark, the save sheet, the note, the likes |
| `addToGroup(account, follow)` | the app's group picker for one of its accounts |
| `openPostMenu(menu)` | the shared post menu over the screen |
| `download(media, fileName, subfolder)`, `saveFile(bytes, fileName, mimeType, subfolder)`, `shareFile(bytes, fileName, mimeType)`, `openDownloads()` | the app's downloads, a file it made saved or shared, the Downloads screen |

The shared pieces, in the same package, drawn in the app's theme. Use them
rather than a copy of your own; a piece two plugins need goes here once,
with a test.

- Posts: `NetworkPostCard` (with `NetworkPostExtras` for what a network adds),
  `NetworkFocalPost`, `QuotedNetworkPost` (a `NetworkPost`), `LinkedText`,
  `NetworkCount`, `NetworkHeaderButton`, `PostLines` (a post's time and
  counts in words) and `NetworkBookmark` (below).
- Media: `MediaTiles`, `MediaPager` (the viewer, with qualities and a
  download), `ZoomableImage`, and `rememberImageRequest` for pictures that
  need request headers.
- Reads and lists: `Fetch` (one read, kept on screen through a failure),
  `MergedFeed` (accounts' pages merged as they come), `FeedReads` (a feed's
  reads, one failing without the others), `PagedItems` with `PagedList`,
  `NetworkProblemView` and `NetworkProblemRow` with `problemOf`, `RetryRow`,
  `MessageText` and `EmptyPane`.
- Articles and X posts: `RssReaderScreen`, the article reader RSS and the web
  sources open (its place kept, an offline copy), and X posts drawn in a
  plugin with `rememberXPostActions` and `XPostMedia`.
- Screens: `PluginScaffold`, `PluginRoute` (a screen and its arguments as
  text, for `open`), `PluginChipTabs` over a home tab, `WithSettings`,
  `PluginShelf` (what one screen hands the next), `rememberKept` (state kept
  while the screen is on the back stack) and `PluginNotices` with
  `NoticeHost`.
- Rows and fields: `PluginSwitchRow`, `PluginActionRow`, `PluginMenuRow`,
  `PluginSectionTitle`, `PluginTabs`, `PluginCounts`, `NetworkPersonRow`,
  `SecretField` (a cookie, a token, a key) and `PluginIcons`.
- The app's features: `HomeTimelineSwitch` and `AddToGroupButton` (below),
  and `PluginSearchHistory` (recent searches, as XTA keeps them).
- Leaving and files: `rememberOpenOutside`, `rememberShareText`,
  `rememberTextFileChooser` and `rememberTextFileSaver`.
- Words: `PluginStrings` with `pluginString` and `pluginPlural`
  (`pluralCategory` gives each language's plural forms).

`PluginSettings` reads a preference with `get(pref)` and `observe(pref)` and
changes one of its own with `set(pref, value)`; its own are the keys under
`plugin.<id>.` and the few its network's NetworkPrefs keeps where XTA kept
them (`keysOf`, Stocks' `other.ticker_chart`).

`:ui`'s `ui.common` (avatar, hairline, loading row, times) is there too, and a
plugin may draw with the app's other public building blocks where they fit:
its icon sets (`PostIcons`, `DiscoverIcons`, `GroupIcons`), `ThreadRows` for
X posts, `rememberRowDrag` for a list dragged into order. Whatever a plugin
uses is part of the plugin API: `tool/plugin_abi.py` checks that plugins
built before still find it. The pieces' words are the app's, in all its
languages: a plugin draws them without strings of its own.

### Posts in Home's timelines

XTA mixes the posts of what a network follows into Following and For you
when the reader turns on its "show in the home timeline" switch, and the
posts of a group's members of other networks into that group's feed
(`SubscriptionSource`). A plugin offers them with `XtaPlugin.feed`, a
`PluginFeed`:

| Member | What it is |
|---|---|
| `followed()` | the accounts followed in the plugin, as `posts` reads them and its group members name them |
| `posts(accounts, kinds, force)` | the newest posts of `accounts`, newest first, as `NetworkPost`s with their `createdAt` |

`kinds` is a `FeedKinds` (the feed's reply and repost settings, XTA's
`FeedPostKinds`: `takes(isReply, isRepost)`); `force` reads past the plugin's
caches, as a pull does. A plugin reads a page of each account a few at a
time, keeps only a few posts (Reddit ten of each community, Bluesky ten in
all, as XTA), lets an account that fails add nothing and throws only when
none answered, and reads through the same pages and caches as its own tab.

The switch is the app's setting `plugin.<id>.in_home_feed`, where XTA keeps
it (`:store`'s `NetworkPrefs.inHomeFeed`): `PluginSettings.inHomeFeed()` reads
it, and `HomeTimelineSwitch(host.settings, name)` is the row a plugin's
settings show for it, in XTA's words. Off until the reader turns it on.

The app does the rest, as XTA does (`ui/feed`'s `FeedSources`, `PluginPosts`,
`FeedMix`):

- **Who is asked**: on Following and For you, every running plugin with a
  feed whose switch is on, for its followed accounts; on a group's feed (its
  screen and its tab on Home), each plugin for its members in the group, its
  nested groups and those read alongside. For you takes every kind of post;
  Following and the groups their own reply and repost settings.
- **Its place**: a post goes above the first X thread older than it (a
  thread sits at its newest post); one older than every thread read so far
  rides at the end until paging reaches it. With nothing from X, or X failing,
  the plugins' posts are shown alone under why.
- **What is new**: what a plugin answers while the reader is down the list
  is held, counted in the new-posts pill, and shown when they come back to
  the top or tap the pill, so nothing moves under their eyes.
- **Failures**: each plugin is read on its own; one that fails says so in a
  pill naming it, with a retry, and the others' posts stay. A plugin that is
  uninstalled, switched off, or loses its members leaves with its posts.
- **Reading again**: a pull or the refresh button reads every plugin past its
  caches; the connection coming back reads the ones that failed.

A post is drawn with the shared row, the plugin's mark on its avatar, and
leads where `postActions` says (its link otherwise).

### Accounts in the app's groups

XTA's groups hold other networks' accounts. A group member written
`<plugin id>:<account>` is a plugin's (a saved search with a colon stays a
search); the account is what the plugin's feed reads it by (Bluesky's DID,
a community's name in lower case). `host.addToGroup(account, follow)` opens the
app's group picker (XTA's group membership sheet) for it; the choice applies
as it closes, and `follow` runs first once a group is picked, for the plugin
to follow the account, as XTA's picker does. `AddToGroupButton(host,
account, follow)` is that as a bar's button; the shared post menu offers it
for a post's author. The group's feed reads its members through their
plugins ([above](#posts-in-homes-timelines)), and counts them among its
members; uninstalling a plugin takes its members out of every group.

### Saved, notes and likes

The reader's archive is the app's: Saved with its folders and the note on a
post, and the likes. A plugin files a post there as a `PluginPostArchive`:
`id` unique across networks (`bluesky:<uri>`, `reddit:<id>`), `authorId` whose
it is, and `content`, XTA's record of such a post where XTA has one
(`{"xtaPlugin": "bluesky", "post": …}`), else XTA's link record,
`PluginPostArchive.link(source, url, author, text, images)`, so a backup
reads the same in XTA. Nothing reaches the network.

| `PluginHost` | What it does |
|---|---|
| `saved` | the ids in Saved, as they change |
| `toggleSaved(post)` | the bookmark: saved where a plain tap files (the folder remembered), or taken out |
| `chooseFolder(post)` | the save sheet, as a long press on the bookmark opens it |
| `editNote(post)` | the note on it in Saved, in the app's editor; writing one saves the post first |
| `setLiked(post, liked)` | in the archive's likes or out of them: a network's own like mirrored there, as XTA mirrors Reddit's upvotes |
| `openPostMenu(menu)` | the shared post menu (XTA's plugin post actions): save or remove, the note, Add to group for its author, Share link, Save to folder…, Reposted by and Quotes where the plugin lists them (`PluginPostMenu.reposts`, `quotes`), Open in browser |

The shared row does this by itself when a plugin's `NetworkPostActions` say
how: `archive` (a post's record) puts the bookmark at the end of its counts,
a tap saving and a long press opening the save sheet, and `more` is what a
long press on the row opens (`host.openPostMenu`). A plugin that draws its
own footer (`NetworkPostExtras.footer`) puts `NetworkBookmark(archive)` in it,
where XTA's footer has its bookmark, and its `onLongPress` comes before
`more`. The sheets open over the app's screens, one at a time.

Saved draws a plugin's post through its plugin: `XtaPlugin.archived(content)`
reads the record back, and the shared row draws it with the plugin's
`postActions`. A record the plugin does not read, or a plugin that is not
installed, gets Saved's own card, whose link opens as the app opens links,
in the plugin's screen once it is there (`routeFor`, `screen`).

### Downloads and files

| `PluginHost` | What it does |
|---|---|
| `download(media, fileName, subfolder)` | a `MediaItem` saved through the app's downloads, with their notices: a photo, or a video's own file (a stream has none) |
| `saveFile(bytes, fileName, mimeType, subfolder)` | a file the plugin made (an exported animation, a novel's text) saved where the download setting says, with the same notices |
| `shareFile(bytes, fileName, mimeType)` | such a file handed to the share sheet (Android's, through the app's file provider); the desktop has none, so it is saved instead, the notice saying where |
| `openDownloads()` | the app's Downloads screen |

A file is named `<plugin>-<its own name>` unless the plugin names it, as
XTA names a plugin's media; a made file without an extension gets the one
its type stands for. The media viewer a plugin opens (`openMedia`) has the
same Download in its bar.

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
  `pluginSections` by name (`PluginBackups`); a section named by one of
  XTA's network keys (`redditSubscriptions`, `booruSubscriptions`: `:store`'s
  `BackupKeys.NETWORKS`) holds that network's rows in XTA's format and goes
  under its key, where XTA reads it back. A restore hands each to the running
  plugin that keeps it (XTA's rows written over what the plugin keeps, as
  XTA's import writes them), once; one for a plugin that is not running
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
