package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.settings.MediaGridLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/** XTA's media grids (media_grid.dart, media_grid_item.dart, media_grid_lightbox.dart): their tiles, layouts and viewer. */
class MediaGridRulesTest {
    private val maya = XUser("1", "maya", "Maya Rivers")
    private val jo = XUser("2", "jo", "Jo")

    private fun photo(width: Int = 100, height: Int = 100, name: String = "a") = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/$name.jpg", width, height)

    private val recorded = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/v.jpg",
        16,
        9,
        variants = listOf(XVideoVariant("https://video.twimg.com/v.mp4", 832_000, "video/mp4")),
    )

    private fun post(id: String, media: List<XMedia> = emptyList(), live: XLive? = null, by: XUser = maya) = XPost(id, by, null, "text", media = media, live = live)

    // ---- tiles ----

    @Test
    fun eachPostsPicturesAreTilesAndARepostsAreThePostReposted() {
        val own = post("10", listOf(photo(), photo(name = "b")))
        val original = post("20", listOf(photo()), by = jo)
        val repost = XPost("21", maya, null, "", reposted = original)
        val tiles = MediaGridItems.of(listOf(XThread("10", listOf(own)), XThread("21", listOf(repost))))
        assertEquals(listOf("10/0", "10/1", "20/0"), tiles.map { it.key })
        assertEquals(listOf("maya", "maya", "jo"), tiles.map { it.post.author.screenName })
        assertEquals(listOf(ViewerPage(photo(), "20", "jo", 0)), listOfNotNull(tiles[2].page))
    }

    @Test
    fun aBroadcastIsOneTileThatPlaysItsRecordingOrOpensItsPost() {
        val vod = MediaGridItems.of(post("3", listOf(photo(), recorded), live = XLive(broadcastId = "1abc"))).single()
        assertEquals(MediaGridItem.Kind.BROADCAST, vod.kind)
        assertEquals("3/0", vod.key)
        assertEquals(ViewerPage(recorded, "3", "maya", 1), vod.page, "Open post scrolls to the video")
        val live = MediaGridItems.of(post("4", listOf(recorded.copy(variants = emptyList())), live = XLive(broadcastId = "1live"))).single()
        assertNull(live.page, "nothing to play here: its post opens")
        val space = MediaGridItems.of(post("5", live = XLive(spaceId = "1room", imageUrl = "https://card/space.jpg"))).single()
        assertTrue(space.isSpace)
        assertEquals("https://card/space.jpg", space.thumbnailUrl)
        assertEquals(listOf(MediaGridItem.Kind.GIF), MediaGridItems.of(post("6", listOf(photo().copy(kind = XMedia.Kind.GIF)))).map { it.kind })
    }

    @Test
    fun aLivestreamsCardIsTitledByWhatItsPostSaysWithoutItsLinks() {
        fun card(text: String) = MediaGridItems.of(XPost("7", maya, null, text, live = XLive(broadcastId = "1b"))).single()
        assertEquals("Live now: the final", card("Live now:\n  the  final https://t.co/abc").excerpt)
        assertNull(card("https://t.co/abc ").excerpt, "a post that only links says the broadcast's kind instead")
        assertTrue(MediaGridLook.BROADCASTS.broadcastCards)
        assertFalse(MediaGridLook.of(MediaGridLayout.FEED, 3).broadcastCards)
    }

    // ---- layouts ----

    @Test
    fun theLayoutFollowsTheMediaSettings() {
        assertEquals(3, MediaGridLook.of(MediaGridLayout.MASONRY, 3).columns)
        assertEquals(5, MediaGridLook.of(MediaGridLayout.MASONRY, 9).columns)
        assertEquals(1, MediaGridLook.of(MediaGridLayout.MASONRY, 0).columns)
        val two = MediaGridLook.of(MediaGridLayout.TWO_COLUMNS, 4)
        assertEquals(2, two.columns)
        assertEquals(1.5f, two.maxAspect, "a roomier two-column masonry")
        val timeline = MediaGridLook.of(MediaGridLayout.FEED, 3)
        assertEquals(1, timeline.columns, "one picture a row, a timeline without text")
        assertEquals(GridPadding(16f, 8f, 16f, 16f), timeline.padding)
    }

    @Test
    fun aTileKeepsItsShapeWithinItsLayoutsBounds() {
        val masonry = MediaGridLook.of(MediaGridLayout.MASONRY, 3)
        fun tile(media: XMedia) = MediaGridItems.of(post("1", listOf(media))).single()
        assertEquals(1.5f, masonry.aspectOf(tile(photo(300, 200))))
        assertEquals(0.6f, masonry.aspectOf(tile(photo(100, 400))), "3:5 at the tallest")
        assertEquals(2f, masonry.aspectOf(tile(photo(1000, 100))))
        assertEquals(16f / 9f, masonry.aspectOf(tile(photo(100, 100).copy(width = null))), "16:9 when X sent no size")
        assertEquals(0.8f, MediaGridLook.of(MediaGridLayout.FEED, 3).aspectOf(tile(photo(100, 400))))
        assertEquals(16f / 9f, MediaGridLook.BROADCASTS.aspectOf(tile(photo(100, 400))))
    }

    // ---- the masonry ----

    @Test
    fun eachTileGoesInTheShortestColumnInItsOrder() {
        val masonry = Masonry.of(listOf(1f, 0.5f, 1f, 2f), columns = 2)
        assertEquals(
            listOf(MasonryCell(0, 0f, 1f), MasonryCell(1, 0f, 2f), MasonryCell(0, 1f, 1f), MasonryCell(0, 2f, 0.5f)),
            masonry.cells,
            "a tie goes to the first column",
        )
        assertEquals(2.5f, masonry.height)
    }

    @Test
    fun oneColumnIsATileAStripe() {
        val masonry = Masonry.of(listOf(1f, 2f, 0.5f), columns = 1)
        assertEquals(listOf(MasonrySlice(0f, 1f, listOf(0)), MasonrySlice(1f, 1.5f, listOf(1)), MasonrySlice(1.5f, 3.5f, listOf(2))), masonry.slices)
    }

    private val shapes = listOf(1f, 0.6f, 1.5f, 0.8f, 2f, 1f, 0.75f, 1.33f, 0.6f, 1.78f, 1f, 0.9f, 1.2f, 0.66f, 2f, 1f, 0.8f, 1.5f, 1.1f, 0.7f, 1.9f, 1f, 0.6f, 1.4f)

    @Test
    fun theStripesCoverTheGridAndHoldEveryTileTheyCross() {
        val masonry = Masonry.of(shapes, columns = 3)
        val slices = masonry.slices
        assertEquals(0f, slices.first().top)
        assertEquals(masonry.height, slices.last().bottom)
        slices.zipWithNext().forEach { (above, below) -> assertEquals(above.bottom, below.top) }
        slices.forEach { slice ->
            val crossing = masonry.cells.indices.filter { masonry.cells[it].top < slice.bottom && masonry.cells[it].bottom > slice.top }
            assertEquals(crossing, slice.cells)
        }
        assertEquals(masonry.cells.indices.toSet(), slices.flatMap { it.cells }.toSet())
        assertTrue(slices.size > 2, "a long grid is several stripes, drawn as they come into view")
    }

    @Test
    fun theStripesCutStayAsTheyAreWhenMoreTilesCome() {
        val before = Masonry.of(shapes.take(15), columns = 3).slices
        val after = Masonry.of(shapes, columns = 3).slices
        assertEquals(before.dropLast(1), after.take(before.size - 1), "only the last, which holds the rest, changes")
    }

    @Test
    fun aCutFallsWhereItSplitsTheFewestTiles() {
        val squares = Masonry.of(List(12) { 1f }, columns = 3)
        assertTrue(squares.slices.all { slice -> slice.cells.all { squares.cells[it].top >= slice.top && squares.cells[it].bottom <= slice.bottom } }, "rows of squares are never cut")
    }

    // ---- the viewer over a grid ----

    private val grid = listOf(
        MediaGridItems.of(post("1", listOf(photo()))),
        MediaGridItems.of(post("2", listOf(recorded.copy(variants = emptyList())), live = XLive(broadcastId = "1live"))),
        MediaGridItems.of(post("3", listOf(recorded), by = jo)),
    ).flatten()

    @Test
    fun aGridsViewerPagesThroughEveryTileItCanShow() {
        val gallery = MediaGallery.ofGrid(grid, flowOf(grid))
        assertEquals(listOf("1", "3"), gallery.pages().map { it.postId })
        assertEquals(1, gallery.startOf(grid[2]))
        assertNull(gallery.startOf(grid[1]), "a broadcast with nothing to play opens its post")
        assertTrue(gallery.opensPosts)
        assertFalse(MediaGallery.of(gallery.pages()).opensPosts, "a post's own media need no Open post")
        assertTrue(MediaGallery.of(gallery.pages(), asIs = true).asIs)
    }

    @Test
    fun aGridsViewerReadsOnAsTheGridGrows(): Unit = runBlocking {
        val more = grid + MediaGridItems.of(post("9", listOf(photo(), photo(name = "b"))))
        val gallery = MediaGallery.ofGrid(grid, flowOf(more))
        assertEquals(2, gallery.pages().size, "what the tap saw")
        assertEquals(listOf("1", "3", "9", "9"), gallery.updates.first().map { it.postId })
    }

    @Test
    fun theViewerAsksForMoreThreePagesFromTheEnd() {
        assertFalse(MediaGallery.nearEnd(1, 5))
        assertTrue(MediaGallery.nearEnd(2, 5))
        assertTrue(MediaGallery.nearEnd(0, 1))
        assertFalse(MediaGallery.nearEnd(0, 0))
    }

    @Test
    fun aGalleryIsKeptByItsKeyAmongTheLastFew() {
        val first = MediaGallery.of(emptyList())
        val key = Galleries.put(first)
        assertEquals(first, Galleries.get(key))
        repeat(8) { Galleries.put(MediaGallery.of(emptyList())) }
        assertNull(Galleries.get(key), "let go once newer ones came")
        assertNotNull(Galleries.get(Galleries.put(first)))
        assertNull(Galleries.get("1"), "a key of an earlier run of the app names nothing")
    }
}
