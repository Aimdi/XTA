package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload

/**
 * What a post's pictures and videos are saved as: "threads-<author>-<post's
 * code>", then its place among several ("-2"), then the file's own kind
 * (".jpg", ".mp4"). Meta's CDN names a file by numbers alone
 * ("471234_1838_n.jpg"), which say neither whose it is nor where it is from;
 * these sort a post's files together and in their order.
 */
internal object ThreadsMediaFiles {
    /** The name [item], the post's [index]th, is saved under. */
    fun name(post: ThreadsPost, index: Int, item: MediaItem): String {
        val stem = listOf("threads", safe(post.handle), safe(post.shortcode ?: post.id).take(MAX_CODE)).filter { it.isNotEmpty() }.joinToString("-")
        val place = if (post.media.size > 1) "-${index + 1}" else ""
        return stem + place + extension(url(item), item.kind != MediaItem.Kind.PHOTO)
    }

    /** The file a download of [item] fetches: a picture itself, a video's or GIF's own file; null for a stream. */
    fun url(item: MediaItem): String? = when (item.kind) {
        MediaItem.Kind.PHOTO -> item.imageUrl
        MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> item.videoUrl
    }?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.takeIf { !it.substringBefore('?').endsWith(".m3u8") }

    /** Every one of [items] (the post's, in order) that has a file of its own, each under its name. */
    fun all(post: ThreadsPost, items: List<MediaItem>): List<PluginDownload> =
        items.mapIndexedNotNull { index, item -> url(item)?.let { PluginDownload(it, name(post, index, item)) } }

    /** The extension the address's file has, else what a [video] or a picture usually is. */
    fun extension(url: String?, video: Boolean): String {
        val file = url.orEmpty().substringBefore('?').substringBefore('#').substringAfterLast('/')
        val found = Regex("\\.[A-Za-z0-9]{2,5}$").find(file)?.value?.lowercase()
        return found ?: if (video) ".mp4" else ".jpg"
    }

    private fun safe(text: String): String = text.trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }.trim('.')

    private const val MAX_CODE = 40
}
