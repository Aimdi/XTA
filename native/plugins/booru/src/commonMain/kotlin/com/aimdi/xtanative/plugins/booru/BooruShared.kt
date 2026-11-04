package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruLinks
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant

/**
 * A booru post as the app's shared rows draw it among other networks' (Home's
 * timelines, a group's feed), as booru_post_card.dart: "Booru #id" from its
 * site, its first tags, its picture and its score; and a post Saved kept as
 * XTA's link record, as Saved shows it.
 */
internal object BooruShared {
    /** How many of a post's tags its card shows. */
    private const val PREVIEW_TAGS = 6

    /** [post] in the shared row, named [title] ("Booru #123"). A post without a date still shows, at the end, as XTA keeps it. */
    fun post(post: BooruPost, title: String): NetworkPost = NetworkPost(
        id = post.key,
        authorId = post.host,
        authorName = title,
        authorHandle = BooruHosts.displayName(post.host),
        createdAt = post.feedDate ?: Instant.EPOCH,
        text = words(post.tags.take(PREVIEW_TAGS)),
        media = listOfNotNull(media(post)),
        likes = (post.score ?: 0).coerceAtLeast(0).toLong(),
        url = BooruArchive.shareUrl(post),
    )

    /** The picture a row shows of [post], a video's still with its file; none when the host gave none. */
    fun media(post: BooruPost): MediaItem? {
        val ratio = post.aspectRatio.toFloat()
        val video = post.videoUrl
        if (video != null) return post.thumbnailUrl.takeIf { it.isNotEmpty() }?.let { MediaItem(it, MediaItem.Kind.VIDEO, videoUrl = video, aspectRatio = ratio, webUrl = post.hostPageUrl) }
        return post.displayUrl.takeIf { it.isNotEmpty() }?.let { MediaItem(it, aspectRatio = ratio, thumbUrl = post.catalogUrl.ifEmpty { null }) }
    }

    /**
     * A post Saved kept ([archive], XTA's link record), as the shared row
     * draws it: [title] of the id its page names on one of [sites], its site,
     * its tags and its picture.
     */
    fun saved(archive: PluginPostArchive, sites: List<BooruPreset>, title: (String) -> String): NetworkPost {
        val record = J(archive.content)
        val url = record["url"].string.orEmpty()
        val site = BooruHosts.displayName(record["author"].string.orEmpty())
        return NetworkPost(
            id = archive.id,
            authorId = archive.authorId.orEmpty(),
            authorName = BooruLinks.postRef(url, sites)?.id?.let(title) ?: site,
            authorHandle = site,
            text = words(record["text"].string.orEmpty().split(' ').filter { it.isNotEmpty() }),
            media = record["images"].list.mapNotNull { it.string?.takeIf(String::isNotEmpty)?.let { image -> MediaItem(image) } },
            url = url,
        )
    }

    private fun words(tags: List<String>): List<TextRun> = if (tags.isEmpty()) emptyList() else listOf(TextRun.Plain(tags.joinToString(" ")))
}
