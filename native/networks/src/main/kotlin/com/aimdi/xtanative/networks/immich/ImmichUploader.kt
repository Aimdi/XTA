package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.x.model.XMedia
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * What has already been sent to Immich, kept on the device (XTA's
 * `immich_upload` table). Immich turns a second copy away by checksum anyway;
 * this keeps the phone from sending a video again only to be told so.
 */
interface ImmichUploadLog {
    /** The media ids among [ids] not uploaded yet. */
    suspend fun pending(ids: Set<String>): Set<String>

    /** Notes that [mediaId] is on the server, as [assetId] when Immich said. */
    suspend fun record(mediaId: String, assetId: String?)
}

/** How an upload run ended, so the caller can say so without knowing the API. */
enum class ImmichUploadStatus { NOTHING_TO_DO, NOT_CONFIGURED, DONE, FAILED }

/** An upload run: [uploaded] assets Immich did not have, [duplicates] it recognised (not a failure), and [failures]. */
data class ImmichUploadReport(val status: ImmichUploadStatus, val uploaded: Int = 0, val duplicates: Int = 0, val failures: Int = 0)

/**
 * Sends the photos and videos of a kept post to the user's Immich, as XTA
 * does when a post is filed in a folder set to upload. It only writes: what
 * the reader chose to keep goes up, and nothing is asked about the rest of
 * their library.
 */
class ImmichUploader(
    private val http: ImmichHttp,
    private val log: ImmichUploadLog,
    private val clock: () -> Instant = Instant::now,
) {
    private val client = ImmichClient(http)

    /**
     * Uploads what of [media] is not on the server yet, to the server at
     * [serverUrl] with [apiKey], and files it in the album [albumName] when
     * one is asked for. One file failing does not stop the others.
     */
    suspend fun upload(
        media: List<XMedia>,
        serverUrl: String,
        apiKey: String,
        includeVideos: Boolean = true,
        albumName: String? = null,
    ): ImmichUploadReport {
        val server = ImmichServer.of(serverUrl, apiKey) ?: return ImmichUploadReport(ImmichUploadStatus.NOT_CONFIGURED)
        val candidates = uploadableMedia(media, includeVideos).ifEmpty { return ImmichUploadReport(ImmichUploadStatus.NOTHING_TO_DO) }
        val ids = candidates.map { it.id }.toSet()
        // A log that cannot be read must not stop an upload: at worst Immich answers "duplicate".
        val pending = orNull { log.pending(ids) } ?: ids
        val todo = candidates.filter { it.id in pending }.ifEmpty { return ImmichUploadReport(ImmichUploadStatus.NOTHING_TO_DO) }
        val results = todo.map { sendOne(server, it) }
        fileInAlbum(server, albumName, results.mapNotNull { it?.assetId })
        return reportOf(results)
    }

    /** Fetches one file and hands it over, noting it as sent; null when either half failed. */
    private suspend fun sendOne(server: ImmichServer, media: UploadableMedia): ImmichUploadResult? = orNull {
        val file = http.download(media.url.toHttpUrl())
        if (file.code != 200) return@orNull null
        client.upload(server, file.bytes, media.fileName, media.id, clock()).also { result ->
            orNull { log.record(media.id, result.assetId) }
        }
    }

    /**
     * Files the uploaded assets in the album named [albumName]. The assets are
     * in the library whatever happens here; XTA let a failure while filing
     * throw out of the run and lose its report, which this does not.
     */
    private suspend fun fileInAlbum(server: ImmichServer, albumName: String?, assetIds: List<String>) {
        val name = albumName?.trim().orEmpty()
        if (name.isEmpty() || assetIds.isEmpty()) return
        val albumId = client.ensureAlbum(server, name) ?: return
        orNull { client.addToAlbum(server, albumId, assetIds) }
    }

    private fun reportOf(results: List<ImmichUploadResult?>): ImmichUploadReport {
        val uploaded = results.count { it?.outcome == ImmichUploadOutcome.CREATED }
        val duplicates = results.count { it?.outcome == ImmichUploadOutcome.DUPLICATE }
        val failures = results.count { it == null }
        return when {
            uploaded == 0 && duplicates == 0 -> ImmichUploadReport(ImmichUploadStatus.FAILED, failures = failures)
            else -> ImmichUploadReport(ImmichUploadStatus.DONE, uploaded, duplicates, failures)
        }
    }
}
