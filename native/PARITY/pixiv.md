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
