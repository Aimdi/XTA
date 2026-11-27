package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.LocalSheetCloser
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.home.verticalScrollByKeys

/** A sheet rising from the foot of the screen, which slides away before a row's work is done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun VideoMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit) {
    KeyedSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        val closer = LocalSheetCloser.current
        Column(Modifier.verticalScrollByKeys(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            content { then -> closer.close(then) }
        }
    }
}
