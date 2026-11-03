# Moving XTA to XTA Native

Every part of the Flutter app (`lib/` in the repository root) and where it goes
in XTA Native. Nothing in XTA is left without a row. **Read** is the layer that
fetches and parses; **Keep** is what the device stores; **Screens** is the
Compose UI, shared by Android and the Linux desktop.

Modules: `:core` (X, feeds, session), `:networks` (the other networks),
`:store` (device data and the XTA backup), `:services` (articles, links,
translation, speech text), `:ui` (all screens, Compose Multiplatform),
`:app` (Android shell), `:desktop` (Linux app).

## Networks (`lib/plugins/`)

Every network but X is a **downloadable plugin** (`PLUGINS.md`): its reading
layer is written in `networks/<id>` first, then moves with its screens into
`plugins/<id>`, built as a signed plugin for Android and Linux.

| XTA | Read | Screens |
|---|---|---|
| x (`lib/client`, `plugins/x`) | `core/x`: every read XTA makes | feed, post, profile, search done; trends, communities, lists and the rest in the screens wave |
| reddit | `networks/reddit` ported (237 tests) | screens wave |
| bluesky | `networks/bluesky` ported (98 tests, live check passes) | first plugin, being built |
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
| tweet | `ui/post` (done in part); translation in `services` |
| home | `ui/home`: a tab per followed network and group, deck, For you, saved |
| profile | `ui/profile` (done in part): follows lists, saved tab, filters, followed by |
| search | `core/x` advanced search; `store` recent searches; `ui/search` |
| trends | `core/x`; `ui/trends` |
| subscriptions | `core/session` + `store`; `ui/subscriptions` (list, import, cleanup, groups) |
| group | `core/feed` and `core/session` rules (done: custom rules, post caps, saved searches, batch read, rotation, nesting, combined and deck groups, packs); `ui/group` |
| saved | `store/saved` (done: saved posts, folders, device likes, local notes and posts); `ui/saved` |
| archive | `store/saved` (done: notes, highlights, tags); `ui/archive` |
| offline | `store/offline` (done) + `services`; `ui/offline` |
| downloads | `store/downloads` (done: queue, resume, history, naming); platform save in `:app` / `:desktop`; `ui/downloads` |
| reading | `store/reading` (done: reading places, journal, appearance) |
| article | `services/article` (done) |
| links | `services/links` (done: opening rules, routing, previews, shared links); `ui/links` |
| speech | `services/speech` (done: text, languages, chunks, offline voices and their downloads); engines per platform; `ui/speech` |
| media | per platform (players, continuity, podcasts, audio session); `ui/media` |
| antenna | `store` (antennas) + `core` (their queries); `ui/antenna` |
| community | `core/x`; `ui/community` |
| intro | `ui/intro` |
| settings | `store` (every setting); `ui/settings`; the plugin store in `plugin-host` + `ui/plugins` |
| catcher | crash reports per platform; a switch in settings |
| utils | `services` (AI client, translation, sizes, debounce, cached pages) and the platform modules |
| ui | `ui/common` (the whole UI is now one Compose Multiplatform module for Android and Linux) |
| l10n | `ui` resources in all 30 of XTA's languages |
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
