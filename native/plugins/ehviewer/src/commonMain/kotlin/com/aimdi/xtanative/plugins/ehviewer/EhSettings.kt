package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.store.settings.EhReadingMode
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginActionRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginIcons
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.PluginSwitchRow
import com.aimdi.xtanative.ui.plugins.ShowTabSwitch
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * EH's settings (eh_settings.dart), in XTA's order: the account (ExHentai,
 * the pasted cookies, kept in the plugin's own folder, and clearing them),
 * the front page's categories, and reading (Japanese titles, the reading
 * mode, the screen kept on, clearing the history), then a test of the site.
 */
@Composable
internal fun EhSettingsBody(app: EhApp, frame: PluginFrame) = WithSettings(app.host.settings) { settings ->
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = frame.padding) {
            item(key = "account") { Column { AccountSection(app, settings) } }
            item(key = "categories") { Column { CategoriesSection(app, settings) } }
            item(key = "reading") { Column { ReadingSection(app, settings) } }
            item(key = "test") { TestConnection(app) }
        }
        NoticeHost(app.notices, Modifier.padding(frame.padding))
    }
}

@Composable
private fun AccountSection(app: EhApp, settings: Settings) {
    val stored by app.cookies.state.collectAsState()
    var typed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { app.cookies.load() }
    PluginSectionTitle(pluginString(Strings.section_account))
    PluginSwitchRow(
        pluginString(Strings.use_exhentai),
        settings[EhViewerPrefs.useExhentai],
        onChange = { on -> app.update { it.with(EhViewerPrefs.useExhentai, on) } },
        description = pluginString(Strings.use_exhentai_description),
    )
    OutlinedTextField(
        value = typed ?: stored.orEmpty(),
        onValueChange = { value ->
            typed = value
            app.launch { app.cookies.set(value) }
        },
        minLines = 3,
        maxLines = 3,
        label = { Text(pluginString(Strings.cookies)) },
        supportingText = { Text(pluginString(Strings.cookies_help)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    PluginActionRow(pluginString(Strings.clear_cookies), onClick = {
        typed = ""
        app.launch { app.cookies.set("") }
    })
}

/** The categories the front page keeps to: a tap adds or takes one away, and the last one chosen stays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(app: EhApp, settings: Settings) {
    val chosen = EhConfig.categories(settings)
    PluginSectionTitle(pluginString(Strings.section_categories))
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EhCategory.entries.forEach { category ->
            val selected = category in chosen
            FilterChip(
                selected = selected,
                onClick = { app.update { EhConfig.withCategory(it, category, !selected) } },
                label = { Text(category.label) },
                leadingIcon = chipCheck(selected),
            )
        }
    }
}

@Composable
private fun ReadingSection(app: EhApp, settings: Settings) {
    PluginSectionTitle(pluginString(Strings.section_reading))
    PluginSwitchRow(
        pluginString(Strings.prefer_japanese),
        EhConfig.preferJapanese(settings),
        onChange = { on -> app.update { it.with(EhViewerPrefs.preferJapanese, on) } },
        description = pluginString(Strings.prefer_japanese_description),
    )
    ModeRow(EhConfig.readingMode(settings)) { mode -> app.update { it.with(EhViewerPrefs.readingMode, mode) } }
    PluginSwitchRow(
        pluginString(Strings.keep_screen_on),
        EhConfig.keepScreenOn(settings),
        onChange = { on -> app.update { it.with(EhViewerPrefs.keepScreenOn, on) } },
        description = pluginString(Strings.keep_screen_on_description),
    )
    PluginActionRow(pluginString(Strings.clear_history), onClick = { app.launch { app.history.clear() } })
    ShowTabSwitch(app.host.settings, pluginString(Strings.show_tab), pluginString(Strings.show_tab_description))
}

/** Reads Popular and says how many galleries the site answered with, or why it did not. */
@Composable
private fun TestConnection(app: EhApp) {
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    PluginActionRow(
        pluginString(Strings.test_connection),
        enabled = !testing,
        trailing = {
            if (testing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(PluginIcons.NetworkCheck, contentDescription = null)
        },
        onClick = {
            testing = true
            scope.launch {
                try {
                    app.ready()
                    val page = app.client.popular()
                    app.notices.tell(Strings.test_ok.get(language, page.items.size))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.notices.tell(Strings.test_failed.get(language, EhText.error(e).get(language)))
                } finally {
                    testing = false
                }
            }
        },
    )
}

/** The reading mode as XTA's dropdown: the mode in use at the row's end, the others in a menu that drops from it. */
@Composable
private fun ModeRow(current: EhReadingMode, onPick: (EhReadingMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PluginActionRow(pluginString(Strings.reader_mode), onClick = { open = true }, trailing = {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(EhText.readingMode(current)), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                EhReadingMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(pluginString(EhText.readingMode(mode))) }, onClick = {
                        open = false
                        onPick(mode)
                    })
                }
            }
        }
    })
}
