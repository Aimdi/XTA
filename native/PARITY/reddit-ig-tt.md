# Parity ledger: reddit, instagram, tiktok (round 3)

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered (native does it, a native test checks it), untested→ported (native did it, test added now),
missing→implemented (native lacked it or differed, implemented and tested now), implemented (UI-only) (native draws it, only a Compose UI test could check it and native has no Compose test harness), n/a (Flutter-only, reason given).

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
covered | reddit_auth_test › reading the redirect › refuses a code whose state does not match, so none can be injected | RedditAuth.codeFrom | RedditAuthTest.theCodeRedditSentBackIsTakenOnlyWithTheStateItWasAskedWith
covered | reddit_auth_test › reading the redirect › ignores every other page the login walks through | RedditAuth.codeFrom | RedditAuthTest.theCodeRedditSentBackIsTakenOnlyWithTheStateItWasAskedWith (forEach of other pages)
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
untested→ported | reddit_avatar_test › the faces themselves › use only the three known cells | RedditFaces.kt | RedditFacesTest.theFacesAreAllAnEightByEightGridOfTheThreeKnownCellsAndDrawSomething
untested→ported | reddit_avatar_test › the faces themselves › every face actually draws something | RedditFaces.kt | RedditFacesTest.theFacesAreAllAnEightByEightGridOfTheThreeKnownCellsAndDrawSomething
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

## test/reddit_html_test.dart
covered | reddit_html_test › reading a listing › takes a link post out of its data attributes | networks/reddit/RedditOldReddit.kt RedditOldListing | RedditOldRedditTest.aLinkPostIsTakenOutOfItsDataAttributes
covered | reddit_html_test › reading a listing › a protocol-relative thumbnail is given a scheme | RedditOldListing | RedditOldRedditTest.aProtocolRelativeThumbnailIsGivenAScheme
covered | reddit_html_test › reading a listing › a self post is recognised by its domain and keeps its body | RedditOldListing | RedditOldRedditTest.aSelfPostIsRecognisedByItsDomainAndKeepsItsBody
covered | reddit_html_test › reading a listing › several posts keep their order | RedditOldListing | RedditOldRedditTest.postsKeepTheirOrderAndAnAdvertIsLeftOut
covered | reddit_html_test › reading a listing › an advert is left out | RedditOldListing | RedditOldRedditTest.postsKeepTheirOrderAndAnAdvertIsLeftOut
covered | reddit_html_test › reading a listing › a pinned post is marked | RedditOldListing | RedditOldRedditTest.pinnedOver18AndSpoilerPostsCarryTheirFlags
covered | reddit_html_test › reading a listing › the domain comes through so the card can say where a link leads | RedditOldListing | RedditOldRedditTest.aLinkPostIsTakenOutOfItsDataAttributes (domain)
covered | reddit_html_test › reading a listing › flair is read, preferring the full label over the abbreviated one | RedditOldListing | RedditOldRedditTest.flairPrefersTheFullLabelOverTheAbbreviatedOne
covered | reddit_html_test › reading a listing › an over-18 post carries the flag | RedditOldListing | RedditOldRedditTest.pinnedOver18AndSpoilerPostsCarryTheirFlags
covered | reddit_html_test › reading a listing › a spoiler post carries the flag | RedditOldListing | RedditOldRedditTest.pinnedOver18AndSpoilerPostsCarryTheirFlags
covered | reddit_html_test › pagination › takes the after cursor off the next link | RedditOldListing | RedditOldRedditTest.theAfterCursorComesOffTheNextLinkAndTheLastPageHasNone, aRelNextLinkOutsideTheButtonStillPagesOn
covered | reddit_html_test › pagination › the last page has no cursor | RedditOldListing | RedditOldRedditTest.theAfterCursorComesOffTheNextLinkAndTheLastPageHasNone
covered | reddit_html_test › markup that cannot be read › a post with no title or permalink is skipped, not guessed at | RedditOldListing | RedditOldRedditTest.markupThatCannotBeReadIsSkippedRatherThanFatal
covered | reddit_html_test › markup that cannot be read › a page with no posts at all yields none rather than throwing | RedditOldListing | RedditOldRedditTest.markupThatCannotBeReadIsSkippedRatherThanFatal
covered | reddit_html_test › markup that cannot be read › an unreadable score is zero rather than fatal | RedditOldListing | RedditOldRedditTest.markupThatCannotBeReadIsSkippedRatherThanFatal (score 0)
covered | reddit_html_test › a subreddit picture › comes from the header image the old site does have | RedditOldListing.subredditIcon | RedditOldRedditTest.aCommunitysPictureComesFromItsHeaderImageNotTheSitesLogo
covered | reddit_html_test › a subreddit picture › falls back to og:image when it is community artwork | RedditOldListing.subredditIcon | RedditOldRedditTest.aCommunitysPictureFallsBackToItsOgImageWhenThatIsCommunityArtwork
covered | reddit_html_test › a subreddit picture › a subreddit with no artwork has none to find | RedditOldListing.subredditIcon | RedditOldRedditTest.aCommunitysPictureFallsBackToItsOgImageWhenThatIsCommunityArtwork (null)
covered | reddit_html_test › the over-18 gate › is recognised so it can be answered with a cookie | RedditOldListing.isOver18Gate | RedditOldRedditTest.theOver18GateIsRecognisedSoItCanBeAnsweredWithACookie
covered | reddit_html_test › the over-18 gate › an ordinary listing is not the gate | RedditOldListing.isOver18Gate | RedditOldRedditTest.theOver18GateIsRecognisedSoItCanBeAnsweredWithACookie

## test/reddit_listing_cache_test.dart
covered | reddit_listing_cache_test › RedditListingCache › a listing fetched for one surface is handed to the next | networks/reddit/RedditFeeds.kt RedditListingCache | RedditFeedsTest.aListingFetchedForOneSurfaceIsHandedToTheNext
covered | reddit_listing_cache_test › RedditListingCache › surfaces mounting at the same moment join one request | RedditListingCache | RedditFeedsTest.surfacesAskingAtTheSameMomentJoinOneRequest
covered | reddit_listing_cache_test › RedditListingCache › a sort of its own is a listing of its own | RedditListingCache | RedditFeedsTest.aSortOrAWindowOfItsOwnIsAListingOfItsOwn
covered | reddit_listing_cache_test › RedditListingCache › a time filter of its own is a listing of its own | RedditListingCache | RedditFeedsTest.aSortOrAWindowOfItsOwnIsAListingOfItsOwn
covered | reddit_listing_cache_test › RedditListingCache › a forced refresh goes past it, or the reader can never get new posts | RedditListingCache | RedditFeedsTest.aForcedRefreshGoesPastItAndIsWhatTheNextReaderIsHanded
covered | reddit_listing_cache_test › RedditListingCache › it is fetched again once the entry is old | RedditListingCache | RedditFeedsTest.anOldEntryIsFetchedAgainAndReplacedNotAccumulatedHoweverFastItSettled
covered | reddit_listing_cache_test › RedditListingCache › a subreddit that failed is left alone briefly, then asked again | RedditListingCache | RedditFeedsTest.aFailureIsHandedToTheNextSurfaceThenForgottenSoonerThanASuccess
covered | reddit_listing_cache_test › RedditListingCache › a failure is forgotten sooner than a success | RedditListingCache | RedditFeedsTest.aFailureIsHandedToTheNextSurfaceThenForgottenSoonerThanASuccess
covered | reddit_listing_cache_test › RedditListingCache › it stops growing, dropping the least recently used | RedditListingCache | RedditFeedsTest.itStopsGrowingDroppingTheLeastRecentlyUsed
covered | reddit_listing_cache_test › RedditListingCache › clear empties it | RedditListingCache.clear | RedditFeedsTest.itStopsGrowingDroppingTheLeastRecentlyUsed (clear)
covered | reddit_listing_cache_test › RedditPostSource › Following, For you and the Reddit tab pay for one fetch between them | RedditFeeds.kt RedditFollowingFeed | RedditFeedsTest.everySurfaceReadingTheSameCommunitiesPaysForOneFetchBetweenThem
covered | reddit_listing_cache_test › RedditPostSource › a group that shares a subreddit with the tab does not fetch it again | RedditFollowingFeed | RedditFeedsTest.aGroupSharingACommunityWithTheTabDoesNotFetchItAgainAndOneNamedTwiceIsReadOnce
covered | reddit_listing_cache_test › RedditPostSource › a forced read reaches Reddit again | RedditFollowingFeed | RedditFeedsTest.aForcedReadReachesRedditAgain
covered | reddit_listing_cache_test › RedditPostSource › one bad subreddit does not empty the feed | RedditFollowingFeed | RedditFeedsTest.oneBadCommunityDoesNotEmptyTheFeedAndIsNotAskedAgainByTheNextSurface
covered | reddit_listing_cache_test › RedditPostSource › and it is not asked again by the next surface | RedditFollowingFeed | RedditFeedsTest.oneBadCommunityDoesNotEmptyTheFeedAndIsNotAskedAgainByTheNextSurface
covered | reddit_listing_cache_test › RedditPostSource › nothing to show is a real answer, not a reason to keep the old posts | RedditFollowingFeed | RedditFeedsTest.nothingToShowIsARealAnswerNotAReasonToKeepTheOldPosts
covered | reddit_listing_cache_test › RedditPostSource › newest first, stickied dropped, trimmed to what the surface shows | RedditFollowingFeed | RedditFeedsTest.newestFirstPinnedDroppedTrimmedToWhatTheSurfaceShows
covered | reddit_listing_cache_test › RedditPostSource › one access token for every surface rather than one per read | networks/reddit RedditSignIn | RedditAuthTest.oneAccessTokenServesEveryReadForFiftyMinutes
covered | reddit_listing_cache_test › RedditPostSource › a refresh token Reddit rejects ends the session rather than the read | RedditSignIn, plugins/reddit RedditApp.session (signs out) | RedditAuthTest.aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot
covered | reddit_listing_cache_test › RedditPostSource › signing in throws away what was cached for the old credentials | RedditFollowingFeed (forgetOnSessionChange) | RedditFeedsTest.anotherSessionThrowsAwayWhatWasCachedForTheOldOne
untested→ported | reddit_listing_cache_test › RedditPostSource › asking for the account-free route does the same | RedditFollowingFeed (RedditSession includes preferPublic) | RedditFeedsTest.anotherSessionThrowsAwayWhatWasCachedForTheOldOne (account-free half added)
covered | reddit_listing_cache_test › RedditPostSource › the stored sort is what every surface reads | RedditFollowingFeed; plugins/reddit RedditApp.readFeed (RedditChoices) | RedditFeedsTest.theSortAndItsWindowAreWhatTheFeedAsksFor; RedditRulesTest.theChoicesAreReadFromThePreferencesWithXtasDefaults
covered | reddit_listing_cache_test › RedditPostSource › the stored time filter is sent for top listings | RedditFollowingFeed | RedditFeedsTest.theSortAndItsWindowAreWhatTheFeedAsksFor
covered | reddit_listing_cache_test › RedditPostSource › NSFW hide mode filters the merged feed | RedditFollowingFeed | RedditFeedsTest.hidingNsfwFiltersTheMergedFeed
covered | reddit_listing_cache_test › RedditPostSource › a subreddit named twice is read once | RedditFollowingFeed | RedditFeedsTest.aGroupSharingACommunityWithTheTabDoesNotFetchItAgainAndOneNamedTwiceIsReadOnce
covered | reddit_listing_cache_test › RedditSavedStore › round-trips post snapshots through prefs | plugins/reddit RedditSaved, RedditPostJson | RedditStoresTest.theSavedPostsAreKeptInTheirPreference; RedditPostJsonTest.aPostComesBackAsItWasKept

## test/reddit_listing_page_test.dart
covered | reddit_listing_page_test › appendRedditPosts › appends new posts in order | networks/reddit appendRedditPosts | RedditPostsTest.aNextPageIsAppendedInOrderEachPostOnce
covered | reddit_listing_page_test › appendRedditPosts › skips duplicates by id, keeping the first | appendRedditPosts | RedditPostsTest.aNextPageIsAppendedInOrderEachPostOnce
covered | reddit_listing_page_test › appendRedditPosts › returns existing unchanged when next is empty | appendRedditPosts | RedditPostsTest.aNextPageIsAppendedInOrderEachPostOnce (empty next)

## test/reddit_media_urls_test.dart
covered | reddit_media_urls_test › what a URL points at › a picture host serves a picture whatever the path looks like | networks/reddit/RedditMedia.kt embeddableImage | RedditPostsTest.aPictureHostServesAPictureWhateverThePathAndAnImageExtensionIsEnoughAnywhere
covered | reddit_media_urls_test › what a URL points at › an image extension is enough on any host | RedditMedia.embeddableImage | RedditPostsTest.aPictureHostServesAPictureWhateverThePathAndAnImageExtensionIsEnoughAnywhere
covered | reddit_media_urls_test › what a URL points at › imgur's gifv is a video player; the gif beside it is the animation | RedditMedia | RedditPostsTest.imgursGifvIsAVideoPlayerAndTheGifBesideItTheAnimation
covered | reddit_media_urls_test › what a URL points at › a page is not a picture | RedditMedia | RedditPostsTest.aPageIsNotAPicture
covered | reddit_media_urls_test › what a URL points at › a giphy share link resolves to the file behind it | RedditMedia.embeddableImage | RedditPostsTest.aGiphyShareLinkAndASingleImgurPageResolveToTheirFiles
covered | reddit_media_urls_test › what a URL points at › a single imgur page resolves; an album does not | RedditMedia.embeddableImage | RedditPostsTest.aGiphyShareLinkAndASingleImgurPageResolveToTheirFiles
covered | reddit_media_urls_test › what a URL points at › a host that needs a key or a round trip is left alone | RedditMedia.embeddableImage | RedditPostsTest.aHostThatNeedsAKeyOrARoundTripIsLeftALink
covered | reddit_media_urls_test › what a URL points at › Reddit's own media token names the file the old site never rendered | RedditMedia.tokenImage | RedditPostsTest.redditsMediaTokenNamesAGiphyFileAndAnEmoteNone
covered | reddit_media_urls_test › what a URL points at › an emote token names no public file, so it resolves to nothing | RedditMedia.tokenImage | RedditPostsTest.redditsMediaTokenNamesAGiphyFileAndAnEmoteNone
covered | reddit_media_urls_test › what a URL points at › video hosts are known regardless of www | RedditMedia.isVideoHost | RedditPostsTest.videoHostsAreKnownWithOrWithoutWww
covered | reddit_media_urls_test › collapseRedditImageUrls › different widths of the same path collapse to the larger width | RedditMedia.collapseImageUrls | RedditPostsTest.copiesOfOnePictureCollapseAndDifferentPicturesStayInOrder
covered | reddit_media_urls_test › collapseRedditImageUrls › preview and i.redd.it of the same file collapse to i.redd.it | RedditMedia.collapseImageUrls | RedditPostsTest.copiesOfOnePictureCollapseAndDifferentPicturesStayInOrder
covered | reddit_media_urls_test › collapseRedditImageUrls › different filenames stay separate, in first-seen order | RedditMedia.collapseImageUrls | RedditPostsTest.copiesOfOnePictureCollapseAndDifferentPicturesStayInOrder
covered | reddit_media_urls_test › collapseRedditImageUrls › exact duplicate URLs collapse to one | RedditMedia.collapseImageUrls | RedditPostsTest.copiesOfOnePictureCollapseAndDifferentPicturesStayInOrder
covered | reddit_media_urls_test › media placeholder titles › common image placeholders match | RedditMedia.isPlaceholderTitle | RedditPostsTest.titlesThatOnlyAnnounceAPictureAreRecognisedAndRealOnesAreNot
covered | reddit_media_urls_test › media placeholder titles › wrapped leftovers are stripped from a longer body | RedditMedia.stripPlaceholderTokens | RedditPostsTest.placeholderLeftoversAreStrippedFromALongerBody
covered | reddit_media_urls_test › media placeholder titles › real titles do not match | RedditMedia.isPlaceholderTitle | RedditPostsTest.titlesThatOnlyAnnounceAPictureAreRecognisedAndRealOnesAreNot
covered | reddit_media_urls_test › stripRedditMediaLinksFromText › drops a preview.redd.it URL left in a meme title | RedditMedia.stripMediaLinks | RedditPostsTest.aPicturesCdnLinkIsDroppedFromTheTextButAPageLinkIsKept
covered | reddit_media_urls_test › stripRedditMediaLinksFromText › a title that is only the CDN URL becomes empty | RedditMedia.stripMediaLinks | RedditPostsTest.aPicturesCdnLinkIsDroppedFromTheTextButAPageLinkIsKept
covered | reddit_media_urls_test › stripRedditMediaLinksFromText › keeps a non-image link | RedditMedia.stripMediaLinks | RedditPostsTest.aPicturesCdnLinkIsDroppedFromTheTextButAPageLinkIsKept
covered | reddit_media_urls_test › redditVRedditDashUrl › builds the DASH playlist from a bare v.redd.it id | RedditMedia.vRedditDashUrl | RedditPostsTest.aBareVRedditLinkGetsItsDashManifest
covered | reddit_media_urls_test › redditVRedditDashUrl › ignores non-video hosts and file paths | RedditMedia.vRedditDashUrl | RedditPostsTest.aBareVRedditLinkGetsItsDashManifest

## test/reddit_read_session_test.dart
covered | reddit_read_session_test › RedditReadSession.resolve › public source never exchanges a refresh token | networks/reddit RedditSignIn.resolve | RedditAuthTest.theAccountFreeRouteNeverMintsAToken
covered | reddit_read_session_test › RedditReadSession.resolve › auto source with a refresh token yields a user access token | RedditSignIn.resolve | RedditAuthTest.aRefreshTokenGivesTheSessionAnAccessToken
covered | reddit_read_session_test › RedditReadSession.resolve › a dead refresh token is cleared and the session falls back | RedditSignIn.resolve; plugins/reddit RedditApp.session clears the refresh token | RedditAuthTest.aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot
covered | reddit_read_session_test › RedditReadSession.resolve › auto without a refresh token keeps the client id for app-only | RedditSignIn.resolve | RedditAuthTest.withoutARefreshTokenTheClientIdIsKeptForAppOnlyReads
covered | reddit_read_session_test › RedditReadSession.fetchSubreddit › threads userToken into the client so oauth.reddit.com is used | RedditClient.listing | RedditClientTest.aUserTokenGoesToTheApiAsItIsWithoutMintingAnother
covered | reddit_read_session_test › RedditReadSession.fetchSubreddit › passes top listing time filters through to the client | RedditClient.listing | RedditClientTest.theTimeWindowGoesWithTopAndControversialOnly
covered | reddit_read_session_test › RedditReadSession.fetchComments › threads credentials so oauth.reddit.com serves the thread JSON | RedditClient.thread | RedditClientTest.aSignedInReaderReadsAThreadFromTheApi

## test/reddit_search_html_test.dart
covered | reddit_search_html_test › post results › are read out of the search markup | networks/reddit/RedditOldReddit.kt RedditOldSearch | RedditOldRedditTest.searchedPostsAreReadOutOfTheSearchMarkup
covered | reddit_search_html_test › post results › a page rendered as a listing is read by the listing parser instead | RedditOldSearch | RedditOldRedditTest.aSearchPageRenderedAsAListingIsReadAsOne
covered | reddit_search_html_test › post results › the id survives a result with no data-fullname | RedditOldSearch | RedditOldRedditTest.aSearchedPostsIdSurvivesWithoutItsFullnameAndItsScoreWithAComma
covered | reddit_search_html_test › post results › a score with a comma is still a number | RedditOldSearch | RedditOldRedditTest.aSearchedPostsIdSurvivesWithoutItsFullnameAndItsScoreWithAComma
covered | reddit_search_html_test › post results › a spoiler result carries the flag | RedditOldSearch | RedditOldRedditTest.searchedPostsAreReadOutOfTheSearchMarkup (spoiler)
covered | reddit_search_html_test › post results › a result with no title is skipped rather than guessed at | RedditOldSearch | RedditOldRedditTest.aSearchResultWithNoTitleIsSkipped
covered | reddit_search_html_test › subreddit results › are read from either markup, without repeating one | RedditOldSearch | RedditOldRedditTest.searchedCommunitiesAreReadFromEitherMarkupEachOnce
covered | reddit_search_html_test › subreddit results › an entry with no name is skipped | RedditOldSearch | RedditOldRedditTest.searchedCommunitiesAreReadFromEitherMarkupEachOnce (nameless entry)
covered | reddit_search_html_test › user results › are read and deduplicated | RedditOldSearch | RedditOldRedditTest.searchedAccountsAreReadEachOnce
covered | reddit_search_html_test › user results › a page with no users yields none rather than throwing | RedditOldSearch | RedditOldRedditTest.searchedAccountsAreReadEachOnce (empty page)
untested→ported | reddit_search_html_test › JSON search listings › posts are read from t3 children | networks/reddit/RedditJson.kt searchPosts | new RedditSearchJsonTest.postsAreReadFromT3Children
untested→ported | reddit_search_html_test › JSON search listings › subreddits are read from t5 children | RedditJson.searchSubreddits | RedditSearchJsonTest.subredditsAreReadFromT5Children
untested→ported | reddit_search_html_test › JSON search listings › users are read from t2 children | RedditJson.searchUsers | RedditSearchJsonTest.usersAreReadFromT2Children
untested→ported | reddit_search_html_test › JSON search listings › a missing listing yields none rather than throwing | RedditJson.search* | RedditSearchJsonTest.aMissingListingYieldsNoneRatherThanThrowing

## test/reddit_signin_survival_test.dart
covered | reddit_signin_survival_test › what a failed token refresh does to the sign-in › a network error keeps the session | networks/reddit/RedditAuth.kt RedditSignIn (signedOut only on 401) | RedditAuthTest.aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot, onlyRedditRefusingTheGrantEndsTheSession
untested→ported | reddit_signin_survival_test › what a failed token refresh does to the sign-in › a 500 from the token endpoint keeps the session | RedditSignIn | RedditAuthTest.onlyRedditRefusingTheGrantEndsTheSession (500)
covered | reddit_signin_survival_test › what a failed token refresh does to the sign-in › a rate limit keeps the session | RedditSignIn | RedditAuthTest.aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot (429)
untested→ported | reddit_signin_survival_test › what a failed token refresh does to the sign-in › a block page keeps the session, as a Reddit error rather than a crash | RedditSignIn | RedditAuthTest.onlyRedditRefusingTheGrantEndsTheSession (block page)
untested→ported | reddit_signin_survival_test › what a failed token refresh does to the sign-in › a rejected client id ends the session | RedditSignIn | RedditAuthTest.onlyRedditRefusingTheGrantEndsTheSession (401)
covered | reddit_signin_survival_test › what a failed token refresh does to the sign-in › invalid_grant ends the session | RedditSignIn; RedditAuth (invalid_grant → 401) | RedditAuthTest.aDeadRefreshTokenSignsTheReaderOutButAPassingFailureDoesNot, aRejectedClientIdOrARevokedSessionIsUnauthorised

## test/reddit_spoiler_pref_test.dart
covered | reddit_spoiler_pref_test › spoilers stay covered until the setting is on | plugins/reddit/RedditRules.kt RedditChoices.gate | RedditRulesTest.aSpoilerIsCoveredFirstThenOver18PostsAsTheReaderChose
covered | reddit_spoiler_pref_test › showing spoilers still covers NSFW when that mode asks for a tap | RedditChoices.gate | RedditRulesTest.aSpoilerIsCoveredFirstThenOver18PostsAsTheReaderChose
covered | reddit_spoiler_pref_test › always-show NSFW and spoilers leaves ordinary media uncovered | RedditChoices.gate | RedditRulesTest.aSpoilerIsCoveredFirstThenOver18PostsAsTheReaderChose

## test/reddit_subreddit_switch_test.dart
covered | reddit_subreddit_switch_test › redditHomeFeedKey › a followed community is not the Following rail | plugins/reddit/RedditRules.kt RedditHomeSource.key/railSelected | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity
covered | reddit_subreddit_switch_test › RedditHomeStore › selecting a followed subreddit changes the feed source | RedditHomeSource.selectingSubreddit/selectingMode | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity
covered | reddit_subreddit_switch_test › RedditHomeStore › an unfollowed persisted community falls back to the rail | RedditHomeSource.reconciled | RedditRulesTest.aCommunityNoLongerFollowedDoesNotOpenAgain
covered | reddit_subreddit_switch_test › tapping a followed chip leaves Home and opens that community | RedditHome.kt CommunityStrip (chip → app.selectSubreddit), RedditHomeSource.isSelected | RedditRulesTest.theTabShowsARailOrOneFollowedCommunity

## test/reddit_text_test.dart
covered | reddit_text_test › redditTextParts › keeps ordinary words as one run | networks/reddit redditTextParts | RedditPostsTest.ordinaryWordsAreOneRun
covered | reddit_text_test › redditTextParts › a markdown link uses the label, not the URL | redditTextParts | RedditPostsTest.aMarkdownLinkIsShownByItsLabelNotItsUrl
covered | reddit_text_test › redditTextParts › the GirlsFrontline2 sticker comment keeps short labels | redditTextParts | RedditPostsTest.aLabelEndingInASpaceKeepsItsShortName
covered | reddit_text_test › redditTextParts › a bare Drive URL shrinks to the host | redditTextParts | RedditPostsTest.bareLinksShrinkToTheirHostOrTheAccountTheyName
covered | reddit_text_test › redditTextParts › a bare X status shrinks to x.com/handle | redditTextParts | RedditPostsTest.bareLinksShrinkToTheirHostOrTheAccountTheyName
covered | reddit_text_test › compactRedditLinkLabel › prefers a non-URL markdown label | redditTextParts (label choice) | RedditPostsTest.aLabelIsPreferredUnlessItIsTheUrlItself
covered | reddit_text_test › compactRedditLinkLabel › does not print a label that is itself the URL | redditTextParts (label choice) | RedditPostsTest.aLabelIsPreferredUnlessItIsTheUrlItself
covered | reddit_text_test › paints Drive and X markdown as short blue labels | plugins/reddit RedditUi.kt (redditTextParts → TextRun.Link, drawn by ui LinkedText in the link colour) | RedditPostsTest.bareLinksShrinkToTheirHostOrTheAccountTheyName; RedditSharedTest.aDiscussionPostShowsItsTitleAndTheStartOfItsWordsWithTheirLinks

## test/reddit_thread_render_test.dart
n/a | reddit_thread_render_test › reddit-thread-de-390-lightsout | native/tool/plugin_screens.sh | golden render for the owner's review boards, skipped unless RENDER_REDDIT_THREAD; native renders its screens with its own screenshot tool
n/a | reddit_thread_render_test › reddit-thread-en-390-light | native/tool/plugin_screens.sh | same golden render
n/a | reddit_thread_render_test › reddit-thread-comments-en-390-lightsout | native/tool/plugin_screens.sh | same golden render
n/a | reddit_thread_render_test › reddit-thread-de-320-x1.3 | native/tool/plugin_screens.sh | same golden render

## test/reddit_thread_test.dart
covered | reddit_thread_test › header and post › the bar names the subreddit and its menu opens the post sheet | plugins/reddit/RedditThread.kt ThreadScreen (PluginScaffold title "r/<community>", ⋮ → PostSheet with Open in browser / Share link) | UI-only (no Compose test harness); the sheet's targets: RedditThreadRulesTest.linksLeadToReddit
implemented (UI-only) | reddit_thread_test › header and post › the author row has a round avatar, then the title at 21 | RedditThread.kt PostAuthor (FaceAvatar 22dp round) and title at 21sp | UI-only visual spec, as XTA; no Compose test harness
covered | reddit_thread_test › header and post › a link with a preview is a card with the picture over the domain | plugins/reddit/RedditMediaViews.kt (thread link card: picture over the domain row with open-in-new) | RedditRulesTest.aPostShowsItsVideoThenItsGalleryThenItsPictureThenItsLink, aLinkCardNamesTheSite (which card); drawing UI-only
covered | reddit_thread_test › header and post › a link without a preview is one row: initial, domain, Open in browser | RedditMediaViews.kt (no preview: initial, domain, "Open in browser", min 56dp) | RedditRulesTest.aLinkCardNamesTheSite; drawing UI-only
missing→implemented | reddit_thread_test › actions under the post › the vote pill upvotes on this device and says so | RedditComments.kt UpvoteButton (device vote, score+1, now toggleable semantics), RedditThreadRules.shownScore | native had no toggled state for screen readers; RedditThreadRulesTest.everyTargetIsFortyEightDpAndAnUpvoteCountsOneMore; RedditStoresTest.anUpvoteIsKeptOnTheDeviceAndGoesInTheBackupAsXtasRows
covered | reddit_thread_test › actions under the post › the comments pill counts the comments and scrolls to them | RedditThread.kt PostActions (comments pill → scrolls to the comments header), RedditThreadRules.commentCount | RedditThreadRulesTest.theCountIsRedditsOrWhatLoadedWhenThatIsMore; scrolling UI-only
covered | reddit_thread_test › actions under the post › share and save sit at the end | RedditThread.kt PostActions (share, then save with long-press folder, at the end) | UI-only; saving: RedditSharedTest.aPostIsFiledInXtasRecordAndReadBack
covered | reddit_thread_test › comments header › counts the comments, sorts from a menu and folds all | RedditComments.kt CommentsHeader; RedditThreadState.resort/toggleAll | RedditStatesTest.aThreadReadsItsCommentsInTheOrderChosenAndFoldsThem; RedditThreadRulesTest.collapseAllFoldsTheTopLevelCommentsWithRepliesAndExpandAllUndoesIt; RedditClientTest.theChosenOrderRidesTheCommentsPieceInItsSpelling
untested→ported | reddit_thread_test › comment rows › replies hang off thread lines, a column further in, not in bubbles | RedditComments.kt ThreadLines, RedditThreadRules.lines/LINE_COLUMN_DP | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap
untested→ported | reddit_thread_test › comment rows › each level has its colour, at 3:1 against the page | RedditThreadRules.lineColour/LINE_COLOURS (moved out of the composable) | RedditThreadRulesTest.eachLevelHasItsColourAtThreeToOneAgainstEveryPage
untested→ported | reddit_thread_test › comment rows › a hairline separates top-level threads, not the first | RedditThreadRules.divided, RedditThread.kt | RedditThreadRulesTest.aHairlineSeparatesTopLevelThreadsButNotTheFirst
covered | reddit_thread_test › comment rows › the header folds the comment and counts the replies it holds | RedditComments.kt CommentHeader (folds, "+N replies" chip), RedditThreadRules.visible | RedditThreadRulesTest.aFoldedCommentKeepsItsRowAndHidesEverythingUnderIt
covered | reddit_thread_test › comment rows › the post author reads in the accent with OP and a round avatar | RedditComments.kt Name (OP in the accent, round avatar for the submitter, OP chip) | RedditCommentsTest.theSubmitterAndTheAuthorsFlairAreReadOffTheTagline, theSubmitterFlagAndMediaTokensAreRead; drawing UI-only
covered | reddit_thread_test › comment rows › flair shows beside the name | RedditComments.kt CommentHeader (flair chip) | RedditCommentsTest.theAuthorsFlairComesWithTheCommentAndAnEmptyOneNotAtAll; drawing UI-only
covered | reddit_thread_test › comment rows › a comment upvotes on this device from its action row | RedditComments.kt CommentContent UpvoteButton (RedditVotes.commentId) | RedditStoresTest.anUpvoteIsKeptOnTheDeviceAndGoesInTheBackupAsXtasRows; RedditThreadRulesTest.everyTargetIsFortyEightDpAndAnUpvoteCountsOneMore
covered | reddit_thread_test › comment rows › removed comments say what happened, without an upvote or menu | RedditComments.kt CommentContent/CommentHeader (removed: italic reason, no upvote, no menu) | RedditThreadRulesTest.aRemovedCommentSaysWhoTookItsWords; RedditCommentsTest.aRemovedCommentIsToldApartFromAStubAndFromAGoneAuthor
covered | reddit_thread_test › comment rows › a comment written since the last visit says it is new | RedditComments.kt Age (", new"), RedditVisits, RedditThreadState (visit noted on open) | RedditStoresTest.aCommentIsNewOnlyWhenWrittenAfterTheLastVisit, aVisitIsKeptPerThreadForTheNewestTwoHundred
implemented (UI-only) | reddit_thread_test › comment rows › the menu reaches the author and copies the words | RedditComments.kt CommentMenu (u/author, Copy, Open in browser) | UI-only menu; no Compose test harness
covered | reddit_thread_test › held-back comments › deeper ones continue on their own page | RedditThreadRules.heldBack (OPEN_SUBTREE → its own page), RedditComments.kt HeldBackRow (now min 48dp) | RedditThreadRulesTest.heldBackCommentsLoadHereUntilRedditRefusesThenOpenOnReddit
covered | reddit_thread_test › held-back comments › the next top-level ones load into the thread | RedditThreadState.loadMore, RedditThreadRules.replacing | RedditThreadRulesTest.aLoadedPageOfHeldBackCommentsTakesTheStubsPlace; RedditStatesTest.heldBackCommentsLoadInPlaceOrSendTheReaderToReddit
covered | reddit_thread_test › jump to the next comment › lands each top-level comment at the top in turn, then the end | RedditThread.kt jump, RedditThreadRules.nextTopLevel | RedditThreadRulesTest.theJumpLandsOnTheNextTopLevelCommentAndStopsAfterTheLast
untested→ported | reddit_thread_test › jump to the next comment › shows only once there are two top-level comments | RedditThreadRules.showsJump, RedditThread.kt | RedditThreadRulesTest.theJumpShowsOnlyOnceThereAreTwoTopLevelComments
covered | reddit_thread_test › jump to the next comment › the next target is the nearest one below the top | RedditThreadRules.nextTopLevel | RedditThreadRulesTest.theJumpLandsOnTheNextTopLevelCommentAndStopsAfterTheLast
missing→implemented | reddit_thread_test › every size › every target is 48 dp | RedditComments.kt (comment header, pills and held-back rows were 44dp/40dp; now RedditThreadRules.TOUCH_TARGET_DP = 48 in both directions) | RedditThreadRulesTest.everyTargetIsFortyEightDpAndAnUpvoteCountsOneMore
covered | reddit_thread_test › every size › 320dp, 2x text, ltr, light | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › every size › 320dp, 2x text, ltr, dim | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › every size › 320dp, 2x text, ltr, lights out | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › every size › 320dp, 2x text, rtl, light | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › every size › 320dp, 2x text, rtl, dim | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › every size › 320dp, 2x text, rtl, lights out | RedditComments.kt ThreadLines (capped at MAX_LINES, mirrored for RTL), HeldBackRow "Continue thread" | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap, eachLevelHasItsColourAtThreeToOneAgainstEveryPage; layout at 320dp/2x is UI-only
covered | reddit_thread_test › last visits › keep the most recent threads | plugins/reddit RedditVisits | RedditStoresTest.aVisitIsKeptPerThreadForTheNewestTwoHundred
covered | reddit_thread_test › last visits › one thread however its permalink ends | plugins/reddit RedditVisits | RedditStoresTest.aVisitIsKeptPerThreadForTheNewestTwoHundred
covered | reddit_thread_test › last visits › nothing is new on a first visit | plugins/reddit RedditVisits | RedditStoresTest.aCommentIsNewOnlyWhenWrittenAfterTheLastVisit

## test/reddit_www_test.dart
covered | reddit_www_test › a feed page › reads a picture post out of its attributes | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPicturePostIsReadOutOfItsAttributes
covered | reddit_www_test › a feed page › a post its media marks NSFW or pinned is read as such, as is one in an NSFW community | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPostItsMediaMarksNsfwOrPinnedIsReadSoAsIsOneInAnNsfwCommunity
covered | reddit_www_test › a feed page › a post in an NSFW community, or behind a blur, is marked as Reddit marks it | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPostInAnNsfwCommunityOrBehindABlurIsMarkedAsRedditMarksIt
covered | reddit_www_test › a feed page › a gallery carries every picture, in order, at the size its feed shows | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aGalleryCarriesEveryPictureInOrderAtTheSizeItsFeedShows
covered | reddit_www_test › a feed page › a crosspost of one picture links the picture, of a gallery the original | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aCrosspostOfOnePictureLinksThePictureAndOfAGalleryTheOriginal
covered | reddit_www_test › a feed page › a video plays its stream, under its poster frame | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aVideoPlaysItsStreamUnderItsPosterFrame
covered | reddit_www_test › a feed page › a self post carries its text, links kept | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aSelfPostCarriesItsTextWithItsLinks
covered | reddit_www_test › a feed page › a link post links its article, with its thumbnail | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aLinkPostLinksItsArticleWithItsThumbnail
covered | reddit_www_test › a feed page › the next page starts after the fullname its partial names | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.theNextPageStartsAfterTheFullnameItsPartialNames
covered | reddit_www_test › a feed page › a profile names its next page on its posts instead | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aProfileNamesItsNextPageOnItsPostsInstead
covered | reddit_www_test › a feed page › a community with nothing in it reads as empty, not as a failure | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aCommunityWithNothingInItReadsAsEmptyNotAsAFailure
covered | reddit_www_test › a feed page › a post missing what every post has is skipped, not guessed at | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPostMissingWhatEveryPostHasIsSkippedNotGuessedAt
covered | reddit_www_test › what www.reddit.com answered with › its browser check is recognised | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.whatWwwAnsweredWithIsRecognised
covered | reddit_www_test › what www.reddit.com answered with › its network block is recognised | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.whatWwwAnsweredWithIsRecognised
covered | reddit_www_test › what www.reddit.com answered with › a page with posts is read, whatever a title on it says | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.whatWwwAnsweredWithIsRecognised
covered | reddit_www_test › what www.reddit.com answered with › a page of the site it does not know, or no page of it at all, is unreadable | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.whatWwwAnsweredWithIsRecognised
covered | reddit_www_test › paging cursors › are base64 of the fullname, with or without padding | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.pagingCursorsAreBase64OfTheFullnameWithOrWithoutPadding
covered | reddit_www_test › paging cursors › anything else is no cursor | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anythingElseIsNoCursor
covered | reddit_www_test › a thread › nests replies under the comment they answer | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aThreadNestsRepliesUnderTheCommentTheyAnswer
covered | reddit_www_test › a thread › reads a comment out of its attributes and its own words | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aCommentIsReadOutOfItsAttributesAndItsOwnWords
covered | reddit_www_test › a thread › replies held back are a stub that opens their parent's page | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.repliesHeldBackAreAStubThatOpensTheirParentsPage
covered | reddit_www_test › a thread › top-level comments held back are said so | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.topLevelCommentsHeldBackAreSaidSo
covered | reddit_www_test › a thread › a comment's own page keeps that comment and its replies | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aCommentsOwnPageKeepsThatCommentAndItsReplies
covered | reddit_www_test › a thread › a comment whose words are gone keeps its row for its replies | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aCommentWhoseWordsAreGoneKeepsItsRowForItsReplies
covered | reddit_www_test › a thread › a big thread keeps the comments Reddit renders as the reader scrolls, and where the rest are | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aBigThreadKeepsTheCommentsRedditRendersLateAndWhereTheRestAre
covered | reddit_www_test › a thread › the browser check holds no thread | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.theBrowserCheckHoldsNoThread
covered | reddit_www_test › cutting a page down › templates go whole, nested ones too, unless they hold the page's own content | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.templatesGoWholeNestedOnesTooUnlessTheyHoldThePagesOwnContent
covered | reddit_www_test › more comments › the next comments come with where the ones after them are | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.theNextCommentsComeWithWhereTheOnesAfterThemAre
covered | reddit_www_test › more comments › the last of them, or a page that is not them, names nothing further | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.theLastOfThemOrAPageThatIsNotThemNamesNothingFurther
covered | reddit_www_test › search › posts come from their tracking JSON and their counter row | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.searchPostsComeFromTheirTrackingJsonAndTheirCounterRow
covered | reddit_www_test › search › communities carry their picture and description | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.searchedCommunitiesCarryTheirPictureAndDescription
covered | reddit_www_test › search › people carry their karma | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.searchedPeopleCarryTheirKarma
covered | reddit_www_test › search › a page that is not a search yields nothing rather than throwing | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPageThatIsNotASearchYieldsNothingRatherThanThrowing
covered | reddit_www_test › an embed card › a self post brings its words, author, counts and age | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedSelfPostBringsItsWordsAuthorCountsAndAge
covered | reddit_www_test › an embed card › a long post arrives whole, not cut where the card fades it out | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aLongEmbeddedPostArrivesWholeNotCutWhereTheCardFadesIt
covered | reddit_www_test › an embed card › a picture post links its picture, under the scaled copy | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedPicturePostLinksItsPictureUnderTheScaledCopy
covered | reddit_www_test › an embed card › a gallery brings every picture in order | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedGalleryBringsEveryPictureInOrder
covered | reddit_www_test › an embed card › a video brings its stream and poster frame | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedVideoBringsItsStreamAndPosterFrame
covered | reddit_www_test › an embed card › a link post links its article | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedLinkPostLinksItsArticle
covered | reddit_www_test › an embed card › a crosspost's title is read from its own element, and it links the original's pictures | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.anEmbeddedCrosspostsTitleIsItsOwnAndItLinksTheOriginalsPictures
covered | reddit_www_test › an embed card › a post that is gone, and a page that is not a card, yield nothing | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPostThatIsGoneAndAPageThatIsNotACardYieldNothing
covered | reddit_www_test › a page names its community › a community post, a profile post in either spelling, and anything else | networks/reddit/RedditWww.kt, RedditWwwPages.kt | RedditWwwTest.aPageNamesItsCommunity

## test/instagram_client_test.dart
covered | instagram_client_test › profile warms the homepage then hits i.instagram.com | networks/instagram/InstagramClient.kt profile | InstagramClientTest.aProfileWarmsTheHomepageThenAsksTheApiHost
covered | instagram_client_test › web_profile_info falls back to www after 429 | InstagramClient.profile | InstagramClientTest.webProfileInfoFallsBackToWwwWhenTheApiHostLimitsOrWantsALogin
covered | instagram_client_test › homepage HTML is accepted when warming the guest session | InstagramClient (warm-up) | InstagramClientTest.aProfileWarmsTheHomepageThenAsksTheApiHost, theHomepagesCookiesAreKeptAndHeard
covered | instagram_client_test › HTML app shell is loginRequired, not a parse crash | InstagramClient | InstagramClientTest.theLoginPageInPlaceOfJsonIsLoginRequired, anAnswerThatIsNotJsonIsUnreadable
covered | instagram_client_test › private empty profile is returned for the lock screen | InstagramClient | InstagramClientTest.aPrivateProfileIsReturnedForItsLock
covered | instagram_client_test › guestDiscover throws when every seed fails | InstagramClient guest mix | InstagramClientTest.theGuestMixFailsWithTheRateLimitWhenEveryAccountFails
untested→ported | instagram_client_test › setCookies and importThreadsCookies share names, not a client | InstagramClient.setCookies, plugins/instagram InstagramRules.kt InstagramThreadsSession (InstagramApp.importThreadsCookies) | InstagramClientTest.aSessionIsAllFiveCookies; InstagramPluginTest.threadsCookiesAreCopiedAsInstagramsSession
covered | instagram_client_test › forYou uses explore_grid when the session answers | InstagramClient.forYou | InstagramClientTest.forYouIsExploreWhenTheSessionReadsIt
covered | instagram_client_test › forYou skips Explore without a session | InstagramClient.forYou | InstagramClientTest.forYouSkipsExploreWithoutASession
covered | instagram_client_test › forYou falls back to public seeds when Explore needs a login | InstagramClient.forYou | InstagramClientTest.forYouFallsBackToThePublicAccountsWhenExploreWantsALogin
covered | instagram_client_test › invalid handle is notFound before any request | InstagramClient / InstagramLink | InstagramClientTest.aNameThatCannotBeAUsernameIsNotFoundBeforeAnyRequest

## test/instagram_discovery_test.dart
covered | instagram_discovery_test › interleaveInstagramDiscover round-robins and dedupes | networks/instagram/InstagramDiscovery.kt | InstagramDiscoveryTest.interleaveTakesARoundFromEachPageAndEachPostOnce, fixedFromXtaARoundOfOnlyRepeatsNoLongerEndsTheMix
covered | instagram_discovery_test › instagramSearchOpensHandle is guest-only for a real handle | InstagramDiscovery.kt | InstagramDiscoveryTest.aTypedUsernameOpensAsAProfileForAGuestOnly
covered | instagram_discovery_test › peopleToFollowFromInstagram skips followed authors | InstagramDiscovery.kt | InstagramDiscoveryTest.peopleToFollowSkipsAuthorsAlreadyFollowed, peopleToFollowStopsAtItsCap
covered | instagram_discovery_test › parseInstagramExplore walks sectional media grids | networks/instagram/InstagramParser.kt explore | InstagramParserTest.exploreFindsPostsWhereverItsSectionsHoldThem
covered | instagram_discovery_test › parseInstagramExplore ignores a reshaped empty payload | InstagramParser.kt explore | InstagramParserTest.anExploreAnswerWithoutPostsIsEmpty

## test/instagram_parse_test.dart
covered | instagram_parse_test › normaliseInstagramHandle strips @ and URLs, rejects paths | networks/instagram InstagramLink / handle normalising | InstagramParserTest.aUsernameIsTypedWithAnAtWithoutOneOrAsAProfileLink; InstagramLinkTest
covered | instagram_parse_test › parseInstagramProfileJson reads web_profile_info | InstagramParser.kt | InstagramParserTest.webProfileInfoDescribesTheAccount
covered | instagram_parse_test › parseInstagramProfileMedia reads the first page of posts | InstagramParser.kt | InstagramParserTest.webProfileInfoCarriesTheNewestPostsByTheProfile, aGridWithoutEdgesFallsBackToItsMediaNodes
covered | instagram_parse_test › parseInstagramMediaNode reads GraphQL sidecar slides | InstagramParser.kt | InstagramParserTest.aSidecarHoldsEverySlideInOrder
covered | instagram_parse_test › parseInstagramUserFeed reads items and video flags | InstagramParser.kt | InstagramParserTest.theFeedReadsReelsAndCarouselsWithTheirOwnAuthors, aVideoIsAReelShownByItsPoster, aFeedThatHasNoMoreHasNoNextPage
covered | instagram_parse_test › parseInstagramTopSearch dedupes users | InstagramParser.kt | InstagramParserTest.topsearchListsEachUsernameOnce
covered | instagram_parse_test › missing fields do not throw | InstagramParser.kt | InstagramParserTest.missingFieldsDoNotThrow
covered | instagram_parse_test › instagramLoginRequired reads fail messages | InstagramParser.kt | InstagramParserTest.loginRequiredIsReadFromFailMessages

## test/plugin_repairs_test.dart
covered | plugin_repairs_test › a value fits its kind › ids and numbers | core repairs/Repairs.kt | core RepairsTest.idsAndNumbersAreDigitsOnly
covered | plugin_repairs_test › a value fits its kind › tokens | core Repairs.kt | core RepairsTest.tokensHoldNoDotsOrSlashes
covered | plugin_repairs_test › a value fits its kind › versions | core Repairs.kt | core RepairsTest.versionsAreNumbersJoinedByDots
covered | plugin_repairs_test › a value fits its kind › header text: one line of printable ASCII, no edge spaces | core Repairs.kt | core RepairsTest.headerTextIsOneLineOfPrintableAsciiWithNoEdgeSpaces
covered | plugin_repairs_test › a published file › puts known values that fit in force, and nothing else | core Repairs.kt | core RepairsTest.onlyKnownValuesThatFitAreInForce
covered | plugin_repairs_test › a published file › replaces the repairs before it, so one withdrawn goes back to the shipped value | core Repairs.kt | core RepairsTest.aSlotTakesAPublishedValueOfItsKindAndOtherwiseKeepsTheShippedOne; StocksRepairsTest/ThreadsRepairsTest.aWithdrawnRepairGoesBackToTheShippedValue; TikTokRepairsTest.whatFitsIsTakenAndAWithdrawnRepairGoesBackToTheShippedValue
covered | plugin_repairs_test › a published file › that does not read changes nothing | core Repairs.kt | core RepairsTest.aFileThatDoesNotReadAsRepairsHoldsNone
covered | plugin_repairs_test › PluginRepairsRegistry › applies the copy last read before reading again | store published/PublishedDocument.kt RepairsRegistry | store PublishedDocumentTest.theCopyLastReadIsInForceBeforeAnyRead
covered | plugin_repairs_test › PluginRepairsRegistry › reads the file at most once a day, and keeps what it read | store PublishedDocument.kt | store PublishedDocumentTest.theFileIsReadAtMostOnceADayAndKept
covered | plugin_repairs_test › PluginRepairsRegistry › keeps what is in force when the file cannot be read | store PublishedDocument.kt | store PublishedDocumentTest.whatIsInForceStaysWhenTheFileCannotBeRead
covered | plugin_repairs_test › repairs.json names only known values that fit, each the one this build ships | each plugin's *Repairs; repairs.json | InstagramRepairsTest/TikTokRepairsTest/ThreadsRepairsTest/PixivRepairsTest/StocksRepairsTest.repairsJsonNamesOnly…, RedditRepairsTest.repairsJsonNamesExactlyRedditsSlots…; networks NetworkRepairsTest.everyPublishedValueBelongsToAPluginThatChecksIt
covered | plugin_repairs_test › every shipped value fits its own kind | each plugin's *Repairs | InstagramRepairsTest/TikTokRepairsTest/….everyShippedValueFitsItsKindAndIsDeclaredOnce; RedditRepairsTest.everyShippedValueFitsItsKindAndEachKeyIsDeclaredOnce
covered | plugin_repairs_test › each network sends the value in force › Instagram: app id and user agent | networks/instagram InstagramClient (repairs) | InstagramClientTest.theAppIdAndUserAgentInForceAreSent; InstagramRepairsTest.theClientTakesWhatFitsAndKeepsTheRest
covered | plugin_repairs_test › each network sends the value in force › TikTok: aid | networks/tiktok TikTokClient (repairs) | TikTokClientTest.theAidAndUserAgentInForceAreSent; TikTokRepairsTest.aRepairPublishedMeanwhileReachesTheNextRequest
covered | plugin_repairs_test › each network sends the value in force › Threads, Pixiv, Reddit and Stocks read their slots | threads/pixiv/reddit/stocks repairs slots | ThreadsRepairsTest.aWithdrawnRepairGoesBackToTheShippedValue; PixivRepairsTest.theAppTakesWhatFitsFromThePublishedEntriesAndKeepsTheRest; RedditRepairsTest.theAgentInForceIsTheRepairedOneWhenItFitsElseTheShippedOne; StocksRepairsTest.aRepairPublishedMeanwhileReachesTheNextRequest

## test/tiktok_client_test.dart
covered | tiktok_client_test › search resolves a display name via profile HTML and discover | networks/tiktok/TikTokClient.kt, TikTokSearch.kt | TikTokClientTest.aSearchFindsADisplayNameThroughProfilePagesAndDiscover
covered | tiktok_client_test › suggest and trending parse unsigned preview endpoints | TikTokClient.kt suggestions/trending | TikTokClientTest.suggestionsAskThePreviewWithTheWordsTyped, trendingAsksTheGuide; TikTokParserTest.suggestionsAsTypedAndTrendingAreReadTogether

## test/tiktok_parse_test.dart
covered | tiktok_parse_test › normaliseTikTokHandle strips @ and rejects junk | networks/tiktok TikTokLink / handle normalising | TikTokParserTest.aUsernameIsTypedWithAnAtWithoutOneOrAsALink
covered | tiktok_parse_test › parseTikTokProfileHtml reads user-detail rehydration | networks/tiktok/TikTokParser.kt | TikTokParserTest.aProfilePageCarriesTheAccount
covered | tiktok_parse_test › HTML entities in rehydration JSON are unescaped | TikTokParser.kt | TikTokParserTest.htmlEntitiesInThePagesJsonAreUndone
covered | tiktok_parse_test › private account is parsed without throwing | TikTokParser.kt | TikTokParserTest.aPrivateAccountIsReadNotRefused
covered | tiktok_parse_test › parseTikTokVideoHtml reads object playAddr | TikTokParser.kt | TikTokParserTest.aVideoPageCarriesTheVideoAndEveryFile
covered | tiktok_parse_test › parseTikTokItemList reads unsigned creator list | TikTokParser.kt | TikTokParserTest.theCreatorListReadsVideosAndPhotoPosts, theListsOwnCursorWinsAndMayBeANumber
covered | tiktok_parse_test › empty itemList preserves hasMore | TikTokParser.kt | TikTokParserTest.anEmptyListKeepsItsHasMore
covered | tiktok_parse_test › imagePost cover is used when video cover is missing | TikTokParser.kt | TikTokParserTest.aPhotoPostsCoverStandsInWhenTheVideoHasNone
covered | tiktok_parse_test › drops www.tiktok.com play URLs and missing fields | TikTokParser.kt | TikTokParserTest.tiktoksOwnPlayerLinksAreNoFilesAndAVideoWithoutAnAuthorIsLeftOut
covered | tiktok_parse_test › status 10201 is preserved for the client | TikTokParser.kt | TikTokParserTest.noSuchAccountsStatusIsKeptForTheClient
covered | tiktok_parse_test › missing rehydration is null, not an exception | TikTokParser.kt | TikTokParserTest.aPageWithoutTheAppsJsonIsNull
covered | tiktok_parse_test › formatTikTokDuration formats minutes and hides zero | TikTokParser.kt / plugins/tiktok TikTokText.kt | TikTokParserTest.theDurationShowsMinutesAndHidesZero
covered | tiktok_parse_test › handle candidates glue a display name into likely usernames | networks/tiktok/TikTokSearch.kt | TikTokSearchTest.aDisplayNameIsGluedIntoLikelyUsernames (+ the other candidate tests)
covered | tiktok_parse_test › discover cards of type 2 become search users | TikTokParser.kt | TikTokParserTest.discoverCardsOfPeopleBecomeSearchUsers, aDiscoverCardNamesItsPersonByLinkOrSubtitle
covered | tiktok_parse_test › suggest preview and guide lists are parsed together | TikTokParser.kt | TikTokParserTest.suggestionsAsTypedAndTrendingAreReadTogether
