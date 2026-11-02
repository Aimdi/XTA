package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedContent.Kind
import com.aimdi.xtanative.store.saved.strings
import java.net.URI
import org.jsoup.Jsoup

/**
 * What of a saved post the offline library keeps, under the id
 * `saved:<post id>` ([idFor]): its pictures, and a video at its best quality.
 */
object OfflineSaved {
    fun idFor(postId: String): String = "saved:$postId"

    /** The pictures and videos of [content] that can be kept; empty for a post that has none, or from a network kept as a link. */
    fun media(content: SavedContent): List<OfflineMediaSource> {
        val post = content.post
        return when (content.kind) {
            Kind.MASTODON -> strings(post["images"]).map { OfflineMediaSource(it) }
            Kind.THREADS -> strings(post["images"]).mapIndexed { i, image ->
                post["videoUrls"][i].string?.takeIf { it.isNotEmpty() }?.let { OfflineMediaSource(it, video = true) } ?: OfflineMediaSource(image)
            }
            Kind.REDDIT -> redditMedia(post)
            Kind.X -> SavedContent.xMedia(post).mapNotNull(::xMedia)
            else -> emptyList()
        }
    }

    fun hasMedia(content: SavedContent): Boolean = media(content).isNotEmpty()

    /** A post the reader must agree to see, kept only once they agree to keep it. */
    fun sensitive(content: SavedContent): Boolean {
        val post = content.post
        return when (content.kind) {
            Kind.X -> post["possibly_sensitive"].boolean == true
            Kind.MASTODON -> post["sensitive"].boolean == true || post["spoiler"].string.orEmpty().isNotEmpty()
            Kind.REDDIT -> post["over18"].boolean == true || post["spoiler"].boolean == true
            else -> false
        }
    }

    /** The title the library lists it under: its text, without markup, cut at 300 characters. */
    fun title(content: SavedContent): String {
        val post = content.post
        val raw = when (content.kind) {
            Kind.REDDIT -> post["title"].string
            Kind.MASTODON, Kind.THREADS -> post["text"].string
            Kind.X -> post["full_text"].string ?: post["text"].string
            else -> null
        }.orEmpty()
        val plain = Jsoup.parseBodyFragment(raw).text()
        return if (plain.length > 300) plain.take(300) + "…" else plain
    }

    /** Who it is from, as the library names them. */
    fun source(content: SavedContent): String {
        val post = content.post
        return when (content.kind) {
            Kind.MASTODON -> post["acct"].string
            Kind.REDDIT -> post["subreddit"].string
            Kind.THREADS -> post["handle"].string
            Kind.X -> post["user"]["screen_name"].string
            else -> null
        }.orEmpty()
    }

    /** Where the post lives on its network. */
    fun url(content: SavedContent): String? {
        val post = content.post
        return when (content.kind) {
            Kind.MASTODON -> post["url"].string
            Kind.REDDIT -> "https://www.reddit.com${post["permalink"].string.orEmpty()}"
            Kind.X -> post["id_str"].string?.let { "https://x.com/i/status/$it" }
            else -> null
        }
    }

    private val redditImagePath = Regex("\\.(jpe?g|png|gif|webp|avif)$", RegexOption.IGNORE_CASE)

    private fun redditMedia(post: J): List<OfflineMediaSource> {
        post["videoFallbackUrl"].string?.let { return listOf(OfflineMediaSource(it, video = true)) }
        val gallery = strings(post["galleryImages"])
        if (gallery.isNotEmpty()) return gallery.map { OfflineMediaSource(it) }
        val url = post["url"].string ?: return emptyList()
        val path = runCatching { URI(url).path }.getOrNull() ?: return emptyList()
        return if (redditImagePath.containsMatchIn(path)) listOf(OfflineMediaSource(url)) else emptyList()
    }

    /** A picture as it is, a video or GIF as its highest-bitrate MP4. */
    private fun xMedia(media: J): OfflineMediaSource? {
        val picture = media["media_url_https"].string ?: media["media_url"].string
        if (media["type"].string == "photo" && picture != null) return OfflineMediaSource(picture)
        return media["video_info"]["variants"].list
            .filter { it["content_type"].string == "video/mp4" }
            .sortedByDescending { it["bitrate"].long ?: 0 }
            .firstNotNullOfOrNull { it["url"].string }
            ?.let { OfflineMediaSource(it, video = true) }
    }
}
