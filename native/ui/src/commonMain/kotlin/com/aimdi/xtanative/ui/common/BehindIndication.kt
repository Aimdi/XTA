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
 * How strongly a whole-row button is washed behind what it holds (the
 * pointer resting on it, a press), and where the keyboard's ring goes round
 * it: a focus shows as the accent's ring, as the app's rows show it
 * ([RowIndication]), not as a grey block over the whole post. Pure, so the
 * rule is tested.
 */
internal object BehindWash {
    const val HOVER = 0.06f
    const val PRESS = 0.12f

    /** A picture's corners, which its ring follows: the posts' media and cards ([com.aimdi.xtanative.ui.post.MediaGrid]). */
    const val PICTURE_RADIUS = 16f

    /**
     * The stronger of the states the row is in, or 0 for none; a picture
     * ([picture]) is never washed for the pointer resting on it, only pressed.
     */
    fun alpha(hovered: Boolean, pressed: Boolean, picture: Boolean = false): Float = when {
        pressed -> PRESS
        hovered && !picture -> HOVER
        else -> 0f
    }

    /**
     * The ring's place in a [width] × [height] dp row, in dp: a post's as the
     * rows' ([RowHighlight.block]), inset from its edges; a picture's on its
     * own edge, along its rounded corners.
     */
    fun ring(width: Float, height: Float, picture: Boolean): RowHighlight.Block =
        if (picture) RowHighlight.Block(0f, 0f, width, height, minOf(PICTURE_RADIUS, width / 2, height / 2)) else RowHighlight.block(width, height)
}

/**
 * An indication that washes a row **behind** its content, where Material's
 * ripple lays its hover over everything the row holds: a post's pictures and
 * videos stayed their own colours under the pointer, not greyed over (X's
 * site lights a post the same way). For rows that hold media; a button with
 * a label alone keeps the ripple. A [picture] itself is washed over, as
 * nothing shows behind it, but only for a press: the pointer resting on it
 * leaves its colours alone. The keyboard's focus draws the accent's [ring]
 * round either ([BehindWash.ring]).
 */
internal class BehindIndication(
    private val color: Color,
    private val ring: Color,
    private val keyboard: State<Boolean>,
    private val picture: Boolean = false,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = WashNode(interactionSource, color, ring, keyboard, picture)

    override fun equals(other: Any?): Boolean =
        other is BehindIndication && other.color == color && other.ring == ring && other.keyboard === keyboard && other.picture == picture

    override fun hashCode(): Int = (color.hashCode() * 31 + ring.hashCode()) * 31 + picture.hashCode()
}

private class WashNode(
    private val interactions: InteractionSource,
    private val color: Color,
    private val ring: Color,
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
        val alpha = BehindWash.alpha(hovers.isNotEmpty(), presses.isNotEmpty(), picture)
        if (picture) {
            drawContent()
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
        } else {
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
            drawContent()
        }
        if (focuses.isNotEmpty() && keyboard.value) focusRing(ring, BehindWash.ring(size.width / density, size.height / density, picture))
    }
}

/**
 * [BehindIndication] in the theme's text colour; a focus rings the row only
 * where the keyboard moved it ([keyboardFocusShown]), as a click focuses too.
 */
@Composable
internal fun rememberBehindIndication(): BehindIndication = rememberWash(picture = false)

/** [BehindIndication] for a picture: washed over it for a press, ringed for the keyboard's focus, never lit for the pointer. */
@Composable
internal fun rememberPictureIndication(): BehindIndication = rememberWash(picture = true)

@Composable
private fun rememberWash(picture: Boolean): BehindIndication {
    val color = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.primary
    val keyboard = rememberUpdatedState(keyboardFocusShown())
    return remember(color, ring, keyboard, picture) { BehindIndication(color, ring, keyboard, picture) }
}
