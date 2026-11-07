package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.compositionLocalOf

/**
 * Settings › Accessibility › Disable animations, for the whole app (XTA's
 * xtaReduceMotion): while it is true, screens open and close, pages turn and
 * lists go back to the top at once. The theme provides it from the settings;
 * Android's own "Remove animations" already stops Compose's animations.
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
