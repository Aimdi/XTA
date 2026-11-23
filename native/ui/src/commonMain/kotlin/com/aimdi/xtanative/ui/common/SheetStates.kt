package com.aimdi.xtanative.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Whether a sheet opens all the way at once: on the desktop, where the
 * mouse's wheel does not pull a half-open sheet up and its rows below the
 * window's edge would be out of reach but for its small handle. A phone
 * opens a tall sheet halfway, as Material's do.
 */
internal expect val SheetsOpenFull: Boolean

/** A sheet's state that opens it as the platform does ([SheetsOpenFull]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = SheetsOpenFull)
