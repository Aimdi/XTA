package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.plugins.NetworkPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** XTA's placeInterleaved: where another network's posts sit among X's chains, by time. */
class FeedMixTest {
    private fun at(millis: Long?) = millis?.let(Instant::ofEpochMilli)

    private fun post(id: String, millis: Long?) =
        SourcedPost("booru", NetworkPost(id, "a", "A", "a", createdAt = at(millis)))

    private fun thread(id: String, vararg millis: Long?) =
        XThread(id, millis.mapIndexed { i, m -> XPost("$id-$i", XUser("u", "a", "A"), at(m), "text") })

    @Test
    fun takesTheNewestPostInTheChainNotTheFirst() {
        val chain = XThread(
            "t",
            listOf(
                XPost("1", XUser("u", "a", "A"), at(5L), "first"),
                XPost("2", XUser("u", "a", "A"), at(9L), "newest"),
            ),
        )
        assertEquals(9L, FeedMix.timeOf(chain))
    }

    @Test
    fun aChainWithNoDatesHasNone() {
        assertNull(FeedMix.timeOf(thread("t", null, null)))
    }

    @Test
    fun anItemNewerThanEverythingGoesAboveTheFirstChain() {
        val buckets = FeedMix.place(listOf(thread("a", 10L), thread("b", 5L)), listOf(post("p", 20L)))
        assertEquals(listOf("p"), buckets[0].map { it.post.id })
        assertEquals(3, buckets.size)
    }

    @Test
    fun anItemLandsDirectlyAboveTheFirstChainOlderThanIt() {
        val buckets = FeedMix.place(listOf(thread("a", 10L), thread("b", 5L)), listOf(post("p", 7L)))
        assertEquals(listOf("p"), buckets[1].map { it.post.id })
        assertEquals(0, buckets[0].size)
    }

    @Test
    fun anItemOlderThanEveryLoadedChainWaitsAtTheEnd() {
        val buckets = FeedMix.place(listOf(thread("a", 10L), thread("b", 5L)), listOf(post("p", 1L)))
        assertEquals(listOf("p"), buckets.last().map { it.post.id })
    }

    @Test
    fun severalItemsKeepNewestFirstWithinABucket() {
        val buckets = FeedMix.place(listOf(thread("a", 5L)), listOf(post("old", 6L), post("new", 9L)))
        assertEquals(listOf("new", "old"), buckets[0].map { it.post.id })
    }

    @Test
    fun aChainWithNoDateIsPassedOverRatherThanGuessedAt() {
        val buckets = FeedMix.place(listOf(thread("undated", null), thread("dated", 5L)), listOf(post("p", 9L)))
        assertEquals(0, buckets[0].size, "the undated chain takes nothing")
        assertEquals(listOf("p"), buckets[1].map { it.post.id })
    }

    @Test
    fun noItemsMeansEmptyBucketsOnePerChainPlusTheTail() {
        val buckets = FeedMix.place(listOf(thread("a", 10L), thread("b", 5L)), emptyList())
        assertEquals(3, buckets.size)
        assertEquals(0, buckets.sumOf { it.size })
    }

    @Test
    fun itemsWithNoChainsAtAllStillComeBack() {
        val buckets = FeedMix.place(emptyList(), listOf(post("a", 1L), post("b", 2L)))
        assertEquals(1, buckets.size)
        assertEquals(listOf("b", "a"), buckets[0].map { it.post.id })
    }
}
