package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyFollow
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The plugin's settings, in XTA's order (bluesky_settings.dart): whether the
 * accounts followed here join Home's timelines, the AppView reads go to
 * (tested here, or back to Bluesky's), the reader's own handle, the imports,
 * and the accounts followed here, each to open, put in groups or unfollow.
 */
@Composable
internal fun BlueskySettingsBody(app: BlueskyApp, frame: PluginFrame) {
    val follows by app.follows.state.collectAsState()
    LaunchedEffect(Unit) { app.follows.load() }
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.plugin_name)) }
        item(key = "intro") { Text(pluginString(Strings.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
        item(key = "appview") { AppViewSetting(app) }
        item(key = "handle") { HandleSetting(app) }
        item(key = "imports") {
            Column(Modifier.padding(top = 16.dp)) {
                MenuRow(DiscoverIcons.CloudDownload, pluginString(Strings.import_following)) {
                    app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING))
                }
                MenuRow(BlueskyIcons.ListAlt, pluginString(Strings.import_list)) { app.host.open(BlueskyRoutes.import(ImportKind.LIST)) }
            }
        }
        item(key = "accounts") { SectionTitle(pluginString(Strings.settings_accounts)) }
        val followed = follows.orEmpty()
        if (follows != null && followed.isEmpty()) {
            item(key = "none") {
                Text(
                    pluginString(Strings.no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(followed, key = { "follow-" + it.actor }) { follow -> FollowRow(app, follow) }
    }
}

/** The AppView, kept as typed (a moment after typing stops); Test reads bsky.app's profile there, Use default goes back to Bluesky's. */
@Composable
private fun AppViewSetting(app: BlueskyApp) {
    val stored by app.prefs.instance.collectAsState()
    var text by rememberSaveable { mutableStateOf(stored.ifBlank { Bluesky.DEFAULT_APP_VIEW }) }
    var testing by remember { mutableStateOf(false) }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    LaunchedEffect(text) {
        delay(SAVE_AFTER_MILLIS)
        if (text.trim() != stored) app.prefs.set(BlueskyPrefs.instance, text.trim())
    }
    suspend fun save() {
        text = BlueskyUrls.appView(text) ?: text.trim()
        app.prefs.set(BlueskyPrefs.instance, text)
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pluginString(Strings.instance), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text(pluginString(Strings.instance_hint)) }, modifier = Modifier.fillMaxWidth())
        NoteText(pluginString(Strings.instance_default, Bluesky.DEFAULT_APP_VIEW))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !testing, onClick = {
                scope.launch {
                    testing = true
                    save()
                    answer = try {
                        app.client.verify()
                        Strings.test_ok.get(language)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorText(e).get(language)
                    }
                    testing = false
                }
            }) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(pluginString(Strings.test))
            }
            TextButton(onClick = {
                text = Bluesky.DEFAULT_APP_VIEW
                scope.launch { save() }
            }) { Text(pluginString(Strings.use_default)) }
        }
        answer?.let { NoteText(it) }
    }
}

/** The reader's own public handle, whose lists and custom feeds are offered on Discover and Lists. */
@Composable
private fun HandleSetting(app: BlueskyApp) {
    val stored by app.prefs.handle.collectAsState()
    var text by rememberSaveable { mutableStateOf(stored) }
    LaunchedEffect(text) {
        delay(SAVE_AFTER_MILLIS)
        if (text.trim() != stored) app.prefs.set(BlueskyPrefs.handle, text.trim())
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pluginString(Strings.your_handle), style = MaterialTheme.typography.titleSmall)
        NoteText(pluginString(Strings.your_handle_description))
        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text(pluginString(Strings.handle_hint)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FollowRow(app: BlueskyApp, follow: BlueskyFollow) {
    Row(
        Modifier.fillMaxWidth().clickable { app.openProfile(follow.actor) }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(follow.avatarUrl, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(follow.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + follow.shownHandle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        AddToGroupButton(app.host, follow.actor)
        TextButton(onClick = { app.launch { app.follows.unfollow(follow.actor) } }) { Text(pluginString(Strings.unfollow)) }
    }
    Hairline()
}

private const val SAVE_AFTER_MILLIS = 600L
