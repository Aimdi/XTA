package com.aimdi.xtanative.ui.plugins

/** A photo, video or GIF as the shared tiles and viewer show it, whatever network it comes from. */
data class MediaItem(
    /** The picture opened full screen, or a video's poster. */
    val imageUrl: String,
    val kind: Kind = Kind.PHOTO,
    /** What plays: an MP4, or an HLS playlist; null for a photo. */
    val videoUrl: String? = null,
    /** Width over height, when the network says. */
    val aspectRatio: Float? = null,
    val alt: String? = null,
    val durationMs: Long? = null,
    /** A smaller picture for a tile, when the network has one. */
    val thumbUrl: String? = null,
    /** A lighter rendition for a tile to play in place; [videoUrl] otherwise. */
    val inlineVideoUrl: String? = null,
    /** Where the video plays on the web: the desktop, which has no player of its own, opens it there. */
    val webUrl: String? = null,
) {
    enum class Kind { PHOTO, VIDEO, GIF }

    /** What a tile shows. */
    val tileUrl: String get() = thumbUrl ?: imageUrl
}
