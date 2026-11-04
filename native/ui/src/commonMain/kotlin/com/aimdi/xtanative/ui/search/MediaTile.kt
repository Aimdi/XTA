package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.settings.MediaGridLayout

/** One photo or video of a post, as a media grid shows it: [index] is its place among the post's media, which the viewer opens at. */
data class MediaTile(val postId: String, val index: Int, val media: XMedia) {
    val key: String get() = "$postId/$index"

    companion object {
        /** The photos and videos of every post of [threads], in the order X sent them; a repost's are the post reposted. */
        fun of(threads: List<XThread>): List<MediaTile> = threads.flatMap { thread ->
            thread.posts.flatMap { post -> post.shown.media.mapIndexed { index, media -> MediaTile(post.shown.id, index, media) } }
        }
    }
}

/**
 * How the media grid lays its tiles out, by the reader's Media settings:
 * [columns] a row, cropped square, or one a row at the media's own shape
 * ([ownShape]) for XTA's feed layout.
 */
data class MediaGridShape(val columns: Int, val ownShape: Boolean) {
    /** The shape a tile of [media] is drawn at, width over height. */
    fun aspectOf(media: XMedia): Float = if (ownShape) (media.aspectRatio ?: 1f).coerceIn(MIN_ASPECT, MAX_ASPECT) else 1f

    companion object {
        /** XTA's bounds for a picture drawn at its own shape: no taller than 4:5, no wider than 1.91:1. */
        const val MIN_ASPECT = 0.8f
        const val MAX_ASPECT = 1.91f

        fun of(layout: MediaGridLayout, columns: Int): MediaGridShape = when (layout) {
            MediaGridLayout.FEED -> MediaGridShape(1, ownShape = true)
            MediaGridLayout.TWO_COLUMNS -> MediaGridShape(2, ownShape = false)
            MediaGridLayout.MASONRY -> MediaGridShape(columns.coerceIn(1, 5), ownShape = false)
        }
    }
}
