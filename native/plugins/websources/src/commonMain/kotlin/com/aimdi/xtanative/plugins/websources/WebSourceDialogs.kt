package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceFile
import com.aimdi.xtanative.networks.websources.WebSourceRefusal
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * Asks the reader to agree to [source] and every site it reads, before
 * anything is read from them (web_source_dialogs.dart). [update] for a
 * newer version of one installed; [origin] the host its file came from.
 */
@Composable
internal fun ConsentDialog(source: WebSourceDefinition, origin: String?, update: Boolean, onDismiss: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { WebSourceMark(source, 40.dp, picture = false) },
        title = { Text(pluginString(if (update) Strings.update_title else Strings.consent_title, source.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (source.description.isNotEmpty()) Text(source.description)
                origin?.let { Text(pluginString(Strings.consent_from, it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(16.dp))
                Text(pluginString(Strings.consent_sites), style = MaterialTheme.typography.titleSmall)
                source.hosts.forEach { host ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(WebSourcesIcons.Public, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(host, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(pluginString(Strings.consent_read_only))
            }
        },
        confirmButton = { Button(onClick = onAgree) { Text(pluginString(if (update) Strings.ok else Strings.install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/** Why a source file cannot be installed, with where in it, or that it could not be read at all ([refused] null). */
@Composable
internal fun RefusalDialog(refused: WebSourceFile.Refused?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                Text(refused?.let { pluginString(refusalText(it.refusal)) } ?: pluginString(Strings.unreachable))
                refused?.detail?.takeIf { it.isNotEmpty() }?.let { Text(pluginString(Strings.refused_detail, it), modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.ok)) } },
    )
}

private fun refusalText(refusal: WebSourceRefusal) = when (refusal) {
    WebSourceRefusal.UNREADABLE -> Strings.refused_unreadable
    WebSourceRefusal.NEWER_FORMAT -> Strings.refused_newer
    WebSourceRefusal.INVALID -> Strings.refused_invalid
    WebSourceRefusal.INSECURE -> Strings.refused_insecure
    WebSourceRefusal.TOO_LARGE -> Strings.refused_too_large
}
