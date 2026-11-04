package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonInstances
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.NetworkPersonRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * XTA's Mastodon settings (mastodon_settings.dart): whether the accounts
 * followed here join Home's timelines, the home instance and a test of it,
 * more instances, the built-in ones tried after them, and the accounts
 * followed here, each to open or unfollow. They are kept in the app's
 * settings under XTA's keys.
 */
@Composable
internal fun MastodonSettingsBody(app: MastodonApp, frame: PluginFrame) {
    val prefs by app.prefs.collectAsState()
    val follows by app.follows.state.collectAsState()
    LaunchedEffect(Unit) { app.follows.load() }
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "in-home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.in_home_feed), pluginString(Strings.in_home_feed_description)) }
        item(key = "intro") { Note(Strings.settings_intro, Modifier.padding(16.dp)) }
        item(key = "home") { HomeInstance(app, prefs.home) }
        item(key = "more") { PluginSectionTitle(pluginString(Strings.more_instances)) }
        items(prefs.extras, key = { "extra:$it" }) { instance -> ExtraRow(instance) { app.changeSettings { MastodonSettings.withoutExtra(it, instance) } } }
        item(key = "add") { AddInstance { typed -> app.changeSettings { MastodonSettings.withExtra(it, typed) } } }
        builtIn()
        item(key = "accounts") { PluginSectionTitle(pluginString(Strings.accounts)) }
        if (follows?.isEmpty() == true) item(key = "none") { Note(Strings.no_accounts, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        items(follows.orEmpty(), key = { "follow:" + it.acct }) { follow ->
            NetworkPersonRow(follow.name, follow.acct, follow.avatarUrl, { app.openProfile(follow.acct) }) {
                IconButton(onClick = { app.unfollow(follow.acct) }) { Icon(Icons.Filled.Delete, pluginString(Strings.unfollow)) }
            }
        }
    }
}

@Composable
private fun Note(text: PluginText, modifier: Modifier) {
    Text(pluginString(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The home instance, kept as it is typed, and Test connection, whose answer shows under it. */
@Composable
private fun HomeInstance(app: MastodonApp, stored: String) {
    var text by rememberSaveable { mutableStateOf(stored) }
    var testing by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<PluginText?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(stored) { if (text.isEmpty()) text = stored }
    Column {
        PluginSectionTitle(pluginString(Strings.instance))
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                answer = null
                app.changeSettings { MastodonSettings.withHome(it, typed) }
            },
            placeholder = { Text(pluginString(Strings.instance_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(enabled = !testing, onClick = {
                testing = true
                scope.launch {
                    answer = test(app, text)
                    testing = false
                }
            }) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(pluginString(Strings.test))
            }
            answer?.let { Text(pluginString(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}

/** Whether [typed] answers as an instance: XTA's test, in its words. */
private suspend fun test(app: MastodonApp, typed: String): PluginText = try {
    app.client.verify(MastodonInstances.normalise(typed) ?: typed.trim())
    Strings.test_ok
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    problemText(e)
}

@Composable
private fun ExtraRow(instance: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(instance, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, pluginString(Strings.delete)) }
    }
}

/** A further instance, added with its button or the keyboard's. */
@Composable
private fun AddInstance(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (MastodonInstances.normalise(text) != null) {
            onAdd(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(pluginString(Strings.instance_hint)) },
        singleLine = true,
        trailingIcon = { IconButton(onClick = add) { Icon(Icons.Filled.Add, pluginString(Strings.add_instance)) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The instances asked after the reader's, so reading works with nothing set. */
private fun LazyListScope.builtIn() {
    item(key = "builtin") {
        Column {
            PluginSectionTitle(pluginString(Strings.builtin_instances))
            Note(Strings.builtin_description, Modifier.padding(horizontal = 16.dp))
        }
    }
    items(MastodonInstances.defaults, key = { "builtin:$it" }) { instance ->
        Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Text(MastodonInstances.domain(instance) ?: instance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
