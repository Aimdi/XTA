package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A custom feed or list chosen to read, by its URI, with the name it was shown under. */
data class BlueskySelection(val uri: String, val name: String)

/**
 * Posts, custom feeds and lists as XTA keeps them on the device (a liked
 * post, a pinned feed or list): `BlueskyPost.toJson` and `fromSnapshot`,
 * key for key, so what one app wrote the other reads.
 */
object BlueskySnapshot {
    /** XTA reads a quote this deep, and no deeper. */
    private const val MAX_QUOTE_DEPTH = 4

    fun json(post: BlueskyPost): JsonObject = row(
        "uri" to post.uri,
        "cid" to post.cid,
        "handle" to post.handle,
        "did" to post.did,
        "authorName" to post.authorName,
        "avatarUrl" to post.avatarUrl,
        "text" to post.text,
        "images" to post.media.map { it.url },
        "imageAspects" to post.media.map { it.aspectRatio },
        "imageIsVideo" to post.media.map { it.isVideo },
        "imageAlts" to post.media.map { it.alt },
        "videoUrls" to post.media.map { it.videoUrl },
        "imageIsGif" to post.media.map { it.isGif },
        "facets" to JsonArray(post.facets.map(::facetJson)),
        "publishedAt" to post.publishedAt?.let(BlueskyTime::iso),
        "repostedAt" to post.repostedAt?.let(BlueskyTime::iso),
        "url" to post.url,
        "replyCount" to post.replyCount,
        "repostCount" to post.repostCount,
        "likeCount" to post.likeCount,
        "quoteCount" to post.quoteCount,
        "repostedByName" to post.repostedByName,
        "repostedByHandle" to post.repostedByHandle,
        "repostedByDid" to post.repostedByDid,
        "quotedPost" to post.quotedPost?.let(::json),
        "linkCard" to post.linkCard?.let { row("url" to it.url, "title" to it.title, "description" to it.description, "imageUrl" to it.imageUrl) },
        "replyToHandle" to post.replyToHandle,
        "isReply" to post.isReply,
        "replyToUri" to post.replyToUri,
        "labels" to post.labels,
    )

    /** A post XTA kept; null without an `at://` URI. A damaged field is left empty, never the whole post. */
    fun post(json: J, depth: Int = 0): BlueskyPost? {
        val uri = json["uri"].string?.takeIf { it.startsWith("at://") } ?: return null
        val handle = json["handle"].string.orEmpty()
        val replyToHandle = json["replyToHandle"].string
        val replyToUri = json["replyToUri"].string
        return BlueskyPost(
            uri = uri,
            cid = json["cid"].string.orEmpty(),
            handle = handle,
            did = json["did"].string.orEmpty(),
            authorName = json["authorName"].string ?: handle,
            avatarUrl = json["avatarUrl"].string,
            text = json["text"].string.orEmpty(),
            url = json["url"].string.orEmpty(),
            media = media(json),
            facets = json["facets"].list.mapNotNull(::facet),
            publishedAt = BlueskyTime.parse(json["publishedAt"].string),
            repostedAt = BlueskyTime.parse(json["repostedAt"].string),
            replyCount = count(json["replyCount"]),
            repostCount = count(json["repostCount"]),
            likeCount = count(json["likeCount"]),
            quoteCount = count(json["quoteCount"]),
            repostedByName = json["repostedByName"].string,
            repostedByHandle = json["repostedByHandle"].string,
            repostedByDid = json["repostedByDid"].string,
            quotedPost = if (depth < MAX_QUOTE_DEPTH) post(json["quotedPost"], depth + 1) else null,
            linkCard = json["linkCard"]["url"].string?.ifEmpty { null }?.let { url ->
                BlueskyLinkCard(url, json["linkCard"]["title"].string, json["linkCard"]["description"].string, json["linkCard"]["imageUrl"].string)
            },
            replyToHandle = replyToHandle,
            isReply = json["isReply"].boolean ?: (!replyToHandle.isNullOrEmpty() || !replyToUri.isNullOrEmpty()),
            replyToUri = replyToUri,
            labels = json["labels"].list.mapNotNull { it.string },
        )
    }

    /** The posts of a list XTA kept (its liked posts' preference); those without a URI left out. */
    fun posts(text: String): List<BlueskyPost> = J.parse(text).list.mapNotNull { post(it) }

    fun json(feed: BlueskyFeedGenerator): JsonObject = row(
        "uri" to feed.uri,
        "displayName" to feed.displayName,
        "description" to feed.description,
        "avatarUrl" to feed.avatarUrl,
        "creatorHandle" to feed.creatorHandle,
    )

    /** A custom feed XTA pinned; an unnamed one is called by its record key. Null without a URI. */
    fun feed(json: J): BlueskyFeedGenerator? {
        val uri = json["uri"].string?.trim()?.ifEmpty { null } ?: return null
        return BlueskyFeedGenerator(
            uri = uri,
            displayName = json["displayName"].nonBlank() ?: BlueskyUrls.rkeyOf(uri) ?: uri,
            description = json["description"].string?.trim().orEmpty(),
            avatarUrl = (json["avatarUrl"].takeIf { it.exists } ?: json["avatar"]).nonBlank(),
            creatorHandle = (json["creatorHandle"].takeIf { it.exists } ?: json["creator"]["handle"]).nonBlank(),
        )
    }

    fun json(list: BlueskyListInfo): JsonObject = row(
        "uri" to list.uri,
        "name" to list.name,
        "itemCount" to list.itemCount,
        "description" to list.description,
        "avatarUrl" to list.avatarUrl,
        "creatorHandle" to list.creatorHandle,
    )

    /** A list XTA pinned; null without a URI. */
    fun list(json: J): BlueskyListInfo? {
        val uri = json["uri"].string?.trim()?.ifEmpty { null } ?: return null
        return BlueskyListInfo(
            uri = uri,
            name = json["name"].string?.trim().orEmpty(),
            itemCount = (json["itemCount"].takeIf { it.exists } ?: json["listItemCount"]).integer ?: 0,
            description = json["description"].string?.trim().orEmpty(),
            avatarUrl = (json["avatarUrl"].takeIf { it.exists } ?: json["avatar"]).nonBlank(),
            creatorHandle = (json["creatorHandle"].takeIf { it.exists } ?: json["creator"]["handle"]).nonBlank(),
        )
    }

    /** The custom feeds XTA pinned (`plugin.bluesky.pinned_feeds`), each once. */
    fun feeds(text: String): List<BlueskyFeedGenerator> = J.parse(text).list.mapNotNull(::feed).distinctBy { it.uri }

    fun feedsJson(feeds: List<BlueskyFeedGenerator>): String = JsonArray(feeds.map(::json)).toString()

    /** The lists XTA pinned (`plugin.bluesky.pinned_lists`), each once. */
    fun lists(text: String): List<BlueskyListInfo> = J.parse(text).list.mapNotNull(::list).distinctBy { it.uri }

    fun listsJson(lists: List<BlueskyListInfo>): String = JsonArray(lists.map(::json)).toString()

    /**
     * The feed or list last opened on [server], as XTA remembers one per
     * AppView (`plugin.bluesky.selectedFeeds`, `selectedLists`): its URI and
     * name; null when it remembers none there.
     */
    fun selection(text: String, server: String): BlueskySelection? {
        val saved = J.parse(text)[server]
        val uri = saved["uri"].string?.takeIf { it.startsWith("at://") } ?: return null
        return BlueskySelection(uri, saved["name"].string ?: return null)
    }

    /** [text] remembering [selection] on [server], beside what it remembers on the others. */
    fun withSelection(text: String, server: String, selection: BlueskySelection): String {
        val kept = (J.parse(text).raw as? JsonObject).orEmpty()
        return JsonObject(kept + (server to row("uri" to selection.uri, "name" to selection.name))).toString()
    }

    /** A post's media slots, as XTA keeps them in parallel lists: a picture or a poster per slot, and what each slot is. */
    private fun media(json: J): List<BlueskyMedia> {
        val aspects = json["imageAspects"].list
        val videos = json["imageIsVideo"].list
        val alts = json["imageAlts"].list
        val videoUrls = json["videoUrls"].list
        val gifs = json["imageIsGif"].list
        return json["images"].list.mapIndexedNotNull { slot, image ->
            val url = image.string ?: return@mapIndexedNotNull null
            BlueskyMedia(
                url = url,
                aspectRatio = aspects.getOrNull(slot)?.number?.takeIf { it > 0 },
                alt = alts.getOrNull(slot)?.nonBlank(),
                isVideo = videos.getOrNull(slot)?.boolean == true,
                videoUrl = videoUrls.getOrNull(slot)?.nonBlank(),
                isGif = gifs.getOrNull(slot)?.boolean == true,
            )
        }
    }

    private fun facetJson(facet: BlueskyFacet): JsonObject =
        row("byteStart" to facet.byteStart, "byteEnd" to facet.byteEnd, "kind" to facet.kind.name.lowercase(), "value" to facet.value)

    /** A facet XTA kept; one that is damaged (no kind, no value, an empty range) is dropped, not the post. */
    private fun facet(json: J): BlueskyFacet? {
        val kind = BlueskyFacet.Kind.entries.firstOrNull { it.name.equals(json["kind"].string, ignoreCase = true) } ?: return null
        val start = json["byteStart"].number?.toInt() ?: return null
        val end = json["byteEnd"].number?.toInt() ?: return null
        val value = json["value"].string?.ifEmpty { null } ?: return null
        return BlueskyFacet(start, end, kind, value).takeIf { start >= 0 && end > start }
    }

    /** A count as XTA reads one back: a finite number, at least 0. */
    private fun count(json: J): Long = json.number?.toLong()?.coerceAtLeast(0) ?: 0

    private fun row(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(fields.associate { (key, value) -> key to element(value) })

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::element))
        else -> JsonPrimitive(value.toString())
    }
}
