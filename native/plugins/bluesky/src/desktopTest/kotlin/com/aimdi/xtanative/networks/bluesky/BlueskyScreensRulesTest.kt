package com.aimdi.xtanative.networks.bluesky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules XTA's Bluesky screens follow that are not the AppView's: quotes' order, the media grid, the search. */
class BlueskyScreensRulesTest {
    private val old = bluePost("old", likes = 9, publishedAt = at("2026-01-01T00:00:00Z"))
    private val new = bluePost("new", likes = 1, publishedAt = at("2026-02-01T00:00:00Z"))
    private val undated = bluePost("undated", likes = 5)

    @Test
    fun quotesSortOnTheDevice() {
        val quotes = listOf(old, undated, new)
        assertEquals(listOf("new", "old", "undated"), BlueskyQuoteSort.RECENT.sorted(quotes).map { it.text })
        assertEquals(listOf("old", "new", "undated"), BlueskyQuoteSort.OLDEST.sorted(quotes).map { it.text })
        assertEquals(listOf("old", "undated", "new"), BlueskyQuoteSort.MOST_LIKED.sorted(quotes).map { it.text })
        assertFalse(BlueskyQuoteSort.offered(listOf(old)))
        assertTrue(BlueskyQuoteSort.offered(quotes))
    }

    @Test
    fun theMediaGridHasATileAPostWithMedia() {
        val picture = BlueskyMedia("https://cdn.example/p.jpg", alt = " A cat ")
        val video = BlueskyMedia("https://cdn.example/poster.jpg", isVideo = true, videoUrl = "https://video.example/v.m3u8")
        val posts = listOf(
            bluePost("text only"),
            bluePost("photo", media = listOf(picture)),
            bluePost("video", media = listOf(video, picture)),
            bluePost("labelled", media = listOf(picture), labels = listOf("nudity")),
        )
        val rows = BlueskyMediaGrid.rows(posts, columns = 2)
        assertEquals(listOf(listOf("photo", "video"), listOf("labelled")), rows.map { row -> row.map { it.post.text } })
        val (photo, clip) = rows.first()
        assertEquals("A cat", photo.alt)
        assertFalse(photo.opensPost)
        assertTrue(clip.opensPost && clip.isVideo && clip.many)
        val labelled = rows.last().single()
        assertTrue(labelled.covered && labelled.opensPost)
        assertNull(labelled.alt, "what is covered says nothing of its picture")
        assertEquals(listOf(2, 2, 3, 5), listOf(100f, 320f, 450f, 1200f).map(BlueskyMediaGrid::columns))
    }

    @Test
    fun aQueryOpensWhereXtasSearchOpensIt() {
        assertEquals(BlueskySearchKind.POSTS, BlueskySearchRules.kindFor("https://bsky.app/profile/maya.bsky.social/post/3k"))
        assertEquals(BlueskySearchKind.POSTS, BlueskySearchRules.kindFor(" #kotlin "))
        assertEquals(BlueskySearchKind.PEOPLE, BlueskySearchRules.kindFor("kotlin news"))
        assertEquals(BlueskySearchKind.PEOPLE, BlueskySearchRules.kindFor("#two #tags"))
    }

    @Test
    fun filtersTakeAnAccountAndATag() {
        assertEquals("", BlueskySearchRules.author("  "))
        assertEquals("maya.bsky.social", BlueskySearchRules.author("@Maya.bsky.social"))
        assertEquals("did:plc:maya", BlueskySearchRules.author("https://bsky.app/profile/did:plc:maya"))
        assertNull(BlueskySearchRules.author("not a handle"))
        assertEquals("kotlin", BlueskySearchRules.tag(" ##kotlin "))
    }

    @Test
    fun recentSearchesKeepTheNewestFirstOnce() {
        val history = (1..25).map { "q$it" }
        val next = BlueskySearchRules.remembered(history, " q3 ")
        assertEquals("q3", next.first())
        assertEquals(1, next.count { it == "q3" })
        assertEquals(BlueskySearchRules.HISTORY_CAP, next.size)
        assertEquals(history, BlueskySearchRules.remembered(history, "  "))
    }
}
