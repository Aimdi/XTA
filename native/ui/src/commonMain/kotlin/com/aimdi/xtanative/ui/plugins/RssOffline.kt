package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.offline.OfflineArticle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An item the RSS reader shows (an RSS plugin's, or a web source's) as the
 * app's offline library keeps it, as XTA's OfflineArticle.rss does, and the
 * id the reading journal keeps its place under.
 */
object RssOffline {
    /** The library's and the journal's id of [item]: `rss:<feed id>:<item id>`, as XTA names it. */
    fun idOf(item: RssItem): String = "rss:${item.feedId}:${item.id}"

    fun articleOf(item: RssItem): OfflineArticle = OfflineArticle(
        id = idOf(item),
        title = item.title,
        source = item.feedTitle,
        bodyHtml = item.bodyHtml.orEmpty(),
        url = item.link,
        payload = buildJsonObject {
            put("kind", KIND)
            put("id", item.id)
            put("title", item.title)
            put("feedId", item.feedId)
            put("feedTitle", item.feedTitle)
            put("link", item.link)
            put("excerpt", item.excerpt)
            put("publishedAt", item.publishedAt?.let(XtaTime::iso))
            put("author", item.author)
            put("imageUrl", item.imageUrl)
            put("categories", JsonArray(item.categories.map(::JsonPrimitive)))
        },
    )

    /** The item [article] keeps, or null when it is no RSS item (a Substack post's copy). */
    fun itemOf(article: OfflineArticle): RssItem? {
        if (article.kind != KIND) return null
        val payload = J(article.payload)
        return RssItem(
            id = payload["id"].string.orEmpty(),
            title = article.title,
            feedId = payload["feedId"].string.orEmpty(),
            feedTitle = article.source,
            link = article.url,
            bodyHtml = article.bodyHtml,
            excerpt = payload["excerpt"].string,
            author = payload["author"].string,
            imageUrl = payload["imageUrl"].string,
            publishedAt = XtaTime.parse(payload["publishedAt"].string),
            categories = payload["categories"].list.mapNotNull { it.string },
        )
    }

    private const val KIND = "rss"
}
