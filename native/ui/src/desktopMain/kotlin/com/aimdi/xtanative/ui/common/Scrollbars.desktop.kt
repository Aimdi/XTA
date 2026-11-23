package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun ListScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxHeight().padding(end = 2.dp), style = themedStyle())
}

@Composable
actual fun ColumnScrollbar(state: ScrollState, modifier: Modifier) {
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxHeight().padding(end = 2.dp), style = themedStyle())
}

/** The default bar in the theme's colours, which also read on a black page. */
@Composable
private fun themedStyle() = defaultScrollbarStyle().copy(
    unhoverColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
    hoverColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    thickness = 6.dp,
)
