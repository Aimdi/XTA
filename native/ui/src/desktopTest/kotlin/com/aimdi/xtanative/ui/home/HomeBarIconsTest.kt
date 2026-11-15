package com.aimdi.xtanative.ui.home

import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBarIconsTest {
    private fun paths(icon: androidx.compose.ui.graphics.vector.ImageVector) = icon.root.filterIsInstance<VectorPath>()

    @Test
    fun everyTabHasAnOutlineAndAFilledIconOnTheSameGrid() {
        val pairs = listOf(
            HomeBarIcons.Home to HomeBarIcons.HomeFilled,
            HomeBarIcons.Subscriptions to HomeBarIcons.SubscriptionsFilled,
            HomeBarIcons.Discover to HomeBarIcons.DiscoverFilled,
            HomeBarIcons.Saved to HomeBarIcons.SavedFilled,
        )
        pairs.forEach { (outline, filled) ->
            assertEquals(24f, outline.viewportWidth)
            assertEquals(24f, filled.viewportWidth)
            assertTrue(paths(outline).all { it.stroke != null && it.fill == null && it.strokeLineWidth == HomeBarIcons.STROKE }, "${outline.name} is drawn in lines")
            assertTrue(paths(filled).any { it.fill != null }, "${filled.name} is filled")
            assertTrue((paths(outline) + paths(filled)).all { it.pathData.isNotEmpty() }, "every path parses")
        }
        assertEquals(6, paths(HomeBarIcons.Subscriptions).size, "a person and its four brackets")
        assertEquals(5, paths(HomeBarIcons.SubscriptionsFilled).size, "the filled person keeps the brackets as lines")
    }
}
