package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aimdi.xtanative.ui.home.LocalSheetKeys
import com.aimdi.xtanative.ui.home.rememberSheetKeys
import com.aimdi.xtanative.ui.home.sheetKeys
import com.aimdi.xtanative.ui.theme.edgeColor

/**
 * A [KeyedSheet] as the desktop draws it ([SheetsAsDialogs]): a dialog
 * centred over the dimmed window, no wider than [SHEET_DIALOG_WIDTH], edged
 * as the desktop's popups are (so it stands apart from a black page too).
 * The sheet's keys work in it as in the sheet ([sheetKeys]); Esc and a
 * click beside it close it, and a button in it closes it through
 * [LocalSheetCloser], which fades it out first, as it fades in
 * ([SheetDialogMotion]). Tab goes round its own controls only, and the
 * keys go back to the window as it goes
 * ([com.aimdi.xtanative.ui.home.GivesKeysBack], in [rememberSheetState]).
 */
@Composable
internal fun SheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    tonalElevation: Dp,
    border: BorderStroke?,
    dragHandle: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keys = rememberSheetKeys()
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    val shown = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    val closer = remember(scope) {
        SheetCloser(scope, slide = { shown.animateTo(0f, tween(SheetDialogMotion.OUT_MS)) }) { dismiss() }
    }
    closer.reduced = reduced
    LaunchedEffect(shown) {
        if (reduced) shown.snapTo(1f) else shown.animateTo(1f, tween(SheetDialogMotion.IN_MS))
    }
    Dialog(onDismissRequest = { closer.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier
                .padding(horizontal = 24.dp, vertical = 40.dp)
                .widthIn(max = SHEET_DIALOG_WIDTH)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = SheetDialogMotion.scale(shown.value)
                    alpha = shown.value
                    scaleX = s
                    scaleY = s
                }
                .sheetKeys(keys),
            shape = AlertDialogDefaults.shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            border = border ?: BorderStroke(1.dp, edgeColor()),
        ) {
            keys.focus = LocalFocusManager.current
            Column(Modifier.fillMaxWidth().padding(top = if (dragHandle) 16.dp else 0.dp, bottom = 12.dp)) {
                CompositionLocalProvider(LocalSheetKeys provides keys, LocalSheetCloser provides closer) { content() }
            }
        }
    }
}

/** How a [SheetDialog] fades in and out: quick, with a slight grow, as the desktop's dialogs open. */
internal object SheetDialogMotion {
    const val IN_MS = 140
    const val OUT_MS = 110

    /** The size the dialog is drawn at, [shown] of the way in: from 96 % to its own. */
    fun scale(shown: Float): Float = 0.96f + 0.04f * shown.coerceIn(0f, 1f)
}

/** A sheet's dialog is a form's or a pick's width, as Material's dialogs are at most. */
internal val SHEET_DIALOG_WIDTH = 560.dp
