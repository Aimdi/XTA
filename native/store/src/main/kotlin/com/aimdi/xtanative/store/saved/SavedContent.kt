package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XVideoVariant
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Which network a kept post came from, as Saved's network picker offers them; [ALL] picks every one. */
enum class SavedSource { ALL, X, REDDIT, MASTODON, BLUESKY, THREADS, OTHER }

/** A picture of a kept post, under an id no other picture shares: downloads and uploads are recorded by it. */
data class SavedPhoto(val id: String, val url: String)

/** A picture, video or GIF of a kept post: [url] is the picture, or a video's poster; [variants] are a video's files. */
data class SavedMedia(val id: String, val kind: XMedia.Kind, val url: String, val variants: List<XVideoVariant> = emptyList())

/**
 * What a kept post's JSON is, read once: the network it came from, the
 * lowercased text a search matches, and whether it carries pictures or
 * video. It reads the formats XTA keeps: an X post as XTA's tweet JSON
 * ([XtaTweetJson]), any other network as XTA's archive blob, marked by its
 * `xtaPlugin` (reddit, mastodon, bluesky, threads, or link for a network
 * kept as a link and its text).
 */
class SavedContent private constructor(
    val kind: Kind,
    /** The post itself: the tweet, a blob's `post`, or the link blob. */
    val post: J,
    /** Lowercased once, so a search does not re-case every post per keystroke. */
    val haystack: String,
) {
    /** The format the post is kept in; [NONE] when nothing readable was kept. */
    enum class Kind { NONE, X, REDDIT, MASTODON, BLUESKY, THREADS, LINK }

    /** For [Kind.LINK], the network the link was kept from. */
    val linkSource: String? get() = if (kind == Kind.LINK) post["source"].string else null

    /** A Threads post kept as a link, before Threads posts were kept whole, is still a Threads post. */
    val source: SavedSource get() = when {
        kind == Kind.THREADS || linkSource == THREADS -> SavedSource.THREADS
        kind == Kind.LINK -> SavedSource.OTHER
        kind == Kind.BLUESKY -> SavedSource.BLUESKY
        kind == Kind.MASTODON -> SavedSource.MASTODON
        kind == Kind.REDDIT -> SavedSource.REDDIT
        else -> SavedSource.X
    }

    /** Whether this is one of [wanted]'s posts. */
    fun from(wanted: SavedSource): Boolean = wanted == SavedSource.ALL || source == wanted

    /** [needle] must already be lowercased. */
    fun matches(needle: String): Boolean = haystack.contains(needle)

    val hasMedia: Boolean get() = when (kind) {
        Kind.LINK, Kind.BLUESKY, Kind.THREADS, Kind.MASTODON -> post["images"].list.isNotEmpty()
        Kind.REDDIT -> redditHasVisualMedia(post)
        Kind.X -> xMedia(post).isNotEmpty()
        Kind.NONE -> false
    }

    /** The X post, for [Kind.X]: what a saved X card draws. */
    val xPost: XPost? by lazy { if (kind == Kind.X) XtaTweetJson.decode(post) else null }

    /**
     * What downloads and uploads take of the post, as XTA's saved media list
     * gives it: every picture, video and GIF of an X post; only the pictures of
     * another network's, never a video's poster.
     */
    val media: List<SavedMedia> get() = when (kind) {
        Kind.LINK -> pictures(strings(post["images"]), "$linkSource:${post["url"].string}") { false }
        Kind.BLUESKY -> pictures(strings(post["images"]), "bluesky:${post["uri"].string}") { i -> post["imageIsVideo"][i].boolean == true }
        Kind.THREADS -> pictures(strings(post["images"]), "threads:${post["url"].string ?: post["id"].string}") { i ->
            !post["videoUrls"][i].string.isNullOrEmpty()
        }
        Kind.MASTODON -> pictures(strings(post["images"]), "mastodon:${post["url"].string}") { false }
        Kind.X -> xMedia(post).mapNotNull(::xSavedMedia)
        Kind.REDDIT, Kind.NONE -> emptyList()
    }

    /** The pictures a folder set to download takes: never a video, nor a video's poster. */
    val photos: List<SavedPhoto> get() = media.filter { it.kind == XMedia.Kind.PHOTO }.map { SavedPhoto(it.id, it.url) }

    companion object {
        /** Nothing kept: the post was too large to store, or its JSON no longer reads. */
        val EMPTY = SavedContent(Kind.NONE, J(null), "")

        private const val THREADS = "threads"

        /** What [content] is; [EMPTY] for nothing kept, and for a blob of a network that no longer reads. */
        fun of(content: JsonElement?): SavedContent {
            val root = J(content)
            if (root.raw !is JsonObject) return EMPTY
            return when (root["xtaPlugin"].string) {
                null -> x(root)
                "link" -> link(root)
                "bluesky" -> bluesky(root["post"])
                THREADS -> threads(root["post"])
                "mastodon" -> mastodon(root["post"])
                "reddit" -> reddit(root["post"])
                else -> null
            } ?: EMPTY
        }

        private fun x(tweet: J) = SavedContent(
            Kind.X,
            tweet,
            haystack(tweet["full_text"].string, tweet["text"].string, tweet["noteText"].string, tweet["user"]["name"].string, tweet["user"]["screen_name"].string),
        )

        private fun link(blob: J): SavedContent? {
            val url = blob["url"].string ?: return null
            if (!isWebUrl(url)) return null
            val text = listOf(blob["source"].string.orEmpty(), blob["author"].string.orEmpty(), blob["text"].string.orEmpty(), url)
            return SavedContent(Kind.LINK, blob, text.joinToString("\n").lowercase())
        }

        private fun bluesky(post: J): SavedContent? {
            if (post["uri"].string?.startsWith("at://") != true) return null
            val handle = post["handle"].string.orEmpty()
            val quote = post["quotedPost"].takeIf { it["uri"].string.orEmpty().isNotEmpty() }
            val card = post["linkCard"].takeIf { it["url"].string.orEmpty().isNotEmpty() }
            val text = listOf(post["text"].string.orEmpty(), post["authorName"].string ?: handle, handle, quote?.get("text")?.string.orEmpty(), card?.get("title")?.string.orEmpty())
            return SavedContent(Kind.BLUESKY, post, text.joinToString("\n").lowercase())
        }

        private fun threads(post: J): SavedContent? {
            if (post["id"].string.isNullOrEmpty()) return null
            val handle = post["handle"].string.orEmpty()
            val text = listOf(
                post["text"].string.orEmpty(),
                post["authorName"].string ?: handle,
                handle,
                post["topicTag"].string.orEmpty(),
                post["quoted"]["text"].string.orEmpty(),
                post["linkCard"]["title"].string.orEmpty(),
            )
            return SavedContent(Kind.THREADS, post, text.joinToString("\n").lowercase())
        }

        private fun mastodon(post: J): SavedContent? {
            if (!isMastodonSnapshot(post)) return null
            val acct = post["acct"].string.orEmpty()
            val quote = post["quote"].takeIf(::isMastodonSnapshot)?.let { listOf(it["text"].string.orEmpty()) }.orEmpty()
            val card = post["card"].takeIf { it.exists }?.let { listOf(it["title"].string.orEmpty(), it["description"].string.orEmpty()) }.orEmpty()
            val text = listOf(post["text"].string.orEmpty(), post["name"].string ?: acct, acct, post["spoiler"].string.orEmpty()) + quote + card
            return SavedContent(Kind.MASTODON, post, text.joinToString("\n").lowercase())
        }

        private fun isMastodonSnapshot(post: J): Boolean =
            post["version"].int == 1 && !post["id"].string.isNullOrEmpty() && !post["acct"].string.isNullOrEmpty() &&
                isWebUrl(post["url"].string.orEmpty())

        private fun reddit(post: J): SavedContent? {
            if (listOf("id", "title", "subreddit", "permalink").any { post[it].string == null }) return null
            return SavedContent(Kind.REDDIT, post, haystack(post["title"].string, post["selfText"].string, post["subreddit"].string, post["author"].string))
        }

        private fun haystack(vararg parts: String?) = parts.filterNotNull().joinToString("\n").lowercase()

        private fun pictures(urls: List<String>, idPrefix: String, isVideo: (Int) -> Boolean): List<SavedMedia> =
            urls.withIndex().filterNot { isVideo(it.index) }.map { SavedMedia("$idPrefix:${it.index}", XMedia.Kind.PHOTO, it.value) }

        private fun xSavedMedia(media: J): SavedMedia? {
            val url = media["media_url_https"].string ?: return null
            val kind = when (media["type"].string) {
                "video" -> XMedia.Kind.VIDEO
                "animated_gif" -> XMedia.Kind.GIF
                else -> XMedia.Kind.PHOTO
            }
            val variants = media["video_info"]["variants"].list.mapNotNull { variant ->
                variant["url"].string?.let { XVideoVariant(it, variant["bitrate"].int, variant["content_type"].string.orEmpty()) }
            }
            return SavedMedia(media["id_str"].string ?: url, kind, url, variants)
        }

        /** A tweet's media, as XTA's tweet JSON keeps them: the extended set, else the plain one. */
        internal fun xMedia(tweet: J): List<J> = tweet["extended_entities"]["media"].list.ifEmpty { tweet["entities"]["media"].list }

        private fun redditHasVisualMedia(post: J): Boolean {
            val gallery = strings(post["galleryImages"])
            val video = post["videoDashUrl"].string != null || post["videoFallbackUrl"].string != null
            return video || redditImageUrl(post["url"].string) != null || gallery.isNotEmpty()
        }

        private val redditImageHosts = setOf("i.redd.it", "preview.redd.it", "i.imgur.com", "i.redditmedia.com")
        private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

        /** [url] when it is a picture Reddit's card shows inline, as XTA's `redditImageUrl` decides. */
        internal fun redditImageUrl(url: String?): String? {
            val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
            if (uri.scheme?.startsWith("http") != true) return null
            val path = uri.path.orEmpty().lowercase()
            if (path.endsWith(".gifv")) return if (url.lowercase().endsWith(".gifv")) url.dropLast(1) else null
            val host = uri.host.orEmpty().lowercase().removePrefix("www.")
            return url.takeIf { host in redditImageHosts || imageExtensions.any(path::endsWith) }
        }

        internal fun isWebUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme in setOf("http", "https") && !uri.rawAuthority.isNullOrEmpty()
        }
    }
}
