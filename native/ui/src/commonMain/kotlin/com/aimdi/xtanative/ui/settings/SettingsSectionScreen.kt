package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.pluginstore.PluginSettingsScreen
import com.aimdi.xtanative.ui.speech.VoiceSettingsScreen
import org.jetbrains.compose.resources.stringResource

/**
 * The screen at `settings/<id>`: one of XTA's sections, or a network's own
 * settings (see [NetworkSections.place]). [focus] is the key of the control
 * the search opened it for.
 */
@Composable
fun SettingsSectionScreen(id: String, focus: String?, onBack: () -> Unit, onOpen: (String) -> Unit, onStartOver: () -> Unit) {
    val graph = LocalGraph.current
    val vm: SettingsViewModel = viewModel(key = "settings-$id") { SettingsViewModel(graph) }
    val section = SettingsSection.of(id)
    if (section == null) {
        NetworkSection(id, vm, onBack)
        return
    }
    val title = stringResource(sectionTitle(section))
    when (section) {
        SettingsSection.GENERAL -> GeneralSettings(title, vm, focus, onBack)
        SettingsSection.POSTS -> PostsSettings(title, vm, focus, onBack)
        SettingsSection.MEDIA -> MediaSettings(title, vm, focus, onBack)
        SettingsSection.HOME -> HomeSettings(title, vm, onBack)
        SettingsSection.THEME -> ThemeSettings(title, vm, focus, onBack)
        SettingsSection.ACCESSIBILITY -> AccessibilitySettings(title, vm, focus, onBack)
        SettingsSection.SPEECH -> VoiceSettingsScreen(onBack)
        SettingsSection.ACCOUNT -> AccountSettings(title, vm, onBack, onOpen, onStartOver)
        SettingsSection.DATA -> DataSettings(title, vm, onBack, onOpen)
        SettingsSection.SYNC -> SyncSettings(title, vm, onBack)
        SettingsSection.AI -> AiSettings(title, vm, onBack)
        SettingsSection.DIAGNOSTICS -> DiagnosticsSettings(title, vm, onBack)
    }
}

/** Network [id]'s settings: its plugin's own once it is a plugin, else the section built from its preferences. */
@Composable
private fun NetworkSection(id: String, vm: SettingsViewModel, onBack: () -> Unit) {
    when (NetworkSections.place(id, pluginIds())) {
        NetworkSettingsPlace.PLUGIN -> PluginSettingsScreen(id, onBack)
        NetworkSettingsPlace.SECTION -> NetworkSections.of(id)?.let { network -> NetworkSettings(network, vm, onBack) }
        NetworkSettingsPlace.NONE -> LaunchedEffect(id) { onBack() }
    }
}

/** What the plugin store knows now, following installs, uninstalls and the catalogue. */
@Composable
internal fun pluginIds(): PluginIds {
    val plugins = LocalPlugins.current
    val store by plugins.manager.state.collectAsStateWithLifecycle()
    val running by plugins.running.collectAsStateWithLifecycle()
    return remember(store, running) {
        PluginIds(
            listed = store.plugins.mapTo(HashSet()) { it.id },
            installed = store.installed.mapTo(HashSet()) { it.id },
            withoutSettings = running.filter { it.plugin.settings == null }.mapTo(HashSet()) { it.plugin.id },
        )
    }
}
