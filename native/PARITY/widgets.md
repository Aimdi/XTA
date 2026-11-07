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
