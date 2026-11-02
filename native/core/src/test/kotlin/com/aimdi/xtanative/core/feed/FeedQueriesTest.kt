package com.aimdi.xtanative.core.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** XTA's test/feed_search_query_test.dart, for the native port. */
class FeedQueriesTest {
    private fun query(terms: List<String>, replies: Boolean = true, reposts: Boolean = true) = FeedQueries.query(terms, replies, reposts)
    private fun pack(terms: List<String>, replies: Boolean = true, reposts: Boolean = true) = FeedQueries.pack(terms, { it }, replies, reposts)

    private val terms = (0 until 100).map { FeedQueries.term("account" + it.toString().padStart(3, '0')) }

    @Test
    fun theMembersAreBracketedSoTheFiltersCoverAllOfThem() {
        assertEquals("(from:a OR from:b) -filter:replies -filter:retweets", query(listOf("from:a", "from:b"), replies = false, reposts = false))
        assertEquals("(from:a) include:nativeretweets", query(listOf("from:a")))
        assertEquals("(from:a) -filter:replies include:nativeretweets", query(listOf("from:a"), replies = false))
        assertEquals("-filter:retweets", query(emptyList(), reposts = false))
    }

    @Test
    fun everySearchIsFilledAsFarAsXReadsIt() {
        for ((replies, reposts) in listOf(true to true, true to false, false to true, false to false)) {
            val chunks = pack(terms, replies, reposts)
            assertEquals(terms, chunks.flatten())
            chunks.forEachIndexed { index, chunk ->
                assertTrue(query(chunk, replies, reposts).length <= FeedQueries.LIMIT)
                if (index < chunks.lastIndex) {
                    assertTrue(query(chunk + chunks[index + 1].first(), replies, reposts).length > FeedQueries.LIMIT)
                }
            }
        }
    }

    @Test
    fun aNewMemberOnlyChangesTheLastChunk() {
        val before = pack(terms)
        val after = pack(terms + FeedQueries.term("newcomer"))
        assertEquals(before.dropLast(1), after.take(before.size - 1))
        assertTrue(before.first().size > 16)
    }

    @Test
    fun aMemberTooLongToShareASearchGetsItsOwnChunk() {
        val long = "(" + "x".repeat(600) + ")"
        assertEquals(listOf(listOf("from:a"), listOf(long), listOf("from:b")), pack(listOf("from:a", long, "from:b")))
    }
}
