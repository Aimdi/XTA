package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Topics and communities followed on this device, the route to a community, and a community kept with a liked post. */
class ThreadsTopicsTest {
    private val cosplay = ThreadsTopic("Cosplayers of Threads", emoji = "🎭", community = true, url = "https://www.threads.com/communities/9")

    @Test
    fun aFollowedTopicComesFirstOnceAndTakesTheMarksItLacked() {
        val books = ThreadsTopic("Books")
        val followed = ThreadsTopics.with(ThreadsTopics.with(emptyList(), books), cosplay)
        assertEquals(listOf("cosplayers of threads", "books"), followed.map { it.key })
        val again = ThreadsTopics.with(followed, ThreadsTopic("#BOOKS", emoji = "📚"))
        assertEquals(listOf("cosplayers of threads", "books"), again.map { it.key })
        assertEquals("📚", again[1].emoji)
        assertTrue(ThreadsTopics.follows(again, "#books"))
        assertFalse(ThreadsTopics.follows(ThreadsTopics.without(again, "Books"), "books"))
    }

    @Test
    fun theFollowedTopicsAreKeptAndAnUnsafeLinkOrPictureIsLeftOut() {
        val kept = ThreadsTopics.decode(ThreadsTopics.encode(listOf(cosplay, ThreadsTopic("Books"))))
        assertEquals(listOf(cosplay, ThreadsTopic("Books")), kept)
        val odd = ThreadsTopics.decode("""[{"name":"X","iconUrl":"http://plain/p.png","url":"https://example.com/c"},{"name":" "},{}]""")
        assertEquals(listOf(ThreadsTopic("X")), odd)
        assertEquals(emptyList(), ThreadsTopics.decode("not json"))
    }

    @Test
    fun aCommunityOpensItsOwnRouteAndALinkToOneToo() {
        assertEquals(ThreadsRoute.Community(cosplay), ThreadsRoutes.parse(ThreadsRoutes.community(cosplay)))
        val opened = ThreadsRoutes.parse(ThreadsRoutes.forUrl("https://www.threads.com/communities/cosplayers")!!) as ThreadsRoute.Community
        assertEquals("cosplayers", opened.topic.name)
        assertEquals("https://www.threads.com/communities/cosplayers", opened.topic.url)
        assertNull(ThreadsRoutes.parse("community?url=https%3A%2F%2Fexample.com%2Fc"), "no name and no threads.com page: nothing to open")
    }

    @Test
    fun aPostsCommunityIsWhatItsCardOpensAndIsKeptWithIt() {
        val facts = ThreadsPostFacts(topicEmoji = "🎭", community = true, communityId = "9", communityUrl = cosplay.url)
        val post = ThreadsPost(id = "1", handle = "mo", authorName = "Mo", text = "hi", topicTag = cosplay.name, facts = facts)
        assertEquals(cosplay, ThreadsTopics.of(post))
        assertEquals(facts, ThreadsFactsJson.facts(J(ThreadsFactsJson.json(facts))))
        assertNull(ThreadsTopics.of(post.copy(topicTag = null)))
    }
}
