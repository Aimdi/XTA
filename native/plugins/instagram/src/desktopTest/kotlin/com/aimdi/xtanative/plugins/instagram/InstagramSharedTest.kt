package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Instagram in the app's Saved and its groups' feeds, as XTA keeps it. */
class InstagramSharedTest {
    private val author = InstagramAuthor(username = "NatGeo", fullName = "National Geographic", avatarUrl = "https://cdn/natgeo.jpg")
    private val carousel = InstagramPost(
        id = "1",
        shortcode = "ABC",
        caption = " A view ",
        createdAt = Instant.parse("2026-11-04T08:00:00Z"),
        author = author,
        likeCount = 42,
        carouselUrls = listOf("https://cdn/1.jpg", "https://cdn/2.jpg"),
    )

    @Test
    fun aPostIsKeptAsXtasLinkRecordOfItsPage() {
        val archive = InstagramShared.archive(carousel)
        assertEquals("instagram:https://www.instagram.com/p/ABC/", archive.id)
        assertEquals("NatGeo", archive.authorId)
        val kept = SavedContent.of(archive.content)
        assertEquals(SavedContent.Kind.LINK, kept.kind)
        assertEquals("instagram", kept.network)
        assertEquals(listOf("https://cdn/1.jpg", "https://cdn/2.jpg"), kept.photos.map { it.url })
        assertTrue(kept.matches("a view"))
    }

    @Test
    fun theSharedRowShowsTheSlidesAndAVideoByItsCoverOpeningOnTheSite() {
        val shown = InstagramShared.post(carousel)
        assertEquals("natgeo", shown.authorId, "what the group's member and the profile are named by")
        assertEquals(listOf(TextRun.Plain("A view")), shown.text)
        assertEquals(listOf("https://cdn/1.jpg", "https://cdn/2.jpg"), shown.media.map { it.imageUrl })
        assertEquals(42L, shown.likes)

        val reel = InstagramShared.post(carousel.copy(id = "2", shortcode = "R", isVideo = true, carouselUrls = emptyList(), coverUrl = "https://cdn/cover.jpg", caption = ""))
        val cover = reel.media.single()
        assertEquals(MediaItem.Kind.VIDEO, cover.kind)
        assertEquals("https://www.instagram.com/reel/R/", cover.webUrl)
        assertEquals(null, cover.videoUrl, "Instagram serves its videos to its own players alone")
        assertTrue(reel.text.isEmpty())
    }
}
