package com.aimdi.xtanative.plugins.reddit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedditRoutesTest {
    private fun opened(url: String) = RedditRoutes.forUrl(url)?.let(RedditRoutes::parse)

    @Test
    fun aCommunityLinkOpensTheCommunity() {
        assertEquals(RedditRoute.Subreddit("dartlang"), opened("https://www.reddit.com/r/dartlang/"))
        assertEquals(RedditRoute.Subreddit("FlutterDev"), opened("https://old.reddit.com/r/FlutterDev/top/?t=week"))
        assertEquals(RedditRoute.Subreddit("pics"), opened("https://reddit.com/R/pics"))
        assertEquals(RedditRoute.Subreddit("pics"), opened("https://www.reddit.com/r/pics/s/AbCdEf123"))
    }

    @Test
    fun aPostLinkOpensItsThreadNamingWhatTheLinkKnows() {
        val thread = opened("https://www.reddit.com/r/dartlang/comments/ABC123/dart_4_is_out/")
        assertEquals(RedditRoute.Thread("/r/dartlang/comments/abc123/", id = "abc123", subreddit = "dartlang", title = "dart 4 is out"), thread)
        val stub = (thread as RedditRoute.Thread).stub()
        assertEquals("dart 4 is out", stub.title)
        assertEquals("dartlang", stub.subreddit)
        assertEquals("/r/dartlang/comments/abc123/", stub.permalink)
        assertEquals(
            RedditRoute.Thread("/r/dartlang/comments/abc123/", id = "abc123", subreddit = "dartlang"),
            opened("https://www.reddit.com/r/dartlang/comments/abc123"),
        )
    }

    @Test
    fun anAccountLinkOpensItsPosts() {
        assertEquals(RedditRoute.User("spez"), opened("https://www.reddit.com/user/spez"))
        assertEquals(RedditRoute.User("spez"), opened("https://www.reddit.com/u/spez/submitted/"))
    }

    @Test
    fun filesShortLinksAndOtherSitesAreNotTheseScreens() {
        assertNull(RedditRoutes.forUrl("https://i.redd.it/abc.jpg"))
        assertNull(RedditRoutes.forUrl("https://v.redd.it/abc"))
        assertNull(RedditRoutes.forUrl("https://redd.it/abc123"))
        assertNull(RedditRoutes.forUrl("https://example.com/r/dartlang"))
        assertNull(RedditRoutes.forUrl("https://www.reddit.com/"))
        assertNull(RedditRoutes.forUrl("https://www.reddit.com/r/x"))
        assertNull(RedditRoutes.forUrl("not a link"))
    }

    @Test
    fun everyRouteReadsBackAsTheScreenItNames() {
        assertEquals(RedditRoute.Thread("/r/a_b/comments/x1/what? & why/"), RedditRoutes.parse(RedditRoutes.thread("/r/a_b/comments/x1/what? & why/")))
        assertEquals(RedditRoute.Subreddit("dartlang"), RedditRoutes.parse(RedditRoutes.subreddit("dartlang")))
        assertEquals(RedditRoute.User("spez"), RedditRoutes.parse(RedditRoutes.user("spez")))
        assertEquals(RedditRoute.Search("dartlang", null), RedditRoutes.parse(RedditRoutes.search("dartlang")))
        assertEquals(RedditRoute.Search("dartlang", "null safety"), RedditRoutes.parse(RedditRoutes.search("dartlang", "null safety")))
        assertEquals(RedditRoute.Saved, RedditRoutes.parse(RedditRoutes.SAVED))
        assertEquals(RedditRoute.SignIn, RedditRoutes.parse(RedditRoutes.SIGN_IN))
        assertNull(RedditRoutes.parse("thread"))
        assertNull(RedditRoutes.parse("profile?actor=x"))
    }
}
