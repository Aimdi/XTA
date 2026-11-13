package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A plugin's post as a mixed timeline keeps it on the device (XTA's feed
 * snapshot): every part the shared row draws, so the post shows as it was
 * shown and its plugin still opens it by its id and link. Not every plugin
 * reads its own records back ([com.aimdi.xtanative.ui.plugins.XtaPlugin.archived]),
 * nor do those records keep a post's time or counts: this keeps the row itself.
 */
object NetworkPostCodec {
    private const val VERSION = 1

    fun encode(post: NetworkPost): JsonElement = buildJsonObject {
        put("v", VERSION)
        fields(post)
    }

    /** The post [json] holds; null for a record it cannot read. */
    fun decode(json: JsonElement): NetworkPost? = runCatching {
        val root = J(json)
        if (root["v"].int != VERSION) null else post(root)
    }.getOrNull()

    private fun JsonObjectBuilder.fields(post: NetworkPost) {
        put("id", post.id)
        put("authorId", post.authorId)
        put("authorName", post.authorName)
        put("authorHandle", post.authorHandle)
        post.avatarUrl?.let { put("avatarUrl", it) }
        post.createdAt?.let { put("createdAt", it.toEpochMilli()) }
        put("text", JsonArray(post.text.map(::run)))
        put("media", JsonArray(post.media.map(::media)))
        post.card?.let { card ->
            put(
                "card",
                buildJsonObject {
                    put("url", card.url)
                    card.title?.let { put("title", it) }
                    card.description?.let { put("description", it) }
                    card.imageUrl?.let { put("imageUrl", it) }
                },
            )
        }
        post.quoted?.let { quoted -> put("quoted", buildJsonObject { fields(quoted) }) }
        put("replies", post.replies)
        put("reposts", post.reposts)
        put("quotes", post.quotes)
        put("likes", post.likes)
        post.views?.let { put("views", it) }
        post.repostedBy?.let { put("repostedBy", it) }
        if (post.pinned) put("pinned", true)
        post.replyingTo?.let { put("replyingTo", it) }
        post.warning?.let { put("warning", it) }
        post.url?.let { put("url", it) }
        if (post.coversText) put("coversText", true)
        post.title?.let { put("title", it) }
    }

    private fun run(run: TextRun): JsonObject = buildJsonObject {
        put("text", run.text)
        when (run) {
            is TextRun.Plain -> put("kind", "plain")
            is TextRun.Link -> {
                put("kind", "link")
                put("url", run.url)
            }
            is TextRun.Mention -> {
                put("kind", "mention")
                put("who", run.who)
            }
            is TextRun.Tag -> {
                put("kind", "tag")
                put("query", run.query)
            }
        }
    }

    private fun media(item: MediaItem): JsonObject = buildJsonObject {
        put("imageUrl", item.imageUrl)
        put("kind", item.kind.name)
        item.videoUrl?.let { put("videoUrl", it) }
        item.aspectRatio?.let { put("aspectRatio", it) }
        item.alt?.let { put("alt", it) }
        item.durationMs?.let { put("durationMs", it) }
        item.thumbUrl?.let { put("thumbUrl", it) }
        item.inlineVideoUrl?.let { put("inlineVideoUrl", it) }
        item.webUrl?.let { put("webUrl", it) }
        item.downloadUrl?.let { put("downloadUrl", it) }
        item.shareUrl?.let { put("shareUrl", it) }
    }

    private fun post(root: J): NetworkPost = NetworkPost(
        id = root["id"].string!!,
        authorId = root["authorId"].string.orEmpty(),
        authorName = root["authorName"].string.orEmpty(),
        authorHandle = root["authorHandle"].string.orEmpty(),
        avatarUrl = root["avatarUrl"].string,
        createdAt = root["createdAt"].long?.let(Instant::ofEpochMilli),
        text = root["text"].list.mapNotNull(::runOf),
        media = root["media"].list.mapNotNull(::mediaOf),
        card = root["card"].takeIf { it.exists }?.let { card ->
            card["url"].string?.let { LinkCard(it, card["title"].string, card["description"].string, card["imageUrl"].string) }
        },
        quoted = root["quoted"].takeIf { it.exists }?.let { quoted -> runCatching { post(quoted) }.getOrNull() },
        replies = root["replies"].long ?: 0,
        reposts = root["reposts"].long ?: 0,
        quotes = root["quotes"].long ?: 0,
        likes = root["likes"].long ?: 0,
        views = root["views"].long,
        repostedBy = root["repostedBy"].string,
        pinned = root["pinned"].boolean == true,
        replyingTo = root["replyingTo"].string,
        warning = root["warning"].string,
        url = root["url"].string,
        coversText = root["coversText"].boolean == true,
        title = root["title"].string,
    )

    private fun runOf(run: J): TextRun? {
        val text = run["text"].string ?: return null
        return when (run["kind"].string) {
            "link" -> run["url"].string?.let { TextRun.Link(text, it) }
            "mention" -> run["who"].string?.let { TextRun.Mention(text, it) }
            "tag" -> run["query"].string?.let { TextRun.Tag(text, it) }
            else -> TextRun.Plain(text)
        } ?: TextRun.Plain(text)
    }

    private fun mediaOf(item: J): MediaItem? {
        val image = item["imageUrl"].string ?: return null
        return MediaItem(
            imageUrl = image,
            kind = MediaItem.Kind.entries.firstOrNull { it.name == item["kind"].string } ?: MediaItem.Kind.PHOTO,
            videoUrl = item["videoUrl"].string,
            aspectRatio = item["aspectRatio"].double?.toFloat(),
            alt = item["alt"].string,
            durationMs = item["durationMs"].long,
            thumbUrl = item["thumbUrl"].string,
            inlineVideoUrl = item["inlineVideoUrl"].string,
            webUrl = item["webUrl"].string,
            downloadUrl = item["downloadUrl"].string,
            shareUrl = item["shareUrl"].string,
        )
    }
}
