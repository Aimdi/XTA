# Round 3 parity ledger: widgets batch

One line per XTA test: `status | test file › test name | native place | note`.
Statuses: covered (native does it and a native test checks it), untested→ported
(native did it; the test is ported now), missing→implemented (native lacked it or
differed; implemented and tested now), n/a (Flutter/Dart-only, reason given).

covered | account_attribution_test.dart › an account that has never been refused is not flagged | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_attribution_test.dart › an account refused within the cooldown is flagged | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_attribution_test.dart › the flag lapses once the cooldown has passed | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_attribution_test.dart › the flag lasts exactly the configured cooldown | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_attribution_test.dart › a flagged account is deprioritised but still reachable as a fallback | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_attribution_test.dart › when every account is flagged one is still tried | core/x/XAccounts.kt; XAccountsTest | XAccountHealth.flagged / XAccountSelector
covered | account_load_test.dart › counts requests in flight per account and endpoint | core/x/XAccounts.kt (XAccountLoad); XAccountsTest | requestsInFlightAreCountedPerAccountAndEndpoint
covered | account_selector_test.dart › returns null when every account is excluded | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › returns the only remaining account | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › prefers healthy accounts over not-found-flagged ones | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › treats expired not-found cooldown as healthy | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › still within not-found cooldown is flagged | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › falls back to flagged accounts when none are healthy | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › excludes rate-limited accounts from the healthy pool | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › falls back to rate-limited accounts when all remaining are limited | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › takes the account with the most requests left | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › an account with no quota known ties with the best one instead of beating it | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › an account that never reports a quota does not take every request | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › a burst goes to the account with the fewest requests in flight | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › equal accounts still share the load | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › a healthy account beats a flagged one with more quota | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
covered | account_selector_test.dart › an excluded account is skipped however much it has left | core/x/XAccounts.kt; XAccountsTest | native takes turns where XTA picks at random among ties
n/a | acquire_future_identity_test.dart › async ??= overwrites a field the function set before awaiting | - | documents Dart's async ??= evaluation order; no app rule
covered | ai_client_test.dart › empty fields mean AI stays off | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › recognises a Grok endpoint or model | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › reads prefs without throwing on missing keys | ui/archive/ArchiveRules.kt aiConfig; ArchiveRulesTest | empty settings read as AI off
covered | ai_client_test.dart › posts a chat completion and reads the text | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › OpenRouter keeps provider/model IDs and posts to the documented endpoint | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › accepts full endpoints and custom paths without duplicating chat/completions | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › a stalled provider times out | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › a non-2xx response is an AiException | services/ai/AiClient.kt; AiClientTest | 
covered | ai_client_test.dart › aiCompletionText survives a reshaped body | services/ai/AiClient.kt; AiClientTest | 
untested→ported | android_data_extraction_test.dart › the app opts out of backup and names its extraction rules | app AndroidManifest.xml; core AndroidDataExtractionTest | 
missing→implemented | android_data_extraction_test.dart › nothing goes to a cloud backup or a device-to-device transfer | app/src/main/res/xml/data_extraction_rules.xml; core AndroidDataExtractionTest | device_* domains were not excluded; added
covered | antenna_query_test.dart › builds OR include query with excludes | store/antenna/AntennaQuery.kt; AntennaQueryTest | 
covered | antenna_query_test.dart › following scope keeps only followed authors | store/antenna/AntennaQuery.kt; AntennaQueryTest | 
covered | archive_filter_test.dart › bookmarks keeps this author's saves | store/saved/SavedView.kt profileArchive; SavedViewTest | 
covered | archive_filter_test.dart › likes keeps this author's hearts | store/saved/SavedView.kt profileArchive; SavedViewTest | 
covered | archive_filter_test.dart › all is saves then hearts, once each | store/saved/SavedView.kt profileArchive; SavedViewTest | 
covered | archive_filter_test.dart › another author is empty | store/saved/SavedView.kt profileArchive; SavedViewTest | 
covered | article_link_test.dart › takes the id out of an article link | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › twitter.com and the www hosts count too | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a trailing slug does not confuse it | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › other X links are not articles | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › another site hosting the same path is not an X article | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › nothing at all is not an article | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › takes the id out of a broadcast link | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › the singular /i/broadcast/ path counts too | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › pscp.tv watch links are broadcasts | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › twitter.com and the www hosts count too | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a scheme-less display URL counts | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a truncated display URL is not an id | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › article links and statuses are not broadcasts | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › nothing at all is not a broadcast | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › broadcastIdInText finds the link in a post body | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › takes the id out of a spaces link | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › the singular /i/space/ path counts too | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › twitter.com and the www hosts count too | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a scheme-less display URL counts | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a truncated display URL is not an id | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › broadcast links are not spaces | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › spaceIdInText finds the link in a post body | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › finds the article among ordinary links | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a post with no article link has none | services/links/XLinks.kt; XLinksTest | 
covered | article_link_test.dart › a post with no links at all has none | services/links/XLinks.kt; XLinksTest | 
covered | author_caps_test.dart › caps chains per author, keeping newest first | core/feed/FeedShaping.kt PostCaps.perAuthor; FeedShapingTest | 
covered | author_caps_test.dart › uncapped authors pass through | core/feed/FeedShaping.kt PostCaps.perAuthor; FeedShapingTest | 
covered | backup_format_test.dart › writes exactly the keys older builds read | store/backup/BackupDocument.kt; BackupDocumentTest | 
covered | backup_format_test.dart › a file that carries nothing restores nothing | store/backup/BackupDocument.kt; BackupDocumentTest | 
covered | backup_format_test.dart › a header-less file is read as the legacy version | store/backup/BackupDocument.kt; BackupDocumentTest | 
covered | backup_format_test.dart › an empty section is not the same as a missing one | store/backup/BackupDocument.kt; BackupDocumentTest | 
n/a | booru_perf_stats_test.dart › nearest-rank percentiles | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › a frame is janky when build or raster misses the refresh budget | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › an empty or missing summary reads as no frames | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › the median of each statistic across trials | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › a timing without a measurement is missing, not zero | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › disk-cache sizes become count, total, median and p95 | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
n/a | booru_perf_stats_test.dart › the summary reads numbered trials and keeps raw S5 and S6 data | - | statistics of XTA's Flutter driver perf harness (test_driver/), not app behaviour
covered | boost_preview_test.dart › reads the post rather than the entities X escaped it with | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | 
untested→ported | boost_preview_test.dart › unescapes ampersands too | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | assertions added (&amp;, empty, 80 chars whole)
untested→ported | boost_preview_test.dart › no text at all is no preview | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | assertions added (&amp;, empty, 80 chars whole)
covered | boost_preview_test.dart › newlines become spaces so the peek stays one paragraph | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | 
covered | boost_preview_test.dart › a long post is cut with an ellipsis | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | 
untested→ported | boost_preview_test.dart › a post that just fits is left whole | ui/post/PostView.kt BoostRuns.preview; PostRulesTest | assertions added (&amp;, empty, 80 chars whole)
covered | catch_up_split_test.dart › keeps a page of nothing but new chains and pages on | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › cuts at the first already-read chain and hands the rest back | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › a page whose very first chain is read keeps nothing | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › an empty page never claims a boundary | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › an unfinished gap-fill outranks every other claim | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › separates "nothing arrived" from "you read it all" | core/feed/CatchUp.kt; CatchUpTest | 
covered | catch_up_split_test.dart › feedCatchUpModeKey is per feed | core/feed/CatchUp.kt; CatchUpTest | 
covered | chrome_avatar_test.dart › chromeAvatarSourceUrl upgrades the tiny _normal crop | ui/profile/ProfileRules.kt fullAvatarUrl (used by ChromeAvatar.fromProfile); ProfileRulesTest | 
covered | clean_url_test.dart › cleanUrl strips tracking parameters | services/links/XLinks.kt clean; XLinksTest | 
covered | clean_url_test.dart › cleanUrl strips the rest of the common click-ids too | services/links/XLinks.kt clean; XLinksTest | 
covered | community_parse_test.dart › reads the header, rules and member faces | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › a body without a community is null, not a throw | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › reads the Top posts, each marked with its community, and the next cursor | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › a community id survives the cache round trip | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
untested→ported | community_parse_test.dart › a body without a timeline is null | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | native answers an empty last page where XTA answers null; assertion added
covered | community_parse_test.dart › reads a page of members and the next cursor | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › an empty slice is an empty last page | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › reads a page of communities and the next cursor | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
covered | community_parse_test.dart › an empty slice is an empty last page | core/x/parse/XCommunityParser.kt; XCommunityAndUsersTest | 
n/a | crash_reporter_test.dart › uncaught isolate errors are marked handled | - | Dart isolate error handler flag
missing→implemented | crash_reporter_test.dart › shouldReport skips synthetic and http account errors | ui/settings/CrashReporter.kt CrashIssue.shouldReport; CrashReporterTest | native filed no-account/rate-limited errors; now skipped
untested→ported | crash_reporter_test.dart › issueApiUri validates owner/name | CrashReporter.issueApiUrl; CrashReporterTest | 
covered | crash_reporter_test.dart › prefsMapWithoutSecrets strips the GitHub token | store/settings/XtaPrefs.kt SecretPrefs; XtaPrefsTest | 
covered | crash_reporter_test.dart › every plugin credential is stripped from an export | store/settings/XtaPrefs.kt SecretPrefs; XtaPrefsTest | 
covered | crash_reporter_test.dart › a credential-shaped key is stripped even when nobody declared it | store/settings/XtaPrefs.kt SecretPrefs; XtaPrefsTest | 
n/a | crash_reporter_test.dart › tells apart release traces that differ only below their header | - | Dart AOT release-trace layout (pid/abs/virt); JVM traces carry symbols
n/a | crash_reporter_test.dart › matches one crash however the process was laid out | - | Dart AOT release-trace layout (pid/abs/virt); JVM traces carry symbols
covered | crash_reporter_test.dart › keeps the first frames of a trace with symbols | CrashIssue.fingerprint; CrashReporterTest | first four frames
covered | crash_reporter_test.dart › buildIssueTitle stays compact | CrashIssue title (80 chars); CrashReporterTest | 
covered | crash_reporter_test.dart › report posts to GitHub when enabled with token | ui/settings/CrashReporter.kt; CrashReporterTest | 
covered | crash_reporter_test.dart › report refuses to send without token | ui/settings/CrashReporter.kt; CrashReporterTest | 
n/a | database_indexes_test.dart › the feed cache is read by index, not by scanning a week of JSON | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › the ordering comes from the index too, so there is no sort step | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › the cursor lookup is indexed | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › a group member lookup by profile is indexed | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › lookups by primary key still use the implicit index | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › a missing table does not stop the upgrade | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › migration 39 is reachable from an older database | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › the saved and liked lists read newest-first by index, not by sorting | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | database_indexes_test.dart › the weekly cleanup finds old chunks by index | - | SQLite indexes/migrations/query plans; native keeps its data in files, no SQLite
n/a | feed_cache_decode_test.dart › async chunk decode matches the sync path | - | decoding off the UI isolate must equal the sync path: Dart isolate mechanics
untested→ported | feed_cache_decode_test.dart › an empty store is nothing to decode | core/feed/SavedFeed.kt; SavedFeedTest | 
untested→ported | feed_cache_decode_test.dart › a corrupt or null chunk is skipped instead of aborting | core/feed/SavedFeed.kt decode; SavedFeedTest | native keeps one file: a damaged thread is skipped, an unreadable file is no feed
n/a | feed_cache_decode_test.dart › chunk encode matches the former UI-isolate jsonEncode | - | decoding off the UI isolate must equal the sync path: Dart isolate mechanics
n/a | feed_cache_timestamp_test.dart › reads SQLite CURRENT_TIMESTAMP as the UTC it is | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
n/a | feed_cache_timestamp_test.dart › leaves an explicit zone alone | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
n/a | feed_cache_timestamp_test.dart › returns null for anything it cannot read | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
n/a | feed_cache_timestamp_test.dart › picks the most recent row | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
n/a | feed_cache_timestamp_test.dart › ignores rows it cannot read | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
n/a | feed_cache_timestamp_test.dart › is null when nothing is readable | core/feed/SavedFeed.kt savedAt | SQLite CURRENT_TIMESTAMP text parsing; native stores savedAt as epoch millis, so the zone mistake cannot happen
covered | feed_chunk_concurrency_test.dart › a guest or a single account keeps the old three | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_chunk_concurrency_test.dart › each account adds about two searches, never more than six | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_chunk_concurrency_test.dart › counts accounts on for home feeds with a working sign-in | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_chunk_concurrency_test.dart › with every account off for home feeds, all working ones count | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_chunk_concurrency_test.dart › an account rate-limited on search does not count | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_chunk_concurrency_test.dart › no accounts means none usable | core/feed/GroupFeed.kt ChunkConcurrency; GroupFeedTest | 
covered | feed_defaults_test.dart › a new group follows the global default instead of pinning a value | core/session/Session.kt ownChoices/withoutOwnChoices; GroupSessionTest | 
covered | feed_defaults_test.dart › countIncludeOverrides counts only feeds that pinned a choice | core/session/Session.kt ownChoices/withoutOwnChoices; GroupSessionTest | 
covered | feed_defaults_test.dart › a group keeps its own choice when only the replies overrides are cleared | core/session/Session.kt ownChoices/withoutOwnChoices; GroupSessionTest | 
covered | feed_defaults_test.dart › the All group is not counted as a feed with its own choice | core/session/Session.kt ownChoices/withoutOwnChoices; GroupSessionTest | 
covered | feed_read_position_test.dart › feedReadPositionKey maps Following group id to following | core/feed/CatchUp.kt FeedKeys/ReadPosition; CatchUpTest | 
covered | feed_read_position_test.dart › isChainSeen matches by id and by timestamp | core/feed/CatchUp.kt FeedKeys/ReadPosition; CatchUpTest | 
covered | feed_read_position_test.dart › caughtUpBoundaryIndex finds the first seen chain below new ones | core/feed/CatchUp.kt FeedKeys/ReadPosition; CatchUpTest | 
covered | feed_read_position_test.dart › newestRecordableChain skips chains without timestamps | core/feed/CatchUp.kt FeedKeys/ReadPosition; CatchUpTest | 
covered | feed_read_position_test.dart › caught-up boundary treats equal timestamps as seen | core/feed/CatchUp.kt FeedKeys/ReadPosition; CatchUpTest | 
covered | feed_search_query_test.dart › an account is searched by its name | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › a saved search keeps its operators in brackets instead of quotes | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › brackets the members so the filters cover all of them | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › asks for native reposts when the feed shows reposts | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › mixes accounts and saved searches | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › leaves the brackets out when there is no member | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › fills every search as far as X reads it (replies $replies, reposts $reposts) | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › a search narrowing another counts its length, and fills it as far as X reads | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › puts more typical accounts in one search than the old 16 | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › a new member only changes the last chunk | core/feed FeedQueries; FeedQueriesTest | 
covered | feed_search_query_test.dart › a member too long to share a search gets its own chunk | core/feed FeedQueries; FeedQueriesTest | 
untested→ported | feed_session_cache_test.dart › the same key keeps the same controller | ui/group/FeedSessions.kt FeedSessionCache; FeedSessionCacheTest | 
untested→ported | feed_session_cache_test.dart › stops growing once it is full | ui/group/FeedSessions.kt FeedSessionCache; FeedSessionCacheTest | 
untested→ported | feed_session_cache_test.dart › drops the least recently used, not the oldest created | ui/group/FeedSessions.kt FeedSessionCache; FeedSessionCacheTest | 
untested→ported | feed_session_cache_test.dart › an evicted key loses its offset and media filter too | ui/group/FeedSessions.kt; FeedSessionCacheTest | place goes with the feed; the media switch lives in MediaModes for the run (holds no pages, so nothing to bound)
untested→ported | feed_session_cache_test.dart › evict drops one key so the next get is a fresh controller | ui/group/FeedSessions.kt FeedSessionCache; FeedSessionCacheTest | 
untested→ported | feed_session_cache_test.dart › invalidateAll empties it | ui/group/FeedSessions.kt FeedSessionCache; FeedSessionCacheTest | 
covered | feed_source_reload_test.dart › a source whose last member was removed is asked again, so its posts leave | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › a source that gained its first member is asked | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › a source whose members changed is asked | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › nothing changed means nothing is refetched | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › are the group members, for an ordinary group | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › include every followed one on the combined feed, when asked for there | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › are the home-feed ones even when the group has no members of its own | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › are only the members when the source is not in the home feed | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › never repeat an id that is both a member and followed | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | feed_source_reload_test.dart › are empty when there is nothing to ask for | ui/feed/PluginPosts.kt load + FeedSources.asks; PluginPostsTest, FeedMixTest | 
covered | future_pool_test.dart › mapWithConcurrency preserves order | core/feed/BatchRead.kt mapWithConcurrency; BatchReadTest | 
covered | future_pool_test.dart › mapWithConcurrency caps in-flight work | core/feed/BatchRead.kt mapWithConcurrency; BatchReadTest | 
covered | future_pool_test.dart › mapWithConcurrency handles empty input | core/feed/BatchRead.kt mapWithConcurrency; BatchReadTest | 
covered | future_pool_test.dart › mapWithConcurrency treats non-positive concurrency as 1 | core/feed/BatchRead.kt mapWithConcurrency; BatchReadTest | 
missing→implemented | group_add_member_store_test.dart › failed addition releases busy state and can be retried | ui/subscriptions/AddMemberModel.kt; AddMemberModelTest | an X follow that threw left the sheet busy; now released, retry possible
untested→ported | group_add_member_store_test.dart › double tap does not repeat a follow while pending or after success | ui/subscriptions/AddMemberModel.kt; AddMemberModelTest | 
missing→implemented | group_add_member_store_test.dart › dismissal while adding does not update a disposed store | ui/subscriptions/AddMemberModel.kt; AddMemberModelTest | dismissing cancelled a follow mid-write; it now runs to its end
untested→ported | group_add_member_store_test.dart › an earlier search cannot replace the latest result for the same query | ui/subscriptions/AddMemberModel.kt; AddMemberModelTest | 
missing→implemented | group_add_member_store_test.dart › a hanging search times out and allows a new search | ui/subscriptions/AddMemberModel.kt; AddMemberModelTest | the 30 s timeout was rethrown as a cancellation and left the spinner forever; now it fails and a new search runs
covered | group_add_sources_test.dart › nothing typed offers nothing | ui/subscriptions/GroupAddSources.kt; GroupAddSourcesTest | 
covered | group_add_sources_test.dart › a bare name could be any of three, so all three are offered | ui/subscriptions/GroupAddSources.kt; GroupAddSourcesTest | 
covered | group_add_sources_test.dart › r/ names a subreddit and nothing else | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a Threads address names a Threads account | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a Reddit address names a subreddit | ui/subscriptions/GroupAddSources.kt; GroupAddSourcesTest | 
covered | group_add_sources_test.dart › a bsky.app profile names a Bluesky account | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › user@instance is a Fediverse address and nothing else | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a Fediverse profile URL is read from its path, whatever the domain | ui/subscriptions/GroupAddSources.kt; GroupAddSourcesTest | 
covered | group_add_sources_test.dart › a dotted name could be a Bluesky handle or a newsletter on its own domain | ui/subscriptions/GroupAddSources.kt; GroupAddSourcesTest | 
covered | group_add_sources_test.dart › a feed URL is offered as RSS, not as a newsletter | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a publication address is offered as its host | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a bare name is offered as its Substack address | GroupAddSources + each plugin's followable() label from its tested normaliser (RedditClientTest, ThreadsLinksTest, BlueskyUrlsTest, MastodonXtaCasesTest, SubstackHostsTest) | 
covered | group_add_sources_test.dart › a source the reader turned off is never offered | data/plugins/AppPlugins.kt followables (running plugins only); GroupAddSourcesTest | a plugin that is off is not running, so offers no row
covered | group_add_sources_test.dart › nothing is offered when every source is off | data/plugins/AppPlugins.kt followables (running plugins only); GroupAddSourcesTest | a plugin that is off is not running, so offers no row
covered | group_add_sources_test.dart › every source knows its plugin and its switch | GroupAddSources ids are the plugin ids | native has no separate switch: the plugin is installed and running or not
covered | group_board_layout_test.dart › a phone keeps the reader's two or three columns | core/session GroupBoard; GroupsTest | 
covered | group_board_layout_test.dart › a value outside two or three falls back into range | core/session GroupBoard; GroupsTest | 
covered | group_board_layout_test.dart › a wide window adds columns so no tile passes the maximum width | core/session GroupBoard; GroupsTest | 
covered | group_board_layout_test.dart › a phone keeps the aspect ratio | core/session GroupBoard; GroupsTest | 
covered | group_board_layout_test.dart › a widened board gets content-height tiles that grow with large text | core/session GroupBoard; GroupsTest | 
covered | group_board_layout_test.dart › two columns are landscape tiles, more are square | core/session GroupBoard; GroupsTest | 
n/a | group_loading_lifecycle_test.dart › a stalled group snapshot times out and a retry can load the feed | core/session/Session.kt (in-memory StateFlow) | a group's membership snapshot is an async SQLite read in XTA that can stall; native reads it from the in-memory session at once
n/a | group_loading_lifecycle_test.dart › a soft reload cancels the old snapshot and preserves current results | core/session/Session.kt (in-memory StateFlow) | a group's membership snapshot is an async SQLite read in XTA that can stall; native reads it from the in-memory session at once
n/a | group_loading_lifecycle_test.dart › a failed membership reload keeps an already readable group visible | core/session/Session.kt (in-memory StateFlow) | a group's membership snapshot is an async SQLite read in XTA that can stall; native reads it from the in-memory session at once
n/a | group_loading_lifecycle_test.dart › destroy releases pending group reads and ignores late completion | core/session/Session.kt (in-memory StateFlow) | a group's membership snapshot is an async SQLite read in XTA that can stall; native reads it from the in-memory session at once
covered | group_membership_writes_test.dart › adding keeps the groups an account is in and takes a repeat quietly | core/session/Session.kt addingTo/removingFrom/withNewGroup; GroupSessionTest | 
covered | group_membership_writes_test.dart › removing takes the account out of the given groups only | core/session/Session.kt addingTo/removingFrom/withNewGroup; GroupSessionTest | 
covered | group_membership_writes_test.dart › a created group comes back by its id, empty, with the default mark and no undo | core/session/Session.kt addingTo/removingFrom/withNewGroup; GroupSessionTest | 
covered | group_membership_writes_test.dart › saving a new group still writes its look and members | ui/subscriptions/GroupDraft.kt; GroupDraftTest savingANewGroupMakesItWithItsMarkMembersAndDate | 
covered | group_membership_writes_test.dart › a suggested group is created with its accounts | ui/subscriptions UngroupedPlans; UngroupedPlansTest applyingMakesTheSuggestedGroups... | 
n/a | group_migration_test.dart › migration 34 adds pinned and position, backfilling alphabetical order | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 35 adds emoji/mark_style and backfills symbol for custom icons | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 36 adds the custom-feed rule columns with inert defaults | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 52 adds nsfw with default off | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 53 creates booru_subscription | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 54 creates eh_favorite | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 55 creates tiktok_subscription | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
n/a | group_migration_test.dart › migration 57 creates instagram_subscription | - | SQLite schema migrations; native keeps groups in its session file (marks, positions, nsfw read by SessionCodec, see GroupSessionTest/XtaBackupTest)
covered | group_tree_test.dart › a parent takes everything nested inside it, however deep | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a middle group takes only its own branch | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group with nothing inside it is just itself | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › an id nobody has heard of is still itself | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a loop in stored data resolves instead of hanging | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › are the groups directly inside | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › none for a leaf | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › only the groups that stand on their own | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group whose parent was deleted comes back to the top rather than vanishing | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group that somehow parents itself is treated as top level | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group cannot go inside itself | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group cannot go inside its own descendant | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › an unrelated pair is fine | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › moving a child up to a sibling branch is fine | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › counts from zero at the top | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a loop stops rather than counting forever | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a child follows its parent instead of vanishing | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › depth follows depth, not the order the ids arrived in | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › every group appears exactly once, cycle or no cycle | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › a group whose parent is filtered out is still shown | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › keeps relative order and sinks NSFW to the second list | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | group_tree_test.dart › an all-safe list has an empty NSFW section | core/session GroupTree, GroupBoard.sections; GroupsTest | 
covered | guest_token_test.dart › a token with no observed rate-limit headers is used | core/x/XGuest.kt; XGuestAndRegistryTest | 
covered | guest_token_test.dart › a token with quota left inside the window is used | core/x/XGuest.kt; XGuestAndRegistryTest | 
covered | guest_token_test.dart › an exhausted token inside the window is replaced | core/x/XGuest.kt; XGuestAndRegistryTest | 
covered | guest_token_test.dart › once the window rolls over the quota has refilled, so the token is used again | core/x/XGuest.kt; XGuestAndRegistryTest | 
covered | guest_token_test.dart › the boundary is inclusive of the reset instant | core/x/XGuest.kt; XGuestAndRegistryTest | 
untested→ported | guest_token_test.dart › the guest bearer is a single constant, not repeated inline | core/x/XGuest.kt, XTransport GUEST_HEADERS; XClientParityTest | assertions added
untested→ported | guest_token_test.dart › the user agent is the header value, not the whole map printed | core/x/XGuest.kt, XTransport GUEST_HEADERS; XClientParityTest | assertions added
covered | held_refresh_test.dart › runs straight away when they are at the top | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › waits when they are reading further down | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › runs when they scroll back to the top | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › runs only once, however often they return to the top | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › returning to the top with nothing held does nothing | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › several changes while reading still cost one refresh | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › a change while at the top clears anything already held | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › uses pixels when the scroll position is attached | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › keeps the last known answer when pixels are unavailable | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
covered | held_refresh_test.dart › stops when the widget is gone | core/feed/CatchUp.kt HeldRefresh; CatchUpTest | 
n/a | held_refresh_test.dart › stops when the inner position is ready | - | retrying a NestedScrollView restore while it has 0 or 2 positions: Flutter scroll plumbing
n/a | held_refresh_test.dart › waits a few frames while NestedScrollView has 0 or 2 positions | - | retrying a NestedScrollView restore while it has 0 or 2 positions: Flutter scroll plumbing
n/a | held_refresh_test.dart › gives up instead of scheduling forever | - | retrying a NestedScrollView restore while it has 0 or 2 positions: Flutter scroll plumbing
n/a | home_observer_lifecycle_test.dart › destroyed home models stop listening to the app group store | - | a destroyed Flutter Store kept listening to another Store; native view models collect in their own scope, which ends with them
covered | home_strip_restore_test.dart › a restored backup keeps the Home timelines it lists | store/settings/Settings.kt restoring; SettingsTest aRestoreAppliesTheSettingsAsXtasImportDoes | 
n/a | home_strip_restore_test.dart › without the conversion shared preferences drops those lists | - | shared_preferences refusing List<dynamic>: a Flutter plugin quirk
covered | home_strip_restore_test.dart › a value is removed from a stored list, and an unset list stays unset | data/plugins/PluginPrefs.kt; PluginPrefsTest | 
untested→ported | http_client_test.dart › the same instance is handed out, so connections are pooled | core/x/http/XHttp.kt OkHttpXHttp.defaultClient; XClientParityTest | 
n/a | http_client_test.dart › clearing it hands out a fresh one rather than null | - | resetting the Dart global client to null; native's shared client is never cleared
covered | import_handles_test.dart › reads @handles, bare names and profile links | core/session ImportHandles; FollowsTest | 
covered | import_handles_test.dart › dedupes by lowercase and skips junk | core/session ImportHandles; FollowsTest | 
covered | import_handles_test.dart › accepts a trailing slash on a profile url | core/session ImportHandles; FollowsTest | 
untested→ported | json_test.dart › a deep path needs no checks along the way | core/json/J.kt; JTest | 
untested→ported | json_test.dart › a missing key anywhere on the path is null at the end, never a throw | core/json/J.kt; JTest | 
untested→ported | json_test.dart › lists are stepped into by index, and past the end is null | core/json/J.kt; JTest | 
untested→ported | json_test.dart › indexing a map or keying a list is a shape mismatch, so null | core/json/J.kt; JTest | 
untested→ported | json_test.dart › a document that is not JSON at all answers every question with null | core/json/J.kt; JTest | 
untested→ported | json_test.dart › each getter answers only for its own type | core/json/J.kt; JTest | 
untested→ported | json_test.dart › numbers tolerate the string-wrapped digits X is fond of | core/json/J.kt; JTest | 
untested→ported | json_test.dart › words are not numbers, however hopefully read | core/json/J.kt; JTest | 
untested→ported | json_test.dart › a list wraps its elements; anything else is an empty one | core/json/J.kt; JTest | 
untested→ported | json_test.dart › exists tells presence apart from a readable value | core/json/J.kt; JTest | 
covered | language_filter_test.dart › splits CSV prefixes | core/feed/FeedRules.kt LanguageFilter; FeedRulesTest | 
covered | language_filter_test.dart › off passes everything through | core/feed/FeedRules.kt LanguageFilter; FeedRulesTest | 
covered | language_filter_test.dart › hide drops non-matching languages | core/feed/FeedRules.kt LanguageFilter; FeedRulesTest | 
covered | language_filter_test.dart › fold keeps chain with reason and merges prior folds | core/feed/FeedRules.kt LanguageFilter; FeedRulesTest | 
covered | likes_by_group_test.dart › each group gets its own section, in the order given | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › an author in two groups appears under both | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › a like from nobody's group lands in the ungrouped section, last | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › a like with no author recorded is ungrouped rather than dropped | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › a group nobody in it has liked from is left out entirely | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › an author whose only group is not being shown counts as ungrouped | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | native takes the shown groups only, so an author of a hidden group is ungrouped by construction
covered | likes_by_group_test.dart › no likes means no sections at all | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | likes_by_group_test.dart › no groups puts everything under ungrouped | store/saved/LikesStore.kt likesByGroup; LikesStoreTest | 
covered | list_url_test.dart › extractListId accepts list URLs and bare ids | services/links/XLinks.kt; XLinksTest | 
untested→ported | list_url_test.dart › rich-card reader accepts only normal web URLs | services/links/LinkOpening.kt isBrowsable; LinkOpeningTest | 
covered | list_url_test.dart › parseUri routes list links, profiles and posts correctly | services/links/XLinks.kt; XLinksTest | 
covered | list_url_test.dart › parseUri routes broadcasts and Spaces instead of failing open | services/links/XLinks.kt; XLinksTest | 
covered | list_url_test.dart › isLiveWatchUrl covers broadcasts, Spaces, and Periscope | services/links/XLinks.kt; XLinksTest | 
covered | local_post_logic_test.dart › rejects blank notes | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › trims and keeps the text | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › clips bodies longer than the cap | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › a blank note with no media is empty | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › media alone is enough | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › empty query matches everything | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › matches case-insensitively | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › keeps a real mime | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › fills in from the extension | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › round-trips through toMap/fromMap | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › a backup map keeps media bytes and fromMap reads them | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › legacy rows without media columns still parse | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › round-trips a reply id | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › null and junk yield null | store/saved/SavedContent.kt of; SavedContentTest aDamagedBlobIsNothing... | a note's quote is read through SavedContent
covered | local_post_logic_test.dart › roots skip replies whose parent is still around | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › a reply whose parent is gone becomes a root | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › direct reply count ignores nested grandchildren | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › thread walks replies under each parent, oldest first | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › thread root walks up to the oldest living ancestor | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_post_logic_test.dart › a cycle does not hang the thread walk | store/saved/LocalPosts.kt, XtaRows; LocalPostsTest | 
covered | local_undo_test.dart › undo restores exact subscription metadata and memberships once | ui/subscriptions/Unfollowing.kt; UnfollowingTest | 
covered | local_undo_test.dart › undo refuses to overwrite a subsequent refollow | ui/subscriptions/Unfollowing.kt; UnfollowingTest | 
covered | local_undo_test.dart › undo does not resurrect deleted groups or disturb unrelated people | ui/subscriptions/Unfollowing.kt; UnfollowingTest | 
missing→implemented | local_undo_test.dart › moving between groups can be undone without changing other members | ui/subscriptions/GroupActions.kt setMemberships + MembershipUndo; UnfollowingTest | native offered no undo after moving a member between groups; now Changes saved · Undo puts that member's groups back
covered | main_thread_stalls_test.dart › an empty record means the thread never stalled | ui/settings/MainThreadStalls.kt; DeviceRecordsTest theWatchdogsRecordShowsItsLatestLinesInTheReport | 
covered | main_thread_stalls_test.dart › keeps the stall line, its frames and the resume line, indented under the probe | ui/settings/MainThreadStalls.kt; DeviceRecordsTest theWatchdogsRecordShowsItsLatestLinesInTheReport | 
covered | main_thread_stalls_test.dart › only the newest lines survive a long record | ui/settings/MainThreadStalls.kt; DeviceRecordsTest theWatchdogsRecordShowsItsLatestLinesInTheReport | 
covered | main_thread_stalls_test.dart › reads the watchdog file from the files directory | ui/settings/MainThreadStalls.kt; DeviceRecordsTest theWatchdogsRecordShowsItsLatestLinesInTheReport | 
covered | main_thread_stalls_test.dart › a missing file is no stall, and a directory lookup that never answers is bounded | ui/settings/MainThreadStalls.kt summary; DeviceRecordsTest | a missing file is no stall; native's files directory is known at once, so no lookup to bound
untested→ported | media_alt_text_test.dart › extracts ext_alt_text keyed by media id_str | core/x/parse/XParser.kt mediaItem; XClientParityTest | assertion added: no description is none
covered | media_alt_text_test.dart › fromData plumbs mediaAltText from extended_entities | core/x/parse/XParser.kt mediaItem alt; XClientParityTest altTextFallsBackToTheMediasDescription | 
untested→ported | media_quality_test.dart › each stored word finds its quality | store/settings/Choices.kt MediaQuality, MediaPrefs.imageQuality; SettingsTest | 
untested→ported | media_quality_test.dart › a word the vocabulary does not know is the fallback, not a guess | store/settings/Choices.kt MediaQuality, MediaPrefs.imageQuality; SettingsTest | 
untested→ported | media_quality_test.dart › the retired disabled value reads as the fallback too | store/settings/Choices.kt MediaQuality, MediaPrefs.imageQuality; SettingsTest | 
untested→ported | media_quality_test.dart › what is stored round-trips through the enum unchanged | store/settings/Choices.kt MediaQuality, MediaPrefs.imageQuality; SettingsTest | 
covered | media_strip_test.dart › an ordinary photo keeps its own | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › a sliver is cropped to the nearest end rather than shown as one | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › a size the response did not carry is treated as square | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › one photo takes the full width and keeps its own shape, unclamped | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › a single photo with no usable size falls back to a square | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › several share one height | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › tall photos are narrower than wide ones, so more of them fit | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › no card fills the row, so the next one always shows an edge | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › nothing attached lays out as nothing | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › the first one needs no scrolling | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › a later one counts the cards and the gaps before it | ui/post/MediaStrip.kt; MediaStripTest | 
covered | media_strip_test.dart › an index past the end stops at the last card rather than throwing | ui/post/MediaStrip.kt startOf; MediaStripTest aRowOpenedAtAPictureStartsThereWithinTheRow | native scrolls the row to the card's index, clamped to the last
covered | media_strip_test.dart › a video uses videoInfo, not the thumbnail size | ui/post/MediaStrip.kt startOf; MediaStripTest aRowOpenedAtAPictureStartsThereWithinTheRow | native scrolls the row to the card's index, clamped to the last
covered | media_strip_test.dart › a broadcast with no sizes is 16:9, not a square | ui/post/MediaStrip.kt startOf; MediaStripTest aRowOpenedAtAPictureStartsThereWithinTheRow | native scrolls the row to the card's index, clamped to the last
covered | media_strip_test.dart › a photo still uses its pixel size | core/x/parse/XParser.kt mediaItem (original_info is the video frame's own size); MediaStripTest | 
covered | muted_keyword_storage_test.dart › reads legacy CSV as hide keywords | core/feed/FeedRules.kt MutedKeywords; FeedRulesTest | 
covered | muted_keyword_storage_test.dart › reads JSON with until and action | core/feed/FeedRules.kt MutedKeywords; FeedRulesTest | 
covered | muted_keyword_storage_test.dart › encode round-trips through JSON storage | core/feed/FeedRules.kt MutedKeywords; FeedRulesTest | 
covered | muted_keyword_storage_test.dart › drops expired terms | core/feed/FeedRules.kt MutedKeywords; FeedRulesTest | 
covered | note_draft_recovery_test.dart › draft restores text, attachment identity and target after interruption | store/saved NoteEditor; NoteEditorTest | 
covered | note_draft_recovery_test.dart › discard is scoped and cannot be resurrected by a queued write | store/saved NoteEditor; NoteEditorTest | 
covered | note_editor_store_test.dart › cancel detection includes attachments and restored original body | store/saved NoteEditor; NoteEditorTest | 
covered | note_editor_store_test.dart › failed save retains draft and attachments and can be retried | store/saved NoteEditor; NoteEditorTest | 
covered | note_editor_store_test.dart › save blocks duplicate saves and attachment mutation until complete | store/saved NoteEditor; NoteEditorTest | 
covered | note_editor_store_test.dart › cancelled and failed pickers preserve existing attachments | store/saved NoteEditor; NoteEditorTest | 
n/a | paging_first_page_test.dart › pagingAwaitingFirstPage is only the in-flight first page | - | infinite_scroll_pagination's first-page state and post-frame fetch scheduling: Flutter package plumbing; native lists start their first read in the view model
n/a | paging_first_page_test.dart › scheduleFirstPageFetch kicks page 0 once | - | infinite_scroll_pagination's first-page state and post-frame fetch scheduling: Flutter package plumbing; native lists start their first read in the view model
n/a | paging_recovery_test.dart › leaving a stalled feed cancels its wait and deadline | - | leaving a feed cancels its view model scope (structured concurrency); no timer to release
covered | paging_recovery_test.dart › stalled page unlocks retry and a late result cannot change its cursor | PagedRead runs counter (PagedReadTest aReadStartedOverDropsTheAnswerOfTheOneBefore) + OkHttp 30 s callTimeout in core XHttp | a stalled call ends as a failure that offers retry
covered | paging_recovery_test.dart › refresh invalidates the old cursor even when the old page finishes last | ui/search/PagedRead.kt; PagedReadTest aReadStartedOverDropsTheAnswerOfTheOneBefore | 
covered | paging_recovery_test.dart › shared first page releases a timeout and does not clear an active retry | core/feed/BatchRead.kt; BatchReadTest aBatchPastItsTimeFailsAndALateAnswerChangesNothing | 
covered | paging_recovery_test.dart › soft refresh times out without losing posts and can retry | ui/plugins Fetch; FetchTest aSoftRefreshKeepsTheItemsShown... and aReadThatFailsKeepsWhatWasShown... | 
covered | paging_recovery_test.dart › refresh coalesces toolbar/pull requests and blocks old-cursor pagination | ui/profile/ProfileViewModel.kt loadTab cancels the tab's in-flight page; refresh keeps the shown profile on failure | no controller test: the rule lives in loadTab's job cancel
covered | paging_recovery_test.dart › retry repeats the failed refresh before using the pagination cursor | ui/profile/ProfileViewModel.kt loadTab cancels the tab's in-flight page; refresh keeps the shown profile on failure | no controller test: the rule lives in loadTab's job cancel
covered | paging_recovery_test.dart › soft refresh cancels an older in-flight page | ui/profile/ProfileViewModel.kt loadTab cancels the tab's in-flight page; refresh keeps the shown profile on failure | no controller test: the rule lives in loadTab's job cancel
covered | paging_recovery_test.dart › repairing missing first-page batches preserves older pages and cursor | core/feed BatchRead retry of failed batches; BatchReadTest aRetryReadsOnlyTheBatchesThatFailed | 
covered | paging_recovery_test.dart › failed first-page repair retains visible items | core/feed BatchRead retry of failed batches; BatchReadTest aRetryReadsOnlyTheBatchesThatFailed | 
covered | podcast_places_test.dart › places keep the newest 50 episodes, newest first, and round-trip | ui/media/Podcasts.kt PodcastPlaces; PlaybackTest placesKeepTheNewestFirstAndAtMostFifty | 
covered | podcast_places_test.dart › a place at the very start is no place, and damaged entries are skipped | ui/media/Podcasts.kt PodcastPlaces; PlaybackTest placesKeepTheNewestFirstAndAtMostFifty | 
covered | published_documents_test.dart › the documents are read from the default branch | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | published_documents_test.dart › only an earlier default counts as one | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | published_documents_test.dart › moves $former to the default branch and forgets what it read | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | published_documents_test.dart › moves the plugin catalogue and keeps its list | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | published_documents_test.dart › keeps an address set by hand, and what was read from it | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | published_documents_test.dart › leaves an install already on the default branch alone | store/settings/DataPrefs.kt PublishedDocuments, SettingsMigrations.repointPublishedDocuments; SettingsMigrationsTest anEarlierReleasesDocumentAddressPointsAtTodays... | 
covered | quick_pick_test.dart › six show until All, which shows every one | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › a sheet can show more before All | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › created choices lead, and every choice shows once | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › a choice gone since the sheet opened is passed over | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › matches ignore case, and a typed name can become a choice | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › a name a choice has, whatever its case, cannot be created again | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › nothing found says so and offers the name | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | quick_pick_test.dart › creating empties the search and keeps All as it was | store/saved QuickPick + ui/subscriptions/GroupPicker.kt; SaveSheetAndNotesTest, GroupPickerTest | 
covered | rate_limit_tracker_test.dart › is not limited when nothing has been flagged | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › is limited before the reset time | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › is not limited at or after the reset time | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › rate limits are per endpoint, not per account globally | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › rate limits are per account for the same endpoint | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › clear removes the limit for that account and endpoint | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › reports the requests left, counting down as they are sent | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › an answer with quota left keeps the account available | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › a spent quota sets the account aside until the reset | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › requests sent count the quota down | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › an answer written before requests in flight does not hand their credits back | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › a new window replaces the count | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › an unknown quota is never counted down | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › an answer without quota headers lifts a flag, as a success always did | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | rate_limit_tracker_test.dart › malformed quota headers are ignored | core/x/XAccounts.kt XRateLimits; XAccountsTest | 
covered | read_failure_kind_test.dart › names the category and the status X answered, never the body | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › a failure outside the taxonomy names its type, not its message | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › separates connectivity and timeout failures | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › separates account, rate limit and endpoint failures | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › does not confuse raw 404 with a proven endpoint rotation | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › server failures are distinct and automatically recoverable | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | read_failure_kind_test.dart › session and endpoint failures never auto-retry; rate limits wait for their reset | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
missing (not done) | read_failure_kind_test.dart › every other failure is transient and retried on a backoff | ui/common/ReadRetries.kt, data/Problem.kt | X's definitive error codes (22/34/50/63/200) map to Problem.Other and back off like any failure; telling them apart needs a new kind in the plugin-visible Problem type, left for the API bump
covered | read_failure_kind_test.dart › X's definitive answers about an account are not retried | core/x/XReadLog.kt failureOf; XReadLogTest; ui/common/ReadRetries.kt; ReadRetriesTest | 
covered | reader_state_test.dart › local search stays available while archive loads and keeps the latest query | ui/search LibrarySearch; LibrarySearchTest | 
covered | reader_state_test.dart › highlight notes, selected tags and OCR survive reopening | store/saved ArchiveNotes; ArchiveNotesTest | 
covered | reader_state_test.dart › dismissal persists per group and undo restores suggestions | store/follows DiscoveryFeedbackStore per group + DiscoveryViewModel undo; FollowsCacheTest, DiscoveryTest | 
covered | reader_state_test.dart › profile shows persisted content while refresh fails | ui/profile/ProfileViewModel.kt load (kept profile stays, problem beside it); KeptProfilesTest | 
covered | saved_tab_order_test.dart › built-in Notes tab is always present, after Favorites | store/saved SavedTabs; SavedViewTest | 
covered | saved_tab_order_test.dart › Notes is a built-in, never a folder id | store/saved SavedTabs; SavedViewTest | 
covered | saved_tab_order_test.dart › stored order keeps Notes if the reader moved it | store/saved SavedTabs; SavedViewTest | 
