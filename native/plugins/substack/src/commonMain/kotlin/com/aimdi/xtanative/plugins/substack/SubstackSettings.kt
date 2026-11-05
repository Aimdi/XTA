package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackOpml
import com.aimdi.xtanative.networks.substack.SubstackOpmlImport
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.PrefChoice
import com.aimdi.xtanative.store.settings.SubstackColumnWidth
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.store.settings.SubstackReaderFont
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberTextFileChooser
import com.aimdi.xtanative.ui.plugins.rememberTextFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Substack's settings, as XTA's SubstackSettingsScreen: "Native reader
 * (preview)", marking posts read as they scroll past, the follows to OPML
 * and back, and the reader's font and column. They are the app's settings
 * under XTA's keys (SubstackPrefs), so the app's backup carries them. The
 * native reader switch chooses between the web view and the native reader on
 * Android; the desktop, which has no web view, always reads natively and
 * does not offer it.
 */
@Composable
internal fun SubstackSettingsBody(app: SubstackApp, frame: PluginFrame) {
    val settings = app.host.settings
    val nativeReader by settings.observe(SubstackPrefs.nativeReader).collectAsState(SubstackPrefs.nativeReader.default)
    val markOnScroll by settings.observe(SubstackPrefs.markReadOnScroll).collectAsState(SubstackPrefs.markReadOnScroll.default)
    val font by settings.observe(SubstackPrefs.readerFont).collectAsState(SubstackPrefs.readerFont.default)
    val column by settings.observe(SubstackPrefs.columnWidth).collectAsState(SubstackPrefs.columnWidth.default)
    val notices = rememberNotices()
    WithNotices(notices) {
        LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            if (articleWebView) {
                item(key = "native") {
                    SwitchRow(Strings.native_reader, Strings.native_reader_description, nativeReader) { on -> set(app, SubstackPrefs.nativeReader, on) }
                }
            }
            item(key = "scroll") {
                SwitchRow(Strings.mark_read_on_scroll, Strings.mark_read_on_scroll_description, markOnScroll) { on -> set(app, SubstackPrefs.markReadOnScroll, on) }
                HorizontalDivider()
            }
            item(key = "opml") { OpmlRows(app, notices) }
            item(key = "font") {
                ChoiceRow(Strings.reader_font, font, SubstackReaderFont.entries, ::fontLabel) { choice -> set(app, SubstackPrefs.readerFont, choice) }
            }
            item(key = "column") {
                ChoiceRow(Strings.column_width, column, SubstackColumnWidth.entries, ::columnLabel) { choice -> set(app, SubstackPrefs.columnWidth, choice) }
            }
        }
    }
}

private fun <T> set(app: SubstackApp, pref: Pref<T>, value: T) = app.launch { app.host.settings.set(pref, value) }

private fun fontLabel(font: SubstackReaderFont): PluginText = if (font == SubstackReaderFont.SERIF) Strings.font_serif else Strings.font_sans

private fun columnLabel(column: SubstackColumnWidth): PluginText = when (column) {
    SubstackColumnWidth.NARROW -> Strings.column_narrow
    SubstackColumnWidth.MEDIUM -> Strings.column_medium
    SubstackColumnWidth.WIDE -> Strings.column_wide
}

@Composable
private fun SwitchRow(title: PluginText, description: PluginText, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pluginString(title), style = MaterialTheme.typography.bodyLarge)
            Text(pluginString(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A setting with a few choices, picked from a menu as XTA's dropdowns are. */
@Composable
private fun <E : PrefChoice> ChoiceRow(title: PluginText, value: E, choices: List<E>, label: (E) -> PluginText, onChoose: (E) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluginString(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(pluginString(label(value)))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(pluginString(label(choice))) }, onClick = { open = false; onChoose(choice) }) }
            }
        }
    }
}

/** An OPML import under way, kept beyond the settings screen while it runs. */
internal class OpmlImports {
    private val mutable = MutableStateFlow<SubstackOpmlImport?>(null)
    val progress: StateFlow<SubstackOpmlImport?> = mutable.asStateFlow()

    val running: Boolean get() = progress.value?.let { it.done < it.total } == true

    /** Follows each of [sites] not followed yet, as XTA's SubstackOpmlImportStore. */
    suspend fun run(app: SubstackApp, sites: List<String>): SubstackOpmlImport = try {
        app.follows.load()
        SubstackOpml.import(
            sites,
            resolve = { app.client.resolve(it) },
            followOf = app::followOf,
            follow = { app.follows.follow(it) },
            progress = { mutable.value = it },
        )
    } catch (e: CancellationException) {
        mutable.value = null
        throw e
    }
}

/** The follows to an OPML file other feed readers import, and an OPML file's publications followed here. */
@Composable
private fun OpmlRows(app: SubstackApp, notices: Notices) {
    val progress by app.opml.progress.collectAsState()
    val running = progress?.let { it.done < it.total } == true
    val empty = pluginString(Strings.opml_empty)
    val language = currentLanguage()
    val title = pluginString(Strings.plugin_name)
    val scope = rememberCoroutineScope()
    val save = rememberTextFileSaver(pluginString(Strings.opml_export), "text/x-opml") { name -> if (name != null) notices.show(Strings.data_exported_to.get(language, name)) }
    val choose = rememberTextFileChooser(pluginString(Strings.opml_import)) { read ->
        app.launch {
            val sites = runCatching { read() }.getOrNull()?.let(SubstackOpml::sites).orEmpty()
            if (sites.isEmpty()) return@launch notices.show(empty)
            val result = app.opml.run(app, sites)
            notices.show(Strings.opml_result.get(language, result.added, result.alreadyFollowed, result.failed))
        }
    }
    Column {
        ActionRow(SubstackIcons.Upload, pluginString(Strings.opml_export), pluginString(Strings.opml_export_description), enabled = !running) {
            scope.launch { save(FILE_NAME, SubstackOpml.export(app.follows.load().map { it.publication }, title)) }
        }
        val importing = progress?.takeIf { running }
        ActionRow(
            SubstackIcons.Download,
            pluginString(Strings.opml_import),
            if (importing != null) pluginString(Strings.opml_importing, importing.done, importing.total) else pluginString(Strings.opml_import_description),
            enabled = !running,
            onClick = choose,
        )
        if (importing != null) LinearProgressIndicator(progress = { importing.done.toFloat() / importing.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val FILE_NAME = "substack-follows.opml"
