package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.Routes
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_downloads
import com.aimdi.xtanative.ui.generated.resources.settings_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowTitlesTest {
    private fun of(route: String?, vararg args: Pair<String, String?>) = WindowTitles.of(route) { name -> args.toMap()[name] }

    @Test
    fun aScreenNamesTheWindow() {
        assertEquals(TitlePart.Search("flutter"), of(Routes.SEARCH, "q" to " flutter "))
        assertEquals(TitlePart.Search(null), of(Routes.SEARCH, "q" to ""))
        assertEquals(TitlePart.Profile("quax_tests"), of(Routes.PROFILE, "screenName" to "quax_tests"))
        assertEquals(TitlePart.Profile("a"), of(Routes.FOLLOWERS, "screenName" to "a"))
        assertEquals(TitlePart.Named(Res.string.settings_title), of(Routes.SETTINGS_SECTION))
        assertEquals(TitlePart.Named(Res.string.home_downloads), of(Routes.DOWNLOADS))
    }

    @Test
    fun homeAndScreensWithoutANameLeaveTheAppsName() {
        assertNull(of(Routes.HOME))
        assertNull(of(Routes.COMMUNITY, "id" to "1"))
        assertNull(of(Routes.PROFILE))
        assertNull(of(null))
        assertEquals("XTA Native", WindowTitles.title(null, "XTA Native"))
        assertEquals("Downloads – XTA Native", WindowTitles.title("Downloads", "XTA Native"))
    }
}
