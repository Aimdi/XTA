package com.aimdi.xtanative.ui.archive

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun HighlightMenu(label: String, onHighlight: (() -> Unit)?, content: @Composable () -> Unit) {
    val highlight = rememberUpdatedState(onHighlight)
    ContextMenuDataProvider(items = { highlight.value?.let { run -> listOf(ContextMenuItem(label, run)) }.orEmpty() }) { content() }
}
