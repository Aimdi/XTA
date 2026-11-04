package com.aimdi.xtanative.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each icon's path is parsed when it is first drawn: a broken one would fail there, on screen. */
class DiscoverIconsTest {
    @Test
    fun everyIconParses() {
        val icons: List<ImageVector> = listOf(
            DiscoverIcons.Tune, DiscoverIcons.History, DiscoverIcons.DeleteSweep, DiscoverIcons.Sensors, DiscoverIcons.Radar,
            DiscoverIcons.TrendingUp, DiscoverIcons.Groups, DiscoverIcons.PersonAdd, DiscoverIcons.PersonRemove,
            DiscoverIcons.PersonSearch, DiscoverIcons.VisibilityOff, DiscoverIcons.TextFields, DiscoverIcons.AlternateEmail,
            DiscoverIcons.PermMedia, DiscoverIcons.CloudDownload,
        )
        icons.forEach { icon -> assertEquals(24f, icon.viewportWidth, icon.name) }
        assertEquals(icons.size, icons.map { it.name }.toSet().size)
    }
}
