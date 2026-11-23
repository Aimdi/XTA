package com.aimdi.xtanative.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.home.GivesKeysBack

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
