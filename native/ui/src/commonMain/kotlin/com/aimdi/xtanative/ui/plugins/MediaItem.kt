package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.ui.media.VideoQuality

/** What [item] plays in the viewer: [chosen] when it is one of [qualities], else the item's own file. */
fun playedUrl(item: MediaItem, qualities: List<VideoQuality>, chosen: String?): String? =
    chosen?.takeIf { url -> qualities.any { it.url == url } } ?: item.videoUrl

/** Which of [qualities] [url] is, for the quality menu to name; null when it is none of them. */
fun qualityOf(url: String?, qualities: List<VideoQuality>): VideoQuality? = qualities.firstOrNull { it.url == url }

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
    /** Where the video plays on the web: a desktop without libmpv, which has no player of its own then, opens it there. */
    val webUrl: String? = null,
) {
    enum class Kind { PHOTO, VIDEO, GIF }

    /** What a tile shows. */
    val tileUrl: String get() = thumbUrl ?: imageUrl
}
