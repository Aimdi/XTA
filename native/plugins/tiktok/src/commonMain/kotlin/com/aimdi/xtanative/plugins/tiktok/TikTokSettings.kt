package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.TikTokPrefs
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginActionRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginIcons
import com.aimdi.xtanative.ui.plugins.PluginSectionTitle
import com.aimdi.xtanative.ui.plugins.PluginSwitchRow
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * TikTok's settings (tiktok_settings.dart), in XTA's order: that no account
 * is used, playback (the embedded player), and the guest session (clearing
 * it, and a test that TikTok answers).
 */
@Composable
internal fun TikTokSettingsBody(app: TikTokApp, frame: PluginFrame) = WithSettings(app.host.settings) { settings ->
    val language = currentLanguage()
    val cleared = pluginString(Strings.session_cleared)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = frame.padding) {
            item(key = "note") { GuestNote() }
            item(key = "playback") { PluginSectionTitle(pluginString(Strings.section_playback)) }
            item(key = "embed") {
                PluginSwitchRow(
                    pluginString(Strings.prefer_embed),
                    settings[TikTokPrefs.preferEmbed],
                    onChange = { on -> app.update { it.with(TikTokPrefs.preferEmbed, on) } },
                    description = pluginString(Strings.prefer_embed_description),
                )
            }
            item(key = "session") { PluginSectionTitle(pluginString(Strings.section_session)) }
            item(key = "clear") {
                PluginActionRow(
                    pluginString(Strings.clear_session),
                    description = pluginString(Strings.clear_session_description),
                    icon = TikTokIcons.Cookie,
                    onClick = {
                        app.launch {
                            app.clearSession()
                            app.notices.tell(cleared)
                        }
                    },
                )
            }
            item(key = "test") { TestConnection(app, language) }
        }
        NoticeHost(app.notices, Modifier.padding(frame.padding))
    }
}

@Composable
private fun GuestNote() {
    Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(pluginString(Strings.guest_note), Modifier.padding(start = 12.dp))
        }
    }
}

/** Opens the public @tiktok profile and says whose it is, or why it did not open. */
@Composable
private fun TestConnection(app: TikTokApp, language: String) {
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PluginActionRow(
        pluginString(Strings.test_connection),
        description = pluginString(Strings.test_connection_description),
        icon = PluginIcons.NetworkCheck,
        enabled = !testing,
        trailing = if (testing) ({ CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }) else null,
        onClick = {
            testing = true
            scope.launch {
                try {
                    val profile = app.client().profile("tiktok")
                    app.notices.tell(Strings.test_ok.get(language, profile.displayName))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.notices.tell(Strings.test_failed.get(language, TikTokText.error(e).get(language)))
                } finally {
                    testing = false
                }
            }
        },
    )
}
