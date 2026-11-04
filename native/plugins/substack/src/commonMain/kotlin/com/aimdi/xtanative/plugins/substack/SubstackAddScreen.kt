package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * Following a publication by what the reader pastes, as XTA's SubstackAddScreen:
 * a handle, an address, a custom domain or a post link is looked up and shown
 * first; following is a separate, explicit step, and stays on this device.
 */
@Composable
internal fun SubstackAddScreen(app: SubstackApp) {
    var input by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    PluginScaffold(title = pluginString(Strings.add), onBack = app.host::back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(pluginString(Strings.add_hint))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(pluginString(Strings.add_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { asked = input.trim() }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            if (asked.isEmpty()) {
                Button(onClick = { asked = input.trim() }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(pluginString(Strings.preview_publication)) }
            } else {
                Preview(app, asked, retype = input.trim()) { asked = it }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preview(app: SubstackApp, asked: String, retype: String, onAsk: (String) -> Unit) {
    val model = rememberKept("substack-add-$asked") { scope -> DirectModel(app, asked, scope) }
    val state by model.state.collectAsState()
    val follows by app.publications.collectAsState()
    val publication = state.publication
    val followed = publication != null && follows != null && app.followOf(publication) != null
    Button(onClick = { if (retype == asked) model.load() else onAsk(retype) }, enabled = !state.loading && retype.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(pluginString(Strings.preview_publication))
    }
    state.error?.let {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pluginString(Strings.add_error), textAlign = TextAlign.Center)
            TextButton(onClick = model::load) { Text(pluginString(Strings.retry)) }
        }
    }
    publication?.let { found ->
        Text(found.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Text(found.baseUrl, modifier = Modifier.padding(top = 4.dp))
        found.description?.takeIf { it.isNotEmpty() }?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { app.follow(found) }, enabled = !state.loading && !followed) {
                Icon(if (followed) Icons.Default.CheckCircle else Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (followed) pluginString(Strings.followed, found.displayName) else pluginString(Strings.follow))
            }
            TextButton(onClick = { app.openPublication(found) }) {
                Icon(SubstackIcons.Newspaper, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.publication))
            }
        }
    }
    state.postError?.let {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pluginString(Strings.preview_post_failed))
            TextButton(onClick = model::load) { Text(pluginString(Strings.retry)) }
        }
    }
    state.post?.let { post ->
        Row(Modifier.fillMaxWidth().clickable { app.openPost(post) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SubstackIcons.Notes, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(post.title, style = MaterialTheme.typography.bodyLarge)
                Text(pluginString(Strings.open_pasted_post), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
    if (followed) TextButton(onClick = app.host::back) { Text(pluginString(Strings.done)) }
}
