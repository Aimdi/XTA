package com.aimdi.xtanative.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [content], the timeline at [index] of those [labels] name, turned by a
 * drag across it (reader_swipe_navigation.dart): the timeline stays still
 * while a small preview at the side the drag heads for names the next one,
 * filling in as the drag nears the distance that turns it; a release far or
 * fast enough turns there ([onTurn]) with one tick. Only a finger or a
 * stylus drags, as in XTA; a drag the timeline's own sideways lists take,
 * one starting at the screen's edges, a second finger or a drag turning
 * back calls it off.
 */
@Composable
internal fun SwipesTimelines(index: Int, labels: List<String>, onTurn: (Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val preview = remember(scope) { SwipePreview(scope) }
    val latestTurn by rememberUpdatedState(onTurn)
    val latestReduced by rememberUpdatedState(reduced)
    val count = labels.size
    BoxWithConstraints(
        modifier.pointerInput(index, count, rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (count < 2 || !down.byHand() || !TimelineSwipe.startsInside(down.position.x / density, size.width / density)) return@awaitEachGesture
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                val travel = Drag(start.position - down.position, start)
                preview.follow(travel.dx / density)
                var blocked = false
                val lifted = horizontalDrag(start.id) { change ->
                    travel.moved(change)
                    change.consume()
                    blocked = blocked || currentEvent.changes.count { it.pressed } > 1 || travel.reversed(density)
                    preview.follow(if (blocked) 0f else travel.dx / density)
                }
                val sideways = abs(travel.dx) > abs(travel.dy) * TimelineSwipe.SIDEWAYS
                val target = if (lifted && !blocked && sideways) HomeBar.swiped(index, count, travel.dx / density, travel.velocity() / density, rtl) else index
                preview.settle(latestReduced)
                if (target != index) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    latestTurn(target)
                }
            }
        },
    ) {
        content()
        Preview(index, labels, preview.distance, rtl, reduced, maxWidth.value * PREVIEW_SHARE)
    }
}

/** How far the drag the preview follows has gone, in dp; back to nothing once it ends, springing there unless motion is reduced. */
@Stable
private class SwipePreview(private val scope: CoroutineScope) {
    var distance by mutableFloatStateOf(0f)
        private set
    private var settling: Job? = null

    fun follow(dp: Float) {
        settling?.cancel()
        distance = dp
    }

    fun settle(reduced: Boolean) {
        settling?.cancel()
        if (reduced || distance == 0f) {
            distance = 0f
            return
        }
        settling = scope.launch { animate(distance, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 400f)) { value, _ -> distance = value } }
    }
}

/** How much of the timeline's width the preview may take. */
private const val PREVIEW_SHARE = 0.65f

/** A drag under way: how far it went across and down, its first clear direction, and its speed. */
private class Drag(start: Offset, first: PointerInputChange) {
    var dx = start.x
        private set
    var dy = start.y
        private set
    private var direction = 0f
    private val tracker = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }

    fun moved(change: PointerInputChange) {
        val moved = change.positionChange()
        dx += moved.x
        dy += moved.y
        tracker.addPosition(change.uptimeMillis, change.position)
    }

    /** Whether the drag now goes the other way than it first went clearly. */
    fun reversed(density: Float): Boolean {
        if (direction == 0f) direction = TimelineSwipe.direction(dx / density)
        return TimelineSwipe.reversed(direction, dx / density)
    }

    /** Its speed across at release, in pixels a second. */
    fun velocity(): Float = tracker.calculateVelocity().x
}

/** A finger or a stylus: a mouse does not drag timelines, as in XTA. */
private fun PointerInputChange.byHand(): Boolean = type == PointerType.Touch || type == PointerType.Stylus || type == PointerType.Eraser

/** The preview of the timeline the drag heads for: "→ name", at the side it heads for, filling in as it nears turning. */
@Composable
private fun BoxScope.Preview(index: Int, labels: List<String>, distance: Float, rtl: Boolean, reduced: Boolean, maxWidth: Float) {
    val target = TimelineSwipe.target(index, labels.size, distance, rtl) ?: return
    val progress = TimelineSwipe.progress(distance)
    val full = progress >= 1f
    val colors = MaterialTheme.colorScheme
    val toward = if (distance < 0) 1f else -1f
    Box(
        Modifier.matchParentSize(),
        contentAlignment = if (distance < 0) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = maxWidth.dp)
                .graphicsLayer {
                    alpha = if (reduced) 1f else progress
                    translationX = if (reduced) 0f else toward * 16.dp.toPx() * (1f - progress)
                }
                .clip(RoundedCornerShape(12.dp))
                .background(if (full) colors.primaryContainer else colors.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (full) colors.onPrimaryContainer else colors.onSurface
            Icon(if (target > index) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(labels[target], color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
