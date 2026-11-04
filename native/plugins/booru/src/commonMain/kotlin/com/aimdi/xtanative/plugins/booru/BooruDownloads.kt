package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.downloads.MAX_DOWNLOAD_STEM_BYTES
import com.aimdi.xtanative.store.downloads.fillNameTemplate
import com.aimdi.xtanative.store.downloads.fitUtf8Bytes
import com.aimdi.xtanative.store.downloads.safeFileStem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a batch of downloads queues, and how many of the posts were skipped as downloaded already. */
internal data class DownloadPlan(val requests: List<DownloadRequest>, val skipped: Int)

/**
 * Booru downloads (booru_download.dart): file names from the reader's
 * template, and the posts a feed has loaded queued at once, without the files
 * the download history already has.
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

    /**
     * What a batch queues: [posts] whose files the download history does not
     * know ([known]) and the batch does not repeat, as many as the queue has
     * [room] for, named by [template].
     */
    fun plan(posts: List<BooruPost>, known: Set<String>, room: Int, template: String, destination: DownloadDestination): DownloadPlan {
        val seen = known.toHashSet()
        val fresh = posts.filter { post -> url(post).toHttpUrlOrNull() != null && seen.add(url(post)) }.map { post ->
            DownloadRequest(url(post), fileName(post, template), destination.treeUri, destination.background)
        }
        return DownloadPlan(fresh.take(room.coerceAtLeast(0)), posts.size - fresh.size)
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
        if (base.isEmpty()) return "$SOURCE-media"
        val extension = extensionOf(base).takeIf(LOOSE_EXTENSION::matches).orEmpty()
        val start = fitUtf8Bytes("$SOURCE-" + base.dropLast(extension.length), MAX_DOWNLOAD_STEM_BYTES).trimEnd('.', ' ')
        return start + extension
    }

    private fun lastSegment(url: String): String = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotEmpty() }.orEmpty()

    private fun extensionOf(name: String): String = name.lastIndexOf('.').let { if (it <= 0) "" else name.substring(it) }

    private const val SOURCE = "booru"
}

/**
 * What "Save on this device" keeps of a post (booru_post_actions.dart's
 * booruPostArchive): XTA's link archive, under the same id from a feed or the
 * viewer, which Saved lists with the other networks' kept links.
 */
internal object BooruArchive {
    /** The page that names the post: on its host, else the file itself. */
    fun shareUrl(post: BooruPost): String = post.hostPageUrl ?: post.originalUrl

    fun id(post: BooruPost): String = "booru:" + shareUrl(post)

    fun authorId(post: BooruPost): String = post.host

    fun content(post: BooruPost): JsonObject = JsonObject(
        mapOf(
            "archiveId" to JsonNull,
            "archiveUserId" to JsonNull,
            "xtaPlugin" to JsonPrimitive("link"),
            "source" to JsonPrimitive("booru"),
            "url" to JsonPrimitive(shareUrl(post)),
            "author" to JsonPrimitive(post.host),
            "text" to JsonPrimitive(post.tagLine),
            "images" to JsonArray(listOf(JsonPrimitive(if (post.isVideo) post.thumbnailUrl else post.displayUrl))),
        ),
    )
}
