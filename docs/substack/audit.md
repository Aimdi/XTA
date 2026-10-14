# Substack plugin audit — recon and baseline

Checkpoint for sections 1–2 of the Substack improvement brief. **No app code was
changed.** This pass adds this document, bug-reproducing probes, a host benchmark
and the device harness for S2/S3.

- Base commit: `082bb06` (Release aimdi177), branch `claude/admiring-mayer-bdfg95`.
- Date: 2026-10-13.
- Toolchain: Flutter 3.44.4 / Dart 3.12.2 through FVM 4.3.1.

## How to read this

Every claim carries one of these evidence tags:

| Tag | Meaning |
|---|---|
| **code** | Read in the file and line cited. |
| **probe** | Reproduced by `test/substack_audit_probes_test.dart`. The test passes against current code and pins the current (buggy) behaviour; a fix flips it on purpose. |
| **VM** | Measured by `test/perf/substack_vm_benchmark_test.dart` on the host VM. `flutter test` runs JIT with assertions on, so these numbers rank work and count bytes. They are not phone timings. |
| **CI** | GitHub Actions record. |
| **UNVERIFIED** | Needs a device, live Substack access or a recorded response. Nothing here was estimated to fill such a gap. |

## Limits of this pass

- **No device.** The container has no `/dev/kvm` and no phone attached, so every S1–S7 device metric is UNVERIFIED.
- **Substack is unreachable.** The session's egress policy answers 403 to CONNECT for `substack.com`, publication subdomains, custom domains and `substackcdn.com`. This was checked with `curl` and the proxy's failure log. As a result there was no live smoke test, no endpoint verification and no HAR. REA is not available.
- **No APK.** `dl.google.com` is denied, so the Android SDK could not be installed. The Flutter SDK came from the `storage.googleapis.com` archive (sha256 checked) and FVM from pub.dev.
- **The "Known issues" list in the brief is unfilled.** It still reads `<issue 1: …>`, `<issue 2>`, `<issue 3>`, so none could be reproduced. The bug list in §7 comes from code reading and probes.

## 0. Ground rules and conflicts

There is no `AGENTS.md`. It was folded into `CLAUDE.md` in `bdc3d85`, so `CLAUDE.md` is the governing file. Also read for this audit: `docs/perf-baseline.md`, the 11 `docs/specs/substack-*.md`, `docs/substack-declutter.md`, `docs/substack-reader-upgrade-validation.md`, `docs/grok-rewrite-plan.md`, `docs/specs/tweet-perf.md` and the `xta-performance-audit` skill.

These need a decision before the matching work starts (see §10):

1. **Scope change.** Earlier Substack specs ruled out what the new bar requires:
   - `docs/specs/substack-improvements.md`: "Explicitly out of scope — Pasting `connect.sid` / paid unlock; Writing notes, comments, reactions".
   - `docs/specs/substack-internal-substitutes.md`: "Out of scope — Compose, comment write, restack write; Login / paid unlock / push".

   The brief's §4 requires login, entitled paid content, reply/like, restack and sync. `CLAUDE.md` forbids writes only towards X. Substack writes are not forbidden, but they reverse those specs.
2. **"Share quote to X composes a post"** collides with a `CLAUDE.md` hard rule ("Never add compose / reply / quote / repost …" towards X), and `CLAUDE.md` overrides the brief. A system share sheet hand-off does not compose inside XTA. That is the most that fits the rule.
3. **There is no secure store in XTA.**
   - Every plugin credential (Threads, EH, TikTok and Instagram cookies; Reddit and Pixiv tokens) lives in app-private preferences listed in `secretPrefKeys` (`lib/constants.dart:837-863`). That keeps them out of JSON backups (`lib/settings/_data.dart:156`) and imports (`lib/utils/pref_lists.dart:54-57`).
   - `android:allowBackup="false"` (`android/app/src/main/AndroidManifest.xml:38`).
   - Nothing is encrypted at rest.
   - A Substack cookie key would not match the suffix rule in `isSecretPrefKey` (`lib/constants.dart:867-872`), so it would have to be listed explicitly.
4. **New dependencies would be needed.** New-post notifications need a background scheduler and a notification plugin; there are none in `pubspec.yaml`. Encrypted storage would need one too. OPML can use the existing `html` parser; `xml` is only a transitive dependency. Nothing was added.
5. **A native block reader replaces the WebView article renderer** (brief §3). That is a reader-module rewrite. `CLAUDE.md` requires a committed spec per module first and forbids big-bang rewrites.
6. **Licence.** XTA is MIT (`LICENSE`, © 2024 Harley Jones). The plugin files carry no upstream attribution, and the specs state that no external client code was copied (`docs/specs/substack-feed-recovery.md`). There is no extra attribution to preserve.

## 1. Module map

**Registration.**
- `SubstackPlugin` (`lib/plugins/substack/substack_plugin.dart:23-155`) is registered in `lib/plugins/plugin_registry.dart:31`, with id `substack` (`lib/constants.dart:224`).
- It provides:
  - its home screen (`:61-63`);
  - search (`:69-71`);
  - the subscription table `substack_subscription` (`:74-80`);
  - group interleaving (`:105-117`);
  - a backup section (`:120-127`);
  - a preference reset (`:130-138`).
- `main.dart:849-857` builds `SubstackClient()` with its own `http.Client`, plus the publications, read, likes and saved stores. They are provided at `main.dart:1089-1110` and preloaded at start-up when the plugin is on (`main.dart:957-961`).

**Directory.** `lib/plugins/substack/` holds 34 files and 10,096 lines (`wc -l`). It relies on shared code in:
- `lib/offline/`: `OfflineStore`, `OfflineArticle`;
- `lib/reading/`: `ArticleReadingStore`, the progress bridge, the reader controls;
- `lib/media/`: the podcast checkpoint, `ContinuityPlayer`, the audio session;
- `lib/speech/`: `SpeechStore`;
- `lib/plugins/plugin_links.dart`: link interception.

| Layer | Where |
|---|---|
| API (every endpoint, one 848-line class) | `substack_client.dart` |
| Parsing and host heuristics | `substack_models.dart` (1,169 lines: models, JSON/RSS/Beehiiv/Remix/JSON-LD parsers, host candidates), `substack_rss.dart` (regex RSS), `substack_html.dart` (sanitizer, speech text, page wrapper), `substack_article_navigation.dart` (contents/find index), `substack_links.dart` |
| Stores | `SubstackPublicationsStore` `substack_store.dart:21` · `SubstackReadStore` `:202` · `SubstackLikesStore` `:249` · `SubstackSavedStore` `:254` · `SubstackFeedStore` `:297` · `SubstackNotesStore` `:519` · `SubstackArchiveStore` `:644` (no references — dead code) · `SubstackAddPublicationStore` `:684` · `SubstackPublicationStore` / `SubstackSimilarStore` `substack_publication_store.dart:75/241` · `SubstackSearchStore` / `SubstackPreviewStore` `substack_search_store.dart:112/465` · `SubstackHomeControlsStore` `substack_home_controls.dart:25` · `SubstackReaderStore` / `SubstackArticleNavigationStore` `substack_reader_store.dart:35/98` · `SubstackCommentsStore` `substack_comments_store.dart:165` · `PodcastStore` `podcast_store.dart:36` · `_SearchHistoryStore` `substack_search_sheet.dart:403` |
| Screens | `SubstackScreen` (Home/Inbox/Notes/Library tabs, `substack_screen.dart:31`, tabs `:179-204`) · `SubstackReaderScreen` (WebView, `substack_reader_screen.dart:36`) · `SubstackCommentsScreen` (`substack_comments_screen.dart:13`) · `SubstackArchiveScreen` (publication page, `substack_archive_screen.dart:31`) · `SubstackSearchScreen` (discovery, `substack_search_sheet.dart:35`) · `SubstackAddScreen` (`substack_add_screen.dart:15`) · `SubstackNoteScreen` (`substack_note_screen.dart:9`) · similar sheet, article navigation sheet |
| Outside the tab | Group feeds (`substack_interleaved.dart:25-67`), home strip header (`lib/home/_feed.dart:612-613`), offline library (`lib/offline/offline_library_screen.dart:46-50`), X link interception (`lib/plugins/plugin_links.dart:208-243`) |

**Persistence.**
- Follows: SQLite `substack_subscription`, `id VARCHAR PRIMARY KEY` (`lib/database/repository.dart:587-590`).
- Pins, read ids, likes, saved posts, search history and the podcast checkpoint: preferences.
- Article bodies: FFCache.
- Retained articles: `OfflineStore` files.

**Caches and their bounds.**

| Cache | Where | Bound |
|---|---|---|
| Feed pages | `SubstackFeedStore._pages`, memory (`substack_store.dart:300`) | Fresh for 10 min (`kAccountPostsCacheTtl`, `lib/plugins/account_posts.dart:30`); not persisted |
| RSS pages | `SubstackClient._rssPages`, memory (`substack_client.dart:16-26`) | ≤64 publications × ≤120 posts |
| Article bodies | `SubstackArticleCache`, FFCache `substack_articles` in the OS temp dir (`substack_article_cache.dart:139-197`) | 14-day TTL, **no size bound** |
| Retained articles | `OfflineStore` (`lib/offline/offline_store.dart:21-26`) | 500 MB total, ≤200 items, ≤2 MB text and ≤24 MB images per article, ≤8 MB per image |
| Decoded images | Flutter image cache (`main.dart:464`) | 96 MB, app-wide |
| Article images and pages | Inside the Android WebView | Chromium's own |

## 2. Data flow per screen

There is no `compute` or `Isolate.run` anywhere in `lib/plugins/substack/` (`rg`), so all of this runs on the UI isolate:
- every response is decoded with `jsonDecode(response.body)` (`substack_client.dart:239, 255, 335, 466, 555, 595, 624, 650, 745, 764`);
- every HTML parse (`package:html`) happens there too.

| Screen | Flow |
|---|---|
| **Home / Inbox** | See the step list below the table. |
| **Reader** | `WebViewController` with JavaScript unrestricted and two channels (`substack_reader_screen.dart:78-93`) → post-frame `_load` → `_resolvePost` (`:139-152`), whose fallback order is listed below the table → `_showContent` (`:257-339`) → `loadHtmlString` (`:306`) → the progress bridge restores the reading position after `onPageFinished` (`:183-187`, `lib/reading/article_reading_bridge.dart:22-26, 60-63`). Rebuild scope: the Scaffold rebuilds on each `SubstackReaderStore.change`, and `_showContent` issues five in a row (`:268-272`). The reader does not listen to feed or settings stores. |
| **Comments** | One request: `/api/v1/post/{id}/comments?all_comments=true&sort=best_first` (`substack_client.dart:546-562`), with no paging and no cache. Then `jsonDecode` → `flattenSubstackComments` with a depth cap of 8 (`substack_models.dart:1129-1163`) → `state.rows` rebuilds outline, sort and search on every access (`substack_comments_store.dart:144`), so on every keystroke → `ListView.builder` of selectable rows (`substack_comments_screen.dart:111-123, 223-227`). |
| **Publication page** | `SubstackPublicationStore.refresh` (`fetchPosts`, limit 20) plus `enrich()` (a full `fetchPublication` probe) (`substack_archive_screen.dart:50-68`). Search is debounced 300 ms and calls `/api/v1/archive?search=` (`substack_publication_store.dart:96-108`). |
| **Notes** | `/api/v1/reader/feed` from a rotating followed host, cursor-bound to that host (`substack_store.dart:565-634`, `substack_client.dart:617-638`). This is the public discovery mix, not Following. |
| **Discovery** | Categories, category pages, publication search (`substack_search_store.dart:131-282`). Article search across followed publications runs four at a time (`:339-347`). |
| **Audio** | `SubstackAudioPlayer` → `PodcastStore` → MediaKit `ContinuityPlayer` and the audio_service session (`podcast_store.dart:96-173`). One checkpoint (URL and position) is kept in preference `plugin.substack.podcast.v1` (`lib/media/podcast_checkpoint.dart:5`). |
| **Offline** | Reader app-bar `OfflineArticleAction` (`substack_reader_screen.dart:430`) → `OfflineStore.keepArticle`, which writes `article.json` and downloads images **one at a time** (`offline_store.dart:242-267`). On open, `renderArticle` re-parses the page and inlines every image as a base64 data URI (`offline_store.dart:369-404`). |
| **Group feeds** | `loadSubstackInterleaved` fetches every member publication again; it shares no cache with `SubstackFeedStore` (`substack_interleaved.dart:35-43`). |

**Home / Inbox flow.**

1. Before `runApp`, start-up loads the four Substack stores in parallel (`main.dart:953-961`, `runApp` at `:1019`). In a post-frame callback the screen reloads any store that is still empty, one after another (`substack_screen.dart:56-77`).
2. `SubstackFeedStore.refresh(force: true)` fetches the first 24 publications, three at a time (`substack_store.dart:398-403`).
3. Each publication is read with `fetchPosts(limit: 8)` (`:466`). That tries, in order, until one answers (`substack_client.dart:105-171`):
   1. `/api/v1/posts`;
   2. `/api/v1/archive`;
   3. Beehiiv `/posts`;
   4. RSS;
   5. leftover twin hosts.
4. Bodies are dropped while parsing (`includeBody: false`).
5. After **each** publication, `_publish()` merges, sorts and filters every loaded post (`substack_store.dart:495-508`).
6. The UI is a nested `ScopedBuilder` (feed, then read state) → `filterSubstackLoaded` → `FeedListView` of `SubstackPostCard` (`substack_screen.dart:345-441`).
7. Covers and logos are decoded at display size (`cacheWidth`) but downloaded at source size (`substack_post_card.dart:179-185, 323-327`).

Nothing is persisted, so a cold start waits on the network.

**Reader fallback order (`_resolvePost`).** The first source that has a body wins:

1. the retained offline copy, matched by id, then by canonical URL;
2. the article cache;
3. a body the post already carries (RSS items carry bodies);
4. `/api/v1/posts/{slug}`;
5. a Beehiiv page;
6. a matching RSS item;
7. leftover twin hosts.

**What `_showContent` does to one body, synchronously, before its first await (`:257-339`).**

- `buildSubstackSpeakText`: sanitizes, then parses again.
- `SubstackArticleDocument.parse`: sanitizes, then parses again.
- `wrapSubstackHtml`: sanitizes again.

That is five full `package:html` parses of the same body on the UI isolate. If the post is retained offline, `OfflineStore.renderArticle` adds a sixth parse plus the base64 inlining.

If there is no body but there is a canonical URL, the reader loads the live site in the WebView with JavaScript (`:311-317`).

## 3. Endpoints

**Request defaults** (`substack_client.dart:15, 30-31, 776-784`):
- headers: `Accept: application/json` and a mobile-Chrome User-Agent;
- timeout: 20 s;
- no cookies, no retry, no backoff;
- every non-200 response becomes an exception.

**Last verified.** No test in the repo or CI calls Substack's own endpoints. The daily `api-canary` job runs only `test/live/substack_custom_domain_smoke_test.dart`, against `www.garbageday.email`, which the test itself describes as a Beehiiv site. It passed on 2026-10-13 (CI: run 38399803814, job 115304193269), so it verifies the Beehiiv fallback and nothing else. Aimdi/XTA#280 ("Live checks failed: Substack") is still open but stale: it is a runner sqlite3 hash failure from 2026-09-23, not a Substack change. The client file first appears in the squashed import `b195e1a` (2026-09-24), so git holds no per-endpoint verification dates.

| # | Endpoint | Used by | Auth | Pagination | Last verified |
|---|---|---|---|---|---|
| 1 | `GET {pub}/api/v1/posts?limit&offset` | first listing choice (`:323-325`) | none | offset/limit (feed 8, publication page 20) | UNVERIFIED |
| 2 | `GET {pub}/api/v1/archive?sort=new&limit&offset` | listing fallback (`:327-329`) | none | offset/limit | UNVERIFIED |
| 3 | `GET {pub}/api/v1/archive?sort=new&search=&limit&offset` | archive search (`:583-611`) | none | offset/limit ≤100 | UNVERIFIED |
| 4 | `GET {pub}/api/v1/posts/{slug}` | article body (`:236-249`) | none (full paid body only for an entitled session — UNVERIFIED) | — | UNVERIFIED |
| 5 | `GET {pub}/api/v1/post/{id}/comments?all_comments=true&sort=best_first` | comments (`:546-562`) | none | none; whether large threads are truncated is UNVERIFIED | UNVERIFIED |
| 6 | `GET {host}/api/v1/reader/feed?limit=20&cursor=` | public Notes (`:617-638`) | none | cursor (`nextCursor`) | UNVERIFIED |
| 7 | `GET substack.com/api/v1/publication/search?query&page` | discovery (`:644-660`) | none | page | UNVERIFIED |
| 8 | `GET substack.com/api/v1/categories` | discovery (`:742-754`) | none | — | UNVERIFIED |
| 9 | `GET substack.com/api/v1/category/public/{id}/{tier}?page` | discovery (`:757-774`) | none | page | UNVERIFIED |
| 10 | `GET substack.com/api/v1/user/{handle}/public_profile` | `@handle` resolution (`:251-262`) | none | — | UNVERIFIED |
| 11 | `GET {pub}/recommendations` (HTML `window._preloads`) | similar publications (`:663-681`) | none | — | UNVERIFIED |
| 12 | `GET {pub}/feed`, `/rss`, `/feed.xml` | RSS fallback and identification (`:522-542`) | none | whole feed; client keeps ≤120 items | UNVERIFIED |
| 13 | `GET {pub}/` (OG tags / Beehiiv Remix) | identification fallback (`:379-391`) | none | — | UNVERIFIED |
| 14 | `GET {pub}/posts?page=N` (Beehiiv JSON) | non-Substack custom domains (`:457-481`) | none | page | CI 2026-10-13 (indirect) |
| 15 | `GET {pub}/p/{slug}` (Beehiiv HTML) | non-Substack post body (`:438-455`) | none | — | UNVERIFIED |
| 16 | redirect follow for `open.substack.com` / `substack.com` input | add-publication (`:298-318`) | none | — | UNVERIFIED |
| 17 | live `{canonical}` page in the WebView | reader without a body (`substack_reader_screen.dart:245-255, 311-317`) | browser cookies, if any | — | UNVERIFIED |

**Not mapped; each needs a HAR.**
- the signed-in subscription list;
- the subscriptions inbox;
- Following Notes;
- reading-progress sync;
- like, restack and comment writes;
- a post-by-id lookup for `substack.com/home/post/p-{id}` links;
- a session or entitlement check;
- the `substackcdn.com/image/fetch` transform grammar (only seen in reconstructed markup here).

## 4. HTML coverage

The article body is rendered by Android WebView after `sanitizeSubstackBodyHtml` (`substack_html.dart:41-81`) and the page CSS (`:222-400`).

**Evidence is limited.** It is the sanitizer's output on **reconstructed** Substack markup: node and class names follow Substack's editor output but were not recorded from a live response. Each row is UNVERIFIED until a recorded body replaces the fixture. Rendering quality inside the WebView is UNVERIFIED too: there is no device.

| Construct | Status | Evidence |
|---|---|---|
| Images with captions | OK | `figure`, `picture`, `srcset`, `width`/`height` and `figcaption` survive. CSS caps width (`:327-344`). Speech reads the caption. |
| Image tap → viewer with pinch zoom | Missing | The tap follows `a.image-link` → `_onNavigation` → `openLink` → browser (`substack_reader_screen.dart:135`). Only page zoom up to 3× (`substack_html.dart:254`). |
| Galleries | Missing | The empty `div.image-gallery-embed` is removed (`:70-74`); probe BUG-08. |
| Footnotes | OK as a jump; bottom sheet Missing | Anchors survive and same-article fragments jump in place (`substack_reader_screen.dart:122-126`, `substack_article_navigation.dart:89-106`). Speech reads the marker into the sentence ("Claim1."). |
| Embedded posts | Partial | The static link block survives unstyled. A tap opens the in-app reader for `*.substack.com` or followed domains (`substack_reader_screen.dart:127-133`). |
| Tweets | Partial | Name, handle and text survive unstyled; photos are not shown. A tap reaches XTA's own status screen (`plugin_links.dart` `_openX`). |
| YouTube | Partial | A live `youtube-nocookie.com` iframe survives and loads lazily inside the article. There is no click-to-load placeholder, so third-party content loads without a tap. |
| Audio (native audio embed) | Missing | The empty placeholder is removed. `AudioPlayer` components are removed outright (`:59`). |
| LaTeX | Missing | The empty `div.latex-rendered` is removed, and so is any `math` element (`:52`). |
| Code blocks | OK | `pre`/`code` styled, horizontal scroll (`:345-356`); no syntax colour. |
| Pull quotes | Partial | Rendered as a plain paragraph. |
| Buttons / CTAs | Partial | `a.button` survives as a plain link to the subscribe page. |
| Subscribe widget | Broken | A live email form survives in the article (probe BUG-07). The selectors at `:59-62` match neither `SubscribeWidgetToDOM` nor `subscription-widget-wrap-editor`. |
| Polls | Missing | The empty placeholder is removed (probe BUG-08). |
| Paywall marker | OK | The marker is removed (`.paywall`, `:63`). Paid is decided from `audience` (`substack_models.dart:124-127`), and a "preview ends" footer links to the site (`substack_reader_screen.dart:296-298`). |
| Tables | OK | `display:block` with horizontal scroll (`:357-370`). |

## 5. Feature matrix against the bar

### Must-haves

| Bar item | Status | Evidence |
|---|---|---|
| Follow without an account (archive API, RSS fallback) | Present | `substack_client.dart:105-171`, `substack_store.dart:110-115`. BUG-02, BUG-03, BUG-04 and BUG-06 in the fallback chain are fixed. |
| Import subscriptions with an account | Missing | No authentication of any kind; the client is "read-only … public" (`substack_client.dart:9-10`). |
| OPML import/export | Missing | No `opml` anywhere in `lib/`. |
| Unified inbox + per-publication pages | Present | Home/Inbox (`substack_screen.dart:179-204, 452-573`) and the publication page (`substack_archive_screen.dart`). A refresh reaches every followed publication (BUG-05 fixed in the Tier 1 batch). |
| Unread state; marked read when scrolled past | Partial | Read ids are kept (`substack_store.dart:202-228`, cap 400 at `constants.dart:246`). They are set only by finishing in the reader (`substack_reader_screen.dart:69`), the card action (`substack_post_card.dart:278-294`) or mark-all (`substack_screen.dart:120-125`). Nothing marks a post read when it scrolls past. |
| Filters: unread, saved, podcasts, paid | Partial | `SubstackFeedFilter { all, unread, free, podcast, video }` (`substack_models.dart:295`). There is "free" but no "paid only", and Saved is a Library section rather than a filter. |
| Typography: font, size, line height, column width | Partial | Size 16–28 and line height 1.4–2.2 (`article_reading_store.dart:123-124`). The font is fixed to Georgia (`substack_html.dart:266`) and the column to 42rem (`:276`). |
| Themes incl. true black | Present (UNVERIFIED render) | Reader colours come from the app's `ColorScheme` (`substack_reader_screen.dart:278-289`); true black is an app theme (`main.dart:733`). |
| Footnotes in bottom sheets | Missing | In-page jump only (see §4). |
| Image viewer with pinch zoom | Missing | See §4. |
| Reading progress saved/restored per post | Present | Journal keyed by publication and slug (`substack_reader_store.dart:107-108`, `article_reading_store.dart`), restored by paragraph and offset (`article_reading_bridge.dart:22-26, 60-63`). |
| Estimated read time | Missing | No word count or read-time code anywhere. |
| Paid: full content when entitled | Missing | No session. |
| Paid: otherwise preview + paid badge | Present | Badge (`substack_post_card.dart:218`), preview footer on a free opening (`substack_reader_screen.dart`, `_showArticle`), and the paid pane (lock, excerpt, "Open in browser") for a paid post without a body, chosen by `substackReaderBodyFor` (`substack_reader_store.dart`). BUG-11 fixed in the Tier 1 batch. |
| Expired session → prompt re-login | Missing | No session. |
| Comments threaded | Present | `substack_comments_store.dart:42-125`. Replies nested more than nine levels deep are dropped (BUG-01). |
| Comments sortable (best/new) | Partial | Original order (the API's `best_first`), newest and oldest (`substack_comments_store.dart:5`); there is no server-side "new". |
| Comment reply and like when logged in | Missing | No session or write path. |
| Likes / restacks / share link or quote / save | Partial | Local likes and saves (`substack_store.dart:248-257`), link share (`substack_reader_screen.dart:410-414`). There are no restacks and no quote share. Text selection in the article is suppressed (BUG-12). Nothing syncs. |
| Audio: episodes via XTA media | Present | `PodcastStore` → MediaKit and the audio_service session (`podcast_store.dart:61, 166-173`). Background playback rests on that session (UNVERIFIED on device). |
| Audio: speed control | Missing | `ContinuityPlayer.setRate` already exists (`lib/media/continuity_player.dart:34, 98`), but `PodcastStore` never calls it. |
| Audio: resume position | Partial | One checkpoint for the last episode only (`podcast_checkpoint.dart`). Opening another episode starts it at zero and drops the first one's place (`podcast_store.dart:103`). |
| Audio: voiceovers | Missing | `audio_items` is not parsed, and the audio embed is removed (§4). |
| Search within a publication's archive | Present | `substack_publication_store.dart:96-108` → endpoint 3. |
| Local search across saved and downloaded posts | Partial | Library matches title, publication and excerpt of followed, saved and liked posts (`substack_screen.dart:613-625`). There is no search over retained article bodies, and none in the offline library screen. |
| Offline: auto-download on Wi-Fi | Missing | No background work or connectivity checks. |
| Offline: manual download per post | Present | `substack_reader_screen.dart:430`, `offline_store.dart:163-170`. |
| New-post notifications | Missing | No scheduler or notification plugin in `pubspec.yaml`. |
| Backup/restore as JSON | Partial | Follows are backup rows (`substack_plugin.dart:120-127`). Saved, liked, read, the podcast checkpoint and the reading journal ride along as preferences in `settings` (`lib/settings/_data.dart:156`). Retained bodies are not included; highlights do not exist. |

### Differentiators

| Item | Status | Evidence |
|---|---|---|
| Substack links in X posts open natively | Partial | Covers `*.substack.com/p/…`, `open.substack.com/pub/…/p/…` and custom domains already followed (`substack_links.dart:47-100`, `plugin_links.dart:208-243`). Unknown custom domains go to the browser. `substack.com/home/post/p-…` app links are not recognised (BUG-10). |
| Share quote to X | Missing (conflicts with CLAUDE.md, §0) | — |
| Highlights and margin notes, Markdown export | Missing | Text selection is suppressed in the article (BUG-12). |
| TTS for posts without audio, with sentence highlighting | Partial | Read aloud and read-from-here exist (`substack_reader_screen.dart:345-383`, `substack_html.dart:167-220`); there is no sentence highlighting. |
| Read-only Notes timeline | Partial | Public discovery feed only (endpoint 6). |

## 6. Tests

**Inventory (measured).** 25 Substack-related files: 20 `test/substack_*.dart` files plus `offline_library_test`, `article_reading_test`, `podcast_continuity_test`, `plugin_links_test` and `shared_links_test`. **301 tests passed in 1 min 47 s** on this VM. This pass adds 10 probes (passing) and two opt-in harnesses.

**After the Tier 1 batch:** the same files hold 337 tests (23 `test/substack_*.dart` files with 279, plus 58 in the five others), and the full suite passed: 4,895 tests, 10 skipped, 0 failed, in 7 min 52 s.

**After batch 2:** 350 tests (24 `test/substack_*.dart` files with 292, plus the same 58), and the full suite passed: 4,908 tests, 10 skipped, 0 failed, in 8 min 59 s.

| Gate in the brief (§5) | Today | Gap |
|---|---|---|
| HTML-to-block parser for every §4 construct | No block parser exists. Sanitizer tests cover scripts, handlers and URLs (`substack_sanitize_test.dart`, 7 tests). | No recorded Substack constructs; the probes use reconstructed markup. |
| Paywall detection | `substack_preview_test.dart` (9) | No recorded paid payload; `_PaywallPane` itself is not pumped (no WebView fake), its selection is (`substackReaderBodyFor`). |
| Offset pagination dedupe | `substack_store_recovery_test.dart` (23), `substack_publication_reader_test.dart` (19) | — |
| API + RSS merge | `substack_post_identity_test.dart` (9), the BUG-02 regression probe | Keys come from canonical URLs; no recorded RSS feed. |
| Follow identity | `substack_follow_identity_test.dart` (9): the publication page, two `newsletter.*` follows, following again, one publication on two hosts | Two apex sites that share a label still count as one follow (BUG-06 trade-off). |
| Cookie host scoping | N/A (no cookies) | Needed before any authentication lands. |
| OPML round trip | N/A | — |
| Rate limiter and backoff | `substack_transport_test.dart` (8), the BUG-03 regression probe | Fake clock; no live 429 recorded. |
| Reader rendering and progress restore (widget) | Store and bridge level only (`article_reading_test.dart`, 11) | The WebView screen itself is not widget-tested. |
| Comment threading (widget) | `substack_discussion_reader_test.dart` (15) | Depth-cap behaviour is pinned as intended (`substack_features_test.dart:90-101`); see BUG-01. |
| Paid preview state (widget) | Footer HTML and the reader-body decision covered | The pane widget is not pumped: the reader screen needs a WebView platform fake no test has yet. |
| Live endpoints | Beehiiv custom domain only, via CI canary | No live test for any Substack endpoint. |
| Performance | none before this pass | `integration_test/substack_perf_test.dart` (S2/S3), `test/perf/substack_vm_benchmark_test.dart` |

## 7. Smoke test and bug list

**What could not run.** The per-screen smoke test against five live publications could not be done: there is no device and Substack egress is denied. That covers login, feed, publication page, post, comments, search, audio and offline across the five publication types (free, paid with account, paid without account, custom domain, podcast-heavy). All of it is UNVERIFIED.

**What ran instead:**
- the 301 existing tests;
- the 10 probes, each reproducing one bug below;
- the S3 harness, on the headless `flutter-tester` device with synthetic data (it completed; S2 skipped because there is no WebView there).

Columns: **T** is the priority tier, **Sev** is severity, and the last column names the evidence.

| ID | T | Sev | What happens | Steps / publication class | Suspected place | Evidence |
|---|---|---|---|---|---|---|
| BUG-01 | 1 | Med | Replies nested deeper than 9 levels are dropped. 13 sent, 9 shown. | Open comments on any post with a long back-and-forth thread. | `substack_models.dart:1129, 1156` (`maxDepth: 8` stops the walk) | probe. An existing test pins it as intended (`substack_features_test.dart:90-101`): its title says "stops indenting", its assertion drops replies. The UI clamps indentation at 2 anyway (`substack_comments_screen.dart:195`). **Your call.** |
| BUG-02 | 1 | High | The same post read via RSS and via the API gets two identities: slug versus numeric id. Read, like, save and offline state split, and the feed cannot dedupe the two. A post read during an outage reads as unread once the API is back, and the reverse. | Any publication whose JSON listing fails or is rate-limited, so the RSS fallback runs. | `substack_rss.dart:90` (`id: slug`) vs `substack_models.dart:161`; keys `substack_store.dart:287` | probe. **Fixed** (batch 2, `docs/specs/substack-post-identity.md`): one key per post from its canonical URL, used by read state, likes, saves, both feeds' dedupe; an RSS copy fetches its numeric id before loading comments. No migration: stored ids still match. A read stored through RSS before the fix (a bare slug) still marks only the RSS copy |
| BUG-03 | 1 | High | A 429 is not handled. The client falls through to other endpoints on the same host: **12 requests** for a `*.substack.com` publication and **22** for a custom domain. The error then says "This is not a Substack publication". There is no backoff. | Rate-limit any followed publication; a 24-publication refresh can send 288+ requests. | `substack_client.dart:105-171, 776-784` | probe, plus the measured counts. **Fixed** (Tier 1 batch): `substack_transport.dart`; the flipped probe is the regression test |
| BUG-04 | 1 | High | When a custom domain is unreachable, posts, bodies, comments and search come from `{first-label}.substack.com`, which can be an unrelated publication. They are shown under the followed publication's name. | Follow `newsletter.example.com`; make it fail. | `substack_models.dart:1043-1070` (twin from `registrableLabel`), used at `substack_client.dart:158-166, 196-209` | probe. **Fixed** (Tier 1 batch): a follow with a stored subdomain asks only `{subdomain}.substack.com`, and a twin naming another custom domain or subdomain is rejected on every read path and in `enrich()`; two flipped probes are the regression tests. A host deeper than an apex domain never guesses its first label as a twin |
| BUG-05 | 1 | Med-High | A refresh reads at most 24 publications. With 30 follows, 6 publications are missing from Home and Inbox until "Load more" is tapped. | Follow 30; pull to refresh. | `substack_store.dart:259, 400` | probe. **Fixed** (Tier 1 batch): a refresh reads every publication; load-more passes stay at 24 |
| BUG-06 | 1 | Med | Custom domains resolved through RSS or the homepage take their **first host label** as id. Following `newsletter.a.com` and then `newsletter.b.com` silently replaces the first follow, because the primary key is `id` and `_write` uses `ConflictAlgorithm.replace`. | Add two `newsletter.*` domains while their JSON fails. | `substack_models.dart:21, 1089-1097`, `substack_client.dart:368-377`, `substack_store.dart:101-108` | probe (equal ids) + schema. **Fixed** (Tier 1 batch): `add` places a follow over the follow it already is, or under its host when another publication holds its id (`substack_follow_identity_test.dart`); a deep host found without JSON is followed under its host (`substackGuessedId`; the flipped probe). Apex domains keep their first label, so two apex sites sharing a label still count as one follow |
| BUG-07 | 1 | Med | The subscribe widget renders as a live email form inside articles. | Any post with an inline subscribe widget. | `substack_html.dart:59-62` | probe (reconstructed markup) |
| BUG-08 | 1 | Med-High | LaTeX, galleries, polls and native audio embeds vanish without a trace. | Maths or photo-essay publications. | `substack_html.dart:52, 70-74` | probe (reconstructed markup) |
| BUG-09 | 1 | Low | The article cache drops the video flag. It also has no size bound and is never invalidated when a post is edited. | Open a video post, go back, open it again. | `substack_article_cache.dart:170-197` | probe. **Fixed**: the video flag (Tier 1 batch; the flipped probe), then a bound of 200 articles and 16 MiB, least recently read dropped first (a 201-article test fails on the old cache), and a cached copy is skipped when the listed post's date or title changed. A body-only edit still waits for a manual refresh or the 14-day expiry |
| BUG-10 | 1 | Med | `substack.com/home/post/p-{id}` app share links are not read in-app. | Tap such a link in an X post. | `substack_links.dart:47-100` | probe; the link shape is UNVERIFIED |
| BUG-11 | 1 | Med | A paid post without a body loads the publisher's live site with JavaScript instead of the preview pane and paid badge. `_PaywallPane` is reachable only when there is no canonical URL. | A paid post the API sends without `body_html`. | `substack_reader_screen.dart:311-329` | code. **Fixed** (Tier 1 batch): `substackReaderBodyFor` picks the paid pane before the live site; `substack_preview_test.dart` fails on the old order and passes on the new |
| BUG-12 | 1 | Med | Long-press is taken for read-aloud: `contextmenu` is cancelled and the touch callout is disabled. Text selection and copy in articles are blocked, and so are quoting and highlights. | Long-press a paragraph. | `substack_html.dart:208-218, 385` | code; UNVERIFIED on device |
| BUG-13 | 2 | Med | Feed covers and logos download the source image; only decoding is capped. | Any image-heavy feed. | `substack_post_card.dart:179-185, 323-327` | code; bytes UNVERIFIED (S6) |
| BUG-14 | 1 | Low | Speech reads footnote markers into sentences ("Claim1."). | A post with footnotes. | `substack_html.dart:84-138` | sanitizer output. **Fixed** (Tier 1 batch): the spoken text and the long-press "read from here" text both skip `footnote-anchor` and `footnote-number`. The Dart side is unit-tested and fails before the fix; the page script was checked in headless Chromium (Playwright 1.56.1), not on an Android WebView |
| BUG-16 | 1 | High | Opening a publication page from a post can silently rewrite another follow. The page starts from the post host's first label as id. When another follow holds that id, the page shows "Unfollow" and pin for the wrong publication, and its metadata write-back replaces that follow's row with the visited publication. | Follow `newsletter.substack.com`; tap the publication name on a `newsletter.pragmaticengineer.com` post. | `substack_archive_screen.dart:16-25, 56-68, 342-396` (id-only checks), `substack_models.dart:208-212` | widget test `substack_follow_identity_test.dart`, found while fixing BUG-06. **Fixed** (Tier 1 batch): one identity rule, `substackFollowOf` (same host, or equal ids that are not guesses), behind every follow, pin and write-back check; the widget test fails before and passes after |
| BUG-15 | 1 | Low | Beehiiv JSON-LD parsing uses unchecked `as String?` casts, against the null-safe parsing rule. A malformed field silently drops that fallback. | A Beehiiv page with an unusual JSON-LD shape. | `substack_models.dart:809-832` | code. **Fixed** (Tier 1 batch): `_postFromJsonLd` reads every field through `Json`; a list headline falls through to the OG tags. The test threw a `TypeError` before the fix |

**Housekeeping, not bugs:**
- `SubstackArchiveStore` is dead code (`substack_store.dart:644-682`);
- canary issue Aimdi/XTA#280 is stale;
- the canary covers no Substack endpoint.

## 8. Baseline

**Environment.**
- Base commit `082bb06`, plus this branch's test-only files.
- Host: Linux VM, 4 vCPU, 15 GB RAM, no device.
- Toolchain: Flutter 3.44.4 / Dart 3.12.2, run through `fvm flutter test` (JIT).
- Network to Substack: denied.

### Device metrics (S1–S7)

| Scenario | Metric | Median | p95 | Target (brief default) |
|---|---|---|---|---|
| S1 cold start → first Substack items | ms | UNVERIFIED — no device | UNVERIFIED | — |
| S2 long post, first paragraph | ms, cached / uncached | UNVERIFIED | UNVERIFIED | ≤300 ms / ≤1.5 s on Wi-Fi |
| S2 long-post fling | janky %, build p95, raster p95 | UNVERIFIED | UNVERIFIED | <1 % janky; p95 under 1000/Hz ms |
| S3 300+ comments, first comment and scroll | ms; janky %, p95 | UNVERIFIED | UNVERIFIED | same |
| S4 refresh of 30 publications | total s, requests, 429s | UNVERIFIED | — | ≤5 s on Wi-Fi, zero 429s |
| S5 ten long posts, then trim memory | MB before/after | UNVERIFIED | — | no monotonic growth |
| S6 bytes for the long post | requested vs original | UNVERIFIED | — | — |
| S7 offline saved posts | ms to readable; missing images | UNVERIFIED | — | zero missing |

**How to fill this table on a phone** (S2/S3 scripted, three trials after a warm-up):

```bash
fvm flutter drive --profile \
  --driver=test_driver/substack_perf_test.dart \
  --target=integration_test/substack_perf_test.dart \
  --dart-define=XTA_COMMIT=$(git rev-parse --short HEAD) --dart-define=XTA_NETWORK=wifi \
  --dart-define=SUBSTACK_PERF_POST=https://… \
  --dart-define=SUBSTACK_PERF_COMMENTS=https://…
```

The driver writes `build/substack_perf_summary.json`. For each trial it records frames, build p50/p95, raster p50/p95 and janky % against `1000 / refresh rate`, then takes medians across the three trials. It also records commit, device model, Android version, refresh rate and network.

Three blind spots to know before reading those numbers:

- `watchPerformance` sees only Flutter frames. The article is a WebView, whose scroll jank happens in Chromium, so the harness also records in-page `requestAnimationFrame` gaps. A native block reader would be fully visible to `watchPerformance`.
- If a synthesized touch fling does not reach the platform view, the harness scrolls with JavaScript and says so in `scroll_method`.
- Without the two URLs the harness uses synthetic content (`test/fixtures/substack_synthetic.dart`). That checks the harness and the CPU side only.

S1, S4, S5, S6 and S7 still need a manual protocol on the device; see `docs/perf-baseline.md`.

### Host VM measurements (VM)

Run with `fvm flutter test test/perf/substack_vm_benchmark_test.dart --dart-define=RUN_BENCH=true`. Each timing is median / p95 of 15 trials after 3 warm-ups, except S4 (10 after 2) and the S7 render (5 after 1).

| Scenario | What was timed | Result |
|---|---|---|
| S2 (synthetic: 9,223 words, 24 images, 111,930-byte body) | decode post JSON | 5.7 / 7.3 ms |
| | one HTML parse | 14.5 / 36.4 ms |
| | contents index (`SubstackArticleDocument.parse`) | 31.2 / 43.6 ms |
| | page wrapper | 11.5 / 14.3 ms |
| | speech text | 31.0 / 39.2 ms |
| | **whole open pipeline, before the first await** | **92.9 / 119.6 ms ≈ 6.4 × one parse** |
| S3 (synthetic; 300 comments = 128 KB, 1,000 = 428 KB) | decode JSON | 300: 0.8 / 1.4 ms · 1,000: 2.3 / 6.1 ms |
| | flatten | 300: 2.6 / 3.9 ms · 1,000: 8.4 / 10.1 ms |
| | rows, original order | 300: 2.1 / 4.2 ms · 1,000: 2.9 / 3.2 ms |
| | rows per search keystroke | 300: 1.9 / 4.0 ms · 1,000: 3.7 / 6.1 ms |
| S4 (instant fake network) | one refresh of 30 publications | **147.6 / 165.8 ms of CPU** across 27 state publishes; only 24 publications fetched (BUG-05) |
| S7 (24 retained images at an assumed 256,000 bytes each) | page size, online → offline | 115,476 → **8,274,517 bytes (×72)** |
| | `renderArticle` | **276 / 530 ms** before the WebView receives the page |

The S3 rows are host CPU only, and they show S3's cost is not in parsing.

### Static diagnosis per scenario

- **S1.** Start-up awaits the four Substack store loads, in parallel, before `runApp` (`main.dart:953-961`, `:1019`). Nothing is persisted, so the first item then waits for:
  1. any store reloads the screen still runs, one after another (`substack_screen.dart:63-69`);
  2. the slowest of the first three publication requests;
  3. their fallback chains.

  Threads already paints a persisted snapshot before any request (`lib/plugins/threads/threads_store.dart:148-165`, pref `plugin.threads.feed_snapshot`); Substack does not.
- **S2.**
  - Five synchronous parses on the UI isolate per open (VM row above) are the likeliest cause of a long frame at open.
  - The WebView also creates a renderer per reader route.
  - The in-repo precedent for the target design is the Pixiv novel reader:
    - `compute` above a size threshold (`lib/plugins/pixiv/pixiv_novel_parser.dart:11`);
    - `SliverList.builder` inside `SelectionArea` (`pixiv_novel_reader_screen.dart:335, 368`);
    - block-index restore (`pixiv_novel_reader_position.dart:63-75`).
- **S3.**
  - One unpaged request and selectable text in every row.
  - Host CPU is small (VM row), so frame cost on a device is the open question.
  - Whether `all_comments=true` returns every comment of a 300+ thread is UNVERIFIED. The harness records the count it received.
- **S4.**
  - BUG-05 caps a pass at 24 publications.
  - BUG-03 multiplies requests under 429.
  - Every refresh refetches the first page of every publication, with no "since last seen".
  - Group feeds refetch the same publications (`substack_interleaved.dart:35-43`).
- **S5.** Each reader route owns a WebView. Following article links pushes reader on reader (`substack_reader_screen.dart:128-131`), so a chain of ten link hops keeps ten WebViews alive. The only memory-pressure observer releases video players (`lib/tweet/video_memory_observer.dart:10`).
- **S6.** Covers and logos are fetched at source size (BUG-13). Body images use the CDN `srcset` already, which the WebView picks by viewport.
- **S7.**
  - Saved (bookmarked) posts carry no body (`substack_models.dart:214`). They are readable offline only if separately retained or still in the 14-day cache.
  - Retained posts inline every image as base64 (VM row).
  - Images are retained one at a time with a 20 s timeout each (`offline_store.dart:250-266`, `offline_transfer.dart:40, 50`).

## 9. Plan, ranked by impact × effort × risk

Each item is scored on three 1–5 scales:

- **I:** user impact.
- **E:** ease (5 = smallest change).
- **S:** safety (5 = least risk).

Rank = I × E × S. The brief's tier order still governs; ranks order work within a tier. The rules for every item:

- one hypothesis per commit;
- a probe or characterization test first;
- the probe flips into the regression test.

### Tier 1 — known issues and bugs

Order is by score, except where a dependency forces a change; the last column says when. ✅ marks an item done in the Tier 1 batch.

| Order | Item | I | E | S | Score | Why here |
|---|---|---|---|---|---|---|
| 1 ✅ | **BUG-03** transport layer: classify 429/5xx, per-host limiter, exponential backoff with jitter, stop the fallback cascade on 429, honest "rate limited" error | 5 | 3 | 4 | 60 | Ahead of BUG-05: lifting the 24-publication cap without a limiter would amplify 429s. First step of splitting the API layer (`substack_transport.dart`); later it also scopes cookies to hosts. |
| 2 ✅ | **BUG-05** reach every followed publication on refresh, keeping concurrency bounded | 4 | 4 | 4 | 64 | Needs #1. |
| 3 ✅ | **BUG-04** use a `{label}.substack.com` twin only when it is proven to be the same publication | 4 | 4 | 4 | 64 | — |
| 4 ✅ | **BUG-11** paid post without a body → preview and paid badge, not the live site | 3 | 5 | 4 | 60 | — |
| 5 | **BUG-01** keep deep replies at the cap depth instead of dropping them | 2 | 5 | 5 | 50 | Flips an existing test, so it waits for your call. |
| 6 ✅ | **BUG-09** article cache keeps the video flag; bounded size; invalidated by post date | 2 | 5 | 5 | 50 | Invalidated by date or title; a body-only edit shows only on refresh or expiry. |
| 7 ✅ | **BUG-02** one canonical identity (host + slug) for read/like/save/offline and for API ↔ RSS dedupe, still honouring existing numeric ids | 4 | 3 | 3 | 36 | Preference-level only; no schema change. High severity, but riskier: it touches stored state. |
| 8 ✅ | **BUG-06** host-based ids for follows resolved without JSON; existing rows untouched | 3 | 4 | 3 | 36 | Done with BUG-16, the same overwrite reached by browsing: one identity rule for every follow check. |
| 9 | **BUG-12** allow text selection; move read-from-here off long-press | 3 | 4 | 3 | 36 | Superseded if the native reader lands. |
| 10 ✅ | **BUG-14 / BUG-15** footnote markers in speech; checked JSON-LD casts | 1 | 5 | 5 | 25 | — |
| blocked | **BUG-07 / BUG-08** sanitizer: drop subscribe forms; turn galleries into figures; show LaTeX source as math text; placeholder with a link for polls and audio | 4 | 4 | 4 | 64 | Needs recorded bodies (HAR); fixtures must not be invented. |
| blocked | **BUG-10** app share links | 3 | 3 | 3 | 27 | Needs a HAR confirming a post-by-id endpoint. |

### Tier 2 — performance (after Tier 1, each item gated by its device scenario)

| Rank | Item | I | E | S | Score | Scenario |
|---|---|---|---|---|---|---|
| 1 ✅ (no result cache) | Parse each body once, off the UI isolate, and cache the result by post id + `post_date` (on the existing WebView path) | 4 | 4 | 4 | 64 | S2. One parse, off the UI isolate above 20,000 characters; open pipeline −39 % CPU on the host. The parsed result is not cached between opens. |
| 2 | Persisted feed snapshot on the Threads pattern; refresh only stale publications | 4 | 4 | 4 | 64 | S1, S4 |
| 3 ✅ | Feed publish cost: parse dates and keys once per post, coalesce publishes per frame | 3 | 4 | 4 | 48 | S4. Keys and dates once per post: refresh CPU −88 % on the host. Coalescing not needed after that. |
| 4 | Offline render without base64 inlining | 4 | 3 | 3 | 36 | S7 |
| 5 | CDN-sized covers and logos | 3 | 4 | 3 | 36 | S6 (needs the URL grammar from a HAR) |
| 6 | Click-to-load embeds | 2 | 4 | 4 | 32 | S2, privacy |
| 7 | **Native block reader** (brief §3): block model + lazy `SliverList` in a `SelectionArea`, reusing the Pixiv reader patterns | 5 | 1 | 2 | 10 | S2, S5, S7 |

Item 7 is the largest change in the plan, and it also unlocks:
- footnote sheets;
- an image viewer;
- highlights;
- sentence highlighting;
- WebView-free scrolling.

It needs a spec, per `CLAUDE.md`, and your go-ahead.

### Tiers 3–5 (later, listed for completeness)

- **Tier 3, finishing partial features:**
  - a paid-only filter and a Saved filter;
  - mark read when scrolled past;
  - font family and column width;
  - podcast speed, via the existing `setRate`;
  - per-episode resume;
  - local full-text search of retained posts;
  - in-app opening for unknown custom domains;
  - read-time estimate.
- **Tier 4, missing must-haves** (most need §10 decisions):
  - login and subscription import;
  - entitled paid bodies and expired-session prompts;
  - comment reply and like;
  - like and restack sync;
  - OPML;
  - footnote sheet and image viewer;
  - auto-download on Wi-Fi and notifications (new dependencies);
  - voiceovers.
- **Tier 5, differentiators:**
  - highlights with Markdown export;
  - TTS sentence highlighting;
  - Following Notes;
  - share to X through the system share sheet only.

## 10. Decisions needed

1. **Known issues.** The three issues in the brief are placeholders. Send them, or confirm that the bug list in §7 is the Tier 1 queue.
2. **Substack sign-in and write actions.** Confirm they are in scope despite the earlier specs (§0.1). This includes cookie login, like, restack, comment and subscribe.
3. **Cookie storage.** Pick one:
   - (A) the existing pattern: app-private preferences listed in `secretPrefKeys`, with `allowBackup=false`. No new dependency, but no encryption at rest.
   - (B) add an encrypted store such as `flutter_secure_storage`. That is a new dependency, and it needs a written justification and your approval.
4. **"Share quote to X".** Drop it, or allow only a system share-sheet hand-off (§0.2).
5. **Native block reader.** Approve a spec for replacing the WebView article renderer, done incrementally behind a setting. Otherwise Tier 2 stays on the WebView path (items 1–6).
6. **BUG-01.** Should deep replies be kept at the cap depth (flipping `substack_features_test.dart:90-101`), or stay dropped?
7. **New dependencies** for notifications and scheduled Wi-Fi downloads (Tier 4). Approval later; listed now so the plan is honest.
8. **Measurement access.** For the baseline, either allow the Substack hosts in this environment's network settings (`substack.com`, `*.substack.com`, `substackcdn.com`, `substack-post-media.s3.amazonaws.com`, the custom domains in the test set) or capture a HAR from the Substack app or web on your side. Also needed: a phone over wireless ADB, the 30-publication test set, the long post and the 300+ comment post.
