package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import com.aimdi.xtanative.ui.theme.keyboardFocusShown
import kotlinx.coroutines.launch

/**
 * How strongly a whole-row button is washed behind what it holds: the pointer
 * resting on it, a press, the keyboard's focus. Pure, so the rule is tested.
 */
internal object BehindWash {
    const val HOVER = 0.06f
    const val FOCUS = 0.10f
    const val PRESS = 0.12f

    /**
     * The strongest of the states the row is in, or 0 for none; a picture
     * ([picture]) is never washed for the pointer resting on it, only pressed
     * or reached with the keyboard.
     */
    fun alpha(hovered: Boolean, focused: Boolean, pressed: Boolean, picture: Boolean = false): Float = when {
        pressed -> PRESS
        focused -> FOCUS
        hovered && !picture -> HOVER
        else -> 0f
    }
}

/**
 * An indication that washes a row **behind** its content, where Material's
 * ripple lays its hover over everything the row holds: a post's pictures and
 * videos stayed their own colours under the pointer, not greyed over (X's
 * site lights a post the same way). For rows that hold media; a button with
 * a label alone keeps the ripple. A [picture] itself is washed over, as
 * nothing shows behind it, but only for a press or the keyboard's focus:
 * the pointer resting on it leaves its colours alone.
 */
internal class BehindIndication(private val color: Color, private val keyboard: State<Boolean>, private val picture: Boolean = false) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = WashNode(interactionSource, color, keyboard, picture)

    override fun equals(other: Any?): Boolean = other is BehindIndication && other.color == color && other.keyboard === keyboard && other.picture == picture

    override fun hashCode(): Int = color.hashCode() * 31 + picture.hashCode()
}

private class WashNode(
    private val interactions: InteractionSource,
    private val color: Color,
    private val keyboard: State<Boolean>,
    private val picture: Boolean,
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
        val alpha = BehindWash.alpha(hovers.isNotEmpty(), focuses.isNotEmpty() && keyboard.value, presses.isNotEmpty(), picture)
        if (picture) {
            drawContent()
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
        } else {
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
            drawContent()
        }
    }
}

/**
 * [BehindIndication] in the theme's text colour; a focus washes only where
 * the keyboard moved it ([keyboardFocusShown]), as a click focuses too.
 */
@Composable
internal fun rememberBehindIndication(): BehindIndication = rememberWash(picture = false)

/** [BehindIndication] for a picture: washed over it for a press or the keyboard's focus, never for the pointer. */
@Composable
internal fun rememberPictureIndication(): BehindIndication = rememberWash(picture = true)

@Composable
private fun rememberWash(picture: Boolean): BehindIndication {
    val color = MaterialTheme.colorScheme.onSurface
    val keyboard = rememberUpdatedState(keyboardFocusShown())
    return remember(color, keyboard, picture) { BehindIndication(color, keyboard, picture) }
}
