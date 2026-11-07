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
untested | test/feed_ink_test.dart › takes two columns: the avatar at 16 dp, everything else at 68 dp | ui post/PostCard.kt, PostGeometry.kt | the avatar now sits in a 48 dp target as XTA's; ported: PostGeometryTest.aPostTakesTwoColumns…
untested | test/feed_ink_test.dart › names the author, handle and time on one line | ui post/AuthorLine.kt | ported: AuthorLineLayoutTest.theNameHandleAndTimeShareOneLineWhenTheyFit
different | test/feed_ink_test.dart › gives way with the handle first, then the name, and keeps the time | ui post/AuthorLine.kt | native cut a handle to "@…"; now left out under 40 dp, and the time keeps 45 % of the line; ported: AuthorLineLayoutTest.theHandleGivesWayFirst…, aLongTimeIsCut…
missing | test/feed_ink_test.dart › with large text puts the handle and time on a second line | ui post/AuthorLine.kt | implemented (text scale ≥ 1.3); ported: AuthorLineLayoutTest.withLargeTextTheHandleAndTimeTakeASecondLine
different | test/feed_ink_test.dart › keeps translate and the ⋯ menu at 48 dp, on the author line | ui post/PostCard.kt, PostFooter.kt | were 32 dp inside the margin; now 48 dp targets, glyph at the top, the last reaching the screen's edge, over the text; ported: PostGeometryTest.translateAndTheMenuAre48DpTargets…
untested | test/feed_ink_test.dart › the opened post keeps its own layout | ui post/PostScreen.kt | visual: the opened post has its own header (name over handle), never PostCard's
untested | test/feed_ink_test.dart › a quoted post keeps its compact header inside the post | ui post/PostCard.kt QuotedPost | visual: 20 dp avatar and author line inside the frame
untested | test/feed_ink_test.dart › a self-thread runs its rail through the avatars, in the same columns | ui post/PostCard.kt | ported: PostGeometryTest.aSelfThreadsRailRunsThroughTheAvatarsCentres
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
