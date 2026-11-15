package com.aimdi.xtanative.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The reply order menu's motion and shape (the sort menu redesign): it opens
 * out of the trigger's top end corner, fading and growing from 95 % while it
 * drops 6 dp, in 190 ms on an ease-out-expo curve, its rows following 30, 55
 * and 80 ms behind; it closes faster than it opens, in 110 ms. A pick slides
 * the highlight to its row, pops the tick, and closes the menu 150 ms later.
 * Every duration is 0 while the reader asked for less motion.
 */
internal object ReplyOrderMotion {
    val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val In = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    const val OPEN_MS = 190
    const val CLOSE_MS = 110
    const val SCRIM_IN_MS = 140
    const val ROW_MS = 220
    const val HIGHLIGHT_MS = 200
    const val CLOSE_AFTER_PICK_MS = 150L
    const val SCRIM_ALPHA = 0.45f
    const val MENU_WIDTH = 236
    const val ROW_HEIGHT = 48
    const val CORNER = 6

    /** How long row [index] waits before it comes in: 30 ms, then 25 ms more for each row. */
    fun rowDelay(index: Int): Int = 30 + 25 * index

    /** [millis], or nothing while the reader wants less motion. */
    fun duration(millis: Int, reduced: Boolean): Int = if (reduced) 0 else millis

    /**
     * Where the menu's start edge sits, in px from the window's left: under the
     * trigger, its end edge on the trigger's end edge (left to right: right;
     * right to left: left), kept [margin] inside the window.
     */
    fun menuX(anchorLeft: Float, anchorRight: Float, menuWidth: Float, windowWidth: Float, margin: Float, rtl: Boolean): Float {
        val wanted = if (rtl) anchorLeft else anchorRight - menuWidth
        return wanted.coerceIn(margin, (windowWidth - menuWidth - margin).coerceAtLeast(margin))
    }

    /** Where the menu's top sits: just under the trigger, or above it when it would not fit below. */
    fun menuY(anchorTop: Float, anchorBottom: Float, menuHeight: Float, windowHeight: Float, gap: Float, margin: Float): Float {
        val below = anchorBottom + gap
        return if (below + menuHeight + margin <= windowHeight) below else (anchorTop - gap - menuHeight).coerceAtLeast(margin)
    }
}

/**
 * The trigger of the reply order (the sort menu redesign): flat, no pill or
 * border, the order's icon in the accent, its name in the line's grey and a
 * chevron that turns over while the menu is open, in a 44 dp tall target.
 */
@Composable
internal fun <T> ReplyOrderButton(
    shown: T,
    options: List<T>,
    label: @Composable (T) -> String,
    icon: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val visible = remember { MutableTransitionState(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val open = visible.targetState
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(ReplyOrderMotion.duration(180, reduced), easing = ReplyOrderMotion.Expo))
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier) {
        Row(
            Modifier
                .onGloballyPositioned { anchor = it.boundsInWindow() }
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { visible.targetState = !visible.targetState }
                .clearAndSetSemantics {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon(shown), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label(shown), color = muted, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = muted, modifier = Modifier.size(16.dp).rotate(turn))
        }
        if (visible.currentState || visible.targetState) {
            ReplyOrderPopup(visible, anchor, shown, options, label, icon, reduced, onSelect)
        }
    }
}

/** A popup over the whole window: the dimming that closes it, and the menu under the trigger. */
@Composable
private fun <T> ReplyOrderPopup(
    visible: MutableTransitionState<Boolean>,
    anchor: Rect?,
    shown: T,
    options: List<T>,
    label: @Composable (T) -> String,
    icon: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    reduced: Boolean,
    onSelect: (T) -> Unit,
) {
    val close = { visible.targetState = false }
    Popup(popupPositionProvider = WholeWindow, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        val scrim by animateFloatAsState(
            if (visible.targetState) ReplyOrderMotion.SCRIM_ALPHA else 0f,
            tween(ReplyOrderMotion.duration(if (visible.targetState) ReplyOrderMotion.SCRIM_IN_MS else ReplyOrderMotion.CLOSE_MS, reduced)),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            val density = LocalDensity.current
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val width = ReplyOrderMotion.MENU_WIDTH.dp
            val height = (ReplyOrderMotion.ROW_HEIGHT * options.size + 8).dp
            val (x, y) = with(density) {
                val box = anchor ?: Rect(0f, 0f, constraints.maxWidth.toFloat(), 0f)
                ReplyOrderMotion.menuX(box.left, box.right, width.toPx(), constraints.maxWidth.toFloat(), 12.dp.toPx(), rtl) to
                    ReplyOrderMotion.menuY(box.top, box.bottom, height.toPx(), constraints.maxHeight.toFloat(), 4.dp.toPx(), 12.dp.toPx())
            }
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.offset { IntOffset(if (rtl) (constraints.maxWidth - x - with(density) { width.toPx() }).toInt() else x.toInt(), y.toInt()) },
                enter = fadeIn(tween(ReplyOrderMotion.duration(ReplyOrderMotion.OPEN_MS, reduced), easing = ReplyOrderMotion.Expo)) +
                    scaleIn(tween(ReplyOrderMotion.duration(ReplyOrderMotion.OPEN_MS, reduced), easing = ReplyOrderMotion.Expo), initialScale = 0.95f, transformOrigin = TransformOrigin(if (rtl) 0f else 1f, 0f)) +
                    slideInVertically(tween(ReplyOrderMotion.duration(ReplyOrderMotion.OPEN_MS, reduced), easing = ReplyOrderMotion.Expo)) { with(density) { -6.dp.roundToPx() } },
                exit = fadeOut(tween(ReplyOrderMotion.duration(ReplyOrderMotion.CLOSE_MS, reduced), easing = ReplyOrderMotion.In)) +
                    scaleOut(tween(ReplyOrderMotion.duration(ReplyOrderMotion.CLOSE_MS, reduced), easing = ReplyOrderMotion.In), targetScale = 0.96f, transformOrigin = TransformOrigin(if (rtl) 0f else 1f, 0f)),
            ) {
                ReplyOrderRows(shown, options, label, icon, reduced, width) { picked ->
                    if (picked != shown) onSelect(picked)
                    close()
                }
            }
        }
    }
}

/** The menu: full-width 48 dp rows, the chosen one tinted with a 3 dp accent bar at its start and a tick. */
@Composable
private fun <T> ReplyOrderRows(
    shown: T,
    options: List<T>,
    label: @Composable (T) -> String,
    icon: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    reduced: Boolean,
    width: Dp,
    onPicked: (T) -> Unit,
) {
    var chosen by remember { mutableStateOf(shown) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ReplyOrderMotion.CORNER.dp)
    val highlight by animateDpAsState(
        (ReplyOrderMotion.ROW_HEIGHT * options.indexOf(chosen).coerceAtLeast(0)).dp,
        tween(ReplyOrderMotion.duration(ReplyOrderMotion.HIGHLIGHT_MS, reduced), easing = ReplyOrderMotion.Expo),
    )
    Box(
        Modifier
            .width(width)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .offset(y = highlight)
                .fillMaxWidth()
                .height(ReplyOrderMotion.ROW_HEIGHT.dp)
                .background(colors.primary.copy(alpha = 0.14f)),
        ) {
            Box(Modifier.padding(vertical = 10.dp).width(3.dp).fillMaxSize().background(colors.primary))
        }
        Column {
            options.forEachIndexed { index, option ->
                val picked = option == chosen
                val enter = remember { Animatable(if (reduced) 1f else 0f) }
                LaunchedEffect(Unit) {
                    if (!reduced) {
                        delay(ReplyOrderMotion.rowDelay(index).toLong())
                        enter.animateTo(1f, tween(ReplyOrderMotion.ROW_MS, easing = ReplyOrderMotion.Expo))
                    }
                }
                Row(
                    Modifier
                        .graphicsLayer {
                            alpha = enter.value
                            translationY = (1f - enter.value) * -4.dp.toPx()
                        }
                        .fillMaxWidth()
                        .height(ReplyOrderMotion.ROW_HEIGHT.dp)
                        .clickable(enabled = !picking) {
                            chosen = option
                            picking = true
                            scope.launch {
                                if (!reduced) delay(ReplyOrderMotion.CLOSE_AFTER_PICK_MS)
                                onPicked(option)
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.RadioButton
                            selected = picked
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(icon(option), null, tint = if (picked) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(
                        label(option),
                        color = if (picked) colors.onSurface else colors.onSurface.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (picked) {
                        val pop = remember(option) { Animatable(if (reduced) 1f else 0.4f) }
                        LaunchedEffect(option) {
                            if (!reduced) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        Icon(Icons.Filled.Check, null, tint = colors.primary, modifier = Modifier.size(18.dp).scale(pop.value).graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) })
                    }
                }
            }
        }
    }
}

/** A popup laid over the whole window, from its top left corner. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}
