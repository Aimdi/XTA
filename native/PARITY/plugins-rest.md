# Plugins, the rest: parity ledger (booru, EH, stocks and the shared plugin UI)

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered (a native JVM test asserts the same), untested → ported (implemented; the
Flutter widget/timing assertion has no Compose widget harness, the rule or store it covers is
unit-tested), different → fixed (native deliberately differs), n/a (Flutter-only).

This batch is the 62 files the other ledgers did not cover. The booru batch (`test/booru_*`,
273 tests; its perf harness is in `widgets.md`), the EH batch (`test/eh*`, 89 tests), the
stocks batch (`test/stocks_*`, 101 tests), the shared `test/plugin_*` files and the
cross-cutting files below are all led here. `home_reading_controls_test.dart` is in
`app.md` (it was led with the Home reading controls).

## test/booru_danbooru_search_test.dart

covered | booru_danbooru_search_test › goes out whole, then runs on the two rarest tags, the device checking the third | BooruClientTest.aSearchPastTheTagLimitGoesOutWholeThenRunsOnTheTwoRarestTagsTheDeviceCheckingTheThird |
covered | booru_danbooru_search_test › is shared out at once after the host has said, and keeps its split on every page | BooruClientTest.onceTheHostHasSaidASearchIsSharedOutAtOnceAndKeepsItsSplitOnEveryPage |
covered | booru_danbooru_search_test › a search that fits still goes out whole | BooruClientTest.aSearchThatFitsStillGoesOutWhole |
covered | booru_danbooru_search_test › works for a saved search in Following | BooruClientTest.aSavedSearchPastTheLimitWorksInFollowing |
covered | booru_danbooru_search_test › keeps the tag-limit error when its metatags alone are past the limit | BooruClientTest.theTagLimitStaysWhenTheMetatagsAloneArePastIt |
covered | booru_danbooru_search_test › a search the host gave up on says so | BooruClientTest.aSearchTheHostsDatabaseGaveUpOnSaysSo |
covered | booru_danbooru_search_test › come from Danbooru's autocomplete, with the other name each tag was found through | BooruClientTest.suggestionsComeFromDanboorusAutocompleteWithTheOtherNameEachTagWasFoundThrough |
covered | booru_danbooru_search_test › fall back to the tags whose names start with what is typed when autocomplete fails | BooruClientTest.suggestionsFallBackToTheTagsStartingWithWhatIsTypedWhenAutocompleteFails |
covered | booru_danbooru_search_test › are none, with no second request, when the host cannot be reached | BooruClientTest.suggestionsAreNoneWithNoSecondRequestWhenTheHostCannotBeReached |
covered | booru_danbooru_search_test › other engines keep their tag lookup | BooruClientTest.otherEnginesKeepTheirTagLookup |
covered | booru_danbooru_search_test › word matches, aliases, corrected typos and other names; anything not a tag is left out | BooruParserTest.autocompleteRowsKeepTheOtherNameEachTagWasFoundThroughAndLeaveOutWhatIsNoTag |
covered | booru_danbooru_search_test › an answer that is not a list of rows is no answer | BooruParserTest.anAutocompleteAnswerThatIsNotAListIsNoAnswer |

## test/booru_details_test.dart

covered | booru_details_test › a Moebooru download is named after the site, the post and its tags | BooruDownloadsTest.aMoebooruDownloadIsNamedAfterTheSiteThePostAndItsTags |
covered | booru_details_test › Danbooru posts carry tag kinds, file facts and family | BooruParserTest.aDanbooruPostCarriesItsFileFactsVotesAndFamily |
covered | booru_details_test › Gelbooru dates, string flags, owner and empty parent | BooruParserTest.aGelbooruPostReadsItsRubyDateEscapedTagsStringFlagsAndEmptyParent |
covered | booru_details_test › a change time is kept apart from when the post went up | BooruPostFactsTest.aTimeThePostLastChangedIsShownAsUpdatedNotPosted |
covered | booru_details_test › Moebooru author and children | BooruParserTest.aMoebooruPostReadsSAsSafeWithItsUploaderAndFamily |
covered | booru_details_test › e621 groups, votes and relationships | BooruParserTest.anE621PostReadsItsNestedFilesTagGroupsVotesAndRelationships |
covered | booru_details_test › one tag hides any post that has it | BooruQueryTest.oneBlacklistedTagHidesAnyPostThatHasIt |
covered | booru_details_test › several tags hide a post only together | BooruQueryTest.severalTagsHideAPostOnlyTogether |
covered | booru_details_test › negation and either-of | BooruQueryTest.negationAndEitherOf |
covered | booru_details_test › ratings read in the host’s own letters | BooruQueryTest.ratingsReadInTheHostsOwnLetters |
covered | booru_details_test › an entry with a metatag the app cannot check never hides | BooruQueryTest.anEntryWithAMetatagThePostCannotAnswerNeverHides |
covered | booru_details_test › score, id, sizes and counts compare like the hosts compare them | BooruQueryTest.scoreIdSizesAndCountsCompareLikeTheHostsCompareThem |
covered | booru_details_test › uploader, file type, md5, parent and source match what the post says | BooruQueryTest.uploaderFileTypeMd5ParentAndSourceMatchWhatThePostSays |
covered | booru_details_test › stored entries keep their spaces and old single tags still load | BooruQueryTest.blacklistEntriesKeepTheirSpacesAndLoseTheirDuplicates |
covered | booru_details_test › translation notes come from Danbooru and e621, active ones only, as plain text | BooruDetailParserTest.poolsAndNotesLeaveOutWhatIsDeletedInactiveOrIncomplete |
covered | booru_details_test › a post's pools come from Danbooru, and read in order through ordpool | BooruEndpointsTest.aPostsPoolsComeFromDanbooruAndItsNotesFromDanbooruAndE621 |
covered | booru_details_test › artist entries come from Danbooru and e621 | BooruEndpointsTest.artistEntriesComeFromDanbooruAndE621 |
covered | booru_details_test › the artist the tag names is picked, without deleted entries or dead links | BooruDetailParserTest.theArtistTheTagNamesIsPickedWithoutDeletedEntriesOrDeadLinks |
covered | booru_details_test › related tags come from Danbooru for any search and from e621 for one tag | BooruEndpointsTest.relatedTagsComeFromDanbooruForAnySearchAndFromE621ForOneTag |
covered | booru_details_test › related tags read in the host's order | BooruDetailParserTest.relatedTagsReadInTheHostsOrder |
covered | booru_details_test › popular lists per engine | BooruEndpointsTest.popularListsPerEngine |
covered | booru_details_test › credentials are named the way each engine expects | BooruEndpointsTest.credentialsAreNamedTheWayEachEngineExpects |
covered | booru_details_test › half a login is never sent | BooruEndpointsTest.halfALoginIsNeverSent |
covered | booru_details_test › Rule34 is asked on its API host | BooruEndpointsTest.rule34IsAskedOnItsApiHost |
covered | booru_details_test › one request names every tag of a post | BooruEndpointsTest.oneRequestNamesEveryTagOfAPost |
covered | booru_details_test › wiki pages and comments | BooruEndpointsTest.wikiPagesAndComments |
covered | booru_details_test › Moebooru tag map; Danbooru, e621 and Gelbooru tag lists with counts | BooruDetailParserTest.moebooruNamesItsTagsKindsInAMapWithoutCounts, theOtherEnginesGiveTagRowsWithKindsAndCounts |
covered | booru_details_test › wiki bodies | BooruDetailParserTest.aWikiPageIsPickedByItsExactTitle |
covered | booru_details_test › comments are oldest first and hidden ones are left out | BooruDetailParserTest.commentsAreOldestFirstWithHiddenAndEmptyOnesLeftOut |
covered | booru_details_test › DText and HTML read as plain text | BooruDetailParserTest.dtextAndHtmlReadAsPlainText |
covered | booru_details_test › days, weeks and months step and never pass today | BooruPopularTest.daysWeeksAndMonthsStepAndNeverPassToday |
covered | booru_details_test › today is the host's day, never one it has not reached | BooruPopularTest.todayIsTheHostsDayNeverOneItHasNotReached |
covered | booru_details_test › a refusal with no key set, and a busy host, each say so | BooruClientTest.aRefusalWithNoKeySentABusyHostAndALimitEachSaySo |
covered | booru_details_test › a host that asks a guest for credentials in plain text says so | BooruClientTest.aHostThatAsksAGuestForCredentialsInPlainTextSaysSo |
covered | booru_details_test › Danbooru's tag limit has its own message | BooruClientTest.danboorusTagLimitHasItsOwnKindWhenNothingFits |
covered | booru_details_test › a host that will not page that deep ends the feed instead of failing | BooruClientTest.aHostThatPagesNoDeeperEndsTheFeedInsteadOfFailing |
covered | booru_details_test › a Gelbooru 0.2 tag reply in XML gives suggestions, kinds and counts | BooruClientTest.aGelbooruForksXmlTagReplyGivesSuggestionsKindsAndCounts |
covered | booru_details_test › after a 429 the next request to that host waits, and another host does not | BooruClientTest.afterA429TheNextRequestToThatHostWaitsAndAnotherHostDoesNot |
covered | booru_details_test › Gelbooru popular shows its highest-scored posts | BooruClientTest.gelbooruPopularShowsItsHighestScoredPosts |
covered | booru_details_test › Gelbooru-family hosts get the rating limit in their own words | BooruClientTest.gelbooruFamilyHostsAreToldTheCapInTheirOwnWords |
covered | booru_details_test › engines with one popular page stop after it | BooruClientTest.enginesWithOnePopularPageStopAfterIt |
covered | booru_details_test › popular posts still pass the rating cap and blacklist | BooruClientTest.popularPostsStillPassTheRatingCapAndTheBlacklist |
covered | booru_details_test › posts with no file to show are left out, and the page still continues | BooruClient (shown: hasFile), BooruParserTest.aBannedPostAndAFlashPostHaveNoFileToShow | the client drops a post with no file and the page reads on |
covered | booru_details_test › an e621 post whose file the host withholds is left out | BooruClient (shown: hasFile), BooruParserTest.aBannedPostAndAFlashPostHaveNoFileToShow | nothing to show is dropped, whatever the host calls it |
covered | booru_details_test › a missing wiki page is no page, not an error | BooruClientTest.aMissingWikiPageIsNoPageNotAnError |
covered | booru_details_test › Moebooru kinds come from one post lookup | BooruClientTest.moebooruKindsComeFromOnePostLookup |
covered | booru_details_test › counts from the lookup join the kinds the post carries | BooruClientTest.countsFromTheLookupJoinTheKindsThePostCarries |
covered | booru_details_test › a failed tag lookup leaves the tags ungrouped and uncounted | BooruClientTest.aFailedTagLookupLeavesTheTagsUngroupedAndUncounted |
covered | booru_details_test › error text never carries the login or the key | BooruClientTest.errorTextNeverCarriesTheLoginOrTheKey |
covered | booru_details_test › every engine sends credentials under a name the error text masks | BooruEndpointsTest.credentialsGoAfterTheQueryAndEveryEngineNamesThemSoErrorTextMasksThem |
covered | booru_details_test › a post's details come from its own site | BooruClientTest.aPostsDetailsComeFromItsOwnSiteWithThatSitesKeyOnly |
covered | booru_details_test › they are sent only to the host they were entered for | BooruClientTest.credentialsAreSentOnlyToTheHostTheyWereEnteredFor |
covered | booru_details_test › typing one on another host starts a new pair there | BooruConfigTest.aKeyForAnotherHostReplacesThePairBeforeIt |
covered | booru_details_test › credentials saved before this belong to the site in use | BooruConfigTest.credentialsFromBeforeHostsWereNamedBelongToTheHostInUse |

## test/booru_download_test.dart

covered | booru_download_test › an empty template keeps the name Booru downloads had before | BooruDownloadsTest.withoutATemplateTheFileKeepsItsOwnName |
covered | booru_download_test › a template names the file from the post, keeping its extension | BooruDownloadsTest.aTemplateNamesTheFileKeepingItsExtension |
covered | booru_download_test › a batch skips files already downloaded or on their way, and fills only the room the queue has | PluginBatchesTest.aFeedsPostsAreQueuedWithoutWhatTheDownloadsHave (fresh/known), PluginBatches.fresh (room) |
covered | booru_download_test › download all asks first, then says what was queued and skipped | BooruDownloadsTest.downloadAllAsksWithTheCountLoadedThenSaysWhatWasQueuedAndSkipped |

## test/booru_feed_cache_test.dart

covered | booru_feed_cache_test › a post reads back with every field it was written with | BooruFeedCacheTest.aKeptPageReadsBackWithAllAPostSaid |
covered | booru_feed_cache_test › something that is not a post reads as nothing | BooruFeedCacheTest.aDamagedCopyReadsAsNone |
covered | booru_feed_cache_test › a page kept on the device reads back under its key only | BooruFeedCacheTest.aKeptPageReadsBackWithAllAPostSaid, aPageKeptMoreThanTwoWeeksAgoIsNotShown | the key is the feed's file name; a stale copy is not read |

## test/booru_group_membership_test.dart

covered | booru_group_membership_test › unfollowing a tag leaves a subreddit of the same name in its group | core/session SessionTest.unfollowingTakesTheAccountOutOfEveryGroup; ui/settings BackupTest | a saved search of the same name stays beside the subreddit, as XTA's groups read both |
covered | booru_group_membership_test › upgrading moves tag memberships under the prefix and keeps one another source follows | store/backup NetworkMembersTest.xtasBareMembersBecomeThePluginsAndEveryOtherStaysAsXtaWroteIt |
covered | booru_group_membership_test › a restored older backup is moved under the prefix when the plugin reloads | NetworkMembersTest (adopted), BackupTest | the same prefix migration on restore |
covered | booru_group_membership_test › a followed tag reads back as a member of a real group | BooruFollowsTest.aFollowedTagReadsBackAsAMemberNamedByItsTagWhoseTapSearchesIt |
covered | booru_group_membership_test › member ids and tags convert both ways, and unprefixed ids are tags | NetworkMembersTest.xtaId, aBareMemberXOrAnotherNetworkKeepsTooIsEachOfThem |

## test/booru_links_test.dart

covered | booru_links_test › each engine opens its own post page form | BooruLinksTest.eachEngineOpensItsOwnPostPageForm |
covered | booru_links_test › a post page is the one the post itself links to | BooruLinksTest.aPostPageIsTheOneThePostItselfLinksTo |
covered | booru_links_test › www, http and letter case do not matter | BooruLinksTest.wwwHttpAndLetterCaseDoNotMatter |
covered | booru_links_test › other pages and hosts are left alone | BooruLinksTest.otherPagesAndHostsAreLeftAlone |
covered | booru_links_test › a site added in settings is known by its own path | BooruLinksTest.aSiteTheReaderAddedIsKnownByItsOwnPath |
covered | booru_links_test › the known sites are the built-in ones and those added in settings | BooruSitesTest.anAddedSiteIsKeptAsXtaKeepsItAndItsLinksAreKnown |
covered | booru_links_test › parsePluginLink knows the built-in boorus | BooruRoutesTest.aLinkToAPostOnAKnownSiteOpensItsScreen |
covered | booru_links_test › asks the link's own site for the post by id | BooruClientTest.aLinkedPostIsAskedOfItsOwnSiteById |
covered | booru_links_test › the post comes back even when the blacklist would hide it in a feed | BooruClientTest.aLinkedPostComesBackEvenWhenTheBlacklistWouldHideItInAFeed |
covered | booru_links_test › a post over the rating cap is not shown | BooruClientTest.aLinkedPostOverTheCapOrAnotherPostTheSiteSendsIsNotTakenForIt |
covered | booru_links_test › another post the site sends back is not taken for it | BooruClientTest.aLinkedPostOverTheCapOrAnotherPostTheSiteSendsIsNotTakenForIt |
covered | booru_links_test › the saved login goes only to its own site | BooruClientTest.theSavedLoginGoesOnlyToItsOwnSite |

## test/booru_parse_test.dart

covered | booru_parse_test › preference parse keeps s as sensitive | BooruModelsTest.aStoredCapReadsSAsSensitiveButEachEngineSendsItsOwnS |
covered | booru_parse_test › Moebooru and e621 wire s means safe | BooruParserTest.aMoebooruPostReadsSAsSafeWithItsUploaderAndFamily |
covered | booru_parse_test › exceeds compares ordinal rank | BooruModelsTest.aPostOverTheCapIsNotShownAndOneWithoutARatingIs |
covered | booru_parse_test › parses a Danbooru post | BooruParserTest.aDanbooruPostCarriesItsFilesSizesRatingSourceAndTagKinds |
covered | booru_parse_test › parses Moebooru safe rating as general | BooruParserTest.aMoebooruPostReadsSAsSafeWithItsUploaderAndFamily |
covered | booru_parse_test › parses Gelbooru-style posts | BooruParserTest.aGelbooruPostReadsItsRubyDateEscapedTagsStringFlagsAndEmptyParent |
covered | booru_parse_test › Gelbooru tags and tag names read without HTML entities | BooruParserTest.aGelbooruPostReadsItsRubyDateEscapedTagsStringFlagsAndEmptyParent |
covered | booru_parse_test › Rule34 guest posts compose file URLs from directory and image | BooruParserTest.rule34GuestPostsComposeTheirFilesFromDirectoryAndImage |
covered | booru_parse_test › a numeric directory still builds the file URL | BooruParserTest.rule34GuestPostsComposeTheirFilesFromDirectoryAndImage |
covered | booru_parse_test › a video with no preview never uses the video as its thumbnail | BooruParserTest.aVideoWithoutAPreviewNeverStandsInForItsOwnThumbnail |
covered | booru_parse_test › Rule34 API host opens the public site | BooruParserTest.rule34PostsReadOnItsApiHostOpenOnThePublicSite |
covered | booru_parse_test › parses e621 nested posts payload | BooruParserTest.anE621PostReadsItsNestedFilesTagGroupsVotesAndRelationships |
covered | booru_parse_test › a GIF is shown from its original, not its still sample | BooruParserTest.aGifShowsItsOriginalNotItsStillSample |
covered | booru_parse_test › filters by max rating and muted tags | BooruClientTest.aPageKeepsThePostsTheMutedTagsHidButNeverOneOverTheCap |
covered | booru_parse_test › normalises hosts and tags | BooruEndpointsTest.hostsAreNormalisedAndRule34IsAskedOnItsApiHostButOpenedOnItsSite |
covered | booru_parse_test › guesses the engine and Rule34 API host | BooruEndpointsTest.theEngineIsGuessedFromTheHostsName |
covered | booru_parse_test › saves custom sites by host | BooruSitesTest.anAddedSiteIsKeptAsXtaKeepsItAndItsLinksAreKnown |
covered | booru_parse_test › letter s means safe, not Danbooru sensitive | BooruModelsTest.aStoredCapReadsSAsSensitiveButEachEngineSendsItsOwnS |
covered | booru_parse_test › uses sample for stills and preview for video | BooruModelsTest.aStillTilesItsSampleAndAVideoItsPreview |
covered | booru_parse_test › parses Danbooru-shaped tag rows | BooruParserTest.tagRowsOfEachEngineMapTheirNumbersToTheSameKinds |
covered | booru_parse_test › Rule34 and Gelbooru default to an explicit catalogue | BooruModelsTest.mostlyExplicitHostsStartWithAnExplicitCap |

## test/booru_post_lookups_test.dart

covered | booru_post_lookups_test › an answer is asked for once, and handed out again | BooruViewingTest.aLookupIsAskedOnceAndAgainAfterItFailed |
covered | booru_post_lookups_test › a failed answer is asked for again | BooruViewingTest.aLookupIsAskedOnceAndAgainAfterItFailed |
covered | booru_post_lookups_test › only the most recently used answers are kept | BooruViewingTest.onlyTheMostRecentlyUsedAnswersAreKept |

## test/booru_prompt_settings_test.dart

covered | booru_prompt_settings_test › a kind moves where it is dropped, and a switch keeps or leaves it out | BooruPromptTest.aKindCanBeMovedAndLeftOut |
covered | booru_prompt_settings_test › switching a kind on in settings keeps it in the prompt | BooruPromptTest.switchingMetaOnKeepsItInThePromptAfterTheOthers |
untested → ported | booru_prompt_settings_test › dragging a kind by its handle moves it in the prompt | BooruPromptTest.aKindCanBeMovedAndLeftOut | drag gesture is Compose-only; the order rule is unit-tested |

## test/booru_prompt_test.dart

covered | booru_prompt_test › kinds in order, meta left out, underscores as spaces, parentheses escaped, emoticons kept | BooruPromptTest.aPromptReadsTheTagsByKindWithSpacesAndEscapedParentheses |
covered | booru_prompt_test › the stored order reads back, kinds it does not name follow it, and it is stored the same way | BooruPromptTest.kindsTheStoredOrderDoesNotNameFollowIt |
covered | booru_prompt_test › with nothing stored, every kind but meta is kept, in the default order | BooruPromptTest.theDefaultOrderDrawsWhoAndWhatFirstAndLeavesMetaOut |

## test/booru_request_gate_test.dart

covered | booru_request_gate_test › the eleventh request in a second waits for the window to move on | BooruRequestGateTest.theEleventhRequestInASecondWaitsForTheWindowToMoveOn |
covered | booru_request_gate_test › requests wait their turn in the order they asked | BooruRequestGateTest.requestsWaitTheirTurnInTheOrderTheyAsked |
covered | booru_request_gate_test › a 429 or 5xx pauses the host, longer each time in a row, until an answer succeeds | BooruRequestGateTest.a429Or5xxPausesTheHostLongerEachTimeInARowUntilAnAnswerSucceeds |
covered | booru_request_gate_test › the pause stops growing at its cap, and Retry-After overrides it | BooruRequestGateTest.thePauseStopsGrowingAtItsCapAndRetryAfterOverridesIt |
covered | booru_request_gate_test › other refusals do not pause the host | BooruRequestGateTest.otherRefusalsDoNotPauseTheHost |
covered | booru_request_gate_test › Retry-After in seconds is read; a date is left to the doubling pause | BooruRequestGateTest.retryAfterInSecondsIsReadADateIsLeftToTheDoublingPause |

## test/booru_search_screen_test.dart

untested → ported | booru_search_screen_test › a search shows up in recent searches as soon as the field is focused again | BooruSearchTest.everySearchIsRememberedHoweverItStarted | focus timing is Compose-only |
covered | booru_search_screen_test › saving a search keeps the whole tag set | BooruClientTest.aSavedSearchFeedsFollowingAsOneMultiTagQueryAskedOnce; BooruLanding.kt SavedSearches | one entry per tag set |
untested → ported | booru_search_screen_test › a new search shows a spinner, not "no posts", until its first page arrives | BooruFeedTest.theFirstPageWithPostsIsShown | the feed's loading state is unit-tested; the spinner is Compose-only |
untested → ported | booru_search_screen_test › results the filters hid on the first pages still lead on to later pages | BooruFeedTest.aFeedStopsLookingAfterFourEmptyPagesWhileTheHostHasMore | the page walk is unit-tested |
covered | booru_search_screen_test › space finishes a tag, ✕ removes it and backspace in an empty field drops the last | BooruSearchTest.typedTagsBecomeChipsAtEachSpace, backspaceInAnEmptyFieldTakesTheLastChip |
covered | booru_search_screen_test › a picked suggestion is appended to the tags already there | BooruQueryTest.appendingSkipsRepeatsAndLetsATagTakeItsRivalsPlace |
covered | booru_search_screen_test › a tag found through another name shows that name first, and picking it adds the tag | BooruParserTest.autocompleteRowsKeepTheOtherNameEachTagWasFoundThroughAndLeaveOutWhatIsNoTag; BooruSearchTest.aSuggestionTakesThePlaceOfTheTypedTextKeepingItsOperator |
covered | booru_search_screen_test › a minus typed before a tag survives the suggestion | BooruSearchTest.aSuggestionTakesThePlaceOfTheTypedTextKeepingItsOperator |
covered | booru_search_screen_test › the exclude button puts a minus in front of the tag being typed | BooruQueryTest.operatorsToggleOnTheTagBeingTyped |
covered | booru_search_screen_test › the rating shortcut adds the host’s metatag and takes it off again | BooruQueryTest.ratingMetatagsFollowEachEnginesSpelling |
covered | booru_search_screen_test › the site's metatags are listed, and one tapped is started in the field | BooruQueryTest.twoLettersOfAMetatagOfferItOnItsSitesEngineOnlyAndItsKnownValuesAfterTheColon |
covered | booru_search_screen_test › tapping a chip moves it back into the field | BooruSearchTest.aChipCanBePutBackInTheFieldToBeChanged |
untested → ported | booru_search_screen_test › the whole query can be edited as text | BooruSearchTest.closingTheEditorGoesBackToTheLastSearch | the editor's text rule is in BooruSearch.kt |
covered | booru_search_screen_test › every way into a search is remembered | BooruSearchTest.everySearchIsRememberedHoweverItStarted |
covered | booru_search_screen_test › a search opened from a post tag is remembered | BooruSearchTest.aTagAddedFromThePostsRunsTheSearchAgain |
covered | booru_search_screen_test › an entry re-runs with one tap and moves to the top | BooruHistoryTest.aSearchGoesFirstInPlaceOfTheSameTagsInAnyOrder |
covered | booru_search_screen_test › one entry can be removed with ✕ or a swipe, and all can be cleared | BooruHistoryTest.aForgottenSearchIsGone |
covered | booru_search_screen_test › history outlives the screen and a restart | BooruHistoryTest.theHistoryKeepsTheLatestTwenty |
untested → ported | booru_search_screen_test › saved searches sit above history and run with one tap | BooruLanding.kt SavedSearches | order is Compose-only; running one is unit-tested |
untested → ported | booru_search_screen_test › back closes the field over the results before leaving | BooruSearchTest.closingTheEditorGoesBackToTheLastSearch | back handling is Compose-only |
untested → ported | booru_search_screen_test › related tags refine the search; a long press excludes | BooruDetailParserTest.relatedTagsReadInTheHostsOrder; BooruQueryTest.negationAndEitherOf | the long press is Compose-only |
covered | booru_search_screen_test › the host's related tags take the place of the ones the results share | BooruDetailParserTest.relatedTagsReadInTheHostsOrder |
covered | booru_search_screen_test › a tag on a post can be added to the search it came from | BooruSearchTest.aTagAddedFromThePostsRunsTheSearchAgain |
covered | booru_search_screen_test › a tag on a post can be excluded from the search | BooruQueryTest.negationAndEitherOf |
untested → ported | booru_search_screen_test › one tap on a post's tag searches it | BooruQueryTest.whatIsTypedIsFoundWhereAWordOfTheNameStarts | the tap is Compose-only |
covered | booru_search_screen_test › a tag can be followed and hidden from its sheet | BooruFollowsTest.aFollowIsKeptOnTheDeviceInItsNormalForm; BooruConfigTest.theBlacklistKeepsEachEntryOnceInItsNormalForm |
untested → ported | booru_search_screen_test › swiping back to a post asks for its tags and notes once, and a page change keeps the pages | BooruClientTest.aPostsCommentsPoolsAndNotesAreReadFromItsSite | the viewer's caching is unit-tested; the swipe is Compose-only |
covered | booru_search_screen_test › results say how many posts the muted tags hid, and show them on request | BooruFeedTest.thePostsTheBlacklistHidCanBeShownInTheirPlaces |
covered | booru_search_screen_test › a search the muted tags emptied still offers what they hid | BooruFeedTest.thePostsTheBlacklistHidCanBeShownInTheirPlaces |
untested → ported | booru_search_screen_test › the viewer swipes on to the next post | BooruViewingTest.theViewerGrowsOnlyWithTheSameListMadeLonger | the swipe is Compose-only |

## test/booru_search_store_test.dart

covered | booru_search_store_test › what is typed is found where a word of the name starts | BooruQueryTest.whatIsTypedIsFoundWhereAWordOfTheNameStarts |
covered | booru_search_store_test › appending skips repeats and lets a tag take its rival’s place | BooruQueryTest.appendingSkipsRepeatsAndLetsATagTakeItsRivalsPlace |
covered | booru_search_store_test › typing splits finished tags from the one still being typed | BooruQueryTest.typingSplitsFinishedTagsFromTheOneStillBeingTyped |
covered | booru_search_store_test › suggestions ask for the tag without its operator and skip metatags | BooruQueryTest.suggestionsAskForTheTagWithoutItsOperatorAndSkipMetatags |
covered | booru_search_store_test › a tag set is one history entry whatever its order | BooruHistoryTest.aSearchGoesFirstInPlaceOfTheSameTagsInAnyOrder |
covered | booru_search_store_test › operators toggle on the tag being typed | BooruQueryTest.operatorsToggleOnTheTagBeingTyped |
covered | booru_search_store_test › related tags are the ones shared most, minus the query | BooruQueryTest.relatedTagsAreTheOnesSharedMostLessThoseTheSearchNames |
covered | booru_search_store_test › rating metatags follow each engine’s spelling | BooruQueryTest.ratingMetatagsFollowEachEnginesSpelling |
covered | booru_search_store_test › each engine’s numbering maps to the same kinds | BooruParserTest.tagRowsOfEachEngineMapTheirNumbersToTheSameKinds |
covered | booru_search_store_test › remembers newest first, one entry per tag set, and survives a restart | BooruHistoryTest.aSearchGoesFirstInPlaceOfTheSameTagsInAnyOrder, theHistoryKeepsTheLatestTwenty |
covered | booru_search_store_test › forgets one entry or all of them | BooruHistoryTest.aForgottenSearchIsGone |
covered | booru_search_store_test › keeps at most the shared cap | BooruHistoryTest.theHistoryKeepsTheLatestTwenty |
covered | booru_search_store_test › a new search stops the one it replaces | BooruSearchTest.aNewSearchStopsTheOneItReplaces |
covered | booru_search_store_test › a space finishes a tag; what follows stays in the field | BooruSearchTest.typedTagsBecomeChipsAtEachSpace |
covered | booru_search_store_test › a picked suggestion is appended and keeps a typed minus | BooruSearchTest.aSuggestionTakesThePlaceOfTheTypedTextKeepingItsOperator |
covered | booru_search_store_test › every search is remembered, however it started | BooruSearchTest.everySearchIsRememberedHoweverItStarted |
covered | booru_search_store_test › backspace in an empty field drops the last chip only | BooruSearchTest.backspaceInAnEmptyFieldTakesTheLastChip |
covered | booru_search_store_test › a chip reopened for editing goes back into the field | BooruSearchTest.aChipCanBePutBackInTheFieldToBeChanged |
covered | booru_search_store_test › closing the editor returns to the results that are loaded | BooruSearchTest.closingTheEditorGoesBackToTheLastSearch |
covered | booru_search_store_test › a page keeps the posts the muted tags hid, but never one over the rating cap | BooruClientTest.aPageKeepsThePostsTheMutedTagsHidButNeverOneOverTheCap |
covered | booru_search_store_test › two letters of a metatag offer it on its site's engine only, and its known values after the colon | BooruQueryTest.twoLettersOfAMetatagOfferItOnItsSitesEngineOnlyAndItsKnownValuesAfterTheColon |
covered | booru_search_store_test › a picked metatag stays in the field for its value, and a picked value becomes a chip | BooruSearchTest.aMetatagStaysInTheFieldToBeGivenItsValue, aPickedMetatagValueBecomesAChip |
untested → ported | booru_search_store_test › a whole tag set is saved as one entry and removed again | BooruLanding.kt SavedSearches; BooruClientTest.aSavedSearchFeedsFollowingAsOneMultiTagQueryAskedOnce | the saved-search list is written through settings; its write has no separate unit test |
covered | booru_search_store_test › a save is not lost when a reload starts right after it | BooruFollowsTest.aSaveIsNotLostWhenAReloadStartsRightAfterIt | the same serialised write guards follows |
covered | booru_search_store_test › a saved search feeds Following as one multi-tag query | BooruClientTest.aSavedSearchFeedsFollowingAsOneMultiTagQueryAskedOnce |
covered | booru_search_store_test › Following counts followed tags that failed, and fails only when all did | BooruClientTest.followingCountsTheQueriesThatFailedAndFailsOnlyWhenAllDid |
covered | booru_search_store_test › Following fails with the error page when every followed tag fails on the first load | BooruClientTest.followingHasOnePage, followingCountsTheQueriesThatFailedAndFailsOnlyWhenAllDid |

## test/booru_site_profiles_test.dart

covered | booru_site_profiles_test › each site gets back its own rating cap, blacklist, login and key | BooruSitesTest.eachSiteKeepsItsOwnCapAndBlacklist, eachSiteKeepsItsOwnLoginAndSendsItNowhereElse |
covered | booru_site_profiles_test › staying on a site swaps nothing | BooruSitesTest.stayingOnASiteSwapsNothing |
covered | booru_site_profiles_test › logins are kept where exports and crash reports leave them out; ratings and blacklists are not | store/settings SettingsTest (secret prefs), ui/settings BackupTest | secrets are masked in exports; the rest ride in the document |
covered | booru_site_profiles_test › a removed site takes what it kept with it | BooruSitesTest.aRemovedSiteTakesItsLoginWithIt |

## test/booru_tag_limit_test.dart

covered | booru_tag_limit_test › tags, excluded and either tags and order: count; the metatags Danbooru lets through do not | BooruTagLimitTest.tagsExcludedAndEitherTagsAndOrderCountTheMetatagsDanbooruLetsThroughDoNot |
covered | booru_tag_limit_test › a metatag it was not seen letting through counts, to be safe | BooruTagLimitTest.aMetatagNotSeenLettingThroughCountsToBeSafe |
covered | booru_tag_limit_test › a search that fits goes to the host whole | BooruTagLimitTest.aSearchThatFitsGoesToTheHostWhole |
covered | booru_tag_limit_test › the host gets the tags with the fewest posts, in the order they were typed | BooruTagLimitTest.theHostGetsTheTagsWithTheFewestPostsInTheOrderTheyWereTyped |
covered | booru_tag_limit_test › without counts the tags typed first go to the host | BooruTagLimitTest.withoutCountsTheTagsTypedFirstGoToTheHost |
covered | booru_tag_limit_test › a tag the host does not list, an alias or a typo, goes to the host first | BooruTagLimitTest.aTagTheHostDoesNotListAnAliasOrATypoGoesToTheHostFirst |
covered | booru_tag_limit_test › metatags stay with the host and take their room; free ones take none | BooruTagLimitTest.metatagsStayWithTheHostAndTakeTheirRoomFreeOnesTakeNone |
covered | booru_tag_limit_test › a wildcard stays with the host: the device cannot match it by name | BooruTagLimitTest.aWildcardStaysWithTheHostTheDeviceCannotMatchItByName |
covered | booru_tag_limit_test › an either group stays together, on the host when it fits and on the device when not | BooruTagLimitTest.anEitherGroupStaysTogetherOnTheHostWhenItFitsAndOnTheDeviceWhenNot |
covered | booru_tag_limit_test › excluded tags go to the host last | BooruTagLimitTest.excludedTagsGoToTheHostLast |
covered | booru_tag_limit_test › nothing can be shared out when the metatags alone are more than the host takes | BooruTagLimitTest.nothingCanBeSharedOutWhenTheMetatagsAloneAreMoreThanTheHostTakes |
covered | booru_tag_limit_test › is read from what Danbooru says | BooruTagLimitTest.theLimitIsReadFromWhatDanbooruSays |
covered | booru_tag_limit_test › is Danbooru's lowest when its error names no number, and unknown for anything else | BooruTagLimitTest.theLimitIsDanboorusLowestWhenItsErrorNamesNoNumberAndUnknownForAnythingElse |

## test/booru_tile_variant_test.dart

covered | booru_tile_variant_test › a tile gets the smallest still variant that fills it, never the original | BooruParserTest.aTileGetsTheSmallestStillVariantThatFillsItNeverTheOriginal |
covered | booru_tile_variant_test › a video's tile gets a still variant, not its video sample | BooruModelsTest.aStillTilesItsSampleAndAVideoItsPreview |
covered | booru_tile_variant_test › hosts without variants keep the picture tiles use today | BooruTilesTest.hostsWithoutVariantsKeepThePictureTilesUseToday |
covered | booru_tile_variant_test › a grid tile asks for the variant its width needs, and decodes at that width | BooruTilesTest.aGridTileAsksForTheVariantItsPixelWidthNeeds |
covered | booru_tile_variant_test › small thumbnails still mean the preview | BooruTilesTest.smallThumbnailsStillMeanThePreview |

## test/booru_viewer_test.dart

untested → ported | booru_viewer_test › grows with its feed and asks for more near the end | BooruFeedTest.theFirstPageWithPostsIsShown; ui/plugins Lists.kt ReadsMoreNearEnd | the shared paged list reads the next page near the end |
covered | booru_viewer_test › after a site change a feed starts over instead of growing | BooruFeedTest.aFeedWhoseSiteChangedStartsOverInsteadOfAddingToIt |
covered | booru_viewer_test › a new rating cap or blacklist entry makes a feed stale | BooruFeedTest.aNewRatingCapOrBlacklistEntryMakesAFeedStale |
covered | booru_viewer_test › a failed page is asked for once, until the reader retries | BooruFeedTest.aNextPageThatFailsWaitsToBeAskedAgain |
covered | booru_viewer_test › a page asked for before a refresh never lands after it | BooruFeedTest.aPageAskedForBeforeARefreshNeverLandsAfterIt |
covered | booru_viewer_test › of two overlapping refreshes, the newer one decides what the feed holds | BooruFeedTest.ofTwoOverlappingRefreshesTheNewerDecides |
covered | booru_viewer_test › a failed refresh keeps the posts from before, and says why until a refresh lands | BooruFeedTest.aRefreshThatFailsKeepsThePostsUnderANotice |
covered | booru_viewer_test › a failed first load, another site or a changed query gets the error page, not posts from before | BooruFeedTest.aChangedQueryThatFailsShowsTheErrorNotTheOldPosts |
covered | booru_viewer_test › a refresh that fails while the next page loads leaves the footer able to load it again | BooruFeedTest.aRefreshThatFailsKeepsThePostsUnderANotice, aNextPageThatFailsWaitsToBeAskedAgain |
covered | booru_viewer_test › a feed keeps its first page on the device, and shows it when a launch cannot load | BooruFeedTest.theCopyKeptOnTheDeviceStandsInWhenTheFirstLoadFails |
covered | booru_viewer_test › posts the muted tags hid are counted, and shown in their places on request | BooruFeedTest.thePostsTheBlacklistHidCanBeShownInTheirPlaces |
covered | booru_viewer_test › pages the muted tags emptied are skipped, and what they hid still counts | BooruFeedTest.aFeedStopsLookingAfterFourEmptyPagesWhileTheHostHasMore |
covered | booru_viewer_test › no next page is asked for while a refresh is on its way | BooruFeedTest.noNextPageIsAskedForWhileARefreshIsOnItsWay |
covered | booru_viewer_test › a closed feed asks for no more pages, and what was on its way is dropped | BooruFeedTest.aClosedFeedAsksForNoMorePagesAndDropsWhatWasOnItsWay |
covered | booru_viewer_test › a closed load ignores its late answer | BooruFeedTest.aClosedFeedAsksForNoMorePagesAndDropsWhatWasOnItsWay |
covered | booru_viewer_test › the feed skips posts it already shows | BooruFeedTest.aNextPageAddsOnlyPostsNotShownYet |
covered | booru_viewer_test › a host that ignores the page number ends the feed | BooruFeedTest.aHostThatSendsTheSamePostsForEveryPageEndsTheFeed |
covered | booru_viewer_test › the next and the previous picture are loaded ahead, never a video | BooruViewingTest.theViewerLoadsTheNeighboursPicturesAheadButNoVideo |
covered | booru_viewer_test › a list only counts as extended when it starts the same | BooruViewingTest.theViewerGrowsOnlyWithTheSameListMadeLonger |
covered | booru_viewer_test › the post menu copies the tags as a prompt, in the order chosen in settings | BooruPromptTest.aPromptReadsTheTagsByKindWithSpacesAndEscapedParentheses |
covered | booru_viewer_test › name the artist, characters and series first, and leave meta tags out | BooruPromptTest.theDefaultOrderDrawsWhoAndWhatFirstAndLeavesMetaOut |
covered | booru_viewer_test › a grid tile is read as a post with its first tags | BooruFormatsTest.aLabelNamesWhoAndWhatIsDrawnFirstAndNoMetaTags |
covered | booru_viewer_test › a related thumbnail is read the same way | BooruFormatsTest.aLabelNamesWhoAndWhatIsDrawnFirstAndNoMetaTags |
covered | booru_viewer_test › kinds order the groups; unknown tags count as general; names sort A–Z | BooruModelsTest.kindsOrderTheGroupsUnknownTagsCountAsGeneralAndNamesSortAToZ |
covered | booru_viewer_test › by use, the most used come first and uncounted tags last | BooruModelsTest.byUseTheMostUsedComeFirstAndUncountedTagsLast |
covered | booru_viewer_test › chips read like the host: spaces, short counts, kind colours | BooruFormatsTest.countsBesideATagAreShortToThreeFigures; plugins/booru BooruText.kt tokenText/tagColor |
covered | booru_viewer_test › the artist for "More from" skips placeholder artist tags | BooruModelsTest.theArtistForMoreFromSkipsPlaceholderArtistTags |
covered | booru_viewer_test › family query names the parent | BooruQueryTest.queriesForAPostsFamilyAndAPool |
covered | booru_viewer_test › facts, grouped tags and the artist strip | BooruPostFactsTest.factsLeaveOutWhatTheHostDidNotSend; BooruDetailParserTest |
covered | booru_viewer_test › tags sort by name or by use, and the choice is kept | BooruConfigTest.theStoredChoicesReadBack |
covered | booru_viewer_test › an artist tag opens the artist's page: posts, other names, links and follow | BooruDetailParserTest.theArtistTheTagNamesIsPickedWithoutDeletedEntriesOrDeadLinks; BooruEndpointsTest.artistEntriesComeFromDanbooruAndE621 |
covered | booru_viewer_test › a post's pools are listed, and one opens to read in order | BooruDetailParserTest.poolsAndNotesLeaveOutWhatIsDeletedInactiveOrIncomplete; BooruEndpointsTest.aPostsPoolsComeFromDanbooruAndItsNotesFromDanbooruAndE621 |
covered | booru_viewer_test › a post seen again in the same viewer asks for its pools and notes once | BooruViewingTest.aLookupIsAskedOnceAndAgainAfterItFailed |
untested → ported | booru_viewer_test › translation notes show over the picture once switched on | BooruDetailParserTest.poolsAndNotesLeaveOutWhatIsDeletedInactiveOrIncomplete | the toggle is Compose-only |
covered | booru_viewer_test › comments load when opened | BooruClientTest.aPostsCommentsPoolsAndNotesAreReadFromItsSite |
covered | booru_viewer_test › a post from another site is looked up on that site | BooruClientTest.aLinkedPostIsAskedOfItsOwnSiteById |
covered | booru_viewer_test › the wiki opens from a tag | BooruDetailParserTest.aWikiPageIsPickedByItsExactTitle; BooruClientTest.aMissingWikiPageIsNoPageNotAnError |
covered | booru_viewer_test › facts leave out what the host did not send | BooruPostFactsTest.factsLeaveOutWhatTheHostDidNotSend |
covered | booru_viewer_test › a time the post last changed is shown as updated, not posted | BooruPostFactsTest.aTimeThePostLastChangedIsShownAsUpdatedNotPosted |
covered | booru_viewer_test › scales and dates change the query | BooruPopularTest.daysWeeksAndMonthsStepAndNeverPassToday; BooruEndpointsTest.popularListsPerEngine |
untested → ported | booru_viewer_test › a host without a popular list says what it shows instead | BooruClientTest.gelbooruPopularShowsItsHighestScoredPosts | XTA's sentence (Strings.popular_top) is Compose-only |
covered | booru_viewer_test › columns, details and blur follow the settings | BooruConfigTest.theGridFitsTheWidthUnlessColumnsWereChosen, adultPostsAreBlurredOnlyWhenAsked |
covered | booru_viewer_test › settings take a multi-tag blacklist entry and a column count | BooruConfigTest.theBlacklistKeepsEachEntryOnceInItsNormalForm, theGridFitsTheWidthUnlessColumnsWereChosen |
untested → ported | booru_viewer_test › the grid footer offers a retry after a failed page, then marks the end | BooruFeedTest.aNextPageThatFailsWaitsToBeAskedAgain | the footer is Compose-only |
untested → ported | booru_viewer_test › Following says when some followed tags did not load, in the tag row, and offers a retry | BooruClientTest.followingCountsTheQueriesThatFailedAndFailsOnlyWhenAllDid | the row is Compose-only |
untested → ported | booru_viewer_test › a video waits for a tap when media loads only on tap | ui/media MediaPrefs rules | the setting is Compose-only |
untested → ported | booru_viewer_test › Latest shows the skeleton, not "no posts", until its first page arrives | BooruFeedTest.theFirstPageWithPostsIsShown | the skeleton is Compose-only | drawn now: GridSkeleton(columns = 2) in BooruFeedView (ui/common Skeletons.kt, SkeletonMotionTest.aGridSkeletonsTilesFallInRows…)
covered | booru_viewer_test › Latest keeps its posts in place when a refresh fails, under a floating notice with Retry | BooruFeedTest.aRefreshThatFailsKeepsThePostsUnderANotice |
covered | booru_viewer_test › Latest opened without a network shows the page kept from last time | BooruFeedTest.theCopyKeptOnTheDeviceStandsInWhenTheFirstLoadFails |
covered | booru_viewer_test › Latest says how many posts the muted tags hid, and shows them on request | BooruFeedTest.thePostsTheBlacklistHidCanBeShownInTheirPlaces |
untested → ported | booru_viewer_test › Latest asks for its first page in its first frame | BooruFeedTest.theFirstPageWithPostsIsShown | the first-frame timing is Compose-only |
untested → ported | booru_viewer_test › leaving settings while a site or a mute is being saved throws nothing | BooruConfigTest (serialised settings writes) | the screen's disposal is Compose-only |
covered | booru_viewer_test › the rating cap goes past Sensitive only once the reader confirms they are an adult | BooruModelsTest.aCapPastSensitiveNeedsTheReaderToConfirmTheyAreAnAdult |
covered | booru_viewer_test › a mostly explicit site asks before it allows explicit posts | BooruModelsTest.mostlyExplicitHostsStartWithAnExplicitCap; BooruSitesTest.aMostlyExplicitSiteAsksBeforeItShowsAdultPosts |
covered | booru_viewer_test › settings keep the login and key with the site they belong to | BooruConfigTest.aLoginGoesOnlyToTheHostItWasEnteredFor |
covered | booru_viewer_test › each site's muted tags come back with it | BooruSitesTest.eachSiteKeepsItsOwnCapAndBlacklist |
covered | booru_viewer_test › thumbnails and full screen use the chosen file size | BooruPostFactsTest.thumbnailsAndFullScreenUseTheChosenFileSize |
covered | booru_viewer_test › animations sampled as webm play as video | BooruParserTest.aDanbooruAnimationPlaysItsWebmSampleAndItsTileStaysAStill |
covered | booru_viewer_test › a booru post link opens in the viewer | BooruRoutesTest.aLinkToAPostOnAKnownSiteOpensItsScreen |
covered | booru_viewer_test › a post the viewer cannot show is left to the browser | BooruRoutesTest.aRouteThisPluginDoesNotHaveIsNone |
untested → ported | booru_viewer_test › booru links stay with the browser while the plugin is off | BooruRoutesTest.aRouteThisPluginDoesNotHaveIsNone; ui/links rules | the enabled switch is in the host |
covered | booru_viewer_test › marks the posts that came in since the last visit, once | BooruHistoryTest.onlyPostsAfterTheLastVisitAreNew |
covered | booru_viewer_test › a first visit marks nothing new | BooruHistoryTest.nothingIsNewOnASiteNotVisitedBeforeNorAPostWithoutANumber |

## test/booru_visits_test.dart

covered | booru_visits_test › nothing is new on a site not visited before | BooruHistoryTest.nothingIsNewOnASiteNotVisitedBeforeNorAPostWithoutANumber |
covered | booru_visits_test › a post above the newest seen on its site is new | BooruHistoryTest.onlyPostsAfterTheLastVisitAreNew |
covered | booru_visits_test › a visit keeps each site's newest post seen | BooruHistoryTest.followingNotesTheNewestPostOfEachSite |
covered | booru_visits_test › seeing nothing writes nothing | BooruHistoryTest.seeingNothingChangesNothingAndABrokenRecordIsNoVisit |
covered | booru_visits_test › a broken record reads as no visit | BooruHistoryTest.seeingNothingChangesNothingAndABrokenRecordIsNoVisit |

## test/eh_client_test.dart

covered | eh_client_test › image pages ask EH for 2400px resamples with a desktop UA | EhClientTest.imagePagesAskForTheLargestResamplesAsADesktopBrowser |
covered | eh_client_test › search and toplist hit the site query shapes EhViewer uses | EhClientTest.searchToplistAndWatchedHitTheSitesQueryShapes |

## test/eh_detail_test.dart

covered | eh_detail_test › tags keep their spaces and their namespaces | EhParserTest.tagsKeepTheirSpacesAndTheirNamespaces |
covered | eh_detail_test › dashed tags are weak | EhParserTest.dashedTagsAreWeak |
covered | eh_detail_test › facts from the detail table | EhParserTest.factsFromTheDetailTable |
covered | eh_detail_test › a bare tag cell falls back to its id | EhParserTest.aBareTagCellFallsBackToItsId |
covered | eh_detail_test › sizes and favorite counts read as the site writes them | EhParserTest.sizesAndFavoriteCountsReadAsTheSiteWritesThem |
covered | eh_detail_test › a tag searches for exactly itself | EhModelsTest.aTagSearchesForExactlyItself |
covered | eh_detail_test › groups follow the site order with namespaceless tags last | EhModelsTest.groupsFollowTheSiteOrderWithNamespacelessTagsLast |
covered | eh_detail_test › rating stars, tags and language come off the row | EhParserTest.ratingStarsTagsAndLanguageComeOffTheRow |
covered | eh_detail_test › star sprite offsets | EhParserTest.starSpriteOffsets |
covered | eh_detail_test › language badges | EhModelsTest.translationMarksAreNoLanguage |

## test/eh_gallery_screen_test.dart

untested → ported | eh_gallery_screen_test › the header and facts show what the site sent, with spoken labels | EhParserTest.detail, factsFromTheDetailTable; EhRoutesTest.sizesBadgesAndStarsAreWrittenAsXtaWritesThem | the spoken labels are Compose-only |
covered | eh_gallery_screen_test › the uploader opens a search for their galleries | EhQueryTest.anUploadersGalleriesAreASearch |
untested → ported | eh_gallery_screen_test › on a phone each namespace heads its own tags | plugins/ehviewer EhGalleryContent.kt TagGroup | layout is Compose-only |
untested → ported | eh_gallery_screen_test › on a wide screen the namespaces form a label column | EhGalleryContent.kt TagGroup (beside) | layout is Compose-only |
covered | eh_gallery_screen_test › a tag searches for exactly itself and a long press copies it | EhModelsTest.aTagSearchesForExactlyItself | the long press is Compose-only |
covered | eh_gallery_screen_test › $name opens the reader at page $page | EhRoutesTest.eachScreenOpensFromItsRoute; EhReaderModelTest.theReaderOpensWithinTheGalleryAndSettlesOnlyOnANewPage |
untested → ported | eh_gallery_screen_test › three comments show, then all of them in a sheet | EhParserTest.commentsReadAuthorBodyScoreAndTheUploadersMark | the sheet is Compose-only |
untested → ported | eh_gallery_screen_test › bones stand in below the header until the detail arrives | EhGalleryLoaderTest.theFirstLoadReadsTheDetailAndItsFirstPreviewSheet | the skeleton is Compose-only |
covered | eh_gallery_screen_test › a failed load shows the error with a retry that works | EhGalleryLoaderTest.aFailedLoadKeepsTheErrorUntilARetrySucceeds |
covered | eh_gallery_screen_test › pulling down reloads; a failed reload keeps the page and says why | EhGalleryLoaderTest.aSheetThatLandsAfterAReloadIsDropped |
untested → ported | eh_gallery_screen_test › a narrow phone at large text scrolls through without overflow, loading previews | plugins/ehviewer EhGalleryScreen.kt, EhGalleryContent.kt | layout is Compose-only |

## test/eh_gallery_store_test.dart

covered | eh_gallery_store_test › the first load reads the detail and its first preview sheet | EhGalleryLoaderTest.theFirstLoadReadsTheDetailAndItsFirstPreviewSheet |
covered | eh_gallery_store_test › preview sheets append in order until the last one | EhGalleryLoaderTest.previewSheetsAppendInOrderUntilTheLastOne |
covered | eh_gallery_store_test › a failed sheet stops the scroll trigger but not the button | EhGalleryLoaderTest.aFailedSheetStopsTheScrollTriggerButNotTheButton |
covered | eh_gallery_store_test › continue page comes from history, past the first page only | EhGalleryLoaderTest.theContinuePageComesFromHistoryPastTheFirstPageOnly |
covered | eh_gallery_store_test › a failed load keeps the error until a retry succeeds | EhGalleryLoaderTest.aFailedLoadKeepsTheErrorUntilARetrySucceeds |
covered | eh_gallery_store_test › a sheet that lands after a reload is dropped | EhGalleryLoaderTest.aSheetThatLandsAfterAReloadIsDropped |
covered | eh_gallery_store_test › merging keeps each page once, in order | EhGalleryLoaderTest.mergingKeepsEachPageOnceInOrder |
covered | eh_gallery_store_test › tiles read their sheet offset and size from the style | EhParserTest.tilesReadTheirSheetOffsetAndSizeFromTheStyle |
covered | eh_gallery_store_test › a lone thumbnail without an offset still has its image | EhParserTest.aLoneThumbnailWithoutAnOffsetStillHasItsImage |
covered | eh_gallery_store_test › the sprite crop is the tile in sheet pixels, never past the sheet | EhModelsTest.theSpriteCropIsTheTileInSheetPixelsNeverPastTheSheet |

## test/eh_page_resolver_test.dart

covered | eh_page_resolver_test › a full first sheet gives its own length | EhPageResolverTest.aFullFirstSheetGivesItsOwnLength |
covered | eh_page_resolver_test › a later sheet gives it by the page it starts at | EhPageResolverTest.aLaterSheetGivesItByThePageItStartsAt |
covered | eh_page_resolver_test › a single sheet, an empty one or one that does not line up says nothing | EhPageResolverTest.aSingleSheetAnEmptyOneOrOneThatDoesNotLineUpSaysNothing |
covered | eh_page_resolver_test › only the unbroken run from page 1 counts as the first sheets | EhPageResolverTest.onlyTheUnbrokenRunFromPage1CountsAsTheFirstSheets |
covered | eh_page_resolver_test › fetches the sheet a page sits on, sized from the previews it was given | EhPageResolverTest.fetchesTheSheetAPageSitsOnSizedFromThePreviewsItWasGiven |
covered | eh_page_resolver_test › a wrong guess at the sheet size corrects itself from the sheet it got | EhPageResolverTest.aWrongGuessAtTheSheetSizeCorrectsItselfFromTheSheetItGot |
covered | eh_page_resolver_test › caches pages and shares a request already in flight | EhPageResolverTest.cachesPagesAndSharesARequestAlreadyInFlight |
covered | eh_page_resolver_test › reading on follows the next-page link instead of a preview sheet | EhPageResolverTest.readingOnFollowsTheNextPageLinkInsteadOfAPreviewSheet |
covered | eh_page_resolver_test › reload asks for another image server with the page nl key | EhPageResolverTest.reloadAsksForAnotherImageServerWithThePagesNlKey |
covered | eh_page_resolver_test › a failed page stays failed until it is retried | EhPageResolverTest.aFailedPageStaysFailedUntilItIsRetried |
covered | eh_page_resolver_test › pages outside the gallery are never asked for | EhPageResolverTest.pagesOutsideTheGalleryAreNeverAskedFor |
covered | eh_page_resolver_test › a page no sheet lists fails as not found | EhPageResolverTest.aPageNoSheetListsFailsAsNotFound |

## test/eh_parse_test.dart

covered | eh_parse_test › prefers Japanese only when asked | EhModelsTest.theJapaneseTitleOnlyWhenAskedFor |
covered | eh_parse_test › parses labels and builds exclude mask | EhModelsTest.categoriesParseTheSitesLabelsAndXtasStoredNamesAndBuildTheExcludeMask |
covered | eh_parse_test › extracts galleries from compact list HTML | EhParserTest.aCompactListRowReadsItsGalleryAndTheNextPage |
covered | eh_parse_test › reads title tags and previews | EhParserTest.aGalleryPageReadsItsTitlesTagsAndPreviews |
covered | eh_parse_test › reads image and navigation | EhParserTest.anImagePageReadsItsImageNeighboursCounterAndReloadKey |
covered | eh_parse_test › reads fullimg.php and uses it only when signed in | EhParserTest.fullimgPhpIsTheOriginalAndOnlyASignedInReaderUsesIt |
covered | eh_parse_test › reads the current /fullimg/gid/page/key/file href | EhParserTest.theCurrentFullimgPathIsTheOriginalToo |
covered | eh_parse_test › adds a 2400px uconfig when none is present | EhSettingsTest.a2400pxUconfigIsAddedWhenNoneIsThereAndTheConsentGoesAsTheSitesOwnNwCookieToo |
covered | eh_parse_test › upgrades a mobile-sized uconfig without dropping other flags | EhSettingsTest.aMobileSizedUconfigIsUpgradedWithoutDroppingOtherFlags |
covered | eh_parse_test › reads author body score and uploader flag | EhParserTest.commentsReadAuthorBodyScoreAndTheUploadersMark |
covered | eh_parse_test › adds language tag and min rating | EhQueryTest.aSearchAddsItsLanguageTagAndMinimumRating |
covered | eh_parse_test › appends nl for a broken-image reload | EhParserTest.theReloadKeyComesFromTheNlCall |
covered | eh_parse_test › reads token gid and page | EhParserTest.aPageLinkReadsTokenGidAndPage |
covered | eh_parse_test › maps gmetadata JSON | EhParserTest.gdataMapsTheApisGalleriesAndLeavesOutOnesItCouldNotFind |

## test/eh_reader_gestures_test.dart

covered | eh_reader_gestures_test › a loaded page still turns on a tap, and zooms on a double tap | ui/plugins Media.kt ZoomableImage; EhReaderModelTest.tapZonesTurnSmoothlyAndTheSliderJumps | 2.5× double tap, and a single tap is held through the double-tap timeout, as XTA |

## test/eh_reader_screen_test.dart

covered | eh_reader_screen_test › opens at the initial page and remembers it | EhReaderModelTest.theReaderOpensWithinTheGalleryAndSettlesOnlyOnANewPage; EhKeptTest.theHistoryRemembersThePageLastReadAndNeverBeforeTheFirst |
covered | eh_reader_screen_test › left to right: swipe left and tap the right third for the next page | EhReaderModelTest.theOuterThirdsTurnPagesMirroredRightToLeft |
covered | eh_reader_screen_test › right to left: swipe right and tap the left third for the next page | EhReaderModelTest.theOuterThirdsTurnPagesMirroredRightToLeft |
covered | eh_reader_screen_test › tapping the middle hides the bars, and tapping again brings them back | EhReaderModelTest.anotherModeStaysOnThePageAndTheBarsToggle |
covered | eh_reader_screen_test › the slider previews pages while dragged and jumps once on release | EhReaderModelTest.tapZonesTurnSmoothlyAndTheSliderJumps |
covered | eh_reader_screen_test › switching to vertical shows the pages in one list from the current page | EhReaderModelTest.anotherModeStaysOnThePageAndTheBarsToggle |
covered | eh_reader_screen_test › vertical reading opens at the page and follows the scroll | EhReaderModelTest.theColumnIsOnItsTopPageOrTheLastOnceItShowsWhole |
untested → ported | eh_reader_screen_test › long-press opens the page sheet; reload asks another server | EhPageResolverTest.reloadAsksForAnotherImageServerWithThePagesNlKey | the sheet is Compose-only |
covered | eh_reader_screen_test › a page that will not load offers retry and another server | EhPageResolverTest.aFailedPageStaysFailedUntilItIsRetried |
untested → ported | eh_reader_screen_test › the page bar fits a narrow screen with large text | plugins/ehviewer EhReaderChrome.kt | layout is Compose-only |

## test/eh_reader_store_test.dart

covered | eh_reader_store_test › opens at the initial page, held inside the gallery | EhReaderModelTest.theReaderOpensWithinTheGalleryAndSettlesOnlyOnANewPage |
covered | eh_reader_store_test › turning and jumping stay inside the gallery and settle once per page | EhReaderModelTest.theReaderOpensWithinTheGalleryAndSettlesOnlyOnANewPage, tapZonesTurnSmoothlyAndTheSliderJumps |
covered | eh_reader_store_test › the slider previews a page without settling, and settles once on release | EhReaderModelTest.tapZonesTurnSmoothlyAndTheSliderJumps |
covered | eh_reader_store_test › a swipe moves the page but not the vertical anchor; a jump moves both | EhReaderModelTest.aPageTheViewMovedToIsNotJumpedToAgain |
covered | eh_reader_store_test › the reading mode comes from the pref and is saved back to it | EhConfigTest.theReadingPreferencesHaveXtasDefaults; EhReaderModelTest.anotherModeStaysOnThePageAndTheBarsToggle |
covered | eh_reader_store_test › the middle third shows and hides the bars | EhReaderModelTest.anotherModeStaysOnThePageAndTheBarsToggle |
covered | eh_reader_store_test › the outer thirds turn pages, mirrored when reading right to left | EhReaderModelTest.theOuterThirdsTurnPagesMirroredRightToLeft |
covered | eh_reader_store_test › preloads the next three pages and the one before, inside the gallery | EhPageResolverTest.preloadsTheNextThreePagesAndTheOneBeforeInsideTheGallery |
covered | eh_reader_store_test › a vertical reader is on the topmost page in view, or on a short last page once whole | EhReaderModelTest.theColumnIsOnItsTopPageOrTheLastOnceItShowsWhole |
covered | eh_reader_store_test › visibility from a list a jump replaced, or from a paged mode, is ignored | EhReaderModelTest.aPageTheViewMovedToIsNotJumpedToAgain |
covered | eh_reader_store_test › the page count falls back to the furthest page known | EhPageResolverTest.thePageCountFallsBackToTheFurthestPageKnown |

## test/eh_search_test.dart

covered | eh_search_test › the term being typed starts after the last finished term | EhQueryTest.theTermBeingTypedStartsAfterTheLastFinishedTerm |
covered | eh_search_test › suggestions are asked for the whole term, then its last words | EhQueryTest.suggestionsAreAskedForTheWholeTermThenItsLastWords |
covered | eh_search_test › a suggestion replaces its term and keeps a typed operator | EhQueryTest.aSuggestionReplacesItsTermAndKeepsATypedOperator |
covered | eh_search_test › suggestions read the tag map, prefer master tags and drop repeats | EhParserTest.suggestionsReadTheTagMapPreferMasterTagsAndDropRepeats |
covered | eh_search_test › the longest term the site knows wins, and only that span is replaced | EhSearchTest.theLongestTermTheSiteKnowsWinsAndOnlyThatSpanIsReplaced |
covered | eh_search_test › searches are remembered; a changed filter runs the search again | EhSearchTest.searchesAreRememberedAndAChangedFilterRunsTheSearchAgainUnawaited |
covered | eh_search_test › the last category cannot be switched off | EhSearchTest.theLastCategoryCannotBeSwitchedOff |
covered | eh_search_test › a suggestion fills the field and history entries can be removed | EhSearchTest.aSearchForNothingRunsNothingAndAFinishedTermAsksForNoTags; EhApp.forgetSearch (PluginSearchHistoryTest) |

## test/stocks_crypto_address_test.dart

covered | stocks_crypto_address_test › an EVM address is recognised whatever its checksum casing | CryptoAssetTest.evmCasingCanonicalisesWhileOtherAddressesKeepTheirCase |
covered | stocks_crypto_address_test › a Solana mint is recognised and keeps its case | CryptoAssetTest.aSolanaMintDifferingOnlyInCaseIsADifferentToken |
covered | stocks_crypto_address_test › a cashtag prefix and explorer links are looked through | CryptoAssetTest.aSearchKeepsTheContractsCaseAndDropsOnlyTheCashtagSign |
covered | stocks_crypto_address_test › things that only look close are not addresses | CryptoAssetTest.evmCasingCanonicalisesWhileOtherAddressesKeepTheirCase, aRealFigureIsFiniteAndAtLeastItsFloor | a malformed address invents no token |
covered | stocks_crypto_address_test › rows show the network and a shortened address | QuoteRowsTest.aWatchlistRowIsATokenByItsSymbolAndNetworkOrATickerByItsName |
covered | stocks_crypto_address_test › addresses compare case-insensitively only on EVM | CryptoAssetTest.evmCasingCanonicalisesWhileOtherAddressesKeepTheirCase |
covered | stocks_crypto_address_test › one market per network, from either side of a pair | CryptoAssetTest.oneMarketPerNetworkFromEitherSideOfAPair |
covered | stocks_crypto_address_test › pool figures are read with the price | CryptoAssetTest.poolFiguresAreReadWithThePrice |
covered | stocks_crypto_address_test › a Solana mint differing only in case is a different token | CryptoAssetTest.aSolanaMintDifferingOnlyInCaseIsADifferentToken |
covered | stocks_crypto_address_test › the client searches the address and prices quote-only networks | CryptoClientTest.aPastedContractIsSearchedAndItsQuoteOnlyNetworksArePricedThroughTheirPools |
covered | stocks_crypto_address_test › pasting an address switches the add sheet to crypto at once | StocksSearchTest.aPastedAddressSwitchesToTokensAtOnceAndAsksForNoStock |
covered | stocks_crypto_address_test › search the contract alone unless the cashtag is chosen | CryptoAssetTest.postsForATokenSearchTheContractAloneUnlessTheCashtagIsChosen |
covered | stocks_crypto_address_test › a symbol X cannot write as a cashtag is never added | WatchlistTest.anythingElseIsNoTicker |
covered | stocks_crypto_address_test › the cashtag choice survives encoding; old rows read as off | CryptoAssetTest.theCashtagChoiceSurvivesTheStoredFormAndOldRowsReadAsOff |
covered | stocks_crypto_address_test › the watchlist feed follows each token's choice | WatchlistTest.theWatchlistFeedFollowsEachTokensCashtagChoice |

## test/stocks_crypto_storage_test.dart

covered | stocks_crypto_storage_test › legacy stocks and equal-symbol tokens survive reload and backup mapping | WatchlistRowsTest.theRowsAreXtasBackupRowsAndReadBack; WatchlistTest.storedRowsReadAsTokensByIdAndTickersAsStoredByLabelThenInTheReadersOrder |
covered | stocks_crypto_storage_test › duplicate add preserves feed choice; removal preserves case-sensitive identity | WatchlistRowsTest.aTickerOrATokenIsWatchedOnceAsXtasTableKeepsIt, aTickerIsRemovedByItsSymbolAndATokenByItsIdAndItsCashtagChoiceIsKept |
covered | stocks_crypto_storage_test › a token added by contract keeps its cashtag choice across reloads and backups | CryptoAssetTest.theCashtagChoiceSurvivesTheStoredFormAndOldRowsReadAsOff; WatchlistRowsTest.theRowsAreXtasBackupRowsAndReadBack |

## test/stocks_crypto_test.dart

covered | stocks_crypto_test › same symbol keeps separate network and contract identities | CryptoAssetTest.theSameSymbolKeepsSeparateNetworkAndContractIdentities |
covered | stocks_crypto_test › only identical contracts merge, retaining the more liquid base pair | CryptoAssetTest.onlyIdenticalContractsMergeKeepingTheMoreLiquidPool |
covered | stocks_crypto_test › EVM casing canonicalizes while non-EVM addresses preserve case | CryptoAssetTest.evmCasingCanonicalisesWhileOtherAddressesKeepTheirCase |
covered | stocks_crypto_test › versioned metadata preserves network, contract, name and symbol | CryptoAssetTest.theStoredFormKeepsNetworkContractNameAndSymbol |
covered | stocks_crypto_test › malformed payloads cannot invent tokens or infinite prices | CryptoAssetTest.malformedPayloadsCannotInventTokensOrInfinitePrices |
covered | stocks_crypto_test › mixed feeds search contracts literally and keep legacy cashtags | WatchlistTest.mixedFeedsSearchContractsLiterallyAndKeepCashtags |
covered | stocks_crypto_test › token quote rejects a pool where the selected token is quote currency | CryptoClientTest.aTokensQuoteRejectsAPoolWhereTheTokenIsTheQuoteCurrency |
covered | stocks_crypto_test › contract search retains case and removes only the cashtag prefix | CryptoAssetTest.aSearchKeepsTheContractsCaseAndDropsOnlyTheCashtagSign |
covered | stocks_crypto_test › a token is named by its symbol, else its name, else its address cut in the middle | CryptoAssetTest.aTokenIsNamedByItsSymbolElseItsNameElseItsAddressCutInTheMiddle |
covered | stocks_crypto_test › tiny token prices remain nonzero and useful | CryptoAssetTest.aRealFigureIsFiniteAndAtLeastItsFloor; StockNumbersTest.underOneUnitFourSignificantDigitsAndFromThreeZerosOnTheZerosAreCounted |
covered | stocks_crypto_test › clearing a search invalidates the request already in flight | StocksSearchTest.anEmptyQueryAsksNothing | the model cancels the search it replaces |

## test/stocks_followed_first_test.dart

covered | stocks_followed_first_test › reads the search's cashtags and quoted addresses | FollowedFirstTest.theTermsAreTheSearchsCashtagsAndQuotedAddresses |
covered | stocks_followed_first_test › a cashtag by its entity, not its letters | FollowedFirstTest.aCashtagNamesTheTickerByItsEntityNotItsLetters |
covered | stocks_followed_first_test › an EVM address in either case, any other exactly | FollowedFirstTest.anEvmAddressNamesItInEitherCaseAnyOtherExactly |
covered | stocks_followed_first_test › a followed account must have written it: a repost is left out | FollowedFirstTest.aFollowedAccountMustHaveWrittenItARepostIsLeftOut |
covered | stocks_followed_first_test › newest first, each post once, only followed posts naming the ticker | FollowedFirstTest.theTopPartIsNewestFirstEachPostOnceOnlyFollowedPostsNamingTheTicker |
covered | stocks_followed_first_test › at most ten, from the last seven days | FollowedFirstTest.atMostTenFromTheLastSevenDays |
covered | stocks_followed_first_test › a later chain repeating a post of the top part is left out | FollowedFirstTest.aLaterThreadRepeatingAPostOfTheTopPartIsLeftOut |
covered | stocks_followed_first_test › narrow the ticker search to followed accounts, packed within 512 characters | FollowedFirstTest.theSearchesNarrowTheTickerSearchToFollowedAccountsPackedWithin512Characters |
covered | stocks_followed_first_test › accounts seen posting the ticker go first | FollowedFirstTest.accountsSeenPostingTheTickerGoFirst |
covered | stocks_followed_first_test › keeps a ticker's searches for five minutes, and a failed one adds nothing | FollowedFirstTest.aTickersSearchesAreKeptFiveMinutesAndAFailedOneAddsNothing |
covered | stocks_followed_first_test › the device answers nothing when its cache cannot be read | FollowedFirstTest.theDeviceAnswersNothingWhenItsPostsCannotBeRead |
covered | stocks_followed_first_test › the first page puts the followed posts on top, a later page leaves them out | FollowedFirstTest.theFirstPagePutsTheFollowedPostsOnTopALaterPageLeavesThemOut |
covered | stocks_followed_first_test › without followed posts the page is as before, with no labels | FollowedFirstTest.withoutFollowedPostsThePageIsAsBeforeWithNoLabels |
covered | stocks_followed_first_test › the first page does not wait past its time for X | FollowedFirstTest.theFirstPageDoesNotWaitPastItsTimeForX |
covered | stocks_followed_first_test › sit above where each part starts, and read as headings | FollowedFirstTest.whenTheFirstPageHadNoOtherPostTheNextPagesFirstStartsTheSecondPart |

## test/stocks_post_extras_test.dart

untested → ported | stocks_post_extras_test › is cut in the middle, in the secondary colour, and copies whole on tap | Cashtags.kt cashtag chip | the middle cut is in the label; the colour and copy are Compose-only |
covered | stocks_post_extras_test › a long word without a digit is only a word | CashtagsTest.cashtagsOnAPostAreUpperCasedAndUnique |
covered | stocks_post_extras_test › a watched token's address leads with its network | QuoteRowsTest.aWatchlistRowIsATokenByItsSymbolAndNetworkOrATickerByItsName |
covered | stocks_post_extras_test › reads as a plain link, without its move | QuoteRowsTest.aRowSaysWhatItIsAndAPickedListingGoesByTheQuotesNameAlone |
untested → ported | stocks_post_extras_test › is drawn at the size of the words around it, at any text size | Cashtags.kt | inline text size is Compose-only |
covered | stocks_post_extras_test › lists the first four cashtags in order, each with its price and move | CashtagsTest.theStripListsTheFirstFourCashtagsInOrderEachWithItsPriceAndMove |
covered | stocks_post_extras_test › a grey bar while loading, No price without one, a closed market in grey | CashtagsTest.aGreyBarWhileLoadingNoPriceWithoutOneAndAClosedMarketMuted |
untested → ported | stocks_post_extras_test › a tap opens the ticker page | WatchlistRowsTest.aRouteNamesATickerOrAToken | the tap is Compose-only |
untested → ported | stocks_post_extras_test › fits 320 dp at 1.3x text, scrolling sideways | Cashtags.kt | layout is Compose-only |
covered | stocks_post_extras_test › a post without cashtags has none | CashtagsTest.aRepostsStripIsTheOriginalsAndAPostWithoutCashtagsHasNone |
covered | stocks_post_extras_test › sits over a signal post; Hide folds the post behind the line, a tap opens it | CashtagsTest.aTelegramInviteWithThreeOrMoreCashtagsIsASignalGroupPostEitherAloneIsNot | the fold is Compose-only |
covered | stocks_post_extras_test › with Fold signal-group posts on, they arrive folded | CashtagsTest.aTelegramInviteWithThreeOrMoreCashtagsIsASignalGroupPostEitherAloneIsNot | the setting decides the fold in Cashtags.kt |
covered | stocks_post_extras_test › any other post is left as it is | CashtagsTest.signalGroupPostsGoToTheEndOfTheirPageBothGroupsInTheirOrderNothingHidden |
covered | stocks_post_extras_test › reads in $name | QuoteRowsTest.aRowSaysWhatItIsAndAPickedListingGoesByTheQuotesNameAlone |
covered | stocks_post_extras_test › a Telegram invite with three or more cashtags is one; either alone is not | CashtagsTest.aTelegramInviteWithThreeOrMoreCashtagsIsASignalGroupPostEitherAloneIsNot |
covered | stocks_post_extras_test › go to the end of their page, both groups in their order, nothing hidden | CashtagsTest.signalGroupPostsGoToTheEndOfTheirPageBothGroupsInTheirOrderNothingHidden |

## test/stocks_render_test.dart

untested → ported | stocks_render_test › stocks-$board-$variant | ui/stocks screens | golden render of each board and variant; Compose has no golden pipeline, the rows and figures it draws are unit-tested |

## test/stocks_store_test.dart

covered | stocks_store_test › accepts what a reader is likely to type | WatchlistTest.aTickerIsWhatAReaderIsLikelyToType |
covered | stocks_store_test › rejects anything that is not a symbol | WatchlistTest.anythingElseIsNoTicker |

## test/stocks_token_sources_test.dart

covered | stocks_token_sources_test › is asked under its own name for the network | CryptoClientTest.geckoTerminalIsAskedUnderItsOwnNameForTheNetwork |
covered | stocks_token_sources_test › prices the token from the first pool it is the base of, with that pool's figures | CryptoClientTest.geckoTerminalPricesTheTokenFromTheFirstPoolItIsTheBaseOfWithThatPoolsFigures |
covered | stocks_token_sources_test › has no market when the token is no pool's base, or the pool has no price | CryptoClientTest.geckoTerminalHasNoMarketWhenTheTokenIsNoPoolsBaseOrThePoolHasNoPrice |
covered | stocks_token_sources_test › asks DexScreener first, and GeckoTerminal not at all when it answers | CryptoClientTest.dexScreenerIsAskedFirstAndGeckoTerminalNotAtAllWhenItAnswers |
covered | stocks_token_sources_test › asks GeckoTerminal when DexScreener refuses, and leaves DexScreener resting | CryptoClientTest.geckoTerminalAnswersWhenDexScreenerRefusesWhichLeavesDexScreenerResting |
covered | stocks_token_sources_test › asks GeckoTerminal when DexScreener has no market for the token, without resting it | CryptoClientTest.geckoTerminalAnswersWhenDexScreenerHasNoMarketWithoutRestingIt |
covered | stocks_token_sources_test › no market anywhere is no market; no answer anywhere is a failure | CryptoClientTest.noMarketAnywhereIsNoMarketAndNoAnswerAnywhereIsAFailure |

## test/stocks_watchlist_query_test.dart

covered | stocks_watchlist_query_test › one symbol is a bare cashtag | WatchlistTest.oneSymbolIsABareCashtagAndSeveralAnOrGroup |
covered | stocks_watchlist_query_test › several symbols become an OR group | WatchlistTest.oneSymbolIsABareCashtagAndSeveralAnOrGroup |
covered | stocks_watchlist_query_test › an empty watchlist is no query | WatchlistTest.oneSymbolIsABareCashtagAndSeveralAnOrGroup | an empty list builds no query |
covered | stocks_watchlist_query_test › the feed caps how many symbols share one search | WatchlistTest.theFeedCapsHowManySymbolsShareOneSearch |

## test/stocks_widgets_test.dart

untested → ported | stocks_widgets_test › the Stocks home leads with the watchlist under its title and count | ui/stocks StocksHome.kt | layout is Compose-only |
untested → ported | stocks_widgets_test › is the title, + and the settings over the four tabs as text | StocksHome.kt | layout is Compose-only |
untested → ported | stocks_widgets_test › becomes the labelled section picker when the tabs do not fit | StocksHome.kt | layout is Compose-only |
untested → ported | stocks_widgets_test › in Home: the title opens the sources, the sliders Home's sheet for Stocks | ui/plugins PluginChrome | the host's chrome is shared; the sheet is Compose-only |
untested → ported | stocks_widgets_test › fits 320dp at ${scale}x text in ${entry.key} | ui/stocks rows | layout is Compose-only |
untested → ported | stocks_widgets_test › German fits 320dp at 1.3x text in ${entry.key} | ui/stocks rows | layout is Compose-only |
untested → ported | stocks_widgets_test › a long press lifts a row to move it, and copies nothing | WatchlistRowsTest.theStarAsksByUpperCaseAndADraggedRowMoves | the drag is Compose-only |
covered | stocks_widgets_test › keeps the order it was dragged into, and files anything new after it | WatchlistTest.theOrderTheReaderDraggedIsKeptAndAnythingNewFiledAfterIt |
covered | stocks_widgets_test › a row dropped elsewhere is still there after a reload | WatchlistRowsTest.theRowsAreXtasBackupRowsAndReadBack |
covered | stocks_widgets_test › nothing yet is loading; a failed lookup, or a quote without a price, is no data | QuoteRowsTest.nothingYetIsLoadingAFailedLookupOrAQuoteWithoutAPriceIsNoData |
covered | stocks_widgets_test › outside every session the market is closed; a coin never is | QuoteRowsTest.outsideEverySessionTheMarketIsClosedACoinNeverIs |
untested → ported | stocks_widgets_test › loading: grey bars where the figures go, the row already named | QuoteRowsTest.aRowSaysWhatItIsAndAPickedListingGoesByTheQuotesNameAlone | the bars are Compose-only |
untested → ported | stocks_widgets_test › market closed: the close and its day, price and move in the secondary colour | QuoteRowsTest.aClosedMarketShowsTheWeekdayOfItsCloseOrItsDateOnceAWeekOld | the colour is Compose-only |
untested → ported | stocks_widgets_test › no data: the reason, no figures, and a retry button | QuoteRowsTest.nothingYetIsLoadingAFailedLookupOrAQuoteWithoutAPriceIsNoData | the button is Compose-only |
covered | stocks_widgets_test › a live move is coloured and says what it is measured over | StockNumbersTest.aGainIsUpALossDownAndUnknownOrFlatIsNeutral |
untested → ported | stocks_widgets_test › two readings: the amber line and both choices, the one shown in the accent | QuoteRowsTest.aCoinFirstCashtagWithAListingOffersBothReadingsTheCoinFirst | the accent and amber are Compose-only |
covered | stocks_widgets_test › a coin-first cashtag on the watchlist finds its listing, once | QuoteCachesTest.aCoinFirstCashtagOnTheWatchlistFindsItsListingOnce |
covered | stocks_widgets_test › the pick is kept, and decides what the symbol is asked for as | QuoteCachesTest.thePickIsKeptAndDecidesWhatTheSymbolIsAskedForAs |
untested → ported | stocks_widgets_test › Markets: Indices and Crypto, each saying its state, and the row without data retries | QuoteRowsTest.aSectionSaysWhetherItsMarketsTrade | the row and retry are Compose-only |
untested → ported | stocks_widgets_test › the strip is flat: symbol over figures, No price, a bar while loading, the filter in the accent | CashtagsTest.aGreyBarWhileLoadingNoPriceWithoutOneAndAClosedMarketMuted | the layout and accent are Compose-only |
untested → ported | stocks_widgets_test › shows price, session and key stats at 320dp and 2x text in ${entry.key} | ui/stocks ticker page | layout is Compose-only |
untested → ported | stocks_widgets_test › a new range loads under the line over the chart before, so nothing below it moves | TickerClientTest.aTickerPageShowsTheRangeAndKeepsTodaysQuoteForItsFigures | the chart placement is Compose-only |
untested → ported | stocks_widgets_test › a silent refresh never overtakes a new range the reader is waiting on | QuoteCachesTest.theReadingsKeptOnTheDeviceArriveLateAndASymbolWhoseReadingChangedIsAskedAgain | the chart's model rule is unit-tested |
untested → ported | stocks_widgets_test › a range that fails to load says so rather than keeping the chart before | TickerClientTest.aRefusalIsUnavailableAndA200ThatIsNotJsonIsABadResponse | the message is Compose-only |
untested → ported | stocks_widgets_test › a failed price load offers a retry instead of a blank | QuoteRowsTest.nothingYetIsLoadingAFailedLookupOrAQuoteWithoutAPriceIsNoData | the button is Compose-only |
untested → ported | stocks_widgets_test › header fits 320dp at 2x text in ${entry.key} | ui/stocks ticker page | layout is Compose-only |
covered | stocks_widgets_test › a pasted address lists the token by network and short address | QuoteRowsTest.aWatchlistRowIsATokenByItsSymbolAndNetworkOrATickerByItsName; StocksSearchTest.aPastedAddressSwitchesToTokensAtOnceAndAsksForNoStock |

## test/plugin_activity_footer_test.dart

untested → ported | plugin_activity_footer_test › a fully loaded list ends at its last item | ui/plugins Lists.kt pagedRows (footer) | the footer is Compose-only; PagedState.next null ends the list |
untested → ported | plugin_activity_footer_test › a page that failed with items shown is one compact row | ui/plugins Lists.kt pagedRows (RetryRow) | the compact row is Compose-only |

## test/plugin_catalogue_test.dart

covered | plugin_catalogue_test › offers the ids it lists, in the order it lists them | plugin-host CatalogueTest.theDocumentPluginsMdDescribesReadsWhole, anEntryWithoutAnIdOrVersionOrWithOneThatCouldNotBeAFolderIsIgnored |
covered | plugin_catalogue_test › an entry marked unavailable is held back rather than offered | CatalogueTest.onlyFalseItselfHoldsAnEntryBackAndOnlyTrueMakesOnePrivate |
covered | plugin_catalogue_test › every id it names is one this build actually has | plugin-host StoreListingTest.aPluginWithoutAFileForThisPlatformIsNotOfferedOnIt; the plugin build's manifest |
covered | plugin_catalogue_test › a document that no longer fits gives up rather than throwing | CatalogueTest.aDocumentThatNoLongerFitsIsNoCatalogueRatherThanAThrow |
covered | plugin_catalogue_test › an entry with no id is skipped, not counted as one | CatalogueTest.anEntryWithoutAnIdOrVersionOrWithOneThatCouldNotBeAFolderIsIgnored |
covered | plugin_catalogue_test › a stale catalogue still offers a plugin this build compiled in | StoreListingTest.anInstalledPluginStaysWhenTheCatalogueDropsItOrCannotBeRead |
untested → ported | plugin_catalogue_test › bytes are shown whole, larger units to one decimal | ui/pluginstore StoreRows.kt (bytes) | the size formatting has no separate unit test |
untested → ported | plugin_catalogue_test › it does not run out of units | ui/pluginstore StoreRows.kt (bytes) | same |

## test/plugin_category_test.dart

covered | plugin_category_test › every built-in plugin has a store category | plugin-host CatalogueTest.aCategoryThisAppDoesNotKnowIsListedAsOther; every plugin's manifest names one |
covered | plugin_category_test › the store groups plugins under focused categories | plugin-host StoreListingTest.installedPluginsComeFirstThenWhatIsOnOfferByCategoryInTheCataloguesOrder |
covered | plugin_category_test › the store lists installed plugins above available categories | StoreListingTest.installedPluginsComeFirstThenWhatIsOnOfferByCategoryInTheCataloguesOrder |
covered | plugin_category_test › store search finds Hacker News by name or id | ui/pluginstore StoreSearchTest |

## test/plugin_compact_header_test.dart

untested → ported | plugin_compact_header_test › compact host preserves source, sections and primary action at $width | ui/plugins Scaffold.kt (PluginChrome) | layout is Compose-only |
untested → ported | plugin_compact_header_test › a bar with filters keeps the sliders and a sheet with its sections | Scaffold.kt; ui/plugins SheetActions.kt | layout is Compose-only |
untested → ported | plugin_compact_header_test › crowded standalone navigation leaves room for posts and every section at $width/$scale | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_compact_header_test › combined options preserve primary, secondary and wrapped actions and full client | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_compact_header_test › entries pinned from the options sheet join the top bar and persist | Scaffold.kt; ui/plugins PluginSettings.kt | the pin's storage is unit-tested; the bar is Compose-only |
untested → ported | plugin_compact_header_test › pins that do not fit stay in the sheet instead of crowding the bar | Scaffold.kt | layout is Compose-only |

## test/plugin_counts_test.dart

covered | plugin_counts_test › a count is written the way the reader's language writes it | StockNumbersTest.theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish (the shared number writing) |
covered | plugin_counts_test › a language intl has no number symbols for gets English digits | StockNumbersTest.theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish |
untested → ported | plugin_counts_test › the same locale is not re-parsed for every card in a feed | ui/plugins Rows.kt PluginCounts | the memoised locale is a Compose-side cache |

## test/plugin_feed_insets_test.dart

untested → ported | plugin_feed_insets_test › standalone lists keep a short gutter | ui/plugins Panes.kt, Lists.kt (content padding) | layout is Compose-only |
untested → ported | plugin_feed_insets_test › embedded lists clear the floating home pill | Panes.kt (frame padding) | layout is Compose-only |
untested → ported | plugin_feed_insets_test › embedded lists clear a floating bar taller than the fixed clearance | Panes.kt | layout is Compose-only |
different → fixed | plugin_feed_insets_test › embedded lists do not reuse the requested outer controller | ui/plugins Lists.kt | Compose lists own their LazyListState; there is no outer controller to share |

## test/plugin_home_chrome_test.dart

untested → ported | plugin_home_chrome_test › chrome has readable sections in a 48dp row | ui/plugins Scaffold.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › embedded chrome skips a second SafeArea | Scaffold.kt (WindowInsets) | layout is Compose-only |
untested → ported | plugin_home_chrome_test › standalone chrome keeps a top SafeArea for the status bar | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › selected tab uses accent when provided | Scaffold.kt | colour is Compose-only |
untested → ported | plugin_home_chrome_test › tab AppBar has no plugin-name title | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › crowded Home sections stay labelled and every destination dispatches once | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › wide Home keeps direct tabs and responds to external section selection | Scaffold.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › Home filter row saves padding but retains 48dp controls | Scaffold.kt; ui/home HomeFilterSheet.kt | layout is Compose-only |
untested → ported | plugin_home_chrome_test › Home suggestions collapse without losing profile or follow actions | ui/home HomeSuggestions | layout is Compose-only |
untested → ported | plugin_home_chrome_test › expanded people and compact filters allow large RTL text | Scaffold.kt; ui/home HomeFilterSheet.kt | layout is Compose-only |

## test/plugin_home_dock_test.dart

untested → ported | plugin_home_dock_test › late disposal cannot remove replacement or another source | ui/plugins PluginHost.kt (docked contributions) | the host's registration is unit-tested; the late disposal is Compose-only |
untested → ported | plugin_home_dock_test › hosted contributions disappear with owner; fallback stays standalone | PluginHost.kt | same |
untested → ported | plugin_home_dock_test › single header keeps section and right-side actions reachable $width/$scale/$rtl | ui/home HomeHeader | layout is Compose-only |
untested → ported | plugin_home_dock_test › large-text reading labels receive their own row only when needed at $width | HomeHeader | layout is Compose-only |

## test/plugin_home_tab_test.dart

covered | plugin_home_tab_test › Substack declares its tab optional; a plugin without a feed does not | ui/plugins XtaPlugin.kt (tab declaration); data/plugins PluginPrefsTest |
covered | plugin_home_tab_test › the tab is shown unless the preference says otherwise | PluginPrefsTest |
covered | plugin_home_tab_test › an unset preference keeps the current behaviour | PluginPrefsTest |
covered | plugin_home_tab_test › a plugin with no optional tab always reports true | XtaPlugin.kt; PluginPrefsTest |
covered | plugin_home_tab_test › every plugin offering to hide its tab actually has a tab screen | plugin manifests; XtaPlugin.kt |

## test/plugin_lazy_tabs_test.dart

untested → ported | plugin_lazy_tabs_test › only the selected tab is built on the first frame | ui/plugins Tabs.kt | Compose builds only the shown pane; the lazy rule is Compose-only |
untested → ported | plugin_lazy_tabs_test › switching away unmounts the previous tab | Tabs.kt | same |

## test/plugin_links_test.dart

covered | plugin_links_test › openWithPlugins ignores plugin URLs when plugins are off | ui/links LinkOpeningTest |
covered | plugin_links_test › openNativeLink handles X profiles without browser fallback | LinkOpeningTest; XLinksTest |
covered | plugin_links_test › openNativeLink leaves ordinary web links for the caller | LinkOpeningTest |

## test/plugin_navigation_gestures_test.dart

untested → ported | plugin_navigation_gestures_test › Saved source filter swipes retain the source menu | ui/saved SavedScreen.kt | layout and gesture are Compose-only |
untested → ported | plugin_navigation_gestures_test › profile section swipes keep segmented buttons accessible | ui/plugins Swipes.kt, SwipeRules.kt | the swipe rule is unit-tested; the gesture is Compose-only |
untested → ported | plugin_navigation_gestures_test › standalone lazy readers swipe once without building inactive panes | Tabs.kt, Swipes.kt | layout is Compose-only |
untested → ported | plugin_navigation_gestures_test › embedded lazy readers leave body swipes to Home source navigation | Swipes.kt | layout is Compose-only |
untested → ported | plugin_navigation_gestures_test › repeated header swipes before selection rebuild are accepted once | SwipeRules.kt | the repeated-swipe rule is unit-tested; the gesture is Compose-only |
untested → ported | plugin_navigation_gestures_test › plugin ${compact ? "icons" : "picker"} switches on release and retains tap | ui/home HomeBar | layout is Compose-only |

## test/plugin_post_media_test.dart

covered | plugin_post_media_test › defaults when the ratio is missing or nonsense | ui/plugins MediaItemTest |
covered | plugin_post_media_test › clamps extreme portraits and banners | MediaItemTest |
covered | plugin_post_media_test › reads width/height and a precomputed aspect | MediaItemTest |
covered | plugin_post_media_test › pluginMediaItemsFrom pairs urls with aspect, video and alt metadata | MediaItemTest; XPostMediaTest.aPhotoIsShownAsXServesIt |
covered | plugin_post_media_test › visiblePluginMedia keeps the tapped surviving item selected | MediaItemTest |
untested → ported | plugin_post_media_test › plugin media filename strips query strings and prefixes the source | store/downloads DownloadNaming.kt | the naming rule is unit-tested; the plugin prefix is shared by PluginBatchesTest |
untested → ported | plugin_post_media_test › plugin media filename is decoded, keeps its start and fits Android | DownloadNaming.kt | same |
covered | plugin_post_media_test › pluginMediaItemsFrom carries the stream and GIF flag | MediaItemTest |
covered | plugin_post_media_test › a poster without a stream is not playable | MediaItemTest |
covered | plugin_post_media_test › an MP4 is offered for download, an HLS playlist is not | MediaItemTest |
covered | plugin_post_media_test › isHlsPlaylistUrl reads the path, not the query | MediaItemTest |

## test/plugin_reader_accessibility_test.dart

untested → ported | plugin_reader_accessibility_test › reader controls fit $tokens $direction embedded=$embedded at $size and 200% text | ui/plugins Reader.kt | layout is Compose-only |
untested → ported | plugin_reader_accessibility_test › gallery columns preserve room for artwork and large captions | ui/plugins Gallery columns (Booru/EH/Pixiv grids) | layout is Compose-only |

## test/plugin_search_history_test.dart

covered | plugin_search_history_test › remembers newest first, dedupes, and caps | ui/plugins PluginSearchHistoryTest |

## test/plugin_tag_chip_test.dart

untested → ported | plugin_tag_chip_test › a long tag and its translation wrap instead of running off the edge | plugins/mastodon MastodonSearch.kt AssistChip; plugins/ehviewer EhGalleryContent.kt TagChip | layout is Compose-only |
untested → ported | plugin_tag_chip_test › a short tag stays one line with a full-size touch target | same | layout is Compose-only |
untested → ported | plugin_tag_chip_test › a weak tag is dashed and unfilled, at full colour and touch target | EhGalleryContent.kt TagChip (dashedOutline) | the dashed style is Compose-only; weakTag parsing is EhParserTest.dashedTagsAreWeak |

## test/account_posts_test.dart

covered | account_posts_test › no accounts asks nobody | RotationTest.membersMergeNewestFirstAPageEachAndUndatedLast (empty list) |
covered | account_posts_test › merges every account newest first | RotationTest.membersMergeNewestFirstAPageEachAndUndatedLast |
covered | account_posts_test › takes only a page of each account | RotationTest.membersMergeNewestFirstAPageEachAndUndatedLast |
covered | account_posts_test › one account failing does not empty the others | RotationTest.oneMemberFailingKeepsTheOthersAndTheFailureShowsOnlyWhenNothingWasRead |
covered | account_posts_test › the error surfaces only when nothing at all could be read | RotationTest.oneMemberFailingKeepsTheOthersAndTheFailureShowsOnlyWhenNothingWasRead |
covered | account_posts_test › a second read inside the window does not ask again | RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld |
covered | account_posts_test › a refresh asks again even inside the window | RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld |
covered | account_posts_test › an expired entry is asked for again | RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld |
covered | account_posts_test › clearing forgets what was cached | Rotation.clear; RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld |
covered | account_posts_test › only so many accounts are asked for on one call | RotationTest.onlySoManyMembersAreAskedPerCallAndTheRestOnTheNext |
covered | account_posts_test › the accounts left over are read on the next call | RotationTest.onlySoManyMembersAreAskedPerCallAndTheRestOnTheNext |
covered | account_posts_test › a cached account never counts against the budget | RotationTest.aCachedMemberIsFreeAndARefreshPastTheBudgetKeepsWhatIsHeld |
covered | account_posts_test › a forced refresh past the budget keeps what the cache holds | RotationTest.aCachedMemberIsFreeAndARefreshPastTheBudgetKeepsWhatIsHeld |
covered | account_posts_test › a forced refresh still refetches inside the budget | RotationTest.aCachedMemberIsFreeAndARefreshPastTheBudgetKeepsWhatIsHeld |
covered | account_posts_test › pendingCount is what the cache cannot answer yet | RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld (pendingCount) |
covered | account_posts_test › onPartial paints the first account immediately and coalesces the rest | RotationTest.aRefreshPaintsWhatWasHeldAndAFailedMemberKeepsWhatItSaidLast |
covered | account_posts_test › forceRefresh with onPartial paints stale posts first | RotationTest.aRefreshPaintsWhatWasHeldAndAFailedMemberKeepsWhatItSaidLast |
covered | account_posts_test › a post with no date sorts last rather than being dropped | RotationTest.membersMergeNewestFirstAPageEachAndUndatedLast |
covered | account_posts_test › a refresh never paints fewer accounts than it held | RotationTest.aRefreshPaintsWhatWasHeldAndAFailedMemberKeepsWhatItSaidLast |
covered | account_posts_test › an account whose refresh fails keeps what it said last | RotationTest.aRefreshPaintsWhatWasHeldAndAFailedMemberKeepsWhatItSaidLast |
covered | account_posts_test › a refresh where every account fails still reports the failure | RotationTest.oneMemberFailingKeepsTheOthersAndTheFailureShowsOnlyWhenNothingWasRead |
covered | account_posts_test › seeded accounts answer from the seed inside the window | RotationTest.aSeedAnswersWithinItsTimeAndNeverReplacesSomethingNewer |
covered | account_posts_test › a seed never replaces something newer | RotationTest.aSeedAnswersWithinItsTimeAndNeverReplacesSomethingNewer |
covered | account_posts_test › prioritize puts never-asked accounts first, then the longest unasked | RotationTest.theNeverAskedComeFirstThenTheLongestUnasked |

## test/x_reader_hub_test.dart

untested → ported | x_reader_hub_test › X advertises its existing search capability | ui/plugins XtaPlugin.kt (search) | the declaration is Compose-side |
untested → ported | x_reader_hub_test › X search routes with query/focus preserved: $query | ui/search (route) | the route is Compose-only |
untested → ported | x_reader_hub_test › reader exposes search and a heading without a dead tab | ui/plugins Reader.kt | layout is Compose-only |
untested → ported | x_reader_hub_test › reader menu dispatches every named library destination | Reader.kt | the menu is Compose-only |
untested → ported | x_reader_hub_test › real reader destinations inherit stores and return safely | Reader.kt | storage is unit-tested elsewhere; navigation is Compose-only |
untested → ported | x_reader_hub_test › reader tools fit narrow screens with larger text | Reader.kt | layout is Compose-only |

## test/alt_microblogging_mark_test.dart

covered | alt_microblogging_mark_test › Bluesky sits top left and Mastodon bottom right, over it | ui/subscriptions GroupBoardModelTest.aPluginsAccountsShowTheirFacesAfterTheXAccountsInTheGroupsOrder |
covered | alt_microblogging_mark_test › the section is named after Mastodon and Bluesky only | ui/subscriptions GroupBoardModelTest; GroupLookTest |

## test/home_x_reddit_headers_test.dart

untested → ported | home_x_reddit_headers_test › X uses the compact Home header and moves its actions into options $name | ui/home HomeHeader | layout is Compose-only |
untested → ported | home_x_reddit_headers_test › Reddit uses the compact Home header with its sections and options $name | plugins/reddit RedditHome.kt | layout is Compose-only |
untested → ported | home_x_reddit_headers_test › Reddit sort and route changes in Home refetch the visible feed | RedditHome.kt; plugins/reddit RedditApp.kt | the refetch is unit-tested; the Home wiring is Compose-only |
untested → ported | home_x_reddit_headers_test › a filtered account marks X options without adding a row | ui/home HomeHeader | layout is Compose-only |
untested → ported | home_x_reddit_headers_test › Following keeps its own bar, picker and actions next to docked sources | ui/home HomeBar | layout is Compose-only |

## test/group_member_preview_test.dart

covered | group_member_preview_test › a Threads account brings its avatar | plugins/threads ThreadsParserTest (profile avatar) |
covered | group_member_preview_test › a Bluesky account brings its avatar | plugins/bluesky BlueskyParserTest.aProfileIsReadFromThePublicAppViewWithXtasHeaders |
covered | group_member_preview_test › a Fediverse account brings its avatar | plugins/mastodon MastodonParserTest (profile avatar) |
covered | group_member_preview_test › a subreddit is marked as one instead of carrying a URL | ui/subscriptions GroupBoardModelTest.aTileCountsEveryMemberAndShowsFourFacesByHandle |
covered | group_member_preview_test › a publication brings its logo | plugins/substack Substack tests (publication logo) |
covered | group_member_preview_test › every source can describe one of its members | store/backup NetworkMembersTest; ui/subscriptions GroupBoardModelTest |

## test/rss_plugin_test.dart

covered | rss_plugin_test › RSS is a built-in newsletter plugin with a subscription table | plugins/rss RssPlugin.kt; plugin-host PluginManagerTest (built-in registration) |

## test/subscription_source_test.dart

covered | subscription_source_test › every network that can join a group is one | NetworkMembersTest.theAccountsAreTheRowsOfTheNetworksXtasGroupsNameByTheirIdAlone |
covered | subscription_source_test › exactly one source owns a given subscription | NetworkMembersTest.theAccountsAreTheRowsOfTheNetworksXtasGroupsNameByTheirIdAlone |
covered | subscription_source_test › no source claims X's own subscriptions | NetworkMembersTest.aBareMemberXOrAnotherNetworkKeepsTooIsEachOfThem |
covered | subscription_source_test › a stored row round-trips through the source that owns it | NetworkMembersTest.anExportWritesThemBackAsXtaWritesThem |
covered | subscription_source_test › comes from the source that owns it | NetworkMembersTest.anExportWritesThemBackAsXtaWritesThem |
covered | subscription_source_test › falls back to a handle for X accounts | NetworkMembersTest.xtasListNamesABareNetworksAccountByItsIdAndTheOthersAsTheirGroupsDo |
untested → ported | subscription_source_test › every source leads somewhere when tapped | ui/plugins XtaPlugin.kt (route) | the tap is Compose-only |
untested → ported | subscription_source_test › an X account has no plugin screen to open | XtaPlugin.kt | the absence is Compose-only |

## test/deepmarks_client_test.dart
covered | deepmarks_client_test › posts the signed event to the documented endpoint | DeepmarksClientTest (post)
covered | deepmarks_client_test › honours a custom API base for self-hosted or local instances | DeepmarksClientTest
covered | deepmarks_client_test › flags an event that reached the API but no relay | DeepmarksClientTest; DeepmarksSavesTest
covered | deepmarks_client_test › does not call out when there is no API key | DeepmarksSavesTest.saveWithoutKey
covered | deepmarks_client_test › maps each documented status to something actionable | DeepmarksClientTest
covered | deepmarks_client_test › an HTML page is a wrong address, not a success | DeepmarksClientTest
covered | deepmarks_client_test › an unreachable host is a network failure | DeepmarksClientTest
covered | deepmarks_client_test › returns the account pubkey when a bookmark reveals it | DeepmarksClientTest.verifyingGivesTheAccountsPubkeyWhenABookmarkRevealsIt
covered | deepmarks_client_test › returns null — not a mismatch — for an account with no bookmarks yet | DeepmarksClientTest
covered | deepmarks_client_test › surfaces the lifetime-tier requirement | DeepmarksClientTest

## test/deepmarks_nostr_test.dart
covered | deepmarks_nostr_test › decodes the NIP-19 nsec test vector | NostrTest
covered | deepmarks_nostr_test › accepts bare hex, in either case, with surrounding space | NostrTest
covered | deepmarks_nostr_test › rejects what is not a key, with a reason | NostrTest
covered | deepmarks_nostr_test › derives the public key from the NIP-19 vector | NostrTest
covered | deepmarks_nostr_test › is the sha256 of the canonical NIP-01 serialisation | NostrTest
covered | deepmarks_nostr_test › puts the URL in the d tag, as NIP-B0 requires | NostrTest
covered | deepmarks_nostr_test › adds title, description and lower-cased topics | NostrTest
covered | deepmarks_nostr_test › leaves out empty optional tags rather than sending blanks | NostrTest
covered | deepmarks_nostr_test › produces an event whose signature verifies against its own id | SchnorrTest
covered | deepmarks_nostr_test › the id matches the event it is sent with, so the server accepts it | NostrTest
covered | deepmarks_nostr_test › serialises to the shape the API documents | NostrTest
covered | deepmarks_nostr_test › two saves of the same URL are separately valid events | NostrTest

## test/hackernews_client_test.dart
covered | hackernews_client_test › hnHtmlToText flattens paragraphs and keeps link text | HackerNewsClientTest
covered | hackernews_client_test › an Algolia hit becomes a story | HackerNewsClientTest
covered | hackernews_client_test › Best reads Firebase ids then items | HackerNewsClientTest
covered | hackernews_client_test › a thread keeps nested comments | HackerNewsClientTest
covered | hackernews_client_test › a Firebase user keeps karma and about | HackerNewsClientTest
covered | hackernews_client_test › saved stories round-trip through JSON | HnDeviceTest
covered | hackernews_client_test › collapsed comments hide their children from the list | HnModelsTest

## test/immich_client_test.dart
covered | immich_client_test › a bare LAN address is http, because that is what those serve | ImmichServerTest
covered | immich_client_test › a bare name is assumed to be reachable properly | ImmichServerTest
covered | immich_client_test › a scheme that was typed is kept | ImmichServerTest
covered | immich_client_test › an /api copied from Immich is dropped rather than doubled | ImmichServerTest
covered | immich_client_test › a reverse-proxy prefix is kept | ImmichServerTest
covered | immich_client_test › a trailing slash changes nothing | ImmichServerTest
covered | immich_client_test › nothing usable is null rather than a guess | ImmichServerTest
covered | immich_client_test › goes to /api/assets with the key and the fields Immich needs | ImmichClientTest
covered | immich_client_test › a file Immich already had is a success, not a failure | ImmichClientTest
covered | immich_client_test › falls back to the older endpoint when the current one is not there | ImmichClientTest
covered | immich_client_test › a rejected key is reported as unauthorized | ImmichClientTest
covered | immich_client_test › no address or key at all never reaches the network | ImmichSavesTest; ImmichClientTest
covered | immich_client_test › a server that knows the user is a pass | ImmichClientTest
covered | immich_client_test › an older instance answering on the previous path still passes | ImmichClientTest
covered | immich_client_test › an HTML page from something that is not Immich is a bad address | ImmichClientTest
covered | immich_client_test › an unreachable server is a network problem, not a wrong key | ImmichClientTest
covered | immich_client_test › an album that exists is used rather than made twice | ImmichClientTest
covered | immich_client_test › an album that does not exist is created | ImmichClientTest
covered | immich_client_test › a server that will not talk about albums gives null, not a throw | ImmichClientTest
covered | immich_client_test › filing nothing does not call the server | ImmichUploaderTest
covered | immich_client_test › filing assets sends their ids | ImmichUploaderTest

## test/immich_media_test.dart
covered | immich_media_test › the original is asked for, not the display copy | ImmichMediaTest
covered | immich_media_test › the name is the file, without the size suffix | ImmichMediaTest
covered | immich_media_test › the id is the plain URL, so :orig does not change it | ImmichMediaTest
covered | immich_media_test › a photo the response carried no URL for is skipped, not uploaded empty | ImmichMediaTest
covered | immich_media_test › the best MP4 wins, and the playlist is never chosen | ImmichMediaTest
covered | immich_media_test › the id is the thumbnail, so it does not move with the variant | ImmichMediaTest
covered | immich_media_test › a GIF is a video by the time it is a file | ImmichMediaTest
covered | immich_media_test › a variant list with no usable MP4 is nothing to upload | ImmichMediaTest
covered | immich_media_test › a video with no thumbnail falls back to its own URL for an id | ImmichMediaTest
covered | immich_media_test › videos are skipped when only photos are wanted | ImmichMediaTest
covered | immich_media_test › a kind nobody knows is passed over rather than guessed at | ImmichMediaTest
covered | immich_media_test › a post with nothing attached is nothing to do | ImmichMediaTest
covered | immich_media_test › a post with several files keeps them all, in order | ImmichMediaTest

## test/karakeep_client_test.dart
covered | karakeep_client_test › assumes https when the scheme is left out | KarakeepClientTest
covered | karakeep_client_test › keeps an explicit scheme, host and port | KarakeepClientTest
covered | karakeep_client_test › drops a trailing slash and the api path copied from the docs | KarakeepClientTest
covered | karakeep_client_test › keeps a path prefix from a reverse proxy | KarakeepClientTest
covered | karakeep_client_test › rejects what is not a server address | KarakeepClientTest
covered | karakeep_client_test › posts a link bookmark with the API key | KarakeepClientTest
covered | karakeep_client_test › a 200 means Karakeep already had the URL | KarakeepClientTest
covered | karakeep_client_test › respects a reverse-proxy path prefix | KarakeepClientTest
covered | karakeep_client_test › omits an empty title rather than sending a blank one | KarakeepClientTest
covered | karakeep_client_test › clamps an over-long title to what Karakeep accepts | KarakeepClientTest
covered | karakeep_client_test › reports missing configuration without making a request | KarakeepSavesTest
covered | karakeep_client_test › a rejected key is reported as such, not as a generic failure | KarakeepClientTest
covered | karakeep_client_test › an HTML page means the address is not a Karakeep | KarakeepClientTest
covered | karakeep_client_test › a dead host is reported as a network problem | KarakeepClientTest
covered | karakeep_client_test › is true when the bookmarks endpoint answers | KarakeepSavesTest
covered | karakeep_client_test › surfaces a bad key | KarakeepSavesTest

## test/karakeep_title_test.dart
covered | karakeep_title_test › names the author, then the post | KarakeepSavesTest (title)
covered | karakeep_title_test › falls back to the handle when there is no display name | KarakeepSavesTest
covered | karakeep_title_test › is just the post when the author is unknown | KarakeepSavesTest
covered | karakeep_title_test › is just the byline for a post with no text, such as a photo | KarakeepSavesTest
covered | karakeep_title_test › collapses newlines so the bookmark list stays readable | KarakeepSavesTest

## test/subscription_loading_regression_test.dart
covered | subscription_loading_regression_test › following completes with the person visible and both stores idle | core/session SessionTest.followingHereAddsTheAccountToTheFollowingFeed
covered | subscription_loading_regression_test › a stalled subscription reload releases queued follows and ignores its late snapshot | core/session SessionTest.importingReplacesSessionsAndFollowsButKeepsTheSwitches
covered | subscription_loading_regression_test › a stalled group reload releases queued saves and membership changes | core/session SessionTest.groupsTakeMembersInAndOutOnce
covered | subscription_loading_regression_test › leaving a pending profile does not block local follows or groups | core/session SessionTest.followingHereAddsTheAccountToTheFollowingFeed
covered | subscription_loading_regression_test › rapid follows and a reload do not cancel a local write | core/session SessionTest.followingAgainKeepsThePlaceAndSwitchesButTakesTheNewName
covered | subscription_loading_regression_test › saving a group publishes the group before the save future completes | core/session SessionTest.aGroupReadsItsMembersEvenThoseKeptOutOfTheFollowingFeed
covered | subscription_loading_regression_test › rapid group saves preserve both groups | core/session SessionTest.groupsTakeMembersInAndOutOnce
covered | subscription_loading_regression_test › repeated follow keeps existing feed settings and memberships | core/session SessionTest.followingAgainKeepsThePlaceAndSwitchesButTakesTheNewName
covered | subscription_loading_regression_test › a failed write stops loading and does not poison the next follow | core/session SessionTest.followingHereAddsTheAccountToTheFollowingFeed

## test/subscription_profiles_test.dart
covered | subscription_profiles_test › names each post's author and the authors of what it reposts and quotes | core/session FollowsTest (profile names) — cite FollowsTest
covered | subscription_profiles_test › picks up a new handle, name or picture and keeps the feed settings | FollowsTest (profile refresh)
covered | subscription_profiles_test › the same picture at another size is no change | FollowsTest
covered | subscription_profiles_test › ignores accounts not followed and what X left out | FollowsTest
covered | subscription_profiles_test › a change of case is the same handle | FollowsTest
covered | subscription_profiles_test › withProfiles changes only the handle, name and picture | FollowsTest
covered | subscription_profiles_test › saves a new name in place, without rebuilding the feeds | FollowsTest
covered | subscription_profiles_test › a new handle reloads the subscriptions and the feeds | FollowsTest
covered | subscription_profiles_test › nothing new changes nothing | FollowsTest

## test/ticker_quote_test.dart
covered | ticker_quote_test › takes the closes and their dates in order | TickerQuoteTest.aPriceHistoryIsItsClosesAndTheirDatesInOrder
covered | ticker_quote_test › a day the market was shut is skipped, not counted as zero | TickerQuoteTest.aDayTheMarketWasShutIsSkippedNotCountedAsZero
covered | ticker_quote_test › integers and numeric strings are both prices | TickerQuoteTest.integersAndNumericStringsAreBothPricesAndMismatchedListsStopAtTheShorter
covered | ticker_quote_test › mismatched list lengths stop at the shorter one | TickerQuoteTest.integersAndNumericStringsAreBothPricesAndMismatchedListsStopAtTheShorter
covered | ticker_quote_test › is measured against the previous close | TickerQuoteTest.theDaysMoveIsMeasuredAgainstThePreviousClose
covered | ticker_quote_test › a fall is negative and reads as down | StockNumbersTest.aGainIsUpALossDownAndUnknownOrFlatIsNeutral
covered | ticker_quote_test › falls back to the last close when no live price is quoted | TickerQuoteTest.withoutALivePriceTheLastCloseIsThePriceAndWithoutACloseThereIsNoMove
covered | ticker_quote_test › no previous close means no move rather than a wrong one | TickerQuoteTest.withoutALivePriceTheLastCloseIsThePriceAndWithoutACloseThereIsNoMove
covered | ticker_quote_test › a previous close of zero does not divide by it | TickerQuoteTest.withoutALivePriceTheLastCloseIsThePriceAndWithoutACloseThereIsNoMove
covered | ticker_quote_test › volume and the year's range are read when the response carries them | TickerQuoteTest.theFiguresTheMarketPageShowsAreReadWhenSent
covered | ticker_quote_test › a pre-market print is reported beside, not instead of, the price | TickerQuoteTest.aPreMarketPrintIsReportedBesideNotInsteadOfThePrice
covered | ticker_quote_test › a symbol quoted without them still charts | TickerQuoteTest.aDayWithoutBarsIsStillAQuoteWithItsMove
covered | ticker_quote_test › a price without bars is still a quote, with its move | TickerQuoteTest.aDayWithoutBarsIsStillAQuoteWithItsMove
covered | ticker_quote_test › neither a price nor bars is no quote | TickerQuoteTest (empty quote)
covered | ticker_quote_test › the badge reads the host's word for the instrument | TickerQuoteTest.theBadgeReadsTheHostsWordForTheInstrument
covered | ticker_quote_test › is measured against the bar a day before the last | TickerQuoteTest.aCoinOverTheLast24HoursIsMeasuredAgainstTheBarADayBeforeTheLast
covered | ticker_quote_test › bars that do not reach back a day leave the quote as it was | TickerQuoteTest.aCoinOverTheLast24HoursIsMeasuredAgainstTheBarADayBeforeTheLast
covered | ticker_quote_test › gives up rather than throwing | TickerQuoteTest.aPayloadThatNoLongerFitsGivesUpRatherThanThrowing
covered | ticker_quote_test › history with no usable closes is nothing to draw | TickerQuoteTest.aPriceHistoryIsItsClosesAndTheirDatesInOrder
covered | ticker_quote_test › the symbol asked for is used when the response omits its own | TickerQuoteTest.aDayWithoutBarsIsStillAQuoteWithItsMove
covered | ticker_quote_test › asks for the symbol upper-cased, with a range and interval | YahooFinanceTest.theChartIsAskedForUpperCasedWithARangeAndInterval
covered | ticker_quote_test › an unknown symbol is reported as not found | TickerClientTest.aNameNothingAnswersForIsNotFound
covered | ticker_quote_test › a refusal is unavailable, not a broken response | TickerClientTest.aRefusalIsUnavailableAndA200ThatIsNotJsonIsABadResponse
covered | ticker_quote_test › a 200 that is not JSON is a bad response | TickerClientTest.aRefusalIsUnavailableAndA200ThatIsNotJsonIsABadResponse
covered | ticker_quote_test › a good response becomes a quote, and carries an agent | TickerClientTest.aGoodAnswerBecomesAQuoteAndTheRequestCarriesAnAgent

## test/ticker_session_test.dart
covered | ticker_session_test › follows the trading periods the chart host sends | TickerQuoteTest.theSessionFollowsTheTradingPeriodsTheHostSends
covered | ticker_session_test › a coin is never closed | TickerQuoteTest.aCoinIsNeverClosedAndWithoutPeriodsTheSessionIsUnknown
covered | ticker_session_test › without periods it says nothing rather than guessing | TickerQuoteTest.aCoinIsNeverClosedAndWithoutPeriodsTheSessionIsUnknown
covered | ticker_session_test › a bar after the close is the after-hours print, apart from the price | TickerQuoteTest.aBarAfterTheCloseIsTheAfterHoursPrintApartFromThePrice
covered | ticker_session_test › a bar inside today's pre-market is the early print | TickerQuoteTest.aBarInsideTodaysPreMarketIsTheEarlyPrintAndDuringTheSessionThereIsNone
covered | ticker_session_test › during the regular session there is no extended print | TickerQuoteTest.aBarInsideTodaysPreMarketIsTheEarlyPrintAndDuringTheSessionThereIsNone
covered | ticker_session_test › a major coin is asked for as the coin first | TickersTest.aMajorCoinIsAskedForAsTheCoinFirstAndTheReaderCanInsistOnEither
covered | ticker_session_test › the reader can insist on either instrument | TickersTest.aMajorCoinIsAskedForAsTheCoinFirstAndTheReaderCanInsistOnEither
covered | ticker_session_test › ordinary symbols are unchanged | TickersTest.anOrdinaryShareIsAskedForAsWrittenThenAsAPair
covered | ticker_session_test › fills every symbol, not only the first few, with one-day quotes | QuoteCachesTest.everySymbolIsFilledNotOnlyTheFirstFewWithOneDayQuotes
covered | ticker_session_test › remembers a symbol whose lookup failed until it answers | QuoteCachesTest.aSymbolWhoseLookupFailedIsRememberedUntilItAnswers
covered | ticker_session_test › a coin is asked for over the last 24 hours, not since midnight | QuoteCachesTest.aCoinIsAskedForOverTheLast24HoursNotSinceMidnight
covered | ticker_session_test › a pair the coin list does not name is asked for again over 24 hours | TickerClientTest.aPairTheCoinListDoesNotNameIsAskedForAgainOver24Hours
covered | ticker_session_test › the ticker page shares a share's day chart, not a coin's | QuoteCachesTest.theTickerPageSharesAShareDayChartNotACoins
covered | ticker_session_test › moves are signed, rounded to flat, and use a real minus | StockNumbersTest.movesAreSignedRoundedToFlatAndUseARealMinus
covered | ticker_session_test › prices keep useful digits at every size | StockNumbersTest.pricesKeepUsefulDigitsAtEverySize
covered | ticker_session_test › under one unit, four significant digits; from three zeros on, the zeros are counted | StockNumbersTest.underOneUnitFourSignificantDigitsAndFromThreeZerosOnTheZerosAreCounted
covered | ticker_session_test › a screen reader hears the zeros written out | StockNumbersTest.aScreenReaderHearsTheZerosWrittenOut
covered | ticker_session_test › the reader's locale groups digits; one intl lacks falls back | StockNumbersTest.theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish
covered | ticker_session_test › gain and loss colours stay readable and unknown is neutral | StockNumbersTest.aGainIsUpALossDownAndUnknownOrFlatIsNeutral

## test/plugin_account_groups_test.dart
covered | plugin_account_groups_test › same handle stays separate across all newly groupable sources | NetworkMembersTest.theAccountsAreTheRowsOfTheNetworksXtasGroupsNameByTheirIdAlone
covered | plugin_account_groups_test › Pixiv selection survives settings round-trip and a real group reload | ui/subscriptions GroupPickerTest
covered | plugin_account_groups_test › removing plugin data preserves an equal handle in another source | NetworkMembersTest.aBareMemberXOrAnotherNetworkKeepsTooIsEachOfThem
covered | plugin_account_groups_test › malformed local data does not prevent loading other subscriptions | ui/subscriptions SubscriptionListTest
covered | plugin_account_groups_test › membership toggles never mutate the original selection | ui/subscriptions GroupDraftTest

## test/plugin_card_overflow_test.dart
untested → ported | plugin_card_overflow_test › a Reddit card in German does not overflow a 320dp phone | plugins/reddit RedditCard.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › a Mastodon card in German does not overflow a 320dp phone | plugins/mastodon MastodonCard.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › a Hacker News card in German does not overflow | plugins/hackernews HnStoryRow.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › an RSS card in German does not overflow | plugins/rss RssRows.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › a Bluesky card in German does not overflow | plugins/bluesky BlueskyCard.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › a Threads card in German does not overflow | plugins/threads ThreadsCard.kt | layout is Compose-only
untested → ported | plugin_card_overflow_test › a Booru card in German does not overflow | plugins/booru BooruParts.kt | layout is Compose-only

## test/plugin_connection_journeys_test.dart
covered | plugin_connection_journeys_test › $id settings: large text, reachable test/save, stale probe invalidation | PluginConnectionTest (six tests)

## test/plugin_feed_chips_test.dart
covered | plugin_feed_chips_test › hidden-tab plugins are not site-switcher chips on Groups | ui/subscriptions GroupBoardModelTest, GroupAddSourcesTest
covered | plugin_feed_chips_test › a plugin that still has a home tab is not on the Groups board | ui/subscriptions GroupBoardModelTest
untested → ported | plugin_feed_chips_test › the board is not wrapped in AnimatedSwitcher | ui/subscriptions GroupBoard | Compose has no AnimatedSwitcher wrapper

## test/plugin_feed_fresh_test.dart
covered | plugin_feed_fresh_test › a feed that has never been fetched is not fresh | core/feed Rotation.kt (MemberCache.fresh) | RotationTest.membersMergeNewestFirstAPageEachAndUndatedLast (empty), pendingCount
covered | plugin_feed_fresh_test › a feed fetched inside the TTL is reused | MemberCache.fresh | RotationTest.aSeedAnswersWithinItsTimeAndNeverReplacesSomethingNewer
covered | plugin_feed_fresh_test › a feed older than the TTL is fetched again | MemberCache.fresh | RotationTest.aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld
missing → implemented | plugin_feed_fresh_test › a timestamp in the future is not treated as fresh | core/feed Rotation.kt (MemberCache.fresh) | native counted a future timestamp as fresh; it now is not. RotationTest.aTimestampInTheFutureIsNotFresh

## test/plugin_home_crash_sweep_test.dart
different → fixed | plugin_home_crash_sweep_test › FeedListView empty does not attach the outer controller | ui/plugins Lists.kt | Compose lists own their LazyListState; the Flutter outer-controller hazard does not exist
different → fixed | plugin_home_crash_sweep_test › FeedListView with one row does not attach the outer | ui/plugins Lists.kt | same
different → fixed | plugin_home_crash_sweep_test › FeedListView with a filled page does not attach the outer | ui/plugins Lists.kt | same
different → fixed | plugin_home_crash_sweep_test › PluginLazyTabs keeps one filled list on the inner controller | ui/plugins Tabs.kt | same
different → fixed | plugin_home_crash_sweep_test › EmptyPane keeps the outer on NestedScrollView only | ui/plugins Panes.kt | same
different → fixed | plugin_home_crash_sweep_test › a ListView without a controller uses the inner one | ui/plugins Lists.kt | same
different → fixed | plugin_home_crash_sweep_test › EhGalleryGrid empty and one tile stay off the outer | plugins/ehviewer EhLists.kt | same
different → fixed | plugin_home_crash_sweep_test › BooruPostGrid empty and one tile stay off the outer | plugins/booru BooruGrid.kt | same
different → fixed | plugin_home_crash_sweep_test › BooruPostGrid filled page stays off the outer | plugins/booru BooruGrid.kt | same
different → fixed | plugin_home_crash_sweep_test › PixivIllustGrid filled page stays off the outer | plugins/pixiv PixivGrid.kt | same
different → fixed | plugin_home_crash_sweep_test › zero feeds does not enter loading | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › empty Following paints and does not attach the outer | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › one feed row does not attach the outer | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › Für dich → RSS remount does not attach two controllers | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › zero publications does not enter loading | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › empty Following paints and does not attach the outer (second) | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › empty Following paints and does not attach the outer (third) | Compose (plugin feeds) | Compose state
different → fixed | plugin_home_crash_sweep_test › add-account cancel leaves the empty pane mounted | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › empty watchlist paints and does not attach the outer | Compose (stocks) | Compose state
different → fixed | plugin_home_crash_sweep_test › Bluesky add cancel disposes with the dialog | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › Mastodon add cancel disposes with the dialog | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › Reddit client-id cancel disposes with the dialog | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › EH page-jump cancel disposes with the dialog | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › Stocks add sheet cancel leaves the host mounted | Compose (plugin dialogs) | Compose state
different → fixed | plugin_home_crash_sweep_test › Add timeline after Networks pop stays mounted | Compose (plugin host) | Compose state
different → fixed | plugin_home_crash_sweep_test › feed-strip add sheet cancel leaves the host | Compose (plugin host) | Compose state
different → fixed | plugin_home_crash_sweep_test › PluginLazyTabs remount does not keep two panes | ui/plugins Tabs.kt | Compose state

## test/plugin_marks_test.dart
untested → ported | plugin_marks_test › plugin marks are glyphs, not the old generic icons | ui/plugins PathIcons.kt | drawing is Compose-only
untested → ported | plugin_marks_test › every plugin mark keeps its size inside a bigger tight box | ui/plugins PathIcons.kt | drawing is Compose-only
untested → ported | plugin_marks_test › every plugin mark paints across the same centred live area | ui/plugins PathIcons.kt | drawing is Compose-only

## test/plugin_post_video_test.dart
untested → ported | plugin_post_video_test › a video with a stream builds the X video player | ui/media (player) | Compose/media player
untested → ported | plugin_post_video_test › a GIF autoplays and loops without controls | ui/media (GIF) | Compose/media player
untested → ported | plugin_post_video_test › videos in a multi-item pager play too | ui/media (pager) | Compose/media player
untested → ported | plugin_post_video_test › a poster without a stream stays a thumbnail | ui/media Media.kt | Compose/media player
untested → ported | plugin_post_video_test › manual loading waits for a tap | ui/media (autoplay setting) | Compose/media player
untested → ported | plugin_post_video_test › a Mastodon card plays its video attachment | plugins/mastodon MastodonCard.kt | Compose/media player
untested → ported | plugin_post_video_test › a Bluesky card plays its HLS video | plugins/bluesky BlueskyCard.kt | Compose/media player

## test/plugin_store_row_test.dart
covered | plugin_store_row_test › a non-matching store query produces no result | ui/pluginstore StoreSearchTest
covered | plugin_store_row_test › an available plugin is one compact row with Install | ui/pluginstore StoreRowsTest
covered | plugin_store_row_test › an installed plugin keeps Open and settings, with tab visibility in its menu | StoreRowsTest
untested → ported | plugin_store_row_test › installed plugin icon actions keep 48dp touch targets | Compose (plugin store row) | layout is Compose-only
covered | plugin_store_row_test › available plugins start open so a new plugin is visible | StoreRowsTest
untested → ported | plugin_store_row_test › plugin tile stacks actions on a narrow large-text layout | Compose (plugin store row) | layout is Compose-only
untested → ported | plugin_store_row_test › plugin tile keeps actions inline when enough width is available | Compose (plugin store row) | layout is Compose-only
untested → ported | plugin_store_row_test › store body is centered, width-bounded, and lazy | Compose (plugin store) | layout is Compose-only
covered | plugin_store_row_test › empty store search state announces the localized result | StoreSearchTest

## test/plugin_url_test.dart
covered | plugin_url_test › parses a profile and a post (Bluesky) | BlueskyUrlsTest
covered | plugin_url_test › parses a DID profile | BlueskyUrlsTest
covered | plugin_url_test › parses a profile and a post (Mastodon) | networks/mastodon MastodonLinks; MastodonClientTest
covered | plugin_url_test › opens short /t/ links as posts whose author is read later | ThreadsLinksTest.aShortLinkOpensAPostWhoseAuthorIsReadLater
covered | plugin_url_test › parses a profile and ignores posts (Threads) | ThreadsLinksTest.aLinkOpensAProfileOrAPost
covered | plugin_url_test › parses a profile and a video (TikTok) | TikTokLinkTest
covered | plugin_url_test › ignores short links (TikTok) | TikTokLinkTest
covered | plugin_url_test › parses a subreddit, a thread, a user, and redd.it | plugins/reddit RedditRulesTest
covered | plugin_url_test › ignores media CDNs (Reddit) | plugins/reddit RedditRulesTest
covered | plugin_url_test › parses artwork and user URLs but not bare ids | PixivLinksTest
covered | plugin_url_test › opens the pixiv:// app scheme and i.pximg.net image files | PixivLinksTest.pixivsAppLinksAreRead
covered | plugin_url_test › opens pixivision.net articles and series pages | PixivLinksTest.seriesNovelsAndPixivisionKnowThePageABrowserOpens
covered | plugin_url_test › leaves pixivision.net pages that are not articles alone | PixivLinksTest.unsupportedLinksNameNothing
covered | plugin_url_test › leaves other hosts and schemes alone | PixivLinksTest.unsupportedLinksNameNothing
covered | plugin_url_test › parses a known instance and ignores Medium-style hosts | networks/mastodon MastodonLinks; MastodonXtaCasesTest
covered | plugin_url_test › parses a story and a user (HN) | plugins/hackernews HnRoutes
covered | plugin_url_test › ignores the front page and other hosts (HN) | plugins/hackernews HnRoutes
covered | plugin_url_test › parsePluginLink tries networks in order | ui/plugins AppPlugins link dispatch; PluginRouteTest

## test/rss_cover_performance_test.dart
untested → ported | rss_cover_performance_test › RSS thumbnail decodes at its painted width | plugins/rss cover request (Coil) | Compose/Coil only

## test/rss_merge_test.dart
covered | rss_merge_test › sorts newest first and drops duplicate ids | RssModelsTest (merge)
covered | rss_merge_test › items without a date sink below dated ones | RssModelsTest
covered | rss_merge_test › unread hides ids already marked read | RssKeptTest; ui/plugins RssReadsTest
covered | rss_merge_test › tag keeps only items from tagged feeds | RssModelsTest (tags)
covered | rss_merge_test › normalises host case and trailing slashes | RssClientTest; RssModelsTest
covered | rss_merge_test › listFromPrefs accepts a restored List, not only a JSON string | RssKeptTest (prefs)
covered | rss_merge_test › recognises feed paths and not site homes | RssClientTest.lookup

## test/rss_parser_test.dart
covered | rss_parser_test › reads RSS 2.0 channel metadata and items | RssModelsTest
covered | rss_parser_test › reads Atom entries and html links | RssModelsTest
covered | rss_parser_test › skips items that have no title, link or guid | RssModelsTest
covered | rss_parser_test › does not throw on mangled markup | RssModelsTest
covered | rss_parser_test › reads RFC 822 and ISO-8601 | RssModelsTest
covered | rss_parser_test › resolves alternate rss and atom links against the page | RssClientTest.lookup

## test/microblog_reader_layout_test.dart
untested → ported | microblog_reader_layout_test › a narrow pushed reader respects system insets and returns with Back | Compose (microblog readers) | layout is Compose-only
untested → ported | microblog_reader_layout_test › Mastodon has one slim reader header, large=$large | Compose (Mastodon reader) | layout is Compose-only
untested → ported | microblog_reader_layout_test › Bluesky keeps one header and local state, large=$large | Compose (Bluesky reader) | layout is Compose-only
untested → ported | microblog_reader_layout_test › Threads accounts move out of the feed and remain manageable, large=$large | Compose (Threads reader) | layout is Compose-only

## test/web_source_client_test.dart
covered | web_source_client_test › asks a listed site for the page, with GET only and its own name | WebSourceClientTest
covered | web_source_client_test › refuses an unlisted site and plain HTTP without asking | WebSourceClientTest
covered | web_source_client_test › follows a redirect on its sites, and not one that leaves them | WebSourceClientTest
covered | web_source_client_test › stops after three redirects | WebSourceClientTest
covered | web_source_client_test › reports another status, a page too large, and no answer | WebSourceClientTest
covered | web_source_client_test › waits between two requests of the same source | WebSourceClientTest
covered | web_source_client_test › reads a source file from any HTTPS address, and not over HTTP | WebSourceClientTest

## test/web_source_definition_test.dart
covered | web_source_definition_test › a valid source is read with its defaults merged into each list | WebSourceDefinitionTest
covered | web_source_definition_test › every fixture shape is a valid source | WebSourceDefinitionTest
covered | web_source_definition_test › a list overrides the fields it names and keeps the rest | WebSourceDefinitionTest
covered | web_source_definition_test › what is not a source file | WebSourceDefinitionTest
covered | web_source_definition_test › a newer format, and a missing one | WebSourceDefinitionTest
covered | web_source_definition_test › a bad id, version, name or layout | WebSourceDefinitionTest
covered | web_source_definition_test › hosts that are missing, too many or malformed | WebSourceDefinitionTest
covered | web_source_definition_test › an address off its hosts, without HTTPS, or with a port or credentials | WebSourceDefinitionTest
covered | web_source_definition_test › a subdomain only when its hosts allow subdomains | WebSourceDefinitionTest
covered | web_source_definition_test › lists that are missing, too many, repeated or unknown in their address | WebSourceDefinitionTest
covered | web_source_definition_test › a search without a query | WebSourceDefinitionTest
covered | web_source_definition_test › fields that cannot name or find a post, or a gallery without pictures | WebSourceDefinitionTest
covered | web_source_definition_test › a rule this page type cannot read | WebSourceDefinitionTest
covered | web_source_definition_test › a rule longer than 300 characters, or one written to fail slowly | WebSourceDefinitionTest
covered | web_source_definition_test › an article address that names no field of the post | WebSourceDefinitionTest
covered | web_source_definition_test › a host check takes listed hosts and their subdomains when asked | WebSourceDefinitionTest

## test/web_source_items_test.dart
covered | web_source_items_test › starts at the first page | WebSourceItemsTest
covered | web_source_items_test › gives a post per element that has an id or link and a title | WebSourceItemsTest
covered | web_source_items_test › reads every field, decoding entities and numbers | WebSourceItemsTest
covered | web_source_items_test › names the next page by number | WebSourceItemsTest
covered | web_source_items_test › an empty page has no next page, and a broken one no posts | WebSourceItemsTest
covered | web_source_items_test › a long page is read the same off the UI thread | WebSourceItemsTest
covered | web_source_items_test › search fills the query | WebSourceItemsTest
covered | web_source_items_test › starts with an empty cursor | WebSourceItemsTest
covered | web_source_items_test › builds links from templates and resolves relative pictures | WebSourceItemsTest
covered | web_source_items_test › names the next page by its cursor | WebSourceItemsTest
covered | web_source_items_test › finds the full text where the article rule says | WebSourceItemsTest
covered | web_source_items_test › gives a post per matching element with a title | WebSourceItemsTest
covered | web_source_items_test › follows the next page link | WebSourceItemsTest
covered | web_source_items_test › an article rule reads markup | WebSourceItemsTest
covered | web_source_items_test › a gallery page gives pictures | WebSourceItemsTest
covered | web_source_items_test › an RSS page is read by the RSS parser | WebSourceItemsTest
covered | web_source_items_test › dates in each form sites write them | WebSourceItemsTest

## test/web_source_paths_test.dart
covered | web_source_paths_test › read keys, indexes and every element | WebSourcePathsTest
covered | web_source_paths_test › a path that leads nowhere gives nothing | WebSourcePathsTest
covered | web_source_paths_test › numbers and booleans are text; maps and lists are not | WebSourcePathsTest
covered | web_source_paths_test › only well-formed paths are paths | WebSourcePathsTest
covered | web_source_paths_test › keys may follow an index without a dot | WebSourcePathsTest
covered | web_source_paths_test › a long path that is not one is refused at once | WebSourcePathsTest
covered | web_source_paths_test › fill every placeholder, or give nothing | WebSourcePathsTest
covered | web_source_paths_test › escape values inside an address, but not a whole address | WebSourcePathsTest
covered | web_source_paths_test › read text, attributes, the item itself and markup | WebSourcePathsTest
covered | web_source_paths_test › split a rule at its attribute only | WebSourcePathsTest
covered | web_source_paths_test › a selector this parser cannot read matches nothing | WebSourcePathsTest

## test/web_sources_screen_test.dart
untested → ported | web_sources_screen_test › the tab offers Add source while nothing is installed | Compose (web sources tab) | Compose only
untested → ported | web_sources_screen_test › an update asking for a new site waits for the reader to agree | WebSourcesKeptTest | the sheet is Compose-only
untested → ported | web_sources_screen_test › a source has a tab per list, its posts, and Follow | Compose (web sources) | Compose only
untested → ported | web_sources_screen_test › a gallery list is a grid of pictures | Compose (web sources) | Compose only
untested → ported | web_sources_screen_test › posts stay readable at large text sizes | Compose (web sources) | Compose only
untested → ported | web_sources_screen_test › Add source lists the published sources and adds one once agreed, fetching nothing from it before | WebSourceCatalogueTest | the sheet is Compose-only
untested → ported | web_sources_screen_test › a refused source file says why and where | WebSourceDefinitionTest | the wording is Compose-only

## test/web_sources_store_test.dart
covered | web_sources_store_test › resolves files against the list and leaves out bad entries | WebSourceCatalogueTest
covered | web_sources_store_test › on agreed sites replaces the source | WebSourcesKeptTest
covered | web_sources_store_test › asking for a new site waits for agreement | WebSourcesKeptTest
covered | web_sources_store_test › that is not newer, is another source, or does not read changes nothing | WebSourcesKeptTest
covered | web_sources_store_test › keeps installed sources, removes them and takes a waiting update | WebSourcesKeptTest
covered | web_sources_store_test › reads sources again at most once a day, and holds back new sites | WebSourceCatalogueTest
covered | web_sources_store_test › follows lists as subscriptions and drops them with their source | WebSourcesKeptTest
covered | web_sources_store_test › names a followed list by its parts | WebSourcesKeptTest
covered | web_sources_store_test › loads pages until one brings nothing new | WebSourceClientTest; WebSourcesKeptTest

## test/feed_ink_networks_test.dart
untested → ported | feed_ink_networks_test › takes two columns: the avatar at 16 dp, everything else at 68 dp | ui/feed (network rows) | layout is Compose-only
untested → ported | feed_ink_networks_test › names the author, handle and short time on one line | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › with large text on a narrow phone takes a second author line and fits | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › says the author and, in a mixed timeline, the network on the avatar | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › dims the server in the handle | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › says "Edited" after the time | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a boost and a pinned post have their context lines at 68 dp; the boost opens the booster | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › says who it replies to under the author line, at 68 dp | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › offers Follow as a 48 dp icon at the end of the author line until followed | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a repost has its context line at 68 dp and it opens the reposter | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › says who it replies to under the author line | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › carries the verified check on the author line | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › offers Follow as a 48 dp icon until followed, before the ⋯ at the end | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a repost has its context line at 68 dp and it opens the reposter (second) | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › puts its topic under the author line, at 68 dp | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a conversation's rail is drawn inside the card, through the avatar | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › the last post of a conversation, and a post outside one, keep their hairline | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a mixed timeline, $name | ui/feed | layout is Compose-only
untested → ported | feed_ink_networks_test › a Threads conversation | ui/feed | layout is Compose-only

## test/group_plugin_post_kinds_test.dart
covered | group_plugin_post_kinds_test › everything passes when the group takes everything | GroupFeedTest (kinds)
covered | group_plugin_post_kinds_test › replies off drops replies and keeps reposts, even of replies | GroupFeedTest
covered | group_plugin_post_kinds_test › reposts off drops reposts and keeps replies | GroupFeedTest
covered | group_plugin_post_kinds_test › each choice caches apart, and the existing names still read | GroupFeedTest
covered | group_plugin_post_kinds_test › replies off drops reply posts | GroupFeedTest
covered | group_plugin_post_kinds_test › replies on shows them | GroupFeedTest
covered | group_plugin_post_kinds_test › reposts off drops reposts | GroupFeedTest
covered | group_plugin_post_kinds_test › hidden replies do not use up the page | GroupFeedTest
covered | group_plugin_post_kinds_test › a self-thread continuation is a reply, as on X | GroupFeedTest
covered | group_plugin_post_kinds_test › a repost reason is read as a repost | GroupFeedTest
covered | group_plugin_post_kinds_test › the AppView is asked for posts without replies, apart from the tab | BlueskyClientTest (author feed filter)
covered | group_plugin_post_kinds_test › a reply to yourself is a reply, though it mentions nobody | GroupFeedTest
covered | group_plugin_post_kinds_test › replies off and reposts off drop replies and boosts | GroupFeedTest
covered | group_plugin_post_kinds_test › replies off and reposts off drop replies and reposts | GroupFeedTest

## test/home_feed_sources_test.dart
untested → ported | home_feed_sources_test › $name joins it only when the plugin is on and the switch is on | ui/home HomeFeed (sources) | Compose-only wiring
untested → ported | home_feed_sources_test › $name stays out when the reader has not asked for it | ui/home HomeFeed | Compose-only wiring
untested → ported | home_feed_sources_test › $name stays out when the plugin is off, however the switch is set | ui/home HomeFeed | Compose-only wiring
untested → ported | home_feed_sources_test › $name stays out of a feed that has never been configured | ui/home HomeFeed | Compose-only wiring

## test/home_plugin_evidence_test.dart
untested → ported | home_plugin_evidence_test › RSS production screen render ${dark ? 'dark' : 'light'} | Compose (render sweep) | golden/layout sweep
untested → ported | home_plugin_evidence_test › pilot production section controls render | Compose (render sweep) | golden/layout sweep

## test/home_plugin_journeys_test.dart
untested → ported | home_plugin_journeys_test › real Home keeps source, sections, loaded pages and scroll through full client and pin edits | Compose (Home journey) | Compose only

## test/published_sources_test.dart
covered | published_sources_test › the published list names sources | WebSourceCatalogueTest
covered | published_sources_test › ${listing.id} is a valid source, published next to the list | WebSourceCatalogueTest

## test/source_tables_test.dart
n/a | source_tables_test › RSS is a subscription source whose table is rss_subscription | - | XTA's SQLite schema; native keeps subscriptions in files (store/backup), no SQLite
n/a | source_tables_test › querySourceTable returns nothing when the table is missing | - | SQLite schema; native keeps no tables
n/a | source_tables_test › querySourceTable reads rows once the table exists | - | SQLite schema; native keeps no tables
n/a | source_tables_test › readRssFeedsTable is empty when rss_subscription is missing | - | SQLite schema; native keeps no tables
n/a | source_tables_test › syncRssFeedsTable does not throw when the table is missing | - | SQLite schema; native keeps no tables

## test/enabled_subscription_sources_test.dart
covered | enabled_subscription_sources_test › disabled plugins stay out of mixed-feed fan-out | core/feed FollowingFeedTest, GroupFeedTest (disabled sources)

## test/social_post_activity_test.dart
covered | social_post_activity_test › Bluesky reads repost actors and quote posts with opaque paging cursors | BlueskyFollowingTest, BlueskyClientTest
covered | social_post_activity_test › Bluesky retains stable repost identity when a handle is absent | BlueskyParserTest
covered | social_post_activity_test › Mastodon uses Link cursor rather than the last account ID | MastodonClientTest.boostersArePagedFromTheLinkHeaderOfTheInstanceThatHasThePost
covered | social_post_activity_test › Mastodon unauthorized quote lists stay errors rather than empty results | MastodonClientTest (quote refusal)
covered | social_post_activity_test › Activity pagination rejects another server and endpoint | MastodonClientTest.aCursorForAnotherListOrInstanceIsRefusedUnread
covered | social_post_activity_test › Activity store keeps prior pages on failure, retries cursor and deduplicates | MastodonFollowingTest; BlueskyFollowingTest

## test/read_recovery_backoff_test.dart
covered | read_recovery_backoff_test › $kind failures retry on a growing backoff and stop at the budget | ReadRetriesTest (Threads waits)
covered | read_recovery_backoff_test › a success renews the budget for the next failure | ReadRetriesTest
covered | read_recovery_backoff_test › sign-in, endpoint and definitive account failures stay manual | ReadRetriesTest.aLongWaitAndWhatOnlyTheReaderCanMendAreNotWaitedFor
covered | read_recovery_backoff_test › a rate limit retries once, after its known reset | ReadRetriesTest.aWaitMetaAskedForIsSatOutOnceASecondPastItsReset
covered | read_recovery_backoff_test › a Threads throttle waits for its cooldown, not seconds | ThreadsFailuresTest.aSessionThrottleWaitsForTheCooldownMetasAnswerArmedAndSoDoesTheNextRead
covered | read_recovery_backoff_test › a Threads read without a setup or a session waits for the reader | ThreadsFailuresTest.withoutASetupOrWithTheSessionRefusedNothingRetriesOnItsOwn
covered | read_recovery_backoff_test › a rate limit without a known reset stays manual | ReadRetriesTest.aLongWaitAndWhatOnlyTheReaderCanMendAreNotWaitedFor
covered | read_recovery_backoff_test › nothing retries while loading | ReadRetriesTest
untested → ported | read_recovery_backoff_test › nothing retries while the surface is out of view | Compose (read recovery) | Compose only
covered | read_recovery_backoff_test › a reported offline network pauses retries until it reconnects | ReadRetriesTest
covered | read_recovery_backoff_test › an unknown network does not block a cold-start retry | ReadRetriesTest
untested → ported | read_recovery_backoff_test › nothing retries while backgrounded; resume retries again | Compose (read recovery) | Compose only
untested → ported | read_recovery_backoff_test › nothing retries under a sheet | Compose (read recovery) | Compose only
covered | read_recovery_backoff_test › a retry that throws, synchronously or later, neither crashes nor loops | ReadRetriesTest
covered | read_recovery_backoff_test › new error objects for the same failing request never renew the budget | ReadRetriesTest
untested → ported | read_recovery_backoff_test › the notice shows the countdown, keeps manual retry, then falls back to it | Compose (read notice) | Compose only
untested → ported | read_recovery_backoff_test › a rate-limited notice counts down to the reset instead of a disabled button | Compose (read notice) | Compose only
untested → ported | read_recovery_backoff_test › a notice counting down keeps one row on a small phone | Compose (read notice) | Compose only
untested → ported | read_recovery_backoff_test › countdown, its details sheet and large text fit a narrow screen | Compose (read notice) | Compose only
untested → ported | read_recovery_backoff_test › visibility reported between frames schedules the frame that delivers it | Compose (read recovery) | Compose only
covered | read_recovery_backoff_test › the budget spends each backoff step once and the reset retry once | ReadRetriesTest

## test/discover_reading_test.dart
untested → ported | discover_reading_test › a recent search fills the field and submits the same query | Compose (Discover) | Compose only
untested → ported | discover_reading_test › recent searches are bounded, deduplicated and isolated by network | ui/plugins PluginSearchHistoryTest (rules) | the Discover wiring is Compose-only
untested → ported | discover_reading_test › empty Mastodon Discover shows followed people and live topics | Compose (Discover) | Compose only
untested → ported | discover_reading_test › inline search keeps its results after opening a profile | Compose (Discover) | Compose only

## Hacker News' screens against XTA's (no XTA test names these; the Dart file is the reference)
missing→implemented | lib/plugins/hackernews/hn_group.dart › a group's feed draws the story card | plugins/hackernews HackerNewsPlugin.postCard, HnApp.drawnStory | the shared row drew it before
missing→implemented | lib/plugins/hackernews/hn_screen.dart › the tabs are chips with XTA's glyphs | plugins/hackernews HnHome.kt HnTab.icon, HnIcons.kt | HackerNewsPluginRulesTest.everyTabHasItsGlyphAndEveryGlyphDraws
missing→implemented | lib/plugins/hackernews/hn_screen.dart › Saved is pulled to read again, and empty panes carry their glyph | plugins/hackernews HnHome.kt SavedPane, FollowingPane (ui/plugins PullToReload, EmptyPane) | ui PullWorkTest; the panes are Compose only
missing→implemented | lib/plugins/hackernews/hn_screen.dart › its own screen has one bar: the tabs, then the search and the settings | plugins/hackernews HnHome.kt (ui/plugins PluginHomeTabs) | the name, search and settings were a row of their own over the tabs; Compose only
missing→implemented | lib/plugins/hackernews/hn_search_sheet.dart, hn_screen.dart › a list that found nothing draws its glyph (search_off, forum) | plugins/hackernews HnStoryList.kt, HnIcons.SearchOff | HackerNewsPluginRulesTest.theEmptyPaneWaitsForAReadThatFoundNothing
covered | lib/plugins/hackernews/hn_search_sheet.dart › the searches run before are offered | plugins/hackernews HnSearch.kt Landing, HnApp.rememberSearch | ui/plugins PluginSearchHistoryTest
