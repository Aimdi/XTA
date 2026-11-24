package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No keyboard to move a sheet's list on a phone. */
@Composable
internal actual fun Modifier.sheetKeys(keys: SheetKeys): Modifier = this
