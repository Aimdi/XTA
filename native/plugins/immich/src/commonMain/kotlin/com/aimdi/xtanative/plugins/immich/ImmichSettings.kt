package com.aimdi.xtanative.plugins.immich

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.ImmichPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginAddressField
import com.aimdi.xtanative.ui.plugins.PluginConnection
import com.aimdi.xtanative.ui.plugins.PluginConnectionControls
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginLinkButton
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.PluginSwitchRow
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.SecretField
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.launch

private const val ABOUT = "https://immich.app"

/**
 * XTA's Immich settings (immich_settings_screen.dart), in its order: what
 * the plugin does, the server's address and the API key, Test connection
 * and Save with what the test said; then the upload options, an album per
 * folder and videos too, where to switch uploading on, and About Immich. The
 * fields are kept on Test or Save, the options as they are switched.
 */
@Composable
internal fun ImmichSettingsBody(app: ImmichApp, frame: PluginFrame) {
    val loaded by app.host.settings.state.collectAsState()
    val stored = loaded ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(frame.padding)) {
        Text(pluginString(Strings.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        Connection(app, stored)
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        PluginSectionTitle(pluginString(Strings.options))
        Option(app, stored, ImmichPrefs.albumPerFolder, Strings.album_per_folder, Strings.album_per_folder_description)
        Option(app, stored, ImmichPrefs.includeVideos, Strings.include_videos, Strings.include_videos_description)
        Text(
            pluginString(Strings.folder_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        PluginLinkButton(pluginString(Strings.learn_more), { app.host.openLink(ABOUT) }, Modifier.padding(start = 8.dp, top = 12.dp))
    }
}

/** The server's address and the API key, with Test connection and Save. */
@Composable
private fun Connection(app: ImmichApp, stored: Settings) {
    var address by rememberSaveable { mutableStateOf(stored[ImmichPrefs.serverUrl]) }
    var apiKey by rememberSaveable { mutableStateOf(stored[ImmichPrefs.apiKey]) }
    val connection = remember { PluginConnection() }
    val language = currentLanguage()
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    PluginSectionTitle(pluginString(Strings.connection))
    PluginAddressField(address, { address = it; connection.invalidate() }, pluginString(Strings.server_url), field, pluginString(Strings.server_url_hint))
    SecretField(
        apiKey,
        { apiKey = it; connection.invalidate() },
        pluginString(Strings.api_key),
        reveal = pluginString(Strings.show),
        conceal = pluginString(Strings.hide),
        modifier = field.padding(top = 16.dp),
        supporting = pluginString(Strings.api_key_hint),
    )
    PluginConnectionControls(
        connection,
        pluginString(Strings.test),
        pluginString(Strings.save),
        keep = { app.keep(address, apiKey) },
        check = { app.test(address, apiKey, language) },
        onSaved = app.host::back,
        modifier = field.padding(top = 20.dp),
    )
}

/** One upload option, kept as it is switched. */
@Composable
private fun Option(app: ImmichApp, stored: Settings, option: Pref<Boolean>, label: PluginText, description: PluginText) {
    val scope = rememberCoroutineScope()
    PluginSwitchRow(pluginString(label), stored[option], { on -> scope.launch { app.set(option, on) } }, description = pluginString(description))
}
