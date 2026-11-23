package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider

/** A menu's width on the desktop, as Material's menus keep theirs. */
private val MenuMinWidth = 200.dp
private val MenuMaxWidth = 320.dp

@Composable
internal actual fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Popup(
        popupPositionProvider = rememberCursorPositionProvider(offset = DpOffset(2.dp, 2.dp), windowMargin = 8.dp),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            // A hairline keeps its edge on the black theme, where the shadow does not show.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}
