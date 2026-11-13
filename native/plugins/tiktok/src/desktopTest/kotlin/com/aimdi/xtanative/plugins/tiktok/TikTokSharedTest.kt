package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.tiktok.TikTokAuthor
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokVideoSource
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TikTokSharedTest {
    private val author = TikTokAuthor("dancer", "Dancer", secUid = "MS4wLj", avatarUrl = "https://p/a.jpg")
    private val video = TikTokPost(
        "7400000000000000001", "Steps #dance", Instant.parse("2026-01-02T03:04:05Z"), author,
        coverUrl = "https://p/cover.jpg", durationSeconds = 65, width = 720, height = 1280,
        diggCount = 12, commentCount = 3, shareCount = 2, playCount = 900,
        sources = listOf(TikTokVideoSource("https://v/720", "720p")),
    )

    @Test
    fun aVideoInTheSharedRowIsItsAuthorCaptionCoverAndCounts() {
        val shown = TikTokShared.post(video)

        assertEquals("7400000000000000001", shown.id)
        assertEquals("dancer", shown.authorHandle)
        assertEquals("Dancer", shown.authorName)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), shown.createdAt)
        assertEquals(listOf(TextRun.Plain("Steps #dance")), shown.text)
        val cover = shown.media.single()
        assertEquals(MediaItem.Kind.VIDEO, cover.kind)
        assertEquals("https://p/cover.jpg", cover.imageUrl)
        assertNull(cover.videoUrl, "TikTok's files play in the plugin's player, which sends what TikTok asks for")
        assertEquals(65_000L, cover.durationMs)
        assertEquals(3L, shown.replies)
        assertEquals(12L, shown.likes)
        assertEquals(900L, shown.views)
        assertEquals("https://www.tiktok.com/@dancer/video/7400000000000000001", shown.url)
    }

    @Test
    fun aPhotoPostIsItsPictureAndACaptionlessOneHasNoText() {
        val photo = TikTokShared.post(TikTokPost("2", "  ", null, author, coverUrl = "https://p/photo.jpg"))

        assertEquals(MediaItem.Kind.PHOTO, photo.media.single().kind)
        assertEquals(emptyList(), photo.text)
        assertNull(photo.views)
    }

    @Test
    fun savedKeepsXtasLinkRecord() {
        val kept = TikTokShared.archive(video)

        assertEquals("tiktok:https://www.tiktok.com/@dancer/video/7400000000000000001", kept.id)
        assertEquals("dancer", kept.authorId)
        val record = J(kept.content)
        assertEquals("link", record["xtaPlugin"].string)
        assertEquals("tiktok", record["source"].string)
        assertEquals("https://www.tiktok.com/@dancer/video/7400000000000000001", record["url"].string)
        assertEquals("dancer", record["author"].string)
        assertEquals("Steps #dance", record["text"].string)
        assertEquals(listOf("https://p/cover.jpg"), record["images"].list.mapNotNull { it.string })
        assertEquals(video.url, TikTokShared.menu(video).url)
        assertNull(TikTokShared.menu(video).author, "XTA's sheet for a video offers no group")
    }

    @Test
    fun theDrawnVideosAreTheLastOnesByTheirId() {
        val drawn = TikTokDrawn(capacity = 2)
        val first = video.copy(id = "1")
        drawn.put(first)
        drawn.put(video.copy(id = "2"))
        assertSame(first, drawn.get("1"))
        drawn.put(video.copy(id = "3"))

        assertNull(drawn.get("2"), "the one drawn longest ago and not opened since goes first")
        assertSame(first, drawn.get("1"))
    }
}
