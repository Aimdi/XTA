# Round 3 parity ledger: microblogs (Bluesky, Mastodon, Threads and the shared microblog UI)

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
untested | bluesky_conversation_controls_test › refresh errors retain controls and branches; stale collapse ids are pruned | plugins/bluesky BlueskyThread.kt (ThreadState) | ThreadState now takes its read and AppView as functions; BlueskyThreadStateTest.aFailedRefreshKeepsTheThread…
missing/different | bluesky_conversation_controls_test › newest refresh wins and disposed stores never start new requests | BlueskyThread.kt (ThreadState) | an older read answering late overwrote the newer; now only the newest lands, nothing is read once the scope ended. BlueskyThreadStateTest.theNewestRefreshWins…
missing/different | bluesky_conversation_controls_test › thread discards late AppView error/content and reloads from current server (both variants) | BlueskyThread.kt (ThreadState) | an answer from an AppView left meanwhile is dropped and the thread read again. BlueskyThreadStateTest.aLateAnswerFromTheAppViewThatWasLeft…
missing/different | bluesky_conversation_controls_test › generic warnings cover text and reset when revision changes | ui/plugins NetworkPosts.kt (NetworkPost.revision), Posts.kt, BlueskyPostRow.kt | the reveal was kept per post id; now per revision (text, media, warning, quote). BlueskyThreadStateTest.aWarningCoversTheText…
untested | bluesky_conversation_controls_test › unavailable text and quotes never offer reveal; media labels retain text | BlueskyPostRow.kt (toNetworkPost) | BlueskyThreadStateTest.anUnavailablePostOrQuoteOffersNoReveal…
untested | bluesky_conversation_controls_test › footer actions remain named when counts are hidden | BlueskyPostRow.kt (footerCounts, new) | footer labels moved into a testable function. BlueskyThreadStateTest.theFooterNamesEachAction…
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
untested | bluesky_feed_browser_test › switch selects immediately, late response cannot overwrite it | plugins/bluesky BlueskySources.kt (SourcePages) | BlueskySourcesTest.eachFeedKeepsItsOwnPagesSoALateOrFailedReadNeverShowsInAnother
untested | bluesky_feed_browser_test › new failed selection never borrows the old feed posts | BlueskySources.kt (SourcePages) | same test (C fails with no posts, A keeps its own)
untested | bluesky_feed_browser_test › refresh failure keeps cached posts and can be retried | ui/plugins PagedItems | BlueskySourcesTest.aFailedRefreshKeepsThePostsShownAndATryAgainClearsIt
untested | bluesky_feed_browser_test › pagination coalesces calls, preserves source order, stops a repeated cursor | PagedItems | BlueskySourcesTest.pagingAsksOnceAtATime…
untested | bluesky_feed_browser_test › pagination failure preserves cursor and permits explicit retry | PagedItems | BlueskySourcesTest.aFailedPageKeepsItsCursorForATryAgain
untested | bluesky_feed_browser_test › switch during pagination cannot append another source | SourcePages | each source pages on its own PagedItems, so a late page lands only in its own; BlueskySourcesTest.eachFeedKeeps…
untested | bluesky_feed_browser_test › changing AppView invalidates in-flight response and loads the new server | SourcePages (keyed by AppView) | BlueskySourcesTest.eachFeedKeeps… (another AppView reads anew)
untested | bluesky_feed_browser_test › destroying while loading ignores the response | PagedItems (scope ends with the screen) | BlueskySourcesTest.anEmptyFeedKeepsItsCursorAndAnEndedScopeTakesNoLateAnswer
covered | bluesky_feed_browser_test › remembered selection is local and scoped to AppView | BlueskyStores.kt (selection/select), BlueskySnapshot | BlueskyReadingTest.theFeedOpenIsRememberedPerAppView
untested | bluesky_feed_browser_test › catalog failure cannot block Discover and remains retryable | BlueskySources.kt (BlueskyFeeds) | new FakeHost test harness; BlueskySourcesTest.aFailedCatalogNeverKeepsDiscoverFromReading…
untested | bluesky_feed_browser_test › new store restores its saved custom feed without waiting for the catalog | BlueskyFeeds.ensureLoaded | BlueskySourcesTest.theFeedLastOpenedIsOpenAgainWithoutWaitingForTheCatalog
untested | bluesky_feed_browser_test › catalog search ignores late queries and retains new pins | BlueskyFeeds.searchCatalog | BlueskySourcesTest.aLateCatalogSearchIsDroppedAndAPinStays
untested | bluesky_feed_browser_test › catalog cursor cycles terminate and duplicates are removed | BlueskySources.kt (Listing) | BlueskySourcesTest.aCatalogCursorThatComesRoundAgain…
missing/different | bluesky_feed_browser_test › catalog paging restarts after changing AppView without reusing the old cursor | BlueskyFeeds.searchCatalog | the next page used the old AppView's cursor; now it reads the first page again. BlueskySourcesTest.afterAnotherAppView…
missing/different | bluesky_feed_browser_test › list lookup without an open source refreshes on AppView change | BlueskyLists.ensureLoaded | on another AppView the looked-up account was dropped for the handle; now it is listed again (and the old selection goes, as XTA). BlueskySourcesTest.theAccountLookedUpIsListedAgain…
missing/different | bluesky_feed_browser_test › list catalog paging restarts after changing AppView | BlueskyLists.lookUp | as the catalog. BlueskySourcesTest.afterAnotherAppView…
untested | bluesky_feed_browser_test › list lookup ignores stale actors and pages their lists | BlueskyLists.lookUp | BlueskySourcesTest.aStaleLookUpIsDropped…
untested | bluesky_feed_browser_test › concurrent pins preserve existing preferences and persist the final order | BlueskyStores.kt (pinnedFeeds) | BlueskySourcesTest.pinsMadeTogether…
untested | bluesky_feed_browser_test › empty feed retains selection, pin, browse and explicit paging | BlueskySourcePanes.kt (FeedsPane: Open a feed, Pin, Find feeds; feed_empty) | UI only; native reads the next page as the list end shows instead of a Load more button; the kept cursor is tested in BlueskySourcesTest.anEmptyFeedKeepsItsCursor…
untested | bluesky_feed_browser_test › catalog search shows metadata and an accessible pin action | BlueskyFeedCatalog.kt (FeedRow: name, @creator, description, Pin/Unpin button) | UI only, read in code; pin/unpin state in BlueskySourcesTest

## test/bluesky_feed_store_test.dart
covered | bluesky_feed_store_test › a second page of the same uris is not a replacement | networks/bluesky BlueskyTimeline.kt (stabilize), FollowingTimeline (StateFlow keeps an equal list) | BlueskyTimelineTest.mergedPagesKeepEachPostAndEachPersonOnce; BlueskyFollowingTimelineTest.followingAnotherAccount… (unchanged read leaves the list)
covered | bluesky_feed_store_test › a cache-walk prefix does not shrink a painted feed | BlueskyFollowing.kt | native never emits a partial walk: accounts not asked or failed keep their last page. BlueskyFollowingTest.aFailedAccountKeepsItsLastPageAndIsAskedAgain, aBoundedReadProgressesPastTheFirstAccounts
covered | bluesky_feed_store_test › new uris at the top may appear | BlueskyFollowing.kt | BlueskyFollowingTest.eachAccountsPageIsReadAndMergedNewestFirst
covered | bluesky_feed_store_test › duplicate uris from two accounts collapse to one row | BlueskyTimeline.stabilize | BlueskyFollowingTest.theSamePostFromTwoAccountsIsOneRow, BlueskyTimelineTest.theOriginalWinsADuplicateRepostInEitherOrder
missing/different | bluesky_feed_store_test › an empty poll does not blank a painted feed | plugins/bluesky BlueskyFollowingPane.kt (FollowingTimeline.refresh) | a read that brought nothing but a failure (e.g. after an AppView change) blanked the list; now it stays with the problem shown. BlueskyFollowingTimelineTest.aReadThatOnlyFailsLeavesThePostsShown
untested | bluesky_feed_store_test › an empty successful response is not fetched again on entry | FollowingTimeline + BlueskyFollowing (ten-minute reuse) | BlueskyFollowingTimelineTest.anEmptyAnswerIsNotAskedAgainOnEntryButAPullAsks
covered | bluesky_feed_store_test › re-entry keeps a painted feed while explicit refresh reads changes | BlueskyFollowing.kt | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced; BlueskyFollowingTimelineTest.followingAnotherAccount…
untested | bluesky_feed_store_test › following another account invalidates the entry identity | FollowingTimeline.start (re-reads when the follows change) | BlueskyFollowingTimelineTest.followingAnotherAccountReadsItAndTheSamePostIsOneRow
covered | bluesky_feed_store_test › a second remount poll does not replace an unchanged first page | BlueskyFollowing.kt (ten-minute reuse) | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced
covered | bluesky_feed_store_test › an empty feed still fetches — remount skip only applies when posts are already there | BlueskyFollowing.kt | BlueskyFollowingTest.eachAccountsPageIsReadAndMergedNewestFirst (first read asks every account)
covered | bluesky_feed_store_test › the first-page cursor is not reset on a later poll | BlueskyFollowing.kt (no cursor is sent; pages reused) | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced
covered | bluesky_feed_store_test › pull-to-refresh asks again even inside the TTL | BlueskyFollowing.kt | BlueskyFollowingTest.aReadWithinTenMinutesReusesThePagesUnlessForced ("pull-to-refresh asks again")
untested | bluesky_feed_store_test › force refresh with the same uris does not notify | FollowingTimeline (StateFlow drops an equal state) | BlueskyFollowingTimelineTest.followingAnotherAccount… checks the list is unchanged; native still shows the loading state meanwhile
covered | bluesky_feed_store_test › the same uri from two followed accounts is one row | BlueskyTimeline.stabilize | BlueskyFollowingTest.theSamePostFromTwoAccountsIsOneRow

## test/bluesky_feeds_test.dart
covered | bluesky_feeds_test › reads bsky.app feed URLs and AT-URIs | networks/bluesky BlueskyUrls.kt (feedRef) | BlueskyUrlsTest.listsFeedsAndStarterPacksAreNamedByLinkOrUri
covered | bluesky_feeds_test › reads generatorView rows and a singular view | BlueskyParser.kt (feedGenerators) | BlueskyParserTest.feedGeneratorsFallBackToTheirRecordKeyAndNeedAUri
covered | bluesky_feeds_test › tolerates a reshaped payload | BlueskyParser.kt | BlueskyParserTest.feedGeneratorsFallBack… ({"feeds":"nope"} is empty), aReshapedFeedIsEmptyNotACrash
covered | bluesky_feeds_test › getListFeed uses the same feed item shape as getAuthorFeed | BlueskyClient.listFeed | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints
covered | bluesky_feeds_test › listView keeps description and creator | BlueskyParser.listMembers | BlueskyParserTest.aListComesWithItsMembersAndPeopleWithoutANameAreDropped
untested | bluesky_feeds_test › successful empty discovery result survives tab re-entry | plugins/bluesky BlueskySources.kt (SourcePages, PagedItems) | BlueskySourcesTest.anEmptyFeedKeepsItsCursor…, enteringAgainReadsNothingAgain…
untested | bluesky_feeds_test › opening the same cached feed does not refetch | BlueskyFeeds.ensureLoaded, SourcePages | BlueskySourcesTest.enteringAgainReadsNothingAgainAndAPinIsKeptUntilUnpinned
untested | bluesky_feeds_test › pin persists and unpin removes the URI | BlueskyFeeds.pin/unpin, BlueskyPreferences.pinnedFeeds | BlueskySourcesTest.enteringAgainReadsNothingAgainAndAPinIsKeptUntilUnpinned
untested | bluesky_feeds_test › selecting the current empty list does not reload it | BlueskyLists (SourcePages per list) | the same SourcePages as feeds: BlueskySourcesTest.eachFeedKeepsItsOwnPages… (same PagedItems back)
untested | bluesky_feeds_test › opening the same cached list does not refetch | BlueskyLists (SourcePages) | as above
covered | bluesky_feeds_test › getFeed and getListFeed hit the AppView paths | BlueskyClient.kt | BlueskyClientTest.feedsListsAndTheGraphAreReadAtTheirOwnEndpoints

## test/bluesky_following_recovery_test.dart
covered | bluesky_following_recovery_test › new repost activity sorts ahead of newly authored posts | networks/bluesky BlueskyTimeline.kt | BlueskyTimelineTest.aRepostsNewActivitySortsAheadOfNewerOriginals
covered | bluesky_following_recovery_test › followed original wins a duplicate repost regardless of merge order | BlueskyTimeline.kt | BlueskyTimelineTest.theOriginalWinsADuplicateRepostInEitherOrder
covered | bluesky_following_recovery_test › newest repost wins among multiple reposters while source rank stays stable | BlueskyTimeline.kt | BlueskyTimelineTest.theNewestRepostWinsWhileTheFirstSeenOrderStays
untested | bluesky_following_recovery_test › count and moderation updates repaint even when identities match | BlueskyPost (data class equality) | BlueskyFollowingTimelineTest.aNewCountOrLabelOfTheSamePostIsAChange
missing/different | bluesky_following_recovery_test › failed first load retries on re-entry | plugins/bluesky BlueskyFollowingPane.kt (FollowingTimeline.start) | showing the tab again after a failed read did nothing; now it reads again. BlueskyFollowingTimelineTest.aFailedFirstReadIsTriedAgainWhenTheTabIsShownAgain
covered | bluesky_following_recovery_test › partial failure preserves failed author posts and retries them | BlueskyFollowing.kt | BlueskyFollowingTest.aFailedAccountKeepsItsLastPageAndIsAskedAgain
untested | bluesky_following_recovery_test › unfollowing everyone clears the authoritative completed feed | FollowingTimeline.refresh | BlueskyFollowingTimelineTest.unfollowingEveryoneEmptiesTheTimelineAndAGroupsReadLeavesIt
covered | bluesky_following_recovery_test › forced bounded refresh progresses past first imported accounts | BlueskyFollowing.kt | BlueskyFollowingTest.aBoundedReadProgressesPastTheFirstAccounts
covered | bluesky_following_recovery_test › failed import batch cannot starve unread accounts | BlueskyFollowing.kt | BlueskyFollowingTest.aFailedBatchCannotStarveTheAccountsNeverAsked
untested | bluesky_following_recovery_test › group subset read cannot replace Following or freshness | BlueskyFollowing.posts vs FollowingTimeline | BlueskyFollowingTimelineTest.unfollowingEveryone…AndAGroupsReadLeavesIt
untested | bluesky_following_recovery_test › older forced refresh cannot overwrite a newer result | FollowingTimeline (cancels the older read), BlueskyFollowing (per-account request count) | BlueskyFollowingTimelineTest.anOlderReadNeverOverwritesANewerOne
missing/different | bluesky_following_recovery_test › response from an old AppView cannot appear in the new source | networks/bluesky BlueskyFollowing.kt (era) | an account's page asked of the old AppView could still be kept after the change (its request number restarted); now each AppView change starts a new era and older answers are not kept. BlueskyFollowingTimelineTest.anAnswerFromTheAppViewLeftIsNeverKept
untested | bluesky_following_recovery_test › follow-set change discards the old pending request | FollowingTimeline.refresh (cancels) | BlueskyFollowingTimelineTest.aChangeOfWhoIsFollowedDropsTheReadUnderWay…
untested | bluesky_following_recovery_test › disposal ignores late reads and leaves no loading work | BlueskyApp.close (ends its scope) | same test, second half
