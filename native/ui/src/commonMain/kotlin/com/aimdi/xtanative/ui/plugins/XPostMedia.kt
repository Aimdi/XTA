package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread

/** The media of an X post a plugin shows, as the plugins' viewer opens it ([PluginHost.openMedia]). */
object XPostMedia {
    /** The post [id] names among [threads]: one of their posts, or one such a post reposts or quotes. */
    fun find(threads: List<XThread>, id: String): XPost? =
        threads.asSequence().flatMap { it.posts }.flatMap { sequenceOf(it, it.reposted, it.quoted, it.reposted?.quoted) }.firstOrNull { it?.id == id }

    /** [post]'s photos, videos and GIFs, in its order. */
    fun items(post: XPost): List<MediaItem> = post.media.map { item(it, post) }

    /** One of [post]'s media: a photo as X serves it, a video or GIF by its best MP4, played on the post's page where there is no player. */
    fun item(media: XMedia, post: XPost): MediaItem {
        val photo = media.kind == XMedia.Kind.PHOTO
        return MediaItem(
            imageUrl = media.url,
            kind = when (media.kind) {
                XMedia.Kind.PHOTO -> MediaItem.Kind.PHOTO
                XMedia.Kind.VIDEO -> MediaItem.Kind.VIDEO
                XMedia.Kind.GIF -> MediaItem.Kind.GIF
            },
            videoUrl = if (photo) null else (media.mp4s.firstOrNull() ?: media.variants.firstOrNull())?.url,
            aspectRatio = media.aspectRatio,
            alt = media.alt,
            durationMs = media.durationMs,
            webUrl = if (photo) null else post.url,
        )
    }
}
