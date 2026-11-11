package com.aimdi.xtanative.plugins.immich

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.networks.immich.ImmichClient
import com.aimdi.xtanative.networks.immich.ImmichProblem
import com.aimdi.xtanative.networks.immich.ImmichServer
import com.aimdi.xtanative.networks.immich.ImmichUploadReport
import com.aimdi.xtanative.networks.immich.ImmichUploadStatus
import com.aimdi.xtanative.networks.immich.ImmichUploader
import com.aimdi.xtanative.store.saved.SavedMedia
import com.aimdi.xtanative.store.settings.ImmichPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.answeredOr

/**
 * A filed post's media sent to the reader's own Immich, and the settings'
 * test of the server, each said in XTA's words (folder_picker.dart's upload,
 * immich_settings_screen.dart).
 */
internal object ImmichSaves {
    /**
     * [post]'s media sent where [settings] point, videos only while they say
     * so, into an album named after its folder while they ask for one.
     */
    suspend fun upload(uploader: ImmichUploader, settings: Settings, post: PluginFiledPost): ImmichUploadReport = uploader.upload(
        media = post.media.map(::xMedia),
        serverUrl = settings[ImmichPrefs.serverUrl],
        apiKey = settings[ImmichPrefs.apiKey],
        includeVideos = settings[ImmichPrefs.includeVideos],
        albumName = post.folder.takeIf { settings[ImmichPrefs.albumPerFolder] },
    )

    /**
     * What XTA says once an upload ends: nothing when there was nothing to
     * send (no media, or all of it sent before). A failed run says why in
     * Test connection's words when the server (or the way to it) turned it
     * away: a refused key or a wrong address is mended in the settings, not
     * by filing the post again.
     */
    fun notice(report: ImmichUploadReport): PluginText? = when (report.status) {
        ImmichUploadStatus.NOTHING_TO_DO -> null
        ImmichUploadStatus.NOT_CONFIGURED -> Strings.not_configured
        ImmichUploadStatus.DONE -> Strings.upload_done
        ImmichUploadStatus.FAILED -> report.problem?.let(::problem) ?: Strings.upload_failed
    }

    /** Whether the server at [address] takes [apiKey], as Test connection says it. */
    suspend fun test(client: ImmichClient, address: String, apiKey: String): Pair<ConnectionStatus, PluginText> {
        val server = ImmichServer.of(address, apiKey) ?: return ConnectionStatus.FAILED to Strings.not_configured
        return answeredOr({ ConnectionStatus.FAILED to problem(ImmichProblem.of(it)) }) {
            client.verify(server)
            ConnectionStatus.OK to Strings.test_ok
        }
    }

    fun problem(problem: ImmichProblem): PluginText = when (problem) {
        ImmichProblem.NOT_CONFIGURED -> Strings.not_configured
        ImmichProblem.UNAUTHORIZED -> Strings.error_unauthorized
        ImmichProblem.BAD_SERVER -> Strings.error_server_url
        ImmichProblem.NETWORK -> Strings.error_network
        ImmichProblem.SERVER -> Strings.error_generic
    }

    /** A medium of the archive as the uploader picks among X's: its picture, or a video's poster with its files. */
    private fun xMedia(media: SavedMedia): XMedia = XMedia(media.kind, media.url, width = null, height = null, variants = media.variants)
}
