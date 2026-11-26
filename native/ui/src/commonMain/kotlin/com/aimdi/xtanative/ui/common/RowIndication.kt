package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Where a lit row's shape sits in its bounds, in dp: a wide row (a setting,
 * a community, a dialog's option) lit as a rounded block inset from its
 * edges, as GNOME's and the X site's lists light a row, not a square
 * full-bleed band; a tall one (a tab) inset above and below; anything small
 * (a chip, an avatar) in its own bounds, with the same corners. Pure, so the
 * rule is tested.
 */
internal object RowHighlight {
    /** From this wide a row is inset at its sides, and from this tall above and below. */
    const val WIDE = 160f
    const val TALL = 44f

    const val SIDE_INSET = 8f
    const val END_INSET = 4f
    const val RADIUS = 8f

    /** The keyboard's ring: as wide as the sidebar's ([com.aimdi.xtanative.ui.home.NavRowLook]), in the accent. */
    const val RING = 2f

    /** How strongly the lit shape is washed with the text colour: the pointer on it, a press. */
    const val HOVER = 0.08f
    const val PRESS = 0.12f

    /** The lit shape of a [width] × [height] dp row, in dp. */
    data class Block(val left: Float, val top: Float, val width: Float, val height: Float, val radius: Float)

    fun block(width: Float, height: Float): Block {
        val side = if (width >= WIDE) SIDE_INSET else 0f
        val end = if (height >= TALL) END_INSET else 0f
        val w = (width - 2 * side).coerceAtLeast(0f)
        val h = (height - 2 * end).coerceAtLeast(0f)
        return Block(side, end, w, h, minOf(RADIUS, w / 2, h / 2))
    }

    /** The wash's strength: a press over the pointer resting on it, nothing for a focus alone (the ring shows that). */
    fun alpha(hovered: Boolean, pressed: Boolean): Float = when {
        pressed -> PRESS
        hovered -> HOVER
        else -> 0f
    }
}

/**
 * The desktop's lighting of a plain clickable row, given as the app's
 * [LocalIndication] there: the pointer and a press wash a rounded
 * [RowHighlight.block] in [wash], and a focus the keyboard moved
 * ([keyboard]) draws the accent's ring ([ring]) round it, the sidebar's
 * ring, where Material's ripple filled the whole row with a heavy grey (the
 * Tweets tab). Material's own buttons draw their own ripple and keep it.
 */
internal class RowIndication(private val wash: Color, private val ring: Color, private val keyboard: State<Boolean>) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RowNode(interactionSource, wash, ring, keyboard)

    override fun equals(other: Any?): Boolean = other is RowIndication && other.wash == wash && other.ring == ring && other.keyboard === keyboard

    override fun hashCode(): Int = (wash.hashCode() * 31 + ring.hashCode()) * 31 + keyboard.hashCode()
}

private class RowNode(
    private val interactions: InteractionSource,
    private val wash: Color,
    private val ring: Color,
    private val keyboard: State<Boolean>,
) : Modifier.Node(), DrawModifierNode {
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val focuses = mutableSetOf<FocusInteraction.Focus>()
    private val presses = mutableSetOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            interactions.interactions.collect { interaction ->
                track(interaction)
                invalidateDraw()
            }
        }
    }

    private fun track(interaction: Interaction) {
        when (interaction) {
            is HoverInteraction.Enter -> hovers += interaction
            is HoverInteraction.Exit -> hovers -= interaction.enter
            is FocusInteraction.Focus -> focuses += interaction
            is FocusInteraction.Unfocus -> focuses -= interaction.focus
            is PressInteraction.Press -> presses += interaction
            is PressInteraction.Release -> presses -= interaction.press
            is PressInteraction.Cancel -> presses -= interaction.press
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = RowHighlight.alpha(hovers.isNotEmpty(), presses.isNotEmpty())
        val ringed = focuses.isNotEmpty() && keyboard.value
        if (alpha == 0f && !ringed) return
        val px = density
        val block = RowHighlight.block(size.width / px, size.height / px)
        val topLeft = Offset(block.left * px, block.top * px)
        val shape = Size(block.width * px, block.height * px)
        if (shape.minDimension <= 0f) return
        if (alpha > 0f) drawRoundRect(wash.copy(alpha = alpha), topLeft, shape, CornerRadius(block.radius * px))
        if (ringed) {
            val half = RowHighlight.RING * px / 2
            drawRoundRect(
                ring,
                topLeft + Offset(half, half),
                Size(shape.width - 2 * half, shape.height - 2 * half),
                CornerRadius((block.radius * px - half).coerceAtLeast(0f)),
                style = Stroke(RowHighlight.RING * px),
            )
        }
    }
}

/**
 * Material's [Tab] lit as the app's rows are where the platform gives
 * [RowIndication] (the desktop): its ripple, which filled the whole tab
 * with grey for the keyboard's focus, is off, and the rounded block and the
 * ring light it instead. Material's own tab elsewhere (Android).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun XtaTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = MaterialTheme.colorScheme.onBackground,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val indication = LocalIndication.current
    if (indication !is RowIndication) {
        Tab(selected, onClick, modifier, text = text, selectedContentColor = selectedContentColor, unselectedContentColor = unselectedContentColor)
        return
    }
    val interactions = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Tab(
            selected,
            onClick,
            modifier.indication(interactions, indication),
            text = text,
            selectedContentColor = selectedContentColor,
            unselectedContentColor = unselectedContentColor,
            interactionSource = interactions,
        )
    }
}
