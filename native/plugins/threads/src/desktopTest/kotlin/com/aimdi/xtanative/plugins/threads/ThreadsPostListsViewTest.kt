package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsMusic
import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsReplyAudience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A post's lists and marks as the plugin shows them: when its counts open a list, the routes, what a list shows, and the marks kept with a post. */
class ThreadsPostListsViewTest {
    private val post = ThreadsPost(
        id = "999",
        handle = "zuck",
        authorName = "Z",
        text = "t",
        url = "https://www.threads.com/@zuck/post/C1",
        repostCount = 3,
        quoteCount = 2,
    )

    @Test
    fun repostsAndQuotesOpenTheirListsOnlyForASignedInReaderAndACountedPost() {
        assertTrue(ThreadsPeopleView.opensReposters(post, signedIn = true))
        assertTrue(ThreadsPeopleView.opensQuotes(post, signedIn = true))
        assertFalse(ThreadsPeopleView.opensReposters(post, signedIn = false))
        assertFalse(ThreadsPeopleView.opensQuotes(post, signedIn = false))
        assertFalse(ThreadsPeopleView.opensReposters(post.copy(repostCount = 0), signedIn = true))
        assertFalse(ThreadsPeopleView.opensQuotes(post.copy(quoteCount = null), signedIn = true))
        assertFalse(ThreadsPeopleView.opensQuotes(post.copy(url = null, id = "rss-item"), signedIn = true), "no media id, no list")
    }

    @Test
    fun theRepostersAndTheQuotesHaveRoutesOfTheirOwnAndAPeopleRouteNeverOpensAPostsList() {
        assertEquals(ThreadsRoute.Reposters("181", post.url), ThreadsRoutes.parse(ThreadsRoutes.reposters("181", post.url)))
        assertEquals(ThreadsRoute.Quotes("181", null), ThreadsRoutes.parse(ThreadsRoutes.quotes("181", null)))
        assertNull(ThreadsRoutes.parse("quotes?id=C1"))
        assertNull(ThreadsRoutes.parse("reposters"))
        assertEquals(ThreadsRoute.People("zuck", ThreadsPeopleList.FOLLOWERS), ThreadsRoutes.parse("people?handle=zuck&list=reposters"))
    }

    @Test
    fun aQuotesListShowsEachPostOnceInTheOrderRead() {
        val one = post.copy(id = "1", url = "https://www.threads.com/@a/post/A1")
        val again = one.copy(id = "1b")
        val two = post.copy(id = "2", url = null)
        assertEquals(listOf("1", "2"), ThreadsPostLists.shown(listOf(one, two), listOf(again, two)).map { it.id })
    }

    @Test
    fun aMissingListSaysThreadsDoesNotGiveItHereAndAThrottleItsTime() {
        val missing = ThreadsFailures.postList(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404"), "en")
        assertEquals(Strings.list_unavailable.get("en"), missing)
        assertEquals(Strings.not_configured.get("en"), ThreadsFailures.postList(ThreadsException(ThreadsFailure.NOT_CONFIGURED, "none"), "en"))
    }

    @Test
    fun editedSitsAfterTheTimeAndAloneWithoutOne() {
        assertEquals("2h · Edited", ThreadsCardRules.timeLine("2h", "Edited"))
        assertEquals("2h", ThreadsCardRules.timeLine("2h", null))
        assertEquals("Edited", ThreadsCardRules.timeLine("", "Edited"))
        assertEquals("", ThreadsCardRules.timeLine(" ", null))
    }

    @Test
    fun thePostsMarksAreKeptWithALikedOrSnapshottedPostAndNothingWhenUnset() {
        val facts = ThreadsPostFacts(
            edited = true,
            likedByAuthor = true,
            replyAudience = ThreadsReplyAudience.MENTIONED,
            music = ThreadsMusic("Made-up Song", "Made-up Band", "https://cdn.example/c.jpg"),
        )
        val kept = ThreadsPostJson.list(ThreadsPostJson.listText(listOf(post.copy(facts = facts)))).single()
        assertEquals(facts, kept.facts)
        assertEquals(ThreadsPostFacts.NONE, ThreadsPostJson.list(ThreadsPostJson.listText(listOf(post))).single().facts)
    }
}
