package com.aimdi.xtanative.ui.post

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How an opened post's poll fills in (poll_fill.dart): each bar grows from
 * nothing to its share in [GROWTH_MS], [STAGGER_MS] after the bar above it,
 * easing out; the whole fill takes [TOTAL_MS], the last of X's four options
 * starting three staggers in.
 */
object PollFill {
    const val GROWTH_MS = 600
    const val STAGGER_MS = 80
    const val TOTAL_MS = 840

    /** How far bar [index] has grown towards its share at [t] (0..1) of the whole fill. */
    fun growth(t: Float, index: Int): Float {
        val start = min(1f, index * STAGGER_MS.toFloat() / TOTAL_MS)
        val end = min(1f, start + GROWTH_MS.toFloat() / TOTAL_MS)
        if (t <= start) return 0f
        if (t >= end || end <= start) return 1f
        val x = (t - start) / (end - start)
        val inverse = 1f - x
        return 1f - inverse * inverse * inverse
    }

    /** How long a fill asked for at [now] waits for its screen, opened at [openedAt], to finish sliding in over [slideMs]. */
    fun startDelay(openedAt: Long, now: Long, slideMs: Long): Long = (slideMs - (now - openedAt)).coerceAtLeast(0)
}

/**
 * The opened post's poll filling in, kept by its conversation screen
 * (FocalPollFill): the copy shown while X answers and the post X sends fill
 * as one, and a poll scrolled back into view stays full. It runs once the
 * first poll asks for it and the screen has slid in; with reduced motion the
 * bars are full at once.
 */
@Stable
class FocalPollFill(
    private val scope: CoroutineScope,
    private val openedAt: Long,
    private val slideMs: Long = SCREEN_SLIDE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** How far the fill has run, 0..1: [PollFill.growth] of it for each bar. */
    var progress by mutableFloatStateOf(0f)
        private set

    private var asked = false

    /** Sets the fill going, once; at once full while motion is [reduced]. */
    fun start(reduced: Boolean) {
        if (asked) return
        asked = true
        if (reduced) {
            progress = 1f
            return
        }
        scope.launch {
            delay(PollFill.startDelay(openedAt, clock(), slideMs))
            val run = Animatable(0f)
            run.animateTo(1f, tween(PollFill.TOTAL_MS, easing = LinearEasing)) { progress = value }
            progress = 1f
        }
    }

    companion object {
        /** How long a screen takes to slide in (Navigation's SLIDE_MS). */
        const val SCREEN_SLIDE_MS = 280L
    }
}

/** The conversation screen's poll fill; null elsewhere, where a poll is full at once. */
val LocalFocalPollFill = staticCompositionLocalOf<FocalPollFill?> { null }

/** A poll's bar (poll_results.dart's PollOptionBar), in dp: a picture sits as far in from its start as from its top, the share [END] from its end. */
object PollBar {
    const val MIN_HEIGHT = 36f
    const val RADIUS = 6f
    const val INSET = 6f
    const val END = 12f
    const val IMAGE = 48f
    const val GAP = 3f

    /** Where a bar's contents start: [INSET] in for its picture, [END] for its text alone. */
    fun start(hasImage: Boolean): Float = if (hasImage) INSET else END

    /** How much of the bar is filled: its [share] as far as it has [grown]. */
    fun fill(share: Double, grown: Float): Float = (share * grown).toFloat().coerceIn(0f, 1f)
}
