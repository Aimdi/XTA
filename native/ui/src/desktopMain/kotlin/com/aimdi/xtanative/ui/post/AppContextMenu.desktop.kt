package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The menus Compose draws itself on the desktop (selected text's Copy and
 * Select all, a field's Cut and Paste, an archive highlight's entry) as the
 * app's own pointer menus are drawn ([PostMenuSurface]): the same container,
 * edge, shadow and rows, at the pointer, walked by the same keys; not
 * Compose's plain white box beside the post's menu.
 */
internal object AppContextMenu : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        if (state.status !is ContextMenuState.Status.Open) return
        val close = { state.status = ContextMenuState.Status.Closed }
        CompositionLocalProvider(LocalMenuAtPost provides false) {
            PostMenuSurface(onDismiss = close) {
                items().forEach { item ->
                    TextMenuRow(item.label) {
                        close()
                        item.onClick()
                    }
                }
            }
        }
    }
}

/** One entry of [AppContextMenu]: its words alone, laid out as the app's pointer menu lays its rows out ([MenuRowMetrics]). */
@Composable
private fun TextMenuRow(label: String, onClick: () -> Unit) {
    val metrics = LocalMenuRowMetrics.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = metrics.minHeight).clickable(onClick = onClick).padding(horizontal = metrics.padding, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
    }
}
