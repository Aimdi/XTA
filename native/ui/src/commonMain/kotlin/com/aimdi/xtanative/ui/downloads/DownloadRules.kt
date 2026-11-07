package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.downloads.originalDownloadUrl
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.saved.KeepNotice
import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the download setting (Settings › Media) sends a new download. */
fun downloadDestination(settings: Settings): DownloadDestination =
    DownloadDestination.of(settings[MediaPrefs.downloadType].stored, settings[MediaPrefs.downloadTreeUri])

/** The file a post's media is saved as: where it is fetched from, and its name. */
data class MediaDownload(val url: String, val fileName: String)

/**
 * The file XTA saves for the media at [index] of [post] (the post reposted,
 * for a repost): a photo at its original size, a video or GIF as its MP4 of
 * the highest bitrate, named after the author. Null when there is no file to
 * save, as for a stream.
 */
fun mediaDownload(post: XPost, index: Int): MediaDownload? {
    val shown = post.shown
    val media = shown.media.getOrNull(index) ?: return null
    val url = when (media.kind) {
        XMedia.Kind.PHOTO -> originalDownloadUrl(media.url)
        XMedia.Kind.VIDEO, XMedia.Kind.GIF -> media.mp4s.firstOrNull()?.url ?: return null
    }
    val source = if (media.kind == XMedia.Kind.PHOTO) media.url else url
    return MediaDownload(url, "${shown.author.screenName}-${fileNameOf(source)}")
}

/** The last part of [url]'s path, without the size X puts after a colon. */
private fun fileNameOf(url: String): String {
    val path = runCatching { URI(url).path }.getOrNull() ?: url.substringBefore('?')
    return path.substringAfterLast('/').substringBefore(':').ifEmpty { "media" }
}

/** The icon a download's row starts with. */
enum class DownloadIcon { DONE, ERROR, CANCELLED, DOWNLOAD }

/** What a row of the downloads list shows and offers. */
data class DownloadRow(
    val icon: DownloadIcon,
    /** The bar and the sizes, while the file arrives. */
    val showsProgress: Boolean,
    /** From 0 to 1; null while the size is unknown, for a bar that runs. */
    val fraction: Float?,
    val retryHint: Boolean,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val canOpen: Boolean,
) {
    companion object {
        fun of(entry: DownloadEntry) = DownloadRow(
            icon = when (entry.status) {
                DownloadStatus.COMPLETED -> DownloadIcon.DONE
                DownloadStatus.FAILED, DownloadStatus.INTERRUPTED -> DownloadIcon.ERROR
                DownloadStatus.CANCELLED -> DownloadIcon.CANCELLED
                else -> DownloadIcon.DOWNLOAD
            },
            showsProgress = entry.status == DownloadStatus.DOWNLOADING,
            fraction = entry.fraction?.toFloat(),
            retryHint = entry.status == DownloadStatus.FAILED,
            canCancel = entry.canCancel,
            canRetry = entry.canRetry,
            canOpen = entry.status == DownloadStatus.COMPLETED && entry.savedUri != null,
        )
    }
}

/**
 * What a download started from a post says once it ends (downloads.dart):
 * where it went when the reader did not pick the place, that it was saved
 * otherwise, a hint to retry when it failed, and nothing when cancelled.
 */
fun downloadNotice(entry: DownloadEntry, folder: String?): KeepNotice? = when {
    entry.status == DownloadStatus.COMPLETED && folder != null && entry.savedUri != null ->
        KeepNotice.SavedTo(folder, entry.savedUri!!, entry.fileName)
    entry.status == DownloadStatus.COMPLETED -> KeepNotice.MediaSaved
    entry.status == DownloadStatus.FAILED -> entry.httpStatus?.takeIf { xMedia(entry.url) }?.let(KeepNotice::MediaRefused) ?: KeepNotice.RetryHint
    else -> null
}

/** Whether [url] is a picture or video X serves, the saves whose failure names the status X returned. */
internal fun xMedia(url: String): Boolean {
    val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
    return host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") || host.endsWith("twimg.com")
}
