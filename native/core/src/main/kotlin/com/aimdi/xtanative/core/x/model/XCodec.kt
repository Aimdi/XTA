package com.aimdi.xtanative.core.x.model

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Posts written to disk and read back, so the feed opens with what it showed
 * last before X has answered. Its own small format, versioned: a file of an
 * older shape is dropped rather than misread.
 */
object XCodec {
    private const val VERSION = 1

    fun encode(threads: List<XThread>): String = buildJsonObject {
        put("v", VERSION)
        put("threads", JsonArray(threads.map(::thread)))
    }.toString()

    /** The threads in [text]; empty when it is unreadable or of another version. */
    fun decode(text: String): List<XThread> {
        val root = J.parse(text)
        if (root["v"].int != VERSION) return emptyList()
        return root["threads"].list.mapNotNull(::thread)
    }

    private fun thread(thread: XThread) = buildJsonObject {
        put("id", thread.id)
        put("posts", JsonArray(thread.posts.map(::post)))
    }

    private fun thread(json: J): XThread? {
        val id = json["id"].string ?: return null
        val posts = json["posts"].list.mapNotNull(::post)
        return if (posts.isEmpty()) null else XThread(id, posts)
    }

    private fun post(post: XPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("author", user(post.author))
        post.createdAt?.let { put("at", it.toEpochMilli()) }
        put("text", post.text)
        put("ds", post.displayStart)
        post.displayEnd?.let { put("de", it) }
        put("entities", JsonArray(post.entities.map(::entity)))
        put("media", JsonArray(post.media.map(::media)))
        post.quoted?.let { put("quoted", post(it)) }
        post.reposted?.let { put("reposted", post(it)) }
        put("replies", post.replyCount)
        put("reposts", post.repostCount)
        put("quotes", post.quoteCount)
        put("likes", post.likeCount)
        put("bookmarks", post.bookmarkCount)
        post.viewCount?.let { put("views", it) }
        post.replyToScreenName?.let { put("replyTo", it) }
        post.conversationId?.let { put("conversation", it) }
    }

    private fun post(json: J): XPost? {
        val id = json["id"].string ?: return null
        val author = user(json["author"]) ?: return null
        return XPost(
            id = id,
            author = author,
            createdAt = json["at"].long?.let(Instant::ofEpochMilli),
            text = json["text"].string.orEmpty(),
            displayStart = json["ds"].int ?: 0,
            displayEnd = json["de"].int,
            entities = json["entities"].list.mapNotNull(::entity),
            media = json["media"].list.mapNotNull(::media),
            quoted = json["quoted"].takeIf { it.exists }?.let(::post),
            reposted = json["reposted"].takeIf { it.exists }?.let(::post),
            replyCount = json["replies"].long ?: 0,
            repostCount = json["reposts"].long ?: 0,
            quoteCount = json["quotes"].long ?: 0,
            likeCount = json["likes"].long ?: 0,
            bookmarkCount = json["bookmarks"].long ?: 0,
            viewCount = json["views"].long,
            replyToScreenName = json["replyTo"].string,
            conversationId = json["conversation"].string,
        )
    }

    private fun user(user: XUser) = buildJsonObject {
        put("id", user.id)
        put("sn", user.screenName)
        put("name", user.name)
        user.avatarUrl?.let { put("avatar", it) }
        put("verified", user.verified)
        put("protected", user.protected)
    }

    private fun user(json: J): XUser? {
        val id = json["id"].string ?: return null
        val screenName = json["sn"].string ?: return null
        return XUser(
            id = id,
            screenName = screenName,
            name = json["name"].string ?: screenName,
            avatarUrl = json["avatar"].string,
            verified = json["verified"].boolean ?: false,
            protected = json["protected"].boolean ?: false,
        )
    }

    private fun entity(entity: XEntity): JsonElement = buildJsonObject {
        put("s", entity.start)
        put("e", entity.end)
        when (entity) {
            is XEntity.Link -> { put("k", "link"); put("url", entity.url); put("expanded", entity.expanded); put("display", entity.display) }
            is XEntity.Mention -> { put("k", "mention"); put("value", entity.screenName) }
            is XEntity.Hashtag -> { put("k", "hashtag"); put("value", entity.tag) }
            is XEntity.Cashtag -> { put("k", "cashtag"); put("value", entity.symbol) }
        }
    }

    private fun entity(json: J): XEntity? {
        val start = json["s"].int ?: return null
        val end = json["e"].int ?: return null
        val value = json["value"].string
        return when (json["k"].string) {
            "link" -> XEntity.Link(start, end, json["url"].string ?: return null, json["expanded"].string.orEmpty(), json["display"].string.orEmpty())
            "mention" -> XEntity.Mention(start, end, value ?: return null)
            "hashtag" -> XEntity.Hashtag(start, end, value ?: return null)
            "cashtag" -> XEntity.Cashtag(start, end, value ?: return null)
            else -> null
        }
    }

    private fun media(media: XMedia) = buildJsonObject {
        put("kind", media.kind.name)
        put("url", media.url)
        media.width?.let { put("w", it) }
        media.height?.let { put("h", it) }
        media.alt?.let { put("alt", it) }
        put(
            "variants",
            buildJsonArray {
                media.variants.forEach { variant ->
                    add(
                        buildJsonObject {
                            put("url", variant.url)
                            variant.bitrate?.let { put("bitrate", it) }
                            put("type", variant.contentType)
                        },
                    )
                }
            },
        )
        media.durationMs?.let { put("ms", it) }
        media.shortUrl?.let { put("short", it) }
    }

    private fun media(json: J): XMedia? {
        val url = json["url"].string ?: return null
        val kind = runCatching { XMedia.Kind.valueOf(json["kind"].string.orEmpty()) }.getOrNull() ?: return null
        return XMedia(
            kind = kind,
            url = url,
            width = json["w"].int,
            height = json["h"].int,
            alt = json["alt"].string,
            variants = json["variants"].list.mapNotNull { variant ->
                XVideoVariant(variant["url"].string ?: return@mapNotNull null, variant["bitrate"].int, variant["type"].string.orEmpty())
            },
            durationMs = json["ms"].long,
            shortUrl = json["short"].string,
        )
    }
}
