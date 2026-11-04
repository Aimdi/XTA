package com.aimdi.xtanative.plugins.instagram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.InstagramPrefs
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.SecretField
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Instagram's settings (instagram_settings.dart): guest browsing first, and
 * the optional session: cookies pasted or copied from Threads, cleared, and
 * a test that reads the public @instagram profile. The cookies are never
 * backed up or exported.
 */
@Composable
internal fun InstagramSettingsBody(app: InstagramApp, frame: PluginFrame) {
    val language = currentLanguage()
    var cookies by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(app) { cookies = app.settings()[InstagramPrefs.cookies] }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            item(key = "guest") { GuestNote() }
            item(key = "section") { Text(pluginString(Strings.section_session), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
            cookies?.let { pasted ->
                item(key = "cookies") {
                    SecretField(
                        value = pasted,
                        onValueChange = { typed ->
                            cookies = typed
                            app.launch { app.setCookies(typed) }
                        },
                        label = pluginString(Strings.cookies),
                        reveal = pluginString(Strings.show),
                        conceal = pluginString(Strings.hide),
                        hint = pluginString(Strings.cookies_hint),
                        supporting = pluginString(Strings.cookies_description),
                        lines = 4,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item(key = "import") {
                ActionRow(InstagramIcons.Copy, Strings.import_threads_cookies, Strings.import_threads_cookies_description) {
                    scope.launch {
                        val copied = app.importThreadsCookies()
                        if (copied) cookies = app.settings()[InstagramPrefs.cookies]
                        app.notices.tell((if (copied) Strings.import_threads_ok else Strings.import_threads_empty).get(language))
                    }
                }
            }
            item(key = "clear") {
                ActionRow(Icons.Filled.Delete, Strings.clear_session, Strings.clear_session_description) {
                    scope.launch {
                        app.clearSession()
                        cookies = ""
                        app.notices.tell(Strings.session_cleared.get(language))
                    }
                }
            }
            item(key = "test") {
                ActionRow(Icons.Filled.Refresh, Strings.test_connection, Strings.test_connection_description, busy = testing) {
                    if (testing) return@ActionRow
                    testing = true
                    scope.launch {
                        cookies?.let { app.setCookies(it) }
                        app.notices.tell(tested(app, language))
                        testing = false
                    }
                }
            }
        }
        NoticeHost(app.notices)
    }
}

/** What the test says: whom Instagram answered with, or why it did not. */
private suspend fun tested(app: InstagramApp, language: String): String = try {
    Strings.test_ok.get(language, app.client().profile("instagram").displayName)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Strings.test_failed.get(language, InstagramFailures.of(e).get(language))
}

@Composable
private fun GuestNote() {
    Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(pluginString(Strings.guest_note))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: PluginText, detail: PluginText, busy: Boolean = false, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(pluginString(title)) },
        supportingContent = { Text(pluginString(detail)) },
        trailingContent = if (busy) { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
