package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Sending a kept post's media to Immich, as XTA's ImmichUploader does when a post is filed in an uploading folder. */
class ImmichUploaderTest {
    private val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/AbC123.jpg", width = null, height = null)
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/T.jpg",
        width = null,
        height = null,
        variants = listOf(XVideoVariant("https://video.twimg.com/x/1280x720/high.mp4", 2_176_000, "video/mp4")),
    )
    private val at = Instant.parse("2026-07-25T10:00:00Z")

    /** The upload log, in memory: what was sent, with its asset ids. */
    private class Log(already: Set<String> = emptySet(), private val unreadable: Boolean = false) : ImmichUploadLog {
        val recorded = linkedMapOf<String, String?>().apply { already.forEach { put(it, null) } }

        override suspend fun pending(ids: Set<String>): Set<String> {
            if (unreadable) throw IllegalStateException("no such table: immich_upload")
            return ids - recorded.keys
        }

        override suspend fun record(mediaId: String, assetId: String?) {
            recorded[mediaId] = assetId
        }
    }

    /** An Immich that creates the photo, already has the video, and keeps one album, "Art". */
    private fun immich(album: (Sent) -> XResponse = { json(fixture("album_assets.json")) }) = FakeImmich { sent ->
        when {
            sent.url.encodedPath == "/api/assets" && "name=\"deviceAssetId\"\r\n\r\n${photo.url}\r\n" in sent.text -> json(fixture("asset_created.json"), 201)
            sent.url.encodedPath == "/api/assets" -> json(fixture("asset_duplicate.json"))
            sent.method == "GET" -> json(fixture("albums.json"))
            else -> album(sent)
        }
    }

    private suspend fun upload(http: FakeImmich, log: Log = Log(), media: List<XMedia> = listOf(photo, video), album: String? = null) =
        ImmichUploader(http, log, clock = { at }).upload(media, "http://immich.local:2283", "key", albumName = album)

    @Test
    fun withoutAnAddressOrAKeyNothingIsFetchedOrSent() = runBlocking {
        val http = FakeImmich()
        for ((address, key) in listOf("" to "", "http://immich.local:2283" to " ", "not a url" to "key")) {
            val report = ImmichUploader(http, Log()).upload(listOf(photo), address, key)
            assertEquals(ImmichUploadReport(ImmichUploadStatus.NOT_CONFIGURED), report)
        }
        assertTrue(http.sent.isEmpty() && http.downloaded.isEmpty())
    }

    @Test
    fun aPostWithNothingToUploadIsNothingToDo() = runBlocking {
        val http = FakeImmich()
        assertEquals(ImmichUploadStatus.NOTHING_TO_DO, upload(http, media = emptyList()).status)
        val photosOnly = ImmichUploader(http, Log()).upload(listOf(video), "http://immich.local:2283", "key", includeVideos = false)
        assertEquals(ImmichUploadStatus.NOTHING_TO_DO, photosOnly.status)
        assertTrue(http.sent.isEmpty())
    }

    @Test
    fun eachFileIsFetchedSentAndNotedWithItsAsset() = runBlocking {
        val http = immich()
        val log = Log()
        val report = upload(http, log)

        assertEquals(ImmichUploadReport(ImmichUploadStatus.DONE, uploaded = 1, duplicates = 1), report)
        assertEquals(listOf("https://pbs.twimg.com/media/AbC123.jpg:orig", "https://video.twimg.com/x/1280x720/high.mp4"), http.downloaded.map { it.toString() })
        assertEquals<Map<String, String?>>(
            mapOf(photo.url to "f5a3c9e2-7b1d-4e8f-a0c6-3d9b2e4f6a18", video.url to "0c7e5a3f-2d9b-4f1e-8a6c-b4d2e0f8a1c3"),
            log.recorded,
        )
        val first = http.sent.first().text
        assertTrue("filename=\"AbC123.jpg\"" in first, "the file goes up under its own name")
        assertTrue("name=\"fileCreatedAt\"\r\n\r\n2026-07-25T10:00:00.000Z\r\n" in first, "stamped with the time of the upload")
    }

    @Test
    fun whatTheLogAlreadyHasIsNotSentAgain() = runBlocking {
        val http = immich()
        val report = upload(http, Log(already = setOf(photo.url)))

        assertEquals(ImmichUploadReport(ImmichUploadStatus.DONE, duplicates = 1), report)
        assertEquals(listOf("https://video.twimg.com/x/1280x720/high.mp4"), http.downloaded.map { it.toString() })
        assertEquals(ImmichUploadStatus.NOTHING_TO_DO, upload(http, Log(already = setOf(photo.url, video.url))).status)
    }

    @Test
    fun aLogThatCannotBeReadDoesNotStopAnUpload() = runBlocking {
        assertEquals(ImmichUploadStatus.DONE, upload(immich(), Log(unreadable = true)).status)
    }

    @Test
    fun aFileThatCannotBeFetchedIsAFailureAndTheOthersStillGo() = runBlocking {
        val http = immich().apply { files = { if (it.host == "pbs.twimg.com") ImmichDownload(404, ByteArray(0)) else ImmichDownload(200, byteArrayOf(9)) } }
        val log = Log()
        val report = upload(http, log)

        assertEquals(ImmichUploadReport(ImmichUploadStatus.DONE, duplicates = 1, failures = 1), report)
        assertEquals(setOf(video.url), log.recorded.keys, "the file that never went up is tried again next time")
    }

    @Test
    fun aRunWhereNothingWentUpHasFailed() = runBlocking {
        val report = upload(FakeImmich { XResponse(500, "{}") })
        assertEquals(ImmichUploadReport(ImmichUploadStatus.FAILED, failures = 2, problem = ImmichProblem.SERVER), report)
    }

    @Test
    fun aRefusedKeyStopsTheRunBeforeTheNextFileIsFetched() = runBlocking {
        val http = FakeImmich { json("""{"message":"Invalid API key"}""", 401) }
        val report = upload(http)
        assertEquals(ImmichUploadReport(ImmichUploadStatus.FAILED, failures = 2, problem = ImmichProblem.UNAUTHORIZED), report)
        assertEquals(1, http.downloaded.size, "the video is not fetched from X only to be refused")
        assertEquals(1, http.sent.size)
    }

    @Test
    fun anUnreachableServerStopsTheRunToo() = runBlocking {
        val http = FakeImmich { throw IOException("no route to host") }
        val report = upload(http)
        assertEquals(ImmichProblem.NETWORK, report.problem)
        assertEquals(1, http.downloaded.size)
    }

    @Test
    fun aFileThatTookTooLongDoesNotStopTheNext() = runBlocking {
        val http = FakeImmich { throw java.io.InterruptedIOException("timeout") }
        upload(http)
        assertEquals(2, http.downloaded.size)
    }

    @Test
    fun theUploadsAreFiledInTheAlbumNamedForTheFolder() = runBlocking {
        val http = immich()
        upload(http, album = "  Art ")

        val filed = http.sent.last()
        assertEquals("PUT", filed.method)
        assertEquals("/api/albums/6a1b2c3d-0000-4000-8000-000000000003/assets", filed.url.encodedPath)
        assertEquals(listOf("f5a3c9e2-7b1d-4e8f-a0c6-3d9b2e4f6a18", "0c7e5a3f-2d9b-4f1e-8a6c-b4d2e0f8a1c3"), J.parse(filed.text)["ids"].list.map { it.string })
    }

    @Test
    fun noAlbumAskedForIsNoAlbumRequest() = runBlocking {
        for (album in listOf(null, "", "   ")) {
            val http = immich()
            upload(http, album = album)
            assertEquals(listOf("/api/assets", "/api/assets"), http.paths, "album: $album")
        }
    }

    @Test
    fun aFailureWhileFilingInTheAlbumDoesNotLoseTheUploadsWhereXtaThrew() = runBlocking {
        val http = immich(album = { throw IOException("connection reset") })
        val report = upload(http, album = "Art")
        assertEquals(ImmichUploadReport(ImmichUploadStatus.DONE, uploaded = 1, duplicates = 1), report)
    }
}
