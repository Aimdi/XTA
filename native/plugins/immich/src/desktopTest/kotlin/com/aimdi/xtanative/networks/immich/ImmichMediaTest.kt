package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Which files of a kept post go to the photo library, and under what name: XTA's immich_media_test. */
class ImmichMediaTest {
    private fun photo(url: String = "https://pbs.twimg.com/media/AbC123.jpg") = XMedia(XMedia.Kind.PHOTO, url, width = null, height = null)

    private fun video(
        thumb: String = "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/T.jpg",
        variants: List<XVideoVariant> = listOf(
            XVideoVariant("https://video.twimg.com/x/320x180/low.mp4", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/x/1280x720/high.mp4", 2_176_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/x/pl/master.m3u8", null, "application/x-mpegURL"),
        ),
        kind: XMedia.Kind = XMedia.Kind.VIDEO,
    ) = XMedia(kind, thumb, width = null, height = null, variants = variants)

    @Test
    fun theOriginalIsAskedForNotTheDisplayCopy() {
        val media = uploadableMedia(listOf(photo())).single()
        assertTrue(media.url.endsWith("/AbC123.jpg:orig"), media.url)
        assertFalse(media.isVideo)
    }

    @Test
    fun theNameIsTheFileWithoutTheSizeSuffix() {
        assertEquals("AbC123.jpg", uploadableMedia(listOf(photo())).single().fileName)
    }

    @Test
    fun theIdIsThePlainUrlSoOrigDoesNotChangeIt() {
        val media = uploadableMedia(listOf(photo())).single()
        assertEquals("https://pbs.twimg.com/media/AbC123.jpg", media.id)
        assertNotEquals(media.url, media.id)
    }

    @Test
    fun aPictureFromAnotherNetworkIsSentAsItIs() {
        val media = uploadableMedia(listOf(photo("https://cdn.bsky.app/img/feed_fullsize/plain/did/abc@jpeg"))).single()
        assertEquals("https://cdn.bsky.app/img/feed_fullsize/plain/did/abc@jpeg", media.url)
        assertEquals("abc@jpeg", media.fileName)
    }

    @Test
    fun aPhotoTheResponseCarriedNoUrlForIsSkippedNotUploadedEmpty() {
        assertTrue(uploadableMedia(listOf(photo(url = ""))).isEmpty())
    }

    @Test
    fun theBestMp4WinsAndThePlaylistIsNeverChosen() {
        val media = uploadableMedia(listOf(video())).single()
        assertEquals("https://video.twimg.com/x/1280x720/high.mp4", media.url)
        assertEquals("high.mp4", media.fileName)
        assertTrue(media.isVideo)
    }

    @Test
    fun theIdIsTheThumbnailSoItDoesNotMoveWithTheVariant() {
        // Which variant wins depends on what the response offered that day; an id that moved with it would upload the video again.
        assertTrue(uploadableMedia(listOf(video())).single().id.endsWith("/T.jpg"))
    }

    @Test
    fun aGifIsAVideoByTheTimeItIsAFile() {
        val media = uploadableMedia(listOf(video(kind = XMedia.Kind.GIF))).single()
        assertTrue(media.isVideo)
        assertTrue(media.url.endsWith(".mp4"))
    }

    @Test
    fun aVariantListWithNoUsableMp4IsNothingToUpload() {
        val unusable = listOf(
            XVideoVariant("https://video.twimg.com/x/pl/master.m3u8", null, "application/x-mpegURL"),
            XVideoVariant("https://video.twimg.com/x/no-bitrate.mp4", null, "video/mp4"),
        )
        assertTrue(uploadableMedia(listOf(video(variants = unusable))).isEmpty())
    }

    @Test
    fun aVideoWithNoThumbnailFallsBackToItsOwnUrlForAnId() {
        assertTrue("1280x720" in uploadableMedia(listOf(video(thumb = ""))).single().id)
    }

    @Test
    fun videosAreSkippedWhenOnlyPhotosAreWanted() {
        val media = uploadableMedia(listOf(photo(), video(), video(kind = XMedia.Kind.GIF)), includeVideos = false)
        assertFalse(media.single().isVideo)
    }

    @Test
    fun aPostWithNothingAttachedIsNothingToDo() {
        assertTrue(uploadableMedia(emptyList()).isEmpty())
    }

    @Test
    fun aPostWithSeveralFilesKeepsThemAllInOrder() {
        val media = uploadableMedia(listOf(photo("https://pbs.twimg.com/media/one.jpg"), video()))
        assertEquals(listOf(false, true), media.map { it.isVideo })
    }
}
