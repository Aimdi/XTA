package com.aimdi.xtanative.plugins.deepmarks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.deepmarks.DeepmarksAccount
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.ui.plugins.PluginAddressField
import com.aimdi.xtanative.ui.plugins.PluginConnection
import com.aimdi.xtanative.ui.plugins.PluginConnectionControls
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginLinkButton
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.SecretField
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString

private const val ABOUT = "https://github.com/ostermayer/deepmarks-public"

/**
 * XTA's Deepmarks settings (deepmarks_settings_screen.dart), in its order:
 * what Deepmarks does with the key, that the API is for lifetime members,
 * the API key, the signing key and whom it signs as, the server's address,
 * Test connection (which also checks the two keys are one account's) and
 * Save, and About Deepmarks. The fields are kept on Test or Save.
 */
@Composable
internal fun DeepmarksSettingsBody(app: DeepmarksApp, frame: PluginFrame) {
    val loaded by app.host.settings.state.collectAsState()
    val stored = loaded ?: return
    var apiKey by rememberSaveable { mutableStateOf(stored[DeepmarksPrefs.apiKey]) }
    var secret by rememberSaveable { mutableStateOf(stored[DeepmarksPrefs.secretKey]) }
    var apiBase by rememberSaveable { mutableStateOf(stored[DeepmarksPrefs.apiBase]) }
    val connection = remember { PluginConnection() }
    val language = currentLanguage()
    val field = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(frame.padding)) {
        Text(pluginString(Strings.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        LifetimeNotice(field)
        PluginSectionTitle(pluginString(Strings.connection), Modifier.padding(top = 4.dp))
        Secret(apiKey, Strings.api_key, Strings.api_key_hint, field) { apiKey = it; connection.invalidate() }
        PluginSectionTitle(pluginString(Strings.identity))
        Secret(secret, Strings.secret_key, Strings.secret_key_hint, field) { secret = it; connection.invalidate() }
        SigningAs(secret, field)
        PluginAddressField(apiBase, { apiBase = it; connection.invalidate() }, pluginString(Strings.api_base), field.padding(top = 16.dp), DeepmarksAccount.DEFAULT_API_BASE)
        PluginConnectionControls(
            connection,
            pluginString(Strings.test),
            pluginString(Strings.save),
            keep = { app.keep(apiKey, secret, apiBase) },
            check = { app.test(apiKey, secret, apiBase, language) },
            onSaved = app.host::back,
            modifier = field.padding(top = 20.dp),
        )
        PluginLinkButton(pluginString(Strings.learn_more), { app.host.openLink(ABOUT) }, Modifier.padding(start = 8.dp, top = 12.dp))
    }
}

/** API access needs a lifetime membership: said before anything is asked for. */
@Composable
private fun LifetimeNotice(modifier: Modifier) {
    Card(modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(pluginString(Strings.lifetime_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Secret(value: String, label: PluginText, hint: PluginText, modifier: Modifier, onValue: (String) -> Unit) {
    SecretField(
        value,
        onValue,
        pluginString(label),
        reveal = pluginString(Strings.show),
        conceal = pluginString(Strings.hide),
        modifier = modifier,
        supporting = pluginString(hint),
    )
}

/** "Signing as <public key>", to copy, once the signing key reads. */
@Composable
private fun SigningAs(secret: String, modifier: Modifier) {
    val publicKey = remember(secret) { DeepmarksSaves.publicKey(secret) } ?: return
    SelectionContainer(modifier.padding(top = 8.dp)) {
        Text(pluginString(Strings.signing_as, publicKey), style = MaterialTheme.typography.bodySmall)
    }
}
