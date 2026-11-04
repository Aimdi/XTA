package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One file of a post to send to a photo library: [url] carries the original,
 * and [id] stays the same across saves whichever variant won, so the upload
 * log knows a file it has already sent.
 */
data class UploadableMedia(val url: String, val id: String, val fileName: String, val isVideo: Boolean)

/**
 * The photos and videos of [media] at the best quality X offers, in order.
 * GIFs are MP4s like any video by the time a photo library has them, so they
 * come through as videos, and are left out with them when [includeVideos] is off.
 * Other networks' kept posts hand their pictures over as photos.
 */
fun uploadableMedia(media: List<XMedia>, includeVideos: Boolean = true): List<UploadableMedia> = media.mapNotNull {
    when (it.kind) {
        XMedia.Kind.PHOTO -> photo(it)
        XMedia.Kind.VIDEO, XMedia.Kind.GIF -> if (includeVideos) video(it) else null
    }
}

private fun photo(media: XMedia): UploadableMedia? {
    val url = media.url.ifEmpty { return null }
    return UploadableMedia(
        // `:orig` is the untouched upload rather than the display-sized copy, which a photo library should hold.
        url = if (url.toHttpUrlOrNull()?.host == "pbs.twimg.com") "$url:orig" else url,
        id = url,
        fileName = fileNameOf(url, fallback = "photo.jpg"),
        isVideo = false,
    )
}

private fun video(media: XMedia): UploadableMedia? {
    val url = bestVariant(media.variants)?.url ?: return null
    return UploadableMedia(
        url = url,
        // The poster, not the variant: an id that moved with the winning variant would upload the video again at another bitrate.
        id = media.url.ifEmpty { url },
        fileName = fileNameOf(url, fallback = "video.mp4"),
        isVideo = true,
    )
}

/** The highest-bitrate progressive MP4: an HLS playlist is not a file a photo library can take. */
private fun bestVariant(variants: List<XVideoVariant>): XVideoVariant? =
    variants.filter { it.contentType == "video/mp4" && it.url.isNotEmpty() && it.bitrate != null }.maxByOrNull { it.bitrate ?: 0 }

/** The name at the end of a media URL, without the query or the `:orig` X uses for sizes. */
private fun fileNameOf(url: String, fallback: String) =
    url.substringBefore('?').substringAfterLast('/').substringBefore(':').ifEmpty { fallback }
