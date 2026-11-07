# Parity ledger: substack-feeds

Every test in XTA's batch, as `status | test file › test name | native place (file) | note`.
Statuses: covered (native does it, a native test checks it), untested (ported a test), missing/different (implemented in native, test ported), n/a (Flutter-only).

## test/article_reading_test.dart
untested | article_reading_test › Substack deep links and fetched article IDs share one reading position | plugins/substack/.../SubstackOffline.kt (ReaderIds) | SubstackArticleReadingTest.aDeepLinkAndTheFetchedArticleShareOneReadingPlace
covered | article_reading_test › opening is recorded separately from finishing; article and appearance survive restart | store/.../reading/ArticleReading.kt | ArticleReadingTest.openingIsNotFinishingAndThePlaceAndTypeSurviveARestart
covered | article_reading_test › only an active reader scrolling to the end after dwell time completes automatically | store/.../reading/ArticleReading.kt | ArticleReadingTest.onlyAnActiveReaderScrollingToTheEndAfterReadingAWhileFinishes
covered | article_reading_test › previews require explicit completion and failed completion can be retried | store/.../reading/ArticleReading.kt | ArticleReadingTest.aPreviewFinishesOnlyWhenAskedAndAFailedFinishCanBeTriedAgain
covered | article_reading_test › nested readers merge their journal writes and the newest 300 entries are retained | store/.../reading/ReadingPlaces.kt | ArticleReadingTest.readersOpenTogetherBothKeepTheirPlaceAndOnlyTheNewest300Stay
covered | article_reading_test › malformed preferences and bridge messages are bounded and safe | store/.../reading/ArticleReading.kt | ArticleReadingTest.damagedFilesAndReportsAreBoundedAndSafe
covered | article_reading_test › reading-position opt-out keeps shared typography but does not restore or persist position | store/.../reading/ArticleReading.kt | ArticleReadingTest.withPlacesNotKeptTheTypeIsSharedButNoPlaceIsReadOrWritten
covered | article_reading_test › a journal of its own, joined only once the reader moves, and emptied on its own | store/.../reading/ArticleReading.kt | ArticleReadingTest.aJournalOfItsOwnIsJoinedOnlyOnceTheReaderMovesAndEmptiedOnItsOwn
covered | article_reading_test › disposed sessions ignore delayed progress callbacks | store/.../reading/ArticleReading.kt | ArticleReadingTest.aClosedReaderIgnoresLateReports
untested | article_reading_test › both HTML sanitizers remove execution paths before the trusted progress bridge is added | networks/.../RssHtml.kt, plugins/substack/.../SubstackHtml.kt | RSS: RssHtmlTest; Substack: SubstackArticleReadingTest.theSanitizerRemovesEveryWayToRunCodeBeforeTheBridgeIsAdded
untested | article_reading_test › bridge scales typography with accessibility and restores paragraph before proportional fallback | plugins/substack/.../SubstackWebPage.kt, SubstackWebReader.kt (scales by the text scale) | SubstackArticleReadingTest.theBridgeRestoresTheParagraphBeforeTheProportionalPlace

## test/home_dock_lifetime_test.dart
n/a | home_dock_lifetime_test › hosted Substack controls and content fit 390 1 RTL=false | plugins/substack/.../SubstackReadingToolbar.kt | pixel offsets and overflow exceptions of Flutter's dock layout; native has the same search and filters buttons on the feed's toolbar (and its filters sheet's text field)
n/a | home_dock_lifetime_test › hosted Substack controls and content fit 320 2 RTL=true | plugins/substack/.../SubstackReadingToolbar.kt | as above, at text scale 2 and RTL: Flutter layout metrics
covered | home_dock_lifetime_test › compact Substack gives the second toolbar space to articles and preserves archives | plugins/substack/.../SubstackReadingToolbar.kt (PublicationPicker → openPublication) | native has no compact dock row; the Following picker opens the publication's archive (route checked by SubstackHomeTest.routesNameEachScreenAndReadBackWhatTheyNamed)
missing/different | home_dock_lifetime_test › Substack reading controls survive scrolling past their lazy owner | plugins/substack/.../SubstackFeedPanes.kt (pinnedToolbar) | native's toolbar scrolled away with the first row; now a sticky header so search and filters stay in reach. No Compose UI test harness in the repo, so no unit test for a layout pin
covered | home_dock_lifetime_test › hosted Substack publications retain archive navigation | plugins/substack/.../SubstackReadingToolbar.kt (PublicationPicker) | as the compact case: Following → a publication opens SubstackPublicationScreen
n/a | home_dock_lifetime_test › a menu opened for a departed source cannot execute its old action | – | Flutter's shared PluginHomeDockStore; native plugins draw their menus inside their own composition, which leaves with the plugin

## test/podcast_continuity_test.dart
untested | podcast_continuity_test › cold restore shows paused position without creating a player | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.aColdRestoreShowsThePausedPlaceWithoutMakingAPlayer
missing/different | podcast_continuity_test › background and closing persist the current episode paused on restart | plugins/substack/.../SubstackPodcast.kt | native has no app-lifecycle hook for plugins: the place is now written every 2 s while playing (was 10 s) and on close; SubstackPodcastTest.closingKeepsTheEpisodeToComeBackPaused
missing/different | podcast_continuity_test › continuous playback writes periodically instead of starving a debounce | plugins/substack/.../SubstackPodcast.kt (checkpoint) | was a 10 s leading throttle; now XTA's 2 s timer started by the first change. SubstackPodcastTest.continuousPlaybackIsWrittenEveryFewSeconds
untested | podcast_continuity_test › completion clears progress and does not resurrect on restart | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.aFinishedEpisodeClearsItsPlaceAndDoesNotComeBack
missing/different | podcast_continuity_test › a changed episode receives no previous episode seek or playing event | plugins/substack/.../SubstackPodcast.kt (wanted) | native sought and played the first episode once its open answered; now an open replaced meanwhile neither seeks nor plays. (The row still names the new episode only once the old open answers.) SubstackPodcastTest.anotherEpisodeAskedForMeanwhileGetsNoSeekOrPlayOfTheFirst
missing/different | podcast_continuity_test › stop during open cancels resume and clears queued checkpoints | plugins/substack/.../SubstackPodcast.kt (stop) | native played after a stop queued behind the open; now stop clears the row at once and the open does not play. SubstackPodcastTest.aStopDuringTheOpenCancelsTheResumeAndClearsTheCheckpoint
untested | podcast_continuity_test › a late old write cannot replace the final stop checkpoint | plugins/substack/.../SubstackPodcast.kt | pending write cancelled by stop, settings writes are serialized. SubstackPodcastTest.aLateWriteOfThePlaceCannotReplaceTheStop
untested | podcast_continuity_test › failed loading preserves checkpoint and allows an explicit retry | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.aFailedLoadKeepsTheCheckpointAndCanBeTriedAgain
missing/different | podcast_continuity_test › restored slider can be moved without initializing playback | plugins/substack/.../SubstackPodcast.kt (seek) | native ignored a seek before the episode was opened; now it moves the restored place and the open starts there. SubstackPodcastTest.aRestoredSliderMovesWithoutStartingThePlayer
untested | podcast_continuity_test › unrestorable podcast stays paused and keeps the checkpoint for retry | plugins/substack/.../SubstackPodcast.kt, ui/.../media/Playback.kt | SubstackPodcastTest.anEpisodeThatCannotBePutBackStaysPausedWithItsCheckpoint
untested | podcast_continuity_test › malformed, oversized, unsafe and completed records are ignored | ui/.../media/Podcasts.kt (PodcastCheckpoint) | PlaybackTest covered part; SubstackPodcastTest.damagedOversizedUnsafeAndFinishedRecordsAreIgnored has XTA's whole list
untested | podcast_continuity_test › a stalled resume seek preserves the checkpoint and permits a later retry | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.aStalledResumeSeekKeepsTheCheckpointAndALaterTryPlaysFromThePlace
missing/different | podcast_continuity_test › a stalled open does not block stop or free a player beneath native work | plugins/substack/.../SubstackPodcast.kt (stop) | stop waited for the stalled open (up to 8 s); now immediate. SubstackPodcastTest.aStalledOpenDoesNotHoldUpTheStop
untested | podcast_continuity_test › a chosen speed applies now, survives a restart, and opens the next episode at it | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.aChosenSpeedAppliesNowSurvivesARestartAndOpensTheNextEpisode
untested | podcast_continuity_test › a stored speed that is not offered reads as normal speed | store/.../settings/NetworkPrefs.kt (podcastSpeed choices) | SubstackPodcastTest.aStoredSpeedThatIsNotOfferedReadsAsNormal
untested | podcast_continuity_test › each episode resumes where it was left, and a finished one starts over | plugins/substack/.../SubstackPodcast.kt, ui/.../media/Podcasts.kt | SubstackPodcastTest.eachEpisodeResumesWhereItWasLeftAndAFinishedOneStartsOver
untested | podcast_continuity_test › normal speed is set on open too, so a rate the player kept is not carried over | plugins/substack/.../SubstackPodcast.kt | SubstackPodcastTest.normalSpeedIsSetOnOpenTooSoAKeptRateIsNotCarriedOver

## test/substack_article_navigation_layout_test.dart
untested | substack_article_navigation_layout_test › contents become matching passages and clearing search restores headings | plugins/substack/.../SubstackArticle.kt (contents), SubstackReaderParts.kt (ContentsSheet) | the sheet's rule moved into SubstackArticle.contents; SubstackArticleNavigationTest.contentsBecomeMatchingPassagesAndClearingTheSearchBringsBackTheHeadings (empty → Strings.no_matches)
untested | substack_article_navigation_layout_test › an article without headings still allows full local find | plugins/substack/.../SubstackArticle.kt (contents) | SubstackArticleNavigationTest.anArticleWithoutHeadingsStillFindsEverything (empty → Strings.no_headings)
untested | substack_article_navigation_layout_test › selecting a passage returns its precise document target | plugins/substack/.../SubstackReaderParts.kt (onJump), SubstackArticle.blockOf | SubstackArticleNavigationTest.aChosenHeadingLeadsToItsOwnPlace
n/a | substack_article_navigation_layout_test › article navigation renders contents and results | – | golden render of Flutter's sheet
n/a | substack_article_navigation_layout_test › article navigation renders compact large-text RTL | – | golden render and overflow check of Flutter's sheet at text scale 2 with a keyboard inset

## test/substack_article_navigation_test.dart
covered | substack_article_navigation_test › contents retain publisher targets and distinguish duplicate heading titles | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.contentsKeepPublisherTargetsAndTellDuplicateHeadingsApart
covered | substack_article_navigation_test › nested paragraphs are indexed once, while plain text still supports find | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.nestedParagraphsAreIndexedOnceAndBareTextStillReads
covered | substack_article_navigation_test › search only sees sanitized visible writing, with all words in any order | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.findSeesOnlySanitizedVisibleWritingWithAllWordsInAnyOrder
covered | substack_article_navigation_test › publisher-injected navigation markers cannot collide with reader targets | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.markersAPublisherLeftCannotCollideWithTheReadersOwn
covered | substack_article_navigation_test › results excerpt around a match without making huge rows | plugins/substack/.../SubstackArticle.kt (SubstackPassage.excerpt) | SubstackArticleTest.aResultIsCutAroundItsMatchWithoutMakingHugeRows
covered | substack_article_navigation_test › article fragment routing › resolves relative, encoded and canonical fragment targets | plugins/substack/.../SubstackArticle.kt (SubstackReading.articleFragment) | SubstackArticleTest.aLinkJumpsInsideTheArticleOnlyWhenItIsThisArticle
covered | substack_article_navigation_test › article fragment routing › never claims other articles or external origins | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.aLinkJumpsInsideTheArticleOnlyWhenItIsThisArticle
untested | substack_article_navigation_test › jump code serializes untrusted targets instead of interpolating executable text | plugins/substack/.../SubstackWebPage.kt (jump) | SubstackArticleNavigationTest.theJumpCarriesAnUntrustedTargetAsDataNotCode
untested | substack_article_navigation_test › native article jumps stop initial restoration without reporting a reading scroll | plugins/substack/.../SubstackWebPage.kt (readingBridge) | SubstackArticleNavigationTest.aJumpStopsTheFirstRestoringWithoutCountingAsReading
covered | substack_article_navigation_test › literal top-level HTML examples remain text through sanitizing and navigation indexing | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.markersAPublisherLeftCannotCollideWithTheReadersOwn (literal part)
untested | substack_article_navigation_test › reader document supports RTL, compact tables and passage focus | plugins/substack/.../SubstackWebPage.kt (wrap) | SubstackArticleNavigationTest.thePageReadsRightToLeftWithScrollingTablesAndAMarkedPassage

## test/substack_audio_player_test.dart
missing/different | substack_audio_player_test › the player row picks the playback speed from the offered ones | plugins/substack/.../SubstackAudioPlayer.kt (SpeedMenu), SubstackPodcast.kt (SPEEDS) | the menu offers SubstackPodcast.SPEEDS and shows "1.5×"; its button had no name: now labelled "Playback speed" (new plugin string, translated from XTA's playback_speed). Choosing is SubstackPodcastTest.aChosenSpeedAppliesNowSurvivesARestartAndOpensTheNextEpisode

## test/substack_audit_probes_test.dart
covered | substack_audit_probes_test › BUG-01 (fixed): replies nested deeper than nine levels are kept at the deepest indent | plugins/substack/.../SubstackComments.kt | SubstackParserTest.aRunawayThreadStopsIndentingAtTheCapButKeepsEveryReply
covered | substack_audit_probes_test › BUG-02 (fixed): one post read through RSS and through the API is one post | plugins/substack/.../SubstackIdentity.kt | SubstackHostsTest.theApiCopyAndTheRssCopyOfAPostAreOnePost
covered | substack_audit_probes_test › BUG-03 (fixed): a rate limit ends the fallback chain and says so | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aRateLimitEndsTheFallbackChainAndSaysSo
covered | substack_audit_probes_test › BUG-04 (fixed): an unreachable custom domain never borrows another publication's posts | plugins/substack/.../SubstackClient.kt | SubstackClientTest.anUnreachableCustomDomainNeverBorrowsAnotherPublicationsPosts
covered | substack_audit_probes_test › BUG-04 (fixed): a followed custom domain looks itself up on its stored subdomain only | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aFollowedCustomDomainLooksItselfUpOnItsStoredSubdomainOnly
covered | substack_audit_probes_test › BUG-05 (fixed): a refresh reads all 30 followed publications | plugins/substack/.../SubstackFeeds.kt | SubstackFeedsTest.aRefreshReadsAllThirtyFollowedPublications
covered | substack_audit_probes_test › BUG-06 (fixed): two deep custom domains resolved from RSS get their own ids | plugins/substack/.../SubstackClient.kt | SubstackClientTest.twoDeepCustomDomainsFoundThroughRssGetTheirOwnIds
covered | substack_audit_probes_test › BUG-07 (fixed; reconstructed markup): no live form survives in an article | plugins/substack/.../SubstackHtml.kt | SubstackHtmlTest.scriptsFormsAndSubstacksChromeAreRemovedOutright
covered | substack_audit_probes_test › BUG-08 (gap marked; reconstructed markup): client-rendered blocks leave a placeholder | plugins/substack/.../SubstackHtml.kt | SubstackHtmlTest.blocksDrawnOnlyBySubstacksScriptsLeaveAPlaceholderAndOtherEmptiesGo
untested | substack_audit_probes_test › BUG-09 (fixed): the article cache remembers that a post is a video | plugins/substack/.../SubstackArticleCache.kt, SubstackPostJson.kt | SubstackAuditProbesTest.theArticleCacheRemembersThatAPostIsAVideo
covered | substack_audit_probes_test › BUG-10 (link shape unverified): an app share link is not read in-app | plugins/substack/.../SubstackHosts.kt (postRef) | SubstackHostsTest (BUG-10 assertion)

## test/substack_blocks_test.dart
covered | substack_blocks_test › paragraphs keep their styles, links and footnote markers | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.paragraphsKeepTheirStylesLinksAndFootnoteMarkers
covered | substack_blocks_test › headings, quotes, nested lists, code, dividers and tables come out in order | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.headingsQuotesNestedListsCodeDividersAndTablesComeOutInOrder
covered | substack_blocks_test › images, held embeds, web-only gaps and footnotes are their own blocks | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.imagesHeldEmbedsWebOnlyGapsAndFootnotesAreTheirOwnBlocks
covered | substack_blocks_test › the synthetic long post loses no text and no image | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.theSyntheticLongPostLosesNoTextAndNoImage
covered | substack_blocks_test › every passage the contents or find can name leads to the block it is drawn in | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.everyPassageContentsOrFindCanNameLeadsToTheBlockItIsDrawnIn
covered | substack_blocks_test › a passage folded into no block of its own leads to the one before it | plugins/substack/.../SubstackBlocks.kt | SubstackArticleTest.aPassageFoldedIntoNoBlockOfItsOwnLeadsToTheOneBeforeIt

## test/substack_body_search_test.dart
covered | substack_body_search_test › a passage is cut around the first match, marked where it was cut | plugins/substack/.../SubstackBodySearch.kt | SubstackOpmlTest.aPassageIsCutAroundTheFirstMatchMarkedWhereItWasCut
covered | substack_body_search_test › kept bodies are searched once read, and only posts with a kept body match | plugins/substack/.../SubstackBodySearch.kt | SubstackOpmlTest.keptBodiesAreSearchedOnceReadAndOnlyPostsWithAKeptBodyMatch
covered | substack_body_search_test › a post kept after a search is found once the search is cleared and typed again | plugins/substack/.../SubstackBodySearch.kt | SubstackOpmlTest.aPostKeptAfterASearchIsFoundOnceTheSearchIsClearedAndTypedAgain
n/a | substack_body_search_test › a search still reading bodies when the store goes away does not touch it | plugins/substack/.../SubstackBodySearch.kt | Dart store lifetime: native's search keeps no state of its own and returns its answer to a caller whose scope cancels it

## test/substack_custom_domain_test.dart
covered | substack_custom_domain_test › a domain that answers the Substack post API for the slug is a post; one request only | plugins/substack/.../SubstackClient.kt (probeCustomDomainPost) | SubstackClientTest.aDomainThatAnswersThePostApiForTheSlugIsAPostAskedOnce
covered | substack_custom_domain_test › a page, an error, or another slug is not a post, and nothing else is asked | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aDomainThatAnswersThePostApiForTheSlugIsAPostAskedOnce (second half)
missing/different → implemented | substack_custom_domain_test › a link the domain does not confirm is left to the browser | ui/links/LinkOpener.kt leave, plugins/substack SubstackPlugin.routeForLater | off the desktop a `/p/` link on an unknown host is asked (4 s, "Checking the link…") and opens in the reader only when the host confirms it; otherwise the browser. LinkOpenerTest.anUnknownDomainIsAsked…, aDomainThatConfirms…

## test/substack_discovery_tts_test.dart
covered | substack_discovery_tts_test › publicationFromDiscoveryJson › maps category / search publication fields | plugins/substack/.../SubstackParser.kt (discovered) | SubstackParserTest.discoveredPublicationsTakeTheirAddressFromBaseUrlCustomDomainOrSubdomain
covered | substack_discovery_tts_test › publicationFromDiscoveryJson › builds a substack.com base when only subdomain is present | plugins/substack/.../SubstackParser.kt | same test (platformer)
covered | substack_discovery_tts_test › publicationFromDiscoveryJson › returns null when nothing identifies the publication | plugins/substack/.../SubstackParser.kt | same test (orphan)
covered | substack_discovery_tts_test › parseSubstackRecommendations › reads recommendedPublication and blurb from _preloads HTML | plugins/substack/.../SubstackPages.kt | SubstackParserTest.recommendationsComeFromThePreloadsEachPublicationOnce
covered | substack_discovery_tts_test › parseSubstackRecommendations › returns empty on missing or reshaped HTML | plugins/substack/.../SubstackPages.kt | same test
covered | substack_discovery_tts_test › mergeSubstackSimilar › drops the seed, prefers recs, then pads with search | plugins/substack/.../SubstackParser.kt (mergeSimilar) | SubstackParserTest.similarDropsTheSeedPrefersRecommendationsThenPadsWithSearch
covered | substack_discovery_tts_test › buildSubstackSpeakText › speaks title and excerpt when there is no body yet | plugins/substack/.../SubstackHtml.kt (speakText) | SubstackHtmlTest.speechIsTheTitleWhereItIsFromTheExcerptThenTheBody
covered | substack_discovery_tts_test › buildSubstackSpeakText › prefers plain body over HTML when both are given | plugins/substack/.../SubstackHtml.kt | same test
covered | substack_discovery_tts_test › buildSubstackSpeakText › strips markup for speech | plugins/substack/.../SubstackHtml.kt | same test
covered | substack_discovery_tts_test › substackHtmlToPlainText › keeps paragraph breaks | plugins/substack/.../SubstackHtml.kt (plainText) | SubstackHtmlTest.plainTextKeepsParagraphBreaksAndSkipsFootnoteNumbers
covered | substack_discovery_tts_test › substackHtmlToPlainText › skips footnote markers and keeps the notes | plugins/substack/.../SubstackHtml.kt | same test
untested | substack_discovery_tts_test › the article page leaves long-press to text selection and reads from a selection | plugins/substack/.../SubstackWebPage.kt | SubstackDiscoveryTtsTest.thePageLeavesLongPressToSelectionAndReadsFromASelection

## test/substack_discussion_reader_test.dart
covered | substack_discussion_reader_test › comment outline honors parent IDs and keeps duplicate, orphan and cyclic inputs safe | plugins/substack/.../networks/substack/SubstackComments.kt | SubstackCommentsTest.theOutlineHonoursParentIdsAndKeepsDuplicateOrphanAndCyclicInputsSafe
covered | substack_discussion_reader_test › legacy depth rows preserve nesting without a parent field | plugins/substack/.../SubstackComments.kt | SubstackCommentsTest.depthAloneNestsCommentsThatCarryNoParent
covered | substack_discussion_reader_test › local search matches author and body while retaining connector context | plugins/substack/.../SubstackComments.kt | SubstackCommentsTest.aSearchMatchesAuthorAndBodyAndKeepsTheCommentsAboveAsContext
covered | substack_discussion_reader_test › date order sorts siblings stably without detaching replies | plugins/substack/.../SubstackComments.kt | SubstackCommentsTest.dateOrderSortsSiblingsStablyWithoutDetachingReplies
covered | substack_discussion_reader_test › ten thousand nested comments use iterative counting and traversal | plugins/substack/.../SubstackComments.kt | SubstackCommentsTest.tenThousandNestedCommentsAreCountedAndWalkedWithoutRecursion
untested | substack_discussion_reader_test › failed refresh retains comments and controls, retry prunes removed collapse IDs | plugins/substack/.../plugins/substack/SubstackComments.kt (CommentsModel, now given its loader) | SubstackDiscussionTest.aFailedRefreshKeepsTheCommentsAndControlsAndARetryForgetsRemovedFolds
untested | substack_discussion_reader_test › newer comment request wins and disposal ignores late data and actions | plugins/substack/.../SubstackComments.kt (CommentsModel) | SubstackDiscussionTest.theNewerReadWinsAndAGoneScreenIgnoresLateAnswers (late actions on a gone model change nothing anyone sees)
covered | substack_discussion_reader_test › discussion link parsing preserves punctuation and rejects non-web schemes | plugins/substack/.../SubstackLinks.kt (textParts) | SubstackCommentsTest.linksInADiscussionKeepTheirTextAndLeavePunctuationOut
covered | substack_discussion_reader_test › discussion text opens the tapped URL and updates link targets | plugins/substack/.../SubstackComments.kt (CommentRow → LinkAnnotation per part) | the parts and their URLs are SubstackCommentsTest.linksInADiscussionKeepTheirTextAndLeavePunctuationOut; a tap in Compose has no test harness here
covered | substack_discussion_reader_test › discussion supports collapsing, searching and clearing with parent context | plugins/substack/.../SubstackComments.kt (rows with collapsed/query/contextOnly; comment_no_match; Clear) | rules in SubstackCommentsTest (outline, search with context) and SubstackDiscussionTest (search clears folds)
untested | substack_discussion_reader_test › missing Note author metadata is safe and local Follow updates visibly | plugins/substack/.../SubstackNotesUi.kt (noteAsPost, NotePublication) | SubstackDiscussionTest.aNoteWithoutItsAuthorIsByUnknownAndItsPictureIsItsMedia; the Follow chip turns to "Following" from app.followOf (follow store: SubstackLocalTest)
untested | substack_discussion_reader_test › Note reactions respect calm mode and have readable semantics | plugins/substack/.../SubstackNotesUi.kt (noteLikes; label "Likes: n") | SubstackDiscussionTest.aNotesHeartsAreCountedExceptInCalmOrZenMode
untested | substack_discussion_reader_test › Note images open the shared fullscreen viewer | plugins/substack/.../SubstackNotesUi.kt (openMedia → host.openMedia) | the picture is the post's media (SubstackDiscussionTest.aNoteWithoutItsAuthorIsByUnknownAndItsPictureIsItsMedia), which the shared card opens full screen
covered | substack_discussion_reader_test › compact enlarged RTL discussion controls remain usable | plugins/substack/.../SubstackComments.kt (sort menu, BranchesMenu collapse all) | expandAll(false) folds every branch: SubstackDiscussionTest.aFailedRefresh…; layout at scale 1.7 is Flutter's
n/a | substack_discussion_reader_test › Substack discussion layout comments | – | golden render
n/a | substack_discussion_reader_test › Substack discussion layout comments-rtl | – | golden render
n/a | substack_discussion_reader_test › Substack discussion layout note-rtl | – | golden render

## test/substack_features_test.dart
covered | substack_features_test › what a podcast post carries › the episode file makes it a podcast | plugins/substack/.../SubstackParser.kt (podcast_url) | SubstackParserTest.aListingKeepsPostsAroundRowsOfAnotherShapeAndDropsTheBody (episode.isPodcast)
covered | substack_features_test › what a podcast post carries › a newsletter is not one, and an empty URL is no episode | plugins/substack/.../SubstackParser.kt | SubstackParserTest.badOptionalScalarsAreNullWithoutLosingThePost ("an empty URL is no episode")
covered | substack_features_test › what a post has gathered › the totals are read when carried | plugins/substack/.../SubstackParser.kt | SubstackParserTest.aWholePostGivesItsBodyBylineCountsAndPublication
covered | substack_features_test › what a post has gathered › the per-emoji map is summed when there is no total | plugins/substack/.../SubstackParser.kt (reactionSum) | SubstackParserTest.aListingKeepsPostsAroundRowsOfAnotherShapeAndDropsTheBody
covered | substack_features_test › what a post has gathered › nothing carried is null, which is not the same as zero | plugins/substack/.../SubstackParser.kt | SubstackParserTest.badOptionalScalarsAreNullWithoutLosingThePost
covered | substack_features_test › reading a discussion › the tree flattens in reading order, children under their parent | plugins/substack/.../SubstackParser.kt (comments) | SubstackParserTest.theDiscussionFlattensInReadingOrderWithDeletedCommentsSkipped
covered | substack_features_test › reading a discussion › a deleted comment vanishes but its children keep their place | plugins/substack/.../SubstackParser.kt | same test ("a deleted comment vanishes, its reply keeps its place")
covered | substack_features_test › reading a discussion › a payload that no longer fits is an empty discussion, not a throw | plugins/substack/.../SubstackParser.kt | SubstackParserTest.aPayloadThatNoLongerFitsIsAnEmptyDiscussion
covered | substack_features_test › reading a discussion › a runaway thread stops indenting at the cap | plugins/substack/.../SubstackParser.kt | SubstackParserTest.aRunawayThreadStopsIndentingAtTheCapButKeepsEveryReply

## test/substack_feed_fresh_test.dart
covered | substack_feed_fresh_test › a second home-strip open inside the TTL does not refetch | plugins/substack/.../SubstackFeeds.kt | SubstackFeedsTest.aRefreshThatIsNotForcedSkipsPublicationsAnsweredWithinTheWindow
covered | substack_feed_fresh_test › pull-to-refresh asks again even inside the TTL | plugins/substack/.../SubstackFeeds.kt | same test (forced refresh asks again)

## test/substack_feed_memory_test.dart
covered | substack_feed_memory_test › the memory keeps first pages without bodies, and an odd value is empty | plugins/substack/.../SubstackHomeFeed.kt (SubstackFeedMemory) | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
covered | substack_feed_memory_test › a restart inside the cache window paints the feed and asks Substack nothing | plugins/substack/.../SubstackHomeFeed.kt | SubstackHomeTest.aRestoredFeedPaintsAtOnceAndReadsOnlyThePublicationsThatAreNoLongerFresh
covered | substack_feed_memory_test › only publications that answered too long ago are asked again; a forced refresh asks all | plugins/substack/.../SubstackHomeFeed.kt | same test
covered | substack_feed_memory_test › resetting the plugin forgets the feed | ui/.../data/plugins/AppPlugins.kt (erase deletes plugin-data/<id>, where feed.json lives) | native keeps the memory in the plugin's folder, not in a preference, so uninstalling erases it (PluginPrefsTest's uninstall tests); XTA has no other reset of it

## test/substack_feed_order_test.dart
covered | substack_feed_order_test › the feed is newest first, ties by feed key, undated posts last | plugins/substack/.../SubstackFeeds.kt | SubstackFeedsTest.theFeedIsNewestFirstTiesByFeedKeyUndatedLast

## test/substack_follow_identity_test.dart
untested | substack_follow_identity_test › BUG-16: a publication page never rewrites another follow that shares its guessed id | plugins/substack/.../SubstackPublicationScreen.kt (enrich → refreshFollow only for app.followOf), SubstackFollowing.refreshed | SubstackFollowIdentityTest.aPublicationPageNeverRewritesAnotherFollowThatSharesItsGuessedId
untested | substack_follow_identity_test › a publication page refreshes its own follow, under that follow's id | plugins/substack/.../SubstackFollowing.kt (refreshed) | SubstackFollowIdentityTest.aPublicationPageRefreshesItsOwnFollowUnderThatFollowsId
covered | substack_follow_identity_test › BUG-06: following two sites whose hosts share a first label keeps both | plugins/substack/.../SubstackFollowing.kt | SubstackLocalTest.followsListPinnedFirstThenByNameAndPlaceANewOneUnderItsOwnHost ("a guessed id another follow holds goes under its host")
untested | substack_follow_identity_test › following the same site again updates its follow in place | plugins/substack/.../SubstackFollowing.kt (followed) | SubstackFollowIdentityTest.followingTheSameSiteAgainUpdatesItsFollowInPlace
covered | substack_follow_identity_test › substackFollowOf › the same host is the same follow, whatever the ids say | plugins/substack/.../SubstackIdentity.kt (SubstackFollows.followOf) | SubstackHostsTest.theSameHostIsTheSameFollowWhateverTheIdsSay
covered | substack_follow_identity_test › substackFollowOf › among follows on one host, the one with the same id wins | plugins/substack/.../SubstackIdentity.kt | same test (garbageday)
covered | substack_follow_identity_test › substackFollowOf › equal ids on two hosts match only when neither is a guess | plugins/substack/.../SubstackIdentity.kt | SubstackHostsTest.equalIdsOnTwoHostsMatchOnlyWhenNeitherIsAGuess
covered | substack_follow_identity_test › substackFollowOf › placing keeps the follow's id, or moves a taken id to the host | plugins/substack/.../SubstackIdentity.kt (place) | SubstackHostsTest.placingKeepsTheFollowsIdOrMovesATakenIdToTheHost
untested | substack_follow_identity_test › a publication found under its own subdomain on another host is the same follow | plugins/substack/.../SubstackFollowing.kt (followed) | SubstackFollowIdentityTest.aPublicationFoundUnderItsOwnSubdomainOnAnotherHostIsTheSameFollow

## test/substack_highlights_test.dart
covered | substack_highlights_test › a selection over two blocks is two quotes; a stray letter is none | plugins/substack/.../SubstackHighlights.kt (quotes) | SubstackHighlightsTest.aSelectionIsOneQuotePerLineWithItsSpacesCollapsed
untested | substack_highlights_test › highlighted text is split out of its runs, keeping their styles | plugins/substack/.../SubstackHighlights.kt (shown) | SubstackHighlightRulesTest.highlightedTextIsSplitOutOfItsRunsKeepingTheirStyles
untested | substack_highlights_test › a short quote is highlighted as a word, not inside other words; Chinese matches anywhere | plugins/substack/.../SubstackHighlights.kt | SubstackHighlightRulesTest.aShortQuoteIsAWordNotInsideOtherWordsAndChineseMatchesAnywhere
covered | substack_highlights_test › highlights are kept by post, survive a restart, drop the oldest beyond the limit | plugins/substack/.../SubstackHighlights.kt | SubstackHighlightsTest.highlightsAreKeptByPostNewestLastAndBoundedOldestPostFirst
covered | substack_highlights_test › a damaged preference reads as no highlights | plugins/substack/.../SubstackHighlights.kt | SubstackLocalTest.aDamagedPreferenceReadsAsNothingKept
covered | substack_highlights_test › the Markdown names the post and quotes each highlight | plugins/substack/.../SubstackHighlights.kt (markdown) | SubstackHighlightsTest.highlightsShareAsMarkdownWithTheirSourceAndQuoteMarksTrimmed
covered | substack_highlights_test › the sheet lists, removes and shares highlights | plugins/substack/.../SubstackReaderParts.kt (HighlightsSheet: remove, share markdown) | remove and markdown are tested in SubstackHighlightsTest; the sheet only calls them
untested | substack_highlights_test › two readers open on two posts keep each other's highlights | plugins/substack/.../SubstackHighlights.kt (one store per plugin) | SubstackHighlightRulesTest.twoPostsReadOneAfterTheOtherKeepEachOthersHighlights

## test/substack_home_controls_test.dart
n/a | substack_home_controls_test › assembled Substack Home 390 1 RTL=false dark=false | plugins/substack/.../SubstackHome.kt | golden render and pixel heights of XTA's slim header inside Flutter's Home; the local search it also checks is SubstackHomeControlsTest.eachFeedKeepsItsOwnSearchAndAResetClearsHomesFilterToo
n/a | substack_home_controls_test › assembled Substack Home 390 1 RTL=false dark=true | plugins/substack/.../SubstackHome.kt | as above (dark)
n/a | substack_home_controls_test › assembled Substack Home 320 2 RTL=true dark=true | plugins/substack/.../SubstackHome.kt | as above (RTL, text scale 2)
n/a | substack_home_controls_test › section icons and reading actions share one slim bar | plugins/substack/.../SubstackHome.kt (HomeHeader) | button geometry in Flutter's bar; native's header has the four panes and Search on one row, and Notes are read only when their pane is chosen (LaunchedEffect(selected))
n/a | substack_home_controls_test › pushed narrow reader keeps every section and respects Back and safe areas | plugins/substack/.../SubstackHome.kt | safe areas and Back of a pushed Flutter route; native's Notes pane has no Home filters (NotesPane has no ReadingToolbar)
untested | substack_home_controls_test › loaded search combines publication, author and article terms without mutating source | plugins/substack/.../SubstackFilters.kt (loaded) | SubstackHomeControlsTest.theLoadedSearchCombinesPublicationAuthorAndArticleWordsWithoutChangingTheList
untested | substack_home_controls_test › oldest and popular are stable and missing dates follow dated articles | plugins/substack/.../SubstackFilters.kt (comparator) | SubstackHomeControlsTest.oldestAndPopularAreStableAndUndatedPostsComeLast
untested | substack_home_controls_test › Home search stays local and survives a lazy tab switch | plugins/substack/.../SubstackHome.kt (HomeControls in the kept HomeState) | SubstackHomeControlsTest.eachFeedKeepsItsOwnSearchAndAResetClearsHomesFilterToo
n/a | substack_home_controls_test › embedded German Home starts with articles instead of stacked controls | – | golden render and toolbar height
untested | substack_home_controls_test › sort and reset work locally and show an active options indicator | plugins/substack/.../SubstackReadingToolbar.kt (Marked badge from SubstackHomeRules.filtered), HomeControls.reset | SubstackHomeControlsTest.eachFeedKeepsItsOwnSearchAndAResetClearsHomesFilterToo
covered | substack_home_controls_test › Following opens a named publication list and its archive | plugins/substack/.../SubstackReadingToolbar.kt (PublicationPicker, "Unread" from withUnread) | SubstackHomeTest.homeShowsTheFollowedPublicationsPostsThroughItsFilterAndInboxOnlyTheUnread (withUnread); route: routesNameEachScreenAndReadBackWhatTheyNamed
covered | substack_home_controls_test › partial failure stays compact and can be retried from its details | plugins/substack/.../SubstackReadingToolbar.kt (warning icon named by partial_error, retry dialog), SubstackFeeds.kt | SubstackFeedsTest.failedPublicationPagesKeepTheirOffsetWhileOthersProgress / aFailedRefreshKeepsWhatWasShownAndAFreshOneReplacesIt
n/a | substack_home_controls_test › a loaded feed ends at its last post, and a refresh shows on the line over it | plugins/substack/.../SubstackFeedPanes.kt (PullToRefreshBox, LoadMoreRow) | footer heights in pixels; native shows a refresh in the pull indicator at the top, not after the last post
n/a | substack_home_controls_test › a page that failed to load more is one compact row at the end | ui/.../plugins (LoadMoreRow) | row height in pixels; native's end row shows the failure with Retry
covered | substack_home_controls_test › pulling Home refreshes a freshly cached feed | plugins/substack/.../SubstackFeedPanes.kt (onRefresh = refresh(force = true)) | SubstackFeedsTest.aRefreshThatIsNotForcedSkipsPublicationsAnsweredWithinTheWindow ("pull to refresh asks again")
covered | substack_home_controls_test › pulling Notes refreshes a freshly cached public stream | plugins/substack/.../SubstackNotesUi.kt (onRefresh = refresh(force = true)), SubstackNotesFeed.kt | SubstackFeedsTest.notePagesKeepTheirIssuingHostAndARefreshAsksTheNextHost
untested | substack_home_controls_test › individual read toggle is reversible and current unread filter updates | plugins/substack/.../SubstackHomeRules.kt, SubstackLocal.kt (read marks) | SubstackHomeControlsTest.theSavedAndUnreadFiltersFollowTheMarksAtOnce
untested | substack_home_controls_test › the Saved filter shows the saved posts on Home, and follows an unsave | plugins/substack/.../SubstackHomeRules.kt | SubstackHomeControlsTest.theSavedAndUnreadFiltersFollowTheMarksAtOnce
covered | substack_home_controls_test › an empty loaded Inbox can page to older unread articles | plugins/substack/.../SubstackFeedPanes.kt (InboxPane keeps its LoadMoreRow under the empty state), SubstackHomeFeed.loadMore | SubstackFeedsTest.overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops (paging)
untested | substack_home_controls_test › Library choice and query survive returning from Home | plugins/substack/.../SubstackHome.kt (HomeControls.librarySection/libraryQuery) | SubstackHomeControlsTest.theLibrarysChoiceAndSearchStayWhileTheHomeTabIsKept
untested | substack_home_controls_test › Home and card actions fit narrow enlarged RTL text | plugins/substack/.../SubstackHome.kt (HomeMenu: Add publication, Mark all as read) | layout is Flutter's; the menu's rule is SubstackHomeControlsTest.theSavedAndUnreadFiltersFollowTheMarksAtOnce (anyUnread)

## test/substack_links_test.dart
covered | substack_links_test › parseSubstackPostLink › reads a post on a publication subdomain | plugins/substack/.../SubstackLinks.kt | SubstackLinksTest.aPostOnAPublicationSubdomainOrTheShareHostIsALink
covered | substack_links_test › parseSubstackPostLink › reads the share host Substack uses in its emails | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › parseSubstackPostLink › keeps the post when the link points at its comments | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › parseSubstackPostLink › ignores query strings and fragments | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › parseSubstackPostLink › a custom domain is only claimed when the reader follows it | plugins/substack/.../SubstackLinks.kt | SubstackLinksTest.aCustomDomainIsOnlyClaimedWhenItIsFollowed
covered | substack_links_test › parseSubstackPostLink › publication pages are not posts | plugins/substack/.../SubstackLinks.kt | SubstackLinksTest.pagesOfAPublicationSubstacksOwnPagesAndOtherSitesAreNoPost
covered | substack_links_test › parseSubstackPostLink › Substack's own pages are not posts | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › parseSubstackPostLink › non-Substack links are left alone | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › parseSubstackPostLink › a lookalike host cannot impersonate a publication | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › substackPostStub › carries what the reader needs to fetch the real post | plugins/substack/.../SubstackLinks.kt (stub) | SubstackLinksTest.aStubCarriesWhatTheReaderNeedsToLoadTheRealPost
covered | substack_links_test › substackPostStub › prefers the followed publication's own name when known | plugins/substack/.../SubstackLinks.kt | same test
covered | substack_links_test › custom-domain candidates › a /p/ link on an unknown domain is a candidate on its own host | plugins/substack/.../SubstackLinks.kt (customDomainCandidate) | SubstackLinksTest.aPostLinkOnAnUnknownDomainIsACandidateOnItsOwnHost
covered | substack_links_test › custom-domain candidates › Substack hosts, followed domains, other shapes and Instagram are not candidates | plugins/substack/.../SubstackLinks.kt | same test

## test/substack_local_library_test.dart
covered | substack_local_library_test › local like/save snapshots › round-trip omits body but keeps what the library needs | plugins/substack/.../SubstackPostJson.kt | SubstackHighlightsTest.aSnapshotReadsBackAsThePostWithoutItsBody
covered | substack_local_library_test › local like/save snapshots › corrupt prefs become an empty library, not a throw | plugins/substack/.../SubstackLocal.kt (SubstackPostShelf) | SubstackLocalTest.aDamagedPreferenceReadsAsNothingKept

## test/substack_native_article_test.dart
covered | substack_native_article_test › a post reads natively: header, text, embeds as links, web-only gaps and footnotes | plugins/substack/.../SubstackArticleView.kt (ArticleHeader "Publication · Author", heading semantics, embed/web-only rows), SubstackBlocks.kt | the blocks are SubstackArticleTest.imagesHeldEmbedsWebOnlyGapsAndFootnotesAreTheirOwnBlocks and paragraphsKeepTheirStylesLinksAndFootnoteMarkers; taps go to ArticleTaps.onLink (no Compose UI harness)
untested | substack_native_article_test › a paid preview ends with the note and the way to the site | plugins/substack/.../SubstackArticleView.kt (ArticleFooter), SubstackArticle.kt (isPreview) | SubstackNativeArticleTest.aPaidPostWithItsOpeningIsAPreviewThatEndsWithTheWayToTheSite
n/a | substack_native_article_test › the long post scrolls on a narrow screen at 200 % text without overflow | – | Flutter overflow check
untested | substack_native_article_test › the setting is off by default, switches on, and a reset turns it off | store/.../settings/NetworkPrefs.kt (SubstackPrefs.nativeReader, readerFont, columnWidth), ui/.../data/plugins/PluginErase.kt | SubstackNativeArticleTest.theNativeReaderIsOffByDefaultAndItsFontAndColumnStartAtSerifAndMedium (native has no reset apart from uninstalling, which erases the plugin.substack.* keys)
covered | substack_native_article_test › a footnote marker opens its note in a sheet; an image opens the viewer | plugins/substack/.../SubstackReaderParts.kt (FootnoteSheet, footnotesOf), SubstackArticleView.kt (onImage) | footnote markers carry their number: SubstackArticleTest.paragraphsKeepTheirStylesLinksAndFootnoteMarkers
covered | substack_native_article_test › the place reached is kept by block and comes back on reopening | store/.../reading/ArticleReading.kt (firstVisibleBlock, journal) | ArticleReadingTest.theFirstBlockStillShowingIsWhereANativeReaderIs and openingIsNotFinishingAndThePlaceAndTypeSurviveARestart
covered | substack_native_article_test › a jump from the contents puts its block at the top | plugins/substack/.../SubstackArticle.kt (blockOf) → list.scrollToItem(ROWS_BEFORE_BLOCKS + index) | SubstackArticleTest.everyPassageContentsOrFindCanNameLeadsToTheBlockItIsDrawnIn
covered | substack_native_article_test › a kept post shows its images from their files, the rest from the network | plugins/substack/.../SubstackArticleView.kt (model = keptImages[src] ?: src), SubstackReaderModel.kt | a one-line choice in the view; the kept files come from the app's offline store (its own tests)
untested | substack_native_article_test › on a wide screen the text keeps its column width and font | store/.../settings/Choices.kt (SubstackColumnWidth.rem) | SubstackNativeArticleTest.theColumnIsNarrowOrWideInRem
covered | substack_native_article_test › highlights show behind their text, and the selection menu saves a new one | plugins/substack/.../SubstackHighlights.kt (shown, quotes) | SubstackHighlightsTest.quotesAreMarkedOnWholeWordsUnlessThatIsTheOnlyPlaceTheyOccur and aSelectionIsOneQuotePerLineWithItsSpacesCollapsed

## test/substack_open_parse_test.dart
covered | substack_open_parse_test › one parse gives the same page and speech text: synthetic long post | plugins/substack/.../SubstackArticle.kt | SubstackArticleTest.oneParseGivesTheSameSpeechTextAsReadingTheBodyAlone (the page half is Dart's sanitized flag: native's wrap always takes the parsed body)
covered | substack_open_parse_test › one parse gives the same page and speech text: bare text | plugins/substack/.../SubstackArticle.kt | same test
covered | substack_open_parse_test › one parse gives the same page and speech text: line breaks and lists | plugins/substack/.../SubstackArticle.kt | same test
covered | substack_open_parse_test › one parse gives the same page and speech text: tables | plugins/substack/.../SubstackArticle.kt | same test
covered | substack_open_parse_test › one parse gives the same page and speech text: stripped parts | plugins/substack/.../SubstackArticle.kt | same test
covered | substack_open_parse_test › one parse gives the same page and speech text: hidden and footnotes | plugins/substack/.../SubstackArticle.kt | same test
covered | substack_open_parse_test › an embed is held behind a button until the reader asks for it | plugins/substack/.../SubstackArticle.kt, SubstackWebPage.kt (label escaped, replaceWith) | SubstackArticleTest.anEmbedIsHeldBehindAPlaceholderUntilTheReaderAsksForIt; SubstackWebPageTest.theLabelsCannotCloseTheirScript
covered | substack_open_parse_test › a parsed article is reused only for the same post, date and body | plugins/substack/.../SubstackArticle.kt (SubstackParsedArticles) | SubstackArticleTest.aParsedArticleIsReusedOnlyForTheSamePostDateAndBody

## test/substack_opml_test.dart
covered | substack_opml_test › an export reads back as the same sites, names escaped | plugins/substack/.../SubstackOpml.kt | SubstackOpmlTest.anExportReadsBackAsTheSameSitesWithNamesEscaped
covered | substack_opml_test › another reader's OPML: nested outlines, feed-only entries, duplicates and junk | plugins/substack/.../SubstackOpml.kt | SubstackOpmlTest.anotherReadersOpmlWithNestedOutlinesFeedOnlyEntriesDuplicatesAndJunk
covered | substack_opml_test › an import adds the new, leaves the followed and counts the rest; a rate limit stops it | plugins/substack/.../SubstackOpml.kt | SubstackOpmlTest.anImportAddsTheNewLeavesTheFollowedAndARateLimitStopsIt
covered | substack_opml_test › the settings rows export the follows and import a file | plugins/substack/.../SubstackSettings.kt (OpmlRows: export to substack-follows.opml, import, opml_result notice) | export/import themselves are the tests above; the rows only call them
missing/different | substack_opml_test › an import whose settings screen closed stops asking | plugins/substack/.../SubstackSettings.kt (OpmlRows) | native ran the import in the plugin's scope, beyond the screen; now in the settings screen's scope, so leaving stops it. SubstackOpmlStopTest.anImportWhoseScreenClosedStopsAsking

## test/substack_pins_test.dart
covered | substack_pins_test › pinned publications sort ahead of the A–Z rest | plugins/substack/.../SubstackFollowing.kt (ordered) | SubstackLocalTest.followsListPinnedFirstThenByNameAndPlaceANewOneUnderItsOwnHost
covered | substack_pins_test › unknown pin ids are skipped | plugins/substack/.../SubstackFollowing.kt | same test ("gone")
covered | substack_pins_test › empty pins leave the list alone | plugins/substack/.../SubstackFollowing.kt | ordered() sorts by name as XTA's follows table is read, then pins nothing; same test

## test/substack_plugin_test.dart
covered | substack_plugin_test › resolveSubstackBase accepts handle and URL | plugins/substack/.../SubstackHosts.kt | SubstackHostsTest.aTypedHandleOrAddressNamesItsPublication
covered | substack_plugin_test › resolveSubstackBase reads share links and @profiles | plugins/substack/.../SubstackHosts.kt | SubstackHostsTest.shareLinksAndProfilesBecomeTheSubdomainAndSubstacksOwnPagesNameNothing
covered | substack_plugin_test › resolveSubstackPostRef parses /p/slug and share URLs | plugins/substack/.../SubstackHosts.kt (postRef) | SubstackHostsTest.aPastedPostGivesItsPublicationAndSlug
covered | substack_plugin_test › publicationFromPostJson keeps the host that served the posts | plugins/substack/.../SubstackParser.kt | SubstackParserTest.aBylineOfAnotherShapeStillGivesThePublicationAtTheHostThatServedIt
covered | substack_plugin_test › publicationFetchBases tries leftover custom domains then Substack | plugins/substack/.../SubstackHosts.kt (fetchBases) | SubstackHostsTest.aFollowedCustomDomainIsReadAtItsHostThenItsTwin
covered | substack_plugin_test › subdomainOf does not turn a custom domain into www | plugins/substack/.../SubstackHosts.kt, SubstackModels.kt (displayName) | SubstackHostsTest.wwwIsNeverAPublicationTitleOrSubdomain
covered | substack_plugin_test › substackHostCandidates adds the Substack twin for a custom domain | plugins/substack/.../SubstackHosts.kt (candidates, requested, leftover) | SubstackHostsTest.aFollowedCustomDomainIsReadAtItsHostThenItsTwin
covered | substack_plugin_test › a stored subdomain replaces the guessed twin; only apex domains guess | plugins/substack/.../SubstackHosts.kt | SubstackHostsTest.aStoredSubdomainReplacesTheGuessedTwinAndOnlyApexDomainsGuess
covered | substack_plugin_test › a deep host found without JSON is followed under its host | plugins/substack/.../SubstackHosts.kt | SubstackHostsTest.aDeepHostFoundWithoutJsonIsFollowedUnderItsHost
covered | substack_plugin_test › a twin must not name another custom domain or subdomain | plugins/substack/.../SubstackHosts.kt | SubstackHostsTest.aTwinMustNotNameAnotherCustomDomainOrSubdomain
covered | substack_plugin_test › publicationFromHomepageHtml reads the real title and avatar | plugins/substack/.../SubstackPages.kt | SubstackRssTest.aHomePagesOpenGraphTagsNameAPublicationThatLeftSubstacksJson
covered | substack_plugin_test › looksLikeBeehiivPostsJson requires the /posts listing shape | plugins/substack/.../SubstackParser.kt | SubstackParserTest.beehiivPostsMapPremiumToPaidAndTheirSlugToTheCanonicalUrl
covered | substack_plugin_test › postFromBeehiivJson maps premium audience and the /p/slug URL | plugins/substack/.../SubstackParser.kt | same test
covered | substack_plugin_test › postFromBeehiivHtml reads OG article tags | plugins/substack/.../SubstackPages.kt | SubstackRssTest.aBeehiivPostPageIsReadFromRemixThenJsonLdThenOpenGraph
covered | substack_plugin_test › postFromBeehiivHtml reads JSON-LD, and an odd JSON-LD shape falls through to OG tags | plugins/substack/.../SubstackPages.kt | same test
covered | substack_plugin_test › publicationFromBeehiivRemix reads name and logo | plugins/substack/.../SubstackPages.kt | SubstackRssTest.aBeehiivSiteIsReadFromItsRemixStateElseItsTags
covered | substack_plugin_test › jsonObjectLiteralAt does not end on a brace inside a string | plugins/substack/.../SubstackPages.kt (jsonObjectAt, remixContext) | same test
covered | substack_plugin_test › publicationFromProfileJson reads primaryPublication | plugins/substack/.../SubstackParser.kt | SubstackParserTest.aProfileGivesItsPrimaryPublicationElseItsFirstPublication
covered | substack_plugin_test › isPaywalled recognizes live only_paid audience | plugins/substack/.../SubstackModels.kt | SubstackParserTest.paidAudiencesArePaywalledAndAPaidPostMayStillCarryItsOpening
covered | substack_plugin_test › SubstackClient.fetchPosts parses public JSON without body | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aPublicationsPostsAreReadFromItsPublicJsonWithoutBodies
covered | substack_plugin_test › SubstackClient.fetchPost loads full body and paywall flag | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aPostIsReadWholeWithItsPaywallFlag
untested | substack_plugin_test › wrapSubstackHtml is theme-aware and sanitizes chrome | plugins/substack/.../SubstackWebPage.kt | SubstackPageThemeTest.thePageFollowsTheThemeAndCarriesOnlyTheSanitizedBody
covered | substack_plugin_test › sanitize and plain text keep readable content | plugins/substack/.../SubstackHtml.kt | SubstackHtmlTest.plainTextKeepsParagraphBreaksAndSkipsFootnoteNumbers, scriptsFormsAndSubstacksChromeAreRemovedOutright
covered | substack_plugin_test › SubstackClient.fetchSimilarPublications prefers recs then search | plugins/substack/.../SubstackClient.kt | SubstackClientTest.similarPublicationsAreTheAuthorsRecommendationsThenSearchHits
covered | substack_plugin_test › fetchPublication refuses a site that is not Substack | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aSiteThatIsNotSubstackIsNotAPublication
covered | substack_plugin_test › fetchPosts falls back to the Substack host after a leftover domain | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aLeftoverCustomDomainIsReadOnItsSubstackTwinUnderTheFollowedAddress
covered | substack_plugin_test › a follow saved as www on a custom domain still loads the Substack twin | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aFollowSavedAsWwwStillReadsTheTwinAndTakesItsNameFromTheByline
covered | substack_plugin_test › fetchPublication prefers a living Beehiiv host over the leftover Substack | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aLivingBeehiivSiteIsReadThereAndItsLeftoverTwinNeverAsked
covered | substack_plugin_test › fetchPost reads a Beehiiv /p/slug page instead of leftover Substack | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aBeehiivPostIsReadFromItsPageNotFromTheLeftoverTwin
covered | substack_plugin_test › fetchPosts uses /api/v1/archive when /posts is gone | plugins/substack/.../SubstackClient.kt | SubstackClientTest.theArchiveListingAnswersWhenPostsIsGone
covered | substack_plugin_test › resolvePublication follows open.substack.com share links | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aShareLinkResolvesToItsPublication
covered | substack_plugin_test › resolvePublication maps a leftover custom domain to its Substack twin | plugins/substack/.../SubstackClient.kt | SubstackClientTest.aLeftoverCustomDomainResolvesThroughItsTwinUnderItsOwnAddress
covered | substack_plugin_test › SubstackPublication prefs round-trip | plugins/substack/.../SubstackPostJson.kt (publications) | SubstackHighlightsTest.aSnapshotReadsBackAsThePostWithoutItsBody (publications line)

## test/substack_post_identity_test.dart
covered | substack_post_identity_test › is the canonical URL without scheme, www, query, fragment or trailing slash | SubstackCommentsTest.aPostOnAPublicationSubdomainOrTheShareHostIsALink
covered | substack_post_identity_test › falls back to the publication host and slug, then to the id | SubstackCommentsTest.aPostOnAPublicationSubdomainOrTheShareHostIsALink
covered | substack_post_identity_test › tells apart two publications that use the same slug | SubstackFeedsTest.aSearchMergesNewestFirstWithoutConfusingEqualIdsOnTwoPublications
covered | substack_post_identity_test › the API copy and the RSS copy of a post are the same post | SubstackFeedsTest.anRssPageAfterAnApiPageRepeatsNoPostAndTheApiCopyStays
covered | substack_post_identity_test › reading one copy marks the other, and unread clears both | SubstackFeedsTest.feedFiltersAreLocalAndTheSavedFilterAsksTheLibrary
covered | substack_post_identity_test › ids stored before the change still count | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
covered | substack_post_identity_test › a like or a save on one copy is on the other, also for entries stored before | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
covered | substack_post_identity_test › an RSS page after an API page repeats no post, and the API copy stays | SubstackFeedsTest.anRssPageAfterAnApiPageRepeatsNoPostAndTheApiCopyStays
covered | substack_post_identity_test › comments of an RSS copy are asked for under the numeric id | SubstackClientTest.anRssCopysCommentsAreAskedUnderTheApisNumber

## test/substack_preview_test.dart
covered | substack_preview_test › a paid post can still arrive with the part it gives away | SubstackClientTest.aPostIsReadWholeWithItsPaywallFlag
covered | substack_preview_test › a paid post with nothing attached has nothing to show | SubstackArticleTest.aPaidPostWithoutABodyGetsThePaidPaneNotTheLiveSite
covered | substack_preview_test › a free post is not marked paid | SubstackClientTest.aPostIsReadWholeWithItsPaywallFlag
covered | substack_preview_test › a paid post without a body gets the paid pane, not the live site | SubstackArticleTest.aPaidPostWithoutABodyGetsThePaidPaneNotTheLiveSite
covered | substack_preview_test › a paid post with its free opening is rendered as an article | SubstackArticleTest.aPaidPostWithoutABodyGetsThePaidPaneNotTheLiveSite
untested → ported | substack_preview_test › a free post without a body opens its page, or says it is empty | Compose (Substack reader) | Compose only
covered | substack_preview_test › is rendered with the article when there is one | SubstackArticleTest.paragraphsKeepTheirStylesLinksAndFootnoteMarkers
untested → ported | substack_preview_test › a free article gets no note at all | Compose (Substack reader) | Compose only
untested → ported | substack_preview_test › a note with nowhere to continue is still shown | Compose (Substack reader) | Compose only

## test/substack_profile_nav_test.dart
covered | substack_profile_nav_test › www is never a publication title | SubstackClientTest.aFollowSavedAsWwwStillReadsTheTwinAndTakesItsNameFromTheByline
covered | substack_profile_nav_test › garbageday.email probes the leftover Substack host | SubstackClientTest.aLeftoverCustomDomainIsReadOnItsSubstackTwinUnderTheFollowedAddress
untested → ported | substack_profile_nav_test › publicationForPost keeps a tappable in-app profile | Compose (Substack publication) | Compose only
covered | substack_profile_nav_test › resolvePublication follows garbageday via the leftover host | SubstackClientTest.aLeftoverCustomDomainResolvesThroughItsTwinUnderItsOwnAddress
covered | substack_profile_nav_test › resolvePublication follows a living Beehiiv custom domain | SubstackClientTest.aLivingBeehiivSiteIsReadThereAndItsLeftoverTwinNeverAsked
untested → ported | substack_profile_nav_test › tapping the publication name opens the in-app profile | Compose (Substack publication) | Compose only
untested → ported | substack_profile_nav_test › the publication profile scrolls header and posts together | Compose (Substack publication) | Compose only

## test/substack_protocol_regression_test.dart
covered | substack_protocol_regression_test › publication search preserves query, offset, limit and followed source base | SubstackClientTest.anArchiveSearchKeepsItsQueryOffsetLimitAndTheFollowedAddress
covered | substack_protocol_regression_test › search bounds its page size and normalizes negative offsets | SubstackClientTest.aSearchBoundsItsPageAndAValidEmptyAnswerStopsTheHosts
covered | substack_protocol_regression_test › a valid empty search result stops fallback while all-host failures remain errors | SubstackClientTest.aSearchBoundsItsPageAndAValidEmptyAnswerStopsTheHosts
covered | substack_protocol_regression_test › malformed 200 search shapes retry hosts instead of reporting no matches | SubstackClientTest.anotherShapeHandsTheSearchToTheNextHost
covered | substack_protocol_regression_test › pagination preserves a valid empty page and rejects inaccessible or malformed archives | SubstackClientTest.aValidEmptyLaterPageIsAnAnswerButAnUnreadableArchiveIsAnError
covered | substack_protocol_regression_test › RSS fallback pages locally without repeating requests and retains post metadata | SubstackClientTest.theRssFallbackPagesLocallyWithoutAskingAgainAndKeepsEachPostsMetadata
covered | substack_protocol_regression_test › RSS paging cache is isolated by source even when publication IDs match | SubstackClientTest.theRssPagingMemoryBelongsToOneSourceEvenWhenIdsMatch
covered | substack_protocol_regression_test › a successful JSON refresh replaces the previous RSS paging cache | SubstackClientTest.aSuccessfulJsonReadReplacesTheRssPagingMemory
covered | substack_protocol_regression_test › a valid empty JSON refresh cannot leave stale RSS posts available for pagination | SubstackClientTest.aValidEmptyJsonReadLeavesNoStaleRssPagesButAFailedOneKeepsThem
covered | substack_protocol_regression_test › a failed refresh preserves previously downloaded RSS pages for offline reading | SubstackClientTest.aValidEmptyJsonReadLeavesNoStaleRssPagesButAFailedOneKeepsThem
covered | substack_protocol_regression_test › bad optional article scalars do not remove valid search siblings | SubstackFeedsTest.aSearchMergesNewestFirstWithoutConfusingEqualIdsOnTwoPublications
covered | substack_protocol_regression_test › bad optional byline publication metadata cannot discard an entire archive page | SubstackFeedsTest.publicationFiltersAndOrdersLeaveTheLoadedPostsAlone
covered | substack_protocol_regression_test › snapshot lists retain valid rows around wrongly typed optional fields | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
covered | substack_protocol_regression_test › Notes tolerate missing author, malformed counts and unsupported bodies without losing siblings | SubstackClientTest.notesComeFromAHostsReaderFeedWithTheirCursor
covered | substack_protocol_regression_test › non-finite counters remain safe and negative counters never display as negative | SubstackFeedsTest.aCursorSeenBeforeEndsThePagingAndANoteSeenAgainReplacesItselfInPlace
covered | substack_protocol_regression_test › comments retain parent IDs and malformed/deleted parents preserve readable descendants | SubstackCommentsTest.theOutlineHonoursParentIdsAndKeepsDuplicateOrphanAndCyclicInputsSafe
covered | substack_protocol_regression_test › comment cycles, repeated identities and very deep deleted chains terminate safely | SubstackCommentsTest.tenThousandNestedCommentsAreCountedAndWalkedWithoutRecursion
covered | substack_protocol_regression_test › comments distinguish a readable empty discussion from exhausted transport failures | SubstackCommentsTest.theOutlineHonoursParentIdsAndKeepsDuplicateOrphanAndCyclicInputsSafe

## test/substack_publication_reader_test.dart
covered | substack_publication_reader_test › a failed next page keeps articles and retry uses the failed offset | SubstackFeedsTest.aFailedNextPageKeepsTheArticlesAndRetryAsksTheFailedOffset
covered | substack_publication_reader_test › failed refresh retains articles and retries the first page | SubstackFeedsTest.aFailedFirstPageKeepsTheArticlesAndRetryAsksTheFirstPage
covered | substack_publication_reader_test › duplicate page requests are suppressed and refresh supersedes an old page | SubstackFeedsTest.overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops
covered | substack_publication_reader_test › repeated server pages stop paging while updating metadata | SubstackFeedsTest.repeatedServerPagesStopPagingAndDuplicatesShowOnce
covered | substack_publication_reader_test › duplicates inside the first server page only appear once | SubstackFeedsTest.repeatedServerPagesStopPagingAndDuplicatesShowOnce
covered | substack_publication_reader_test › search has separate pagination and clearing it restores the archive | SubstackFeedsTest.anArchiveAndItsSearchAskTheirOwnEndpointsAndOffsets
covered | substack_publication_reader_test › late results cannot replace the newer query, even after returning to the same query | SubstackFeedsTest.aNewerLoadSupersedesAnOlderOnesLateResults
untested → ported | substack_publication_reader_test › search failure is retryable and does not empty the archive | Compose (Substack archive model) | Compose only
untested → ported | substack_publication_reader_test › disposing while a request is in flight ignores completion | Compose (Substack archive model) | Compose only
untested → ported | substack_publication_reader_test › publication search waits for a pause in typing | Compose (Substack archive model) | Compose only
covered | substack_publication_reader_test › metadata enrichment preserves local identity and custom-domain address | SubstackClientTest.enrichingKeepsTheFollowsIdAndAddressAndFailsQuietly
covered | substack_publication_reader_test › a failed metadata lookup leaves the archive usable | SubstackClientTest.enrichingKeepsTheFollowsIdAndAddressAndFailsQuietly
covered | substack_publication_reader_test › filters distinguish unread, paid, audio and video without changing loaded pages | SubstackFeedsTest.feedFiltersAreLocalAndTheSavedFilterAsksTheLibrary
covered | substack_publication_reader_test › orders dates chronologically with unknown dates last and stable ties | SubstackFeedsTest.theFeedIsNewestFirstTiesByFeedKeyUndatedLast
covered | substack_publication_reader_test › similar recommendations suppress duplicate loads and exclude seed/duplicates | SubstackClientTest.similarPublicationsAreTheAuthorsRecommendationsThenSearchHits
untested → ported | substack_publication_reader_test › similar recommendation errors are retryable | Compose (Substack recommendations) | Compose only
covered | substack_publication_reader_test › a publication filter keeps paging available when its current page has no matches | SubstackFeedsTest.publicationFiltersAndOrdersLeaveTheLoadedPostsAlone
untested → ported | substack_publication_reader_test › publication controls fit a narrow RTL screen with large text | Compose (Substack publication) | Compose only
untested → ported | substack_publication_reader_test › recommendation rows fit a narrow RTL screen with large text | Compose (Substack publication) | Compose only

## test/substack_read_time_test.dart
covered | substack_read_time_test › read time is the words at 230 a minute, rounded up, at least one minute | SubstackArticleTest.readTimeIsTheWordsAt230AMinuteRoundedUpAtLeastOne
covered | substack_read_time_test › the body, when at hand, counts instead of the API figure | SubstackArticleTest.readTimeIsTheWordsAt230AMinuteRoundedUpAtLeastOne
covered | substack_read_time_test › no estimate for an episode, a video, or a post with no count | SubstackArticleTest.readTimeIsTheWordsAt230AMinuteRoundedUpAtLeastOne
covered | substack_read_time_test › the API word count is read, survives a saved snapshot, and a bad one is ignored | SubstackArticleTest.readTimeIsTheWordsAt230AMinuteRoundedUpAtLeastOne
untested → ported | substack_read_time_test › the article page names the read time with the byline | Compose (Substack reader) | Compose only

## test/substack_reader_recovery_test.dart
untested → ported | substack_reader_recovery_test › refresh keeps the existing readable document after a network failure | Compose (Substack reader model) | Compose only
untested → ported | substack_reader_recovery_test › latest request wins when older reads finish last | Compose (Substack reader model) | Compose only
untested → ported | substack_reader_recovery_test › initial failure is retryable and stale errors cannot replace success | Compose (Substack reader model) | Compose only
untested → ported | substack_reader_recovery_test › in-flight reads safely stop after store disposal | Compose (Substack reader model) | Compose only
covered | substack_reader_recovery_test › navigation store restores contents after clearing a local query | SubstackArticleNavigationTest.contentsBecomeMatchingPassagesAndClearingTheSearchBringsBackTheHeadings
covered | substack_reader_recovery_test › article cache isolates full custom domain, scheme and case-sensitive slug | SubstackHomeTest.theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale
covered | substack_reader_recovery_test › cache preserves the paid-preview boundary and rejects the wrong article | SubstackArticleTest.aParsedArticleIsReusedOnlyForTheSamePostDateAndBody
covered | substack_reader_recovery_test › the article cache keeps the 200 most recently read articles | SubstackHomeTest.theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale
covered | substack_reader_recovery_test › the article cache drops the least recently read first, by count and by size | SubstackHomeTest.theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale
covered | substack_reader_recovery_test › a cached copy is used only while the listed post keeps its date and title | SubstackArticleTest.aParsedArticleIsReusedOnlyForTheSamePostDateAndBody
covered | substack_reader_recovery_test › the newest article stays even when it alone is over the size limit | SubstackHomeTest.theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale
covered | substack_reader_recovery_test › malformed or unavailable optional cache never breaks reading | SubstackHomeTest.theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale

## test/substack_reader_style_test.dart
untested → ported | substack_reader_style_test › the defaults keep the look the reader had: Georgia in a 42rem column | Compose (Substack reader style) | Compose only
untested → ported | substack_reader_style_test › the chosen font and width reach the page; unknown values read as the defaults | Compose (Substack reader style) | Compose only

## test/substack_rss_test.dart
covered | substack_rss_test › reads channel metadata and items | SubstackClientTest.theRssFallbackPagesLocallyWithoutAskingAgainAndKeepsEachPostsMetadata
covered | substack_rss_test › recognises Substack RSS and rejects Ghost | SubstackCommentsTest.pagesOfAPublicationSubstacksOwnPagesAndOtherSitesAreNoPost
covered | substack_rss_test › unread / free / podcast | SubstackClientTest.theRssFallbackPagesLocallyWithoutAskingAgainAndKeepsEachPostsMetadata
covered | substack_rss_test › builds a note URL and publication | SubstackClientTest.notesComeFromAHostsReaderFeedWithTheirCursor

## test/substack_sanitize_test.dart
covered | substack_sanitize_test › event handlers do not survive, whatever element they are on | SubstackArticleTest.findSeesOnlySanitizedVisibleWritingWithAllWordsInAnyOrder
covered | substack_sanitize_test › a link whose destination is code loses the destination, not the text | SubstackArticleTest.findSeesOnlySanitizedVisibleWritingWithAllWordsInAnyOrder
covered | substack_sanitize_test › a document smuggled in as a URL is refused | SubstackArticleNavigationTest.theJumpCarriesAnUntrustedTargetAsDataNotCode
covered | substack_sanitize_test › scripts are still removed outright | SubstackArticleTest.findSeesOnlySanitizedVisibleWritingWithAllWordsInAnyOrder
covered | substack_sanitize_test › ordinary writing and its markup come through untouched | SubstackArticleTest.paragraphsKeepTheirStylesLinksAndFootnoteMarkers
covered | substack_sanitize_test › an embed keeps the host it points at | SubstackArticleTest.anEmbedIsHeldBehindAPlaceholderUntilTheReaderAsksForIt
covered | substack_sanitize_test › the spoken version never reads out a handler | SubstackArticleTest.oneParseGivesTheSameSpeechTextAsReadingTheBodyAlone

## test/substack_scrolled_past_test.dart
untested → ported | substack_scrolled_past_test › only cards fully above the top are reported, once a second, and only scrolling down | Compose (Substack feed) | Compose only
untested → ported | substack_scrolled_past_test › a fast fling past many cards still reports each one | Compose (Substack feed) | Compose only

## test/substack_search_upgrade_test.dart
untested → ported | substack_search_upgrade_test › late category list cannot replace a submitted search | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › late category results cannot overwrite a newer category | Compose (Substack discovery model) | Compose only
covered | substack_search_upgrade_test › late query failure cannot overwrite a newer query | SubstackFeedsTest.aNewerLoadSupersedesAnOlderOnesLateResults
covered | substack_search_upgrade_test › name results page, deduplicate, and stop repeated pages | SubstackFeedsTest.nameResultsPageDeduplicateAndStopAtARepeatedPage
covered | substack_search_upgrade_test › failed page retains prior results and retries the same page | SubstackFeedsTest.aFailedDiscoveryPageKeepsTheListAndAsksThatPageAgain
covered | substack_search_upgrade_test › category paging stays on its selected category | SubstackFeedsTest.aCategoryLeaderboardPagesWithinItsCategory
covered | substack_search_upgrade_test › punctuated publication names remain name searches | SubstackClientTest.findingPublicationsResolvesAddressesAndHandlesButSearchesNames
untested → ported | substack_search_upgrade_test › refresh failure retains the last readable category page | Compose (Substack discovery model) | Compose only
covered | substack_search_upgrade_test › publication URL resolves without a name search | SubstackClientTest.aShareLinkResolvesToItsPublication
covered | substack_search_upgrade_test › handle resolution can recover when search is unavailable | SubstackClientTest.anEmptyNameSearchIsAnAnswerAndAMultiWordNameIsNeverAHandle
covered | substack_search_upgrade_test › empty publication search stays distinct from a failed request | SubstackClientTest.publicationSearchAsksSubstackAndKeepsOnlyAddressablePublications
covered | substack_search_upgrade_test › pasted post resolves its publication before fetching article | SubstackClientTest.aPostTheJsonCannotGiveComesFromTheFeedElseIsNotFound
untested → ported | substack_search_upgrade_test › pasted article failure preserves publication preview | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › disposal prevents late publication results and further requests | Compose (Substack discovery model) | Compose only
covered | substack_search_upgrade_test › merges newest-first without confusing equal IDs on different publications | SubstackFeedsTest.aSearchMergesNewestFirstWithoutConfusingEqualIdsOnTwoPublications
covered | substack_search_upgrade_test › partial failure retains other articles and retries only the failed publication | SubstackFeedsTest.aPartialFailureKeepsTheOtherArticlesAndRetriesOnlyTheFailedPublication
covered | substack_search_upgrade_test › paging uses independent offsets and stops an unchanged result page | SubstackFeedsTest.pagingUsesEachPublicationsOwnOffsetAndStopsAnUnchangedPage
covered | substack_search_upgrade_test › failed refresh keeps earlier readable article results | SubstackFeedsTest.aFailedRefreshKeepsWhatWasShownAndAFreshOneReplacesIt
untested → ported | substack_search_upgrade_test › a changed followed publication host cannot commit an older search | Compose (Substack discovery model) | Compose only
covered | substack_search_upgrade_test › load more resets to the current followed set instead of paging a removed source | SubstackFeedsTest.followingAnotherSetDropsOnlyTheRemovedPublicationsPostsAndLateResults
covered | substack_search_upgrade_test › overlapping article pages update metadata without duplicating rows | SubstackFeedsTest.overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops
covered | substack_search_upgrade_test › only four publication searches run before the next batch starts | SubstackFeedsTest.onlyFourPublicationSearchesRunBeforeTheNextBatchStarts
untested → ported | substack_search_upgrade_test › changing query prevents the old search from starting further batches | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › failed replacement lookup cannot reuse the prior publication | Compose (Substack discovery model) | Compose only
covered | substack_search_upgrade_test › looking up an article never silently follows it | SubstackClientTest.aPostTheJsonCannotGiveComesFromTheFeedElseIsNotFound
untested → ported | substack_search_upgrade_test › new lookup supersedes a slow earlier preview | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › follow return state remains changed across subsequent previews | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › pending follow serializes user writes and blocks a new lookup | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › closing a preview prevents late lookup from starting article fetch | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › article failure leaves preview followable and independently retryable | Compose (Substack discovery model) | Compose only
untested → ported | substack_search_upgrade_test › compact enlarged-text $mode remains usable | Compose (Substack discovery) | Compose only

## test/substack_store_recovery_test.dart
covered | substack_store_recovery_test › successful empty results are cached and failed results can retry immediately | SubstackHomeTest.aRestoredFeedPaintsAtOnceAndReadsOnlyThePublicationsThatAreNoLongerFresh
covered | substack_store_recovery_test › failed publication pages retain their offset while other publications progress | SubstackFeedsTest.failedPublicationPagesKeepTheirOffsetWhileOthersProgress
covered | substack_store_recovery_test › refresh failure keeps readable rows and a page retry clears the refresh error | SubstackFeedsTest.aFailedRefreshKeepsWhatWasShownAndAFreshOneReplacesIt
covered | substack_store_recovery_test › a refresh reads every publication within the concurrency bound; paging passes rotate failures | SubstackFeedsTest.aRefreshStaysWithinTheConcurrencyBoundAndPagingRotatesFailures
covered | substack_store_recovery_test › a duplicate paging request is coalesced and refresh supersedes the old page | SubstackFeedsTest.overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops
untested → ported | substack_store_recovery_test › changing a publication host rejects old results even when its id is unchanged | Compose (Substack feed model) | Compose only
covered | substack_store_recovery_test › source identity normalizes host spelling and trailing slashes while preserving paths | SubstackCommentsTest.aCustomDomainIsOnlyClaimedWhenItIsFollowed
covered | substack_store_recovery_test › removing a followed publication removes only its cached rows | SubstackFeedsTest.followingAnotherSetDropsOnlyTheRemovedPublicationsPostsAndLateResults
covered | substack_store_recovery_test › overlapping pages update metadata and cyclic duplicate pages stop | SubstackFeedsTest.overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops
covered | substack_store_recovery_test › read changes repaint all articles and unread filtering stays local | SubstackFeedsTest.feedFiltersAreLocalAndTheSavedFilterAsksTheLibrary
covered | substack_store_recovery_test › the paid filter keeps paid posts; the saved filter follows the Saved library | SubstackFeedsTest.feedFiltersAreLocalAndTheSavedFilterAsksTheLibrary
untested → ported | substack_store_recovery_test › disposed feeds ignore pending requests | Compose (Substack feed model) | Compose only
covered | substack_store_recovery_test › pages keep the issuing host and refresh rotates discovery hosts | SubstackFeedsTest.notePagesKeepTheirIssuingHostAndARefreshAsksTheNextHost
covered | substack_store_recovery_test › failed pages keep visible notes and retry the same cursor | SubstackFeedsTest.aFailedNotesPageKeepsTheNotesAndRetriesTheSameCursor
covered | substack_store_recovery_test › cyclic cursors stop and overlapping notes replace counts without duplicated rows | SubstackFeedsTest.aCursorSeenBeforeEndsThePagingAndANoteSeenAgainReplacesItselfInPlace
covered | substack_store_recovery_test › successful empty results are fresh and a failed refresh remains retryable | SubstackHomeTest.aRestoredFeedPaintsAtOnceAndReadsOnlyThePublicationsThatAreNoLongerFresh
untested → ported | substack_store_recovery_test › source changes supersede pending discovery and disposal ignores late responses | Compose (Substack feed model) | Compose only
covered | substack_store_recovery_test › refresh keeps notes while offline and replaces a pending older page | SubstackFeedsTest.aFailedNotesPageKeepsTheNotesAndRetriesTheSameCursor
covered | substack_store_recovery_test › rapid read changes preserve ids and explicit unread survives reload | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
covered | substack_store_recovery_test › read ids are deduplicated before history capacity is applied | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing
untested → ported | substack_store_recovery_test › rapid likes use the latest committed state and repeated toggles cancel | Compose (Substack state) | Compose only
untested → ported | substack_store_recovery_test › rapid saved changes persist before disposal and later actions do nothing | Compose (Substack state) | Compose only
untested → ported | substack_store_recovery_test › a thrown preference write keeps state and allows the next action | Compose (Substack state) | Compose only
covered | substack_store_recovery_test › rapid pin changes preserve both publications and repeated toggles unpin | SubstackHomeTest.theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing

## test/substack_transport_test.dart
covered | substack_transport_test › Substack hosts share one rate bucket; other hosts have their own | SubstackClientTest.aRateLimitEndsTheFallbackChainAndSaysSo
covered | substack_transport_test › Retry-After is read as seconds or as an HTTP date, and nothing else | shared transport: BooruRequestGateTest.retryAfterInSecondsIsReadADateIsLeftToTheDoublingPause
covered | substack_transport_test › a 429 backs off with growing jittered waits, then reports the rate limit | SubstackClientTest.aRateLimitEndsTheFallbackChainAndSaysSo
untested → ported | substack_transport_test › a Retry-After answer is waited out, and the next answer is read | shared transport (services) | the retry waits are in the shared client
untested → ported | substack_transport_test › a long wait fails at once and holds back every host in the bucket | shared transport (services) | same
untested → ported | substack_transport_test › a server error is retried once after a short jittered pause | shared transport (services) | same
untested → ported | substack_transport_test › a success clears the strikes, so the next rate limit starts small again | shared transport (services) | same
untested → ported | substack_transport_test › no more than four requests are in flight per bucket | shared transport (services) | same
