package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class BooruViewingTest {
    @Test
    fun theViewerLoadsTheNeighboursPicturesAheadButNoVideo() {
        val posts = listOf(
            post(id = "1", sampleUrl = "https://cdn.example/1.jpg"),
            post(id = "2", sampleUrl = "https://cdn.example/2.jpg"),
            post(id = "3", fileUrl = "https://cdn.example/3.mp4", fileExt = "mp4"),
        )
        assertEquals(listOf("https://cdn.example/2.jpg"), BooruPager.preloadUrls(posts, 0))
        assertEquals(listOf("https://cdn.example/1.jpg"), BooruPager.preloadUrls(posts, 1))
        assertEquals(listOf("https://cdn.example/2.jpg"), BooruPager.preloadUrls(posts, 2))
    }

    @Test
    fun theViewerGrowsOnlyWithTheSameListMadeLonger() {
        val shown = listOf(post(id = "1"), post(id = "2"))
        assertTrue(BooruPager.extends(shown, shown + post(id = "3")))
        assertFalse(BooruPager.extends(shown, listOf(post(id = "9"), post(id = "2"), post(id = "3"))))
        assertFalse(BooruPager.extends(shown, shown))
        assertFalse(BooruPager.extends(emptyList(), shown))
    }

    @Test
    fun theViewerKeepsTheLatestLists() {
        val viewers = BooruViewers(capacity = 2)
        val first = viewers.put(ViewerSource(listOf(post(id = "1"))))
        val second = viewers.put(ViewerSource(listOf(post(id = "2"))))
        viewers.put(ViewerSource(listOf(post(id = "3"))))
        assertNull(viewers.get(first))
        assertEquals("2", viewers.get(second)?.posts?.single()?.id)
    }

    @Test
    fun aLookupIsAskedOnceAndAgainAfterItFailed(): Unit = runBlocking {
        // As a screen's kept scope is: one lookup that fails stops no other.
        val scope = CoroutineScope(SupervisorJob())
        val lookups = BooruLookups(scope)
        var asked = 0
        assertEquals(1, lookups.remember("tags:1") { ++asked })
        assertEquals(1, lookups.remember("tags:1") { ++asked })
        val failed = runCatching {
            lookups.remember<Int>("notes:1") {
                asked++
                error("down")
            }
        }
        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertEquals(3, lookups.remember("notes:1") { ++asked })
        scope.cancel()
    }
}
