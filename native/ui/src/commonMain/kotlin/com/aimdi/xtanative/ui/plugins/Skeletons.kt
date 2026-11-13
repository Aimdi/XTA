package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.GridSkeleton

/**
 * What a plugin's feed paints while its first page is on the way (XTA's
 * PluginFeedSkeleton): [count] post-shaped placeholders in place of a
 * spinner, so the feed fills in where it will be. Not scrollable: put it
 * where the list goes, or in a list's item so a pull still reads again.
 */
@Composable
fun PluginFeedSkeleton(modifier: Modifier = Modifier, count: Int = 5) {
    FeedSkeleton(modifier, count)
}

/** [PluginFeedSkeleton] for a tab that shows a grid (XTA's PluginGridSkeleton): [count] tiles in [columns] columns. */
@Composable
fun PluginGridSkeleton(modifier: Modifier = Modifier, count: Int = 9, columns: Int = 3) {
    GridSkeleton(modifier, count, columns)
}
