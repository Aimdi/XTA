package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.WithReadingColumn

/**
 * A plugin's list of posts or rows kept to the app's reading column in a
 * wide window, as the app keeps its own: [content] is given [padding] with
 * the room on either side that centres the rows in 720 dp; the list itself
 * still fills the width, so the wheel works across all of it. Given its
 * [list], the desktop also draws its scrollbar at the window's edge and its
 * reading keys (Page Down, Space, Home, End …) move it. A phone, or a narrow
 * column, keeps its width. Plugin API 15; leave picture grids full width.
 */
@Composable
fun PluginReadingColumn(padding: PaddingValues, list: LazyListState? = null, content: @Composable (PaddingValues) -> Unit) =
    WithReadingColumn(padding, list, content)
