package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
 * Placeholder tiles while a grid's first page loads (XTA's PluginGridSkeleton):
 * [count] square tiles in [columns] sharing one pulse, so a grid of pictures
 * fills in place. Not scrollable itself; rows that do not fit are not drawn.
 */
@Composable
fun GridSkeleton(modifier: Modifier = Modifier, columns: Int = 3, count: Int = 9) {
    val pulse = rememberSkeletonPulse()
    val value by pulse
    val color = lerp(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh, value)
    Column(modifier.fillMaxWidth().clipToBounds().padding(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        SkeletonGrid.rows(count, columns).forEach { tiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                repeat(columns) { index ->
                    if (index < tiles) Bone(Modifier.weight(1f).aspectRatio(1f), color, RoundedCornerShape(GRID_RADIUS)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** How a grid skeleton's tiles fall in rows. */
internal object SkeletonGrid {
    /** The number of tiles in each row of [count] tiles in [columns]; the last row may be short. */
    fun rows(count: Int, columns: Int): List<Int> {
        if (count <= 0 || columns <= 0) return emptyList()
        val full = List(count / columns) { columns }
        return if (count % columns == 0) full else full + count % columns
    }
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
