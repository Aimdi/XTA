package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PixivRoutesTest {
    private val every = listOf(
        PixivRoute.Work(12, "3"),
        PixivRoute.Reader(12, 2, vertical = true, shelf = "4"),
        PixivRoute.User(7, "novels"),
        PixivRoute.Users(PixivUserListKind.FOLLOWERS, 7),
        PixivRoute.Users(PixivUserListKind.FOLLOWING, 0),
        PixivRoute.RecommendedUsers,
        PixivRoute.Comments(PixivKind.NOVEL, 5, "9"),
        PixivRoute.Search("青空 & sky", PixivSearchKind.NOVELS),
        PixivRoute.Series(8),
        PixivRoute.Novel(5),
        PixivRoute.NovelSeries(6),
        PixivRoute.PixivisionList,
        PixivRoute.Pixivision(10, "ja"),
        PixivRoute.Short("someone"),
        PixivRoute.History(PixivKind.NOVEL),
        PixivRoute.FavoriteTags,
        PixivRoute.Mute,
        PixivRoute.CopyTemplate,
        PixivRoute.SignIn,
        PixivRoute.Settings,
        PixivRoute.Picture("11"),
    )

    @Test
    fun everyScreenComesBackFromItsRoute() {
        every.forEach { assertEquals(it, PixivRoutes.parse(PixivRoutes.of(it))) }
    }

    @Test
    fun aRouteWithoutWhatItNeedsNamesNoScreen() {
        assertNull(PixivRoutes.parse("work"))
        assertNull(PixivRoutes.parse("work?id=0"))
        assertNull(PixivRoutes.parse("user?id=-3"))
        assertNull(PixivRoutes.parse("picture"))
        assertNull(PixivRoutes.parse("elsewhere"))
    }

    @Test
    fun pixivLinksOpenTheirScreens() {
        assertEquals(PixivRoute.Work(123), parsedUrl("https://www.pixiv.net/artworks/123"))
        assertEquals(PixivRoute.User(45, null), parsedUrl("https://www.pixiv.net/users/45"))
        assertEquals(PixivRoute.Novel(9), parsedUrl("https://www.pixiv.net/novel/show.php?id=9"))
        assertEquals(PixivRoute.NovelSeries(3), parsedUrl("https://www.pixiv.net/novel/series/3"))
        assertEquals(PixivRoute.Short("abc"), parsedUrl("https://pixiv.me/abc"))
    }

    @Test
    fun otherLinksAreNotPixivs() {
        assertNull(PixivRoutes.forUrl("https://example.com/artworks/123"))
        assertNull(PixivRoutes.forUrl("artworks/123"))
    }

    @Test
    fun aGroupsCreatorOpensOnTheirProfile() {
        assertEquals(PixivRoute.User(2841234), PixivRoutes.forAccount("2841234")?.let(PixivRoutes::parse))
        assertNull(PixivRoutes.forAccount("0"))
        assertNull(PixivRoutes.forAccount("someone"))
        assertNull(PixivRoutes.forAccount(""))
    }

    private fun parsedUrl(url: String): PixivRoute? = PixivRoutes.forUrl(url)?.let(PixivRoutes::parse)
}
