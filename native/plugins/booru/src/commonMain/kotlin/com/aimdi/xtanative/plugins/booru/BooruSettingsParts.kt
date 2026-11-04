package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A setting with its value under it; a tap changes it. */
@Composable
internal fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One of [choices] picked under [title]; [selected] is the one in force. */
@Composable
internal fun <T> ChoiceDialog(title: String, choices: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/** A booru site to add: its host, the name it is shown by, and the engine it speaks, guessed from the host as it is typed. */
@Composable
internal fun AddSiteDialog(initialHost: String, onDismiss: () -> Unit, onAdd: (BooruPreset) -> Unit) {
    var host by remember { mutableStateOf(initialHost) }
    var name by remember { mutableStateOf(BooruHosts.displayName(initialHost)) }
    var engine by remember { mutableStateOf(BooruHosts.guessEngine(initialHost) ?: BooruEngine.GELBOORU_V2) }
    var invalid by remember { mutableStateOf(false) }
    val submit = {
        val normalised = BooruHosts.normalise(host)
        if (normalised.isEmpty()) {
            invalid = true
        } else {
            onAdd(BooruPreset(BooruSites.customId(normalised), name.trim().ifEmpty { BooruHosts.displayName(normalised) }, engine, normalised))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.add_site_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { value ->
                        BooruHosts.guessEngine(value)?.let { engine = it }
                        if (name.isEmpty() || name == BooruHosts.displayName(host)) name = BooruHosts.displayName(value)
                        host = value
                        invalid = false
                    },
                    label = { Text(pluginString(Strings.host)) },
                    placeholder = { Text(pluginString(Strings.host_hint)) },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(pluginString(Strings.site_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(pluginString(Strings.site_name)) }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
                EnginePicker(engine) { engine = it }
            }
        },
        confirmButton = { Button(onClick = submit) { Text(pluginString(Strings.add_site)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

@Composable
private fun EnginePicker(engine: BooruEngine, onPick: (BooruEngine) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp)) {
        Text(pluginString(Strings.engine), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(engineLabel(engine)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                BooruEngine.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(engineLabel(choice)) }, onClick = { open = false; onPick(choice) })
                }
            }
        }
    }
}

/** The kinds of tag "Copy tags as prompt" writes, in its order (booru_prompt_settings.dart): drag a kind to move it, switch it off to leave it out. */
@Composable
internal fun PromptSection(app: BooruApp, settings: Settings) {
    val kinds = remember(settings[BooruPrefs.promptKinds]) { BooruPrompt.kinds(settings[BooruPrefs.promptKinds]) }
    val save = { next: List<PromptKind> -> app.update { it.with(BooruPrefs.promptKinds, BooruPrompt.stored(next)) } }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(pluginString(Strings.prompt_section))
        Text(pluginString(Strings.prompt_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        PromptKinds(kinds, onMove = save, onToggle = { kind, on -> save(BooruPrompt.toggled(kinds, kind.category, on)) })
    }
}

/** The kinds as rows, each with a handle that drags it among the others and a switch. */
@Composable
private fun PromptKinds(kinds: List<PromptKind>, onMove: (List<PromptKind>) -> Unit, onToggle: (PromptKind, Boolean) -> Unit) {
    var order by remember(kinds) { mutableStateOf(kinds) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val row = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    Column(Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, kind ->
            key(kind.category) {
                Row(
                    Modifier.fillMaxWidth().height(ROW_HEIGHT).zIndex(if (dragged == index) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged == index) offset else 0f }
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        GroupIcons.DragHandle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).padding(12.dp).pointerInput(kind.category) {
                            detectDragGestures(
                                onDragStart = { dragged = order.indexOfFirst { it.category == kind.category }; offset = 0f },
                                onDragEnd = { dragged = null; offset = 0f; onMove(order) },
                                onDragCancel = { dragged = null; offset = 0f; order = kinds },
                            ) { change, amount ->
                                change.consume()
                                val from = dragged ?: return@detectDragGestures
                                offset += amount.y
                                val to = (from + (offset / row).roundToInt()).coerceIn(0, order.lastIndex)
                                if (to != from) {
                                    order = BooruPrompt.moved(order, from, to)
                                    offset -= (to - from) * row
                                    dragged = to
                                }
                            }
                        },
                    )
                    Text(kindLabel(kind.category), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = kind.included, onCheckedChange = { onToggle(kind, it) })
                }
            }
        }
    }
}

private val ROW_HEIGHT = 56.dp

/** Asks the site in use for one post, and says how that went. */
@Composable
internal fun TestConnection(app: BooruApp) {
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !testing) {
            testing = true
            scope.launch {
                app.tell(testMessage(app, language))
                testing = false
            }
        }.defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pluginString(Strings.test_connection), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (testing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(BooruIcons.NetworkCheck, contentDescription = null)
    }
}

private suspend fun testMessage(app: BooruApp, language: String): String = try {
    Strings.test_ok.get(language, app.client.latest(limit = 1).posts.size)
} catch (error: kotlin.coroutines.cancellation.CancellationException) {
    throw error
} catch (error: Exception) {
    Strings.test_failed.get(language, errorMessage(error, language))
}
