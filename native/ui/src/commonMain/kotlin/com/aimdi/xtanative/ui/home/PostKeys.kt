package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * What the desktop's keys do to the post they are on ([DesktopKeys.action]):
 * Enter and o [open] it, l [like]s it, b [save]s it, Ctrl+Shift+C copies
 * its link ([copyLink]), the Menu key and Shift+F10 open its ⋯ [menu], u its
 * [author]'s profile, Shift+Enter and Ctrl+Enter open it [inWindow], m and v
 * its first picture or video ([media]), t [translate]s it or back.
 */
class PostKeys(
    val open: () -> Unit,
    val like: () -> Unit,
    val save: () -> Unit,
    val copyLink: () -> Unit,
    val menu: () -> Unit,
    val author: () -> Unit = {},
    val inWindow: () -> Unit = {},
    val media: () -> Unit = {},
    val translate: () -> Unit = {},
)

/**
 * A post drawn in [list], where it is laid out ([place]) and what the keys do
 * to it ([keys]); [spot] names it on its screen and in its column, so the
 * same post drawn there anew (back from a post opened over it) is known again.
 */
class KeyedPost internal constructor(internal val list: LazyListState, internal val spot: Any? = null, keys: () -> PostKeys) {
    private val latest = keys
    internal var place: LayoutCoordinates? = null

    /** Where Tab from the window's frame goes while the post is marked ([DesktopActions.tabToMarked]). */
    internal val focus = FocusRequester()

    /** What the keys do to it now. */
    val keys: PostKeys get() = latest()
}

/** The list the reading keys move that what is drawn here is in ([ReadList]); null outside one, and off the desktop. */
private val LocalReadList = staticCompositionLocalOf<LazyListState?> { null }

/** [content] as drawn in [list], which the desktop's keys move: the posts in it are the ones they act on ([keyedPost]). */
@Composable
fun ReadList(list: LazyListState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReadList provides list, content = content)
}

/**
 * Lets the desktop's keys act on the post [id] drawn here with [keys] once
 * the reading keys are on it ([DesktopActions.currentPost]), and marks it,
 * faintly, after j or k land on it. Nothing off the desktop, or outside a
 * list the keys move ([ReadList]).
 */
@Composable
fun Modifier.keyedPost(keys: PostKeys, id: String): Modifier {
    val actions = LocalDesktopActions.current ?: return this
    val list = LocalReadList.current ?: return this
    val latest by rememberUpdatedState(keys)
    val spot = listOf(LocalViewModelStoreOwner.current, LocalReadColumn.current, id)
    val post = remember(list, spot) { KeyedPost(list, spot) { latest } }
    DisposableEffect(actions, post) {
        actions.addPost(post)
        onDispose { actions.removePost(post) }
    }
    val marked by remember(actions, post) { derivedStateOf { actions.markedPost === post } }
    val tint = MaterialTheme.colorScheme.primary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onGloballyPositioned { post.place = it }.focusRequester(post.focus).drawBehind {
        if (!marked) return@drawBehind
        drawRect(tint.copy(alpha = MARK_ALPHA))
        val bar = MARK_BAR.toPx()
        drawRect(tint, topLeft = Offset(if (rtl) size.width - bar else 0f, 0f), size = Size(bar, size.height))
    }
}

/** How strongly the marked post is tinted: enough to find it, not enough to read it differently. */
private const val MARK_ALPHA = 0.06f

/** The bar along the marked post's start edge. */
private val MARK_BAR = 3.dp
