package com.aimdi.xtanative.ui.chrome

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.chrome_confirm_close
import com.aimdi.xtanative.ui.generated.resources.settings_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.settings_no
import com.aimdi.xtanative.ui.generated.resources.settings_yes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource

/** Asks for the app to close, as Back on Home does (XTA's confirmCloseApp); [ConfirmClose] answers. */
object AppClosing {
    private val asks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val asked: SharedFlow<Unit> = asks.asSharedFlow()

    fun ask() {
        asks.tryEmit(Unit)
    }
}

/**
 * Closing the app when asked to ([AppClosing]): at once, or once the reader
 * says yes to XTA's question while Settings › General › Confirm close is
 * on, as it is unless they turned it off.
 */
@Composable
fun ConfirmClose() {
    val graph = LocalGraph.current
    val close by rememberUpdatedState(rememberCloseApp())
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(graph) {
        AppClosing.asked.collect {
            if (graph.settings.state.value?.get(GeneralPrefs.confirmClose) == false) close() else asking = true
        }
    }
    if (!asking) return
    AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(Res.string.settings_are_you_sure)) },
        text = { Text(stringResource(Res.string.chrome_confirm_close)) },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(Res.string.settings_no)) } },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                close()
            }) { Text(stringResource(Res.string.settings_yes)) }
        },
    )
}

/**
 * The system's Back on the first screen ([enabled]) closes the app,
 * through [ConfirmClose], instead of leaving it there. Android alone: the
 * desktop's first screen asks on its mouse's back button.
 */
@Composable
fun CloseOnBack(enabled: Boolean) {
    BackHandling(enabled = enabled) { AppClosing.ask() }
}
