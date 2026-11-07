package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.post
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the viewer says under a post (booru_viewer_test.dart's facts). */
class BooruPostFactsTest {
    private fun bare(updatedAt: Instant? = null): BooruPost =
        post(rating = null, width = 0, height = 0, fileExt = null).copy(score = null, updatedAt = updatedAt)

    @Test
    fun factsLeaveOutWhatTheHostDidNotSend() {
        assertEquals(emptyList(), postFacts(bare(), "en", Locale.ENGLISH))
    }

    @Test
    fun aTimeThePostLastChangedIsShownAsUpdatedNotPosted() {
        val fact = postFacts(bare(Instant.parse("2024-01-02T03:04:00Z")), "en", Locale.ENGLISH, ZoneOffset.UTC).single()
        assertTrue(fact.text.startsWith("Updated "), fact.text)
        assertTrue("2024" in fact.text, fact.text)
    }

    @Test
    fun thumbnailsAndFullScreenUseTheChosenFileSize() {
        val picture = post(previewUrl = "https://cdn.example/p.jpg", sampleUrl = "https://cdn.example/s.jpg", fileUrl = "https://cdn.example/o.png")
        assertEquals(picture.displayUrl, BooruDisplay().viewerUrl(picture))
        assertEquals("https://cdn.example/o.png", BooruDisplay(originalInViewer = true).viewerUrl(picture))
        assertEquals(picture.thumbnailUrl, BooruDisplay(smallThumbnails = true).tileUrl(picture, 900))
    }
}
