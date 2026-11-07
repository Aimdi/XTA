# Parity ledger: reddit, instagram, tiktok (round 3)

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered (native does it, a native test checks it), untested→ported (native did it, test added now),
missing→implemented (native lacked it or differed, implemented and tested now), n/a (Flutter-only, reason given).

Native test paths are under `native/plugins/<id>/src/desktopTest/kotlin/com/aimdi/xtanative/`
(`networks/<id>/…` or `plugins/<id>/…`) and `native/ui/src/desktopTest/…`, `native/store/src/test/…`.

## test/discover_plugin_search_test.dart
covered | discover_plugin_search_test › a plugin chip with a query is plugin search, not X trends | ui trends/DiscoverScopes.kt | DiscoverScopesTest.theBodyIsXsTrendsOrThePluginsSearch
covered | discover_plugin_search_test › selecting Reddit with a query does not render trend rows | ui trends/DiscoverScopes.kt, TrendsScreen.kt PluginPane; reddit RedditSearch.kt (Posts/Subreddits/Users tabs) | DiscoverScopesTest: PLUGIN_SEARCH for a plugin scope with a query; TrendsScreen draws plugin.search.Results instead of TrendsBody
covered | discover_plugin_search_test › clearing Discover returns the selected plugin to its empty state | ui trends/TrendsViewModel.clear (committed = ""), DiscoverScopes.body | DiscoverScopesTest: blank query in a plugin scope → PLUGIN_EMPTY
covered | discover_plugin_search_test › empty Reddit Discover is not the X worldwide list | ui TrendsScreen.kt PluginPane (discover_plugin_empty "Search %s for …") | DiscoverScopesTest.theBodyIsXsTrendsOrThePluginsSearch

## test/plugin_feed_skeleton_test.dart
n/a | plugin_feed_skeleton_test › the skeleton never takes the outer scroll controller | ui common/Skeletons.kt | Flutter NestedScrollView/ScrollController plumbing; native FeedSkeleton is a plain non-scrolling Column
n/a | plugin_feed_skeleton_test › a grid skeleton paints tiles without a scroll position clash | ui common/Skeletons.kt | same Flutter scroll-position plumbing
untested→ported | plugin_feed_skeleton_test › the bones stop pulsing when animations are switched off | ui common/Skeletons.kt (SkeletonMotion, LocalReducedMotion ← disableAnimations) | new ui SkeletonMotionTest
missing→implemented | plugin_feed_skeleton_test › a loading Reddit feed shows bones, not a bare spinner | reddit plugins/reddit/RedditStates.kt RedditFeedBody, RedditHome.kt FollowingFeed, RedditListing.kt pagedState | native showed a spinner (LoadingRow); now FeedSkeleton; RedditStatesTest.aLoadingFeedShowsBonesNotABareSpinner

## test/reddit_about_search_test.dart
covered | reddit_about_search_test › what a community says about itself › the old.reddit sidebar yields name, counts and description | reddit networks/reddit/RedditOldReddit.kt sidebar | RedditOldRedditTest.theSidebarYieldsNameCountsAndDescription
covered | reddit_about_search_test › what a community says about itself › a page with no sidebar yields nothing rather than throwing | RedditOldReddit.kt | RedditOldRedditTest.theSidebarYieldsNameCountsAndDescription (second assert)
covered | reddit_about_search_test › what a community says about itself › with a client id, about.json is read over OAuth | RedditClient.about | RedditClientTest.withAClientIdAboutJsonIsReadFromTheApi
covered | reddit_about_search_test › what a community says about itself › without any credential, the sidebar is scraped instead | RedditClient.about | RedditClientTest.withoutAnyCredentialTheSidebarIsTheLastResort
covered | reddit_about_search_test › search order › the chosen order and the community scope ride the query | RedditClient.searchPosts | RedditClientTest.theOrderAndTheCommunityScopeRideTheSearchOnEachHost
covered | reddit_about_search_test › search order › OAuth search is used when a client id is present | RedditClient.searchPosts | RedditClientTest.aSearchWithAClientIdAsksTheApi

## test/reddit_archive_test.dart
covered | reddit_archive_test › archive ids cannot collide with an X snowflake | plugins/reddit/RedditShared.kt RedditArchive.id ("reddit:") | RedditSharedTest.aPostIsFiledInXtasRecordAndReadBack
covered | reddit_archive_test › the blob round-trips and is recognised as Reddit | RedditShared.kt RedditArchive.of/post, RedditPostJson | RedditSharedTest.aPostIsFiledInXtasRecordAndReadBack, anotherNetworksRecordIsNoneOfItsAndADamagedOneIsNothing; RedditPostJsonTest
covered | reddit_archive_test › parseSavedContent renders a Reddit blob, not an X tweet | store saved/SavedContent.kt | SavedContentTest.aRedditBlobIsReadAsRedditNotAsATweet; RedditSharedTest (matches)
covered | reddit_archive_test › an ordinary tweet blob still parses as a tweet | store saved/SavedContent.kt | SavedContentTest.anOrdinaryTweetIsAnXPostSearchedByTextAndAuthor
covered | reddit_archive_test › an upvote stores the same blob Archiv likes will render | RedditApp.kt (host.setLiked(RedditArchive.of(post))) | RedditSharedTest.aPostIsFiledInXtasRecordAndReadBack (the one record both use)

## test/reddit_auth_test.dart
covered | reddit_auth_test › authorizeUrl › asks for a code against the registered app | networks/reddit/RedditAuth.kt | RedditAuthTest.theLoginAsksForACodeAgainstTheRegisteredApp
covered | reddit_auth_test › authorizeUrl › asks for a lasting session, or the reader would log in again hourly | RedditAuth.kt | RedditAuthTest.theLoginAsksForACodeAgainstTheRegisteredApp (duration=permanent)
covered | reddit_auth_test › authorizeUrl › asks only for read scopes: XTA never posts, votes or subscribes | RedditAuth.kt | RedditAuthTest.theLoginAsksForReadScopesOnly
covered | reddit_auth_test › reading the redirect › takes the code Reddit sent back | RedditAuth.codeFrom | RedditAuthTest.theCodeRedditSentBackIsTakenOnlyWithTheStateItWasAskedWith
covered | reddit_auth_test › reading the redirect › refuses a code whose state does not match, so none can be injected | RedditAuth.codeFrom | same test
covered | reddit_auth_test › reading the redirect › ignores every other page the login walks through | RedditAuth.codeFrom | same test (forEach of other pages)
covered | reddit_auth_test › reading the redirect › a refusal is recognised, so declining closes quietly | RedditAuth.deniedIn | RedditAuthTest.aRefusalIsRecognisedSoDecliningClosesQuietly
covered | reddit_auth_test › exchanging the code › posts the documented grant with the client id as basic auth | RedditAuth.exchangeCode | RedditAuthTest.theCodeIsExchangedWithTheDocumentedGrantAndTheClientIdAsBasicAuth
covered | reddit_auth_test › exchanging the code › a refresh token buys an access token without asking the reader again | RedditAuth.accessToken | RedditAuthTest.aRefreshTokenBuysAnAccessTokenWithoutAskingTheReaderAgain
covered | reddit_auth_test › exchanging the code › signing in still needs a client id, and says so | RedditAuth.exchangeCode (require), RedditSettings.kt AccountRow opens the client-id dialog first | RedditAuthTest.signingInStillNeedsAClientId
covered | reddit_auth_test › exchanging the code › a rejected client id is reported as unauthorised, not as a broken response | RedditAuth.kt | RedditAuthTest.aRejectedClientIdOrARevokedSessionIsUnauthorised
covered | reddit_auth_test › exchanging the code › a response without the token is a bad response rather than a crash | RedditAuth.kt | RedditAuthTest.anAnswerWithoutTheTokenOrNotJsonIsUnreadableNotACrash
covered | reddit_auth_test › the signed-in read › a user token goes to the authenticated host and is used verbatim | RedditClient.listing | RedditClientTest.aUserTokenGoesToTheApiAsItIsWithoutMintingAnother

## test/reddit_auth_timeout_test.dart
covered | reddit_auth_timeout_test › stalled token headers is aborted and a retry succeeds | RedditAuth.kt withDeadline (15 s) | RedditAuthTest.aStalledTokenRequestIsANetworkFailureAndARetrySucceeds
covered | reddit_auth_timeout_test › stalled token body is aborted and a retry succeeds | RedditAuth.kt withDeadline covers the whole response read | RedditAuthTest.aStalledTokenRequestIsANetworkFailureAndARetrySucceeds
covered | reddit_auth_timeout_test › a stalled renewal after the token expires releases every waiter and keeps sign-in | networks/reddit RedditSignIn (shared mint) | RedditAuthTest.aStalledSharedRenewalLetsEverySurfaceFallBackAndRetriesLater
covered | reddit_auth_timeout_test › a standalone reader keeps its sign-in when token renewal times out | RedditSignIn.resolve (one path for every surface) | RedditAuthTest.aStalledSharedRenewalLetsEverySurfaceFallBackAndRetriesLater (signedOut false, retry mints)

## test/reddit_avatar_test.dart
covered | reddit_avatar_test › choosing an avatar › the same name always gets the same one | plugins/reddit/RedditFaces.kt | RedditRulesTest.aNameAlwaysHasTheSameFaceWhateverItsCase; RedditFacesTest
covered | reddit_avatar_test › choosing an avatar › case does not make a different person | RedditFaces.kt | RedditRulesTest.aNameAlwaysHasTheSameFaceWhateverItsCase
untested→ported | reddit_avatar_test › choosing an avatar › different names generally look different | RedditFaces.kt | RedditFacesTest.differentNamesGenerallyLookDifferent
untested→ported | reddit_avatar_test › choosing an avatar › every face and background gets used across enough names | RedditFaces.kt (backgrounds now visible to tests) | RedditFacesTest.everyFaceAndBackgroundGetsUsedAcrossEnoughNames
missing→implemented | reddit_avatar_test › choosing an avatar › an empty name still resolves rather than throwing | RedditFaces.kt | native crashed (the FNV offset basis overflowed Int → negative index); the hash is now kept as a Long as Dart does; RedditFacesTest.anEmptyNameStillResolves
untested→ported | reddit_avatar_test › the faces themselves › are all the same square grid | RedditFaces.kt | RedditFacesTest.theFacesAreAllAnEightByEightGridOfTheThreeKnownCellsAndDrawSomething
untested→ported | reddit_avatar_test › the faces themselves › use only the three known cells | RedditFaces.kt | same test
untested→ported | reddit_avatar_test › the faces themselves › every face actually draws something | RedditFaces.kt | same test
covered | reddit_avatar_test › ink › is dark on a light background and light on a dark one | RedditFaces.kt RedditFace.ink | RedditRulesTest.aNameAlwaysHasTheSameFaceWhateverItsCase; RedditFacesTest
untested→ported | reddit_avatar_test › ink › every background gets ink that contrasts with it | RedditFaces.kt | RedditFacesTest.theInkIsDarkOnALightBackgroundLightOnADarkOneAndAlwaysContrasts

## test/reddit_client_test.dart
covered | reddit_client_test › normaliseSubreddit › accepts the shapes people paste | RedditClient.kt normaliseSubreddit | RedditClientTest.aSubredditNameIsTakenOutOfWhateverWasPasted
covered | reddit_client_test › normaliseSubreddit › pulls the name out of a URL | normaliseSubreddit | RedditClientTest.aSubredditNameIsTakenOutOfWhateverWasPasted
covered | reddit_client_test › normaliseSubreddit › rejects what is not a subreddit | normaliseSubreddit | RedditClientTest.aSubredditNameIsTakenOutOfWhateverWasPasted
covered | reddit_client_test › authorisation › uses the installed_client grant with the client id as basic auth | RedditTransport.kt token | RedditClientTest.aClientIdUsesTheInstalledClientGrantWithTheIdAsBasicAuth
covered | reddit_client_test › authorisation › reuses the token for a second request instead of re-authorising | RedditTransport.kt | RedditClientTest.theTokenIsReusedUntilAMinuteBeforeItExpires
covered | reddit_client_test › authorisation › a token that is about to expire is not reused | RedditTransport.kt | RedditClientTest.theTokenIsReusedUntilAMinuteBeforeItExpires
covered | reddit_client_test › authorisation › no client id reads www.reddit.com first, with no token and no auth header | RedditClient.listing | RedditClientTest.withoutAnAccountWwwIsReadFirstWithNoTokenAndAsABrowser
covered | reddit_client_test › authorisation › when www refuses, old.reddit is scraped, with no token and no auth header | RedditClient.listing | RedditClientTest.whenWwwRefusesOldRedditIsScrapedAsABrowser
covered | reddit_client_test › authorisation › unreadable HTML falls back to the JSON endpoints rather than giving up | RedditClient.listing | RedditClientTest.unreadableHtmlFallsBackToTheJsonListings
covered | reddit_client_test › authorisation › the over-18 gate is answered with a cookie, not a login | RedditClient.kt over18 cookie | RedditClientTest.theOver18GateIsAnsweredWithACookieAndTheConsentKept
covered | reddit_client_test › authorisation › an anonymous reader that Reddit refuses is told what happened | RedditClient.kt | RedditClientTest.anAnonymousReaderRedditRefusesIsToldWhatHappened
covered | reddit_client_test › authorisation › a refusal from www is retried against old.reddit.com | RedditClient.kt public routes | RedditClientTest.aRefusalFromWwwIsAskedOfOldRedditAndTheJsonRoutes
covered | reddit_client_test › authorisation › a page that reads is not asked for twice | RedditClient.kt | RedditClientTest.aPageThatReadsIsNotAskedTwice
covered | reddit_client_test › authorisation › both public hosts refusing is reported as a block, not as missing setup | RedditClient.kt; plugins/reddit RedditTrouble.BLOCKED | RedditClientTest.anAnonymousReaderRedditRefusesIsToldWhatHappened (403, "both public hosts"); RedditRulesTest.aFailureIsNamedAsXtaNamesIt
covered | reddit_client_test › authorisation › both public hosts throttling is reported as rate limiting | RedditClient.kt; RedditTrouble.RATE_LIMITED | RedditClientTest.anAnonymousReaderRedditRefusesIsToldWhatHappened (429); RedditRulesTest.aFailureIsNamedAsXtaNamesIt
covered | reddit_client_test › authorisation › the public hosts are asked as a browser, not as an app | RedditClient.WEB_USER_AGENT | RedditClientTest.withoutAnAccountWwwIsReadFirstWithNoTokenAndAsABrowser, whenWwwRefusesOldRedditIsScrapedAsABrowser
covered | reddit_client_test › authorisation › the API keeps the agent Reddit asks its clients for | RedditClient.API_USER_AGENT | RedditClientTest.aClientIdUsesTheInstalledClientGrantWithTheIdAsBasicAuth
covered | reddit_client_test › authorisation › a 404 is not retried: the subreddit is simply not there | RedditClient.kt | RedditClientTest.aMissingCommunityIsNotAskedOfTheNextRoute
covered | reddit_client_test › authorisation › a client id still uses the authenticated host | RedditClient.kt | RedditClientTest.aClientIdUsesTheInstalledClientGrantWithTheIdAsBasicAuth (oauth.reddit.com, Bearer)
covered | reddit_client_test › authorisation › a rejected client id is reported as unauthorised | RedditClient.verify | RedditClientTest.aRejectedClientIdIsUnauthorised
covered | reddit_client_test › what Reddit served instead of a listing › each refusal is recognised for what it is | RedditOldReddit.kt page kinds | RedditOldRedditTest.eachPageRedditServesInsteadOfAListingIsRecognised; RedditWwwTest.whatWwwAnsweredWithIsRecognised
covered | reddit_client_test › what Reddit served instead of a listing › a community with nothing in it is empty, not broken | RedditClient.kt | RedditClientTest.aCommunityWithNothingInItIsEmptyNotBrokenAndNotAskedElsewhere
covered | reddit_client_test › what Reddit served instead of a listing › an empty community on old.reddit is empty too | RedditClient.kt | RedditClientTest.aCommunityWithNothingInItIsEmptyNotBrokenAndNotAskedElsewhere (old.reddit half)
covered | reddit_client_test › what Reddit served instead of a listing › a page whose shape changed is worth asking another route about | RedditClient.kt | RedditClientTest.aPageWhoseShapeChangedIsWorthAskingAnotherRouteAbout
covered | reddit_client_test › what Reddit served instead of a listing › a refusal is reported as itself and ends the search | RedditClient.kt | RedditClientTest.aPrivateBannedOrQuarantinedCommunityIsReportedAsItselfAndEndsTheSearch
covered | reddit_client_test › what Reddit served instead of a listing › a login page refuses only its host, and the next route is asked | RedditClient.kt | RedditClientTest.aLoginPageRefusesOnlyItsHostAndTheNextRouteIsAsked, aRedirectToTheLoginPageIsARefusalOfItsHost
covered | reddit_client_test › what Reddit served instead of a listing › a private community is refused with a 403 and still names the reason | RedditClient.kt | RedditClientTest.aPrivateCommunityRefusedWithA403StillNamesTheReason
covered | reddit_client_test › fetchSubreddit › asks for the sort, limit and raw_json, and reads the listing | RedditClient.listing | RedditClientTest.theApiIsAskedForTheSortLimitAndRawJson
covered | reddit_client_test › fetchSubreddit › default page size asks Reddit for fifty posts | RedditClient.PAGE_SIZE | RedditClientTest.aPageAsksForFiftyPostsByDefault
covered | reddit_client_test › fetchSubreddit › adds t= for authenticated top and controversial listings only | RedditClient.listing | RedditClientTest.theTimeWindowGoesWithTopAndControversialOnly
covered | reddit_client_test › fetchSubreddit › adds t= to the public site, HTML and JSON listing routes | RedditClient.listing | RedditClientTest.theTimeWindowRidesEveryAccountFreeRouteInItsSpelling
covered | reddit_client_test › fetchSubreddit › passes the cursor on for the next page | RedditClient.listing | RedditClientTest.theNextPageIsAskedForAfterTheCursor
covered | reddit_client_test › fetchSubreddit › a null after means the end of the listing | RedditJson.kt listing | RedditClientTest.theApiIsAskedForTheSortLimitAndRawJson (next = after), aCommunityWithNothingInIt… (null next)
covered | reddit_client_test › fetchSubreddit › skips children that are not posts, and posts without a title | RedditJson.kt | RedditPostsTest.childrenThatAreNotPostsAndPostsWithoutATitleAreSkipped
covered | reddit_client_test › fetchSubreddit › a self post carries its text and no thumbnail | RedditJson.kt | RedditPostsTest.aSelfPostCarriesItsTrimmedTextAndASentinelThumbnailIsNone
covered | reddit_client_test › fetchSubreddit › a spoiler post carries the flag | RedditJson.kt | RedditPostsTest.aSelfPostCarriesItsTrimmedTextAndASentinelThumbnailIsNone (spoiler); RedditOldRedditTest.pinnedOver18AndSpoilerPostsCarryTheirFlags
covered | reddit_client_test › fetchSubreddit › an unknown subreddit name never leaves the device | RedditClient.kt | RedditClientTest.aNameThatIsNoCommunityNeverLeavesTheDevice
covered | reddit_client_test › fetchSubreddit › each documented status maps to its own kind | RedditClient.kt; RedditTrouble | RedditClientTest.eachStatusOfTheApiIsItsOwnFailure; RedditRulesTest.aFailureIsNamedAsXtaNamesIt
covered | reddit_client_test › fetchSubreddit › a 401 on a listing drops the cached token so the next try re-authorises | RedditTransport.kt | RedditClientTest.a401DropsTheCachedTokenSoTheNextTryAuthorisesAgain
covered | reddit_client_test › fetchSubreddit › HTML instead of JSON is a bad response, not a crash | RedditClient.kt | RedditClientTest.htmlWhereJsonWasAskedForIsUnreadableNotACrash
covered | reddit_client_test › fetchSubreddit › an unreachable host is a network failure | RedditClient.kt | RedditClientTest.anUnreachableHostIsAFailedConnectionNotARefusal
covered | reddit_client_test › what a post has to show › a direct picture is shown at full width | networks/reddit RedditMedia.kt | RedditPostsTest.aDirectPictureIsShownAtFullWidthAndAPageIsNot
covered | reddit_client_test › what a post has to show › a page is not a picture, however tempting the thumbnail is | RedditMedia.kt | RedditPostsTest.aDirectPictureIsShownAtFullWidthAndAPageIsNot, aPageIsNotAPicture
covered | reddit_client_test › what a post has to show › placeholder titles stay hidden when the picture is already shown | RedditModels.kt showsTitle | RedditPostsTest.placeholderTitlesStayHiddenBesideThePictureTheyAnnounce
covered | reddit_client_test › what a post has to show › a title that is the picture URL stays hidden next to the picture | RedditModels.kt | RedditPostsTest.aTitleThatIsThePicturesUrlStaysHiddenBesideIt
covered | reddit_client_test › what a post has to show › a video is recognised so the card offers a play badge, not a dead image | RedditMedia.kt | RedditPostsTest.aVideoIsRecognisedAndABareVRedditLinkStillPlays
covered | reddit_client_test › what a post has to show › HTML listings without secure_media still resolve a playable DASH URL | RedditMedia.kt | RedditPostsTest.aBareVRedditLinkGetsItsDashManifest, aVideoIsRecognisedAndABareVRedditLinkStillPlays
covered | reddit_client_test › what a post has to show › JSON videoDashUrl wins over reconstructing from the link | RedditMedia.kt | RedditPostsTest.aVideoBringsItsStreamsAndShapeFromItselfOrTheOriginalItCrossposts
covered | reddit_client_test › what a post has to show › link cards use previewImage, else the listing thumbnail | RedditModels.kt cardPreviewImage | RedditPostsTest.linkCardsUseThePreviewElseTheListingThumbnail
covered | reddit_client_test › what a post has to show › NSFW hide mode removes over-18 posts | RedditRules.kt | RedditPostsTest.hidingNsfwRemovesOver18Posts; RedditRulesTest.hidingOver18PostsTakesThemOutOfTheListings
covered | reddit_client_test › what a post has to show › saved snapshots keep enough post data to reopen | plugins/reddit RedditPostJson.kt | RedditPostJsonTest.aPostComesBackAsItWasKept
covered | reddit_client_test › a gallery post › carries its pictures in the author's order, unescaped | RedditJson.kt gallery | RedditPostsTest.aGalleryCarriesItsPicturesInTheAuthorsOrderUnescaped
covered | reddit_client_test › a gallery post › a GIF in a gallery serves its gif, not a missing u | RedditJson.kt | RedditPostsTest.aGalleryPictureIsTheWidestCopyThatFitsAPhoneButAGifKeepsMoving
covered | reddit_client_test › a gallery post › files with no order are still worth showing | RedditJson.kt | RedditPostsTest.galleryFilesWithNoOrderAreStillShownAndNonsenseIsNothing
covered | reddit_client_test › a gallery post › a post that is not a gallery has no gallery | RedditJson.kt | RedditPostsTest.galleryFilesWithNoOrderAreStillShownAndNonsenseIsNothing
covered | reddit_client_test › a gallery post › metadata that no longer fits reads as nothing rather than throwing | RedditJson.kt | RedditPostsTest.galleryFilesWithNoOrderAreStillShownAndNonsenseIsNothing
covered | reddit_client_test › a post's preview › is read and unescaped, for the card of a video or an article | RedditJson.kt | RedditPostsTest.aPreviewIsReadAndUnescapedForTheCardButIsNotThePostsPicture
covered | reddit_client_test › a post's preview › a post without one has none | RedditJson.kt | RedditPostsTest.aPreviewIsReadAndUnescapedForTheCardButIsNotThePostsPicture
covered | reddit_client_test › fetchComments › a signed-in reader hits the authenticated host | RedditClient.thread | RedditClientTest.aSignedInReaderReadsAThreadFromTheApi
covered | reddit_client_test › fetchComments › a refused OAuth read falls back to the old site | RedditClient.thread | RedditClientTest.aRefusedApiThreadFallsBackToTheWebsite
covered | reddit_client_test › fetchComments › an empty scrape falls back to public JSON comments | RedditClient.thread | RedditClientTest.anEmptyScrapeFallsBackToThePublicJson
covered | reddit_client_test › fetchComments › preferPublic scrapes even when a token is sitting there | RedditClient.thread | RedditClientTest.theAccountFreeRouteIsKeptForAThreadEvenWithATokenThere
covered | reddit_client_test › www.reddit.com without an account › a thread is read from the piece that loads its comments | RedditClient.thread | RedditClientTest.aThreadIsReadFromThePieceThatLoadsItsComments
covered | reddit_client_test › www.reddit.com without an account › the chosen order rides the comments piece in its spelling | RedditClient.thread | RedditClientTest.theChosenOrderRidesTheCommentsPieceInItsSpelling
covered | reddit_client_test › www.reddit.com without an account › a comment's page asks for that comment, in either spelling | RedditLinks.wwwCommentsUrl | RedditClientTest.aCommentsPageAsksForThatCommentInEitherSpelling
covered | reddit_client_test › www.reddit.com without an account › a post that arrived bare is read from its embed card | RedditClient.kt | RedditClientTest.aPostThatArrivedBareIsReadFromItsEmbedCard
covered | reddit_client_test › www.reddit.com without an account › a comment's page is filled in from its post's card | RedditClient.kt | RedditClientTest.aCommentsPageIsFilledInFromItsPostsCard
covered | reddit_client_test › www.reddit.com without an account › a card Reddit refuses, or no answer at all, leaves the post as it was | RedditClient.kt | RedditClientTest.aCardRedditRefusesOrNoAnswerAtAllLeavesThePostAsItWas
covered | reddit_client_test › www.reddit.com without an account › with an account the API names the post, and the card stands in when it refuses | RedditClient.kt | RedditClientTest.withAnAccountTheApiNamesThePostAndTheCardStandsInWhenItRefuses
covered | reddit_client_test › www.reddit.com without an account › a post's own copy fills a bare one, without replacing what a feed post came with | RedditModels.kt | RedditPostsTest.aPostsOwnCopyFillsABareOneWithoutReplacingWhatAFeedPostCameWith
covered | reddit_client_test › www.reddit.com without an account › a big thread's held-back comments are asked for as the site asks: by POST, with the cursor and the CSRF token it set | RedditClient.kt | RedditClientTest.aBigThreadsHeldBackCommentsAreAskedForAsTheSiteAsks
covered | reddit_client_test › www.reddit.com without an account › held-back comments Reddit will not hand over are an error the thread can send the reader on from | RedditClient.kt | RedditClientTest.heldBackCommentsRedditWillNotHandOverAreAFailure; RedditThreadRulesTest.heldBackCommentsLoadHereUntilRedditRefusesThenOpenOnReddit
covered | reddit_client_test › www.reddit.com without an account › a profile post's comments live in the account's community | RedditLinks.wwwCommentsUrl | RedditClientTest.aCommentsPageAsksForThatCommentInEitherSpelling (u_spez); RedditWwwTest.aPageNamesItsCommunity
covered | reddit_client_test › www.reddit.com without an account › the browser check falls through to old.reddit | RedditClient.kt | RedditClientTest.theBrowserCheckFallsThroughToOldReddit
covered | reddit_client_test › www.reddit.com without an account › when nothing else serves a thread, the failure is www's own | RedditClient.kt | RedditClientTest.whenNothingElseServesAThreadTheFailureIsWwwsOwn
covered | reddit_client_test › www.reddit.com without an account › the next page asks for the cursor as the site writes it | RedditClient.kt | RedditClientTest.theNextPageIsAskedForAfterTheCursor (dDNfYWJjMTIz)
covered | reddit_client_test › www.reddit.com without an account › a search reads www.reddit.com's results, in its terms | RedditClient.searchPosts | RedditClientTest.aSearchReadsWwwsResultsInItsTerms
covered | reddit_client_test › www.reddit.com without an account › communities and people are searched by their own types | RedditClient.kt | RedditClientTest.communitiesAndPeopleAreSearchedByTheirOwnTypes
covered | reddit_client_test › www.reddit.com without an account › a profile is read from its piece, and pages on | RedditClient.userPosts | RedditClientTest.aProfileIsReadFromItsPieceAndPagesOn
covered | reddit_client_test › www.reddit.com without an account › what a community says about itself comes from the community search | RedditClient.about | RedditClientTest.whatACommunitySaysAboutItselfComesFromTheCommunitySearch

## test/reddit_comments_test.dart
covered | reddit_comments_test › JSON comment tree › the author's flair comes with the comment, an empty one not at all | networks/reddit/RedditJson.kt comments | RedditCommentsTest.theAuthorsFlairComesWithTheCommentAndAnEmptyOneNotAtAll
covered | reddit_comments_test › JSON comment tree › a comment counts the replies under it, not rows that stand for more | RedditJson.kt | RedditCommentsTest.aCommentCountsTheRepliesUnderItNotRowsThatStandForMore
covered | reddit_comments_test › JSON comment tree › an author's paragraphs survive | RedditCommentContent.kt | RedditCommentsTest.anAuthorsParagraphsSurvive
covered | reddit_comments_test › JSON comment tree › parses a nested t1 tree | RedditJson.kt | RedditCommentsTest.aNestedT1TreeIsRead
covered | reddit_comments_test › JSON comment tree › a more child becomes a stub with the held-back count | RedditJson.kt | RedditCommentsTest.aMoreChildBecomesAStubWithTheHeldBackCount
covered | reddit_comments_test › JSON comment tree › a nested more stub points at its parent comment | RedditJson.kt | RedditCommentsTest.aNestedMoreStubPointsAtItsParentComment
covered | reddit_comments_test › JSON comment tree › a top-level more stub points at the post itself | RedditJson.kt | RedditCommentsTest.aTopLevelMoreStubPointsAtThePostItself
covered | reddit_comments_test › JSON comment tree › more without id uses the first child id | RedditJson.kt | RedditCommentsTest.moreWithoutAnIdUsesItsFirstChildsId
covered | reddit_comments_test › JSON comment tree › empty replies string is no replies | RedditJson.kt | RedditCommentsTest.anEmptyRepliesStringIsNoReplies
covered | reddit_comments_test › JSON comment tree › the submitter flag and media tokens are read | RedditJson.kt | RedditCommentsTest.theSubmitterFlagAndMediaTokensAreRead
covered | reddit_comments_test › JSON comment tree › a bare image URL becomes media and leaves no body | RedditCommentContent.kt | RedditCommentsTest.aBareImageUrlBecomesMediaAndLeavesNoBody
covered | reddit_comments_test › JSON comment tree › a >image> leftover next to a picture is dropped | RedditCommentContent.kt | RedditCommentsTest.aPlaceholderBesideAPictureIsDroppedAndACaptionKeepsItsWords
covered | reddit_comments_test › JSON comment tree › a caption next to a picture keeps the words, not the URL | RedditCommentContent.kt | RedditCommentsTest.aPlaceholderBesideAPictureIsDroppedAndACaptionKeepsItsWords
covered | reddit_comments_test › JSON comment tree › markdown links to pages stay in the body so they can be opened | RedditCommentContent.kt | RedditCommentsTest.markdownLinksToPagesStayInTheBodySoTheyCanBeOpened
covered | reddit_comments_test › JSON comment tree › reading a thread › takes the author, body, score and time | networks/reddit/RedditOldReddit.kt RedditOldComments | RedditCommentsTest.aScrapedCommentHasItsAuthorBodyScoreAndTime
covered | reddit_comments_test › JSON comment tree › reading a thread › a picture comment shows the picture, not the URL that made it | RedditOldComments, RedditCommentContent.kt | RedditCommentsTest.aPictureCommentShowsThePictureNotTheUrlThatMadeItAndIsKept
covered | reddit_comments_test › JSON comment tree › reading a thread › a picture-only comment is kept rather than skipped as empty | RedditOldComments | RedditCommentsTest.aPictureCommentShowsThePictureNotTheUrlThatMadeItAndIsKept
covered | reddit_comments_test › JSON comment tree › reading a thread › paragraphs, line breaks, lists and code keep their shape | RedditOldComments | RedditCommentsTest.paragraphsLineBreaksListsAndCodeKeepTheirShape, aNumberedListCountsFromItsStart
covered | reddit_comments_test › JSON comment tree › reading a thread › words around a link survive | RedditOldComments | RedditCommentsTest.wordsAroundAPictureLinkSurvive
covered | reddit_comments_test › JSON comment tree › reading a thread › a labelled page link is kept as markdown, not dumped as the URL | RedditOldComments | RedditCommentsTest.aLabelledPageLinkIsKeptAsMarkdownAndABareOneAsText
covered | reddit_comments_test › JSON comment tree › reading a thread › a link to a page is left as text | RedditOldComments | RedditCommentsTest.aLabelledPageLinkIsKeptAsMarkdownAndABareOneAsText
covered | reddit_comments_test › JSON comment tree › reading a thread › the same picture linked twice is shown once | RedditCommentContent.kt | RedditCommentsTest.theSamePictureTwiceIsShownOnce
covered | reddit_comments_test › JSON comment tree › reading a thread › a Reddit GIF token becomes the GIF, and never shows as raw text | RedditCommentContent.kt | RedditCommentsTest.aRedditGifTokenBecomesTheGifAndNeverShowsAsText
covered | reddit_comments_test › JSON comment tree › reading a thread › a token beside words takes only itself out of the text | RedditCommentContent.kt | RedditCommentsTest.aRedditGifTokenBecomesTheGifAndNeverShowsAsText ("this exactly")
covered | reddit_comments_test › JSON comment tree › reading a thread › a >image> leftover next to an inlined picture is dropped | RedditCommentContent.kt | RedditCommentsTest.anInlinedPictureIsPickedUpAndItsPlaceholderDropped
covered | reddit_comments_test › JSON comment tree › reading a thread › an inlined img is picked up as well as a link | RedditOldComments | RedditCommentsTest.anInlinedPictureIsPickedUpAndItsPlaceholderDropped
covered | reddit_comments_test › JSON comment tree › reading a thread › replies hang off the comment they answer | RedditOldComments | RedditCommentsTest.repliesHangOffTheCommentTheyAnswerAsDeepAsThePageGoes
covered | reddit_comments_test › JSON comment tree › reading a thread › nesting goes as deep as the page does | RedditOldComments | RedditCommentsTest.repliesHangOffTheCommentTheyAnswerAsDeepAsThePageGoes
covered | reddit_comments_test › JSON comment tree › reading a thread › a comment does not swallow its replies' text | RedditOldComments | RedditCommentsTest.repliesHangOffTheCommentTheyAnswerAsDeepAsThePageGoes
covered | reddit_comments_test › JSON comment tree › reading a thread › siblings keep their order | RedditOldComments | RedditCommentsTest.repliesHangOffTheCommentTheyAnswerAsDeepAsThePageGoes (sibling order)
covered | reddit_comments_test › JSON comment tree › reading a thread › a hidden score is absent rather than zero | RedditOldComments | RedditCommentsTest.aHiddenScoreIsAbsentRatherThanZero
covered | reddit_comments_test › JSON comment tree › reading a thread › the submitter is marked | RedditOldComments | RedditCommentsTest.theSubmitterAndTheAuthorsFlairAreReadOffTheTagline
covered | reddit_comments_test › JSON comment tree › reading a thread › the author's flair is read off the tagline | RedditOldComments | RedditCommentsTest.theSubmitterAndTheAuthorsFlairAreReadOffTheTagline
covered | reddit_comments_test › JSON comment tree › rows that are not comments › a "load more" control is skipped | RedditOldComments | RedditCommentsTest.rowsThatAreNotCommentsAreSkipped
covered | reddit_comments_test › JSON comment tree › rows that are not comments › a deleted comment with no body is left out | RedditOldComments | RedditCommentsTest.rowsThatAreNotCommentsAreSkipped
covered | reddit_comments_test › JSON comment tree › rows that are not comments › a page with no comment area yields none rather than throwing | RedditOldComments | RedditCommentsTest.rowsThatAreNotCommentsAreSkipped
covered | reddit_comments_test › JSON comment tree › flattening for display › depth-first, with the depth carried alongside | networks/reddit flattenComments | RedditCommentsTest.flatteningIsDepthFirstWithTheDepthAlongside
covered | reddit_comments_test › JSON comment tree › flattening for display › nothing to flatten is an empty list | flattenComments | RedditCommentsTest.flatteningIsDepthFirstWithTheDepthAlongside
covered | reddit_comments_test › JSON comment tree › the post body on a thread page › is read when the listing did not carry it | RedditOldComments.selfText | RedditCommentsTest.thePostsOwnWordsAreReadOffItsThreadPage
covered | reddit_comments_test › JSON comment tree › the post body on a thread page › a link post has none | RedditOldComments.selfText | RedditCommentsTest.thePostsOwnWordsAreReadOffItsThreadPage
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › the outbound link is read off the thing row | RedditOldComments.postMedia | RedditCommentsTest.thePostsOutboundLinkIsReadOffItsRowButARelativeOneIsNoLink
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › a relative link — a self post pointing at itself — is not a link | RedditOldComments.postMedia | RedditCommentsTest.thePostsOutboundLinkIsReadOffItsRowButARelativeOneIsNoLink
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › an expanded gallery leaves its files on the page, in order and unescaped | RedditOldComments.postMedia | RedditCommentsTest.anExpandedGalleryLeavesItsFilesOnThePageInOrderAndUnescaped
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › only Reddit-hosted files count; tracking pixels and avatars do not | RedditOldComments.postMedia | RedditCommentsTest.onlyRedditHostedFilesCountAndTheSameFileIsOneFile
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › the same file twice is one file | RedditOldComments.postMedia | RedditCommentsTest.onlyRedditHostedFilesCountAndTheSameFileIsOneFile
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › low and high quality of the same picture collapse to the better one | RedditOldComments.postMedia | RedditCommentsTest.copiesOfOnePictureCollapseToTheBestOne
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › the same picture at two widths is one picture, preferring the larger | RedditOldComments.postMedia | RedditCommentsTest.copiesOfOnePictureCollapseToTheBestOne
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › a preview and an i.redd.it of the same file collapse to i.redd.it | RedditOldComments.postMedia | RedditCommentsTest.copiesOfOnePictureCollapseToTheBestOne
covered | reddit_comments_test › JSON comment tree › what the post page says the post points at › a page with no post is nothing, not a throw | RedditOldComments.postMedia | RedditCommentsTest.copiesOfOnePictureCollapseToTheBestOne (empty page)
covered | reddit_comments_test › JSON comment tree › what a page holds back › a load-more control becomes a stub pointing at its parent | RedditOldComments | RedditCommentsTest.aLoadMoreControlBecomesAStubPointingAtItsParent
covered | reddit_comments_test › JSON comment tree › what a page holds back › a deep-thread continuation carries its own target | RedditOldComments | RedditCommentsTest.aDeepThreadContinuationCarriesItsOwnTarget
covered | reddit_comments_test › JSON comment tree › what a page holds back › a control with nowhere to go is dropped rather than dead on screen | RedditOldComments | RedditCommentsTest.aControlWithNowhereToGoIsDroppedRatherThanDeadOnScreen
covered | reddit_comments_test › JSON comment tree › comments loaded in place › a repeated top-level thread is dropped with its replies, new ones kept | networks/reddit withoutShownThreads | RedditCommentsTest.aRepeatedTopLevelThreadLoadedInPlaceIsDroppedWithItsReplies
covered | reddit_comments_test › JSON comment tree › folding a thread › nothing collapsed shows everything, hiding nothing | plugins/reddit/RedditThreadRules.kt fold | RedditThreadRulesTest.aFoldedCommentKeepsItsRowAndHidesEverythingUnderIt
covered | reddit_comments_test › JSON comment tree › folding a thread › a collapsed comment keeps its row, its subtree does not | RedditThreadRules.kt | RedditThreadRulesTest.aFoldedCommentKeepsItsRowAndHidesEverythingUnderIt
untested→ported | reddit_comments_test › JSON comment tree › folding a thread › collapsing a leaf hides nothing but still marks the row | RedditThreadRules.kt visible (the row stays; RedditComments draws it folded from the collapsed set) | RedditThreadRulesTest.collapsingALeafHidesNothingButKeepsItsRow
covered | reddit_comments_test › JSON comment tree › top-level load-more stubs › a root morechildren row survives, pointing at the post page | RedditOldComments | RedditCommentsTest.aRootLoadMoreRowSurvivesPointingAtThePostPage

## test/reddit_community_icon_test.dart
covered | reddit_community_icon_test › redditCommunityIconUrl › prefers community_icon, unescapes, and strips a signed thumb | networks/reddit/RedditMedia.kt communityIconUrl | RedditPostsTest.aCommunityIconIsUnescapedAndItsSignedResizingStripped
covered | reddit_community_icon_test › redditCommunityIconUrl › falls back to icon_img when community_icon is empty | RedditMedia.communityIconUrl | RedditPostsTest.aCommunityIconIsUnescapedAndItsSignedResizingStripped
covered | reddit_community_icon_test › redditCommunityIconUrl › rejects the site snoo and placeholder names | RedditMedia.communityIconUrl | RedditPostsTest.aCommunityIconIsUnescapedAndItsSignedResizingStripped
covered | reddit_community_icon_test › fetchSubredditIcon › reads community_icon from public about.json | RedditClient.icon | RedditClientTest.theAccountFreeAboutJsonIsReadAndOldRedditIsItsSecondDoor, whatACommunitySaysAboutItselfComesFromTheCommunitySearch
covered | reddit_community_icon_test › fetchSubredditIcon › oauth about.json is used when a client id is set | RedditClient.icon | RedditClientTest.anIconWithAClientIdComesFromTheApi
covered | reddit_community_icon_test › fetchSubredditIcon › guest about.json falls back to old.reddit when www is blocked | RedditClient.icon | RedditClientTest.theAccountFreeAboutJsonIsReadAndOldRedditIsItsSecondDoor
untested→ported | reddit_community_icon_test › redditAvatarUrlFromAbout › null while loading so a disk cache can still paint | plugins/reddit/RedditRules.kt RedditAboutIcon, RedditSheets.kt | RedditRulesTest.aboutsPictureIsNothingWhileReadEmptyAfterAMissElseThePicture
untested→ported | reddit_community_icon_test › redditAvatarUrlFromAbout › empty string after a miss so lookup is not repeated | RedditAboutIcon.of | RedditRulesTest.aboutsPictureIsNothingWhileReadEmptyAfterAMissElseThePicture
untested→ported | reddit_community_icon_test › redditAvatarUrlFromAbout › the community icon when about.json had one | RedditAboutIcon.of | RedditRulesTest.aboutsPictureIsNothingWhileReadEmptyAfterAMissElseThePicture

## test/reddit_feed_actions_test.dart
untested→ported | reddit_feed_actions_test › full Reddit actions and back fit a 320dp screen; saved stays reachable | plugins/reddit/RedditHome.kt HomeBar (rail scrolls sideways, then the actions), RedditRules.kt RedditHomeChrome; Saved in the menu | RedditRulesTest.theMenuOffersSavedCommunitiesTheRoutesAndRedditsSettingsNotASignIn (back is the host's PluginScaffold; the 320dp fit is Compose layout)
untested→ported | reddit_feed_actions_test › Reddit chrome leads with search, then sort and one menu, not a plus | RedditRules.kt RedditHomeChrome.actions, RedditHome.kt | RedditRulesTest.theBarLeadsWithSearchThenTheOrderThenOneMenuAndNoPlus
untested→ported | reddit_feed_actions_test › overflow offers saved, communities and settings, not Reddit sign-in | RedditRules.kt RedditHomeChrome.menu, RedditHome.kt HomeMenu (labels "Saved", "Subscriptions", "Reddit · Settings", "Best available", "Without an account") | RedditRulesTest.theMenuOffersSavedCommunitiesTheRoutesAndRedditsSettingsNotASignIn
untested→ported | reddit_feed_actions_test › choosing how Reddit is read stores it and refetches the visible feed | RedditRules.kt RedditHomeChrome.choosing/checked, RedditApp.change (stores, then reread() refetches the feed) | RedditRulesTest.choosingHowRedditIsReadStoresItAndTheChosenRouteIsChecked
covered | reddit_feed_actions_test › the list sheet opens a community when its row is tapped | RedditSheets.kt CommunitiesSheet/CommunityTile (row → app.openSubreddit; "Your communities", "Add subreddit", avatars, people glyph) | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity (opening a followed community); avatar URL rule RedditRulesTest.aboutsPictureIsNothingWhileReadEmptyAfterAMissElseThePicture

## test/reddit_gallery_fetch_test.dart
covered | reddit_gallery_fetch_test › fetching the pictures a scraped gallery post is missing › reads them from the post's own public JSON, in order | RedditClient.galleryImages | RedditClientTest.aScrapedGallerysPicturesAreReadFromThePostsOwnJsonInOrder
covered | reddit_gallery_fetch_test › fetching the pictures a scraped gallery post is missing › a post that turns out not to be a gallery yields nothing | RedditClient.galleryImages | RedditClientTest.aGalleryThatIsNotOneOrIsRefusedYieldsNothingRatherThanThrowing
covered | reddit_gallery_fetch_test › fetching the pictures a scraped gallery post is missing › a refusal yields nothing rather than throwing | RedditClient.galleryImages | RedditClientTest.aGalleryThatIsNotOneOrIsRefusedYieldsNothingRatherThanThrowing
covered | reddit_gallery_fetch_test › fetching the pictures a scraped gallery post is missing › a body that is not JSON yields nothing rather than throwing | RedditClient.galleryImages | RedditClientTest.aGalleryThatIsNotOneOrIsRefusedYieldsNothingRatherThanThrowing

## test/reddit_gallery_link_test.dart
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › a gallery link is one | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › old.reddit spells it the same way | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › a trailing slash makes no difference | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › an ordinary post is not one | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › a direct image is not one | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › somewhere else entirely is not one, whatever its path says | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised
covered | reddit_gallery_link_test › recognising a gallery a listing did not carry the pictures for › nothing is not one | RedditMedia.isGalleryUrl | RedditPostsTest.aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised

## test/reddit_gallery_loader_test.dart
covered | reddit_gallery_loader_test › remembering a gallery answer › a successful answer is fetched once and reused | networks/reddit/RedditFeeds.kt RedditGalleryLoader | RedditFeedsTest.aGalleryAnswerIsFetchedOnceAndReused
covered | reddit_gallery_loader_test › remembering a gallery answer › a refusal is asked again next time, and can recover | RedditGalleryLoader | RedditFeedsTest.anEmptyAnswerIsAskedAgainAndCanRecoverButNotForever
covered | reddit_gallery_loader_test › remembering a gallery answer › a genuine non-gallery is not hammered forever either | RedditGalleryLoader | RedditFeedsTest.anEmptyAnswerIsAskedAgainAndCanRecoverButNotForever (EMPTY_RETRIES)
covered | reddit_gallery_loader_test › remembering a gallery answer › what it holds is bounded, oldest first out | RedditGalleryLoader | RedditFeedsTest.whatTheLoaderHoldsIsBoundedOldestFirstOut
covered | reddit_gallery_loader_test › remembering a gallery answer › a fetch that throws does not pin an errored future forever | RedditGalleryLoader | RedditFeedsTest.aFetchThatThrowsDoesNotPinItsFailureForeverHoweverFastItFails
n/a | reddit_gallery_loader_test › the gallery card in a recycled list › a card reused for a different post does not keep the old pictures | plugins/reddit/RedditMediaViews.kt (produceState keyed on the post's permalink, starting empty) | Flutter element/State recycling; a Compose produceState keyed on the post has no state to carry to another post

## test/reddit_home_chrome_test.dart
covered | reddit_home_chrome_test › a narrow subreddit reader does not falsely select Following in its section menu | plugins/reddit/RedditRules.kt RedditHomeSource.railSelected, RedditHome.kt (rail scrolls sideways) | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity (a community open: no rail selected)
covered | reddit_home_chrome_test › Reddit full client identifies itself and labels its feed sections | RedditHome.kt Rail (Following/Popular/All with their glyphs), RedditHomeSource.selectingMode | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity (selectingMode → POPULAR)
n/a | reddit_home_chrome_test › embedded Reddit chrome skips a second SafeArea | ui plugins PluginScaffold | Flutter SafeArea nesting; Compose insets come from the host frame's padding
covered | reddit_home_chrome_test › a followed community deselects Home/Popular/All | RedditHomeSource.railSelected | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity

## test/reddit_home_empty_test.dart
untested→ported | reddit_home_empty_test › RedditFeedStore › zero follows does not fetch or enter loading | plugins/reddit/RedditStates.kt RedditFeed.refresh (no names: answered, nothing read) | RedditStatesTest.zeroFollowsReadNothingNeverLoadAndARemountRefreshStaysANoOp
untested→ported | reddit_home_empty_test › RedditFeedStore › a remount refresh with zero follows stays a no-op | RedditFeed.refresh | RedditStatesTest.zeroFollowsReadNothingNeverLoadAndARemountRefreshStaysANoOp
covered | reddit_home_empty_test › Following with zero subreddits paints empty and does not attach the outer controller | RedditStates.kt RedditFeedBody.NOTHING_FOLLOWED → RedditHome.kt AddFirst ("Füge ein Subreddit hinzu, um zu lesen", "Subreddit hinzufügen") | RedditStatesTest.aLoadingFeedShowsBonesNotABareSpinner (NOTHING_FOLLOWED); outer-controller part is Flutter plumbing
covered | reddit_home_empty_test › tapping add opens a sheet and cancel leaves the empty pane | RedditHome.kt AddFirst → AddSubredditDialog; cancel only closes it | RedditStatesTest.aLoadingFeedShowsBonesNotABareSpinner (the empty body holds while nothing is followed); the dialog itself is Compose-only
covered | reddit_home_empty_test › switching off Reddit and back does not refetch or freeze | RedditFeed (fresh feed shown again without reading; no names no read) | RedditStatesTest.theFeedIsReadOnceAndShownAgainWhileFresh, zeroFollowsReadNothingNeverLoadAndARemountRefreshStaysANoOp
untested→ported | reddit_home_empty_test › an empty chip strip is not a list | RedditRules.kt RedditHomeSource.showsStrip, RedditHome.kt | RedditStatesTest.anEmptyStripOfCommunitiesIsNotShown
n/a | reddit_home_empty_test › EmptyPane under the home strip does not reuse the outer controller | RedditHome.kt | Flutter ScrollController attachment under a NestedScrollView
