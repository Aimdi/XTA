# Pixiv: XTA's tests against XTA Native

One line per XTA test: `status | test file › test name | native place (file) | note`.
Native paths are under `native/plugins/pixiv/src/`; tests under `desktopTest/.../plugins/pixiv/` or `.../networks/pixiv/`.
Statuses: **covered** (native does it, a native test checks it), **ported** (native did it untested; the test is ported now),
**fixed** (native lacked it or differed; implemented now and tested), **untested** (native does it, only in Compose UI, no test),
**n/a** (Flutter mechanics with nothing for the reader to see).

## pixiv_accounts_test.dart
ported | pixiv_accounts_test › read each user once and skip entries without an id or token | PixivPeople.kt (PixivAccounts.of) | PixivAccountsTest.eachUserIsReadOnce…
ported | pixiv_accounts_test › an account is replaced in place, a new one is added last | PixivPeople.kt (PixivAccounts.with/without) | PixivAccountsTest.anAccountIsReplacedInPlace…
covered | pixiv_accounts_test › the token response user brings the avatar used in the switcher | networks/PixivAuth.kt (PixivAuthUser.parse) | PixivAuthTest.theTokenResponsesUserReadsAQuotedIdAndPremium
ported | pixiv_accounts_test › adds and switches accounts; switching clears the access token | PixivSession.kt (switchTo) | PixivAccountsTest.switchingPutsTheStoredAccountInUse…
ported | pixiv_accounts_test › a token Pixiv rotated since storing is kept when switching away | PixivSession.kt, PixivPeople.kt (keepingActive) | PixivAccountsTest.aTokenPixivRotated…; PixivPeopleTest too
ported | pixiv_accounts_test › signing out forgets only the active account and hands over to another | PixivSession.kt (signOut) | PixivAccountsTest.signingOutForgets…
ported | pixiv_accounts_test › a token check answered after a switch reports, and keeps, only the account now in use | PixivSession.verified, networks/PixivClient.kt | PixivAccountsTest.aTokenCheckAnsweredAfterASwitch…; client half in PixivClientTest
covered | pixiv_accounts_test › a user is only kept with the token of the account stored as in use | PixivPeople.kt (remembered) | PixivPeopleTest.aSignInIsRememberedOnlyForTheAccountInUse
ported | pixiv_accounts_test › an account never checked since it signed in is kept by id before another takes over | PixivPeople.kt (keepingActive) | PixivAccountsTest.anAccountNeverChecked… (display name "5" added)
ported | pixiv_accounts_test › a token typed in Settings has no owner, so a switch keeps every stored token | PixivSession.useToken | PixivAccountsTest.aTokenTypedInSettings…
ported | pixiv_accounts_test › a token entered in Settings is a switch: the last account keeps its token and its marks go | PixivSession.useToken, PixivRuntime.accountChanged | PixivAccountsTest.aTokenEnteredInSettingsIsASwitch…
ported | pixiv_accounts_test › a switch from the plugin's Settings page reloads Favorites for the new account | PixivHomeModel.kt (PixivSessionKey) | PixivAccountsTest.aSwitchMadeAnywhereReloadsFavorites…
covered | pixiv_accounts_test › a screen built again after a switch never shows the last account's lists | PixivHomeModel.kt | a new model starts empty; the switch test above checks the old one empties
ported | pixiv_accounts_test › the accounts key is a secret, so exports and crash reports leave it out | store XtaPrefs.kt (SecretPrefs) | PixivAccountsTest.theAccountsAreASecret…
ported | pixiv_accounts_test › whose the tokens are and the saved pages stay on the device, both ways | store XtaPrefs.kt (SecretPrefs) | PixivAccountsTest.whoseTheTokensAre…; XtaPrefsTest too
ported | pixiv_accounts_test › restoring another account's backup leaves the account in use and every stored token alone | store Settings.restoring, PixivSession | PixivAccountsTest.restoringAnotherAccountsBackup…
ported | pixiv_accounts_test › the More hub shows the active account and switches from its sheet | PixivMore.kt (AccountHeader, AccountSwitcher) | state half in PixivAccountsTest.aSwitchDropsWhatWasLoaded…; header/Premium badge drawn in Compose
untested | pixiv_accounts_test › cancelling a removal leaves the account switcher open | PixivMore.kt (PixivAccountList) | ask sheet's cancel does nothing, confirm closes; Compose only. Removal itself: PixivAccountsTest.removingAnAccountKeepsTheOthers
n/a | pixiv_accounts_test › embedded in Home, the More list scrolls with Home's controller | PixivMore.kt (ListToTop) | Flutter's PrimaryScrollController wiring; native scrolls its own list to the top on a second tap
n/a | pixiv_accounts_test › the More hub fits a narrow screen with large text | PixivMore.kt | Flutter overflow assertion; Compose rows wrap (FlowRow)

## pixiv_auth_test.dart
covered | pixiv_auth_test › generatePkce produces verifier and S256 challenge | networks/PixivAuth.kt (PixivLogin.pkce) | PixivAuthTest.pkceMakesAVerifierAndItsS256Challenge
covered | pixiv_auth_test › each generatePkce call is unique | networks/PixivAuth.kt | PixivAuthTest.pkceMakesAVerifierAndItsS256Challenge
covered | pixiv_auth_test › loginUri includes PKCE parameters | networks/PixivAuth.kt (loginUrl) | PixivAuthTest.theLoginPageCarriesThePkceChallenge
covered | pixiv_auth_test › reads code from https callback | networks/PixivAuth.kt (codeFrom) | PixivAuthTest.theCodeComesFromPixivsCallbackOrItsAppScheme
covered | pixiv_auth_test › reads code from pixiv:// scheme | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › reads code from pixiv://account/login path | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › returns null for unrelated URLs | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › returns null when code is missing | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › detects error query on https callback | networks/PixivAuth.kt (deniedIn) | PixivAuthTest.aRefusedSignInIsReadFromTheRedirect
covered | pixiv_auth_test › detects error query on pixiv scheme | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › returns false when no error | networks/PixivAuth.kt | same test
covered | pixiv_auth_test › posts authorization_code grant and parses tokens | networks/PixivClient.kt (exchangeCode) | PixivAuthTest.theCodeExchangeIsSigned…
covered | pixiv_auth_test › 401 becomes unauthorized | networks/PixivClient.kt | PixivAuthTest.aCodePixivRefusesIsUnauthorized…

## pixiv_bookmark_actions_test.dart
ported | pixiv_bookmark_actions_test › auto-tags are the work's own tags without popularity tags | PixivBookmarking.kt (autoTags) | PixivBookmarkActionsTest.autoTagsAreTheWorksOwnTags… (東方100users入り too)
covered | pixiv_bookmark_actions_test › the heart bookmarks publicly unless the reader chose private | PixivBookmarking.kt | PixivBookmarkingTest.theHeartBookmarksPublicly…, theDefaultVisibilityAndAutoTags…
ported | pixiv_bookmark_actions_test › the editor's visibility wins over the default | PixivBookmarking.save | PixivBookmarkActionsTest.theEditorsVisibilityWins…
covered | pixiv_bookmark_actions_test › sends the work's tags when on and no tags were chosen | PixivBookmarking.kt | PixivBookmarkingTest.theDefaultVisibilityAndAutoTagsComeFromTheSettings
ported | pixiv_bookmark_actions_test › explicit tags from the editor are sent as they are, even none | PixivBookmarking.save | PixivBookmarkActionsTest.explicitTagsFromTheEditor…
covered | pixiv_bookmark_actions_test › off sends no tags | PixivBookmarking.kt | PixivBookmarkingTest.theHeartBookmarksPublicly… (form has no tags)
covered | pixiv_bookmark_actions_test › follows an author the reader does not follow yet, publicly | PixivBookmarking.followAuthor | PixivBookmarkingTest.aNewBookmarkFollowsTheCreator…
ported | pixiv_bookmark_actions_test › leaves a followed author, the reader's own work and a re-filed bookmark alone | PixivBookmarking.kt | PixivBookmarkingTest.noOneIsFollowed… + PixivBookmarkActionsTest.aRefiledBookmarkFollowsNoOne
covered | pixiv_bookmark_actions_test › off follows no one | PixivBookmarking.kt | PixivBookmarkingTest.theHeartBookmarksPublicly… (no follow call)
covered | pixiv_bookmark_actions_test › a new bookmark saves the work once | PixivBookmarking.kt (saveAll), PixivSheets.landed | PixivBookmarkingTest.aNewBookmarkFollowsTheCreatorOnPixivAndAsksToSave…
ported | pixiv_bookmark_actions_test › removing, re-filing or bookmarking after a save does not save again | PixivBookmarking.kt | PixivBookmarkActionsTest.removingRefilingOrBookmarkingAfterASave…
ported | pixiv_bookmark_actions_test › bookmarks a saved work with the defaults when on | PixivBookmarking.afterSave | PixivBookmarkActionsTest.aSavedWorkIsBookmarkedWithTheDefaults
covered | pixiv_bookmark_actions_test › does nothing when off or already bookmarked | PixivBookmarking.afterSave | PixivBookmarkingTest.bookmarkAfterSaveOnlyWhenAskedAndNotYetBookmarked
covered | pixiv_bookmark_actions_test › brings a card that missed a bookmark made elsewhere in line | PixivBookmarking.detail | PixivBookmarkingTest.theDetailBringsAStaleCardInLine
ported | pixiv_bookmark_actions_test › leaves a write still on its way to set the heart | PixivBookmarking.detail | PixivBookmarkActionsTest.theDetailLeavesAWriteStillOnItsWay…
ported | pixiv_bookmark_actions_test › so saving that bookmark in the editor is a re-file: no follow, no save | PixivBookmarking.kt | PixivBookmarkActionsTest.soSavingThatBookmarkInTheEditorIsARefile…
covered | pixiv_bookmark_actions_test › bookmarks, then removes, keeping the store in step | PixivBookmarking.kt | PixivBookmarkingTest.theHeartBookmarksPubliclyAndTakesTheBookmarkAway…
ported | pixiv_bookmark_actions_test › a failed write leaves the bookmark as it was and frees the work | PixivBookmarking.kt, PixivMarks.kt | PixivBookmarkActionsTest.aFailedWriteLeavesTheBookmark…

## pixiv_bookmark_after_save_test.dart
ported | pixiv_bookmark_after_save_test › saving a page from the work bookmarks it with the defaults | PixivSheets.kt (savePages → afterSave) | PixivBookmarkActionsTest.savingAPageFromTheWork…
ported | pixiv_bookmark_after_save_test › saving every page bookmarks once | PixivSheets.saveAll | PixivBookmarkActionsTest.savingEveryPageBookmarksOnce
ported | pixiv_bookmark_after_save_test › a save that never started or the setting off bookmarks nothing | PixivSheets.afterSave | PixivBookmarkActionsTest.aSaveThatNeverHappened…
ported | pixiv_bookmark_after_save_test › saving from a tile's long-press bookmarks it too | PixivWorkSheets.kt → PixivSheets.saveAll | the tile's sheet saves through the same PixivSheets.saveAll, tested above
ported | pixiv_bookmark_after_save_test › an already bookmarked work is left as it is | PixivSheets.kt | PixivBookmarkActionsTest.anAlreadyBookmarkedWorkIsLeftAsItIs
ported | pixiv_bookmark_after_save_test › the Bookmarking settings switch their preferences | PixivSettings.kt (bookmarkSwitches, now a testable list) | PixivBookmarkActionsTest.theBookmarkingSettingsSwitchTheirPreferences

## pixiv_bookmark_api_test.dart
covered | pixiv_bookmark_api_test › reads visibility and which tags the bookmark is filed under | networks/PixivBookmarks.kt | PixivBookmarksTest.aWorksBookmarkSaysItsVisibility…
covered | pixiv_bookmark_api_test › a missing or reshaped detail reads as a public work not bookmarked | networks/PixivBookmarks.kt | PixivBookmarksTest.aMissingOrReshapedDetail…
covered | pixiv_bookmark_api_test › add sends the visibility and every tag space-joined in one tags[] field | networks/PixivBookmarks.kt | PixivWritesTest.aWorkIsBookmarkedWithItsTagsSpaceJoinedInOneField
covered | pixiv_bookmark_api_test › add without tags sends no tags[] field | networks/PixivBookmarks.kt | PixivWritesTest.aBookmarkWithoutTagsSendsNoTagField
covered | pixiv_bookmark_api_test › delete posts the work id | networks/PixivBookmarks.kt | PixivWritesTest.aWorksBookmarkIsDeleted
covered | pixiv_bookmark_api_test › the first page asks for the reader's tags of one visibility, later pages follow next_url | networks/PixivBookmarks.kt | PixivBookmarksTest.theFirstTagPageAsks…
covered | pixiv_bookmark_api_test › all tags follow next_url page after page, up to the page limit | networks/PixivBookmarks.kt | PixivBookmarksTest.allTagsFollowNextUrl…
covered | pixiv_bookmark_api_test › a reshaped tag list is an empty last page | networks/PixivBookmarks.kt | PixivBookmarksTest.aReshapedTagListIsAnEmptyLastPage
covered | pixiv_bookmark_api_test › Favorites narrows to a tag, 未分類 for untagged ones, and sends none for all | networks/PixivClient.kt (bookmarks) | PixivBookmarksTest.theReadersFavoritesNarrowToATag…
ported | pixiv_bookmark_api_test › suggests at most eight matches, ignoring case | PixivBookmarking.kt (PixivBookmarkDraft.matches) | PixivBookmarkEditorTest.atMostEightMatchesAreSuggested…
ported | pixiv_bookmark_api_test › an empty query suggests nothing | PixivBookmarking.kt | PixivBookmarkEditorTest.anEmptyQuerySuggestsNothing
ported | pixiv_bookmark_api_test › typed text becomes tags split on whitespace | PixivSearchMemory.kt (PixivTyping.words) | PixivBookmarkEditorTest.typedTextBecomesTagsSplitOnWhitespace

## pixiv_bookmark_editor_test.dart
covered | pixiv_bookmark_editor_test › a work not bookmarked yet takes the default visibility and pre-checks auto-tags | PixivBookmarking.kt (PixivBookmarkDraft.fromDetail) | PixivBookmarkingTest.theEditorStartsFromPixivsDetail; also PixivBookmarkEditorTest
ported | pixiv_bookmark_editor_test › an existing bookmark keeps its own visibility and tags | PixivBookmarkDraft.fromDetail | PixivBookmarkEditorTest.anExistingBookmarkKeeps…
ported | pixiv_bookmark_editor_test › typed tags go on top checked; listed ones are checked where they are | PixivBookmarkDraft.withTyped | PixivBookmarkEditorTest.typedTagsGoOnTop…
ported | pixiv_bookmark_editor_test › select all and clear all | PixivBookmarkDraft.allToggled/toggled | PixivBookmarkEditorTest.selectAllAndClearAll
ported | pixiv_bookmark_editor_test › suggestions come from the reader's own tags | PixivBookmarkDraft.matches, PixivBookmarkEditor.kt (rememberKnownTags: public+private, distinct) | PixivBookmarkEditorTest.suggestionsComeFromTheReadersOwnTags
untested | pixiv_bookmark_editor_test › a long press on a tile's heart opens the editor, and Save sends the checked tags and visibility | PixivBookmarkEditor.kt, PixivGrid.kt | Compose; the draft's saved tags/visibility are tested (saveTakesATagStillInTheField)
untested | pixiv_bookmark_editor_test › an existing bookmark opens with its visibility and tags, and can be removed | PixivBookmarkEditor.kt (EditorTitle, Remove) | Compose; remove write in PixivBookmarkingTest
untested | pixiv_bookmark_editor_test › typing suggests the reader's own tags and picking one adds it checked | PixivBookmarkEditor.kt (TagField) | Compose; matching tested
untested | pixiv_bookmark_editor_test › a failed load offers a retry that loads again | PixivBookmarkEditor.kt | Compose (failure → Retry bumps attempt)
ported | pixiv_bookmark_editor_test › a failed save keeps the editor open with the reason | PixivSheets.editBookmark | PixivBookmarkEditorTest.aFailedSaveKeepsTheEditorOpenWithTheReason
n/a | pixiv_bookmark_editor_test › large text on a small screen keeps every control reachable | PixivBookmarkEditor.kt (PixivSheetFrame scrolls) | Flutter overflow assertion
n/a | pixiv_bookmark_editor_test › landscape with large text and the keyboard up does not overflow | PixivBookmarkEditor.kt | Flutter overflow assertion
n/a | pixiv_bookmark_editor_test › the buttons stay above the navigation bar | PixivBookmarkEditor.kt | window insets; Compose's ModalBottomSheet pads them
ported | pixiv_bookmark_editor_test › Save takes a tag still in the field | PixivBookmarkDraft.saved | PixivBookmarkEditorTest.saveTakesATagStillInTheField
untested | pixiv_bookmark_editor_test › typing suggests the reader's tags from later pages too | PixivBookmarkEditor.kt (allTags, ten pages) | allTags paging in PixivBookmarksTest; the editor reading it is Compose
covered | pixiv_bookmark_editor_test › a card that missed a bookmark made elsewhere shows it once the editor loads | PixivBookmarking.detail | PixivBookmarkingTest.theDetailBringsAStaleCardInLine
untested | pixiv_bookmark_editor_test › Save and Remove wait while another write for the work runs | PixivBookmarkEditor.kt (writing = saving or busy) | Compose; busy marks tested in PixivBookmarkingTest.aSecondTapWaits…
ported | pixiv_bookmark_editor_test › while it saves the sheet stays put, and a failure after a drag away is still told | PixivSheets.editBookmark | PixivBookmarkEditorTest.aFailureAfterTheEditorWasDraggedAwayIsStillTold
ported | pixiv_bookmark_editor_test › a save that lands after a drag away still buzzes and names the author followed | PixivSheets.editBookmark/landed | PixivBookmarkEditorTest.aSaveThatLandsAfterADragAway…
n/a | pixiv_bookmark_editor_test › closing it while the bookmark detail loads throws nothing | PixivBookmarkEditor.kt | Flutter setState-after-dispose; a LaunchedEffect is cancelled with the sheet
untested | pixiv_bookmark_editor_test › Edit bookmark in the detail menu opens the editor without a long press's buzz | PixivWorkSheets.kt | Compose
untested | pixiv_bookmark_editor_test › is a 48 dp labelled button with tap and long-press actions | PixivGrid.kt (heart) | Compose semantics
ported | pixiv_bookmark_editor_test › a tap bookmarks with the defaults and buzzes lightly; a long press buzzes firmer | PixivSheets.toggleBookmark, PixivHaptics | PixivBookmarkEditorTest.aTapBookmarksWithTheDefaultsAndBuzzesLightly (the long press's medium buzz is in Compose)
ported | pixiv_bookmark_editor_test › following the author after a bookmark says so | PixivSheets.landed | PixivBookmarkEditorTest.followingTheAuthorAfterABookmarkSaysSo
