# Booru plugin audit

Date: 2026-10-12. Code audited: `lib/plugins/booru/` as shipped in aimdi177
(`claude/main` 082bb06a). This branch changes no Booru code; it adds the
benchmark (`integration_test/booru_perf_test.dart`) and these documents.

## How this audit was made

- **Reading.** All 39 files in `lib/plugins/booru/` (5,998 lines) and the
  shared XTA code they call, in six independent read-only passes: module map
  and data flow, sites, features, upstream drift, tests, and a screen-by-screen
  walk-through. The findings marked ✔ in the tables below were then re-read by
  hand against the code.
- **Tests run.** The 16 test files that touch Booru: 197 tests, all passing
  (§6). The benchmark's own statistics have 7 unit tests, all passing.
- **Upstream.** Boorusama (GPL-3.0) at commit 1716a684, used only as a
  reference for site behaviour and features. No code was copied (§5.1).
- **Not done, and why.**
  - No request reached a real booru. This environment's network policy
    refuses all 12 preset hosts (the proxy answers `CONNECT tunnel failed,
    response 403`). §7 is therefore a static walk-through of every screen and
    engine, not a run.
  - No Android device is attached. Every performance number is UNVERIFIED.
    `docs/perf-baseline.md` has the S1–S7 protocol and the script that
    produces them.
  - The benchmark did run end to end on the Linux desktop build against a
    local stand-in for Danbooru. That proves the script's flows work; its
    numbers describe a desktop VM and are not reported.

**Citations.** `booru_x.dart:N` is `lib/plugins/booru/booru_x.dart`, line N
(ranges inclusive). Other XTA paths are relative to the repository root.
`pub:` is a package at the version locked in `pubspec.lock`, as found in the
pub cache. `BS:` is Boorusama at 1716a684
(<https://github.com/khoadng/Boorusama>).

## Findings at a glance

### Bugs in existing features (priority tier 1)

27 bugs, from the hand checks and the §7 walk-through, most severe first.
**Checked** says how far each was confirmed. ✔ means re-read by hand against
the code. "Code" means found by reading the code in one of the passes and not
re-read by hand. "Site, likely" means the code path is certain but the
host's reply is inferred from Boorusama's handling or the site's documented
API, because no live request was possible.

| ID | What goes wrong | Where | Severity | Checked | Also |
|---|---|---|---|---|---|
| B1 | A failed request shows the login and API key (or Moebooru password hash) on screen. The exception message carries the full request URL, credentials included, and the generic error page prints the raw exception. Network failures leak it the same way, because `http.ClientException` names the URL | `booru_client.dart:26-34, 329-376`; `booru_endpoints.dart:27-36`; `lib/ui/errors.dart:608-612`; `pub:http-1.6.0/lib/src/io_client.dart:227` | High | ✔; fixed in `dba4687f` | §3 X2 |
| B2 | Credentials follow the reader to the next site. Switching preset, engine or host keeps the single global login and key, and every request to the new host carries them, under that engine's parameter names | `booru_settings.dart:61-73, 226-249`; `booru_endpoints.dart:27-36` | High | ✔; fixed in `ce934ebc` | ST-01, X1 |
| B3 | Feeds are not reset when the site, rating cap or blacklist changes. The next page comes from the new host and is appended to the old grid; old posts look up tags, comments and wiki on the new host with the old host's ids; newly hidden or over-cap posts stay visible | `booru_screen.dart:57-71, 122-126`; `booru_store.dart:150-164`; `booru_client.dart:110-130, 164-203` | High | ✔; fixed in `a9ef31c1`, `63fb05ba` | ST-04, X6 |
| B4 | Half-entered credentials are sent. The login and the key are added to requests independently and saved on every keystroke, so a login without a key (the key is labelled optional) goes out on every request; Danbooru and e621 likely answer 401 | `booru_endpoints.dart:27`; `booru_settings.dart:261, 273` | Medium | ✔ code; site, likely; fixed in `7825d39a` | ST-02 |
| B5 | Retry loop at the end of a feed. `loadMore` swallows a failure (429, 410, 5xx, bad JSON) and leaves `hasMore` true, and the grid calls it on every scroll notification within 800 px of the end, so each failure is followed by another request at once. No spinner, error or end marker is shown, because `loadingMore` is a plain field, not Store state | `booru_store.dart:116-123, 150-175`; `booru_grid.dart:47-55, 76-82` | Medium | ✔; fixed in `afd18acb` | ST-11, X4 |
| B6 | The Following tab hides failures. Each failing followed query becomes an empty page, so with no network, a 429 or a host that needs a key, the tab says there are no recent posts instead of showing an error with Retry | `booru_client.dart:222-238` | Medium | ✔; fixed in `e5315b74` for the Following tab. The interleaved Home feed still leaves a failed query out without saying so | ST-10 |
| B7 | Every first open flashes "No posts yet — check the host in settings" with a Retry button for at least 150 ms before loading starts. A new store holds `[]` and is not loading until `execute`'s 50 ms delay has passed, and Latest first waits for two other store loads | `booru_screen.dart:74-92, 229-259`; `booru_search_store.dart:176-182`; `pub:triple-2.1.0/lib/src/base_store.dart:201-212` | Medium | ✔; fixed in `56ddcba9`. Not covered: on a cold start that opens on Following, its "Follow tags" pane can show until the followed tags load, a separate store | ST-13, P3 |
| B8 | A load-more still in flight when a refresh lands appends posts from the old list (for example the previous Popular date) and moves the page counter past pages never shown. Two overlapping refreshes do the same: `execute` drops the older result, but the loader's side effects still land after the newer one (page counter and paging state, and since `e5315b74` Following's failure count) | `booru_store.dart:125-175`; `booru_screen.dart:133-137`; `pub:triple-2.1.0/lib/src/base_store.dart:201-233` | Medium | ✔ by reading; depends on timing; fixed in `cc31597e` | ST-12 |
| B9 | Downloads from Moebooru sites get the percent-encoded file name with the whole tag list (`booru-yande.re%20123456%20…`), cut from the front at 240 characters so the `booru-` prefix and id can be lost. The naming helper is shared with other plugins | `lib/plugins/plugin_post_media.dart:581-586`; `lib/downloads/download_entry.dart:120-124` | Medium | ✔; fixed in `6ec2f4bd`. The Moebooru URL shape is from ST-14, not observed here | ST-14 |
| B10 | Posts with no usable file (banned or restricted for guests, deleted, Flash) are kept: broken tiles, a picture tap that does nothing, a download that silently does nothing, and a save with an empty image URL | `booru_parse.dart:77-89, 206-210`; `booru_models.dart:135-157`; `booru_post_media.dart:37, 56-67`; `booru_post_actions.dart:26` | Medium | Code; site, likely; fixed in `18989036` (left out of every page; not checked live) | ST-05, E5, G10 |
| B11 | Errors that sites send as HTTP 200 bodies are read as invalid JSON. Rule34 without a key says "returned something unexpected" instead of asking for credentials, and its Following, suggestions and tag kinds come back empty. Gelbooru's "Too deep" feeds the B5 loop | `booru_client.dart:364-376`; `booru_engines.dart:52-55, 99-104` | Medium | Code; site, likely; fixed in `2926ce05` (the bodies are built from Boorusama's checks, not checked live) | ST-03, G2, G4 |
| B12 | On Gelbooru 0.2 forks (Safebooru, TBIB, Xbooru, Rule34) the tag endpoint likely answers in XML, so suggestions never appear, a post's tags are not grouped or counted, and the "More from" artist strip is missing | `booru_client.dart:172-184, 205-218, 283-300`; `booru_endpoints.dart:89-90` | Medium | Code; site, likely; fixed in `864e395f` (XML tag rows read; not checked live) | ST-08, G6 |
| B13 | Danbooru's two-tag search limit for guests and Members is not handled. Adding `order:score` or a refinement past it likely gets HTTP 422, shown as a generic error, and a starred three-tag search adds nothing to Following | `booru_client.dart:235-237, 357-362`; `booru_search_landing.dart:178, 189-194` | Medium | Code; site, likely; fixed in `bd92c6c7` for the message. Not covered: a warning when a search is saved, which needs Danbooru's list of free metatags | ST-09 |
| B14 | e621 GIFs show the still JPEG sample in the post view and full screen, unless "original in full screen" is on | `booru_models.dart:139-141`; `booru_post_media.dart:37` | Medium | Code; site, likely; fixed in `b503f6a4` for every engine's GIFs (not checked live) | ST-06, E6 |
| B15 | Gelbooru tags keep HTML entities (`girls&#039;_frontline`) in chips, copied tags and follows | `booru_parse.dart:48-49, 236-243` | Medium | Code; site, likely; fixed in `033da183`. Whether Gelbooru's tag lookup and search accept the decoded name is a live check | ST-07 |
| B16 | A feed can strand. When the client-side rating cap (Gelbooru family, every popular list) empties four pages in a row, the tab shows an empty pane with no way to reach later pages | `booru_store.dart:125-148`; `booru_client.dart:256-263`; `booru_screen.dart:247-260` | Medium | By reading; fixed in `94fb4cdb` (Load more on a feed its filters emptied) and `6803168b` (the cap sent to Gelbooru-family hosts in Boorusama's forms; not checked live) | ST-20, X9, G1 |
| B17 | Unfollowing a booru tag also removes every other source's group membership with the same bare id, such as a subreddit called `art` | `booru_store.dart:48-52`; `lib/plugins/plugin.dart:76` | Medium: data loss, rare | ✔; fixed in `d259a6a4` (migration 62, data only; approved for the frozen `lib/database/`) | |
| B18 | "Load media only on tap" is ignored for Booru videos: they call `TweetVideo` directly and skip the gate in `PluginPostVideo` | `booru_post_media.dart:44-49`; `lib/plugins/plugin_post_media.dart:450-461` | Low | ✔; fixed in `20d4f4a8`, the benchmark adjusted in `48224b84` | ST-22 |
| B19 | A custom `hypnohub.net` site is guessed as Moebooru; Boorusama lists it as Gelbooru 0.2, so its requests likely 404 | `booru_engines.dart:179-183` | Low | Code; site, likely; fixed in `5a3b9819`, not checked live | ST-18, M2 |
| B20 | A 403 always says "check login and API key", even with none set or behind a bot challenge; a 503 (e621's rate limit) says "returned something unexpected" | `booru_client.dart:345-362`; `booru_errors.dart:11, 14` | Low | Code; fixed in `add22b17` | ST-19, X5 |
| B21 | The Moebooru wiki is a title search limited to 10 results, so a common tag's page is often reported missing | `booru_endpoints.dart:108-109`; `booru_detail_parse.dart:35-41` | Low | Code; site, likely; partly fixed in `0f88f167`: up to 100 titles are searched for the exact one. The API offers no exact lookup that is documented here | ST-17 |
| B22 | Popular asks for "today" in the device's local date, which the host may not have reached yet (UTC+8 and UTC+9 mornings), so the default list is empty. Boorusama does the same | `booru_popular.dart:17-18`; `booru_endpoints.dart:46-63` | Low | Code; fixed in `972d8d6e`, taking the host's day as UTC's (not checked live) | ST-16 |
| B23 | Rule34 media: `api-cdn` sample URLs are used as-is, a missing preview falls back to the original file (or the `.mp4` itself), the file-URL fallback fails when `directory` is a number, and no `Referer` is sent | `booru_parse.dart:86-88, 319-330`; `booru_models.dart:135-157` | Low | Code; site, likely; partly fixed in `e39aa081`: a numeric `directory` and a video without a preview. Open, live check: the api-cdn rewrite and the `Referer` header | ST-15, G8, G9 |
| B24 | On Gelbooru 0.2 forks "Posted" shows the time of the last edit (`change`), and Following sorts by it. A test codifies the fallback; the label is what is wrong | `booru_parse.dart:277-280`; `booru_post_facts.dart:35`; `test/booru_details_test.dart:118` | Low | Code; fixed in `ec2cf187` | ST-21 |
| B25 | Downloading a Danbooru ugoira saves the frame archive (`.zip`), not the playable webm | `booru_post_actions.dart:31-32`; `booru_models.dart:188-189` | Low | Code; fixed in `006f364f` | ST-23 |
| B26 | Booru settings call `setState` after awaiting a preference write, with no `mounted` check; leaving the screen at that moment throws in release builds (logged, not a crash) | `booru_settings.dart:72, 118, 229, 298` | Low | ✔; fixed in `0d10bd5d` | ST-24 |
| B27 | Late results reach stores that were already destroyed (leaving a search mid-load, swiping past a post before its lookups finish). Debug builds assert "used after being disposed"; release builds ignore it, but the requests still run to the end | `booru_search_store.dart:176-179, 201-207`; `booru_post_screen.dart:65`; `pub:asp-1.3.0/lib/asp.dart:118-122` | Low | ✔; fixed in `14851203` | ST-25 |

No release-mode crash was found on any traced path (§7.3).

Fixed since the audit: B1 (`dba4687f`), B2 (`ce934ebc`), B3 (`a9ef31c1`,
`63fb05ba`), B4 (`7825d39a`), B5 (`afd18acb`), B6 (`e5315b74`), B7
(`56ddcba9`), B8 (`cc31597e`), B9 (`6ec2f4bd`), B10 (`18989036`), B11
(`2926ce05`), B12 (`864e395f`), B13 (`bd92c6c7`), B14 (`b503f6a4`), B15
(`033da183`), B16 (`94fb4cdb`, `6803168b`), B17 (`d259a6a4`), B18
(`20d4f4a8`), B19 (`5a3b9819`), B20 (`add22b17`), B22 (`972d8d6e`), B24
(`ec2cf187`), B25 (`006f364f`), B26 (`0d10bd5d`), B27 (`14851203`), and in
part B21 (`0f88f167`) and B23 (`e39aa081`). Each fix landed with a
regression test.

B10–B16, B21 and B23 depend on how a host answers. This environment cannot
reach any booru host, so their tests use answers built from the shapes the
audit read in Boorusama's parsers, not recorded ones; each is unverified
until it is checked live. Still open: B23's api-cdn rewrite and `Referer`
header, B21's exact title lookup, B13's warning when a search is saved, and
B6 for the interleaved Home feed.

### Performance: what the code suggests (priority tier 2)

These are hypotheses from reading the code, not measurements. Each names the
baseline scenario that will confirm or refute it (`docs/perf-baseline.md`).
None should be acted on before that scenario has been measured on a phone.

| ID | Hypothesis | Evidence | Scenario |
|---|---|---|---|
| P1 | Grid tiles download and decode the site's sample: Danbooru's `large_file_url`, about 850 px wide, or the original file when the post is small. A smaller variant would fill a two- or three-column tile | `booru_models.dart:146-157`; `booru_display.dart:42` | S7 bytes, S2 raster |
| P2 | Every response is decoded and parsed on the UI isolate: feed pages, the per-post tag lookup, and the merged Following feed | `booru_client.dart:342-376` (`jsonDecode` at :370); no `compute` or `Isolate.run` in the plugin | S2 and S3 frame times around page loads |
| P3 | Latest waits at least 150 ms before its first request: three `execute` calls in sequence, each with triple's default 50 ms delay, plus a SQLite read. Both stores were already loaded at startup | `booru_screen.dart:74-92`; `pub:triple-2.1.0/lib/src/base_store.dart:201-212`; `lib/main.dart:985-988` | S1 |
| P4 | Any preference write anywhere in the app rebuilds every mounted booru grid: `BooruDisplay.of` depends on `PrefService`, an `InheritedNotifier` over all preferences | `booru_display.dart:34-37`; `pub:pref-2.8.0/lib/src/service/pref_service.dart:13-25` | S2 rebuild counts |
| P5 | Tile downloads are never cancelled when tiles scroll away (no cancel token), and the viewer precaches nothing for its neighbours | `booru_image.dart:33-59`; no `precacheImage` in the plugin | S7 bytes, S3 open time |
| P6 | The full-screen viewer decodes at full resolution: the shared `TweetPhoto` passes no `cacheWidth`. With "original in viewer", one 4000 × 6000 picture decodes to about 96 MB, the whole 96 MiB image cache | `lib/tweet/_photo.dart:36-37`; `lib/plugins/plugin_post_media.dart:767-773`; `lib/main.dart:464` | S6 |
| P7 | Every page change rebuilds the viewer's whole `Scaffold` and `PageView`. Pages are not kept alive, so swiping back re-runs a post's tag lookup and up to two related-post searches | `booru_post_pager.dart:39-56`; `booru_post_screen.dart:53-61, 91-92, 117-123` | S3 build times, request counts |
| P8 | The image disk cache never shrinks: no size cap, no maximum age, and the plugin declares no `caches` | `pub:extended_image_library-5.0.1/lib/src/network/network_image_io.dart:185-215`; `lib/plugins/plugin.dart:77` | Storage; S7 |

Acted on since, none of it measured (UNVERIFIED until S1–S7 run on a phone):
X3 and X4, a per-site request gate with backoff (`f8d57c09`); P1, Danbooru's
variants for tiles (`5a9c7b02`); P3 (`1536ac45`); P5's viewer half,
neighbours preloaded (`a2993c50`); P7 (`5ffeeb8e`); and P8, a 30-day age
bound with the size and a clear button in settings (`d732c7cc`). P4 was
dropped: on the browsing path only a search writes preferences. P2 waits for
a trace that shows parse cost, and P6 and P5's tile cancelling for a phone.

### Decisions (2026-10-12)

1. **Writing to sites: no.** The plugin stays read-only, as its spec says
   (`docs/specs/booru-plugin.md:14, 75`). Favourites stay on the device;
   syncing favourites and votes with a site (§4 row 7a) is out of scope.
2. **Licence: clean-room.** No Boorusama code or data files are copied. It is
   used only to learn how each site behaves; XTA's code stays MIT.
3. **Credentials: app settings for now.** Login and key stay in the app's
   private preferences, which backups and exports already leave out. The leaks
   (B1, B2, B4) are fixed first; secure storage is reconsidered with per-site
   profiles.
4. **Video target: the pool's rule.** After the viewer closes no player is in
   use; up to `kVideoPoolSize` (3) idle players stay cached for instant resume
   and are freed under memory pressure (`lib/tweet/video_controller_pool.dart:114-133,
   155-162`). The S5 target in `docs/perf-baseline.md` says so.

The repository has no AGENTS.md; its rules were merged into CLAUDE.md, which
governs here.

## 1. Module map

### 1.1 Where the plugin registers in XTA

| Touch point | What it does for booru |
|---|---|
| `lib/plugins/plugin_registry.dart:4,34` | Adds `BooruPlugin()` to `builtInPlugins` (:23-40, between Pixiv and EhViewer). `pluginById('booru')` resolves it (:44-50). Because it mixes in `SubscriptionSource` (`booru_plugin.dart:26`), `subscriptionSources` / `enabledSubscriptionSources` (:55-65) return it. That is how group and home feeds find it. |
| `lib/main.dart` | Imports the client and stores (:82-83). Registers defaults for all 18 booru prefs (:666-683): disabled, Danbooru host and preset, rating `g`, columns 0 (auto), tile details on, tag sort `name`. **Always** constructs one `BooruClient(prefService)`, `BooruTagsStore()` and `BooruMuteStore(prefService)`, even when the plugin is off (:905-907, unconditional block). Runs an un-awaited startup `booruTags.load()` + `booruMute.load()` only when `plugin.booru.enabled` is set (:976-988). Provides the three via `Provider` (:1142-1144). No named route. No deep-link parsing: there is no booru match in `lib/utils/urls.dart` or `lib/plugins/plugin_url.dart`. |
| `lib/constants.dart:459-477` | `pluginIdBooru='booru'` plus 18 keys: enabled, show_tab, engine, host, preset, login, api_key, max_rating, in_home_feed, search_history, muted_tags, custom_sites, grid_columns, small_thumbnails, original_in_viewer, tile_details, blur_explicit, tag_sort. |
| `lib/database/entities.dart:847-885` | `BooruSubscription extends Subscription`: one row per followed tag or saved multi-tag search. `id` = `name` = the normalised query (`booru_store.dart:57-72`), `screenName=id`, plus `in_feed`. |
| `lib/database/repository.dart:29,747-756` | Declares `tableBooruSubscription`. Migration 53 creates `booru_subscription(id VARCHAR PK, name NOT NULL, in_feed INT DEFAULT 1, created_at)`. There is no other booru code there; `BooruTagsStore` calls `Repository.readOnly()/writable()` (`repository.dart:1045,1061`) directly (`booru_store.dart:24,42,61`). |
| `lib/settings/backup_category.dart:19`, `import_preview.dart:144` | `BackupCategory.booruTags`, with preview label `plugin_booru_followed_tags`. Wired through `BooruPlugin.backupSections` (`booru_plugin.dart:139-146`: jsonKey `booruSubscriptions`), which `pluginBackupSections()` collects (`lib/settings/backup_data.dart:28`). After import, `reloadFromDatabase` calls `BooruTagsStore.load()` (`booru_plugin.dart:117-119`; caller `lib/settings/_data.dart:129`). |
| `lib/plugins/plugin_tag_chip.dart` | Shared `PluginTagKind` (:6), `pluginTagKindColor` (:10-21), `pluginCompactCount` (:40) and `PluginTagChip` (:45-126) render post tags (`booru_tag_list.dart:181-198`). The category-to-kind map is in `booru_tag_style.dart:7-19`. |
| `lib/plugins/plugin_query_words.dart:7-18` | `queryWords` / `queryText` / `splitQueryInput`. Booru's tokenizer just wraps them (`booru_query.dart:80-82,115`). |
| `lib/tweet/_video_controls.dart:381-383,643-645,1022-1024` | Contains no booru logic. The shared X video player reuses the booru string key `plugin_booru_video` ("Video", `lib/l10n/intl_en.arb:1788`) as the accessibility label for its play button, seek slider and centre play button. Renaming or removing that booru key would break the X player. |

**Other generic hooks the plugin implements:**
- `homePage` / `homeScreen` (`booru_plugin.dart:55-67`), called from `lib/home/home_screen.dart:201` and `lib/home/_feed.dart:520`.
- `settingsScreen` (:70).
- `openSearch` (:72-83), called from `lib/search/search_scope.dart:83-84` and `lib/trends/discover_plugin_search.dart:119`.
- `subtitleFor` / `avatarFor` / `destinationFor` / `unfollow` (:98-123), called from `lib/subscriptions/subscription_look.dart:33-49` and `subscription_unfollow.dart:42`.
- `previewOf` (:101-103), called from `lib/group/group_model.dart:396`.
- `interleavedPosts` / `inHomeFeed` / `homeFeedIds` (:125-136).
- `resetPreferences` and `forgetLoadedData` for uninstall (:149-174; `plugin.dart:103-125`).

**Defaults it does not override:**
- `caches` stays `[]` (`plugin.dart:77`), so footprint and uninstall never measure or clear booru's image cache.
- `groupMembershipPrefix` stays null (`plugin.dart:76`). See §2.12.

### 1.2 Files in `lib/plugins/booru/`

**Stores (flutter_triple)**
- `booru_store.dart`: three stores.
  - `BooruTagsStore : Store<List<String>>` (:16-73): followed tags and saved searches, read from SQLite.
  - `BooruMuteStore : Store<Set<String>>` (:77-109): blacklist, stored as pref JSON.
  - `BooruFeedStore : Store<List<BooruPost>>` (:112-186): paginated feed over an injected `loader({page})`.
- `booru_load_store.dart`: `BooruLoadStore<T> : Store<T?>` (:5-22). One-shot `ensure()` / `reload()`, used for tag info, comments and wiki.
- `booru_pager_store.dart`: `BooruPagerStore : Store<BooruPagerState{posts,index}>` (:6-49). It follows a feed store. Also holds the pure helper `booruPostsExtend` (:52-56).
- `booru_search_store.dart`: `BooruSearchStore : Store<BooruSearchState{tags,input,suggestions,categories,query,editing}>` (:11-208). It owns the per-query results feed.

**Repositories and API clients**
- `booru_client.dart`: `BooruClient` (:36-378).
  - Reads its config live from prefs on every call (:48-77).
  - Methods: `posts`, `latest`, `search`, `popular`, `tagInfo`, `wiki`, `comments`, `suggestTags`, `postsForTags`.
  - Adds rating tags to queries (:256-281) and filters rating/muted posts client-side (:112-127).
  - Maps HTTP status to `BooruException` (:17-34, :329-377).
- `booru_endpoints.dart`: `BooruSite` (:10-41) handles per-engine auth parameter names, `endpoint` and Gelbooru `dapi`. Also URL builders for popular, tag info, wiki and comments, plus capability flags (:46-131).
- `booru_engines.dart`: `BooruEngine` (:6-36); 12 presets (:56-129); host normalisation, including Rule34's `api.` request host and page host (:131-170); engine guessing (:174-191); mostly-explicit host list (:205-211); custom-sites JSON (:213-267).
- `booru_interleaved.dart`: `SubscriptionSource` adapter: `booruInHomeFeed`, `booruHomeTags`, `loadBooruInterleaved` (8 posts per tag) (:10-35).

**Models and parsers (pure)**
- `booru_models.dart`:
  - `BooruRating`, with per-engine wire parsing (:11-77).
  - `BooruPost` (:79-223): URL variants `thumbnailUrl`, `displayUrl`, `catalogUrl`, `originalUrl`, `videoUrl`; plus `aspectRatio`, `key`, `hostPageUrl`.
  - Page, tag-category, tag-info, suggestion and comment types (:225-305).
- `booru_parse.dart`:
  - `parseBooruPosts` for 4 engines (:11-29, :68-120; e621 :175-226).
  - Tag suggestions (:31-46) and the Gelbooru date parser (:300-317).
  - `booruPostAllowed` / `booruPostMuted` (:350-390) and `parseBooruBlacklist` (:393-404).
- `booru_detail_parse.dart`: `parseBooruTagInfo` (:12-24), `parseBooruWiki` (:35-41), `parseBooruComments` (:47-73).
- `booru_query.dart`: query grammar (operators, metatags, slots; :8-78), normalisation, history key, suggestion prefix, related tags, rating and score metatags (:80-168).
- `booru_popular.dart`: `BooruPopularQuery{scale,date}` with day/week/month stepping (:6-50).

**Screens and widgets**
- `booru_plugin.dart`: `BooruPlugin extends XtaPlugin with SubscriptionSource` (:26-175).
- `booru_screen.dart`: `BooruScreen` home with Latest/Popular/Following tabs (:31-217), `_FeedTab` (:219-272), `_FollowingTab` (:274-322).
- `booru_popular_tab.dart`: scale chips and date stepper/picker, or a "top scores" note for Gelbooru (:12-95).
- `booru_grid.dart`: `BooruPostGrid` masonry (:20-92), `openBooruPost` (:96-104), `BooruPostTile` (:106-229).
- `booru_image.dart`: `BooruNetworkImage`, an ExtendedImage wrapper that decodes at paint width (:7-63).
- `booru_post_pager.dart`: `BooruPostPager`, the PageView viewer (:14-58).
- `booru_post_screen.dart`: `BooruPostScreen`, a single-post pager (:24-34), and `BooruPostDetails`, the page body (:38-130).
- `booru_post_media.dart`: `BooruPostMedia`, picture or video plus the full-screen entry (:15-69).
- `booru_post_facts.dart`: `booruPostFacts` (pure, :12-45) and the `BooruPostFacts` widget (:47-83).
- `booru_post_actions.dart`: `BooruPostActions` app bar (download, save, menu; :49-128) and link/share/archive helpers (:18-44).
- `booru_post_card.dart`: `BooruPostCard` for mixed feeds (:14-85) and `booruInterleavedItems` (:87-95).
- `booru_tag_list.dart`: `BooruTagSection` (:78-199), plus pure `booruTagGroups` / `booruArtistTag` (:34-74).
- `booru_tag_sheet.dart`: `showBooruTagActions` (:25-131) and `showBooruWiki` / `_WikiSheet` (:133-201).
- `booru_comments.dart`: `BooruComments`, a lazy ExpansionTile (:15-98).
- `booru_related.dart`: `BooruRelatedStrip`, the artist/family strip (:19-115).
- `booru_search_screen.dart`: `BooruSearchScreen` (:22-213), save-search star (:216-240), raw-query dialog (:242-282).
- `booru_search_field.dart`: `BooruTagField` chip input (:9-138).
- `booru_search_landing.dart`: suggestions, query tools, saved and recent searches (:15-333).
- `booru_search_results.dart`: results grid plus related-tag chips (:14-105).
- `booru_settings.dart`: `BooruSettingsScreen` (uses `setState`) for host, engine, credentials, rating, mute and connection test (:19-397); `_AddBooruSiteDialog` (:399-516).
- `booru_display_settings.dart`: grid and viewer prefs block (:8-71).

**Pure helpers**
- `booru_display.dart`: `BooruDisplay` turns prefs into column count, tile/viewer URL and blur (:8-48).
- `booru_errors.dart`: `booruErrorMessage` (:4-16).
- `booru_labels.dart`: localized label switches (:6-35).
- `booru_tag_style.dart`: category to kind/colour, display names, token/query `TextSpan`s (:7-70).
- `booru_text.dart`: `booruPlainText`, DText/HTML to plain text (:18-33).

### 1.3 Shared infrastructure: what booru reuses and what it builds itself

| Concern | Reused | Booru's own |
|---|---|---|
| HTTP | None. The X transport in `lib/client/` is not used; most plugins own a client, so this is consistent. | `BooruClient` creates a `package:http` `http.Client()` in its constructor (`booru_client.dart:40-41`); one instance comes from `main.dart:905`. 20 s timeout, fixed User-Agent, `Accept: application/json` (:43-45, :329-340). No retry, no abort, no response cache. The plugin imports none of: `utils/cache.dart` (FFCache), `cached_page.dart`, `request_budget.dart`, `read_request_scope.dart`. |
| JSON | `Json` extension type (`lib/utils/json.dart:17-67`), used in both parse files | — |
| Images: grid, post page, strips, cards | `extended_image` package, used directly | `BooruNetworkImage` (`booru_image.dart:7-63`): `ExtendedImage.network(cache:true, timeLimit 15s, retries 1)`; a LayoutBuilder sets `cacheWidth = maxWidth×DPR`; no `headers`, no `cancelToken`. It duplicates `CappedNetworkImage` (`lib/ui/capped_network_image.dart:10-26`) and the tile in `plugin_post_media.dart:314-343`. Image bytes come over extended_image's own shared `HttpClient`, not `BooruClient` (`pub:extended_image_library-5.0.1/lib/src/network/network_image_io.dart:369-370`). |
| Full-screen viewer | `openPluginImageViewer` → `PluginImageViewer` → `TweetPhoto(inPageView:true)` (`plugin_post_media.dart:621-650,752-775`; `lib/tweet/_photo.dart:35-37,107-137`) | Builds a single `PluginMediaItem` (`booru_post_media.dart:51-68`). |
| Video | `TweetVideo` and the shared `VideoControllerPool` (`lib/tweet/_video.dart:120-151,187-199`; `main.dart:1027-1030`; `kVideoPoolSize=3` at `constants.dart:99`) | Calls `TweetVideo` directly (`booru_post_media.dart:44-49`). This skips `PluginPostVideo`'s `optionMediaDisableAutoload` gate (`plugin_post_media.dart:450-461`). |
| Download | `downloadPluginMediaItem` → `downloadUriToPickedFile` → `DownloadStore.shared.enqueue` (`plugin_post_media.dart:590-619`; `lib/utils/downloads.dart:70-99`) | `booruDownloadItem` leaves out `isVideo`, so videos download too (`booru_post_actions.dart:29-32`; the guard is `plugin_post_media.dart:597`). |
| Save / archive | `PluginLinkPost.archive` (`plugin_link_post.dart:28-32`), `savePluginPost` → `fileSavedTweet` (`plugin_post_actions.dart:36-42`), `SavedTweetModel` (`lib/saved/saved_tweet_model.dart:9,33`), `showPluginPostActions` on long-press of a card (`plugin_post_actions.dart:63`) | `booruPostArchive` and `_SaveButton` (`booru_post_actions.dart:21-27,103-128`). |
| Share / links | `share_plus` (`booru_post_actions.dart:94`) | `openBooruLink` calls `launchUrl(externalApplication)` directly (:34-38). This skips `openUri` (`lib/utils/urls.dart:366-382`), so the embedded-browser and chosen-browser prefs and `prepareUrl` are not applied. |
| Prefs | `pref` package (`PrefService`, `PrefSwitch`, `PrefTitle`); search history via `PluginSearchHistoryStore` (`plugin_search_history.dart:6,23-78`, capped at 20) | `BooruDisplay.fromPrefs` / `.of` (`booru_display.dart:25-37`). |
| Credentials | No secure storage (no `FlutterSecureStorage` anywhere in `lib`). | See the credentials note below the table. |
| Home shell / navigation | `PluginSessionLease` / `PluginSessionStore` (`plugin_session.dart:7-58`, Home-scoped via `home_screen.dart:87-90`); `PluginViewStore` (`plugin_view_store.dart:5-30`); `PluginLazyTabs` (`plugin_lazy_tabs.dart:11-34`); `PluginHomeChrome` / `PluginHomeTab` / `PluginEmbedded` (`plugin_home_chrome.dart:23,32,42`); `PluginHomeSecondaryAction` (`plugin_home_dock.dart:328`); `pluginInnerScrollController` (`plugin_feed_insets.dart:30-35`); `pluginGalleryColumns` (`plugin_gallery_layout.dart:5-8`); `PluginGridSkeleton` (`plugin_feed_skeleton.dart:75`); `PluginFilterRow` (:5); `pluginMark` (`plugin_marks.dart:70`); `EmptyPane`; `FullPageErrorWidget` / `showSnackBar` (`lib/ui/errors.dart:438,46`); `pinPluginOnFeedStripIn` (`lib/home/feed_strip_store.dart:113`) | Navigation is plain `Navigator.push(MaterialPageRoute)` throughout: `booru_plugin.dart:77-82`, `booru_screen.dart:177-185,291,306-309`, `booru_grid.dart:98-103`, `booru_related.dart:76-79`, `booru_tag_sheet.dart:53`, `booru_post_card.dart:27-30`. |
| Mixed feeds | `SubscriptionSource` (`subscription_source.dart:25-100`), `provenanceInterleavedItem` (`lib/ui/provenance_accent.dart:27-41`), and the host-side `ProgressiveFeedStore` / `FeedSnapshotCache` | `booruInterleavedItems` (`booru_post_card.dart:87-95`). |
| Masonry | `flutter_staggered_grid_view` `SliverMasonryGrid.count` (`booru_grid.dart:64-74`) | — |

**Credentials, in detail:**
- Login and the API key / password hash are plain prefs (`booru_settings.dart:253-275`; `booru_client.dart:60-62`). They are sent as query parameters on every request (`booru_endpoints.dart:27-36`).
- `plugin.booru.api_key` matches the suffix rule in `isSecretPrefKey` (`constants.dart:866-872`). It is therefore stripped from exports, backups and crash reports (`lib/utils/crash_reporter.dart:241-245`; `lib/settings/_data.dart:156`) and dropped on import (`lib/utils/pref_lists.dart:54-57`).
- `plugin.booru.login` does not match the rule, so it is exported.
- **Gap:** `BooruException.message` embeds the full request URI, auth parameters included (`booru_client.dart:345-361`). The fallback branch of `FullPageErrorWidget` prints `'$error'` (`lib/ui/errors.dart:608-612`). The API key can therefore appear on the error screen.

## 2. Data flow per screen

### Common pipeline

**Request path.** URL from `BooruSite.endpoint` / `dapi` (`booru_endpoints.dart:33-40`; host via `booruRequestHost`, :18) → `fetchJson` (`booru_client.dart:342`) → `http.get` with a 20 s timeout (:329-340) → `_decodeJson`, which maps status codes, reads `response.body` and calls `jsonDecode` (:344-377) → engine parser → feed filter `_page` (:112-127).

**Everything after the socket runs on the main isolate.** There is no `compute` or `Isolate.run` anywhere in `lib/plugins/booru` (grep: none). Specific sites:
- `jsonDecode` at :370.
- Parsers called at :102, :150, :173, :192, :202, :214.
- On every page, `mutedTags` re-decodes the pref JSON (:74-75 → `booru_parse.dart:393-404`).
- `booruPostMuted` re-tokenises every blacklist entry for every post (`booru_parse.dart:360-373`). `BooruMuteStore` is never consulted here.

**Store loads use triple `execute`.** It waits a fixed 50 ms debounce before calling the loader. A superseded call only has its *result* discarded through `CancelableOperation`; the HTTP request keeps running (`pub:triple-2.1.0/lib/src/base_store.dart:201-234`; debounce :203-212, cancel :216-219).

### Feed paging (`BooruFeedStore`)

Used by Latest, Popular, Following, search results and related strips.

**How it works:**
- `refresh()` fetches up to 4 raw pages in sequence until one survives filtering (`booru_store.dart:125-148`).
- `loadMore()` does not go through `execute`; it is guarded by `_loadingMore` / `_hasMore`. It fetches up to 3 pages until it finds unseen posts, dedupes by `key = host:id` (:150-185; `booru_models.dart:185`), appends `[...state, ...fresh]` (:163), and stops after 3 repeated pages (:169). Errors are swallowed (:170-172).
- `hasMore` means "the raw page was full" (`booru_client.dart:107`).
- Triggers: any `ScrollNotification` within 800 px of the end (`booru_grid.dart:47-55`); in the viewer, reaching 4 from the end (`booru_pager_store.dart:20,31-35`).

**Gaps:**
- `_loadingMore` is a plain field, not store state (:118,152,173). The bottom spinner (`booru_grid.dart:76-82`) therefore only shows if something else rebuilds the tab mid-load.
- There is no generation token. A `loadMore` still in flight when a `refresh` starts will append posts from the old sequence and overwrite `_page` (:157-163).
- A superseded refresh still writes `_page` / `_hasMore` when it finishes (:144-145).
- A failing host is retried on every scroll notification, with no backoff.
- A first page too short to fill the viewport only pages after the user drags.

### Shared grid (`BooruPostGrid`)

- `SliverMasonryGrid.count`; columns come from the pref or from `pluginGalleryColumns` (1–4 by width); scroll cache extent is 1200 px (`booru_grid.dart:59-74`; `booru_display.dart:39-40`).
- **Cell size is known before the image loads:** `AspectRatio(width/height)`, clamped to 0.45–1.6 (`booru_grid.dart:117,128-129`; 1:1 when dimensions are missing, `booru_models.dart:159-162`), plus optional text rows (:154-184).
- **Image variant:** `tileUrl` (`booru_display.dart:42`) defaults to `catalogUrl` (`booru_models.dart:146-157`):
  - sample (`large_file_url` / `sample_url`) if present;
  - otherwise the **original file**;
  - otherwise the preview.

  Videos and the "small thumbnails" pref use the preview.
- Decode is bounded to tile width × DPR (`booru_image.dart:45-60`).
- No image precache. Image downloads are not cancelled when tiles scroll away (`cancelToken` unused; `pub:extended_image-10.1.0/lib/src/extended_image.dart:480`).
- The blur pref wraps the image in `ImageFiltered` with σ=20 (`booru_grid.dart:205-206`).
- `BooruDisplay.of` depends on `PrefService` (`booru_display.dart:35`). That is an `InheritedNotifier` notified on every pref write anywhere in the app (`pub:pref-2.8.0/lib/src/service/pref_service.dart:13-18`; `base.dart:137-142,160-189`). Every mounted booru grid therefore rebuilds on unrelated writes, such as a search-history write.

### 2.1 Home – Latest

- **Request:** `client.latest(page)` → `posts(tags:[])` (`booru_client.dart:79-108`).
  - `_withRatingTag` adds `rating:g` for Danbooru, `rating:s` for Moebooru/e621, nothing for Gelbooru (:256-281).
  - `_postsUri` (:303-327): `/posts.json` (Danbooru, e621), `/post.json` (Moebooru), or Gelbooru `index.php?page=dapi&s=post&q=index&json=1&pid=page-1`. Limit 40 (:46).
- **Parse:** `parseBooruPosts` (`booru_parse.dart:11-29`), main isolate.
- **Store:** `BooruFeedStore` held in the session slot `booru/latest` (`booru_screen.dart:57`).
- **Widget:** `_FeedTab` (:219-272). Two-column skeleton on first load; the grid stays visible during refresh (:231-240); errors use `FullPageErrorWidget` (:241-246); empty uses `EmptyPane` (:248-259).
- **First-request latency:** a post-frame callback awaits `BooruTagsStore.load()` (SQLite read + 50 ms debounce), then `BooruMuteStore.load()` (+50 ms), then `refresh()` (+50 ms) (`booru_screen.dart:74-92,122-126`). That is at least 150 ms of debounce plus a DB read before the first HTTP call, even though `main.dart:985-988` already loaded both stores.
- **Cache:**
  - In memory only, held by `PluginSessionStore` for the Home lifetime (`plugin_session.dart:11-18`; `home_screen.dart:87-90`). Outside Home it is disposed with the screen (`plugin_session.dart:46-57`; `booru_screen.dart:118`).
  - Unbounded: the list grows with every page. Not persisted.
  - Not invalidated when host, engine or rating change. No pref listener exists in `booru_screen.dart`, and the client re-reads prefs per call (`booru_client.dart:48-77`).

### 2.2 Home – Popular

- **Request:** the loader reads the session `PluginViewStore<BooruPopularQuery>` (slot `booru/popular-query`) at call time (`booru_screen.dart:58-62`), then calls `client.popular` (`booru_client.dart:134-160`). `booruPopularUri` (`booru_endpoints.dart:46-67`) builds:
  - Danbooru: `/explore/posts/popular.json?date&scale&page&limit`
  - e621: `/popular.json?date&scale`
  - Moebooru: `/post/popular_by_{scale}.json?day&month&year`
  - Gelbooru: `null`, so it falls back to `posts(['sort:score'])`, all-time, ignoring the date (`booru_query.dart:158`).

  Popular URLs get no rating tag; filtering is client-side only (`booru_client.dart:150-159`).
- **Paging:** only Danbooru pages (`booru_endpoints.dart:73`). Other engines return an empty page with `hasMore:false` for page > 1 (`booru_client.dart:147-149`).
- **Store / widget:** same as Latest, slot `booru/popular`. `BooruPopularTab` controls (`booru_popular_tab.dart:31-63`) call `_showPopular`, which does `select` then `refresh()` (`booru_screen.dart:133-137`).
- **Cancellation on date or scale change:** result-level only. The old request continues, and an in-flight `loadMore` for the old date is not invalidated.

### 2.3 Home – Following

- **Request:**
  - The loader reads `BooruTagsStore.state`. If it is empty, or page > 1, it returns an empty page (`booru_screen.dart:64-71`).
  - Otherwise `postsForTags` issues one `posts()` per unique normalised query, limit 10 each, all in parallel through `Future.wait` with no concurrency cap (`booru_client.dart:222-238`). Per-tag errors become an empty page.
- **Merge:** dedupe by `key`, then sort newest first by `createdAt` (:240-253), main isolate.
- **Store:** slot `booru/following`; a single page (`hasMore:false`), so no infinite scroll.
- **Widget:** `_FollowingTab`, a tag chip row (tap opens search, delete removes) above `_FeedTab` (:274-322).
- **Refresh triggers:** first selection of the tab (:95-99), and any change to the followed list once bootstrapped (observer at :81-90). Each follow or unfollow therefore re-fetches all N tags.
- **Cache:** same as Latest.

### 2.4 Search landing (editing)

- **Store:** one `BooruSearchStore` per screen (`booru_search_screen.dart:46`).
- **Suggestions:**
  - `BooruTagField.onChanged` → `store.type` (`booru_search_field.dart:119-126`; `booru_search_store.dart:83-88`).
  - Prefix must be ≥ 2 characters and not a metatag (`booru_query.dart:119-123`); a 250 ms Timer debounce is cancelled on each keystroke (`booru_search_store.dart:76,185-193`).
  - `client.suggestTags` builds `_tagsUri` (`booru_client.dart:205-218,283-301`): Danbooru/e621 `/tags.json?search[name_matches]=x*&search[order]=count&limit=12`; Moebooru `/tag.json?name=x*&order=count`; Gelbooru dapi `tag&name_pattern=x%`.
  - `parseBooruTagSuggestions` (`booru_parse.dart:31-46`) runs on the main isolate; errors become `[]`.
  - A stale reply is dropped when `state.input != input` (`booru_search_store.dart:195-199`). The HTTP request is not aborted, and nothing is cached.
- **Saved searches:** from `BooruTagsStore` / SQLite (`booru_search_landing.dart:220-258`).
- **Recent searches:** pref `plugin.booru.search_history` as JSON, capped at 20, identity `booruTagSetKey` (`booru_search_screen.dart:41-45`; `plugin_search_history.dart:6,23-38`; `booru_query.dart:108-111`).
- **Query tools:** rating metatags up to the max rating, score order, and the `-` / `~` operators (`booru_search_landing.dart:150-218`; `booru_query.dart:143-161`).

### 2.5 Search results

- **Request:** `search()` creates a **new** `BooruFeedStore` per query, with loader `client.search(query,page)` → `posts(booruQueryTokens(query))` (`booru_search_store.dart:171-183`; `booru_client.dart:82-86`). It awaits the `history.remember` pref write before calling `refresh()` (:181-182).
- **Store:** the per-query store, exposed as `results` (:79). The widget is keyed by `ObjectKey(results)`, and `BooruSearchStore` is provided to descendants (`booru_search_screen.dart:191-196`).
- **Widget:** `BooruSearchResults` (`booru_search_results.dart:14-54`).
  - Shows a full-screen spinner on every load, pull-to-refresh included (:25). Home keeps the grid instead.
  - Related-tag chips are recomputed in `build` over all loaded posts (:36; `booru_query.dart:129-138`).
- **Cancellation on a new query:** the previous store is neither destroyed nor cancelled; it is "left to the GC" (:176-179), so its requests and any `loadMore` keep running. `destroy` only destroys the latest store (:201-207).
- **Cache:** only the current list, unbounded within that query.

### 2.6 Post screen / pager

- **Entry:**
  - `openBooruPost(posts, index, feed)` (`booru_grid.dart:96-104`) passes the source list, an optional search store (`Provider<BooruSearchStore?>`), and the feed.
  - Cards in mixed feeds open `BooruPostScreen` instead: a one-post pager with no feed (`booru_post_screen.dart:24-34`).
- **Store:** `BooruPagerStore` (`booru_post_pager.dart:29`) adopts feed updates only when they extend its current list (`booru_pager_store.dart:28,37-40,52-56`). It prefetches data, not images.
- **Widget:** `PageView.builder` of `BooruPostDetails`, keyed by post key (`booru_post_pager.dart:48-54`).
  - There is no keep-alive anywhere in the plugin (grep: none), so swiping back re-runs every per-page request.
- **Media:** `BooruPostMedia` (`booru_post_media.dart:15-49`).
  - Image: `displayUrl` (sample, else file, else preview) in an `AspectRatio` clamped to 0.4–2.2, `BoxFit.contain`, decode bounded to width × DPR.
  - Video: `TweetVideo(videoUrl)`; poster is the preview; pool key `booru-<host>-<id>:0` (`_video.dart:187-189`).
  - The Hero tag matches the grid tile's (`booru_grid.dart:18`).
- **Tag info:**
  - A `BooruLoadStore` starts in `initState` (`booru_post_screen.dart:55-61`).
  - One request for all tags (`booru_endpoints.dart:77-96`): Danbooru `/tags.json?search[name_comma]=…&only=name,category,post_count`; e621 `search[name]`; Gelbooru dapi `tag&names=`; Moebooru `/post.json?tags=id:N&include_tags=1` (kinds only).
  - `parseBooruTagInfo` runs on the main isolate (`booru_detail_parse.dart:12-24`). On failure it falls back to the post's own categories (`booru_client.dart:164-185`).
  - Grouping and sorting run in `build` (`booru_tag_list.dart:87-90`).
- **Cache:** none across page instances.

### 2.7 Full-screen viewer

- **Entry:** `openPluginImageViewer` with a single item: url = `viewerUrl`, downloadUrl = `originalUrl` (`booru_post_media.dart:51-68`).
- **Image variant:** `viewerUrl` is the sample by default, or the original file when "original in viewer" is on (`booru_display.dart:44`). A video with no playable URL opens the original externally instead (`booru_post_media.dart:52-55`).
- **Widget:** `PluginImageViewer` → `TweetPhoto(inPageView:true)` (`plugin_post_media.dart:752-775`).
- **Decode is unbounded.** This path passes `cacheWidth:null` (`_photo.dart:35-37,107-118`), so no resize wrapper is applied (`pub:extended_image_library-5.0.1/lib/src/extended_resize_image_provider.dart:93-115`). Full-size decodes go into the 96 MiB ImageCache (`main.dart:464`).
- Single item only: you cannot swipe to neighbouring posts from the viewer.

### 2.8 Tag sheet and wiki

- **Sheet:** `showBooruTagActions` (`booru_tag_sheet.dart:25-67`) reads followed/hidden state from the stores. Actions:
  - **Search:** pushes a new `BooruSearchScreen`.
  - **Add / exclude:** calls `search.refine`, which creates a new results store (see 2.5), then pops the pager.
  - **Follow:** SQLite write followed by a full re-read (`booru_store.dart:34-55`), which triggers the Following refetch (2.3).
  - **Hide:** pref JSON write (:89-108). Grids already on screen are **not** re-filtered: grid widgets never read `BooruMuteStore`, and filtering only happens at fetch time (`booru_client.dart:112-127`).
- **Wiki:**
  - `_WikiSheet` uses a `BooruLoadStore` (`booru_tag_sheet.dart:150-158`).
  - `booruWikiUri` (`booru_endpoints.dart:103-113`): Danbooru/e621 `/wiki_pages.json?search[title]`; Moebooru `/wiki.json?query`; none for Gelbooru.
  - `parseBooruWiki` (`booru_detail_parse.dart:35-41`). A 404 shows as empty.
  - `booruPlainText` runs its regexes in `build` (`booru_tag_sheet.dart:181`). No cache.

### 2.9 Comments

- **Trigger:** a collapsed ExpansionTile; `ensure()` runs on first expansion (`booru_comments.dart:43-49`).
- **Request:** `booruCommentsUri` (`booru_endpoints.dart:115-131`): Danbooru/e621 `/comments.json?group_by=comment&search[post_id]&limit=100`; Moebooru `/comment.json?post_id`. Hidden for Gelbooru (:101; `booru_post_screen.dart:93`).
- **Parse:** `parseBooruComments`, sorted oldest first (`booru_detail_parse.dart:47-73`), main isolate.
- One request, at most 100 comments, no paging. `booruPlainText` runs per tile in `build` (`booru_comments.dart:93`). Cache: per widget only.

### 2.10 Related strips

- **Which strips:**
  - Artist ("More from …"): appears once the tag kinds identify an artist (`booru_tag_list.dart:60-74`; `booru_post_screen.dart:117-123`).
  - Family (`parent:<parentId ?? id>`): shown when the post has a parent or children (`booru_related.dart:15`; `booru_post_screen.dart:91-92`).
- **Requests:** each strip owns a `BooruFeedStore` running `client.search(query, limit:20)` from `initState`, destroyed on dispose (`booru_related.dart:36-47`). That is up to 2 extra requests per page view, or up to 4 each when filtering empties pages (`booru_store.dart:134-143`).
- **Display:** hidden while loading, on error or when empty; the current post is excluded by id (`booru_related.dart:51-62`).
- **Thumbnails:** preview image; height 132; width = 132 × aspect clamped to 0.6–1.5, so the size is known before load; decode bounded (:20,99-114).
- **Tap:** opens a pager over the strip only, with no feed (:106).
- The artist strip is keyed by artist (`booru_post_screen.dart:119`), and `ScopedBuilder` returns builder output in place (`pub:flutter_triple-3.0.0/lib/src/widgets/scoped_builder.dart:197-216`). So it is not refetched when tag info moves from loading to loaded.

### 2.11 Settings

- **Writes:** prefs are written on every keystroke for host (with an engine guess), login and API key (`booru_settings.dart:240-249,261,273`). Presets and custom sites set preset, engine, host and rating together (:61-120; `booru_engines.dart:213-267`).
- **Other controls:** max rating (:277-300); mute list via `BooruMuteStore` (:317-364); display prefs (`booru_display_settings.dart:8-71`); turning "show tab" off pins booru on the feed strip (:306-315).
- **Network:** the only call is "Test connection", `client.latest(limit:1)` (:128-156).
- Changes take effect on the next request, since the client re-reads prefs per call. Nothing refreshes the session feeds (see 2.1).

### 2.12 Following tags interleaved into X groups and the home timeline

- **Group feed request path:**
  - `_loadPluginPosts` loops over enabled sources (`lib/group/_feed.dart:204-225`).
  - The ids are the booru group members, plus all followed tags when this is the combined Following feed and "in home feed" is on (`lib/group/feed_source_reload.dart:29-34`; `booru_interleaved.dart:12-19`).
  - `groupPosts` → `interleavedPosts` (`subscription_source.dart:57-58`) → `loadBooruInterleaved` → `postsForTags(limitPerTag:8)` (`booru_interleaved.dart:10,21-35`; same fan-out as 2.3) → `booruInterleavedItems` (`booru_post_card.dart:87-95`).
- **Home "For you":** the same loader runs when booru is in the home feed (`lib/home/_for_you.dart:91-105`).
- **Store:** the host's `ProgressiveFeedStore` (`lib/tweet/progressive_feed_store.dart:33-180`).
  - 30 s timeout (:46).
  - On reload it cancels at the result level via run counters and `ReadRequestScope`; booru's HTTP is not aborted (:60-146).
  - Items are sorted on the main isolate (:27-29).
  - Loaded once per mount, no paging (`lib/group/_feed.dart:194-199`).
- **Cache:** `FeedSnapshotCache` uses key `feed:<source>:<sha256(sorted ids)>`, a 7-day max age, 100 items, and prunes to 40 keys (`lib/tweet/feed_snapshot_cache.dart:10-19,38-46`). It only writes items that carry a `snapshot` (:41). Booru items have no `id`, `linkUrl` or `snapshot` (`booru_post_card.dart:89-94`; `provenance_accent.dart:27-41`). As a result:
  - booru never gets an instant or offline preview;
  - partial-failure dedupe falls back to date-based keys (`progressive_feed_store.dart:131-134`).
- **Widget:** `BooruPostCard` uses a fixed 72×72 preview with explicit `cacheWidth = 72×DPR` (`booru_post_card.dart:36-46`), so its size is known before load. Tap opens `BooruPostScreen`; long-press opens the shared post sheet.
- **Group membership:**
  - Membership rows store the bare normalised tag as `profile_id`. There is no prefix (`plugin.dart:76`), and the table has only `(group_id, profile_id)` with no source column (`repository.dart:179`).
  - `BooruTagsStore.remove` deletes membership rows by that bare id (`booru_store.dart:48-52`).
  - A tag equal to another source's bare id therefore shares the same row. For example, a subreddit name: `RedditSubscription` id is the subreddit name (`entities.dart:807-824`).
  - `in_feed` is always written as 1 and never read (`booru_store.dart:68`; `booru_interleaved.dart:15-19`).

### 2.13 Cache inventory

| What | Where / key | Bound |
|---|---|---|
| Home feeds | memory; `PluginSessionStore` slots `booru/latest`, `booru/popular`, `booru/following` (plus `booru/view`, `booru/popular-query`) | Home lifetime. Unbounded growth. Not persisted. Not invalidated on config change. |
| Search results | memory; current store (plus orphaned previous stores until GC) | Screen lifetime. Unbounded within a query. |
| Tag info, comments, wiki, strips | per-widget `BooruLoadStore` / `BooruFeedStore` | Widget lifetime; refetched on every page rebuild. |
| Suggestions | none | — |
| Followed tags | SQLite `booru_subscription` + `BooruTagsStore` | Unbounded rows; full re-read after each write. |
| Blacklist | pref `plugin.booru.muted_tags` + `BooruMuteStore` | Unbounded; the client re-decodes it on every page. |
| History | pref `plugin.booru.search_history` | 20 entries. |
| Image bytes (disk) | `<tmp>/cacheimage/md5(url)` (`pub:extended_image_library-5.0.1/lib/src/platform.dart:13,56`; `network_image_io.dart:142`) | No size cap. Never expires, because no `cacheMaxAge` is passed (:193-203). No app-level sweep found. Not declared in `caches`. Lookups use sync `existsSync` / `statSync` on the main isolate (:190-195). |
| Decoded images | Flutter `ImageCache`, keyed by (url, cacheWidth) | 96 MiB (`main.dart:464`). Grid, post page and viewer each decode the same sample URL into separate entries; the viewer's entry is full resolution. |
| Mixed-feed snapshot | `FeedSnapshotCache` | Effectively empty for booru. |
| Video players | `VideoControllerPool`, key `booru-<host>-<id>:0` | 3 (`constants.dart:99`). |

## 3. Site matrix

“Cap” means the reader's maximum-rating setting.

**What every row shares.** One global `BooruClient` handles all requests, and it talks to one host at a time (lib/main.dart:905).

- **Headers.** Every API call is a `GET` with a 20 s timeout. It sends exactly two headers: `User-Agent: XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)` and `Accept: application/json` (booru_client.dart:43-45, 329-340). It never sends `Authorization`, `Referer` or cookies.
- **Media sends no headers at all.** This covers thumbnails and samples (`ExtendedImage.network`, booru_image.dart:34-42, 51-58), the video player (booru_post_media.dart:43-48) and downloads (lib/plugins/plugin_post_media.dart:598-606).
- **Credential storage.** There is one global pair for whichever host is selected: `plugin.booru.login` and `plugin.booru.api_key` (lib/constants.dart:465-466; booru_client.dart:60-62).
  - Both are plaintext SharedPreferences. `PrefServiceShared.init(prefix: 'pref_')` (lib/main.dart:486) calls `SharedPreferences.getInstance()` (`pub:pref-2.8.0/lib/src/service/shared_preferences.dart:17-21`).
  - Secure storage is not used: a grep of `lib/` and `pubspec.yaml` finds no secure-storage use.
  - Both are written on every keystroke (booru_settings.dart:261, 273). The key field is obscured (booru_settings.dart:268).
  - The key, but not the login, is removed from exports and backups by the `api_key` suffix rule (lib/constants.dart:867-872; lib/utils/crash_reporter.dart:241-245).
- **Error and rate-limit handling** (booru_client.dart:344-377):
  - 401/403 become `unauthorized`, 404 becomes `notFound` and 429 becomes `rateLimited`.
  - Every other non-2xx status, and any 2xx body that is not JSON, becomes `badResponse`.
  - There is no retry, no backoff, no `Retry-After` or `x-rate-limit-*` parsing, no delay between requests and no concurrency cap.
  - Failures are swallowed silently in `loadMore` (booru_store.dart:170-171), per tag in `postsForTags` (booru_client.dart:235-237), in `suggestTags` (booru_client.dart:213-217) and in `tagInfo` (booru_client.dart:182-184).
  - Images get a 15 s limit and 1 retry (booru_image.dart:4, 39-40, 56-57).
- **Fan-out.**
  - The Following tab sends one request per followed tag, all at once (`Future.wait`, booru_client.dart:229-238).
  - `refresh` can chain up to 4 page requests and `loadMore` up to 3 (booru_store.dart:134-143, 156-167).
  - Every opened post fires one tag lookup (booru_post_screen.dart:59-60) and up to two related-post feeds (booru_post_screen.dart:91-92, 117-123).
- **Page sizes.**
  - 40 for Latest, Search and Popular (booru_client.dart:46).
  - 20 for the related strips (booru_related.dart:39).
  - 10 per followed tag, and the Following tab only ever loads page 1 (booru_client.dart:222-225; booru_screen.dart:66-70).
  - 8 per tag in the home feed (booru_interleaved.dart:10).
  - 12 tag suggestions, only for a prefix of 2+ characters, after a 250 ms debounce (booru_client.dart:205-208; booru_query.dart:117-123; booru_search_store.dart:76).
  - 1 for "Test connection" (booru_settings.dart:132).
- **End of feed.** `hasMore` is `rawCount >= limit`, counted before rating and mute filtering (booru_client.dart:107, 110-127). The feed also stops after 3 pages that add nothing new (booru_store.dart:166-169). Repeated posts are dropped by their `host:id` key (booru_store.dart:177-185; booru_models.dart:185).
- **Response format.** JSON only. An empty, `[]` or `{}` body is read as an empty list (booru_client.dart:364-376).
- **Rating cap.** It is applied client-side to every page (booru_client.dart:112-127; booru_parse.dart:349-354). It is also sent to the server only where a row below says so (booru_client.dart:256-281).

| Engine / site | Host | Auth: fields · how sent · where stored | Endpoints used (exact paths and params) | Pagination · page size | Rate-limit handling | User-Agent / headers | Response format · notable quirks |
|---|---|---|---|---|---|---|---|
| **Engine: Danbooru** (`danbooru`) | Any configured host. Custom hosts are guessed as Danbooru when they contain `donmai.us`, `danbooru` or `aibooru` (booru_engines.dart:185-189) | `login` + `api_key` as query params on every API call (booru_endpoints.dart:27-36). Stored in shared prefs (above). BS sends an HTTP Basic header instead and uses params only on web (BS:packages/booru_clients/lib/src/danbooru/danbooru_client.dart:65-70; BS:lib/boorus/danbooru/client_provider.dart:47-56) | **Posts:** `GET /posts.json?limit=40&tags={q}&page={n}` (booru_client.dart:303-317)<br>**Popular:** `GET /explore/posts/popular.json?date=yyyy-MM-dd&scale={day,week,month}&page={n}&limit=40` (booru_endpoints.dart:43, 49-55)<br>**Autocomplete:** `GET /tags.json?search[name_matches]={prefix}*&search[order]=count&limit=12` (booru_client.dart:286-290)<br>**Tag lookup:** `GET /tags.json?search[name_comma]=a,b&only=name,category,post_count&limit={n}` (booru_endpoints.dart:81-86)<br>**Wiki:** `GET /wiki_pages.json?search[title]={tag}&limit=1` (booru_endpoints.dart:105-107)<br>**Comments:** `GET /comments.json?group_by=comment&search[post_id]={id}&limit=100&only=id,created_at,score,body,is_deleted,creator[name]` (booru_endpoints.dart:117-123)<br>**Parent/child:** a posts search for `parent:{parent_id or id}` with limit 20 (booru_related.dart:15, 39; booru_post_screen.dart:91-92). **Artist strip:** a posts search for the artist tag (booru_post_screen.dart:117-123)<br>**Pools:** none. `pool` is only a recognised metatag name (booru_query.dart:39) | Page number `page={n}` starting at 1 (booru_client.dart:314-317). Popular is paged too (booru_endpoints.dart:73). No `b`/`a` cursors. 40 per page | Shared handling: 429 becomes an error and nothing more. A 410 page-limit answer becomes `badResponse` (booru_client.dart:357-362) | Shared UA and `Accept` (booru_client.dart:44-45, 334) | Bare JSON array (booru_parse.dart:18). Tag kinds come from the `tag_string_{kind}` fields (booru_parse.dart:122-134). URLs come from `preview_file_url`, `large_file_url` and `file_url` (booru_parse.dart:86-88). Rating `s` means sensitive (booru_models.dart:61-62). The cap is sent to the server as `rating:g`, `-rating:q -rating:e` or `-rating:e` (booru_client.dart:275-280) |
| **Engine: Moebooru** (`moebooru`) | Any configured host. Guessed for `yande.re`, `konachan`, `sakugabooru` and `hypnohub` (booru_engines.dart:179-183) | `login` + `password_hash` as query params (booru_endpoints.dart:27-36). The reader must paste the SHA1 hash, not the password (lib/l10n/intl_en.arb:1616-1619). BS uses the same param names (BS:packages/booru_clients/lib/src/moebooru/moebooru_client.dart:52-57) but computes the hash from the password with a per-site salt (BS:packages/booru_clients/boorus.yaml:186, 195, 201; BS:lib/boorus/moebooru/configs/types.dart:8-22) | **Posts:** `GET /post.json?limit=40&tags={q}&page={n}` (booru_client.dart:318-321)<br>**Popular:** `GET /post/popular_by_{day,week,month}.json?day=&month=&year=`, with `day` left out for month (booru_endpoints.dart:58-63)<br>**Autocomplete:** `GET /tag.json?name={prefix}*&order=count&limit=12` (booru_client.dart:291-295)<br>**Tag lookup (kinds only):** `GET /post.json?tags=id:{id}&api_version=2&include_tags=1&limit=1` (booru_endpoints.dart:91-94)<br>**Wiki:** `GET /wiki.json?query={tag}&limit=10`, with the exact title picked client-side (booru_endpoints.dart:108-109; booru_detail_parse.dart:35-44)<br>**Comments:** `GET /comment.json?post_id={id}` (booru_endpoints.dart:126-127)<br>**Parent/child and artist strip:** same as Danbooru (posts search)<br>**Pools:** none | `page={n}` (booru_client.dart:318-321). Popular is a single page: requests for page 2+ return empty (booru_client.dart:147-149; booru_endpoints.dart:73). 40 per page | Shared handling | Shared | Bare JSON array (booru_parse.dart:18). `tags` is a space-separated string (booru_parse.dart:237). `created_at` is Unix seconds (booru_parse.dart:270-276). `author` is the uploader (booru_parse.dart:114-118). `include_tags` returns a name-to-kind map (booru_detail_parse.dart:10-31). Rating `s` means safe and maps to General (booru_models.dart:52-60). Kind 5 (circle) maps to artist and 6 (faults) to meta (booru_models.dart:255-256). The cap is sent as `rating:s`, `-rating:q -rating:e` or `-rating:e` (booru_client.dart:266-272) |
| **Engine: Gelbooru v2** (`gelbooru_v2`; `gelbooru` and `gelbooru2` are also accepted, booru_engines.dart:25-28) | Default for any unrecognised host (booru_engines.dart:172-191). Rule34's API is rerouted to `api.rule34.xxx` (booru_engines.dart:149-159; booru_endpoints.dart:18) | `user_id` + `api_key` as query params (booru_endpoints.dart:27-36). BS uses the same names (BS:packages/booru_clients/boorus.yaml:49-51; BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:108-109) | Every call is `GET /index.php?page=dapi&s={kind}&q=index&json=1` (booru_endpoints.dart:38-40):<br>**Posts:** `s=post&limit=40&tags={q}&pid={n-1}` (booru_client.dart:322-325)<br>**Popular:** none (booru_endpoints.dart:64-65, 70). Replaced by a posts search for `sort:score` (booru_client.dart:139-146; booru_query.dart:157-158), with a note in the UI (booru_popular_tab.dart:31, 65-77)<br>**Autocomplete:** `s=tag&limit=12&name_pattern={prefix}%` (booru_client.dart:296-299)<br>**Tag lookup:** `s=tag&names={a b …}&limit={n}` (booru_endpoints.dart:89-90)<br>**Wiki:** none (booru_endpoints.dart:98, 110-111)<br>**Comments:** none. The code says "Gelbooru only answers comments in XML" (booru_endpoints.dart:100-101, 128-129)<br>**Parent/child and artist strip:** posts search<br>**Pools:** none | Zero-based page index `pid={n-1}` (booru_client.dart:324). 40 per page | Shared handling. A 200 response whose body is not JSON becomes `badResponse` (booru_client.dart:369-376) | Shared. **No `Referer`.** BS sends one for this family (BS:lib/boorus/gelbooru_v2/client_provider.dart:72) | Either a `{"post":[…]}` object or a bare array (booru_parse.dart:19). Tags come as `{"tag":[…]}` or a bare array (booru_parse.dart:37). Ruby-style dates (booru_parse.dart:299-317), falling back to `change` (booru_parse.dart:277-280). Booleans arrive as `"true"`/`"false"` strings (booru_parse.dart:171-173). The file URL is built as `/images/{directory}/{image}` when `file_url` is missing (booru_parse.dart:319-330). Ratings come as words (booru_models.dart:63-72). **No rating tag is sent to the server: the cap is client-side only** (booru_client.dart:260-263) |
| **Engine: e621** (`e621`; `e926` is also accepted) | Any host containing `e621` or `e926` (booru_engines.dart:178) | `login` + `api_key` as query params (booru_endpoints.dart:27-36). BS sends an HTTP Basic header (BS:packages/booru_clients/lib/src/e621/e621_client.dart:10-11, 31-36) | **Posts:** `GET /posts.json?limit=40&tags={q}&page={n}` (booru_client.dart:314-317)<br>**Popular:** `GET /popular.json?date=yyyy-MM-dd&scale={day,week,month}` (booru_endpoints.dart:56-57)<br>**Autocomplete:** `GET /tags.json?search[name_matches]={prefix}*&search[order]=count&limit=12` (booru_client.dart:286-290)<br>**Tag lookup:** `GET /tags.json?search[name]=a,b&limit={n}` (booru_endpoints.dart:87-88)<br>**Wiki:** `GET /wiki_pages.json?search[title]={tag}&limit=1` (booru_endpoints.dart:105-107)<br>**Comments:** `GET /comments.json?group_by=comment&search[post_id]={id}&limit=100` (booru_endpoints.dart:124-125)<br>**Parent/child and artist strip:** posts search<br>**Pools:** none | `page={n}` (booru_client.dart:314-317). Popular is a single page (booru_endpoints.dart:73; booru_client.dart:147-149). 40 per page | Shared handling. A 503 becomes a generic `badResponse` (booru_client.dart:357-362) | **API calls do get a descriptive UA** naming project, version and a contact URL (booru_client.dart:44-45, 334). **Media requests get none** (booru_image.dart:34-58) | The response is `{"posts":[…]}` (booru_parse.dart:20). `file`, `preview`, `sample`, `score` and `relationships` are nested objects, and `tags` are grouped by kind with the `invalid` group ignored (booru_parse.dart:175-226). Empty `{"tags":[]}` and `{"comments":[]}` wrappers are handled (booru_parse.dart:38; booru_detail_parse.dart:49). The commenter comes from `creator_name` (booru_detail_parse.dart:61). Rating `s` means safe and maps to General (booru_models.dart:52-60). Kind 5 is species and 7 is meta (booru_models.dart:253-254). The cap is sent the same way as Moebooru (booru_client.dart:266-272) |
| Danbooru | `https://danbooru.donmai.us` (booru_engines.dart:57-62). Also the app's default host and engine (lib/main.dart:668-670; booru_client.dart:52-58) | Same as engine | Same as engine | Same as engine | Shared | Shared | Default cap is General (booru_settings.dart:66-71), so `rating:g` is appended. BS lists the same site (BS:packages/booru_clients/boorus.yaml:8) |
| AIBooru | `https://aibooru.online` (booru_engines.dart:63-68) | Same as Danbooru engine | Same as Danbooru engine | Same as Danbooru engine | Shared | Shared | Cap General. BS also treats it as a Danbooru site (BS:packages/booru_clients/boorus.yaml:17-18) |
| Yande.re | `https://yande.re` (booru_engines.dart:69-74) | Same as Moebooru engine. BS has a site-specific salt (BS:packages/booru_clients/boorus.yaml:194-197) | Same as Moebooru engine | Same as Moebooru engine | Shared | Shared | Cap General, so `rating:s` is appended (booru_client.dart:266-272) |
| Konachan | `https://konachan.com` (booru_engines.dart:75-80) | Same as Moebooru engine. BS salt at BS:packages/booru_clients/boorus.yaml:184-188 | Same as Moebooru engine | Same as Moebooru engine | Shared | Shared | Cap General, so `rating:s` is appended |
| Sakugabooru | `https://www.sakugabooru.com` (booru_engines.dart:81-86). BS reads from `https://sakugabooru.com/` and uses `www` only for POSTs (BS:packages/booru_clients/boorus.yaml:198-203; BS:lib/boorus/moebooru/client_provider.dart:35-60) | Same as Moebooru engine. BS salt at BS:packages/booru_clients/boorus.yaml:201 | Same as Moebooru engine | Same as Moebooru engine | Shared | Shared | Cap General |
| Safebooru | `https://safebooru.org` (booru_engines.dart:87-92) | Same as Gelbooru v2 engine | Same as Gelbooru v2 engine | `pid` | Shared | Shared, no Referer | Cap General, filtered client-side only (booru_client.dart:260-263). BS classes it as Gelbooru 0.2 (BS:packages/booru_clients/boorus.yaml:177) |
| Gelbooru | `https://gelbooru.com` (booru_engines.dart:93-98) | Same as Gelbooru v2 engine. The code says "Gelbooru.com often needs an API key" (booru_engines.dart:52-53), but the field is labelled "Optional" (lib/l10n/intl_en.arb:1472) | Same as Gelbooru v2 engine | `pid` | Shared | Shared, no Referer. BS sets one (BS:lib/boorus/gelbooru/client_provider.dart:55) | Cap Explicit (booru_engines.dart:205-211). The search chips use rating words: general, sensitive, questionable, explicit (booru_query.dart:147). The `@attributes`/`post` wrapper is handled (booru_parse.dart:19). BS treats this site as a separate engine, "Gelbooru 0.2.5" (BS:packages/booru_clients/boorus.yaml:21-31) |
| Rule34 | Page links go to `https://rule34.xxx`; the API goes to `https://api.rule34.xxx` (booru_engines.dart:99-104, 149-170) | Same as Gelbooru v2 engine, and listed under "guest-friendly first" (booru_engines.dart:52-55). **BS marks auth as required** (BS:packages/booru_clients/boorus.yaml:111-114) | Same as Gelbooru v2 engine, all on the API host (booru_endpoints.dart:18). BS also puts posts and `/autocomplete.php` on the API host (BS:packages/booru_clients/boorus.yaml:116-119) | `pid` | Shared | Shared, no Referer | Cap Explicit. Built file URLs resolve against `https://rule34.xxx` (booru_client.dart:102-106; booru_parse.dart:319-330, 340-347) |
| TBIB | `https://tbib.org` (booru_engines.dart:105-110) | Same as Gelbooru v2 engine | Same as Gelbooru v2 engine | `pid` | Shared | Shared, no Referer | Cap General, filtered client-side only. BS lists it at BS:packages/booru_clients/boorus.yaml:176 |
| Xbooru | `https://xbooru.com` (booru_engines.dart:111-116) | Same as Gelbooru v2 engine | Same as Gelbooru v2 engine | `pid` | Shared | Shared, no Referer | Cap Explicit (booru_engines.dart:205-211). The code expects the "file URL built from directory + image" case here (booru_parse.dart:319). BS lists it at BS:packages/booru_clients/boorus.yaml:175 |
| e926 | `https://e926.net` (booru_engines.dart:117-122) | Same as e621 engine | Same as e621 engine | Same as e621 engine | Shared | Descriptive UA on API calls only | Cap General, so `rating:s` is appended. BS lists it at BS:packages/booru_clients/boorus.yaml:212 |
| e621 | `https://e621.net` (booru_engines.dart:123-128) | Same as e621 engine | Same as e621 engine | Same as e621 engine | Shared | Descriptive UA on API calls only | Cap Explicit, so no rating tag is appended (booru_client.dart:266-272). BS lists it at BS:packages/booru_clients/boorus.yaml:211 |

### Correctness risks

#### All engines
- **X1. Credentials follow the reader to other hosts** (booru_settings.dart:61-73, 226-229, 240-248; booru_client.dart:60-62; booru_endpoints.dart:27-36). Switching preset, engine or host never clears the one global login/key, so a Danbooru `api_key` is sent to gelbooru.com, with the Danbooru login sent as `user_id`. Boorusama ties credentials to each site's config (BS:lib/boorus/e621/client_provider.dart:9-20).
- **X2. The API key or password hash is shown on screen** (booru_client.dart:33, 348, 352, 355, 360, 374). Exception messages embed the full URI, which carries `api_key`/`password_hash` (booru_endpoints.dart:33-36). The feed and search error pages then print `'$error'` (lib/ui/errors.dart:611, reached from booru_screen.dart:241-246 and booru_search_results.dart:26-31).
- **X3. No request throttling** (booru_client.dart:229-238, 329-340). Bursts go out concurrently with nothing limiting them. Boorusama puts every engine here through a 10 requests/s sliding window that admits requests one at a time (BS:lib/core/http/client/src/providers/providers.dart:44-46, 188-197; BS:lib/core/http/client/src/interceptors/sliding_window_rate_limit_interceptor.dart:101-123).
- **X4. Retry storm after a 429 or a page-limit error** (booru_store.dart:150-175 and booru_grid.dart:47-53). `loadMore` swallows the error but leaves `_hasMore` true, and every scroll notification near the end calls it again immediately, with no backoff.
- **X5. Different HTTP failures produce the same message** (booru_client.dart:345-362). A 403 is shown as "check login and API key" (lib/l10n/intl_en.arb:1538), and 410, 422 and 503 all show as one generic bad response. Boorusama separates 401, 403, 410 (pagination limit), 422 (tag limit), 429 and 503 (BS:lib/core/errors/types.dart:34-41). It also checks 403/503 bodies for Cloudflare challenge pages (BS:lib/core/ddos/solver/src/protection_detector.dart:18-55).
- **X6. Detail calls use the current host, not the post's own host** (booru_client.dart:129-130, 170, 189, 200). `BooruPost` does record its `host` and `engine` (booru_models.dart:79-83). But feeds are cached per session and only refreshed when empty (booru_screen.dart:57, 122-126). So after a site switch, opening an old post looks up the same numeric id on the new host.
- **X7. Page-number paging can repeat or skip posts** (booru_client.dart:303-327). No engine uses a `b{id}`-style cursor. Repeats are removed (booru_store.dart:177-185), but posts that slide onto an already-fetched page, for example after deletions, are skipped without any sign. Boorusama also pages by number, so this matches it (BS:packages/booru_clients/lib/src/danbooru/danbooru_client_posts.dart:15-20; BS:packages/booru_clients/lib/src/e621/e621_client.dart:46-53).
- **X8. The Following tab never paginates** (booru_screen.dart:66-70; booru_client.dart:222-238). It only ever shows the first 10 posts per followed tag.
- **X9. Dead end when the rating filter empties every page** (booru_store.dart:129-147 and booru_screen.dart:247-260). After 4 fully filtered pages the tab shows an empty pane, which has no scroll to reach page 5 or later. This affects every case where the cap is only applied client-side: Gelbooru v2, and all popular lists, whose URIs carry no rating tag (booru_endpoints.dart:46-67).
- **X10. Images and other media are fetched without the UA or a Referer** (booru_image.dart:34-42, 51-58; lib/plugins/plugin_post_media.dart:598-606). Boorusama sends its UA plus each engine's extra headers on every image (BS:lib/core/images/booru_image.dart:79-84; BS:lib/core/http/client/src/providers/providers.dart:124-133).
- **X11. The spec is out of date** (docs/specs/booru-plugin.md:21, 56). It lists 9 presets and calls Gelbooru-family credentials "Optional". The code ships 12 presets, including Rule34 (booru_engines.dart:56-129); see G2.

#### Danbooru (Danbooru, AIBooru)
- **D1. Credentials go in the query string** (booru_endpoints.dart:27-36). Boorusama sends HTTP Basic auth and uses `login`/`api_key` params only on web, to avoid CORS (BS:packages/booru_clients/lib/src/danbooru/danbooru_client.dart:65-70; BS:lib/boorus/danbooru/client_provider.dart:47-56).
- **D2. Deep pages fail without explanation** (booru_client.dart:314-317). Page numbers grow without limit. Boorusama shows a dedicated "pagination limit" message for a 410 (BS:lib/core/errors/types.dart:36). XTA turns it into a `badResponse` that is swallowed and retried (booru_client.dart:357-362; booru_store.dart:170-171).
- **D3. Checked and consistent with Boorusama.**
  - The appended rating tags (booru_client.dart:275-280) match Boorusama's query composer (BS:lib/boorus/danbooru/tags/tag/src/query_composer.dart:13-21).
  - `rating` is a free metatag, so it does not count toward the tag limit (BS:assets/tagdef.json:68-69).
  - The popular, tag-lookup and comment params match Boorusama's (BS:packages/booru_clients/lib/src/danbooru/danbooru_client_explores.dart:18-26; BS:packages/booru_clients/lib/src/danbooru/danbooru_client_tags.dart:20-28; BS:packages/booru_clients/lib/src/danbooru/danbooru_client_comments.dart:34-42).

#### Moebooru (Yande.re, Konachan, Sakugabooru)
- **M1. The password hash has to be pasted by the reader** (booru_endpoints.dart:31; lib/l10n/intl_en.arb:1616-1619). Boorusama derives it from the password with each site's salt (BS:packages/booru_clients/boorus.yaml:186, 195, 201; BS:lib/boorus/moebooru/configs/types.dart:8-22). A reader who types the plain password sends it in every URL and in error text (see X2).
- **M2. hypnohub is guessed as Moebooru** (booru_engines.dart:179-183). Boorusama lists hypnohub.net as Gelbooru 0.2 (BS:packages/booru_clients/boorus.yaml:151-154). A custom hypnohub site would therefore get `/post.json` instead of the dapi.
- **M3. Two endpoints are not confirmed by the reference client.** Autocomplete uses `tag.json?name={prefix}*` (booru_client.dart:291-295) and the tag lookup uses `include_tags=1` (booru_endpoints.dart:93-94). Boorusama uses neither; it loads `/tag/summary.json` instead (BS:packages/booru_clients/lib/src/moebooru/moebooru_client.dart:79-88). Both XTA calls fail silently (booru_client.dart:182-184, 213-217).
- **M4. Popular is a single unfiltered page** (booru_endpoints.dart:58-63; booru_client.dart:147-159). No rating tag can be sent, so under the General cap the one page is filtered client-side and there is no page 2.

#### Gelbooru v2 (Safebooru, Gelbooru, Rule34, TBIB, Xbooru)
- **G1. No server-side rating filter** (booru_client.dart:260-263). XTA already knows the correct metatags (booru_query.dart:143-151). Boorusama sends `rating:general`, `rating:safe` or `-rating:explicit` (BS:lib/boorus/gelbooru/tags/query_composer.dart:13-26; BS:lib/boorus/gelbooru_v2/tags/query_composer.dart:13-29). With the General cap that Safebooru and TBIB default to (booru_engines.dart:205-211), pages come back short or hit the X9 dead end.
- **G2. Rule34 needs credentials** (booru_engines.dart:52-55, 99-104; lib/l10n/intl_en.arb:1472). Boorusama marks Rule34 `auth.required: true` (BS:packages/booru_clients/boorus.yaml:111-114). It also turns Rule34's HTTP 200 "missing authentication" body into a 401 (BS:lib/core/http/client/src/interceptors/auth_error_response_interceptor.dart:8-37; BS:lib/boorus/gelbooru_v2/client_provider.dart:66-67). XTA reads that body as invalid JSON and reports `badResponse` (booru_client.dart:364-376) instead of a credentials error.
- **G3. The Gelbooru.com API-key requirement is barely acknowledged** (booru_engines.dart:52-53). The only mention is a comment saying it "often needs an API key". Nothing in the code mentions a 2025 requirement, the key is labelled optional (lib/l10n/intl_en.arb:1472), and an auth failure shows the generic credentials message (booru_client.dart:345-350). Boorusama does not mark gelbooru.com as auth-required (BS:packages/booru_clients/boorus.yaml:21-31), so neither codebase confirms the requirement.
- **G4. Gelbooru's "Too deep" pagination answer is mishandled** (booru_client.dart:322-325). Boorusama turns gelbooru.com's HTTP 200 "Too deep" body into a 410 (BS:lib/boorus/gelbooru/client_provider.dart:46-50). XTA parses it as invalid JSON, swallows it and retries on every scroll (booru_store.dart:150-175; booru_grid.dart:47-53).
- **G5. `sort:score` is sent without a direction** (booru_query.dart:157-158; booru_client.dart:139-146). Boorusama always sends `sort:score:desc` for both Gelbooru families (BS:lib/boorus/gelbooru/artists/widgets.dart:33; BS:lib/boorus/gelbooru_v2/artists/widgets.dart:33). If a host sorts ascending by default, the Popular fallback lists the lowest scores first.
- **G6. The tag dapi is assumed to answer in JSON** (booru_client.dart:296-299, 342-377; booru_endpoints.dart:89-90).
  - Boorusama parses gelbooru.com's tag dapi as either XML or JSON, depending on the Content-Type (BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:372-395).
  - For Gelbooru v2 forks, Boorusama avoids the tag dapi and uses `/autocomplete.php?q=` plus scraping tags from the HTML post page (BS:packages/booru_clients/boorus.yaml:77-80, 95-99, 118-119).
  - In XTA, an XML answer silently gives no suggestions and no tag kinds (booru_client.dart:182-184, 213-217).
- **G7. Suggestions are not ordered by post count** (booru_client.dart:296-299, compared with 288 and 293). The other engines ask for count order; the Gelbooru request does not, so the 12 suggestions come in the host's default order. Boorusama uses `page=autocomplete2&type=tag_query` on gelbooru.com (BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:170-185).
- **G8. No Referer header** (booru_client.dart:334; booru_image.dart:34-58). Boorusama sets `Referer: {site}` on both API and media requests for both Gelbooru families (BS:lib/boorus/gelbooru/client_provider.dart:55; BS:lib/boorus/gelbooru_v2/client_provider.dart:72; BS:lib/boorus/gelbooru_v2/gelbooru_v2_repository.dart:50-53).
- **G9. Only the full-size file URL is built from `directory` + `image`** (booru_parse.dart:319-330). Boorusama also builds thumbnail and sample URLs from them (BS:packages/booru_clients/lib/src/gelbooru_v2/post_v2_dto.dart:36-74). Without those, XTA's tiles fall back to the original file (booru_models.dart:135-157).
- **G10. Posts with no media are kept** (booru_parse.dart:77-78). Boorusama drops posts that lack `hash`/`md5` (BS:packages/booru_clients/lib/src/gelbooru_v2/parsers/gel_parsers.dart:46; BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:131). XTA keeps any post with an id and counts it toward `hasMore` (booru_client.dart:107).

#### e621 (e926, e621)
- **E1. The descriptive UA is only on API calls** (booru_client.dart:44-45, 334). API calls qualify: the UA names the project, a version and a contact URL. However:
  - The version is a fixed `0.1`, not the app version.
  - It names the developer ("by Aimdi"), not the signed-in account.
  - Image, video and download requests send no UA at all (booru_image.dart:34-58; lib/plugins/plugin_post_media.dart:598-606).

  Boorusama sends `{AppName}/{version}` on both API and media requests (BS:lib/core/http/client/src/providers/providers.dart:124-133, 155-160).
- **E2. Credentials go in the query string** (booru_endpoints.dart:27-36). Boorusama uses HTTP Basic auth (BS:packages/booru_clients/lib/src/e621/e621_client.dart:10-11, 31-36).
- **E3. Request volume** (booru_client.dart:229-238; booru_post_screen.dart:59-60). The Following tab fires one request per followed tag at the same moment. Every opened post triggers a `tags.json` lookup for post counts (booru_endpoints.dart:87-88), even though the post already carries its tag kinds (booru_parse.dart:146-150). With no limiter (X3) and 503 folded into `badResponse` (booru_client.dart:357-362), throttling shows up as tags that silently come back empty, or as a generic error.
- **E4. `invalid` tags and the lore/invalid kind codes are dropped** (booru_parse.dart:136-144, 183-194; booru_models.dart:247-259). Boorusama keeps the `invalid` group and maps code 6 to invalid and 8 to lore (BS:lib/boorus/e621/posts/parser.dart:62; BS:lib/boorus/e621/tags/parser.dart:16-26). In XTA those tags are never shown and cannot trigger the blacklist (booru_parse.dart:360-363), and lore suggestions get no kind.
- **E5. Posts with a null `file.url` are kept** (booru_parse.dart:206-210). Boorusama discards them (BS:lib/boorus/e621/posts/parser.dart:15-18; BS:lib/boorus/e621/posts/providers.dart:42-51). XTA renders them with fallback or empty media (booru_models.dart:135-157; booru_image.dart:23-31) and counts them toward `hasMore` (booru_client.dart:107).
- **E6. GIFs are shown from the sample image** (booru_models.dart:139-157). Boorusama deliberately uses the original instead of the sample for GIFs (BS:lib/boorus/e621/posts/parser.dart:30, 39). XTA prefers `sample.url` in tiles and in the detail view.
- **E7. Checked and consistent with Boorusama.** The posts, popular, tag-lookup (`search[name]`, comma-joined) and comment params match Boorusama's (BS:packages/booru_clients/lib/src/e621/e621_client.dart:41-62, 109-128, 157-202).

## 4. Feature matrix

**Partial** notes say what is missing. **Missing** notes say what was searched for.

| Feature | Status | Evidence (file:line) | Notes |
|---|---|---|---|
| 1a. Multi-site profiles | Partial | booru_engines.dart:38-50, 56-129, 213-267; booru_settings.dart:61-120, 184-203; booru_client.dart:48-58; docs/specs/booru-plugin.md:68 | There are 12 built-in presets. User-added hosts are saved as JSON in `plugin.booru.custom_sites`. Tapping a chip rewrites the single global engine/host/preset that `BooruClient` reads. **Missing:** a "profile" (`BooruPreset`) holds only id, name, engine and host. It has no credentials, blacklist, rating cap or follows of its own, and only one site is active at a time. **Since `5a3b9819`:** a custom `hypnohub.net` is guessed as Gelbooru 0.2. |
| 1b. Per-profile login / API key | Missing | lib/constants.dart:465-466; booru_settings.dart:61-73, 252-275; booru_client.dart:60-62, 129-130; booru_endpoints.dart:27-40 | Searched the preset and custom-site model and its encoding (booru_engines.dart:38-50, 241-249) for credential fields and found none. There is one global `login` + `api_key` pair, and `_applyPreset` never touches it. Every request to the active host attaches it as query parameters (`user_id`/`login`, `api_key`/`password_hash`). **So a key entered for one site is sent to whichever site is selected next.** **Since `ce934ebc`:** the pair is bound to the host it was entered for and sent nowhere else; still one pair, not one per profile. **Since `7825d39a`:** it is sent only when both the login and the key are filled in. |
| 1c. Credentials in secure storage | Missing | lib/main.dart:486; lib/constants.dart:465-466, 829-872; booru_settings.dart:261, 266-273, 387-390; lib/utils/crash_reporter.dart:241-245; lib/utils/pref_lists.dart:54-57; android/app/src/main/AndroidManifest.xml:38; booru_client.dart:26-34, 344-362; lib/ui/errors.dart:589-611; booru_screen.dart:241-246 | Searched pubspec.yaml and lib/ for flutter_secure_storage, SecureStorage, keychain, keystore and encrypted_shared: no match. Login and key are plain shared preferences (`PrefServiceShared`), written on every keystroke and only obscured on screen. The Moebooru password hash goes under the same `api_key` key. The app's only protection is redaction: the `api_key` suffix keeps the key out of export, import and crash reports. `plugin.booru.login` matches no rule, so it is exported. `allowBackup="false"` keeps prefs out of Android auto-backup. **Leak:** `BooruException.message` contains the request URL, credentials included. The generic error page that feeds use prints `'$error'`, so a 401/403/404/429/5xx can show the key on screen. **Since `dba4687f`:** error text masks the credentials. Storage stays in app settings for now (decision 3). |
| 2a. Tag autocomplete with category colours and post counts | Present | booru_client.dart:205-218, 283-301; booru_parse.dart:31-66; booru_search_store.dart:76, 133-144, 185-199; booru_search_landing.dart:88-147; booru_query.dart:117-126 | Prefix lookup with a 250 ms debounce. Danbooru, e621 and Moebooru are asked for count order; Gelbooru's `name_pattern` request sends no order. Each row is tinted by tag kind and shows a locale-formatted count, with the full count in `semanticsLabel`. It needs at least 2 characters, skips metatags and keeps a typed `-` or `~`. A failed lookup silently shows nothing. Picked kinds also colour the query chips. **Since `864e395f`:** Gelbooru 0.2 forks, which answer the tag endpoint in XML, get suggestions, kinds and counts too (not checked live). |
| 2b. Per-engine metatags (`rating:`, `score:`, `order:` …) | Partial | booru_query.dart:24-78, 140-161; booru_search_landing.dart:149-218; booru_search_screen.dart:112-120, 242-282; booru_client.dart:256-281 | Engine-aware parts: the `rating:` spelling per host (letters, Gelbooru words, no "sensitive" on Moebooru/e621), `order:score` vs Gelbooru `sort:score`, and `~` hidden on Gelbooru. Rating chips only go up to the cap. **Missing:** a per-engine metatag catalogue or metatag autocomplete (there is one shared name list, used only for parsing and display). There is no `score:` helper. Other metatags have to be typed or entered with "Edit as text". |
| 2c. Search history | Present | booru_search_store.dart:171-183; booru_search_screen.dart:41-45; lib/plugins/plugin_search_history.dart:6, 23-78; booru_query.dart:107-111; booru_search_landing.dart:260-307; lib/constants.dart:469 | Every executed search is kept newest-first in `plugin.booru.search_history`, one entry per tag set (`a b` = `b a`), capped at 20. Tap to re-run; ✕ or swipe removes one; there is a clear-all. |
| 2d. Saved searches | Present | booru_search_screen.dart:128-140, 215-240; booru_store.dart:14-73; booru_search_landing.dart:62-63, 220-258; lib/database/repository.dart:29 | A star in the search bar saves the normalised tag set to `booru_subscription`. Saved searches are listed above history, run with one tap and feed the Following tab. |
| 2e. Favourite tags | Partial | booru_tag_sheet.dart:58-59, 105-110; booru_store.dart:14-16; booru_search_landing.dart:242-249; booru_search_screen.dart:83-87; booru_search_store.dart:159-163 | "Follow tag" puts the tag into the same list as saved searches. **Missing:** a separate favourite-tag list for building queries. Tapping a saved chip replaces the current query (`run`) instead of adding the tag. |
| 3a. Global blacklist | Present | lib/constants.dart:470; booru_store.dart:75-109; booru_settings.dart:122-126, 317-364; booru_tag_sheet.dart:60-61, 111-116; booru_client.dart:74-75, 100-127, 155-159, 229-238; test/booru_details_test.dart:437 | Stored as JSON in `plugin.booru.muted_tags`. It is edited in settings or with a tag's "Hide posts with this tag", and applies to every fetched page: Latest, Popular, Search, Following, related strips and the home mix. Filtering happens only at fetch time; lists already on screen are not re-filtered after a mute (booru_store.dart:111-186). |
| 3b. Per-profile blacklist | Missing | lib/constants.dart:470; booru_client.dart:74-75; booru_engines.dart:38-50 | Searched booru/ for host-keyed mute storage: only the single global pref exists, and sites have no blacklist field. |
| 3c. Blacklist semantics vs Danbooru help:blacklists | Partial | booru_parse.dart:356-404; booru_query.dart:70-77, 100-105; test/booru_details_test.dart:182-217; lib/l10n/intl_en.arb:1596; docs/specs/booru-plugin.md:42 | Supported: AND within an entry, `-tag` negation, a `~a ~b` OR group (ANDed with the rest), and `rating:x` in the host's own letters. Matching is case-insensitive. **Missing:** `score:`, and any metatag other than `rating:`, is ignored, so an entry containing one never hides anything (booru_parse.dart:379-382; the test at :208-211 asserts this). |
| 3d. Hidden-post count shown | Missing | booru_client.dart:110-127; booru_models.dart:225-235 | Searched booru/ and its l10n keys for hidden, filtered, reveal and muted-count: none in the UI. Muted posts are dropped without counting, and `BooruPostPage` carries only posts, page and hasMore. |
| 3e. Tap-to-reveal | Missing | booru_client.dart:117-123; booru_grid.dart:123-124, 137-138, 205-206; booru_display.dart:46-47; lib/l10n/intl_en.arb:1474-1476 | Blacklisted posts never reach the grid, so there is nothing to reveal. The separate opt-in blur for q/e thumbnails is lifted only by opening the post ("Opening the post shows it unblurred"). A tile tap always opens the viewer. |
| 4. Rating filter with explicit-content opt-in | Partial | lib/main.dart:666, 673, 682; booru_plugin.dart:155; booru_settings.dart:61-73, 100, 277-300; booru_engines.dart:203-211; booru_client.dart:64-72, 121, 256-281; booru_parse.dart:349-354; booru_search_landing.dart:174-177 | **Default:** plugin disabled, cap General (`g`), blur off. The cap is enforced on the server with appended rating tags (since `6803168b` also for the Gelbooru family: `rating:general` on Gelbooru, `rating:safe` on its forks, `-rating:explicit` for Sensitive and Questionable; not checked live) and always on the client. **Missing:** a real opt-in step. The Max rating sheet offers Explicit with no confirmation. Picking the Rule34, Gelbooru, Xbooru or e621 preset (or adding a custom host matching those names) silently sets the cap to Explicit. Posts with no readable rating pass any cap. |
| 5a. Post detail: tags grouped by category | Present | booru_tag_list.dart:22-48, 78-199; booru_client.dart:162-185; booru_endpoints.dart:75-96; lib/plugins/plugin_tag_chip.dart:8-21 | Groups are Artist, Copyright, Character, Species, General and Meta, as kind-tinted chips with compact counts. Sort A–Z or by use (remembered); followed and hidden tags are marked. Moebooru gives kinds but no counts. If the lookup fails, the kinds the post carries are used. |
| 5b. Tap a tag to search | Partial | booru_tag_list.dart:196; booru_post_screen.dart:128-129; booru_tag_sheet.dart:25-53, 100 | Tapping a tag opens an action sheet whose first entry is Search. **Missing:** one-tap search; it takes a second tap. |
| 5c. Long-press add / exclude / blacklist / copy | Partial | booru_tag_list.dart:185-197; lib/plugins/plugin_tag_chip.dart:59-61, 116-124; booru_tag_sheet.dart:54-65, 101-118; booru_grid.dart:96-104; booru_search_results.dart:86-96 | All four actions exist in the tap sheet. Blacklist is "Hide posts with this tag"; add and exclude appear only when the post was opened from a search. **Missing:** a long-press gesture. Post tag chips pass no `onLongPress`, although `PluginTagChip` supports one. Only the related-tag chips in search results long-press to exclude. |
| 5d. Source link | Present | booru_post_screen.dart:84-90; booru_post_actions.dart:34-38, 75, 98; lib/plugins/plugin_links.dart:52-58 | Shown as a Source row and an "Open source" menu item. It always opens in an external app (`launchUrl(..., externalApplication)`), even for a Pixiv or X source that `openLink` could open inside XTA. |
| 5e. Artist / character / copyright pages | Partial | booru_tag_sheet.dart:62-63, 117, 133-201; booru_endpoints.dart:98, 103-113; booru_post_screen.dart:117-123; booru_tag_list.dart:59-74; booru_text.dart:1-2, 18-33; docs/specs/booru-plugin.md:70 | Any tag's wiki opens in a sheet (not on Gelbooru) as flattened plain text, with links reduced to their labels. There is a "More from {artist}" strip with See all. **Missing:** artist pages (aliases, URLs) and character/copyright landing pages. The spec lists artist pages as "Not yet". |
| 5f. Pools / series reader | Missing | booru_query.dart:39; booru_related.dart:14-15; booru_post_screen.dart:91-92; docs/specs/booru-plugin.md:70 | Searched booru/ for pool, pools.json and /pools: only `pool` as a recognised metatag name. A parent/child "family" strip exists, but there is no pool list or ordered reader (spec: not yet). |
| 5g. Related tags | Partial | booru_query.dart:128-138; booru_search_results.dart:36-39, 56-105 | Shows the up to 12 tags most shared by the loaded results (count > 1) as refine chips: tap adds, long-press excludes. **Missing:** related-tag data from the host, and any related-tags block on the post detail screen. |
| 5h. Comments | Present | booru_comments.dart:14-72; booru_endpoints.dart:100-101, 115-131; booru_detail_parse.dart:46-73; booru_post_screen.dart:93 | Read-only, loaded when the section is expanded, with retry. Deleted and hidden comments are skipped. Available on Danbooru, e621 and Moebooru; not on Gelbooru (XML only). |
| 5i. Notes / translation overlay | Missing | docs/specs/booru-plugin.md:70 | Searched booru/ for note, translat and notes endpoints: nothing. The spec lists "Translation notes over the picture" as Not yet. |
| 6a. Viewer: pinch + double-tap zoom | Present | booru_post_media.dart:31-37, 51-67; lib/plugins/plugin_post_media.dart:621-650, 752-775; lib/tweet/_photo.dart:107-137; lib/ui/double_tap_zoom.dart:14-35 | Tapping the picture opens the shared full-screen viewer: gesture mode up to 4×, with an animated 1×↔4× double-tap. The inline picture on the post screen does not zoom. |
| 6b. Swipe between posts | Present | booru_post_pager.dart:41-56; booru_pager_store.dart:16-40; booru_grid.dart:96-104; test/booru_search_screen_test.dart:430-438 | A horizontal `PageView` over the list the post came from. It loads more of the feed within 4 posts of the end. The full-screen viewer gets only one image, so swiping there does not change post. |
| 6c. Swipe-down dismiss | Present | lib/plugins/plugin_post_media.dart:768-774; lib/tweet/_photo.dart:112-121 | Vertical slide-out in the full-screen viewer (`pullToClose: true`). The post pager itself is an ordinary route. |
| 6d. Video / animated playback where a playable variant exists | Present | booru_models.dart:164-174; booru_post_media.dart:22-26, 44-55; lib/tweet/video_controller_pool.dart:114-133; lib/tweet/_video.dart:187-189, 631-632; test/booru_viewer_test.dart:437-457 | The first mp4, webm or mkv among the file and sample plays inline in the app's pooled player, looping. Danbooru zip animations sampled as webm play too. The player controls offer native fullscreen. A post marked as video with no playable file opens the original externally on tap. GIF is not in the video set and goes through the image path. **Since `20d4f4a8`:** with "load media only on tap" on, the video waits behind the shared tap-to-show placeholder. **Since `b503f6a4`:** a GIF is shown from its original, so it animates in the post view (its sample is a still JPEG). |
| 6e. Sound toggle | Present | lib/tweet/_video.dart:136-147, 641-651, 958-990; lib/tweet/video_mute_badge.dart:19-64; booru_post_media.dart:44-49 | An always-visible mute badge (Booru leaves controls enabled). Mute state is app-wide. |
| 7a. Favourites / votes synced with the site when logged in | Missing | booru_client.dart:329-340; booru_post_facts.dart:21-30; docs/specs/booru-plugin.md:14, 75 | Searched booru/ for `.post(`, `.put(`, `.delete(`, favorite and vote: the client only sends GETs, and score, votes and favourites are display-only. This is by design. Spec line 75 hard rule: "No compose / favorite / upload / comment write-backs to any booru". Line 14: "Stay read-oriented: no favorites, uploads, comments, or account write-back". |
| 7b. Local favourites | Present | booru_post_actions.dart:20-27, 66, 103-128; lib/plugins/plugin_post_actions.dart:21, 36-42; booru_post_card.dart:26; booru_grid.dart:123-124; docs/specs/booru-plugin.md:69 | "Save on this device" (viewer app bar, or long-press on a mixed-feed card) stores a link post (host, tag line, one image) in the app-wide Saved list. Not offered on grid tiles. There is no Booru grid of saved posts (spec: not yet). |
| 8a. Single download | Present | booru_post_actions.dart:29-32, 60-65; lib/plugins/plugin_post_media.dart:588-619, 727-741; lib/utils/downloads.dart:70-99 | Downloads the original file, videos included, through the shared Download Center queue. Available from the viewer app bar and the full-screen viewer. **Since `006f364f`:** an animation whose original is a zip of frames downloads as the webm it plays. |
| 8b. Bulk download by query / selection / pool | Missing | lib/downloads/download_store.dart:128-146; booru_grid.dart:123-124; docs/specs/booru-plugin.md:69 | Searched booru/ for enqueueBatch, bulk, downloadAll, selection, multiSelect and pool: none, apart from the `pool` metatag name. `enqueueBatch` exists but Booru never calls it, and tiles have no selection mode. Spec: "bulk download of a search" not yet. |
| 8c. Filename templates (`{id}` `{md5}` `{artist}` `{character}` `{ext}`) | Missing | booru_post_actions.dart:64; lib/plugins/plugin_post_media.dart:581-586; lib/plugins/pixiv/pixiv_download_naming.dart:9-33 | Searched booru/, lib/downloads/ and lib/utils/downloads.dart for template, `{id}` and `{md5}`: none. Booru passes no `fileName`, so files are named `booru-<basename of the file URL>`. Pixiv has its own template engine, which Booru does not use. **Since `6ec2f4bd`:** that base name is decoded, and a long one keeps its start (site and post id) and extension within Android's byte limit. Still no templates. |
| 8d. MD5 dedupe | Missing | booru_parse.dart:108, 216; booru_models.dart:99, 126; android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt:244, 356 | MD5 is parsed, but nothing reads `post.md5` (searched lib/ for `.md5`). The download path never checks for an existing file, and SAF/MediaStore rename on a name clash, so downloading again makes a second copy. |
| 8e. Resumable background work with progress notification | Partial | lib/downloads/download_transfer.dart:117-146, 192-287; lib/downloads/download_store.dart:16-18, 45, 150-239; lib/downloads/download_entry.dart:45-46, 116; lib/downloads/downloads_screen.dart:113-133; android/app/src/main/AndroidManifest.xml:160-170 | Present: a queue with a concurrency limit, cancel and retry, and progress bars in the Download Center. A staged `.part` resumes with Range/If-Range only when the host sent a strong ETag. Interrupted jobs come back as retryable after a restart. **Missing:** transfers run in-process from `DownloadStore`, with no WorkManager or download foreground service (the only declared foreground service is audio playback). There is also no progress notification. Searched lib/downloads, lib/utils/download*.dart, the Android sources, the manifest and pubspec for notification, WorkManager and foreground: none. |
| 8f. Saved via MediaStore / SAF to a user-picked folder | Present | lib/downloads/download_destination.dart:12-36; lib/downloads/download_transfer.dart:289-318; lib/utils/download_directory.dart:17-24, 56-95, 133-150; android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt:24, 43-62, 192-213, 267-372 | Three destinations: a picked SAF folder (`ACTION_OPEN_DOCUMENT_TREE` with a persistable grant, then `createDocument`); MediaStore `Pictures/XTA`, `Movies/XTA` or `Download/XTA` on Android 10+; or the save dialog. Booru passes no `subfolder`, so there are no per-site or per-tag folders. |
| 9a. History of viewed posts | Missing | booru_plugin.dart:86; booru_search_screen.dart:41-45 | Searched booru/ for history, viewed and recent: only search history. The plugin owns one table (`booru_subscription`) and records nothing about opened posts. |
| 9b. Backup/restore of settings, profiles, blacklists, favourites as JSON | Present | booru_plugin.dart:138-146; lib/settings/backup_category.dart:19; lib/settings/backup_data.dart:28, 103-107, 113, 133-134; lib/settings/backup_rows.dart:139-145; lib/settings/settings_export_screen.dart:155-173; lib/settings/export_preferences.dart:6-23; lib/plugins/subscription_source.dart:29-30; lib/settings/_data.dart:86-92, 127-130, 152-156; lib/settings/import_preview.dart:144; lib/constants.dart:866-872; lib/utils/pref_lists.dart:54-57 | Follows and saved searches are exported as `booruSubscriptions` (category `booruTags`). Blacklist, custom sites, history, rating and display settings sit in `settings`, but only when Settings is ticked, because Booru declares no `subscriptionPreferenceKey`. Local favourites go in `tweets`. The API key / password hash is stripped on export and import, so it must be re-entered after a restore. `plugin.booru.login` is not stripped. |
| 10a. Offline: graceful errors | Present | booru_client.dart:17-34, 162-185, 213-217, 229-238, 329-377; booru_errors.dart:4-16; booru_screen.dart:241-246; booru_search_results.dart:26-31; booru_store.dart:170-171; booru_related.dart:53-54 | Network errors, 401/403, 404, 429, other statuses and bad JSON each map to a localised message. Secondary loads fail quietly: tag kinds fall back, suggestions come back empty, a failing followed tag is skipped, load-more keeps the list, related strips hide. Caveat: the generic error page also prints the raw exception, which can include the API key (see 1c). **Since `dba4687f`:** the raw exception no longer contains credentials. **Since `add22b17`:** a 403 without credentials and a 503 have their own messages. **Since `afd18acb`:** a failed next page says so under the grid and is not asked for again until Retry; the end of a feed is marked. **Since `e5315b74`:** Following shows the error page when every followed query fails, and a notice with Retry when some do. **Since `2926ce05` and `bd92c6c7`:** a guest refused in plain text (Rule34) is told the host needs a login and API key; a page past the host's limit (410, gelbooru.com's "Too deep") ends the feed; Danbooru's tag limit (422) has its own message. Not checked live. |
| 10b. Retry | Present | booru_screen.dart:241-259; booru_search_results.dart:26-31; booru_comments.dart:54-57; booru_tag_sheet.dart:171, 189-200; booru_grid.dart:87-88; booru_settings.dart:128-156, 365-375 | Retry on full-page errors, empty feeds, comments and wiki. Pull-to-refresh on grids. "Test connection" in settings. **Since `afd18acb` and `e5315b74`:** Retry under a grid whose next page failed, and on Following's notice when some followed tags failed. **Since `94fb4cdb`:** a feed whose first pages the filters emptied offers Load more instead of a dead end. |
| 10c. Last page kept in cache | Partial | booru_screen.dart:54-71, 122-126, 229-246; lib/plugins/plugin_session.dart:5-7, 30-50; booru_client.dart:329-340; booru_image.dart:33-59 | Feeds stay in memory for one Home session and are reused without refetching when the tab comes back. The old grid stays visible during a reload. Images use ExtendedImage's `cache: true`. **Missing:** no on-disk copy of post lists (the HTTP layer has no cache, so lists are gone after a restart). A failed refresh replaces the grid with the error page instead of keeping the last page visible. |
| 11a. X integration: "Find source" via SauceNAO/IQDB, opening the matching booru post | Missing | lib/plugins/pixiv/pixiv_saucenao.dart:26-37, 53-68; lib/plugins/pixiv/pixiv_saucenao_sheet.dart:23, 26-31, 43-48; lib/plugins/pixiv/pixiv_search_screen.dart:218; test/pixiv_saucenao_test.dart:32-33, 56-86 | Searched lib/ and test/ (excluding generated code) for saucenao, iqdb, reverse image, find_source, ascii2d and tineye. Only Pixiv's SauceNAO exists: it takes an image picked from the device, opens from Pixiv search, and keeps only Pixiv ids. Its test asserts Danbooru matches are dropped (expects `[555, 777]` at :84). There is no IQDB, no entry point on X media, and nothing opens a booru post. |
| 11b. Booru links in X posts render natively | Missing | lib/plugins/plugin_url.dart:5-137; lib/plugins/plugin_links.dart:37-58, 105-127 | Searched plugin_links.dart, plugin_url.dart, lib/links/, lib/utils/urls.dart and AndroidManifest for booru, donmai, gelbooru, yande, konachan, e621, rule34 and safebooru: none. `parsePluginLink` has no booru parser, so a booru URL falls through to the browser. Outside the plugin, only main.dart and plugin_registry.dart import Booru code. |
| 12. "Copy tags as prompt" | Partial | booru_post_actions.dart:73, 96; booru_models.dart:182; booru_tag_style.dart:21-22; booru_tag_sheet.dart:64-65 | "Copy tags" copies the raw space-joined tag string; a single tag can be copied from its sheet. **Missing:** underscore→space (done only for display), parenthesis escaping, and configurable category order or exclusions. Searched booru/ for "prompt": none. |
| 13a. Tag / artist subscriptions | Present | booru_tag_sheet.dart:58-59, 105-110; booru_search_screen.dart:128-140; booru_store.dart:14-73; booru_plugin.dart:26, 85-136; lib/database/entities.dart:848-865; docs/specs/booru-plugin.md:68 | Follow any tag (artist tags included) or a multi-tag search. Rows live in `booru_subscription` and can be group members. Follows have no host column, so they apply to whichever site is active (spec: per-host follows not yet). |
| 13b. Merged feed of followed tags | Present | booru_client.dart:220-254; booru_screen.dart:64-71, 274-321; booru_interleaved.dart:10-35; booru_post_card.dart:87-95; lib/l10n/intl_en.arb:1572 | The Following tab merges page 1 of every followed query (10 posts each), de-duplicated by host:id, newest first. A failing query is skipped. **Since `e5315b74`:** the Following tab says when some failed, and shows the error page when all did; the Home mix still skips them silently. Posts can optionally be mixed into home Following / For you (8 per tag) as provenance cards. Following never pages past that first merged set. |
| 13c. "New posts since last visit" | Missing | booru_store.dart:177-185; booru_screen.dart:64-71 | Searched booru/ for lastVisit, last_seen, unread, since and seen: only in-session de-duplication. No visit time is stored and new posts are not marked; Following always shows the latest page. |
| 14a. Accessibility: semantic labels on thumbnails | Partial | booru_grid.dart:119-184; booru_image.dart:33-59; booru_related.dart:99-113; booru_display.dart:14, 29; lib/main.dart:681; booru_post_media.dart:31-35; booru_tag_list.dart:195; booru_post_facts.dart:64-67 | Grid tiles and related-strip thumbnails have no `Semantics` or `semanticLabel`. A tile is announced only through its visible text: the optional details (host, 4 tags, score; on by default) and the Video / Q / E badges. With details off, a general-rated still has no label. Labels do exist elsewhere: the post image ("Open full screen"), tag chips with full counts, and the post facts. |
| 14b. 48 dp touch targets | Present | lib/plugins/plugin_tag_chip.dart:42-44, 110-113; booru_search_field.dart:77; booru_search_landing.dart:104-105, 213, 247, 318-319; booru_search_results.dart:77-80, 94; booru_screen.dart:304; booru_popular_tab.dart:44; lib/tweet/video_mute_badge.dart:17, 66 | Chips use `MaterialTapTargetSize.padded`; suggestion rows and section headers have a 48 minimum; the related-tags hint is 48×48; the mute badge sits in a full touch target. The settings chips (booru_settings.dart:186-203, 353-359) don't set this and rely on the theme default. |
| 14c. Text scaling | Present | lib/plugins/plugin_gallery_layout.dart:4-7; booru_display.dart:39-40; booru_grid.dart:65, 157-182; lib/plugins/plugin_tag_chip.dart:101-105 | The automatic column count widens tiles at large text sizes. Tile text is line-limited with ellipsis, and tag chips wrap to 2 lines. A manually chosen column count (2–5) ignores the text scale. |

**Tally:** 22 Present · 13 Partial · 15 Missing (50 rows). Rows 1b and 1c contain security findings: credentials are sent to whichever host is selected, and the API key can be shown on error pages.

## 5. Drift from upstream (design comparison)

This compares `lib/plugins/booru/` (39 files, 5,998 lines) and `docs/specs/booru-plugin.md` with Boorusama at commit 1716a684.

### 5.1 Provenance check

**Verdict:** no sign of copied or translated Boorusama code. The overlap is limited to Flutter/Dart framework names, the sites' public API vocabulary and booru query syntax, and each shared idea is written differently. This matches the spec's claim (`docs/specs/booru-plugin.md:3-6`).

What was checked:

- **Identifiers.**
  - XTA's booru files contain 1,640 distinct identifiers of 6 or more characters. 833 of them also appear somewhere in Boorusama's 3,047 Dart files (about 275k lines).
  - Every shared one is a framework or widget name (`SliverMasonryGrid`, `ScrollCacheExtent`), an English word, or a public API field (`tag_string_artist`, `large_file_url`, `password_hash`, `search[name_comma]`).
  - No domain-level class or function shares both name and role.
  - One name is shared with a different meaning. XTA's `BooruEngine` is an enum of API families (`booru_engines.dart:6-36`). Boorusama's `BooruEngine` is a class that bundles a booru with its builder and repository (`BS:lib/core/boorus/engine/src/booru_engine.dart:6-16`).
- **Comments.** None of the 226 XTA comment lines appears verbatim in Boorusama.
- **String literals.** 109 of XTA's 290 literals (6+ characters) also occur in Boorusama. They are only endpoint paths (`/explore/posts/popular.json`), parameter and JSON keys, site hosts and `package:` import URIs.
- **Code shingles.** Sequences of 16 tokens were compared, keeping identifiers and stripping imports. 168 sequences are shared:
  - all are generic Flutter boilerplate (`Padding`/`EdgeInsets` trees, `initState`/`dispose` bodies), URI rebuilding, or a list-equality loop;
  - none is in parsing, query or filter logic;
  - XTA's booru tests (`test/booru_*.dart`) share no fixtures; only an endpoint path, a host and import URIs coincide.
- **Distinctive constants differ.**
  - Non-artist tags: XTA has a list of nine tags that are not real artists (`booru_tag_list.dart:60-74`). Boorusama has a two-entry list that matches by substring (`BS:lib/core/posts/details_parts/src/information_section.dart:269-282`). Only `banned_artist`, a real Danbooru tag, appears in both.
  - Metatags: XTA has 22 metatag names in alphabetical order (`booru_query.dart:26-49`). Boorusama has 64 metatags plus a separate free-metatag list, in Danbooru's order (`BS:assets/tagdef.json`).
  - Gelbooru dates: XTA parses them with a regex and applies the timezone offset by hand (`booru_parse.dart:284-317`). Boorusama splits the string and rebuilds an ISO date (`BS:packages/foundation/lib/src/datetime/time_utils.dart:61-84`). Its `_months` map belongs to a Realbooru HTML scraper (`BS:packages/booru_clients/lib/src/gelbooru_v2/parsers/rb_parsers.dart:204-221`).
  - Ratings: XTA has four levels from general to explicit, and parsing depends on the engine (`booru_models.dart:11-77`). Boorusama has five levels including `unknown`, ordered explicit to general, parsed the same way for every engine (`BS:lib/core/posts/rating/src/rating.dart:1-41`).
- **Closest parallels.** These are dictated by the sites' syntax or APIs, and each is expressed differently:
  - the `-`/`~` operator enum (`booru_query.dart:8-22`; `BS:lib/core/search/queries/filter_operator.dart:1-24`);
  - blacklist matching as "all plain terms, plus at least one `~` term" (`booru_parse.dart:356-390`; `BS:lib/core/posts/filter/src/check_tag.dart`);
  - the `parent:` query for related posts (`booru_related.dart:15`; `BS:lib/core/posts/post/src/types/post.dart:78`);
  - the Gelbooru 0.2 `/images/{directory}/{image}` fallback (`booru_parse.dart:319-330`; `BS:packages/booru_clients/lib/src/gelbooru_v2/post_v2_dto.dart:50-56`);
  - Rule34's `api.` host (`booru_engines.dart:149-159`; `BS:packages/booru_clients/boorus.yaml:115-119`).
- **History.** Commits 95e21fbc ("Rework booru tag search after Boorusama") and 10659c74 ("Bring the Booru plugin closer to Boorusama") name Boorusama as the feature model. Their messages describe behaviour, not ported code. Two doc comments credit the inspiration (`booru_plugin.dart:23`, `booru_screen.dart:30`).

**Limit:** a similarity scan cannot prove that rewritten logic was authored independently. It found nothing that suggests copying.

### 5.2 Engines and sites

Boorusama has 16 engine modules (`BS:lib/boorus/*`) and 17 API clients (`BS:packages/booru_clients/lib/src/*`; `sankaku_idol` is a second Sankaku client). Its sites are listed in `BS:packages/booru_clients/boorus.yaml`. XTA has 4 engines (`booru_engines.dart:6-10`).

| Boorusama engine | Sites listed in `BS:packages/booru_clients/boorus.yaml` | In XTA |
|---|---|---|
| danbooru | danbooru, donmai.moe, safebooru.donmai.us, aibooru, testbooru (:1-19) | Yes. Presets are only Danbooru and AIBooru (`booru_engines.dart:57-68`). |
| moebooru | konachan.com, konachan.net, yande.re, sakugabooru (:179-203) | Yes. No konachan.net preset. |
| e621 | e621, e926 (:205-212) | Yes (`booru_engines.dart:117-128`). |
| gelbooru_v2 (Gelbooru 0.2) | rule34.xxx, where auth is marked **required** (:111-114); hypnohub (:151); realbooru, which needs HTML scraping (:156-169); bbooru, which needs a cookie (:171-173); xbooru, tbib, safebooru | Partly. XTA speaks only the JSON API, so realbooru and bbooru are out of reach. The Rule34 preset doesn't say a key is needed (`booru_engines.dart:99-104`; `lib/l10n/intl_en.arb:1470`). XTA guesses hypnohub is Moebooru (`booru_engines.dart:179-183`), but Boorusama lists it as Gelbooru 0.2; worth re-checking. |
| gelbooru (Gelbooru 0.2.5) | gelbooru.com (:21-31) | Not as its own engine. gelbooru.com goes through the generic 0.2 JSON API (`booru_engines.dart:93-98`), so it gets no `autocomplete2`, notes or XML comments. |
| gelbooru_v1 (Gelbooru 0.1) | illusioncards.booru.org (:33-41) | Missing. Not in the spec's "later" list either. |
| sankaku (+ idol client) | chan, beta, sankaku.app, idol (:224-239) | Missing. XTA's host guess returns nothing for it (`booru_engines.dart:177`). |
| philomena | derpibooru, ponybooru | Missing. |
| shimmie2 | rule34.paheal.net | Missing (`booru_engines.dart:177`). |
| szurubooru, hydrus | self-hosted | Missing. |
| zerochan | zerochan.net, kpop.asiachan.com | Missing. |
| nozomi, anime-pictures, hybooru, eshuushuu | one site each | Missing. |

XTA lacks 12 of Boorusama's 16 engines. The spec's "later" list (`docs/specs/booru-plugin.md:65-67`) names 10 of them; it leaves out Gelbooru 0.1 and a dedicated Gelbooru 0.2.5 engine. For a "best client" bar:
- Sankaku, Zerochan, Philomena and paheal matter most, because their catalogues are large.
- Szurubooru and Hydrus matter to people who host their own booru.

One idea answers the spec's concern that tags with spaces break the chip field. Boorusama keeps `_` internally for Philomena and Zerochan and converts to spaces only when it calls the site (`BS:packages/booru_clients/lib/src/philomena/philomena_client.dart:42`; `.../zerochan/zerochan_client.dart:54-56`).

### 5.3 Capability by capability

"Matters" rates each gap for a read-only client aiming to be the best booru reader. Writing to the sites is out of scope under XTA's rules.

- **Profiles / configs.**
  - *Boorusama:* any number of saved profiles. Each `BooruConfig` has its own site URL, login, API key or password hash, rating filter (including a per-rating one), blacklist mode, tags always added to searches, download naming and folder, image and video quality, gestures, layout, theme, proxy and network settings (`BS:lib/core/configs/config/src/types/booru_config.dart:200-229`; `BS:lib/core/configs/manage`).
  - *XTA:* one active site, picked from presets or custom chips (`booru_settings.dart:61-120,178-206`). There is a single global login and key (`booru_client.dart:60-62`), and the rating cap, blacklist and follows are also global. The follows table has no host column (`lib/database/repository.dart:747-756`).
  - *Bug:* switching preset keeps the old login and key (`booru_settings.dart:61-73`), and every request appends them (`booru_endpoints.dart:27,33-36`). A key typed for one site is therefore sent to whichever site is chosen next. Boorusama scopes credentials to each profile's client and explicitly keeps them off substitute media hosts (`BS:lib/core/http/client/src/providers/providers.dart:67-68`).
  - **Matters: High.** Profiles are already planned (`docs/specs/booru-plugin.md:68`), and the credential bleed is a privacy bug either way.
- **Auth / login.**
  - *Boorusama:*
    - a button that checks the API key works (`BS:lib/core/configs/auth/src/widgets/verify_api_key_button.dart`);
    - it hashes the Moebooru password itself, using each site's salt (`BS:lib/boorus/moebooru/configs/types.dart:8-22`; `BS:packages/booru_clients/boorus.yaml:186-201`);
    - Gelbooru login cookies (`BS:packages/booru_clients/lib/src/gelbooru/gelbooru_session.dart`);
    - cookie capture through a WebView (`BS:lib/core/configs/auth/src/pages/cookie_access_webview_page.dart`);
    - automatic token refresh (`BS:lib/core/http/client/src/interceptors/auto_refresh_auth_interceptor.dart`);
    - e621 credentials go in an HTTP Basic header (`BS:packages/booru_clients/lib/src/e621/e621_client.dart:10-33`); Danbooru's still go in the URL (`danbooru_auth_interceptor.dart:14-22`).
  - *XTA:* pasted keys only. Moebooru users must compute and paste the SHA1 hash themselves (`lib/l10n/intl_en.arb:1618`), and credentials always go in the URL (`booru_endpoints.dart:26-31`).
  - *Leak:* error messages include the full request URL (`booru_client.dart:344-377`). The generic error page prints the raw error (`lib/ui/errors.dart:611`), and the Booru feed and search use that page (`booru_screen.dart:241-246`; `booru_search_results.dart:26-31`). Any failed response therefore shows `login=` and `api_key=` on screen.
  - **Matters:** **High** for the on-screen leak, which is cheap to fix. **Medium** for the login flows; the WebView-based ones can't run on XTA's desktop build (`CLAUDE.md:15-16`).
- **Favourites / votes written back to the site.**
  - *Boorusama:*
    - Danbooru favourites, favourite groups and votes (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client_favorites.dart:15,32`; `danbooru_client_posts.dart:86,110`; `danbooru_client_favorite_groups.dart`);
    - e621 favourites (`e621_client.dart:83,100`);
    - Moebooru votes (`moebooru_client.dart:185-200`);
    - Gelbooru and Szurubooru favourites;
    - local bookmarks with tag search, sorting and shuffle (`BS:lib/core/bookmarks/src/pages/bookmark_page.dart`).
  - *XTA:* none, by design (`docs/specs/booru-plugin.md:14,75`). "Save on this device" puts the post in the app-wide Saved list (`booru_post_actions.dart:20-27,103-128`).
  - **Matters: N/A by rule.** The read-only counterpart, a booru-style grid of saved posts, is the real gap (`docs/specs/booru-plugin.md:69`).
- **Blacklist.**
  - *Boorusama:*
    - the same entry syntax (all terms must match, `-` excludes, `~` means any of), plus `rating:`, `score:<`, `downvotes:>`, `uploader:`, `uploaderid:`, `source:` and `status:` (`BS:lib/core/posts/filter/src/tag_expression.dart:14-24`, `.../src/types/`);
    - lists can be global or per profile, merged or replacing each other (`BS:lib/core/configs/search/src/types/blacklist_combination_mode.dart`);
    - it syncs the Danbooru account blacklist (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client_users.dart:53-59`) and ships a default list (`BS:assets/tagdef.json`);
    - the grid shows how many posts each tag hid and lets you show them temporarily (`BS:lib/core/posts/listing/src/widgets/blacklist_controls.dart`);
    - it sends the rating cap to the server even on Gelbooru 0.2 (`BS:lib/boorus/gelbooru_v2/tags/query_composer.dart:10-30`).
  - *XTA:* the same syntax, but `rating:` is the only metatag it understands; an entry using any other metatag never hides anything (`booru_parse.dart:375-384`). There is one global list (`booru_store.dart:77-109`), applied silently after each fetch (`booru_client.dart:112-127`). On Gelbooru-family sites XTA doesn't send the rating cap to the server (`booru_client.dart:260-263`), though it knows the right tokens (`booru_query.dart:143-151`). When filtering empties a page, it fetches up to 4 more pages on refresh and 3 when scrolling (`booru_store.dart:129-143,154-169`).
  - **Matters: Medium.** `score:<` and uploader rules are widely used, and visible counts build trust.
- **Saved searches.**
  - *Boorusama:* Danbooru only, stored on the server, with labels and a feed (`search:<label>`) (`BS:lib/boorus/danbooru/saved_searches/saved_search/src/types/saved_search.dart:70`; `.../feed/src/saved_search_feed_page.dart`).
  - *XTA:* stored locally and works on every engine. Starring a search turns it into a followed entry that feeds the Following tab (`booru_search_screen.dart:128-140,215-240`; `booru_store.dart:57-72`).
  - **Matters: Low.** XTA's approach suits a read-only client better; labels would be a nice addition.
- **Favourite tags.**
  - *Boorusama:* local favourite tags with labels, sorting, filtering, import and export, shown on the search page (`BS:lib/core/tags/favorites`; `.../providers/favorite_tags_notifier.dart:189`; `BS:lib/core/search/search/src/widgets/favorite_tags_section.dart`).
  - *XTA:* "Follow" doubles as "favourite", and every followed tag feeds the Following tab (`booru_tag_sheet.dart:58-59`).
  - **Matters: Low–Medium.** A bookmark-only tag would avoid adding a request to every Following load.
- **Search history.**
  - *Boorusama:* SQLite rows with search count, timestamps, engine, site URL and query type, plus a full-history page (`BS:lib/core/search/histories/src/types/search_history.dart`; `.../data/repo_sqlite.dart:35`; `.../full_history_page.dart`).
  - *XTA:* a JSON preference capped at 20 entries (`lib/plugins/plugin_search_history.dart:6`), de-duplicated by tag set (`booru_query.dart:107-111`; `booru_search_screen.dart:41-45`) and shared across all sites.
  - **Matters: Low–Medium.** It needs per-site history once profiles exist.
- **Autocomplete.**
  - *Boorusama:* waits 350 ms after typing (`BS:lib/foundation/debounce_mixin.dart:13`). It remembers results for each query, hides adult suggestions in its safe-mode setting, and colours suggestions from a saved tag cache (`BS:lib/core/search/suggestions/suggestions_notifier.dart:50-89`). It uses each site's own autocomplete endpoint, which understands aliases:
    - Danbooru `/autocomplete.json`, whose results carry the original alias (`antecedent`) (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client.dart:113-133`);
    - e621 `/tags/autocomplete.json` (`e621_client.dart:228-262`);
    - Gelbooru `autocomplete2` (`gelbooru_client.dart:170-200`);
    - Gelbooru 0.2 `/autocomplete.php` (`BS:packages/booru_clients/boorus.yaml:77-80`);
    - Moebooru: it downloads the site's whole tag list and matches locally (`BS:lib/boorus/moebooru/autocompletes`, `.../tag_summary`).
  - *XTA:* waits 250 ms, needs at least 2 characters, drops out-of-date replies, and remembers nothing (`booru_search_store.dart:76,185-199`; `booru_query.dart:117-123`). It matches only on prefix, through `tags.json` with `name_matches=q*`, `tag.json`, or the Gelbooru API's `name_pattern` (`booru_client.dart:283-301`), so aliases are never found. Boorusama uses that same `tags.json` call only as its e621 fallback for 2-character input.
  - **Matters:** **High** for finding tags, because of aliases. **Medium** for request volume, because of caching.
- **Metatags.**
  - *Boorusama:*
    - 64 Danbooru metatags plus a list of "free" ones (`BS:assets/tagdef.json`);
    - a page listing metatags (`BS:lib/core/tags/metatag/src/pages/metatag_list_page.dart`) and a metatag section on the search page (`BS:lib/core/search/search/src/widgets/metatags_section.dart`);
    - per-engine syntax highlighting (`BS:lib/core/search/syntax`; `BS:lib/boorus/danbooru/syntax`);
    - user metatags (`BS:lib/boorus/danbooru/tags/user_metatags`).
  - *XTA:*
    - a fixed list of 22 metatag names, used for parsing and colouring (`booru_query.dart:26-49`);
    - quick chips for `-`, `~`, ratings and score order (`booru_search_landing.dart:149-218`);
    - rating and order tokens written correctly for each engine (`booru_query.dart:143-161`).
  - **Matters: Medium** (helps people discover what they can search for).
- **Pools.**
  - *Boorusama:* Danbooru and Szurubooru pools, with list and detail pages (`BS:lib/boorus/danbooru/pools`; `BS:lib/boorus/szurubooru/pools`; `BS:lib/core/posts/pools`).
  - *XTA:* none (`docs/specs/booru-plugin.md:70`); `pool` is only recognised as a metatag name (`booru_query.dart:39`).
  - **Matters: Medium** (comics and image sequences).
- **Notes / translation overlay.**
  - *Boorusama:* draws note boxes over the image, with several display modes (`BS:lib/core/notes/note/src/rendering/polygon_note_painter.dart`; `.../types/note_display_mode.dart`). It fetches notes for Danbooru, e621, Gelbooru, Gelbooru 0.2 (scraped from HTML) and Szurubooru (`BS:lib/boorus/{danbooru,e621,gelbooru,gelbooru_v2,szurubooru}/notes`), with a per-profile setting for fetching them automatically (`booru_config.dart:226`).
  - *XTA:* none (`docs/specs/booru-plugin.md:70`).
  - **Matters: High** for anyone reading translated art or comics.
- **Artist / character pages.**
  - *Boorusama:*
    - on Danbooru: an artist page with links and commentary, artist search, a character page, and tag pages with the wiki and a tag cloud (`BS:lib/boorus/danbooru/artists/{artist,urls,commentaries,search}`; `BS:lib/boorus/danbooru/tags/details/src/danbooru_character_page.dart`; `BS:lib/core/tags/details`);
    - artist pages for e621, Gelbooru, Gelbooru 0.2, Moebooru and Sankaku (`BS:lib/boorus/*/artists`);
    - artist, character and uploader strips on the post page (`BS:lib/core/posts/details_parts/src/{artist,character,uploader}_post_list.dart`).
  - *XTA:*
    - "More from {artist}" and parent/child strips (`booru_post_screen.dart:91-92,117-123`; `booru_related.dart:15-41`);
    - the wiki as plain text in a sheet (`booru_tag_sheet.dart:133-201`; `booru_text.dart:18-33`);
    - no wiki on Gelbooru (`booru_endpoints.dart:98`).
  - **Matters: Medium–High.** All of it is read-only.
- **Comments.**
  - *Boorusama:* comments on every engine, including Gelbooru (parsed from XML, `BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:399-400`) and Rule34 (scraped from HTML, `BS:packages/booru_clients/boorus.yaml:124-149`). It renders Danbooru's DText markup (`BS:packages/dtext`) and lets you post and vote (`danbooru_client_comments.dart`).
  - *XTA:* read-only and loaded on demand, for Danbooru, e621 and Moebooru only, shown as plain text (`booru_comments.dart:43-49,93`; `booru_endpoints.dart:101,115-131`).
  - **Matters: Low–Medium** (Gelbooru coverage, links and quotes).
- **Related tags.**
  - *Boorusama:* Danbooru's server-side related tags, grouped by kind (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client_tags.dart:37-56`; `BS:lib/boorus/danbooru/tags/related`), plus trending and AI tags (`BS:lib/boorus/danbooru/tags/{trending,ai}`).
  - *XTA:* the tags most shared by the posts already loaded, on every engine; tap adds a tag to the search, long-press excludes it (`booru_query.dart:128-138`; `booru_search_results.dart:36-39,56-104`).
  - **Matters: Low.** XTA's version works everywhere; server data would only improve Danbooru.
- **Downloads.**
  - *Boorusama:*
    - filename templates with tag, MD5 and size tokens (`BS:packages/filename_generator/lib/src/token.dart`; presets in `BS:lib/core/downloads/filename/types/builtin_format.dart:1-8`);
    - bulk download of a whole search, with quality choice, 100 posts per page, 5 at a time, skip-if-exists, blacklist and metadata files alongside (`BS:lib/core/bulk_downloads/src/types/download_options.dart:28-31,65-72`; `BS:lib/core/downloads/sidecar`);
    - saved download tasks and a dry run (`BS:lib/core/bulk_downloads/src/providers/dry_run.dart`);
    - background downloads with notifications (`BS:lib/core/downloads/background/notification.dart`; `BS:pubspec.yaml:61,84`);
    - its "MD5 dedupe" is really a filename check: skip-if-exists looks for the target file name (`BS:lib/core/bulk_downloads/src/data/filesystem_io.dart:27-30`), and the presets put `{md5}` in that name;
    - some bulk features need the paid tier (`BS:lib/core/bulk_downloads/src/providers/saved_task_lock_notifier.dart:23-36`).
  - *XTA:* downloads one post's original file through the app-wide download queue (`booru_post_actions.dart:60-65` → `lib/plugins/plugin_post_media.dart:590-606` → `lib/utils/downloads.dart:70-79`), which has a background option (`lib/downloads/download_store.dart:89-112`). Files are named `booru-<file name from the URL>` (`lib/plugins/plugin_post_media.dart:581-586`). There is no bulk download, no naming template and no skip-if-exists (`docs/specs/booru-plugin.md:69`).
  - **Matters: High.** It is a signature booru-client feature and is entirely read-side.
- **Image sizes in the grid and viewer.**
  - *Boorusama:*
    - 5 quality levels combined with 5 grid sizes, showing the small thumbnail while the bigger image loads (`BS:lib/core/images/image_quality.dart:7-23`; `BS:lib/core/posts/listing/src/types/grid_thumbnail_url_generator_default.dart`);
    - on Danbooru it picks the 180, 360 or 720 px version to fit the tile (`BS:lib/boorus/danbooru/posts/listing/src/grid_thumbnail_url.dart:38-69`; sizes read at `BS:packages/booru_clients/lib/src/danbooru/types/post_dto.dart:90-91,188-192`);
    - standard, masonry and classic layouts, and a page-by-page mode (`.../types/image_list_type.dart:7-10`; `.../page_mode.dart:7-9`);
    - viewer image and video quality set per profile (`booru_config.dart:214-215`).
  - *XTA:* loads the site's sample file (about 850 px per the spec, `docs/specs/booru-plugin.md:25`) or the ~150 px preview (`booru_models.dart:146-157`; `booru_display.dart:42-47`). The grid is masonry only (`booru_grid.dart:64-74`) and decodes images at the size they are drawn (`booru_image.dart:45-60`). Video tiles show the preview (`booru_models.dart:147-149`).
  - **Matters: Medium** (data use and how fast the grid feels).
- **Caching.**
  - *Boorusama:*
    - its own image and video caches, up to 100 MB per video, plus a memory layer (`BS:packages/cache_manager/lib/src/`; `video_cache_manager.dart:20`);
    - a screen showing cache size (`BS:lib/core/cache/cache_notifier.dart:151`);
    - a saved tag cache per site (`BS:lib/core/tags/local/src/data/repo_sqlite.dart:38`);
    - in-memory caches for comments, pools and e621 popular (`BS:lib/core/comments/src/types/comment.dart:68`; `BS:lib/boorus/danbooru/pools/pool/src/data/pool_repository_impl.dart:55`; `BS:lib/boorus/e621/popular/providers.dart:34`).
  - *XTA:* only the image library's disk cache, with a 15 s timeout and 1 retry (`booru_image.dart:4,34-41,51-58`). It caches no API data: tag kinds and counts are fetched again every time a post opens (`booru_post_screen.dart:59-60`; `booru_client.dart:164-185`).
  - **Matters: Medium.**
- **Rate limiting / retry.**
  - *Boorusama:*
    - each profile's HTTP client is capped at 10 requests per second (`BS:lib/core/http/client/src/providers/providers.dart:23-47,188-196`);
    - some sites get stricter caps plus a pause after a 429 that honours the `Retry-After` header, e.g. anime-pictures at 1 request per second (`BS:lib/boorus/anime-pictures/client_provider.dart:27-34`; `BS:lib/core/http/client/src/interceptors/sliding_window_rate_limit_interceptor.dart:33-35,60-75`);
    - image downloads retry up to 5 times with growing waits, also honouring `Retry-After` (`BS:packages/retriable/lib/src/fetch_strategy.dart:11-26,62-63`);
    - it also handles Cloudflare-style challenges (`BS:lib/core/ddos`), page-limit errors and duplicate image requests (`BS:lib/core/http/client/src/interceptors/`).
  - *XTA:* a 20 s timeout, no retry, and a 429 becomes an error straight away (`booru_client.dart:43,329-340,354-356`). The Following tab and the home mix send one request per followed entry, all at the same time (`booru_client.dart:222-254`; `booru_interleaved.dart:21-35`).
  - **Matters: High.** Firing every request at once is the likeliest way to get rate-limited or banned as the follow list grows.
- **User-Agent.**
  - *Boorusama:* sends `AppName/version` on every client (`providers.dart:155-160`), overrides it per site (Sankaku gets a browser User-Agent, `BS:packages/booru_clients/boorus.yaml:228-231`), and reuses the WebView's User-Agent on protected sites (`BS:lib/core/ddos/solver/src/user_agent_provider.dart:23-34`).
  - *XTA:* sends a descriptive User-Agent with the project URL on API calls (`booru_client.dart:44-45,334`), but no headers at all on image requests (`booru_image.dart:34-59`).
  - **Matters: Low.** XTA's API User-Agent is good etiquette; sending it on images too would be cheap.
- **Video / animated posts.**
  - *Boorusama:* several video players (media_kit, video_player, fvp, WebView; `BS:lib/core/videos/engines/src/engines/`), a size-limited video cache (`BS:lib/core/videos/cache`), speed and sound controls (`BS:lib/core/videos/player/src/providers/`), an e621 quality picker (`BS:lib/boorus/e621/videos/widgets.dart:33`), and GIFs that animate in the grid (`BS:lib/core/posts/listing/src/types/animated_posts_default_state.dart`).
  - *XTA:* the app's shared player on loop (`booru_post_media.dart:44-49`), using Danbooru's webm version of zip animations (`booru_models.dart:168-174`). Opening a video full screen sends it to another app (`booru_post_media.dart:51-55`), and the grid shows only still posters.
  - **Matters: Medium** (Sakugabooru and e621 users).
- **Backup / restore.**
  - *Boorusama:* backs up profiles, blacklist, favourite tags, bookmarks, history, downloads and settings, as zip files, automatically, or by transfer over the local network (`BS:lib/core/backups/{sources,zip,auto,servers}`).
  - *XTA:* followed entries are their own backup section (`booru_plugin.dart:138-146`). The blacklist, custom sites and history live in preferences, so they travel with the app's settings export, minus secrets (`lib/settings/export_preferences.dart:6-23`; `lib/utils/crash_reporter.dart:241-245`).
  - **Matters: Low.**
- **Following tags and artists.**
  - *Boorusama:* no local follow feed; only Danbooru's server-side saved-search feed.
  - *XTA:* ahead here. The Following tab merges the newest posts of every followed entry, on any engine (`booru_screen.dart:64-71`; `booru_client.dart:222-254`). Entries can also join XTA groups and the home mix (`booru_plugin.dart:85-136`; `booru_interleaved.dart:12-35`; `booru_post_card.dart:87-95`).
  - **Matters:** this is XTA's differentiator. It needs per-site follows and the throttling above to scale.
- **Reverse image search.**
  - *Boorusama:* nothing for readers. Danbooru's IQDB similarity search appears only when uploading (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client.dart:146-163`; `BS:lib/boorus/danbooru/posts/uploads/src/internal_widgets/similar_tab.dart:49`).
  - *XTA:* none in the Booru plugin. The Pixiv plugin's SauceNAO client could be generalised (`lib/plugins/pixiv/pixiv_saucenao.dart:133-167`).
  - **Matters: Low** for matching Boorusama; it could set XTA apart.
- **Explore / popular** (not in your list, but relevant).
  - *Boorusama:* adds Danbooru "hot" and "most viewed" (`BS:lib/boorus/danbooru/posts/explores/src/types/explore_category.dart`), popular searches (`BS:packages/booru_clients/lib/src/danbooru/danbooru_client_explores.dart`), and Moebooru's recent-popular list (`popular_recent`, `moebooru_client.dart:94`).
  - *XTA:* popular by day, week or month, stepping through dates on three engines, and top-scored posts as a fallback on Gelbooru (`booru_client.dart:132-160`; `booru_popular_tab.dart:31`).
  - **Matters: Low.**

**Where XTA is already different, and arguably ahead:**
- the follow-and-group feed above;
- related tags on every engine;
- search chips where conflicting terms replace each other, e.g. `x`, `-x` and `~x`, or two ratings (`booru_query.dart:70-98`);
- detecting sites that repeat or ignore page numbers (`booru_store.dart:166-185`);
- a descriptive API User-Agent;
- accessibility work: post counts read aloud and 48 dp touch targets (`booru_tag_list.dart:181-197`; `booru_search_landing.dart:104-131`).

**Gaps that matter most, in order:**
1. Stop sending credentials to the wrong site and showing them in error pages.
2. Throttle and back off the burst of requests for followed tags.
3. Per-site profiles: credentials, follows, blacklist and history.
4. Autocomplete that understands aliases, using each engine's own endpoint, with caching.
5. Bulk download with naming templates and skip-if-exists.
6. Danbooru notes, pools and artist pages.
7. More engines: Sankaku, Zerochan, Philomena, Shimmie2, and Gelbooru 0.2.5 as its own engine.

### 5.4 Architecture differences that matter for porting ideas

- **State management.**
  - *Boorusama:* Riverpod 2.6.1 (`BS:pubspec.yaml:86`), using only Notifier and AsyncNotifier (`BS:CLAUDE.md:6`). Providers are families keyed by the active profile's settings (e.g. `BS:lib/boorus/danbooru/client_provider.dart:14-22`).
  - *XTA:* flutter_triple Stores (`pubspec.yaml:86`; `CLAUDE.md:74`) for the feed, search, tags, blacklist, pager and lazy-load state (`booru_store.dart`, `booru_search_store.dart`, `booru_pager_store.dart`, `booru_load_store.dart`), injected with `provider`.
  - *Porting:* an AsyncNotifier maps cleanly onto a Store with `execute()`. XTA has nothing like a per-profile provider family, though: `BooruClient` re-reads one global site from preferences on every call (`booru_client.dart:48-62`). Profiles will need an explicit site object passed to the client and stores; `BooruSite` (`booru_endpoints.dart:10-41`) is the natural place for it.
- **Per-engine structure.**
  - *Boorusama* splits each engine into three layers:
    1. a pure-Dart API client per engine (`BS:packages/booru_clients/lib/src/<engine>/`); Danbooru's is built from one mixin per resource (`danbooru_client.dart:31-50`);
    2. a YAML list of sites with per-site overrides for endpoints, parsers, auth and capabilities (`BS:packages/booru_clients/boorus.yaml:43-177`), turned into Dart code by `BS:packages/booru_clients/tools/generators/` via `gen.sh`; the generated `BS:lib/boorus/registry.g.dart` is not committed (`BS:.gitignore:143`);
    3. a module per engine (`BS:lib/boorus/<engine>/`) that implements about 30 capability methods of `BooruRepository` (`BS:lib/core/boorus/engine/src/booru_repository.dart:29-62`, with defaults in `BS:lib/core/boorus/defaults/src/booru_repository_default.dart:51`) and the screen factories of `BooruBuilder` (`BS:lib/core/boorus/engine/src/booru_builder.dart`), registered together as `BooruComponents` (`BS:lib/core/boorus/engine/src/booru_component.dart:10-40`).
  - *XTA:* one enum, with a `switch` on the engine repeated across `booru_client.dart:256-327`, `booru_endpoints.dart:46-131`, `booru_parse.dart:11-120`, `booru_models.dart:48-74,247-259` and `booru_query.dart:143-161`. What each engine supports is answered by separate yes/no functions (`booru_endpoints.dart:69-73,98-101`; `booru_query.dart:160-161`).
  - *Porting:* a fifth engine would touch every one of those switches. Before adding the spec's later engines, the idea worth adopting is one XTA-native adapter per engine that bundles its URLs, parsers and capabilities. XTA's existing pure URL and parsing functions are already the right place to unit-test it (`docs/specs/booru-plugin.md:45-48`).
- **Networking.**
  - *Boorusama:* Dio with a chain of interceptors for rate limiting, challenge handling, auth, cookies, logging, de-duplication and page limits (`BS:lib/core/http/client/src/interceptors/`), over HTTP/2 where a site is configured for it (`protocol: https_2` in `BS:packages/booru_clients/boorus.yaml`).
  - *XTA:* a single `package:http` request method (`booru_client.dart:329-340`).
  - *Porting:* throttling has to be a wrapper inside the plugin, because `lib/client/` is frozen and specific to X (`CLAUDE.md:28-30`).
- **Storage.**
  - *Boorusama:* Hive and sqlite3 (`BS:pubspec.yaml:95,132`) for profiles, history, the tag cache, bulk tasks and bookmarks.
  - *XTA:* JSON preferences for the blacklist, history, custom sites and credentials, plus one sqflite table created by migration 53 (`lib/database/repository.dart:747-756`).
  - *Porting:* a tag cache, per-site follows or download tasks would each need a new migration (`docs/specs/booru-plugin.md:78`; `CLAUDE.md:28-30`).
- **Platform.** Several Boorusama features need a WebView: the challenge solver (`BS:lib/core/ddos/solver`), cookie login (`BS:lib/core/configs/auth/src/pages/cookie_access_webview_page.dart`) and the WebView video player. XTA's Linux build has no WebView (`CLAUDE.md:15-16`), so these can't be ported as they are.
- **Dropped on purpose (per the spec).**
  - Writing to the site: favourites, votes, uploads, comments and tag edits (`docs/specs/booru-plugin.md:14,75`).
  - Searches are saved locally (`:24`) and follows are global (`:68`).
  - Deferred to later:
    - more engines (`:65-67`);
    - profiles (`:68`);
    - a grid of saved posts and bulk download (`:69`);
    - notes, pools and artist pages (`:70`);
    - login flows beyond pasted keys (`:71`).
  - Boorusama's paid tier (`BS:pubspec.yaml:117`; e.g. `BS:lib/core/home/src/types/custom_home.dart:19-27`) has no XTA counterpart and doesn't need one.

### 5.5 Licence note

Boorusama is distributed under GPL-3.0 (`BS:COPYING`), and XTA's code is MIT (`LICENSE`). Copying or translating Boorusama source into XTA would bring GPL-3.0 obligations to the result, and that includes data files such as `BS:packages/booru_clients/boorus.yaml` or `BS:assets/tagdef.json`. Ideas, features and factual knowledge of the sites' public APIs (endpoint paths, parameter names, tag syntax) are not copyrightable and can be reimplemented independently.

## 6. Test inventory and gaps

All 197 tests in the 16 listed files pass, and none reaches the real network. None of the Booru tests uses recorded fixtures: every payload is a hand-written inline Map or List. There is no `test/fixtures/Booru*`, no Booru case in `parser_resilience_test.dart`, and no Booru smoke test in `test/live/`.

Two of the gaps below are probable defects found by reading the code, not just missing tests (neither was run): the API key can appear on screen in error pages (item 11), and late results can land in a disposed store (item 10).

### Inventory

Test counts are `test(` plus `testWidgets(` declarations. Per file they add up to exactly 197: 106 in the five Booru files and 91 in the shared files.

| File (tests) | Group (n) | What it covers | Data | Network |
|---|---|---|---|---|
| `booru_parse_test.dart` (17) | BooruRating (3) | `s` means sensitive in preferences but safe on the wire for Moebooru/e621; `exceeds` | Hand-written maps | None (pure) |
| | parseBooruPosts (7) | One post each: Danbooru, Moebooru (relative URLs), Gelbooru list, Rule34 `{post:[…]}` with file URL built from directory+image, Rule34 API host → page link, e621 `{posts:[…]}`; rating-cap and mute helpers | | |
| | host helpers (3) | Host/tag/query normalising, engine guess, Rule34 API/page host, custom sites encode/parse/upsert | | |
| | Gelbooru-family ratings (1) | `s` = safe; `sensitive` as a word | | |
| | catalogUrl (1) | Sample for stills, preview for video | | |
| | tag suggestions (1) | Danbooru-shaped rows | | |
| | booruHostIsMostlyExplicit (1) | Rule34/Gelbooru vs Danbooru/Safebooru | | |
| `booru_details_test.dart` (28) | post details (5) | Danbooru per-kind tag strings, size/md5/votes/parent; Gelbooru Ruby-style date, string booleans, owner, parent 0; bad date falls back to `change`; Moebooru author/children; e621 tag groups, votes, relationships | Hand-written maps; `MockClient` | None |
| | blacklist (6) | One tag; several (AND); `-tag`; `~a ~b`; `rating:` in the host's letters; unsupported metatag never hides; stored-entry normalising | | |
| | endpoints (5) | Popular URL per engine; credential parameter names (Danbooru, Gelbooru, Moebooru); Rule34 API host; one tag-info request per engine; wiki/comments URLs | | |
| | detail parsers (4) | Tag kinds/counts per engine; wiki body pick; comment order and hidden comments; DText/HTML to plain text | | |
| | popular dates (1) | Day/week/month steps, never past today | | |
| | client (7) | Gelbooru popular = `sort:score`; e621 popular is one page; popular applies rating cap + blacklist; wiki 404 → null; Moebooru kinds in one call; lookup counts merged with post kinds; non-JSON lookup → empty | | |
| `booru_search_store_test.dart` (20) | query helpers (7) | Token slots, input split, suggestion prefix, tag-set key, operator toggle, related tags, per-engine rating metatag / score order / `~` support | Hand-written; fake `BooruClient` subclass; `MockClient`; sqflite FFI temp DB | None |
| | suggestion categories (1) | Each engine's category codes, including Gelbooru's string fields | | |
| | history store (3) | Newest first per tag set; survives restart; forget/clear; cap | | |
| | search store (6) | Typing makes chips; picked suggestion keeps `-`; every search remembered; backspace; reopen chip; close editor | | |
| | saved searches (3) | Add/remove a tag set; save survives a concurrent reload; `postsForTags` sends one request per saved query | | |
| `booru_search_screen_test.dart` (21, widget) | top level (2) | Search shows in history on refocus; star saves/unsaves the whole tag set | Fake client returning 2 synthetic posts with null URLs; in-memory saved store | No real network. The 4 tests that open a post call the real `BooruClient.tagInfo` over a default `http.Client()`. flutter_test answers every such request with 400, and `tagInfo` swallows the error (booru_client.dart:182-184) |
| | tags as chips (7) | Space / ✕ / backspace; suggestion appends; `-` survives a pick; Exclude button; rating shortcut; chip back into field; Edit as text | | |
| | history (5) | Every way into a search recorded; initial query; tap re-runs and moves to top; ✕ / swipe / clear; survives screen and restart | | |
| | saved searches and results (7) | Saved above history; back closes the editor first; related tags tap / long-press; post tag add / exclude; follow + hide from the tag sheet; viewer swipe #1 → #2 | | |
| `booru_viewer_test.dart` (20) | pager store (4) | Prefetch near the end and growth; dedupe across pages; a host that ignores `page` ends the feed; `booruPostsExtend` | Hand-built `BooruPost`s; fake client (search, comments, tagInfo, wiki overridden) | None |
| | tag groups (5) | Kind order, A–Z, by count; chip text, short counts, colours; artist strip skips placeholder artists; family query | | |
| | details (5) | Facts, grouped tags, "More from"; tag sort kept; comments load on expand; wiki from a tag; facts leave out missing fields | | |
| | popular tab (2) | Scale/date changes; Gelbooru note | | |
| | display (3) | Columns/details/blur from prefs; settings: multi-tag mute entry and column pick; tile/viewer URL per setting | | |
| | (ungrouped, 1) | Webm-sampled animation plays as video | | |
| `plugin_category_test.dart` (2 of 4) | — | Booru listed under Art; listed as available when not installed | Built-in registry | None |
| `pixiv_saucenao_test.dart` (0 of 8) | — | No Booru code runs; Danbooru links are only non-Pixiv input to be skipped (3 tests) | Hand-written HTML/JSON; `MockClient` | None |
| `plugin_marks_test.dart` (3 of 3) | — | Booru mark is `inventory_2`; size inside a bigger box; painted area centred | — | None |
| `tab_icons_test.dart` (1 of 2) | — | Home-strip tab shows the Booru mark and label | Prefs | None |
| `plugin_feed_chips_test.dart` (1 of 3, +1 with Booru enabled) | — | A hidden-tab Booru is not a Groups chip | Prefs | None |
| `plugin_home_crash_sweep_test.dart` (2 of 27) | — | `BooruPostGrid` with 0 / 1 / 8 tiles in a NestedScrollView: no exception, outer controller untouched, survives a drag | Synthetic posts with `''` URLs | None |
| `plugin_card_overflow_test.dart` (1 of 7) | — | `BooruPostCard` in German at 320 dp does not overflow | Synthetic post | None |
| `plugin_home_chrome_test.dart` (1 of 10) | — | Only checks the text "Booru" is absent; builds no Booru code | — | None |
| `backup_format_test.dart` (1 of 4) | — | Pinned backup keys include `booruSubscriptions` | — | None |
| `feed_strip_test.dart` (1 of 15) | — | `FeedTab(pluginIdBooru).icon == Icons.inventory_2` | — | None |
| `group_migration_test.dart` (1 of 8) | — | Migration 53 creates `booru_subscription` and accepts a row | sqflite FFI, v34 schema built in code | None |

### Test run

Command: `fvm flutter test <the 16 files> 2>&1 | tail -40`. Exit code 0, and `git status` was clean afterwards. The last lines, verbatim:

```
00:23 +196: test/group_migration_test.dart: migration 57 creates instagram_subscription
00:23 +197: test/group_migration_test.dart: (tearDownAll)
00:23 +197: All tests passed!
```

That is 197 passed, 0 failed, 0 skipped: no `-N` or `~N` counter appears anywhere in the log. The only other output was the "running flutter as root" warning and a `pub get` resolve.

### Gaps against the required list

Paths are under `lib/plugins/booru/` unless they start with another folder.

1. **Blacklist matcher: partly covered.**
   - **Covered:** one tag, AND of several, `-tag`, `~a ~b`, `rating:s` read per engine (Danbooru vs Moebooru), unsupported metatags never hide, stored-entry normalising (booru_details_test.dart:182-216 and booru_parse_test.dart:214-234).
   - **Untested** (booru_parse.dart:360-390, tokens in booru_query.dart:59-68):
     - An entry of only negated tags (`-solo`) hides every post *without* that tag (booru_parse.dart:371).
     - AND mixed with either (`solo ~hat ~cap`), and a lone `~tag` (369-372).
     - `-rating:x`, word ratings (`rating:explicit`), and upper case `Rating:E` (booru_query.dart:63).
     - `rating:` entries against Gelbooru or e621 posts; the fallback to Danbooru letters for an unknown engine (booru_parse.dart:387); posts with no rating (389).
     - Lower-casing of the post's own tags (362).
     - Plain tags that contain a colon, such as `re:zero` or `:d` (booru_query.dart:26-49, 62-66).
     - `tag*` wildcards and Gelbooru `{a ~ b}` groups are not supported. They are compared literally (booru_parse.dart:377) and so never match; no test pins that.
     - `BooruMuteStore.unmute` (booru_store.dart:101-108).

2. **Image variant selection: partly covered.**
   - **Covered:** catalog image (booru_parse_test.dart:291-331); parsed preview/sample (33-212); `BooruDisplay.tileUrl`/`viewerUrl` when all three URLs exist (booru_viewer_test.dart:412-434); ugoira `videoUrl` (437-457).
   - **Untested:**
     - What happens when a variant is missing: `thumbnailUrl`, `displayUrl`, `catalogUrl` falling back to the file, `originalUrl` (booru_models.dart:135-157, 188-189).
     - `videoUrl` prefers the file over the sample, and recognises mp4/mkv (170-174).
     - `isVideo` set from `fileExt` with no playable URL (164-166). The viewer then shows the thumbnail, and full screen opens the file externally (booru_post_media.dart:37, 52-54).
     - The image inside the viewer ignores `originalInViewer` (booru_post_media.dart:37), while full screen honours it (:60).
     - Variants used by save/download (booru_post_actions.dart:21-32), the feed card (booru_post_card.dart:42) and the related strip (booru_related.dart:109).
     - Decode width of `maxWidth × DPR` (booru_image.dart:45-60).

3. **Pagination and dedupe: partly covered; there is no before-id code.** Pages are numeric only: `page=N`, or `pid=N-1` on Gelbooru (booru_client.dart:313-326).
   - **Covered:** dedupe across pages by `host:id` (booru_viewer_test.dart:147-160, against booru_store.dart:179-185); a host that ignores `page` ends the feed (162-175); e621 popular as one page.
   - **Untested:**
     - Page/`pid`/`limit`/`tags` URLs per engine (booru_client.dart:303-327).
     - `hasMore` comes from the raw count (107), so a page emptied by filters shouldn't end the feed (110-127).
     - `refresh()` skipping up to 4 filtered-empty pages (booru_store.dart:134-143).
     - `loadMore()` skipping empty pages (156-167), swallowing errors (170-172), and its guard against running twice (151).
     - The first page is not deduped (141). Duplicate ids in one page would give two tiles the same Hero tag (booru_grid.dart:133-134).
     - Following and the home interleave merging across tags and sorting newest first (booru_client.dart:240-252; booru_interleaved.dart:21-35). Its only test answers `[]`.
     - Danbooru popular paging (booru_client.dart:147-159).
     - The grid's load-more trigger 800 px from the end (booru_grid.dart:47-55).
     - `_withRatingTag` (booru_client.dart:256-281): tests only hit the no-op cases (cap `e`, Gelbooru).

4. **Filename templates: no code.** Booru downloads use the shared `pluginMediaFileName`, which gives `booru-<basename of the original URL>` (lib/plugins/plugin_post_media.dart:581-586), via booru_post_actions.dart:31-32 and :64. That helper is tested only for Mastodon (test/plugin_post_media_test.dart:62). No Booru name is tested: Danbooru md5 names, Rule34's built `/images/<dir>/<image>` URL, or percent-encoded or very long names.

5. **Rate limiter / backoff: no code.**
   - A 429 is only mapped to `rateLimited` (booru_client.dart:354-356) and shown as a message (booru_errors.dart:12). There is no throttle, no Retry-After handling and no retry.
   - Requests fan out without a limit:
     - Following fires one request per followed entry at once with `Future.wait`, and silently turns each failure, a 429 included, into an empty page (booru_client.dart:229-238).
     - Every viewer page adds a tag lookup and up to two related-strip searches (booru_post_screen.dart:59-60, 91-92, 117-123).
     - Only images retry, once, with a 15 s limit (booru_image.dart:4, 39-40, 56-57).
   - No test references `BooruException`, `BooruErrorKind` or `booruErrorMessage`. The 401/403/429/5xx, timeout and network branches (booru_client.dart:329-377) are never hit.

6. **Parsers against recorded real responses: untested for all four engines.** All inputs are minimal hand-written maps (booru_parse_test.dart:33-212; booru_details_test.dart:56-179, 300-375; booru_search_store_test.dart:146-180).
   - **Units that need captured Danbooru, Moebooru, Gelbooru v2 and e621 responses:** `parseBooruPosts` / `_parseOne` / `_parseE621` (booru_parse.dart:11-29, 68-120, 175-226); `parseBooruTagSuggestions` (31-46); tag info, wiki and comments (booru_detail_parse.dart:12-66); `fetchJson` turning an empty body into `[]` (booru_client.dart:364-367).
   - **Shapes nobody pins:**
     - The object envelope around Gelbooru's `post` list (booru_parse.dart:19).
     - Items with no preview or sample: only the file URL is built (320-330).
     - Posts that come back with no file URL at all: they are kept and shown as broken tiles.
     - `_extFromUrl` (332-338) is never asserted.

7. **Grid keys and placeholders: smoke tests only.**
   - Grid tiles have no per-post key (booru_grid.dart:69-73), unlike the viewer pages (booru_post_pager.dart:46, 53).
   - Layout before the image loads comes from `AspectRatio`, clamped to 0.45–1.6 (booru_grid.dart:117, 128-129), and is 1:1 when the host sends no size (booru_models.dart:159-162). No test measures a tile.
   - The empty-URL placeholder (booru_image.dart:23-31) and the failed-load placeholder (booru_grid.dart:195-203) are rendered by some tests but never checked. There is no loading placeholder.
   - The first-load skeleton (booru_screen.dart:231-240) is untested because no test renders `BooruScreen`.

8. **Viewer navigation: partly covered.**
   - **Covered:** prefetch and growth in the store (booru_viewer_test.dart:125-145, against booru_pager_store.dart:31-40); `booruPostsExtend` (177-182); one widget swipe on a feed with nothing more to load (booru_search_screen_test.dart:430-438).
   - **Untested:**
     - A swipe that triggers `loadMore` and grows the `PageView` while staying on the same page (booru_post_pager.dart:41-55).
     - Clamping of the starting index and out-of-range `show` (booru_pager_store.dart:27, 32).
     - The "list was extended" check compares only the first and last old keys (55).
     - The grid → viewer Hero (booru_grid.dart:133; booru_post_media.dart:27-28).
     - Full screen (booru_post_media.dart:51-68) and video playback (22-26, 44-49).

9. **Blacklist reveal: no code.**
   - Muted and over-cap posts are dropped inside the client (booru_client.dart:112-127), so there is no hidden count and no "show anyway".
   - Hiding a tag from the tag sheet or settings (booru_tag_sheet.dart:60-61; booru_settings.dart:122-126) does not re-filter posts already loaded. Untested.
   - The explicit/questionable blur (booru_display.dart:46-47; booru_grid.dart:137-138, 205-206) has no tap-to-reveal: tapping opens the viewer, which is never blurred. The blur test covers explicit only, not questionable.

10. **Store lifecycle (cancel on query change): partly covered; nothing cancels requests.**
   - **Covered:** load-once for comments (booru_viewer_test.dart:284-294); a save survives a concurrent reload (booru_search_store_test.dart:304-312); the pager ignores feed updates that don't extend its list.
   - **Untested, and risky by code reading:**
     - **Old query keeps loading.** Each new query creates a new results store and the old one keeps loading, by design (booru_search_store.dart:176-179). The UI stays correct only because results are keyed by `ObjectKey` (booru_search_screen.dart:194). No test sends a late answer for query A after query B.
     - **`loadMore()` races `refresh()`.** `loadMore()` does not go through `execute` (booru_store.dart:150-175). A page that arrives after a `refresh()` is appended to the new list and moves `_page` (159-163). That can happen after a Popular date/scale change (booru_screen.dart:133-137) or pull-to-refresh. A superseded `refresh()` also still writes `_page`/`_hasMore` (144-145), because triple only cancels delivery of the result (triple-2.1.0 base_store.dart:201-234).
     - **`destroy()` mid-load.** `destroy()` doesn't cancel a pending `execute`, and flutter_triple's `propagate` doesn't check for a disposed store (flutter_triple-3.0.0 store.dart:45-56, 108-116). A late result therefore notifies a disposed `ValueNotifier` (asp `Atom`), which asserts in debug builds. It happens when leaving search mid-load (booru_search_store.dart:205), swiping past a post before its strips or tag lookup load (booru_related.dart:43-47; booru_post_screen.dart:63-66), or closing comments or the wiki (booru_comments.dart:34-37; booru_tag_sheet.dart:154-157). All tests use fakes that answer instantly, so this path never runs.
     - **Suggestion timing.** The stale-suggestion guard (booru_search_store.dart:197) and the 250 ms debounce (:76, :192) are untested; the tests use a zero debounce.
     - **`BooruScreen` itself.** Its followed-tags observer, lazy per-tab loading and store reuse across rebuilds (booru_screen.dart:54-92, 122-126) are untested, as is `BooruLoadStore.reload` after an error (booru_load_store.dart:18-21).

11. **Settings migration of credentials: no code.**
   - **How credentials work today:** there is one global login/key pair (lib/constants.dart:465-466), appended to every request URL (booru_endpoints.dart:27-36). Switching preset, engine or host keeps it (booru_settings.dart:61-73, 210-248), so host A's key is sent to host B under B's parameter names. Custom sites store no credentials (booru_engines.dart:241-249), and no settings migration touches Booru (lib/main.dart:758-761).
   - **Tests:** only the parameter names for Danbooru, Gelbooru and Moebooru (booru_details_test.dart:246-252).
   - **Not tested:**
     - e621's parameter names.
     - That credentials actually reach the URL.
     - What happens on a host switch.
     - `resetPreferences` clearing them (booru_plugin.dart:153-154).
     - Backup stripping of `plugin.booru.api_key`: it is removed only by the generic `*api_key` suffix rule, tested generically in test/crash_reporter_test.dart:63-72. The login is exported.
   - **Probable leak (code reading, untested):** `BooruException` messages contain the full request URI (booru_client.dart:338, 348, 352, 355, 360, 374). `FullPageErrorWidget` prints `'$error'` for plugin errors (lib/ui/errors.dart:589-612). So with credentials set, any 401/403/404/429/5xx or bad JSON on Latest, Popular, Following or Search (booru_screen.dart:241-246; booru_search_results.dart:26-31) shows `api_key` or `password_hash` on screen.

## 7. Smoke test (static substitute)

**Every item in this section is static and was not run live.** The sandbox's network policy blocks every booru host and there is no Android device. As the closest substitute, each flow was traced from its screen widget down to URL building, HTTP error mapping and JSON parsing, for every engine and preset. The results were checked against Boorusama's clients (GPL-3.0, read only, cited by path, no code copied). Wherever a row says **"Likely"**, the host behaviour comes from Boorusama's handling or the host's documented API, not from a response observed here.

### 7.1 Coverage (flow × engine)

The engines and their presets are:
- **Danbooru:** Danbooru, AIBooru
- **Moebooru:** Yande.re, Konachan, Sakugabooru
- **Gelbooru v2:** Safebooru, Gelbooru, Rule34, TBIB, Xbooru
- **e621:** e926, e621

ST-04 (feeds not reset after a site or filter change) applies to every row, so it isn't repeated in the table.

| Flow | Danbooru | Moebooru | Gelbooru v2 | e621 |
|---|---|---|---|---|
| Login / credentials | ST-01, ST-02 | ST-01 | ST-01, ST-03 (Rule34) | ST-01, ST-02 |
| Home Latest | ST-05, ST-13 | ST-13 | ST-03, ST-07, ST-13, ST-15, ST-20 | ST-05, ST-13 |
| Popular | ST-12, ST-16 | ST-16 | no list; uses sort:score (ST-03 on Rule34) | ST-16 |
| Following | ST-09, ST-10 | ST-10 | ST-03, ST-10 | ST-10 |
| Search + autocomplete | ST-09 | no defect found | ST-07, ST-08 | no defect found |
| Page 2+ | ST-11, ST-12 | ST-11 | ST-11 | ST-11 |
| Post: tag lookup | no defect found | no defect found | ST-07, ST-08 | no defect found |
| Post: comments | no defect found | no defect found | hidden (XML only), correct | no defect found |
| Post: related strips | no defect found | no defect found | ST-08 (no artist strip) | no defect found |
| Post: wiki | no defect found | ST-17 | hidden, correct | no defect found |
| Full-screen viewer | ST-05 | no defect found | ST-15 | ST-05, ST-06 |
| Video / animated | ST-22, ST-23 (ugoira) | ST-22 (Sakugabooru) | ST-15, ST-22 | ST-06, ST-22 |
| Download / save / share | ST-05, ST-23 | ST-14 | ST-15 | ST-05 |

Issues that cut across all engines: ST-18 (engine guessed for a custom host), ST-19 (error-message mapping), ST-24 and ST-25 (widget lifecycle).

### 7.2 Findings

| ID | Flow | Engine(s) | Steps to reproduce | Expected vs likely actual | Suspected file:line | Evidence | Confidence |
|---|---|---|---|---|---|---|---|
| ST-01 | Login / credentials (Settings site chips, host field) | All four engines, every preset | 1. Settings → Danbooru; enter a login and an API key. 2. Tap the e621 (or Yande.re, or Safebooru) chip. 3. Go back to Latest and pull to refresh. | **Expected:** the new site is used as a guest, or with its own key. **Likely:** the Danbooru login and key go to the new host on every request (e621 as `login`/`api_key`, Gelbooru as `user_id`/`api_key`, Moebooru as `login`/`password_hash`). e621 answers 401, so every tab says "The host refused the request — check login and API key". The other hosts silently receive a key from another site. | booru_settings.dart:61-73 (`_applyPreset` never clears the login/key prefs), 240-248; booru_client.dart:60-62, 129-130; booru_endpoints.dart:27, 33-36 | One global login/key pair is merged into every endpoint of whatever host is current. Boorusama gives each site its own credentials: BS:packages/booru_clients/lib/src/danbooru/danbooru_client.dart:51-71, BS:packages/booru_clients/lib/src/e621/e621_client.dart:17-37, BS:packages/booru_clients/lib/src/moebooru/moebooru_client.dart:52-57. | High for the key leak; medium for which hosts answer 401. Static, not run live. |
| ST-02 | Login / credentials | Danbooru, AIBooru, e621, e926 (Moebooru and Gelbooru unverified) | In Settings, type a login and leave the API key empty (the helper text says the key is optional), or paste a key with no login. Open Latest. | **Expected:** guest access until both fields are filled. **Likely:** a half credential goes out on every request, and Danbooru/e621 reject it as failed API authentication (401) on every tab. This also happens while the user is still typing, because each keystroke is saved. | booru_endpoints.dart:27 (adds the login and the key independently); booru_settings.dart:261, 273 | Boorusama only attaches credentials when both are present: BS:packages/booru_clients/lib/src/danbooru/danbooru_auth_interceptor.dart:15-21, BS:packages/booru_clients/lib/src/moebooru/moebooru_client.dart:52-57, BS:packages/booru_clients/lib/src/gelbooru_v2/gelbooru_v2_client.dart:76-82. | Medium. Static, not run live. |
| ST-03 | Login, Latest, Popular, Following, autocomplete, tag lookup | Gelbooru v2: Rule34 preset | In Settings, choose Rule34 with no keys (the preset list is commented "guest-friendly" and the key is described as optional). Open Latest, then tap Test connection. | **Expected:** posts, or a message saying this host needs a user id and API key. **Likely:** the API returns HTTP 200 with the text "Missing authentication…". JSON decoding fails, so the screen says "The host returned something unexpected". Following shows "No recent posts". Autocomplete and tag kinds stay empty. | booru_engines.dart:52-55, 99-104; booru_client.dart:364-376; booru_errors.dart:14 | Boorusama marks rule34.xxx as `auth: required: true` (BS:packages/booru_clients/boorus.yaml:111-113). It also turns a 200 body that starts with "missing authentication" into a 401 (BS:lib/core/http/client/src/interceptors/auth_error_response_interceptor.dart:6-38). | High. Static, not run live. |
| ST-04 | Latest, Popular, Following, page 2+, post details | All | 1. Load Danbooru Latest and scroll one page. 2. In Settings, tap Safebooru (or change Max rating, or add a blacklist entry). 3. Go back. 4. Keep scrolling, open one of the old posts, and expand Comments. | **Expected:** the feeds reload for the new site and filters. **Likely:** the old Danbooru grid stays, and the next page load appends Safebooru posts (`pid=2…`) below it, mixing two hosts. Tag counts, "More from", wiki and comments for the old posts are fetched from the new host using Danbooru ids: on Safebooru this shows "No comments yet"; after switching to e621 it shows another post's comments. Posts now above the rating cap, or newly muted, stay visible. | booru_screen.dart:57-71, 122-126 (refreshes only when a feed is empty); booru_store.dart:150-164; booru_client.dart:129-130, 164-203 (detail calls use the current site, not `post.host`/`post.engine`); booru_post_screen.dart:58, 93; lib/home/home_screen.dart:87-90 | The feed stores live in the `PluginSessionStore`, which lasts as long as Home. Nothing listens to the host, engine, rating or blacklist preferences. | High. Static, not run live. |
| ST-05 | Grid, post view, full screen, download, save | Danbooru/AIBooru (banned or restricted posts for guests; `.swf` files) and e621/e926 (`file.url: null`; `.swf` files) | As a Danbooru guest, search `banned_artist`; as an e621 guest, search `status:deleted`. Tap a tile, then the picture, then Download, then Save. | **Expected:** these posts are hidden or marked unavailable. **Likely:** broken-image tiles. Tapping the picture does nothing, because the shared viewer drops the empty URL. Download returns without any message. Save stores an entry whose image URL is empty. For flash posts, the `.swf` file is used as an image, giving a broken tile and viewer. | booru_parse.dart:86-89, 208-210 (posts are kept even when every URL is null); booru_models.dart:135-157; booru_post_media.dart:37, 56-67; lib/plugins/plugin_post_media.dart:597-599, 629-630; booru_post_actions.dart:26 | Boorusama drops these posts: e621 posts without `file.url` (BS:lib/boorus/e621/posts/parser.dart:18), Gelbooru posts without an md5/hash (BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:131; BS:packages/booru_clients/lib/src/gelbooru_v2/parsers/gel_parsers.dart:46), and Danbooru swf/flash posts plus, optionally, banned posts (BS:lib/boorus/danbooru/posts/post/src/providers.dart:108-113). | Medium-high. Static, not run live. |
| ST-06 | Post view and full-screen viewer (animated posts) | e621/e926 (possibly also large GIFs on Gelbooru/Moebooru whose sample is a JPEG) | On e621, search `type:gif` and open a GIF wider than 850 px. | **Expected:** the GIF animates. **Likely:** the post view and full screen show the still JPEG sample. It only animates when "Original files in full screen" is turned on. | booru_models.dart:139-141 (`displayUrl` prefers the sample); booru_post_media.dart:37; booru_display.dart:44; booru_parse.dart:209 | Boorusama uses the original file for e621 GIFs: BS:lib/boorus/e621/posts/parser.dart:30-39. | Medium-high. Static, not run live. |
| ST-07 | Tile details, post tags, tag sheet (search, follow, copy), related-tag chips | Gelbooru (gelbooru.com) | On Gelbooru, search `girls'_frontline` and open a post. | **Expected:** the tag reads `girls'_frontline`. **Likely:** chips, copied tags and followed entries read `girls&#039;_frontline`. `&amp;` and `&quot;` appear the same way. | booru_parse.dart:236-243 (tags split as-is), 48-49 (suggestion names used as-is) | Boorusama decodes HTML entities in Gelbooru post tags and tag names: BS:lib/boorus/gelbooru/common/parser.dart:1-8, BS:lib/boorus/gelbooru/posts/parser.dart:20-21, BS:lib/boorus/gelbooru/tags/parser.dart:12. | Medium. Static, not run live. |
| ST-08 | Autocomplete; tag kinds and counts; "More from artist" | Gelbooru 0.2 forks (Safebooru, TBIB, Xbooru, Rule34); gelbooru.com for suggestion ranking only | On Safebooru, open Search, type `bl`, then open any post. | **Expected:** suggestions; tags grouped by kind with counts; an artist strip. **Likely:** the tag endpoint (`index.php?page=dapi&s=tag&json=1`) answers in XML (Rule34 answers with the authentication text). The decode error is swallowed, so no suggestion list ever appears, tags stay in one ungrouped list without counts, and "More from" is missing. On gelbooru.com, suggestions are not ranked by use because no `orderby=count` is sent. | booru_client.dart:283-300, 205-218, 172-184; booru_endpoints.dart:89-90 | Boorusama never uses this tag endpoint for Gelbooru 0.2 forks. It autocompletes with `/autocomplete.php?q=` and reads post tags from the post page (BS:packages/booru_clients/boorus.yaml:77-80, 95-99, Rule34 override at 119). Its gelbooru.com tag parser expects XML replies (BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:372-395), and its gelbooru.com autocomplete uses `page=autocomplete2` (170-199). | Medium for the forks; low for gelbooru.com ranking. Static, not run live. |
| ST-09 | Refining a search, "Order by score" chip, tag sheet "Add to search", saved searches in Following | Danbooru, AIBooru (guest or Member accounts, which have a 2-tag search limit) | As a Danbooru guest, search `1girl solo`, then tap "Order by score" and search again (or tap a related-tag chip, or use "Add to search" from a post). Also star `1girl solo smile`. | **Expected:** results, or a "tag limit reached" message. **Likely:** HTTP 422, shown as "The host returned something unexpected" because Danbooru's own message is thrown away. The starred 3-tag search silently adds nothing to Following. | booru_search_landing.dart:178, 189-194; booru_search_results.dart:36-39; booru_tag_sheet.dart:54-57; booru_client.dart:357-362, 235-237 | Boorusama's list of Danbooru metatags that don't count toward the limit (BS:assets/tagdef.json, `free_metatags`) includes rating, status, parent and others, but not `order`. So `order:score` counts toward the limit, while the `rating:` tags XTA appends don't. | Medium. Static, not run live. |
| ST-10 | Following tab; Booru posts in the home feed | All | Turn on airplane mode (or use Rule34 without keys, or follow 10+ tags on e621). Open Following and pull to refresh. | **Expected:** an error with Retry, or a notice that some tags failed. **Likely:** "No recent posts for your followed tags". All N tag requests are sent at once, and the rate-limited ones (e621 documents a limit of about 2 requests per second) disappear without a trace. | booru_client.dart:229-238; booru_screen.dart:66-70, 315 | Each per-tag failure is turned into an empty page, so the feed's error view can never appear for Following. There is no throttling around `Future.wait`. | High for the silent empty state; low-medium for the dropped tags. Static, not run live. |
| ST-11 | Page 2+ (all grids and the viewer's prefetch) | All | Scroll Latest to the end with the network off, or while the host returns 429/503, and keep dragging. | **Expected:** a spinner at the bottom, then an error with retry, or an end-of-feed marker. **Likely:** no spinner ever appears, and there is no error and no end marker. A new request goes out as soon as the previous one fails, for as long as the user keeps dragging. | booru_store.dart:116-118, 150-175; booru_grid.dart:47-55, 76-82; booru_screen.dart:237, 266; booru_search_results.dart:45 | The "loading more" flag is a plain field read during build. The only rebuild happens after the `finally` block has already cleared it. The `catch (_) {}` leaves "has more" set to true. | High. Static, not run live. |
| ST-12 | Page 2+ racing a refresh; changing the Popular scale or date | All (Popular only pages on Danbooru/AIBooru) | In Danbooru Popular (Day), scroll until page 2 starts loading and immediately tap Week (or pull to refresh Latest while page N is loading). | **Expected:** the week list, starting at page 1 and then page 2. **Likely:** the stale page 2 of the day list is appended to the week list and the page counter becomes 2. The next load fetches week page 3, so week page 2 is never shown. | booru_store.dart:125-148, 156-164; booru_screen.dart:133-137 | `refresh()` resets the page counter but can't cancel a load-more that is already running. `execute` only discards the superseded result itself (pub:triple-2.1.0/lib/src/base_store.dart:201-237), not the page-counter writes made inside the refresh (booru_store.dart:144-145). | Medium. Static, not run live. |
| ST-13 | First opening of Latest and Popular; starting a search | All | Cold-start the app and open the Booru tab; then open Popular; then run any search. | **Expected:** a skeleton or spinner. **Likely:** for at least 100-150 ms, Latest shows "No posts yet — check the host in settings" with a Retry button before loading starts. Popular briefly shows "No popular posts for this date", and a new search briefly shows its no-results text. | booru_screen.dart:74-92, 247-259; booru_search_store.dart:178-182; booru_search_results.dart:33-35 | Every `execute` waits 50 ms before switching to the loading state (pub:triple-2.1.0/lib/src/base_store.dart:201-212). Latest also first waits for the followed-tags and blacklist loads. A new feed starts as an empty list, so the empty-state pane is drawn. | High; low impact. Static, not run live. |
| ST-14 | Download (file name) | Yande.re, Konachan (Moebooru) | On Yande.re, open a post and tap Download. | **Expected:** a short name such as `booru-yande.re-123456.png`. **Likely:** `booru-yande.re%20123456%20tag%20tag%20….png`, with percent escapes and the whole tag list. Names longer than 240 characters are cut from the front, losing `booru-` and the post id. | lib/plugins/plugin_post_media.dart:581-586 (uses `path.basename(uri.path)`, which keeps the percent encoding); lib/downloads/download_entry.dart:120-124 (keeps the last 240 characters) | Moebooru file URLs end in `<site> <id> <tags>.<ext>`, percent-encoded. | Medium-high. Static, not run live. |
| ST-15 | Grid tiles, post view, video poster, file URLs | Rule34 (and Gelbooru 0.2 hosts that omit `preview_url` or `file_url`) | With Rule34 keys set, open Latest, then open image and video posts. | **Expected:** thumbnails, samples and posters load. **Likely:** sample URLs on `api-cdn.rule34.xxx/samples/` are used as-is for tiles and the post view. When `preview_url` is missing, the tile falls back to the original file: a full-size image, or the `.mp4` itself, which can't be decoded as an image. The fallback that builds a file URL only works when `directory` is a JSON string. No `Referer` header is sent. | booru_parse.dart:86-88, 319-330 (`json['directory'].string` is null when the value is a number; lib/utils/json.dart:36); booru_models.dart:135-157 | Boorusama rewrites api-cdn sample URLs to `wimg.rule34.xxx` (BS:lib/boorus/gelbooru_v2/posts/types.dart:155-174). It builds thumbnail and sample URLs and converts `directory` to a string (BS:packages/booru_clients/lib/src/gelbooru_v2/post_v2_dto.dart:31-80). It sends a `Referer` header (BS:lib/boorus/gelbooru_v2/gelbooru_v2_repository.dart:50-54). | Low-medium. Static, not run live. |
| ST-16 | Popular on its default date ("today") | Danbooru, AIBooru, e621, e926, the Moebooru presets | With the device set to UTC+8 or UTC+9, open Popular in the local morning. | **Expected:** today's popular posts. **Likely:** "No popular posts for this date", because the local date is a day the host has not reached yet. | booru_popular.dart:17-18; booru_endpoints.dart:46-63 | The date sent is the device's local date. Boorusama does the same (BS:packages/booru_clients/lib/src/danbooru/danbooru_client_explores.dart:10), so this behaviour is shared with the reference. | Low-medium. Static, not run live. |
| ST-17 | Wiki (from the tag sheet) | Moebooru (Yande.re, Konachan, Sakugabooru) | On Yande.re, open a post, tap a common tag such as `dress`, then Wiki. | **Expected:** that tag's wiki page. **Likely:** "This tag has no wiki page" whenever the page isn't among the first 10 results of Moebooru's wiki search. | booru_endpoints.dart:108-109; booru_detail_parse.dart:35-41 | The `query` parameter is a search sorted by title, not a title lookup, and only an exact title match among those 10 results is accepted. Boorusama has no Moebooru wiki to compare against. | Low-medium. Static, not run live. |
| ST-18 | Settings: Add site, or the host field | Custom host hypnohub.net | In Settings, tap Add site, enter `hypnohub.net`, tap Add, then open Latest. | **Expected:** the Gelbooru v2 engine. **Likely:** the engine is guessed as Moebooru, `/post.json` returns 404, and the screen says "That page was not found on the host". | booru_engines.dart:179-183 | Boorusama lists `https://hypnohub.net/` under gelbooru_v2 (BS:packages/booru_clients/boorus.yaml:151). | Medium. Static, not run live. |
| ST-19 | Error messages (all screens) | All, especially hosts behind Cloudflare; e621's rate limit | As a guest, hit a bot-protection challenge (a 403 HTML page) or e621's rate limit (503). | **Expected:** a message about bot protection or rate limiting. **Likely:** a 403 shows "check login and API key" even when no credentials are set; a 503 shows "The host returned something unexpected". | booru_client.dart:345-362; booru_errors.dart:11, 14 | Boorusama detects challenge pages on 403/503 responses (BS:lib/core/ddos/solver/src/protection_detector.dart:18-58). | Low-medium. Static, not run live. |
| ST-20 | Latest under a rating cap | TBIB (and custom Gelbooru hosts left on General) | Choose the TBIB preset (its cap defaults to General) and open Latest. | **Expected:** general-rated posts. **Likely:** no `rating:` tag is sent. Up to 4 pages of 40 posts are fetched and filtered on the device; if all of them are above the cap, the tab says "No posts yet — check the host in settings" even though the host works. Later page loads can also add nothing. | booru_client.dart:256-263; booru_store.dart:131-143; booru_engines.dart:205-211; booru_screen.dart:248-259 | The plugin already builds Gelbooru rating metatags (booru_query.dart:143-151), but only uses them for the search chips. | Low. Static, not run live. |
| ST-21 | Post facts: "Posted" date; ordering in Following and the home feed | Safebooru, Rule34, TBIB, Xbooru | Open a Safebooru post that was re-tagged recently. | **Expected:** the upload date. **Likely:** the time of the last edit (`change`) is shown as the posted date and used to order Following and home-feed cards. | booru_parse.dart:277-280; booru_post_facts.dart:35; booru_client.dart:248-252 | Boorusama leaves the creation date empty for Gelbooru v2 (BS:lib/boorus/gelbooru_v2/posts/parser.dart:48). XTA's test at test/booru_details_test.dart:118 codifies this fallback, so it's intentional but mislabelled. | Low. Static, not run live. |
| ST-22 | Video in the post view; grid images | Every host with video (Danbooru, Sakugabooru, Gelbooru, Rule34, e621) | In Settings → Media, turn on "disable media autoload", then open a Booru video post. | **Expected:** a tap-to-load placeholder, as in the other plugins. **Likely:** the shared X video player (`TweetVideo`) is built directly and loads the poster and stream under its normal rules; grid images load regardless of the setting. | booru_post_media.dart:22-25, 44-49 | The manual-load gate lives in `PluginPostVideo` (lib/plugins/plugin_post_media.dart:449-461), which Booru bypasses. | Medium; low impact. Static, not run live. |
| ST-23 | Download of an animated post | Danbooru ugoira | Open an `ugoira` post (it plays the webm sample) and tap Download. | **Expected:** a playable file, or a choice of files. **Likely:** the frame archive `booru-<md5>.zip`. | booru_post_actions.dart:31-32; booru_models.dart:188-189 | Boorusama offers both the sample and the original as download sources (BS:lib/boorus/danbooru/posts/post/src/providers.dart:118-160). | Low. Static, not run live. |
| ST-24 | Settings | All | Pick an engine, max rating or site, then leave the screen immediately. | **Expected:** nothing happens. **Likely (rare):** `setState` runs after the screen is disposed; in release builds `_element!` throws and the error is logged. | booru_settings.dart:72, 118, 229, 298 | `setState` is called after `await prefs.set(...)` without a `mounted` check. | Low. Static, not run live. |
| ST-25 | Swiping in the post viewer; leaving a search | All | Swipe quickly through posts, or back out of a search, while the tag lookup, comments, related strips or results are still loading. | **Expected:** nothing. **Likely:** in debug builds, "A ValueNotifier was used after being disposed" from the late update; no effect in release builds. | booru_post_screen.dart:65; booru_comments.dart:36; booru_related.dart:45; booru_tag_sheet.dart:156; booru_search_store.dart:205 | `Store.destroy` disposes the store's notifiers (pub:flutter_triple-3.0.0/lib/src/stores/store.dart:108-116), while the pending `execute` still calls `update` afterwards. | Low (debug builds only). Static, not run live. |

### 7.3 Checked and found consistent (no finding)

- **Request shapes match the reference:**
  - **Danbooru:** posts and explore/popular (`date`, `scale`, `page`, `limit`; BS:packages/booru_clients/lib/src/danbooru/danbooru_client_explores.dart:12-31) and the `search[name_comma]` tag lookup (danbooru_client_tags.dart:13-35).
  - **e621:** the `/popular.json` `{posts}` unwrap (BS:packages/booru_clients/lib/src/e621/e621_client.dart:157-176), the comma-separated `search[name]` tag lookup (178-202), and comments with `group_by=comment` (109-128).
  - **Moebooru:** the `popular_by_day/week/month` parameters (BS:packages/booru_clients/lib/src/moebooru/moebooru_client.dart:111-168) and `comment.json?post_id` (170-184).
  - **Gelbooru:** `pid = page - 1` with `user_id`/`api_key` (BS:packages/booru_clients/lib/src/gelbooru/gelbooru_client.dart:98-110) and the gelbooru.com `names=` tag lookup (346-369).
  - **Rule34:** the API-host rewrite matches BS:packages/booru_clients/boorus.yaml:117.
- **No parser crash found:** every field access goes through `lib/utils/json.dart` `Json`. These shapes were checked:
  - e621 `{"posts":…}`, `{"tags":[]}` and `{"comments":[]}`;
  - a Gelbooru `@attributes` reply with no `post` key;
  - an empty body, `[]` or `{}` (booru_client.dart:364-367);
  - Moebooru's integer `created_at`;
  - Gelbooru's string booleans and a `parent_id` of 0 or `""`;
  - ids sent as numbers or as strings;
  - Danbooru ugoira, where the original is a `.zip` and the `.webm` sample is the one that plays (booru_models.dart:170-172).
- **e621 User-Agent:** sent on every API request (booru_client.dart:44-45, 334).
- **Release-mode crashes:** none found on any traced path. The only throwing paths are ST-24 (rare) and ST-25 (debug builds only). Both are fixed since: ST-24 in `0d10bd5d` (B26), ST-25 in `14851203` (B27).

### 7.4 What to confirm first on a device

Each of ST-03, ST-01/ST-02, ST-05, ST-06 and ST-08 can be confirmed or ruled out with one request and one screen once a device and network are available.
