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
    /** The file a download saves when it is not the one shown, as a booru's original beside the picture it shows. */
    val downloadUrl: String? = null,
    /** What the viewer's Share gives, as a booru's post page; the picture's own address otherwise. */
    val shareUrl: String? = null,
) {
    /** As the plugins of API 2 make one, before [downloadUrl] and [shareUrl]. */
    @Deprecated("The constructor of plugin API 2", level = DeprecationLevel.HIDDEN)
    constructor(
        imageUrl: String,
        kind: Kind = Kind.PHOTO,
        videoUrl: String? = null,
        aspectRatio: Float? = null,
        alt: String? = null,
        durationMs: Long? = null,
        thumbUrl: String? = null,
        inlineVideoUrl: String? = null,
        webUrl: String? = null,
    ) : this(imageUrl, kind, videoUrl, aspectRatio, alt, durationMs, thumbUrl, inlineVideoUrl, webUrl, null, null)

    enum class Kind { PHOTO, VIDEO, GIF }

    /** What a tile shows. */
    val tileUrl: String get() = thumbUrl ?: imageUrl

    /** Its description as the reader is shown it, trimmed; null when it has none worth showing (XTA's usable alt). */
    val description: String? get() = alt?.trim()?.takeIf { it.isNotEmpty() }

    /** The address the viewer's Share gives (XTA's resolvedShareUrl). */
    val sharedUrl: String get() = shareUrl?.takeIf { it.isNotBlank() } ?: imageUrl
}

/** What a long press on a tile does, as XTA's tiles answer one. */
enum class TileHold { DESCRIPTION, SAVE, NOTHING }

/** A long press on [item]'s tile: its description where it has one, else a photo saved where the screen [saves] them, else nothing. */
fun tileHold(item: MediaItem, saves: Boolean): TileHold = when {
    item.description != null -> TileHold.DESCRIPTION
    saves && item.kind == MediaItem.Kind.PHOTO -> TileHold.SAVE
    else -> TileHold.NOTHING
}
