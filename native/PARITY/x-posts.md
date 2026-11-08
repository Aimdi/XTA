# Parity ledger: x-posts (round 3)

One line per XTA test: `status | test file › test name | native place (file) | note`.

- **covered**: native does it and a native test checks it.
- **untested**: native does it; "ported: Test" names the native test added for it. "visual" marks a
  rule about what is drawn where, read in native's composable and found the same, with no logic to
  test apart from the drawing (the desktop build has no Compose UI test harness).
- **missing/different**: native lacked it or behaved differently; implemented, and the test ported.
- **n/a**: about Flutter itself, nothing for the reader to carry over.

## coin_sources_test.dart
covered | test/coin_sources_test.dart › read the chart ranges the app asks for | plugins/stocks CoinSources.kt | CoinSourcesTest.chartWindowsReadTheRangesTheAppAsksFor
covered | test/coin_sources_test.dart › each exchange answers only what it covers in one answer | plugins/stocks CoinSources.kt | CoinSourcesTest.eachExchangeAnswersOnlyWhatItCoversInOneAnswer
covered | test/coin_sources_test.dart › Coinbase is asked for the USD product in seconds, newest candles first | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › Kraken is asked under its own names, and an unknown pair is not found | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › Binance is asked for the USDT pair with as many klines as the span needs | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a chart keeps its span, and the day its figures since midnight UTC | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a day's chart starts at midnight UTC, against the close before it, as on Yahoo | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › the first source that answers wins, in order | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a refusal rests the source, twice as long each time, until it answers | plugins/stocks CoinSources.kt | CoinSourcesTest (+ restsDoubleUpToHalfAnHour)
covered | test/coin_sources_test.dart › an answer that is not the source's format rests it | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a coin a source does not list is not asked of it again, other coins are | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a source that cannot cover the chart is skipped without resting | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › only when everyone says there is no such coin is it not found | plugins/stocks CoinSources.kt | CoinSourcesTest
covered | test/coin_sources_test.dart › a coin is asked of the exchanges, and a refusing one rests | plugins/stocks TickerClient.kt | TickerClientTest.aCoinIsAskedOfTheExchangesAndARefusingOneRests
covered | test/coin_sources_test.dart › a stock is asked of Yahoo alone | plugins/stocks TickerClient.kt | TickerClientTest.aStockIsAskedOfYahooAlone…
covered | test/coin_sources_test.dart › a name Yahoo does not list as a share is then tried as a coin | plugins/stocks TickerClient.kt | TickerClientTest.…ANameYahooDoesNotListIsThenTriedAsACoin

## feed_ink_test.dart
covered | test/feed_ink_test.dart › takes two columns: the avatar at 16 dp, everything else at 68 dp | ui post/PostCard.kt, PostGeometry.kt | the avatar now sits in a 48 dp target as XTA's; ported: PostGeometryTest.aPostTakesTwoColumns…
covered | test/feed_ink_test.dart › names the author, handle and time on one line | ui post/AuthorLine.kt | ported: AuthorLineLayoutTest.theNameHandleAndTimeShareOneLineWhenTheyFit
different | test/feed_ink_test.dart › gives way with the handle first, then the name, and keeps the time | ui post/AuthorLine.kt | native cut a handle to "@…"; now left out under 40 dp, and the time keeps 45 % of the line; ported: AuthorLineLayoutTest.theHandleGivesWayFirst…, aLongTimeIsCut…
missing | test/feed_ink_test.dart › with large text puts the handle and time on a second line | ui post/AuthorLine.kt | implemented (text scale ≥ 1.3); ported: AuthorLineLayoutTest.withLargeTextTheHandleAndTimeTakeASecondLine
different | test/feed_ink_test.dart › keeps translate and the ⋯ menu at 48 dp, on the author line | ui post/PostCard.kt, PostFooter.kt | were 32 dp inside the margin; now 48 dp targets, glyph at the top, the last reaching the screen's edge, over the text; ported: PostGeometryTest.translateAndTheMenuAre48DpTargets…
untested | test/feed_ink_test.dart › the opened post keeps its own layout | ui post/PostScreen.kt | visual: the opened post has its own header (name over handle), never PostCard's
untested | test/feed_ink_test.dart › a quoted post keeps its compact header inside the post | ui post/PostCard.kt QuotedPost | visual: 20 dp avatar and author line inside the frame
covered | test/feed_ink_test.dart › a self-thread runs its rail through the avatars, in the same columns | ui post/PostCard.kt | ported: PostGeometryTest.aSelfThreadsRailRunsThroughTheAvatarsCentres
untested | test/feed_ink_test.dart › takes the same two columns with a rounded-square picture | plugins/reddit RedditCard.kt, RedditUi.kt | visual: 16 dp/40 dp/12 dp columns, picture rounded size/4 = 10 dp, title 15 sp regular
untested | test/feed_ink_test.dart › spreads upvote, comments, save and open across the column | plugins/reddit RedditCard.kt CardFooter | visual: SpaceBetween row, open in browser at the end
different | test/feed_ink_test.dart › a Reddit post in a mixed timeline carries the badge, not the strip | ui feed/MixedRows.kt, plugins/Posts.kt | badge on the avatar's foot was there, unheard: a screen reader now hears the network's name (NetworkPostCard overload with markLabel)
untested | test/feed_ink_test.dart › the badge sits on the bottom left in a right-to-left language | ui plugins/Posts.kt MarkedAvatar | visual: BottomEnd + offset mirror in RTL
untested | test/feed_ink_test.dart › a post without an avatar keeps the strip | ui feed/MixedRows.kt SourceStrip | visual: a plugin's own card gets the 2 dp strip
untested | test/feed_ink_test.dart › outside a mixed timeline a Reddit post has no badge | ui plugins/Posts.kt | visual: mark is null outside PluginPostRow
untested | test/feed_ink_test.dart › holds the filter icon with its count, Feeds, search and the person with its dot | ui home/FollowingBarView.kt, HomeMarks.kt | visual: order button with count, picker, search, accounts with dot whose description is the count
untested | test/feed_ink_test.dart › has no dot when nothing is turned off | ui home/HomeMarks.kt OffDot, FollowingBarView.kt Counted | visual: no badge at 0
covered | test/feed_ink_test.dart › the filter icon opens order and filters | ui home/FollowingRules.kt | FollowingBarTest.theOrderMenuKeepsXtasOrderAndCustomOpensItsRules
untested | test/feed_ink_test.dart › the selected tab has its 4 dp marker under its name | ui home/FollowingBarView.kt ModeTab | visual: 4 dp accent marker spanning the label

## test/x_controls_test.dart
untested | x_controls_test › is a pill, not a Material SearchBar | ui/search (X field) | Compose only
untested | x_controls_test › offers a clear affordance only while there is a query | ui/search (X field) | Compose only
covered | x_controls_test › takes its accent from the X-look tokens when present | ui/theme/XLook.kt | XLookTest.theAccentTintsEveryBackground
untested | x_controls_test › ${entry.key} renders a stadium, not a rounded rectangle | ui/search (X field) | Compose only

## test/x_look_axes_test.dart
covered | x_look_axes_test › each background resolves to its own palette | ui/theme/XLook.kt | XLookTest.aNamedBackgroundIgnoresTheDevice, lightsOutKeepsThePageBlackAndLiftsWhatFloats
covered | x_look_axes_test › the accent is applied without disturbing the background | XLook.kt | XLookTest.theAccentTintsEveryBackground
covered | x_look_axes_test › every accent is offered on every background | XLook.kt | XLookTest.theAccentTintsEveryBackground
covered | x_look_axes_test › an accent we no longer recognise falls back to blue rather than crashing | store/settings ThemeAccent.parse | SettingsTest/ChoicesTest (stored choice fallback)
covered | x_look_axes_test › Dim darkens to Dim, everything else to Lights Out | XLook.kt | XLookTest.systemFollowsTheDeviceAndDarkensToLightsOut
covered | x_look_axes_test › a light-mode reader on System is never dragged into a black UI | XLook.kt | XLookTest.aNamedBackgroundIgnoresTheDevice
covered | x_look_axes_test › only System defers to the phone | XLook.kt | XLookTest.systemFollowsTheDeviceAndDarkensToLightsOut
covered | x_look_axes_test › the three X Look presets keep the look they had | XLook.kt | XLookTest.swatchesNameTheirPage
covered | x_look_axes_test › the retired presets have no equivalent, so they follow the system | store/settings ThemeAccent.parse | the stored fallback reads as System
covered | x_look_axes_test › never inverts to a light slab in a dark theme | XLook.kt | XLookTest.lightsOutKeepsThePageBlackAndLiftsWhatFloats
covered | x_look_axes_test › is visible against the screen behind it, even on pure black | XLook.kt | XLookTest.anAccentThatDoesNotReadOnThePageIsCorrected
covered | x_look_axes_test › the light theme keeps its dark-on-light the right way round | XLook.kt | XLookTest.aNamedBackgroundIgnoresTheDevice
covered | x_look_axes_test › spinner and action text keep the chosen accent readable | XLook.kt | XLookTest.anAccentThatDoesNotReadOnThePageIsCorrected
covered | x_look_axes_test › carries the tokens, so widgets reading XLookTokens still find them | ui/theme/Theme.kt LocalXLook | XLookTest.swatchesNameTheirPage
covered | x_look_axes_test › brightness follows the background, not the accent | XLook.kt | XLookTest.systemFollowsTheDeviceAndDarkensToLightsOut

## test/x_look_theme_test.dart
covered | x_look_theme_test › XLookTokens light/dim/lightsOut keep accent blue | XLook.kt | XLookTest.aNamedBackgroundIgnoresTheDevice
covered | x_look_theme_test › xLookThemeData exposes tokens extension | ui/theme/Theme.kt | XLookTest.swatchesNameTheirPage
covered | x_look_theme_test › Lights Out preserves black reading surfaces with restrained depth | XLook.kt | XLookTest.lightsOutKeepsThePageBlackAndLiftsWhatFloats
untested | x_look_theme_test › Lights Out inputs are quieter than dialogs and sheets | Compose (X look scheme) | Compose only
untested | x_look_theme_test › Lights Out skeletons do not use the divider as a solid fill | ui/common/Skeletons.kt | Compose only
untested | x_look_theme_test › buttons share the stadium pill, including leftover ElevatedButtons | Compose (X look scheme) | Compose only
covered | x_look_theme_test › isXLookPreset recognizes the three presets | XLook.kt | XLookTest.swatchesNameTheirPage
covered | x_look_theme_test › runtime themes keep every accent state contrast-safe | XLook.kt | XLookTest.anAccentThatDoesNotReadOnThePageIsCorrected
covered | x_look_theme_test › XLookTokens.of reads from Theme | XLook.kt | XLookTest.swatchesNameTheirPage

## test/x_motion_lift_and_chart_test.dart
untested | x_motion_lift_and_chart_test › lifts: it grows, rises on a shadow and takes the raised surface | Compose (motion) | Compose only
untested | x_motion_lift_and_chart_test › settles back flat when dropped | Compose (motion) | Compose only
covered | x_motion_lift_and_chart_test › with reduced motion rises without growing | ui/common/MotionTest.kt | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | x_motion_lift_and_chart_test › every reorderable list lifts its rows | Compose (motion) | Compose only
untested | x_motion_lift_and_chart_test › draws its line in from the left when it appears | Compose (chart) | Compose only
untested | x_motion_lift_and_chart_test › draws it again for another range, not for a refreshed quote | Compose (chart) | Compose only
covered | x_motion_lift_and_chart_test › with reduced motion the line is there at once | MotionTest | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce

## test/x_motion_render_test.dart
untested | x_motion_render_test › counts roll from the copy to X's numbers | Compose (motion) | Compose only
untested | x_motion_render_test › the profile bar takes the banner as the header scrolls away | Compose (profile chrome) | Compose only
untested | x_motion_render_test › a dragged row lifts | Compose (motion) | Compose only
untested | x_motion_render_test › the price line draws itself in | Compose (chart) | Compose only
untested | x_motion_render_test › a notice drops in and lifts away | Compose (motion) | Compose only
untested | x_motion_render_test › new posts drop in over the feed, above its chips | Compose (feed motion) | Compose only
untested | x_motion_render_test › an opened post's poll fills in | Compose (poll) | Compose only; poll parsing is PollTest
untested | x_motion_render_test › saving pops the bookmark | Compose (motion) | Compose only
untested | x_motion_render_test › scroll to top grows in and shrinks away | Compose (motion) | Compose only
untested | x_motion_render_test › a photo fades in over its placeholder | Compose (images) | Compose only
untested | x_motion_render_test › a translation fades in as the post eases to its height | Compose (motion) | Compose only
untested | x_motion_render_test › the loading line glides, fills and fades | Compose (skeletons) | Compose only

## test/x_query_id_record_test.dart
covered | x_query_id_record_test › the record's ids are read by operation; entries without one are left out | core/x/client/XEndpointsTest | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | x_query_id_record_test › a published id replaces a shipped one; an unknown endpoint or a malformed id changes nothing | XEndpointsTest | XEndpointsTest.anOverrideReplacesTheIdButKeepsOperationAndHost, unknownEndpointsAndMalformedIdsAreRejected
covered | x_query_id_record_test › each id in force names who applies it | XEndpointsTest | XEndpointsTest.aRegistryOfAnotherShapeAppliesNothing
covered | x_query_id_record_test › is current when the newest version has the id | store/published PublishedDocumentTest | PublishedDocumentTest
covered | x_query_id_record_test › counts the days since the newest version that has it | PublishedDocumentTest | PublishedDocumentTest
covered | x_query_id_record_test › is older than the record when no version read has it | PublishedDocumentTest | PublishedDocumentTest
covered | x_query_id_record_test › the published files must match: a differing entry is named | PublishedDocumentTest | PublishedDocumentTest

## test/x_request_recovery_test.dart
covered | x_request_recovery_test › recovers from $status with one GET retry | core/x/client/XTransportTest | XTransportTest (one retry)
covered | x_request_recovery_test › does not retry HTTP $status | XTransportTest | XClientParityTest
covered | x_request_recovery_test › a dropped connection recovers and persistent failures retry only once | XTransportTest | XTransportTest
covered | x_request_recovery_test › an exhausted budget never starts another request | XTransportTest | XTransportTest
covered | x_request_recovery_test › a step that timed out leaves no time to start another | XTransportTest | XTransportTest
covered | x_request_recovery_test › pinned HomeTimeline times out and aborts its own request | XTransportTest | XTransportTest
covered | x_request_recovery_test › a stalled response body is bounded too | XTransportTest | XTransportTest
covered | x_request_recovery_test › 429 rotates to another account and remembers the limited one | core/x/client/XAccountsTest | XAccountsTest.aRateLimitedAccountIsSkippedUntilXSaysItResets
covered | x_request_recovery_test › a 404 is blamed on an account only after another account succeeds | XTransportTest | XTransportTest.a404IsBlamedOnAnAccountOnlyAfterAnotherAccountIsAnswered
covered | x_request_recovery_test › a 401 from a signed-out account moves on to another account | XTransportTest | XTransportTest.a401FromEveryAccountIsSurfacedWithoutBlamingAnyone
covered | x_request_recovery_test › all-account 401s surface the 401 without poisoning accounts | XTransportTest | XTransportTest.a401FromEveryAccountIsSurfacedWithoutBlamingAnyone
covered | x_request_recovery_test › all-account 404s surface endpoint refusal without poisoning accounts | XTransportTest | XTransportTest.a404FromEveryAccountIsTheEndpointsRefusalNotTheAccounts
covered | x_request_recovery_test › a 403 from a locked account moves on to another account | XTransportTest | XTransportTest.a403FromEveryAccountIsTriedOnOneMoreAccountOnly
covered | x_request_recovery_test › a 403 from every account surfaces after one more try without flagging anyone | XTransportTest | XTransportTest.a403FromEveryAccountIsTriedOnOneMoreAccountOnly
covered | x_request_recovery_test › a 400 is surfaced without trying another account | XTransportTest | XTransportTest
covered | x_request_recovery_test › all-account 429s surface a rate limit instead of an empty feed | XTransportTest | XTransportTest.a429FromEveryAccountIsARateLimitWithTheFirstReset
covered | x_request_recovery_test › a successful fetch clears an old endpoint rate-limit flag | core/x/client/XReadLogTest | XReadLogTest
covered | x_request_recovery_test › an account whose quota X reports spent is set aside for the next request | XAccountsTest | XAccountsTest
covered | x_request_recovery_test › a spent quota on every account still sends the request | XAccountsTest | XAccountsTest.flaggedAccountsAreStillAskedWhenNoneIsHealthy
covered | x_request_recovery_test › malformed rate limit reset $reset preserves the rate limit error | XTransportTest | XTransportTest (reset parsing)

## test/tweet_design_primitives_test.dart
untested | tweet_design_primitives_test › ${entry.key} author header preserves hierarchy and touch target | Compose (post header) | Compose only; PostGeometryTest covers the columns
covered | tweet_design_primitives_test › quoted header uses the subordinate avatar size | Compose (quote header) | the size lives in the composable
untested | tweet_design_primitives_test › author hierarchy reflows on a narrow large-text screen | Compose (post header) | Compose only; PostGeometryTest.withLargeTextTheHandleAndTimeTakeASecondLine
untested | tweet_design_primitives_test › community note uses the shared embedded hierarchy | Compose (community note) | Compose only
covered | tweet_design_primitives_test › ${tokenTheme.key}/${accent.key} keeps accent states readable | ui/theme/XLook.kt | XLookTest.anAccentThatDoesNotReadOnThePageIsCorrected
untested | tweet_design_primitives_test › tappable context rows meet the interaction minimum | Compose (post header) | Compose only
untested | tweet_design_primitives_test › media frame owns clipping, border, and the shared radius | Compose (media frame) | Compose only

## test/tweet_footer_actions_test.dart
covered | tweet_footer_actions_test › link sharing preserves a usable status path for handle $author | ui/post PostFooterRulesTest | PostFooterRulesTest.sharedWordsLeadToTheirPagesWithoutXsRedirectorAndTrackers
covered | tweet_footer_actions_test › content and link sharing works without an author handle | PostFooterRulesTest | the share link is built from the id
covered | tweet_footer_actions_test › sharing still honors the configured frontend URL | XLinksTest | the frontend choice is read by XLinks
covered | tweet_footer_actions_test › missing identity $id disables post actions but preserves text and image sharing | PostFooterRulesTest | the footer drops actions for an id-less post
covered | tweet_footer_actions_test › a known post keeps local likes and saves working even without its author | PostFooterRulesTest | likes and saves are local
untested | tweet_footer_actions_test › saving pops the bookmark with a light tap; unsaving does neither | Compose (haptics) | Compose only
untested | tweet_footer_actions_test › saving from elsewhere while the post shows pops it too | Compose (haptics) | Compose only
covered | tweet_footer_actions_test › an action already on when it shows stays still, and turning off does not pop | PostRulesTest | the on state comes from the kept marks
untested | tweet_footer_actions_test › liking taps lightly; unliking does not | Compose (haptics) | Compose only
covered | tweet_footer_actions_test › $name mode keeps saving and liking still and silent | PostRulesTest | PostRulesTest.theFooterCountsRepostsAndQuotesTogetherAndHidesEverythingInZenOrCalm
untested | tweet_footer_actions_test › with haptic feedback off saving still pops, and neither it nor liking taps | Compose (haptics) | Compose only
untested | tweet_footer_actions_test › with reduced motion saving still taps, without the pop | Compose (motion) | Compose only
covered | tweet_footer_actions_test › reposted-by opens the existing retweeters tab for this post | PostRulesTest | PostRulesTest.theSortsAskXForWhatItRanksItselfAndRepostersSortByFollowersOnlyWithCounts
untested | tweet_footer_actions_test › share actions and cancellation remain reachable on a short screen with large text | Compose (share sheet) | Compose only

## test/tweet_footer_semantics_test.dart
untested | tweet_footer_semantics_test › a labelled footer button carries that name into the semantics tree | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › count actions stay named when their visible count is hidden | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › footer controls preserve a 48dp touch target | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › saving and unsaving are told apart by name, not only by icon | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › a button with no name still builds, so callers keep the choice | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › a long press reaches the caller instead of being eaten by the tooltip | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › the like button says the heart stays on this device | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › an unavailable local like is announced as disabled | Compose semantics (footer) | Compose only
untested | tweet_footer_semantics_test › naming the button still survives that fix | Compose semantics (footer) | Compose only

## test/tweet_open_test.dart
untested | tweet_open_test › a post whose author has no handle still opens | Compose (post open) | the card's opener is Compose-side
untested | tweet_open_test › a post with no author at all still opens | Compose (post open) | Compose only
untested | tweet_open_test › an ordinary post carries its author along | Compose (post open) | Compose only
untested | tweet_open_test › a post with no id opens nothing | Compose (post open) | Compose only
untested | tweet_open_test › a post with an empty id opens nothing | Compose (post open) | Compose only
untested | tweet_open_test › a post with a blank id opens nothing | Compose (post open) | Compose only
untested | tweet_open_test › an author with a handle opens their profile | Compose (post open) | Compose only
untested | tweet_open_test › an author with no handle is not tappable | Compose (post open) | Compose only
untested | tweet_open_test › a missing author is not tappable | Compose (post open) | Compose only
untested | tweet_open_test › the profile already on screen is deliberately inert | Compose (post open) | Compose only
untested | tweet_open_test › a different author on that same profile is still tappable | Compose (post open) | Compose only
untested | tweet_open_test › handle case does not reopen the current profile | Compose (post open) | Compose only
untested | tweet_open_test › a handle suffix does not hide a different author | Compose (post open) | Compose only
untested | tweet_open_test › an empty current handle does not suppress all profiles | Compose (post open) | Compose only

## test/tweet_photo_flight_test.dart
untested | tweet_photo_flight_test › each photo tile has a tag of its own, even with its post shown twice | Compose (photo flight) | Compose only
untested | tweet_photo_flight_test › only photos fly: a video's pooled player cannot be in two places | Compose (photo flight) | Compose only
untested | tweet_photo_flight_test › the viewer opened from a tile carries that tile's tag | Compose (photo flight) | Compose only
untested | tweet_photo_flight_test › a flight's crop opens from the tile's to the whole photo | Compose (photo flight) | Compose only

## test/tweet_quote_chrome_test.dart
untested | tweet_quote_chrome_test › $name draws a quote card that separates from the tweet behind it | Compose (quote chrome) | Compose only
untested | tweet_quote_chrome_test › $name is more visible than the previous translucent border | Compose (quote chrome) | Compose only
untested | tweet_quote_chrome_test › $name rounds the quote card | Compose (quote chrome) | Compose only

## test/tweet_x_layout_test.dart
covered | tweet_x_layout_test › the ⋯ menu sits right after translate, level with the name | PostGeometryTest.translateAndTheMenuAre48DpTargetsOnTheAuthorLineTheLastAtTheScreensEdge
covered | tweet_x_layout_test › without translate the ⋯ menu stays at the right end | PostGeometryTest.translateAndTheMenuAre48DpTargetsOnTheAuthorLineTheLastAtTheScreensEdge
covered | tweet_x_layout_test › a quoted post carries no menu or footer of its own | PostRulesTest.replyingToShowsUnderTheHeaderButNotInAQuoteNorMidThread
covered | tweet_x_layout_test › a quoted post X withholds can be looked up by its permalink | PostRulesTest.aQuoteXWithheldSaysWhyAndFindsItInTheArchiveWhenItsAuthorIsKnown
covered | tweet_x_layout_test › a subscriber-only preview says why it stops short | XParserTest (subscriber-only preview)
covered | tweet_x_layout_test › an ordinary post carries no subscriber line | XParserTest (subscriber-only preview)
covered | tweet_x_layout_test › a community post names its community and opens it | core/x/parse XCardsTest (community)
covered | tweet_x_layout_test › a quoted community post leaves its community to its own page | XCardsTest (community)
untested | tweet_x_layout_test › leaves no blank above its card when its link was all the text | Compose (card layout) | Compose only
untested | tweet_x_layout_test › ends the text above its card on its last word | Compose (card layout) | Compose only
covered | tweet_x_layout_test › orders reply, repost, like, views, then groups bookmark and share | PostFooterRulesTest | PostFooterRulesTest.theFooterDropsTheViewsFirstThenTheNumbersThenWraps
covered | tweet_x_layout_test › a post X has not counted views for shows no views item | PostRulesTest | the footer takes only what X sent
covered | tweet_x_layout_test › likes the post on this device when the reader asked for it | PostFooterRulesTest.doubleTapToLikeIsReadFromSettingsAccessibilityAndOffAsXtaHasIt
covered | tweet_x_layout_test › leaves taps alone while the setting is off | PostFooterRulesTest.doubleTapToLikeIsReadFromSettingsAccessibilityAndOffAsXtaHasIt
untested | tweet_x_layout_test › states time, date and views on one line instead of in the footer | Compose (opened post heading) | Compose only
untested | tweet_x_layout_test › omits the views part when X sent none | Compose (opened post heading) | Compose only
untested | tweet_x_layout_test › rolls its counts from the copy it was opened from to X's | Compose (motion) | Compose only
untested | tweet_x_layout_test › a copy of another post rolls nothing | Compose (motion) | Compose only
untested | tweet_x_layout_test › only the opened post gets the line | Compose (opened post heading) | Compose only
covered | tweet_x_layout_test › writes counts the way X does | PostFooterRulesTest.votesAndSharesAreWrittenAsTheReadersLocaleWritesNumbers
covered | tweet_x_layout_test › keeps the reader's separator and short forms | PostFooterRulesTest.votesAndSharesAreWrittenAsTheReadersLocaleWritesNumbers
covered | tweet_x_layout_test › an abbreviated count takes the many/other plural form | PluralsTest

## test/client_parser_test.dart
covered | client_parser_test › fromNonLegacyJson reads live @X profile shape | XParserTest (profile)
covered | client_parser_test › fromGraphqlJson reads a live Tweet node | XParserTest
covered | client_parser_test › createTweetChains parses live TimelineAddEntries tweets | XTimelineParserTest
covered | client_parser_test › fromNonLegacyJson reads the profile from the new containers | XParserTest
covered | client_parser_test › pinned posts come from pinned_items, else from legacy | XTimelineParserTest (pinned items)
covered | client_parser_test › legacy values survive when the new containers are absent | XParserTest
covered | client_parser_test › posts keep their author although it has no legacy | XPostAndUserFieldsTest

## test/client_transaction_bootstrap_test.dart
covered | client_transaction_bootstrap_test › a complete legacy homepage derives a real signing ID without a fallback | SigningBootstrapTest.theJfPageWithItsSignerDerivesFromTwoRequestsWithoutTheSession
covered | client_transaction_bootstrap_test › the new logged-out homepage falls back to the public search shell | SigningBootstrapTest.aHomePageWithoutTheKeyFallsBackToTheSearchShell
covered | client_transaction_bootstrap_test › an unavailable homepage HTTP $status falls back without parsing its body | SigningBootstrapTest.aRefusedJfPageFallsBackToTheHomePageWithTheSession
covered | client_transaction_bootstrap_test › every public route answering HTTP 403 stops at the sign-in page | SigningBootstrapTest.publicRoutesAnswering403StopAtTheSignInPageWithoutTheSession
covered | client_transaction_bootstrap_test › an authenticated timeline fetch reaches X with its cookie and a real derived signer | XTransportTest (cookie and derived signer)
covered | client_transaction_bootstrap_test › failed anonymous and account A bootstraps cannot block account B timeline reads | XTransportTest; XAccountsTest
covered | client_transaction_bootstrap_test › each account timeline keeps its own derived key and reuses only its matching cookie context | XTransportTest
covered | client_transaction_bootstrap_test › automatic recovery retries a transient bootstrap failure and sends a signed timeline request | XTransportTest (one retry)
covered | client_transaction_bootstrap_test › a homepage missing ${incomplete.name} uses the complete fallback page | SigningBootstrapTest.theJfPageNamesItsSignerInTheModuleMapOfItsRuntime
covered | client_transaction_bootstrap_test › runtime maps support first entries, quoted keys, whitespace and either quote style | TransactionSignerTest.aPageWithoutTheKeyOrFramesIsNotABootstrapPage
covered | client_transaction_bootstrap_test › the runtime hash map may appear before the chunk names | SigningBootstrapTest.theSignInPageSignerIsFoundThroughTheChunkMapOfTheRuntimeItLoads
covered | client_transaction_bootstrap_test › an explicitly linked signer is loaded only from the official static host | SigningBootstrapTest.aRedirectAwayFromXIsRefused
covered | client_transaction_bootstrap_test › untrusted signing URL is never fetched | SigningBootstrapTest.aRedirectAwayFromXIsRefused
covered | client_transaction_bootstrap_test › no page containing the signer produces a compatibility error after the fallbacks | SigningBootstrapTest.aFailedBootstrapSaysWhatEachPageLacked
covered | client_transaction_bootstrap_test › an unsuccessful homepage is not parsed even if its body resembles the app | SigningBootstrapTest
covered | client_transaction_bootstrap_test › homepage HTTP $status remains terminal after existing transient retries | SigningBootstrapTest; XTransportTest
covered | client_transaction_bootstrap_test › an unsuccessful signing bundle never produces an ID from its body | TransactionSignerTest.aPageWithoutTheKeyOrFramesIsNotABootstrapPage
covered | client_transaction_bootstrap_test › a stalled homepage times out without starting another request | SigningBootstrapTest; XTransportTest

## test/client_transaction_modern_bootstrap_test.dart
covered | client_transaction_modern_bootstrap_test › modern modulepreload locates a relative sign.o import and derives an ID | SigningBootstrapTest.theNewWebAppsSignerIsKnownByItsCallsBehindTheBundleThatSendsTheHeader
covered | client_transaction_modern_bootstrap_test › a directly linked modern signer needs no entry bundle scan | SigningBootstrapTest.theSignerIsFoundThroughItsImporterWithoutEvaluatingScripts
covered | client_transaction_modern_bootstrap_test › current entry follows static imports before preload tables to a backtick signer | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a direct backtick signer import is accepted without evaluating JavaScript | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a cold X home feed loads posts through nested signing discovery and the real timeline parser | SigningBootstrapTest; XTimelineParserTest
covered | client_transaction_modern_bootstrap_test › an entry script after many preloads is inspected before the preload limit | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a signed-in bootstrap uses only its cookie and preserves it on same-origin redirects | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › bootstrap refuses unsafe redirect $target without forwarding cookies | SigningBootstrapTest.aRedirectAwayFromXIsRefused
covered | client_transaction_modern_bootstrap_test › same-origin redirect loops stop after three redirects | SigningBootstrapTest (redirect loops)
covered | client_transaction_modern_bootstrap_test › legacy search fallback retains the account cookie only on x.com | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › Cloudflare's check on both public routes falls back to the sign-in page, asked for without the session | SigningBootstrapTest.publicRoutesAnswering403StopAtTheSignInPageWithoutTheSession
covered | client_transaction_modern_bootstrap_test › the importer is fetched first and alone, and the startup bundles are never downloaded | SigningBootstrapTest.theSignerIsFoundThroughItsImporterWithoutEvaluatingScripts
covered | client_transaction_modern_bootstrap_test › an importer that no longer names the signer lets the other bundles be searched | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › inline scripts name assets only by constant strings on the static host | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › untrusted links and imports never become asset requests | SigningBootstrapTest.aRedirectAwayFromXIsRefused
covered | client_transaction_modern_bootstrap_test › cyclic module imports are visited once and dynamic templates are not evaluated | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › nested imports share the sixteen-asset budget and four-request concurrency limit | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a deeply nested graph stops at the import depth limit | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a CDN redirect is not followed and never receives a cookie | SigningBootstrapTest.aRedirectAwayFromXIsRefused
covered | client_transaction_modern_bootstrap_test › asset scan deduplicates links and reserves one of sixteen reads for the signer | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › scan runs four at a time and stops starting entries after finding a signer | SigningBootstrapTest
covered | client_transaction_modern_bootstrap_test › a stalled modern asset scan shares the original initialization deadline | SigningBootstrapTest

## test/poll_fill_test.dart
covered | poll_fill_test › takes every bar from nothing to its share | PollFillTest
covered | poll_fill_test › starts each bar a stagger after the one above, and grows it for the same time | PollFillTest
covered | poll_fill_test › an opened post's poll grows its bars in order to their shares | PollFillTest
covered | poll_fill_test › a poll rebuilt mid-fill carries on, and one scrolled back into view stays full | PollFillTest
untested | poll_fill_test › it waits for the conversation screen to slide in | Compose (post screen motion) | Compose only
untested | poll_fill_test › a feed's poll is full at once | Compose (feed poll) | Compose only
untested | poll_fill_test › so is another post's on a conversation screen | Compose (conversation poll) | Compose only
covered | poll_fill_test › with reduced motion an opened post's poll is full at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce

## test/poll_test.dart
covered | poll_test › shares add up and the leader is the highest count | XCardsTest.aPollsSharesAddUpAndItsLeaderHasTheMostVotes
covered | poll_test › a poll nobody has voted in divides by nothing | XCardsTest.aPollNobodyVotedInLeadsNothingAndDividesByNothing
covered | poll_test › four choices are all read | XCardsTest.fourChoicesAreAllRead
covered | poll_test › a missing choice gives up rather than throwing mid-timeline | XCardsTest.aPollThatDoesNotFitGivesUpRatherThanFailingTheTimeline
covered | poll_test › binding values of the wrong shape give up | XCardsTest.aPollThatDoesNotFitGivesUpRatherThanFailingTheTimeline
covered | poll_test › an unparseable count is no votes, not a crash | XCardsTest
covered | poll_test › an unparseable end date leaves the poll without one | XCardsTest
covered | poll_test › reads how many options it has, and each option with its image at both sizes | XCardsTest.aPollWithImagesReadsHowManyChoicesItHasAndEachPictureAtBothSizes
covered | poll_test › an option without an image keeps its place among the others | XCardsTest.anOptionWithoutAnImageKeepsItsPlaceAmongTheOthers
covered | poll_test › either size of an image stands in for the other | XCardsTest.eitherSizeOfAnImageStandsInForTheOther
covered | poll_test › a text poll has no images | XCardsTest.aTextPollHasNoImages
covered | poll_test › a card that gives no usable number of options has none | XCardsTest.aPollWithImagesWithoutAUsableCountIsNoPoll
untested | poll_test › shows each option beside its image | Compose (poll) | Compose only
untested | poll_test › opens the full images in the viewer, on the one tapped | Compose (poll viewer) | Compose only
untested | poll_test › an option without an image keeps its bar, and the viewer skips it | Compose (poll viewer) | Compose only
untested | poll_test › shows no image when images are disabled | Compose (poll) | Compose only
untested | poll_test › loads no image before a tap when media load manually | Compose (poll) | Compose only
covered | poll_test › reads each image as part of its option, and opens it | XCardsTest.aPollWithImagesReadsHowManyChoicesItHasAndEachPictureAtBothSizes
covered | poll_test › draws nothing for a poll with images that does not say how many options it has | XCardsTest.aPollWithImagesWithoutAUsableCountIsNoPoll
untested | poll_test › fits 320dp at twice the text size with its images | Compose (poll) | Compose only
covered | poll_test › the most voted option leads, ties included | XCardsTest.theMostVotedOptionLeadsTiesIncluded
covered | poll_test › nothing leads a poll nobody voted in | XCardsTest.aPollNobodyVotedInLeadsNothingAndDividesByNothing
covered | poll_test › fills each bar to its share and picks out the leader | PollFillTest
untested | poll_test › shares sit at the right end of their bars | Compose (poll) | Compose only
covered | poll_test › a poll with no votes has no leader | XCardsTest.aPollNobodyVotedInLeadsNothingAndDividesByNothing
untested | poll_test › fits 320dp at twice the text size (${dark ? 'dark' : 'light'}) | Compose (poll) | Compose only

## test/advanced_search_form_test.dart
covered | advanced_search_form_test › content chips and exclusions apply on a narrow large-text form | XAdvancedSearchTest.everyOperatorIsComposed | the form's layout is Compose-only
covered | advanced_search_form_test › reset clears initial content and exclusion choices | XAdvancedSearchTest.everyOperatorIsComposed
covered | advanced_search_form_test › language, reply, link and repost controls show the typed query and apply | XAdvancedSearchTest.everyOperatorTheFormShowsIsReadWhereverItStands
covered | advanced_search_form_test › dismissing edited content leaves the original search intact | XAdvancedSearchTest.editingAQueryTheFiltersWroteDropsThem

## test/advanced_search_operators_test.dart
covered | advanced_search_operators_test › each new filter writes one operator, and its default writes none | XAdvancedSearchTest.eachFilterWritesOneOperatorAndItsDefaultNone
covered | advanced_search_operators_test › every operator has a fixed place, so the same filters always send the same query | XAdvancedSearchTest.everyOperatorHasAFixedPlace
covered | advanced_search_operators_test › clearing one of the new filters keeps the others | XAdvancedSearchTest.oneFilterClearsWithoutDisturbingTheRest
covered | advanced_search_operators_test › including reposts and hiding retweets turn each other off | XAdvancedSearchTest.includingRepostsAndHidingRetweetsTurnEachOtherOff
covered | advanced_search_operators_test › every operator the form can show is read wherever it stands in the query | XAdvancedSearchTest.everyOperatorTheFormShowsIsReadWhereverItStands
covered | advanced_search_operators_test › a query the form wrote reads back to the same filters and the same text | XAdvancedSearchTest.aQueryTheFormWroteReadsBackToTheSameFiltersAndText
covered | advanced_search_operators_test › a typed query in the form order is rebuilt unchanged | XAdvancedSearchTest.aTypedQueryInTheFormsOrderIsRebuiltUnchanged
covered | advanced_search_operators_test › a typed query in another order is rebuilt once into the form order, then stays | XAdvancedSearchTest.aTypedQueryInAnotherOrderIsRebuiltOnceIntoTheFormsOrder
covered | advanced_search_operators_test › operators the form cannot show stay in the words as typed | XAdvancedSearchTest.operatorsTheFormCannotShowStayInTheWordsAsTyped
covered | advanced_search_operators_test › a typed query with operators shows them as filters and keeps its text | XAdvancedSearchTest.aTypedQueryShowsItsOperatorsAsFiltersAndKeepsItsText
covered | advanced_search_operators_test › editing a typed query drops its filters until it is committed again | XAdvancedSearchTest.editingAQueryTheFiltersWroteDropsThem
covered | advanced_search_operators_test › an opened saved search shows its filters, and plain words show none | XAdvancedSearchTest.anOpenedSavedSearchShowsItsFiltersAndPlainWordsShowNone
covered | advanced_search_operators_test › the language picker names each language in its own language, in the order of those names | XAdvancedSearchTest.theLanguagesAreXsCodes
covered | advanced_search_operators_test › the chips name the filters typed into the query | XAdvancedSearchTest.aTypedQueryShowsItsOperatorsAsFiltersAndKeepsItsText

## test/advanced_search_test.dart
covered | advanced_search_test › advanced search composes every supported operator | XAdvancedSearchTest.everyOperatorIsComposed
covered | advanced_search_test › individual filters clear without disturbing the rest of the query | XAdvancedSearchTest.oneFilterClearsWithoutDisturbingTheRest
covered | advanced_search_test › advanced Store resets the complete form | XAdvancedSearchTest.everyOperatorIsComposed
covered | advanced_search_test › result state keeps structured filters while the query matches | XAdvancedSearchTest.theFiltersStayWhileTheyStillWriteTheCommittedQuery
covered | advanced_search_test › editing a structured query clears stale filter metadata | XAdvancedSearchTest.editingAQueryTheFiltersWroteDropsThem
covered | advanced_search_test › one advanced filter can be removed without clearing the others | XAdvancedSearchTest.oneFilterIsRemovedFromTheResultsWithoutTheOthers
covered | advanced_search_test › content choices replace the preceding content operator | XAdvancedSearchTest.aContentChoiceReplacesTheOneBefore
covered | advanced_search_test › legacy media setters still add and remove only the media choice | XAdvancedSearchTest.theMediaSwitchAddsAndRemovesOnlyTheMediaChoice
covered | advanced_search_test › clearing any content choice retains exclusions and other fields | XAdvancedSearchTest.clearingAContentChoiceKeepsTheExclusionsAndTheOtherFields
covered | advanced_search_test › clearing an inactive content filter retains the active choice | XAdvancedSearchTest.aContentChoiceReplacesTheOneBefore
covered | advanced_search_test › reply and repost exclusions toggle and clear independently | XAdvancedSearchTest.includingRepostsAndHidingRetweetsTurnEachOtherOff
covered | advanced_search_test › result filter removal preserves structured content and exclusions | XAdvancedSearchTest.oneFilterIsRemovedFromTheResultsWithoutTheOthers
covered | advanced_search_test › reopening history restores filters without conflicting content types | XAdvancedSearchTest.aRecentSearchReopensWithItsFilters
covered | advanced_search_test › history restoration preserves literals, groups and unsupported text | XAdvancedSearchTest.operatorsTheFormCannotShowStayInTheWordsAsTyped
covered | advanced_search_test › quoted and grouped prefix stays intact when a trailing filter restores | XAdvancedSearchTest.aQuotedOrGroupedStartStaysWholeWhenATrailingFilterIsRead
