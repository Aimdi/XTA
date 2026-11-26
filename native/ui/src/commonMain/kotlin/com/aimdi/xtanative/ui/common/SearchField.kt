package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.settings.isDesktop
import com.aimdi.xtanative.ui.theme.edgeColor
import com.aimdi.xtanative.ui.theme.fieldColor

/**
 * The app's one search field look: a pill filled apart from the page
 * ([fieldColor]) with a hairline round it ([edgeColor]), the accent's edge
 * while it has the keys, a grey magnifier, the hint in the text's own weight
 * whatever bar holds it, and [trailing] buttons (clear, filters) inside its
 * end. 40 dp tall on the desktop, as its fields are; 48 on a phone, where
 * the buttons inside keep their touch size.
 */
internal object SearchFieldLook {
    /** The field's height on the desktop, and on a phone. */
    val DESKTOP_HEIGHT: Dp = 40.dp
    val PHONE_HEIGHT: Dp = 48.dp

    /** Its corners: fully round at either height. */
    val Shape = RoundedCornerShape(percent = 50)

    /** Its hairline, and the accent's edge while it has the keys. */
    val EDGE: Dp = 1.dp
    val FOCUSED_EDGE: Dp = 1.5.dp

    /** The field's height here: [DESKTOP_HEIGHT] or [PHONE_HEIGHT]. */
    fun height(desktop: Boolean): Dp = if (desktop) DESKTOP_HEIGHT else PHONE_HEIGHT
}

/** [SearchField] for a plain [String]. */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interactions = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        interactionSource = interactions,
        decorationBox = { inner -> SearchFieldFrame(value.isEmpty(), placeholder, interactions, leading, trailing, inner) },
    )
}

/** [SearchField] for a [TextFieldValue], where the caller places the caret (a search picked from the recent ones). */
@Composable
internal fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interactions = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        interactionSource = interactions,
        decorationBox = { inner -> SearchFieldFrame(value.text.isEmpty(), placeholder, interactions, leading, trailing, inner) },
    )
}

/** What is drawn round the typed words: the pill, the magnifier, the hint while nothing is typed, and the buttons. */
@Composable
private fun SearchFieldFrame(
    empty: Boolean,
    placeholder: String,
    interactions: MutableInteractionSource,
    leading: (@Composable () -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
    inner: @Composable () -> Unit,
) {
    val focused by interactions.collectIsFocusedAsState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val edge = if (focused) MaterialTheme.colorScheme.primary else edgeColor()
    val width = if (focused) SearchFieldLook.FOCUSED_EDGE else SearchFieldLook.EDGE
    // The buttons inside are as tall as the field on the desktop, not Material's 48 dp touch size.
    val touch = if (isDesktop) Dp.Unspecified else LocalMinimumInteractiveComponentSize.current
    Row(
        Modifier
            .heightIn(min = SearchFieldLook.height(isDesktop))
            .background(fieldColor(), SearchFieldLook.Shape)
            .border(width, edge, SearchFieldLook.Shape)
            .padding(start = if (leading == null) 14.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides touch) {
            if (leading == null) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            } else {
                leading()
            }
            Box(Modifier.weight(1f).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (empty) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
            trailing()
        }
    }
}
