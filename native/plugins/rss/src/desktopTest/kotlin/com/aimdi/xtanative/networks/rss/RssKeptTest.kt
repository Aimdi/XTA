package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssItem
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssKeptTest {
    private val blog = RssFeed(id = "https://blog.example/feed", feedUrl = "https://blog.example/feed", name = "Blog", siteUrl = "https://blog.example/")
    private val news = RssFeed(id = "https://news.example/rss", feedUrl = "https://news.example/rss", name = "News")

    @Test
    fun feedsAreReadAsXtaWritesThemAndAsItsTableDid() {
        val xta = """[{"id":"https://blog.example/feed","feedUrl":"https://blog.example/feed","name":"Blog","siteUrl":"https://blog.example/","iconUrl":null,"description":null},""" +
            """{"feed_url":"https://News.example/rss/","name":"News","site_url":null},{"id":"","feedUrl":"x"},{"name":"no address"},42]"""

        val feeds = RssFeeds.decode(xta)

        assertEquals(listOf(blog, news.copy(feedUrl = "https://News.example/rss/")), feeds)
        assertEquals(feeds, RssFeeds.decode(RssFeeds.encode(feeds)), "what is written reads back")
        assertEquals(emptyList(), RssFeeds.decode("not json"))
        assertEquals(emptyList(), RssFeeds.decode(null))
    }

    @Test
    fun aFeedFollowedGoesFirstOnceAndAnUnfollowedOneGoes() {
        assertEquals(listOf(news, blog), RssFeeds.followed(listOf(blog, news), news))
        assertEquals(listOf(blog), RssFeeds.unfollowed(listOf(blog, news), news.id))
    }

    @Test
    fun aBackupsRowsAreFollowedTheFeedsAlreadyThereKeepingTheirPlace() {
        val renamed = news.copy(name = "News, renamed")
        val other = RssFeed(id = "https://other.example/atom", feedUrl = "https://other.example/atom", name = "Other")

        assertEquals(listOf(renamed, other), RssFeeds.restored(listOf(blog, news), listOf(other, renamed)))
    }

    @Test
    fun tagsAreCleanedSortedAndDroppedWhenEmpty() {
        val tags = RssTags.withTags(emptyMap(), "blog", RssTags.parse(" News, blogs / , Tech"))

        assertEquals(mapOf("blog" to listOf("News", "blogs", "Tech")), tags)
        assertEquals("News, blogs, Tech", RssTags.field(tags, "blog"))
        assertEquals(emptyMap(), RssTags.withTags(tags, "blog", listOf(" ")))
        assertEquals(listOf("News", "Tech", "blogs"), RssTags.all(tags + ("news" to listOf("News"))))
        assertEquals(tags, RssTags.decode(RssTags.encode(tags)))
        assertEquals(mapOf("a" to listOf("x")), RssTags.decode("""{"a": [" x ", ""], "b": "no list"}"""))
    }

    @Test
    fun theTimelineFiltersByReadMarksAndTagsAndStaysFreshTenMinutes() {
        val one = RssItem(id = "1", title = "One", feedId = "blog", feedTitle = "Blog")
        val two = RssItem(id = "2", title = "Two", feedId = "news", feedTitle = "News")
        val tags = mapOf("blog" to listOf("Tech"))

        assertEquals(listOf(two), RssTimelineRules.shown(listOf(one, two), RssFilter.UNREAD, setOf("1"), null, tags))
        assertEquals(listOf(one), RssTimelineRules.shown(listOf(one, two), RssFilter.ALL, emptySet(), "Tech", tags))
        assertTrue(RssTimelineRules.anyUnread(listOf(one, two), setOf("1")))
        assertFalse(RssTimelineRules.anyUnread(listOf(one), setOf("1")))

        val at = Instant.parse("2026-11-04T10:00:00Z")
        assertTrue(RssTimelineRules.fresh(at, at.plus(Duration.ofMinutes(9))))
        assertFalse(RssTimelineRules.fresh(at, at.plus(Duration.ofMinutes(10))))
        assertFalse(RssTimelineRules.fresh(null, at))
    }
}
