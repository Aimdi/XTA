package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.Asked
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkApi
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovelApi
import com.aimdi.xtanative.networks.pixiv.PixivSocialApi
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.pixiv
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** XTA's pixiv_bookmark_actions_test and pixiv_bookmark_after_save_test, where native's tests did not check them yet. */
class PixivBookmarkActionsTest {
    private val tags = listOf(PixivTag("オリジナル"), PixivTag("1000users入り"), PixivTag("東方100users入り"), PixivTag("風景"))
    private val work = PixivIllust(
        id = 7, title = "Work", caption = "", type = "illust", thumbnailUrl = "https://i.pximg.net/7.jpg", pageCount = 1,
        userId = 42, userName = "Mika", userAccount = "mika", tags = tags,
    )
    private val detailBookmarked = """{"bookmark_detail": {"is_bookmarked": true, "restrict": "private", "tags": []}}"""

    private class Pixiv(val settings: Settings = Settings(), detail: String = "{}", failWrites: Boolean = false, held: CompletableDeferred<Unit>? = null) {
        val http = FakePixivHttp { request ->
            when {
                request.method == "GET" -> answer(detail)
                failWrites -> answer("", 503)
                else -> {
                    held?.await()
                    answer("{}")
                }
            }
        }
        private val client = pixiv(http)
        private val social = PixivSocialApi(client)
        val following = PixivFollowing({ id, restrict -> social.follow(id, restrict) }, { id -> social.unfollow(id) })
        val bookmarking = PixivBookmarking(PixivBookmarkApi(client), PixivNovelApi(client), following, { settings }, { settings[PixivPrefs.userId].takeIf { it > 0 } ?: 77 })
        val writes: List<Asked> get() = http.asked.filter { it.method == "POST" }
        val bookmarkWrites: List<String> get() = writes.filter { "bookmark" in it.url.encodedPath }.map { "${it.url.encodedPath}:${it.form["restrict"]}:${it.form["tags[]"].orEmpty()}" }
        val follows: List<Asked> get() = writes.filter { "follow" in it.url.encodedPath }
    }

    @Test
    fun autoTagsAreTheWorksOwnTagsWithoutAnyPopularityTag() {
        val pixiv = Pixiv(Settings().with(PixivPrefs.autoTagBookmarks, true))
        assertEquals(listOf("オリジナル", "風景"), pixiv.bookmarking.autoTags(tags.map { it.name }))
    }

    @Test
    fun theEditorsVisibilityWinsOverTheDefault() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.defaultPrivateBookmark, true))
        pixiv.bookmarking.save(work, "public", emptyList())
        assertEquals(listOf("/v2/illust/bookmark/add:public:"), pixiv.bookmarkWrites)
    }

    @Test
    fun explicitTagsFromTheEditorAreSentAsTheyAreEvenNone() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.autoTagBookmarks, true))
        pixiv.bookmarking.save(work, "public", emptyList())
        pixiv.bookmarking.save(work, "public", listOf("mine"))
        assertEquals(listOf("/v2/illust/bookmark/add:public:", "/v2/illust/bookmark/add:public:mine"), pixiv.bookmarkWrites)
    }

    @Test
    fun aRefiledBookmarkFollowsNoOne() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.followAfterBookmark, true))
        val outcome = pixiv.bookmarking.save(work.copy(isBookmarked = true), "public", listOf("x"))
        assertEquals(PixivBookmarkOutcome(bookmarked = true), outcome)
        assertTrue(pixiv.follows.isEmpty())
    }

    @Test
    fun removingRefilingOrBookmarkingAfterASaveDoesNotSaveAgain() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.downloadAfterBookmark, true).with(PixivPrefs.bookmarkAfterDownload, true))
        assertFalse(pixiv.bookmarking.save(work.copy(isBookmarked = true), "public", emptyList())!!.saveAll)
        assertFalse(pixiv.bookmarking.toggle(work.copy(id = 8, isBookmarked = true))!!.saveAll)
        assertFalse(pixiv.bookmarking.afterSave(work.copy(id = 9))!!.saveAll)
    }

    @Test
    fun aSavedWorkIsBookmarkedWithTheDefaults() = runBlocking {
        val pixiv = Pixiv(
            Settings().with(PixivPrefs.bookmarkAfterDownload, true).with(PixivPrefs.defaultPrivateBookmark, true)
                .with(PixivPrefs.autoTagBookmarks, true).with(PixivPrefs.followAfterBookmark, true),
        )
        assertEquals(PixivBookmarkOutcome(bookmarked = true, followedAuthor = true), pixiv.bookmarking.afterSave(work))
        assertEquals("private", pixiv.writes.first().form["restrict"])
        assertTrue(pixiv.writes.first().body.orEmpty().let { "%E3%82%AA" in it && "%E9%A2%A8" in it }, "both own tags, popularity tags left out")
        assertFalse(pixiv.writes.first().body.orEmpty().contains("users"))
    }

    @Test
    fun theDetailLeavesAWriteStillOnItsWayToSetTheHeart(): Unit = runBlocking {
        val pixiv = Pixiv(detail = detailBookmarked)
        val held = CompletableDeferred<Unit>()
        val write = async { pixiv.bookmarking.works.exclusive(7) { held.await() } }
        while (!pixiv.bookmarking.works.now.isBusy(7)) yield()
        pixiv.bookmarking.detail(work)
        assertNull(pixiv.bookmarking.works.now.marked[7L])
        held.complete(Unit)
        write.await()
    }

    @Test
    fun soSavingThatBookmarkInTheEditorIsARefileNoFollowNoSave() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.followAfterBookmark, true).with(PixivPrefs.downloadAfterBookmark, true), detail = detailBookmarked)
        val detail = pixiv.bookmarking.detail(work)
        val outcome = pixiv.bookmarking.save(work, detail.restrict, emptyList())
        assertEquals(PixivBookmarkOutcome(bookmarked = true, followedAuthor = false, saveAll = false), outcome)
        assertEquals(listOf("/v2/illust/bookmark/add:private:"), pixiv.bookmarkWrites)
        assertTrue(pixiv.follows.isEmpty())
    }

    @Test
    fun aFailedWriteLeavesTheBookmarkAsItWasAndFreesTheWork() = runBlocking {
        val pixiv = Pixiv(failWrites = true)
        assertFailsWith<Exception> { pixiv.bookmarking.toggle(work) }
        assertFalse(pixiv.bookmarking.isBookmarked(work))
        assertFalse(pixiv.bookmarking.works.now.isBusy(7))
    }

    // pixiv_bookmark_after_save_test: the saves of a work's screen and of a tile's sheet go through PixivSheets.

    private fun saving(bookmarkAfterSave: Boolean = true, block: suspend (PixivTestHost, PixivRuntime, PixivSheets) -> Unit) {
        val http = FakePixivHttp { answer("{}") }
        val host = PixivTestHost(Settings().with(PixivPrefs.refreshToken, "r").with(PixivPrefs.accessToken, "a").with(PixivPrefs.accessExpiresAt, "2999-01-01T00:00:00Z").with(PixivPrefs.userId, 77L).with(PixivPrefs.bookmarkAfterDownload, bookmarkAfterSave), http)
        withRuntime(host) { runtime -> block(host, runtime, PixivSheets(runtime) { "en" }) }
    }

    private fun PixivTestHost.bookmarkAdds(): List<String> =
        (http as FakePixivHttp).asked.filter { it.url.encodedPath.endsWith("bookmark/add") }.map { "${it.form["illust_id"]}:${it.form["restrict"]}" }

    private val manyPages = work.copy(id = 120, pageCount = 8, pageUrls = (0 until 8).map { "https://i.pximg.net/img-master/120_p$it.jpg" })

    @Test
    fun savingAPageFromTheWorkBookmarksItWithTheDefaults() = saving { host, _, sheets ->
        sheets.savePages(manyPages, listOf(0))
        eventually { host.bookmarkAdds().isNotEmpty() }
        assertEquals(1, host.batches.single().size)
        assertEquals(listOf("120:public"), host.bookmarkAdds())
    }

    @Test
    fun savingEveryPageBookmarksOnce() = saving { host, _, sheets ->
        sheets.saveAll(manyPages)
        eventually { host.bookmarkAdds().isNotEmpty() }
        assertEquals(8, host.batches.single().size)
        assertEquals(listOf("120:public"), host.bookmarkAdds())
    }

    @Test
    fun aSaveThatNeverHappenedOrTheSettingOffBookmarksNothing() {
        saving { host, runtime, sheets ->
            host.saves = { files -> files.map { false } }
            sheets.saveAll(manyPages)
            eventually { host.batches.isNotEmpty() }
            kotlinx.coroutines.delay(200)
            assertTrue(host.bookmarkAdds().isEmpty())
            assertTrue(PixivSavedPages.of(runtime.settings.now).isEmpty())
        }
        saving(bookmarkAfterSave = false) { host, runtime, sheets ->
            sheets.savePages(manyPages, listOf(0))
            eventually { PixivSavedPages.of(runtime.settings.now).isNotEmpty() }
            kotlinx.coroutines.delay(100)
            assertTrue(host.bookmarkAdds().isEmpty())
            assertEquals(setOf("120_p0"), PixivSavedPages.of(runtime.settings.now), "the page is remembered as saved all the same")
        }
    }

    @Test
    fun anAlreadyBookmarkedWorkIsLeftAsItIs() = saving { host, runtime, sheets ->
        sheets.savePages(manyPages.copy(isBookmarked = true), listOf(0))
        eventually { PixivSavedPages.of(runtime.settings.now).isNotEmpty() }
        kotlinx.coroutines.delay(100)
        assertTrue(host.bookmarkAdds().isEmpty())
    }

    @Test
    fun theBookmarkingSettingsSwitchTheirPreferences() {
        assertEquals(
            listOf(PixivPrefs.defaultPrivateBookmark, PixivPrefs.autoTagBookmarks, PixivPrefs.followAfterBookmark, PixivPrefs.downloadAfterBookmark, PixivPrefs.bookmarkAfterDownload, PixivPrefs.haptics),
            bookmarkSwitches.map { it.first },
        )
        assertEquals("Bookmarking", Strings.bookmark_settings.get("en"))
    }
}
