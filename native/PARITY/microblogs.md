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
untested | bluesky_import_test › a handle it cannot read is said so, not ignored | plugins/bluesky BlueskyImport.kt (ImportRun.start false → the field shows invalid_handle) | BlueskyImportTest.aHandleItCannotReadIsRefusedAndAsksNothing
untested | bluesky_import_test › correcting the field clears the complaint | BlueskyImport.kt (onValueChange clears `refused`) | UI only, read in code
## test/bluesky_interleaved_test.dart
untested | bluesky_interleaved_test › blueskyInterleavedItems keeps dated posts up to the limit | plugins/bluesky BlueskyApp.kt (HomeFeed.posts, SHARED = 10) | BlueskyImportTest.homeTakesTenDatedPostsAndLeavesRepliesOutWhereTheyAreOff
untested | bluesky_interleaved_test › blueskyInterleavedItems drops posts without a date | HomeFeed.posts | same test
untested | bluesky_interleaved_test › a group with replies off leaves Bluesky replies out, and keeps them when on | HomeFeed.posts (FeedKinds) | same test
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
untested | bluesky_profile_chrome_test › profile identity matches the X header: name, handle, joined, counts | plugins/bluesky BlueskyProfileScreen.kt (Header, About: name, @handle, description, Joined, following/followers) | UI only, read in code; the parsed fields in BlueskyParserTest.aProfileReadsItsCountsBannerJoinDateAndPin. Native's profile screen carries Follow in the header as XTA's screen does
## test/bluesky_profile_saved_test.dart
covered | bluesky_profile_saved_test › Saved on a profile is only that author's local likes | networks/bluesky BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTabListsOnlyItsAuthorsLikedPosts
covered | bluesky_profile_saved_test › a DID match still counts after a handle rename | BlueskyLocal.likedBy (DID first) | BlueskyLocalTest.aProfilesLikesTab… (by DID with no handle)
covered | bluesky_profile_saved_test › handle match is case-insensitive | BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTab… ("MAYA.bsky.social")
covered | bluesky_profile_saved_test › blank actor does not dump every local like onto the tab | BlueskyLocal.likedBy | BlueskyLocalTest.aProfilesLikesTab… (blank → empty)

## test/bluesky_profile_upgrade_test.dart
untested | bluesky_profile_upgrade_test › metadata failure does not block refresh of an already loaded feed | plugins/bluesky BlueskyProfileScreen.kt (ProfileState, tabs on their own PagedItems) | BlueskyProfileStateTest.aFailedProfileReadKeepsWhatWasShownAndItsPostsReadAgain
untested | bluesky_profile_upgrade_test › failed refresh retries page one without mistaking old cursor for pagination | ui/plugins PagedItems.refresh | BlueskyProfileStateTest.aFailedProfileRead… (page one asked without a cursor), BlueskySourcesTest.aFailedRefreshKeepsThePostsShown…
untested | bluesky_profile_upgrade_test › failed next page retries its cursor and preserves earlier cards | PagedItems.more/retryMore | BlueskySourcesTest.aFailedPageKeepsItsCursorForATryAgain
untested | bluesky_profile_upgrade_test › cursor cycles terminate while new posts from the final page survive | PagedItems.more | BlueskySourcesTest.pagingAsksOnceAtATime…StopsAtARepeatedCursor ("third" kept)
untested | bluesky_profile_upgrade_test › refresh invalidates an older pagination result | PagedItems.refresh (cancels the page under way) | BlueskyProfileStateTest.aRefreshDropsAPageStillComingOfTheListBefore
missing/different | bluesky_profile_upgrade_test › a handle resolving to a new DID clears old identity feeds and pending pages | ProfileState.load | the tabs kept reading the old DID; now they are dropped when the handle names another account. BlueskyProfileStateTest.aHandleNowNamingAnotherAccountDropsTheOldAccountsPages
missing/different | bluesky_profile_upgrade_test › switching AppView rejects late results and reloads from the selected source | BlueskyProfileScreen (state kept per AppView and actor) | the profile stayed on the AppView it was opened with; now another AppView reads it anew and the old one's late answers land in a state no longer shown. UI keying only, read in code
covered | bluesky_profile_upgrade_test › pin is first and appears once only in Posts | networks/bluesky BlueskyProfileTab.withPin | BlueskyTimelineTest.profileTabsShowTheirOwnPostsAndPostsStartsWithThePinOnce
untested | bluesky_profile_upgrade_test › pin failure retains the feed and has an independent retry | ProfileState.readPin/retryPin | BlueskyProfileStateTest.aPinThatFailsKeepsTheFeedAndIsTriedAgainOnItsOwnAndARemovedPinGoes
untested | bluesky_profile_upgrade_test › a removed pin and disposed late pin do not remain visible | ProfileState.load (pin kept only while the profile still pins it; scope ends with the screen) | same test, last part
covered | bluesky_profile_upgrade_test › bio links preserve UTF-8 offsets and balanced URL punctuation | networks/bluesky BlueskyFacets.profileLinks | BlueskyFacetsTest.bioLinksKeepUtf8OffsetsAndBalancedPunctuation
untested | bluesky_profile_upgrade_test › profile metadata fits narrow large text and exposes posts count | BlueskyProfileScreen.kt (ProfileDetails: post_count plural, Count ≥ 48 dp) | UI only, read in code (the bio is LinkedText, not selectable)
untested | bluesky_profile_upgrade_test › Posts identifies and renders the pinned card once | BlueskyProfileScreen.postRows, NetworkPost.pinned | UI; the once-only rule is BlueskyTimelineTest.profileTabsShow…PinOnce

## test/bluesky_protocol_regression_test.dart
covered | bluesky_protocol_regression_test › leading whitespace and Unicode retain the exact linked facet span | networks/bluesky BlueskyFacets.kt | BlueskyFacetsTest.leadingWhitespaceAndUnicodeKeepTheExactMentionSpan
covered | bluesky_protocol_regression_test › repost activity date remains separate from publication through offline snapshots | BlueskySnapshot.kt, BlueskyParser.kt | BlueskyParserTest.aRepostKeepsWhoRepostedItAndWhen, BlueskySnapshotTest.whatThisAppWritesReadsBackTheSame
untested | bluesky_protocol_regression_test › malformed optional cached fields cannot discard other saved posts | BlueskySnapshot.post/posts | BlueskySnapshotTest.damagedOptionalFieldsOfOnePostLoseNeitherItNorTheOthers
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
