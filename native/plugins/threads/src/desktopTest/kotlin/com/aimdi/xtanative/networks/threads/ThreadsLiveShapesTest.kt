package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Communities, reposts and the front page as CI's live probe saw a guest
 * given them (ThreadsDiscoveryLiveTest): every `tag_header` has
 * `display_name`, `tag_cluster_name`, a numeric `id` and `is_community`
 * false, communities included, and a community's emoji and favicon; a
 * repost is an outer post by the reposter, with no caption and its own code,
 * holding the original under `share_info.reposted_post`; the front page's
 * cursor answers nothing, and reading the page again brings new posts.
 * The values here are made up.
 */
class ThreadsLiveShapesTest {
    private val time = FakeTime()

    private fun header(display: String, cluster: String, emoji: String? = null, favicon: String? = null) = mapOf(
        "display_name" to display,
        "tag_cluster_name" to cluster,
        "id" to "17900000000000001",
        "is_community" to false,
        "community_emoji" to emoji,
        "community_favicon_url" to favicon,
    )

    private fun tagged(header: Map<String, Any?>) = ThreadsParser.post(j(postJson("1", info = mapOf("tag_header" to header))))!!

    @Test
    fun aCommunityIsToldByItsMarkThoughMetaSaysIsCommunityFalse() {
        val community = tagged(header("Cosplayers of Threads", "Cosplay", emoji = "🎭", favicon = "https://cdn.example/f.png"))
        assertEquals("Cosplayers of Threads", community.topicTag)
        assertTrue(community.facts.community)
        assertEquals("17900000000000001", community.facts.communityId)
        assertNull(community.facts.communityUrl, "no community address was seen")
        val topic = tagged(header("Books", "Books"))
        assertFalse(topic.facts.community)
        assertNull(topic.facts.communityId)
    }

    @Test
    fun aDisplayNameWrittenAsASlugGivesWayToTheClustersName() {
        assertEquals("Cosplay Photography", tagged(header("cosplayphotography", "Cosplay Photography")).topicTag)
        assertEquals("Knitting", tagged(header("Knitting", "Crafts")).topicTag)
        assertEquals("Crafts", tagged(header("", "Crafts")).topicTag)
    }

    @Test
    fun aRepostIsTheOriginalSharedByTheOuterPostsAuthorOnceInHome() {
        val original = postJson("2", user = "meta", takenAt = 1_767_000_000)
        val outer = postJson("10", user = "mosseri", text = "", takenAt = 1_767_100_000, info = mapOf("share_info" to mapOf("reposted_post" to original)))
        val page = page(listOf(listOf(outer), listOf(postJson("3", user = "mosseri"))))
        val reposts = ThreadsPages.reposts(ThreadsPages.chains(page), "mosseri")
        val repost = reposts.single()
        assertEquals("meta", repost.handle)
        assertEquals("mosseri", repost.repostedByHandle)
        assertEquals("C2", repost.shortcode, "the card links the original, not the outer post's own code")
        val mixed = ThreadsFeedMix.merge(listOf(ThreadsParser.post(j(original))!!), reposts)
        assertEquals(listOf("10"), mixed.map { it.id })
    }

    @Test
    fun aFrontPageCursorThatAnswersNothingGivesWayToReadingThePageAgain(): Unit = runBlocking {
        val front = fixture("paging_profile_page.html").replace("BarcelonaProfileThreadsTabDirectQuery", "BarcelonaLoggedOutFeedContainerQuery")
        var reads = 0
        val meta = FakeMeta { asked ->
            when {
                asked.method == "POST" -> ok("""{"data":{"viewer":{}},"extensions":{"is_final":true}}""")
                reads++ == 0 -> ok(front)
                else -> ok(page(listOf(listOf(postJson("again-$reads", user = "nasa")))))
            }
        }
        val forYou = ThreadsForYou(direct(meta, time = time))
        val first = forYou.page()
        val second = forYou.page(first.next)
        assertEquals(listOf("again-2"), second.posts.map { it.id })
        assertEquals("guest:2", second.next)
    }
}
