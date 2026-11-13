package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** threads.com's public pages, as a logged-out browser gets them (XTA's threads_parse.dart). */
class ThreadsPagesTest {
    private val profilePage = fixture("profile_page.html")

    @Test
    fun aProfilePageGivesTheCardAGuestIsShown() {
        val profile = ThreadsPages.guestProfile(profilePage, "zuck")!!
        assertEquals("zuck", profile.username)
        assertEquals("Mark Zuckerberg", profile.fullName)
        assertEquals(5_700_000L, profile.followerCount)
        assertEquals(1_330L, profile.mediaCount)
        assertEquals("Mostly MMA takes. See the latest conversations with @zuck.", profile.biography)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-19/zuck.jpg?stp=dst-jpg_s150x150&oe=6700", profile.profilePicUrl,
            "an address escaped twice is unescaped twice")
        assertEquals("314216", profile.pk)
    }

    @Test
    fun aGuestCardKnowsNoFollowingCountAndNoLink() {
        val page = """
            <meta property="og:title" content="Maya Chen (&#064;maya) &#x2022; Threads, Say more" />
            <meta property="og:description" content="1.2K Followers &#x2022; 48 Threads &#x2022; Ceramics, slowly." />
            <meta content="https://cdn.example/maya.jpg" property="og:image" />
        """
        val profile = ThreadsPages.guestProfile(page, "@Maya")!!
        assertEquals("maya", profile.username)
        assertEquals("Maya Chen", profile.fullName)
        assertEquals(1_200L, profile.followerCount)
        assertEquals(48L, profile.mediaCount)
        assertEquals("https://cdn.example/maya.jpg", profile.profilePicUrl, "content may come before the property")
        assertNull(profile.followingCount)
        assertNull(profile.externalUrl)
    }

    @Test
    fun aPageWithoutItsTagsGivesNoCard() {
        assertNull(ThreadsPages.guestProfile("<html><body>Log in</body></html>", "zuck"))
        val nameless = ThreadsPages.guestProfile("""<meta property="og:image" content="https://cdn/x.jpg" />""", "zuck")!!
        assertEquals("zuck", nameless.fullName, "the handle stands in for a name the page did not give")
    }

    @Test
    fun theLsdTokenAndTheOwnersIdAreReadOffThePage() {
        assertEquals("AVqbxe3J_YA", ThreadsPages.lsd(profilePage))
        assertEquals("314216", ThreadsPages.userId(profilePage, "zuck"))
        assertNull(ThreadsPages.lsd("nope"))
        assertEquals("AbC_12-x", ThreadsPages.lsd("""prefix["LSD",[],{"token":"AbC_12-x"}]suffix"""))
    }

    @Test
    fun theOwnersIdIsPropsUserIdElseAPkNearTheHandleElseTheMostNamedUserId() {
        assertEquals("63055343223", ThreadsPages.userId("""{"props":{"initial_thread_count":4,"user_id":"63055343223"}}""", "zuck"))
        assertEquals("63055343223", ThreadsPages.userId("""{"username":"zuck","full_name":"Z","pk":"63055343223"}""", "zuck"))
        assertEquals("63055343223", ThreadsPages.userId("""{"pk":"63055343223","full_name":"Z","username":"Zuck"}""", "zuck"))
        assertNull(ThreadsPages.userId("""{"username":"zuck","full_name":"Z","pk":"63055343223"}""", "instagram"))
        assertEquals("63404918397", ThreadsPages.userId("""{"userID":"0"}{"userID":"63404918397"}{"userID":"63404918397"}{"userID":"1"}""", "anyone"))
        assertEquals("7", ThreadsPages.userId("""{"user_id":"0"}{"userID":"7"}""", "anyone"), "the logged-out stub 0 is no one")
        assertNull(ThreadsPages.userId("""{"user_id":"5"}""", " "))
    }

    @Test
    fun compactCountsReadTheirSuffixes() {
        assertEquals(5_700_000L, ThreadsPages.compactCount("5.7M"))
        assertEquals(1_400L, ThreadsPages.compactCount("1.4K"))
        assertEquals(2_000_000_000L, ThreadsPages.compactCount("2b"))
        assertEquals(380L, ThreadsPages.compactCount(" 380 "))
        assertEquals(1_330L, ThreadsPages.compactCount("1,330"))
        assertNull(ThreadsPages.compactCount("nope"))
        assertNull(ThreadsPages.compactCount(null))
    }

    @Test
    fun aProfilePageKeepsTheThreadsTheProfileWroteOrRepostedWithTheirLength() {
        val threads = ThreadsPages.profileThreads(profilePage, "zuck")
        assertEquals(listOf("3400000000000000100", "3400000000000000102"), threads.map { it.id })
        assertEquals(1, threads.first().selfThreadCount)
        assertEquals(10L, threads.first().likeCount)
        val repost = threads.last()
        assertTrue(repost.isRepost)
        assertEquals("meta", repost.handle)
        assertEquals("zuck", repost.repostedByHandle)
        assertEquals("A post from Meta", repost.text)
    }

    @Test
    fun aProfilePageKeepsOnlyTheRootOfEachChain() {
        val threads = ThreadsPages.profileThreads(page(listOf(listOf(postJson("1", text = "root"), postJson("2", user = "other", text = "reply")))), "zuck")
        assertEquals(listOf("root"), threads.map { it.text })
        assertEquals(0, threads.single().selfThreadCount)
    }

    @Test
    fun eachThreadItemsListIsAChainAndARepeatedPostStaysWhereItFirstAppeared() {
        val chains = ThreadsPages.chains(page(listOf(
            listOf(postJson("1"), postJson("2")),
            listOf(postJson("3", user = "a"), postJson("4", user = "b")),
            listOf(postJson("2"), postJson("5", user = "c")),
        )))
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4"), listOf("5")), chains.map { chain -> chain.map { it.id } })
    }

    @Test
    fun aScriptThatIsNotJsonOrHoldsNoThreadsIsSkipped() {
        val body = """<script data-sjs>{"thread_items": [oops</script><script data-sjs>{"other":1}</script>""" + page(listOf(listOf(postJson("1"))))
        assertEquals(listOf("1"), ThreadsPages.thread(body).map { it.id })
        assertTrue(ThreadsPages.chains("<html></html>").isEmpty())
    }

    @Test
    fun aPostPageFlattensToItsRootAndReplies() {
        assertEquals(listOf("root", "reply"), ThreadsPages.thread(page(listOf(listOf(postJson("1", text = "root"), postJson("2", user = "other", text = "reply"))))).map { it.text })
        assertEquals(listOf("501", "502", "503", "504", "505", "506", "507"), ThreadsPages.thread(fixture("post_page.html")).map { it.id })
    }

    @Test
    fun theRepliesPageKeepsOnlyTheProfilesOwnReplies() {
        val replies = ThreadsPages.replies(fixture("replies_page.html"), "zuck")
        assertEquals(listOf("702", "703"), replies.map { it.id })
        assertEquals("dana", replies.last().replyToHandle)
        val inline = ThreadsPages.replies(page(listOf(
            listOf(postJson("1", user = "meta"), postJson("2", info = mapOf("is_reply" to true, "reply_to_author" to mapOf("username" to "meta")))),
            listOf(postJson("3", user = "other"), postJson("4", user = "another")),
        )), "zuck")
        assertEquals(listOf("2"), inline.map { it.id })
    }

    @Test
    fun aSearchPageGivesInEachChainThePostItFoundTheChainFor() {
        val page = fixture("search_page.html")
        assertEquals(listOf("602", "603", "604"), ThreadsPages.searchResults(page, "kiln").map { it.id })
        assertEquals(listOf("601", "603", "604"), ThreadsPages.searchResults(page, "#ceramics", tag = true).map { it.id },
            "a tag search takes the post filed under the topic")
    }

    @Test
    fun aProfilePiecedTogetherFromPostsKnowsNoCount() {
        val profile = ThreadsProfile.fromPosts("@Maya", listOf(
            ThreadsPost(id = "0", handle = "ola", authorName = "Ola", text = "a reply"),
            ThreadsPost(id = "1", handle = "maya", authorName = "Maya Chen", text = "kiln", avatarUrl = "https://example.org/a.jpg"),
        ))!!
        assertEquals("maya", profile.username)
        assertEquals("Maya Chen", profile.fullName)
        assertEquals("https://example.org/a.jpg", profile.profilePicUrl)
        assertEquals(listOf(null, null, null), listOf(profile.followerCount, profile.followingCount, profile.mediaCount))
        assertNull(profile.externalUrl)
        assertNull(ThreadsProfile.fromPosts("nobody", emptyList()))
    }
}
