package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.FeedCache
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ReadMarksTest {
    /** A post X would have made at [at]: its id is a snowflake of that time. */
    private fun thread(at: Instant, threadId: String? = null): XThread {
        val id = ((at.toEpochMilli() - 1_288_834_974_657L) shl 22).toString()
        return XThread(threadId ?: id, listOf(XPost(id, XUser("a", "a", "A"), at, "text")))
    }

    private fun marks() = ReadMarks(FeedCache(Files.createTempDirectory("marks").resolve("feed-read-g.json").toFile()))

    @Test
    fun aFeedNeverReadHereHasNoPositionAndNothingUnread(): Unit = runBlocking {
        val marks = marks()
        assertNull(marks.position())
        assertFalse(marks.unread(tracks = true))
    }

    @Test
    fun theLastReadPostIsKeptAndANewerOneReadLightsTheDot(): Unit = runBlocking {
        val marks = marks()
        val read = thread(Instant.parse("2026-05-01T10:00:00Z"))
        marks.record(read, at = 1)
        assertEquals(read.id, marks.position()?.threadId)
        marks.noteNewest(read)
        assertFalse(marks.unread(tracks = true), "nothing newer than what was read")
        marks.noteNewest(thread(Instant.parse("2026-05-01T11:00:00Z"), threadId = "1"))
        assertTrue(marks.unread(tracks = true), "a conversation's own id is its root's: the first post's time counts")
        assertFalse(marks.unread(tracks = false), "a feed that keeps no position never lights up")
    }

    @Test
    fun anIdsTimeIsItsSnowflakes() {
        val at = Instant.parse("2026-05-01T10:00:00Z")
        assertEquals(at.toEpochMilli(), ReadMarks.timeOfId(thread(at).id))
        assertNull(ReadMarks.timeOfId("not a number"))
        assertEquals("read-g1", ReadMarks.key("g1"))
    }

    @Test
    fun theSamePostReadAndNewestIsReadToTheMillisecond(): Unit = runBlocking {
        val marks = marks()
        val read = thread(Instant.parse("2026-05-01T10:00:00.523Z"))
        val seconds = XThread(read.id, listOf(read.posts.first().copy(createdAt = Instant.parse("2026-05-01T10:00:00Z"))))
        marks.record(seconds, at = 1)
        marks.noteNewest(seconds)
        assertFalse(marks.unread(tracks = true), "X gives a post's time in seconds, its snowflake to the millisecond: the same post is read")
    }

    @Test
    fun theCountIsOfTheKeptPostsAfterTheLastReadOne(): Unit = runBlocking {
        val marks = marks()
        val old = thread(Instant.parse("2026-05-01T10:00:00Z"))
        val newer = (1..3).map { thread(Instant.parse("2026-05-01T1${it}:00:00Z")) }
        assertEquals(0, marks.unreadCount(tracks = true, newer + old), "never read from X: nothing to count")
        marks.record(old, at = 1)
        marks.noteNewest(newer.last())
        assertEquals(3, marks.unreadCount(tracks = true, newer.reversed() + old + newer.first()))
        assertEquals(1, marks.unreadCount(tracks = true, listOf(old)), "the newer posts not kept still count one")
        assertEquals(0, marks.unreadCount(tracks = false, newer), "a feed that keeps no position never counts")
    }

    @Test
    fun markingTheNewestReadClearsTheDotAndKeepsItsTime(): Unit = runBlocking {
        val marks = marks()
        assertFalse(marks.markNewestRead(at = 1), "nothing read from X, nothing to mark")
        marks.record(thread(Instant.parse("2026-05-01T10:00:00Z")), at = 1)
        val newest = thread(Instant.parse("2026-05-01T12:00:00.250Z"), threadId = "1")
        marks.noteNewest(newest)
        assertTrue(marks.unread(tracks = true))
        assertTrue(marks.markNewestRead(at = 2))
        assertFalse(marks.unread(tracks = true))
        assertEquals(newest.posts.first().id, marks.position()?.threadId)
        assertEquals(Instant.parse("2026-05-01T12:00:00.250Z"), marks.position()?.createdAt)
        assertTrue(marks.position()!!.isSeen(thread(Instant.parse("2026-05-01T12:00:00Z"))), "the posts up to it read as seen")
    }

    @Test
    fun aCountPast99IsWrittenAs99Plus() {
        assertEquals("7", ReadMarks.label(7))
        assertEquals("99", ReadMarks.label(99))
        assertEquals("99+", ReadMarks.label(100))
    }
}
