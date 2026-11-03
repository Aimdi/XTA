package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.pluginString

/** The plugin's settings: what Following shows, and the accounts followed here, each to open or unfollow. */
@Composable
internal fun BlueskySettingsBody(app: BlueskyApp, frame: PluginFrame) {
    val settings by app.settings.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    LaunchedEffect(Unit) {
        app.settings.load()
        app.follows.load()
    }
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "intro") {
            Text(
                pluginString(Strings.settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item(key = "feed") { Section(pluginString(Strings.settings_feed)) }
        item(key = "replies") {
            SwitchRow(pluginString(Strings.settings_replies), settings.replies) { on -> app.launch { app.settings.update { it.copy(replies = on) } } }
        }
        item(key = "reposts") {
            SwitchRow(pluginString(Strings.settings_reposts), settings.reposts) { on -> app.launch { app.settings.update { it.copy(reposts = on) } } }
        }
        item(key = "accounts") {
            Hairline(Modifier.padding(top = 8.dp))
            Section(pluginString(Strings.settings_accounts))
        }
        val followed = follows.orEmpty()
        if (follows != null && followed.isEmpty()) {
            item(key = "none") {
                Text(
                    pluginString(Strings.settings_none_followed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(followed, key = { "follow-" + it.actor }) { follow -> FollowRow(app, follow) }
    }
}

@Composable
private fun FollowRow(app: BlueskyApp, follow: BlueskyFollow) {
    Row(
        Modifier.fillMaxWidth().clickable { app.host.open(BlueskyRoutes.profile(follow.actor)) }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(follow.avatarUrl, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(follow.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + follow.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        TextButton(onClick = { app.launch { app.follows.unfollow(follow.actor) } }) { Text(pluginString(Strings.unfollow)) }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
