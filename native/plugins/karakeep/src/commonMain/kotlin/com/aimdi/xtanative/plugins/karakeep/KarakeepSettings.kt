package com.aimdi.xtanative.plugins.karakeep

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.ui.plugins.PluginAddressField
import com.aimdi.xtanative.ui.plugins.PluginConnection
import com.aimdi.xtanative.ui.plugins.PluginConnectionControls
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginLinkButton
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.SecretField
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString

private const val ABOUT = "https://karakeep.app"

/**
 * XTA's Karakeep settings (karakeep_settings_screen.dart), in its order: what
 * Karakeep is, the server's address and the API key, Test connection and
 * Save with what the test said, and About Karakeep. The fields are kept on
 * Test or Save, as XTA keeps them.
 */
@Composable
internal fun KarakeepSettingsBody(app: KarakeepApp, frame: PluginFrame) {
    val loaded by app.host.settings.state.collectAsState()
    val stored = loaded ?: return
    var address by rememberSaveable { mutableStateOf(stored[KarakeepPrefs.serverUrl]) }
    var apiKey by rememberSaveable { mutableStateOf(stored[KarakeepPrefs.apiKey]) }
    val connection = remember { PluginConnection() }
    val language = currentLanguage()
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(frame.padding)) {
        Text(pluginString(Strings.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
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
        PluginLinkButton(pluginString(Strings.learn_more), { app.host.openLink(ABOUT) }, Modifier.padding(start = 8.dp, top = 12.dp))
    }
}
