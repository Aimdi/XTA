package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.networks.RssEnclosure
import com.aimdi.xtanative.networks.RssItem
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RssTimelineCopyTest {
    private val item = RssItem(
        id = "https://a.example/feed#1",
        title = "One",
        feedId = "https://a.example/feed",
        feedTitle = "A",
        link = "https://a.example/1",
        excerpt = "Short",
        bodyHtml = "<p>Long</p>",
        publishedAt = Instant.parse("2026-11-01T10:00:00Z"),
        author = "Ann",
        imageUrl = "https://a.example/1.jpg",
        categories = listOf("News"),
        enclosures = listOf(RssEnclosure("https://a.example/1.mp3", "audio/mpeg", 1234)),
    )

    @Test
    fun theTimelineReadsBackAsItWasKept() {
        val copy = RssTimelineCopy(listOf(item, item.copy(id = "2", publishedAt = null, enclosures = emptyList())), Instant.parse("2026-11-01T11:00:00Z"), setOf("https://a.example/feed"))

        assertEquals(copy, RssTimelineCopies.decode(RssTimelineCopies.encode(copy)))
    }

    @Test
    fun onlyTheNewestAreKeptAndWhatDoesNotReadIsNothing() {
        val many = (1..RssTimelineCopies.KEPT + 5).map { item.copy(id = "$it") }
        val kept = RssTimelineCopies.decode(RssTimelineCopies.encode(RssTimelineCopy(many, Instant.EPOCH, emptySet())))

        assertEquals(many.take(RssTimelineCopies.KEPT).map { it.id }, kept?.items?.map { it.id })
        assertNull(RssTimelineCopies.decode(null))
        assertNull(RssTimelineCopies.decode("[1, 2]"))
        assertEquals(emptyList(), RssTimelineCopies.decode("""{"fetchedAt":"2026-11-01T11:00:00Z","items":[{"title":"no id"}]}""")?.items)
    }
}
