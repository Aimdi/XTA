package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.home.GivesKeysBack
import com.aimdi.xtanative.ui.home.LocalSheetKeys
import com.aimdi.xtanative.ui.home.rememberSheetKeys
import com.aimdi.xtanative.ui.home.sheetKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Whether a sheet opens all the way at once: on the desktop, where the
 * mouse's wheel does not pull a half-open sheet up and its rows below the
 * window's edge would be out of reach but for its small handle. A phone
 * opens a tall sheet halfway, as Material's do.
 */
internal expect val SheetsOpenFull: Boolean

/**
 * A sheet's state that opens it as the platform does ([SheetsOpenFull]), or
 * all the way on every platform with [alwaysFull] (a sheet that is never
 * half useful, such as one with a field or a long pick).
 * On the desktop the sheet gives the window's keys back as it goes
 * ([GivesKeysBack]), to a video full screen under it or to the window, so
 * Esc, the reading keys and the rest work again at once after it closes,
 * rather than only after a click.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSheetState(alwaysFull: Boolean = false): SheetState {
    GivesKeysBack()
    return rememberModalBottomSheetState(skipPartiallyExpanded = alwaysFull || SheetsOpenFull)
}

/**
 * Material's [ModalBottomSheet], which the desktop's reading keys reach
 * ([sheetKeys]): ↑ ↓, Page Up and Down, Space, Home and End move the list
 * in it marked [com.aimdi.xtanative.ui.home.movedByKeys] (or
 * [com.aimdi.xtanative.ui.home.verticalScrollByKeys]), and the sheet takes
 * the keys as it opens; where it has no list, ↑ and ↓ go along its rows.
 * Esc closes it, as before. A button in it that closes it does so through
 * [LocalSheetCloser], so the sheet slides away as a swipe or Esc has it go,
 * rather than vanishing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyedSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    tonalElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keys = rememberSheetKeys()
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    val closer = remember(sheetState, scope) { SheetCloser(scope, sheetState::hide) { dismiss() } }
    closer.reduced = LocalReducedMotion.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.sheetKeys(keys),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
    ) {
        keys.focus = LocalFocusManager.current
        CompositionLocalProvider(LocalSheetKeys provides keys, LocalSheetCloser provides closer) { content() }
    }
}

/**
 * Closes the sheet it belongs to from a button in it: the sheet slides away
 * first ([slide]), then it is dismissed ([dismiss], the sheet's own
 * onDismissRequest) and the button's work done, so a pick does not make the
 * sheet vanish at once. While motion is [reduced] all of it happens at once.
 * A second press while it slides does nothing; a slide the reader stops
 * (dragging the sheet back) leaves it open, the press undone.
 */
@Stable
internal class SheetCloser(
    private val scope: CoroutineScope?,
    private val slide: suspend () -> Unit,
    private val dismiss: () -> Unit,
) {
    var reduced = false
    private var closing = false

    /** Slides the sheet away, then dismisses it and runs [then]. */
    fun close(then: () -> Unit = {}) {
        if (closing) return
        val finish = {
            dismiss()
            then()
        }
        if (reduced || scope == null) return finish()
        closing = true
        scope.launch { slide() }.invokeOnCompletion { cause ->
            closing = false
            if (cause == null) finish()
        }
    }

    /** [action] as a button that closes the sheet runs it: once the sheet has slid away. */
    fun <T> closing(action: (T) -> Unit): (T) -> Unit = { value -> close { action(value) } }

    /** [action] as a button that closes the sheet runs it: once the sheet has slid away. */
    fun closing(action: () -> Unit): () -> Unit = { close(action) }

    companion object {
        /** Outside a [KeyedSheet]: nothing slides, the work is done at once. */
        val None = SheetCloser(null, {}, {})
    }
}

/** The [SheetCloser] of the [KeyedSheet] around, or [SheetCloser.None] outside one. */
internal val LocalSheetCloser = staticCompositionLocalOf { SheetCloser.None }
