package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How far ↑ and ↓ move a list: about a line of a post and a half. */
internal val LINE = 64.dp

/**
 * A reading key's [action] ([DesktopKeys]: a line, a screen, the top or the
 * end, j and k's next post or the one before) done to [list], the one being
 * read, with [line] the pixels of a line; false when there is no list, or it
 * is one the action does not move. The desktop's window does it, and so does
 * a hardware keyboard on Android ([onHardwareKey]).
 */
internal fun DesktopActions.read(action: DeskAction, list: ScrollableState?, scope: CoroutineScope, line: Float): Boolean = when (list) {
    is LazyListState -> {
        if (action == DeskAction.NEXT_ITEM || action == DeskAction.PREVIOUS_ITEM) {
            // From the post marked: where nothing is left to go to, the mark stays.
            keyStep(list, action == DeskAction.NEXT_ITEM)?.let { keyed ->
                unmarkPost()
                scope.launch {
                    when (val step = keyed.step) {
                        is DesktopKeys.KeyStep.ToPost -> {
                            list.animateScrollBy(step.by.toFloat())
                            keyed.post?.let(::markPost)
                        }
                        // Where j and k land, the post the keys then act on is marked.
                        is DesktopKeys.KeyStep.ToItem -> markPostIn(list, list.stepTo(step))
                    }
                }
            }
        } else {
            unmarkPost()
            scope.launch { list.moveFor(action, line) }
        }
        true
    }
    is ScrollState -> DesktopKeys.scrolledTo(action, list.value, list.maxValue, list.viewportSize, line)?.let { target ->
        unmarkPost()
        scope.launch { list.moveTo(action, target) }
        true
    } ?: false
    else -> false
}

/** [action] done to the list, eased as the wheel's own moves are (j and k: [stepTo]). */
internal suspend fun LazyListState.moveFor(action: DeskAction, line: Float) {
    val screen = layoutInfo.viewportSize.height * (1 - DesktopKeys.SCREEN_KEPT)
    when (action) {
        DeskAction.LINE_UP -> animateScrollBy(-line)
        DeskAction.LINE_DOWN -> animateScrollBy(line)
        DeskAction.SCREEN_UP -> animateScrollBy(-screen)
        DeskAction.SCREEN_DOWN -> animateScrollBy(screen)
        DeskAction.TO_TOP -> scrollToItem(0)
        DeskAction.TO_END -> layoutInfo.totalItemsCount.takeIf { it > 0 }?.let { scrollToItem(it - 1) }
        else -> Unit
    }
}

/** j or k's [step] to an item's start ([DesktopKeys.itemStep]), eased as the wheel's own moves are; the item it went to. */
private suspend fun LazyListState.stepTo(step: DesktopKeys.KeyStep.ToItem): Int {
    animateScrollToItem(step.index, step.offset)
    // Tabs in their own place before the step stick on the way, over the post's start: it comes out from under them.
    val covered = DesktopKeys.coveredAfterStep(layoutInfo.visibleItemsInfo.map { DesktopKeys.ShownItem(it.index, it.offset, it.size) }, step.index)
    if (covered > 0) animateScrollBy(-covered.toFloat())
    return step.index
}

/** A page that scrolls as a whole to [target] ([DesktopKeys.scrolledTo]): at once to its start or end, as a list's Home and End go; eased otherwise. */
internal suspend fun ScrollState.moveTo(action: DeskAction, target: Int) {
    if (action == DeskAction.TO_TOP || action == DeskAction.TO_END) scrollTo(target) else animateScrollTo(target)
}
