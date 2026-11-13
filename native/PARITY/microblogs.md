# Round 3 parity ledger: microblogs (Bluesky, Mastodon, Threads and the shared microblog UI)

> A record of the port: the `.dart` test files and widgets named here are XTA's Flutter sources as of commit `fab7619b`, the last that has them. The Flutter app was deleted after it; the repository's history keeps it (`git show fab7619b:test/…`).

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered, untested (test ported), missing/different (implemented and test ported), n/a (Flutter-only).

## test/bluesky_archive_actions_test.dart
covered | bluesky_archive_actions_test › Bluesky archive snapshot stays identifiable, searchable and retains repost actor | plugins/bluesky BlueskyArchive.kt, store SavedContent.kt | BlueskyArchiveTest.aPostIsFiledInXtasRecordAndReadBackWhole (round trip keeps repostedByDid), SavedContentTest.aBlueskyPostIsSearchable…
covered | bluesky_archive_actions_test › Bluesky image downloads never treat video thumbnails as photos | store SavedContent.kt | SavedContentTest.aBlueskyPostIsSearchableAndNeverOffersAVideoThumbnailAsAPhoto, BlueskyArchiveTest.savedReadsTheRecord…
covered | bluesky_archive_actions_test › Malformed plugin blobs are not parsed as X posts | store SavedContent.kt | SavedContentTest.aDamagedBlobIsNothingRatherThanACrashOrATweet
covered | bluesky_archive_actions_test › Other plugin notes retain a portable source link and local text | store SavedContent.kt | SavedContentTest.anotherNetworkKeptAsALinkIsOtherAndSearchedByItsLink

## test/bluesky_client_test.dart
covered | bluesky_client_test › getProfile parses a public AppView payload | plugins/bluesky BlueskyClient.kt | BlueskyClientTest.aProfileIsReadFromThePublicAppViewWithXtasHeaders
covered | bluesky_client_test › getAuthorFeed returns posts and cursor | BlueskyClient.kt | BlueskyClientTest.anAuthorFeedSendsItsLimitCursorAndFilterAndHandsBackTheNextCursor
covered | bluesky_client_test › getAuthorFeed sends the official filter | BlueskyClient.kt | same test (filter=Bluesky.AUTHOR_FEED_REPLIES)
covered | bluesky_client_test › searchPosts hits feed.searchPosts and parses PostView rows | BlueskyClient.kt, BlueskyParser.kt | BlueskyClientTest.postSearchFromBlueskysAppViewGoesToItsApiAndPagesByTime (until: cursor), BlueskyParserTest.facetsAreReadInByteOrder
covered | bluesky_client_test › maps 404 and 429 to typed errors | BlueskyClient.kt, BlueskyErrorKind | BlueskyClientTest.refusalsAreTypedByTheirStatus
covered | bluesky_client_test › searchActors returns profiles from the actors list | BlueskyClient.kt, BlueskyParser.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints; name→handle fallback in BlueskyParserTest.blankProfileFieldsAreNullAndANameFallsBackToTheHandle
covered | bluesky_client_test › getPostThread asks for the uri and flattens the tree | BlueskyClient.kt | BlueskyClientTest.aThreadIsAskedWithItsDepthAndAMissingOneIsNotFound, BlueskyParserTest.aThreadFlattens…
covered | bluesky_client_test › getFollows and getList hit the graph endpoints | BlueskyClient.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints
covered | bluesky_client_test › getStarterPack and resolveStarterPackUri | BlueskyClient.kt | BlueskyClientTest.webLinksResolveThroughTheirOwnersDid
covered | bluesky_client_test › getFollowers hits the graph endpoint | BlueskyClient.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints
covered | bluesky_client_test › resolveListUri builds an AT-URI from a web list reference | BlueskyClient.kt | BlueskyClientTest.webLinksResolveThroughTheirOwnersDid
covered | bluesky_client_test › resolveBaseUrl is consulted per request and empty falls back | BlueskyClient.kt | BlueskyClientTest.theAppViewIsAskedBeforeEveryRequestAndABadOneFallsBack
covered | bluesky_client_test › getSuggestions parses actors | BlueskyClient.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints (suggestions)

## test/bluesky_conversation_controls_test.dart
covered | bluesky_conversation_controls_test › author focus preserves connecting context but removes unrelated branches | networks/bluesky BlueskyReplies.kt | BlueskyRepliesTest.authorFocusKeepsTheRepliesLeadingToTheAuthorAndDropsTheRest
covered | bluesky_conversation_controls_test › reply order sorts siblings stably while retaining their descendants | BlueskyReplies.kt | BlueskyRepliesTest.replyOrderSortsSiblingsStablyAndKeepsTheirRepliesUnderThem
covered | bluesky_conversation_controls_test › twenty thousand nested replies build, count and collapse without recursion | BlueskyReplies.kt | BlueskyRepliesTest.twentyThousandNestedRepliesBuildCountAndCollapseWithoutRecursion
covered | bluesky_conversation_controls_test › refresh errors retain controls and branches; stale collapse ids are pruned | plugins/bluesky BlueskyThread.kt (ThreadState) | ThreadState now takes its read and AppView as functions; BlueskyThreadStateTest.aFailedRefreshKeepsTheThread…
missing/different | bluesky_conversation_controls_test › newest refresh wins and disposed stores never start new requests | BlueskyThread.kt (ThreadState) | an older read answering late overwrote the newer; now only the newest lands, nothing is read once the scope ended. BlueskyThreadStateTest.theNewestRefreshWins…
missing/different | bluesky_conversation_controls_test › thread discards late AppView error/content and reloads from current server (both variants) | BlueskyThread.kt (ThreadState) | an answer from an AppView left meanwhile is dropped and the thread read again. BlueskyThreadStateTest.aLateAnswerFromTheAppViewThatWasLeft…
missing/different | bluesky_conversation_controls_test › generic warnings cover text and reset when revision changes | ui/plugins NetworkPosts.kt (NetworkPost.revision), Posts.kt, BlueskyPostRow.kt | the reveal was kept per post id; now per revision (text, media, warning, quote). BlueskyThreadStateTest.aWarningCoversTheText…
covered | bluesky_conversation_controls_test › unavailable text and quotes never offer reveal; media labels retain text | BlueskyPostRow.kt (toNetworkPost) | BlueskyThreadStateTest.anUnavailablePostOrQuoteOffersNoReveal…
covered | bluesky_conversation_controls_test › footer actions remain named when counts are hidden | BlueskyPostRow.kt (footerCounts, new) | footer labels moved into a testable function. BlueskyThreadStateTest.theFooterNamesEachAction…
missing/different | bluesky_conversation_controls_test › media grid exposes ALT but never announces protected descriptions | BlueskyProfileScreen.kt (BlueskyMediaTile.label) | tile announced as "author · alt / Media / Content warning" as XTA (was the alt alone); new plugin string `media` from XTA's arb. BlueskyThreadStateTest.aMediaTileIsAnnounced…
untested | bluesky_conversation_controls_test › conversation controls work in compact large-text RTL layout | BlueskyThread.kt (BranchesMenu, Controls, SortMenu, back to selected) | the controls exist; their state (fold all, author only) is tested in BlueskyThreadStateTest; Compose has no RenderFlex overflow to check. The sort store sharing is in conversation_sort_test below

## test/bluesky_discovery_test.dart
covered | bluesky_discovery_test › reposted authors come first; quotes fill the rest | networks/bluesky BlueskyTimeline.kt (peopleToFollow) | BlueskyTimelineTest.repostedAuthorsComeFirstThenQuotedOnesAndFollowedOnesNever
covered | bluesky_discovery_test › parseBlueskyStarterPackRef reads web and at:// forms | BlueskyUrls.kt (starterPackRef) | BlueskyUrlsTest.listsFeedsAndStarterPacksAreNamedByLinkOrUri
covered | bluesky_discovery_test › starterPackListUri reads list.uri, list string, or record.list | BlueskyParser.kt | BlueskyParserTest.aStarterPackNamesItsListAsAViewAUriOrInItsRecord

## test/bluesky_facets_test.dart
covered | bluesky_facets_test › reads link, mention and tag features with byte ranges | networks/bluesky BlueskyFacets.kt, BlueskyParser.kt | BlueskyFacetsTest.linkMentionAndTagFeaturesKeepTheirByteRangesInOrder
covered | bluesky_facets_test › splits ASCII text around a link facet | BlueskyFacets.kt | BlueskyFacetsTest.asciiTextSplitsAroundALink
covered | bluesky_facets_test › utf-8 emoji before a facet still maps byte indices | BlueskyFacets.kt | BlueskyFacetsTest.anEmojiBeforeAFacetStillMapsItsByteOffsets

## test/bluesky_feed_browser_test.dart
(The first nine run for both the ranked feed and the list source; native reads both through SourcePages/PagedItems, one per AppView and URI.)
covered | bluesky_feed_browser_test › switch selects immediately, late response cannot overwrite it | plugins/bluesky BlueskySources.kt (SourcePages) | BlueskySourcesTest.eachFeedKeepsItsOwnPagesSoALateOrFailedReadNeverShowsInAnother
covered | bluesky_feed_browser_test › new failed selection never borrows the old feed posts | BlueskySources.kt (SourcePages) | BlueskySourcesTest.eachFeedKeepsItsOwnPagesSoALateOrFailedReadNeverShowsInAnother
covered | bluesky_feed_browser_test › refresh failure keeps cached posts and can be retried | ui/plugins PagedItems | BlueskySourcesTest.aFailedRefreshKeepsThePostsShownAndATryAgainClearsIt
covered | bluesky_feed_browser_test › pagination coalesces calls, preserves source order, stops a repeated cursor | PagedItems | BlueskySourcesTest.pagingAsksOnceAtATime…
covered | bluesky_feed_browser_test › pagination failure preserves cursor and permits explicit retry | PagedItems | BlueskySourcesTest.aFailedPageKeepsItsCursorForATryAgain
covered | bluesky_feed_browser_test › switch during pagination cannot append another source | SourcePages | each source pages on its own PagedItems, so a late page lands only in its own; BlueskySourcesTest.eachFeedKeeps…
covered | bluesky_feed_browser_test › changing AppView invalidates in-flight response and loads the new server | SourcePages (keyed by AppView) | BlueskySourcesTest.eachFeedKeeps… (another AppView reads anew)
covered | bluesky_feed_browser_test › destroying while loading ignores the response | PagedItems (scope ends with the screen) | BlueskySourcesTest.anEmptyFeedKeepsItsCursorAndAnEndedScopeTakesNoLateAnswer
covered | bluesky_feed_browser_test › remembered selection is local and scoped to AppView | BlueskyStores.kt (selection/select), BlueskySnapshot | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
covered | bluesky_feed_browser_test › catalog failure cannot block Discover and remains retryable | BlueskySources.kt (BlueskyFeeds) | new FakeHost test harness; BlueskySourcesTest.aFailedCatalogNeverKeepsDiscoverFromReading…
covered | bluesky_feed_browser_test › new store restores its saved custom feed without waiting for the catalog | BlueskyFeeds.ensureLoaded | BlueskySourcesTest.theFeedLastOpenedIsOpenAgainWithoutWaitingForTheCatalog
covered | bluesky_feed_browser_test › catalog search ignores late queries and retains new pins | BlueskyFeeds.searchCatalog | BlueskySourcesTest.aLateCatalogSearchIsDroppedAndAPinStays
covered | bluesky_feed_browser_test › catalog cursor cycles terminate and duplicates are removed | BlueskySources.kt (Listing) | BlueskySourcesTest.aCatalogCursorThatComesRoundAgain…
missing/different | bluesky_feed_browser_test › catalog paging restarts after changing AppView without reusing the old cursor | BlueskyFeeds.searchCatalog | the next page used the old AppView's cursor; now it reads the first page again. BlueskySourcesTest.afterAnotherAppView…
missing/different | bluesky_feed_browser_test › list lookup without an open source refreshes on AppView change | BlueskyLists.ensureLoaded | on another AppView the looked-up account was dropped for the handle; now it is listed again (and the old selection goes, as XTA). BlueskySourcesTest.theAccountLookedUpIsListedAgain…
missing/different | bluesky_feed_browser_test › list catalog paging restarts after changing AppView | BlueskyLists.lookUp | as the catalog. BlueskySourcesTest.afterAnotherAppView…
covered | bluesky_feed_browser_test › list lookup ignores stale actors and pages their lists | BlueskyLists.lookUp | BlueskySourcesTest.aStaleLookUpIsDropped…
covered | bluesky_feed_browser_test › concurrent pins preserve existing preferences and persist the final order | BlueskyStores.kt (pinnedFeeds) | BlueskySourcesTest.pinsMadeTogether…
covered | bluesky_feed_browser_test › empty feed retains selection, pin, browse and explicit paging | BlueskySourcePanes.kt (FeedsPane: Open a feed, Pin, Find feeds; feed_empty) | UI only; native reads the next page as the list end shows instead of a Load more button; the kept cursor is tested in BlueskySourcesTest.anEmptyFeedKeepsItsCursor…
covered | bluesky_feed_browser_test › catalog search shows metadata and an accessible pin action | BlueskyFeedCatalog.kt (FeedRow: name, @creator, description, Pin/Unpin button) | UI only, read in code; pin/unpin state in BlueskySourcesTest

## test/bluesky_feed_store_test.dart
covered | bluesky_feed_store_test › a second page of the same uris is not a replacement | networks/bluesky BlueskyTimeline.kt (stabilize), FollowingTimeline (StateFlow keeps an equal list) | BlueskyTimelineTest.mergedPagesKeepEachPostAndEachPersonOnce; BlueskyFollowingTimelineTest.followingAnotherAccount… (unchanged read leaves the list)
covered | bluesky_feed_store_test › a cache-walk prefix does not shrink a painted feed | BlueskyFollowing.kt | native never emits a partial walk: accounts not asked or failed keep their last page. BlueskyFollowingTest.aFailedAccountKeepsItsLastPageAndIsAskedAgain, aBoundedReadProgressesPastTheFirstAccounts
covered | bluesky_feed_store_test › new uris at the top may appear | BlueskyFollowing.kt | BlueskyFollowingTest.eachAccountsPageIsReadAndMergedNewestFirst
covered | bluesky_feed_store_test › duplicate uris from two accounts collapse to one row | BlueskyTimeline.stabilize | BlueskyFollowingTest.theSamePostFromTwoAccountsIsOneRow, BlueskyTimelineTest.theOriginalWinsADuplicateRepostInEitherOrder
missing/different | bluesky_feed_store_test › an empty poll does not blank a painted feed | plugins/bluesky BlueskyFollowingPane.kt (FollowingTimeline.refresh) | a read that brought nothing but a failure (e.g. after an AppView change) blanked the list; now it stays with the problem shown. BlueskyFollowingTimelineTest.aReadThatOnlyFailsLeavesThePostsShown
covered | bluesky_feed_store_test › an empty successful response is not fetched again on entry | FollowingTimeline + BlueskyFollowing (ten-minute reuse) | BlueskyFollowingTimelineTest.anEmptyAnswerIsNotAskedAgainOnEntryButAPullAsks
covered | bluesky_feed_store_test › re-entry keeps a painted feed while explicit refresh reads changes | BlueskyFollowing.kt | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced; BlueskyFollowingTimelineTest.followingAnotherAccount…
covered | bluesky_feed_store_test › following another account invalidates the entry identity | FollowingTimeline.start (re-reads when the follows change) | BlueskyFollowingTimelineTest.followingAnotherAccountReadsItAndTheSamePostIsOneRow
covered | bluesky_feed_store_test › a second remount poll does not replace an unchanged first page | BlueskyFollowing.kt (ten-minute reuse) | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced
covered | bluesky_feed_store_test › an empty feed still fetches — remount skip only applies when posts are already there | BlueskyFollowing.kt | BlueskyFollowingTest.eachAccountsPageIsReadAndMergedNewestFirst (first read asks every account)
covered | bluesky_feed_store_test › the first-page cursor is not reset on a later poll | BlueskyFollowing.kt (no cursor is sent; pages reused) | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced
covered | bluesky_feed_store_test › pull-to-refresh asks again even inside the TTL | BlueskyFollowing.kt | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced ("pull-to-refresh asks again")
covered | bluesky_feed_store_test › force refresh with the same uris does not notify | FollowingTimeline (StateFlow drops an equal state) | BlueskyFollowingTimelineTest.followingAnotherAccount… checks the list is unchanged; native still shows the loading state meanwhile
covered | bluesky_feed_store_test › the same uri from two followed accounts is one row | BlueskyTimeline.stabilize | BlueskyFollowingTest.theSamePostFromTwoAccountsIsOneRow

## test/bluesky_feeds_test.dart
covered | bluesky_feeds_test › reads bsky.app feed URLs and AT-URIs | networks/bluesky BlueskyUrls.kt (feedRef) | BlueskyUrlsTest.listsFeedsAndStarterPacksAreNamedByLinkOrUri
covered | bluesky_feeds_test › reads generatorView rows and a singular view | BlueskyParser.kt (feedGenerators) | BlueskyParserTest.feedGeneratorsFallBackToTheirRecordKeyAndNeedAUri
covered | bluesky_feeds_test › tolerates a reshaped payload | BlueskyParser.kt | BlueskyParserTest.feedGeneratorsFallBack… ({"feeds":"nope"} is empty), aReshapedFeedIsEmptyNotACrash
covered | bluesky_feeds_test › getListFeed uses the same feed item shape as getAuthorFeed | BlueskyClient.listFeed | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints
covered | bluesky_feeds_test › listView keeps description and creator | BlueskyParser.listMembers | BlueskyParserTest.aListComesWithItsMembersAndPeopleWithoutANameAreDropped
covered | bluesky_feeds_test › successful empty discovery result survives tab re-entry | plugins/bluesky BlueskySources.kt (SourcePages, PagedItems) | BlueskySourcesTest.anEmptyFeedKeepsItsCursor…, enteringAgainReadsNothingAgain…
covered | bluesky_feeds_test › opening the same cached feed does not refetch | BlueskyFeeds.ensureLoaded, SourcePages | BlueskySourcesTest.enteringAgainReadsNothingAgainAndAPinIsKeptUntilUnpinned
covered | bluesky_feeds_test › pin persists and unpin removes the URI | BlueskyFeeds.pin/unpin, BlueskyPreferences.pinnedFeeds | BlueskySourcesTest.enteringAgainReadsNothingAgainAndAPinIsKeptUntilUnpinned
covered | bluesky_feeds_test › selecting the current empty list does not reload it | BlueskyLists (SourcePages per list) | the same SourcePages as feeds: BlueskySourcesTest.eachFeedKeepsItsOwnPages… (same PagedItems back)
covered | bluesky_feeds_test › opening the same cached list does not refetch | BlueskyLists (SourcePages) | BlueskySourcesTest.eachFeedKeepsItsOwnPagesSoALateOrFailedReadNeverShowsInAnother
covered | bluesky_feeds_test › getFeed and getListFeed hit the AppView paths | BlueskyClient.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints

## test/bluesky_following_recovery_test.dart
covered | bluesky_following_recovery_test › new repost activity sorts ahead of newly authored posts | networks/bluesky BlueskyTimeline.kt | BlueskyTimelineTest.aRepostsNewActivitySortsAheadOfNewerOriginals
covered | bluesky_following_recovery_test › followed original wins a duplicate repost regardless of merge order | BlueskyTimeline.kt | BlueskyTimelineTest.theOriginalWinsADuplicateRepostInEitherOrder
covered | bluesky_following_recovery_test › newest repost wins among multiple reposters while source rank stays stable | BlueskyTimeline.kt | BlueskyTimelineTest.theNewestRepostWinsWhileTheFirstSeenOrderStays
covered | bluesky_following_recovery_test › count and moderation updates repaint even when identities match | BlueskyPost (data class equality) | BlueskyFollowingTimelineTest.aNewCountOrLabelOfTheSamePostIsAChange
missing/different | bluesky_following_recovery_test › failed first load retries on re-entry | plugins/bluesky BlueskyFollowingPane.kt (FollowingTimeline.start) | showing the tab again after a failed read did nothing; now it reads again. BlueskyFollowingTimelineTest.aFailedFirstReadIsTriedAgainWhenTheTabIsShownAgain
covered | bluesky_following_recovery_test › partial failure preserves failed author posts and retries them | BlueskyFollowing.kt | BlueskyFollowingTest.aFailedAccountKeepsItsLastPageAndIsAskedAgain
covered | bluesky_following_recovery_test › unfollowing everyone clears the authoritative completed feed | FollowingTimeline.refresh | BlueskyFollowingTimelineTest.unfollowingEveryoneEmptiesTheTimelineAndAGroupsReadLeavesIt
covered | bluesky_following_recovery_test › forced bounded refresh progresses past first imported accounts | BlueskyFollowing.kt | BlueskyFollowingTest.aBoundedReadProgressesPastTheFirstAccounts
covered | bluesky_following_recovery_test › failed import batch cannot starve unread accounts | BlueskyFollowing.kt | BlueskyFollowingTest.aFailedBatchCannotStarveTheAccountsNeverAsked
covered | bluesky_following_recovery_test › group subset read cannot replace Following or freshness | BlueskyFollowing.posts vs FollowingTimeline | BlueskyFollowingTimelineTest.unfollowingEveryone…AndAGroupsReadLeavesIt
covered | bluesky_following_recovery_test › older forced refresh cannot overwrite a newer result | FollowingTimeline (cancels the older read), BlueskyFollowing (per-account request count) | BlueskyFollowingTimelineTest.anOlderReadNeverOverwritesANewerOne
missing/different | bluesky_following_recovery_test › response from an old AppView cannot appear in the new source | networks/bluesky BlueskyFollowing.kt (era) | an account's page asked of the old AppView could still be kept after the change (its request number restarted); now each AppView change starts a new era and older answers are not kept. BlueskyFollowingTimelineTest.anAnswerFromTheAppViewLeftIsNeverKept
covered | bluesky_following_recovery_test › follow-set change discards the old pending request | FollowingTimeline.refresh (cancels) | BlueskyFollowingTimelineTest.aChangeOfWhoIsFollowedDropsTheReadUnderWay…
covered | bluesky_following_recovery_test › disposal ignores late reads and leaves no loading work | BlueskyApp.close (ends its scope) | BlueskyFollowingTimelineTest.aChangeOfWhoIsFollowedDropsTheReadUnderWay…

## test/bluesky_home_recovery_test.dart
missing/different | bluesky_home_recovery_test › fresh Bluesky session restores the visible Following anchor without an author read | plugins/bluesky BlueskyFollowingPane.kt (FollowingTimeline.refresh, KeepsPlace), BlueskyPlace.kt | the anchor was restored but every account was read again at once; now the kept posts are shown and nothing is read until a pull, as XTA. BlueskyFollowingTimelineTest.aFreshSessionShowsThePostsWhereFollowingWasLeft…; anchor: BlueskyStoresTest.followingsListOpensAtItsAnchor…
covered | bluesky_home_recovery_test › Liked tab selection survives a fresh mounted session | BlueskyStores.kt (reading prefs: tab) | BlueskyStoresTest.theFiltersAndTheTabAreKeptAsXtaKeepsThem; the Liked tab reads only the device's likes (BlueskyLocalTest.theLikedTabShowsTheNewestKeptPosts…)
covered | bluesky_home_recovery_test › Following exposes local filter controls and reset without another network read | BlueskyFilters.kt, networks BlueskyReaderFilter.kt | the filter applies to the posts held (no read); BlueskyTimelineTest.theReaderQueryNeedsEveryWord…, replyAndRepostSwitchesCompose…; the golden image is Flutter's
n/a | bluesky_home_recovery_test › Following filter sheet fits a narrow RTL screen at double text size | BlueskyFilters.kt | a Flutter golden render and RenderFlex overflow check; Compose has no overflow exception to assert

## test/bluesky_import_test.dart
covered | bluesky_import_test › a bare name is not a Bluesky handle | networks/bluesky BlueskyUrls.handle | BlueskyUrlsTest.whatIsNoHandleIsRefused ("elios")
covered | bluesky_import_test › a dotted handle is | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBareWithAnAtOrInAProfileLink
covered | bluesky_import_test › so is a DID | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBare… (did:plc:abc234)
covered | bluesky_import_test › so is a pasted profile URL | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBare…
covered | bluesky_import_test › nothing typed is nothing | BlueskyUrls.handle | BlueskyUrlsTest.whatIsNoHandleIsRefused
covered | bluesky_import_test › a handle it cannot read is said so, not ignored | plugins/bluesky BlueskyImport.kt (ImportRun.start false → the field shows invalid_handle) | BlueskyImportTest.aHandleItCannotReadIsRefusedAndAsksNothing
untested | bluesky_import_test › correcting the field clears the complaint | BlueskyImport.kt (onValueChange clears `refused`) | UI only, read in code
## test/bluesky_interleaved_test.dart
covered | bluesky_interleaved_test › blueskyInterleavedItems keeps dated posts up to the limit | plugins/bluesky BlueskyApp.kt (HomeFeed.posts, SHARED = 10) | BlueskyImportTest.homeTakesTenDatedPostsAndLeavesRepliesOutWhereTheyAreOff
covered | bluesky_interleaved_test › blueskyInterleavedItems drops posts without a date | HomeFeed.posts | BlueskyImportTest.homeTakesTenDatedPostsAndLeavesRepliesOutWhereTheyAreOff
covered | bluesky_interleaved_test › a group with replies off leaves Bluesky replies out, and keeps them when on | HomeFeed.posts (FeedKinds) | BlueskyImportTest.homeTakesTenDatedPostsAndLeavesRepliesOutWhereTheyAreOff
## test/bluesky_likes_snapshot_test.dart
covered | bluesky_likes_snapshot_test › BlueskyPost snapshot round-trips all liked-post fields | networks/bluesky BlueskySnapshot.kt | BlueskySnapshotTest.whatThisAppWritesReadsBackTheSame, aPostXtaKeptReadsWholly

## test/bluesky_models_test.dart
covered | bluesky_models_test › accepts bare hosts and trims trailing slashes | networks/bluesky BlueskyUrls.appView | BlueskyUrlsTest.anAppViewIsMadeUsableFromABareHostOrAFullAddress
covered | bluesky_models_test › refuses empty or non-http values | BlueskyUrls.appView | BlueskyUrlsTest.anUnusableAppViewFallsBackToBlueskysOwn
covered | bluesky_models_test › blueskyAppViewFromPrefs falls back to the working default | BlueskyUrls.appViewOrDefault | BlueskyUrlsTest.anUnusableAppViewFallsBackToBlueskysOwn
covered | bluesky_models_test › accepts bare handles, @handles and profile URLs | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBareWithAnAtOrInAProfileLink
covered | bluesky_models_test › keeps dotted custom handles | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBare… (jay.bsky.team)
covered | bluesky_models_test › accepts did:plc identifiers | BlueskyUrls.handle | BlueskyUrlsTest.handlesComeBare…
covered | bluesky_models_test › refuses what is not a handle | BlueskyUrls.handle | BlueskyUrlsTest.whatIsNoHandleIsRefused
covered | bluesky_models_test › builds a bsky.app post URL from handle and at:// URI | BlueskyUrls.webUrl | BlueskyUrlsTest.aPostsWebLinkUsesItsHandleElseTheDidInItsUri
covered | bluesky_models_test › returns null when the URI has no rkey | BlueskyUrls.webUrl/rkeyOf | BlueskyUrlsTest.aPostsWebLink…, aRecordKeyIsTheLastOfThreeParts
covered | bluesky_models_test › reads text, author, images and date off a feed item | BlueskyParser.kt | BlueskyParserTest.aFeedItemReadsItsAuthorTextPicturesAndCounts
covered | bluesky_models_test › drops empty items and tolerates a reshaped feed | BlueskyParser.kt | BlueskyParserTest.postsWithNothingToShowOrNoUriAreDropped, aReshapedFeedIsEmptyNotACrash
covered | bluesky_models_test › keeps engagement counts and a repost reason | BlueskyParser.kt | BlueskyParserTest.aFeedItemReads…, aRepostKeepsWhoRepostedItAndWhen
covered | bluesky_models_test › reads a quote embed and an external link card | BlueskyParser.kt | BlueskyParserTest.aQuoteKeepsItsOwnPicturesButNotAQuoteOfItsOwn, anExternalEmbedIsALinkCard…
covered | bluesky_models_test › prefers fullsize, keeps aspect, and reads a video thumb | BlueskyParser.kt | BlueskyParserTest.picturesPreferTheirFullSizeKeepTheirShapeAndAltAndAppearOnce, aVideoPlaysItsPlaylist…
covered | bluesky_models_test › reads reply parent handle from a feed item | BlueskyParser.kt | BlueskyParserTest.aReplyNamesWhomAndWhatItAnswers
covered | bluesky_models_test › quoted viewRecord keeps embeds media | BlueskyParser.kt | BlueskyParserTest.aQuoteKeepsItsOwnPictures…
covered | bluesky_models_test › flattens parents and nested replies | BlueskyParser.kt | BlueskyParserTest.aThreadFlattensAncestorsRootFirstAndRepliesDepthFirst
covered | bluesky_models_test › reads documented fields defensively | BlueskyParser.profile | BlueskyParserTest.blankProfileFieldsAreNullAndANameFallsBackToTheHandle
covered | bluesky_models_test › reads banner and createdAt from the AppView | BlueskyParser.profile | BlueskyParserTest.aProfileReadsItsCountsBannerJoinDateAndPin
covered | bluesky_models_test › blank banner and createdAt stay null | BlueskyParser.profile | BlueskyParserTest.blankProfileFieldsAreNull…
covered | bluesky_models_test › reads bsky.app list URLs and AT-URIs | BlueskyUrls.listRef | BlueskyUrlsTest.listsFeedsAndStarterPacksAreNamedByLinkOrUri
covered | bluesky_models_test › parseBlueskyFollowsPage reads follows and cursor | BlueskyClient.follows | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints
covered | bluesky_models_test › parseBlueskyListMembersPage reads subjects | BlueskyParser.listMembers | BlueskyParserTest.aListComesWithItsMembers…
covered | bluesky_models_test › parseBlueskyFollowersPage reads followers and cursor | BlueskyClient.followers | BlueskyClientTest.feedsListsAndTheGraph…
covered | bluesky_models_test › a video keeps its HLS playlist alongside the thumbnail | BlueskyParser.kt | BlueskyParserTest.aVideoPlaysItsPlaylistWithItsThumbnailAsThePoster
covered | bluesky_models_test › a video inside recordWithMedia plays, beside the quote | BlueskyParser.kt | BlueskyParserTest.recordWithMediaCarriesTheQuoteBesideItsClip
covered | bluesky_models_test › the playlist survives a liked-post snapshot | BlueskySnapshot.kt | BlueskySnapshotTest.aPostXtaKeptReadsWholly / whatThisAppWritesReadsBackTheSame (videoUrls)

## test/bluesky_profile_chrome_test.dart
covered | bluesky_profile_chrome_test › home remounts with posts already painted do not fetch | networks/bluesky BlueskyFollowing.kt (ten-minute reuse), plugins/bluesky FollowingTimeline | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced, BlueskyFollowingTimelineTest.followingAnotherAccount… (one request)
covered | bluesky_profile_chrome_test › the first empty paint still fetches | FollowingTimeline.start | BlueskyFollowingTimelineTest.anEmptyAnswerIsNotAskedAgainOnEntryButAPullAsks
covered | bluesky_profile_chrome_test › pull-to-refresh fetches even when posts are on screen | FollowingTimeline.refresh(force) | BlueskyFollowingTimelineTest.anEmptyAnswerIsNotAskedAgain…ButAPullAsks
covered | bluesky_profile_chrome_test › profile identity matches the X header: name, handle, joined, counts | plugins/bluesky BlueskyProfileScreen.kt (Header, About: name, @handle, description, Joined, following/followers) | UI only, read in code; the parsed fields in BlueskyParserTest.aProfileReadsItsCountsBannerJoinDateAndPin. Native's profile screen carries Follow in the header as XTA's screen does
## test/bluesky_profile_saved_test.dart
covered | bluesky_profile_saved_test › Saved on a profile is only that author's local likes | networks/bluesky BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTabListsOnlyItsAuthorsLikedPosts
covered | bluesky_profile_saved_test › a DID match still counts after a handle rename | BlueskyLocal.likedBy (DID first) | BlueskyLocalTest.aProfilesLikesTab… (by DID with no handle)
covered | bluesky_profile_saved_test › handle match is case-insensitive | BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTab… ("MAYA.bsky.social")
covered | bluesky_profile_saved_test › blank actor does not dump every local like onto the tab | BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTab… (blank → empty)

## test/bluesky_profile_upgrade_test.dart
covered | bluesky_profile_upgrade_test › metadata failure does not block refresh of an already loaded feed | plugins/bluesky BlueskyProfileScreen.kt (ProfileState, tabs on their own PagedItems) | BlueskyProfileStateTest.aFailedProfileReadKeepsWhatWasShownAndItsPostsReadAgain
covered | bluesky_profile_upgrade_test › failed refresh retries page one without mistaking old cursor for pagination | ui/plugins PagedItems.refresh | BlueskyProfileStateTest.aFailedProfileRead… (page one asked without a cursor), BlueskySourcesTest.aFailedRefreshKeepsThePostsShown…
covered | bluesky_profile_upgrade_test › failed next page retries its cursor and preserves earlier cards | PagedItems.more/retryMore | BlueskySourcesTest.aFailedPageKeepsItsCursorForATryAgain
covered | bluesky_profile_upgrade_test › cursor cycles terminate while new posts from the final page survive | PagedItems.more | BlueskySourcesTest.pagingAsksOnceAtATime…StopsAtARepeatedCursor ("third" kept)
covered | bluesky_profile_upgrade_test › refresh invalidates an older pagination result | PagedItems.refresh (cancels the page under way) | BlueskyProfileStateTest.aRefreshDropsAPageStillComingOfTheListBefore
missing/different | bluesky_profile_upgrade_test › a handle resolving to a new DID clears old identity feeds and pending pages | ProfileState.load | the tabs kept reading the old DID; now they are dropped when the handle names another account. BlueskyProfileStateTest.aHandleNowNamingAnotherAccountDropsTheOldAccountsPages
missing/different | bluesky_profile_upgrade_test › switching AppView rejects late results and reloads from the selected source | BlueskyProfileScreen (state kept per AppView and actor) | the profile stayed on the AppView it was opened with; now another AppView reads it anew and the old one's late answers land in a state no longer shown. UI keying only, read in code
covered | bluesky_profile_upgrade_test › pin is first and appears once only in Posts | networks/bluesky BlueskyProfileTab.withPin | BlueskyTimelineTest.profileTabsShowTheirOwnPostsAndPostsStartsWithThePinOnce
covered | bluesky_profile_upgrade_test › pin failure retains the feed and has an independent retry | ProfileState.readPin/retryPin | BlueskyProfileStateTest.aPinThatFailsKeepsTheFeedAndIsTriedAgainOnItsOwnAndARemovedPinGoes
covered | bluesky_profile_upgrade_test › a removed pin and disposed late pin do not remain visible | ProfileState.load (pin kept only while the profile still pins it; scope ends with the screen) | BlueskyProfileStateTest.aPinThatFailsKeepsTheFeedAndIsTriedAgainOnItsOwnAndARemovedPinGoes
covered | bluesky_profile_upgrade_test › bio links preserve UTF-8 offsets and balanced URL punctuation | networks/bluesky BlueskyFacets.profileLinks | BlueskyFacetsTest.bioLinksKeepUtf8OffsetsAndBalancedPunctuation
untested | bluesky_profile_upgrade_test › profile metadata fits narrow large text and exposes posts count | BlueskyProfileScreen.kt (ProfileDetails: post_count plural, Count ≥ 48 dp) | UI only, read in code (the bio is LinkedText, not selectable)
covered | bluesky_profile_upgrade_test › Posts identifies and renders the pinned card once | BlueskyProfileScreen.postRows, NetworkPost.pinned | UI; the once-only rule is BlueskyTimelineTest.profileTabsShow…PinOnce

## test/bluesky_protocol_regression_test.dart
covered | bluesky_protocol_regression_test › leading whitespace and Unicode retain the exact linked facet span | networks/bluesky BlueskyFacets.kt | BlueskyFacetsTest.leadingWhitespaceAndUnicodeKeepTheExactMentionSpan
covered | bluesky_protocol_regression_test › repost activity date remains separate from publication through offline snapshots | BlueskySnapshot.kt, BlueskyParser.kt | BlueskyParserTest.aRepostKeepsWhoRepostedItAndWhen, BlueskySnapshotTest.whatThisAppWritesReadsBackTheSame
covered | bluesky_protocol_regression_test › malformed optional cached fields cannot discard other saved posts | BlueskySnapshot.post/posts | BlueskySnapshotTest.damagedOptionalFieldsOfOnePostLoseNeitherItNorTheOthers
covered | bluesky_protocol_regression_test › cached quote nesting is bounded without losing the outer post | BlueskySnapshot.post (depth) | BlueskySnapshotTest.deepQuotesStopWhereXtaStops (the post and four quotes)
covered | bluesky_protocol_regression_test › profile pin reads its strong-reference URI safely | BlueskyParser.profile | BlueskyParserTest.aProfileReadsItsCountsBannerJoinDateAndPin, blankProfileFieldsAreNull… (pinnedPost: true → none)
covered | bluesky_protocol_regression_test › post links fall back to the stable DID when the handle is missing | BlueskyUrls.webUrl | BlueskyUrlsTest.aPostsWebLinkUsesItsHandleElseTheDidInItsUri
covered | bluesky_protocol_regression_test › pinned generator metadata survives a flat preference round trip | BlueskySnapshot.feed | BlueskySnapshotTest.pinnedFeedsAndListsReadAsXtaKeptThem, BlueskyReadingTest.pinsReadAsXtaWroteThem
covered | bluesky_protocol_regression_test › pinned list counts and metadata survive a flat preference round trip | BlueskySnapshot.list | same tests
covered | bluesky_protocol_regression_test › getPosts sends repeated URI parameters in deduplicated batches of at most 25 | BlueskyClient.posts | BlueskyClientTest.postsAreAskedInDeduplicatedBatchesOf25
covered | bluesky_protocol_regression_test › post search carries encoded author, tags, order and cursor parameters | BlueskyClient.searchPosts | BlueskyClientTest.postSearchOnAnotherAppViewCarriesItsParametersAndItsCursor
covered | bluesky_protocol_regression_test › post search from Bluesky's AppView goes to its API and pages by time, not by cursor | BlueskyClient.searchPosts | BlueskyClientTest.postSearchFromBlueskysAppViewGoesToItsApiAndPagesByTime
covered | bluesky_protocol_regression_test › post search stops where the API has no further page | BlueskyParser.searchCursor | BlueskyParserTest.theSearchCursorAsksForPostsOlderThanTheOldestShown
covered | bluesky_protocol_regression_test › actor search carries cursor and reads the next public page | BlueskyClient.searchActorsPage | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints (cursor "second/page=")

## test/threads_account_safety_test.dart
covered | threads_account_safety_test › two reads started together do not reach Meta together | ThreadsPacingTest.twoSessionReadsStartedTogetherLeaveTheSessionGapApart
covered | threads_account_safety_test › a second read reuses the stored id instead of searching again | ThreadsDirectClientTest.anAccountsIdIsSearchedForOnceAndRemembered
covered | threads_account_safety_test › the id survives a restart, so a fresh client does not search either | ThreadsDirectClientTest.aGuestCardRemembersItsAccountsId
covered | threads_account_safety_test › a throttle is remembered past a restart | ThreadsFailuresTest.aThrottleIsRememberedPastARestart
covered | threads_account_safety_test › an expired cooldown lets reading resume and clears itself | ThreadsFailuresTest.anEndedCooldownLetsReadingResumeAndClearsItself

## test/threads_conversation_test.dart
covered | threads_conversation_test › splits the focus chain into ancestors, the post and its continuation | ThreadsConversationTest.theFocusChainSplitsIntoWhatItAnswersThePostAndItsContinuation
covered | threads_conversation_test › a link stub finds its post by short code, whatever the host | ThreadsConversationTest.aLinkStubFindsItsPostByShortCodeWhateverTheHost
covered | threads_conversation_test › a short link whose code is not on the page focuses the first root | ThreadsConversationTest.aShortLinkWhoseCodeIsNotOnThePageFocusesTheFirstRoot
covered | threads_conversation_test › an opened repost keeps saying who reposted it | ThreadsConversationTest.anOpenedRepostKeepsSayingWhoRepostedIt
covered | threads_conversation_test › an empty page keeps the seed | ThreadsConversationTest.anEmptyPageKeepsTheSeed
covered | threads_conversation_test › long reply threads are cut until opened | ThreadsConversationTest.aLongReplyThreadIsCutUntilItIsOpened
covered | threads_conversation_test › an opened thread shows everything | ThreadsConversationTest.anOpenedThreadShowsEverything
covered | threads_conversation_test › author-only keeps the threads the author took part in | ThreadsConversationTest.theAuthorsRepliesAreTheThreadsTheAuthorTookPartIn
covered | threads_conversation_test › Top keeps the order Meta sent | ThreadsConversationTest.topKeepsTheOrderMetaSent
covered | threads_conversation_test › Newest and Oldest move each reply thread whole, by its first reply | ThreadsConversationTest.newestAndOldestMoveEachReplyThreadWholeByItsFirstReply
covered | threads_conversation_test › an order Threads does not offer keeps Meta's | ThreadsConversationTest.topKeepsTheOrderMetaSent
covered | threads_conversation_test › the order applies to the author's replies too | ThreadsConversationTest.theOrderAppliesToTheAuthorsRepliesToo
covered | threads_conversation_test › choosing an order keeps the rest of the view | ThreadsConversationTest.aRealPageSortsItsReplyThreadsByTheirFirstReply
covered | threads_conversation_test › reads the page once and reuses it on the next open | ThreadsReaderTest.aConversationIsReadOnceAndReusedOnTheNextOpen
covered | threads_conversation_test › a failed read keeps the tapped post on screen | ThreadsReaderTest.aFailedConversationReadSaysWhy
covered | threads_conversation_test › a post without a permalink needs no network | ThreadsReaderTest.aPostWithoutALinkHasNoConversationToRead

## test/threads_direct_test.dart
covered | threads_direct_test › splits a Cookie header into a map | ThreadsSessionTest.aPastedCookieHeaderSplitsIntoItsCookies
covered | threads_direct_test › rejects incomplete cookies | ThreadsSessionTest.incompleteCookiesAreNoSession
covered | threads_direct_test › accepts IGT:2 with or without Bearer prefix | ThreadsSessionTest.aBearerIsAnIgt2TokenWithOrWithoutItsPrefix
covered | threads_direct_test › reads caption, user and permalink from thread_items | ThreadsParserTest.aRootCarriesItsAuthorCountsAndPermalink
covered | threads_direct_test › picks the largest image candidate and reply_to_author | ThreadsParserTest.theLargestCandidateWinsAndTheOriginalSizeIsTheFallbackShape
covered | threads_direct_test › reads likes, replies, reposts and a link preview | ThreadsParserTest.aRootCarriesItsAuthorCountsAndPermalink
covered | threads_direct_test › unwraps share_info.reposted_post and keeps the reposter | ThreadsParserTest.aRepostIsReadAsTheOriginalRepostedAtTheRepostsTime
covered | threads_direct_test › pulls posts out of data-sjs blobs | ThreadsPagesTest.aProfilePageKeepsTheThreadsTheProfileWroteOrRepostedWithTheirLength
covered | threads_direct_test › profile parse keeps only the root of each thread_items list | ThreadsPagesTest.aProfilePageKeepsOnlyTheRootOfEachChain
covered | threads_direct_test › keeps reposts when matching the reposter handle | ThreadsParserTest.aRepostIsReadAsTheOriginalRepostedAtTheRepostsTime
covered | threads_direct_test › keeps every reply in the thread_items chain | ThreadsPagesTest.eachThreadItemsListIsAChainAndARepeatedPostStaysWhereItFirstAppeared
covered | threads_direct_test › extractThreadsLsd reads the LSD token blob | ThreadsPagesTest.theLsdTokenAndTheOwnersIdAreReadOffThePage
covered | threads_direct_test › extractThreadsUserIdFromHtml prefers props.user_id on logged-out pages | ThreadsPagesTest.theOwnersIdIsPropsUserIdElseAPkNearTheHandleElseTheMostNamedUserId
covered | threads_direct_test › extractThreadsUserIdFromHtml falls back to pk near username | ThreadsPagesTest.theOwnersIdIsPropsUserIdElseAPkNearTheHandleElseTheMostNamedUserId
covered | threads_direct_test › extractThreadsUserIdFromHtml falls back to modal userID and ignores 0 | ThreadsPagesTest.theOwnersIdIsPropsUserIdElseAPkNearTheHandleElseTheMostNamedUserId
covered | threads_direct_test › threadsProfileFromPosts builds a card when OG scrape is empty | ThreadsPagesTest.aProfilePiecedTogetherFromPostsKnowsNoCount
covered | threads_direct_test › threadsProfileFromGuestHtml reads OG title, description and image | ThreadsPagesTest.aProfilePageGivesTheCardAGuestIsShown
covered | threads_direct_test › parseThreadsCompactCount reads K/M suffixes | ThreadsPagesTest.compactCountsReadTheirSuffixes
covered | threads_direct_test › parseThreadsGraphqlFeed reads mediaData.threads | ThreadsParserTest.aGraphqlProfileTabGivesEachThreadsRootAndSkipsAPostWithoutAnAuthor
covered | threads_direct_test › fetchFollowingTimeline sends Bearer to Instagram API | ThreadsDirectClientTest.theBearerReadsItsHomeTimelineFromTheAppApi
covered | threads_direct_test › maps login_required to sessionSuspended | ThreadsFailuresTest.aLostSessionWaitsForTheReaderAndIsParked
covered | threads_direct_test › fetchGuestAccount uses GraphQL after reading LSD and user id from HTML | ThreadsDirectClientTest.aGuestReadsAProfilesThreadsThroughGraphqlWithTheTokenAndIdOfItsPage
covered | threads_direct_test › profile + posts for one handle share a single HTML GET | ThreadsDirectClientTest.aProfilesCardAndThreadsAskedTogetherShareOnePageRead
covered | threads_direct_test › fetchUserThreads falls back to guest GraphQL when cookies are refused | ThreadsDirectClientTest.whenTheCookieApiRefusesTheGuestReadStandsIn
covered | threads_direct_test › guest GraphQL still works while a cookie session is cooling down | ThreadsFailuresTest.guestReadsGoOnWhileTheSessionCoolsDown
covered | threads_direct_test › fetchGuestAccount falls back to SSR when GraphQL returns empty | ThreadsDirectClientTest.whenGraphqlComesBackEmptyOrAsAPageThePagesOwnThreadsStandIn
covered | threads_direct_test › fetchGuestPostThread scrapes root and replies from a post URL | ThreadsDirectClientTest.aPostPageIsReadOnceAndSharedByItsShortCodeForTenMinutes; ThreadsPagesTest.aPostPageFlattensToItsRootAndReplies
covered | threads_direct_test › pasted cookies do not hit cookie REST unless the reader opts in | ThreadsDirectClientTest.pastedCookiesAreNotSpentOnAnAccountUnlessTheReaderOptsIn
covered | threads_direct_test › cookie REST uses a browser UA, not the official app UA | ThreadsDirectClientTest.withSessionReadsTheCookieApiReadsTheAccountAsABrowserWould

## test/threads_discovery_test.dart
covered | threads_discovery_test › reposted original authors come first and skip follows | ThreadsDiscoveryTest.repostedAuthorsComeFirstAndFollowedOnesAreSkipped
covered | threads_discovery_test › caps the strip and ignores empty handles | ThreadsDiscoveryTest.theStripIsCappedAndEmptyHandlesAreIgnored
covered | threads_discovery_test › starter handles are the public ones a guest can try | ThreadsDiscoveryTest.theStarterHandlesAreThePublicOnesAGuestCanTry

## test/threads_feed_snapshot_test.dart
covered | threads_feed_snapshot_test › showing everything changes nothing | ThreadsFeedTest.theFiltersAreReadAsXtaWritesThemAndHideWhatTheyName
covered | threads_feed_snapshot_test › hides replies and reposts independently | ThreadsFeedTest.theFiltersAreReadAsXtaWritesThemAndHideWhatTheyName
covered | threads_feed_snapshot_test › media and links narrow to what carries them | ThreadsFeedTest.theFiltersAreReadAsXtaWritesThemAndHideWhatTheyName
covered | threads_feed_snapshot_test › choices survive a restart and a bad stored value reads as none | ThreadsFeedTest.theFiltersAreReadAsXtaWritesThemAndHideWhatTheyName
covered | threads_feed_snapshot_test › a restart inside the cache window paints at once and asks Meta nothing | ThreadsReaderTest.aFeedKeptAcrossARestartAsksMetaNothingWithinTenMinutes
covered | threads_feed_snapshot_test › an account unfollowed since is not restored | ThreadsFeedTest.theSnapshotKeepsTheNewestPostsAndWhenEachAccountAnswered
covered | threads_feed_snapshot_test › a reposted post belongs to the account that reposted it | ThreadsSharedTest.aMixedFeedTakesTenOfEachAccountARepostCountingForItsReposter
covered | threads_feed_snapshot_test › forgetting empties the feed and its snapshot | ThreadsFeedTest.theSnapshotKeepsTheNewestPostsAndWhenEachAccountAnswered
covered | threads_feed_snapshot_test › a damaged snapshot restores nothing | ThreadsFeedTest.aSnapshotOfAnotherShapeIsNoSnapshot

## test/threads_feed_store_test.dart
covered | threads_feed_store_test › refresh loads Accounts even when a Bearer is pasted | ThreadsReaderTest.aPastedBearerDoesNotHideTheFollowedAccounts
covered | threads_feed_store_test › refresh falls back to guest when RSSHub returns nothing | ThreadsReaderTest.anRssHubInstanceIsAskedFirstAndTheGuestReadStandsInWhenItHasNothing
covered | threads_feed_store_test › soft refresh failure keeps prior posts on screen | ThreadsReaderTest.aRefreshShowsWhatEachAccountSaidLastWhileItWaits
covered | threads_feed_store_test › refresh with cookies still uses guest unless session APIs are opted in | ThreadsReaderTest.cookiesAloneDoNotSpendTheSessionOnFollowedAccounts

## test/threads_feeds_parity_test.dart
covered | threads_feeds_parity_test › each followed account brings up to ten posts, in the order they came | ThreadsSharedTest.aMixedFeedTakesTenOfEachAccountARepostCountingForItsReposter
covered | threads_feeds_parity_test › a repost counts for the account that reposted it | ThreadsSharedTest.aMixedFeedTakesTenOfEachAccountARepostCountingForItsReposter
covered | threads_feeds_parity_test › a feed read offline shows a Threads post as a Threads card | ThreadsSharedTest.theSharedRowShowsWhoRepostedWhomItAnswersAndOnlyTheCountsMetaGave
covered | threads_feeds_parity_test › a link post kept before still reads back as a link post | ThreadsPostJsonTest.aPostKeptReadsBackAsItWasEveryRenditionAndAQuoteOneLevelDeep
covered | threads_feeds_parity_test › take the name and picture their posts show now | ThreadsKeptTest.followedAccountsKeepTheNameAndPictureTheirNewestPostsShow
covered | threads_feeds_parity_test › nothing changes when the posts show them as kept, or show nothing | ThreadsKeptTest.followedAccountsKeepTheNameAndPictureTheirNewestPostsShow
covered | threads_feeds_parity_test › a saved Threads post offers its pictures and videos to keep offline | ThreadsSharedTest.aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack

## test/threads_likes_snapshot_test.dart
covered | threads_likes_snapshot_test › ThreadsPost snapshot round-trips all liked-post fields | ThreadsPostJsonTest.aPostKeptReadsBackAsItWasEveryRenditionAndAQuoteOneLevelDeep; ThreadsKeptTest.aLikeIsKeptHereItsPostInXtasPreferenceNewestFirst

## test/threads_parity_render_test.dart
untested → ported | threads_parity_render_test › (render sweeps) | Compose (Threads reader) | golden/layout sweeps; no Compose golden pipeline

## test/threads_parse_test.dart
covered | threads_parse_test › a video keeps its poster and the largest playable rendition | ThreadsParserTest.aVideoKeepsEveryFileLargestFirstOneFileAndOneQualityEach
covered | threads_parse_test › every rendition is kept, largest first, one file and one quality each | ThreadsParserTest.aVideoKeepsEveryFileLargestFirstOneFileAndOneQualityEach
covered | threads_parse_test › a carousel mixes stills and videos, each with its description | ThreadsParserTest.aCarouselKeepsEachPicturesLargestFileItsShapeAndDescriptionAndItsVideos
covered | threads_parse_test › reads the quoted post one level deep | ThreadsParserTest.aQuoteIsReadOneLevelDeepAndANumericIdIsKept
covered | threads_parse_test › a quote with no words of its own is still a post | ThreadsParserTest.aQuoteWithNoWordsOfItsOwnIsStillAPostUnderEitherName
covered | threads_parse_test › reads the topic tag without its hash | ThreadsParserTest.metasFragmentsResolveMentionsUnwrappedLinksAndTags
covered | threads_parse_test › ids sent as numbers by the app API are kept | ThreadsParserTest.aQuoteIsReadOneLevelDeepAndANumericIdIsKept
covered | threads_parse_test › resolves mentions, unwrapped links and tags | ThreadsParserTest.metasFragmentsResolveMentionsUnwrappedLinksAndTags
covered | threads_parse_test › a split that does not spell the caption is ignored | ThreadsParserTest.aSplitThatDoesNotSpellTheCaptionOrIsOnlyTextIsIgnored
covered | threads_parse_test › plain-text-only fragments add nothing over the caption | ThreadsParserTest.aSplitThatDoesNotSpellTheCaptionOrIsOnlyTextIsIgnored
covered | threads_parse_test › caption runs fall back to patterns without fragments | ThreadsCaptionTest.withoutMetasFragmentsTheRunsComeFromPatternsAndSpellTheCaption
covered | threads_parse_test › a profile bucket counts the author's continuation only | ThreadsParserTest.theAuthorsContinuationIsCountedAndAReplyFromAnyoneElseEndsIt
covered | threads_parse_test › SSR profile scrape keeps the chain length too | ThreadsPagesTest.aProfilePageKeepsTheThreadsTheProfileWroteOrRepostedWithTheirLength
covered | threads_parse_test › threadsSsrChains keeps each thread_items list as a chain | ThreadsPagesTest.eachThreadItemsListIsAChainAndARepeatedPostStaysWhereItFirstAppeared
covered | threads_parse_test › the replies page keeps only the profile's own replies | ThreadsPagesTest.theRepliesPageKeepsOnlyTheProfilesOwnReplies
covered | threads_parse_test › every new field survives a round trip, quotes one level deep | ThreadsPostJsonTest.aPostKeptReadsBackAsItWasEveryRenditionAndAQuoteOneLevelDeep
covered | threads_parse_test › an old snapshot without the new fields still reads | ThreadsPostJsonTest.aPostXtaKeptBeforeRenditionsPlaysItsOneVideo
covered | threads_parse_test › threadsShortcodeOf reads post and short links on either host | ThreadsLinksTest.theShortCodeIsReadOffPostAndShortLinksOnEitherHost

## test/threads_profile_card_test.dart
covered | threads_profile_card_test › knows the counts the page gave, not whom it follows, and does not link to itself | ThreadsRulesTest.aProfileShowsTheCountsMetaToldAndNoLinkToItself
covered | threads_profile_card_test › pieced together from posts knows no count | ThreadsPagesTest.aProfilePiecedTogetherFromPostsKnowsNoCount
covered | threads_profile_card_test › a guest profile shows the counts it knows, no following count and no link to itself | ThreadsPagesTest.aGuestCardKnowsNoFollowingCountAndNoLink
covered | threads_profile_card_test › a profile that knows no count shows none | ThreadsPagesTest.aProfilePiecedTogetherFromPostsKnowsNoCount
covered | threads_profile_card_test › the bio's @mentions and links can be tapped | ThreadsCaptionTest.metasOwnFragmentsWinOverPatterns

## test/threads_profile_store_test.dart
covered | threads_profile_store_test › tabs split posts, replies and media, without repeats | ThreadsReaderTest.tabsSplitThreadsRepliesAndMediaWithoutRepeatsNewestFirst
covered | threads_profile_store_test › the replies page is asked for once, and a failure falls back to the posts | ThreadsReaderTest.aRepliesPageThatFailsLeavesTheRepliesTheThreadsCarried
covered | threads_profile_store_test › a failed refresh keeps the profile already on screen | ThreadsReaderTest.aProfileIsItsCardAndItsThreadsReadTogetherFromOnePage
covered | threads_profile_store_test › nothing at all to show surfaces the error | ThreadsReaderTest.aProfileWithNeitherCardNorThreadsSurfacesTheFailure

## test/threads_reader_widget_test.dart
untested → ported | threads_reader_widget_test › a quoting, tagged, chained card fits a narrow phone in German | Compose (Threads card) | layout is Compose-only
untested → ported | threads_reader_widget_test › the conversation shows context, the author's thread and cut reply threads | ThreadsConversationTest.theFocusChainSplitsIntoWhatItAnswersThePostAndItsContinuation | the drawing is Compose-only
untested → ported | threads_reader_widget_test › replies can be sorted, each reply thread moving whole, and the order holds for the session | ThreadsConversationTest.newestAndOldestMoveEachReplyThreadWholeByItsFirstReply | the gesture is Compose-only
untested → ported | threads_reader_widget_test › the replies header, author filter and order fit a narrow phone in German | Compose (Threads reader) | layout is Compose-only
covered | threads_reader_widget_test › every file Meta offers is a quality named by its lines; a post kept before plays its one file | ThreadsRulesTest.aVideoPlaysWhatTheQualitySettingPicksAndOffersEveryRenditionByItsLines
covered | threads_reader_widget_test › several pictures lie along one row at one height, the next one in view | ThreadsRulesTest.severalPicturesShareOneRowTheNextOnesEdgeInView
untested → ported | threads_reader_widget_test › one picture keeps its own shape, and opens in X's viewer | Compose (Threads card) | the viewer is shared; Compose-only
untested → ported | threads_reader_widget_test › a card follows the absolute-time setting | Compose (Threads card) | Compose only
untested → ported | threads_reader_widget_test › the opened post says its time and date under it, and its text can be selected | Compose (Threads reader) | Compose only
covered | threads_reader_widget_test › a conversation opens on the post that was tapped, what it answers a scroll up | ThreadsConversationTest.theFocusChainSplitsIntoWhatItAnswersThePostAndItsContinuation
untested → ported | threads_reader_widget_test › a tapped card opens as the card that is dragged back | Compose (Threads reader) | Compose only
untested → ported | threads_reader_widget_test › a ⋯ button in the header opens the actions a long press opened | Compose (Threads reader) | Compose only
untested → ported | threads_reader_widget_test › the buttons are named for screen readers, and small targets are 48 dp | Compose accessibility (Threads) | Compose only
covered | threads_reader_widget_test › a quoted post shows its link card, as the post itself does | ThreadsParserTest.aQuoteIsReadOneLevelDeepAndANumericIdIsKept
covered | threads_reader_widget_test › a #tag in a caption Meta sent as plain text opens the posts under it | ThreadsCaptionTest.tagsAreFoundInPlainTextButNotInsideALinkOrAWord
untested → ported | threads_reader_widget_test › with the gesture on, a double-tap likes the post here and a tap still opens it | Compose (Threads card setting) | Compose only
untested → ported | threads_reader_widget_test › with the gesture off, a double-tap likes nothing and a tap opens the post at once | Compose (Threads card setting) | Compose only

## test/threads_refresh_order_test.dart
covered | threads_refresh_order_test › successive pulls to refresh reach every followed account | ThreadsReaderTest.successivePullsToRefreshReachEveryFollowedAccount

## test/threads_retry_test.dart
covered | threads_retry_test › a session throttle waits for the cooldown Meta's answer armed, and so does the next read | ThreadsFailuresTest.aSessionThrottleWaitsForTheCooldownMetasAnswerArmedAndSoDoesTheNextRead
covered | threads_retry_test › a lost session waits for the reader | ThreadsFailuresTest.aLostSessionWaitsForTheReaderAndIsParked
covered | threads_retry_test › a guest throttle waits as long as any limit without a stated reset | ThreadsFailuresTest.aGuestThrottleWaitsAsLongAsAnyLimitWithoutAStatedResetAndParksNoSession
covered | threads_retry_test › a host that did not answer is tried again on the usual backoff | ThreadsFailuresTest.aHostThatDidNotAnswerIsTriedAgainOnTheUsualBackoff
covered | threads_retry_test › without a setup, or with the session refused, nothing retries on its own | ThreadsFailuresTest.withoutASetupOrWithTheSessionRefusedNothingRetriesOnItsOwn

## test/threads_rich_text_test.dart
covered | threads_rich_text_test › finds @handles and http(s) URLs | ThreadsCaptionTest.handlesAndWebAddressesAreFound
covered | threads_rich_text_test › finds #tags in plain text, not inside a link or a word | ThreadsCaptionTest.tagsAreFoundInPlainTextButNotInsideALinkOrAWord
covered | threads_rich_text_test › ignores email-shaped text as a mention | ThreadsCaptionTest.emailShapedTextIsNoMention
covered | threads_rich_text_test › trimThreadsCaptionUrl drops trailing punctuation | ThreadsCaptionTest.trailingPunctuationIsNotPartOfAnAddress

## test/threads_saving_test.dart
covered | threads_saving_test › keeps the whole post and is filed under Threads | ThreadsSharedTest.aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack
covered | threads_saving_test › keeps the id a link save used, and an old link save is filed under Threads too | ThreadsSharedTest.aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack
covered | threads_saving_test › offers its pictures, not its video, to the saved media | ThreadsSharedTest.aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack
covered | threads_saving_test › a damaged blob is nothing rather than a crash | ThreadsPostJsonTest.aRowWithoutAnIdIsDropped
covered | threads_saving_test › a post offers the bookmark in its actions | ThreadsSharedTest.aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack | the action row is Compose-only
covered | threads_saving_test › Saved shows a saved Threads post as a Threads card | ThreadsSharedTest.theSharedRowShowsWhoRepostedWhomItAnswersAndOnlyTheCountsMetaGave

## test/threads_search_query_test.dart
covered | threads_search_query_test › a post link names the post, and is not kept in the history | ThreadsLinksTest.aPostLinkNamesThePostAndIsNotKeptInTheHistory
covered | threads_search_query_test › a #tag names the posts filed under it, spaces and all | ThreadsLinksTest.aTagNamesThePostsFiledUnderItSpacesAndAll
covered | threads_search_query_test › an @handle or a profile link names the profile | ThreadsLinksTest.anAtHandleOrAProfileLinkNamesTheProfile
covered | threads_search_query_test › other words search posts, offering the handle they also spell | ThreadsLinksTest.otherWordsSearchPostsOfferingTheHandleTheyAlsoSpell
covered | threads_search_query_test › the field shows a tag with its # | ThreadsLinksTest.theFieldShowsATagWithItsHash

## test/threads_search_sheet_test.dart
covered | threads_search_sheet_test › a guest typing $typed gets the page it names | ThreadsDirectClientTest.aGuestSearchReadsThePublicSearchPageAsALoggedOutBrowser

## test/threads_search_test.dart
covered | threads_search_test › takes, in each chain, the post the page found it for | ThreadsPagesTest.aSearchPageGivesInEachChainThePostItFoundTheChainFor
covered | threads_search_test › takes a tag search by the topic the post is filed under | ThreadsLinksTest.aTagNamesThePostsFiledUnderItSpacesAndAll
covered | threads_search_test › asks the public search page: top, recent, or a tag | ThreadsLinksTest.theSearchPageIsAskedForTopRecentOrATag
covered | threads_search_test › a guest search reads the page as a logged-out browser and returns its posts | ThreadsDirectClientTest.aGuestSearchReadsThePublicSearchPageAsALoggedOutBrowser
covered | threads_search_test › shows the top posts as Threads cards, and Recent when chosen | ThreadsLinksTest.theSearchPageIsAskedForTopRecentOrATag
untested → ported | threads_search_test › says so when nothing is found | Compose (Threads search) | Compose only
untested → ported | threads_search_test › a failed read offers to try again | Compose (Threads search) | Compose only
covered | threads_search_test › words that also spell a handle offer that profile on top | ThreadsLinksTest.otherWordsSearchPostsOfferingTheHandleTheyAlsoSpell
untested → ported | threads_search_test › the title holds the query, and other words search again on the page, in the order chosen | Compose (Threads search) | Compose only
covered | threads_search_test › a #tag searches its posts on the page; an @handle or a post link opens its page | ThreadsLinksTest.aTagNamesThePostsFiledUnderItSpacesAndAll, anAtHandleOrAProfileLinkNamesTheProfile, aPostLinkNamesThePostAndIsNotKeptInTheHistory

## test/threads_test.dart
covered | threads_test › unwraps l.threads.com redirects and leaves other URLs alone | ThreadsLinksTest.threadsOutboundRedirectIsUnwrappedAndOtherLinksAreLeftAlone
covered | threads_test › takes a bare handle, an @handle and a profile link alike | ThreadsLinksTest.aBareHandleAnAtHandleAndAProfileLinkAreTheSameAccount
covered | threads_test › keeps the dots and underscores handles are allowed | ThreadsLinksTest.aBareHandleAnAtHandleAndAProfileLinkAreTheSameAccount
covered | threads_test › refuses what is not a handle at all | ThreadsLinksTest.whatIsNotAHandleAtAllIsRefused
covered | threads_test › asks the instance for JSON, whatever trailing slashes it was given | ThreadsRssHubTest.theInstanceIsAskedForJsonWhateverTrailingSlashesItWasGiven
covered | threads_test › reads text, link and date off an item | ThreadsRssHubTest.anItemGivesItsWordsLinkDateAndPictures
covered | threads_test › takes the author from the feed title, without the site suffix | ThreadsRssHubTest.theAuthorIsTheFeedsTitleWithoutTheSiteUnlessAnItemNamesItsOwn
covered | threads_test › falls back to the handle when the feed titles itself uselessly | ThreadsRssHubTest.theAuthorIsTheFeedsTitleWithoutTheSiteUnlessAnItemNamesItsOwn
covered | threads_test › collects the pictures the html embeds and the attachments declare | ThreadsRssHubTest.anItemGivesItsWordsLinkDateAndPictures
covered | threads_test › keeps a picture-only post but drops an empty one | ThreadsRssHubTest.aPictureOnlyItemIsKeptAndAnEmptyOneOrOneWithoutAnyIdIsNot
covered | threads_test › a reshaped feed yields nothing rather than throwing | ThreadsRssHubTest.aReshapedFeedYieldsNothingRatherThanThrowing
covered | threads_test › reads every documented field | ThreadsXyTest.aProfileReadsEveryDocumentedField
covered | threads_test › external_url is nullable, and blank counts as absent | ThreadsXyTest.aLinkIsOptionalAndABlankOneIsNone
covered | threads_test › a field that stopped being sent leaves a blank, not a crash | ThreadsXyTest.aFieldThatStoppedBeingSentLeavesABlankNotACrash
covered | threads_test › a reshaped response yields an empty profile rather than throwing | ThreadsXyTest.aProfileReadsEveryDocumentedField
covered | threads_test › a profile becomes a followable account, carrying its face | ThreadsKeptTest.accountsAreXtasRowsListedByNameWhateverTheCase
covered | threads_test › builds endpoints under the configured base, trailing slashes and all | ThreadsXyTest.endpointsLieUnderTheConfiguredBaseTrailingSlashesAndAll

## test/threads_time_to_content_test.dart
covered | threads_time_to_content_test › the first account's posts appear before the slow ones answer | ThreadsReaderTest.theFirstAccountsThreadsArriveBeforeTheSlowOnesAnswer
covered | threads_time_to_content_test › a known account with a fresh LSD costs one request, not two | ThreadsDirectClientTest.aKnownAccountWithAFreshTokenCostsOneRequestNotTwo
covered | threads_time_to_content_test › guest departures leave sooner than the session floor | ThreadsPacingTest.guestReadsLeaveSoonerThanTheSessionFloor

## test/mastodon_archive_test.dart
covered | mastodon_archive_test › Saved combines network, folder and note search, then opens the stored conversation | MastodonDeviceTest.savedKeepsAPostByItsAddress; store/saved SavedContentTest
covered | mastodon_archive_test › same status id from two servers never collides in Saved | MastodonDeviceTest.savedKeepsAPostByItsAddress (keyed by address)
covered | mastodon_archive_test › the shared index retains warning, author and searchable text | MastodonDeviceTest.aSnapshotKeepsEverythingACardShows
covered | mastodon_archive_test › folder media extraction preserves server URLs and unique asset ids | MastodonDeviceTest.aSnapshotKeepsEverythingACardShows
covered | mastodon_archive_test › bookmark writes the shared archive and can undo it | MastodonDeviceTest.savedKeepsAPostByItsAddress

## test/mastodon_client_journeys_test.dart
covered | mastodon_client_journeys_test › full client loads only chosen feeds and restores their positions | MastodonReadersTest.restoredPostsShowAtOnceAndGoOnBelowTheLast; MastodonDeviceTest.readingPlacesComeBackForTheirInstancesOnly
untested → ported | mastodon_client_journeys_test › Home controls stay hidden until the top, reduced motion | Compose (Mastodon Home) | Compose only
covered | mastodon_client_journeys_test › Following exposes people, profiles, and a working add action | MastodonReadersTest.followingShowsAccountsAsTheyAnswer | the add action is Compose-only
untested → ported | mastodon_client_journeys_test › search keeps query, result type and position after profile and back | Compose (Mastodon search) | Compose only
untested → ported | mastodon_client_journeys_test › failed search can be retried without losing the query | Compose (Mastodon search) | Compose only
untested → ported | mastodon_client_journeys_test › new search wins over a slower old response, including old errors | Compose (Mastodon search model) | the rule is in the Compose model
untested → ported | mastodon_client_journeys_test › a search result arriving after disposal is ignored | Compose (Mastodon search model) | Compose only

## test/mastodon_client_layout_test.dart
untested → ported | mastodon_client_layout_test › populated Mastodon $variant | Compose (Mastodon screens) | layout is Compose-only

## test/mastodon_client_test.dart
covered | mastodon_client_test › verify prefers /api/v2/instance | MastodonClientTest.verifyAsksTheV2InstanceEndpoint
covered | mastodon_client_test › verify falls back to /api/v1/instance | MastodonClientTest.verifyFallsBackToV1WhereV2IsMissing
covered | mastodon_client_test › lookup parses a public account payload | MastodonClientTest.aLookupSendsXtasHeadersAndReadsTheAccount
covered | mastodon_client_test › getStatuses returns parsed posts | MastodonClientTest.statusesAskForTheirLimitAndParseThePosts
covered | mastodon_client_test › fetchAccount reuses the looked-up id on the next page | MastodonClientTest.accountPostsLookTheIdUpOnceAndReadTheStatusesAgain
covered | mastodon_client_test › fetchThread resolves a status URL then loads context | MastodonClientTest.aThreadResolvesTheStatusAddressThenReadsItsContext
covered | mastodon_client_test › maps 404 and 429 to typed errors | MastodonClientTest.answersOtherThan200AreTypedByTheirCode
covered | mastodon_client_test › fetchThread on origin skips a search 401 and uses the URL snowflake | MastodonClientTest.onItsOriginAThreadIsReadByTheIdInItsAddressWhenSearchIsRefused
covered | mastodon_client_test › fetchThreadAnywhere rediscovers a remote post via account statuses when search is closed | MastodonClientTest.aRemotePostIsFoundAmongItsAuthorsStatusesWhenSearchIsClosed
covered | mastodon_client_test › getPublicTimeline reads /timelines/public?local=true | MastodonClientTest.theLocalTimelineAsksLocalTrue
covered | mastodon_client_test › getPublicTimeline falls back to Misskey featured | MastodonClientTest.aPublicTimelineTheMastodonApiRefusesIsReadAsMisskeyFeaturedNotes
covered | mastodon_client_test › getPublicTimeline pages with max_id | MastodonClientTest.theFederatedTimelinePagesWithMaxId
covered | mastodon_client_test › getStatuses asks only_media and max_id | MastodonClientTest.statusesAskForMediaPinsAndAnOlderPage
covered | mastodon_client_test › Misskey local-timeline pages with untilId | MastodonClientTest.misskeysLocalTimelinePagesWithUntilId
covered | mastodon_client_test › getStatuses asks pinned=true | MastodonClientTest.statusesAskForMediaPinsAndAnOlderPage
covered | mastodon_client_test › search reads accounts, statuses, and hashtags | MastodonClientTest.aSearchReadsAccountsStatusesAndHashtags

## test/mastodon_content_warning_test.dart
covered | mastodon_content_warning_test › a content warning is labelled and hides the post body | MastodonPluginRulesTest.aContentWarningHoldsEverythingBackUntilOpened; MastodonParserTest.aContentWarningStaysOutOfTheBody
covered | mastodon_content_warning_test › the warning stays visible once the body is shown | MastodonPluginRulesTest.aContentWarningHoldsEverythingBackUntilOpened
untested → ported | mastodon_content_warning_test › a long warning in German does not overflow a 320dp phone | Compose (Mastodon card) | layout is Compose-only

## test/mastodon_feed_reliability_test.dart
covered | mastodon_feed_reliability_test › collapses remote aliases and keeps same ids from separate origins | MastodonFeedsTest.copiesOnOtherInstancesCollapseButTheSameIdOnAnotherOriginStays
covered | mastodon_feed_reliability_test › prefers originals at the first public position, boost first | MastodonFeedsTest.anOriginalTakesTheFirstPlaceOfABoostOfIt
covered | mastodon_feed_reliability_test › newest refresh owns both timeline and source | MastodonFollowingTest.anOlderReadCannotOverwriteWhatANewerOneFound
covered | mastodon_feed_reliability_test › forget invalidates a pending refresh and resets source | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › destroy ignores a pending read and prevents new ones | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › uses raw timeline cursor after boost deduplication | MastodonClientTest.aTimelinePagesBelowABoostsOwnIdAfterBoostsCollapse
covered | mastodon_feed_reliability_test › paging failures preserve posts and notify a retryable state | MastodonFollowingTest.oneFailingAccountLeavesTheOthersAndWhatItSaidLast
covered | mastodon_feed_reliability_test › an old page cannot append to a refreshed feed or clear newer loading | MastodonFollowingTest.anOlderReadCannotOverwriteWhatANewerOneFound
covered | mastodon_feed_reliability_test › failed refresh retains original source and cursor | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › repeated cursor stops an unbounded duplicate paging loop | MastodonClientTest.aPageThatRepeatsItsCursorEndsPaging
covered | mastodon_feed_reliability_test › forget also invalidates a pending page | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › hide boosts keeps original followed posts in date order | MastodonFeedsTest.hidingBoostsKeepsTheFollowedAuthorsOwnPost
covered | mastodon_feed_reliability_test › new refresh wins and an older request cannot contaminate its cache | MastodonFollowingTest.anOlderReadCannotOverwriteWhatANewerOneFound
covered | mastodon_feed_reliability_test › mixed Home and group reads leave dedicated Following state intact | MastodonFollowingTest.followedAccountsAreMergedNewestFirst
covered | mastodon_feed_reliability_test › forget discards pending partials and prevents cache resurrection | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › forgotten mixed-feed reads do not deliver stale results | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › instance change invalidates an older mixed-feed request | MastodonFollowingTest.otherInstancesOvertakeAnOlderRead
covered | mastodon_feed_reliability_test › failed forced refresh keeps all prior posts, including cached partials | MastodonFollowingTest.oneFailingAccountLeavesTheOthersAndWhatItSaidLast
covered | mastodon_feed_reliability_test › destroy ignores pending following partials | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › newer refresh wins even when the old request finishes last | MastodonFollowingTest.anOlderReadCannotOverwriteWhatANewerOneFound
covered | mastodon_feed_reliability_test › forget and destroy invalidate pending Explore responses | MastodonFollowingTest.aForgottenReadDeliversNothing
covered | mastodon_feed_reliability_test › failed refresh preserves posts and reports a recoverable error | MastodonFollowingTest.oneFailingAccountLeavesTheOthersAndWhatItSaidLast
covered | mastodon_feed_reliability_test › post errors are handled immediately while tags are still pending | MastodonClientTest.exploreKeepsItsPostsWhenTheTagsFail

## test/mastodon_instances_test.dart
covered | mastodon_instances_test › asks the account's own instance first — it is the only complete one | MastodonClientTest.anAddressIsLookedUpWhereItLivesAndAnythingElseSearched
covered | mastodon_instances_test › the reader's instances come between the origin and the defaults | MastodonDeviceTest.theReadersInstancesAreTheHomeOneThenTheOthers
covered | mastodon_instances_test › a bare local name has no origin, so the reader's home leads | MastodonXtaCasesTest.instancesAreAskedInXtasOrder
covered | mastodon_instances_test › never asks the same instance twice, however many ways it was named | MastodonXtaCasesTest.instancesAreAskedInXtasOrder
covered | mastodon_instances_test › with nothing configured there is still a full list — that is the point | MastodonDeviceTest.theReadersInstancesAreTheHomeOneThenTheOthers
covered | mastodon_instances_test › junk in the configured list is dropped rather than asked | MastodonXtaCasesTest.instancesAreAskedInXtasOrder
covered | mastodon_instances_test › keeps the origin and at most one built-in default | MastodonXtaCasesTest.instancesAreAskedInXtasOrder
covered | mastodon_instances_test › keeps every instance the reader configured | MastodonDeviceTest.theReadersInstancesAreTheHomeOneThenTheOthers
covered | mastodon_instances_test › an origin that is already a default is not asked twice | MastodonXtaCasesTest.instancesAreAskedInXtasOrder
covered | mastodon_instances_test › a miss on one instance moves to the next, and the winner's answer is returned | MastodonClientTest.aMissOnOneInstanceMovesToTheNext
covered | mastodon_instances_test › when every instance fails, the most telling failure is the one reported | MastodonClientTest.whenEveryInstanceFailsTheMostTellingFailureIsReported
covered | mastodon_instances_test › an empty candidate list is not configured, not a crash | MastodonClientTest.noInstanceToAskIsNotConfigured
covered | mastodon_instances_test › a hanging first instance fails over instead of blocking | MastodonClientTest.aHangingFirstInstanceFailsOverInsteadOfBlocking
covered | mastodon_instances_test › a later load asks the instance that already answered first | MastodonClientTest.theInstanceThatAnsweredIsAskedFirstNextTime
covered | mastodon_instances_test › home first, then the added list, in order | MastodonDeviceTest.theReadersInstancesAreTheHomeOneThenTheOthers
covered | mastodon_instances_test › nothing configured is an empty list, and corrupt storage reads the same | MastodonDeviceTest.theReadersInstancesAreTheHomeOneThenTheOthers
covered | mastodon_instances_test › keeps order and drops ids already shown | MastodonFeedsTest.uniqueKeepsTheOrderAndDropsWhatIsAlreadyThere

## test/mastodon_models_test.dart
covered | mastodon_models_test › adds https and strips a trailing slash | MastodonXtaCasesTest.instancesNormaliseAsInXta
covered | mastodon_models_test › refuses empty or scheme-less garbage | MastodonClientTest.onlyBadAddressesFailAsNotConfiguredWhereXtaHitANullCheck
covered | mastodon_models_test › accepts bare users, @users, user@domain and profile URLs | MastodonXtaCasesTest.acctsNormaliseAsInXta
covered | mastodon_models_test › refuses what is not an address | MastodonClientTest.anAcctThatIsNoAddressIsNotFoundWithoutAsking
covered | mastodon_models_test › attaches the home domain to a local username | MastodonXtaCasesTest.acctsGetTheirDomainAsInXta
covered | mastodon_models_test › reads /@user/id and /users/user/statuses/id | MastodonXtaCasesTest.statusIdsComeOutOfAddressesAsInXta
covered | mastodon_models_test › ignores profile URLs and junk | MastodonXtaCasesTest.statusAddressesMatchAsInXta
covered | mastodon_models_test › treats trailing slashes and host case as the same post | MastodonXtaCasesTest.statusAddressesMatchAsInXta
covered | mastodon_models_test › reads text, author, images, counts, link card and unwraps a boost | MastodonParserTest.aStatusReadsItsTextAuthorMediaCountsAndCard
covered | mastodon_models_test › keeps a link-only status that has no text or images | MastodonParserTest.aLinkOnlyStatusIsKept
covered | mastodon_models_test › drops empty items and tolerates a reshaped payload | MastodonParserTest.emptyStatusesAndOnesWithoutAnIdOrAddressAreDropped
covered | mastodon_models_test › reads documented fields and becomes a followable account | MastodonParserTest.aProfileReadsItsFieldsHeaderAndEmoji
covered | mastodon_models_test › reads profile fields and bot flag | MastodonParserTest.aProfileReadsItsFieldsHeaderAndEmoji
covered | mastodon_models_test › sums uses across history days | MastodonClientTest.trendingTagsAddUpTheirHistory
covered | mastodon_models_test › keeps spoiler out of the body and parses a poll | MastodonParserTest.aContentWarningStaysOutOfTheBody, aPollIsReadForItsResults
covered | mastodon_models_test › reads who boosted and who a reply is to | MastodonParserTest.aBoostIsUnwrappedToThePostItShares, aReplyNamesTheFirstAccountItMentions
covered | mastodon_models_test › reads an edited quote and mention list | MastodonParserTest.anEditedPostReadsItsQuoteOneLevelDeep
covered | mastodon_models_test › splits mentions and tags and resolves a local @name | MastodonTextTest.mentionsAndTagsAreSplitAndALocalNameResolved
covered | mastodon_models_test › reads accounts, statuses, and hashtags | MastodonClientTest.aSearchReadsAccountsStatusesAndHashtags
covered | mastodon_models_test › puts pinned first and drops the duplicate from the timeline | MastodonFeedsTest.pinsComeFirstWithoutRepeating
covered | mastodon_models_test › maps a featured note onto MastodonPost | MastodonClientTest.aPublicTimelineTheMastodonApiRefusesIsReadAsMisskeyFeaturedNotes
covered | mastodon_models_test › unwraps a pure renote onto the original author | MastodonParserTest.aBoostIsUnwrappedToThePostItShares
covered | mastodon_models_test › keeps a quote-renote as the comment plus the original | MastodonParserTest.anEditedPostReadsItsQuoteOneLevelDeep
covered | mastodon_models_test › a video plays its MP4 with the preview as poster | MastodonParserTest.mediaCarriesItsMeta
covered | mastodon_models_test › gifv is a looping clip; pictures keep their preview | MastodonParserTest.aGifvPlaysTheRemoteFileUntilTheInstanceHasCachedIt
covered | mastodon_models_test › remote_url stands in while the instance has not cached it | MastodonParserTest.aGifvPlaysTheRemoteFileUntilTheInstanceHasCachedIt
covered | mastodon_models_test › a quoted status keeps its video | MastodonParserTest.anEditedPostReadsItsQuoteOneLevelDeep
covered | mastodon_models_test › the GIF flag survives a saved snapshot | MastodonDeviceTest.aSnapshotKeepsEverythingACardShows

## test/mastodon_profile_reader_test.dart
covered | mastodon_profile_reader_test › three tabs load independently and cache their own responses | MastodonClientTest.aProfilesTabsAskForTheirOwnStatuses; MastodonReadersTest.aProfileOpensOnItsPostsPinnedFirstAndReadsATabWhenShown
covered | mastodon_profile_reader_test › pins do not change the raw page cursor or hide later timeline entries | MastodonClientTest.aProfileOpensOnItsPinsThenItsTimeline
covered | mastodon_profile_reader_test › a profile with only pinned posts does not claim another timeline page | MastodonClientTest.aProfileWithOnlyPinsHasNoFurtherPage
covered | mastodon_profile_reader_test › failed replies remain retryable without polluting the media tab | MastodonClientTest.pinsThatFailLeaveTheProfileStandingUnlessTheInstanceIsThrottling
untested → ported | mastodon_profile_reader_test › refresh invalidates every outstanding tab response | Compose (Mastodon profile model) | Compose only
covered | mastodon_profile_reader_test › a repeated page cannot create an endless pagination loop | MastodonClientTest.aPageThatRepeatsItsCursorEndsPaging
untested → ported | mastodon_profile_reader_test › disposed stores ignore in-flight replies | Compose (Mastodon profile model) | Compose only
covered | mastodon_profile_reader_test › profile metadata links, verified status and enlarged text remain readable | MastodonParserTest.aProfileFieldLinksOnlyToExactlyOneSafeAddress | the enlarged layout is Compose-only
untested → ported | mastodon_profile_reader_test › profile exposes replies and accessible profile actions on a compact screen | Compose (Mastodon profile) | Compose only
covered | mastodon_profile_reader_test › profile browser actions reject malformed and non-web destinations | MastodonParserTest.aProfileFieldLinksOnlyToExactlyOneSafeAddress

## test/mastodon_reader_metadata_test.dart
covered | mastodon_reader_metadata_test › Misskey renotes retain followed booster identity across reading snapshots | MastodonParserTest.aBoostKeepsItsWrapperCursorAndDateApartFromTheOriginal
covered | mastodon_reader_metadata_test › boost preserves wrapper cursor/date independently of original identity | MastodonParserTest.aBoostKeepsItsWrapperCursorAndDateApartFromTheOriginal
covered | mastodon_reader_metadata_test › old snapshots retain the original cursor and publication date fallback | MastodonDeviceTest.xtasOwnSnapshotReads
covered | mastodon_reader_metadata_test › quote warnings survive parsing, snapshots and opening the quote | MastodonParserTest.aContentWarningStaysOutOfTheBody; MastodonDeviceTest.aSnapshotKeepsEverythingACardShows
covered | mastodon_reader_metadata_test › poll does not present unpublished option totals as zero votes | MastodonParserTest.aPollThatHoldsItsResultsBackDoesNotClaimZeroVotes
covered | mastodon_reader_metadata_test › profile fields retain exactly one safe link and server verification | MastodonParserTest.aProfileFieldLinksOnlyToExactlyOneSafeAddress
covered | mastodon_reader_metadata_test › malformed optional metadata never blocks a profile or post | MastodonParserTest.malformedOptionalMetadataNeverBlocksAProfileOrAPost
covered | mastodon_reader_metadata_test › canonical identity preserves path case and ports but ignores query/fragment | MastodonXtaCasesTest.statusAddressesMatchAsInXta

## test/mastodon_reading_cards_test.dart
covered | mastodon_reading_cards_test › URLs retain fragments and balanced parentheses; entities drop punctuation | MastodonTextTest.addressesKeepFragmentsAndBalancedBracketsAndLoseTrailingPunctuation
covered | mastodon_reading_cards_test › email addresses and embedded fragments do not turn into mentions or tags | MastodonTextTest.anEntityGluedToAWordStaysText
covered | mastodon_reading_cards_test › empty hosts, embedded schemes and URL credentials are not links | MastodonTextTest.addressesWithoutAHostOrWithCredentialsAreNoLinks
covered | mastodon_reading_cards_test › links, mentions and hashtags dispatch to their own destinations | MastodonXtaCasesTest.linksOpenInTheAppAsInXta
covered | mastodon_reading_cards_test › sensitive attachments are not built until revealed and can be hidden again | MastodonPluginRulesTest.sensitiveMediaWaitsBehindXtasNotice
covered | mastodon_reading_cards_test › a recycled card does not inherit another post reveal choice | MastodonPluginRulesTest.sensitiveMediaWaitsBehindXtasNotice
covered | mastodon_reading_cards_test › quoted warning text remains visible without exposing the quoted body | MastodonPluginRulesTest.aContentWarningHoldsEverythingBackUntilOpened
untested → ported | mastodon_reading_cards_test › hidden counts retain accessible action names | Compose accessibility (Mastodon card) | Compose only
covered | mastodon_reading_cards_test › multi-choice percentages use voters and malformed counts remain bounded | MastodonParserTest.pollSharesAreBoundedAndNeedATotal
covered | mastodon_reading_cards_test › hidden poll results do not falsely claim zero votes | MastodonParserTest.aPollThatHoldsItsResultsBackDoesNotClaimZeroVotes
untested → ported | mastodon_reading_cards_test › poll status and totals remain readable with enlarged text on a narrow phone | Compose (Mastodon card) | Compose only
covered | mastodon_reading_cards_test › the media grid does not expose alt text before a sensitive reveal | MastodonPluginRulesTest.sensitiveMediaWaitsBehindXtasNotice
untested → ported | mastodon_reading_cards_test › the media grid exposes descriptions separately from opening an image | Compose (Mastodon media grid) | Compose only

## test/mastodon_reading_experience_test.dart
covered | mastodon_reading_experience_test › a fresh client session restores the visible post without fetching a new page | MastodonDeviceTest.readingPlacesComeBackForTheirInstancesOnly
covered | mastodon_reading_experience_test › branches retain sibling order and collapse only their descendants | MastodonThreadsTest.branchesKeepTheirSiblingOrderAndFoldOnlyTheirOwnReplies
covered | mastodon_reading_experience_test › cyclic and duplicate replies cannot loop or duplicate a card | MastodonThreadsTest.cyclicAndDuplicateRepliesNeitherLoopNorRepeat
covered | mastodon_reading_experience_test › status snapshots retain warnings, reply links, polls and quoted media | MastodonDeviceTest.aSnapshotKeepsEverythingACardShows
covered | mastodon_reading_experience_test › restart restores choice, content and anchor only for its server scope | MastodonDeviceTest.readingPlacesComeBackForTheirInstancesOnly
covered | mastodon_reading_experience_test › disabled reading memory and malformed snapshots are safe | MastodonDeviceTest.aSnapshotWithoutAnAuthorOrAWebAddressIsNone
untested → ported | mastodon_reading_experience_test › switching to media during posts pagination keeps both responses in their own tab | Compose (Mastodon profile model) | Compose only
covered | mastodon_reading_experience_test › sensitive media tiles expose a warning without fetching the image | MastodonPluginRulesTest.sensitiveMediaWaitsBehindXtasNotice

## test/mastodon_search_hashtag_test.dart
covered | mastodon_search_hashtag_test › search body swipes change result sections without another request | MastodonReadersTest.aSearchOpensOnTheFirstTabThatFoundSomething
covered | mastodon_search_hashtag_test › post URL recognition accepts exact Mastodon paths and rejects ambiguous URLs | MastodonXtaCasesTest.pastedStatusAddressesAreRecognisedAsInXta
covered | mastodon_search_hashtag_test › post search reads the origin directly before account lookup or guest search | MastodonClientTest.aPastedStatusAddressIsReadFromItsOriginFirst
untested → ported | mastodon_search_hashtag_test › direct post failures remain retryable and an old URL cannot replace a newer search | Compose (Mastodon search model) | Compose only
untested → ported | mastodon_search_hashtag_test › closed search routes neither update from direct reads nor start new reads | Compose (Mastodon search model) | Compose only
covered | mastodon_search_hashtag_test › hashtag refresh keeps loaded posts and provides a recoverable error | MastodonReadersTest.aFailedRefreshKeepsWhatWasShown
covered | mastodon_search_hashtag_test › hashtag pagination uses wrapper cursors, deduplicates URLs and stops repeated pages | MastodonClientTest.aPageThatRepeatsItsCursorEndsPaging
untested → ported | mastodon_search_hashtag_test › hashtag pagination can retry after failure without losing the current page | Compose (Mastodon hashtag model) | Compose only
untested → ported | mastodon_search_hashtag_test › refresh invalidates older hashtag pagination and overlapping refreshes | Compose (Mastodon hashtag model) | Compose only
untested → ported | mastodon_search_hashtag_test › closing a hashtag route ignores its pending request | Compose (Mastodon hashtag model) | Compose only
untested → ported | mastodon_search_hashtag_test › hashtag reader retries an initial failure and offers manual page retry | Compose (Mastodon hashtag model) | Compose only

## test/mastodon_thread_reader_test.dart
covered | mastodon_thread_reader_test › author focus keeps connecting replies and excludes unrelated branches | MastodonThreadsTest.authorFocusKeepsTheRepliesLeadingToTheAuthorsOwn
covered | mastodon_thread_reader_test › author identity is case insensitive and does not match another server | MastodonThreadsTest.theAuthorIsMatchedWithoutCaseAndNotOnAnotherServer
covered | mastodon_thread_reader_test › ancestors follow actual parent links without repeating the selected post | MastodonThreadsTest.ancestorsFollowTheirReplyLinksWithoutRepeatingTheOpenedPost
covered | mastodon_thread_reader_test › malformed duplicate, cyclic, orphan, and ancestor replies remain finite | MastodonThreadsTest.duplicateCyclicOrphanAndAncestorRepliesStayFinite
covered | mastodon_thread_reader_test › a deeply nested conversation avoids recursive stack growth | MastodonThreadsTest.aDeepConversationIsWalkedWithoutRecursion
covered | mastodon_thread_reader_test › author mode and expansion changes survive refresh; vanished IDs are pruned | MastodonReadersTest.aThreadFoldsItsRepliesAndAuthorFocusUnfoldsThem
untested → ported | mastodon_thread_reader_test › failed refresh keeps the conversation readable and a retry clears the error | Compose (Mastodon thread) | Compose only
untested → ported | mastodon_thread_reader_test › superseded reads and disposal cannot replace current conversation state | Compose (Mastodon thread) | Compose only
covered | mastodon_thread_reader_test › author focus, fold-all, and selected-post return work in the conversation | MastodonReadersTest.aThreadFoldsItsRepliesAndAuthorFocusUnfoldsThem
untested → ported | mastodon_thread_reader_test › a reload keeps the replies in place under the loading line | Compose (Mastodon thread) | Compose only
untested → ported | mastodon_thread_reader_test › empty public context has a clear finished state | Compose (Mastodon thread) | Compose only
untested → ported | mastodon_thread_reader_test › an empty author filter explains its scope and can return to all replies | Compose (Mastodon thread) | Compose only
untested → ported | mastodon_thread_reader_test › thread controls fit a compact enlarged-text RTL layout | Compose (Mastodon thread) | Compose only

## test/mastodon_timeline_controls_test.dart
covered | mastodon_timeline_controls_test › combined filters use all query words and preserve source ordering | MastodonFeedsTest.filtersUseEveryQueryWordAndKeepTheInstancesOrder
covered | mastodon_timeline_controls_test › links match URL-only posts and embedded cards but not ordinary text | MastodonFeedsTest.linksMatchAddressesInTheTextAndPreviewCardsButNotOrdinaryText
covered | mastodon_timeline_controls_test › dates sort by boost time with stable ties and unknown dates last | MastodonFeedsTest.datesSortByBoostTimeWithStableTiesAndUndatedLast
covered | mastodon_timeline_controls_test › canonical duplicates collapse without colliding between hosts | MastodonFeedsTest.copiesCollapseWithoutCollidingAcrossHosts
covered | mastodon_timeline_controls_test › preferences persist per timeline across surfaces but never save search text | MastodonDeviceTest.timelineOptionsAreKeptPerTimelineWithoutTheSearch
covered | mastodon_timeline_controls_test › malformed stored choices keep all posts visible | MastodonDeviceTest.damagedTimelineOptionsLeaveTheTimelinesWhole
covered | mastodon_timeline_controls_test › filter sheet applies locally and preserves complete reading snapshot | MastodonDeviceTest.aSnapshotKeepsEverythingACardShows
untested → ported | mastodon_timeline_controls_test › empty local results offer reset and preserve the underlying feed | Compose (Mastodon filter sheet) | Compose only
untested → ported | mastodon_timeline_controls_test › timeline controls and sheet fit compact RTL at enlarged text | Compose (Mastodon filter sheet) | Compose only

## test/bluesky_reader_controls_test.dart
covered | bluesky_reader_controls_test › content filters distinguish images, videos, cards and annotated links | BlueskyTimelineTest.theReaderFilterTellsPicturesVideosCardsAndLinksApart
covered | bluesky_reader_controls_test › query requires every term across text, author, handle and card title | BlueskyTimelineTest.theReaderQueryNeedsEveryWordAcrossTextAuthorHandleAndCardTitle
covered | bluesky_reader_controls_test › reply and repost switches compose and duplicate records render once | BlueskyTimelineTest.replyAndRepostSwitchesComposeAndADuplicateShowsOnce
covered | bluesky_reader_controls_test › ordering uses repost date, stable ties and unknown dates last without mutating the feed | BlueskyTimelineTest.theReaderOrdersByActivityKeepsTiesAndPutsUnknownDatesLast
covered | bluesky_reader_controls_test › restart restores bounded reading position and options without persisting search text | BlueskyReadingTest.aPlaceKeepsTwoDozenBeforeItsAnchorAndAtMostItsLimit
covered | bluesky_reader_controls_test › positions belong to one AppView while filter preferences survive a source change | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
covered | bluesky_reader_controls_test › reset clears session state and queued persistence before disposal | BlueskyReadingTest.aPlaceKeepsTwoDozenBeforeItsAnchorAndAtMostItsLimit
covered | bluesky_reader_controls_test › an unchanged source preserves the current reading snapshot | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
covered | bluesky_reader_controls_test › restored posts intersect current authors and reposters by stable DID or handle | BlueskyReadingTest.aRestoredPlaceMatchesFollowsByDidOrHandleAndEmptyNamesMatchNothing
covered | bluesky_reader_controls_test › empty identifiers never match unrelated cached posts | BlueskyReadingTest.aRestoredPlaceMatchesFollowsByDidOrHandleAndEmptyNamesMatchNothing
covered | bluesky_reader_controls_test › disabled position memory stores preferences but no post snapshot | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
covered | bluesky_reader_controls_test › disabling position memory immediately prevents restoring an existing snapshot | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
covered | bluesky_reader_controls_test › malformed snapshot data cannot prevent opening a fresh reader | BlueskySnapshotTest.damagedOptionalFieldsOfOnePostLoseNeitherItNorTheOthers
covered | bluesky_reader_controls_test › malformed records are skipped while valid snapshots and bounded offsets remain readable | BlueskySnapshotTest.damagedOptionalFieldsOfOnePostLoseNeitherItNorTheOthers
untested → ported | bluesky_reader_controls_test › filter sheet remains operable at 320px with enlarged RTL text | Compose (Bluesky filter sheet) | Compose only

## test/bluesky_reading_experience_test.dart
covered | bluesky_reading_experience_test › reply metadata and active warnings survive local-like snapshots | BlueskyThreadStateTest.aWarningCoversTheTextAndIsShownAgainWhenThePostChanges
covered | bluesky_reading_experience_test › thread topology supplies a parent when a record omits it | BlueskyRepliesTest (parent links)
covered | bluesky_reading_experience_test › branch collapse retains siblings, orphans and cyclic posts exactly once | BlueskyRepliesTest (branches, cycles)
untested → ported | bluesky_reading_experience_test › paging result stays in Replies when Media is selected in flight | Compose (Bluesky profile tabs) | Compose only
untested → ported | bluesky_reading_experience_test › paging errors retain cards and wait for explicit retry | Compose (Bluesky profile tabs) | Compose only
untested → ported | bluesky_reading_experience_test › refresh keeps visible cards and other tab data while the profile loads | Compose (Bluesky profile) | Compose only
covered | bluesky_reading_experience_test › thread refresh after disposal cannot publish a late response | BlueskyThreadStateTest.theNewestRefreshWinsAndAnEndedStoreReadsNothing
covered | bluesky_reading_experience_test › sensitive grid and card do not mount media until revealed | BlueskyThreadStateTest.aMediaTileIsAnnouncedWithItsDescriptionUnlessItIsCovered
untested → ported | bluesky_reading_experience_test › conversation highlights the opened post and collapses a branch | Compose (Bluesky thread) | Compose only
untested → ported | bluesky_reading_experience_test › profile names local likes accurately and retains Posts position | Compose (Bluesky profile) | Compose only

## test/bluesky_reading_layout_test.dart
untested → ported | bluesky_reading_layout_test › Bluesky reading layout $variant | Compose (Bluesky screens) | layout is Compose-only

## test/bluesky_search_upgrade_test.dart
covered | bluesky_search_upgrade_test › canonical URL and AT URI preserve actor and record key | BlueskyUrlsTest (canonical forms)
covered | bluesky_search_upgrade_test › rejects $input | BlueskyUrlsTest
covered | bluesky_search_upgrade_test › post URL cannot be interpreted as an author filter | BlueskyUrlsTest; BlueskyScreensRulesTest.filtersTakeAnAccountAndATag
covered | bluesky_search_upgrade_test › profile search begins with an author filter ready for a query | BlueskyScreensRulesTest.filtersTakeAnAccountAndATag
untested → ported | bluesky_search_upgrade_test › keeps independent pages when changing tabs | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › older successful people search cannot replace a newer one | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › older failed post search cannot replace a newer one | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › clearing query invalidates outstanding reads | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › suggestion failures stay separate from a submitted query | Compose (Bluesky search model) | Compose only
covered | bluesky_search_upgrade_test › people pagination deduplicates and stops a repeated cursor | BlueskyTimelineTest.aRepeatedOrBlankCursorEndsPaging
covered | bluesky_search_upgrade_test › post cursor cycle terminates without discarding newly loaded posts | BlueskyTimelineTest.aRepeatedOrBlankCursorEndsPaging
covered | bluesky_search_upgrade_test › load more failure preserves results and cursor for retry | BlueskySearch.kt (ResultList, SearchFoot), ui/plugins PagedItems | the next page is asked by XTA's "Load more results" and the last says "No more results"; a failed page keeps its own retry. BlueskyPartsTest.aSearchOffersItsNextPageThenSaysThereIsNoMore, BlueskySourcesTest.aFailedPageKeepsItsCursorForATryAgain
untested → ported | bluesky_search_upgrade_test › refresh error preserves content but retries refresh instead of pagination | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › structured filters invalidate earlier post requests and preserve people | Compose (Bluesky search model) | Compose only
covered | bluesky_search_upgrade_test › a hashtag uses a structured exact tag | BlueskyScreensRulesTest.filtersTakeAnAccountAndATag
covered | bluesky_search_upgrade_test › a post URL switches to posts and resolves through the configured AppView | BlueskyUrlsTest; BlueskyClientTest
covered | bluesky_search_upgrade_test › an AT URI skips handle resolution | BlueskyUrlsTest
covered | bluesky_search_upgrade_test › AppView changes discard the old response and expose retry | BlueskyClientTest.theAppViewIsAskedBeforeEveryRequestAndABadOneFallsBack
untested → ported | bluesky_search_upgrade_test › unchanged filters do not issue another request | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › disposal ignores completion and further commands | Compose (Bluesky search model) | Compose only
untested → ported | bluesky_search_upgrade_test › full-screen compact search supports filters and tabs without overflow | Compose (Bluesky search) | Compose only

## Screen parity pass (no XTA test of its own)
missing/different | bluesky_post_card.dart, mastodon_post_card.dart › a link card keeps its post in the browser's bar (LinkPostContext) | ui/plugins Posts.kt (PostLinkCard, LocalPostNetwork), NetworkPosts.kt (linkContext) | the page opened on its own; NetworkPostsTest.aLinkOpenedFromACardCarriesItsPostAsXtasCardsHandIt
missing/different | bluesky_content_warning.dart, mastodon_post_card.dart › a revealed warning stays a Show/Hide toggle, a covered quote opens in place | ui/plugins Posts.kt (WarningLine, CoverLine, QuotedNetworkPost) | Show was one way and a covered quote only named its warning; NetworkPostsTest.aWarningOverTheMediaAloneIsDrawnAtTheMediaAndOnlyWhereThereIsSome, MastodonPluginRulesTest.aPostIsDrawnWithItsLinksMentionsAndTags
missing/different | mastodon_interleaved.dart › Home and the groups draw a Mastodon post as the Mastodon card | MastodonPostRow.kt (sharedExtras) | only the poll was added to the shared row; MastodonSharedTest.theSharedRowSaysWhoBoostedAndThatItWasEditedAsXtasCardDoes
missing/different | bluesky_profile_screen.dart › Bluesky's own error words on every screen | BlueskyParts.kt (errorText(Problem), ProblemView, ProblemRow) | lists said the app's generic words; BlueskyPartsTest.aFailedReadIsToldInBlueskysOwnWordsOnEveryScreen
missing/different | bluesky_reader_view.dart, plugin_feed_people.dart › the people a feed brings sit under the third post on Home, folded | BlueskyFollowingPane.kt (PeopleStrip, filteredRows), BlueskyPlace.kt (FollowingPlaces.STRIP_AFTER) | they stood open above the posts everywhere; BlueskyStoresTest.followingsListOpensAtItsAnchorAndKeepsTheFirstPostOnScreen

## Threads beyond XTA's tests (round 9: pictures, For you, the audit's items)

Rows as above: state | what | where | test.

missing/different | a picture's sizes: Meta's candidates of no size or one size gave the last, its smallest (XTA's _bestCandidate does too) | plugins/threads ThreadsImages.kt, ThreadsParser.media | every size kept largest first; a tile loads the smallest that fills it, the viewer and a download the largest. ThreadsImagesTest
missing/different | a kept post (liked, the tab's snapshot) knew one address per picture | ThreadsPostJson (`imageSizes`, beside XTA's lists) | ThreadsPostJsonTest.everySizeOfAPictureIsKeptBesideXtasLists…, sizesKeptForAnotherAddressAreNotThePictures
missing/different | a profile's page showed the 150 px picture | ThreadsParser.hdPicture, ThreadsProfile.pictureUrl, ThreadsProfileCard (a tap opens it in the viewer) | ThreadsImagesTest.aProfileKeepsItsLargestPictureForItsPageAndTheSmallOneForRows
missing/different | the Linux desktop shrank every picture by the nearest pixel (Coil 3.2's Skia decoder) | ui/common PictureScaling.kt, PictureScaling.desktop.kt, appImageLoader | PictureScalingTest (the size); the drawing itself needs a desktop with Skia
native only | For you: the Bearer's timeline page by page, else threads.com's public front page; needs sign-in, rate limited, a failed page, the end; the place and the section kept | ThreadsForYou.kt, ThreadsDirectClient.forYouTimeline/guestForYou, ThreadsForYouKept.kt, ThreadsForYouPane.kt | ThreadsForYouTest, ThreadsForYouKeptTest, ThreadsForYouLiveTest (RUN_LIVE)
missing/different | profile tabs were an underlined tab row; XTA's are one segmented button (plugin_profile_tabs.dart) | ThreadsProfileScreen.ProfileTabs; a second tap goes to the top, a pull reads the replies page too | Compose only
missing/different | a link card opened without its post (threads_post_card.dart:421-449) | ui/post CardView(card, from, modifier), ThreadsPostCard.ThreadsLink, ThreadsLinkContext | ThreadsSharedTest.aLinkCardOpensWithItsPostBesideIt…
missing/different | the followed accounts were read behind whichever section was shown | ThreadsHome.HomePane | Compose only
missing/different | the conversation as XTA's card that is dragged back (XtaCardPageRoute) | XtaPlugin.opensAsCard, ui/pluginstore PluginCards.kt (PluginCardScreen over SwipeBackCard and CardBacks; the plugin's back slides the card out), ThreadsPlugin.opensAsCard | SlidingBacksTest; the drag itself is the app's (SwipeBackMathTest, CardDragsTest) and needs a device

## Threads, round 10 (the chrome, the card, the audit, and past XTA)

missing/different | the tab drew its own strip of chips; XTA's is PluginHomeChrome (one bar on its own screen, a section picker when folded in Home) | ThreadsHome.HomeBar over the kit's PluginHomeTabs | Compose only (PluginTabFitTest is the kit's)
missing/different | a video was a poster that opened the viewer; XTA plays it in place with the app's player, under Settings › Media (threads_media.dart) | ThreadsMedia.MediaTile over the kit's MediaTiles, ThreadsMediaItems.tile (the file the video-quality setting picks) | ThreadsRulesTest.aFeedTileLoadsThePictureThatFillsItAndPlaysTheQualityTheSettingPicks
missing/different | a described picture carried no ALT and a long press did nothing (plugin_post_media.dart) | ThreadsMedia.MediaTile (AltBadge, AltTextDialog, LocalMediaSave) | the same test (the description); Compose only
missing/different | a feed's first read showed a spinner; XTA paints PluginFeedSkeleton, and a stub's conversation a skeleton post | ThreadsHome.HomePane, ThreadsForYouPane, ThreadsConversationScreen.threadRows | Compose only
native only | a dot for each picture of a row, the one in view marked | ThreadsMedia.StripDots, ThreadsStrip.shown | ThreadsRulesTest.theDotsMarkTheTileInViewAndTheLastAtTheRowsEnd
native only | a followed profile's page offers Unfollow, after XTA's question | ThreadsProfileCard, UnfollowDialog | Compose only
native only | a profile's link reads without its scheme | ThreadsProfiles.linkLabel | ThreadsRulesTest.aProfilesLinkReadsWithoutItsSchemeAndStillOpensWhole


## Threads, round 12 (the audit's last screens, and past XTA)

Rows as above: state | what | where | test.

missing/different | an account without a picture was a blank disc; XTA draws FallbackAvatar (its initial on its colour) on cards, quotes, profiles, people rows, the accounts and the settings | ui/plugins Faces.kt (PluginAvatar, PluginFallbackFace, new), used by every Threads face | PluginFacesTest
missing/different | a search's results loaded under a spinner; XTA paints TweetFeedSkeleton (threads_search_screen.dart) | ThreadsSearchPage.Results | Compose only
missing/different | a parked session was a red sentence; XTA's row has the paused mark in the error colour (threads_settings.dart) | ThreadsSettings.Parked | Compose only
missing/different | a conversation read again over its replies put a bar and a retry row into the list; XTA's ReloadStatus is a line over the top edge and a floating notice with Retry | ThreadsParts.ReloadStatus, ThreadsConversationScreen.ConversationList | Compose only
covered | the rest of search (the sheet, its people rows with Follow on this device, recent searches, the public accounts to try, Top and Recent, the profile the words spell, empty and failed states) and of the settings, line by line against threads_search_sheet.dart, threads_search_screen.dart and threads_settings.dart | ThreadsSearch.kt, ThreadsSearchPage.kt, ThreadsSettings.kt | the rows above
native only | a threads.com or threads.net search or topic link (`/search?q=…`, `serp_type=tags`) opens the plugin's search on what it asked, pasted into search too; XTA opened it as the profile "@search", and `/login` or `/settings` as accounts | ThreadsLinks.parse (ThreadsLink.Search, SITE_PAGES), ThreadsRoutes.forUrl, ThreadsQuery.of | ThreadsRulesTest.aSearchOrTopicLinkOpensThePluginsSearchAndThreadsOwnPagesOpenNothing, ThreadsLinksTest.aPastedSearchOrTopicLinkSearchesWhatItAsked
native only | a profile opened before shows at once what it showed last (its card and first twenty threads, the 24 newest profiles, in the plugin's folder, never a session) while Meta is asked again, a line at the top meanwhile; a read again that fails says so over what is shown, with Retry (XTA said nothing over a profile on screen) | ThreadsProfilesKept.kt (ThreadsProfilesKept, ThreadsProfileShelf), ThreadsApp.keptProfile/keepProfile, ThreadsProfileScreen | ThreadsProfilesKeptTest
native only | a failed profile, post or search says what failed in Threads' terms: a missing account "Could not read that Threads account" and a missing post "Could not read that Threads post" (XTA said "That instance has no Threads route" for both), a throttle the time Meta's wait ends ("try again after 14:05"), and a guest sent to Meta's sign-in page "Threads shows this page only to signed-in readers right now" (a redirect to /login, followed by hand in one paced departure; on the front page it is For you's own sign-in state) | ThreadsFailures.profile/post/search/time, ThreadsFailure.SIGN_IN_REQUIRED, ThreadsDirectClient.guestPage/followed, ThreadsLinks.isSignInPage; strings error_no_post, error_throttled_until, error_sign_in in 29 languages | ThreadsRulesTest.aPageThatFailedSaysWhatIsMissingWhenMetasWaitEndsAndThatItWantsASignIn, ThreadsDirectClientTest.aGuestPageSentToTheSignInPageSaysSo…, onlyMetasOwnSignInPageIsTheSignInWall
native only | a recent search can be forgotten with the cross on its chip, as XTA's other plugins allow (XTA's Threads sheet kept every one); the plugin's own copy of the history rule gave way to the kit's PluginSearchHistory | ThreadsSearch.RecentChip, ThreadsApp.forget | ThreadsRulesTest.theSearchHistoryPutsTheNewestFirstOnceKeepsTwentyAndForgetsOneOnAsking
native only | a GIF a post carries from GIPHY (`giphy_media_info`, the shape Instagram's comments use; not seen live from here) loops silently in place from its MP4, marked GIF, in place of the still, kept as one in likes and snapshots (`imageGifs` beside XTA's lists) and offered no quality menu; XTA showed the still or nothing | ThreadsParser.gif/media, ThreadsMedia.isGif, ThreadsMediaItems, ThreadsPostJson | ThreadsParserTest.aGifFromGiphyIsItsLargestRenditionLooping…, ThreadsRulesTest.aGifLoopsAsTheAppsGifsDoAndIsKeptAsOne
native only | the tab's feed and the sheet's people search name the time a throttle ends too | ThreadsFailures.feedText, ThreadsHome, ThreadsSearch.FailedPeople | ThreadsRulesTest.aPageThatFailedSaysWhatIsMissing…

## Threads, round 13 (past XTA: what a reader notices)

Rows as above: state | what | where | test.

native only | live probes log the shape of what a guest is shown (each page's answer or its redirect, unread post and user keys, features by key path, preloaded queries, paging keys), key names and counts only | desktopTest ThreadsLiveTest.theShapeOfWhatAGuestIsShown, ThreadsShape | ThreadsShapeTest (the reader, on a kept page); the reading itself is CI's live job
native only | Home opens where the reader stopped, after For you, Liked or a restart (For you already did); posts a pull or a reopening's read brings in above the reader wait under "↑ N new posts", which goes to the top (XTA's list jumped or kept them unseen) | ThreadsHomePlace.kt (ThreadsHomePlace, ThreadsFresh, home.json in the plugin's folder), ThreadsPlaces.kt (KeepsPlace, FreshPill), ThreadsHome.Feed; string new_posts (the app's, 29 languages) | ThreadsHomePlaceTest; the list itself needs a device
native only | words muted on this device hide a post in Home and For you (its text, its quote's, its link card's and its topic), matched as the groups' muted words (MutedKeywords), set in the tab's Filters sheet under XTA's custom_feed_muted_keywords wording; kept beside XTA's keys in `plugin.threads.feed_options`, written only when there are some; Reset filters keeps them | ThreadsFeedOptions (mutedWords, muting, unmuting, unmuted, reset), ThreadsHomeParts.MutedWords, ThreadsForYouPane.ForYouList; strings muted_words, muted_words_description, muted_words_hint in 29 languages | ThreadsFeedTest.aMutedWordHidesAPostWhereverItsWordsShowAndStaysThroughAReset
native only | in a conversation, a reply the opened post's author wrote is marked "Author" beside their name, as Threads' own app marks it (XTA marked nothing; its "Author's replies" filter only) | ThreadsConversation.byAuthor, ThreadsPostCard(byAuthor) AuthorChip, ThreadsConversationScreen.replyRows; string author in 29 languages (the noun of XTA's plugin_threads_author_replies per language) | ThreadsConversationTest.theOpenedPostsAuthorsRepliesAreMarkedAndNoOneElses
native only | a picture or video opened in a conversation is viewed among every one of the conversation's posts as shown (what it answers, the post, its thread, the replies in their order and as far as opened), swiped through in that order; a quote's, or a post with nothing more around it, opens alone with Open post as before (XTA's viewer held one post's) | ThreadsMediaItems.galleryStart, ThreadsApp.openMedia(…, among), LocalThreadsGallery (ThreadsMedia), ThreadsConversationScreen.ConversationList | ThreadsRulesTest.aPictureOpenedInAConversationIsViewedAmongAllItsPictures; the swipe needs a device
## Threads, round 14: the card (what a guest is shown beyond words and media)

Rows as above: state | what | where | test. The keys are those CI's shape probe saw a guest given (threads-shape: `styling_info.is_spoiler`, `tag_header.community_emoji`/`community_favicon_url`, `reshare_count`, `gen_ai_detection_method`, `pinned_post_info`, `sharing_friction_info`, `linked_inline_media`); their values' shapes were not logged, so each is read to be harmless when absent or other.

native only | a spoiler the author marked is covered on the card until tapped (heard as "Spoiler"), kept in likes and snapshots; in the app's shared rows the post is held back behind "Spoiler" | ThreadsParser.fragments (a spoiler alone is reason to trust Meta's split), ThreadsTextFragment.spoiler, ThreadsCardParts.ThreadsCaptionText/ThreadsCardRules.covered, ThreadsShared.post | ThreadsCardTest
native only | a topic shows its community's emoji or picture; a pinned post says "Pinned" (or "Pinned by the author" under its own post), on the card and in shared rows | ThreadsPostFacts(Reader), ThreadsCardParts.TopicTag/PinnedLine | ThreadsCardTest
native only | Meta's AI label ("AI info", hidden for "NONE") and its check-before-sharing request are lines under the words | ThreadsCardParts.FactLines | ThreadsCardTest.noAiLabelIsShownForNoneOrAShapeNotKnown
native only | the opened post counts its quotes and its shares (`reshare_count`) | ThreadsCardParts.FocalCounts | Compose only
native only | a poll's results through the app's poll card, read only; read from a list of options or `option_a…d` with percentages and `total_votes`; none was seen live, so absent or uncountable it draws nothing | ThreadsPostFactsReader.poll, ThreadsCardRules.card | ThreadsCardTest
native only | a post's inline media (`linked_inline_media`) stands in when it has none of its own | ThreadsParser.original | ThreadsCardTest.inlineMediaStandsInForAPostWithoutItsOwn
native only | the card's ⋯ and long press open its own sheet: Copy text and Copy link above the app's post sheet | ThreadsCardParts.ThreadsPostSheet, ThreadsShared.menu | Compose only
native only | a caption over 600 characters or 12 lines is cut to 10 lines in lists, with Show more / Show less; a long press on the author line shows the exact time | ThreadsCardParts, ThreadsPostCard.Header | ThreadsCardTest.aLongCaptionIsCutShortInAList
native only | a screen reader hears the card as one description (who reposted it, who and when, topic, words, labels) with Like on this device and More options as actions | ThreadsPostCard.rememberSummary, ThreadsCardRules.summary | ThreadsCardTest.theCardIsOneDescription…
## Threads, round 14: the conversation

Rows as above: state | what | where | test.

missing/different | a post page now preloads four queries (BarcelonaPostPageTargetQuery, …UpwardQuery, …DownwardQuery, BarcelonaLoggedOutRelatedPostsQuery; CI's shape probe); only `thread_items` was read, so a guest's conversation showed the related posts, one of them as the post itself, and none of the replies | networks/threads ThreadsPostPage.kt (new: the post from `data.media`, what it answers and its author's continuation from the thread posts around it, each `direct_replies` edge of the post itself as a reply thread, the related posts apart), ThreadsDirectClient.postPage (postChains kept over it), ThreadsReader.conversation, ThreadsConversation.from(page) | ThreadsConversationPageTest (a kept page in the probe's shape; what a `posts` connection holds inside was not logged, so it is read whatever holds it)
native only | replies nest by Meta's `reply_level`: the only answer to a post continues its chain with a rail, one of several steps in under the post it answers (three steps at most) with a guide line per step; without every level a thread stays one chain as before | ThreadsConversation.replyRows, ThreadsReplyShape, ThreadsReplyRows.kt | ThreadsConversationPageTest.repliesNestByTheirLevel…, withoutEveryLevel…
native only | an opened long reply thread can be cut again ("Show fewer replies"); a thread Meta holds more of ends in "This thread goes on at threads.com", and the replies in "More replies on threads.com" when `direct_replies.page_info.has_next_page` (no guest query is known to read the next page: no doc id, no variables) | ThreadsReplyRow.Less/OnWeb, ThreadsReplyRows (moreRepliesOnWeb) | ThreadsConversationTest.anOpenedThreadShowsEverything, ThreadsConversationPageTest.aThreadMetaHoldsMore…
native only | the related posts Threads shows a guest under a post sit after the conversation, under "Related threads" | ThreadsReplyRows.relatedRows | ThreadsConversationPageTest.relatedPostsAreNeverTakenForReplies
native only | the whole conversation read aloud with the app's voice (the kit's ReadAloudButton, as Substack's reader) | ThreadsConversationScreen, ThreadsConversation.spokenText | ThreadsConversationPageTest.theWholeConversationIsReadAloudPostByPost
missing/different | translate: the kit's translation is X's (XTranslator asks X by post id); nothing translates a Threads post's words, so there is none here | — | —
## Threads, round 13: media

Rows as above: state | what | where | test.

native only | a saved picture or video was "threads-<Meta's numbered file>" (471234_1838_n.jpg); it is "threads-<author>-<post's code>-<place>.<kind>", from the viewer's Download, a tile's long press and a video's ⋮ Download alike | ThreadsMediaFiles, ThreadsMediaScreen, ThreadsPostMedia (LocalMediaSave per tile), ThreadsViewed.source | ThreadsMediaTest.aSavedFileIsNamedAfterItsAuthor…, aFileWhoseAddressNamesNoKind…
native only | a long press on a picture showed its description or saved it at once; it opens a sheet: Image description, Download, Download all (one batch, one notice, beyond the screen), Open post, Copy link, Share link, each only where it applies | ThreadsMediaSheet (ThreadsMediaActions), ThreadsApp.beyondScreen | ThreadsMediaTest.aLongPressOffersOnlyWhatApplies…, downloadAllSaves…
missing/different | a picture without a description had no name for a screen reader; it is "Picture 2 of 4" (or "Picture"), its long press labelled | ThreadsMedia.MediaTile | Compose only
native only | media Meta links into a post (`text_post_app_info.linked_inline_media`, seen live) is shown where the post has none of its own | ThreadsMediaParts.of, ThreadsParser.original | ThreadsMediaPartsTest.mediaMetaLinksIntoAPost…
native only | media Meta lays a cover over (`media_overlay_info`, seen live; what it says is not read) waits behind "Threads put a warning over this media" and Show where Settings hides sensitive media, nothing loaded meanwhile | ThreadsMediaParts.covered, ThreadsPost.mediaCovered, ThreadsMediaCover | ThreadsMediaPartsTest.aPostIsCovered…, ThreadsMediaTest.coveredMediaWaits…
left out → round 15 | a liked or snapshotted post keeps no cover (ThreadsPostJson does not write `mediaCovered`; it does now, below); a video's own progress (the kit's player keeps none); captions, audio-only posts and music (`text_app_music_info`), whose shapes no probe has shown yet: ThreadsMediaShapeLiveTest (RUN_LIVE) logs their keys, the DASH manifest's representations and `media_type`s for the next round | n/a | n/a

## Threads, round 15: usable day to day

Rows as above: state | what | where | test. A walk of the plugin as a reader uses it, step by step through the code.

native only | the first open with no one followed is For you, which a guest reads at once (XTA opened on an empty Home); a kept section still wins | ThreadsForYouKept.openingSection, ThreadsApp.restoreForYou | ThreadsStartTest.theFirstOpenWithNoOneFollowedIsForYou…
native only | an empty Home offers For you beside Add account and Discover, and the public accounts every guest can read plus the authors For you showed, each with Follow in one tap | ThreadsStarters, ThreadsHome.NothingYet, ThreadsHomeParts.StarterPeople | ThreadsStartTest.theStartersComeFirst…
native only | Add account takes several handles, @handles or profile links at once (spaces, commas, lines), said under its field; nothing is followed when any of it is not a handle | ThreadsAccounts.handlesIn/addAll, ThreadsApp.followHandles, AddAccountDialog; string add_accounts_hint in 29 languages | ThreadsFollowingTest
missing/different | every follow and unfollow read twenty accounts again (a forced read); a follow from anywhere now says "Account added" and reads only what is not within its ten minutes | ThreadsApp.follow/followed/unfollowNow/restoreAccounts | Compose only
native only | with more follows than one load reads (twenty, six with a session), Home reads on a pause after each load while it is shown, until a load leaves as many unread as before; a throttle ends a load at once instead of asking for every account left | ThreadsCatchUp, ThreadsHome.CatchesUp, AccountPosts(stopsLoad) | ThreadsFollowingTest.homeReadsOn…, AccountPostsTest
missing/different | removing an account in Settings asked nothing (the tab's list and the profile ask "Unfollow @…?") | ThreadsSettings.Accounts | Compose only
native only | the session section says what a session adds; a parked session's notice over Home leads to the settings; Home's read failing over posts shown says so with Retry (it said nothing); the RSSHub section says what it is for | ThreadsSettings.Session/RssHub, ThreadsHome.ParkedNotice/HomePane; strings session_unlocks, instance_intro in 29 languages | Compose only
missing/different | a liked or snapshotted post kept no cover; `mediaCovered` is kept beside XTA's keys (written only when set), and the app's shared row covers such media where the reader hides sensitive media | ThreadsPostJson, ThreadsShared.post(covered), ThreadsMediaCover.hides | ThreadsPostJsonTest.metasCoverAndTheCardsFacts…, ThreadsCardTest.theSharedRowCoversMediaMetaCovered…

## Bluesky and Mastodon, round 10 (the chrome, the covers, the audit's items)

Rows as above: state | what | where | test.

missing/different | both drew a strip of chips under the app's bar, two bars on their own screens (bluesky_screen.dart's PluginHomeChrome, mastodon_navigation.dart's MastodonCompactBar) | BlueskyHome.HomeBar, MastodonHome.SectionBar over the kit's PluginHomeTabs | Compose only; PluginTabFitTest holds the folding
missing/different | Mastodon's Following had a second row for posts or accounts and Add account (mastodon_people.dart's one button with a ticked menu) | MastodonHome.FollowingMenu | Compose only
missing/different | a warning in Home's timelines, the groups and Saved was a line of text over blurred tiles (BlueskyContentWarning, _MastodonContentWarning: nothing drawn until Show) | ui/plugins NetworkPostExtras.cover, PostCover, PostCovers; BlueskyPostRow.ContentCover, MastodonPostRow.WarningCover | PostCoversTest
missing/different | a quoted Bluesky post showed its text over its media, and its link card (_QuotedPost: media, then text, no card) | NetworkPostExtras.quoteMediaFirst; BlueskyPostRow.toNetworkPost | Compose only
missing/different | a Bluesky post opened from a link showed a loading row alone (plugin_links.dart's _blueskyStub) | BlueskyRoutes PostTarget.stub, BlueskyThreadScreen's seed | BlueskyRoutesTest.aPostOpenedFromALinkIsShownByWhatTheLinkNamesWhileItIsRead
missing/different | "… reposted" over a Bluesky post led nowhere (_repostLine opens who reposted it) | NetworkPostExtras.onRepostLine without a line of its own; BlueskyPostRow.extrasOf | Compose only
missing/different | a Mastodon post drew media, link card, quote, poll (_SpoilerBody: quote, media, poll, link card) | NetworkPostExtras.quoteFirst; MastodonPostRow | Compose only
left out → round 13 | XTA's dock (plugin_home_dock.dart): the Filters button and the instance's name inside the bar, and its options sheet | Filters and the instance came in round 13 (below); the options sheet stays left out, its entries being the bar's own | n/a
left out | a quoted Mastodon post as XTA's _QuoteEmbed (a "Media" line for its pictures, a warning that does not open in place) | native shows the pictures and opens the warning in place | n/a
left out → round 13 | Bluesky's cover over a sensitive post's quote and link card where it has no media of its own | the plugin's own rows now cover them (round 13); the app's shared row (Home's mix, groups) still covers media alone | n/a

## Bluesky and Mastodon, round 13 (the dock, the cover, the audit's screens, and past XTA)

Rows as above: state | what | where | test.

missing/different | Filters and the order were a row over the list that scrolled away; XTA's dock (PluginDockFilterButton, on Home and in the microblog reader shell) puts Filters in the bar, a tune with how many are set, the order in its sheet | BlueskyHome.HomeBar, BlueskyFilters.FiltersButton (Following and Liked), MastodonHome.SectionBar, MastodonTimelineControls.FiltersButton (every timeline but Following's accounts), through PluginHomeTabs' actions, no kit change | MastodonPluginRulesTest.theBarsFiltersCountTheOrderAndTheSheetNamesOnlyLocalAndFederatedsInstance; the bar Compose only
missing/different | Local's and Federated's instance stood at the start of that row; XTA's compact dock shows it as the leading label of its options sheet | MastodonTimelineControls.FilterSheet (under its title), sourceOf | the same test
missing/different | a post a label covers the media of drew its quote and link card in the open, and with no media of its own showed no cover at all; XTA's one cover holds the media, quote and card back under the text (bluesky_post_card.dart's _body) | BlueskyPostRow.PostRow, NetworkPost.coveringAttachments (the text drawn as the row's `above`, the post's warning then covering the rest): every list of the plugin's own, its thread and its Saved; the app's shared row (Home's mix, groups, the app's Saved) still covers the media alone, which only a kit flag could change | BlueskyThreadStateTest.aMediaLabelOnAPostThatQuotesOrLinksCoversTheQuoteAndCardWithTheMediaButNotTheText
missing/different | an account without a picture was a blank disc; XTA draws FallbackAvatar (its initial on its colour, seeded by its handle) in Bluesky's people rows, search, follows lists, the strip of people a feed brings, the settings' follows and the profile, and in Mastodon's profile | BlueskyParts.PersonRow, BlueskySettingsScreen.FollowRow, BlueskyFollowingPane.PeopleRow, BlueskyProfileScreen.Header, MastodonProfileCard.Names, over the kit's PluginAvatar (v11) | PluginFacesTest (the kit's); Mastodon's people rows are the kit's NetworkPersonRow, still a blank disc
missing/different | Bluesky's Following, Mastodon's timelines and a hashtag's page loaded under a spinner; XTA paints PluginFeedSkeleton (bluesky_screen.dart's _HomePane, mastodon_screen.dart's panes) | BlueskyFollowingPane.followingStatus, MastodonTimelinePane.TimelineRowView, MastodonTagScreen | Compose only
missing/different | Bluesky's own search screen left the keyboard down when opened on nothing and up over the results after a search (XTA's field autofocuses and unfocuses on search), and autocorrected handles | BlueskySearch.BlueskySearchScreen | Compose only
covered | the rest of Bluesky's search (bluesky_search_sheet.dart line by line: People and Posts, Latest and Top, the filters' dialog and its chips, the landing's recent searches, imports and suggestions, Load more results and No more results) and of Mastodon's search (mastodon_search_results.dart), settings (mastodon_settings.dart), tag screen (MastodonTagScreen) and Bluesky's follows lists (bluesky_follows_screen.dart) | BlueskySearch.kt, MastodonSearch.kt, MastodonSettingsBody.kt, MastodonTagScreen.kt, BlueskyPeople.kt | the rows above; XTA's posts search saying "No matching accounts" stays native's "Nothing found for …"
missing/different | a feed or list not pinned showed the filled pin; XTA outlines it until pinned (bluesky_feeds_pane.dart's _selectedHeader) | BlueskySourcePanes.SelectedHeader | Compose only
missing/different | the handle fields of the imports and the add, open-feed and look-up dialogs autocorrected what was typed (XTA's fields set autocorrect: false) | BlueskyImport.ImportScreen, BlueskyParts.TextPrompt | Compose only
missing/different | Mastodon's thread menu button had no name for a screen reader | MastodonThreadScreen.BranchesMenu | Compose only
native only | Bluesky's Liked reads again on the desktop's F5 as its other tabs do, and Mastodon's followed accounts go to the top when the tab is tapped again | BlueskyFollowingPane.LikedPane, MastodonHome.PeoplePane | Compose only
covered | Bluesky's feeds and lists panes (bluesky_feeds_pane.dart), imports (bluesky_import_*_screen.dart, the 200 ms between pages included) and both thread screens (bluesky_thread_screen.dart, mastodon_thread_screen.dart), line by line | BlueskySourcePanes.kt, BlueskyImport.kt, BlueskyThread.kt, MastodonThreadScreen.kt | the rows above

## Threads, round 14: search (past XTA)

Rows as above: state | what | where | test.

native only | a search has People and Topics tabs beside Top and Recent: the accounts that wrote the top posts (those the words name first, with Follow on this device) and, with a session, its people search above them; the topics the posts are filed under, the words themselves offered as a topic. Both come from the page Top already read, so a guest gets them and Meta is asked nothing more | ThreadsSearchReading.people/topics, ThreadsSearchFound.related, ThreadsSearchFoundParts.foundPeople/foundTopics | ThreadsSearchFoundTest
native only | a topic's page opens under its name with the community's emoji or picture when Meta's `tag_header` carries one (`community_emoji`, `community_favicon_url`, seen in the CI probe; their values were not, so text other than a string, or a link not https, is left out) | ThreadsSearchReading.marks, ThreadsTopicHeader | ThreadsSearchFoundTest.aSearchPageNamesTheTopics…, aMarkThatIsNotText…
native only | typing in the search sheet or the search page's field suggests recent searches, accounts followed here and topics of the posts read this session, words starting with the typed text first; `@` asks for accounts, `#` for topics; nothing is asked of Meta per key | ThreadsSuggestions, ThreadsSuggestionList/Rows | ThreadsSearchSuggestionsTest
native only | each search tab keeps its place when another is chosen, and tapping the chosen tab again goes to its top | ThreadsSearchPage.ThreadsSearchBody | Compose only
missing/different | a search page (about 1 MB) was parsed on the screen's thread; it is now read off it | ThreadsDirectClient.searchFound | ThreadsSearchFoundTest.theClientReadsTheSearchPageOnce…

## Threads, round 15: paging (lists go on past the first page)

Rows as above: state | what | where | test.

missing/different | a guest's conversation showed the post and its related threads but no reply (CI's live run: "0 reply threads", "a conversation of 1"): Meta's Downward query answers its media in a shape not taken for the post, so its `direct_replies` were never read. Now the post's own connection, else the Downward media's `direct_replies` whatever that media holds, else the first `direct_replies` of the page's own queries; a reply found there is never taken for the author's continuation; and with no reply found, a page's `thread_items` chains stand in | ThreadsPostPageReader.arranged/mediaReplies/anyReplyConnection/orChains | ThreadsConversationPageTest.repliesAreFoundWhenTheDownwardQueryAnswersItsMediaOtherwise, aPageWhoseRepliesTheNewerReadingMissesKeepsItsChains
native only | every guest list goes on as the reader scrolls where its page names a cursor: a profile's Threads, Replies, Media and Reposts tabs, For you's public page, search's Top and Recent and a topic's page, and a conversation's replies. The page's preloaded query (`adp_<Name>RelayPreloader`), its doc id and variables, and its `page_info.end_cursor` are read off the page; the next page is asked of guest GraphQL as threads.com's client asks it (the same doc id, the variables with `after`, the page's LSD token, the Relay form fields); each item once, a few pages bringing nothing new passed over, a cursor that comes round again ending the list, 600 items at most in memory; a spinner, Retry with Meta's words (a throttle's end time), or "No more posts" under the list; ThreadsRetryBudget's waits and the client's spacing pace it; the reads end with the screen. Where no cursor is named, or Meta answers in a shape not understood, the list ends in today's "open in browser" / "More replies on threads.com" foot | networks/threads ThreadsNextPages.kt, ThreadsPaging.kt, ThreadsDirectClient.more…/profileNext/guestQuery; plugins/threads ThreadsMoreRows.kt, ThreadsProfileScreen (ThreadsProfilePaging), ThreadsSearchPage.Results, ThreadsConversationScreen, ThreadsForYou (the public page's cursor before re-reads); string list_end in 29 languages | ThreadsNextPagesTest, ThreadsPagingTest
left out | whether Meta answers a guest's next page this way is CI's to tell: ThreadsPagingLiveTest (RUN_LIVE) logs, per page, each preloaded query's doc id and variable names, where the LSD token and app id sit (key paths), every page_info, and one next-page request's status, type, error codes, posts and new cursor (`LIVE Threads paging …`) | ThreadsPagingLiveTest | —

## Threads, round 16: the day-to-day walk

Rows as above: state | what | where | test. Three things a day of reading turned up: accounts that fail without a name, one slow account holding the rest, and signing in.

native only | a followed account that keeps failing was never named: it stayed among "N more accounts still to read". Each account's last failure is kept with its kind (missing — renamed, deleted or mistyped; private; throttled, with Meta's end time; Meta's sign-in page; network), and over Home's feed "N accounts could not be read" opens the list of them, each with why, Retry (that account alone, read again now), its profile, and Unfollow on this device. A missing or private account is that account's answer for its ten minutes: it is not "still to read", and a load does not ask it again in that window; an account that answers again leaves the list. A guest read of a private account (its page there, no threads) is told apart from a missing one | networks/threads ThreadsAccountFailure.kt (ThreadsTrouble, new), AccountPosts (failures, settles, forgetFailure), ThreadsReader.failures/retry, ThreadsFailure.PRIVATE_ACCOUNT, ThreadsDirectClient.guestAccount; plugins/threads ThreadsFailedAccounts.kt (new), ThreadsApp.failures/retrying/retryAccount, ThreadsFailures.trouble; strings accounts_failed, account_missing, account_private, retry, unfollow_on_device in 29 languages | AccountPostsTest.aMissingAccountIsNamed…, anAccountThatAnswersAgain…; ThreadsFailedAccountsTest
native only | followed accounts were read one at a time, so one slow account (up to the 25 s timeout) held every account after it; three are now read at once. The client still lets requests leave one at a time, the guest or session gap apart, so Meta sees no more requests than before: the gain is that a slow answer no longer blocks the queue. A throttle stops the load at once, the reads beside it given up before they ask (not named as failed); the feed's order stays by time, ties in the follows' order; partial paints never land out of order; Home's load is given up once Home has been gone two seconds (a rotation is not gone), a load started elsewhere (a follow from a profile) runs on | AccountPosts.merge/noting/unlessStopped, ThreadsReader.ACCOUNTS_AT_ONCE; plugins/threads ThreadsShownLoads.kt (new), ThreadsApp.readFeed/homeLoads, ThreadsHome.HomePane | AccountPostsTest.threeAccountsAreReadAtOnce…, ThreadsShownLoadsTest, ThreadsPacingTest (the gap holds across reads started together)
native only | a session meant pasting cookies or a Bearer by hand, with no word on where to find them and nothing said of a wrong paste until Meta refused it. On Android, Settings offers Sign in on threads.com: Threads' own page in the plugin's web view (as Pixiv's sign-in and the app's X sign-in do; the plugin API offers no web sign-in, and none was added), whose cookies are looked at each second until they hold a whole session; then only the five the cookie API sends are kept where a pasted session is (the app's secret preferences, in no backup), the web view's own threads.com and Instagram cookies are expired, and the session is tried once with a read: "Signed in as @handle". Everywhere, "Where to find the cookies" says step by step how a desktop browser shows them (open on the desktop); a paste is read as it is typed (a `Cookie:` name and a cookie table's rows are understood, the rest of the header dropped) and what is wrong is said under its field: not a Cookie header, a Bearer in the cookie field, which cookies are missing, a Bearer that is not `IGT:2:`; Test session says that without asking Meta, else "Signed in as @handle" (the Bearer's test as before). The session stays opt-in for followed accounts; nothing is written to Threads | networks/threads ThreadsSessionCheck.kt (new); plugins/threads ThreadsSignIn.kt (new; expect webSignIn, ThreadsLoginPage), androidMain ThreadsSignIn.android.kt, desktopMain ThreadsSignIn.desktop.kt, ThreadsRoutes (sign-in), ThreadsApp.signIn/checkSession/signedIn/clearSession, ThreadsSettings.Session/WebSignIn/SignedIn/CookieSteps, ThreadsFailures.pasted/untestable; strings sign_in_web, sign_in_web_detail, cookies_how, cookies_steps, cookies_not_header, cookies_bearer, cookies_missing, bearer_invalid, signed_in_as, session_empty in 29 languages | ThreadsSessionCheckTest, ThreadsFailedAccountsTest.aSessionThatCannotBeTested…, theSignInPageIsAScreenOfItsOwn; the web view itself is Android only, compiled in CI
missing/different | Home's Retry could be pressed while Meta's throttle lasted, and pressing it within the wait is how a throttle becomes a ban; it is now disabled and reads "Try again after 10:15" (Meta's end time) until the wait ends, then wakes by itself, in place of the feed and over it alike; a throttled account's Retry in the failed accounts' list waits the same way | ThreadsParts.RetryOrWait/FailedRead (new), ReloadStatus(retryAfter), ThreadsHome.HomePane, ThreadsFailedAccounts, ThreadsFailures.retryBlockedUntil; string retry_after (there already) | ThreadsFailedAccountsTest.retryWaitsOnlyForAThrottleWhoseEndIsAhead; the button itself is Compose only

## Threads, round 17: discovery, reposts and communities

Rows as above: state | what | where | test. The owner, beside threads.com: other people's reposts never showed, communities were missing, and For you was five posts. CI's live probe `ThreadsDiscoveryLiveTest` (RUN_LIVE, lines `LIVE Threads discovery …`) logs what this round could not see: a community's `tag_header` keys and page, the reposts pages, the front page's queries and re-reads, the empty search page.

missing/different | other people's reposts never showed: a profile's own page (and guest GraphQL's profile read) carries none, so its Reposts tab filtered nothing and Home had none. CI's paging probe saw `/@handle/reposts` preload `BarcelonaProfileRepostsTabDirectQuery` with `share_info.reposted_post` posts; the Reposts tab now reads that page when it opens (pull to read again, the next page where it names one, "No reposts on the public page" when empty), and Home reads the followed accounts' reposts pages beside their threads, three pages a load at most, each kept half an hour, never a failure of Home's own. A post an account wrote and another reposted is one card, the newest (a repost dated when it was reposted), with "<name> reposted" over it; Hide reposts hides them; the app's timelines get the reposts already read | ThreadsDirectClient.guestReposts/moreReposts, ThreadsPages.reposts, ThreadsReader.extras/heldExtras/repostsPage, ThreadsFeedMix (new), ThreadsApp.readFeed/SharedFeed, ThreadsProfileScreen (REPOSTS lists), ThreadsProfileHeader.reposts; string reposts_empty in 29 languages | ThreadsRepostsTest, ThreadsProfileHeaderTest.theRepostsTabTakesTheRepostsPageNewestFirstEachOnce; whether a reposts page names a next page a guest can read is CI's to tell
missing/different | a post's community ("› 🎭 Cosplayers of Threads" beside the author on threads.com) showed only where its `tag_header` named it `display_name` or `name`, as a grey topic opening a topic search. The name is now read under every key it might be under (`display_name`, `name`, `community_name`, `title`, `tag_name`, a nested `community`), a community is told from a topic (`is_community`, its emoji or favicon, a community id), its id and own threads.com page are kept when Meta names them (a link elsewhere, a profile or a post is not), and the card shows it in the accent colour with its mark; a tap opens the community's page. Kept with liked and snapshotted posts | ThreadsCommunities (new), ThreadsParser.topicTag, ThreadsPostFacts(community, communityId, communityUrl), ThreadsFactsJson, ThreadsCardParts.TopicTag | ThreadsCommunitiesTest, ThreadsTopicsTest.aPostsCommunityIsWhatItsCardOpens…; which keys a community's header really has is CI's probe's to tell
native only | a community has a page of its own in the plugin: its mark and name, Follow on this device, its posts page after page where Meta names a next one, pull to read again, share and open in browser, Retry when it fails and threads.com when it is empty. Its own threads.com page is read where Meta named one, else the posts filed under its name stand in. A threads.com `/community/…` or `/communities/…` link opens it (it opened as the account "@communities") | ThreadsCommunityScreen (new), ThreadsRoute.Community, ThreadsRoutes.community/forUrl, ThreadsLinks (ThreadsLink.Community), ThreadsDirectClient.communityPage/moreCommunity; strings community, community_posts in 29 languages | ThreadsTopicsTest.aCommunityOpensItsOwnRoute…, ThreadsCommunitiesTest.aCommunityLinkIsNeverTakenForAnAccount, aCommunityPageIsRead…
native only | topics and communities can be followed on this device (the topic page's and the community page's Follow, Following to stop), kept in the plugin's folder; Home reads their pages beside the followed accounts (with the reposts' budget: three pages a load, each kept half an hour), and Home with topics but no account shows their posts. The search page's Topics tab lists communities first, marked "Community", each opening its page | ThreadsTopics (new), ThreadsApp.followTopic/unfollowTopic/openTopic/readFeed, ThreadsReader.extras (topic and community pages), ThreadsSearchFoundParts.ThreadsTopicHeader/foundTopics; string topic_followed in 29 languages | ThreadsTopicsTest, ThreadsCommunitiesTest.aCommunityPageIsReadWithItsMarksAndHomeReadsAFollowedOne
missing/different | a guest's For you was the front page's five posts, replaced by the next five on each refresh, and ended after two re-reads (CI: the front page's next page answers a guest nothing). A guest's refresh now puts the new posts above those shown (300 held, 160 kept on disk around the reader's place), so the selection grows; past the re-reads it explores, one page per scroll to the end, the topics and communities followed here, then those of the posts For you and Home show (communities and the most posted in first), then a few broad topics every guest can read, each once per selection and twelve at most; a page with nothing new is passed over for the one it names (twice at most) instead of ending the feed; and a front page that shows a guest nothing gives way to exploring rather than "sign in" | ThreadsForYou (explore, MAX_EXPLORED), ThreadsForYouFeed (KEPT, EMPTY_PAGES_PASSED), ThreadsForYouKept.CAP, ThreadsDiscovery.topics/topicOf/page/STARTER_TOPICS, ThreadsApp.exploreTopics/discoveredTopics | ThreadsExploreTest, ThreadsForYouKeptTest.aLongFeedKeeps…
native only | For you shows a strip of topics and communities to explore (followed first, each with its mark, opening its page) after its first eight posts, and its people to follow open from the start | ThreadsForYouRow.Topics, ThreadsSearchFoundParts.TopicStrip, ThreadsForYouPane, ThreadsHomeParts.PeopleStrip(startOpen) | ThreadsForYouKeptTest.theTopicsToExploreSitFurtherOn…; the strip itself is Compose only
left out | Meta's own trending topics and suggested accounts for a guest: no probe has seen them on a guest page; ThreadsDiscoveryLiveTest logs the empty search page's suggestion-like key paths for the next round. The front page's `BarcelonaLoggedOutFeedPaginationQuery` (seen by name only, CI's paging probe) has no doc id a page names; the probe looks for one near its name | — | —
missing/different | CI's discovery probe (threads-shape4): every `tag_header` carries `display_name`, `tag_cluster_name`, a numeric `id` and `is_community` false, communities included (12 of 12), and 9 of 12 a community emoji and favicon; no community address, nor any doc id for the community queries. A community is now told by its emoji or https favicon (`is_community` only when true), its `id` kept as the community's then; a `display_name` written as a slug gives way to `tag_cluster_name`, which also stands in when there is no name. The community page reads the posts filed under its name (CI: twenty for a guest, no next page) whenever its own page is unknown, fails or is empty. Reposts read as the probe saw them (`/@x/reposts`, the outer post by the reposter with no caption and its own code, the original under `share_info.reposted_post`): the card links the original, and the original and its repost are one card. The front page's cursor answers a guest nothing while a re-read brought five posts not seen before each time, so an empty next page gives way to reading the page again, and For you reads it again four times (it was two) before exploring | ThreadsCommunities.name/isCommunity, ThreadsPostFactsReader, ThreadsSearchReading.mark, ThreadsCommunityReads.read, ThreadsCommunityModel, ThreadsForYou.guestMore/GUEST_EXTRA_READS | ThreadsLiveShapesTest, ThreadsTopicsTest.aCommunityPageFallsBack…, ThreadsForYouTest, ThreadsExploreTest
left out | the empty search page preloads `BarcelonaSearchTrendingTopicsSectionQuery` with `result.data.trends` (CI): Meta's own trending topics for a guest, not read yet | — | —

## Threads, round 18: discovery, search and feeds (the audit)

Rows as above: state | what | where | test. What threads.com offers a reader in search, topics, communities, trending and its feeds, guest and signed in, beside what the plugin has; then what this round added. Post-level features (quotes, reposted-by, attachments) are another round's.

The audit. Guest: threads.com's search page lists "Trending now" before a search; a search has Top and Recent (`filter=recent`), a topic (`serp_type=tags`) the same two with the topic's header; a profile search is a session's (a guest page carries no user objects, CI); communities have their own pages; the front page is For you. Signed in: Following and For you feeds, custom feeds (`BarcelonaCustomFeedPickerPageQuery`, `BarcelonaGetCommunityCustomFeedQuery`, `BarcelonaRefetchCustomFeedsContextQuery`, named on every page CI read, no doc id near any of them), joined communities, people search, suggested accounts.

have | search's Top, Recent, People (the top posts' authors, and with cookies Meta's people search) and Topics (communities first), page after page; a topic's page with its mark and Top and Recent; a community's page; topics and communities followed on this device feeding Home and For you; a guest's For you (the front page, re-read, then explored); a Bearer session's For you, page after page; suggestions as the reader types; recent searches | rounds 14 to 17 | as there
missing/different | Meta's trending topics, preloaded by the empty search page for a guest (`BarcelonaSearchTrendingTopicsSectionQuery`, `result.data.trends`), were never read | below | below
left out | Threads' own Following feed and custom feeds for a signed-in reader: the custom feeds' queries are named on every page with no doc id near them, and a page signed in is never read (the cookie session reads the `/api/v1` REST API only, and no REST route for either is known); the Bearer API's Following timeline is not known either. Home is the following feed read from the accounts followed here | — | —
left out | a guest's profile search: no guest page seen so far carries user objects; ThreadsDiscoveryLiveTest now asks two forms a profiles search page might take (`serp_type=accounts`, `filter=profiles`) and logs their user objects and queries | — | —
left out | a post's `text_post_app_info.related_trends_info` (CI: on 2 or 3 posts a page): the trend a post is part of, a card's matter | — | —
left out | joined communities, suggested accounts and notifications of a signed-in reader: no read of a signed-in page | — | —

native only | before a search (the app's search landing and the Threads search sheet, while nothing is typed), "Trending now" lists Meta's trending topics as threads.com shows a guest: each with the line Meta writes under it (a context such as a count, and its summary), opening its search (a topic's page where Meta names a topic or a `serp_type=tags` link). One guest read of `threads.com/search`, no session spent, kept half an hour and shared while in flight; a guest sent to sign in has none; nothing shows while there are none; a failed read says why with Retry, which during a throttle waits for its end. A search a trend opened shows what Meta wrote of it above its tabs, from the trends already read. The items' keys were not seen (the probe saw only `data.trends`), so each item is read under every key it might use (`title`, `name`, `trend_name`, `query_text`, `search_query`…; a connection's `edges[].node`; a `search_url`), an item without a name is left out, at most twenty | networks/threads ThreadsTrends.kt (new), ThreadsDirectClient.trends/knownTrends; plugins/threads ThreadsTrending.kt (new: TrendingTopics, TrendHeader), ThreadsSearch.BeforeSearch, ThreadsSearchPage.ThreadsSearchBody; string trending_now in 29 languages | ThreadsTrendsTest
left out | whether the trends' items carry these keys is CI's to tell: ThreadsDiscoveryLiveTest (RUN_LIVE) logs, for the empty search page, the kind of `data.trends`, its items' keys and the kind of each, their nested keys, how many ThreadsTrends read (with a context, a summary, as topics), and the trending query's doc id and variable names (`LIVE Threads discovery search trend…`); kinds and counts only | ThreadsDiscoveryLiveTest | —

## Threads, round 18: posts and conversations

Rows as above: state | what | where | test. An audit of what threads.com shows a reader on a post and in its conversation, guest and signed in, against what the plugin draws. Already there before this round: quoted posts boxed inline, GIFs, link cards, carousels, spoilers, polls, Meta's AI label and check-before-sharing, pinned, community and topic chips, the reply order (Relevant, Recent, Oldest), "Author" beside the author's replies and their filter, nested replies, related threads, Copy text, Copy link, Share link, who liked a post (signed in), read aloud. What CI's shape probes saw a guest given and nothing read: `caption_is_edited`, `text_post_app_info.is_liked_by_root_author`, `reply_control`, `reply_approval_info`, `text_app_music_info` (a song's title, artist and cover), `caption.can_translate`/`has_translation`, and on the post page the query names `BarcelonaInlineRepliesExpansionQuery` and `BarcelonaPostPendingRepliesDialogQuery`, with the relay switches `BarcelonaHasPublicViewCountCard`, `BarcelonaHasGhostPostProduction`, `BarcelonaHasStitchedAttachment` and `BarcelonaHasPodcastV2Consumption`. CI's new probe `ThreadsPostListsLiveTest` (RUN_LIVE, lines `LIVE Threads post lists …`) logs what this round could not see.

missing/different | who reposted a post: Threads lists them for a signed-in reader, the plugin did not. The repost count opens "Reposted by" for a signed-in reader (the likers' screen and rows), and the card's sheet offers it; without a session the screen says where to sign in. No probe has seen the path the app reads it at: `text_feed/{media}/reposters/` then `media/{media}/reposters/` are tried in turn (a 404 moves on, the one that answered is asked first afterwards, a throttle stops at once and parks the session), with the session's pacing; none answering says "Threads does not give this list here" with Retry | networks/threads ThreadsPeople (REPOSTERS, paths, `reposters` rows), ThreadsDirectClient.sessionApi; plugins/threads ThreadsPostPeopleScreen, ThreadsRoute.Reposters, ThreadsPostCard.Engagement, ThreadsCardParts.PostListRows; strings reposted_by, reposters_empty, show_reposted_by, list_unavailable in 29 languages | ThreadsPostListsTest, ThreadsPostListsViewTest
missing/different | a post's quotes ("Quotes" under the opened post) were only counted. The count opens the posts that quote it for a signed-in reader, each a feed card with the quoted post boxed, page after page where Meta names `next_max_id` or `paging_tokens.downwards`, pull to read again, loading, Retry with Meta's words, empty and end; read from a timeline's `threads`/`items` or a plain `medias`/`posts`/`quotes` list, each post once, never the quoted post itself. Paths unconfirmed as for reposters (`text_feed/{media}/quotes/`, then `media/{media}/quotes/`) | networks/threads ThreadsQuotes (new), ThreadsDirectClient.quotes; plugins/threads ThreadsQuotesScreen (new), ThreadsRoute.Quotes, ThreadsCardParts.FocalCounts/PostListRows, ThreadsPostLists; strings show_quotes, quotes_empty in 29 languages | ThreadsPostListsTest.aQuotesPageReads…, theCookiesAskThreadsCom…, ThreadsPostListsViewTest
native only | a post whose words were changed says "Edited" after its time (and after the opened post's date); a reply the opened post's author liked says "Liked by the author" under its words (threads.com's mark); the opened post says who may answer it when not everyone ("Only accounts mentioned in the post can reply" and three more), read from Meta's words for it and nothing else; all heard in the card's description and kept with liked and snapshotted posts | ThreadsPostFacts (edited, likedByAuthor, replyAudience: ThreadsReplyAudience), ThreadsCardRules.timeLine, ThreadsCardParts.FactLines/ReplyAudienceLine, ThreadsFactsJson; strings edited, liked_by_author, replies_limited_* in 29 languages | ThreadsPostMarksTest, ThreadsPostListsViewTest
native only | a post's song (`text_app_music_info`, seen live on a topic page) is named under its words with its cover, as "Title · Artist"; nothing is played (its audio is a DASH manifest the player does not take) | ThreadsPostFactsReader.music, ThreadsMusic, ThreadsCardParts.MusicLine, ThreadsIcons.MusicNote; string music in 29 languages | ThreadsPostMarksTest.aPostsSongIsNamed…
missing/different | a reply thread Meta holds more of ended in "This thread goes on at threads.com", leaving the app; it is "Continue this thread", opening the conversation of its last post shown, whose page carries the replies under it (a reply's page is read as any post's; CI's conversation probe reads one) | ThreadsReplyRow.OnWeb.continuesAt, ThreadsReplyRows; string continue_thread in 29 languages (thread_on_web gone) | ThreadsPostListsTest.aThreadMetaHoldsMoreOf…
left out | view counts: the relay switches name a public view count card, but no post a guest was given carried a count; translation: Meta flags `caption.can_translate`, no guest read of a translation is known, and the app's translator is X's; ghost (expiring) posts: a switch only, no key seen on a post; long-text attachments: no `attachment` key past link previews and quotes was seen; podcasts, and playing a song: shapes not seen, or not playable; replies held for approval (`BarcelonaPostPendingRepliesDialogQuery`): shown to the author only. The probe logs `reply_control` and `reply_approval_info` shapes, the post page's quote, repost, inline-expansion and pending-replies queries with any doc id beside them, `/quotes`, `/reposts` and `/likes` under a post, and how each session path answers a guest (a 404 where the likers' path asks for a session says the path is wrong) | ThreadsPostListsLiveTest | —

## Threads, round 19: post content

Rows as above: state | what | where | test. What round 18 left for later on a post's content, as far as the data allows. Seen live (CI's shape logs, guest): `detected_language` on nearly every post, `caption.can_translate`/`has_translation`, `text_post_app_info.related_trends_info` (2 to 8 posts a page, its own keys not logged), `root_post_author`, `community_badge.badge_type`. Not seen on any post a guest was given: a view count, a ghost post, a text attachment; each is read under the names it is likely to take and only where it holds what it should, and a new probe logs what a guest is given.

native only | Translate, as threads.com offers it: Meta's own translation, for a signed-in reader (the app's translator is X's Grok, which translates X posts by their id only and cannot take a Threads post's words; no plugin API change). Offered from the card's sheet and under the opened post's words where the reader is signed in, the post has words and no spoiler, Meta does not say `can_translate` false, and its `detected_language` is not the reader's (the app's translation language setting, decided as the app's X posts decide it; a post with no language keeps the offer). The translation stands in for the words with "Translated by Threads" and Show original; "Translating…" while asked; "Threads could not translate this post" with Retry. Asked once per post and language, kept for the run (200 posts), shared by a card and its opened post; going back and forth asks nothing. Path unconfirmed: Instagram's `language/translate/?id=&type=1` (the caption's id where `caption.pk` is given), then `text_feed/{media}/translate/`, then `media/{media}/translate/`, in the reader's language (Accept-Language), with the session's pacing; when none answers, the offer is withdrawn for the run rather than shown to fail again | networks/threads ThreadsTranslation (new), ThreadsDirectClient.translation (sessionApi takes a language), ThreadsPostContent.language/canTranslate/captionId; plugins/threads ThreadsTranslations (new: the state, ThreadsTranslationRules), ThreadsPostContentParts (new: TranslationLine), ThreadsCardParts.TranslateRow, ThreadsApp.translations; strings translate, show_original, translated_by_threads, translating, translation_failed in 29 languages | ThreadsPostContentTest, ThreadsPostContentViewTest
native only | a post's attached long text (Threads' text attachment) in a box under its words, "Attached text" over it, its title where given, cut to six lines with Show more in a list and whole (selectable) on the opened post, its link where it carries one. Read from `text_attachment` (the public API's name: `plaintext`, `link_attachment_url`) or `long_text_attachment`/`text_attachment_info`/`attached_text`, on `text_post_app_info` or the post, as plain text, `text`/`body`, or fragments; https links only, `l.threads.com` unwrapped | ThreadsPostContent.attachment, ThreadsTextAttachment, ThreadsPostContentParts.TextAttachmentBlock; string attached_text | ThreadsPostContentTest.attachedTextIsRead…, ThreadsPostContentViewTest
native only | views ("1.2K Views", first among the opened post's counts) where a post carries a count: `view_count`, `views_count`, `public_view_count` or `impression_count` on the post or its `text_post_app_info`, or a `count` under `view_count_info`/`view_counts`; a number only (words such as "1.2K" are not read) | ThreadsPostContent.viewCount, ThreadsCardParts.FocalCounts; string views | ThreadsPostContentTest.aViewCount…
native only | a ghost post says so over its words: "Ghost post · disappears in 3 hours" (minutes in its last hour), "Ghost post" when its time is not known or past. Marked only by a true `is_ghost_post` (or `is_ghost`, `ghost_post`) or a ghost object (`ghost_post_info`, `ghost_info`), never by an expiry alone; it ends when that object (or the post, once a ghost) writes it, in seconds or milliseconds, else a day after it was posted (Meta's rule for ghost posts) | ThreadsPostContent.ghost, ThreadsGhost, ThreadsPostContentParts.GhostLine, ThreadsContentRules.ghostLeft; strings ghost_post, ghost_post_hours, ghost_post_minutes | ThreadsPostContentTest.aGhostPost…, ThreadsPostContentViewTest.aGhostPostSays…
native only | the trends a post is part of (`related_trends_info`, seen live) as chips under it, "Trending · Name", each opening its search; read as Meta's trending topics are (ThreadsTrends' keys), as a list, a wrapper holding one, or one trend itself, each once, at most three | ThreadsTrends.related, ThreadsPostContent.trends, ThreadsPostContentParts.TrendChips; string post_trend | ThreadsPostContentTest.aPostsTrends…
native only | all of these (language, translatability, views, ghost mark, attached text, trends) kept with liked and snapshotted posts, and heard in the card's description | ThreadsFactsJson, ThreadsPostCard.rememberSummary | ThreadsPostContentViewTest.aKeptPostKeeps…
have | shares (`reshare_count`) on the opened post, "Replying to @…" from `reply_to_author` | round 18 and before | as there
left out | a community member's badge (`community_badge.badge_type`, seen on 2 posts): its values are not known, so nothing is drawn; `root_post_author` (the thread's first author, seen on replies): "Replying to" already names whom a reply answers. The probe logs the badge's keys and one-word types | ThreadsPostContentLiveTest | —
left out | whether Meta's translation answers at any of the paths, and the shapes of views, ghost posts and attached text, are CI's to tell: ThreadsPostContentLiveTest (RUN_LIVE, `LIVE Threads post content …`) logs, for a topic search, a profile and a post, the languages named (codes only), the caption's keys, `related_trends_info`'s kinds and key paths, `community_badge`'s keys, the key paths that may hold views, a ghost mark, attached text or a translation, how many posts each is read from, the post page's queries about them with any doc id, and how each translation path answers a guest (a 302 to sign in: real; a 404: wrong). ThreadsShape now counts `detected_language` and `related_trends_info` as read and lists trend, view, ghost and expiry paths among the features | ThreadsPostContentLiveTest, ThreadsShape | —

## Threads, round 20: guest profiles, posts and media

Rows as above: state | what | where | test. An audit of what threads.com shows a reader without a session on a profile, a post and its media, against what the plugin draws; signed-in features (Following, activity, saved) are another round's. Seen live, in CI's run 107 (`LIVE Threads shape …`, guest): on a profile's own user object `profile_tags` (as `profile_tags.edges[].node`, with `community_favicon_url`), `platform_podcast_info` (on @threads), `bio_links`, `show_text_post_app_badge`, `interop_messaging_user_fbid`, and `inline_sticker_fragment` among the bio's fragments; on posts `text_post_app_info.community_badge.badge_type` (MEMBER, on a post page), `has_audio`, `media_type` 1, 2, 8 and 19, `share_info.can_quote_attachment` and `quoted_attachment_author_attribution_allowed`. Not seen: a fediverse key, a join date or "about this profile", a play count, a vanished quote's flag (the probes count only keys that hold something, so a false flag was never logged), a poll.

The audit. Already there: the verified badge (profile and posts), a private profile's lock and line, the bio with its mentions, tags and links as Meta split it, every bio link, the exact follower count, the picture full size, the Threads, Replies, Media and Reposts tabs, a pinned post, a picture's description (ALT and its dialog, "Picture 2 of 4" for a screen reader), a carousel's dots, a poll's results with its votes and "Ends in" / "Ended" (the app's poll card), GIFs, videos played in place, a post's song, Meta's media cover, spoilers, the topic or community beside the author, Meta's AI label, edited, liked by the author, who may reply, views, ghost posts, trends, quotes boxed, link cards. Missing, and added below: the topics a profile lists, its podcast, its fediverse sharing, a community member's badge, a quote Threads no longer shows, a video's plays, and (past threads.com) the Media tab as a grid.

native only | a profile's topics and communities (threads.com's chips under the bio) as chips under the bio, each with its emoji or favicon, opening the community's page or the topic's search; read from `profile_tags` as CI saw it (`edges[].node`) or a plain list, each node as a post's `tag_header` is read (ThreadsCommunities: its name, a slug giving way to `tag_cluster_name`, a community told by its mark), each once, ten at most; a node without a name is left out. Kept with a kept profile | networks/threads ThreadsGuestFacts.kt (new: ThreadsProfileExtras.tags), ThreadsProfileFacts.tags, ThreadsProfileOwner, ThreadsParser.profile; plugins/threads ThreadsGuestParts.kt (new: ProfileTags), ThreadsProfileChrome.ThreadsProfileCard, ThreadsProfilesKept | ThreadsGuestFactsTest.aProfilesTagsAreRead…, theOwnersObjectCarries…, ThreadsGuestViewTest.aKeptProfileKeeps…
native only | a profile's podcast (`platform_podcast_info`, seen live; its own keys not yet) as "Podcast · Its name" under the links, opening where it is heard; read under the names it is likely to take (`title`, `podcast_name`, `show_name`…; `url`, `podcast_url`, `show_url`…, over https only, Meta's redirect unwrapped; `platform` as people write it, "APPLE_PODCASTS" as Apple Podcasts), at the top or under `podcast`/`show`; nothing without a name or an address | ThreadsProfileExtras.podcast, ThreadsPodcast, ThreadsGuestParts.ProfilePodcast; string podcast | ThreadsGuestFactsTest.aProfilesPodcastIsNamed…
native only | a profile that shares to the fediverse says so under its links, "Shares to the fediverse · @handle@threads.net" (the address Threads federates every account under, or the one Meta gives), a tap copying the address. Guessed: no key was seen; any key of the user object naming the fediverse, federation or ActivityPub that holds true, an object saying it is enabled, or an address marks it; a key about being eligible, a prompt or what a reader can do does not | ThreadsProfileExtras.fediverse, ThreadsFediverse, ThreadsGuestParts.ProfileFediverse; strings fediverse_sharing, fediverse_copied | ThreadsGuestFactsTest.fediverseSharingIsShownOnly…
native only | a community member's badge (`community_badge.badge_type`, seen live as MEMBER) as "Member" beside the author, as "Author" is drawn; Founder, Moderator, Champion and Top contributor are read where Meta's words name them (the relay switches name founder, champion and top-contributor cards), any other word draws nothing; heard in the card's description, kept with liked and snapshotted posts | ThreadsCommunityBadge, ThreadsGuestMarks.communityBadge, ThreadsPostFacts.communityBadge, ThreadsPostCard.Header (AuthorChip), ThreadsFactsJson; strings badge_member, badge_founder, badge_moderator, badge_champion, badge_top_contributor | ThreadsGuestFactsTest.aMembersBadge…, ThreadsGuestViewTest
native only | a post quoting one Threads no longer shows has "This post is unavailable" boxed where the quote would be (a post that only quoted it is no longer dropped as empty). Guessed: a true `share_info` flag whose name says a quoted post is unavailable (`quoted_attachment_post_unavailable`, `quoted_post_unavailable`); a quote that is there is drawn whatever the flag | ThreadsGuestMarks.quoteUnavailable, ThreadsParser.original, ThreadsPostCard.QuoteUnavailable; string quote_unavailable | ThreadsGuestFactsTest.aQuoteThreadsNoLongerShows…
native only | a video's plays ("1.2K Plays") among the opened post's counts, where Meta gives a number under a play key (`play_count`, `video_play_count`, `ig_play_count`…) on the post or its carousel's items, the largest; none was seen on a guest's post | ThreadsGuestMarks.playCount, ThreadsCardParts.FocalCounts; string plays | ThreadsGuestFactsTest.aVideosPlays…
native only | the profile's Media tab as a grid, a toggle at the end of its filters: one square per post, its first picture or video's poster, marked when the post holds more or is a video; a tap opens its media in the viewer (with Open post), a long press the post; media Meta covered waits behind its mark where the reader hides sensitive media and opens the post instead. Drawn from the posts the tab already has: nothing more is read | ThreadsMediaGrid, ThreadsGuestParts.MediaGridRow, ThreadsProfileChrome.ContentToggles, ThreadsProfileScreen, ThreadsIcons.GridView/ViewAgenda; strings media_as_grid, media_as_list, media_video | ThreadsGuestViewTest.theMediaGridIsOneSquarePerPost…; the grid itself is Compose only
have | the requests: nothing here asks Meta anything more (ThreadsRequestCountTest unchanged) | — | ThreadsRequestCountTest
left out | "About this profile" and a join date: no guest page seen carries either, and no query about them was named; the bio's stickers (`inline_sticker_fragment`, seen live): their shape is not known, and a bio whose split they break is read by patterns as before; a silent video (`has_audio` false) and audio-only posts: no `audio` key on a guest's post (CI's media probe); a poll: none seen yet, read as round 14 has it | — | —
left out | which of these keys a guest is really given is CI's to tell: ThreadsGuestLiveTest (RUN_LIVE, `LIVE Threads guest …`) logs, for @threads, @zuck and @instagram, every key of the profile's own user object with its kind (false and null included), the shapes of `profile_tags`, `platform_podcast_info`, `bio_links` and the bio's stickers, the key paths naming the fediverse or a join date, and queries about them; for those profiles, a topic search, a video search and a community post, `share_info`'s keys with their kinds, `community_badge`'s types, play-like keys, described pictures and `has_audio`; and how many of each the plugin read. ThreadsShape counts `community_badge` as read. threads.com was not reachable from this round's machine, so nothing here was read live beyond run 107's log | ThreadsGuestLiveTest, ThreadsShape | —
## Threads, round 20: signed-in reader

Rows as above: state | what | where | test. What Threads shows a signed-in reader of their own account and the plugin lacked: Threads' Following feed, Activity, the posts saved and liked on Threads, and the reader's own profile. Every read is a GET with the reader's session (the cookies on threads.com's `/api/v1`, else the Bearer on the app API), paced at the session's gap and parked on a throttle as every session read is; nothing is followed, saved, liked, answered or marked seen, and no token or cookie is logged. Paths no probe has seen answer are tried in turn (a 404 moves on, the one that answered is asked first afterwards), and each list stays off (`ThreadsSignedInRules.*_PATH_KNOWN = false`) until CI's new probe `ThreadsSignedInLiveTest` (RUN_LIVE, lines `LIVE Threads signed in …`) shows its path real: a guest sent to sign in (302) beside the controls `text_feed/{media}/replies/` and `accounts/current_user/`, where a wrong path is a 404. With every switch off, Home's menu offers a signed-in reader only their profile.

native only (gated) | Threads' Following feed (the accounts the reader follows on Threads, newest first), beside Home's accounts followed here: Home's menu "Following on Threads", a screen of feed cards page after page, pull to read again, Retry with Meta's words, empty and end. Asked as the Bearer's For you is, at `feed/text_post_app_timeline/` with `feed_type=following` (`feed_view_info` empty, so nothing is reported seen), then `text_feed/following_timeline/`. A guest cannot tell whether Meta takes `feed_type=following` there (it is sent to sign in whatever is asked), so an answer whose authors Meta marks with the reader's `friendship_status` as mostly not followed (For you, had the type been ignored) is passed over as a path that does not serve it. Off until a session confirms it (`FOLLOWING_PATH_KNOWN`) | networks/threads ThreadsOwnFeeds.kt (new: ThreadsOwnFeed, paths, params, isFollowing), ThreadsDirectClient.ownFeed (sessionApi takes `accepts`); plugins/threads ThreadsSignedIn.kt (new: ThreadsSignedInRules), ThreadsSignedInScreens.kt (new: ThreadsOwnFeedScreen), ThreadsRoute.OwnFeed, ThreadsHome.SignedInRows; strings following_on_threads, following_on_threads_empty in 29 languages | ThreadsSignedInTest.theFollowingFeed…, aTimelineWhoseAuthors…; ThreadsSignedInViewTest
native only (gated) | Activity: the reader's replies, mentions, quotes, reposts, likes on their posts and new followers, Meta's own line for each with who did it (their face; a tap opens the post it is about by its short link, else their profile), when, and the kind's mark; All, Replies and Mentions as Threads' Activity filters them (`selected_filters` `text_post_app_replies`, `text_post_app_mentions`); page after page with `next_max_id`. Read from `text_feed/text_app_notifications/`, then Instagram's `news/inbox/`, always with `mark_as_seen=false`. Stories are read as Instagram's inbox writes them (`new_stories` then `old_stories`, `args.text` or its rich text, `profile_name`, `profile_image` over https, `timestamp` in seconds or milliseconds, `media[0]` by code or id, `destination`'s `media_id`), the kind from Meta's story name (a like of a reply is a like); a line without words is left out. Off until a path is seen real (`ACTIVITY_PATH_KNOWN`) | networks/threads ThreadsActivity.kt (new), ThreadsPeople.shortcodeOf, ThreadsDirectClient.activity; plugins/threads ThreadsSignedInScreens (ThreadsActivityScreen), ThreadsRoute.Activity; strings activity, activity_empty, mentions | ThreadsSignedInTest.activityIsAskedNeverToMarkAnythingSeen…, aPageOfActivity…, storiesAreKnown…, activityFallsBack…; ThreadsSignedInViewTest.anActivityLineOpens…
native only (gated) | the posts the reader saved on Threads and those they liked there, each a list of feed cards as the Following feed is (a bookmark or heart here stays on the device). Tried at `text_feed/saved/`, `text_feed/saved_posts/`, `feed/text_post_app_saved/`, and `text_feed/liked/`, `text_feed/liked_posts/`, `feed/text_post_app_liked/`; read from a timeline or a plain `items`/`medias`/`posts` list of posts, bare or under `post` or `media` (quotes are now read the same way, ThreadsListedPosts). Instagram's own `feed/saved/posts/` and `feed/liked/` are left out: they would list Instagram's posts. Off until a path is seen real (`SAVED_PATH_KNOWN`, `LIKED_PATH_KNOWN`) | ThreadsOwnFeeds (SAVED, LIKED), ThreadsListedPosts, ThreadsQuotes.page; strings saved_on_threads, saved_on_threads_empty, liked_on_threads, liked_on_threads_empty | ThreadsSignedInTest.aPageOfOwnPosts…, savedPostsWithABearer…
native only | "Your profile" in Home's menu for a reader signed in with cookies: their own profile, with whom they follow and who follows them (where an account they follow on Threads can be followed on this device), by the handle the sign-in already read, else one `accounts/current_user/` read kept for the run; a failure says why in the notices. A Bearer alone has no read naming its account, so it is not offered | ThreadsApp.ownHandle, ThreadsHome.SignedInRows; string your_profile | ThreadsSignedInViewTest.nothingIsOfferedWithoutASession…
native only | a signed-in list no path answers says "Threads does not give this here yet." with Retry; one opened without a session shows where to sign in | ThreadsFailures.ownList; string signed_in_unavailable | ThreadsSignedInViewTest.aListNoPathAnswers…
left out | custom feeds, joined communities and suggested accounts: no REST route known, and their GraphQL queries carry no doc id a page names (round 18); Activity's other filters (Follows, Quotes, Reposts, Verified: no filter name seen), a story's seen state, and everything that writes: following back, answering, marking seen, saving or liking on Threads | — | —
left out | whether any of these paths is real is CI's to tell: ThreadsSignedInLiveTest asks each candidate and threads.com's pages `/activity`, `/saved`, `/liked` and `/following` as a guest and logs the status and where a redirect leads (host and path only). A path that answers 302 beside the controls switches its list on; the Following feed's `feed_type` needs a session's word | ThreadsSignedInLiveTest | —

## Threads, round 21: doc ids

Rows as above: state | what | where | test. The wall every round since 17 met: threads.com serves deeper replies, About this profile, translation, reposters, quotes and its feeds through Relay queries whose names CI saw on guest pages, but a page names a doc id only for the queries it preloads, and every REST guess was a 404. The ids are in threads.com's JavaScript bundles. This round reads them there, and prepares the three guest reads worth most so that an id alone switches each on. Only queries: a mutation is never read out of a bundle, and nothing here writes to Threads.

native only | the doc ids a bundle names: a module of the id alone (`__d("Barcelona…Query_threadsRelayOperation",[],(function(…){e.exports="…"}),null)`, whatever follows the name: `_instagramRelayOperation`, `$Parameters`) and a request written out (`params:{id:"…",metadata:{},name:"Barcelona…Query",operationKind:"query"}`, as code or JSON); the variables, provided variables and linked fields a query's definition declares, by name; the bundles a page names (its scripts, its preloads and its `rsrcMap`, on Meta's static hosts only, those its bootloader lists for a wanted component first). A name that is not `Barcelona…Query`, or a module that says `operationKind:"mutation"`, is never returned | ThreadsDocIds | ThreadsDocIdsTest (made-up snippets of each shape)
native only (off until an id is known) | the replies under a reply read in place: "Continue this thread" asks `BarcelonaInlineRepliesExpansionQuery` for the thread's last post (its media id under the variable `postID`) as its own page asks it, puts what is new on the end of the thread and opens it; a refusal, an answer in another shape or nothing new opens the post's own conversation as before. Read as a post page's next replies are, else as every post the answer holds but the reply | ThreadsDirectClient.deeperReplies/readsDeeperReplies, ThreadsPostPageReader.deeper, ThreadsConversation.withDeeper/brings, ThreadsReplyThread.then; ThreadsConversationScreen (onContinue) | ThreadsBundleReadsTest
native only (off until an id is known) | when an account joined, as Threads' "About this profile" says it: a line "Joined July 2023" at the top of a profile's Filters sheet, asked when the sheet opens (`BarcelonaAboutThisProfileDialogQuery`, the account's id under `userID`); the month and year in the reader's language where Meta gives a moment, else Meta's own words; no line when Meta says nothing. The date is read under every name it might go by anywhere under `data` | ThreadsDirectClient.aboutProfile/readsAboutProfile, ThreadsBundleReads.about, ThreadsProfileHeader.joined; ThreadsProfileChrome (ProfileJoined); string profile_joined | ThreadsBundleReadsTest, ThreadsProfileHeaderTest.aJoinDate…
native only (off until an id is known) | trending topics when the search page carries none: `BarcelonaSearchTrendingTopicsSectionQuery` asked with the token of the page just read (no second page is read for one), read as the page's `data.trends` are and remembered as they are | ThreadsDirectClient.trends (askedTrends) | ThreadsBundleReadsTest
native only | the switches: `ThreadsRepairs` slots `threads.inline_replies_doc_id`, `threads.about_profile_doc_id` and `threads.trending_topics_doc_id` ship empty (`ThreadsRepairs.WAITING`), and a read whose id is empty asks Meta nothing, so every screen costs the requests it did (ThreadsRequestCountTest unchanged). An id of digits switches its read on: in the slot's shipped value with the same entry in `repairs.json` (a release), or in `repairs.json` alone, which installed plugins read daily. The variable each query takes its id under is a slot too (`threads.inline_replies_variable` `postID`, `threads.about_profile_variable` `userID`, both guesses, published as shipped): the probe logs the names the bundles declare | ThreadsSession.kt (ThreadsRepairs); repairs.json | ThreadsRepairsTest
left out | translation, reposters, quotes, the Following feed, custom feeds and communities by their queries: session reads or ones whose variables and answers are unknown; the probe watches their names (`BarcelonaPostTranslationQuery`, `BarcelonaProfileUserInterestsDialogUserQuery`, `BarcelonaCustomFeedPickerPageQuery`, `BarcelonaGetCommunityCustomFeedQuery`, `BarcelonaLoggedOutFeedPaginationQuery`, `BarcelonaPostPageDirectRepliesRefetchQuery`, `BarcelonaSearchResultsRefetchableQuery`, `BarcelonaProfileThreadsTabRefetchableDirectQuery`, `BarcelonaProfileRepostsTabRefetchableDirectQuery`) so the next round has their ids | — | —
left out | the ids themselves are CI's to tell: ThreadsDocIdsLiveTest (RUN_LIVE) reads a profile, a post and the search page as a guest, at most 40 of the bundles they name (6 MB of each, two seconds apart), and logs `LIVE Threads doc ids <QueryName>=<id>` for every query found (preloaded ones included), `LIVE Threads doc ids declared <QueryName>: variables …; provided …; fields …` for the watched ones, `LIVE Threads doc ids unread <QueryName>: …` (how its module starts) for one a bundle names in a shape not read, and a summary (bundles named and read, bytes, names found, each watched query found, named without an id, or missing). No cookie, token or post text is logged | ThreadsDocIdsLiveTest | —
