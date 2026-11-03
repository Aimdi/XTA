# Moving XTA to XTA Native

Every part of the Flutter app (`lib/` in the repository root) and where it goes
in XTA Native. Nothing in XTA is left without a row. **Read** is the layer that
fetches and parses; **Keep** is what the device stores; **Screens** is the
Compose UI, shared by Android and the Linux desktop.

Modules: `:core` (X, feeds, session), `:networks` (the other networks),
`:store` (device data and the XTA backup), `:services` (articles, links,
translation, speech text), `:ui` (all screens, Compose Multiplatform),
`:plugin-host` (the plugin store's engine), `:plugins:<id>` (the downloadable
plugins), `:app` (Android shell), `:desktop` (Linux app).

## Networks (`lib/plugins/`)

Every network but X is a **downloadable plugin** (`PLUGINS.md`): its reading
layer is written in `networks/<id>` first, then moves with its screens into
`plugins/<id>`, built as a signed plugin for Android and Linux.

| XTA | Read | Screens |
|---|---|---|
| x (`lib/client`, `plugins/x`) | `core/x`: every read XTA makes | feed, post, profile, search, Discover, communities, list import and antennas done; the rest in the screens wave |
| reddit | `networks/reddit` ported (237 tests) | screens wave |
| bluesky | `plugins/bluesky` (the reading layer moved from `networks/bluesky`; 98 tests, live check passes) | the first plugin: Discover and Following on Home's strip, threads, profiles with their tabs, search, its settings |
| mastodon | `networks/mastodon` ported (146 tests) | screens wave |
| threads | `networks/threads` ported (166 tests, live check passes) | screens wave |
| substack | `networks/substack` ported (153 tests) | screens wave (reader, podcasts) |
| booru | `networks/booru` ported (151 tests, live check passes) | screens wave |
| pixiv | `networks/pixiv` ported (216 tests) | screens wave |
| ehviewer | `networks/ehviewer` ported (97 tests, live check passes) | screens wave |
| instagram | `networks/instagram` ported (51 tests) | screens wave |
| tiktok | `networks/tiktok` ported (52 tests, live check passes) | screens wave |
| rss | `networks/rss` ported (48 tests) | screens wave |
| hackernews | `networks/hackernews` ported (18 tests) | screens wave |
| websources | `networks/websources` ported (73 tests) | screens wave |
| stocks | `networks/stocks` ported (161 tests, live check passes) | screens wave |
| immich | `networks/immich` ported (52 tests): saving to the reader's own library | screens wave |
| deepmarks | `networks/deepmarks` ported (30 tests): saving bookmarks | screens wave |
| karakeep | `networks/karakeep` ported (23 tests): saving links | screens wave |

## The app (`lib/`)

| XTA | Goes to |
|---|---|
| client | `core/x` (done) |
| database | `store` (no SQLite migrations: XTA's data arrives through its backup) |
| tweet | `ui/post` (done: post card, opened post, conversation and its orders, quotes and reposters, polls, cards, translation) |
| home | `ui/home` (done: strip of Following, For you, groups and pinned networks; the deck; the account menu; desktop navigation) |
| profile | `ui/profile` (done: header, filters, follows lists, followed by, note, Posts/Replies/Media/Saved tabs, gates) |
| search | `ui/search` (done: tabs, advanced search, languages, recent searches, media grid, search in a loaded feed); `store` recent searches |
| trends | `ui/trends` (done: Discover with places, trends, shortcuts) |
| subscriptions | `ui/subscriptions` (done: groups board, people list, imports of handles, lists and packs, cleanup, sort ungrouped by hand or AI) |
| group | `core/feed` and `core/session` rules; `ui/group` (done: the group feed with progress, retries, folds, catch-up; feed settings) |
| saved | `store/saved`; `ui/saved` (done: tabs, folders, save sheet, device likes, notes and their editor, profile Saved tab) |
| archive | `store/saved`; `ui/archive` (done: highlights, tags, AI transcription) |
| offline | `store/offline`; `ui/offline` (done: collection and media) |
| downloads | `store/downloads`; `ui/downloads` (done; MediaStore on Android, `~/Downloads` on the desktop) |
| reading | `store/reading` (done: reading places, journal, appearance) |
| article | `services/article`; `ui/article` (done) |
| links | `services/links`; `ui/links` (done: opening rules, in-app browser on Android, previews, post context bar, shared links) |
| speech | `services/speech`; `ui/speech` (done: the bar, system voices on Android, voice settings and downloads; offline voices download but cannot speak yet) |
| media | `ui/media` (done: viewer with zoom and video controls, continuity player); lock-screen controls need media3-session |
| antenna | `store` (antennas) + `ui/antenna` (done: list, editor, feed) |
| community | `ui/community` (done: posts, about, members, links) |
| intro | `ui/intro` (done) |
| settings | `store/settings`; `ui/settings` (done: every section, backup export and restore, WebDAV, theme); the plugin store in `plugin-host` + `ui/pluginstore` (done), the plugin API in `ui/plugins` (done) |
| catcher | crash reports kept on the device (done; a switch in settings) |
| utils | `services` (AI client, translation, sizes, debounce, cached pages) and the platform modules |
| ui | `ui/common` (the whole UI is now one Compose Multiplatform module for Android and Linux) |
| l10n | `ui` resources in all 29 of XTA's languages (done for every screen so far, `SCREENS.md`) |
| generated | not needed |

## Platforms

| What | Android (`:app`) | Linux (`:desktop`) |
|---|---|---|
| Package | APK beside XTA (`com.aimdi.xtanative`) | tarball and Arch package, as XTA ships |
| Links | x.com and the networks' links open in the app; shared links arrive | — |
| Privacy | secure window switch; no backup or device transfer of data | data in `~/.local/share/xta-native` |
| Media | Media3 players, audio session | system player for video |
| Speech | system text-to-speech, downloadable voices | — |
| Downloads | MediaStore | `~/Downloads` |
