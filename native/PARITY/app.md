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
untested | feed_strip_test › X tabs use house and spark; plugins reuse XtaPlugin.icon | Compose (Home tab icon) | the icon choice lives in a composable
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
covered | group_discovery_follow_test › group rows are keyed the way each network keys its own follows | DiscoveryViewModel.memberOf | DiscoveryViewModelTest.aCandidateIsKeyedTheWayItsNetworkKeysIt
covered | group_discovery_follow_test › the add store marks a row as adding, then added, and forgets a failed one | DiscoveryViewModel.add | DiscoveryViewModelTest.addingACandidateMarksItAddedAndPutsItInTheGroup | the failed half needs a failing follow write; the state path is the same

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
covered | group_discovery_test › all-followed candidates never trigger the configured AI | DiscoveryViewModel.rankWithAi | DiscoveryViewModelTest.anAllFollowedCandidatesNeverTriggerTheAi
covered | group_discovery_test › Scan more reads another rotating batch and keeps what the first one found | DiscoveryBoard (never reads less), rotating read | DiscoverySourcesTest.theBoardKeepsEachSourcesLatestAnswerAndNeverReadsLess, aRotatingRead…
covered | group_discovery_test › hiding an account removes it until Undo brings it back | Discovery.visible (HIDE judgement), DiscoveryViewModel.undo | DiscoveryTest.whoTheReaderFollowsAndWhatTheyHidIsLeftOut…
covered | group_discovery_test › following from a profile removes its cached discovery card without refetch | Discovery.followedExcept / DiscoveryBoard.visible(followed) | DiscoverySourcesTest.theAiOrderLeads… ("the followed leave"), DiscoveryTest.whoTheReaderFollows…
untested | group_discovery_test › switching from Discover remembers that its pane has been opened | GroupFeedScreen.kt (discovering saved per group) | Compose state
covered | group_discovery_test › a superseded source request cannot replace newer results | DiscoveryViewModel generation/absorb | DiscoveryViewModelTest.aLateSourceCannotReplaceANewerScan
covered | group_discovery_test › a superseded AI response cannot replace newer discovery | DiscoveryViewModel.rank (run != generation) | DiscoveryViewModelTest.aSupersededAiOrderCannotReplaceANewerScan
covered | group_discovery_test › following during a source request excludes its eventual result | DiscoveryViewModel.publish | DiscoveryViewModelTest.followingDuringASourceRequestExcludesItsEventualResult
covered | group_discovery_test › closing discovery retires pending sources | DiscoveryViewModel (viewModelScope) | DiscoveryViewModelTest.closingDiscoveryRetiresPendingSources
covered | group_discovery_test › a finished source paints before a stalled one times out, and survives it | DiscoveryViewModel per-source publish, Discovery.SOURCE_MILLIS | view model; time share in DiscoveryTest.eachListAndEachPageMayTakeItsShareOfAMinute
covered | group_discovery_test › reporting a setup failure retires an older pending request | DiscoveryViewModel generation/absorb | DiscoveryViewModelTest.aLateSourceCannotReplaceANewerScan (the same run check)

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
covered | group_ungrouped_sorter_test › only placing writes, and undo removes what its choice added | ui/subscriptions/SortViewModels.kt (UngroupedWrites) | UngroupedSorterViewModelTest.onlyPlacingWritesAndUndoTakesItBack
covered | group_ungrouped_sorter_test › a failed save throws and keeps the account | SortViewModels.kt (save) | UngroupedSorterViewModelTest.aFailedSaveLeavesTheAccountOnShowAndSaysSo
covered | group_ungrouped_sorter_test › a tap while a choice is saved is ignored | SortViewModels.kt (saving guard) | UngroupedSorterViewModelTest.aTapWhileAChoiceIsSavedIsIgnored
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
untested | home_reading_controls_test › real viewport retains controls for keyboard | Compose (Home viewport) | the keyboard inset is passed as protected; the store's protected path is tested
untested | home_reading_controls_test › real viewport retains controls for accessibility | Compose (Home viewport) | the screen reader is passed as protected; the store's protected path is tested
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
different → fixed | video_controller_pool_test › abandoned startup cannot allocate more decoders and late completion is disposed | ui/media PlayerTurns, InlineTurns | native keeps one player per feed (and a tile's own), released at once; there is no capped decoder pool to abandon
different → fixed | video_controller_pool_test › a late release cannot release a replacement with the same key | ui/media InlineTurns | one holder per turns object, replaced before release; no keyed server-side pool
covered | video_controller_pool_test › reattaching to a cached key shares one acquisition | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
covered | video_controller_pool_test › a player keyed to its widget alone goes with the widget | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
covered | video_controller_pool_test › a player keyed to its post stays cached for the next screen | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
different → fixed | video_controller_pool_test › play no more at once than the pool can host | ui/media PlayerTurns | the feed shares one player; other tiles play only while they hold it
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
different → fixed | video_memory_pressure_test › idle creation finishing after a memory warning is disposed once | ui/media PlayerTurns | no idle player pool exists; a player is made for the holder and released with it
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
different → fixed | video_playback_policy_test › a cached player is not reattached by a tile that is off screen | ui/media PlayerTurns | a tile asks when it is on screen; there is no cache to reattach from
covered | video_playback_policy_test › a GIF that has been let go stays let go until it is back on screen | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › a video still showing its play button never allocates, visible or not | VideoSettingsTest.aPlainVideoWaitsForATapAndAnUnseenGifHoldsNoPlayer
covered | video_playback_policy_test › fullscreen never reclaims, even when the tile looks fully hidden | PlayerTurnsTest.fullScreenKeepsThePlayerThroughADropAndARelease
different → fixed | video_playback_policy_test › a truly off-screen tile may reclaim | ui/media InlineTurns | the holder is whoever asked last; visibility is the caller's cue
covered | video_playback_policy_test › a half-visible tile, or one still marked visible elsewhere, stays | VideoSettingsTest.aTileIsSeenWhileHalfOfItIsOnScreen
covered | video_playback_policy_test › reattaching to a cached player is always allowed | PlayerTurnsTest.aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo
different → fixed | video_playback_policy_test › a new player is refused when the pool is full and nothing is free | ui/media PlayerTurns | no pool cap: one player per feed, so nothing is refused
different → fixed | video_playback_policy_test › a new player is allowed when an unused entry can be evicted | ui/media PlayerTurns | no eviction: a tile's own player is released at once
different → fixed | video_playback_policy_test › acquire at capacity must drop unused entries to free a slot | ui/media PlayerTurns | one player per feed; there is no capacity to acquire at
different → fixed | video_playback_policy_test › release leaves unused entries cached up to maxSize | ui/media PlayerTurns | release lets the player go; it is not cached

## test/video_quality_continuity_test.dart
covered | video_quality_continuity_test › quality switch preserves position, volume, rate and active play intent | PlaybackTest.aRestoredSourceIsSoughtOnceItKnowsItsLength
covered | video_quality_continuity_test › paused video remains paused after a quality switch | PlaybackTest.pause
covered | video_quality_continuity_test › if native start is ignored seek waits until new duration arrives | PlaybackTest.aRestoredSourceIsSoughtOnceItKnowsItsLength
covered | video_quality_continuity_test › unacknowledged seek falls back once to a playable start | PlaybackTest.aFailedReplacedOrStuckSourceIsNotRestored
covered | video_quality_continuity_test › missing duration times out without seeking an unready decoder | ui/media PlaybackRestore.restore | PlaybackTest.aSourceThatNeverSaysItsLengthTimesOutWithoutSeeking
covered | video_quality_continuity_test › rapid quality choices serialize native opens and latest choice wins | ui/media InlineTurns, PlaybackRestore | PlayerTurnsTest.theLatestAskWins; PlaybackTest.aSourceReplacedWhileWaitingForItIsNotSeeked
covered | video_quality_continuity_test › new choice cancels a duration wait rather than waiting for timeout | ui/media PlaybackRestore.isCurrent | PlaybackTest.aSourceReplacedWhileWaitingForItIsNotSeeked
covered | video_quality_continuity_test › disposal cancels restoration before any seek or playback command | PlaybackTest.dispose
different → fixed | video_quality_continuity_test › hiding the video during restoration suppresses delayed automatic play | ui/media PlayerTurns.pause/resume | pausing clears the resume and sets active false; returning is the only place that plays
covered | video_quality_continuity_test › mute and speed changes made while loading take precedence | VideoSettingsTest.aVideoPlaysAsTheMediaSettingsSay
covered | video_quality_continuity_test › failed variant restores the original source and remains retryable | MpvVideoRulesTest.aFailedChangeOfQualityGoesBackToTheVariantBefore
covered | video_quality_continuity_test › completed videos start paused and invalid rate or volume is not applied | PlaybackTest.theResumePlaceStopsShortOfTheEnd
covered | video_quality_continuity_test › resume target stays within a shorter variant and does not seek EOF | PlaybackTest.theResumePlaceStopsShortOfTheEnd
covered | video_quality_continuity_test › stalled native seek fails promptly without overlapping a fallback open | ui/media PlaybackRestore.restore (seekTimeout) | PlaybackTest.aStalledSeekFailsPromptly
covered | video_quality_continuity_test › stalled native open fails promptly and disposal waits for its late completion | ui/media PlaybackRestore.restore (readyTimeout/isCurrent) | PlaybackTest.aSourceThatNeverSaysItsLengthTimesOutWithoutSeeking, aReplacedSourceIsNotSeekedEvenWhenItBecomesReady
covered | video_quality_continuity_test › latest choice unblocks when an uncancellable open is still stalled | ui/media PlaybackRestore.isCurrent | PlaybackTest.aSourceReplacedWhileWaitingForItIsNotSeeked
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
different → fixed | profile_loading_recovery_test › stalled profile cache does not prevent the network load | ui/profile ProfileViewModel | native keeps the profile in files, not Flutter platform channels: a read never waits on a separate cache process, so the network load is never held by it
missing → implemented | profile_loading_recovery_test › timed out profile recovers and ignores the old response | ui/profile ProfileViewModel.load (loadRun) | a late answer to an older load could replace a newer one; now only the current run writes. ProfileViewModelTest.aProfileThatAnswersLateCannotReplaceANewerOne
covered | profile_loading_recovery_test › closing a stalled profile permits another profile to load | ui/profile ProfileViewModel (viewModelScope) | ProfileViewModelTest.closingAStalledProfilePermitsAnotherToLoad
different → fixed | profile_loading_recovery_test › stalled cache open and write cannot hold successful posts | ui/profile ProfileViewModel | the cache is file-backed and synchronous; there is no channel open/write to stall a successful page
different → fixed | profile_loading_recovery_test › a stalled stale-cache read preserves the original network error | ui/profile ProfileViewModel | file reads cannot stall separately from the network; firstOrNext keeps the network error when there is no stale page
covered | profile_loading_recovery_test › cached page remains available when the network fails | ui/profile ProfileViewModel.firstOrNext (readStale) | ProfileViewModelTest.aTabReadThatFailsFallsBackToThePageKeptOnTheDevice, aProfileKeptOnTheDeviceShowsWhenTheNetworkFails

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
different → fixed | profile_view_store_test › back-to-top state only reflects the latest threshold result | ui/profile ProfileScreen (derivedStateOf) | Compose reads the latest scroll threshold directly; there is no async store to answer late

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

## test/parser_resilience_test.dart
covered | parser_resilience_test › reads the identity fields X has already moved out of legacy | XPostAndUserFieldsTest
covered | parser_resilience_test › reads a profile that has finished the legacy migration | XParserTest
covered | parser_resilience_test › a response with neither legacy nor core is empty, not an exception | XParserTest
covered | parser_resilience_test › survives a null legacy | XParserTest
covered | parser_resilience_test › survives losing any single top-level field | XParserTest
covered | parser_resilience_test › survives losing or nulling any single top-level field | XParserTest
covered | parser_resilience_test › a container of the wrong shape reads as absent | XParserTest
covered | parser_resilience_test › an author whose legacy is gone still yields a tweet | XPostAndUserFieldsTest
covered | parser_resilience_test › an unknown __typename is not a crash | XParserTest
covered | parser_resilience_test › parses the live entries | XTimelineParserTest
covered | parser_resilience_test › skips entries whose content X has reshaped | XTimelineParserTest
covered | parser_resilience_test › an entry with no content at all is skipped, not fatal | XTimelineParserTest
covered | parser_resilience_test › reads a legacy cursor-bottom value | XTimelineParserTest
covered | parser_resilience_test › reads an sq-C operation cursor | XTimelineParserTest
covered | parser_resilience_test › reads a replaceEntry cursor without throwing on sibling junk | XTimelineParserTest
covered | parser_resilience_test › null entryId and missing content yield null, not a crash | XTimelineParserTest
covered | parser_resilience_test › a non-string cursor value is ignored | XTimelineParserTest
covered | parser_resilience_test › keeps good tweets when a sibling entry loses its result | XTimelineParserTest
covered | parser_resilience_test › parses a minimal timeline wrapper | XTimelineParserTest
covered | parser_resilience_test › missing timeline / instructions is empty, not fatal | XTimelineParserTest
covered | parser_resilience_test › one entry with null content does not wipe the page | XTimelineParserTest
covered | parser_resilience_test › null sortIndex and missing entryId are tolerated | XTimelineParserTest
covered | parser_resilience_test › missing home_timeline_urt is empty, not a crash | XTimelineParserTest

## test/transaction_key_test.dart
covered | transaction_key_test › a key that has never been derived is not usable | XTransportTest (key lifetime)
covered | transaction_key_test › a fresh key is usable | XTransportTest
covered | transaction_key_test › a key older than its lifetime is re-derived | XTransportTest
covered | transaction_key_test › the shipped lifetime is well short of a day | TransactionSignerTest; XTransportTest
covered | transaction_key_test › a stalled setup expires and retry recovers without restarting | XTransportTest
covered | transaction_key_test › ${failure.runtimeType} keeps its type and the next concurrent requests recover immediately | XTransportTest
covered | transaction_key_test › synchronous setup failures also respect the retry cooldown | XTransportTest
covered | transaction_key_test › a successful derivation is reused rather than repeated per request | XTransportTest
covered | transaction_key_test › a failed derivation is not cached, so a later request retries | XTransportTest
covered | transaction_key_test › a persistent failure is retried on a cooldown | XTransportTest
covered | transaction_key_test › the caller still sees the failure while the cooldown suppresses retries | XTransportTest
covered | transaction_key_test › a retry happens once the cooldown has elapsed | XTransportTest
covered | transaction_key_test › a success clears the cooldown | XTransportTest
covered | transaction_key_test › the key is re-derived once its lifetime has passed | XTransportTest
covered | transaction_key_test › concurrent first requests share one derivation | XTransportTest
covered | transaction_key_test › anonymous failure cooldown does not block an authenticated context | XTransportTest
covered | transaction_key_test › cookie contexts isolate failures and reuse keys with any Cookie header casing | XTransportTest
covered | transaction_key_test › changing the session cookie derives a fresh key | XTransportTest
covered | transaction_key_test › many session contexts evict old keys without evicting the most recently used one | XTransportTest
covered | transaction_key_test › no header is asked for when there is no uri | XTransportTest
covered | transaction_key_test › reset clears diagnostics and a late previous initialization cannot restore its error | XTransportTest

## test/endpoints_test.dart
covered | endpoints_test › every shipped endpoint is keyed by its own name | XEndpointsTest.everyShippedEndpointIsKeyedByItsOwnNameAndHasAnIdShapedId
covered | endpoints_test › every shipped query id is query-id shaped | XEndpointsTest.everyShippedEndpointIsKeyedByItsOwnNameAndHasAnIdShapedId
covered | endpoints_test › builds the URI a call site used to hardcode | XEndpointsTest.aUrlIsTheOperationOnItsHostWithTheParameters
covered | endpoints_test › the Posts tab is UserOriginalsTimeline and UserTweets is gone | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | endpoints_test › reposts come from UserRepostsTimeline, under a key of their own | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | endpoints_test › the profile Media tab stays on UserMedia | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | endpoints_test › AudioSpaceById is a shipped GraphQL operation | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | endpoints_test › the two SearchTimeline call sites keep distinct hosts and registry keys | XEndpointsTest.theTwoSearchTimelineReadsKeepTheirOwnHostsAndKeys
covered | endpoints_test › an override replaces the query id but keeps operation and host | XEndpointsTest.anOverrideReplacesTheIdButKeepsOperationAndHost
covered | endpoints_test › unknown endpoints and malformed ids are rejected | XEndpointsTest.unknownEndpointsAndMalformedIdsAreRejected
covered | endpoints_test › a path separator in an override can never escape the operation | XEndpointsTest.aPathInAnOverrideNeverEscapesTheOperation
covered | endpoints_test › an unknown endpoint name is a programming error, not a silent 404 | XEndpointsTest.anUnknownEndpointNameIsAMistakeNotASilent404
covered | endpoints_test › reads the published shape | PublishedDocumentTest
covered | endpoints_test › tolerates documents that are not the shape we expect | PublishedDocumentTest
covered | endpoints_test › the published endpoints.json parses and names only known endpoints | PublishedDocumentTest
covered | endpoints_test › the published endpoints.json names the ids this build ships | PublishedDocumentTest

## test/interleaved_items_test.dart
covered | interleaved_items_test › takes the newest post in the chain, not the first | ui/feed FeedMix.timeOf | FeedMixTest.takesTheNewestPostInTheChainNotTheFirst
covered | interleaved_items_test › a chain with no dates has none | FeedMix.timeOf | FeedMixTest.aChainWithNoDatesHasNone
covered | interleaved_items_test › an item newer than everything goes above the first chain | FeedMix.place | FeedMixTest.anItemNewerThanEverythingGoesAboveTheFirstChain
covered | interleaved_items_test › an item lands directly above the first chain older than it | FeedMix.place | FeedMixTest.anItemLandsDirectlyAboveTheFirstChainOlderThanIt
covered | interleaved_items_test › an item older than every loaded chain waits at the end | FeedMix.place | FeedMixTest.anItemOlderThanEveryLoadedChainWaitsAtTheEnd
covered | interleaved_items_test › several items keep newest-first order within a bucket | FeedMix.place | FeedMixTest.severalItemsKeepNewestFirstWithinABucket
covered | interleaved_items_test › a chain with no date is passed over rather than guessed at | FeedMix.place | FeedMixTest.aChainWithNoDateIsPassedOverRatherThanGuessedAt
covered | interleaved_items_test › no items means empty buckets, one per chain plus the tail | FeedMix.place | FeedMixTest.noItemsMeansEmptyBucketsOnePerChainPlusTheTail
covered | interleaved_items_test › items with no chains at all still come back | FeedMix.place | FeedMixTest.itemsWithNoChainsAtAllStillComeBack
covered | interleaved_items_test › a group of nothing but plugin members shows only those posts | GroupFeedTest (plugin-only group)
covered | interleaved_items_test › one X post is enough to use the paged list | GroupFeedTest
covered | interleaved_items_test › an empty feed is not a plugin-only one | GroupFeedTest
covered | interleaved_items_test › the first page still loading is neither | GroupFeedTest
covered | interleaved_items_test › plugin posts stay when X has not answered | GroupFeedTest
covered | interleaved_items_test › plugin posts stay when X answered with nothing | GroupFeedTest
covered | interleaved_items_test › an empty plugin slot is not a reason to skip the X error | GroupFeedTest
covered | interleaved_items_test › X posts already on screen keep the mixed list | GroupFeedTest
covered | interleaved_items_test › an empty answer does not dirty an already-empty slot | GroupFeedTest
covered | interleaved_items_test › posts land in the slot | GroupFeedTest
covered | interleaved_items_test › an empty answer clears a filled slot | GroupFeedTest

## test/conversation_sort_test.dart
covered | conversation_sort_test › each reply order maps to its TweetDetail rankingMode | ConversationRulesTest.onlyXsOwnOrderIsKeptAndNeverACountWithNothingUnderIt
covered | conversation_sort_test › TweetDetail variables carry the ranking mode and cursor | XEndpointsTest.aUrlIsTheOperationOnItsHostWithTheParameters
covered | conversation_sort_test › TweetDetail keeps relevance when no order is given | ConversationRulesTest.onlyXsOwnOrderIsKeptAndNeverACountWithNothingUnderIt
covered | conversation_sort_test › each quote order maps to its SearchTimeline product | XEndpointsTest.theTwoSearchTimelineReadsKeepTheirOwnHostsAndKeys
covered | conversation_sort_test › an order the network lacks falls back to its default | ConversationRulesTest.onlyXsOwnOrderIsKeptAndNeverACountWithNothingUnderIt
covered | conversation_sort_test › the network default keeps the order it sent | ConversationRulesTest.onlyXsOwnOrderIsKeptAndNeverACountWithNothingUnderIt
covered | conversation_sort_test › recent and oldest sort by date, undated last either way | ConversationRulesTest.repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue
covered | conversation_sort_test › most liked orders by likes and keeps ties in network order | ConversationRulesTest.repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue
covered | conversation_sort_test › oldest is the /context order | ConversationRulesTest.repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue
covered | conversation_sort_test › recent orders siblings at every level and keeps sub-threads | ConversationRulesTest.repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue
covered | conversation_sort_test › most liked keeps ties in server order | ConversationRulesTest.repliesNestUnderWhatTheyAnswerOldestFirstAndPastTwoLevelsContinue
covered | conversation_sort_test › the thread store starts from the session choice it can honour | ConversationRulesTest
covered | conversation_sort_test › recent, oldest and most liked sort what is loaded | ConversationRulesTest
covered | conversation_sort_test › the activity sort offers every device order | PostRulesTest.theSortsAskXForWhatItRanksItselfAndRepostersSortByFollowersOnlyWithCounts
covered | conversation_sort_test › a single quote needs no control | Compose (quote sort) | the control is Compose-only; sorting is ConversationRulesTest
covered | conversation_sort_test › recent keeps the order the network returned | ConversationRulesTest.onlyXsOwnOrderIsKept...
covered | conversation_sort_test › most followers sorts on device, unknown counts last | PostRulesTest.theSortsAskXForWhatItRanksItselfAndRepostersSortByFollowersOnlyWithCounts
covered | conversation_sort_test › follower order is offered only when counts are present | PostRulesTest.theSortsAskXForWhatItRanksItself...
covered | conversation_sort_test › the activity sort hides its control without follower counts | PostRulesTest.theSortsAskXForWhatItRanksItself...
covered | conversation_sort_test › Retweeters users carry the follower count when X sends one | XParserTest (retweeters)
covered | conversation_sort_test › reordering paged items keeps the page sizes | ConversationRulesTest
covered | conversation_sort_test › the store remembers each choice independently | ConversationRulesTest

## test/reply_filter_test.dart
covered | reply_filter_test › a plain post is not a reply | PostRulesTest.replyingToShowsUnderTheHeaderButNotInAQuoteNorMidThread
covered | reply_filter_test › answering another account is a reply | PostRulesTest.replyingToShowsUnderTheHeaderButNotInAQuoteNorMidThread
covered | reply_filter_test › answering yourself is a thread, not a reply | PostRulesTest.replyingToShowsUnderTheHeaderButNotInAQuoteNorMidThread
covered | reply_filter_test › falls back to screen names when ids are missing | XPostAndUserFieldsTest (reply target)
covered | reply_filter_test › an unidentifiable reply target counts as a reply | PostRulesTest.replyingToShowsUnderTheHeader...
covered | reply_filter_test › null is not a reply | PostRulesTest.replyingToShowsUnderTheHeader...
covered | reply_filter_test › keeps everything when nobody is filtered | PostRulesTest.replyingToShowsUnderTheHeader...
covered | reply_filter_test › drops that user's replies to others | PostRulesTest
covered | reply_filter_test › matches the screen name case-insensitively | PostRulesTest
covered | reply_filter_test › keeps their own threads | PostRulesTest
covered | reply_filter_test › leaves other people's replies alone | PostRulesTest
covered | reply_filter_test › is independent of the retweet filter | PostRulesTest
covered | reply_filter_test › the two filters are stored separately | SettingsTest (feed filters)

## test/reply_sort_bar_test.dart
untested | reply_sort_bar_test › the reply order sits in its own full-width row, closed by a hairline | Compose (reply sort bar) | Compose only

## test/webdav_sync_test.dart
covered | webdav_sync_test › is incomplete until every field is filled | WebDavSyncTest (isConfigured)
covered | webdav_sync_test › rejects anything that is not https | WebDavSyncTest (insecure URL)
covered | webdav_sync_test › sends credentials as HTTP basic auth | WebDavSyncTest
covered | webdav_sync_test › first upload uses If-None-Match and records the returned validator | WebDavSyncTest
covered | webdav_sync_test › missing parent collection retains the conditional header on retry | WebDavSyncTest
covered | webdav_sync_test › an unseen remote version is never overwritten | WebDavSyncTest
covered | webdav_sync_test › another device changing the file during upload causes 412 after archiving | WebDavSyncTest
covered | webdav_sync_test › successful replacement preserves a version that can be restored | WebDavSyncTest
covered | webdav_sync_test › weak or missing validators refuse to overwrite remote backups | WebDavSyncTest
covered | webdav_sync_test › archive failure prevents replacing the current backup | WebDavSyncTest
covered | webdav_sync_test › bad credentials and network failures remain distinct | WebDavSyncTest
covered | webdav_sync_test › no request is made for missing settings or insecure URLs | WebDavSyncTest
covered | webdav_sync_test › history cannot redirect authenticated reads to another server or path | WebDavSyncTest
covered | webdav_sync_test › returns the stored document | WebDavSyncTest
covered | webdav_sync_test › a first sync with nothing uploaded yet reads as notFound | WebDavSyncTest
covered | webdav_sync_test › a server failure carries the status for the error message | WebDavSyncTest
covered | webdav_sync_test › the exported settings never carry the sync password | WebDavSyncTest
covered | webdav_sync_test › status mapping keeps 2xx apart from the failure classes | WebDavSyncTest

## test/background_download_test.dart
covered | background_download_test › a fresh install saves in the background | DownloadDestinationTest (default)
covered | background_download_test › resolves each mode, and a directory without a folder still asks | DownloadDestinationTest
covered | background_download_test › moves the inherited ask default once and keeps deliberate choices | DownloadDestinationTest
covered | background_download_test › pictures, videos and everything else land in their own XTA folder | DownloadDestinationTest
covered | background_download_test › the copy carries the staged path, type and folder, never the bytes | DownloadTransferTest; PlatformFiles
covered | background_download_test › only a finished background save reports its folder | DownloadRulesTest.downloadNotice
covered | background_download_test › a background download is copied into its shared folder without a dialog | DownloadDestinationTest; DownloadTransferTest
covered | background_download_test › Android 9 and older fall back to the save dialog | DownloadDestinationTest (platform)
covered | background_download_test › an explicit ask still opens the dialog | DownloadDestinationTest
covered | background_download_test › a chosen folder still saves through its document tree | DownloadDestinationTest
untested | background_download_test › cancelling stops the native copy by operation id | ui/downloads PlatformFiles (Android) | platform boundary
untested | background_download_test › an item finished after the cancel is deleted again | PlatformFiles | platform boundary
untested | background_download_test › a failed copy surfaces as an error | PlatformFiles | platform boundary
covered | background_download_test › background is recorded and survives the history file, unless a folder is named | DownloadHistoryTest
covered | background_download_test › a Pixiv batch in background mode never opens the folder picker | PluginBatchesTest
untested | background_download_test › settings offers background saving and explains where files go | Compose (download settings) | Compose only

## test/broadcasts_test.dart
covered | broadcasts_test › a broadcasts URL marks the tweet | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | broadcasts_test › the broadcast card name marks the tweet | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | broadcasts_test › a broadcast_url on the card is enough, even without the name | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | broadcasts_test › the link in the post body counts when entities omit it | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | broadcasts_test › a video whose expanded_url is the broadcast link is a broadcast | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | broadcasts_test › GraphQL list-shaped card bindings still yield the id | XCardsTest.theCardsMediaKeyAndShapeAreKeptForThePlayer
covered | broadcasts_test › an ordinary post is not a broadcast | XCardsTest.aLiveLinkTellsASpaceFromABroadcast
covered | broadcasts_test › video media is detected separately from the link | XCardsTest.theCardsMediaKeyAndShapeAreKeptForThePlayer
covered | broadcasts_test › a spaces URL marks the tweet as a Space | XCardsTest.aSpaceIsFoundByItsLinkOrItsCard
covered | broadcasts_test › the audiospace card name and id mark the tweet | XCardsTest.aSpaceIsFoundByItsLinkOrItsCard
covered | broadcasts_test › broadcastMediaKeyOf reads the card binding | XCardsTest.theCardsMediaKeyAndShapeAreKeptForThePlayer
covered | broadcasts_test › playbackUrlFromBroadcastStatus reads HLS and ignores junk | LiveRoomsTest
covered | broadcasts_test › mediaKeyFromAudioSpace and broadcasts/show parsers | LiveRoomsTest
covered | broadcasts_test › LivePlayRequest.fromUrl distinguishes Spaces from broadcasts | LiveRoomsTest
covered | broadcasts_test › LivePlayRequest.fromTweet carries the card media_key | LiveRoomsTest

## test/timeline_cache_test.dart
n/a | timeline_cache_test › migration 38 creates the cache table | store/offline TimelineCacheTest.go | XTA's SQLite migration; native keeps one JSON cache, no SQLite
covered | timeline_cache_test › a written thread reads back | TimelineCacheTest
covered | timeline_cache_test › an entry older than maxAge is a miss | TimelineCacheTest
covered | timeline_cache_test › a stale entry is still readable through readStale | TimelineCacheTest
covered | timeline_cache_test › writing the same key twice keeps one row, the newer one | TimelineCacheTest
covered | timeline_cache_test › an empty result never overwrites what is cached | TimelineCacheTest
covered | timeline_cache_test › the cursor survives the round trip so pagination continues | TimelineCacheTest
covered | timeline_cache_test › the show-more cursor survives the round trip | TimelineCacheTest
covered | timeline_cache_test › remove drops a key so the next read misses | TimelineCacheTest
covered | timeline_cache_test › a missing key is a miss, not an error | TimelineCacheTest
covered | timeline_cache_test › an entry that no longer decodes is a miss, not a crash | TimelineCacheTest
covered | timeline_cache_test › threads and profile variants never share a key | TimelineCacheTest
covered | timeline_cache_test › clear empties the table | TimelineCacheTest

## test/media_grid_design_test.dart
covered | media_grid_design_test › the three media layouts remain intentionally distinct | MediaGridRulesTest
covered | media_grid_design_test › browse thumbnails bound invalid and extreme source ratios | MediaGridRulesTest; MediaRulesTest
untested | media_grid_design_test › an icon-only media badge retains a semantic label | Compose semantics (media badge) | Compose only

## test/media_page_test.dart
covered | media_page_test › keeps the media of the last page instead of discarding it | ProfileRulesTest.theMediaGridHasATilePerPictureAndOneForABroadcastWhichKeepsItsVideoThumb
covered | media_page_test › carries the cursor on while the timeline keeps moving | ProfileRulesTest
covered | media_page_test › a missing cursor is the end | ProfileRulesTest
covered | media_page_test › reads past pages with no media rather than calling it the end | ProfileRulesTest
covered | media_page_test › gives up after the look-ahead so a dry feed still ends | ProfileRulesTest
covered | media_page_test › stops at the end of the timeline rather than looking past it | ProfileRulesTest
covered | media_page_test › all keeps everything | ProfileRulesTest; MediaRulesTest
covered | media_page_test › photos keeps only stills | MediaRulesTest
covered | media_page_test › videos keeps GIFs too, since X serves them as video | MediaRulesTest
covered | media_page_test › Livestreams reads posts, not UserMedia | ProfileRulesTest; PostRulesTest
covered | media_page_test › Livestreams looks further because broadcasts are sparse | ProfileRulesTest
covered | media_page_test › a photo post that is a broadcast is one live tile, not a still | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText; ProfileRulesTest
covered | media_page_test › a video with a broadcasts link is a broadcast, not a video | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | media_page_test › UserMedia video whose expanded_url is the broadcast is not a clip | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | media_page_test › a card-only broadcast still gets a tile | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | media_page_test › a spaces link on a video is a live tile, not a clip | XCardsTest.aSpaceIsFoundByItsLinkOrItsCard
covered | media_page_test › a card-only Space still gets a tile | XCardsTest.aSpaceIsFoundByItsLinkOrItsCard
covered | media_page_test › a VOD with variants uses the lightbox; a card-only live still plays in-app | LiveRoomsTest; MediaRulesTest
covered | media_page_test › a first page that is only a cursor is followed, not shown as empty | ProfileRulesTest
covered | media_page_test › a feed that is cursors all the way down still ends at the bound | ProfileRulesTest

## test/media_viewer_counter_test.dart
untested | media_viewer_counter_test › swiping to the next photo rolls the counter to its number | Compose (viewer counter) | Compose only

## test/link_browser_screen_test.dart
covered | link_browser_screen_test › the top bar names the domain, the network and the padlock | LinkOpeningTest; NetworkLinksTest
covered | link_browser_screen_test › tracking is stripped before the page loads | XLinksTest (tracking removal)
covered | link_browser_screen_test › the bottom bar carries the post: counts, share and the mark | Compose (link browser)
covered | link_browser_screen_test › the bar reads as one "back to post" control | Compose semantics (link browser)
untested | link_browser_screen_test › zen mode hides the counts here too | Compose (link browser) | Compose only
untested | link_browser_screen_test › tapping the bar closes the browser and opens the post | Compose (link browser) | Compose only
untested | link_browser_screen_test › on the post's own screen the bar just goes back to it | Compose (link browser) | Compose only
untested | link_browser_screen_test › back steps through the page before closing the browser | Compose (link browser) | Compose only
untested | link_browser_screen_test › the close button leaves at once | Compose (link browser) | Compose only
untested | link_browser_screen_test › the menu offers the browser, copy, share and reload | Compose (link browser) | Compose only
untested | link_browser_screen_test › without a post there is no bar; the title names the page | Compose (link browser) | Compose only
untested | link_browser_screen_test › chrome fits 320dp at double text size in light and dark | Compose (link browser) | Compose only
covered | link_browser_screen_test › in the app: the browser with the post bar | LinkOpeningTest (in-app browser)
covered | link_browser_screen_test › a chosen browser: the link leaves the app | LinkOpenerTest (chosen browser)
covered | link_browser_screen_test › a link an XTA screen reads never reaches a browser | LinkOpeningTest; NetworkLinksTest

## test/link_preview_card_test.dart
covered | link_preview_card_test › an article is compact, with or without a picture | XCardsTest.aPreviewKeepsXsNameForItsLayout
covered | link_preview_card_test › a video page or a photo keeps the large picture | XCardsTest.aLargePreviewKeepsItsPictureAtEachSizeTheImageQualityPicks
covered | link_preview_card_test › nothing to show large means compact | XCardsTest.aPreviewKeepsXsNameForItsLayout
covered | link_preview_card_test › linkDomain drops www and case | XLinksTest (domain)
covered | link_preview_card_test › with an image: the tile shows it beside domain and title | LinkPreviewsTest
covered | link_preview_card_test › without an image: a link glyph holds the tile | LinkPreviewsTest
covered | link_preview_card_test › a missing title falls back to the address | LinkPreviewsTest
untested | link_preview_card_test › screen readers hear one link: domain and title | Compose semantics (card) | Compose only
untested | link_preview_card_test › a long title fits a 320dp phone at double text size in true black | Compose (card) | Compose only
untested | link_preview_card_test › the large layout keeps the picture across the card | Compose (card) | Compose only
covered | link_preview_card_test › an article in a $name card is the compact row | XCardsTest.aPreviewKeepsXsNameForItsLayout
covered | link_preview_card_test › a Mastodon article link is the shared compact row | plugins/mastodon MastodonCard.kt | shared card rules; Compose-only layout
covered | link_preview_card_test › with a browser chosen in settings, the link leaves the app | LinkOpenerTest (chosen browser)

## test/card_page_route_test.dart
covered | card_page_route_test › a post slides in from the right over the page it was opened from | Compose (route motion) | the motion is Compose-only
covered | card_page_route_test › the back button slides it out past the right edge | SwipeBackMathTest.itComesInFromTheFarEdgeAndGoesOutATenthPastIt
covered | card_page_route_test › the card follows the finger, the page behind showing and scaled under it | CardDragsTest; SwipeBackMathTest.thePageBehindShowsAsTheCardLeaves...
covered | card_page_route_test › let go past 30 % of the width, it flies off and the post is gone | SwipeBackMathTest.lettingGoStandingStillLeavesPastThirtyPercentOfTheWidth
covered | card_page_route_test › let go short of the line, it drops back and the post stays | SwipeBackMathTest.theSpringDropsTheCardBackWithoutOvershootingWhenLetGoStill
covered | card_page_route_test › a quick throw leaves from a short drag | SwipeBackMathTest.aThrowAwayFromRestLeavesAfterSixteenDp
covered | card_page_route_test › a flick back towards rest keeps the post, even past the line | SwipeBackMathTest.aFlickBackTowardsRestNeverLeavesASlowDriftBackStillDoes
covered | card_page_route_test › grabbed above the middle it turns clockwise, below the other way | SwipeBackMathTest.itTurnsClockwiseGrabbedAboveTheMiddleAndTheOtherWayBelow
covered | card_page_route_test › with tilt off it does not turn | SwipeBackMathTest.theSwingFollowsTheSpeedUpToFourDegreesAndOnlyWithTilt
covered | card_page_route_test › dragged left of rest it gives only a third | SwipeBackMathTest.pastTheStartingEdgeADragGivesOnlyAThird
covered | card_page_route_test › caught while it drops back, it is held again | SwipeBackMathTest.aCaughtCardLiftsOnFromWhereItIs
covered | card_page_route_test › a back press while it drops back throws it from where it is | SwipeBackMathTest.theSpringCarriesTheSpeedItWasLetGoAt
covered | card_page_route_test › a post opened from a post slides over it, the first shrinking behind | Compose (route motion) | Compose only
covered | card_page_route_test › an up or down drag scrolls the post | CardDragsTest.aDragToTheLeftOrUpAndDownIsLeftToThePage
covered | card_page_route_test › a drag to the left is left to the page | CardDragsTest.aDragToTheLeftOrUpAndDownIsLeftToThePage
untested | card_page_route_test › a row that scrolls sideways keeps its drags | Compose (gesture) | Compose only
untested | card_page_route_test › a tap still presses a button, and a touch lifts nothing | Compose (gesture) | Compose only
untested | card_page_route_test › a drag that started on a button does not press it | Compose (gesture) | Compose only
untested | card_page_route_test › a post that may not be left cannot be dragged away | Compose (gesture) | Compose only
covered | card_page_route_test › a drag away from the edge does nothing, one from the edge works | CardDragsTest.fromAnywhereADragStartsAnywhereOffOnlyAtTheEdge
covered | card_page_route_test › moves the card with the finger and throws it on commit | CardDragsTest
covered | card_page_route_test › drops the card back on cancel | SwipeBackMathTest.theSpringDropsTheCardBack...
covered | card_page_route_test › from the right edge the card goes left | CardDragsTest.inARightToLeftLanguageItIsDraggedToTheLeft
untested | card_page_route_test › a post that may not be left keeps the gesture away | Compose (gesture) | Compose only
untested | card_page_route_test › the post appears and leaves at once, and still follows a drag, flat | Compose (reduced motion) | Compose only
untested | card_page_route_test › let go short of the line, it is back at once | Compose (reduced motion) | Compose only
untested | card_page_route_test › both switches sit in Accessibility, on, and turn off | Compose (settings) | Compose only
covered | card_page_route_test › the post is dragged to the left, and a drag to the right is left alone | CardDragsTest.inARightToLeftLanguageItIsDraggedToTheLeft

## test/swipe_back_card_test.dart
covered | swipe_back_card_test › shrinks by 6 % lifted, and 3 % more across a full width | SwipeBackMathTest.aCardShrinksBySixPercentLiftedAndThreeMoreAcrossAFullWidth
covered | swipe_back_card_test › rounds its corners to 24 at full lift, in whole pixels | SwipeBackMathTest.itsCornersRoundToTwentyFourAtFullLiftInWholeDp
untested | swipe_back_card_test › at rest it is drawn exactly where it is | Compose (swipe-back card) | Compose only
covered | swipe_back_card_test › the point it turns around follows the offset, whatever the turn and lift | SwipeBackMathTest
covered | swipe_back_card_test › turning clockwise brings a point right of the origin down | SwipeBackMathTest.itTurnsClockwiseGrabbedAboveTheMiddleAndTheOtherWayBelow
covered | swipe_back_card_test › mirrored, it leaves to the left around the mirrored point | SwipeBackMathTest; CardDragsTest.inARightToLeftLanguageItIsDraggedToTheLeft
covered | swipe_back_card_test › comes in from the far edge and goes out a tenth past it | SwipeBackMathTest.itComesInFromTheFarEdgeAndGoesOutATenthPastIt
covered | swipe_back_card_test › is lifted while far and flat within 40 % of a width | SwipeBackMathTest.slidingItIsLiftedWhileFarAndFlatWithinFortyPercentOfAWidth
covered | swipe_back_card_test › rises in 140 ms, easing out | SwipeBackMathTest.theLiftRisesIn140MsEasingOut
covered | swipe_back_card_test › a caught card lifts on from where it is | SwipeBackMathTest.aCaughtCardLiftsOnFromWhereItIs
covered | swipe_back_card_test › dropping back, it flattens by distance and is flat within 224 ms | SwipeBackMathTest.droppingBackItFlattensByDistanceAndIsFlatWithin224Ms
covered | swipe_back_card_test › is clockwise grabbed above the middle and the other way below | SwipeBackMathTest.itTurnsClockwiseGrabbedAboveTheMiddleAndTheOtherWayBelow
covered | swipe_back_card_test › reaches 7° across a full width, plus swing and spin | SwipeBackMathTest.itReachesSevenDegreesAcrossAFullWidthPlusSwingAndSpin
covered | swipe_back_card_test › the swing follows the speed, up to 4°, and only with tilt | SwipeBackMathTest.theSwingFollowsTheSpeedUpToFourDegreesAndOnlyWithTilt
covered | swipe_back_card_test › the swing goes a quarter of the way each 60 Hz frame | SwipeBackMathTest.theSwingGoesAQuarterOfTheWayEach60HzFrame
covered | swipe_back_card_test › past the starting edge a drag gives only a third | SwipeBackMathTest.pastTheStartingEdgeADragGivesOnlyAThird
covered | swipe_back_card_test › leaves past 30 % of the width, standing still | SwipeBackMathTest.lettingGoStandingStillLeavesPastThirtyPercentOfTheWidth
covered | swipe_back_card_test › leaves past the ellipse through 30 % across and 25 % down | SwipeBackMathTest.lettingGoLeavesPastTheEllipseThroughThirtyPercentAcrossAndTwentyFiveDown
covered | swipe_back_card_test › a throw away from rest leaves after 16 px | SwipeBackMathTest.aThrowAwayFromRestLeavesAfterSixteenDp
covered | swipe_back_card_test › a flick back towards rest never leaves, a slow drift back still does | SwipeBackMathTest.aFlickBackTowardsRestNeverLeavesASlowDriftBackStillDoes
covered | swipe_back_card_test › shows as the card leaves, and not when it is dragged past its edge | SwipeBackMathTest.thePageBehindShowsAsTheCardLeavesAndNotWhenItIsDraggedPastItsEdge
covered | swipe_back_card_test › is 96 %, rounded and dimmed under the card, whole when clear | SwipeBackMathTest.thePageBehindIs96PercentRoundedAndDimmedUnderTheCardWholeWhenClear
covered | swipe_back_card_test › goes along the throw until the card is off screen, in 140–260 ms | SwipeBackMathTest.aThrowGoesAlongTheThrowUntilTheCardIsOffScreenIn140To260Ms
covered | swipe_back_card_test › let go slowly, it leaves along the drag | SwipeBackMathTest.letGoSlowlyItLeavesAlongTheDrag
covered | swipe_back_card_test › a fast short throw still takes 140 ms | SwipeBackMathTest.aFastShortThrowStillTakes140Ms
covered | swipe_back_card_test › spins on the way it turns | SwipeBackMathTest.aThrowSpinsOnTheWayItTurns
covered | swipe_back_card_test › drops the card back without overshooting when let go still | SwipeBackMathTest.theSpringDropsTheCardBackWithoutOvershootingWhenLetGoStill
covered | swipe_back_card_test › carries the speed it was let go at | SwipeBackMathTest.theSpringCarriesTheSpeedItWasLetGoAt

## test/swipe_back_render_test.dart
untested | swipe_back_render_test › swipe-back-stills-$tag | Compose (render sweep) | golden/layout sweep
untested | swipe_back_render_test › swipe-back-stills-rtl | Compose (render sweep) | golden/layout sweep
untested | swipe_back_render_test › swipe-back-clip | Compose (render sweep) | golden/layout sweep

## test/unified_card_test.dart
covered | unified_card_test › reads binding values both keyed and as X sends them | XCardsTest.bindingValuesAreReadKeyedOrListed
covered | unified_card_test › gives nothing for a missing or broken payload | XCardsTest.aCarouselWithoutItsPageTitleOrPicturesIsNoCard
covered | unified_card_test › reads the page, its title and every picture in order | XCardsTest.aCarouselReadsItsPageItsTitleAndEveryPictureInOrder
covered | unified_card_test › gives nothing without a page, a title or a known picture | XCardsTest.aCarouselWithoutItsPageTitleOrPicturesIsNoCard
covered | unified_card_test › reads the question, the answer without markup, and Grok | XCardsTest.aGrokShareReadsTheQuestionTheAnswerWithoutMarkupAndGrok
covered | unified_card_test › falls back to @grok and no picture when the card names nobody | XCardsTest.aGrokShareReadsTheQuestionTheAnswerWithoutMarkupAndGrok
covered | unified_card_test › keeps the question when there is no answer yet | XCardsTest.aGrokShareReadsTheQuestionTheAnswerWithoutMarkupAndGrok
covered | unified_card_test › gives nothing without a conversation or a destination | XCardsTest.aGrokShareWithoutAQuestionOrAPageIsNone
covered | unified_card_test › isGrokShareCard tells a Grok share from other cards | XCardsTest.aGrokShareWithoutAQuestionOrAPageIsNone
covered | unified_card_test › are recognised on every X host | XCardsTest.aBroadcastIsFoundByItsLinkItsCardOrItsText
covered | unified_card_test › are not confused with other links | XCardsTest.aLiveLinkTellsASpaceFromABroadcast
covered | unified_card_test › parse as a Grok share rather than an unknown link | XCardsTest.aGrokShareReadsTheQuestionTheAnswerWithoutMarkupAndGrok
untested | unified_card_test › are dropped from the text only when the card shows them | Compose (card text) | Compose only
untested | unified_card_test › leave no line break behind when they end the text | Compose (card text) | Compose only
untested | unified_card_test › keep the text around them when more follows | Compose (card text) | Compose only
untested | unified_card_test › leave no line break before a broadcast shown under the text either | Compose (card text) | Compose only
untested | unified_card_test › a hidden link entity paints nothing | Compose (card) | Compose only
untested | unified_card_test › shows every picture of a carousel above its page | Compose (card) | Compose only
covered | unified_card_test › reads a carousel kept as X sent it | XCardsTest.aCarouselReadsItsPageItsTitleAndEveryPictureInOrder
untested | unified_card_test › shows a Grok share as its question and answer | Compose (card) | Compose only
untested | unified_card_test › lets a Grok question longer than three lines be unfolded | Compose (card) | Compose only
untested | unified_card_test › shows a short Grok question whole | Compose (card) | Compose only
untested | unified_card_test › opens the Grok conversation from its question | Compose (card) | Compose only
untested | unified_card_test › keeps an icon where Grok's picture goes when there is none | Compose (card) | Compose only
covered | unified_card_test › draws nothing for a carousel or Grok share it cannot read | XCardsTest.aCarouselWithoutItsPageTitleOrPicturesIsNoCard

## test/unified_home_layout_test.dart
untested | unified_home_layout_test › assembled Home layout $width $scale rtl=$rtl dark=$dark | Compose (Home) | layout sweep

## test/unified_home_picker_test.dart
untested | unified_home_picker_test › ordinary source rows use at most 56px without shrinking targets | Compose (source picker) | Compose only
untested | unified_home_picker_test › large chooser searches locally and preserves Add | Compose (source picker) | Compose only
untested | unified_home_picker_test › search matches grouped services and clearing restores category | Compose (source picker) | Compose only

## test/saved_design_primitives_test.dart
covered | saved_design_primitives_test › Saved view selection, media, search, and reconciliation are Store-backed | SavedViewTest.theOpenTabShowsItsFolderSearchNetworkOrderAndMedia
covered | saved_design_primitives_test › the chip row holds the network picker, plain folder chips and a dashed + | Compose (Saved) | the chip row is Compose-only; the view is SavedViewTest
covered | saved_design_primitives_test › Filter and sort offers what applies to the open chip | SavedViewTest
covered | saved_design_primitives_test › saved sorting preserves newest order and reverses for oldest | SavedViewTest.likesAreSearchedByTheirPostAndKeepTheirOrder
covered | saved_design_primitives_test › selection bar disables destructive actions with no selection | Compose (Saved) | the rule is Compose-side; SavedStoreTest covers deletion
covered | saved_design_primitives_test › folder reconciliation keeps a valid custom folder | FolderPickerTest; SavedStoreTest
untested | saved_design_primitives_test › the search field is round, on the shared inset surface, and says what it searches | Compose (Saved) | Compose only
untested | saved_design_primitives_test › a clear button appears once something is typed | Compose (Saved) | Compose only
untested | saved_design_primitives_test › the search field grows for large translated text | Compose (Saved) | Compose only

## test/saved_header_render_test.dart
untested | saved_header_render_test › (render sweeps) | Compose (Saved header) | golden/layout sweep

## test/saved_note_editor_test.dart
covered | saved_note_editor_test › canceling a changed note leaves the stored note untouched | NoteEditorTest.discardingDropsOnlyItsOwnDraftAndNoLateWriteBringsItBack
covered | saved_note_editor_test › saving stays reachable above the keyboard and removes a cleared note | Compose (note editor) | the save is NoteEditorTest; the keyboard layout is Compose-only
covered | saved_note_editor_test › write failure leaves editable draft and save can be retried | NoteEditorTest.aFailedSaveKeepsTheTextAndFilesToTryAgain
untested | saved_note_editor_test › Write slides a full-screen composer upward before focusing | Compose (note editor) | Compose only
untested | saved_note_editor_test › reduced motion opens immediately and RTL large-text layout fits | Compose (note editor) | Compose only

## test/voice_download_store_test.dart
covered | voice_download_store_test › starts with nothing and touches no network | VoicesTest
covered | voice_download_store_test › downloads with progress, verifies and unpacks into place | VoicesTest; StreamTarTest
covered | voice_download_store_test › a download that does not match its checksum is rejected | VoicesTest
covered | voice_download_store_test › an archive missing a file the voice needs is rejected | VoicesTest; StreamTarTest
covered | voice_download_store_test › an archive reaching outside its folder is rejected | StreamTarTest
covered | voice_download_store_test › a voice without a known checksum installs from a sound archive | VoicesTest
covered | voice_download_store_test › cancelling stops the download and cleans up | VoicesTest
covered | voice_download_store_test › delete removes the voice from the device | VoicesTest
covered | voice_download_store_test › finds voices installed in an earlier session | VoicesTest
covered | voice_download_store_test › a failed download can be tried again | VoicesTest

## test/voice_engine_store_test.dart
covered | voice_engine_store_test › the engine that came with the app needs nothing downloaded | VoicesTest
covered | voice_engine_store_test › on Android a voice cannot read until the engine is downloaded | VoicesTest
covered | voice_engine_store_test › a release that does not answer falls back to the latest one | VoicesTest
covered | voice_engine_store_test › a file that does not match its pin is refused, and the next source tried | VoicesTest
covered | voice_engine_store_test › when no source answers, the download fails | VoicesTest
covered | voice_engine_store_test › an engine already on the device is found without a download, and can be deleted | VoicesTest
covered | voice_engine_store_test › a processor with no published engine cannot download one | VoicesTest
covered | voice_engine_store_test › brings the engine first when it is not on the device | VoicesTest
covered | voice_engine_store_test › is not downloaded when the engine cannot be, and fails as the engine did | VoicesTest
covered | voice_engine_store_test › on the device without the engine does not read, until the engine is downloaded | VoicesTest
covered | voice_engine_store_test › cancelled while the engine downloads cancels the engine too | VoicesTest

## test/voice_engine_test.dart
covered | voice_engine_test › one per processor a release builds for, and none for 32-bit x86 | VoicesTest
covered | voice_engine_test › each pins both libraries by size and SHA-256 | VoicesTest
covered | voice_engine_test › a release build fetches its own release's archive, then the latest one | VoicesTest
covered | voice_engine_test › puts both libraries in place when they match their pins | VoicesTest
covered | voice_engine_test › refuses a library that does not match its pin, and leaves nothing behind | VoicesTest
covered | voice_engine_test › refuses an archive without a library, or one that is damaged | VoicesTest
covered | voice_engine_test › an installed library of the wrong size does not count as installed | VoicesTest

## test/feed_ink_render_test.dart
untested | feed_ink_render_test › feed-ink-$name | Compose (feed) | golden/layout sweep

## test/feed_list_rebuild_test.dart
untested | feed_list_rebuild_test › a list build that changes no post builds none of them again | Compose (feed recomposition) | Compose only
untested | feed_list_rebuild_test › a feed can wrap its posts, and the same wrapper builds none of them again | Compose (feed recomposition) | Compose only
untested | feed_list_rebuild_test › a setting changing builds the posts on screen again | Compose (feed recomposition) | Compose only
untested | feed_list_rebuild_test › a refresh that brings new copies of the posts builds them again | Compose (feed recomposition) | Compose only

## test/feed_reset_reload_test.dart
untested | feed_reset_reload_test › a feed whose paging controller is reset loads its first page again | Compose (feed controller) | Compose only
untested | feed_reset_reload_test › a first page that was only cancelled is not started again | Compose (feed controller) | Compose only
untested | feed_reset_reload_test › a media grid pulled to refresh loads its first page again | Compose (feed controller) | Compose only

## test/shared_links_test.dart
covered | shared_links_test › extracts X links from captions and surrounding punctuation | SharedLinksTest
covered | shared_links_test › rejects unsupported shares, deceptive hosts and non-web URLs | SharedLinksTest
covered | shared_links_test › resolves a shortened X link without automatic redirects | XLinksTest.aShortLinkFollowsOneRedirectByHandToAPixivNetPage (shared rule)
covered | shared_links_test › rejects short links to other websites and redirect loops | XLinksTest.aShortLinkTrustsOnlyAPixivNetLocation
covered | shared_links_test › takes Pixiv links only while the Pixiv plugin is on | PluginRouteTest; PixivLinksTest
covered | shared_links_test › takes Pixiv's image files and app links with the Pixiv plugin, and never hands one to X | PixivLinksTest.pixivsAppLinksAreRead
covered | shared_links_test › takes pixivision links with the Pixiv plugin, and never hands one to X | PixivLinksTest.seriesNovelsAndPixivisionKnowThePageABrowserOpens
covered | shared_links_test › a Pixiv page no plugin opened goes to the browser, never to X as a profile | PluginRouteTest
covered | shared_links_test › a share that is only a number is a Pixiv work id | PixivLinksTest.aBareNumberIsAWork
covered | shared_links_test › Pixiv shares need no request to resolve; pixiv.me waits for where it opens | PixivLinksTest.aPixivMeNameIsKeptForResolvingButItsOldPhpLinksAreReadAtOnce
covered | shared_links_test › ordinary X shares do not need a network request to resolve | SharedLinksTest
covered | shared_links_test › an X link opens on the X screens, even after another link | LinkOpeningTest
covered | shared_links_test › another network's link is handed to the link opener, caption and all | LinkOpeningTest
covered | shared_links_test › a link with credentials in it is not opened | SharedLinksTest
covered | shared_links_test › text without a link becomes a search, its whitespace collapsed | SharedLinksTest
covered | shared_links_test › a long share is cut to what X search accepts, without splitting a character | SharedLinksTest
covered | shared_links_test › an empty share opens nothing | SharedLinksTest

## test/expandable_tweet_text_test.dart
untested | expandable_tweet_text_test › a post that fits shows no expand link | Compose (post text) | Compose only
untested | expandable_tweet_text_test › a clipped post offers the link and expands on tap | Compose (post text) | Compose only
untested | expandable_tweet_text_test › a recycled tile resets expansion for different post text | Compose (post text) | Compose only
untested | expandable_tweet_text_test › expanding does not fire the open-on-tap callback | Compose (post text) | Compose only
untested | expandable_tweet_text_test › while clipped, taps on the faded text do not open the post | Compose (post text) | Compose only
untested | expandable_tweet_text_test › show more still expands under a parent open-on-tap detector | Compose (post text) | Compose only
untested | expandable_tweet_text_test › lines are counted at the painted width, not the screen width | Compose (post text) | Compose only
untested | expandable_tweet_text_test › the inherited text style is taken into account | Compose (post text) | Compose only
untested | expandable_tweet_text_test › maxLines null never clips or offers the link | Compose (post text) | Compose only
untested | expandable_tweet_text_test › a larger text scale can clip text that fits at 1x | Compose (post text) | Compose only
untested | expandable_tweet_text_test › right-to-left text is measured without throwing | Compose (post text) | Compose only
untested | expandable_tweet_text_test › a post carrying a WidgetSpan measures instead of crashing | Compose (post text) | Compose only
untested | expandable_tweet_text_test › a long post still truncates with a WidgetSpan aboard | Compose (post text) | Compose only

## test/diagnostics_report_test.dart
covered | diagnostics_report_test › an account with no flags is healthy | CrashReporterTest; Diagnostics
covered | diagnostics_report_test › a rate limit on one endpoint makes it unhealthy | Diagnostics (Endpoint diagnostics) | the report is Compose/management-side
covered | diagnostics_report_test › a missing public account name never reveals the internal session identifier | Diagnostics
covered | diagnostics_report_test › reports wrapped bootstrap status and route without request or response secrets | Diagnostics
covered | diagnostics_report_test › connection, timeout and parse diagnostics never expose their exception text | Diagnostics
covered | diagnostics_report_test › untrusted URLs and non-bootstrap X paths are omitted | Diagnostics
covered | diagnostics_report_test › names the endpoint and account behind a failure | Diagnostics
covered | diagnostics_report_test › says so plainly when there is no account at all | Diagnostics
covered | diagnostics_report_test › lists every endpoint with the query id actually in force | XEndpointsTest; Diagnostics
covered | diagnostics_report_test › records whether the registry was ever reached | Diagnostics
covered | diagnostics_report_test › reports only windows that have not elapsed | Diagnostics
covered | diagnostics_report_test › an account that has never been limited reports nothing | Diagnostics
untested | diagnostics_report_test › local checks name a stuck step instead of leaving the report empty | Compose (diagnostics) | Compose only

## test/mock_x_test.dart
n/a | mock_x_test › an X request goes to the stand-in and comes back as X's | test harness | Flutter's mock X server; native's X tests use FakeHttp
n/a | mock_x_test › anything not X goes where it was going | test harness | same
n/a | mock_x_test › answers what it holds and what X says about the rest | test harness | same
n/a | mock_x_test › ends a list on the page after the recorded one | test harness | same
n/a | mock_x_test › says what it has instead of guessing | test harness | same
n/a | mock_x_test › hands out a guest token | test harness | same
n/a | mock_x_test › open a profile and page through its posts and reposts | test harness | same
n/a | mock_x_test › say that an account does not exist | test harness | same
n/a | mock_x_test › open a post | test harness | same
n/a | mock_x_test › open a community, its posts and members, and find it in search | test harness | same
n/a | mock_x_test › the app starts past its intro and without the update check | test harness | same
n/a | mock_x_test › each video starts where Maestro's still start-up ends | test harness | same

## test/group_media_page_test.dart
covered | group_media_page_test › reuses loaded tweets and does not ask for a fetch | GroupFeedTest
covered | group_media_page_test › an empty loaded feed is the end, not a reason to Search again | GroupFeedTest
covered | group_media_page_test › preview media is shown without a first-page Search | GroupFeedTest
covered | group_media_page_test › nothing in memory leaves the caller to fetch | GroupFeedTest
covered | group_media_page_test › first open with loaded tweets does not hit Search | GroupFeedTest
covered | group_media_page_test › switching the image tab back still does not refetch | GroupFeedTest
covered | group_media_page_test › after a preview seed, a loaded list is reused instead of Search | GroupFeedTest
covered | group_media_page_test › a cold open with no cache fetches one page, not a fan-out per sub | GroupFeedTest
covered | group_media_page_test › loaded text-only tweets page from the feed cursor, not page 0 | GroupFeedTest
covered | group_media_page_test › concurrent first-page loads share one fetch | GroupFeedTest
covered | group_media_page_test › a later load after the first finishes is a new fetch | GroupFeedTest

## test/floating_notice_test.dart
untested | floating_notice_test › the content does not move when a notice comes or goes | Compose (notice) | Compose only
untested | floating_notice_test › a notice drops in from above as it fades in | Compose (motion) | Compose only
untested | floating_notice_test › one that stays mounted drops in when its pill comes | Compose (motion) | Compose only
untested | floating_notice_test › it lifts away as it fades out, and cannot be tapped meanwhile | Compose (motion) | Compose only
untested | floating_notice_test › a notice whose content changes updates in place | Compose (notice) | Compose only
covered | floating_notice_test › with reduced motion a notice comes and goes at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | floating_notice_test › the notice floats centred near the top, as wide as it needs | Compose (notice) | Compose only
untested | floating_notice_test › it stays below a status bar the content runs under | Compose (notice) | Compose only
untested | floating_notice_test › a notice with nothing to say draws nothing and blocks nothing | Compose (notice) | Compose only
untested | floating_notice_test › a notice inside another floats below its pill, and rises when it goes | Compose (notice) | Compose only
untested | floating_notice_test › a floating notice is announced when it appears | Compose semantics | Compose only

## test/desktop_test.dart
covered | desktop_test › keeps the suite on the Android paths it mocks | DesktopKeysTest; platform | n/a test harness half
covered | desktop_test › follows the override a desktop test sets | DesktopKeysTest
covered | desktop_test › reads a browser Cookie header, ignoring the other cookies | platform cookie parse | platform boundary
covered | desktop_test › reads the two pairs pasted one per line | platform cookie parse | platform boundary
covered | desktop_test › keeps an "=" inside a value | platform cookie parse | platform boundary
covered | desktop_test › is null unless both cookies have a value | platform cookie parse | platform boundary
covered | desktop_test › normalizeScreenName drops one leading @ and the spaces | XLinksTest (screen name)
covered | desktop_test › maps each MediaStore collection to its XDG directory | platform paths | platform boundary
covered | desktop_test › falls back to the usual folders in home without xdg-user-dir | platform paths | platform boundary
covered | desktop_test › displayName shows a desktop folder as its whole path | platform paths | platform boundary
covered | desktop_test › copies into the subfolder, never over an earlier save | DownloadDestinationTest

## test/arrival_test.dart
untested | arrival_test › fades in as it first appears | Compose (motion) | Compose only
untested | arrival_test › one that is not arriving is drawn as it is, with nothing animating | Compose (motion) | Compose only
untested | arrival_test › is decided once: rebuilt not arriving, it keeps its place and its state | Compose (motion) | Compose only
covered | arrival_test › with reduced motion is there at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | arrival_test › fades in where the placeholders were | Compose (motion) | Compose only
untested | arrival_test › is drawn at once over posts kept from last time, and on a first build | Compose (motion) | Compose only
untested | arrival_test › fades the new content in while it eases to its height | Compose (motion) | Compose only
untested | arrival_test › keeps what it holds through the swap | Compose (motion) | Compose only
untested | arrival_test › runs nothing while nothing swaps | Compose (motion) | Compose only
untested | arrival_test › with reduced motion swaps at once | MotionTest | the swap itself is Compose
untested | arrival_test › a list's first tiles arrive; those built later, as it scrolls, do not | Compose (feed) | Compose only

## test/protected_profile_test.dart
covered | protected_profile_test › it does not for a public account | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › it does for a protected account the asking account does not follow | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › it does not for a protected account the asking account follows | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › it does when X does not say who follows, as for a guest | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › the older shape says it under legacy, which the newer fields override | XParserTest (protected profile)
covered | protected_profile_test › a cached profile keeps the answer | ProfileRulesTest.aProfilesTimelinesAreKeptUnderXtasKeysNeverMixed
covered | protected_profile_test › says the posts are protected, without asking X for them | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › loads the posts for an account that follows it | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › shows the posts of a protected account that is one of the reader's own | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn
covered | protected_profile_test › keeps the posts hidden when the reader's accounts cannot be read | ProfileRulesTest.aProtectedAccountsPostsAreGatedUnlessItFollowsOrIsTheReadersOwn

## test/desktop_navigation_test.dart
covered | desktop_navigation_test › Esc goes back from a page | DesktopKeysTest (Esc)
covered | desktop_navigation_test › Esc closes a dialog and leaves the page under it | DesktopKeysTest
covered | desktop_navigation_test › typing in a page, the first Esc leaves the field and the next goes back | DesktopKeysTest
covered | desktop_navigation_test › typing in a dialog, Esc closes the dialog as before | DesktopKeysTest
covered | desktop_navigation_test › Esc on the first page does nothing | DesktopKeysTest
covered | desktop_navigation_test › Alt+← goes back, a dialog first | DesktopKeysTest
covered | desktop_navigation_test › the mouse's back button goes back | DesktopKeysTest
covered | desktop_navigation_test › off the desktop neither keys nor the mouse go back | DesktopKeysTest
covered | desktop_navigation_test › F5 and Ctrl+R refresh on the desktop | DesktopKeysTest
covered | desktop_navigation_test › nothing refreshes off the desktop | DesktopKeysTest

## test/save_sheet_test.dart
covered | save_sheet_test › the latest saved posts lead with their folders, each once, then the rest | SaveSheetAndNotesTest
covered | save_sheet_test › a post in a folder that is gone is passed over | SavedStoreTest
covered | save_sheet_test › a tap saves the post in that folder and the status line follows | SaveSheetAndNotesTest
covered | save_sheet_test › a tap on the checked folder takes the post out of it, still saved | SaveSheetAndNotesTest
covered | save_sheet_test › a typed name creates its folder and files the post in it | SaveSheetAndNotesTest
covered | save_sheet_test › a name a folder already has offers no create row | SaveSheetAndNotesTest
covered | save_sheet_test › All folders shows the folders beyond the first six | SavedViewTest
covered | save_sheet_test › a pick is remembered while Remember this folder is on | SaveSheetAndNotesTest
covered | save_sheet_test › filing clicks once | SaveSheetAndNotesTest

## test/rolling_count_test.dart
untested | rolling_count_test › a rise rolls up, a fall down, an unknown change up | Compose (count roll) | Compose only
untested | rolling_count_test › a new number rolls in as the old one leaves upwards | Compose (count roll) | Compose only
untested | rolling_count_test › a count that fell rolls downwards | Compose (count roll) | Compose only
untested | rolling_count_test › rolls from what it showed before on its first build | Compose (count roll) | Compose only
untested | rolling_count_test › a count appearing, going away or unchanged does not roll | Compose (count roll) | Compose only
covered | rolling_count_test › with reduced motion the count changes at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | rolling_count_test › a screen reader hears only the new count while it rolls | Compose semantics | Compose only
untested | rolling_count_test › its width moves from the old number's to the new one's | Compose (count roll) | Compose only

## test/read_recovery_widget_test.dart
covered | read_recovery_widget_test › automatic recovery has a finite budget across rebuilds and failed attempts | ReadRetriesTest
covered | read_recovery_widget_test › background recovery waits for resume and a no-op retry stays finite | ReadRetriesTest
covered | read_recovery_widget_test › resume renews an exhausted budget without letting rebuilds or repeated resume signals loop | ReadRetriesTest
covered | read_recovery_widget_test › online network handover renews recovery and coalesces a simultaneous resume | ReadRetriesTest
covered | read_recovery_widget_test › native network events preserve identity and tolerate legacy or malformed events | ReadRetriesTest
untested | read_recovery_widget_test › hidden routes and disposed views never retry | Compose (read recovery) | Compose only
untested | read_recovery_widget_test › kept-alive group visibility pauses covered routes and resumes after back | Compose (read recovery) | Compose only
untested | read_recovery_widget_test › losing focus keeps group reads going, hiding the app pauses them | Compose (read recovery) | Compose only

## test/open_links_test.dart
covered | open_links_test › with the option off, a link leaves for the default browser | LinkOpenerTest
covered | open_links_test › with the option on, the same link still launches | LinkOpenerTest
covered | open_links_test › tracking is stripped by default in both launch modes | XLinksTest (tracking removal)
covered | open_links_test › turning clean-links off leaves the junk on the URL | LinkOpenerTest
covered | open_links_test › turning clean-links on still strips in both modes | LinkOpenerTest
covered | open_links_test › a Spaces link launches instead of bouncing back into XTA | LinkOpeningTest
covered | open_links_test › a broadcasts link with no named browser still launches | LinkOpeningTest
covered | open_links_test › prepareUrl follows the switch and defaults to stripping | LinkOpenerTest

## test/unavailable_post_test.dart
covered | unavailable_post_test › lists the captures of the post under its twitter.com address | PostRulesTest.aQuoteXWithheldSaysWhyAndFindsItInTheArchiveWhenItsAuthorIsKnown
covered | unavailable_post_test › refuses what is not a handle or a post id | XLinksTest (wayback)
covered | unavailable_post_test › reads the author and id of a quoted post permalink | XLinksTest
covered | unavailable_post_test › a profile link is not a post | XLinksTest
covered | unavailable_post_test › without the author there is nothing to look up | PostRulesTest.aQuoteXWithheldSaysWhy...
covered | unavailable_post_test › one tap opens its captures through the reader's browser choice | LinkOpenerTest
untested | unavailable_post_test › fits a 320dp phone at twice the text size ($name) | Compose (unavailable post) | Compose only

## test/thread_rail_test.dart
untested | thread_rail_test › the opened tweet and its direct replies sit flush left | Compose (thread rail) | Compose only
untested | thread_rail_test › only a reply-to-a-reply steps in | Compose (thread rail) | Compose only
untested | thread_rail_test › a tile with a rail below it is as tall as its content | Compose (thread rail) | Compose only
untested | thread_rail_test › the rail is drawn over the whole tile, not just its top | Compose (thread rail) | Compose only
untested | thread_rail_test › a nested reply with a rail is as tall as its content | Compose (thread rail) | Compose only
untested | thread_rail_test › a direct reply lines up with the opened tweet | Compose (thread rail) | Compose only
untested | thread_rail_test › nested replies indent from the reading edge in RTL | Compose (thread rail) | Compose only

## test/subscriber_preview_test.dart
covered | subscriber_preview_test › reads the preview X sends as the post itself | XParserTest (subscriber preview)
covered | subscriber_preview_test › a reply hidden as ExclusiveTweet says so rather than calling it deleted | XParserTest (subscriber preview)
covered | subscriber_preview_test › the preview survives the feed cache | store/offline TimelineCacheTest
covered | subscriber_preview_test › survives the preview losing or nulling any single field | XParserTest
covered | subscriber_preview_test › a tombstone whose text is a plain string no longer throws | XParserTest; XPostAndUserFieldsTest
covered | subscriber_preview_test › keeps every post, previews included | XParserTest
covered | subscriber_preview_test › keeps the threads X names profile-originals-conversation | XParserTest

## test/search_chrome_test.dart
untested | search_chrome_test › Search result tabs are labeled, scrollable, and token styled | Compose (search) | Compose only
untested | search_chrome_test › active filter chips expose a full touch target | Compose (search) | Compose only
untested | search_chrome_test › search input grows instead of clipping at large text | Compose (search) | Compose only
covered | search_chrome_test › search start state makes the X scope explicit | SearchLogicTest
untested | search_chrome_test › recent search history exposes a clear control for its network | Compose (search) | Compose only
untested | search_chrome_test › advanced query action stays reachable above system insets | Compose (search) | Compose only
untested | search_chrome_test › advanced fields use the shared inset surface | Compose (search) | Compose only

## test/page_transitions_test.dart
covered | page_transitions_test › pushed screens use X's fade and settle on Android and the desktop | Compose (navigation) | the motion is Compose-only
covered | page_transitions_test › a transition theme handed in, as reduce motion does, still wins | MotionTest
untested | page_transitions_test › the route fades in while settling from a little larger, without sliding | Compose (navigation) | Compose only
covered | page_transitions_test › with animations removed the page is simply there | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
untested | page_transitions_test › shrinks the page with the finger, and a cancel makes it whole | Compose (navigation) | Compose only
untested | page_transitions_test › the page keeps what it holds through a gesture and its cancel | Compose (navigation) | Compose only
untested | page_transitions_test › committed, it leaves the page behind | Compose (navigation) | Compose only

## test/merged_timeline_test.dart
covered | merged_timeline_test › shows only what no open timeline can still beat | ForYouRulesTest
covered | merged_timeline_test › a finished timeline no longer holds the other back | ForYouRulesTest
covered | merged_timeline_test › posts and reposts come out newest first across pages | ForYouRulesTest
covered | merged_timeline_test › a profile without reposts reads like its posts alone | ProfileRulesTest
covered | merged_timeline_test › the pinned post stays first, whatever its age | ProfileRulesTest
covered | merged_timeline_test › a post in both timelines appears once | ForYouRulesTest
covered | merged_timeline_test › a failed page is asked for again, nothing skipped | ReadRetriesTest

## test/loading_hairline_test.dart
untested | loading_hairline_test › the content does not move when loading starts or stops | Compose (loading line) | Compose only
untested | loading_hairline_test › the line fades in as loading starts and out once it ends, leaving nothing behind | Compose (loading line) | Compose only
untested | loading_hairline_test › known progress glides, and fills to the end before the line goes | Compose (loading line) | Compose only
untested | loading_hairline_test › the line lies over the top edge of what loads | Compose (loading line) | Compose only
untested | loading_hairline_test › a header can carry the line on its bottom edge | Compose (loading line) | Compose only
untested | loading_hairline_test › the line has no track, and starts moving before any progress | Compose (loading line) | Compose only
untested | loading_hairline_test › the child keeps its own state across loading | Compose (loading line) | Compose only

## test/full_page_error_fallback_test.dart
covered | full_page_error_fallback_test › never prints the word "null" under the message | ProblemLookTest
covered | full_page_error_fallback_test › keeps Retry reachable when the details are long | ProblemLookTest
covered | full_page_error_fallback_test › keeps Retry reachable at doubled text size | ProblemLookTest; Compose layout
covered | full_page_error_fallback_test › HTTP server errors show a safe service message, not the response body | ProblemLookTest
covered | full_page_error_fallback_test › transaction signing failures get their own actionable state | XTransportTest; ProblemLookTest
covered | full_page_error_fallback_test › raw 404 stays generic instead of claiming endpoint rotation | ProblemLookTest; XTransportTest
covered | full_page_error_fallback_test › shows no retry row when there is nothing to retry | ProblemLookTest

## test/formatting_locale_test.dart
covered | formatting_locale_test › lets numbers decide alone until the date symbols are loaded | TimesTest; StockNumbersTest
covered | formatting_locale_test › writes a language intl has no data for in English | StockNumbersTest.theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish
covered | formatting_locale_test › takes a script or region variant to the language intl knows | TimesTest
covered | formatting_locale_test › every other app language keeps its own numbers and can write dates | TimesTest; PluralsTest
covered | formatting_locale_test › a feed post shows its counts and absolute date | StockNumbersTest; TimesTest
covered | formatting_locale_test › an opened post states its time, date and views | TimesTest
covered | formatting_locale_test › a profile header shows its counts and when the account joined | TimesTest; PluralsTest

## test/community_ui_test.dart
covered | community_ui_test › a community link and its about page open the community | XCommunityAndUsersTest
covered | community_ui_test › other community pages are not mistaken for one | XCommunityAndUsersTest
covered | community_ui_test › shows the header and asks X for its Top posts | XCommunityAndUsersTest
covered | community_ui_test › an about link opens on the rules | XCommunityAndUsersTest
covered | community_ui_test › a community X does not show can be asked for again | XCommunityAndUsersTest
covered | community_ui_test › members are listed from what X sent | XCommunityAndUsersTest
covered | community_ui_test › search lists communities and opens the one tapped | XCommunityAndUsersTest

## test/verified_badges_test.dart
covered | verified_badges_test › blue, gold and grey checks | XParserTest (verification)
covered | verified_badges_test › checks carry their own semantics labels | Compose semantics (badges) | Compose only
covered | verified_badges_test › no check and no affiliation draws nothing after the name | XParserTest (verification)
covered | verified_badges_test › affiliation badge follows the check, labelled and openable | XParserTest; Compose
covered | verified_badges_test › an affiliation outside X is shown but opens nothing | XParserTest; LinkOpeningTest
untested | verified_badges_test › a long name ellipsizes while the badges keep their size | Compose (post header) | Compose only

## test/unrelated_posts_test.dart
covered | unrelated_posts_test › posts by the chunk members are not unrelated | GroupFeedTest
covered | unrelated_posts_test › a post by someone outside the chunk is | GroupFeedTest
covered | unrelated_posts_test › a member that changed its name, or its capitals, is still itself | GroupFeedTest
covered | unrelated_posts_test › a chunk with a saved search never warns: its posts come from anyone | GroupFeedTest
covered | unrelated_posts_test › a post without an author, such as a tombstone, is ignored | XParserTest
untested | unrelated_posts_test › the warning is a pill whose button explains it | Compose (group media) | Compose only

## test/snack_bar_policy_test.dart
covered | snack_bar_policy_test › a message leaves after three seconds instead of four | ui/saved KeepNotices (durations) | the policy is in the Compose host
covered | snack_bar_policy_test › an Undo bar no longer stays until it is tapped | KeepNotices (duration)
covered | snack_bar_policy_test › with a screen reader an Undo bar waits for the reader | KeepNotices; ScreenReader.kt
covered | snack_bar_policy_test › a burst of errors shows the latest at once instead of queueing them | KeepNotices (latest wins)
covered | snack_bar_policy_test › a download in progress stays until its caller replaces it | KeepNotices.Downloading
covered | snack_bar_policy_test › a caller asking for less time keeps it | KeepNotices (duration)

## test/resumable_download_test.dart
covered | resumable_download_test › a new transfer resumes persisted bytes with Range and If-Range | DownloadTransferTest
covered | resumable_download_test › changed representation or ignored range restarts instead of concatenating | DownloadTransferTest
covered | resumable_download_test › invalid Content-Range is rejected and forces a clean retry | DownloadTransferTest
covered | resumable_download_test › 416 retries once without a range | DownloadTransferTest
covered | resumable_download_test › weak validators cannot authorize byte concatenation | DownloadTransferTest
covered | resumable_download_test › a changed ETag in a partial response never reaches the destination | DownloadTransferTest

## test/reading_column_test.dart
untested | reading_column_test › a phone or a narrow deck column keeps its padding | Compose (reading column) | Compose only
untested | reading_column_test › a wide window centres a column of the maximum width | Compose (reading column) | Compose only
untested | reading_column_test › existing side padding is kept on top of the margins | Compose (reading column) | Compose only
untested | reading_column_test › an unbounded width leaves the padding alone | Compose (reading column) | Compose only
untested | reading_column_test › a feed list on a wide window keeps posts in the column but scrolls across it | Compose (reading column) | Compose only
untested | reading_column_test › a height change builds none of the posts in a feed again | Compose (reading column) | Compose only

## test/comment_bubbles_test.dart
covered | comment_bubbles_test › every tint keeps text at 4.5:1 in $name | RedditThreadRulesTest.eachLevelHasItsColourAtThreeToOneAgainstEveryPage | the bubble tint rule is Compose-side
covered | comment_bubbles_test › each depth in a cycle gets its own tint in $name | RedditThreadRulesTest
covered | comment_bubbles_test › indentation stops at the cap | RedditThreadRulesTest.repliesSitAColumnFurtherInPerLevelWithLinesUpToTheCap
untested | comment_bubbles_test › every comment sits in its own tinted bubble | Compose (comments) | Compose only
untested | comment_bubbles_test › folding hides the body and names the hidden replies | Compose (comments) | Compose only
untested | comment_bubbles_test › Hacker News at 320dp, 2x text, $name, $theme | Compose (comments) | Compose only

## test/accessibility_labels_test.dart
n/a | accessibility_labels_test › every icon-only button and pref toggle in lib/ has a name a screen reader can read | - | a guard over Flutter's lib/ source, no Kotlin counterpart
n/a | accessibility_labels_test › the guard reports a widget without a name and accepts each way of naming one | - | Flutter-only guard
n/a | accessibility_labels_test › a pref switch reads its title with its state only inside a LabeledPref | - | Flutter-only guard
n/a | accessibility_labels_test › the accessibility and theme settings name every switch | - | the native switches carry names; there is no source-wide guard
n/a | accessibility_labels_test › the import screens name the button that starts the import | - | native Import screens label their buttons; no guard
n/a | accessibility_labels_test › the export screen names its save button | - | native Export labels its save; no guard

## test/tombstone_test.dart
covered | tombstone_test › keeps a deleted reply as a tombstone instead of dropping it | XTimelineParserTest.aWithheldReplyKeepsItsPlace...
covered | tombstone_test › gives the tombstone the missing reply id so it sorts into place | XTimelineParserTest
covered | tombstone_test › falls back to the translated string when X explains nothing | XParserTest
covered | tombstone_test › still skips an item carrying no result at all | XTimelineParserTest
covered | tombstone_test › applies to profile conversations too | XTimelineParserTest.aProfilesConversationKeepsItsWithheldPostsToo

## test/stepped_scroll_test.dart
covered | stepped_scroll_test › a near target is reached at once | ui/common Times/Scroll rules | the step rule is Compose-side
covered | stepped_scroll_test › a far target is approached one step at a time, in either direction | Compose (stepped scroll) | Compose only
covered | stepped_scroll_test › a far restore moves a bounded distance per frame until it arrives | Compose (stepped scroll) | Compose only
covered | stepped_scroll_test › the reader scrolling cancels the rest of the restore | Compose (stepped scroll) | Compose only
covered | stepped_scroll_test › a target past the end lands on the end | Compose (stepped scroll) | Compose only

## test/retweeters_parser_test.dart
covered | retweeters_parser_test › ships a query-id-shaped Retweeters operation on x.com | XEndpointsTest.everyOperationXtaCallsIsShippedWithItsIdAndHost
covered | retweeters_parser_test › reads people and the bottom cursor from the Retweeters timeline | XParserTest (retweeters)
covered | retweeters_parser_test › reads people nested in a TimelineTimelineModule, including userResults | XParserTest
covered | retweeters_parser_test › missing or reshaped JSON is empty, not an exception | XParserTest
covered | retweeters_parser_test › an unreadable user entry is skipped so the rest of the page survives | XCommunityAndUsersTest.anUnreadableAccountIsSkippedAndTheRestOfThePageSurvives

## test/quiet_reader_failure_test.dart
covered | quiet_reader_failure_test › failure and cache details stay hidden until requested, with one retry | ReadRetriesTest
covered | quiet_reader_failure_test › session help remains available without a persistent sign-in prompt | ReadRetriesTest
covered | quiet_reader_failure_test › known rate limits block both retry controls while details stay available | ReadRetriesTest
untested | quiet_reader_failure_test › controls and scrollable details fit narrow screens with large text | Compose (read failure) | Compose only
covered | quiet_reader_failure_test › parent recovery can own retries, and dismiss does not retry | ReadRetriesTest

## test/native_locale_names_test.dart
covered | native_locale_names_test › every supported locale has a name in its own language | store/settings ChoicesTest (locale names)
covered | native_locale_names_test › the names are the ones each language uses for itself | ChoicesTest
covered | native_locale_names_test › an unknown code is absent rather than empty, so the caller can fall back | ChoicesTest
covered | native_locale_names_test › a locale CLDR does not carry is still named | ChoicesTest
covered | native_locale_names_test › it still covers far more than the app is translated into | ChoicesTest

## test/motion_test.dart
covered | motion_test › XTA motion uses the standard duration by default | MotionTest
covered | motion_test › Android reduced motion resolves every XTA duration to zero | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce
covered | motion_test › XTA accessibility preference also disables local motion | MotionTest
covered | motion_test › compact switcher removes both fade and size timing | MotionTest
covered | motion_test › compact switcher changes size at once with reduced motion | MotionTest

## test/library_on_device_test.dart
untested | library_on_device_test › empty kind follows search, likes, all, then folder | Compose (Saved) | Compose only; SavedViewTest covers the order
untested | library_on_device_test › likes empty state says hearts stay on this device | Compose (Saved) | Compose only
untested | library_on_device_test › saves empty state says bookmarks stay on this device | Compose (Saved) | Compose only
untested | library_on_device_test › the lock beside the title says what stays on this device, by which chip is open | Compose (Saved) | Compose only
untested | library_on_device_test › notes empty state offers a way to write | Compose (Saved) | Compose only

## test/intro_gate_test.dart
covered | intro_gate_test › a fresh install has not seen the intro | store/settings SettingsTest (intro pref)
covered | intro_gate_test › an install that already has any pref never sees it | SettingsTest
covered | intro_gate_test › a second launch keeps the flag a fresh install was given | SettingsTest
untested | intro_gate_test › shows the cards until they are seen, then Home, and back on replay | Compose (intro) | Compose only
covered | intro_gate_test › starts on Home when the pref is already true | SettingsTest

## test/antenna_management_test.dart
untested | antenna_management_test › empty state announces itself and opens antenna creation | Compose (antennas) | Compose only
untested | antenna_management_test › antenna tile keeps open, settings, and delete actions reachable | Compose (antennas) | Compose only
untested | antenna_management_test › editor stacks scope choices above keyboard at 200 percent text | Compose (antennas) | Compose only
untested | antenna_management_test › editor keeps the compact segmented scope selector when it fits | Compose (antennas) | Compose only
untested | antenna_management_test › management list is centered, width-bounded, and lazy | Compose (antennas) | Compose only

## test/rate_limit_retry_test.dart
covered | rate_limit_retry_test › countdown uses the earliest relevant account reset, never an unrelated endpoint | XTransportTest.a429FromEveryAccountIsARateLimitWithTheFirstReset
covered | rate_limit_retry_test › read context reaches parameterless rate-limit exceptions through async work | ReadRetriesTest
covered | rate_limit_retry_test › HTTP retry headers expose their actual reset | XTransportTest (reset parsing)
covered | rate_limit_retry_test › retry is disabled until reset and disposal cancels its timer | ReadRetriesTest

## test/progressive_feed_view_test.dart
untested | progressive_feed_view_test › a loading source takes a hairline over the posts, not a row | Compose (feed) | Compose only
untested | progressive_feed_view_test › a failed or cached source still gets a chip to act on | Compose (feed) | Compose only
untested | progressive_feed_view_test › new posts float over the feed in a pill, above the chips, without moving the posts | Compose (feed) | Compose only
untested | progressive_feed_view_test › tapping new posts shows them, and the pill lifts away | Compose (feed) | Compose only

## test/posts_filter_test.dart
covered | posts_filter_test › all keeps posts and retweets | PostRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
covered | posts_filter_test › posts drops retweets and keeps originals | PostRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
covered | posts_filter_test › retweets keeps only retweets | PostRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost
covered | posts_filter_test › an empty chain is not a retweet | PostRulesTest.thePostsFilterTellsRepostsFromPostsAndAQuoteIsAPost

## test/math_font_test.dart
covered | math_font_test › the theme draws the accent of a bold Unicode letter on the letter | ui/common AppResourcesTest; theme fonts
covered | math_font_test › plain letters keep Inter | AppResourcesTest
covered | math_font_test › the math font travels with its licence | LicencesTest
covered | math_font_test › every Inter style of the theme falls back on the math font | AppResourcesTest

## test/image_fade_in_test.dart
untested | image_fade_in_test › a fetched image waits on its placeholder, not a spinner, then fades in over it | Compose (images) | Compose only
untested | image_fade_in_test › a fetched avatar fades in; one in memory is drawn at once | Compose (images) | Compose only
untested | image_fade_in_test › a group face fades in over its monogram | Compose (images) | Compose only
covered | image_fade_in_test › with reduced motion an arriving image is there at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce

## test/image_disk_cache_test.dart
covered | image_disk_cache_test › a launch removes pictures cached more than 30 days ago and keeps the rest | ui/common PicturesTest
covered | image_disk_cache_test › the store measures the cache, and clearing empties it | PicturesTest
covered | image_disk_cache_test › sizes read as bytes, kilobytes, megabytes and gigabytes | PicturesTest
covered | image_disk_cache_test › settings show the size, and clear the cache once asked twice | PicturesTest; Compose settings

## test/heart_burst_test.dart
untested | heart_burst_test › liking plays the burst around the heart without moving the footer | Compose (motion) | Compose only
untested | heart_burst_test › unliking just empties the heart | Compose (motion) | Compose only
untested | heart_burst_test › $name keeps the heart still | Compose (motion) | Compose only
untested | heart_burst_test › every stage of the burst paints, and only a new moment repaints | Compose (motion) | Compose only

## test/grow_in_test.dart
untested | grow_in_test › a floating button grows in as it fades in | Compose (motion) | Compose only
untested | grow_in_test › it shrinks away as it fades out, and cannot be tapped or read meanwhile | Compose (motion) | Compose only
untested | grow_in_test › hidden, it does not lift a floating snack bar | Compose (motion) | Compose only
covered | grow_in_test › with reduced motion it comes and goes at once | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce

## test/conversation_sort_widget_test.dart
covered | conversation_sort_widget_test › the reply sort reorders a Bluesky thread for the session | ConversationRulesTest; plugins/bluesky BlueskyRepliesTest
covered | conversation_sort_widget_test › Mastodon falls back to its own order and sorts on device | MastodonThreadsTest
covered | conversation_sort_widget_test › a plugin quote list offers device orders above its items | PostRulesTest.theSortsAskXForWhatItRanksItselfAndRepostersSortByFollowersOnlyWithCounts
covered | conversation_sort_widget_test › X reposters offer follower order only with follower counts | PostRulesTest.theSortsAskXForWhatItRanksItself...

## test/browser_picker_test.dart
untested | browser_picker_test › the destination tile sits above the clean-links switch | Compose (link settings) | Compose only
covered | browser_picker_test › picking a named browser turns the in-app view off | LinkOpenerTest (chosen browser)
covered | browser_picker_test › picking in-app again leaves the last browser stored | LinkOpenerTest
covered | browser_picker_test › installedBrowsers maps the platform list | platform (Android) | platform boundary

## test/boost_runs_test.dart
covered | boost_runs_test › detects retweet chains | PostRulesTest.runsOfTwoOrMoreRepostsFoldIntoOneRow
covered | boost_runs_test › groups consecutive boosts of length >= 2 | PostRulesTest.runsOfTwoOrMoreRepostsFoldIntoOneRow
covered | boost_runs_test › leaves isolated boosts as single chains | PostRulesTest.runsOfTwoOrMoreRepostsFoldIntoOneRow
covered | boost_runs_test › returns run length only at the first chain of a run | PostRulesTest.runsOfTwoOrMoreRepostsFoldIntoOneRow

## test/sensitive_media_gate_test.dart
covered | sensitive_media_gate_test › ordinary media does not rebuild when hideSensitive flips | PostRulesTest.onlyAPostMarkedSensitiveWithMediaIsGated
covered | sensitive_media_gate_test › gated media appears once hideSensitive is cleared | PostRulesTest.onlyAPostMarkedSensitiveWithMediaIsGated
covered | sensitive_media_gate_test › sensitive reveal is immediate when motion is disabled | MotionTest.withMotionDisabledAListJumpsToItsTopAtOnce

## test/quotes_retweeters_list_test.dart
covered | quotes_retweeters_list_test › the retweets tab starts its first fetch without mounting a paged list | ConversationRulesTest
covered | quotes_retweeters_list_test › an empty first page leaves the spinner for the empty message | ConversationRulesTest
covered | quotes_retweeters_list_test › a failed first page leaves the spinner for the error | ConversationRulesTest

## test/provenance_accent_test.dart
covered | provenance_accent_test › maps known plugin ids to their brand colours | ui/feed ProvenanceColorsTest
covered | provenance_accent_test › provenanceAccent lays out in unbounded height without crashing | ProvenanceColorsTest
covered | provenance_accent_test › lightens near-black Threads brand on dark theme | ProvenanceColorsTest

## test/haptic_feedback_test.dart
covered | haptic_feedback_test › XTA's taps play while haptic feedback is on, as it is unless turned off | SwitchedHapticsTest
covered | haptic_feedback_test › turning haptic feedback off silences every one of them | SwitchedHapticsTest
covered | haptic_feedback_test › the switch sits in Accessibility, on, and turns haptic feedback off | SwitchedHapticsTest

## test/groups_model_test.dart
covered | groups_model_test › create group then reload works on a fresh migrated DB | SessionTest; GroupBoardModelTest
covered | groups_model_test › importing an old-format backup brings groups back | XtaBackupTest
covered | groups_model_test › NSFW groups sink below safe ones after toggle | GroupBoardModelTest.nsfwGroupsGetASectionOfTheirOwn

## test/gif_download_test.dart
covered | gif_download_test › a GIF saves its MP4, not the poster frame | MediaRulesTest; DownloadNaming
covered | gif_download_test › a video saves the variant its player is on, else the best MP4 | MediaRulesTest.aDownloadSavesTheVariantWatchedOrTheBest
covered | gif_download_test › a photo, or a stream with no MP4, has no video to save | MediaRulesTest.qualitiesAreTheMp4sHighestFirstNamedByTheirHeight

## test/error_layout_test.dart
covered | error_layout_test › does not overflow, and keeps its actions reachable | ProblemLookTest
covered | error_layout_test › the emoji screen keeps both of its buttons | ProblemLookTest
covered | error_layout_test › at the ordinary text size it still centres rather than scrolling | ProblemLookTest

## test/discover_ia_test.dart
untested | discover_ia_test › the home Search tab is labeled Discover | Compose (Discover) | Compose only
untested | discover_ia_test › an empty trends list explains itself instead of going blank | Compose (Discover) | Compose only
covered | discover_ia_test › Discover offers people search and antennas | SettingsLogicTest; Compose

## test/desktop_pager_test.dart
untested | desktop_pager_test › the buttons show only where there is a page to turn to | Compose (desktop viewer) | Compose only
covered | desktop_pager_test › ← and → turn the pages, and stop at either end | DesktopKeysTest (pager keys)
covered | desktop_pager_test › off the desktop the viewer is as it was | Compose (viewer) | Compose only

## test/custom_reader_gestures_test.dart
untested | custom_reader_gestures_test › Reddit body defers to Home or switches discovery sections | Compose (plugin reader) | Compose only
untested | custom_reader_gestures_test › Stocks body defers to Home or switches sections | Compose (plugin reader) | Compose only
untested | custom_reader_gestures_test › Threads profile body swipes and visible tabs share selection without reloading | Compose (Threads profile) | Compose only

## test/avatar_follow_badge_test.dart
untested | avatar_follow_badge_test › the badge sits on the avatar rim at the lower-right diagonal ($size) | Compose (avatar) | Compose only
untested | avatar_follow_badge_test › one tap follows, the plus becomes a check, then the badge leaves | Compose (avatar) | Compose only
covered | avatar_follow_badge_test › following taps lightly and a long press clicks, unless haptic feedback is off | SwitchedHapticsTest | the gesture is Compose-only

## test/annotation_backup_test.dart
covered | annotation_backup_test › JSON backup restores annotation text, tags and extraction exactly | store/saved ArchiveNotesTest
covered | annotation_backup_test › restore preserves current edits and merges additional highlights and tags | ArchiveNotesTest
covered | annotation_backup_test › legacy backups and unrelated sidecar keys cannot erase existing data | ArchiveNotesTest

## test/search_results_entry_test.dart
untested | search_results_entry_test › empty X Search reuses recent history and library search access | Compose (search) | Compose only
untested | search_results_entry_test › compact enlarged search keeps input and actions reachable | Compose (search) | Compose only

## test/save_sheet_render_test.dart
untested | save_sheet_render_test › save-sheet-$tag | Compose (render sweep) | golden/layout sweep
untested | save_sheet_render_test › save-sheet-create-$tag | Compose (render sweep) | golden/layout sweep

## test/loaded_feed_search_test.dart
covered | loaded_feed_search_test › loaded text includes names, handles, expanded URLs and quoted posts | SavedContentTest
covered | loaded_feed_search_test › plugin snapshots remain searchable without fetching or building posts | SavedContentTest; PluginArchiveTest

## test/group_sheet_render_test.dart
untested | group_sheet_render_test › group-sheet-$tag | Compose (render sweep) | golden/layout sweep
untested | group_sheet_render_test › group-sheet-create-$tag | Compose (render sweep) | golden/layout sweep

## test/database_single_connection_test.dart
n/a | database_single_connection_test › reads and writes share one connection | - | XTA's SQLite; native keeps files
n/a | database_single_connection_test › adding a timeline author to a group does not lock out every read and write | - | XTA's SQLite; native keeps files

## test/cashtag_quotes_test.dart
covered | cashtag_quotes_test › cashtags on a post are uppercased and unique | CashtagsTest.cashtagsOnAPostAreUpperCasedAndUnique
covered | cashtag_quotes_test › a post with no symbols has none | CashtagsTest.aRepostsStripIsTheOriginalsAndAPostWithoutCashtagsHasNone

## test/broadcast_media_card_test.dart
covered | broadcast_media_card_test › broadcast cards identify the stream and keep open-post context | LiveRoomsTest
untested | broadcast_media_card_test › Space cards fit a narrow RTL viewport with large text | Compose (live card) | Compose only

## test/boost_card_profile_test.dart
untested | boost_card_profile_test › a repost card names whose post it is and opens that profile | Compose (post card) | Compose only
untested | boost_card_profile_test › a tap on the post text still opens the post | Compose (post card) | Compose only

## test/article_reader_controls_test.dart
covered | article_reader_controls_test › appearance changes are shared, finish is explicit and controls fit narrow large-text RTL | ReadingAppearanceStore (reading/ReadingPlaces); Compose layout
untested | article_reader_controls_test › production reader controls render $variant | Compose (render sweep) | golden/layout sweep

## test/app_icon_asset_test.dart
covered | app_icon_asset_test › the app bundles its own icon at 512 px | AppResourcesTest
covered | app_icon_asset_test › the 2000 px source icon is not bundled | AppResourcesTest

## test/android_secure_window_test.dart
n/a | android_secure_window_test › the desktop never calls the Android screenshot channel | - | Android platform boundary
n/a | android_secure_window_test › Android still releases FLAG_SECURE through the channel | - | Android platform boundary

## test/note_post_layout_test.dart
untested | note_post_layout_test › post-style notes $variant | Compose (render sweep) | golden/layout sweep

## test/locale_delegates_test.dart
n/a | locale_delegates_test › a Scaffold builds in ${locale.toLanguageTag()} | - | Flutter widget binding

## test/app_reading_layout_test.dart
untested | app_reading_layout_test › app reading layout $variant | Compose (render sweep) | golden/layout sweep
