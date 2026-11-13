package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.store.offline.OfflineArticle
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class RssOfflineTest {
    private val item = RssItem(
        id = "https://blog.example/feed::42",
        title = "A post",
        feedId = "https://blog.example/feed",
        feedTitle = "Blog",
        link = "https://blog.example/42",
        excerpt = "Short",
        bodyHtml = "<p>Long body</p>",
        publishedAt = Instant.parse("2026-08-04T09:30:00Z"),
        author = "Ann",
        imageUrl = "https://blog.example/42.png",
        categories = listOf("news"),
    )

    @Test
    fun anItemIsKeptAsXtaKeepsItAndComesBackWhole() {
        val article = RssOffline.articleOf(item)

        assertEquals("rss:https://blog.example/feed:https://blog.example/feed::42", article.id)
        assertEquals("Blog", article.source)
        assertEquals("rss", article.kind)
        assertEquals("2026-08-04T09:30:00.000Z", J(article.payload)["publishedAt"].string, "Dart's toIso8601String")
        assertEquals(item, RssOffline.itemOf(article))
    }

    @Test
    fun aCopyOfAnotherKindIsNoItem() {
        val substack = OfflineArticle("substack:1:2", "T", "S", "<p>x</p>", JsonObject(mapOf("kind" to JsonPrimitive("substack"))))
        assertNull(RssOffline.itemOf(substack))
    }

    @Test
    fun aWebSourcesArticleIsKeptUnderItsOwnFeed() {
        val reading = item.copy(id = "lobsters:abc", feedId = "websources:lobsters")
        assertEquals("rss:websources:lobsters:lobsters:abc", RssOffline.idOf(reading))
    }
}
