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

## pixiv_bookmark_store_test.dart
covered | pixiv_bookmark_store_test › isBookmarked uses the illust flag until a write is recorded | PixivMarks.kt (PixivMarks.on/count) | PixivBookmarkingTest.theCountMovesWithWhatThisSessionChanged
covered | pixiv_bookmark_store_test › a recorded bookmark or removal adjusts the count | PixivMarks.kt | PixivBookmarkingTest.theCountMovesWithWhatThisSessionChanged
covered | pixiv_bookmark_store_test › a removal never drives the count below zero | PixivMarks.kt | PixivBookmarkingTest.theCountMovesWithWhatThisSessionChanged
covered | pixiv_bookmark_store_test › one write per work at a time; a second one is skipped | PixivMarks.kt (exclusive) | PixivBookmarkingTest.aSecondTapWaitsForTheFirstWrite
covered | pixiv_bookmark_store_test › a failed write frees the work and rethrows | PixivMarks.kt | PixivBookmarkActionsTest.aFailedWriteLeavesTheBookmarkAsItWasAndFreesTheWork

## pixiv_bookmark_tag_picker_test.dart
ported | pixiv_bookmark_tag_picker_test › lists All, Uncategorized and the reader's tags with counts, and returns the one picked | PixivFavorites.kt (BookmarkTagPicker), PixivSections.kt (tagLabel) | PixivFavoritesTest.theChipAndThePickerSay…; the list itself is Compose
ported | pixiv_bookmark_tag_picker_test › Uncategorized filters by Pixiv's 未分類 tag | PixivKept.kt (UNTAGGED) | PixivFavoritesTest.theChipAndThePickerSay…
untested | pixiv_bookmark_tag_picker_test › typing suggests at most eight matches, ignoring case, and takes any typed tag | PixivFavorites.kt (TagRows, "Use …") | Compose; matching tested in PixivBookmarkEditorTest
missing | pixiv_bookmark_tag_picker_test › the Private tab lists private tags and picks privately | PixivHomeModel.favorites(picked), PixivSections.kt | implemented: a tag picked on the Private tab was dropped as the visibility changed; PixivFavoritesTest.theFavoritesListLoadsTheVisibilityAndTagPicked
n/a | pixiv_bookmark_tag_picker_test › large text on a small screen still lays out | PixivFavorites.kt | Flutter overflow assertion
n/a | pixiv_bookmark_tag_picker_test › landscape with large text and the keyboard up does not overflow | PixivFavorites.kt | Flutter overflow assertion
untested | pixiv_bookmark_tag_picker_test › scrolling pages on through next_url | PixivFavorites.kt (TagRows nearEnd → PixivList.more) | Compose scroll; PixivList paging tested in PixivPagingTest
untested | pixiv_bookmark_tag_picker_test › a first page that does not fill the sheet asks for the next by itself | PixivFavorites.kt (nearEnd while all rows show) | Compose
untested | pixiv_bookmark_tag_picker_test › a search pages on until it finds what is typed | PixivFavorites.kt (PixivTagList LaunchedEffect) | Compose
untested | pixiv_bookmark_tag_picker_test › a page that fails stops a search from asking until the next keystroke | PixivFavorites.kt | Compose; a failed more() sets moreFailed and hasMore stays, the effect keys on query/size/hasMore so it does not loop
n/a | pixiv_bookmark_tag_picker_test › closing it while the tags load throws nothing | PixivFavorites.kt | Flutter setState-after-dispose; the list's scope ends with the sheet
untested | pixiv_bookmark_tag_picker_test › opens on the visibility being shown, with its tag selected | PixivFavorites.kt (shown = sources.indexOf) | Compose
ported | pixiv_bookmark_tag_picker_test › the tag chip shows All and opens the picker | PixivSections.kt (FavoritesSection chip, tagLabel) | PixivFavoritesTest.theChipAndThePickerSay…
ported | pixiv_bookmark_tag_picker_test › a chosen tag shows on the chip, which clears back to All | PixivHomeModel.favorites | PixivFavoritesTest.aChosenTagClearsBackToAll…
missing | pixiv_bookmark_tag_picker_test › the Favorites list loads the visibility and tag picked | PixivHomeModel.favorites(picked = true) | implemented (same fix); PixivFavoritesTest.theFavoritesListLoadsTheVisibilityAndTagPicked

## pixiv_client_test.dart
covered | pixiv_client_test › refreshAccessToken stores tokens and returns the user | networks/PixivClient.kt | PixivClientTest.aRefreshKeepsBothTokensAndNamesTheAccount
covered | pixiv_client_test › following uses the access token and parses illusts | networks/PixivClient.kt | PixivAuthTest.signingInPutsTheTokens…, PixivClientTest.aSwitchedAccountRefreshesBeforeItsFirstRead
covered | pixiv_client_test › missing refresh token is notConfigured | networks/PixivClient.kt | PixivClientTest.withoutARefreshTokenThereIsNoAccount…
covered | pixiv_client_test › every request carries the time and its hash, and they agree | networks/PixivClient.kt | PixivClientTest.everyRequestCarriesTheTimeAndItsHash…
covered | pixiv_client_test › the API requests are signed the same way | networks/PixivClient.kt | PixivClientTest.theApiReadsAreSignedTheSameWay…
covered | pixiv_client_test › ranking and search hit the expected paths and query parameters | networks/PixivDiscovery.kt, PixivSearch.kt | PixivDiscoveryTest, PixivSearchTest
covered | pixiv_client_test › followUser posts to the follow-add endpoint | networks/PixivSocial.kt | PixivWritesTest.aCreatorIsFollowedAndUnfollowed
covered | pixiv_client_test › Pixiv's "Rate Limit" answer reads as rate limiting, not a refused token | networks/PixivClient.kt, PixivErrors.kt | PixivClientTest.pixivsRateLimitAnswerReadsAsRateLimiting…
covered | pixiv_client_test › a refused token surfaces what Pixiv actually said | networks/PixivClient.kt | PixivClientTest.aRefusedRefreshSaysWhatPixivSaid
covered | pixiv_client_test › concurrent refreshAccessToken calls share one network round-trip | networks/PixivClient.kt | PixivClientTest.refreshesAskedForTogetherShareOneRoundTrip
covered | pixiv_client_test › ensureUserId reuses a stored id without forcing another refresh | networks/PixivClient.kt | PixivClientTest.ensureUserIdReusesAStoredId…
covered | pixiv_client_test › related keeps R-18 when the opened illust is R-18 | networks/PixivClient.kt | PixivClientTest.relatedWorksOfAnR18SeedKeepR18
covered | pixiv_client_test › ranking is /v1/illust/ranking; likes are bookmarks with user_id | networks/PixivClient.kt, PixivDiscovery.kt | PixivClientTest.theCoreReadsAskTheirOwnPaths…, PixivDiscoveryTest

## pixiv_client_transport_test.dart
covered | pixiv_client_transport_test › maps XTA locales onto the five languages Pixiv answers in | networks/PixivClient.kt | PixivClientTest.acceptLanguageMapsLocales…
covered | pixiv_client_transport_test › every request carries the active locale, token requests included | networks/PixivClient.kt | PixivClientTest.everyRequestCarriesTheActiveLocale…
covered | pixiv_client_transport_test › a 400 about the OAuth process refreshes once and replays the request | networks/PixivClient.kt | PixivClientTest.aTokenRefusedWithA400AboutOAuth…
covered | pixiv_client_transport_test › requests refused together share one refresh | networks/PixivClient.kt | PixivClientTest.requestsRefusedTogetherShareOneRefresh
covered | pixiv_client_transport_test › a refusal after the refresh is reported, not retried forever | networks/PixivClient.kt | PixivClientTest.aRefusalAfterTheRefreshIsReported…
covered | pixiv_client_transport_test › any other 400 is a bad response and leaves the token alone | networks/PixivClient.kt | PixivClientTest.anyOther400IsABadResponse…
covered | pixiv_client_transport_test › a connection closed before any header is sent again once | core XHttp.kt (OkHttpXHttp.get) | core XClientParityTest.aFailedRequestIsTriedOnceMoreButNotOneThatTimedOut (the app's shared transport)
covered | pixiv_client_transport_test › a second drop is a network error | core XHttp.kt, networks/PixivErrors.kt | same core test; an IOException reads as NETWORK (PixivBookmarkEditorTest)
missing | pixiv_client_transport_test › other failures are not retried | core XHttp.kt | not changed: the app's shared transport retries any failed GET once (as XTA's X client does), a refused connection too; changing it would change X. Writes are never retried, as in XTA
covered | pixiv_client_transport_test › getJson without auth sends no token and never refreshes | networks/PixivClient.kt | PixivClientTest.aReadWithoutAuthSendsNoTokenAndNeverRefreshes
covered | pixiv_client_transport_test › the token never leaves the app API host | networks/PixivClient.kt | PixivClientTest.theTokenNeverLeavesTheAppApiHost
covered | pixiv_client_transport_test › getText returns an HTML body as text | networks/PixivClient.kt | PixivClientTest.getTextReturnsAnHtmlBodyAsText
ported | pixiv_client_transport_test › postForm sends a form and reads an empty answer as null | networks/PixivClient.kt (postForm: an empty object) | PixivTransportTest.postFormSendsAForm…
covered | pixiv_client_transport_test › illustPageFrom follows the reader's filters unless it is their own list | networks/PixivClient.kt | PixivClientTest.illustPageFollowsTheReadersFilters…
covered | pixiv_client_transport_test › is stored from the token response and cleared on sign-out | networks/PixivClient.kt | PixivClientTest.premiumIsKeptFromTheTokenResponseAndClearedOnSignOut
covered | pixiv_client_transport_test › a token response without a user keeps what was known | networks/PixivClient.kt | PixivClientTest.aTokenResponseWithoutAUserKeepsWhatWasKnown

## pixiv_comment_emoji_test.dart
covered | pixiv_comment_emoji_test › numbers every set the way Pixiv serves the images | networks/PixivComments.kt (PixivCommentText.emojiIds) | PixivCommentsTest.emojiAreNumberedTheWayPixivServesTheirPictures
covered | pixiv_comment_emoji_test › (normal) and (heart) become emoji between the words | networks/PixivComments.kt (parts) | PixivCommentsTest.emojiCodesSplitTheText…
covered | pixiv_comment_emoji_test › an unknown code stays text | networks/PixivComments.kt | same test
covered | pixiv_comment_emoji_test › adjacent codes are separate emoji with nothing between | networks/PixivComments.kt | same test
covered | pixiv_comment_emoji_test › parentheses inside ordinary text are left alone | networks/PixivComments.kt | same test
covered | pixiv_comment_emoji_test › empty text has no parts and an emoji-only comment has no text part | networks/PixivComments.kt | same test
covered | pixiv_comment_emoji_test › an emoji part knows its code and image | networks/PixivComments.kt | PixivCommentsTest.emojiAreNumbered…

## pixiv_comment_models_test.dart
covered | pixiv_comment_models_test › reads a full comment | networks/PixivComments.kt | PixivCommentsTest.aPageReadsItsCommentsTheTotalAndTheNextPage
covered | pixiv_comment_models_test › reads a sticker comment with no text | networks/PixivComments.kt | same test
covered | pixiv_comment_models_test › keeps a comment whose author is missing or withdrawn | networks/PixivComments.kt | same test
covered | pixiv_comment_models_test › reads whom a reply answers from parent_comment | networks/PixivComments.kt | same test
covered | pixiv_comment_models_test › survives a reshaped payload without throwing | networks/PixivComments.kt | PixivCommentsTest.aReshapedCommentSurvives…
covered | pixiv_comment_models_test › reads the comments, the total and the next page | networks/PixivComments.kt | PixivCommentsTest.aPageReadsItsComments…
covered | pixiv_comment_models_test › an empty or reshaped page is simply empty | networks/PixivComments.kt | PixivCommentsTest.anEmptyOrReshapedPageIsSimplyEmpty
covered | pixiv_comment_models_test › prefers the name, then the account | networks/PixivComments.kt (authorName) | PixivCommentsTest.aPageReadsItsComments… (sticker's @account)
covered | pixiv_comment_models_test › Pixiv links and plain text pass | networks/PixivComments.kt (linksOutside) | PixivCommentsTest.pixivLinksAndPlainTextPassTheSpamGuard
covered | pixiv_comment_models_test › a link anywhere else is caught, however it is written | networks/PixivComments.kt | PixivCommentsTest.aLinkAnywhereElseIsCaught…
covered | pixiv_comment_models_test › each target asks its own endpoints | networks/PixivComments.kt (PixivCommentTarget) | PixivCommentsTest.eachKindOfWorkAsksItsOwnEndpoints

## pixiv_comments_api_test.dart
covered | pixiv_comments_api_test › an artwork target reads /v3/illust/comments with the token | networks/PixivComments.kt (PixivCommentsApi) | PixivCommentsTest.anArtworksCommentsAreReadWithTheToken
covered | pixiv_comments_api_test › a novel target reads /v3/novel/comments | networks/PixivComments.kt | PixivCommentsTest.novelCommentsRepliesAndLaterPages…
covered | pixiv_comments_api_test › replies come from each kind's own replies endpoint | networks/PixivComments.kt | same test
covered | pixiv_comments_api_test › a later page follows next_url as Pixiv wrote it | networks/PixivComments.kt | same test
covered | pixiv_comments_api_test › only reads: no request posts anything | networks/PixivComments.kt | same test (every request is a GET)

## pixiv_comments_screen_test.dart
untested | pixiv_comments_screen_test › shows text with inline emoji, a sticker and whom a reply answers, and offers no composer | PixivComments.kt (CommentTile, CommentText, CommentByline) | Compose; the parts and reply-to are tested in PixivCommentsTest
untested | pixiv_comments_screen_test › View replies opens the thread with its comment on top | PixivComments.kt (pinned parent header) | Compose
untested | pixiv_comments_screen_test › a thread without replies says so under its comment | PixivComments.kt (comments_replies_empty) | Compose
untested | pixiv_comments_screen_test › a comment linking outside Pixiv waits behind Show | PixivComments.kt (HiddenLink) | Compose; linksOutside tested
missing | pixiv_comments_screen_test › a comment shown past its link note stays shown in its thread | PixivRuntime.revealedComments, PixivComments.kt | implemented: Show was kept per row, so the thread's pinned comment hid it again; PixivCommentsListTest.aCommentShownPastItsLinkNote…
untested | pixiv_comments_screen_test › copying a comment keeps its emoji as their codes | PixivComments.kt (inline emoji's alternate text is the code) | Compose selection
untested | pixiv_comments_screen_test › muting a comment asks first and hides it | PixivComments.kt (CommentMenu), PixivMutes.kt | Compose ask; hiding tested in PixivCommentsListTest
ported | pixiv_comments_screen_test › muting an author hides every comment they wrote | PixivComments.kt (PixivMutes.shows) | PixivCommentsListTest.aMutedCommentAndEverythingByAMutedAuthorAreHidden
untested | pixiv_comments_screen_test › a comment without an author can only be muted itself | PixivComments.kt (CommentMenu: the author's line only with a user) | Compose
missing | pixiv_comments_screen_test › muting the comment a thread hangs off leaves the thread | PixivComments.kt (leavesThread → back) | implemented; PixivCommentsListTest.mutingTheCommentAThreadHangsOff…
ported | pixiv_comments_screen_test › opening shows the skeleton, never a passing "No comments yet" or "No replies yet" | PixivPaging.kt (PixivListState.waiting) | PixivCommentsListTest.theFirstLoadCountsAsLoading…
covered | pixiv_comments_screen_test › a later page that fails keeps the comments and offers Retry instead of a spinner | PixivPaging.kt (moreFailed) | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
untested | pixiv_comments_screen_test › scrolling toward the end of a long page asks for the next | PixivUi.kt (PixivRows) | Compose scroll
untested | pixiv_comments_screen_test › pulling down reloads the comments | PixivUi.kt (PixivRows refresh) | Compose; refresh tested in PixivPagingTest
untested | pixiv_comments_screen_test › a short first page reaches for the next one by itself | PixivUi.kt (PixivRows) | Compose
untested | pixiv_comments_screen_test › no comments says so, and a failed load offers Retry | PixivUi.kt (PixivRows empty/problem rows) | Compose
ported | pixiv_comments_screen_test › the novel target reads the novel's comments and replies | PixivComments.kt (readComments), networks/PixivComments.kt | PixivCommentsListTest.theNovelTargetReads…
n/a | pixiv_comments_screen_test › large text keeps every comment inside the screen | PixivComments.kt | Flutter overflow assertion
untested | pixiv_comments_screen_test › every control is a labelled, finger-sized target | PixivComments.kt (IconButtons with descriptions) | Flutter's tap-target guideline; Compose's IconButton is 48 dp
untested | pixiv_comments_screen_test › the artwork detail shows the comment count and opens the comments | PixivWorkParts.kt (comments_view_count) | Compose

## pixiv_comments_store_test.dart
covered | pixiv_comments_store_test › pages through a work's comments without repeats | PixivPaging.kt (PixivList) | PixivPagingTest.pagesJoinInOrderWithoutRepeatsToTheEnd
covered | pixiv_comments_store_test › a thread reads the replies under its parent | PixivComments.kt (readComments), networks/PixivComments.kt | PixivCommentsTest.novelCommentsRepliesAndLaterPages…
ported | pixiv_comments_store_test › novel comments and replies use the novel target | PixivComments.kt | PixivCommentsListTest.theNovelTargetReads…
ported | pixiv_comments_store_test › leaves out muted comments and muted authors, as they are when each page lands | PixivComments.kt (visible filter reads mutes.now) | PixivCommentsListTest.theMutesAreTakenAsEachPageLands
ported | pixiv_comments_store_test › skips pages the mutes empty instead of stopping on them | PixivPaging.kt (PixivPages.visiblePage) | PixivCommentsListTest.pagesTheMutesEmptyAreSkipped…
ported | pixiv_comments_store_test › a last page the mutes empty still ends the paging for the list | PixivPaging.kt | same test
covered | pixiv_comments_store_test › a failed first page is an error, a failed later page keeps the list | PixivPaging.kt | PixivPagingTest.aFirstPageThatFailsSaysWhy…, aFailedNextPageKeepsTheList…
covered | pixiv_comments_store_test › a later page that failed is announced, and clears once one arrives | PixivPaging.kt (moreFailed, retryMore) | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
ported | pixiv_comments_store_test › the first load counts as loading from the start, not as an empty list | PixivPaging.kt (waiting) | PixivCommentsListTest.theFirstLoadCountsAsLoading…
ported | pixiv_comments_store_test › hides a muted comment and everything by a muted author | PixivComments.kt (shows) | PixivCommentsListTest.aMutedCommentAndEverythingByAMutedAuthorAreHidden

## pixiv_copy_info_test.dart
covered | pixiv_copy_info_test › fills every placeholder | PixivSearchMemory.kt (PixivCopyInfo.filled) | PixivSearchMemoryTest + PixivDetailTest.copyInfoFillsEveryPlaceholder (the two links)
covered | pixiv_copy_info_test › leaves unknown braces and a title that looks like a placeholder as written | PixivCopyInfo.filled | PixivSearchMemoryTest; PixivDetailTest.unknownBraces…
ported | pixiv_copy_info_test › the default template names title, artist and work id | PixivWorkSheets.kt (defaultCopyTemplate) | PixivDetailTest.theDefaultTemplateNamesTitleArtistAndWorkId
ported | pixiv_copy_info_test › the editor inserts placeholders, saves as it goes and resets to the default | PixivSettingsMore.kt (PixivCopyTemplateScreen, inserted) | PixivDetailTest.theEditorInsertsPlaceholders… (the screen saving as it goes is Compose)
untested | pixiv_copy_info_test › Copy info puts the filled template on the clipboard | PixivWork.kt (WorkMenu COPY_INFO, info_copied) | Compose clipboard; the filled text is tested

## pixiv_detail_caption_test.dart
ported | pixiv_detail_caption_test › keeps line breaks, bold and links, and reads other tags for their text | PixivOpen.kt (PixivHtml.parts) | PixivDetailTest.aCaptionKeepsLineBreaks…
missing | pixiv_detail_caption_test › makes Pixiv paths absolute and unwraps jump.php | PixivOpen.kt (PixivHtml.href) | implemented: `javascript:` and other schemes were made Pixiv paths; now no link. PixivDetailTest.pixivPathsAreMadeAbsolute…
ported | pixiv_detail_caption_test › an empty or broken caption is no parts rather than an error | PixivHtml.parts | PixivDetailTest.anEmptyOrBrokenCaption…
ported | pixiv_detail_caption_test › a Pixiv link in the caption opens in XTA, and the caption is selectable | PixivOpen.kt (openHref), PixivWorkParts.kt (PixivHtmlText in a SelectionContainer) | PixivDetailTest.aPixivLinkInTheCaptionOpensInTheApp…
untested | pixiv_detail_caption_test › a plain-text caption still shows when Pixiv sent no HTML | PixivWorkParts.kt (PixivHtmlText plain fallback) | Compose

## pixiv_detail_parts_test.dart
ported | pixiv_detail_parts_test › the overflow menu offers every entry, downloading all only for many pages | PixivWork.kt (PixivWorkMenu, now a testable list) | PixivDetailTest.theMenuOffersEveryEntry…
untested | pixiv_detail_parts_test › muting the author asks first, mutes and leaves the work | PixivWorkSheets.kt (MuteSheet → MuteRows leave), PixivSheets.onMutedHere | Compose
untested | pixiv_detail_parts_test › cancelling the mute keeps the work open and mutes nothing | PixivWorkSheets.kt | Compose
untested | pixiv_detail_parts_test › a similar work leaves at once when it is muted | PixivWork.kt (related list's mutes filter) | Compose; mute filtering tested in PixivMutesTest
untested | pixiv_detail_parts_test › a work that cannot load keeps its picture, says why and recovers on retry | PixivWork.kt (DetailGrid PixivProblem) | Compose
untested | pixiv_detail_parts_test › a Pixiv link opens the work or the creator, and says when the work will not load | PixivPlugin.kt (link screen: open_link_failed with Retry) | Compose; link parsing tested in PixivLinksTest
n/a | pixiv_detail_parts_test › the detail fits a narrow phone with large text | PixivWork.kt | Flutter overflow assertion

## pixiv_detail_split_test.dart
ported | pixiv_detail_split_test › automatic splits from 840dp, stacked never does, side by side wherever both panes fit | PixivRules.kt (PixivLayout.splits) | PixivDetailTest.automaticSplitsFrom840…; PixivRulesTest
ported | pixiv_detail_split_test › the details keep 320dp and the pictures 240dp whatever the divider says | PixivLayout.splitImageWidth | PixivDetailTest.theDetailsKeep320…
ported | pixiv_detail_split_test › the layout and divider read from preferences, with defaults for anything unusable | store PixivPrefs.detailLayout/detailSplit | PixivDetailTest.theLayoutAndDividerRead…
untested | pixiv_detail_split_test › a tablet puts the pictures left and the details right, at least 320dp wide | PixivWork.kt (SplitDetail) | Compose layout; widths tested
untested | pixiv_detail_split_test › a phone stacks them, and so does a tablet set to stacked | PixivWork.kt | Compose; splits() tested
untested | pixiv_detail_split_test › side by side applies to a landscape phone too | PixivWork.kt | Compose; splits(SPLIT) tested
untested | pixiv_detail_split_test › turning the device keeps the page on screen | PixivWork.kt (page in PixivWorkModel state) | Compose
untested | pixiv_detail_split_test › dragging the divider resizes the panes, keeps 320dp of details and is remembered | PixivWork.kt (SplitDetail draggable → detailSplit) | Compose; clamping tested
n/a | pixiv_detail_split_test › holding the divider keeps the pictures as they were decoded instead of loading them again | PixivWork.kt | Flutter image cache keys; Coil keeps its memory cache

## pixiv_discover_filter_test.dart
ported | pixiv_discover_filter_test › hidden R-18 and AI previews never become the suggestion's work | PixivDiscover.kt (around: mutes.filter of the previews the client let through) | PixivDiscoverTest.hiddenR18AndAiPreviews…
ported | pixiv_discover_filter_test › the reader's own choices let them back in | PixivDiscover.kt | PixivDiscoverTest.theReadersOwnChoicesLetThemBackIn
missing | pixiv_discover_filter_test › a choice changed since the last scan applies to the cached creators at once | PixivDiscover.kt (scope) | not changed: native's scope holds the R-18/AI/mute choices, so a change asks Pixiv again instead of re-filtering the cached creators; what shows is the same. Re-filtering would need the app's Discover cache to keep raw answers (a plugin API change). PixivDiscoverTest.anotherAccountOrAnotherChoiceIsAnotherScope
ported | pixiv_discover_filter_test › another account asks again rather than reading the last one's answers | PixivDiscover.kt (scope holds the account) | PixivDiscoverTest.anotherAccountOrAnotherChoiceIsAnotherScope

## pixiv_discovery_api_test.dart
covered | pixiv_discovery_api_test › send the mode, the archive day when one is picked, and the Android filter | networks/PixivDiscovery.kt | PixivDiscoveryTest.rankingsSendTheMode…
covered | pixiv_discovery_api_test › AI boards keep their AI works under Hide AI; the others drop them | networks/PixivDiscovery.kt | PixivDiscoveryTest.aiBoardsKeepTheirAiWorks…
covered | pixiv_discovery_api_test › R-18 works stay out of every board while Show R-18 is off | networks/PixivDiscovery.kt | PixivDiscoveryTest.r18WorksStayOutOfEveryBoard…
covered | pixiv_discovery_api_test › recommended manga asks the manga endpoint with ranking labels | networks/PixivDiscovery.kt | PixivDiscoveryTest.recommendedMangaAsks…
covered | pixiv_discovery_api_test › the Following feed asks for the chosen follows | networks/PixivClient.kt | PixivDiscoveryTest.theFollowingFeedAsksForTheChosenFollows
covered | pixiv_discovery_api_test › the walkthrough is the one call sent without a token | networks/PixivDiscovery.kt | PixivDiscoveryTest.theShowcaseIsReadWithoutAnyToken…
covered | pixiv_discovery_api_test › watchlist changes post the series id as a form | networks/PixivDiscovery.kt | PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
covered | pixiv_discovery_api_test › recommended creators keep their previews under the reader's filters | networks/PixivDiscovery.kt | PixivDiscoveryTest.recommendedCreatorsKeepTheirPreviews…
covered | pixiv_discovery_api_test › Pixivision articles come from every category, newest first | networks/PixivDiscovery.kt | PixivDiscoveryTest.pixivisionArticlesComeFromEveryCategory…
covered | pixiv_discovery_api_test › a page carries the header and the works | networks/PixivDiscovery.kt | PixivDiscoveryTest.aSeriesPageCarriesItsHeaderAndItsWorks
covered | pixiv_discovery_api_test › a reshaped header reads as no header, and missing fields as defaults | networks/PixivDiscovery.kt | PixivDiscoveryTest.aReshapedSeriesHeader…
covered | pixiv_discovery_api_test › the context gives the place and the neighbours the reader may see | networks/PixivDiscovery.kt | PixivDiscoveryTest.theSeriesContextGivesThePlace…
covered | pixiv_discovery_api_test › a context without a place is no context | networks/PixivDiscovery.kt | PixivDiscoveryTest.aContextWithoutAPlaceIsNoContext
covered | pixiv_discovery_api_test › lists each watched series and skips rows that do not parse | networks/PixivDiscovery.kt | PixivDiscoveryTest.theMangaWatchlistListsEach…
covered | pixiv_discovery_api_test › a reshaped page is an empty page | networks/PixivDiscovery.kt | PixivDiscoveryTest.theMangaWatchlist… (reshaped rows) 
covered | pixiv_discovery_api_test › are fetched as a desktop browser in the reader's language, without the token | networks/Pixivision.kt | PixivDiscoveryTest.aPixivisionArticleIsReadAsADesktopBrowser…
covered | pixiv_discovery_api_test › a missing article and an unreadable page say why | networks/Pixivision.kt | PixivDiscoveryTest.aMissingArticleIsNotFound…

## pixiv_discovery_test.dart
covered | pixiv_discovery_test › a picked date rides along; none means the latest board | networks/PixivDiscovery.kt | PixivDiscoveryTest.rankingsSendTheModeTheArchiveDay…
covered | pixiv_discovery_test › each tag carries its representative illust | networks/PixivClient.kt (trendingTags) | PixivClientTest.trendingTagsAndSuggestionsCarryTheirTranslations
covered | pixiv_discovery_test › suggests tags with their translations | networks/PixivClient.kt (autocomplete) | same test
covered | pixiv_discovery_test › an empty word suggests nothing without asking | networks/PixivClient.kt | PixivClientTest.aBlankWordSuggestsNothingWithoutAsking
covered | pixiv_discovery_test › recommended illusts hit /v1/illust/recommended | networks/PixivClient.kt | PixivClientTest.theCoreReadsAskTheirOwnPaths…
covered | pixiv_discovery_test › recommended users parse user_previews | networks/PixivDiscovery.kt | PixivDiscoveryTest.recommendedCreatorsKeepTheirPreviews…

## pixiv_download_index_test.dart
covered | pixiv_download_index_test › records pages and answers what is saved | PixivSaving.kt (PixivSavedPages) | PixivSavingTest.savedPagesAreRememberedNewestLastWithoutRepeats
covered | pixiv_download_index_test › is kept in settings, newest last, and read back on the next launch | PixivSaving.kt | PixivSavingTest.savedPagesAreRememberedNewestLastWithoutRepeats, PixivSettingsStoreTest.aBackupRestoredIntoTheAppShowsHere
covered | pixiv_download_index_test › forgets the oldest pages beyond the cap | PixivSaving.kt | PixivSavingTest.onlyTheNewestPagesAreKept
untested | pixiv_download_index_test › a broken or reshaped stored list reads as what it validly holds | PixivSaving.kt (decode) | the decode is tolerant; no separate test
covered | pixiv_download_index_test › clear forgets everything, in memory and in settings | PixivSaving.kt | PixivSavingTest.savedPagesAreRememberedNewestLastWithoutRepeats
covered | pixiv_download_index_test › a write from outside the store shows at once, and the next save keeps it | PixivSettingsStoreTest.aBackupRestoredIntoTheAppShowsHere
untested | pixiv_download_index_test › a reset of the setting empties the index, and a closed index stops following it | PixivSaving.kt (close) | Compose-side lifetime
covered | pixiv_download_index_test › cappedPixivIndex folds repeats and keeps the newest | PixivSaving.kt | PixivSavingTest.onlyTheNewestPagesAreKept

## pixiv_download_naming_test.dart
covered | pixiv_download_naming_test › fills every placeholder and keeps the extension | PixivSaving.kt (template) | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept
covered | pixiv_download_naming_test › replaces characters no file system accepts, from the template and the work alike | PixivSaving.kt | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept
untested | pixiv_download_naming_test › a title that reads like a placeholder stays as written | PixivSaving.kt (one-pass fill) | PixivSearchMemoryTest.theCopyTemplateIsFilledInOnePass covers the one-pass rule for copy; the file naming has no separate test
untested | pixiv_download_naming_test › unknown placeholders are kept, outer dots and spaces trimmed, long names cut | PixivSaving.kt | no separate test
untested | pixiv_download_naming_test › a long name is cut to a byte budget between whole characters | PixivSaving.kt | no separate test
untested | pixiv_download_naming_test › a name that ends up empty falls back to the work and page | PixivSaving.kt | no separate test
covered | pixiv_download_naming_test › the extension comes from the original file, else .jpg | PixivSaving.kt | PixivSavingTest.theExtensionComesFromTheFileElseJpg
covered | pixiv_download_naming_test › {part} is required | PixivSaving.kt | PixivSavingTest.aTemplateThatLostItsPartIsNotUsed
covered | pixiv_download_naming_test › reads the template and both folder settings | PixivSaving.kt | PixivSavingTest.foldersArePerCreatorAndForR18AsAsked
covered | pixiv_download_naming_test › a stored template without {part} is not used | PixivSaving.kt | PixivSavingTest.aTemplateThatLostItsPartIsNotUsed
covered | pixiv_download_naming_test › R-18 works alone go to R-18, and an artist name cannot leave its folder | PixivSaving.kt | PixivSavingTest.foldersArePerCreatorAndForR18AsAsked
untested | pixiv_download_naming_test › an artist folder never starts with a dot, which Android would hide from the gallery | PixivSaving.kt | no separate test
covered | pixiv_download_naming_test › nothing set means no subfolder | PixivSaving.kt | PixivSavingTest.foldersArePerCreatorAndForR18AsAsked
covered | pixiv_download_naming_test › batch requests carry the template name and the subfolder | PixivSaving.kt, ui/downloads/PluginBatches.kt | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept, PluginBatchesTest.aWorksPagesAreSavedTogetherAndEachSaysHowItEnded

## pixiv_download_test.dart
covered | pixiv_download_test › requests every page in order and a failed page does not stop the rest | ui/downloads/PluginBatches.kt | PluginBatchesTest.aWorksPagesAreSavedTogetherAndEachSaysHowItEnded
covered | pixiv_download_test › publishes progress after each page | ui/downloads/PluginBatches.kt | PluginBatchesTest.watchedBatchCountsThePageOnItsWayFromOne
covered | pixiv_download_test › cancelling stops before the next page and cancels the page in flight | PluginBatches.kt | PluginBatchesTest.aStoppedBatchStartsNoMoreAndWhatIsUnderWayIsCancelled
covered | pixiv_download_test › keeps as many pages in flight as the queue allows and remembers which saved | PluginBatches.kt; PixivSaving.kt | PluginBatchesTest.aWorksPagesAreSavedTogether…, PixivSavingTest.savedPagesAreRemembered…
covered | pixiv_download_test › cancelling stops every page in flight | PluginBatches.kt | PluginBatchesTest.aStoppedBatchStartsNoMoreAndWhatIsUnderWayIsCancelled
covered | pixiv_download_test › download all shows page progress with a cancel button and reports the final count | PluginBatches.kt | PluginBatchesTest.watchedBatchCountsThePageOnItsWayFromOne
covered | pixiv_download_test › cancelling a download-all from the snackbar stops the remaining pages | PluginBatches.kt | PluginBatchesTest.aStoppedBatchStartsNoMoreAndWhatIsUnderWayIsCancelled
untested | pixiv_download_test › declining the folder for a whole work downloads nothing | PixivSaving.kt (folder choice) | the system dialog is Compose-side
covered | pixiv_download_test › the reader download button and a long-press save the page on screen, asking the second time | PixivSaving.kt (resave) | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
covered | pixiv_download_test › page requests save the originals under the default {illust_id}_p{part} names | PixivSaving.kt | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept
covered | pixiv_download_test › only the Pixiv image host is sent the Pixiv referer | networks/PixivImages.kt | PixivImagesTest.everyPictureRequestCarriesPixivsReferer
untested | pixiv_download_test › the shared transfer sends that referer when it fetches the file | store/downloads/DownloadNaming.kt (pixivImageHeaders) | the headers are wired; no separate test
covered | pixiv_download_test › a manga page saves its original and previews its medium image | PixivModelsTest.aMangaShowsItsLargePagesAndSavesItsOriginals
covered | pixiv_download_test › a single image saves the original from meta_single_page | networks/PixivImages.kt | PixivImagesTest.aPageComesInEachSizeFallingBackToWhatPixivSent
covered | pixiv_download_test › an illust built without per-page lists falls back to the viewer pages | PixivImages.kt | PixivImagesTest.aPageComesInEachSizeFallingBackToWhatPixivSent

## pixiv_ugoira_export_test.dart
covered | pixiv_ugoira_export_test › delays become hundredths of a second, never under two | PixivGif.kt | PixivGifTest.delaysAreHundredthsOfASecondAsXtaRoundsThem
covered | pixiv_ugoira_export_test › keeps every frame and its own delay, and loops | PixivGif.kt | PixivGifTest.framesKeepTheirOrderAndDelays, anAnimationIsEncodedAFrameAtATime
covered | pixiv_ugoira_export_test › an unreadable frame fails instead of saving a broken file | PixivGif.kt | PixivGifTest.aFrameThatWillNotDecodeFailsTheExport
untested | pixiv_ugoira_export_test › runs on a background isolate and reports each frame | PixivGif.kt (Dispatchers) | the encode is off the main thread; the isolate itself is not tested
untested | pixiv_ugoira_export_test › cancelling stops the background encode | PixivGif.kt | no separate test
covered | pixiv_ugoira_export_test › frames come out of the archive in the metadata order | PixivUgoira.kt | PixivUgoiraTest.framesComeInTheMetadatasOrderWithTheirDelays
covered | pixiv_ugoira_export_test › the ZIP path saves the archive bytes as fetched, without encoding | PixivUgoira.kt | PixivUgoiraTest.theArchiveComesFromTheServerTheReaderPicked
covered | pixiv_ugoira_export_test › the GIF path encodes with progress, then saves the GIF | PixivGif.kt | PixivGifTest.anAnimationIsEncodedAFrameAtATime
untested | pixiv_ugoira_export_test › cancel during encoding stops it and saves nothing | PixivGif.kt | no separate test
untested | pixiv_ugoira_export_test › cancel while fetching ends the export at once and ignores the late response | PixivUgoira.kt | no separate test
untested | pixiv_ugoira_export_test › a failed fetch or a refused write ends as failed | PixivUgoira.kt | no separate test
untested | pixiv_ugoira_export_test › the page sheet of an ugoira offers GIF, with a slow note, and ZIP | PixivUi/Sheets (Compose) | Compose only
covered | pixiv_ugoira_export_test › Save frames as ZIP writes the archive under the template and marks the work saved | PixivUgoira.kt; PixivSaving.kt | PixivUgoiraTest.theArchiveComesFromTheServer…, PixivSavingTest.aPageIsNamedByTheTemplate…
covered | pixiv_ugoira_export_test › Save as GIF encodes the fetched frames and saves a .gif | PixivGif.kt; PixivSaving.kt | PixivGifTest.anAnimationIsEncodedAFrameAtATime
covered | pixiv_ugoira_export_test › exporting a work saved before asks first, and Cancel saves nothing | PixivSaving.kt | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
untested | pixiv_ugoira_export_test › declining the folder saves nothing | PixivSaving.kt (folder choice) | the system dialog is Compose-side
untested | pixiv_ugoira_export_test › the progress row shows the frame, the slow note and Cancel only while it can stop | Compose (PixivUi) | Compose only

## pixiv_haptics_test.dart
untested | pixiv_haptics_test › buzzes are at least the gap apart | ui/common Haptics.kt | Compose-side timing
untested | pixiv_haptics_test › plays only when the reader left haptics on, and spaces a burst out | Haptics.kt | Compose-side
untested | pixiv_haptics_test › XTA's Haptic feedback switch silences Pixiv's too | Haptics.kt (the app's pref) | Compose-side
untested | pixiv_haptics_test › the reader's page sheet buzzes when a long press opens it, not from its button | PixivReader/Sheets (Compose) | Compose only
covered | pixiv_haptics_test › a follow from a creator's button buzzes lightly once Pixiv took it | PixivBookmarkingTest.aFollowButtonFollowsPubliclyThenUnfollows | the buzz is Compose-only
untested | pixiv_haptics_test › a private follow from the follow dialog buzzes lightly once Pixiv took it | PixivBookmarkingTest.aNovelsHeartUsesTheDefaultVisibilityAndItsLongPressBookmarksPrivately | the buzz is Compose-only

## pixiv_history_screen_test.dart
covered | pixiv_history_screen_test › lists the newest first and filters by title or artist | PixivKept.kt | PixivKeptTest.theHistoryPutsTheLatestFirstOnceEach
covered | pixiv_history_screen_test › a long press forgets one work after asking | PixivKept.kt | PixivKeptTest.theHistoryPutsTheLatestFirstOnceEach
covered | pixiv_history_screen_test › Clear all asks first, then empties the history | PixivKept.kt | PixivKeptTest.theHistoryPutsTheLatestFirstOnceEach
untested | pixiv_history_screen_test › the pause switch stops recording, and a tile reopens its work | PixivKept.kt (pause); PixivSettingsStore | the pause is stored; the switch is Compose-only
untested | pixiv_history_screen_test › large text keeps the tiles inside the screen | Compose (PixivHistoryScreen) | Compose only
untested | pixiv_history_screen_test › a narrow screen with large text scrolls the filter away to reach the works | Compose | Compose only

## pixiv_history_store_test.dart
covered | pixiv_history_store_test › puts the newest first and moves a reopened work to the top | PixivKept.kt | PixivKeptTest.theHistoryPutsTheLatestFirstOnceEach
covered | pixiv_history_store_test › keeps at most 500 works, dropping the oldest | PixivKept.kt | PixivKeptTest.theHistoryPutsTheLatestFirstOnceEach
untested | pixiv_history_store_test › filters by title or artist, ignoring case | PixivHistory screen (Compose) | the filter runs in the screen
covered | pixiv_history_store_test › round-trips every field | PixivKept.kt | PixivKeptTest.theHistoryIsReadBackFromItsFile
covered | pixiv_history_store_test › an R-18 or AI work keeps its rating through the file, so its tile keeps the badges | PixivKept.kt | PixivKeptTest.aKeptWorkShowsAsTheWorkItWas
covered | pixiv_history_store_test › drops entries without an id or thumbnail and tolerates the rest missing | PixivKept.kt (decode) | PixivKeptTest.aKeptWorkShowsAsTheWorkItWas
covered | pixiv_history_store_test › opens as a stub the detail fills in, keeping tags for the mute gate | PixivKept.kt; PixivMutes.kt | PixivKeptTest.aKeptWorkShowsAsTheWorkItWas, PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
covered | pixiv_history_store_test › records, removes and clears, keeping the file in step | PixivKept.kt | PixivKeptTest.theHistoryIsReadBackFromItsFile
covered | pixiv_history_store_test › a record made before the file was read keeps what the file held | PixivKept.kt (serialised write) | PixivKeptTest.theHistoryIsReadBackFromItsFile
covered | pixiv_history_store_test › keeps novels apart under their own key | PixivKept.kt | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
covered | pixiv_history_store_test › a novel without a cover keeps its row after a restart | PixivKept.kt | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas

## pixiv_mute_gate_test.dart
covered | pixiv_mute_gate_test › a muted work waits behind a notice that says why, until shown this time | PixivMutes.kt | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy | the notice is Compose-only
untested | pixiv_mute_gate_test › the notice opens the mute settings, and unmuting there shows the work | PixivMutes.kt; PixivSettings (Compose link) | Compose only
covered | pixiv_mute_gate_test › an unmuted work opens straight away and joins the history unless paused | PixivMutes.kt; PixivKept.kt | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_mute_gate_test › a work whose detail does not load stays out of the history | PixivRuntime/PixivHistory (Compose) | Compose only
untested | pixiv_mute_gate_test › a paused history records nothing | PixivKept.kt (pause); PixivSettingsStore | the pause is stored; the switch is Compose-only
untested | pixiv_mute_gate_test › swiping between works, each work swiped to joins the history and a muted one waits behind its notice | Compose (PixivPager) | Compose only

## pixiv_mute_store_test.dart
covered | pixiv_mute_store_test › filters muted authors, tags and illust ids | PixivMutes.kt | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
covered | pixiv_mute_store_test › an r'pattern' entry matches single tags, ignoring case | PixivMutes.kt | PixivMutesTest.aNameMatchesWhateverItsCaseAPatternTheTagsTogether
covered | pixiv_mute_store_test › a pattern sees every tag joined as #a#b, so it can ask for several together | PixivMutes.kt | PixivMutesTest.aNameMatchesWhateverItsCaseAPatternTheTagsTogether
covered | pixiv_mute_store_test › plain entries still match whole tags only | PixivMutes.kt | PixivMutesTest.aNameMatchesWhateverItsCaseAPatternTheTagsTogether
covered | pixiv_mute_store_test › patterns keep their case; plain names are lower-cased | PixivMutes.kt | PixivMutesTest.aTagIsKeptWithoutItsHashAndCaseAPatternAsWritten
covered | pixiv_mute_store_test › an invalid pattern or a blank entry is refused | PixivMutes.kt | PixivMutesTest.onlyNamesAndPatternsThatCompileCanBeMuted
covered | pixiv_mute_store_test › legacy bare author ids still load, without names | PixivMutes.kt | PixivMutesTest.xtasOlderListOfBareCreatorIdsStillReads
covered | pixiv_mute_store_test › authors round-trip with the name they were muted under | PixivMutes.kt | PixivMutesTest.theMutesComeBackFromXtasPreferences
covered | pixiv_mute_store_test › a typed pattern is kept as written; an invalid one is refused and not saved | PixivMutes.kt | PixivMutesTest.aTagIsKeptWithoutItsHashAndCaseAPatternAsWritten, onlyNamesAndPatternsThatCompileCanBeMuted
covered | pixiv_mute_store_test › a change builds on the stored mutes, so an import is never saved over | PixivMutes.kt; PixivSettingsStore | PixivSettingsStoreTest.changesMadeBeforeTheSettingsArrivedAreKeptOverThem
covered | pixiv_mute_store_test › a settings import reloads the mutes and the searches the app holds | PixivSettingsStore | PixivSettingsStoreTest.aBackupRestoredIntoTheAppShowsHere
covered | pixiv_mute_store_test › a damaged preference reads as nothing muted | PixivMutes.kt | PixivMutesTest.theMutesComeBackFromXtasPreferences
covered | pixiv_mute_store_test › names the work, then the author by name, then the matching tag | PixivMutes.kt | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy

## pixiv_favorite_tags_store_test.dart
covered | pixiv_favorite_tags_store_test › adding and removing build on the stored tags, so an import is never saved over | PixivSearchMemory.kt; PixivSettingsStore | PixivSettingsStoreTest.changesMadeBeforeTheSettingsArrivedAreKeptOverThem
covered | pixiv_favorite_tags_store_test › a reorder over a list that changed underneath shows the stored list instead | PixivSearchMemory.kt | PixivSearchMemoryTest.favouriteTagsKeepTheReadersOrderOnceEach
covered | pixiv_favorite_tags_store_test › adds at the end, and a tag differing only in case replaces the old one | PixivSearchMemory.kt | PixivSearchMemoryTest.favouriteTagsKeepTheReadersOrderOnceEach
covered | pixiv_favorite_tags_store_test › reorders to the index the row was dropped at, and removes | PixivSearchMemory.kt | PixivSearchMemoryTest.favouriteTagsKeepTheReadersOrderOnceEach
covered | pixiv_favorite_tags_store_test › persists in order with translations, for the next screen and the backup | PixivSearchMemory.kt; PixivSettingsStore | PixivSearchMemoryTest.favouriteTagsKeepTheReadersOrderOnceEach
covered | pixiv_favorite_tags_store_test › reads bare names, skips blanks and survives garbage | PixivSearchMemory.kt | PixivSearchMemoryTest.favouriteTagsKeepTheReadersOrderOnceEach
untested | pixiv_favorite_tags_store_test › pixivMoved leaves the list alone for an index outside it | PixivSearchMemory.kt (moved) | no separate test

## pixiv_novel_api_test.dart
covered | pixiv_novel_api_test › recommended asks for ranking novels and the Android filter, then follows next_url | PixivNovelTest.recommendedAsksForRankingNovelsThenFollowsNextUrl
covered | pixiv_novel_api_test › following sends public or private | PixivNovelTest.followingSendsPublicOrPrivateAndTheReadersChoicesApply
covered | pixiv_novel_api_test › Show R-18 and Hide AI apply to the feeds | PixivNovelTest.aListLeavesOutR18AndAiUnlessAskedAndSkipsWhatDoesNotParse
covered | pixiv_novel_api_test › send the board, the archive date when one is picked, and the Android filter | PixivNovelTest.rankingsSendTheBoardTheArchiveDateAndTheAndroidFilter
covered | pixiv_novel_api_test › an AI board keeps its AI novels with Hide AI on; other boards drop them | PixivNovelTest.anAiBoardKeepsItsAiNovelsUnderHideAi
covered | pixiv_novel_api_test › the reader's own keep R-18 and AI novels; someone else's follow the filters | PixivNovelTest.theReadersOwnBookmarksKeepEverythingAndSomeoneElsesFollowTheChoices
covered | pixiv_novel_api_test › add and delete post the novel id and, for add, the visibility | PixivWritesTest.aNovelsBookmarkIsAddedAndDeleted
covered | pixiv_novel_api_test › lists watched series, skipping rows that do not parse | PixivNovelTest.theWatchlistListsWatchedSeriesSkippingRowsThatDoNotParse
covered | pixiv_novel_api_test › add and delete post the series id | PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
covered | pixiv_novel_api_test › asks by series id, then by next_url, and hides chapters the filters hide | PixivNovelTest.aSeriesIsAskedByIdThenByNextUrlHidingWhatTheChoicesHide
covered | pixiv_novel_api_test › sends the word, place to look, order and dates to /v1/search/novel, then follows next_url | PixivNovelTest.searchSendsTheWordPlaceOrderAndDatesThenFollowsNextUrl
covered | pixiv_novel_api_test › the search's own AI choice decides, not the feeds' | PixivNovelTest.theSearchsOwnAiChoiceDecidesNotTheFeeds
covered | pixiv_novel_api_test › read the novel tags with their pictures, leaving a filtered picture out | PixivNovelTest.trendingNovelTagsKeepTheirPicturesUnlessTheChoicesHideThem
covered | pixiv_novel_api_test › a reshaped answer is an empty list | PixivNovelTest.aMissingOrReshapedNovelReadsAsEmptyFieldsNeverAThrow
covered | pixiv_novel_api_test › ask by user id with the Android filter; the reader's own keep R-18 and AI novels | PixivNovelTest.aCreatorsNovelsAskByUserIdAndTheReadersOwnKeepEverything
covered | pixiv_novel_api_test › reads the novel by id | PixivNovelTest.aNovelsDetailIsReadByIdAndAWithheldOneIsNotFound
covered | pixiv_novel_api_test › a withheld or missing novel is not found rather than a throw from parsing | PixivNovelTest.aNovelsDetailIsReadByIdAndAWithheldOneIsNotFound
covered | pixiv_novel_api_test › content reads the webview page and the novel object in it | PixivNovelTest.aNovelsTextIsReadFromItsWebviewPage
covered | pixiv_novel_api_test › a page without the novel object is a bad response | PixivNovelTest.aPageWithoutTheNovelObjectIsABadResponse

## pixiv_novel_history_test.dart
covered | pixiv_novel_history_test › keep the card fields and round-trip, a novel without a cover included | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
covered | pixiv_novel_history_test › the novel history keeps its own file and reloads novels without covers | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
covered | pixiv_novel_history_test › opens its reader, which records it in the novel history | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
untested | pixiv_novel_history_test › a paused history records nothing | PixivHistory screen (Compose) | the pause switch is Compose-only
untested | pixiv_novel_history_test › switches to the novels, newest first, and the filter carries over | PixivHistory screen (Compose) | Compose only
covered | pixiv_novel_history_test › a long press forgets one novel and Clear all empties only the novels | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
covered | pixiv_novel_history_test › a novel keeps its R-18 and AI marks in the history | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
covered | pixiv_novel_history_test › a muted novel stays in the history, and tapping one opens it by id and moves it up | PixivMutesTest.novelsAreHiddenByTheirCreatorTheirIdOrTheirTags
untested | pixiv_novel_history_test › More opens the history on the kind the sections show | PixivHistory screen (Compose) | Compose only
untested | pixiv_novel_history_test › the switch and the novels fit a narrow phone at large text | PixivHistory screen (Compose) | Compose only
covered | pixiv_novel_history_test › forgetting what was loaded empties the novel history and reloads the novel searches | PixivSettingsStoreTest.aBackupRestoredIntoTheAppShowsHere

## pixiv_novel_models_test.dart
covered | pixiv_novel_models_test › reads every field a list sends | PixivNovelTest.aNovelReadsEveryFieldAListSends
covered | pixiv_novel_models_test › R-18, R-18G and AI come from x_restrict and novel_ai_type | PixivNovelTest.ratingsAndAiComeFromXRestrictAndNovelAiType
covered | pixiv_novel_models_test › a missing or reshaped payload reads as empty fields, never a throw | PixivNovelTest.aMissingOrReshapedNovelReadsAsEmptyFieldsNeverAThrow
covered | pixiv_novel_models_test › a novel Pixiv withholds, or one without an id, is not a novel | PixivNovelTest.aNovelPixivWithholdsOrOneWithoutAnIdIsNoNovel
covered | pixiv_novel_models_test › leaves out R-18 and AI novels unless asked for, and skips what does not parse | PixivNovelTest.aListLeavesOutR18AndAiUnlessAskedAndSkipsWhatDoesNotParse
covered | pixiv_novel_models_test › a reshaped list is an empty list | PixivNovelTest.aMissingOrReshapedNovelReadsAsEmptyFieldsNeverAThrow
covered | pixiv_novel_models_test › reads the series, its first and newest chapters, and numbers the chapters in order | PixivNovelTest.aSeriesPageReadsTheSeriesItsEndsAndNumbersItsChapters
covered | pixiv_novel_models_test › a chapter the filters hide keeps the next one's number, and a hidden newest chapter is not offered | PixivNovelTest.aChapterTheFiltersHideKeepsTheNextOnesNumberAndAHiddenNewestIsNotOffered
covered | pixiv_novel_models_test › a reshaped page has no series and no chapters | PixivNovelTest.aReshapedSeriesPageHasNoSeriesAndNoChapters
covered | pixiv_novel_models_test › hides novels by author, by tag name, by pattern and by id | PixivMutesTest.novelsAreHiddenByTheirCreatorTheirIdOrTheirTags
covered | pixiv_novel_models_test › filters any list whose items carry a novel | PixivMutesTest.novelsAreHiddenByTheirCreatorTheirIdOrTheirTags
covered | pixiv_novel_models_test › carries the text, the chapters beside it and the pictures it names | PixivNovelMarkupTest.aNovelsPageCarriesItsTextTheChaptersBesideItAndItsPictures
covered | pixiv_novel_models_test › a first page may be filed under the bare work id | PixivNovelMarkupTest.aFirstPageMayBeFiledUnderTheBareWorkId
covered | pixiv_novel_models_test › missing or reshaped parts are empty, and no text is no content | PixivNovelMarkupTest.missingOrReshapedPartsAreEmptyAndNoTextIsNoContent
covered | pixiv_novel_models_test › a novel joins the history under its cover | PixivKeptTest.aKeptNovelShowsAsTheNovelItWas

## pixiv_novel_parser_test.dart
covered | pixiv_novel_parser_test › is cut at its own closing brace when the text holds braces, quotes and escapes | PixivNovelMarkupTest.theNovelObjectIsCutAtItsOwnClosingBraceWhateverTheTextHolds
covered | pixiv_novel_parser_test › skips a "novel:" that opens no object and takes the next one | PixivNovelMarkupTest.aNovelLabelThatOpensNoObjectIsPassedOver
covered | pixiv_novel_parser_test › is null for a page without one, an unclosed object or broken JSON | PixivNovelMarkupTest.aPageWithoutAnObjectAnUnclosedOneOrBrokenJsonHasNone
covered | pixiv_novel_parser_test › every line is a paragraph and blank lines are kept | PixivNovelMarkupTest.everyLineIsAParagraphAndBlankLinesAreKept
covered | pixiv_novel_parser_test › [newpage] breaks are numbered from page 2 | PixivNovelMarkupTest.pageBreaksAreNumberedFromPageTwo
covered | pixiv_novel_parser_test › [chapter:] is a heading, ruby inside it included | PixivNovelMarkupTest.aChapterIsAHeadingRubyInsideItIncluded
covered | pixiv_novel_parser_test › [[rb:]] takes either arrow and keeps the text around it | PixivNovelMarkupTest.rubyTakesEitherArrowAndKeepsTheTextAroundIt
covered | pixiv_novel_parser_test › [[jumpuri:]] links Pixiv and outside addresses alike, the label kept | PixivNovelMarkupTest.jumpuriLinksPixivAndOutsideAddressesAlikeTheLabelKept
covered | pixiv_novel_parser_test › a jumpuri without a label shows its address; one that is no web address stays text | PixivNovelMarkupTest.aLinkWithoutALabelShowsItsAddressAndOneThatIsNoWebAddressStaysText
covered | pixiv_novel_parser_test › [jump:N] is a page jump | PixivNovelMarkupTest.jumpIsAPageJump
covered | pixiv_novel_parser_test › [pixivimage:] and [uploadedimage:] are pictures of their own, splitting a line | PixivNovelMarkupTest.picturesAreBlocksOfTheirOwnSplittingALine
covered | pixiv_novel_parser_test › space beside a block tag is not a line of its own | PixivNovelMarkupTest.spaceBesideABlockTagIsNotALineOfItsOwn
covered | pixiv_novel_parser_test › unknown tags and malformed markup stay as text without throwing | PixivNovelMarkupTest.unknownTagsAndMalformedMarkupStayTextWithoutThrowing
covered | pixiv_novel_parser_test › an empty text is one blank line | PixivNovelMarkupTest.anEmptyTextIsOneBlankLine
covered | pixiv_novel_parser_test › drops the markup: ruby in brackets, links by label, pictures left out | PixivNovelMarkupTest.thePlainTextDropsTheMarkup
untested | pixiv_novel_parser_test › a long text is parsed off the UI thread to the same blocks | PixivNovelMarkup.kt (Dispatchers) | the parse is off the main thread; the thread jump is not tested

## pixiv_novel_profile_test.dart
untested | pixiv_novel_profile_test › shows for a creator with novels: their novels, then their novel bookmarks | PixivUser screen (Compose) | Compose only
untested | pixiv_novel_profile_test › is left out for a creator without novels, but shown on the reader's own profile | PixivUser screen (Compose) | Compose only
untested | pixiv_novel_profile_test › fits a narrow phone at large text | PixivUser screen (Compose) | Compose only
covered | pixiv_novel_profile_test › a creator shown anyway past their mute still shows their own novels | PixivMutesTest.aCreatorsOwnScreenShowsThemEvenWhenMuted
covered | pixiv_novel_profile_test › parse, filter and keep three, skipping what does not parse | PixivNovelTest.aListLeavesOutR18AndAiUnlessAskedAndSkipsWhatDoesNotParse
untested | pixiv_novel_profile_test › fall back to the works when the creator has no novel to show | PixivUser screen (Compose) | Compose only
covered | pixiv_novel_profile_test › a novel's long press offers its comments and opens them for the novel | PixivCommentsListTest.theNovelTargetReadsTheNovelsCommentsAndReplies | the sheet is Compose-only

## pixiv_novel_reader_test.dart
untested | pixiv_novel_reader_test › a novel from a list shows its header, then its text laid out from the markup | PixivNovelReader.kt (Compose) | Compose only
covered | pixiv_novel_reader_test › a novel opened by its id fetches its detail and joins the novel history | PixivNovelTest.aNovelsDetailIsReadByIdAndAWithheldOneIsNotFound; PixivKeptTest.aKeptNovelShowsAsTheNovelItWas
untested | pixiv_novel_reader_test › a card opens its novel in the reader, from what the list already has | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › a paused history leaves the opened novel out | PixivNovelReader.kt; history pause | Compose only
untested | pixiv_novel_reader_test › a novel that will not load says why and loads again on retry | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the text is selectable, so Copy and translators reach it | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the shared appearance sheet changes the text size | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › chapters beside the novel: off when not viewable, replacing the reader when opened | PixivNovelTest.aSeriesPageReadsTheSeriesItsEndsAndNumbersItsChapters | the buttons are Compose-only
untested | pixiv_novel_reader_test › the menu offers the chapters too, with the unviewable one off | PixivNovelReader.kt | Compose only
covered | pixiv_novel_reader_test › View comments opens the novel's comments, above and below the text | PixivCommentsListTest.theNovelTargetReadsTheNovelsCommentsAndReplies
covered | pixiv_novel_reader_test › a page jump scrolls to the start of its page | PixivNovelMarkupTest.jumpIsAPageJump | the scroll is Compose-only
covered | pixiv_novel_reader_test › an outside link asks first; a Pixiv link opens in XTA | PixivNovelMarkupTest.jumpuriLinksPixivAndOutsideAddressesAlikeTheLabelKept; PixivLinksTest (open)
covered | pixiv_novel_reader_test › a link to another novel opens it in the reader | PixivLinksTest.aTagsNovelsPageSearchesNovelsAndItsOtherPagesWorks
untested | pixiv_novel_reader_test › pictures: a work missing from the page is fetched, a tap opens it, a long press saves | PixivNovelMarkupTest.picturesAreBlocksOfTheirOwnSplittingALine | the fetch and gestures are Compose-only
untested | pixiv_novel_reader_test › pictures open and save from the picked image server; a fetched work saves as its page | PixivImagesTest (server choice); PixivSaving.kt | the gestures are Compose-only
covered | pixiv_novel_reader_test › export saves <title>.txt, as plain text or with the markup | PixivNovelExportTest.theFileIsNamedAfterTheNovel, plainTextLeavesTheMarkupOut, theMarkupIsKeptAsPixivStoresIt
covered | pixiv_novel_reader_test › an export name loses what no file system accepts and falls back to the id | PixivNovelExportTest.aTitleWithNothingAFileCanHoldNamesItByItsId
untested | pixiv_novel_reader_test › sharing from the menu sends the link with the menu button as the sheet's origin | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the series page shares its link from its button | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the author row opens the profile on its Novels tab | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the place reached is kept by passage and comes back on reopening | PixivNovelPlaces.kt | no separate test
untested | pixiv_novel_reader_test › with reading positions off, the place is not kept | PixivNovelPlaces.kt | no separate test
untested | pixiv_novel_reader_test › a novel only opened, or one that would not load, leaves no place behind | PixivNovelPlaces.kt | no separate test
untested | pixiv_novel_reader_test › with the history paused, a novel keeps no place and finds none | PixivNovelPlaces.kt | no separate test
untested | pixiv_novel_reader_test › places stay out of backups and go with the novel history when the plugin forgets its data | PixivNovelPlaces.kt; backup | no separate test
untested | pixiv_novel_reader_test › a text size dragged over several steps keeps the passage being read at the top | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › large text at 320 dp fits without overflow | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › a phone at normal text keeps the chapter buttons side by side | PixivNovelReader.kt | Compose only
untested | pixiv_novel_reader_test › the first block showing is the first whose bottom is below the top | PixivNovelPlaces.kt | no separate test

## pixiv_novel_screens_test.dart
untested | pixiv_novel_screens_test › the mode button swaps every section to novels and back | PixivNovel screens (Compose) | Compose only
covered | pixiv_novel_screens_test › Home switches between Recommended, Following by visibility and the Watchlist | PixivNovelTest.recommendedAsksForRankingNovelsThenFollowsNextUrl, followingSendsPublicOrPrivateAndTheReadersChoicesApply, theWatchlistListsWatchedSeriesSkippingRowsThatDoNotParse
untested | pixiv_novel_screens_test › each mode keeps its own place on Rankings and Favorites | PixivNovel screens (Compose) | Compose only
untested | pixiv_novel_screens_test › tapping the source, visibility or board already shown brings its list to the top | PixivNovel screens (Compose) | Compose only
covered | pixiv_novel_screens_test › another account empties the novel lists and reloads the one shown | PixivAccountsTest.aSwitchDropsWhatWasLoadedForTheLastAccount
covered | pixiv_novel_screens_test › View latest opens the newest chapter Pixiv names, else the series | PixivNovelTest.aSeriesPageReadsTheSeriesItsEndsAndNumbersItsChapters
untested | pixiv_novel_screens_test › View latest says so when Pixiv no longer has the chapter | PixivNovel screens (Compose) | Compose only
covered | pixiv_novel_screens_test › R-18 boards are offered while Show R-18 is on, and the shown one goes with it | PixivTagsTest.r18BoardsAreOfferedOnlyWhileShowR18IsOn; PixivRulesTest.aBoardWhoseChipIsGoneGivesWayToTheFirst
covered | pixiv_novel_screens_test › shows cover, title, author, length, ratings, series and tags | PixivNovelTest.aNovelReadsEveryFieldAListSends
covered | pixiv_novel_screens_test › the heart bookmarks with the default visibility, removes, and a long press files privately | PixivBookmarkingTest.aNovelsHeartUsesTheDefaultVisibilityAndItsLongPressBookmarksPrivately
covered | pixiv_novel_screens_test › a failed bookmark says why and leaves the heart as it was | PixivBookmarkingTest.aFailedWriteLeavesTheBookmarkAsItWasAndFreesTheWork
covered | pixiv_novel_screens_test › a long press offers bookmark, copy link and mutes; muting the novel hides it | PixivMutesTest.novelsAreHiddenByTheirCreatorTheirIdOrTheirTags
untested | pixiv_novel_screens_test › large text on a narrow screen wraps rather than overflows | PixivNovel screens (Compose) | Compose only
covered | pixiv_novel_screens_test › shows its state, totals and caption, numbers its chapters and toggles the watchlist | PixivNovelTest.aSeriesPageReadsTheSeriesItsEndsAndNumbersItsChapters; PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
covered | pixiv_novel_screens_test › an ongoing series whose first chapter is hidden offers no start | PixivNovelTest.aChapterTheFiltersHideKeepsTheNextOnesNumberAndAHiddenNewestIsNotOffered
untested | pixiv_novel_screens_test › a series that fails to load offers a retry | PixivNovel screens (Compose) | Compose only
covered | pixiv_novel_screens_test › a profile's Bookmarks tab switches to the creator's public novel bookmarks | PixivNovelTest.theReadersOwnBookmarksKeepEverythingAndSomeoneElsesFollowTheChoices
covered | pixiv_novel_screens_test › another account drops the novel bookmarks this session made | PixivAccountsTest.aSwitchDropsWhatWasLoadedForTheLastAccount

## pixiv_novel_search_screen_test.dart
covered | pixiv_novel_search_screen_test › the landing shows recent novel searches and trending novel tags, not the works' ones | PixivNovelTest.trendingNovelTagsKeepTheirPicturesUnlessTheChoicesHideThem; PixivSearchMemoryTest
covered | pixiv_novel_search_screen_test › a novel's tag searches novels, not works | PixivNovelTest.searchSendsTheWordPlaceOrderAndDatesThenFollowsNextUrl
covered | pixiv_novel_search_screen_test › a search lists novels and creators with their novel covers and titles | PixivNovelTest.searchSendsTheWordPlaceOrderAndDatesThenFollowsNextUrl
covered | pixiv_novel_search_screen_test › the filter sheet offers the novel places to look and keeps popular for Premium | PixivSearchTest.novelsOfferTheirOwnPlacesAndPopularOnlyToPremium
covered | pixiv_novel_search_screen_test › Premium sorts novels by popularity | PixivSearchTest.novelsOfferTheirOwnPlacesAndPopularOnlyToPremium
covered | pixiv_novel_search_screen_test › a number offers the novel, its series and its author, and opens the series | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_novel_search_screen_test › a novel number Pixiv does not have says so | PixivNovelTest.aNovelsDetailIsReadByIdAndAWithheldOneIsNotFound
untested | pixiv_novel_search_screen_test › the shortcuts and creator cards fit a narrow phone at large text | PixivNovel search (Compose) | Compose only
untested | pixiv_novel_search_screen_test › Novel mode's Search section is novel search, and Illustration mode's the works one | PixivNovel search (Compose) | Compose only

## pixiv_novel_search_test.dart
covered | pixiv_novel_search_test › offer the novel places to look, and popular only to Premium | PixivSearchTest.novelsOfferTheirOwnPlacesAndPopularOnlyToPremium
covered | pixiv_novel_search_test › the novel query takes no AI or bookmark parameters | PixivSearchTest.theNovelQueryTakesNoAiOrBookmarkParameters
covered | pixiv_novel_search_test › a filter carried over keeps only what novels take | PixivSearchTest.aFilterCarriedOverToNovelsKeepsOnlyWhatNovelsTake
covered | pixiv_novel_search_test › novels remember their own filter apart from the works' | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_novel_search_test › a pasted number opens a novel; a link opens what it names | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_novel_search_test › a search finds novels and creators and keeps the word in the novel history | PixivNovelTest.searchSendsTheWordPlaceOrderAndDatesThenFollowsNextUrl
covered | pixiv_novel_search_test › a filter change searches again and is remembered under the novel key | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_novel_search_test › Hide AI reaches the novel search, which then leaves AI novels out | PixivNovelTest.theSearchsOwnAiChoiceDecidesNotTheFeeds
covered | pixiv_novel_search_test › the landing loads the novel trending tags and no suggested creators | PixivNovelTest.trendingNovelTagsKeepTheirPicturesUnlessTheChoicesHideThem

## pixiv_novel_store_test.dart
covered | pixiv_novel_store_test › follow the card until a write, and move the count by one | PixivBookmarkingTest.theCountMovesWithWhatThisSessionChanged
covered | pixiv_novel_store_test › a tap bookmarks with the default visibility, then removes | PixivBookmarkingTest.theHeartBookmarksPubliclyAndTakesTheBookmarkAwayOnPixiv
covered | pixiv_novel_store_test › a long press always files it privately, even a public bookmark | PixivBookmarkingTest.aNovelsHeartUsesTheDefaultVisibilityAndItsLongPressBookmarksPrivately
covered | pixiv_novel_store_test › a write while one is on its way is skipped, and a failed one leaves the heart as it was | PixivBookmarkingTest.aSecondTapWaitsForTheFirstWrite, aFailedWriteLeavesTheBookmarkAsItWasAndFreesTheWork
covered | pixiv_novel_store_test › numbers chapters across pages, counting the ones a page hid | PixivNovelTest.aChapterTheFiltersHideKeepsTheNextOnesNumberAndAHiddenNewestIsNotOffered
untested | pixiv_novel_store_test › a page asked for before a watchlist change keeps what the change left | PixivNovel screens (Compose/model) | Compose-side ordering
untested | pixiv_novel_store_test › a page that lands after its screen closed writes before the stores go | PixivNovel screens (Compose) | Compose-side lifetime
covered | pixiv_novel_store_test › the watchlist toggle writes, and a failure leaves it as it was | PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
covered | pixiv_novel_store_test › each list loads with the choices made, read when it loads | PixivNovelTest.followingSendsPublicOrPrivateAndTheReadersChoicesApply
covered | pixiv_novel_store_test › a board hidden by Show R-18 going off moves to the first chip | PixivRulesTest.aBoardWhoseChipIsGoneGivesWayToTheFirst
covered | pixiv_novel_store_test › the novel boards pin under their own preference, with every board neither R-18 nor AI by default | PixivRulesTest.pinsDropUnknownBoardsAndFallBackToTheDefaults

## pixiv_models_test.dart
covered | pixiv_models_test › reads fields and filters R-18 by default | PixivModelsTest.aListPageReadsEachWorkAndLeavesOutR18AndStubsByDefault
covered | pixiv_models_test › reads tags, size and manga page urls | PixivModelsTest.aWorksDetailCarriesItsSeriesCommentsFollowAndCaptionMarkup, aMangaShowsItsLargePagesAndSavesItsOriginals
covered | pixiv_models_test › keeps R-18 when asked | PixivModelsTest.theReadersChoicesDecideR18AndAiWorks
covered | pixiv_models_test › drops deleted-or-private placeholder stubs | PixivModelsTest.aListPageReadsEachWorkAndLeavesOutR18AndStubsByDefault
covered | pixiv_models_test › related to an R-18 seed keeps R-18 even when the home feed hides it | PixivClientTest.relatedWorksOfAnR18SeedKeepR18
untested | pixiv_models_test › the detail viewer follows the art instead of a fixed 55% frame | Compose (PixivDetail) | Compose only
covered | pixiv_models_test › a reshaped payload yields nothing rather than throwing | PixivModelsTest.aReshapedPageYieldsNothingRatherThanThrowing
covered | pixiv_models_test › reads the nested user and profile objects | PixivModelsTest.aDetailsUserCarriesTheProfileCounts
covered | pixiv_models_test › a profile without counts, or with counts as strings, still reads | PixivSocialTest.aProfileReadsTheUserTheCountsTheLinksAndTheDetails

## pixiv_model_fields_test.dart
covered | pixiv_model_fields_test › a full payload carries series, comments, follow, caption HTML and rating | PixivModelsTest.aWorksDetailCarriesItsSeriesCommentsFollowAndCaptionMarkup
covered | pixiv_model_fields_test › a payload without them falls back to quiet defaults | PixivModelsTest.aWorkWithoutThemFallsBackToQuietDefaults
covered | pixiv_model_fields_test › a reshaped payload neither throws nor invents values | PixivModelsTest.aReshapedWorkNeitherThrowsNorInventsValues
covered | pixiv_model_fields_test › copyWith keeps the new fields and can change the follow | PixivModelsTest.aWorksDetailCarriesItsSeriesCommentsFollowAndCaptionMarkup (copyWith)
covered | pixiv_model_fields_test › reads premium and a quoted id | PixivAuthTest.theTokenResponsesUserReadsAQuotedIdAndPremium
covered | pixiv_model_fields_test › a missing or reshaped user is an unknown free account | PixivModelsTest.aReshapedWorkNeitherThrowsNorInventsValues
covered | pixiv_model_fields_test › keeps follow, mute and the raw novels beside each creator | PixivModelsTest.aCreatorPreviewKeepsFollowMuteAndTheirWorksAndNovels
covered | pixiv_model_fields_test › drops R-18 and AI previews the way the feeds drop them | PixivModelsTest.aCreatorPreviewLeavesOutR18AndAiAsTheFeedsDo
covered | pixiv_model_fields_test › a reshaped list is no creators | PixivModelsTest.aCreatorPreviewLeavesOutR18AndAiAsTheFeedsDo

## pixiv_extras_test.dart
untested | pixiv_extras_test › zooming keeps the tapped point where it was | Compose (ZoomableImage) | Compose only
untested | pixiv_extras_test › a double-tap zooms in where tapped and the next one zooms back out | Compose (ZoomableImage) | Compose only
untested | pixiv_extras_test › tapping the detail image opens that page in the zoomable reader | Compose (PixivDetail) | Compose only
untested | pixiv_extras_test › the detail screen shows the author's other works and opens them | Compose (PixivDetail More by) | Compose only
untested | pixiv_extras_test › leaving the detail before the author's works arrive is safe | Compose (PixivDetail) | Compose only
untested | pixiv_extras_test › an author with no other works adds nothing | Compose (PixivDetail) | Compose only
covered | pixiv_extras_test › show Pixiv's spelling with the translation beside it, ordered by kind | PixivModelsTest.aTagShowsItsTranslationOnlyWhenItDiffers; PixivTagsTest.tagsAreOrderedByKindKeepingPixivsOrderWithinOne
untested | pixiv_extras_test › a tap searches the tag | Compose (PixivDetail) | Compose only
untested | pixiv_extras_test › a long press offers to mute the tag, as the mute sheet does | PixivMutes.kt; Compose sheet | Compose only
covered | pixiv_extras_test › copies the link and downloads every page | PixivSaving.kt; ui/downloads/PluginBatches.kt | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
covered | pixiv_extras_test › the creator's AI flag is read and can be filtered out | PixivModelsTest.theReadersChoicesDecideR18AndAiWorks
covered | pixiv_extras_test › hiding AI works filters feeds but never the reader's own bookmarks | PixivClientTest.illustPageFollowsTheReadersFiltersUnlessItIsTheirOwnList
untested | pixiv_extras_test › AI works carry a badge in the grid | Compose (PixivGrid TileBadges) | Compose only
covered | pixiv_extras_test › the settings switch turns AI hiding on and off | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
covered | pixiv_extras_test › metadata lists the archive and each frame's delay | PixivUgoiraTest.theMetadataListsTheArchiveAndEachFramesDelay
covered | pixiv_extras_test › frames come out of a deflated archive intact | PixivUgoiraTest.framesComeOutOfAStoredOrDeflatedArchiveIntact
covered | pixiv_extras_test › a damaged archive is refused | PixivUgoiraTest.aDamagedArchiveReadsAsNone
untested | pixiv_extras_test › playback shows frames in order for their delays and stops when paused | Compose (PixivUgoiraPlayer) | Compose only
untested | pixiv_extras_test › the detail screen plays an ugoira in place and pauses it | Compose (PixivDetail) | Compose only

## pixiv_store_test.dart
covered | pixiv_store_test › appends only new ids | PixivPagingTest.pagesJoinInOrderWithoutRepeatsToTheEnd
covered | pixiv_store_test › soft refresh keeps prior tiles while replacing contents | PixivPagingTest.aFirstPageThatFailsSaysWhyAndALaterRefreshKeepsWhatWasShown
covered | pixiv_store_test › loadMore dedupes and does not wipe the list on append failure | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
covered | pixiv_store_test › switching sources never shows the old source under the new label | PixivPagingTest.whatTheDeviceKeepsIsReadAgainOnlyWhenItChanges
covered | pixiv_store_test › soft refresh failure keeps prior tiles | PixivPagingTest.aFirstPageThatFailsSaysWhyAndALaterRefreshKeepsWhatWasShown
covered | pixiv_store_test › skips fully filtered pages until something visible remains | PixivPagesTest.pagesTheFiltersEmptyAreSkippedUntilSomethingShows

## pixiv_shared_state_test.dart
covered | pixiv_shared_state_test › are saved, reloaded and removed like the other mutes | PixivMutesTest.theMutesComeBackFromXtasPreferences
covered | pixiv_shared_state_test › a damaged pref reads as nothing muted | PixivMutesTest.theMutesComeBackFromXtasPreferences
untested | pixiv_shared_state_test › copyWith keeps every set it was not asked to change | PixivSearchModel.kt (copyWith) | no separate test
covered | pixiv_shared_state_test › defaults to today's Home, Following and bookmarks | PixivRulesTest.pinsDropUnknownBoardsAndFallBackToTheDefaults
covered | pixiv_shared_state_test › copyWith sets and clears the bookmark tag and keeps the rest | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_shared_state_test › keeps what the old Pixiv history stored under the same pref | PixivKeptTest.theHistoryIsReadBackFromItsFile
covered | pixiv_shared_state_test › a search differing only in case replaces the older one | PixivSearchMemoryTest.recentSearchesPutTheNewestFirstOnceWhateverItsCase
untested | pixiv_shared_state_test › a search made on a pushed screen already shows on the one underneath | Compose state | Compose only

## pixiv_viewing_test.dart
covered | pixiv_viewing_test › each place starts at its default and ignores a size it does not offer | PixivRulesTest.aPickedColumnCountYieldsOnlyToANarrowPane
covered | pixiv_viewing_test › a tile shows the medium preview, or the large image when Pixiv sent one | PixivImagesTest.aTileShowsTheMediumPreviewOrTheLargePictureWhenPixivSentOne
covered | pixiv_viewing_test › a page comes in each size, falling back to what Pixiv did send | PixivImagesTest.aPageComesInEachSizeFallingBackToWhatPixivSent
covered | pixiv_viewing_test › the grid size setting changes which file tiles load | PixivImagesTest.aTileShowsTheMediumPreview…; PixivRulesTest
covered | pixiv_viewing_test › the work page size setting changes which file the detail loads | PixivImagesTest.aPageComesInEachSizeFallingBackToWhatPixivSent
covered | pixiv_viewing_test › automatic follows the gallery layout; a picked count applies to its orientation only | PixivRulesTest.aPickedColumnCountYieldsOnlyToANarrowPane
covered | pixiv_viewing_test › a picked count never squeezes tiles below 96dp, and an unknown count means automatic | PixivRulesTest.aPickedColumnCountYieldsOnlyToANarrowPane
covered | pixiv_viewing_test › a grid takes the picked count and follows when it changes | PixivRulesTest.aPickedColumnCountYieldsOnlyToANarrowPane
untested | pixiv_viewing_test › the AI badge shows until switched off, live in the grid | Compose (PixivGrid) | Compose only
untested | pixiv_viewing_test › a failed tile retries on tap while the rest of it still opens the work | Compose (Images.kt) | Compose only
untested | pixiv_viewing_test › a detail page that failed offers retry over its poster | Compose (PixivDetail) | Compose only
untested | pixiv_viewing_test › an image that fails never shows the library's English text; an avatar falls back to initials | Compose (Images.kt) | Compose only
untested | pixiv_viewing_test › reader progress is the share of bytes that arrived, unknown without a size | Compose (progress) | Compose only

## pixiv_tag_kinds_test.dart
covered | pixiv_tag_kinds_test › reads the series out of ASCII and full-width parentheses | PixivTagsTest.aCharacterTagNamesItsSeriesInAsciiOrFullWidthParentheses
covered | pixiv_tag_kinds_test › needs both a name and a series | PixivTagsTest.aCharacterTagNeedsBothANameAndASeriesAndMatchingParentheses
covered | pixiv_tag_kinds_test › mismatched parentheses name no series | PixivTagsTest.aCharacterTagNeedsBothANameAndASeriesAndMatchingParentheses
covered | pixiv_tag_kinds_test › a Name(Series) tag is a character and its series a copyright | PixivTagsTest.aNameSeriesTagIsACharacterAndItsSeriesACopyright
covered | pixiv_tag_kinds_test › full-width parentheses name a character and a copyright too | PixivTagsTest.aNameSeriesTagIsACharacterAndItsSeriesACopyright
covered | pixiv_tag_kinds_test › a series no other tag names stays a character without a copyright | PixivTagsTest.aNameSeriesTagIsACharacterAndItsSeriesACopyright
covered | pixiv_tag_kinds_test › Pixiv's original tag is a copyright | PixivTagsTest.originalIsACopyrightAndRatingsAiAndMilestonesAreMeta
covered | pixiv_tag_kinds_test › ratings, AI and bookmark milestones are meta | PixivTagsTest.originalIsACopyrightAndRatingsAiAndMilestonesAreMeta
covered | pixiv_tag_kinds_test › anything unsure is general | PixivTagsTest.originalIsACopyrightAndRatingsAiAndMilestonesAreMeta
covered | pixiv_tag_kinds_test › tags are ordered copyright, character, general, meta and keep Pixiv's order within a kind | PixivTagsTest.tagsAreOrderedByKindKeepingPixivsOrderWithinOne
covered | pixiv_tag_kinds_test › a work without tags has none | PixivTagsTest.tagsAreOrderedByKindKeepingPixivsOrderWithinOne

## pixiv_ranking_modes_test.dart
covered | pixiv_ranking_modes_test › offers R-18 boards only while Show R-18 is on | PixivTagsTest.r18BoardsAreOfferedOnlyWhileShowR18IsOn
covered | pixiv_ranking_modes_test › marks the AI boards, which keep AI works under Hide AI | PixivTagsTest.theAiBoardsAreMarkedForWorksAndNovelsAlike
covered | pixiv_ranking_modes_test › read back without unknown or repeated boards, and fall back to the defaults | PixivRulesTest.pinsDropUnknownBoardsAndFallBackToTheDefaults
covered | pixiv_ranking_modes_test › show in table order, R-18 ones only with Show R-18, and never none | PixivRulesTest.theChipsFollowTheTableAndAlwaysShowOne
covered | pixiv_ranking_modes_test › the shown board falls back to the first chip when its own is gone | PixivRulesTest.aBoardWhoseChipIsGoneGivesWayToTheFirst
covered | pixiv_ranking_modes_test › are saved as JSON in table order and survive a new store | PixivRulesTest.pinningKeepsTheTablesOrderAndTheLastChipStays
covered | pixiv_ranking_modes_test › follow the stored pins, so an import or a reset shows and is not written back | PixivRulesTest.pinningKeepsTheTablesOrderAndTheLastChipStays
covered | pixiv_ranking_modes_test › a tap while the last one is still being saved builds on it | PixivSettingsStoreTest.changesReachTheAppInTheOrderMade
covered | pixiv_ranking_modes_test › pins a board from the editor; the chip stays for the next visit | PixivRulesTest.pinningKeepsTheTablesOrderAndTheLastChipStays
untested | pixiv_ranking_modes_test › pinned chips stay legible on the accent in the true-black theme | Compose (chips) | Compose only
covered | pixiv_ranking_modes_test › offers R-18 boards with Show R-18 on and keeps the last chip pinned | PixivRulesTest.theChipsFollowTheTableAndAlwaysShowOne
covered | pixiv_ranking_modes_test › Show R-18 going off from any settings entry moves off an R-18 board | PixivRulesTest.aBoardWhoseChipIsGoneGivesWayToTheFirst
covered | pixiv_ranking_modes_test › unpinning the board on show moves to the first chip and reloads | PixivRulesTest.aBoardWhoseChipIsGoneGivesWayToTheFirst

## pixiv_search_filters_test.dart
covered | pixiv_search_filters_test › past day and week step back across a month edge | PixivSearchTest.aPastDayAndWeekStepBackAcrossAMonthEdge
covered | pixiv_search_filters_test › a month back from the 31st lands on the shorter month's last day | PixivSearchTest.aMonthBackFromThe31stLandsOnTheShorterMonthsLastDay
covered | pixiv_search_filters_test › month, six months and a year cross the year edge | PixivSearchTest.monthsHalfYearsAndYearsCrossTheYearEdge
covered | pixiv_search_filters_test › any time sends no dates; a custom range sends its own, padded | PixivSearchTest.anyTimeSendsNoDatesAndACustomRangeSendsItsOwn
covered | pixiv_search_filters_test › custom without dates means any time | PixivSearchTest.anyTimeSendsNoDatesAndACustomRangeSendsItsOwn
covered | pixiv_search_filters_test › carries target, sort and the fixed parameters | PixivSearchTest.theQueryCarriesThePlaceTheOrderAndPixivsFixedParameters
covered | pixiv_search_filters_test › the users入り threshold is appended to the sent word only | PixivSearchTest.thePopularityTagGoesOnTheSentWordAlone
covered | pixiv_search_filters_test › Premium bookmark brackets map to min and max | PixivSearchTest.premiumBookmarkBracketsBecomeAMinimumAndAMaximum
covered | pixiv_search_filters_test › the AI switch sends search_ai_type 1 to hide and 0 to include | PixivSearchTest.theAiSwitchSendsOneToHideAndZeroToInclude
covered | pixiv_search_filters_test › the popular preview asks for translated tags too | PixivSearchTest.thePopularPreviewAsksForTranslatedTagsToo
covered | pixiv_search_filters_test › only the audience sorts are kept for Premium; oldest first is free | PixivSearchTest.onlyTheAudienceOrdersNeedPremiumForWorks
covered | pixiv_search_filters_test › an account without Premium drops a Premium order and the bookmark bracket | PixivSearchTest.anAccountWithoutPremiumDropsAPremiumOrderAndTheBookmarkBracket
covered | pixiv_search_filters_test › a filter survives a JSON round trip | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_search_filters_test › unknown or reshaped fields keep the fallback | PixivSearchMemoryTest.whatAKeptFilterLacksKeepsTheFallbacks
covered | pixiv_search_filters_test › prefs keep a filter, forget it, and shrug off garbage | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_search_filters_test › only the word after the last space is being typed | PixivSearchMemoryTest.theWordBeingTypedIsTheOneAfterTheLastSpace
covered | pixiv_search_filters_test › a picked suggestion replaces the last word and leaves room for the next | PixivSearchMemoryTest.theWordBeingTypedIsTheOneAfterTheLastSpace
covered | pixiv_search_filters_test › digits alone name an id | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_search_filters_test › the ugoira choice keeps all, only ugoira, or none | PixivSearchTest.theUgoiraChoiceKeepsAllOnlyUgoiraOrNone
covered | pixiv_search_filters_test › counts are formatted even in a language intl has no number symbols for | StockNumbersTest.theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish

## pixiv_search_api_test.dart
covered | pixiv_search_api_test › illust search sends the filter query and follows next_url | PixivSearchTest.aWorkSearchSendsTheFilterQueryAndFollowsNextUrl
covered | pixiv_search_api_test › without an AI choice the feeds' Hide AI applies | PixivSearchTest.withoutAnAiChoiceTheFeedsHideAiApplies
covered | pixiv_search_api_test › user search sends the word and parses each creator with their previews | PixivSearchTest.aCreatorSearchSendsTheWordAndReadsEachCreatorWithTheirPreviews
covered | pixiv_search_api_test › the popular preview pages through its own endpoint | PixivSearchTest.thePopularPreviewPagesThroughItsOwnEndpoint
covered | pixiv_search_api_test › a reshaped answer reads as an empty page | PixivSearchTest.aReshapedAnswerReadsAsAnEmptyPage

## pixiv_social_api_test.dart
covered | pixiv_social_api_test › userProfile asks for the detail and reads the whole profile | PixivSocialTest.aProfileReadsTheUserTheCountsTheLinksAndTheDetails
covered | pixiv_social_api_test › userProfile refuses an answer without a user | PixivSocialTest.aProfileIsAskedByUserIdAndOneWithoutAUserRefused
covered | pixiv_social_api_test › reads whether and how the reader follows | PixivSocialTest.theFollowDetailSaysWhetherAndHowTheReaderFollows
covered | pixiv_social_api_test › a reshaped answer reads as not followed | PixivSocialTest.aReshapedProfileKeepsWhatStillParsesAndDropsWhatIsNoLink
covered | pixiv_social_api_test › asks for manga by type and leaves out R-18 for someone else | PixivSocialTest.aCreatorsWorksComeByKindAndLeaveOutR18ForSomeoneElse
covered | pixiv_social_api_test › keeps every one of the reader's own works | PixivSocialTest.theReadersOwnProfileKeepsEveryWorkTheyPosted
covered | pixiv_social_api_test › is the same request the More by strip and group posts make through the client | PixivSocialTest.aCreatorsWorksComeByKindAndLeaveOutR18ForSomeoneElse
covered | pixiv_social_api_test › userBookmarks sends the user, restrict and tag | PixivSocialTest.aCreatorsBookmarksSendTheUserTheVisibilityAndTheTag
covered | pixiv_social_api_test › following sends restrict and parses preview cards with their works | PixivSocialTest.followListsSendTheirVisibilityAndReadPreviewCards
covered | pixiv_social_api_test › followers are always public | PixivSocialTest.followListsSendTheirVisibilityAndReadPreviewCards
covered | pixiv_social_api_test › a list without previews is an empty last page | PixivSocialTest.aListWithoutPreviewsIsAnEmptyLastPage
covered | pixiv_social_api_test › a private follow posts restrict=private, and unfollow posts the delete | PixivWritesTest.aCreatorIsFollowedAndUnfollowed
covered | pixiv_social_api_test › a failed follow leaves the follow as it was | PixivWritesTest.aRefusedOrUnreadableWriteFails

## pixiv_saucenao_test.dart
covered | pixiv_saucenao_test › the HTML page gives each Pixiv work once, with similarity, title and artist | PixivSauceNaoTest.theHtmlPageGivesEachPixivWorkOnceWithSimilarityTitleAndArtist
covered | pixiv_saucenao_test › a page without result blocks still yields the Pixiv links on it | PixivSauceNaoTest.aPageWithoutResultBlocksStillGivesThePixivLinksOnIt
covered | pixiv_saucenao_test › a JSON answer reads pixiv_id or an ext_url, and skips other sites | PixivSauceNaoTest.aJsonAnswerReadsPixivIdOrAnExtUrlAndSkipsOtherSites
covered | pixiv_saucenao_test › broken JSON and empty pages give no matches | PixivSauceNaoTest.brokenJsonAndEmptyPagesGiveNoMatches
covered | pixiv_saucenao_test › ids come from artworks/<id> or illust_id= on Pixiv only | PixivSauceNaoTest.idsComeFromArtworksOrIllustIdOnPixivAlone
covered | pixiv_saucenao_test › posts the image as a multipart file to saucenao.com and parses the page | PixivSauceNaoTest.thePictureGoesUpAsAFormToSauceNaoAloneAndItsMatchesAreRead
covered | pixiv_saucenao_test › a refusal becomes a Pixiv error the sheet can word | PixivSauceNaoTest.aRateLimitIsSaidSoAndAnyOtherRefusalIsAnAnswerOfNoUse
covered | pixiv_saucenao_test › an upload that never gets an answer times out as a network error | PixivSauceNaoTest.aRateLimitIsSaidSoAndAnyOtherRefusalIsAnAnswerOfNoUse (timeout)

## pixiv_links_test.dart
covered | pixiv_links_test › treats bare numeric ids as artworks | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_links_test › parses artwork urls and paths | PixivLinksTest.worksAreReadFromAddressesAndPaths
covered | pixiv_links_test › parses user urls and paths | PixivLinksTest.creatorsAreReadFromAddressesAndPaths
covered | pixiv_links_test › ignores unsupported links | PixivLinksTest.unsupportedLinksNameNothing
covered | pixiv_links_test › reads the short forms and the old PHP pages | PixivLinksTest.theShortFormsAndTheOldPhpPagesAreRead
covered | pixiv_links_test › opens a tag page as its tag, decoded | PixivLinksTest.aTagPageOpensAsItsTagDecoded
covered | pixiv_links_test › a tag's novels page searches novels; its other pages search works | PixivLinksTest.aTagsNovelsPageSearchesNovelsAndItsOtherPagesWorks
covered | pixiv_links_test › a creator's page opens their profile on the tab it names | PixivLinksTest.aCreatorsPageOpensTheirProfileOnTheTabItNames
covered | pixiv_links_test › reads the work id from an image file name | PixivLinksTest.aPictureFileNamesItsWork
covered | pixiv_links_test › reads Pixiv's app links | PixivLinksTest.pixivsAppLinksAreRead
covered | pixiv_links_test › declares series, novels and pixivision with the page the browser opens | PixivLinksTest.seriesNovelsAndPixivisionKnowThePageABrowserOpens
covered | pixiv_links_test › keeps a pixiv.me name for resolving, but reads its old PHP links at once | PixivLinksTest.aPixivMeNameIsKeptForResolvingButItsOldPhpLinksAreReadAtOnce
covered | pixiv_links_test › follows one redirect, without following it automatically, to a pixiv.net page | PixivLinksTest.aShortLinkFollowsOneRedirectByHandToAPixivNetPage
covered | pixiv_links_test › accepts only a pixiv.net Location | PixivLinksTest.aShortLinkTrustsOnlyAPixivNetLocation
covered | pixiv_links_test › is null when there is no redirect or the request fails | PixivLinksTest.aShortLinkWithoutARedirectOrWhoseRequestFailsResolvesToNothing

## pixiv_image_source_test.dart
covered | pixiv_image_source_test › Pixiv's own server or nothing picked leaves the URL alone | PixivImagesTest.pixivsOwnServerOrNoChoiceLeavesTheAddressAlone
covered | pixiv_image_source_test › the mirror swaps only the host and keeps the path | PixivImagesTest.theMirrorSwapsOnlyTheHost
covered | pixiv_image_source_test › a custom server keeps its port and path prefix | PixivImagesTest.aServerOfTheReadersOwnKeepsItsPortAndPathPrefix
covered | pixiv_image_source_test › an address with spaces or of another kind is refused and changes nothing | PixivImagesTest.anUnusableServerIsRefusedAndChangesNothing
covered | pixiv_image_source_test › the static host and other servers are never mirrored | PixivImagesTest.theStaticServerAndOtherSitesAreNeverMirrored
covered | pixiv_image_source_test › a stored host reads as Pixiv, the mirror or a custom server | PixivImagesTest.aStoredHostReadsAsPixivTheMirrorOrACustomServer
covered | pixiv_image_source_test › downloads fetch from the picked server under Pixiv's file names | PixivImagesTest.aStoredHostReadsAsPixivTheMirrorOrACustomServer; PixivSavingTest
covered | pixiv_image_source_test › a single picture, such as an avatar or one in a novel, shows and saves from the picked server | PixivImagesTest (single picture)
covered | pixiv_image_source_test › an ugoira archive comes from the mirror once, then from memory | PixivUgoiraTest.theArchiveComesFromTheServerTheReaderPicked
covered | pixiv_image_source_test › a failed archive is fetched again next time | PixivUgoiraTest.aDamagedArchiveReadsAsNone
covered | pixiv_image_source_test › an image server's refusal is never blamed on the Pixiv token | PixivClientTest.pixivsRateLimitAnswerReadsAsRateLimitingNotARefusedToken
untested | pixiv_image_source_test › images on screen load from the picked server | Compose (Coil) | Compose only
untested | pixiv_image_source_test › keeps the most recently used values up to its capacity | PixivImages.kt (cache) | no separate test
untested | pixiv_image_source_test › a cleanup only drops the value it was meant for | PixivImages.kt (cache) | no separate test

## pixiv_loads_test.dart
untested | pixiv_loads_test › destroys the stores only once the loads tracked so far settle | Compose lifetime (PixivRuntime) | Compose only
untested | pixiv_loads_test › a failed load still lets the stores go | Compose lifetime | Compose only
untested | pixiv_loads_test › a tracked list waits for the page it is fetching before it is destroyed | Compose lifetime | Compose only

## pixiv_discovery_screens_test.dart
covered | pixiv_discovery_screens_test › the chips switch between Following, Recommended, Manga and Watchlist | PixivRulesTest.theChipsFollowTheTableAndAlwaysShowOne
covered | pixiv_discovery_screens_test › Following can be narrowed to public or private follows from one button | PixivDiscoveryTest.theFollowingFeedAsksForTheChosenFollows
untested | pixiv_discovery_screens_test › See all opens the full lists of articles and creators | Compose (PixivHome) | Compose only
untested | pixiv_discovery_screens_test › sideways scrolls in the Recommended header do not page its works | Compose (PixivHome) | Compose only
untested | pixiv_discovery_screens_test › pulling Recommended down also refreshes its articles and creators | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › another account signing in empties the lists the last one loaded and reloads the shown one | PixivAccountsTest.aSwitchDropsWhatWasLoadedForTheLastAccount
untested | pixiv_discovery_screens_test › tapping the Manga or Watchlist chip again brings its list back to the top | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › turning Show R-18 off reloads the lists loaded under it | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
untested | pixiv_discovery_screens_test › the Recommended header fits a narrow phone with large text | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › View latest opens the newest work | PixivDiscoveryTest.recommendedMangaAsksForTheMangaEndpointWithRankingLabels
untested | pixiv_discovery_screens_test › View latest waits for the newest work, so a second tap opens it once | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › a row opens its series | PixivDiscoveryTest.aSeriesPageCarriesItsHeaderAndItsWorks
covered | pixiv_discovery_screens_test › a watchlist change on the series page refreshes the list | PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
untested | pixiv_discovery_screens_test › rows fit a narrow phone with large text | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › shows its header and works, and goes on and off the watchlist | PixivDiscoveryTest.aSeriesPageCarriesItsHeaderAndItsWorks; PixivWritesTest.mangaAndNovelSeriesJoinAndLeaveTheWatchlist
covered | pixiv_discovery_screens_test › keeps its header and watchlist toggle when no work can be shown | PixivDiscoveryTest.aReshapedSeriesHeaderReadsAsNoneAndMissingFieldsAsDefaults
untested | pixiv_discovery_screens_test › the header fits a narrow phone with large text | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › names the series and moves through it with previous and next | PixivDiscoveryTest.theSeriesContextGivesThePlaceAndTheNeighboursTheReaderMaySee
untested | pixiv_discovery_screens_test › a series line under a tile opens the series | Compose (PixivHome) | Compose only
untested | pixiv_discovery_screens_test › shows the artwork ID, which copies, and the size | Compose (PixivDetail) | Compose only
untested | pixiv_discovery_screens_test › previews Pixiv's walkthrough works and asks to sign in to open one | Compose (PixivHome) | Compose only
covered | pixiv_discovery_screens_test › the list opens an article | PixivisionTest.theEnglishLayoutGivesTheTitleTheDescriptionAsIntroAndTheCover
covered | pixiv_discovery_screens_test › an article shows its intro and works; a work opens, its artist too | PixivisionTest.theEnglishLayoutGivesTheTitleTheDescriptionAsIntroAndTheCover
covered | pixiv_discovery_screens_test › an article's works leave out muted creators and works, as soon as they are muted | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_discovery_screens_test › a pull keeps the article on screen, even when the page cannot be fetched | Compose (Pixivision) | Compose only
untested | pixiv_discovery_screens_test › an article that cannot be read says so and offers a retry | Compose (Pixivision) | Compose only
covered | pixiv_discovery_screens_test › the session account counts signing out and a new account as a switch, not learning the id | PixivAccountsTest.aSwitchMadeAnywhereReloadsFavoritesForTheNewAccount
covered | pixiv_discovery_screens_test › turning Show R-18 or Hide AI either way empties the session lists | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
covered | pixiv_discovery_screens_test › suggested creators leave out authors the reader mutes | PixivDiscoveryTest.hiddenR18AndAiPreviewsNeverBecomeTheSuggestionsWork; PixivMutesTest

## pixiv_follow_dialog_test.dart
covered | pixiv_follow_dialog_test › starts on Private for a creator not followed yet, and on the follow for one followed | PixivBookmarkingTest.aFollowButtonFollowsPubliclyThenUnfollows
covered | pixiv_follow_dialog_test › loads the follow detail and switches a public follow to private | PixivSocialTest.theFollowDetailSaysWhetherAndHowTheReaderFollows
covered | pixiv_follow_dialog_test › follows a new creator privately unless the switch is turned off | PixivWritesTest.aCreatorIsFollowedAndUnfollowed
covered | pixiv_follow_dialog_test › unfollows from the dialog, and Cancel changes nothing | PixivWritesTest.aCreatorIsFollowedAndUnfollowed
untested | pixiv_follow_dialog_test › a detail that will not load offers a retry and no confirm | Compose (follow dialog) | Compose only
untested | pixiv_follow_dialog_test › cancelling before the follow detail arrives is safe | Compose (follow dialog) | Compose only
covered | pixiv_follow_dialog_test › has a follow button that follows the author | PixivBookmarkingTest.aFollowButtonFollowsPubliclyThenUnfollows
covered | pixiv_follow_dialog_test › offers no follow on the reader's own work | PixivBookmarkingTest.noOneIsFollowedWhoIsFollowedAlreadyOrTheReaderThemself
untested | pixiv_follow_dialog_test › large text on a narrow phone puts the button under the name | Compose (follow dialog) | Compose only

## pixiv_grid_test.dart
untested | pixiv_grid_test › a landscape tile is only as tall as the image and caption | Compose (PixivGrid) | Compose only

## pixiv_home_chrome_test.dart
untested | pixiv_home_chrome_test › Pixiv chrome is Home / Rankings / Favorites / Search / More | Compose (PixivHome chrome) | Compose only
untested | pixiv_home_chrome_test › the mode button keeps the five icon tabs on a phone, the mark making room | Compose (PixivHome chrome) | Compose only
untested | pixiv_home_chrome_test › embedded Pixiv chrome skips a second SafeArea | Compose (PixivHome chrome) | Compose only

## pixiv_illust_pager_test.dart
untested | pixiv_illust_pager_test › a drag pushing past the last or first page is reported with its direction | Compose (pager) | Compose only
untested | pixiv_illust_pager_test › ordinary paging, flings and vertical scrolling are not edge pushes | Compose (pager) | Compose only
untested | pixiv_illust_pager_test › a push turns once it is far enough, once per drag, either way | Compose (pager) | Compose only
covered | pixiv_illust_pager_test › the next page joins at the end, then the list says it has no more | PixivPagesTest.aLaterPageIsMergedWithoutRepeats
covered | pixiv_illust_pager_test › a failed page offers retry, and the retry asks again | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
covered | pixiv_illust_pager_test › works muted after opening stay out of the later pages | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_illust_pager_test › a second next-page call waits for the one in flight instead of returning early | PixivPaging.kt (busy guard) | the guard is in the model; no separate test
covered | pixiv_illust_pager_test › a refresh forgets that the last next page failed | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
covered | pixiv_illust_pager_test › next pages that only repeat works already shown are passed over to the next new work | PixivPagesTest.aLaterPageIsMergedWithoutRepeats
covered | pixiv_illust_pager_test › a list that keeps repeating itself stops asking and offers Retry rather than a spinner | PixivPagesTest.aListThatEndsEmptyEndsWithoutAskingFurther
untested | pixiv_illust_pager_test › a list without a store pages through what it has and nothing after | Compose (pager) | Compose only
untested | pixiv_illust_pager_test › is off by default: a tile opens its work alone | Compose (pager setting) | Compose only
covered | pixiv_illust_pager_test › swipes to the next work, loads the list's next page and ends with no more | PixivPagesTest.aLaterPageIsMergedWithoutRepeats
covered | pixiv_illust_pager_test › a failed next page says so, and Retry loads it | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries
covered | pixiv_illust_pager_test › a profile shown anyway keeps its muted creator's next works in the pager | PixivMutesTest.aCreatorsOwnScreenShowsThemEvenWhenMuted
untested | pixiv_illust_pager_test › a work of many pages turns to the next work only past its last page | Compose (pager) | Compose only
untested | pixiv_illust_pager_test › a turn made while the finger stays down leaves the next work free to turn back | Compose (pager) | Compose only

## pixiv_link_wiring_test.dart
covered | pixiv_link_wiring_test › a series link opens the series screen instead of the browser | PixivRoutesTest.pixivLinksOpenTheirScreens
covered | pixiv_link_wiring_test › a pixivision link opens the article screen instead of the browser | PixivRoutesTest.pixivLinksOpenTheirScreens
untested | pixiv_link_wiring_test › a series link that cannot be read still offers its page on pixiv.net | Compose (route fallback) | Compose only
untested | pixiv_link_wiring_test › a novel series link that cannot be read still offers its page on pixiv.net | Compose (route fallback) | Compose only
covered | pixiv_link_wiring_test › a novel link opens its reader and joins the history; a novel series link opens its screen | PixivRoutesTest.pixivLinksOpenTheirScreens; PixivLinksTest.aTagsNovelsPageSearchesNovelsAndItsOtherPagesWorks
covered | pixiv_link_wiring_test › a number also offers its Pixivision article and opens it | PixivLinksTest.aBareNumberIsAWork
untested | pixiv_link_wiring_test › the shortcut tiles fit a narrow phone at large text | Compose (PixivSearch) | Compose only
untested | pixiv_link_wiring_test › Home's people icon opens the signed-in reader's following list | Compose (PixivHome) | Compose only

## pixiv_page_overview_test.dart
untested | pixiv_page_overview_test › the reader counter opens every page as a numbered thumbnail with the current one ringed | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › tapping a thumbnail jumps the horizontal reader to that page | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › tapping a thumbnail jumps the vertical reader to that page | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › the overview offers this page, all pages and the other reading direction | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › the overview fits 320dp at twice the text size | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › a landscape phone at twice the text size keeps room for pages | Compose (PixivPagesView) | Compose only
untested | pixiv_page_overview_test › the detail page counter opens the overview and a thumbnail turns the viewer | Compose (PixivDetail) | Compose only
untested | pixiv_page_overview_test › Read vertically opens the vertical reader where the viewer is | Compose (PixivDetail) | Compose only
untested | pixiv_page_overview_test › the reader slider follows the drag and turns to the page where it is let go | Compose (PixivReader) | Compose only
untested | pixiv_page_overview_test › a single-page work has no page bar and no download-all | Compose (PixivReader) | Compose only

## pixiv_page_selection_test.dart
untested | pixiv_page_selection_test › starts empty, ticks and unticks pages, and keeps reading order | Compose (PixivPagesView) | Compose only
untested | pixiv_page_selection_test › All ticks every page, and once all are ticked clears them | Compose (PixivPagesView) | Compose only
covered | pixiv_page_selection_test › picked pages, and only those, are saved in reading order | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
untested | pixiv_page_selection_test › All and None tick and clear every page, Cancel goes back to the actions | Compose (PixivPagesView) | Compose only
untested | pixiv_page_selection_test › Select pages in a page sheet opens the overview ready to pick | Compose (PixivPagesView) | Compose only
untested | pixiv_page_selection_test › while picking, a long-press still opens the page | Compose (PixivPagesView) | Compose only
covered | pixiv_page_selection_test › picked pages already saved are asked about, and can be left out | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
covered | pixiv_page_selection_test › cancelling the already-saved question saves nothing | PixivSavingTest.aResaveTakesAllTheNewOnesOrNone
untested | pixiv_page_selection_test › picking fits 320dp at twice the text size | Compose (PixivPagesView) | Compose only

## pixiv_paged_feed_test.dart
untested | pixiv_paged_feed_test › a works feed is the one paged feed: skeleton first, then the grid kept through a refresh | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › an empty works feed offers Retry, and a failed first page says why | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › its own scroll asks for the next page; a sideways strip above the works never does | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › the next page's spinner comes right after the works, above Home's navigation bar | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › columns are worked out over the whole width, outside the grid's padding | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › a fixed works grid scrolls the same way, without paging its source | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › scrolling a fixed grid builds no shown tile again | Compose (PixivGrid) | Compose only
untested | pixiv_paged_feed_test › scrolling a works feed keeps every built tile as it was | Compose (PixivGrid) | Compose only

## pixiv_paged_store_test.dart
covered | pixiv_paged_store_test › refresh loads the first page and remembers where the next one is | PixivPagingTest.pagesJoinInOrderWithoutRepeatsToTheEnd
covered | pixiv_paged_store_test › loadMore follows next_url and merges without repeats | PixivPagingTest.pagesJoinInOrderWithoutRepeatsToTheEnd
covered | pixiv_paged_store_test › useLoader clears the old list so a failure cannot show it under the new source | PixivPagingTest.whatTheDeviceKeepsIsReadAgainOnlyWhenItChanges
covered | pixiv_paged_store_test › a failed page or refresh keeps the list it had | PixivPagingTest.aFailedNextPageKeepsTheListAndRetries, aFirstPageThatFailsSaysWhyAndALaterRefreshKeepsWhatWasShown
covered | pixiv_paged_store_test › pages the filter empties are skipped until something shows | PixivPagesTest.pagesTheFiltersEmptyAreSkippedUntilSomethingShows
covered | pixiv_paged_store_test › from the old source is dropped after useLoader | PixivPagingTest.whatTheDeviceKeepsIsReadAgainOnlyWhenItChanges
covered | pixiv_paged_store_test › from before a refresh is dropped, and paging goes on from the fresh page | PixivPagingTest.aFirstPageThatFailsSaysWhyAndALaterRefreshKeepsWhatWasShown
covered | pixiv_paged_store_test › from a soft refresh of the old source never shows after useLoader | PixivPagingTest.whatTheDeviceKeepsIsReadAgainOnlyWhenItChanges
covered | pixiv_paged_store_test › the illust list store is the paged store keyed by work id | PixivPagingTest.pagesJoinInOrderWithoutRepeatsToTheEnd

## pixiv_profile_render_test.dart
untested | pixiv_profile_render_test › (render sweeps) | Compose (PixivUser) | golden/layout sweeps; Compose has no golden pipeline, the model is unit-tested

## pixiv_reader_journey_test.dart
untested | pixiv_reader_journey_test › Pixiv descriptor keeps all modes and lazy source/favorites controls reachable | Compose (reader descriptor) | Compose only

## pixiv_reader_store_test.dart
untested | pixiv_reader_store_test › a 48-page work retains its page when switching reading direction | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › page selection cannot escape the artwork bounds | PixivReader.kt; PixivPagesView.kt | no separate test
untested | pixiv_reader_store_test › continuous progress follows the page occupying most viewport space | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › late vertical visibility callbacks do not move the horizontal page | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › a jump retires the former page even when its hidden callback arrives during scrolling | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › an older scroll completion cannot replace the newest selected page | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › attaching a horizontal controller cannot publish its old page during restoration | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › disposing the reader retires pending navigation and visibility callbacks | PixivReader.kt | no separate test
untested | pixiv_reader_store_test › the slider shows the page under the thumb without moving the reader until released | PixivReader.kt | no separate test

## pixiv_search_screen_test.dart
covered | pixiv_search_screen_test › applies its choices and Remember keeps them | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_search_screen_test › offers Oldest to everyone and the audience sorts only to Premium | PixivSearchTest.onlyTheAudienceOrdersNeedPremiumForWorks
covered | pixiv_search_screen_test › a work muted elsewhere leaves the Most popular strip at once | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
covered | pixiv_search_screen_test › Popular without Premium shows the free preview as the grid, with a note | PixivSearchTest.thePopularPreviewPagesThroughItsOwnEndpoint
untested | pixiv_search_screen_test › a filter change shows the loading grid at once and leaves the creators be | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › scrolling the popular strip sideways loads no more works | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › the popularity menu narrows the sent word only | PixivSearchTest.thePopularityTagGoesOnTheSentWordAlone
covered | pixiv_search_screen_test › creators found show as preview cards with a follow button | PixivSearchTest.aCreatorSearchSendsTheWordAndReadsEachCreatorWithTheirPreviews
untested | pixiv_search_screen_test › the filter bar keeps 48dp targets and scrolls instead of overflowing with large text | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › a number handed in offers its shortcuts instead of searching it | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_search_screen_test › a number offers to open it as an artwork or a user instead of opening it | PixivLinksTest.aBareNumberIsAWork
covered | pixiv_search_screen_test › paste opens a pasted Pixiv link at once | PixivLinksTest.worksAreReadFromAddressesAndPaths
untested | pixiv_search_screen_test › paste puts plain text at the cursor | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › pasting replaces the selection, or adds to the end without one | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › a suggestion completes the last word; a long press copies it | PixivSearchMemoryTest.theWordBeingTypedIsTheOneAfterTheLastSpace
untested | pixiv_search_screen_test › Clear recent searches asks first | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › suggested creators leave out the ones the reader muted, at once | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_search_screen_test › trending tiles widen to two columns at large text | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › a recent search is one labelled node offering both its tap and its long press | Compose accessibility (PixivSearch) | Compose only
untested | pixiv_search_screen_test › more than twelve recent searches fold behind Show all | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › trending fails and retries on its own; tiles show the translation | PixivClientTest.trendingTagsAndSuggestionsCarryTheirTranslations
untested | pixiv_search_screen_test › each tag is one labelled, finger-sized target that also offers its long press | Compose accessibility (PixivSearch) | Compose only
untested | pixiv_search_screen_test › a long press offers mute, favourite and copy | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › each tag gets a search tab; edit mode removes after asking | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › a tag pinned from a work opened here gets its tab, and later edits keep it | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › a remembered Popular without Premium says its tabs show the preview | Compose (PixivSearch) | Compose only
untested | pixiv_search_screen_test › with none saved, says how to add one | Compose (PixivSearch) | Compose only
covered | pixiv_search_screen_test › searches a chosen image and lists its Pixiv matches | PixivSauceNaoTest

## pixiv_search_store_test.dart
covered | pixiv_search_store_test › a search keeps what was typed and sends the popularity tag only to Pixiv | PixivSearchTest.thePopularityTagGoesOnTheSentWordAlone
covered | pixiv_search_store_test › popular without Premium fills the grid from the free preview | PixivSearchTest.thePopularPreviewPagesThroughItsOwnEndpoint
covered | pixiv_search_store_test › the free preview still takes the popularity tag | PixivSearchTest.thePopularPreviewPagesThroughItsOwnEndpoint
untested | pixiv_search_store_test › a filter change fetches the works again, not the creators; the strip only when it changes | PixivSearchModel.kt | Compose-side model
covered | pixiv_search_store_test › popular with Premium asks the real search for popular_desc | PixivSearchTest.onlyTheAudienceOrdersNeedPremiumForWorks
covered | pixiv_search_store_test › Remember keeps the filter for the next search screen; turning it off forgets it | PixivSearchMemoryTest.aKeptFilterComesBackAsItWasWritten
covered | pixiv_search_store_test › a remembered Premium order falls back to newest after Premium ends | PixivSearchTest.anAccountWithoutPremiumDropsAPremiumOrderAndTheBookmarkBracket
covered | pixiv_search_store_test › a new search hides AI works exactly when the feeds do | PixivSearchTest.withoutAnAiChoiceTheFeedsHideAiApplies
covered | pixiv_search_store_test › the ugoira choice filters loaded works on the device | PixivSearchTest.theUgoiraChoiceKeepsAllOnlyUgoiraOrNone
covered | pixiv_search_store_test › suggestions complete the last word; a pick replaces it or searches it | PixivSearchMemoryTest.theWordBeingTypedIsTheOneAfterTheLastSpace
covered | pixiv_search_store_test › a number offers its shortcuts; a link asks for no suggestions | PixivLinksTest.aBareNumberIsAWork
untested | pixiv_search_store_test › trending and creators load apart: one failing leaves the other | PixivSearchModel.kt | Compose-side model
untested | pixiv_search_store_test › a favourite tag's tab only fetches works and keeps no history | PixivSearchModel.kt | Compose-side model
covered | pixiv_search_store_test › muted tags leave trending, and a muted picture leaves its tag bare | PixivMutesTest.aNameMatchesWhateverItsCaseAPatternTheTagsTogether
covered | pixiv_search_store_test › a tag muted by an r'pattern' leaves trending too | PixivMutesTest.aNameMatchesWhateverItsCaseAPatternTheTagsTogether

## pixiv_settings_browsing_test.dart
covered | pixiv_settings_browsing_test › reads Shown and links to pixiv.net to change it | PixivSocialTest.theReadersAiDisplaySettingIsRead
covered | pixiv_settings_browsing_test › reads Partially hidden, and says when Pixiv cannot be asked | PixivSocialTest.theReadersAiDisplaySettingIsRead
covered | pixiv_settings_browsing_test › reads the setting again for the account switched to | PixivSocialTest.theReadersAiDisplaySettingIsRead
covered | pixiv_settings_browsing_test › parses show_ai and refuses an answer without it | PixivSocialTest.theReadersAiDisplaySettingIsRead
covered | pixiv_settings_browsing_test › typed tags and patterns are muted; a broken pattern is refused | PixivMutesTest.onlyNamesAndPatternsThatCompileCanBeMuted
untested | pixiv_settings_browsing_test › the pattern help is shown whole at large text on a narrow phone | Compose (settings) | Compose only
untested | pixiv_settings_browsing_test › tapping an entry asks before unmuting; a long press copies a tag | Compose (settings) | Compose only
covered | pixiv_settings_browsing_test › maps the stored choice to a tab, Home for anything else | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
covered | pixiv_settings_browsing_test › the setting stores the picked section | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
untested | pixiv_settings_browsing_test › the Pixiv screen opens on it, and tapping the shown section scrolls it to the top | Compose (settings) | Compose only
untested | pixiv_settings_browsing_test › the browsing section offers history, its pause switch and the copy template | Compose (settings); PixivKept | Compose only

## pixiv_settings_downloads_test.dart
covered | pixiv_settings_downloads_test › the editor inserts details at the cursor and previews the name | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept
covered | pixiv_settings_downloads_test › a template without {part} cannot be saved, and Reset brings the default back | PixivSavingTest.aTemplateThatLostItsPartIsNotUsed
covered | pixiv_settings_downloads_test › saving stores the template and the setting shows it; separators cannot be typed | PixivSavingTest.aPageIsNamedByTheTemplateItsExtensionKept; PixivSettingsStoreTest
covered | pixiv_settings_downloads_test › the folder switches store their settings | PixivSavingTest.foldersArePerCreatorAndForR18AsAsked
covered | pixiv_settings_downloads_test › the saved pages count can be forgotten after confirming | PixivSavingTest.onlyTheNewestPagesAreKept
untested | pixiv_settings_downloads_test › the section reads at twice the text size on a narrow screen | Compose (settings) | Compose only

## pixiv_settings_sections_test.dart
untested | pixiv_settings_sections_test › settings are the account, content, browsing, viewing, bookmarking, downloads and mute sections in order | Compose (settings) | Compose only
covered | pixiv_settings_sections_test › muted comments and novels are listed and can be unmuted | PixivMutesTest.theMutesComeBackFromXtasPreferences
covered | pixiv_settings_sections_test › pasting or clearing a refresh token flips Sign in and Sign out once entered | PixivAuthTest.aPastedTokenIsRefreshedAndNamesItsAccount
covered | pixiv_settings_sections_test › signing out forgets the follows made in this session | PixivAccountsTest.signingOutForgetsOnlyTheActiveAccountAndHandsOverToAnother

## pixiv_settings_viewing_test.dart
covered | pixiv_settings_viewing_test › every viewing preference has a default that its reader agrees with | PixivRulesTest.aPickedColumnCountYieldsOnlyToANarrowPane; PixivSettingsStoreTest
covered | pixiv_settings_viewing_test › each choice shows its default and a pick from its dialog is kept | PixivSettingsStoreTest.aChangeShowsAtOnceAndReachesTheAppsSettings
covered | pixiv_settings_viewing_test › swiping between works starts off and the AI badge starts on | PixivSettingsStoreTest (defaults)
covered | pixiv_settings_viewing_test › picks the mirror | PixivImagesTest.aStoredHostReadsAsPixivTheMirrorOrACustomServer
covered | pixiv_settings_viewing_test › refuses a custom address with spaces and keeps a usable one | PixivImagesTest.anUnusableServerIsRefusedAndChangesNothing
covered | pixiv_settings_viewing_test › reset goes back to Pixiv's own server, and cancel changes nothing | PixivImagesTest.pixivsOwnServerOrNoChoiceLeavesTheAddressAlone
untested | pixiv_settings_viewing_test › the dialog fits a narrow phone at twice the text size | Compose (settings) | Compose only

## pixiv_tile_actions_test.dart
covered | pixiv_tile_actions_test › a group timeline drops a Pixiv work as soon as it is muted | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_tile_actions_test › a long-pressed tile lists Pixiv actions above the shared ones | Compose (PixivGrid) | Compose only
covered | pixiv_tile_actions_test › Download saves every page of the work and marks the tile | PixivSavingTest; ui/downloads/PluginBatches.kt
untested | pixiv_tile_actions_test › a single-page work offers a plain Download | Compose (PixivGrid) | Compose only
covered | pixiv_tile_actions_test › Bookmark goes through the shared bookmark store | PixivBookmarkingTest.theEditorFilesTheBookmarkWithItsVisibilityAndTags
untested | pixiv_tile_actions_test › Copy link puts the work link on the clipboard | Compose (PixivGrid) | Compose only
covered | pixiv_tile_actions_test › Mute author and Mute work ask first, then mute | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
covered | pixiv_tile_actions_test › on the work, it fills for a saved page and follows the page shown | PixivSavingTest.savedPagesAreRememberedNewestLastWithoutRepeats
covered | pixiv_tile_actions_test › in the reader, too | PixivSavingTest.savedPagesAreRememberedNewestLastWithoutRepeats
untested | pixiv_tile_actions_test › on a narrow tile the saved mark clears the R-18 and AI labels and the heart | Compose (PixivGrid) | Compose only

## pixiv_user_card_test.dart
covered | pixiv_user_card_test › shows three works, leaving out R-18, AI-hidden and muted ones | PixivModelsTest.aCreatorPreviewLeavesOutR18AndAiAsTheFeedsDo; PixivMutesTest
covered | pixiv_user_card_test › Show R-18 lets R-18 previews through | PixivModelsTest.theReadersChoicesDecideR18AndAiWorks
covered | pixiv_user_card_test › the follow button is a full touch target and follows, then unfollows | PixivBookmarkingTest.aFollowButtonFollowsPubliclyThenUnfollows
untested | pixiv_user_card_test › tapping the card opens the profile and a work opens that work | Compose (PixivUserCard) | Compose only
untested | pixiv_user_card_test › large text on a narrow phone does not overflow | Compose (PixivUserCard) | Compose only
untested | pixiv_user_card_test › survives the list rebuilding its rows with the follow it loaded | Compose (PixivUserCard) | Compose only
untested | pixiv_user_card_test › survives the row being recycled and is shared with every card of the creator | Compose (PixivUserCard) | Compose only
untested | pixiv_user_card_test › a screen reader can open each preview work on its own | Compose accessibility (PixivUserCard) | Compose only
covered | pixiv_user_card_test › the profile counts read as whole phrases, singular where the count is one | PluralsTest
untested | pixiv_user_card_test › puts the follow button under the name when beside it would squeeze the name | Compose (PixivUserCard) | Compose only
untested | pixiv_user_card_test › keeps the follow button beside the name when there is room | Compose (PixivUserCard) | Compose only

## pixiv_user_list_test.dart
covered | pixiv_user_list_test › skips a page whose creators were all muted and keeps each creator once | PixivMutesTest (filters)
covered | pixiv_user_list_test › the reader's own list asks for their id | PixivSocialTest.theReadersOwnIdComesFromTheStoredAccount
covered | pixiv_user_list_test › your own following list shows public follows and switches to the private ones | PixivSocialTest.followListsSendTheirVisibilityAndReadPreviewCards
covered | pixiv_user_list_test › someone else's following list has no public / private switch | PixivSocialTest.followListsSendTheirVisibilityAndReadPreviewCards
covered | pixiv_user_list_test › the followers list shows who follows them | PixivSocialTest.followListsSendTheirVisibilityAndReadPreviewCards
covered | pixiv_user_list_test › each card unfollows in place and offers Add to group | PixivWritesTest.aCreatorIsFollowedAndUnfollowed | Add to group is Compose-only
covered | pixiv_user_list_test › a creator muted while the list is open disappears from it | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
untested | pixiv_user_list_test › an empty list says so | Compose (PixivUsers) | Compose only
covered | pixiv_user_list_test › scrolling to the end brings the next pages | PixivSocialTest.aListWithoutPreviewsIsAnEmptyLastPage
untested | pixiv_user_list_test › leaving before the first page arrives is safe | Compose (PixivUsers) | Compose only
untested | pixiv_user_list_test › leaving while the next page loads is safe | Compose (PixivUsers) | Compose only
untested | pixiv_user_list_test › large text on a narrow phone does not overflow | Compose (PixivUsers) | Compose only

## pixiv_user_profile_test.dart
covered | pixiv_user_profile_test › reads the user, the counts, the links and the details | PixivSocialTest.aProfileReadsTheUserTheCountsTheLinksAndTheDetails
covered | pixiv_user_profile_test › profile_publicity hides what the creator keeps private | PixivSocialTest.whatTheCreatorKeepsPrivateIsLeftEmpty
covered | pixiv_user_profile_test › a missing profile does not throw and reads as empty | PixivSocialTest.aMissingProfileReadsAsEmpty
covered | pixiv_user_profile_test › a reshaped profile keeps what still parses and drops what is not a link | PixivSocialTest.aReshapedProfileKeepsWhatStillParsesAndDropsWhatIsNoLink
covered | pixiv_user_profile_test › the birthday shows only the parts that are public | PixivSocialTest.aProfileReadsTheUserTheCountsTheLinksAndTheDetails
untested | pixiv_user_profile_test › copies the ID, opens the lists and the links, and leaves out empty fields | Compose (PixivUser) | Compose only
covered | pixiv_user_profile_test › a bare profile still has its name, ID and counts | PixivSocialTest.aProfileReadsTheUserTheCountsTheLinksAndTheDetails

## pixiv_user_screen_test.dart
untested | pixiv_user_screen_test › the header shows the bio and counts, and the tabs are Works, Bookmarks, Following and Info | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › Works starts on manga when there is more of it and switches to illustrations | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › a creator with illustrations only gets no switch | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › Bookmarks lists the public bookmarks and Following lists whom they follow | PixivSocialTest.aCreatorsBookmarksSendTheUserTheVisibilityAndTheTag, followListsSendTheirVisibilityAndReadPreviewCards
untested | pixiv_user_screen_test › Info copies the user ID and opens the followers list | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › a website on Pixiv opens in XTA, on the profile tab its link names | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › opens on the tab it was asked for, and the works count goes back to Works | PixivLinksTest.aCreatorsPageOpensTheirProfileOnTheTabItNames
untested | pixiv_user_screen_test › the following count opens the following list | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › a muted creator shows a placeholder that can show anyway | PixivMutesTest.aCreatorsOwnScreenShowsThemEvenWhenMuted
covered | pixiv_user_screen_test › Show anyway shows the creator's works, and their muted tags stay hidden | PixivMutesTest.aCreatorsOwnScreenShowsThemEvenWhenMuted
covered | pixiv_user_screen_test › Unmute on the placeholder brings the profile back for good | PixivMutesTest.theMutesComeBackFromXtasPreferences
covered | pixiv_user_screen_test › Mute author in the menu asks first, then shows the placeholder | PixivMutesTest.aMutedWorkCreatorOrTagHidesTheWorkAndSaysWhy
covered | pixiv_user_screen_test › Copy info puts the name, account and link on the clipboard | PixivDetailTest.copyInfoFillsEveryPlaceholder
covered | pixiv_user_screen_test › Follow privately in the menu follows with restrict=private | PixivWritesTest.aCreatorIsFollowedAndUnfollowed
covered | pixiv_user_screen_test › your own profile offers no follow button and no private follow | PixivBookmarkingTest.noOneIsFollowedWhoIsFollowedAlreadyOrTheReaderThemself
untested | pixiv_user_screen_test › the header image and avatar say how to save them | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › a long press on the header image and a tap on the avatar both go to the download path | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › leaving a profile before it arrives is safe | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › a profile lifts only the author mute on its own creator | PixivMutesTest.aCreatorsOwnScreenShowsThemEvenWhenMuted
untested | pixiv_user_screen_test › large text on a narrow phone does not overflow | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › Follow fills the row with an edge and Add to group sits beside it with its name | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › once followed, Follow is outlined and says Unfollow | PixivBookmarkingTest.aFollowButtonFollowsPubliclyThenUnfollows
untested | pixiv_user_screen_test › Add to group keeps its icon and tooltip at 320 dp with text x2 | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › your own profile shows Add to group alone | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › Premium is an outlined badge | Compose (PixivUser) | Compose only
untested | pixiv_user_screen_test › a count leads with its number in bold, and a hairline closes the header | Compose (PixivUser) | Compose only
covered | pixiv_user_screen_test › German writes the following count out in full | PluralsTest, PluralTextsTest
covered | pixiv_user_screen_test › a count finds its number wherever the language puts it | PluralsTest

## pixiv_viewer_controls_test.dart
untested | pixiv_viewer_controls_test › HD flips for the visit and decodes whole, as does a zoom | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › turning a page in the horizontal reader lets go of the zoom; the vertical one keeps it | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › a jump to another page in the horizontal reader lets go of the zoom | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › a loaded page is remembered once, and nothing changes after closing | Compose (PixivReader) | Compose only
covered | pixiv_viewer_controls_test › a whole-page decode goes three screens wide and never past its cap | PixivPixelsTest
untested | pixiv_viewer_controls_test › share waits for the page and HD shows whether it is on | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › a loaded page can be shared | Compose (PixivReader) | Compose only
covered | pixiv_viewer_controls_test › starts on the originals when full-screen pages are set to original, and HD switches back | PixivImagesTest; PixivRulesTest
untested | pixiv_viewer_controls_test › the vertical reader zooms on double-tap and then decodes pages whole | Compose (PixivReader) | Compose only
covered | pixiv_viewer_controls_test › goes out under Pixiv's name with the type of the file shown | PixivDownloadsTest.aSharedPictureIsNamedAsPixivNamesItsPagesWithTheTypeItsFileIs
untested | pixiv_viewer_controls_test › downloads the page when the cache has no copy and hands the file to the share sheet | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › says so when the page cannot be had | Compose (PixivReader) | Compose only
untested | pixiv_viewer_controls_test › holding stops playback and resuming plays again; a reader's own pause stays | Compose (ugoira player) | Compose only
untested | pixiv_viewer_controls_test › covered while the frames download, it waits paused until shown again | Compose (ugoira player) | Compose only
untested | pixiv_viewer_controls_test › the view pauses while its ticker is off and plays on when it comes back | Compose (ugoira player) | Compose only

## pixivision_parser_test.dart
covered | pixivision_parser_test › reads the title, the description as the intro and the cover | PixivisionTest.theEnglishLayoutGivesTheTitleTheDescriptionAsIntroAndTheCover
covered | pixivision_parser_test › lists each featured work once, with its artist, and nothing outside the article | PixivisionTest.eachFeaturedWorkComesOnceWithItsArtistAndNothingOutsideTheArticle
covered | pixivision_parser_test › takes the opening paragraphs as the intro when there is no description | PixivisionTest.theChineseLayoutTakesTheOpeningParagraphsAsIntro
covered | pixivision_parser_test › reads lazy images, tracking queries and the older member links | PixivisionTest.lazyPicturesTrackingQueriesAndOldMemberLinksAreRead
covered | pixivision_parser_test › a page with no article reads as empty rather than throwing | PixivisionTest.aPageWithNoArticleReadsAsEmptyRatherThanThrowing
covered | pixivision_parser_test › a work without an artist link is left out instead of borrowing a neighbour | PixivisionTest.aWorkWithoutAnArtistLinkIsLeftOutInsteadOfBorrowingANeighbours
covered | pixivision_parser_test › a block missing its title and pictures still yields the ids | PixivisionTest.aBlockMissingItsTitleAndPicturesStillGivesTheIds
covered | pixivision_parser_test › the page language follows the XTA locale | PixivisionTest.thePageLanguageFollowsTheLocale
