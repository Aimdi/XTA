# Parity ledger: batch "app"

One line per XTA test: `status | test file › test name | native place (file) | note`.
Statuses: covered (native does it, a native test checks it), untested → ported (native did it, the test is now ported),
missing → implemented (native lacked it, now implemented and tested), n/a (Flutter mechanics only), open (not done; why).

## alt_microblogging_journey_test.dart
covered | alt_microblogging_journey_test › real Home retains loaded readers and their selected sections when grouping is undone | ui/home/HomeViewModel.kt (HomeStrip.tabs) | Home's pages never depend on the grouping switch (only picker/header do), so a reader page is never rebuilt; AltMicrobloggingTest.theMembersGiveWay… + thePickerLists… check grouping changes only what is listed

## alt_microblogging_test.dart
covered | alt_microblogging_test › grouping round trip preserves plugin preferences, credentials and ordered pins | ui/home/AltMicroblogging.kt, store AppPrefs (home.alt_microblogging.*) | grouping is one flag; AltMicrobloggingTest.theMembersGiveWay…, onlyAMemberIsRemembered…
covered | alt_microblogging_test › disabled, unpinned and missing remembered sources cannot be reintroduced | AltMicroblogging.destination, core FeedStrip.tabs | AltMicrobloggingTest.theEntryOpensTheOneShown…; FeedStripTest (unusable pins dropped)
untested → ported | alt_microblogging_test › one Home row retains unread state and opens a real reader, large RTL false/true | ui/home/SourcePickerSheet.kt (entriesOf, MicroblogsRow) | AltMicrobloggingTest.thePickerListsTheMicroblogsAsOneRow…; the row's dot is any member unread, it opens AltMicroblogging.destination
untested → ported | alt_microblogging_test › Plugin Store switch immediately restores every original row without changing selection | SourcePickerSheet.kt entriesOf | AltMicrobloggingTest.thePickerLists… (ungrouped = every row)
untested | alt_microblogging_test › all service chips remain reachable at 320dp with double text in de/ar | ui/home/HomeFeed.kt MicroblogSwitcher | Compose layout (scrolling row of 48dp chips); no Compose UI test harness here
covered | alt_microblogging_test › Networks groups across recent and all sections and can still open Threads | AltMicroblogging.sections, HomeBottomBar.kt MicroblogsEntry | AltMicrobloggingTest.theNetworksSheetLists…

## feed_strip_add_sheet_test.dart
covered | feed_strip_add_sheet_test › the plus sheet can add, remove and reorder plugin timelines | ui/home/StripSheet.kt (StripPins), HomeViewModel.kt | HomeMenuTest.theSheetSplitsPinnedAndAvailableNetworks, movingAPinKeepsTheNetworksShown; FeedStripTest.pinsAreAddedOnceForgottenAndMoved

## feed_strip_test.dart
covered | feed_strip_test › an unset strip lists no network until the reader adds one | store AppPrefs home.feed_strip_plugins (default empty), core/feed/FeedStrip.migrated | FeedStripTest.aFirstLaunchStartsWithNoNetwork…
covered | feed_strip_test › an empty saved list is intentional when the plugin still has a tab | FeedStrip.tabs/migrated | FeedStripTest.followingAndForYouAlwaysComeFirst… (empty pins → Following, X)
covered | feed_strip_test › a hidden-tab plugin off the strip stays one Add timeline away | FeedStrip.candidates | FeedStripTest.aNetworkOffTheStripStaysOneAddAway
covered | feed_strip_test › saved pins are returned as-is | FeedStrip.visible/tabs | FeedStripTest.followingAndForYou…
covered | feed_strip_test › hidden-tab plugins Home used to add are kept by the migration | FeedStrip.migrated | FeedStripTest.aFirstLaunchStarts…/older install
covered | feed_strip_test › pinning a feed plugin writes it onto the strip | FeedStrip.pinned, IntroFlow/HomeViewModel | FeedStripTest.pinsAreAddedOnce…
covered | feed_strip_test › always starts with Following and For you | FeedStrip.tabs | FeedStripTest.followingAndForYouAlwaysComeFirst…, HomeStripTest
covered | feed_strip_test › skips plugins that are not enabled | FeedStrip.tabs(usable) | FeedStripTest.followingAndForYou…
covered | feed_strip_test › includes an enabled pin | FeedStrip.tabs, HomeStrip.tabs | FeedStripTest, HomeStripTest.followingAndForYouComeFirst…
covered | feed_strip_test › empty pins show no plugin, even one that hid its bottom tab | FeedStrip.tabs | FeedStripTest.followingAndForYou…
n/a | feed_strip_test › equality is by id, not identity | — | Dart FeedTab ==; native HomeTab is a data class/object
untested | feed_strip_test › X tabs use house and spark; plugins reuse XtaPlugin.icon | ui/home/HomeFeed.kt TimelineMark | Following = dynamic_feed, X = X mark, plugins = plugin.icon, as XTA; icon choice lives in a composable
covered | feed_strip_test › moves a pin to the slot after it is taken out | FeedStrip.reorder | FeedStripTest.pinsAreAddedOnce…
covered | feed_strip_test › a no-op or out-of-range index leaves the list | FeedStrip.reorder | FeedStripTest.pinsAreAddedOnce…
covered | feed_strip_test › persists the new pin order | StripPins.moved + HomeViewModel writes HomePrefs.feedStripNetworks | HomeMenuTest.movingAPinKeepsTheNetworksShown

## home_chrome_test.dart
n/a | home_chrome_test › HomeSelectionStore only changes for a new selection | — | flutter_triple store notification mechanics; native state is a StateFlow (equal values never re-emit)
untested | home_chrome_test › feed switcher marks and returns the selected feed | ui/home/SourcePickerSheet.kt PickerRow (tick on the shown timeline) | Compose; selection logic is SourcePickerSheet entriesOf/onSource (AltMicrobloggingTest)
untested | home_chrome_test › bottom navigation uses distinct selected icons and a hairline boundary | ui/home/HomeBottomBar.kt (Hairline over the bar, filled/outlined icons per selection, 64dp) | Compose drawing
untested | home_chrome_test › long-press Home invokes the picker action without changing tabs | HomeBottomBar.kt BarItem onLongClick = onHoldHome for the Feed page | Compose gesture
covered | home_chrome_test › selected navigation remains legible with a yellow accent | ui/theme XLook (accent corrected for contrast) | XLookTest.anAccentThatDoesNotReadOnThePageIsCorrected
untested | home_chrome_test › home title and actions remain bounded at large text scale | HomeScreen.kt/HomeFeed.kt titles maxLines 1 + Ellipsis | Compose layout
untested | home_chrome_test › home source dock keeps contained selection and a fixed add action | HomeFeed.kt TimelineTopBar / StripSheet | Compose layout; native's strip add is the top bar's picker + sheet

## home_filter_drawer_review_test.dart
covered | home_filter_drawer_review_test › Home filter stages changes, refuses to save no account, and applies once | ui/home/HomeFilter.kt (HomeFilterDraft), HomeFilterSheet.kt (draft applied only by Apply) | HomeFilterTest.theDraftTogglesOnlyAndResets, allAccountsOffCannotBeApplied, theFilterIsKeptInTheSettings…
covered | home_filter_drawer_review_test › Home filter keeps only one group on with a long press | HomeFilterDraft.only, FilterRow onLongClick | HomeFilterTest.theDraftTogglesOnlyAndResets
covered | home_filter_drawer_review_test › Home filter searches accounts and persists independent group toggles | HomeFilterSheet.kt (trimmed, case-blind search), HomeFilter.applied | HomeFilterTest.theFilterIsKeptInTheSettings…, theDraftTogglesOnlyAndResets (reset = NONE_OFF)
missing → implemented | home_filter_drawer_review_test › an empty Home account filter keeps Add account available | ui/home/HomeFilterSheet.kt NoAccounts | native showed "No results"; now XTA's home_feed_accounts_empty + Add account (sign-in). Composable, no unit test
covered | home_filter_drawer_review_test › Home group drawer shows pins first and searches without losing group identity | ui/home/HomeGroups.kt DrawerGroups.matching | HomeMenuTest.theGroupSearchKeepsPinnedGroupsFirst, theMenuListsTheGroupsThatCanOpen
untested | home_filter_drawer_review_test › Home filter remains usable at 320px with large text and the keyboard open | HomeFilterSheet.kt | Compose layout (sheet 90% high, list weighted, buttons pinned under it)
untested | home_filter_drawer_review_test › Home drawer search and selected group remain reachable with large text, RTL and keyboard | ui/home/AccountMenu.kt | Compose layout

## home_first_run_strip_test.dart
covered | home_first_run_strip_test › persists an empty strip, so Home offers only Following and X | store AppPrefs feedStripNetworks default [] , feedStripHandPicked default true | FeedStripTest.aFirstLaunchStarts…, XtaPrefsTest (hand-picked)
covered | home_first_run_strip_test › installing plugins afterwards offers them instead of adding them | FeedStrip.candidates; installing never pins (only Intro/sheet/store pin) | FeedStripTest.aNetworkOffTheStripStaysOneAddAway
covered | home_first_run_strip_test › starts empty even if a plugin were already switched on | FeedStrip.migrated | FeedStripTest.aFirstLaunchStartsWithNoNetwork…
missing → implemented | home_first_run_strip_test › a saved strip is kept as it is and later installs are not added | store/settings/FeedStripMigration.kt | a restored backup that already hand-picked Home keeps those pins; a later install is not added. FeedStripMigrationTest.aHandPickedStripIsKeptAndLaterInstallsAreNotAdded
missing → implemented | home_first_run_strip_test › a strip that was never saved keeps every timeline it showed | store/settings/FeedStripMigration.kt | an old backup with no saved pins keeps every enabled Home network. FeedStripMigrationTest.aStripThatWasNeverSavedKeepsEveryTimelineItShowed
missing → implemented | home_first_run_strip_test › a hidden-tab plugin Home added on its own is written into the strip | store/settings/FeedStripMigration.kt | a network that hid its tab is written onto Home. FeedStripMigrationTest.aHiddenTabNetworkHomeShowedIsWrittenIntoTheStrip
covered | home_first_run_strip_test › a plugin installed with its bottom tab hidden waits under Add timeline | FeedStrip.candidates, HomeBar.pages | FeedStripTest.aNetworkOffTheStrip…, HomeBarTest
covered | home_first_run_strip_test › hiding a bottom tab still moves that plugin to Home | FeedStrip.pinned (PluginErase.kt pinned) | FeedStripTest.pinsAreAddedOnce…
covered | home_first_run_strip_test › fresh install: the picker adds plugin timelines one at a time | StripSheet.kt, HomeViewModel.pin | HomeMenuTest.theSheetSplits…, FeedStripTest.pinsAreAddedOnce…

## home_following_bar_test.dart
covered | home_following_bar_test › Following's header is one bar: Posts / Media, then the filter icon, Feeds, search and accounts | ui/home/FollowingBarView.kt, FollowingRules.kt FollowingBar | FollowingBarTest.twoRowsAreTwiceAsTall (48 dp, 3 actions); order of controls is the Row's
untested | home_following_bar_test › The filter icon counts filters and says the order, without drawing its name | FollowingBarView.kt OrderButton (badge = activeFilters, description "‹order› · Filters") | Compose; count = GroupFeedRules.activeFilters (GroupFeedRulesTest)
covered | home_following_bar_test › Tweets / Media at {390,411,360,320} dp, text ×{1,1.3}, {en,de}: oneRow/compact/twoRows (5 cases) | FollowingRules.kt FollowingBar.fit | FollowingBarTest.theTabsMoveCloserThenToASecondRow…
covered | home_following_bar_test › The closer fit keeps the filter icon | FollowingBar.fit/height | FollowingBarTest.twoRowsAreTwiceAsTall (compact = one 48 dp row)
covered | home_following_bar_test › Without room for one row the tabs sit under the controls | FollowingBar.height(TWO_ROWS) | FollowingBarTest.twoRowsAreTwiceAsTall
untested | home_following_bar_test › The filter icon waits for the group; the rest is there at once | FollowingBarView.kt | Compose
untested | home_following_bar_test › The selected tab carries the marker, and Media asks for media | FollowingBarView.kt | Compose; Media key = FollowingBar.MEDIA_KEY (FollowingBarTest)
covered | home_following_bar_test › The chip opens Recent, Popular, Custom and Filters | FollowingBar.ORDERS, opensCustom | FollowingBarTest.theOrderMenuKeepsXtasOrder…
untested | home_following_bar_test › The Feeds sheet offers the side menu, and the avatar on a long press | SourcePickerSheet.kt (menu row) | Compose

## home_group_filter_test.dart
covered | home_group_filter_test › profileIdsExcludedByGroups unions members of disabled groups | core/session/Session.kt excludedFromFollowing | GroupSessionTest.theFollowingFeedLeavesOutGroupsSwitchedOff…
covered | home_group_filter_test › turning off a parent also hides nested group members | Session.excludedFromFollowing (GroupTree.withDescendants) | GroupSessionTest.theFollowingFeedLeavesOut… ("turning off a parent hides its nested groups too")
covered | home_group_filter_test › subscriptionAllowedInFollowing honours inFeed and group exclusion | Session.feedMembers | SessionTest.theFeedReadsOnlyTheAccountsXtaShowsInIt, GroupSessionTest
covered | home_group_filter_test › HomeGroupFilterStore persists disabled group ids | HomeFilter.applied (HomePrefs.followingHiddenGroupIds + session.offFollowing) | HomeFilterTest.theFilterIsKeptInTheSettingsAndTheSession
covered | home_group_filter_test › a group can be switched off Following | HomeFilterSheet.kt FilterRow, HomeFilterDraft.toggled | HomeFilterTest.theDraftTogglesOnlyAndResets

## home_hiding_header_test.dart
untested | home_hiding_header_test › without the home bar to follow, the header stays | ui/home/HomeScreen.kt / HomeFeed.kt (top bar follows HomeBarsState.hidden only when hideOnScroll) | Compose layout
covered | home_hiding_header_test › the header leaves with the bottom bar and returns with it | HomeFeed.kt (header uses the same HomeBarsState) | rule itself in BarHiding (HomeBarTest)

## home_navigation_bar_test.dart
untested | home_navigation_bar_test › the light/dim/lights out bar is flat across the bottom with a hairline on top (3 themes) | ui/home/HomeBottomBar.kt (background × 0.88, Hairline, 64 dp) | Compose drawing; constants match XTA (kHomeNavigationBarOpacity 0.88, height 64)
untested | home_navigation_bar_test › on light/dim/lights out every icon is in the text colour and the selected one is filled (3 themes) | HomeBottomBar.kt BarItem/BarIcon (filled when on, bold label) | Compose drawing
untested | home_navigation_bar_test › the bar keeps its colour under the system gesture area | HomeBottomBar.kt (background on the Column, navigationBars inset inside) | Compose layout
untested | home_navigation_bar_test › every destination keeps a 48dp touch target on a narrow phone | HomeBottomBar.kt (weight(1f) × 64 dp) | Compose layout
untested | home_navigation_bar_test › long labels ellipsize at 2x text on 320dp (ltr, rtl) | HomeBottomBar.kt BarItem (maxLines 1, Ellipsis) | Compose layout
untested | home_navigation_bar_test › without labels, as by default, the icons sit in the middle of the bar | HomeBottomBar.kt (Arrangement.Center) | Compose layout

## home_navigation_visibility_test.dart
covered | home_navigation_visibility_test › the bar follows the content pixel for pixel, within its travel | ui/home/HomeBar.kt BarHiding.followed | HomeBarTest.theBarsFollowTheFingerAndSettle…
untested → ported | home_navigation_visibility_test › a stopped bar rests by the last direction and how far it went | BarHiding.restsHidden | HomeBarTest.aStoppedBarRestsByTheLastDirection…
different → fixed | home_navigation_visibility_test › settling takes up to 250 ms, never under 120 ms | BarHiding.settleMillis (was 280 ms × distance in HomeBottomBar) | HomeBarTest.settlingTakesUpTo250MsNeverUnder120
covered | home_navigation_visibility_test › a partial scroll moves the bar partly, in both directions | BarHiding.scrolled | HomeBarTest.theBarsFollowTheFinger…
untested | home_navigation_visibility_test › a scroll end settles a caught bar away with an eased motion | HomeBottomBar.kt HomeBarsState.moveTo (tween, FastOutSlowIn) | Compose animation
covered | home_navigation_visibility_test › a short scroll down springs back, a short scroll up drops away again | BarHiding.resting | HomeBarTest.theBarsFollowTheFinger…
untested | home_navigation_visibility_test › with reduced motion the settle is instant | HomeBarsState.moveTo (reduced → snap) | Compose state holder
different → fixed | home_navigation_visibility_test › the top, the end of a list and a tab change bring it back | HomeBarsState.connection onPostScroll | native showed the bar only past the top; now past the end too (any leftover scroll); no Compose test harness
covered | home_navigation_visibility_test › a programmatic jump or a sideways scroll does not move it | HomeBarsState (nested scroll only sees drags; available.y == 0 ignored) | by construction
missing → implemented | home_navigation_visibility_test › a screen reader keeps it on screen | ui/common/ScreenReader.kt, HomeScreen.kt rememberShell | Android's touch exploration keeps the bars put (rememberScreenReaderOn); the desktop has none. HomeBarTest covers the hide rule the switch turns off
untested | home_navigation_visibility_test › with the setting off the bar stays | rememberHomeBars(enabled) | Compose state holder
covered | home_navigation_visibility_test › the bar tracks the finger, then settles away when it lifts | HomeBarsState | rule in BarHiding (HomeBarTest)
untested | home_navigation_visibility_test › a hidden bar lets taps through, a shown one takes them | HomeBottomBar.kt hiding() (bar laid out 0 high when gone) | Compose layout
untested | home_navigation_visibility_test › with reduced motion the lifted finger snaps it away at once | HomeBarsState.moveTo | Compose state holder

## home_source_groups_test.dart
covered | home_source_groups_test › legacy For you destination restores the dedicated X source | ui/home/HomeTabs.kt HomeStrip.initialIndex (LEGACY_FOR_YOU) | HomeStripTest.homeOpensOnTheDefaultFeed ("foryou" → X)
covered | home_source_groups_test › X is available without optional plugins and For you is not a Home row | core FeedStrip.tabs | FeedStripTest.followingAndForYouAlwaysComeFirst…, HomeStripTest
covered | home_source_groups_test › drawer prioritizes pins without mutating the chosen group order | ui/home/HomeGroups.kt DrawerGroups.matching | HomeMenuTest.theGroupSearchKeepsPinnedGroupsFirst

## home_source_picker_marks_test.dart
untested | home_source_picker_marks_test › every Home source picker mark fills the same box at the same size (grouped false/true) | ui/home/SourcePickerSheet.kt (every mark 24 dp: TimelineMark, GroupMark, MicroblogMark) | Compose layout

## home_strip_startup_test.dart
covered | home_strip_startup_test › home strip chrome builds after a JSON-string strip pref | store settings Pref.kt lenientList | SettingsTest ("a JSON array in a string")
untested | home_strip_startup_test › networks sheet builds with RSS on the strip | ui/home/HomeBottomBar.kt NetworksSheet | Compose; rows from AltMicroblogging.sections (AltMicrobloggingTest)
n/a | home_strip_startup_test › IconLabel survives a tight tab and a scrollable strip | — | Flutter RenderFlex overflow of XTA's IconLabel widget; native's strip is a picker, no IconLabel
untested | home_strip_startup_test › Networks Add timeline opens after the sheet pops | HomeScreen.kt HomeSheets (sheet = STRIP replaces the networks sheet) | Compose

## home_swipe_navigation_test.dart
covered | home_swipe_navigation_test › previews a destination but commits only on release, with one tick | ui/home/TimelineSwipeView.kt (preview follows, turn + SegmentTick on lift), TimelineSwipe.target/progress, HomeBar.swiped | AltMicrobloggingTest.aDragNamesTheNextTimeline…, HomeBarTest.aSwipeAcrossTheBar…
covered | home_swipe_navigation_test › with haptic feedback off a committed swipe gives no tick | ui/common switched haptics (LocalHapticFeedback provided off by the setting) | SwitchedHapticsTest
covered | home_swipe_navigation_test › short, reversed, cancelled, and boundary drags stay quiet | HomeBar.swiped, TimelineSwipe.reversed/target | HomeBarTest.aSwipeAcrossTheBar… ("too short", "no wrapping"), AltMicrobloggingTest.aDragThatTurnsBack…
untested | home_swipe_navigation_test › a second pointer cancels even if it lifts before the first | TimelineSwipeView.kt (blocked once >1 pointer pressed) | Compose gesture
untested | home_swipe_navigation_test › vertical and diagonal feed drags scroll without changing source | TimelineSwipeView.kt (horizontal slop + SIDEWAYS 1.3) | Compose gesture; SIDEWAYS constant
untested | home_swipe_navigation_test › a nested carousel owns swipes at both ends | TimelineSwipeView.kt (horizontal slop only on unconsumed drags) | Compose gesture
untested | home_swipe_navigation_test › a slider and press controls retain their gestures and semantics | TimelineSwipeView.kt | Compose gesture
covered | home_swipe_navigation_test › system back edges and the conservative fallback are excluded | TimelineSwipe.startsInside (EDGE 24 dp) | AltMicrobloggingTest.aDragStartingAtTheEdgesIsTheSystems
covered | home_swipe_navigation_test › RTL reverses the physical direction of the source sequence | TimelineSwipe.target(rtl), HomeBar.swiped(rtl) | AltMicrobloggingTest, HomeBarTest
untested | home_swipe_navigation_test › external source and source-order changes invalidate an armed swipe | TimelineSwipeView.kt pointerInput(index, count, rtl) restarts on a change | Compose gesture
untested | home_swipe_navigation_test › a route covering the feed cancels the outstanding interaction | TimelineSwipeView.kt (gesture cancelled when the page leaves composition) | Compose gesture
untested | home_swipe_navigation_test › reduced motion during spring return removes it without a command | TimelineSwipeView.kt preview.settle(reduced) | Compose state
untested | home_swipe_navigation_test › a fresh swipe during return previews and commits its own destination | TimelineSwipeView.kt preview.follow cancels settling | Compose state
covered | home_swipe_navigation_test › a rejected source change emits no success feedback | HomeBar.swiped returns the same index past the ends → no tick | HomeBarTest ("no wrapping")
untested | home_swipe_navigation_test › actual tweet media does not hand its edge drag to main navigation | TimelineSwipeView.kt (consumed drags never start a turn) | Compose gesture

## home_timeline_layout_test.dart
untested | home_timeline_layout_test › populated Home layout light/dark/black/large-rtl (4 variants) | ui/home/HomeScreen.kt, HomeFeed.kt | golden-style Compose layout; no Compose UI test harness
untested | home_timeline_layout_test › Following keeps its one bar while the feed is read | FollowingBarView.kt (48 dp bar, controls) | Compose layout; FollowingBar.height in FollowingBarTest
untested | home_timeline_layout_test › The Feeds sheet opens the side menu | SourcePickerSheet.kt (menu row → account menu) | Compose
untested | home_timeline_layout_test › The last Home post scrolls fully above the floating bar | HomeFeed.kt (contentPadding bottom = bar height) | Compose layout
covered | home_timeline_layout_test › On the desktop, where a mouse cannot pull it down, Following shows refresh | FollowingBar.actions(desktop) = 4 (refresh) | FollowingBarTest.twoRowsAreTwiceAsTall (actions(desktop))
untested | home_timeline_layout_test › Home keeps nothing for the status bar under its header | HomeScreen.kt insets | Compose layout
untested | home_timeline_layout_test › A short scrollable Home keeps its bar stable | HomeBottomBar.kt HomeBarsState (nothing to scroll → no leftover → bar stays) | Compose
untested | home_timeline_layout_test › Source picker preserves a reading position and opens timeline management | SourcePickerSheet.kt, StripSheet.kt | Compose; pages keep their own LazyListState
covered | home_timeline_layout_test › reading swipes switch source and return to cached posts and position | TimelineSwipeView.kt, GroupFeedViewModel (FeedSessionCache) | GroupFeedViewModelTest checks a reopened feed shows its kept posts
untested | home_timeline_layout_test › Home media reuses cached posts and survives switching sources | GroupFeedBody.kt mediaOnly over the same run | Compose
covered | home_timeline_layout_test › Home searches its loaded snapshot and returns to the same feed | ui/search/LoadedFeedSearch.kt LoadedFeed.Index | SearchLogicTest (LoadedFeed matching); the feed keeps its run while the search layer is open
covered | home_timeline_layout_test › Home order updates the actual Following group and keeps filters reachable | FollowingBarView.kt order menu → session feeds[following] | FollowingBarTest.theOrderMenuKeepsXtasOrder…, GroupFeedRulesTest

## intro_screen_test.dart
covered | intro_screen_test › Skip marks the intro seen, reloads Home once and changes no setting | ui/intro/IntroFlow.kt finished() | IntroFlowTest.theCardsShowUntilSeenAndCanBeShownAgain
untested | intro_screen_test › Next advances and the indicator announces the page | ui/intro/IntroIllustrations.kt (intro_page_of, liveRegion) | Compose semantics
covered | intro_screen_test › system Back on the third card returns to the second | IntroFlow (back stops at the first) | IntroFlowTest.theCardsGoInXtasOrderAndBackStopsAtTheFirst
covered | intro_screen_test › a source tile enables the plugin and pins it; again reverts both | IntroFlow.toggled | IntroFlowTest.aTileSwitchesItsNetworkOnAndPinsItOnHome
untested | intro_screen_test › the look picker writes the background and the accent | IntroScreen.kt BackgroundPicker/AccentPicker → ThemePrefs | Compose; writes are one-line Settings.with
covered | intro_screen_test › Start reading reloads Home once and marks the intro seen | IntroFlow.finished, Home reads settings as a flow | IntroFlowTest.theCardsShowUntilSeen…
untested | intro_screen_test › the account card names who is signed in and moves on with Next | IntroScreen.kt (intro_signed_in_as) | Compose
untested | intro_screen_test › without an account the card offers sign-in, Not now and a backup import | IntroScreen.kt (intro_not_now, settings_import_backup) | Compose

## navigation_swipe_test.dart
untested | navigation_swipe_test › long-press Home switches sources and opens groups from another tab | HomeBottomBar.kt (onHoldHome → picker), SourcePickerSheet.kt onGroup | Compose gesture
untested → ported | navigation_swipe_test › a leftward swipe advances, a rightward one goes back | ui/home/HomeBar.kt swiped (ui/common TabSwipes) | HomeBarTest.theBarsSwipeRulesAreXtasPageAfterNavigationSwipe
untested → ported | navigation_swipe_test › the ends clamp rather than wrapping around | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a mis-tap, going nowhere at no speed, cannot change tab | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a slow but long drag counts, even at no speed | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a long drag still clamps at the ends | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › an opposing release cancels rather than unexpectedly changing direction | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a fast but tiny movement cannot change tab | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a moderate opposing release cancels a long drag | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested → ported | navigation_swipe_test › a single tab has nowhere to go | HomeBar.swiped | HomeBarTest.theBarsSwipeRules…
untested | navigation_swipe_test › touches across the feed and navigation bar cancel both gestures | HomeBottomBar.kt barSwipe (draggable) | Compose gesture
covered | navigation_swipe_test › a rightward swipe advances in RTL | HomeBar.swiped(rtl) | HomeBarTest.aSwipeAcrossTheBarTurnsOnePage… (rtl case)
untested | navigation_swipe_test › adding a second finger cancels a pending navigation | HomeBottomBar.kt barSwipe | Compose gesture (draggable follows one pointer)
untested | navigation_swipe_test › pages scroll on behind the floating bar and end above it | HomeScreen.kt (contentPadding = bar height) | Compose layout
untested | navigation_swipe_test › a page's floating button clears the bar (inset 0/24) | HomeScreen.kt | Compose layout
n/a | navigation_swipe_test › publishes edge-swipe handoff to nested page content | — | Flutter InheritedWidget plumbing for nested gesture arenas; native's TimelineSwipe edges are its own (TimelineSwipe.startsInside)
untested | navigation_swipe_test › moves to the next tab and back | HomeBottomBar.kt barSwipe → onPage | Compose gesture; rule in HomeBar.swiped
covered | navigation_swipe_test › does nothing at the first tab | HomeBar.swiped | HomeBarTest
untested | navigation_swipe_test › still switches when animations are turned off | HomeScreen.kt (Crossfade snap() when reduced) | Compose
covered | navigation_swipe_test › pagesForNavigationBar keeps two-or-more lists | HomeBar.pages | HomeBarTest.theBarHoldsTheReadersPagesInTheirOrder
covered | navigation_swipe_test › pagesForNavigationBar replaces a single leftover tab | HomeBar.pages (MINIMUM → DEFAULT_PAGES) | HomeBarTest.aPageHomeCannotShowIsLeftOffAndTooFewMeansHomesFour
covered | navigation_swipe_test › a single leftover page still builds the default bar | HomeBar.pages | HomeBarTest.aPageHomeCannotShow…
covered | navigation_swipe_test › an empty page list still builds the default tabs | HomeBar.pages | HomeBarTest.aPageHomeCannotShow…
untested | navigation_swipe_test › tapping a destination still works | HomeBottomBar.kt BarItem onClick | Compose
untested | navigation_swipe_test › the pager builds only the visible destination | HomeScreen.kt (Crossfade over the one page shown) | Compose
n/a | navigation_swipe_test › a pref write does not rebuild the visible page | — | Flutter rebuild counting; Compose recomposition is skipped by stable state

## network_switcher_test.dart
n/a | network_switcher_test › keeps pin order and always includes the open network | — | XTA's width-limited strip of recent plugin tabs; native has no such strip (Home's timelines are chosen in the picker, which lists them all); the bar's networks use HomeBar.slots (HomeBarTest)
n/a | network_switcher_test › does not shuffle when switching among already-visible tabs | — | same strip; native's picker order is the pins' order (StripPins)
covered | network_switcher_test › Following and For you stay first when many plugins are pinned | core FeedStrip.tabs, HomeStrip.tabs | FeedStripTest, HomeStripTest.followingAndForYouComeFirst…
covered | network_switcher_test › X destinations stay visible when many plugins join the bar | ui/home/HomeBar.kt slots | HomeBarTest.oneNetworkKeepsItsPlaceAfterTheOthersAndMoreGoBehindNetworks
covered | network_switcher_test › a single plugin page does not collapse into Networks | HomeBar.slots | HomeBarTest.oneNetworkKeepsItsPlace… (one network keeps its page)
covered | network_switcher_test › an enabled plugin waits under Add timeline | FeedStrip.candidates | FeedStripTest.aNetworkOffTheStripStaysOneAddAway
covered | network_switcher_test › a pin the reader removed stays removed | FeedStrip.forgotten; native never re-pins | FeedStripTest.pinsAreAddedOnceForgottenAndMoved
covered | network_switcher_test › enabling a plugin selects its bottom tab, not a Home timeline | ui/settings/HomePages.kt load (seeds once) | SettingsLogicTest.theSavedOrderComesFirstThenTheRestAndAnUnseededNetworkJoinsOnce
n/a | network_switcher_test › visible row identity stays stable across plugin taps | — | XTA strip row keys; native's pages keep their own keys (HomeStripTest.keysNeverCollide, theTabShownStays…)

## plugin_tab_seeding_test.dart
covered | plugin_tab_seeding_test › an enabled plugin takes a tab the first time it is loaded | HomePages.load | SettingsLogicTest.theSavedOrderComesFirst…AnUnseededNetworkJoinsOnce
covered | plugin_tab_seeding_test › a disabled plugin offers no tab at all | HomePages.available | SettingsLogicTest.aNetworkWithoutItsTabOrSwitchedOffIsNoPage
covered | plugin_tab_seeding_test › a tab the reader removed stays removed | HomePages.load (seeded) | SettingsLogicTest.theSavedOrderComesFirst… (second load)
covered | plugin_tab_seeding_test › a tab the reader kept is left alone | HomePages.load | SettingsLogicTest.theSavedOrderComesFirst…
covered | plugin_tab_seeding_test › the default tabs are still the ones selected | HomePages.DEFAULTS | SettingsLogicTest.aBarWithNothingTickedGetsHomesFourBack, HomeBarTest
covered | plugin_tab_seeding_test › an empty saved page list still keeps the default tabs | HomePages.load | SettingsLogicTest.aBarWithNothingTickedGetsHomesFourBack
covered | plugin_tab_seeding_test › enabling RSS selects its tab and leaves Home to the reader | HomePages.load + FeedStrip (no pin) | SettingsLogicTest, FeedStripTest
covered | plugin_tab_seeding_test › a JSON-string home.pages pref still loads the default tabs | store Pref.kt lenientList | SettingsTest (JSON array in a string)

## pref_lists_test.dart
covered | pref_lists_test › stringListPref reads a real string list | store settings Pref.kt textsPref/lenientList | SettingsTest
covered | pref_lists_test › stringListPref accepts a JSON array string | lenientList | SettingsTest ("a JSON array in a string")
covered | pref_lists_test › stringListPref accepts a List<dynamic> from a backup restore | PrefValue.Texts from backup JSON arrays | SettingsTest, BackupDocumentTest
covered | pref_lists_test › stringListPref is null when the key was never set | Settings[pref] → its default | SettingsTest
covered | pref_lists_test › FeedStripStore survives a JSON-string strip pref | HomePrefs.feedStripNetworks (texts) | SettingsTest
covered | pref_lists_test › NetworkRecentsStore survives a JSON-string recents pref | HomePrefs.recentNetworks (texts) | SettingsTest

## tab_icons_test.dart
untested | tab_icons_test › Abos section tabs show folder and people beside the labels | ui/subscriptions/SubscriptionsScreen.kt HeaderTab(GroupIcons.FolderOutlined / PeopleOutlined) | Compose
untested | tab_icons_test › home strip tabs show plugin icons beside the labels | ui/home/HomeFeed.kt TimelineMark (plugin.icon) | Compose

## combined_groups_test.dart
covered | combined_groups_test › nothing is combined to begin with | core/session CombinedGroups | GroupsTest.groupsAreCombinedOnAWhimAndBringWhatIsNestedInThem
covered | combined_groups_test › holding one adds it, holding it again takes it back out | CombinedGroups.toggled | GroupsTest.groupsAreCombinedOnAWhim…
covered | combined_groups_test › clearing drops the lot | CombinedGroups.cleared | GroupsTest.groupsAreCombinedOnAWhim…
covered | combined_groups_test › a group cannot be read alongside itself | CombinedGroups.released | GroupsTest.groupsAreCombinedOnAWhim…
covered | combined_groups_test › releasing one that is not in the combination changes nothing | CombinedGroups.released | GroupsTest.groupsAreCombinedOnAWhim…
covered | combined_groups_test › each combined group brings everything nested inside it | CombinedGroups.covers | GroupsTest.groupsAreCombinedOnAWhim…
covered | combined_groups_test › groups that share nothing stay separate | CombinedGroups.covers | GroupsTest.groupsAreCombinedOnAWhim…

## custom_feed_rules_test.dart
covered | custom_feed_rules_test › splits on commas and newlines, keeping phrases whole | core/feed MutedKeywords.parseTyped | FeedRulesTest.typedTermsSplitOnCommasAndLineBreaks…
covered | custom_feed_rules_test › is empty for nothing useful | MutedKeywords.parseTyped | FeedRulesTest.typedTermsSplit…
covered | custom_feed_rules_test › round-trips through the stored form | MutedKeywords.join/parse | FeedRulesTest.typedTermsSplit…, theStoredFormReadsXtasLegacyTermsAndItsJson
covered | custom_feed_rules_test › a single word matches whole words only | MutedKeywords.matches | FeedRulesTest.aSingleWordMatchesWholeWordsOnly…
covered | custom_feed_rules_test › is case-insensitive | MutedKeywords.matches | FeedRulesTest.aSingleWordMatches… (BITCOIN)
covered | custom_feed_rules_test › a phrase matches anywhere | MutedKeywords.matches | FeedRulesTest.aSingleWordMatches… (black friday)
covered | custom_feed_rules_test › a term with punctuation matches as written | MutedKeywords.matches | FeedRulesTest.aSingleWordMatches… ($TSLA)
covered | custom_feed_rules_test › handles non-Latin words | MutedKeywords.matches | FeedRulesTest.aSingleWordMatches… (кот)
covered | custom_feed_rules_test › an empty term never matches | MutedKeywords.matches | FeedRulesTest.aSingleWordMatches…
covered | custom_feed_rules_test › passes everything through when no rule is set | core/feed FeedRules | FeedRulesTest.withNoRuleSetEverythingPasses…
covered | custom_feed_rules_test › sfw keeps only non-sensitive posts, nsfw only sensitive ones | FeedRules | FeedRulesTest.sfwKeepsOnlyPostsNotMarkedSensitive…
covered | custom_feed_rules_test › the likes threshold keeps posts at or above it | FeedRules | FeedRulesTest.theFloorsReadTheFirstPost…
covered | custom_feed_rules_test › the repost threshold counts reposts and quotes together | FeedRules | FeedRulesTest.theFloorsReadTheFirstPost…
covered | custom_feed_rules_test › a muted word hides the post | FeedRules | FeedRulesTest.aMutedWordHidesThePost…
covered | custom_feed_rules_test › a muted word inside a quoted post also hides it | FeedRules | FeedRulesTest.aMutedWordHidesThePostAlsoInsideAQuote…
covered | custom_feed_rules_test › fold keeps the chain with a reason | FeedRules | FeedRulesTest.foldKeepsTheThreadWithItsReason
covered | custom_feed_rules_test › expired mute does not match | MutedKeywords | FeedRulesTest.anExpiredMuteNoLongerApplies
covered | custom_feed_rules_test › rules combine: a post must satisfy all of them | FeedRules | FeedRulesTest.aPostMustSatisfyEveryRule
covered | custom_feed_rules_test › the cache key changes with every rule, so feeds do not share chunks | FeedRules.cacheKey | FeedRulesTest.theCacheKeyChangesWithEveryRule…

## feed_chunk_logic_test.dart
covered | feed_chunk_logic_test › keeps the first chain for a repeated chain id | core/feed FeedOrder.dedupe | FeedShapingTest.theSamePostComesOnceWhateverThreadItCameIn
covered | feed_chunk_logic_test › drops a later chain whose leading tweet id already appeared | FeedOrder.dedupe | FeedShapingTest.theSamePostComesOnce…
n/a | feed_chunk_logic_test › newest and oldest tweet ids | — | XTA's BigInt id helpers; native's gap fill compares post times (FeedOrder.timeOf, MergedTimeline), tested in CatchUpTest.theGapIsFilled…
covered | feed_chunk_logic_test › continues while fetched window is entirely above stored posts | core/feed GapFill.shouldContinue | CatchUpTest.theGapIsFilledWhileTheFreshWindowIsAbove…
covered | feed_chunk_logic_test › stops once fetched content overlaps stored posts | GapFill.shouldContinue | CatchUpTest.theGapIsFilled…
covered | feed_chunk_logic_test › stops at the gap-fill page budget | GapFill.shouldContinue | CatchUpTest.theGapIsFilled…
covered | feed_chunk_logic_test › does not gap-fill without stored posts or a cursor | GapFill.shouldContinue | CatchUpTest.theGapIsFilled…

## feed_first_page_action_test.dart
covered | feed_first_page_action_test › unread posts above the last-read one restore the reader to it | core/feed FirstPageAction | CatchUpTest.unreadPostsAboveTheLastReadOneBringTheReaderBackToIt
covered | feed_first_page_action_test › a restored session offset wins, so the reader keeps their place | FirstPageAction | CatchUpTest.unreadPostsAbove… (keptPlace)
covered | feed_first_page_action_test › nothing new above the last-read post records instead of restoring | FirstPageAction | CatchUpTest.aFirstPageRecordsWhenNothingIsNew…
covered | feed_first_page_action_test › a reader who has never read this feed records the top of it | FirstPageAction | CatchUpTest.aFirstPageRecords…
covered | feed_first_page_action_test › a later page scrolled away from the top records nothing | FirstPageAction | CatchUpTest.aRefreshMidScrollRecordsNothing…
covered | feed_first_page_action_test › the caught-up boundary is decided once, not on every refresh | FirstPageAction (evaluated) | CatchUpTest.aRefreshMidScroll…
covered | feed_first_page_action_test › an empty page has nothing to record | FirstPageAction | CatchUpTest.aFirstPageRecords…
covered | feed_first_page_action_test › a page of undated posts has nothing recordable in it | FirstPageAction, ReadPosition.newestRecordable | CatchUpTest.aFirstPageRecords…, theNewestRecordableThread…

## feed_rules_test.dart
covered | feed_rules_test › applies the group's rules whatever the sort mode | core/feed FeedSettings.rules (separate from sort) | FeedRulesTest; GroupFeedRulesTest
covered | feed_rules_test › is unchanged when the sort mode is custom | FeedRules.cacheKey (no sort in it) | FeedRulesTest.theCacheKeyChangesWithEveryRule…
covered | feed_rules_test › a group with nothing configured filters nothing | FeedRules() | FeedRulesTest.withNoRuleSetEverythingPasses…

## feed_search_fallback_test.dart
covered | feed_search_fallback_test › SearchTimeline 429 and a refused endpoint use UserTweets | core/feed/GroupFeed.kt fallsBack | GroupFeedTest.profilesAreAskedForUnlessTheyCannotGetThroughEither
covered | feed_search_fallback_test › any other failure of the search itself tries the profiles | GroupFeed.fallsBack | GroupFeedTest.profilesAreAskedFor… (parse, timeout)
covered | feed_search_fallback_test › profiles are not asked for when they cannot get through either | GroupFeed.fallsBack | GroupFeedTest.profilesAreAskedFor… (offline, no account, no signer)
different → fixed | feed_search_fallback_test › null and empty stored cursors become a fresh search | core/feed/SavedFeed.kt decode | an empty stored cursor was kept as ""; GroupFeedTest.anEmptyStoredCursorIsAFreshSearch
covered | feed_search_fallback_test › SearchSubscription members have no rest_id for UserTweets | GroupFeed.readChunk (filterNot search) | GroupFeedTest.aSavedSearchIsSearchedInBrackets…, theProfilesFallbackDropsReposts…AndSkipsSavedSearches
covered | feed_search_fallback_test › keeps retweets when the group asked for them | GroupFeed.wanted | GroupFeedTest (default plan keeps reposts)
untested → ported | feed_search_fallback_test › drops retweet chains to match -filter:retweets | GroupFeed.wanted | GroupFeedTest.theProfilesFallbackDropsRepostsTheFeedLeavesOut…
covered | feed_search_fallback_test › does not page SearchTimeline after a fallback or a failed search | GroupFeed.readChunk (fallback page has no cursor) | GroupFeedTest.aLaterPageWhoseSearchFailsFallsBackToo ("no search cursor")
covered | feed_search_fallback_test › is silent when every chunk survived | core/feed FeedErrors.toShow | GroupFeedTest.whenNothingCouldBeRead…
covered | feed_search_fallback_test › prefers the rate-limit widget over a refused endpoint | FeedErrors.toShow | GroupFeedTest.whenNothingCouldBeRead…
covered | feed_search_fallback_test › falls back to endpoint-refused, then the first error | FeedErrors.toShow | GroupFeedTest.whenNothingCouldBeRead…
covered | feed_search_fallback_test › returns the search page when SearchTimeline is healthy | GroupFeed | GroupFeedTest (every healthy read)
covered | feed_search_fallback_test › loads UserTweets when search is rate-limited | GroupFeed.readChunk | GroupFeedTest.aLaterPageWhoseSearchFailsFallsBackToo
covered | feed_search_fallback_test › loads UserTweets when SearchTimeline is refused for every account | GroupFeed.readChunk | GroupFeedTest.aSavedSearchIsSearched… (403 → profile)
covered | feed_search_fallback_test › keeps the search error when fallback finds nothing | GroupFeed.readChunk (own.isEmpty → rethrow) | GroupFeedTest.aSearchThatGivesNoAnswerInItsTime… (failure kept)
covered | feed_search_fallback_test › keeps the search error when fallback itself throws | GroupFeed.ownPosts (runCatching per profile) | GroupFeedTest.aSearchThatGivesNoAnswer…
covered | feed_search_fallback_test › loads UserTweets when X answers the search with an error | GroupFeed.readChunk | GroupFeedTest.aSavedSearchIsSearched…
covered | feed_search_fallback_test › does not fan out UserTweets while offline | GroupFeed.fallsBack(IOException) = false | GroupFeedTest.profilesAreAskedFor…
untested → ported | feed_search_fallback_test › returns completed posts and stops starting requests when its budget expires | GroupFeed.ownPosts (withTimeoutOrNull fallbackMillis) | GroupFeedTest.theProfilesFallbackAsksTwoAtATimeAndKeepsWhatAnsweredInItsTime
covered | feed_search_fallback_test › skips search subscriptions and surviving users still contribute | GroupFeed.ownPosts | GroupFeedTest.theProfilesFallbackDropsReposts…AndSkipsSavedSearches, aSavedSearchIsSearched…
untested → ported | feed_search_fallback_test › caps in-flight profile fetches | GroupFeed.FALLBACK_CONCURRENCY | GroupFeedTest.theProfilesFallbackAsksTwoAtATime…
covered | feed_search_fallback_test › an empty chunk is a no-op | GroupFeed.ownPosts (accounts empty) | GroupFeedTest.aSavedSearchIsSearched… (onlySearch: no profile read)
covered | feed_search_fallback_test › one rate-limited chunk still lets the others return posts | GroupFeed chunks | GroupFeedTest.aRetryReadsOnlyTheChunksThatFailed…

## group_chrome_test.dart
n/a | group_chrome_test › Group media mode and route selection are Store-backed | — | flutter_triple store plumbing; native keeps them in GroupFeedScreen state/view model
covered | group_chrome_test › active Group filter count includes every effective custom rule | ui/group/GroupFeedRules.kt activeFilters | GroupFeedRulesTest.theCustomChipCountsTheRulesThatAreOn
untested | group_chrome_test › Group controls show order and media state in one token-sized row | ui/group/GroupFeedScreen.kt GroupChips/OrderChip | Compose layout
untested | group_chrome_test › while Discover is open only its chip shows, and its close returns to the feed | GroupFeedScreen.kt GroupChips(discovering) | Compose
untested → ported | group_chrome_test › picking Discover, or the order a group already has, saves nothing | GroupFeedRules.withSort (same order → same settings, no new session) ; Discover is a view flag, not an order | GroupFeedRulesTest.pickingTheOrderAGroupAlreadyHasChangesNothing

## group_discover_mode_test.dart
untested | group_discover_mode_test › Discover is its own view: only its chip shows, Back returns to the feed, nothing is saved | GroupFeedScreen.kt (discovering flag, BackHandling) | Compose

## group_discovery_feed_test.dart
covered | group_discovery_feed_test › every repost, quote, reply and mention by a member points at a candidate the member vouches for | ui/group/DiscoveryReads.kt | DiscoverySourcesTest.aMembersPostPointsAtWhatItRepostsQuotesAnswersAndMentions…
covered | group_discovery_feed_test › replies are only mined when the group shows them | DiscoveryReads | DiscoverySourcesTest.repliesCountOnlyWhereTheFeedTakesThem…
covered | group_discovery_feed_test › mined candidates merge across members into one ranked row | ui/group/Discovery.kt merged/ranked | DiscoveryTest.anAccountPointedAtByManyMembersIsOneCandidate…

## group_discovery_follow_test.dart
covered | group_discovery_follow_test › adding to a group follows first and keeps the memberships the account already has | DiscoveryViewModel.add → Session.addingTo | GroupSessionTest (addingTo keeps other groups)
untested | group_discovery_follow_test › group rows are keyed the way each network keys its own follows | DiscoveryViewModel.followed (X id; plugins follow through their own PluginFollows) | each plugin keys its follows itself
untested | group_discovery_follow_test › the add store marks a row as adding, then added, and forgets a failed one | DiscoveryViewModel.add (adding/added sets) | view model over AppGraph; no test harness for DiscoveryViewModel yet

## group_discovery_follows_test.dart
covered | group_discovery_follows_test › reads never-asked members first, remembers answers across launches and asks again after the TTL | ui/group/DiscoveryReads.kt rotating read | DiscoverySourcesTest.aRotatingReadAsksTheNeverAskedFirstAndKeepsAnswersTenMinutes, FollowsCacheTest
covered | group_discovery_follows_test › a member whose read fails is reported without losing the others | rotating read | DiscoverySourcesTest.aRotatingReadThatReadsNothingIsAFailure
covered | group_discovery_follows_test › co-follows become candidates vouched for by each member following them, never a member itself | Discovery.kt | DiscoveryTest.theAccountsMembersFollowAreCandidatesButNotTheMembersThemselves

## group_discovery_mine_test.dart
covered | group_discovery_mine_test › the plan resolves flags, orders members oldest first and hashes chunks like the live feed | core GroupFeedPlan (same plan as the feed) | GroupFeedTest.aPlanReadsItsMembersOldestFirst…
covered | group_discovery_mine_test › mining reads the chunks the feed stored and reports which members they covered | DiscoveryReads (kept feed) | DiscoverySourcesTest.theKeptFeedCoversTheMembersOfEachSearchItKept…

## group_discovery_screen_test.dart
untested | group_discovery_screen_test › inline Discovery renders compact rows with their reason in a pushed group route | ui/group/DiscoveryPane.kt (reasonText) | Compose; reason rule in DiscoveryTest.aRowSaysWhyFromTheSignal…
untested | group_discovery_screen_test › a setup failure offers Retry and can recover | DiscoveryPane.kt / DiscoveryViewModel.load | Compose
untested | group_discovery_screen_test › Add to this group follows locally, keeps other memberships and marks the row in place | DiscoveryViewModel.add | Compose
untested | group_discovery_screen_test › tapping the snippet expands the real post and the header collapses it again | DiscoveryPane.kt Snippet | Compose
untested | group_discovery_screen_test › Hide removes the row and the snackbar undoes it | DiscoveryViewModel hide/undo (canUndo) | Compose; ranking without hidden in DiscoveryTest.whoTheReaderFollowsAndWhatTheyHid…

## group_discovery_test.dart
covered | group_discovery_test › deduplicates identities per network and excludes followed aliases | ui/group/Discovery.kt identity/follows | DiscoveryTest.anAccountIsToldApartByItsNetworkAndIdOrHandleWithoutItsAt, whoTheReaderFollows…
covered | group_discovery_test › merges one account seen through several members and ranks it by how many vouch | Discovery.merged/ranked | DiscoveryTest.anAccountPointedAtByManyMembersIsOneCandidate…, mostMembersComeFirst…
covered | group_discovery_test › among equally supported accounts the stronger signal, then the newer support, wins | Discovery.ranked | DiscoveryTest.mostMembersComeFirstThenTheStrongestSignalsThen…TheNewest…
untested → ported | group_discovery_test › the Unicode tokenizer keeps every script while nameTokens stays as it was | Discovery.tokens | DiscoveryTest.theWordsOfANameAreKeptInEveryScript (nameTokens is XTA's legacy helper, not used by native)
covered | group_discovery_test › the group name only breaks ties, in any script | Discovery.ranked (group tokens last) | DiscoveryTest.mostMembersComeFirst…ThenTheGroupsWords
covered | group_discovery_test › rejects invented AI ids and preserves candidates omitted by AI | ui/group/DiscoveryAi.kt order | DiscoverySourcesTest.theAiIsAskedAboutTheCandidatesAsDataAndItsAnswerReadSafely
untested | group_discovery_test › Bluesky candidates come from actual reposts and quotes only | plugins/bluesky BlueskyDiscovery.kt | plugin code (reposts → REPOSTED, quotes → QUOTED); belongs with the microblogs batch's plugin tests
untested | group_discovery_test › Mastodon candidates come from boosts and quoted authors | plugins/mastodon MastodonDiscovery.kt | same
covered | group_discovery_test › a failing source keeps the others and names itself | DiscoveryViewModel / DiscoveryBoard | DiscoverySourcesTest.theBoardKeepsEachSourcesLatestAnswer…
covered | group_discovery_test › rerankWithAi orders what is loaded without fetching again | DiscoveryViewModel.rankWithAi, DiscoveryBoard.aiRanked | DiscoverySourcesTest.theAiOrderLeadsOnceGiven…
covered | group_discovery_test › unusable AI response keeps verified local candidates and reports fallback | DiscoveryBoard.aiRanked(failed) | DiscoverySourcesTest.theAiOrderLeads…, theAiIsAsked… (null order)
untested | group_discovery_test › all-followed candidates never trigger the configured AI | DiscoveryViewModel.rankWithAi (nothing ranked → no request) | view model; no harness
covered | group_discovery_test › Scan more reads another rotating batch and keeps what the first one found | DiscoveryBoard (never reads less), rotating read | DiscoverySourcesTest.theBoardKeepsEachSourcesLatestAnswerAndNeverReadsLess, aRotatingRead…
covered | group_discovery_test › hiding an account removes it until Undo brings it back | Discovery.visible (HIDE judgement), DiscoveryViewModel.undo | DiscoveryTest.whoTheReaderFollowsAndWhatTheyHidIsLeftOut…
covered | group_discovery_test › following from a profile removes its cached discovery card without refetch | Discovery.followedExcept / DiscoveryBoard.visible(followed) | DiscoverySourcesTest.theAiOrderLeads… ("the followed leave"), DiscoveryTest.whoTheReaderFollows…
untested | group_discovery_test › switching from Discover remembers that its pane has been opened | GroupFeedScreen.kt (discovering saved per group) | Compose state
untested | group_discovery_test › a superseded source request cannot replace newer results | DiscoveryViewModel generation check | view model; no harness
untested | group_discovery_test › a superseded AI response cannot replace newer discovery | DiscoveryViewModel.rank (run != generation) | view model; no harness
untested | group_discovery_test › following during a source request excludes its eventual result | DiscoveryViewModel.publish (followed read at publish time) | view model
untested | group_discovery_test › closing discovery retires pending sources | DiscoveryViewModel (scan job in viewModelScope) | view model
covered | group_discovery_test › a finished source paints before a stalled one times out, and survives it | DiscoveryViewModel per-source publish, Discovery.SOURCE_MILLIS | view model; time share in DiscoveryTest.eachListAndEachPageMayTakeItsShareOfAMinute
untested | group_discovery_test › reporting a setup failure retires an older pending request | DiscoveryViewModel (generation) | view model

## group_feed_title_test.dart
untested | group_feed_title_test › the member count and the group mark sit in the app bar | ui/group/GroupFeedScreen.kt GroupTopBar | Compose layout
untested | group_feed_title_test › a pushed group keeps the switcher, now with a full-height touch target | GroupFeedScreen.kt / GroupSwitcher.kt | Compose layout
untested | group_feed_title_test › both lines fit the toolbar at the largest text size (switchable: false/true) | GroupTopBar | Compose layout
untested | group_feed_title_test › the name does not move when the count arrives | GroupTopBar | Compose layout
untested | group_feed_title_test › a pushed group on a 360dp phone keeps its name and drops the mark instead | GroupTopBar | Compose layout
untested | group_feed_title_test › a narrow deck column title does not overflow | ui/home/DeckScreen.kt | Compose layout
covered | group_feed_title_test › the combined-groups count matches the name it follows | GroupFeedUi.memberCount (session.membersOf(groupId, alsoRead)) | count rule: GroupSessionTest.groupsReadTogetherBringWhatIsNestedInEach

## group_members_test.dart
covered | group_members_test › a mixed group keeps X members for search and plugin members for their own fetch | core Session.membersOf (X accounts + searches), data/plugins PluginMembers.of | PluginMembersTest.aGroupsPluginMembersAreItsAndItsNestedGroups…, GroupSessionTest
covered | group_members_test › Reddit and Substack members are never searched on X | Session.membersOf (plugin ids are no followed account) | PluginMembersTest, GroupSessionTest.aGroupReadsEverythingNestedInIt…
covered | group_members_test › other add-to-group plugins split the same way | PluginMembers.accounts | PluginMembersTest.aMemberIsThePluginsIdAndItsAccount
covered | group_members_test › an X-only group has no plugin slots | PluginMembers.of / FeedSources.asks (no ask without members) | FeedMixTest.theHomeTimelinesAskTheFollowedAccounts…AndGroupsTheirMembers
covered | group_members_test › are the group members, so adding a subreddit or publication is enough | FeedSources.asks(home = false) | FeedMixTest.theHomeTimelinesAsk…AndGroupsTheirMembers
covered | group_members_test › X members still have ids of their own for the search query | core GroupFeedPlan.queryOf (from:handle) | GroupFeedTest.aSavedSearchIsSearchedInBrackets…, FeedQueriesTest

## group_switcher_test.dart
untested | group_switcher_test › the title shows the group and hints that it opens something | ui/group/GroupSwitcher.kt | Compose
covered | group_switcher_test › the menu offers the home feeds and a way to every group | GroupSwitcher.kt | DiscoveryTest.theTitlesMenuOffersAFewPinnedGroupsButTheOneOpen
covered | group_switcher_test › a pinned group is one tap, without opening the full list | GroupSwitcher.kt | DiscoveryTest.theTitlesMenuOffersAFewPinnedGroups…
untested | group_switcher_test › tapping it lists every group with the current one checked | GroupSwitcher.kt sheet | Compose
untested | group_switcher_test › choosing another group reports it and closes the sheet | GroupFeedScreen.kt onSwitch | Compose
untested | group_switcher_test › choosing the group you are already in changes nothing | GroupFeedScreen.kt (onSwitch ignores the shown id) | Compose
untested | group_switcher_test › with no groups the sheet says so instead of showing an empty list | GroupSwitcher.kt | Compose

## group_unread_test.dart
n/a | group_unread_test › feedChunkHash matches SubscriptionGroupFeedChunk.hash | — | XTA's SQLite chunk-row hash; native keys a feed by GroupFeedPlan.readKey (GroupFeedTest.aPlanThatReadsTheSame…)
covered | group_unread_test › members pack into hashes by the length of their search | core FeedQueries.pack | FeedQueriesTest.everySearchIsFilledAsFarAsXReadsIt, aMemberTooLongToShareASearch…
covered | group_unread_test › groupHasUnread is false when tracking is off | core Unread.has | CatchUpTest.aFeedIsUnreadOnlyWhenTracked…
covered | group_unread_test › groupHasUnread is false when nothing is cached | Unread.has | CatchUpTest.aFeedIsUnreadOnlyWhenTracked…
covered | group_unread_test › groupHasUnread is true when tracking, cached, and never marked | Unread.has | CatchUpTest.aFeedIsUnreadOnlyWhenTracked…
covered | group_unread_test › groupHasUnread is true when the cache is newer than last read | Unread.has | CatchUpTest.aFeedIsUnreadOnlyWhenTracked…
covered | group_unread_test › groupHasUnread is false when the cache is not newer | Unread.has | CatchUpTest.aFeedIsUnreadOnlyWhenTracked…
covered | group_unread_test › popular feeds do not track even when the global switch is on | Unread.tracks | CatchUpTest.popularFeedsAndTheMediaGridKeepNoReadPosition
covered | group_unread_test › a parent group hashes the union of nested members | Session.membersOf (nested) → plan key | GroupSessionTest.aGroupReadsEverythingNestedInIt…
untested | group_unread_test › an unread board tile exposes Unread in semantics | ui/subscriptions GroupsPage tiles | Compose semantics

## home_feed_unread_test.dart
covered | home_feed_unread_test › Following hashes match the live -1 chunk and skip out-of-feed users | Session.feedMembers (inFeed only) | SessionTest.theFeedReadsOnlyTheAccountsXtaShowsInIt
covered | home_feed_unread_test › Following last-read prefers following over the legacy -1 key | Unread.lastReadForFollowing | CatchUpTest.theFollowingFeedsLastReadPrefersItsKeyOverXtasOlderOne
covered | home_feed_unread_test › Following is unread when its cached chunk is newer than last read | Unread.has, ui/home/HomeUnread.kt | CatchUpTest, HomeFilterTest.onlyFeedsThatKeepAPositionLightUp
covered | home_feed_unread_test › For You newest-cached pref round-trips | store FeedPrefs forYouNewestCachedAt | XtaPrefsTest (declared prefs), SettingsTest
untested | home_feed_unread_test › an unread strip tab exposes Unread in semantics | SourcePickerSheet.kt PickerRow (home_unread description) | Compose semantics

## profile_followed_by_line_test.dart
covered | profile_followed_by_line_test › no known follower shows nothing | ui/profile FollowedBy.label (null) | ProfileRulesTest.followedByNamesWhoFollows…
untested | profile_followed_by_line_test › the row grows and fades in once its followers are known | ui/profile/ProfileParts.kt | Compose animation
covered | profile_followed_by_line_test › one follower | FollowedBy.Label.Names, posts_followed_by_one | ProfileRulesTest.followedByNamesWhoFollows…
covered | profile_followed_by_line_test › two followers | FollowedBy.Label.Names, posts_followed_by_two | ProfileRulesTest
covered | profile_followed_by_line_test › three followers | FollowedBy.Label.Names, posts_followed_by_three | ProfileRulesTest
covered | profile_followed_by_line_test › many followers cap the avatars and open the list | FollowedBy.Label.Many, FollowedBySheet (posts_followed_by_basis) | ProfileRulesTest (Many("A","B",2))
untested | profile_followed_by_line_test › a long German line wraps on a narrow screen | ProfileParts.kt | Compose layout

## group_board_test.dart
untested → ported | group_board_test › colour is deterministic for a seed and stable across calls | ui/subscriptions/GroupLook.kt faceColor | GroupLookTest.aFacesColourIsStableNeverOutOfRangeAndNeverGrey
untested → ported | group_board_test › different seeds land on different colours | GroupLook.faceColor | GroupLookTest.aFacesColourIsStable…
untested → ported | group_board_test › index is never negative even for hash overflow | GroupLook.faceColor (floorMod) | GroupLookTest.aFacesColourIsStable…
missing → implemented | group_board_test › palette is never grey — a missing avatar must look deliberate | GroupLook.harmonize, GroupMarks.kt MemberFaceView | never grey: GroupLookTest.aFacesColourIsStable…; a face is rotated at most 15° toward the theme accent, as Material's harmonize does, keeping its saturation
untested → ported | group_board_test › initial uses the first alphanumeric of the name, else the seed | GroupLook.faceInitial | GroupLookTest.aFacesInitialIsTheFirstLetterOrDigit…
untested | group_board_test › renders a single initial at the requested size | ui/subscriptions/GroupIcons.kt / GroupsPage.kt Face | Compose
untested | group_board_test › an empty group falls back to its own initial | GroupsPage.kt tile mark | Compose; rule GroupLookTest.onlyAMarkTheReaderPicked…
covered | group_board_test › lays out {0..5} member(s) without overflow (one per count) | GroupBoardModel.tiles (≤ 4 faces) | GroupBoardModelTest.aTileCountsEveryMemberAndShowsFourFacesByHandle
covered | group_board_test › never renders more than four avatars | GroupLook.FACES = 4 | GroupBoardModelTest.aTileCountsEveryMember…
untested | group_board_test › an unreachable image degrades to a same-size monogram | GroupsPage.kt Face (Coil error → monogram) | Compose
untested | group_board_test › shows name, localized count and a semantics label | GroupsPage.kt tile | Compose
untested | group_board_test › is flat and rippleless — no Card, no InkWell | GroupsPage.kt tile | Compose drawing
untested | group_board_test › the card is a flat surface: no colour wash, no accent bar | GroupsPage.kt tile | Compose drawing
untested | group_board_test › marks a pinned group | GroupsPage.kt tile (pin glyph) | Compose
untested | group_board_test › survives a 2x text scale without overflowing | GroupsPage.kt | Compose layout; tile height rule GroupsTest.aWidenedBoardHasContentHeightTilesThatGrowWithLargeText
untested | group_board_test › lays the faces beside the name and count in 60 dp (compact) | GroupsPage.kt compact row | Compose layout
untested | group_board_test › keeps the name clear of the discovery button | GroupsPage.kt | Compose layout
untested | group_board_test › grows with 2x text instead of overflowing | GroupsPage.kt | Compose layout
untested | group_board_test › tapping calls onTap | GroupsPage.kt | Compose
untested | group_board_test › light/dim/lights out keeps tile text legible (per theme) | GroupsPage.kt colours from the theme | Compose; contrast rule XLookTest

## group_editor_loading_test.dart
n/a | group_editor_loading_test › read failure shows Retry and can load the editor again | ui/subscriptions/GroupEditSheet.kt | native's editor opens on the in-memory session (no read that can fail); a missing group shows unable_to_load_the_group on its feed instead
n/a | group_editor_loading_test › closing the editor during loading ignores the late result | GroupEditSheet.kt | no asynchronous read
n/a | group_editor_loading_test › a stalled read leaves loading and offers Retry | GroupEditSheet.kt | no asynchronous read
n/a | group_editor_loading_test › idle stores can be disposed outside the widget clock | — | flutter_triple store disposal

## group_mark_test.dart
covered | group_mark_test › groupFallbackColor is deterministic per name | GroupLook | GroupLookTest.aGroupWithoutAColourGetsTheSameOneFromItsNameAsInXta
covered | group_mark_test › groupInitial is a single letter and skips leading non-letters | GroupLook | GroupLookTest.theInitialIsTheFirstLetterWithItsAccentsElseTheFirstGrapheme
covered | group_mark_test › resolveGroupMarkKind follows style plus stored fields | GroupLook | GroupLookTest.theMarkIsTheEmojiTheIconOrTheInitialAsTheStyleAndWhatIsSetAllow
covered | group_mark_test › hasExplicitGroupMark is false until the user picks emoji or icon | GroupLook | GroupLookTest.onlyAMarkTheReaderPickedTakesThePlaceOfTheMembersFaces
untested | group_mark_test › GroupMark shows one initial by default | ui/subscriptions/GroupIcons.kt GroupMark | Compose
untested | group_mark_test › GroupMark is a solid disc in the group colour, not a tonal chip | GroupMark | Compose drawing
covered | group_mark_test › the glyph stays legible on both pale and saturated colours | GroupLook.isDark | GroupLookTest.whiteGoesOnDarkColoursAndBlackOnPaleOnes
untested | group_mark_test › GroupMark shows the chosen emoji | GroupMark | Compose; kind rule in GroupLookTest
untested | group_mark_test › GroupMark shows the chosen icon | GroupMark | Compose; GroupLookTest.anIconIsReadFromXtasJsonOrAsABareKey
covered | group_mark_test › board tile keeps the member mosaic when no mark was chosen | GroupLook explicit mark rule | GroupLookTest.onlyAMarkTheReaderPicked…
covered | group_mark_test › an explicit emoji outranks the member mosaic on the board | GroupLook | GroupLookTest.onlyAMarkTheReaderPicked…
covered | group_mark_test › an explicit icon outranks the member mosaic on the board | GroupLook | GroupLookTest.onlyAMarkTheReaderPicked…
untested | group_mark_test › an explicit mark still fits at 2x text scale | GroupMark | Compose layout

## group_membership_sheet_test.dart
covered | group_membership_sheet_test › the account's groups lead, then the groups picked last, then the rest | ui/subscriptions/GroupPicker.kt | GroupPickerTest.theSheetOpensOnTheAccountsGroupsThenTheRecentThenTheRest
untested → ported | group_membership_sheet_test › recent picks put the groups added first, twelve at most | core GroupPicks.remembered (RECENT_LIMIT 12) | GroupPickerTest.recentPicksPutTheGroupsAddedFirstTwelveAtMost
covered | group_membership_sheet_test › ticking changes the status line, and closing gives the choice | GroupPicker.status/answer | GroupPickerTest.theLineUnderTheChipsSaysWhereTheAccountIs, closingUntouched…
covered | group_membership_sheet_test › closing with the groups it opened with gives nothing | GroupPicker.answer | GroupPickerTest.closingUntouchedChangesNothing…
covered | group_membership_sheet_test › a typed name creates a group, ticks it and empties the field | GroupPicker (create row) | GroupPickerTest.aSearchOffersToMakeTheGroupItDoesNotFind
covered | group_membership_sheet_test › a name a group has offers no create row | GroupPicker | GroupPickerTest.aSearchOffersToMakeTheGroup…
covered | group_membership_sheet_test › All groups shows the groups beyond the first six | GroupPicker | GroupPickerTest.theFirstSixShowUntilAll…
covered | group_membership_sheet_test › an account in more groups than six shows them all first | GroupPicker | GroupPickerTest.theSheetOpensOnTheAccountsGroups…, theFirstSixShow…
untested | group_membership_sheet_test › with no groups yet it says so and offers to create one | MembershipSheet.kt (groups_none_yet) | Compose
covered | group_membership_sheet_test › follows a new account and saves its groups once the sheet closes changed | GroupPicker.answer + Session.addingTo | GroupPickerTest.closingUntouched…, GroupSessionTest
covered | group_membership_sheet_test › closing the sheet unchanged neither follows nor saves | GroupPicker.answer (null) | GroupPickerTest.closingUntouched…

## group_ungrouped_sorter_render_test.dart
n/a | group_ungrouped_sorter_render_test › sorter-card-{tag} (render) | — | golden render job, skipped unless rendering; no rule
n/a | group_ungrouped_sorter_render_test › sorter-end-{tag} (render) | — | same
n/a | group_ungrouped_sorter_render_test › sorter-plan-{tag} (render) | — | same

## group_ungrouped_sorter_test.dart
covered | group_ungrouped_sorter_test › placing and skipping move on, one choice for each account | ui/subscriptions UngroupedSorter | UngroupedSorterTest.eachChoiceMovesToTheNextAccountAndTheRoundEnds
covered | group_ungrouped_sorter_test › undo makes the last account current again and forgets its choice | UngroupedSorter | UngroupedSorterTest.undoBringsTheLastAccountBack…
covered | group_ungrouped_sorter_test › a round of the skipped holds only them, from the start | UngroupedSorter | UngroupedSorterTest.theSkippedGetARoundOfTheirOwn
untested | group_ungrouped_sorter_test › only placing writes, and undo removes what its choice added | ui/subscriptions/SortViewModels.kt | view model over AppGraph
untested | group_ungrouped_sorter_test › a failed save throws and keeps the account | SortViewModels.kt | view model
untested | group_ungrouped_sorter_test › a tap while a choice is saved is ignored | SortViewModels.kt | view model
covered | group_ungrouped_sorter_test › nothing to place in puts nothing | UngroupedSorter | UngroupedSorterTest
untested | group_ungrouped_sorter_test › a tap on a group puts the account in it and shows the next | ui/subscriptions/SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › Skip leaves the account where it is; Undo takes each choice back | SortScreens.kt | Compose; rules in UngroupedSorterTest
untested | group_ungrouped_sorter_test › Several groups places the account in every group ticked | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › closing Several groups with nothing ticked changes nothing | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › one group needs no Several groups | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › Create group asks for a name and puts the account in the new group | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › the skipped ones can be sorted again | SortScreens.kt | Compose; UngroupedSorterTest.theSkippedGetARound…
untested | group_ungrouped_sorter_test › a failed save keeps the card and says so | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › placing an account clicks once; skipping does not | SortScreens.kt (haptic on place) | Compose
untested | group_ungrouped_sorter_test › Open profile leads to the account | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › with nobody to sort it says everyone is in a group | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › Left ungrouped leads to the sorter, and the plan is made again after it | SortScreens.kt | Compose
untested | group_ungrouped_sorter_test › with no match by name the main button sorts one by one | SortScreens.kt | Compose
covered | group_ungrouped_sorter_test › Apply places the plan, then goes on to the accounts left over | SortScreens.kt, UngroupedPlans.applied | UngroupedPlansTest.applyingMakesTheSuggestedGroups…
untested | group_ungrouped_sorter_test › Apply closes the screen when it placed everyone | SortScreens.kt | Compose

## group_ungrouped_test.dart
covered | group_ungrouped_test › skips saved searches and anyone already in a group | ui/subscriptions/UngroupedPlans.kt | UngroupedPlansTest.onlyTheAccountsInNoGroupAreSorted
covered | group_ungrouped_test › assigns an ungrouped account to the group with shared name tokens | UngroupedPlans | UngroupedPlansTest.anAccountGoesWhereItSharesMostWords…
covered | group_ungrouped_test › suggests a new group when leftovers share a long token | UngroupedPlans | UngroupedPlansTest.aClusterNeedsAWordOfFourLettersOrMore
covered | group_ungrouped_test › parses an AI reply and drops unknown ids | services/ai AiPrompts.groupingPlan | AiClientTest
covered | group_ungrouped_test › AI assignments win; heuristic fills the rest | AiPrompts GroupingPlan.mergedWith | AiClientTest
covered | group_ungrouped_test › the store prefers a valid AI plan and falls back on a blank reply | SortViewModels.askAi (empty parse → heuristic) | AiClientTest (groupingPlan of "no json" is unused)
covered | group_ungrouped_test › opening the sorter does not send accounts to a configured AI | SortViewModels.plan(useAi = false) by default | by construction (AI only on its own button)

## home_reading_controls_test.dart
missing → implemented | home_reading_controls_test › deliberate down hides, small reversals do not flicker, deliberate up reveals | core/feed/ReadingControls.kt | ReadingControlsTest.deliberateDownHidesSmallReversalsDoNotFlickerAndDeliberateUpReveals
missing → implemented | home_reading_controls_test › programmatic jumps and stale source notifications cannot hide controls | ReadingControls.observe | ReadingControlsTest.programmaticJumpsAndStaleSourceNotificationsCannotHideControls
missing → implemented | home_reading_controls_test › short feeds and protected focus, modal or accessible navigation stay visible | ReadingControls.observe | ReadingControlsTest.shortFeedsAndProtectedMomentsStayVisible; Home passes a screen reader and the keyboard as protected
missing → implemented | home_reading_controls_test › reclaimed space does not create a short-feed hide/reveal loop | ReadingControls.observe | ReadingControlsTest.reclaimedSpaceDoesNotCreateAShortFeedLoop
missing → implemented | home_reading_controls_test › pin is reversible, persisted, and restored by a new Home session | HomePrefs.keepControlsVisible, ui/plugins/ReadingControls.kt | ReadingControlsTest.pinIsReversiblePersistedAndRestoredByANewSession
missing → implemented | home_reading_controls_test › source changes reveal controls without changing the global pin setting | ReadingControls.bind | ReadingControlsTest.sourceChangesRevealControlsWithoutChangingThePin
untested | home_reading_controls_test › real viewport retains controls for focus | ui/plugins/ReadingControls.kt ReadingControlsBar | no Compose widget harness; focus on the row calls reveal
untested | home_reading_controls_test › real viewport retains controls for keyboard | HomeFeedPage | the keyboard inset is passed as protected; the store's protected path is tested
untested | home_reading_controls_test › real viewport retains controls for accessibility | HomeFeedPage | the screen reader is passed as protected; the store's protected path is tested
covered | home_reading_controls_test › real viewport retains controls for short | ReadingScroll.of | ReadingControlsTest.aListThatFitsIsShortAndOneThatRunsPastIsNot

## test/offline_library_test.dart
covered | offline_library_test › article snapshots preserve identity, body, author and media metadata | store/offline OfflineSavedTest
covered | offline_library_test › cold restart reports exact partial images and renders without remote fetches | OfflineStoreTest
covered | offline_library_test › a kept article hands its images over as files, for readers that need no page | OfflineStoreTest
covered | offline_library_test › missing files downgrade actual availability and storage bytes | OfflineStoreTest
covered | offline_library_test › Substack slug links find numeric-ID retained articles without a fetch | OfflineSavedTest
covered | offline_library_test › empty bodies never become available and SVG is not embeddable | OfflineStoreTest
covered | offline_library_test › storage and per-image limits apply before publication | OfflineStoreTest
covered | offline_library_test › streamed size overflow cannot create an available file | OfflineStoreTest
covered | offline_library_test › interrupted response cleans staging and is never available | OfflineStoreTest
covered | offline_library_test › remove cancels an in-flight copy before publication | OfflineStoreTest
covered | offline_library_test › a failed replacement keeps the previous durable copy | OfflineStoreTest
covered | offline_library_test › failed manifest write after remove cannot leave a ready state | OfflineStoreTest
covered | offline_library_test › failed manifest write after clear cannot leave ready entries | OfflineStoreTest
covered | offline_library_test › clear removes copies and startup removes interrupted staging | OfflineStoreTest
covered | offline_library_test › malformed manifest reports an error and explicit retry recovers | OfflineStoreTest

## test/backup_coverage_test.dart
n/a | backup_coverage_test › every table in the schema is either backed up or deliberately not | - | XTA's SQLite schema guard; native keeps no SQLite (files), BackupDocumentTest covers the sections
n/a | backup_coverage_test › every table a plugin owns is either backed up or excused | - | XTA's SQLite schema guard; native keeps no SQLite
covered | backup_coverage_test › no two sections claim the same key or table | store/backup BackupDocumentTest
covered | backup_coverage_test › the newly covered sections survive an actual import | BackupDocumentTest
n/a | backup_coverage_test › nothing is excluded that no longer exists | - | XTA's SQLite schema guard; native keeps no SQLite

## test/export_preferences_test.dart
covered | export_preferences_test › subscription export includes preference-backed authors without other settings | ui/settings BackupTest
covered | export_preferences_test › group membership ids still resolve after subscription-only export round trip | BackupTest
covered | export_preferences_test › settings choice keeps normal preferences and still strips credentials | BackupTest
covered | export_preferences_test › excluding both settings and subscriptions exports no preferences | BackupTest

## test/video_controller_pool_test.dart
untested | video_controller_pool_test › stalled URL resolution ends the spinner and offers manual restart | Compose (player) | Compose only
covered | video_controller_pool_test › stalled native startup exposes retry and retains capacity until disposal | ui/media PlayerTurnsTest.retry
covered | video_controller_pool_test › eviction retains capacity until the native player is actually disposed | PlayerTurnsTest.release
untested | video_controller_pool_test › abandoned startup cannot allocate more decoders and late completion is disposed | ui/media (pool) | no pure rule
untested | video_controller_pool_test › a late release cannot release a replacement with the same key | ui/media (pool) | no pure rule
covered | video_controller_pool_test › reattaching to a cached key shares one acquisition | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
covered | video_controller_pool_test › a player keyed to its widget alone goes with the widget | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
covered | video_controller_pool_test › a player keyed to its post stays cached for the next screen | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
untested | video_controller_pool_test › play no more at once than the pool can host | ui/media (pool) | no pure rule
untested | video_controller_pool_test › are keyed like the same GIF in a post | Compose (player keys) | Compose only

## test/video_download_variant_test.dart
covered | video_download_variant_test › saves the quality being watched | MediaRulesTest.aDownloadSavesTheVariantWatchedOrTheBest
covered | video_download_variant_test › falls back to the best file when the player is on HLS or nothing known | MediaRulesTest.aDownloadSavesTheVariantWatchedOrTheBest
covered | video_download_variant_test › a stream without an MP4 variant has nothing to save | MediaRulesTest.qualitiesAreTheMp4sHighestFirstNamedByTheirHeight

## test/video_driver_properties_test.dart
untested | video_driver_properties_test › Android gets AAudio with an OpenSL ES fallback, and MediaCodec decoding | Android media driver | platform-specific; Compose/Android only
covered | video_driver_properties_test › elsewhere libmpv keeps its own audio output and decoder | ui/media MpvOptionsTest, MpvEngineTest

## test/video_fullscreen_handoff_test.dart
covered | video_fullscreen_handoff_test › a tile gone under its full-screen player gives the slot back once that closes | PlayerTurnsTest.fullScreenKeepsThePlayerThroughADropAndARelease
covered | video_fullscreen_handoff_test › a tile still there when full screen closes keeps its player until it goes | PlayerTurnsTest.fullScreenKeepsThePlayerThroughADropAndARelease

## test/video_memory_pressure_test.dart
covered | video_memory_pressure_test › system memory pressure releases idle players and preserves attached players | PlayerTurnsTest.release
untested | video_memory_pressure_test › idle creation finishing after a memory warning is disposed once | ui/media (pool) | no pure rule
covered | video_memory_pressure_test › a visible token protects a released player until the view hides | PlayerTurnsTest.release

## test/video_mute_badge_test.dart
untested | video_mute_badge_test › a loading inline video keeps its poster, with no spinner | Compose (media tile) | Compose only
untested | video_mute_badge_test › a full-screen player that opts in still shows it is loading | Compose (media tile) | Compose only
untested | video_mute_badge_test › a video waiting for a free player shows no spinner either | Compose (media tile) | Compose only
covered | video_mute_badge_test › the mute badge toggles sound at once, without controls shown | VideoSettingsTest.theAppsMuteIsTheSettingUntilTheReaderChooses
untested | video_mute_badge_test › with animations on, the badge cross-fades from one icon to the other | Compose (motion) | Compose only
untested | video_mute_badge_test › the badge is a labelled toggle | Compose semantics | Compose only
untested | video_mute_badge_test › the badge sits in the bottom-end corner with a full touch target | Compose (media tile) | Compose only
untested | video_mute_badge_test › right-to-left puts the badge in the bottom-left corner | Compose (media tile) | Compose only
covered | video_mute_badge_test › a GIF has no sound, so no badge and no spinner | VideoSettingsTest.aGifLoopsSilentlyWhateverTheSettings
untested | video_mute_badge_test › a tap-to-play poster keeps its play button and no badge | Compose (media tile) | Compose only
untested | video_mute_badge_test › a Mastodon or Bluesky video loads without a spinner and shows the badge | Compose (plugin card) | Compose only
untested | video_mute_badge_test › a plugin GIF shows no badge | Compose (plugin card) | Compose only

## test/video_playback_policy_test.dart
covered | video_playback_policy_test › a plain video waits for a tap | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › autoplay, GIFs, a tap and a cached player all skip it | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › an off-screen GIF allocates nothing | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › an off-screen autoplay video allocates nothing | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › the same GIF allocates once it has been on screen | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › a tap creates the player as soon as it is on screen | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › an already pooled player reattaches without waiting for visibility | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
untested | video_playback_policy_test › a cached player is not reattached by a tile that is off screen | ui/media (pool) | no pure rule
covered | video_playback_policy_test › a GIF that has been let go stays let go until it is back on screen | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › a video still showing its play button never allocates, visible or not | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › fullscreen never reclaims, even when the tile looks fully hidden | PlayerTurnsTest.fullScreenKeepsThePlayerThroughADropAndARelease
untested | video_playback_policy_test › a truly off-screen tile may reclaim | ui/media (pool) | no pure rule
covered | video_playback_policy_test › a half-visible tile, or one still marked visible elsewhere, stays | VideoSettingsTest.aTileIsSeenWhileHalfOfItIsOnScreen
covered | video_playback_policy_test › reattaching to a cached player is always allowed | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
untested | video_playback_policy_test › a new player is refused when the pool is full and nothing is free | ui/media (pool) | no pure rule
untested | video_playback_policy_test › a new player is allowed when an unused entry can be evicted | ui/media (pool) | no pure rule
untested | video_playback_policy_test › acquire at capacity must drop unused entries to free a slot | ui/media (pool) | no pure rule
untested | video_playback_policy_test › release leaves unused entries cached up to maxSize | ui/media (pool) | no pure rule

## test/video_quality_continuity_test.dart
covered | video_quality_continuity_test › quality switch preserves position, volume, rate and active play intent | PlaybackTest.aRestoredSourceIsSoughtOnceItKnowsItsLength
covered | video_quality_continuity_test › paused video remains paused after a quality switch | PlaybackTest.pause
covered | video_quality_continuity_test › if native start is ignored seek waits until new duration arrives | PlaybackTest.aRestoredSourceIsSoughtOnceItKnowsItsLength
covered | video_quality_continuity_test › unacknowledged seek falls back once to a playable start | PlaybackTest.aFailedReplacedOrStuckSourceIsNotRestored
untested | video_quality_continuity_test › missing duration times out without seeking an unready decoder | ui/media (player) | no pure rule
untested | video_quality_continuity_test › rapid quality choices serialize native opens and latest choice wins | ui/media (player) | no pure rule
untested | video_quality_continuity_test › new choice cancels a duration wait rather than waiting for timeout | ui/media (player) | no pure rule
covered | video_quality_continuity_test › disposal cancels restoration before any seek or playback command | PlaybackTest.dispose
untested | video_quality_continuity_test › hiding the video during restoration suppresses delayed automatic play | ui/media (player) | no pure rule
covered | video_quality_continuity_test › mute and speed changes made while loading take precedence | VideoSettingsTest.aVideoPlaysAsTheMediaSettingsSay
covered | video_quality_continuity_test › failed variant restores the original source and remains retryable | MpvVideoRulesTest.aFailedChangeOfQualityGoesBackToTheVariantBefore
covered | video_quality_continuity_test › completed videos start paused and invalid rate or volume is not applied | PlaybackTest.theResumePlaceStopsShortOfTheEnd
covered | video_quality_continuity_test › resume target stays within a shorter variant and does not seek EOF | PlaybackTest.theResumePlaceStopsShortOfTheEnd
untested | video_quality_continuity_test › stalled native seek fails promptly without overlapping a fallback open | ui/media (player) | no pure rule
untested | video_quality_continuity_test › stalled native open fails promptly and disposal waits for its late completion | ui/media (player) | no pure rule
untested | video_quality_continuity_test › latest choice unblocks when an uncancellable open is still stalled | ui/media (player) | no pure rule
covered | video_quality_continuity_test › latest choice cannot receive the seek of an abandoned earlier source | MpvVideoRulesTest.aFailedChangeOfQualityGoesBackToTheVariantBefore

## test/profile_count_roll_test.dart
untested | profile_count_roll_test › a count is plain text until X's copy changes it, then rolls to the new number | Compose (count roll) | Compose only
untested | profile_count_roll_test › a rolling count is read as one line with its label | Compose semantics | Compose only

## test/profile_design_primitives_test.dart
untested | profile_design_primitives_test › ${entry.key} missing profile media uses tonal fallbacks | Compose (profile parts) | Compose only
untested | profile_design_primitives_test › profile count link meets the interaction minimum | Compose (profile parts) | Compose only
untested | profile_design_primitives_test › profile counts stay side by side and remain tappable | Compose (profile parts) | Compose only
untested | profile_design_primitives_test › avatar bottom, actions and wrapped counts have separate targets at 320px | Compose (profile parts) | Compose only
untested | profile_design_primitives_test › profile actions stay flat in one row and remain tappable | Compose (profile parts) | Compose only
untested | profile_design_primitives_test › profile tabs use one pinned-height selection surface | Compose (profile tabs) | Compose only
untested | profile_design_primitives_test › profile identity wraps on a narrow large-text screen | Compose (profile header) | Compose only
untested | profile_design_primitives_test › profile filter shows every choice as an icon and marks the selected one without color alone | Compose (profile filter) | Compose only
untested | profile_design_primitives_test › flat profile tabs share the width and mark the selected name | Compose (profile tabs) | Compose only

## test/profile_flat_test.dart
covered | profile_flat_test › groups a count the language writes out, and abbreviates as before | PluralsTest; StockNumbersTest | the same number writing
untested | profile_flat_test › a blank line is a third of a line, scaled with the text | Compose (bio) | Compose only
untested | profile_flat_test › keeps the text, so a copy still holds the blank lines | Compose (bio) | Compose only
untested | profile_flat_test › says Subscribed with a check, and opens the same menu | Compose (profile actions) | Compose only
untested | profile_flat_test › says Subscribe in the accent when not subscribed | Compose (profile actions) | Compose only
untested | profile_flat_test › shows an eye-off icon for an account hidden from the main feed | Compose (profile header) | Compose only
untested | profile_flat_test › drops its label at 320 dp with text ×2 and keeps its tooltip | Compose (profile actions) | Compose only
untested | profile_flat_test › draws no ring around settings and follow | Compose (profile actions) | Compose only
untested | profile_flat_test › sets the bio's blank lines tight | Compose (bio) | Compose only
untested | profile_flat_test › puts the link and the join date on one row | Compose (profile header) | Compose only
covered | profile_flat_test › writes German counts in full and the date without Twitter | PluralsTest; TimesTest
covered | profile_flat_test › says when the account joined, with the date, in every language | TimesTest
untested | profile_flat_test › lays the note across the width between hairlines | Compose (profile note) | Compose only
covered | profile_flat_test › tabs are names that share the width, the second says Replies, and filters are named icons | ProfileRulesTest.aProfileOpensOnTheTabTheReaderChoseInXtasOrder
untested | profile_flat_test › scroll from the start when the names do not fit | Compose (profile tabs) | Compose only

## test/profile_followed_by_test.dart
covered | profile_followed_by_test › orders by how recently each followed, then subscription order | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › counts unread subscriptions as unchecked, not as non-followers | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › ignores former subscriptions, other networks and the profile | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › nothing remembered means nothing to show | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › names up to three, then two and a count | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › keep a month of reads across launches, without asking X | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › a stale read still asks X again for Discover | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead
covered | profile_followed_by_test › the store answers from what is remembered | ProfileRulesTest.followedByNamesWhoFollowsMostRecentFirstFromTheListsAlreadyRead

## test/profile_load_failure_test.dart
covered | profile_load_failure_test › an account that does not exist says so instead of offering a retry | ProfileRulesTest.whatXSaidOfAProfileIsThePageAndAnythingElseIsRetried
covered | profile_load_failure_test › a suspended or private account says which | ProfileRulesTest.whatXSaidOfAProfileIsThePageAndAnythingElseIsRetried
covered | profile_load_failure_test › a failure X did not explain can be retried | ProfileRulesTest.whatXSaidOfAProfileIsThePageAndAnythingElseIsRetried
covered | profile_load_failure_test › the failure details give X's own answer | ProfileRulesTest.whatXSaidOfAProfileIsThePageAndAnythingElseIsRetried

## test/profile_loading_recovery_test.dart
untested | profile_loading_recovery_test › stalled profile cache does not prevent the network load | ui/profile ProfileViewModel | no view-model test harness
untested | profile_loading_recovery_test › timed out profile recovers and ignores the old response | ProfileViewModel | no view-model test harness
untested | profile_loading_recovery_test › closing a stalled profile permits another profile to load | ProfileViewModel | no view-model test harness
untested | profile_loading_recovery_test › stalled cache open and write cannot hold successful posts | ProfileViewModel | no view-model test harness
untested | profile_loading_recovery_test › a stalled stale-cache read preserves the original network error | ProfileViewModel | no view-model test harness
untested | profile_loading_recovery_test › cached page remains available when the network fails | ProfileViewModel | no view-model test harness

## test/profile_note_test.dart
untested | profile_note_test › the empty row asks for a note in full German and stays one flat line | Compose (profile note) | Compose only
untested | profile_note_test › a saved note replaces the label on the row | Compose (profile note) | Compose only
untested | profile_note_test › tapping the row opens a sheet that actually saves | ui/profile ProfileParts, store profile notes | the sheet is Compose-only; the store is shared with Saved's note editor
untested | profile_note_test › clearing the sheet deletes the note and restores the label | Compose (profile note) | Compose only

## test/profile_posts_recovery_test.dart
covered | profile_posts_recovery_test › a failed first page of posts recovers on its own | ReadRetriesTest
covered | profile_posts_recovery_test › a posts tab that needs sign-in keeps the manual notice | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | profile_posts_recovery_test › a failed next page recovers on its own below the loaded posts | ReadRetriesTest

## test/profile_reposts_test.dart
covered | profile_reposts_test › Retweets reads the reposts timeline, not the posts one | ProfileRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
covered | profile_reposts_test › All shows posts and reposts, newest first | ProfileRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
covered | profile_reposts_test › Posts leaves reposts out | ProfileRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost

## test/profile_top_bar_test.dart
untested | profile_top_bar_test › with the header in view the bar holds no title and no banner | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › scrolled, the name rises in over the blurred banner, in white | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › the actions stay light while the bar turns, never the theme's grey | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › scrolling back to the top clears the bar again | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › without a banner the bar keeps its colour and only the title rises in | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › with reduced motion the bar changes at once | Compose (profile top bar) | Compose only
untested | profile_top_bar_test › the scrim keeps white text readable over a white banner | Compose (profile top bar) | Compose only

## test/profile_view_store_test.dart
covered | profile_view_store_test › profile filters update independently | ProfileRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
untested | profile_view_store_test › back-to-top state only reflects the latest threshold result | ProfileViewModel | no view-model test harness

## test/download_center_screen_test.dart
untested | download_center_screen_test › Downloads production screen render $variant | Compose (Downloads) | golden/layout sweep
covered | download_center_screen_test › retry and clear history act on real store without deleting downloaded files | DownloadQueueTest, DownloadHistoryTest

## test/download_center_test.dart
covered | download_center_test › only the X image host receives original-image normalization | DownloadNamingTest.originalDownloadUrl
covered | download_center_test › file names cannot escape their chosen destination | DownloadNamingTest (safeDownloadFolder)
covered | download_center_test › history is atomic and active work becomes retryable after restart | DownloadHistoryTest
covered | download_center_test › history rejects oversized or malformed metadata and bounds retained entries | DownloadHistoryTest
covered | download_center_test › streams chunks to disk, reports progress and deletes the staging file | DownloadTransferTest
covered | download_center_test › a short response never writes an incomplete media file to the destination | DownloadTransferTest
covered | download_center_test › cancellation closes HTTP and refuses a late response before any destination write | DownloadTransferTest
covered | download_center_test › server errors never reach the file picker or document provider | DownloadTransferTest; DownloadQueueTest
covered | download_center_test › queued cancellation never starts HTTP and active cancellation cannot finish as success | DownloadQueueTest
covered | download_center_test › queued cancellation blocks the pump and retry while partial-file cleanup is pending | DownloadQueueTest
covered | download_center_test › retry uses the same history entry and batch results count only saved destinations | DownloadQueueTest; PluginBatchesTest
covered | download_center_test › immediate retry survives the cancelled attempt finishing late | DownloadQueueTest
covered | download_center_test › a failed history read cannot erase existing records and can be retried | DownloadHistoryTest
covered | download_center_test › retrying metadata write preserves the current failed download entry | DownloadHistoryTest
covered | download_center_test › network does not start when the queued work cannot be persisted | DownloadQueueTest

## test/download_directory_test.dart
covered | download_directory_test › recognises the media X serves | DownloadNamingTest (mime types)
covered | download_directory_test › labels a saved frame archive as a ZIP | DownloadNamingTest (mime types)
covered | download_directory_test › falls back to a generic type rather than guessing | DownloadNamingTest (mime types)
covered | download_directory_test › shows the folder, not the whole tree URI | DownloadDestinationTest
covered | download_directory_test › copes with an SD card volume and a bare id | DownloadDestinationTest
untested | download_directory_test › sends the tree, name, bytes and type to the platform | ui/downloads PlatformFiles (Android) | platform boundary, no JVM test
untested | download_directory_test › a lost grant surfaces as an error the caller can report | PlatformFiles | platform boundary
untested | download_directory_test › copies a path without channel bytes and carries its cancellable operation id | PlatformFiles | platform boundary
covered | download_directory_test › a subfolder reaches the platform cleaned, and only when there is one | DownloadDestinationTest; DownloadNamingTest
untested | download_directory_test › cancel and cleanup identify only the in-flight operation and its new document | PlatformFiles | platform boundary
untested | download_directory_test › open includes saved document and media type | PlatformFiles | platform boundary
covered | download_directory_test › is false without asking the platform when nothing is stored | DownloadDestinationTest
untested | download_directory_test › reports what the platform says about a stored tree | PlatformFiles | platform boundary
untested | download_directory_test › returns the chosen tree, and null when the user backs out | PlatformFiles | platform boundary

## test/download_queue_limit_test.dart
covered | download_queue_limit_test › a store left at its default runs one transfer at a time, oldest first | DownloadQueueTest (concurrency 1)
covered | download_queue_limit_test › with a limit of two, two run and the third waits for a free slot | DownloadQueueTest
covered | download_queue_limit_test › raising the limit starts queued downloads at once; lowering it stops nothing | DownloadQueueTest
covered | download_queue_limit_test › downloads without a folder fetch side by side but open the save dialog one at a time | DownloadQueueTest (dialog turn)
covered | download_queue_limit_test › a download cancelled while it waits for the dialog never opens one | DownloadQueueTest
covered | download_queue_limit_test › the limit stays within the offered choices | store/settings SettingsTest (MediaPrefs.simultaneous)
covered | download_queue_limit_test › the Simultaneous downloads setting stores the limit and applies it to the queue | SettingsTest; DownloadQueueTest
covered | download_queue_limit_test › safeDownloadFolder keeps a path inside the chosen folder | DownloadNamingTest
covered | download_queue_limit_test › an entry keeps its subfolder in history, and a tampered one is cleaned | DownloadHistoryTest
covered | download_queue_limit_test › a queued request carries its cleaned subfolder to the transfer | DownloadQueueTest
covered | download_queue_limit_test › bytes made on the device are staged and copied, never sent through the channel | PluginBatchesTest; Downloading.kt madeNow
covered | download_queue_limit_test › the transfer hands the subfolder to the platform save | DownloadTransferTest

## test/offline_library_layout_test.dart
untested | offline_library_layout_test › offline production collection $variant | Compose (Offline) | golden/layout sweep

## test/offline_speech_engine_test.dart
covered | offline_speech_engine_test › a German article is read by the downloaded German voice | OfflineSpeechEngineTest
covered | offline_speech_engine_test › an English article falls back to the system engine | OfflineSpeechEngineTest
covered | offline_speech_engine_test › switched off, the system engine reads | OfflineSpeechEngineTest
covered | offline_speech_engine_test › a voice that fails to load hands over to the system | OfflineSpeechEngineTest
covered | offline_speech_engine_test › text of unknown language uses the app language | OfflineSpeechEngineTest
covered | offline_speech_engine_test › keeps a loaded voice and swaps it for another language | OfflineSpeechEngineTest
covered | offline_speech_engine_test › a short article is a single clip | OfflineSpeechEngineTest
covered | offline_speech_engine_test › chunks play in order with the next one prepared early | OfflineSpeechEngineTest
covered | offline_speech_engine_test › stopping drops what was prepared and plays nothing more | OfflineSpeechEngineTest
covered | offline_speech_engine_test › the speech rate becomes sherpa speed | SherpaVoiceSynthesizerTest; TtsRulesTest
covered | offline_speech_engine_test › completes when the clip has played to the end | OfflineSpeechEngineTest
covered | offline_speech_engine_test › a stale "completed" before playback starts is ignored | OfflineSpeechEngineTest
covered | offline_speech_engine_test › a player error fails the clip | OfflineSpeechEngineTest
covered | offline_speech_engine_test › a clip that never starts fails instead of hanging | OfflineSpeechEngineTest

## test/offline_voice_catalog_test.dart
covered | offline_voice_catalog_test › ids are unique and usable as folder names | VoicesTest
covered | offline_voice_catalog_test › every voice is pinned to k2-fsa tts-models over https | VoicesTest
covered | offline_voice_catalog_test › every voice has a confirmed checksum and size | VoicesTest
covered | offline_voice_catalog_test › names its model files and licence | VoicesTest
covered | offline_voice_catalog_test › offers German and English | VoicesTest
covered | offline_voice_catalog_test › reads German with the neutral Thorsten speaker | VoicesTest
covered | offline_voice_catalog_test › matches the language whatever the region | VoicesTest
covered | offline_voice_catalog_test › finds nothing for a language no voice speaks | VoicesTest
covered | offline_voice_catalog_test › prefers the better voice when two speak the language | VoicesTest
covered | offline_voice_catalog_test › strip the archive folder | StreamTarTest
covered | offline_voice_catalog_test › refuse anything outside it | StreamTarTest
covered | offline_voice_catalog_test › speech rate maps onto sherpa speed | TtsRulesTest
covered | offline_voice_catalog_test › loads VITS voices with their espeak-ng data | SherpaVoiceSynthesizerTest
covered | offline_voice_catalog_test › loads Kokoro with its speaker embeddings | SherpaVoiceSynthesizerTest

## test/offline_voices_section_test.dart
untested | offline_voices_section_test › lists every voice with language, size and licence | Compose (voice settings) | Compose only
untested | offline_voices_section_test › download shows progress and can be cancelled | Compose (voice settings) | Compose only; the download is OfflineVoices
untested | offline_voices_section_test › installing, ready and failed states | Compose (voice settings) | Compose only; states in OfflineVoices
untested | offline_voices_section_test › delete asks first, then removes the voice | Compose (voice settings) | Compose only
untested | offline_voices_section_test › fits 320 dp at large text with full-size tap targets | Compose (voice settings) | Compose only
untested | offline_voices_section_test › the engine has its own row, and the note says where it comes from | Compose (voice settings) | Compose only
covered | offline_voices_section_test › a voice on the device without the engine says it needs it | VoicesTest
untested | offline_voices_section_test › deleting the engine asks first and says what it means for the voices | Compose (voice settings) | Compose only
covered | offline_voices_section_test › a voice and the engine already on the device show as such when the settings open | VoicesTest
covered | offline_voices_section_test › where no engine is published, nothing can be downloaded | VoicesTest
untested | offline_voices_section_test › the engine row fits 320 dp at large text | Compose (voice settings) | Compose only

## test/reader_feed_test.dart
covered | reader_feed_test › article identity removes tracking but preserves article parameters | XLinksTest (tracking removal)
covered | reader_feed_test › one source completes while another waits, fails, and retries independently | BatchReadTest.aBatchThatAnsweredShowsBeforeASlowerOneFinishes, aRetryReadsOnlyTheBatchesThatFailed
untested | reader_feed_test › new posts are held until requested while reading away from the top | Compose (reader) | Compose only
covered | reader_feed_test › obsolete source requests cannot replace a newly selected group | BatchReadTest.aCancelledLoadNeitherShowsNorStartsWhatWasQueued
covered | reader_feed_test › rebuilding an unchanged feed does not restart or cancel pending sources | BatchReadTest; FeedSessionCacheTest
covered | reader_feed_test › changed members and explicit refresh still reload plugin sources | BatchReadTest
covered | reader_feed_test › snapshots survive new store instances and damaged rows are ignored | store/offline TimelineCacheTest
covered | reader_feed_test › queued writes and deletion do not resurrect discarded state | store kept-tests

## test/reader_link_stack_layout_test.dart
untested | reader_link_stack_layout_test › a link stack eases open and closed | Compose (reader link stack) | Compose only
untested | reader_link_stack_layout_test › link stack preserves each commentary, dark=$dark scale=$scale | Compose (reader link stack) | Compose only

## test/reader_recovery_controls_test.dart
covered | reader_recovery_controls_test › network proceeds when cache stalls or throws | BatchReadTest
covered | reader_recovery_controls_test › deadline covers cache time and cancelled cache cannot publish or start network | BatchReadTest
covered | reader_recovery_controls_test › concurrent retries coalesce and status changes reuse the sorted list | ReadRetriesTest
covered | reader_recovery_controls_test › late network result cannot replace a successful manual retry | BatchReadTest
untested | reader_recovery_controls_test › prefix index discovers existing rows and does not await an unrelated write | store index | no separate test
untested | reader_recovery_controls_test › coalesced cache trimming preserves newest rows and unrelated data | store cache | no separate test

## test/reader_reliability_test.dart
covered | reader_reliability_test › completed batch is published before a slower batch finishes | BatchReadTest.aBatchThatAnsweredShowsBeforeASlowerOneFinishes
covered | reader_reliability_test › selective retry does not refetch successful batches | BatchReadTest.aRetryReadsOnlyTheBatchesThatFailed
covered | reader_reliability_test › abandoned batch cannot publish or start queued work | BatchReadTest.aCancelledLoadNeitherShowsNorStartsWhatWasQueued
covered | reader_reliability_test › timed out batch releases its slot and ignores late completion | BatchReadTest.aBatchPastItsTimeFailsAndALateAnswerChangesNothing
covered | reader_reliability_test › overall deadline retains successes and retries only unfinished batches | BatchReadTest.theLoadsDeadlineKeepsWhatAnsweredAndARetryReadsOnlyWhatDidNot
covered | reader_reliability_test › read cancellation prevents subsequent fanout after late completion | BatchReadTest.aCancelledLoadNeitherShowsNorStartsWhatWasQueued
covered | reader_reliability_test › recovery excludes failures that need the reader and coalesces repeated signals | ReadRetriesTest
untested | reader_reliability_test › diagnostic timings are bounded and do not retain exception messages | ui/settings diagnostics | no separate test
covered | reader_reliability_test › a failed read says why by category, and a cancelled or slow one says nothing more | ReadRetriesTest; ProblemLookTest

## test/reader_tab_gestures_test.dart
covered | reader_tab_gestures_test › reader tabs switch only on release and preserve reading position | TabSwipesTest.aSwipeTurnsOneTabWhenItGoesFarOrFastEnough
untested | reader_tab_gestures_test › cancellation and a second pointer in the header do not change tabs | Compose (tabs) | Compose only
covered | reader_tab_gestures_test › rapid swipes do not duplicate an unfinished tab change | TabSwipesTest.aSwipeThatTurnsBackLeavesTheTabs
covered | reader_tab_gestures_test › RTL and reduced motion retain navigation and visible alternatives | TabSwipesTest; MotionTest
untested | reader_tab_gestures_test › a nested carousel keeps its boundary gestures | Compose (tabs) | Compose only
untested | reader_tab_gestures_test › changing reduced motion during navigation preserves the selected page | Compose (tabs) | Compose only
untested | reader_tab_gestures_test › pinch zoom belongs to the image viewer | Compose (viewer) | Compose only

## test/settings_ai_test.dart
covered | settings_ai_test › the Grok chip fills the xAI server and model | SettingsLogicTest.aPresetFromAnotherProviderClearsTheKeyAndTakesItsModel
covered | settings_ai_test › OpenRouter saves a custom model ID and restores it | SettingsTest (AiPrefs); SettingsLogicTest
covered | settings_ai_test › switching provider clears the previous provider key and model | SettingsLogicTest.aPresetFromAnotherProviderClearsTheKeyAndTakesItsModel

## test/settings_backup_test.dart
covered | settings_backup_test › carries every section the backup covers | BackupTest; BackupRoundTripTest
covered | settings_backup_test › says which format wrote it, from which build and when | BackupTest.aFileIsABackupXtaCanRestoreOrSaysWhyNot
covered | settings_backup_test › accepts what it knows and refuses what came after it | BackupTest.aFileIsABackupXtaCanRestoreOrSaysWhyNot
covered | settings_backup_test › reads a file without a header as the version before the header | BackupTest
covered | settings_backup_test › imports the sections it does have | BackupTest.aSectionTheFileDoesNotCarryChangesNothing
covered | settings_backup_test › restores nothing for the sections it never had | BackupTest.aSectionTheFileDoesNotCarryChangesNothing
covered | settings_backup_test › writes each section to its own table | BackupTest.onlyTheChosenSectionsAreWrittenAndNotesAlwaysAre
covered | settings_backup_test › never carries a cache | BackupTest.onlyTheChosenSectionsAreWrittenAndNotesAlwaysAre
covered | settings_backup_test › leaves reading positions out unless they were asked for | BackupTest.readingPositionsGoToTheFeedsThisDeviceHas
covered | settings_backup_test › adds up the sections behind one category | BackupTest
covered | settings_backup_test › is empty when there is nothing to restore | BackupTest

## test/settings_design_primitives_test.dart
covered | settings_design_primitives_test › export selection enforces group-member dependencies | BackupTest.groupsGoOutWithWhatTheirRowsCarried
untested | settings_design_primitives_test › settings rows retain the established touch target | Compose (settings) | Compose only
covered | settings_design_primitives_test › preference selector exposes and persists selected state | SettingsTest
untested | settings_design_primitives_test › theme screen keeps visual selectors and legacy OLED controls | Compose (theme settings) | Compose only

## test/settings_direct_access_test.dart
untested | settings_direct_access_test › search opens and highlights autoplay below the fold, and the toggle works | Compose (settings search) | Compose only
covered | settings_direct_access_test › translated control index includes reading position and text size | SettingsLogicTest.sectionsMatchTheirTitleOrDescription
untested | settings_direct_access_test › installed Mastodon has separate client and settings actions | Compose (plugin settings) | Compose only

## test/settings_hub_test.dart
untested | settings_hub_test › the hub shows everyday tiles and a collapsed Advanced fold | Compose (settings hub) | Compose only
untested | settings_hub_test › search flattens Advanced so Diagnostics is findable | Compose (settings hub) | Compose only
covered | settings_hub_test › search matches a short hint, not the old pref-name dump | SettingsLogicTest.sectionsMatchTheirTitleOrDescription
covered | settings_hub_test › search finds read-aloud by Sherpa | SettingsLogicTest.everyWordMustBeFoundAndATitleStartingWithTheFirstComesFirst
untested | settings_hub_test › Data is one tile that opens import, export, and sync | Compose (settings hub) | Compose only
untested | settings_hub_test › expanding Advanced reveals AI and Diagnostics | Compose (settings hub) | Compose only

## test/home_account_filter_test.dart
covered | home_account_filter_test › homeFeedDisabledIdsFromPrefs reads JSON string lists | HomeFilterTest.theDraftTogglesOnlyAndResets
covered | home_account_filter_test › enabledHomeAccounts falls back when every account is disabled | HomeFilterTest.allAccountsOffCannotBeApplied
covered | home_account_filter_test › mergeHomeTimelineChains dedupes and sorts newest first | ForYouRulesTest (merge)
covered | home_account_filter_test › dropChainsFromAuthors removes posts by turned-off logins | ForYouRulesTest (authors)
covered | home_account_filter_test › home timeline cursors round-trip as JSON | store/offline TimelineCacheTest
covered | home_account_filter_test › canDisableHomeAccount keeps at least one account on | HomeFilterTest.allAccountsOffCannotBeApplied
covered | home_account_filter_test › setEnabled refuses to turn off the last account | HomeFilterTest.allAccountsOffCannotBeApplied
covered | home_account_filter_test › homeFollowingCacheKey matches the home tab cache | TimelineCacheTest
covered | home_account_filter_test › enabled accounts are the ones fetch should prefer | ForYouRulesTest
covered | home_account_filter_test › toggling evicts Following cache without a parent FeedRefreshController | TimelineCacheTest
covered | home_account_filter_test › the last account on cannot be switched off | HomeFilterTest.allAccountsOffCannotBeApplied
covered | home_account_filter_test › a spare account can be excluded from Following and For you | HomeFilterTest.theFilterIsKeptInTheSettingsAndTheSession

## test/home_navigation_test.dart
untested | home_navigation_test › a tab enabled after the start gets a focus node | Compose (Home tabs) | Compose only
untested | home_navigation_test › tapping a tab fades its page in | Compose (Home tabs) | Compose only
untested | home_navigation_test › a tab enabled after the start is highlighted when tapped | Compose (Home tabs) | Compose only
untested | home_navigation_test › Discover enabled after the start searches the network it was reached from | Compose (Discover) | Compose only
untested | home_navigation_test › a tab disabled after the start drops its focus node | Compose (Home tabs) | Compose only

## test/home_timeline_nested_scroll_test.dart
untested | home_timeline_nested_scroll_test › the loading skeleton is the only inner scrollable | Compose (nested scroll) | Compose only
untested | home_timeline_nested_scroll_test › a nested skeleton does not steal the inner controller from the list | Compose (nested scroll) | Compose only
untested | home_timeline_nested_scroll_test › cached Following preview does not attach the outer | Compose (nested scroll) | Compose only
untested | home_timeline_nested_scroll_test › empty Following is a pullable inner list, not a Center | Compose (nested scroll) | Compose only
covered | home_timeline_nested_scroll_test › jumps immediately when motion is disabled | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | home_timeline_nested_scroll_test › does not animate when NestedScrollView has two positions | Compose (nested scroll) | Compose only

## test/home_timeline_picker_test.dart
untested | home_timeline_picker_test › Source picker reaches many long names and keeps Add visible (large RTL $large) | Compose (source picker) | Compose only

## test/subscriptions_header_test.dart
untested | subscriptions_header_test › keeps the icons while they fit, then the names, then takes two rows | Compose (subscriptions header) | Compose only
untested | subscriptions_header_test › one row holds the switch, search, add and options in ${locale.languageCode} | Compose (subscriptions header) | Compose only
untested | subscriptions_header_test › drops the tab icons before it gives up the row | Compose (subscriptions header) | Compose only
untested | subscriptions_header_test › a narrow screen with large text falls back to two rows | Compose (subscriptions header) | Compose only
untested | subscriptions_header_test › keeps Back when the X reader opens the screen | Compose (subscriptions header) | Compose only
covered | subscriptions_header_test › filters the groups and closes on Back | SubscriptionListTest.aSearchFindsTheNameOrTheHandle
covered | subscriptions_header_test › filters the people by name and handle | SubscriptionListTest.aSearchFindsTheNameOrTheHandle

## test/subscriptions_list_test.dart
covered | subscriptions_list_test › a subscription can be dragged to a new place | SubscriptionListTest.aDraggedRowLandsWhereItWasDropped
untested | subscriptions_list_test › saving a change keeps the list where the reader had it, with no spinner | Compose (subscriptions list) | Compose only
untested | subscriptions_list_test › a first load shows the spinner until there is a list | Compose (subscriptions list) | Compose only

## test/subscriptions_menu_sheet_test.dart
untested | subscriptions_menu_sheet_test › groups sheet shows the board, not a wall of unlabeled rows | Compose (menu sheet) | Compose only
untested | subscriptions_menu_sheet_test › list layout hides the column picker | Compose (menu sheet) | Compose only
untested | subscriptions_menu_sheet_test › people sheet has no board, columns, or deck | Compose (menu sheet) | Compose only
covered | subscriptions_menu_sheet_test › picking list layout updates the pref and hides columns | SettingsTest (subscriptions layout)
covered | subscriptions_menu_sheet_test › Kompakt is a third column choice, and 2 or 3 turn it off | SettingsTest (column choices)

## test/subscriptions_render_test.dart
untested | subscriptions_render_test › (render sweeps) | Compose (subscriptions) | golden/layout sweep
