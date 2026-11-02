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

| XTA | Read | Screens |
|---|---|---|
| x (`lib/client`, `plugins/x`) | `core/x` done; trends, communities, lists, quotes, reposters, Spaces, advanced search: `core/x` | feed, post, profile, search done; the rest in the screens wave |
| reddit | `networks/reddit` | screens wave |
| bluesky | `networks/bluesky` | screens wave |
| mastodon | `networks/mastodon` | screens wave |
| threads | `networks/threads` | screens wave |
| substack | `networks/substack` | screens wave (reader, podcasts) |
| booru | `networks/booru` | screens wave |
| pixiv | `networks/pixiv` | screens wave |
| ehviewer | `networks/ehviewer` | screens wave |
| instagram | `networks/instagram` | screens wave |
| tiktok | `networks/tiktok` | screens wave |
| rss | `networks/rss` | screens wave |
| hackernews | `networks/hackernews` | screens wave |
| websources | `networks/websources` | screens wave |
| stocks | `networks/stocks` | screens wave |
| immich | `networks/immich` | screens wave |
| deepmarks | `networks/deepmarks` | screens wave |
| karakeep | `networks/karakeep` | screens wave |

## The app (`lib/`)

| XTA | Goes to |
|---|---|
| client | `core/x` (done; missing operations with the X parity work) |
| database | `store` (no SQLite migrations: XTA's data arrives through its backup) |
| tweet | `ui/post` (done in part); translation in `services` |
| home | `ui/home`: a tab per followed network and group, deck, For you, saved |
| profile | `ui/profile` (done in part): follows lists, saved tab, filters, followed by |
| search | `core/x` advanced search; `store` recent searches; `ui/search` |
| trends | `core/x`; `ui/trends` |
| subscriptions | `core/session` + `store`; `ui/subscriptions` (list, import, cleanup, groups) |
| group | `core/feed` rules (custom rules, author caps, catch-up, discovery rotation, combined and deck groups, batch read); `ui/group` |
| saved | `store` (saved posts, folders, device likes, local notes and posts); `ui/saved` |
| archive | `store`; `ui/archive` |
| offline | `store` + `services`; `ui/offline` |
| downloads | `store` (registry, naming); platform download in `:app` / `:desktop`; `ui/downloads` |
| reading | `store` (reading places in articles) |
| article | `services` (article parser, blocks, entities) |
| links | `services` (opening rules, previews, post context); `ui/links` |
| speech | `services` (text, languages, chunks); engines per platform; `ui/speech` |
| media | per platform (players, continuity, podcasts, audio session); `ui/media` |
| antenna | `store` (antennas) + `core` (their queries); `ui/antenna` |
| community | `core/x`; `ui/community` |
| intro | `ui/intro` |
| settings | `store` (every setting); `ui/settings` |
| catcher | crash reports per platform; a switch in settings |
| utils | `services` and the platform modules |
| ui | `ui/common` |
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
