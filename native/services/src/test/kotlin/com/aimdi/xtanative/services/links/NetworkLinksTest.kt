package com.aimdi.xtanative.services.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkLinksTest {
    private val mastodonHosts = NetworkLinks.mastodonHosts(NetworkLinks.MASTODON_DEFAULT_INSTANCES)

    @Test
    fun blueskyProfilesPostsAndDidsAreRead() {
        assertEquals(NetworkLink.BlueskyProfile("alice.bsky.social"), NetworkLinks.bluesky("https://bsky.app/profile/alice.bsky.social"))
        val post = NetworkLinks.bluesky("https://www.bsky.app/profile/alice.bsky.social/post/3kabc") as NetworkLink.BlueskyPost
        assertEquals("3kabc", post.rkey)
        assertEquals("at://alice.bsky.social/app.bsky.feed.post/3kabc", post.atUri)
        assertEquals(NetworkLink.BlueskyProfile("did:plc:abcdefg"), NetworkLinks.bluesky("https://bsky.app/profile/did:plc:abcdefg"))
    }

    @Test
    fun threadsProfilesPostsAndShortLinksAreRead() {
        assertEquals(NetworkLink.ThreadsProfile("zuck"), NetworkLinks.threads("https://www.threads.net/@zuck"))
        val post = NetworkLinks.threads("https://www.threads.com/@zuck/post/Dabc123") as NetworkLink.ThreadsPost
        assertEquals("zuck", post.handle)
        assertEquals("https://www.threads.com/@zuck/post/Dabc123", post.url)
        assertEquals(NetworkLink.ThreadsPost("https://www.threads.com/t/Dabc123", ""), NetworkLinks.threads("https://www.threads.net/t/Dabc123"))
        assertNull(NetworkLinks.threads("https://www.threads.net/t/"))
    }

    @Test
    fun instagramProfilesOpenAndItsPostsDoNot() {
        assertEquals(NetworkLink.InstagramProfile("nasa"), NetworkLinks.instagram("https://www.instagram.com/nasa/"))
        listOf("p/AbC123/", "reel/AbC123/", "explore/").forEach { assertNull(NetworkLinks.instagram("https://www.instagram.com/$it"), it) }
    }

    @Test
    fun tiktokProfilesAndVideosOpenAndShortLinksDoNot() {
        assertEquals(NetworkLink.TikTokProfile("nasa"), NetworkLinks.tiktok("https://www.tiktok.com/@nasa"))
        assertEquals(NetworkLink.TikTokVideo("1234567890", "nasa"), NetworkLinks.tiktok("https://www.tiktok.com/@nasa/video/1234567890"))
        assertNull(NetworkLinks.tiktok("https://vm.tiktok.com/ZMabc/"))
        assertNull(NetworkLinks.tiktok("https://www.tiktok.com/foryou"))
    }

    @Test
    fun redditSubredditsThreadsUsersAndShortLinksAreRead() {
        assertEquals(NetworkLink.RedditSubreddit("dartlang"), NetworkLinks.reddit("https://www.reddit.com/r/dartlang"))
        assertEquals(
            NetworkLink.RedditThread("abc123", "dartlang", "/r/dartlang/comments/abc123/", "hello world"),
            NetworkLinks.reddit("https://old.reddit.com/r/dartlang/comments/abc123/hello_world"),
        )
        assertEquals(NetworkLink.RedditUser("spez"), NetworkLinks.reddit("https://www.reddit.com/u/spez"))
        assertEquals(NetworkLink.RedditThread("abc123", "", "/comments/abc123/"), NetworkLinks.reddit("https://redd.it/abc123"))
        assertNull(NetworkLinks.reddit("https://i.redd.it/abc.jpg"))
        assertNull(NetworkLinks.reddit("https://v.redd.it/abc"))
    }

    @Test
    fun mastodonLinksOpenOnlyOnKnownInstances() {
        val links = NetworkLinks(mastodonHosts)
        assertEquals(NetworkLink.MastodonProfile("gargron@mastodon.social"), links.mastodon("https://mastodon.social/@gargron"))
        assertEquals("123456", (links.mastodon("https://mastodon.social/@gargron/123456") as NetworkLink.MastodonStatus).statusId)
        assertEquals(
            NetworkLink.MastodonStatus("gargron@mas.to", "99", "https://mas.to/users/gargron/statuses/99"),
            links.mastodon("https://mas.to/users/gargron/statuses/99"),
        )
        assertNull(links.mastodon("https://medium.com/@someone/a-story"))
        assertNull(NetworkLinks().mastodon("https://mastodon.social/@gargron"))
    }

    @Test
    fun hackerNewsStoriesAndUsersOpenAndTheFrontPageDoesNot() {
        assertEquals(NetworkLink.HnStory(8863), NetworkLinks.hackerNews("https://news.ycombinator.com/item?id=8863"))
        assertEquals(NetworkLink.HnUser("pg"), NetworkLinks.hackerNews("https://www.news.ycombinator.com/user?id=pg"))
        assertNull(NetworkLinks.hackerNews("https://news.ycombinator.com/"))
        assertNull(NetworkLinks.hackerNews("https://example.com/item?id=1"))
    }

    @Test
    fun networksAreTriedInXtasOrder() {
        val links = NetworkLinks(mastodonHosts)
        assertEquals(NetworkLink.BlueskyProfile("alice.bsky.social"), links.parse("https://bsky.app/profile/alice.bsky.social"))
        assertEquals(NetworkLink.MastodonProfile("gargron@mastodon.social"), links.parse("https://mastodon.social/@gargron"))
        assertNull(links.parse("https://example.com/"))
    }

    @Test
    fun pixivAndBooruLinksGoToTheirOwnReaders() {
        val asked = ArrayList<String>()
        val links = NetworkLinks(pixiv = { asked += it; "artworks" in it || it.startsWith("pixiv://") }, booru = { "/posts/" in it })
        assertEquals(NetworkLink.Claimed("pixiv", "https://www.pixiv.net/artworks/123"), links.parse("https://www.pixiv.net/artworks/123"))
        assertEquals(NetworkLink.Claimed("pixiv", "pixiv://illusts/123"), links.parse("pixiv://illusts/123"))
        assertNull(links.parse("https://www.pixiv.net/en/"))
        // Another host never reaches Pixiv's reader, whatever its path.
        assertNull(links.parse("https://example.com/artworks/123"))
        assertEquals(listOf("https://www.pixiv.net/artworks/123", "pixiv://illusts/123", "https://www.pixiv.net/en/"), asked)
        assertEquals(NetworkLink.Claimed("booru", "https://danbooru.donmai.us/posts/1"), links.parse("https://danbooru.donmai.us/posts/1"))
    }
}
