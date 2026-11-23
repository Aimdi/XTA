package com.aimdi.xtanative.ui.plugins

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.rememberSheetState

/**
 * A plugin's sheet's state, as the app's own sheets have it: on the desktop
 * the sheet opens all the way (a mouse's wheel does not pull a half-open
 * sheet up) and gives the window's keys back as it closes; on a phone it
 * opens as Material's do, or all the way with [alwaysFull]. Pass it as the
 * `sheetState` of a `ModalBottomSheet`. Plugin API 15.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPluginSheetState(alwaysFull: Boolean = false): SheetState = rememberSheetState(alwaysFull)
