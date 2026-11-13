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
}
