package com.aimdi.xtanative.networks.bluesky

/**
 * One square of a profile's Media tab (bluesky_media_grid.dart): the post's
 * first picture or poster, covered when the post is labelled.
 */
data class BlueskyMediaTile(val post: BlueskyPost) {
    val imageUrl: String? get() = post.media.firstOrNull()?.url

    /** Labelled, so its picture stays covered here. */
    val covered: Boolean get() = post.isSensitive || post.isUnavailable

    val isVideo: Boolean get() = post.media.firstOrNull()?.isVideo == true

    /** More than one picture or video. */
    val many: Boolean get() = post.media.size > 1

    /** The first picture's description, when it has one and is not covered. */
    val alt: String? get() = post.media.firstOrNull()?.alt?.trim()?.ifEmpty { null }?.takeUnless { covered }

    /** A tap opens the post itself, not the viewer: for a video, or for what is covered. */
    val opensPost: Boolean get() = covered || isVideo
}

object BlueskyMediaGrid {
    /** About 150 dp a tile, two to five across. */
    fun columns(widthDp: Float): Int = (widthDp / 150).toInt().coerceIn(2, 5)

    /** The posts with media, a tile each, in rows of [columns]. */
    fun rows(posts: List<BlueskyPost>, columns: Int): List<List<BlueskyMediaTile>> =
        posts.filter { it.hasMedia }.map(::BlueskyMediaTile).chunked(columns.coerceAtLeast(1))
}
