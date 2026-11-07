package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's feed_cache_decode_test.dart: what the feed kept reads back, and a damaged part is skipped rather than losing the rest. */
class SavedFeedTest {
    @Test
    fun aFeedWithNothingKeptHasNothingToShow() {
        assertTrue(SavedFeed.decode(SavedFeed.encode(SavedFeed(emptyList(), null)))!!.threads.isEmpty())
    }

    @Test
    fun aDamagedThreadIsSkippedAndTheRestStillReads() {
        val text = SavedFeed.encode(SavedFeed(listOf(thread(1), thread(2)), null, savedAt = 7))
        val damaged = text.replace("\"threads\":[", "\"threads\":[\"junk\",{\"posts\":null},")
        val read = SavedFeed.decode(damaged)!!
        assertEquals(listOf("1", "2"), ids(read.threads))
        assertEquals(7L, read.savedAt)
    }

    @Test
    fun anUnreadableFileIsNoFeed() {
        assertNull(runCatching { SavedFeed.decode("{not-json") }.getOrNull())
        assertNull(SavedFeed.decode("null"))
    }
}
