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
open | home_first_run_strip_test › a saved strip is kept as it is and later installs are not added | FeedStrip.migrated (tested, not wired) | native never auto-adds installs (kept); the one-time migration of a pre-hand-picked XTA backup is not run
open | home_first_run_strip_test › a strip that was never saved keeps every timeline it showed | FeedStrip.migrated | same: only matters for an old XTA backup restored into native; rule tested in FeedStripTest, not run on load
open | home_first_run_strip_test › a hidden-tab plugin Home added on its own is written into the strip | FeedStrip.migrated | same
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
untested | home_hiding_header_test › the header leaves with the bottom bar and returns with it | HomeFeed.kt (header uses the same HomeBarsState) | rule itself in BarHiding (HomeBarTest)

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
open | home_navigation_visibility_test › a screen reader keeps it on screen | HomeBottomBar.kt | Compose Multiplatform gives commonMain no "screen reader on" signal; not implemented
untested | home_navigation_visibility_test › with the setting off the bar stays | rememberHomeBars(enabled) | Compose state holder
untested | home_navigation_visibility_test › the bar tracks the finger, then settles away when it lifts | HomeBarsState | rule in BarHiding (HomeBarTest)
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
untested | home_timeline_layout_test › On the desktop, where a mouse cannot pull it down, Following shows refresh | FollowingBar.actions(desktop) = 4 (refresh) | FollowingBarTest.twoRowsAreTwiceAsTall (actions(desktop))
untested | home_timeline_layout_test › Home keeps nothing for the status bar under its header | HomeScreen.kt insets | Compose layout
untested | home_timeline_layout_test › A short scrollable Home keeps its bar stable | HomeBottomBar.kt HomeBarsState (nothing to scroll → no leftover → bar stays) | Compose
untested | home_timeline_layout_test › Source picker preserves a reading position and opens timeline management | SourcePickerSheet.kt, StripSheet.kt | Compose; pages keep their own LazyListState
untested | home_timeline_layout_test › reading swipes switch source and return to cached posts and position | TimelineSwipeView.kt, GroupFeedViewModel (FeedSessionCache) | GroupFeedViewModelTest checks a reopened feed shows its kept posts
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
