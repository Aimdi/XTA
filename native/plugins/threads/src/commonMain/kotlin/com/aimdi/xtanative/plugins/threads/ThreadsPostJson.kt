package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsVideoRendition
import com.aimdi.xtanative.store.backup.XtaTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A post as XTA keeps it where a post outlives its page (threads_models.dart's
 * `toJson` and `fromSnapshot`): the liked posts and the tab's restart
 * snapshot, in the preferences XTA writes them to, so both apps read each
 * other's. Its media are four lists side by side, every video rendition kept;
 * a quote is kept one level deep.
 */
internal object ThreadsPostJson {
    fun list(text: String): List<ThreadsPost> = J.parse(text).list.mapNotNull(::post)

    fun listText(posts: List<ThreadsPost>): String = JsonArray(posts.map(::json)).toString()

    /** The post [row] keeps; null without an id. */
    fun post(row: J): ThreadsPost? {
        if (row.raw !is JsonObject) return null
        val id = row["id"].string.orEmpty()
        if (id.isEmpty()) return null
        val handle = row["handle"].string.orEmpty()
        val replyTo = row["replyToHandle"].string
        return ThreadsPost(
            id = id,
            handle = handle,
            authorName = row["authorName"].string ?: handle,
            text = row["text"].string.orEmpty(),
            avatarUrl = row["avatarUrl"].string,
            media = media(row),
            publishedAt = XtaTime.parse(row["publishedAt"].string),
            url = row["url"].string,
            likeCount = count(row["likeCount"]),
            replyCount = count(row["replyCount"]),
            repostCount = count(row["repostCount"]),
            quoteCount = count(row["quoteCount"]),
            linkCard = linkCard(row["linkCard"]),
            quoted = row["quoted"].takeIf { it.raw is JsonObject }?.let(::post)?.copy(quoted = null),
            topicTag = row["topicTag"].string,
            fragments = row["fragments"].list.mapNotNull(::fragment),
            selfThreadCount = count(row["selfThreadCount"])?.toInt() ?: 0,
            repostedByHandle = row["repostedByHandle"].string,
            repostedByName = row["repostedByName"].string,
            isVerified = row["isVerified"].boolean ?: false,
            replyToHandle = replyTo,
            isReply = row["isReply"].boolean ?: !replyTo.isNullOrEmpty(),
        )
    }

    fun json(post: ThreadsPost): JsonObject = JsonObject(
        mapOf(
            "id" to text(post.id),
            "handle" to text(post.handle),
            "authorName" to text(post.authorName),
            "avatarUrl" to text(post.avatarUrl),
            "text" to text(post.text),
            "images" to JsonArray(post.media.map { text(it.url) }),
            "imageAspects" to JsonArray(post.media.map { media -> media.aspectRatio?.let(::JsonPrimitive) ?: JsonNull }),
            "imageAlts" to JsonArray(post.media.map { text(it.alt) }),
            "videoUrls" to JsonArray(post.media.map { text(it.videoUrl) }),
            "videoRenditions" to JsonArray(post.media.map { media -> JsonArray(media.renditions.map(::rendition)) }),
            "publishedAt" to text(post.publishedAt?.let(XtaTime::iso)),
            "url" to text(post.url),
            "likeCount" to number(post.likeCount),
            "replyCount" to number(post.replyCount),
            "repostCount" to number(post.repostCount),
            "quoteCount" to number(post.quoteCount),
            "linkCard" to (post.linkCard?.let(::card) ?: JsonNull),
            "quoted" to (post.quoted?.let { json(it.copy(quoted = null)) } ?: JsonNull),
            "topicTag" to text(post.topicTag),
            "fragments" to JsonArray(post.fragments.map(::fragment)),
            "selfThreadCount" to JsonPrimitive(post.selfThreadCount),
            "repostedByHandle" to text(post.repostedByHandle),
            "repostedByName" to text(post.repostedByName),
            "isVerified" to JsonPrimitive(post.isVerified),
            "replyToHandle" to text(post.replyToHandle),
            "isReply" to JsonPrimitive(post.isReply),
        ),
    )

    /**
     * The pictures and videos, from XTA's lists side by side. A post kept
     * before renditions were plays the one file it kept.
     */
    private fun media(row: J): List<ThreadsMedia> = row["images"].list.mapIndexedNotNull { index, image ->
        val url = image.string ?: return@mapIndexedNotNull null
        val kept = row["videoRenditions"][index].list.mapNotNull(::rendition)
        val single = row["videoUrls"][index].string?.takeIf { it.isNotEmpty() }?.let { listOf(ThreadsVideoRendition(it)) }.orEmpty()
        ThreadsMedia(url, row["imageAspects"][index].double, row["imageAlts"][index].string, kept.ifEmpty { single })
    }

    private fun rendition(rendition: ThreadsVideoRendition): JsonObject =
        JsonObject(mapOf("url" to text(rendition.url), "width" to number(rendition.width?.toLong()), "height" to number(rendition.height?.toLong())))

    private fun rendition(row: J): ThreadsVideoRendition? {
        val url = row["url"].string?.takeIf { it.isNotEmpty() } ?: return null
        return ThreadsVideoRendition(url, size(row["width"]), size(row["height"]))
    }

    private fun card(card: ThreadsLinkCard): JsonObject = JsonObject(
        mapOf(
            "url" to text(card.url),
            "title" to text(card.title),
            "description" to text(card.description),
            "imageUrl" to text(card.imageUrl),
            "providerName" to text(card.providerName),
        ),
    )

    private fun linkCard(row: J): ThreadsLinkCard? {
        val url = row["url"].string?.takeIf { it.isNotEmpty() } ?: return null
        return ThreadsLinkCard(url, row["title"].string, row["description"].string, row["imageUrl"].string, row["providerName"].string)
    }

    private fun fragment(fragment: ThreadsTextFragment): JsonObject =
        JsonObject(mapOf("kind" to text(fragment.kind.name.lowercase()), "text" to text(fragment.text), "target" to text(fragment.target)))

    private fun fragment(row: J): ThreadsTextFragment? {
        val kind = ThreadsTextFragment.Kind.entries.firstOrNull { it.name.lowercase() == row["kind"].string } ?: return null
        val words = row["text"].string ?: return null
        return ThreadsTextFragment(kind, words, row["target"].string)
    }

    /** A count as XTA's `_snapshotCount` reads one: any number, cut to its whole part. */
    private fun count(value: J): Long? = (value.raw as? JsonPrimitive)?.takeIf { !it.isString }?.let { value.double?.toLong() }

    private fun size(value: J): Int? = count(value)?.toInt()?.takeIf { it > 0 }

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun number(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
}
