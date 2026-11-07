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
