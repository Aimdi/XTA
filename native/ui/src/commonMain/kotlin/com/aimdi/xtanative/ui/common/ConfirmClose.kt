package com.aimdi.xtanative.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close_app_confirm
import com.aimdi.xtanative.ui.generated.resources.settings_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.settings_no
import com.aimdi.xtanative.ui.generated.resources.settings_yes
import org.jetbrains.compose.resources.stringResource

/**
 * System Back where the app has nothing left to go back to (XTA's
 * confirmCloseApp, which Home and the intro's first card share): the app
 * closes at once, or, while Settings › General's Confirm close is on, once
 * the reader says Yes. Only while [enabled]; the desktop has no system Back.
 */
@Composable
fun ConfirmCloseOnBack(enabled: Boolean = true) {
    val graph = LocalGraph.current
    val confirms by graph.settings.observe(GeneralPrefs.confirmClose).collectAsStateWithLifecycle(graph.settings.state.value?.get(GeneralPrefs.confirmClose) ?: true)
    var asking by remember { mutableStateOf(false) }
    val close = rememberCloseApp()
    BackHandling(enabled = enabled && confirms) { asking = true }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(Res.string.settings_are_you_sure)) },
            text = { Text(stringResource(Res.string.close_app_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    close()
                }) { Text(stringResource(Res.string.settings_yes)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(Res.string.settings_no)) } },
        )
    }
}

/** Closes the app, as Back leaving its last screen does: Android's activity finishes. Nothing on the desktop, which never asks. */
@Composable
internal expect fun rememberCloseApp(): () -> Unit
