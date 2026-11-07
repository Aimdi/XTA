package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruParser
import com.aimdi.xtanative.networks.booru.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which picture a grid tile asks for (booru_tile_variant_test.dart). */
class BooruTilesTest {
    private val danbooru = BooruParser.posts(Fixtures.json("danbooru_posts.json"), BooruEngine.DANBOORU, "https://danbooru.donmai.us").first()

    @Test
    fun aGridTileAsksForTheVariantItsPixelWidthNeeds() {
        // 150 dp at 2x is 300 px: the 360 variant is 254 px wide, so the 720 one.
        assertEquals("https://cdn.donmai.us/720x720/0f/1e/0f1e.webp", BooruDisplay().tileUrl(danbooru, pixelWidth = 300))
    }

    @Test
    fun hostsWithoutVariantsKeepThePictureTilesUseToday() {
        val bare = BooruParser.posts(
            J.parse("""[{"id":3,"file_url":"https://cdn.donmai.us/original/c.png","large_file_url":"https://cdn.donmai.us/sample/c.jpg"}]"""),
            BooruEngine.DANBOORU,
            "https://danbooru.donmai.us",
        ).single()
        assertEquals(bare.catalogUrl, BooruDisplay().tileUrl(bare, pixelWidth = 200))
    }

    @Test
    fun smallThumbnailsStillMeanThePreview() {
        assertEquals(danbooru.thumbnailUrl, BooruDisplay(smallThumbnails = true).tileUrl(danbooru, pixelWidth = 500))
    }
}
