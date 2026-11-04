package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivKeptTest {
    private fun dir() = StoreDir(Files.createTempDirectory("pixiv-kept").toFile())

    private fun mark(kind: PixivKind, id: Long, vararg tags: String) =
        PixivDeviceBookmark(kind, PixivKept.of(work(id)), tags.toList(), Instant.ofEpochSecond(id))

    @Test
    fun aKeptWorkShowsAsTheWorkItWas() {
        val illust = work(3, userId = 8, tags = listOf("cat"), pages = 4)
        val shown = PixivKept.of(illust).toIllust()
        assertEquals(illust.id, shown.id)
        assertEquals(illust.userId, shown.userId)
        assertEquals(illust.pageCount, shown.pageCount)
        assertEquals(illust.title, shown.title)
        assertEquals(listOf("cat"), shown.tags.map { it.name })
    }

    @Test
    fun aKeptNovelShowsAsTheNovelItWas() {
        val kept = PixivKept.of(novel(5, userId = 6))
        assertEquals("Novel 5", kept.toNovel().title)
        assertEquals(6L, kept.toNovel().user.id)
    }

    @Test
    fun theHistoryPutsTheLatestFirstOnceEach(): Unit = runBlocking {
        val history = PixivHistory(dir().file("history.json"), PixivKind.WORK)
        history.record(PixivVisit(PixivKept.of(work(1)), Instant.ofEpochSecond(1)))
        history.record(PixivVisit(PixivKept.of(work(2)), Instant.ofEpochSecond(2)))
        history.record(PixivVisit(PixivKept.of(work(1)), Instant.ofEpochSecond(3)))
        assertEquals(listOf(1L, 2L), history.load().map { it.kept.id })
        history.remove(1)
        assertEquals(listOf(2L), history.load().map { it.kept.id })
        history.clear()
        assertTrue(history.load().isEmpty())
    }

    @Test
    fun theHistoryIsReadBackFromItsFile(): Unit = runBlocking {
        val store = dir()
        PixivHistory(store.file("history.json"), PixivKind.WORK).record(PixivVisit(PixivKept.of(work(4)), Instant.ofEpochSecond(4)))
        val again = PixivHistory(StoreDir(store.root).file("history.json"), PixivKind.WORK)
        assertEquals(listOf(4L), again.load().map { it.kept.id })
    }

    @Test
    fun bookmarksKeptHereComeBackFromTheirBackup(): Unit = runBlocking {
        val bookmarks = PixivDeviceBookmarks(dir().file("bookmarks.json"))
        bookmarks.put(PixivKind.WORK, PixivKept.of(work(1)), listOf("cat", "cat"), Instant.ofEpochSecond(10))
        bookmarks.put(PixivKind.NOVEL, PixivKept.of(novel(2)), emptyList(), Instant.ofEpochSecond(11))
        val other = PixivDeviceBookmarks(dir().file("bookmarks.json"))
        other.import(bookmarks.export())
        assertEquals(bookmarks.load(), other.load())
        assertEquals(listOf("cat"), other.find(PixivKind.WORK, 1)?.tags)
        bookmarks.remove(PixivKind.WORK, 1)
        assertNull(bookmarks.find(PixivKind.WORK, 1))
    }

    @Test
    fun aBookmarkMadeAgainKeepsWhenItWasFirstMade(): Unit = runBlocking {
        val bookmarks = PixivDeviceBookmarks(dir().file("bookmarks.json"))
        bookmarks.put(PixivKind.WORK, PixivKept.of(work(1)), emptyList(), Instant.ofEpochSecond(10))
        bookmarks.put(PixivKind.WORK, PixivKept.of(work(1)), listOf("sky"), Instant.ofEpochSecond(20))
        assertEquals(Instant.ofEpochSecond(10), bookmarks.find(PixivKind.WORK, 1)?.at)
    }

    @Test
    fun theTagFilterCountsAndNarrowsTheBookmarksOfOneKind() {
        val marks = listOf(mark(PixivKind.WORK, 1, "cat", "sky"), mark(PixivKind.WORK, 2, "cat"), mark(PixivKind.WORK, 3), mark(PixivKind.NOVEL, 4, "cat"))
        assertEquals(listOf("cat" to 2, "sky" to 1), PixivDeviceBookmarks.tagCounts(marks, PixivKind.WORK))
        assertEquals(listOf(1L, 2L), PixivDeviceBookmarks.under(marks, PixivKind.WORK, "cat").map { it.kept.id })
        assertEquals(listOf(3L), PixivDeviceBookmarks.under(marks, PixivKind.WORK, PixivDeviceBookmarks.UNTAGGED).map { it.kept.id })
        assertEquals(3, PixivDeviceBookmarks.under(marks, PixivKind.WORK, null).size)
    }
}
