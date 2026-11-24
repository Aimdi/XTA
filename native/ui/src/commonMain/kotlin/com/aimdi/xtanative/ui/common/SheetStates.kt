package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * Esc closes it, as before.
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.sheetKeys(keys),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
    ) {
        keys.focus = LocalFocusManager.current
        CompositionLocalProvider(LocalSheetKeys provides keys) { content() }
    }
}
