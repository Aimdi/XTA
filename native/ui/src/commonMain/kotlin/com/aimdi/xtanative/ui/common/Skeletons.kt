package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder posts while a feed's first page loads (XTA's TweetFeedSkeleton):
 * [count] post-shaped tiles sharing one pulse, so the feed fills in place
 * rather than jumping from a spinner to a screen of posts. Not scrollable
 * itself: it sits in the list or page that holds it.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier, count: Int = 6) {
    val pulse = rememberSkeletonPulse()
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        repeat(count) { PostSkeletonBones(pulse) }
    }
}

/**
 * One placeholder post (XTA's TweetSkeletonTile), as the foot of a list while
 * its next page loads: the list grows into something post-shaped instead of
 * a spinner that appears and is swapped out.
 */
@Composable
fun PostSkeleton(modifier: Modifier = Modifier) {
    Box(modifier) { PostSkeletonBones(rememberSkeletonPulse()) }
}

/**
 * Placeholder tiles while a grid's first page loads (XTA's
 * PluginGridSkeleton): [count] square tiles in [columns] columns, sharing one
 * pulse. Not scrollable itself.
 */
@Composable
fun GridSkeleton(modifier: Modifier = Modifier, count: Int = 9, columns: Int = 3) {
    val pulse = rememberSkeletonPulse()
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val across = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(SkeletonGrid.rows(count, across)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(across) { column ->
                    val tile = Modifier.weight(1f).aspectRatio(1f)
                    if (SkeletonGrid.drawn(row, column, count, across)) Bone(tile, lerp(base, highlight, pulse.value), RoundedCornerShape(8.dp)) else Spacer(tile)
                }
            }
        }
    }
}

/** How a grid skeleton's tiles fall into rows: the last row keeps its tiles' width and leaves the rest empty. */
internal object SkeletonGrid {
    fun rows(count: Int, columns: Int): Int = if (count <= 0) 0 else (count + columns - 1) / columns

    fun drawn(row: Int, column: Int, count: Int, columns: Int): Boolean = row * columns + column < count
}

/** Whether the bones pulse: not while the reader switched animations off (XTA's optionDisableAnimations); then they hold [STILL]. */
internal object SkeletonMotion {
    const val STILL = 1f

    fun pulses(reducedMotion: Boolean): Boolean = !reducedMotion
}

/** The pulse every bone follows, 0 to 1 and back; still at its end with reduced motion. */
@Composable
private fun rememberSkeletonPulse(): State<Float> {
    if (!SkeletonMotion.pulses(LocalReducedMotion.current)) return remember { mutableFloatStateOf(SkeletonMotion.STILL) }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MILLIS, easing = EaseInOut), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )
}

@Composable
private fun PostSkeletonBones(pulse: State<Float>) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val value by pulse
    val color = lerp(base, highlight, value)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Bone(Modifier.size(40.dp), color, CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Bone(Modifier.width(140.dp).height(12.dp), color)
            Spacer(Modifier.height(8.dp))
            Bone(Modifier.fillMaxWidth().height(12.dp), color)
            Spacer(Modifier.height(6.dp))
            Bone(Modifier.width(220.dp).height(12.dp), color)
            Spacer(Modifier.height(12.dp))
            Bone(Modifier.fillMaxWidth().height(120.dp), color, RoundedCornerShape(16.dp))
        }
    }
}

/** One grey block of a skeleton. */
@Composable
private fun Bone(modifier: Modifier, color: Color, shape: Shape = RoundedCornerShape(BONE_RADIUS)) {
    Box(modifier.clip(shape).background(color))
}

private val BONE_RADIUS: Dp = 4.dp
private val GRID_GAP: Dp = 4.dp
private val GRID_RADIUS: Dp = 8.dp
private const val SKELETON_PULSE_MILLIS = 1_100
