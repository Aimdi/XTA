package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.MAX_DOWNLOAD_STEM_BYTES
import com.aimdi.xtanative.store.downloads.extensionFor
import com.aimdi.xtanative.store.downloads.fitUtf8Bytes
import com.aimdi.xtanative.ui.plugins.MediaItem
import java.net.URI
import java.net.URLDecoder

/** What a plugin's media downloads as (plugin_post_media.dart's downloadPluginMediaItem and pluginMediaFileName). */
object PluginDownloads {
    /**
     * The file [media] saves: its original where the network names one
     * ([MediaItem.downloadUrl], XTA's resolvedDownloadUrl), else a photo
     * itself, a video's own file; null for a stream (an HLS playlist), which
     * has no one file.
     */
    fun url(media: MediaItem): String? = media.downloadUrl?.takeIf { isWeb(it) && !isPlaylist(it) } ?: when (media.kind) {
        MediaItem.Kind.PHOTO -> media.imageUrl.takeIf(::isWeb)
        MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> media.videoUrl?.takeIf { isWeb(it) && !isPlaylist(it) }
    }

    /**
     * "<plugin>-<the file's own name>", as XTA names a plugin's media: the
     * name decoded from the address, its separators made `_`, its start kept
     * within Android's limit (where sites put their name and the post's) and
     * its extension kept; "<plugin>-media" when the address names no file.
     */
    fun fileName(source: String, url: String): String {
        val base = lastSegment(url)
        val name = source.trim().ifEmpty { "xta" }
        if (base.isEmpty()) return "$name-media"
        val extension = extensionOf(base)
        val stem = base.dropLast(extension.length)
        return fitUtf8Bytes("$name-$stem", MAX_DOWNLOAD_STEM_BYTES).replace(trailingDotsAndSpaces, "") + extension
    }

    /**
     * [fileName] with the extension [mimeType] stands for when it has none,
     * so the platform files it as what it is: a GIF among the pictures, a
     * text among the downloads.
     */
    fun named(fileName: String, mimeType: String): String {
        val name = fileName.trim().ifEmpty { "xta-file" }
        if (extensionOf(name).isNotEmpty()) return name
        return extensionFor(mimeType)?.let { "$name.$it" } ?: name
    }

    private val trailingDotsAndSpaces = Regex("[. ]+$")
    private val extensionPattern = Regex("^\\.[A-Za-z0-9]{1,5}$")

    private fun lastSegment(url: String): String {
        val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty()
        val segment = path.split('/').lastOrNull { it.isNotEmpty() } ?: return ""
        val decoded = runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)
        return decoded.replace(Regex("[/\\\\?]"), "_")
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return ""
        return name.substring(dot).takeIf { extensionPattern.matches(it) }.orEmpty()
    }

    private fun isWeb(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun isPlaylist(url: String): Boolean = runCatching { URI(url).path.orEmpty() }.getOrDefault(url).lowercase().endsWith(".m3u8")
}
