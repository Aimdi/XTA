package com.aimdi.xtanative.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Settings › Accessibility › Disable animations, for the whole app (XTA's
 * xtaReduceMotion): while it is true, screens open and close, pages turn and
 * lists go back to the top at once. The theme provides it from the settings,
 * or the desktop's own settings turning animations off; Android's own
 * "Remove animations" already stops Compose's animations.
 */
val LocalReducedMotion = compositionLocalOf { false }

/** How a list goes back to its top. Reduced motion jumps; otherwise it slides. */
enum class TopJump { INSTANT, ANIMATED }

/** [reduced] motion never animates the jump, as XTA's scrollToTop. */
fun topJump(reduced: Boolean): TopJump = if (reduced) TopJump.INSTANT else TopJump.ANIMATED

/** Back to the list's top: scrolled there, or at once while motion is [reduced], as XTA's scrollToTop. */
suspend fun LazyListState.scrollToTop(reduced: Boolean) {
    if (topJump(reduced) == TopJump.INSTANT) scrollToItem(0) else animateScrollToItem(0)
}

/** The pager on [page]: slid there, or at once while motion is [reduced], as XTA's home turns its pages. */
suspend fun PagerState.turnTo(page: Int, reduced: Boolean) {
    if (reduced) scrollToPage(page) else animateScrollToPage(page)
}

/**
 * XTA's motion (motion.dart's kXtaMotionFast and kXtaMotionStandard): the
 * few durations the app's small movements share, so a pill, a hover card and
 * a notice come and go alike. Every one of them is still while motion is
 * reduced ([LocalReducedMotion]).
 */
object Motion {
    /** A quick change: a fade over a hover, a pill. */
    const val FAST_MS = 120

    /** A standard change: something coming in or growing. */
    const val STANDARD_MS = 180

    /** How far a pill or notice grows from as it comes in, and shrinks to as it goes. */
    const val POP_FROM = 0.9f

    /** A fade in, or nothing while motion is [reduced]. */
    fun fadeEnter(reduced: Boolean, ms: Int = FAST_MS): EnterTransition = if (reduced) EnterTransition.None else fadeIn(tween(ms))

    /** A fade out, or nothing while motion is [reduced]. */
    fun fadeExit(reduced: Boolean, ms: Int = FAST_MS): ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(ms))

    /** A pill coming in: faded and grown a little, or at once while motion is [reduced]. */
    fun popEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn(tween(STANDARD_MS)) + scaleIn(tween(STANDARD_MS), initialScale = POP_FROM)

    /** A pill going: faded and shrunk a little, or at once while motion is [reduced]. */
    fun popExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(tween(FAST_MS)) + scaleOut(tween(FAST_MS), targetScale = POP_FROM)
}

/**
 * The size of what this draws growing and shrinking as it changes, while
 * motion is not [reduced]; at once while it is. Only for what the reader
 * opens or folds: a list row that always carries it would animate as the
 * list reuses it for another row.
 */
fun Modifier.resizing(reduced: Boolean): Modifier = if (reduced) this else this.animateContentSize(tween(Motion.STANDARD_MS))

/**
 * A list row coming in, going and moving to its new place as the list
 * changes (new posts above it, another reply order), unless motion is
 * [reduced]: then rows change places at once, as they did.
 */
fun LazyItemScope.itemMotion(reduced: Boolean): Modifier =
    if (reduced) Modifier else Modifier.animateItem(fadeInSpec = tween(Motion.STANDARD_MS), fadeOutSpec = tween(Motion.FAST_MS))
