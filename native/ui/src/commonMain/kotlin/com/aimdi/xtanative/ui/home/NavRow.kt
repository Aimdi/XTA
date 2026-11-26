package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.theme.keyboardFocusShown

/**
 * How a row of the account menu is lit, one rule for every row in it: the
 * selected one is clearly the strongest, in the accent as the rail lights
 * its place shown ([RailLook]), where it was a grey that read as the
 * pointer's; the pointer only a faint wash of the text colour, and the
 * keyboard's focus a ring, which never darkens the fill so it cannot pass
 * for the selection.
 */
internal object NavRowLook {
    const val HOVER_ALPHA = 0.06f

    /** The pointer's wash on a dark page, where light text at [HOVER_ALPHA] barely shows on black (Lights out). */
    const val DARK_HOVER_ALPHA = 0.10f

    /** How much of its colour ([accented]: the accent, else the text colour) washes the row, on a [dark] page or a light one. */
    fun fillAlpha(selected: Boolean, hovered: Boolean, dark: Boolean = false): Float = when {
        selected -> RailLook.selectedAlpha(hovered)
        hovered -> if (dark) DARK_HOVER_ALPHA else HOVER_ALPHA
        else -> 0f
    }

    /** Whether the row is lit in the accent, not the text colour: the place shown, as on the rail. */
    fun accented(selected: Boolean): Boolean = selected

    /** A ring marks the focus only where the keyboard moved it ([keyboard]). */
    fun ring(focused: Boolean, keyboard: Boolean): Boolean = focused && keyboard

    /** The label's weight: bold for the selected row, as X marks the page it shows. */
    fun weight(selected: Boolean): FontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
}

/**
 * Whether the account menu drawn here is laid out for a desktop's pointer
 * ([NavRowSizes]): the desktop window's column and the menu its rail opens.
 * A phone's drawer and a tablet's column keep their rows tall for a finger.
 */
internal val LocalNavCompact = staticCompositionLocalOf { false }

/** The account menu's sizes: a finger's, or a pointer's where it is [compact] ([LocalNavCompact]). */
internal object NavRowSizes {
    val ROW = 56.dp
    val COMPACT_ROW = 40.dp
    val GROUP_ROW = 64.dp
    val COMPACT_GROUP_ROW = 48.dp
    val GROUP_MARK = 40.dp
    val COMPACT_GROUP_MARK = 32.dp

    /** The one width every row's icon is centred in, the widest of them (the bar's pages' 26 dp), so the labels start in one line. */
    val ICON_SLOT = 26.dp

    fun row(compact: Boolean): Dp = if (compact) COMPACT_ROW else ROW

    fun groupRow(compact: Boolean): Dp = if (compact) COMPACT_GROUP_ROW else GROUP_ROW

    fun groupMark(compact: Boolean): Dp = if (compact) COMPACT_GROUP_MARK else GROUP_MARK
}

/** Material's ripple for a press only: [NavRowLook] draws the hover and the focus. */
@OptIn(ExperimentalMaterial3Api::class)
internal val PressOnly = RippleConfiguration(rippleAlpha = RippleAlpha(pressedAlpha = 0.1f, focusedAlpha = 0f, draggedAlpha = 0.16f, hoveredAlpha = 0f))

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
    minHeight: Dp = NavRowSizes.row(LocalNavCompact.current),
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
                .background(
                    (if (NavRowLook.accented(selected)) colors.primary else colors.onSurface)
                        .copy(alpha = NavRowLook.fillAlpha(selected, hovered, dark = colors.background.luminance() < 0.5f)),
                )
                .then(if (ring) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                .selectable(selected, interactionSource = interactions, indication = ripple(), role = role, onClick = onClick)
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * A page or a screen in the account menu: its icon, centred in the rows' one
 * icon slot ([NavRowSizes.ICON_SLOT]), and label; while [selected] the icon
 * in the accent, as the rail's, and the label bold, in the text colour,
 * which reads on the accent's light wash where the accent itself would not.
 */
@Composable
internal fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    NavRowFrame(selected, onClick, modifier) {
        val tint = if (NavRowLook.accented(selected)) MaterialTheme.colorScheme.primary else LocalContentColor.current
        Box(Modifier.size(NavRowSizes.ICON_SLOT), contentAlignment = Alignment.Center) { CompositionLocalProvider(LocalContentColor provides tint, content = icon) }
        TipText(label, fontWeight = NavRowLook.weight(selected), style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

/** The shape of a taller row (a group), rounded as the pill rows are at their height. */
internal val NavRowTallShape = RoundedCornerShape(20.dp)
