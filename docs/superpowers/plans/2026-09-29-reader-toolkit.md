# Reader Toolkit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Restore and finish all six approved reader features in XTA.

**Architecture:** Feature-owned flutter_triple Stores share bounded serialized preference writes and native reader adapters. UI scopes preserve native rendering and controllers. Named mixes aggregate real source slots and render the existing cards.

**Tech Stack:** Flutter 3.44.4 / Dart 3.12.2, existing pref/provider/flutter_triple/http/crypto, exact locked xml 6.6.1.

**Spec:** `docs/superpowers/specs/2026-09-29-reader-toolkit.md`

## Global Constraints

Do not change `lib/client/` or `lib/database/`. Keep Flutter 3.44.4, Dart 3.12.2, dart_twitter_api 0.6.0, all existing dependency versions, and dependency_overrides unchanged. The sole dependency exception is promotion of already-locked xml 6.6.1 to an exact direct dependency, with lockfile classification only. Use flutter_triple Store for feature state; no production setState or ChangeNotifier. Localize every new user-visible string through ARB/L10n in all 29 locales; retain existing values and key order. Generate ignored localization normally; never manually edit or track generated code. Preserve native cards, navigation, content warnings, caches, paging, subscriptions, scroll position, and true-black themes. Keep Android touch targets at least 48 logical pixels and verify 320dp, German and Arabic, text scale 2, light/dim/lights-out themes. X remains read-only; no X write endpoints. Keep changes incremental and scope-bound; format new Dart with line length 120 and avoid mass formatting. All writes must report false/throw failures honestly; serialized writes use latest successful state and destruction/generation guards. Do not log credentials, raw private URLs, or translated/history content. No merge or release is authorized by this feature task.

## Review Focus

1. Failed/cache-mutating preference saves retain last successful state; concurrent changes do not overwrite sibling entries (Tasks 1, 3, 5, 6).
2. Content warnings, hidden/pending cards, inactive routes and app background cannot enter history or fetch new concealed media (Tasks 3, 4, 6).
3. Query-significant feed URLs survive history/import/add/group identity resolution and private query values stay out of errors/logs (Tasks 3, 5).
4. Long multilingual/Unicode text and adversarial regex remain bounded and responsive; translations preserve paragraph/URL content (Tasks 2, 4).
5. Stale source pages/auth changes, uneven feeds and partial failures preserve healthy mixed content and do not stall native paging (Tasks 4, 6).

---

### Task 1: Per-feed appearance and shared Reader Tools entry

**Files:** Create `lib/reading/feed_appearance_store.dart`, `feed_appearance_scope.dart`, `feed_appearance_controls.dart`, `reader_preference_writes.dart`, `reader_tools_settings.dart`; narrowly modify Home/settings and native X/Reddit/Mastodon/Bluesky/Threads/RSS/Substack consumers; add `test/feed_appearance_store_test.dart`, `test/feed_appearance_controls_test.dart`, native regression tests and all 29 ARBs.

**Interfaces:** Produce `FeedIdentity(source,nativeId)`, `FeedAppearanceStore(prefs,{write})/.forPrefs`, `appearance/save/modify`, `FeedAppearanceScope(feed,child,store?,publishAction=true)`, controls/actions/capability helpers, `ReaderToolEntry` and default `ReaderToolsSettings`, shared `ReaderPreferenceWrites.enqueue/isPending/drain`.

- [ ] Write compiling behavioral RED for JSON identity/restart, absent inherit, latest serialized modifications, false/throw persistence, reset, active native feed identity, hidden-count semantics, link/media separation and 1000/2MB bounds. Capture actual RED output.
- [ ] Implement spec section 1 with focused files and inherited source defaults. Wire both active Home feed scopes/actions and actual native card/media consumers. Settings must expose the later shared tools without placeholders.
- [ ] Run focused/affected checks, six narrow German/Arabic 2x theme cases plus native card tests; generate localization normally and verify all old ARB values/order. Analyze without new findings; format only new/changed relevant Dart.
- [ ] Self-review, commit and write full `task-1-report.md` with interfaces, native callsites, RED/GREEN commands/output/logs and material limitations. Independent task review precedes Task 2.

### Task 2: Shared explicit translation

**Files:** Create focused `lib/reading/reader_translation_{config,service,store,controls,settings}.dart`; narrowly modify native X/Mastodon/Bluesky/RSS/Substack article/Notes readers and Reader Tools; add `test/reader_translation_*_test.dart` and all 29 ARBs.

**Interfaces:** Consume Reader Tools/writes. Produce provider/config/fingerprint APIs, injected HTTP/AI translation service, `ReaderTranslationStore(text,config,prefs,service,maxCache,maxChars)` with translate/showOriginal/reset/enabled/target/destroy, Controls(text,store?,article=false), service scope and article plaintext extractor.

- [ ] RED disabled/default/config restart, DeepL/Libre/AI request protocols and base paths, URL/Unicode/paragraph boundaries at 4000, stale/cancel/fail/retry, secret 32-entry/500000-character cache, article/full Notes integration and native CW/original restoration.
- [ ] Implement spec section 2 in focused units; expose settings through Reader Tools and actual native readers. Never silently invoke a provider or replace original rich content.
- [ ] GREEN focused/native tests and required UI matrix, locale/integrity/analyzer checks; self-review/commit and write `task-2-report.md`. Independent review gate.

### Task 3: Actual-view history

**Files:** Create focused `lib/reading/reading_history_{entry,store,hook,adapters,navigation,screen}.dart`; narrowly modify `main.dart`, reader search and native cards/readers/profile/web/archive surfaces; add `test/reading_history_*_test.dart` and all 29 ARBs.

**Interfaces:** Consume shared writes/tools/native translation originals. Produce `ReadingHistoryEntry`, `historyPublicUrl/historyNativeId`, root-owned `ReadingHistoryStore(prefs,{write,clock})/.forPrefs` with record/clear/remove/setEnabled/search/recordingEnabled/destroy, `ReadingHistoryHook(entry,child,store?,eligible=true)`, ancestor-AND Eligibility, native adapters, `openReadingHistory`, `resolveHistoryRss`.

- [ ] RED bounds/restart/search, visible foreground/current-route eligibility, CW/offstage/filter exclusion, clear/remove recapture prevention, rapid enable/disable with failed writes, serialized clear, meaningful query duplicates/order and secret stripping, native/offline RSS navigation.
- [ ] Implement spec section 3; record native original content at actual visibility, not fetch/tap. Shared reader search must use the root-owned history Store, not a stale snapshot.
- [ ] GREEN focused native/lifecycle/privacy/navigation tests and required UI matrix, locale/integrity/analyzer checks; self-review/commit and write `task-3-report.md`. Independent review gate.

### Task 4: Shared safe filters

**Files:** Create focused `lib/reading/shared_filter_{store,worker,boundary,collection,settings}.dart`; narrowly integrate native timeline/search/card/grid/thread/group consumers; add `test/shared_filter_*_test.dart` and all 29 ARBs.

**Interfaces:** Produce rule/scope/action/expiry APIs, root-owned Store(prefs,{write,now,regexTimeout}), inherited timeline/search scopes, Boundary(text,child,store?), plaintext extractor, SharedFilterCollection<T>(items,keyOf,textOf,itemBuilder,builder,sliver,onFilterRevision). Stable keys are unique; rawIndex identifies original source item, projected indexes drive separators/connectors.

- [ ] RED ordered keyword/regex rules/scopes/expiry/restart/failed saves, 250ms isolate cancellation and failure-reveal, hidden/fold geometry/history/CW, newly pending versus mounted retained-child lifecycle, independent account-search separators, native suppressed paging resumed by genuine user scroll/retry and no controller/cell replacement.
- [ ] Implement spec section 4 using one owned bounded worker, typed native collection projection and explicit lifecycle retention; no UI-isolate arbitrary regex and no redundant collection/card fold boundaries.
- [ ] GREEN core/native paging/lifecycle and editor/fold/failure German/Arabic 2x three-theme matrix; locale/integrity/analyzer checks; self-review/commit and `task-4-report.md`. Independent review gate.

### Task 5: RSS OPML and identity-safe persistence

**Files:** Create `lib/plugins/rss/rss_opml.dart`, `rss_opml_controls.dart`; narrowly modify `rss_store.dart`, `rss_group.dart`, `rss_add_screen.dart`, `rss_settings.dart`, `lib/subscriptions/group_add_follow.dart`, Reader Tools, exact xml declaration/classification; add `test/rss_opml{,_store,_controls,_group}_test.dart`, all 29 ARBs.

**Interfaces:** Produce `parseRssOpml(String)->RssOpmlDocument(feeds,duplicates,skipped)`, `exportRssOpml(Iterable<RssFeed>)->String`, `normalizeRssFeedUrl(String)->String?`, `allocateRssFeedIdentity(RssFeed,Iterable<RssFeed>)->RssFeed`; Store `importOpml(document)->Future<RssImportResult(imported,duplicates,skipped,persisted,tableSynced)>`, `add(feed)->Future<RssFeed>`, `followedFeed(url)`, `retryTableSync()->Future<bool>`, `tableSyncPending`, `syncRssFeedsTable()->Future<bool>`.

- [ ] RED nested/entity/escaped XML and size/depth/DTD/invalid URL/percent/UTF8, duplicate/query/path identity, add/import before pending load, cache-mutating false/throw writes and exact old raw restoration, native SQLite group membership and all three stored-ID consumers, saved-but-unsynced retry, actual picker/share/cancel with .opml filename.
- [ ] Implement spec section 5 with canonical preference serialization, stable existing IDs, bounded streaming picker and modern ShareParams. Secondary table sync is transactional and never deletes healthy follows after a failed canonical write.
- [ ] GREEN parser/store/actual-controls/SQLite-group/native-regression checks and required narrow matrix; exact xml-only dependency promotion, locale/integrity/analyzer checks; self-review/commit and `task-5-report.md`. Independent review gate.

### Task 6: Persistent native mixed feeds

**Files:** Create focused `lib/reading/mixed_feed_{definition,store,adapters,screen,settings}.dart` and source-specific adapters as needed; narrowly modify `lib/home/_feed.dart`, `home_screen.dart`, Home availability/options/body dispatch, appearance capability and Reader Tools; add `test/mixed_feed_*_test.dart`, all 29 ARBs.

**Interfaces:** Consume approved identity/appearance/translation/history/filter APIs and identity-safe RSS follows. Produce persistable definition/source descriptors, source-qualified native entries, page results/cursors, source-slot state, shared mix Store, native card builders and Home/Reader Tools actions.

- [ ] RED definition restart/edit/remove/reorder/name, real native source descriptor routing/cursors and per-account X For You, all X-list members/per-query chunk paging, network-qualified dedup/stable chronological/undated order/fair alternating, stale auth/server/source generations, healthy cache retention on partial failures and isolated retries, source removal/disposal, appearance/name/order changes without refetch/controller replacement.
- [ ] Implement spec section 6 using native APIs/card/navigation/CW pipelines; provide actual configured source picking rather than silently substituting author profiles. Wire both Home picker paths and explicit mix appearance capability/name. Deduplicate before shared filters.
- [ ] GREEN source adapters/Store/native cards/both Home routes/retained-state checks and required UI matrix; locale/integrity/analyzer checks; self-review/commit and `task-6-report.md`. Independent review gate.

## Final gate

- [ ] Most-capable whole-branch review, triage earlier deferred findings, one reviewed fix wave if needed.
- [ ] One final whole test suite, analyzer, new-Dart formatting, all-29 ARB integrity, exact frozen-path/pin audit; existing Android CI/build against the final tree.
- [ ] Publish exact verified tree on an own feature branch, draft PR stacked on the unmerged header branch; resolve actual required checks and mark ready. No merge/release. Preserve checkpoint/decision records remotely.
