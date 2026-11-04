package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.LocalGraph
import org.jetbrains.compose.resources.stringResource

/**
 * The screen at `settings/<id>`: one of XTA's sections, or a network's own
 * section built from its preferences. [focus] is the key of the control the
 * search opened it for.
 */
@Composable
fun SettingsSectionScreen(id: String, focus: String?, onBack: () -> Unit, onOpen: (String) -> Unit, onStartOver: () -> Unit) {
    val graph = LocalGraph.current
    val vm: SettingsViewModel = viewModel(key = "settings-$id") { SettingsViewModel(graph) }
    val section = SettingsSection.of(id)
    if (section == null) {
        val network = NetworkSections.of(id)
        if (network == null) LaunchedEffect(id) { onBack() } else NetworkSettings(network, vm, onBack)
        return
    }
    val title = stringResource(sectionTitle(section))
    when (section) {
        SettingsSection.GENERAL -> GeneralSettings(title, vm, focus, onBack)
        SettingsSection.POSTS -> PostsSettings(title, vm, focus, onBack)
        SettingsSection.MEDIA -> MediaSettings(title, vm, focus, onBack)
        SettingsSection.HOME -> HomeSettings(title, vm, onBack)
        SettingsSection.THEME -> ThemeSettings(title, vm, focus, onBack)
        SettingsSection.ACCESSIBILITY -> AccessibilitySettings(title, vm, focus, onBack, onOpen)
        SettingsSection.SPEECH -> SpeechSettings(title, vm, onBack)
        SettingsSection.ACCOUNT -> AccountSettings(title, vm, onBack, onOpen, onStartOver)
        SettingsSection.DATA -> DataSettings(title, vm, onBack, onOpen)
        SettingsSection.SYNC -> SyncSettings(title, vm, onBack)
        SettingsSection.AI -> AiSettings(title, vm, onBack)
        SettingsSection.DIAGNOSTICS -> DiagnosticsSettings(title, vm, onBack)
    }
}
