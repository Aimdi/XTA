package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.aimdi.xtanative.networks.mastodon.MastodonAcct
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.launch

/**
 * XTA's Add account: a Fediverse address, looked up on its own instance, then
 * the reader's, then the built-in ones, and followed here. What went wrong
 * stays under the field, where the reader can fix it.
 */
@Composable
internal fun AddAccountDialog(app: MastodonApp, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<PluginText?>(null) }
    val scope = rememberCoroutineScope()
    val submit = {
        val acct = MastodonAcct.normalise(text)
        if (acct == null) {
            failure = Strings.invalid_handle
        } else if (!busy) {
            busy = true
            scope.launch {
                val problem = app.follow(acct)
                busy = false
                if (problem == null) onDismiss() else failure = problemText(problem)
            }
        }
    }
    val shown = failure
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.add_account)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    failure = null
                },
                placeholder = { Text(pluginString(Strings.handle_hint)) },
                singleLine = true,
                isError = shown != null,
                supportingText = shown?.let { problem -> @Composable { Text(pluginString(problem)) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = !busy) { Text(pluginString(Strings.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
