package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.theme.keyboardFocusShown

/**
 * How a row of the account menu is lit, one rule for every row in it: the
 * selected one is clearly the strongest, the pointer only a faint wash, and
 * the keyboard's focus a ring, which never darkens the fill so it cannot pass
 * for the selection.
 */
internal object NavRowLook {
    const val SELECTED_ALPHA = 0.14f
    const val SELECTED_HOVER_ALPHA = 0.18f
    const val HOVER_ALPHA = 0.06f

    /** How much of the text colour washes the row. */
    fun fillAlpha(selected: Boolean, hovered: Boolean): Float = when {
        selected && hovered -> SELECTED_HOVER_ALPHA
        selected -> SELECTED_ALPHA
        hovered -> HOVER_ALPHA
        else -> 0f
    }

    /** A ring marks the focus only where the keyboard moved it ([keyboard]). */
    fun ring(focused: Boolean, keyboard: Boolean): Boolean = focused && keyboard

    /** The label's weight: bold for the selected row, as X marks the page it shows. */
    fun weight(selected: Boolean): FontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
}

/** Material's ripple for a press only: [NavRowLook] draws the hover and the focus. */
@OptIn(ExperimentalMaterial3Api::class)
private val PressOnly = RippleConfiguration(rippleAlpha = RippleAlpha(pressedAlpha = 0.1f, focusedAlpha = 0f, draggedAlpha = 0.16f, hoveredAlpha = 0f))

/**
 * A row of the account menu lit by [NavRowLook], inset from the menu's edges
 * on [shape]: a page, a screen or a group. [content] lays out what is in it.
 * A screen reader hears a page or a screen as a tab ([role]), and a group,
 * which opens rather than being shown here, on [NavRowTallShape], as a button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavRowFrame(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    minHeight: Dp = 56.dp,
    role: Role = if (shape == NavRowTallShape) Role.Button else Role.Tab,
    content: @Composable RowScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val ring = NavRowLook.ring(focused, keyboardFocusShown())
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalRippleConfiguration provides PressOnly, LocalContentColor provides colors.onSurface) {
        Row(
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.onSurface.copy(alpha = NavRowLook.fillAlpha(selected, hovered)))
                .then(if (ring) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                .selectable(selected, interactionSource = interactions, indication = ripple(), role = role, onClick = onClick)
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A page or a screen in the account menu: its icon and label, the label bold while [selected]. */
@Composable
internal fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    NavRowFrame(selected, onClick, modifier) {
        icon()
        TipText(label, fontWeight = NavRowLook.weight(selected), style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

/** The shape of a taller row (a group), rounded as the pill rows are at their height. */
internal val NavRowTallShape = RoundedCornerShape(20.dp)
