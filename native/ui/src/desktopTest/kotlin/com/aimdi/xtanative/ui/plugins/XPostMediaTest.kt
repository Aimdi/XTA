package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XPostMediaTest {
    private val author = XUser("1", "trader", "Trader")
    private val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/a.jpg", 1200, 800, alt = "A chart")
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/poster.jpg",
        1280,
        720,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/low.mp4", 320_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/list.m3u8", null, "application/x-mpegURL"),
            XVideoVariant("https://video.twimg.com/high.mp4", 2_176_000, "video/mp4"),
        ),
        durationMs = 12_000,
    )

    private fun post(id: String, media: List<XMedia> = emptyList(), quoted: XPost? = null, reposted: XPost? = null) =
        XPost(id, author, createdAt = null, text = "", media = media, quoted = quoted, reposted = reposted)

    @Test
    fun findsAPostItsThreadsRepostOrQuote() {
        val quoted = post("3", listOf(photo))
        val reposted = post("2", quoted = quoted)
        val threads = listOf(XThread("t", listOf(post("1", reposted = reposted))))
        assertEquals(reposted, XPostMedia.find(threads, "2"))
        assertEquals(quoted, XPostMedia.find(threads, "3"))
        assertNull(XPostMedia.find(threads, "4"))
    }

    @Test
    fun aPhotoIsShownAsXServesIt() {
        val item = XPostMedia.items(post("1", listOf(photo))).single()
        assertEquals(MediaItem("https://pbs.twimg.com/media/a.jpg", MediaItem.Kind.PHOTO, aspectRatio = 1.5f, alt = "A chart"), item)
    }

    @Test
    fun aVideoPlaysItsBestMp4AndOpensItsPostWithoutAPlayer() {
        val item = XPostMedia.items(post("9", listOf(video))).single()
        assertEquals(MediaItem.Kind.VIDEO, item.kind)
        assertEquals("https://pbs.twimg.com/poster.jpg", item.imageUrl)
        assertEquals("https://video.twimg.com/high.mp4", item.videoUrl)
        assertEquals(12_000L, item.durationMs)
        assertEquals("https://x.com/trader/status/9", item.webUrl)
    }
}
