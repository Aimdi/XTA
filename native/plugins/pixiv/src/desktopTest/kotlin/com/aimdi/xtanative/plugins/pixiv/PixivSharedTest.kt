package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pixiv's works among other networks' posts, and in the app's Saved as XTA keeps them. */
class PixivSharedTest {
    private val illust = work(42, userId = 3).copy(
        title = "Sky",
        caption = "Drawn at dawn",
        pageUrls = listOf("https://i.pximg.net/img-master/img/42_p0.jpg", "https://i.pximg.net/img-master/img/42_p1.jpg"),
        userAvatarUrl = "https://i.pximg.net/user-profile/img/3.jpg",
        width = 1200,
        height = 800,
        totalBookmarks = 500,
        totalViews = 9000,
        totalComments = 7,
        createdAt = Instant.parse("2026-10-30T12:00:00Z"),
    )

    @Test
    fun aWorkIsKeptAsXtasLinkRecordWithEveryPage() {
        val archive = PixivShared.archive(illust)
        assertEquals("pixiv:https://www.pixiv.net/artworks/42", archive.id)
        assertEquals("Artist 3", archive.authorId)
        val record = J(archive.content)
        assertEquals("link", record["xtaPlugin"].string)
        assertEquals("Sky\n\nDrawn at dawn", record["text"].string, "title and caption, a paragraph apart")
        assertEquals(illust.viewerUrls, record["images"].list.map { it.string })
        assertEquals("pixiv", SavedContent.of(archive.content).network, "Saved hands it to this plugin")
        assertEquals("Sky", J(PixivShared.archive(illust.copy(caption = " ")).content)["text"].string, "a blank caption is left out")
    }

    @Test
    fun aNovelIsKeptWithItsCover() {
        val kept = novel(9).copy(caption = "A tale", coverUrl = "https://i.pximg.net/novel-cover/9.jpg")
        val record = J(PixivShared.archive(kept).content)
        assertEquals("https://www.pixiv.net/novel/show.php?id=9", record["url"].string)
        assertEquals("Writer 2", record["author"].string)
        assertEquals("Novel 9\n\nA tale", record["text"].string)
        assertEquals(listOf("https://i.pximg.net/novel-cover/9.jpg"), record["images"].list.map { it.string })
    }

    @Test
    fun theSharedRowShowsTheWorksFirstPageFromTheReadersServer() {
        val shown = PixivShared.post(illust, PixivImages.MIRROR_HOST)
        assertEquals("42", shown.id)
        assertEquals("3", shown.authorId, "the creator, as the groups hold them")
        assertEquals("Artist 3", shown.authorName)
        assertEquals("artist3", shown.authorHandle)
        assertEquals("Sky", shown.title)
        assertEquals(listOf("https://i.pixiv.re/img-master/img/42_p0.jpg"), shown.media.map { it.imageUrl }, "the first page alone, as the tile shows it")
        assertEquals(1.5f, shown.media.single().aspectRatio)
        assertEquals("https://i.pixiv.re/user-profile/img/3.jpg", shown.avatarUrl)
        assertEquals(500L, shown.likes)
        assertEquals(7L, shown.replies)
        assertEquals(9000L, shown.views)
        assertEquals(illust.createdAt, shown.createdAt)
    }

    @Test
    fun theCreatorsPictureComesFromPixivsOwnServerToo() {
        val shown = PixivShared.post(illust, PixivImages.HOST)
        assertEquals("https://i.pximg.net/user-profile/img/3.jpg", shown.avatarUrl, "the shared avatar asks with Pixiv's Referer")
        assertEquals("https://i.pximg.net/img-master/img/42_p0.jpg", shown.media.single().imageUrl)
    }

    @Test
    fun savedDrawsAKeptWorkTitleOverCaption() {
        val archive = PixivShared.record(PixivShared.archive(illust).content)!!
        val shown = PixivShared.saved(archive, PixivImages.MIRROR_HOST)
        assertEquals(archive.id, shown.id, "what the row files and takes out")
        assertEquals("Sky", shown.title)
        assertEquals(listOf(TextRun.Plain("Drawn at dawn")), shown.text)
        assertEquals("Artist 3", shown.authorName)
        assertEquals(listOf("https://i.pixiv.re/img-master/img/42_p0.jpg", "https://i.pixiv.re/img-master/img/42_p1.jpg"), shown.media.map { it.imageUrl })
        assertEquals("https://www.pixiv.net/artworks/42", shown.url)
    }

    @Test
    fun anotherPluginsRecordIsNoneOfPixivs() {
        val booru = PluginPostArchive.link("booru", "https://danbooru.donmai.us/posts/1", "x", "y")
        assertNull(PixivShared.record(booru.content))
    }
}
