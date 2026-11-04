package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.NetworkPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Posts sharing an article stacked, and runs of reposts folded (lib/tweet/feed_link_grouping.dart, link_identity.dart, boost_runs.dart). */
class FeedLayoutTest {
    private val amy = XUser("1", "amy", "Amy")

    private fun post(id: String, at: Long, vararg links: String) = XPost(
        id = id,
        author = amy,
        createdAt = Instant.ofEpochSecond(at),
        text = links.joinToString(" "),
        entities = links.map { XEntity.Link(0, 1, "https://t.co/$id", it, it) },
    )

    private fun thread(id: String, at: Long, vararg links: String) = XThread(id, listOf(post(id, at, *links)))

    private fun boost(id: String, at: Long, vararg links: String) = XThread(id, listOf(post(id, at).copy(reposted = post("o$id", at, *links))))

    @Test
    fun anArticleIsOneKeyForEveryWayOfWritingIt() {
        assertEquals(
            ArticleLinks.canonical("https://News.Example/story?b=2&a=1"),
            ArticleLinks.canonical("https://news.example/story?utm_source=x&a=1&fbclid=z&b=2#top"),
        )
        assertEquals("https://news.example/story", ArticleLinks.canonical(" https://news.example/story?utm_medium=feed "))
    }

    @Test
    fun postsProfilesHomePagesAndSignInsAreNoArticle() {
        listOf(
            "https://x.com/amy/status/1",
            "https://mobile.twitter.com/amy",
            "https://bsky.app/profile/amy",
            "https://www.instagram.com/p/1",
            "https://news.example/",
            "https://news.example",
            "https://news.example/login?next=/story",
            "https://news.example/#/story",
            "https://news.example/app#!story",
            "https://me:secret@news.example/story",
            "ftp://news.example/story",
            "not a link",
        ).forEach { assertNull(ArticleLinks.canonical(it), it) }
        assertNull(ArticleLinks.canonical(null))
    }

    @Test
    fun aThreadHasAnArticleOnlyAsOneLonePostWithOneLink() {
        assertEquals("https://news.example/a", ArticleLinks.of(thread("1", 1, "https://news.example/a", "https://news.example/a?utm_x=1")))
        assertNull(ArticleLinks.of(thread("2", 1, "https://news.example/a", "https://news.example/b")), "two articles")
        assertEquals("https://news.example/a", ArticleLinks.of(boost("3", 1, "https://news.example/a")), "a repost stands for the post")
        val conversation = XThread("4", listOf(post("4", 1, "https://news.example/a"), post("5", 2)))
        assertNull(ArticleLinks.of(conversation), "a conversation may discuss several")
    }

    @Test
    fun aPluginPostsArticleIsItsCardElseItsOwnAddressWhenItHasATitle() {
        val card = NetworkPost("p", "a", "A", "a", card = LinkCard("https://news.example/a"), url = "https://social.example/@a/1")
        assertEquals("https://news.example/a", ArticleLinks.of(card))
        assertNull(ArticleLinks.of(NetworkPost("p", "a", "A", "a", url = "https://social.example/@a/1")), "a post's own page")
        assertEquals("https://blog.example/post", ArticleLinks.of(NetworkPost("p", "a", "A", "a", url = "https://blog.example/post", title = "Post")))
    }

    @Test
    fun postsSharingAnArticleStackUnderTheNewestOfThemPluginsIncluded() {
        val newest = thread("3", 300, "https://news.example/a")
        val other = thread("2", 200)
        val oldest = thread("1", 100, "https://news.example/a?utm_campaign=y")
        val rss = SourcedPost("rss", NetworkPost("r", "f", "Feed", "f", createdAt = Instant.ofEpochSecond(250), url = "https://news.example/a", title = "A"))
        val layout = FeedLayout.of(listOf(newest, other, oldest), listOf(rss), collapseReposts = false)
        assertEquals(FeedPart.Stack(listOf("x:3", "rss:r", "x:1")), layout.ofThread(0, newest))
        assertEquals(FeedPart.Plain, layout.ofThread(1, other))
        assertEquals(FeedPart.Hidden, layout.ofThread(2, oldest))
        assertEquals(FeedPart.Hidden, layout.ofPost(rss))
    }

    @Test
    fun aPluginsPostAnchorsTheStackWhenItIsTheNewest() {
        val x = thread("1", 100, "https://news.example/a")
        val rss = SourcedPost("rss", NetworkPost("r", "f", "Feed", "f", createdAt = Instant.ofEpochSecond(250), card = LinkCard("https://news.example/a")))
        val layout = FeedLayout.of(listOf(x), listOf(rss), collapseReposts = false)
        assertEquals(FeedPart.Stack(listOf("rss:r", "x:1")), layout.ofPost(rss))
        assertEquals(FeedPart.Hidden, layout.ofThread(0, x))
    }

    @Test
    fun runsOfRepostsFoldOnlyWithTheSettingOn() {
        val threads = listOf(thread("1", 500), boost("2", 400), boost("3", 300), boost("4", 200), thread("5", 100), boost("6", 50))
        val folded = FeedLayout.of(threads, collapseReposts = true)
        assertEquals(FeedPart.Run(threads.subList(1, 4)), folded.ofThread(1, threads[1]))
        assertEquals(FeedPart.Hidden, folded.ofThread(2, threads[2]))
        assertEquals(FeedPart.Hidden, folded.ofThread(3, threads[3]))
        assertEquals(FeedPart.Plain, folded.ofThread(5, threads[5]), "a lone repost stays a post")
        val plain = FeedLayout.of(threads, collapseReposts = false)
        threads.forEachIndexed { index, thread -> assertEquals(FeedPart.Plain, plain.ofThread(index, thread)) }
    }

    @Test
    fun aRunHoldingAStackedPostIsDrawnPostByPost() {
        val threads = listOf(boost("1", 400, "https://news.example/a"), boost("2", 300), thread("3", 200, "https://news.example/a"))
        val layout = FeedLayout.of(threads, collapseReposts = true)
        assertEquals(FeedPart.Stack(listOf("x:1", "x:3")), layout.ofThread(0, threads[0]))
        assertEquals(FeedPart.Plain, layout.ofThread(1, threads[1]), "its run is left alone")
        assertEquals(FeedPart.Hidden, layout.ofThread(2, threads[2]))
    }

    @Test
    fun aFeedWithNothingToStackOrFoldDrawsEveryPost() {
        val threads = listOf(thread("1", 2, "https://news.example/a"), thread("2", 1, "https://news.example/b"))
        val layout = FeedLayout.of(threads, collapseReposts = false)
        threads.forEachIndexed { index, thread -> assertEquals(FeedPart.Plain, layout.ofThread(index, thread)) }
        assertEquals(FeedPart.Plain, FeedLayout.None.ofPost(sourced("rss", "r", 1)))
    }
}
