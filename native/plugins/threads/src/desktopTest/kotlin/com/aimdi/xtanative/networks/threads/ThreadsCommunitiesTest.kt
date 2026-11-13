package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A post's community, as threads.com shows it beside the author. CI's probe
 * saw `tag_header.is_community`, `community_emoji` and `community_favicon_url`;
 * the name, id and link keys below are read wherever they might be, and
 * nothing breaks when they are not there.
 */
class ThreadsCommunitiesTest {
    private fun posted(header: Map<String, Any?>) = ThreadsParser.post(j(postJson("1", info = mapOf("tag_header" to header))))!!

    @Test
    fun aCommunityIsItsNameAndMarkAndIsToldFromATopic() {
        val post = posted(mapOf("display_name" to "Cosplayers of Threads", "is_community" to true, "community_emoji" to "🎭"))
        assertEquals("Cosplayers of Threads", post.topicTag)
        assertTrue(post.facts.community)
        assertEquals("🎭", post.facts.topicEmoji)
        val topic = posted(mapOf("display_name" to "#Books"))
        assertEquals("Books", topic.topicTag)
        assertFalse(topic.facts.community)
    }

    @Test
    fun theNameIsReadUnderAnyKeyItMightBeUnder() {
        assertEquals("Knitting", posted(mapOf("community_name" to "Knitting", "is_community" to true)).topicTag)
        assertEquals("Ceramics", posted(mapOf("community" to mapOf("name" to "Ceramics"))).topicTag)
        assertNull(posted(mapOf("is_community" to true)).topicTag, "a community without a name shows nothing")
    }

    @Test
    fun anIdAndAPageAreKeptOnlyWhenTheyAreACommunitysOwn() {
        val post = posted(mapOf("display_name" to "Cats", "is_community" to true, "id" to "17841400000", "url" to "/communities/17841400000"))
        assertEquals("17841400000", post.facts.communityId)
        assertEquals("https://www.threads.com/communities/17841400000", post.facts.communityUrl)
        val topic = posted(mapOf("display_name" to "Cats", "id" to "123456"))
        assertNull(topic.facts.communityId, "a topic's own id is no community")
        assertFalse(topic.facts.community)
        assertNull(posted(mapOf("display_name" to "Cats", "is_community" to true, "url" to "https://example.com/c")).facts.communityUrl)
        assertNull(posted(mapOf("display_name" to "Cats", "is_community" to true, "url" to "/@zuck")).facts.communityUrl)
    }

    @Test
    fun aCommunityLinkIsNeverTakenForAnAccount() {
        assertEquals(ThreadsLink.Community("https://www.threads.com/communities/123"), ThreadsLinks.parse("https://threads.net/communities/123"))
        assertEquals(ThreadsLink.Profile("zuck"), ThreadsLinks.parse("https://www.threads.com/@zuck"))
    }

    @Test
    fun aCommunityPageIsReadWithItsMarksAndHomeReadsAFollowedOne() = runBlocking {
        val body = page(
            listOf(
                listOf(postJson("1", user = "mo", info = mapOf("tag_header" to mapOf("display_name" to "Cosplayers", "is_community" to true, "community_emoji" to "🎭")))),
                listOf(postJson("2", user = "ling")),
            ),
        )
        val meta = FakeMeta { ok(body) }
        val time = FakeTime()
        val client = direct(meta, time = time)
        val found = client.communityPage("https://www.threads.com/communities/9")
        assertEquals(listOf("1", "2"), found.posts.map { it.id })
        assertEquals(ThreadsTopic("Cosplayers", emoji = "🎭", community = true), found.topics.single())
        assertEquals("/communities/9", meta.asked.single().path)
        val reader = ThreadsReader(client, ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock)
        assertEquals(setOf("1", "2"), reader.extras(emptyList(), listOf("https://www.threads.com/communities/9", "Knitting")).map { it.id }.toSet())
        assertEquals(listOf("/communities/9", "/search"), meta.asked.map { it.path }, "Home takes the page the screen was answered a moment ago")
    }
}
