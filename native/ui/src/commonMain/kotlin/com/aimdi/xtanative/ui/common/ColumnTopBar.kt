package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen's top bar over content kept in a centred column [column] wide
 * (the reading column, or a settings page's): the bar's background still
 * spans the screen, but Back, the title and the actions stand over the
 * column, not at the window's far edges. Where the screen is no wider than
 * the column (a phone, a narrow window) it is the plain [TopAppBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    column: Dp = ReadingColumn.MAX_WIDTH.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inset = ReadingColumn.inset(maxWidth.value, column.value).dp
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = inset, right = inset)),
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

/**
 * [content], a row that belongs with a [ColumnTopAppBar] (a group's chips),
 * no wider than [column] and centred under it.
 */
@Composable
fun InBarColumn(column: Dp = ReadingColumn.MAX_WIDTH.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = column).fillMaxWidth()) { content() }
    }
}
