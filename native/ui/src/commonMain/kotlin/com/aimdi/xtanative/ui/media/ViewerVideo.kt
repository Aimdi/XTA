package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings

/**
 * What the viewer plays of a video or GIF (_video.dart's _createPooled): the
 * variant the video-quality setting picks among [qualities], looping when the
 * loop setting says, with its sound as the app-wide mute says; a GIF always
 * loops, silent. [prefetchSeconds] is how far ahead it reads, 0 for XTA's own
 * bound.
 */
internal data class ViewerVideo(
    val url: String,
    val qualities: List<VideoQuality>,
    val gif: Boolean,
    val loop: Boolean,
    val muted: Boolean,
    val prefetchSeconds: Int,
) {
    companion object {
        /** [media] as [settings] say it plays; null when it has nothing to play. */
        fun of(media: XMedia, settings: Settings?): ViewerVideo? {
            val qualities = VideoRules.qualities(media)
            val quality = settings?.get(MediaPrefs.videoQuality) ?: MediaQuality.MEDIUM
            val url = VideoRules.defaultUrl(qualities, VideoRules.streamUrl(media), quality) ?: return null
            return of(url, qualities, media.kind == XMedia.Kind.GIF, settings)
        }

        /** The file at [url], with the [qualities] it has (none for a single file), as [settings] say it plays. */
        fun of(url: String, qualities: List<VideoQuality>, gif: Boolean, settings: Settings?) = ViewerVideo(
            url = url,
            qualities = qualities,
            gif = gif,
            loop = gif || settings?.get(MediaPrefs.loop) == true,
            muted = gif || VideoMute.muted(settings?.get(MediaPrefs.mute) ?: true),
            prefetchSeconds = settings?.get(MediaPrefs.prefetchSeconds) ?: 0,
        )
    }
}
