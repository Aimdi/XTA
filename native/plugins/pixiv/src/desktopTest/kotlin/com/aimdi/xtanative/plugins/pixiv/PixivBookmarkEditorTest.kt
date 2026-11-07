package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkDetail
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkTagChoice
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred

/** XTA's pixiv_bookmark_editor_test and the tag matching of pixiv_bookmark_api_test: the editor's draft, and its writes through PixivSheets. */
class PixivBookmarkEditorTest {
    private val draft = PixivBookmarkDraft(tags = listOf("a", "b"), checked = setOf("b"))
    private fun detail(bookmarked: Boolean = false, restrict: String = "public", tags: List<PixivBookmarkTagChoice> = listOf(PixivBookmarkTagChoice("オリジナル", false), PixivBookmarkTagChoice("1000users入り", false))) =
        PixivBookmarkDetail(bookmarked, restrict, tags)

    @Test
    fun aWorkNotBookmarkedYetTakesTheDefaultVisibilityAndPrechecksAutoTags() {
        val opened = PixivBookmarkDraft.fromDetail(detail(), "private", listOf("オリジナル"))
        assertTrue(opened.private)
        assertEquals(setOf("オリジナル"), opened.checked)
    }

    @Test
    fun anExistingBookmarkKeepsItsOwnVisibilityAndTags() {
        val opened = PixivBookmarkDraft.fromDetail(detail(true, "private", listOf(PixivBookmarkTagChoice("x", true), PixivBookmarkTagChoice("y", false))), "public", listOf("y"))
        assertEquals("private", opened.restrict)
        assertEquals(setOf("x"), opened.checked)
    }

    @Test
    fun typedTagsGoOnTopCheckedAndListedOnesAreCheckedWhereTheyAre() {
        val added = draft.copy(typed = " new  a new2 ").withTyped()
        assertEquals(listOf("new", "new2", "a", "b"), added.tags)
        assertEquals(setOf("new", "new2", "a", "b"), added.checked)
        assertEquals("", added.typed)
    }

    @Test
    fun selectAllAndClearAll() {
        assertTrue(draft.allToggled().allChecked)
        assertTrue(draft.allToggled().allToggled().checked.isEmpty())
        assertEquals(listOf("a", "b"), draft.toggled("a").saved())
    }

    @Test
    fun suggestionsComeFromTheReadersOwnTags() {
        assertEquals(listOf("Cat"), PixivBookmarkDraft.matches(listOf("Cat", "dog"), "CA"))
    }

    @Test
    fun saveTakesATagStillInTheField() {
        assertEquals(listOf("typed"), PixivBookmarkDraft(emptyList(), emptySet(), typed = "typed").saved())
        assertEquals(listOf("mine", "オリジナル"), PixivBookmarkDraft(listOf("オリジナル"), setOf("オリジナル"), typed = "mine").saved())
    }

    // pixiv_bookmark_api_test › tag matching

    private val known = (0 until 12).map { if (it % 2 == 0) "Cat$it" else "dog$it" }

    @Test
    fun atMostEightMatchesAreSuggestedWhateverTheCase() {
        assertEquals(8, PixivBookmarkDraft.matches((0 until 20).map { "CAT$it" }, "cat").size)
        assertEquals(listOf("Cat0", "Cat2", "Cat4", "Cat6", "Cat8", "Cat10"), PixivBookmarkDraft.matches(known, "cAt"))
        assertEquals(listOf("dog1", "dog11"), PixivBookmarkDraft.matches(known, "G1"))
    }

    @Test
    fun anEmptyQuerySuggestsNothing() {
        assertTrue(PixivBookmarkDraft.matches(known, "   ").isEmpty())
    }

    @Test
    fun typedTextBecomesTagsSplitOnWhitespace() {
        assertEquals(listOf("blue", "sky", "sea"), PixivTyping.words("  blue   sky\tsea "))
        assertTrue(PixivTyping.words("   ").isEmpty())
    }

    // The editor's and the heart's writes, as PixivSheets runs them.

    private val work = PixivIllust(
        id = 120, title = "Work", caption = "", type = "illust", thumbnailUrl = "https://i.pximg.net/120.jpg", pageCount = 1,
        userId = 42, userName = "Mika", userAccount = "mika", tags = listOf(PixivTag("オリジナル")),
    )

    private class Played {
        val kinds = mutableListOf<HapticFeedbackType>()
        val haptics = PixivHaptics({ kinds += it }, { true })
    }

    private fun sheets(settings: Settings = signedInSettings(), gate: CompletableDeferred<Unit>? = null, failWrites: Boolean = false, block: suspend (PixivRuntime, PixivSheets, FakePixivHttp) -> Unit) {
        val http = FakePixivHttp { request ->
            if (request.method == "POST") gate?.await()
            if (request.method == "POST" && failWrites) throw java.io.IOException("offline") else answer("{}")
        }
        withRuntime(PixivTestHost(settings, http)) { runtime -> block(runtime, PixivSheets(runtime) { "en" }, http) }
    }

    @Test
    fun aFailedSaveKeepsTheEditorOpenWithTheReason() = sheets(failWrites = true) { runtime, sheets, _ ->
        val sheet = PixivSheet.EditWork(work)
        sheets.show(sheet)
        val failed = CompletableDeferred<Throwable?>()
        sheets.editBookmark(sheet, quietHaptics(), { it.save(work, "public", emptyList()) }) { failed.complete(it) }
        val error = failed.await()
        assertNotNull(error)
        assertEquals("Could not reach Pixiv", failureText(com.aimdi.xtanative.networks.pixiv.PixivFailure.of(error)).get("en"))
        assertEquals(sheet, sheets.open)
        assertNull(runtime.bookmarking.works.now.marked[120L])
    }

    @Test
    fun aFailureAfterTheEditorWasDraggedAwayIsStillTold() {
        val gate = CompletableDeferred<Unit>()
        sheets(gate = gate, failWrites = true) { runtime, sheets, _ ->
            val heard = runtime.heard()
            val sheet = PixivSheet.EditWork(work)
            sheets.show(sheet)
            var done = false
            sheets.editBookmark(sheet, quietHaptics(), { it.save(work, "public", emptyList()) }) { done = true }
            sheets.close()
            gate.complete(Unit)
            eventually { heard.isNotEmpty() }
            assertEquals(listOf("Could not reach Pixiv"), heard)
            assertFalse(done, "the editor is gone, so it hears nothing")
            assertNull(runtime.bookmarking.works.now.marked[120L])
        }
    }

    @Test
    fun aSaveThatLandsAfterADragAwayStillBuzzesAndNamesTheAuthorFollowed() {
        val gate = CompletableDeferred<Unit>()
        sheets(signedInSettings().with(PixivPrefs.followAfterBookmark, true), gate = gate) { runtime, sheets, http ->
            val heard = runtime.heard()
            val played = Played()
            val sheet = PixivSheet.EditWork(work)
            sheets.show(sheet)
            sheets.editBookmark(sheet, played.haptics, { it.save(work, "public", emptyList()) }) {}
            sheets.close()
            gate.complete(Unit)
            eventually { heard.isNotEmpty() }
            assertEquals(listOf("Followed Mika"), heard)
            assertEquals(listOf(HapticFeedbackType.TextHandleMove), played.kinds)
            assertEquals(listOf("/v2/illust/bookmark/add", "/v1/user/follow/add"), http.asked.map { it.url.encodedPath })
        }
    }

    @Test
    fun aTapBookmarksWithTheDefaultsAndBuzzesLightly() = sheets(signedInSettings().with(PixivPrefs.defaultPrivateBookmark, true)) { runtime, sheets, http ->
        val played = Played()
        sheets.toggleBookmark(work, played.haptics)
        eventually { played.kinds.isNotEmpty() }
        assertEquals("private", http.asked.single().form["restrict"])
        assertTrue(runtime.bookmarking.isBookmarked(work))
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played.kinds)
    }

    @Test
    fun followingTheAuthorAfterABookmarkSaysSo() = sheets(signedInSettings().with(PixivPrefs.followAfterBookmark, true)) { runtime, sheets, http ->
        val heard = runtime.heard()
        sheets.toggleBookmark(work, quietHaptics())
        eventually { heard.isNotEmpty() }
        assertEquals(listOf("Followed Mika"), heard)
        assertEquals(mapOf("user_id" to "42", "restrict" to "public"), http.asked.last().form)
    }
}
