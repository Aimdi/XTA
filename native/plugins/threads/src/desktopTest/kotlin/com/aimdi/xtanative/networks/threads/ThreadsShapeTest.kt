package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.JsonObject

class ThreadsShapeTest {
    @Test
    fun aPageIsToldByItsKeysPathsAndCountsNeverItsValues() {
        val html = fixture("post_page.html")
        val roots = ThreadsShape.scripts(html)
        val posts = ThreadsShape.posts(roots)

        assertEquals(8, posts.size)
        assertEquals(8, ThreadsShape.keyCounts(posts)["caption"])
        assertEquals(1, ThreadsShape.keyCounts(posts)["like_count"])
        assertEquals(mapOf("text_post_app_info.is_reply" to 1), ThreadsShape.paths(posts, Regex("^is_reply$"), depth = 3))
        assertEquals(listOf("BarcelonaPostPageDirectQueryRelayPreloader", "BarcelonaPostPageRefetchableDirectQuery"), ThreadsShape.queries(html))
        val told = ThreadsShape.told(ThreadsShape.keyCounts(posts)) + ThreadsShape.told(ThreadsShape.paths(roots, ThreadsShape.FEATURES, depth = 40))
        assertFalse("zuck" in told || "Threads turns three" in told || "AVpostPage1" in told, told)
    }

    @Test
    fun aKeyThatIsNotAPlainNameIsNotSpelledOut() {
        val counts = ThreadsShape.keyCounts(listOf(json(mapOf("63404918397" to 1, "Zuck" to 2, "ok" to null)) as JsonObject))
        assertEquals(mapOf("<other>" to 2), counts)
        assertEquals("none", ThreadsShape.told(emptyMap()))
    }
}
