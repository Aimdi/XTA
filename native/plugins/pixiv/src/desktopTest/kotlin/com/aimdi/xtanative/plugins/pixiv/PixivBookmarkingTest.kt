package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.Asked
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkApi
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkDetail
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkTagChoice
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivNovelApi
import com.aimdi.xtanative.networks.pixiv.PixivSocialApi
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.pixiv
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class PixivBookmarkingTest {
    private val work = PixivIllust(
        id = 5, title = "Work", caption = "", type = "illust", thumbnailUrl = "", pageCount = 1, userId = 2, userName = "A", userAccount = "a",
        tags = listOf(PixivTag("猫"), PixivTag("1000users入り")), totalBookmarks = 10,
    )
    private val novel = PixivNovel(id = 8, title = "Novel", user = PixivUser(2, "A", "a"), totalBookmarks = 3)

    private class Pixiv(var settings: Settings = Settings(), detail: String = "{}") {
        val http = FakePixivHttp { request -> answer(if (request.method == "GET") detail else "{}") }
        private val client = pixiv(http)
        private val social = PixivSocialApi(client)
        val following = PixivFollowing({ id, restrict -> social.follow(id, restrict) }, { id -> social.unfollow(id) })
        val bookmarking = PixivBookmarking(PixivBookmarkApi(client), PixivNovelApi(client), following, { settings }, { 77 })
        val writes: List<Asked> get() = http.asked.filter { it.method == "POST" }
        val paths: List<String> get() = writes.map { it.url.encodedPath }
    }

    @Test
    fun theHeartBookmarksPubliclyAndTakesTheBookmarkAwayOnPixiv() = runBlocking {
        val pixiv = Pixiv()
        assertEquals(PixivBookmarkOutcome(bookmarked = true), pixiv.bookmarking.toggle(work))
        assertTrue(pixiv.bookmarking.isBookmarked(work))
        assertEquals(PixivBookmarkOutcome(bookmarked = false), pixiv.bookmarking.toggle(work))
        assertFalse(pixiv.bookmarking.isBookmarked(work))
        assertEquals(listOf("/v2/illust/bookmark/add", "/v1/illust/bookmark/delete"), pixiv.paths)
        assertEquals(mapOf("illust_id" to "5", "restrict" to "public"), pixiv.writes.first().form)
    }

    @Test
    fun theDefaultVisibilityAndAutoTagsComeFromTheSettings() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.defaultPrivateBookmark, true).with(PixivPrefs.autoTagBookmarks, true))
        pixiv.bookmarking.toggle(work)
        assertEquals(mapOf("illust_id" to "5", "restrict" to "private", "tags[]" to "猫"), pixiv.writes.single().form)
    }

    @Test
    fun aWorkPixivSaysIsBookmarkedIsRemovedByTheHeart() = runBlocking {
        val pixiv = Pixiv()
        pixiv.bookmarking.toggle(work.copy(isBookmarked = true))
        assertEquals(listOf("/v1/illust/bookmark/delete"), pixiv.paths)
    }

    @Test
    fun aNewBookmarkFollowsTheCreatorOnPixivAndAsksToSaveWhenTheReaderWantsThat() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.followAfterBookmark, true).with(PixivPrefs.downloadAfterBookmark, true))
        val outcome = pixiv.bookmarking.toggle(work)
        assertEquals(PixivBookmarkOutcome(bookmarked = true, followedAuthor = true, saveAll = true), outcome)
        assertEquals(listOf("/v2/illust/bookmark/add", "/v1/user/follow/add"), pixiv.paths)
        assertEquals(mapOf("user_id" to "2", "restrict" to "public"), pixiv.writes.last().form)
        assertTrue(pixiv.following.isFollowed(PixivUser(2, "A", "a")))
    }

    @Test
    fun noOneIsFollowedWhoIsFollowedAlreadyOrTheReaderThemself() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.followAfterBookmark, true))
        pixiv.bookmarking.toggle(work.copy(userIsFollowed = true))
        pixiv.bookmarking.toggle(work.copy(id = 6, userId = 77))
        assertEquals(listOf("/v2/illust/bookmark/add", "/v2/illust/bookmark/add"), pixiv.paths)
    }

    @Test
    fun theEditorFilesTheBookmarkWithItsVisibilityAndTags() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.autoTagBookmarks, true))
        pixiv.bookmarking.save(work, "private", listOf("mine"))
        assertEquals(mapOf("illust_id" to "5", "restrict" to "private", "tags[]" to "mine"), pixiv.writes.single().form)
    }

    @Test
    fun bookmarkAfterSaveOnlyWhenAskedAndNotYetBookmarked() = runBlocking {
        assertNull(Pixiv().bookmarking.afterSave(work))
        val pixiv = Pixiv(Settings().with(PixivPrefs.bookmarkAfterDownload, true).with(PixivPrefs.downloadAfterBookmark, true))
        assertNull(pixiv.bookmarking.afterSave(work.copy(isBookmarked = true)))
        assertEquals(PixivBookmarkOutcome(bookmarked = true), pixiv.bookmarking.afterSave(work))
        assertEquals(1, pixiv.writes.size)
    }

    @Test
    fun theDetailBringsAStaleCardInLine() = runBlocking {
        val pixiv = Pixiv(detail = """{"bookmark_detail": {"is_bookmarked": true, "restrict": "private", "tags": []}}""")
        val detail = pixiv.bookmarking.detail(work)
        assertTrue(detail.isBookmarked)
        assertTrue(pixiv.bookmarking.isBookmarked(work))
        assertEquals("/v2/illust/bookmark/detail", pixiv.http.asked.single().url.encodedPath)
    }

    @Test
    fun aSecondTapWaitsForTheFirstWrite() = runBlocking {
        val held = CompletableDeferred<Unit>()
        val store = PixivMarkStore()
        val first = async { store.exclusive(5) { held.await(); "first" } }
        while (!store.now.isBusy(5)) kotlinx.coroutines.yield()
        assertNull(store.exclusive(5) { "second" })
        held.complete(Unit)
        assertEquals("first", first.await())
        assertFalse(store.now.isBusy(5))
    }

    @Test
    fun theCountMovesWithWhatThisSessionChanged() {
        val marks = PixivMarks(mapOf(1L to true, 2L to false))
        assertEquals(11, marks.count(1, sent = false, total = 10))
        assertEquals(10, marks.count(1, sent = true, total = 10))
        assertEquals(9, marks.count(2, sent = true, total = 10))
        assertEquals(0, marks.count(2, sent = true, total = 0))
        assertEquals(10, marks.count(3, sent = true, total = 10))
    }

    @Test
    fun aNovelsHeartUsesTheDefaultVisibilityAndItsLongPressBookmarksPrivately() = runBlocking {
        val pixiv = Pixiv(Settings().with(PixivPrefs.followAfterBookmark, true))
        assertEquals(true, pixiv.bookmarking.toggle(novel))
        assertEquals(false, pixiv.bookmarking.toggle(novel))
        assertEquals(true, pixiv.bookmarking.bookmarkPrivately(novel))
        assertEquals(listOf("/v2/novel/bookmark/add", "/v1/novel/bookmark/delete", "/v2/novel/bookmark/add"), pixiv.paths)
        assertEquals(listOf("public", null, "private"), pixiv.writes.map { it.form["restrict"] })
    }

    @Test
    fun aFollowButtonFollowsPubliclyThenUnfollows() = runBlocking {
        val pixiv = Pixiv()
        val user = PixivUser(9, "B", "b")
        assertEquals(true, pixiv.following.toggle(user))
        assertEquals(false, pixiv.following.toggle(user))
        assertEquals(true, pixiv.following.follow(user, "private"))
        assertEquals(listOf("/v1/user/follow/add", "/v1/user/follow/delete", "/v1/user/follow/add"), pixiv.paths)
        assertEquals("private", pixiv.writes.last().form["restrict"])
    }

    @Test
    fun theEditorStartsFromPixivsDetail() {
        val tags = listOf(PixivBookmarkTagChoice("猫", false), PixivBookmarkTagChoice("mine", true))
        val filed = PixivBookmarkDraft.fromDetail(PixivBookmarkDetail(true, "private", tags), "public", listOf("猫"))
        assertEquals(listOf("猫", "mine"), filed.tags)
        assertEquals(setOf("mine"), filed.checked)
        assertEquals("private", filed.restrict)
        val fresh = PixivBookmarkDraft.fromDetail(PixivBookmarkDetail(false, "public", tags.map { it.copy(checked = false) }), "private", listOf("猫"))
        assertEquals(setOf("猫"), fresh.checked)
        assertTrue(fresh.private)
    }

    @Test
    fun aNovelsPlaceIsKeptInXtasJournal() {
        val point = ArticleReadPoint(fraction = 0.5, paragraph = 4, updatedAt = 1)
        val kept = PixivNovelPlaces.kept(Settings(), 8, point)
        assertTrue(kept[PixivPrefs.novelReading].contains("\"pixiv-novel:8\""))
        assertEquals(point, PixivNovelPlaces.placesIn(kept)["pixiv-novel:8"])
    }
}
