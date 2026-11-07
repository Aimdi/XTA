package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.FeedState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** XTA's feed_session_cache_test.dart: the feeds left in this run, bounded to the ones used last. */
class FeedSessionCacheTest {
    private fun kept(key: String) = KeptFeed(key, FeedState(emptyList()), FeedShown(), savedAt = null)

    private fun place(offset: Int) = KeptPlace("post", offset, atTop = false)

    @Test
    fun theSameKeyKeepsTheSameFeed() {
        val cache = FeedSessionCache()
        val feed = kept("a")
        cache.keepFeed("a", feed)
        assertSame(feed, cache.feed("a"))
        assertSame(cache.feed("a"), cache.feed("a"))
    }

    @Test
    fun itStopsGrowingOnceFull() {
        val cache = FeedSessionCache(maxEntries = 3)
        repeat(10) { cache.keepFeed("group$it", kept("group$it")) }
        assertEquals(3, cache.size)
    }

    @Test
    fun theLeastRecentlyUsedGoesNotTheOldestMade() {
        val cache = FeedSessionCache(maxEntries = 2)
        val a = kept("a")
        cache.keepFeed("a", a)
        cache.keepPlace("a", place(100))
        cache.keepFeed("b", kept("b"))
        cache.keepPlace("b", place(5))
        assertSame(a, cache.feed("a"), "touching a makes b the one to go")
        cache.keepFeed("c", kept("c"))
        assertSame(a, cache.feed("a"))
        assertEquals(100, cache.place("a")?.offset)
        assertNull(cache.place("b"), "b was used least recently")
        assertNull(cache.feed("b"))
    }

    @Test
    fun anEvictedKeyLosesItsPlaceToo() {
        val cache = FeedSessionCache(maxEntries = 1)
        cache.keepFeed("a", kept("a"))
        cache.keepPlace("a", place(42))
        cache.keepFeed("b", kept("b"))
        assertNull(cache.place("a"))
        assertNull(cache.feed("a"))
    }

    @Test
    fun evictDropsOneKeySoTheNextOpeningStartsEmpty() {
        val cache = FeedSessionCache()
        cache.keepFeed("home--1", kept("home--1"))
        cache.keepPlace("home--1", place(80))
        cache.evict("home--1")
        assertEquals(0, cache.size)
        assertNull(cache.place("home--1"))
        assertNull(cache.feed("home--1"))
    }

    @Test
    fun clearEmptiesIt() {
        val cache = FeedSessionCache()
        cache.keepFeed("a", kept("a"))
        cache.keepPlace("a", place(1))
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.place("a"))
    }
}
