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
