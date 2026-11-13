package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruPostRef
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BooruRoutesTest {
    private val danbooru = "https://danbooru.donmai.us"
    private val site = SiteRef(danbooru, BooruEngine.DANBOORU)
    private val ref = BooruPostRef(danbooru, BooruEngine.DANBOORU, "42")

    @Test
    fun everyRouteReadsBackAsTheScreenItNamed() {
        assertEquals(BooruRoute.Post(ref), BooruRoutes.parse(BooruRoutes.post(ref)))
        assertEquals(BooruRoute.Viewer("k", 3, ref), BooruRoutes.parse(BooruRoutes.viewer("k", 3, post(id = "42", host = danbooru))))
        assertEquals(BooruRoute.Tag("kei_(keigarou)", BooruTagCategory.ARTIST, site), BooruRoutes.parse(BooruRoutes.tag("kei_(keigarou)", BooruTagCategory.ARTIST, site)))
        assertEquals(BooruRoute.Tag("solo", null, null), BooruRoutes.parse(BooruRoutes.tag("solo", null, null)))
        assertEquals(BooruRoute.Pool("7", "Best & brightest = 100%", site), BooruRoutes.parse(BooruRoutes.pool("7", "Best & brightest = 100%", site)))
        assertEquals(BooruRoute.Search("初音ミク -comic rating:g", "solo"), BooruRoutes.parse(BooruRoutes.search("初音ミク -comic rating:g", "solo")))
        assertEquals(BooruRoute.Search("", null), BooruRoutes.parse(BooruRoutes.search(null)))
    }

    @Test
    fun aRouteThisPluginDoesNotHaveIsNone() {
        assertNull(BooruRoutes.parse("settings?x=1"))
        assertNull(BooruRoutes.parse("post?host=https%3A%2F%2Fdanbooru.donmai.us&engine=nope&id=1"))
        assertNull(BooruRoutes.parse("tag?name="))
    }

    @Test
    fun aLinkToAPostOnAKnownSiteOpensItsScreen() {
        val board = BooruPreset("custom:board.example", "Board", BooruEngine.MOEBOORU, "https://board.example")
        val sites = BooruPresets.ALL + board
        assertEquals(BooruRoutes.post(ref), BooruRoutes.forUrl(" https://danbooru.donmai.us/posts/42 ", sites))
        assertEquals(BooruRoutes.post(BooruPostRef(board.host, BooruEngine.MOEBOORU, "9")), BooruRoutes.forUrl("https://board.example/post/show/9", sites))
        assertNull(BooruRoutes.forUrl("https://board.example/post/show/9", BooruPresets.ALL))
        assertNull(BooruRoutes.forUrl("https://example.com/posts/42", sites))
    }

    @Test
    fun aPostsPageIsWhereItsHostShowsIt() {
        assertEquals("https://yande.re/post/show/5", BooruRoutes.pageUrl(BooruPostRef("https://yande.re", BooruEngine.MOEBOORU, "5")))
        assertEquals("https://gelbooru.com/index.php?page=post&s=view&id=5", BooruRoutes.pageUrl(BooruPostRef("https://gelbooru.com", BooruEngine.GELBOORU_V2, "5")))
    }
}
