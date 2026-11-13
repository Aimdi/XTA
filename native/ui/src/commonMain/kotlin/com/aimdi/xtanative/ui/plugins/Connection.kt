package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Test connection and Save under the fields of a settings screen for the
 * reader's own server, and what the last test said under them (XTA's
 * PluginConnectionActions and PluginConnectionFeedback), named [test] and
 * [save]. Test keeps the fields ([keep]) and then asks [check] how they do,
 * a spinner in its mark meanwhile; Save keeps them and then [onSaved] (the
 * screen closes, as XTA's Save closes it). A field changed calls
 * [PluginConnection.invalidate], which takes the answer away.
 */
@Composable
fun PluginConnectionControls(
    connection: PluginConnection,
    test: String,
    save: String,
    keep: suspend () -> Unit,
    check: suspend () -> ConnectionState,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by connection.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(modifier) {
        ConnectionButtons(
            state,
            test,
            save,
            onTest = { scope.launch { connection.test(keep, check) } },
            onSave = {
                scope.launch {
                    kept(keep)
                    onSaved()
                }
            },
        )
        ConnectionFeedback(state)
    }
}

/** [keep] run, a failure to keep leaving the fields as they were stored; a cancellation goes on. */
private suspend fun kept(keep: suspend () -> Unit) {
    try {
        keep()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Closing the screen is all Save can still do.
    }
}

/** A way out of a plugin's settings to a page about its service, as XTA's "About …" buttons are: [label] after the open-in-new mark. */
@Composable
fun PluginLinkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(PostIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionButtons(state: ConnectionState, test: String, save: String, onTest: () -> Unit, onSave: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTest, enabled = !state.running, modifier = Modifier.heightIn(min = 48.dp)) {
            if (state.running) {
                CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            } else {
                Icon(WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(test)
        }
        OutlinedButton(onClick = onSave, enabled = !state.running, modifier = Modifier.heightIn(min = 48.dp)) { Text(save) }
    }
}

/** What the last test said beside its sign: in the error colour when it failed, else in the accent; read out as it changes. */
@Composable
private fun ConnectionFeedback(state: ConnectionState) {
    val message = state.message ?: return
    val color = if (state.status == ConnectionStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.Top) {
        Icon(signOf(state.status), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun signOf(status: ConnectionStatus): ImageVector = when (status) {
    ConnectionStatus.OK -> Icons.Outlined.CheckCircle
    ConnectionStatus.WARNING -> Icons.Outlined.Info
    else -> SavedIcons.ErrorOutline
}

private val WifiTethering: ImageVector by lazy {
    pathIcon(
        "WifiTethering",
        "M12,11c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,13c0,-3.31 -2.69,-6 -6,-6s-6,2.69 -6,6c0,2.22 1.21,4.15 3,5.19" +
            "l1,-1.74c-1.19,-0.7 -2,-1.97 -2,-3.45 0,-2.21 1.79,-4 4,-4s4,1.79 4,4c0,1.48 -0.81,2.75 -2,3.45l1,1.74c1.79,-1.04 3,-2.97 3,-5.19z" +
            "M12,3C6.48,3 2,7.48 2,13c0,3.7 2.01,6.92 4.99,8.65l1,-1.73C5.61,18.53 4,15.96 4,13c0,-4.42 3.58,-8 8,-8s8,3.58 8,8" +
            "c0,2.96 -1.61,5.53 -4,6.92l1,1.73c2.99,-1.73 5,-4.95 5,-8.65 0,-5.52 -4.48,-10 -10,-10z",
    )
}
