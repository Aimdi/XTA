package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A scrollbar down the end edge of a list on the desktop, where a mouse
 * wants one to drag and to see how far it has read; nothing on Android,
 * whose lists show their own while they move. Place it over the list,
 * aligned to the end and as tall as the list.
 */
@Composable
expect fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** [ListScrollbar] for a page that scrolls as one column (a settings page). */
@Composable
expect fun ColumnScrollbar(state: ScrollState, modifier: Modifier = Modifier)
