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
| reddit | `plugins/reddit` (moved from `networks/reddit`; 239 tests) | plugin: XTA's tab, communities, threads, search and settings |
| bluesky | `plugins/bluesky` (124 tests, live check passes) | plugin: Following, Discover and custom feeds, lists, device likes, threads, profiles, search, imports and settings |
| mastodon | `plugins/mastodon` (169 tests) | plugin: Explore, Local, Federated and Following, trending tags, profiles, threads, hashtags, search, boosts and quotes, saved posts and settings; Home's timelines, groups and Saved through the host |
| threads | `plugins/threads` (174 tests, live check passes) | plugin: Home and Liked, the post card, conversations with their orders, profiles with honest counts, search, every video rendition, settings; Home's timelines (ten posts an account), groups, Saved and downloads, retries by Meta's advice |
| substack | `plugins/substack` (177 tests) | plugin: XTA's screens, the reader and podcasts; groups and the shared post sheet |
| booru | `plugins/booru` (151 tests, live check passes) | plugin: XTA's screens and settings; Home's timelines, groups, Saved and downloads through the host |
| pixiv | `plugins/pixiv` (222 tests, live check passes) | plugin: Home, Rankings, Favorites and Search with search by image (SauceNAO), works and novels, ugoira saved as GIF or ZIP, novels exported as text, profiles, comments, series, Pixivision and settings; groups, Saved and batch downloads with their progress through the host |
| ehviewer | `plugins/ehviewer` (97 tests, live check passes) | plugin, private as in XTA: Popular, the front page, the toplists, Watched, history and favourites, search with XTA's categories and the site's tag suggestions, galleries with tags, comments and previews, the reader in its three modes with downloads, settings |
| instagram | `plugins/instagram` (56 tests) | plugin: For you and Following, profiles, the accounts followed here, search, the post card with its carousel, settings; groups and the shared post sheet |
| tiktok | `plugins/tiktok` (61 tests, live check passes) | plugin, private as in XTA: Following and Accounts, trending, discover, search for people and videos, profiles, the player in XTA's renditions or TikTok's own, groups, settings |
| rss | `plugins/rss` (25 tests; its parsing, shared with the web sources, at `:networks`' top level, and its read marks, shared too, in `ui/plugins`' `RssReads`) | plugin: the timeline with unread and tag filters, feeds and their tags, a site's feeds discovered, OPML import and export, the reader with its place and offline copy, Home's timelines and groups, settings |
| hackernews | `plugins/hackernews` (29 tests) | plugin: Top, New, Best, Ask, Show and Jobs, Saved and Following, stories with their comments, users, search and settings; groups, Saved and the shared post sheet |
| websources | `plugins/websources` (85 tests) | plugin: the published catalogue, adding a source with its consent, its lists, search and follows, the reader with its read marks among RSS's, Home's timelines and groups, whose posts open in the reader as XTA's do |
| stocks | `plugins/stocks` (171 tests, live check passes) | plugin: the watchlist, posts about it, trending and markets, a ticker's and a token's pages with the chart, the price strip under X posts and cashtags opening the ticker, a watched token's network before its contract in X posts' text |
| immich | `plugins/immich` (52 tests): the reader's own library | plugin: "Upload media to Immich" in Saved's folders, an album per folder, its settings and connection test |
| deepmarks | `plugins/deepmarks` (30 tests): bookmarks signed on the device | plugin: "Save to Deepmarks" in the post sheets, its settings and connection test |
| karakeep | `plugins/karakeep` (18 tests): the reader's own bookmarks | plugin: "Save to Karakeep" in the post sheets, its settings and connection test |

## The app (`lib/`)

| XTA | Goes to |
|---|---|
| client | `core/x` (done) |
| database | `store` (no SQLite migrations: XTA's data arrives through its backup) |
| tweet | `ui/post` (done: post card, opened post, conversation and its orders, quotes and reposters, polls, cards, translation, the footer with the device's like and bookmark, the share sheet with the post as a picture, the ⋯ sheet, double-tap to like, contract addresses in the text) |
| home | `ui/home` (done: the bottom bar with Settings › Home's pages, its labels and hide-on-scroll; Following, X and the pinned networks turned by a drag or picked in the timeline picker, Mastodon, Bluesky and Threads grouped; Following, the groups and the deck as group feeds; Following's bar with Posts/Media, order, filters and the timeline picker; search in the loaded posts; the account menu; desktop navigation) |
| profile | `ui/profile` (done: header, filters, follows lists, followed by, note, Posts/Replies/Media/Saved tabs, gates) |
| search | `ui/search` (done: tabs, advanced search, languages, recent searches, media grid, search in a loaded feed); `store` recent searches |
| trends | `ui/trends` (done: Discover with places, trends, shortcuts) |
| subscriptions | `ui/subscriptions` (done: groups board, people list with every plugin's followed accounts, imports of handles, lists and packs, cleanup, sort ungrouped by hand or AI); `store/backup` `NetworkMembers` (XTA's groups' members of other networks) |
| group | `core/feed` and `core/session` rules; `ui/group` (done: the group feed with progress, retries, folds, catch-up; feed settings; Media; Discover from what the feed kept, the members' posts and follows (kept in `store/follows`) and the plugins' networks (`XtaPlugin.discovery`: Bluesky, Mastodon, Pixiv), with Rank with AI) |
| saved | `store/saved`; `ui/saved` (done: tabs, folders, save sheet, device likes, notes and their editor, profile Saved tab) |
| archive | `store/saved`; `ui/archive` (done: highlights, tags, AI transcription) |
| offline | `store/offline`; `ui/offline` (done: collection and media; a kept article opens in the reader of the plugin it came from, `XtaPlugin.routeForOffline`) |
| downloads | `store/downloads`; `ui/downloads` (done; MediaStore on Android, `~/Downloads` on the desktop) |
| reading | `store/reading` (done: reading places, journal, appearance) |
| article | `services/article`; `ui/article` (done) |
| links | `services/links`; `ui/links` (done: opening rules, in-app browser on Android, previews, post context bar, shared links) |
| speech | `services/speech`; `ui/speech` (done: the bar, system voices on Android, voice settings and downloads, and downloaded voices speaking through sherpa-onnx's C API and the app's JNI shim, `services/src/main/c`: the engine downloads with the first voice on Android, pinned as in XTA, and the Linux packages carry it; voices and engine archives unpack in the app, tar with bzip2 or xz, as XTA's archive package does) |
| media | `ui/media` (done: viewer with zoom and video controls, videos playing in their tiles with XTA's controls and mute corner, full screen in the video's orientation with picture-in-picture on Android, Settings › Media's video settings applied to the players, continuity player, and XTA's media session: the notification and the lock screen's controls while reading aloud or a podcast plays, kept by a foreground service) |
| antenna | `store` (antennas) + `ui/antenna` (done: list, editor, feed) |
| community | `ui/community` (done: posts, about, members, links) |
| intro | `ui/intro` (done) |
| settings | `store/settings`; `ui/settings` (done: every section, backup export and restore, WebDAV, theme, the licences of every library the build resolves, `gradle/licences.gradle.kts`); the plugin store in `plugin-host` + `ui/pluginstore` (done), the plugin API in `ui/plugins` (done) |
| catcher | crash reports kept on the device (done; a switch in settings) |
| utils | `services` (AI client, translation, sizes, debounce, cached pages) and the platform modules |
| ui | `ui/common` (the whole UI is now one Compose Multiplatform module for Android and Linux) |
| l10n | `ui` resources and the plugins' strings in all 29 of XTA's languages (done for every screen so far, `SCREENS.md`), Latin Belarusian (`values-be-rXL`) filled in by `tool/belarusian_latin.py` |
| generated | not needed |

## Platforms

| What | Android (`:app`) | Linux (`:desktop`) |
|---|---|---|
| Package | APK beside XTA (`com.aimdi.xtanative`) | tarball and Arch package, as XTA ships |
| Links | x.com and the networks' links open in the app; shared links arrive | — |
| Privacy | secure window switch; no backup or device transfer of data | data in `~/.local/share/xta-native` |
| Media | Media3 players, the media session with its notification and lock-screen controls | the system's libmpv, as XTA's media_kit: videos, GIFs, the feed's player, podcasts, read-aloud clips and offline videos in the app (the app's JNI shim and mpv's software renderer); the system player without it; no media session, as XTA has none there |
| Speech | system text-to-speech, downloadable voices (the engine downloads with the first) | downloadable voices (the engine in the packages) |
| Downloads | MediaStore | `~/Downloads` |
