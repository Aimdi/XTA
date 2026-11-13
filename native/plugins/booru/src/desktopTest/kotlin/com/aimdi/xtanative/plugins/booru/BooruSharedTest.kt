package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A booru post among other networks' posts, and as Saved keeps and draws it. */
class BooruSharedTest {
    private val picture = post(
        id = "123",
        host = "https://danbooru.donmai.us",
        tags = listOf("hatsune_miku", "solo", "smile", "blue_hair", "twintails", "long_hair", "microphone"),
        score = 42,
        width = 800,
        height = 1200,
        previewUrl = "https://cdn.donmai.us/preview/abcd.jpg",
        sampleUrl = "https://cdn.donmai.us/sample/abcd.jpg",
        fileUrl = "https://cdn.donmai.us/original/abcd.png",
        fileExt = "png",
    )

    @Test
    fun theSharedRowShowsWhatTheCardDoes() {
        val shown = BooruShared.post(picture.copy(createdAt = Instant.parse("2026-11-01T10:00:00Z")), "Booru #123")
        assertEquals("https://danbooru.donmai.us:123", shown.id)
        assertEquals("Booru #123", shown.authorName)
        assertEquals("danbooru.donmai.us", shown.authorHandle)
        assertEquals(listOf(TextRun.Plain("hatsune_miku solo smile blue_hair twintails long_hair")), shown.text, "its first six tags")
        assertEquals(42L, shown.likes, "its score")
        assertEquals("https://danbooru.donmai.us/posts/123", shown.url)
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), shown.createdAt)
        val media = shown.media.single()
        assertEquals("https://cdn.donmai.us/sample/abcd.jpg", media.imageUrl)
        assertEquals(800f / 1200f, media.aspectRatio)
    }

    @Test
    fun aPostWithoutADateStillShowsAtTheEnd() {
        assertEquals(Instant.EPOCH, BooruShared.post(picture, "Booru #123").createdAt, "as XTA keeps a Gelbooru post without one")
        assertEquals(0L, BooruShared.post(picture.copy(score = -3), "x").likes)
    }

    @Test
    fun aVideoShowsItsStillAndPlaysItsFile() {
        val clip = post(id = "7", previewUrl = "https://cdn.example/7.jpg", fileUrl = "https://cdn.example/7.webm", fileExt = "webm")
        val media = BooruShared.media(clip)!!
        assertEquals(MediaItem.Kind.VIDEO, media.kind)
        assertEquals("https://cdn.example/7.jpg", media.imageUrl)
        assertEquals("https://cdn.example/7.webm", media.videoUrl)
        assertNull(BooruShared.media(post(id = "8")), "a post the host gave no file of")
    }

    @Test
    fun savedDrawsAKeptPostByItsRecord() {
        val archive = PluginPostArchive.ofLink(BooruArchive.of(picture).content, BooruArchive.SOURCE)!!
        val shown = BooruShared.saved(archive, BooruPresets.ALL) { id -> "Booru #$id" }
        assertEquals("booru:https://danbooru.donmai.us/posts/123", shown.id, "what the row files and takes out")
        assertEquals("Booru #123", shown.authorName)
        assertEquals("danbooru.donmai.us", shown.authorHandle)
        assertEquals(listOf(TextRun.Plain(picture.tagLine)), shown.text, "every tag the record keeps")
        assertEquals(listOf("https://cdn.donmai.us/sample/abcd.jpg"), shown.media.map { it.imageUrl })
        assertEquals("https://danbooru.donmai.us/posts/123", shown.url)
        assertEquals("booru", SavedContent.of(archive.content).network, "Saved hands it to this plugin")
    }

    @Test
    fun aRecordOfAnUnknownSiteIsNamedByItsSite() {
        val elsewhere = PluginPostArchive.link("booru", "https://booru.example/posts/9", "https://booru.example", "solo")
        val shown = BooruShared.saved(PluginPostArchive.ofLink(elsewhere.content, "booru")!!, BooruPresets.ALL) { id -> "Booru #$id" }
        assertEquals("booru.example", shown.authorName)
    }
}
