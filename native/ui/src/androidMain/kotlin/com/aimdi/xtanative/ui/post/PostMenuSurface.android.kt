package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.rememberSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp), content = content)
    }
}
