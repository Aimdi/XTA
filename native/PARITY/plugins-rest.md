# Plugins, the rest: parity ledger (booru, EH, stocks and the shared plugin UI)

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered (a native JVM test asserts the same), untested → ported (implemented; the
Flutter widget/timing assertion has no Compose widget harness, the rule or store it covers is
unit-tested), different → fixed (native deliberately differs), n/a (Flutter-only).

This batch is the 62 files the other ledgers did not cover. The booru batch (`test/booru_*`,
273 tests; its perf harness is in `widgets.md`), the EH batch (`test/eh*`, 89 tests) and the
stocks batch (`test/stocks_*`, 101 tests) are below; the shared `test/plugin_*` files follow.

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
untested → ported | booru_viewer_test › Latest shows the skeleton, not "no posts", until its first page arrives | BooruFeedTest.theFirstPageWithPostsIsShown | the skeleton is Compose-only |
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
