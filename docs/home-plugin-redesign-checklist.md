# Home/plugin redesign checkpoint

Base: `4c6b8e87f4acce99b6998ac2b65a13892ac41673` (`aimdi126`).
Branch: `codex/home-plugin-redesign`. No merge, tag or release publication.

## Checklist

- [x] Read uploaded requirements, AGENTS, CLAUDE, README, SDK pins, workflows,
  existing design specs and relevant tests; inspect runtime entry points.
- [x] Verify default branch/latest production release/tag SHA and ancestry.
- [x] Local baseline ARB/skill checks; production CI success verified.
- [x] Compare two Home layouts; write design/navigation/reference decisions.
- [x] Home contextual navigation and production-used shared presentation.
- [ ] Reddit, Pixiv, RSS pilots, journey checks and test renders.
- [x] Remaining browsing clients and integration setup (verification pending).
- [ ] Format, code generation, analysis, deterministic tests, debug APK.
- [ ] Final frozen-file check, commit and per-plugin evidence review.

## Registry coverage matrix

All 13 browsing plugins use `homePage` -> the screen below, also reached via
`feedStripScreen`; Reddit overrides the latter with `RedditFeedList`. Their
settings routes come from descriptors except Substack/Stocks, which expose
controls inside their clients. Integration plugins have no browsing entry.
Enabled/install state, Home pins and bottom-bar visibility remain distinct.

| Plugin | Real sections / content | Search, setup and supporting routes | Presentation work and files | Required checks | Status |
| --- | --- | --- | --- | --- | --- |
| Threads | Home, local Liked; social posts | Discover/people search, add handle, session settings, profiles/threads | `threads_screen.dart`: readable local library and discovery context | Section selection, add/search/settings, cached feed | Implemented; CI pending |
| Bluesky | Following, Discover, Lists, local Liked | People search, imports, lists/feeds, profiles/threads, settings descriptor | `bluesky_screen.dart`, feeds pane: distinct discovery/list/library navigation | Lazy panes, imports/search, local likes | Implemented; CI pending |
| Mastodon | Explore, Local, Federated, Following | Instance-aware search/add account, settings, hashtag/profile/thread routes | `mastodon_screen.dart`: combine duplicated controls and show instance context | Instance labels, 4 sections, no extra fetch | Implemented; CI pending |
| TikTok | Following videos, Accounts | Handle search/settings, creator profile and player | `tiktok_screen.dart`: creator navigation and video browsing hierarchy | Following/accounts, playback entry, follow actions | Implemented; CI pending |
| Instagram | Existing For you, Following, Accounts | Search/settings, profiles, media viewer, local follows/likes | `instagram_screen.dart`: source/creator navigation and lazy discovery | 3 sections, settings return, media entry | Implemented; CI pending |
| Reddit | Following/Popular/All or followed subreddit; local Saved | Sort/search, community management, source/auth settings, listings/comments | `reddit_screen.dart`, Home entry: distinguish rail/community/actions | Existing source/sort/saved/navigation tests | Implemented; pilot analysis passed; renders pending |
| Hacker News | Top/New/Best/Ask/Show/Jobs/Saved/Following; stories/comments | Search, user pages, reader/settings, local saves/follows | `hn_screen.dart`, story card: readable sections and ranked story metadata | Eight sections, story vs comments, lazy fetch | Implemented; CI pending |
| Substack | Home/Inbox/Notes/Library; articles and notes | Discovery/add publication, archive, reader/comments/TTS, local read/save/likes | `substack_screen.dart`: article filter/empty hierarchy and publication library | Unread, notes lazy load, archive/reader | Implemented; CI pending |
| RSS | Home/Feeds; articles | Add/autodiscovery, read/unread, tags, feed reader/settings; no general search descriptor | `rss_screen.dart`, `rss_card.dart`: article rows, source tags, filter reset | Read semantics, empty filters, feed/settings paths | Implemented; pilot analysis passed; renders pending |
| Pixiv | Home/Rankings/Favorites/Search/More; illustrations | Following/Recommended, mode/date, public/private favorites, auth/history/mute/bookmark | `pixiv_screen.dart`, `pixiv_grid.dart`: compact controls, named sections and artwork metadata | Five sections, ranking/source controls, auth, adaptive grid | Implemented; pilot analysis passed; renders pending |
| Booru | Latest/Following; gallery | Tag search/mute, settings, post/detail viewer; private catalogue behavior | `booru_screen.dart`, grid: tag/source context and adaptive artwork | Tag selection, private visibility, gallery entry | Implemented; CI pending |
| EhViewer | Popular/Front/Toplist/Watched/History/Favorites; galleries | Search, period filters, session settings, gallery/reader; private catalogue behavior | `eh_screen.dart`, grid: period/filter hierarchy and gallery metadata | Six sections, period, session/visibility preferences | Implemented; CI pending |
| Stocks | Watchlist/Trending/Markets; quotes and cashtag posts | Add/manage symbols, ticker details; no search descriptor | `stocks_screen.dart`: label quote vs post sections, filter reset/retry | Symbol filter reset, no invented quote freshness | Implemented; CI pending |
| Karakeep | Integration only | Server/API key, existing verify, explicit link save | `karakeep_settings_screen.dart`: setup sections, responsive actions, probe invalidation | Editing invalidates probe; no automatic saves | Implemented; CI pending |
| Deepmarks | Integration only | API/signing keys/base, public identity/mismatch probe, explicit save | `deepmarks_settings_screen.dart`: identity hierarchy, responsive feedback | Key mismatch/unknown owner preserved, no automatic writes | Implemented; CI pending |
| Immich | Integration only | Server/API key, existing verify, album/video preferences, selected-folder uploads | `immich_settings_screen.dart`: connection vs upload options, feedback | Existing toggles/save, probe invalidation, no automatic upload | Implemented; CI pending |

## Verification log

Baseline local: `python scripts/validate_arb.py` -> no errors; existing missing
placeholder warnings. `bash scripts/check_skill_sync.sh` -> pass.
`git merge-base --is-ancestor aimdi126 HEAD` -> pass; initial tree clean.
No local Flutter/FVM/Android SDK and no connected device. Production baseline
verify run `33641378899` and build run `33641378888` both succeeded.

Final commands/results, artifact URLs and remaining device/live-service limits
will be recorded here as verification completes.

Checkpoint: social/community UI commit `29bc60d558e933721d57859916473740be9a71dd`.
Pilot static analysis succeeded. Evidence capture required a fake-clock fix;
tests/build are pending a clean rerun. No runtime-device evidence yet.

Article/gallery/market commit: `fd7f3c6f143fa7eb11c1fd7d9d4efc6c1e6a7403`.
Repository verify run `34004211795` succeeded. The separate exact-commit review
workflow still needs completed evidence captures and APK. Integration setup,
actual Home journeys and session retention are the next verification checkpoint.

Verification checkpoint `1e6a6ed9b92b4e203097980e081a1c9041000049`: static
analysis passed; repository verify `34005007294` exposed 16 deterministic test
failures (12 small action targets, Home scroll after source switching, and three
integration finder errors). The review workflow `34005007304` incorrectly
reported success because its shell pipe masked the test exit code; its APK is
not a final verified deliverable. Explicit Bash/pipefail now fixes that gate.

Next corrective pass keeps Home's nested coordinator mounted, enforces 48dp
plugin action targets, fits Reddit's full header on 320dp by placing Saved and
community management in the existing overflow, and corrects integration tests
to address the actual icon-button constructor and lazy form. Additional Pixiv
mode/date/private-favorites and RSS unread/reset journeys are included. Test
renders now load the production Material icon font; the RSS filter row aligns
to the reading edge. Verification and final APK remain pending.

Checkpoint `14195db8d4448f8d76f314316a986d2df81c5603`: 2,178 tests passed,
five skipped and one Home journey failed. Advancing the Store test clock
exposed a real hit-target obstruction: after a scrolled source switch, RSS's
Unread control was under the pinned Home strip. Home now uses the existing
shared shell with a fixed header and one primary reader scrollable. The
journey explicitly asserts the control is hit-testable. A targeted journey
step now precedes the full suite and publishes its log/renders early. Final
verification and APK remain pending. The final ARB sort only reorders metadata;
all parsed keys and values are identical.
