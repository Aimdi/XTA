package com.aimdi.xtanative.networks.bluesky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlueskyTimelineTest {
    @Test
    fun aRepostsNewActivitySortsAheadOfNewerOriginals() {
        val old = bluePost("old", publishedAt = at("2026-01-01T00:00:00Z"), repostedByHandle = "bob.test", repostedAt = at("2026-09-20T00:00:00Z"))
        val recent = bluePost("recent", publishedAt = at("2026-09-19T00:00:00Z"))
        assertSame(old, BlueskyTimeline.stabilize(listOf(recent, old)).first())
    }

    @Test
    fun theOriginalWinsADuplicateRepostInEitherOrder() {
        val original = bluePost("same", publishedAt = at("2026-01-01T00:00:00Z"))
        val repost = bluePost("same", publishedAt = at("2026-01-01T00:00:00Z"), repostedByHandle = "bob.test", repostedAt = at("2026-09-20T00:00:00Z"))
        listOf(listOf(original, repost), listOf(repost, original)).forEach { rows ->
            assertFalse(BlueskyTimeline.stabilize(rows).single().isRepost)
        }
    }

    @Test
    fun theNewestRepostWinsWhileTheFirstSeenOrderStays() {
        val first = bluePost("other")
        val old = bluePost("same", repostedByHandle = "old.test", repostedAt = at("2026-09-19T00:00:00Z"))
        val fresh = bluePost("same", repostedByHandle = "new.test", repostedAt = at("2026-09-20T00:00:00Z"))
        val rows = BlueskyTimeline.dedupe(listOf(first, old, fresh))
        assertSame(first, rows.first())
        assertSame(fresh, rows.last())
    }

    @Test
    fun equalTimesAreOrderedByUriAndUndatedPostsGoLast() {
        val noon = at("2026-08-01T12:00:00Z")
        val rows = BlueskyTimeline.stabilize(listOf(bluePost("undated"), bluePost("b", publishedAt = noon), bluePost("a", publishedAt = noon)))
        assertEquals(listOf("a", "b", "undated"), rows.map { it.text })
    }

    @Test
    fun aFeedWithoutRepliesLeavesRepliesOutButKeepsReposts() {
        val reply = bluePost("reply", parent = "root")
        val repostedReply = reply.copy(repostedByHandle = "bob.test")
        assertFalse(BlueskyTimeline.takes(reply, replies = false, reposts = true))
        assertTrue(BlueskyTimeline.takes(repostedReply, replies = false, reposts = true), "a repost follows the repost setting")
        assertFalse(BlueskyTimeline.takes(repostedReply, replies = true, reposts = false))
        assertTrue(BlueskyTimeline.takes(bluePost("own"), replies = false, reposts = false))
    }

    @Test
    fun repostedAuthorsComeFirstThenQuotedOnesAndFollowedOnesNever() {
        val people = BlueskyTimeline.peopleToFollow(
            listOf(
                bluePost("1", did = "did:plc:alice", handle = "alice.bsky.social"),
                bluePost("2", did = "did:plc:bob", handle = "Bob.bsky.social", authorName = "Bob", repostedByHandle = "alice.bsky.social"),
                bluePost("3", did = "did:plc:alice", handle = "alice.bsky.social", quote = bluePost("4", did = "did:plc:cara", handle = "cara.bsky.social", authorName = "Cara")),
                bluePost("5", did = "did:plc:bob", handle = "bob.bsky.social", authorName = "Bob again"),
            ),
            alreadyFollows = { it == "alice.bsky.social" },
        )
        assertEquals(listOf("bob.bsky.social", "cara.bsky.social"), people.map { it.handle })
        assertEquals(listOf(true, false), people.map { it.fromRepost })
        assertEquals(listOf("Bob", "Cara"), people.map { it.name })
        val many = (1..20).map { bluePost("$it", did = "did:plc:p", handle = "p$it.bsky.social") }
        assertEquals(12, BlueskyTimeline.peopleToFollow(many, alreadyFollows = { false }).size)
    }

    @Test
    fun profileTabsShowTheirOwnPostsAndPostsStartsWithThePinOnce() {
        val pin = bluePost("pin")
        val posts = listOf(bluePost("one"), pin, bluePost("reply", parent = "one"), bluePost("pic", media = listOf(BlueskyMedia("https://img.example/p.jpg"))))
        assertEquals(listOf("reply"), posts.filter(BlueskyProfileTab.REPLIES::shows).map { it.text })
        assertEquals(listOf("pic"), posts.filter(BlueskyProfileTab.MEDIA::shows).map { it.text })
        assertEquals(listOf("pin", "one", "reply", "pic"), BlueskyProfileTab.POSTS.withPin(posts, pin).map { it.text })
        assertEquals(posts, BlueskyProfileTab.REPLIES.withPin(posts, pin), "only Posts shows the pin")
        assertEquals(posts, BlueskyProfileTab.POSTS.withPin(posts, null))
        assertEquals(
            listOf(Bluesky.AUTHOR_FEED_POSTS, Bluesky.AUTHOR_FEED_REPLIES, Bluesky.AUTHOR_FEED_MEDIA),
            BlueskyProfileTab.entries.map { it.filter },
        )
    }

    @Test
    fun aRepeatedOrBlankCursorEndsPaging() {
        assertEquals("next", BlueskyPaging.next(" next ", emptySet()))
        assertNull(BlueskyPaging.next("one", setOf("one", "two")), "a cursor that comes round again")
        assertNull(BlueskyPaging.next("  ", emptySet()))
        assertNull(BlueskyPaging.next(null, emptySet()))
    }

    @Test
    fun mergedPagesKeepEachPostAndEachPersonOnce() {
        val posts = BlueskyPaging.merge(listOf(bluePost("root")), listOf(bluePost("root"), bluePost("a")), BlueskyPost::uri)
        assertEquals(listOf("root", "a"), posts.map { it.text })
        val maya = BlueskyProfile(did = "", handle = "Maya.bsky.social", displayName = "Maya")
        val people = BlueskyPaging.merge(listOf(maya), listOf(maya.copy(handle = "maya.bsky.social"), BlueskyProfile("", "", "nobody")), BlueskyProfile::identity)
        assertEquals(listOf(maya), people, "the same handle in another case, and no one at all, are dropped")
    }

    @Test
    fun theReaderFilterTellsPicturesVideosCardsAndLinksApart() {
        val posts = listOf(
            bluePost("plain"),
            bluePost("image", media = listOf(BlueskyMedia("https://media.example/photo.jpg"))),
            bluePost("video", media = listOf(BlueskyMedia("https://media.example/poster.jpg", isVideo = true))),
            bluePost("card", linkCard = BlueskyLinkCard("https://article.example", "A field guide")),
            bluePost("link", facets = listOf(BlueskyFacet(0, 4, BlueskyFacet.Kind.LINK, "https://article.example"))),
        )
        fun shown(content: BlueskyReaderFilter.Content) = BlueskyReaderFilter(content = content).apply(posts).map { it.text }
        assertEquals(listOf("image"), shown(BlueskyReaderFilter.Content.IMAGES))
        assertEquals(listOf("video"), shown(BlueskyReaderFilter.Content.VIDEOS))
        assertEquals(listOf("card", "link"), shown(BlueskyReaderFilter.Content.LINKS))
        assertEquals(listOf("plain", "image", "video", "card", "link"), shown(BlueskyReaderFilter.Content.ALL))
        assertFalse(BlueskyReaderFilter().filtered)
        assertTrue(BlueskyReaderFilter(content = BlueskyReaderFilter.Content.LINKS).filtered)
    }

    @Test
    fun theReaderQueryNeedsEveryWordAcrossTextAuthorHandleAndCardTitle() {
        val posts = listOf(
            bluePost("match", text = "Quiet coast", authorName = "MAYA", linkCard = BlueskyLinkCard("https://example.org", "Camera journal")),
            bluePost("partial", text = "Camera coast", authorName = "Elsewhere", handle = "other.example"),
        )
        fun shown(query: String) = BlueskyReaderFilter(query = query).apply(posts).map { it.uri.substringAfterLast('/') }
        assertEquals(listOf("match"), shown("  MAYA  camera COAST "))
        assertEquals(listOf("partial"), shown("other.example"))
        assertTrue(shown("missing").isEmpty())
    }

    @Test
    fun replyAndRepostSwitchesComposeAndADuplicateShowsOnce() {
        val original = bluePost("original")
        val posts = listOf(original, bluePost("reply", isReply = true), bluePost("repost", repostedByDid = "did:plc:reader"), original)
        fun shown(filter: BlueskyReaderFilter) = filter.apply(posts).map { it.text }
        assertEquals(listOf("original", "repost"), shown(BlueskyReaderFilter(hideReplies = true)))
        assertEquals(listOf("original", "reply"), shown(BlueskyReaderFilter(hideReposts = true)))
        assertEquals(listOf("original"), shown(BlueskyReaderFilter(hideReplies = true, hideReposts = true)))
    }

    @Test
    fun theReaderOrdersByActivityKeepsTiesAndPutsUnknownDatesLast() {
        val posts = listOf(
            bluePost("unknown"),
            bluePost("older", publishedAt = at("2025-01-01T00:00:00Z")),
            bluePost("tie-a", publishedAt = at("2026-01-01T00:00:00Z")),
            bluePost("reposted", publishedAt = at("2024-01-01T00:00:00Z"), repostedByHandle = "bob.test", repostedAt = at("2026-02-01T00:00:00Z")),
            bluePost("tie-b", publishedAt = at("2026-01-01T00:00:00Z")),
        )
        fun shown(order: BlueskyReaderFilter.Order) = BlueskyReaderFilter(order = order).apply(posts).map { it.text }
        assertEquals(listOf("reposted", "tie-a", "tie-b", "older", "unknown"), shown(BlueskyReaderFilter.Order.NEWEST))
        assertEquals(listOf("older", "tie-a", "tie-b", "reposted", "unknown"), shown(BlueskyReaderFilter.Order.OLDEST))
        assertEquals(listOf("unknown", "older", "tie-a", "reposted", "tie-b"), shown(BlueskyReaderFilter.Order.FEED))
    }
}
