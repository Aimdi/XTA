package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.downloads.MAX_DOWNLOAD_STEM_BYTES
import com.aimdi.xtanative.store.downloads.fillNameTemplate
import com.aimdi.xtanative.store.downloads.fitUtf8Bytes
import com.aimdi.xtanative.store.downloads.safeFileStem
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Booru downloads (booru_download.dart): file names from the reader's
 * template, and the posts a feed has loaded as one batch, which the app's
 * downloads queue without the files they have already.
 */
internal object BooruDownloads {
    /** What a file name template may say, as the settings list them. */
    val TOKENS: List<String> = listOf("{id}", "{md5}", "{site}", "{artist}", "{character}", "{copyright}", "{rating}")

    private val EXTENSION = Regex("^\\.[a-z0-9]{1,5}$")
    private val LOOSE_EXTENSION = Regex("^\\.[A-Za-z0-9]{1,5}$")

    /** The file a post's download fetches: its video, else its original. */
    fun url(post: BooruPost): String = post.videoUrl ?: post.originalUrl

    /** What [post] is saved as: [template] filled in, keeping the file's extension; the file's own name when the template is empty or leaves nothing. */
    fun fileName(post: BooruPost, template: String): String {
        val stem = safeFileStem(fillNameTemplate(template, values(post)))
        return if (stem.isEmpty()) ownName(url(post)) else stem + extension(post)
    }

    /** [posts]' files as one batch, each named by [template]. */
    fun files(posts: List<BooruPost>, template: String): List<PluginDownload> = posts.map { PluginDownload(url(it), fileName(it, template)) }

    /** What the viewer's Download saves of [post]: its video, else its original (booru_post_actions.dart's booruDownloadItem). */
    fun media(post: BooruPost): MediaItem {
        val video = post.videoUrl ?: return MediaItem(post.originalUrl)
        return MediaItem(post.thumbnailUrl, MediaItem.Kind.VIDEO, videoUrl = video)
    }

    private fun values(post: BooruPost): Map<String, String> = mapOf(
        "{id}" to post.id,
        "{md5}" to post.md5.orEmpty(),
        "{site}" to BooruHosts.displayName(post.host),
        "{artist}" to first(post, BooruTagCategory.ARTIST),
        "{character}" to first(post, BooruTagCategory.CHARACTER),
        "{copyright}" to first(post, BooruTagCategory.COPYRIGHT),
        "{rating}" to post.rating?.code.orEmpty(),
    )

    private fun first(post: BooruPost, kind: BooruTagCategory): String = post.tags.firstOrNull { post.tagCategories[it] == kind }.orEmpty()

    /** The saved file's extension with its dot: the downloaded address's, else the one the host named. */
    private fun extension(post: BooruPost): String {
        val fromUrl = extensionOf(lastSegment(url(post))).lowercase()
        if (EXTENSION.matches(fromUrl)) return fromUrl
        val named = post.fileExt.orEmpty().lowercase()
        return if (named.isEmpty()) "" else ".$named"
    }

    /** The file's own name, as XTA names a plugin's media without a template: `booru-<name>`. */
    private fun ownName(url: String): String {
        val base = lastSegment(url)
        if (base.isEmpty()) return "${BooruArchive.SOURCE}-media"
        val extension = extensionOf(base).takeIf(LOOSE_EXTENSION::matches).orEmpty()
        val start = fitUtf8Bytes("${BooruArchive.SOURCE}-" + base.dropLast(extension.length), MAX_DOWNLOAD_STEM_BYTES).trimEnd('.', ' ')
        return start + extension
    }

    private fun lastSegment(url: String): String = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotEmpty() }.orEmpty()

    private fun extensionOf(name: String): String = name.lastIndexOf('.').let { if (it <= 0) "" else name.substring(it) }
}

/**
 * What "Save on this device" keeps of a post (booru_post_actions.dart's
 * booruPostArchive): XTA's link record under the post's page, the same from
 * a feed, the viewer or the shared timelines, which Saved lists with the
 * other networks' kept links.
 */
internal object BooruArchive {
    const val SOURCE = "booru"

    /** The page that names the post: on its host, else the file itself. */
    fun shareUrl(post: BooruPost): String = post.hostPageUrl ?: post.originalUrl

    fun of(post: BooruPost): PluginPostArchive =
        PluginPostArchive.link(SOURCE, shareUrl(post), post.host, post.tagLine, listOf(if (post.isVideo) post.thumbnailUrl else post.displayUrl).filter { it.isNotEmpty() })
}
