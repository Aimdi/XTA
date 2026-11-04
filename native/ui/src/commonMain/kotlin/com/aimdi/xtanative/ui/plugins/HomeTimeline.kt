package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_in_home_timeline_description
import com.aimdi.xtanative.ui.generated.resources.settings_in_home_timeline
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A plugin's "show in the home timeline" switch for its settings (XTA's
 * in_home_feed): on, the posts of what is followed in it join Home's
 * Following and For you ([PluginFeed]). [network] names the plugin in the
 * reader's language. Off until the reader turns it on.
 */
@Composable
fun HomeTimelineSwitch(settings: PluginSettings, network: String, modifier: Modifier = Modifier) {
    val current by settings.state.collectAsState()
    val on = settings.inHomeFeed(current)
    val scope = rememberCoroutineScope()
    val turn: (Boolean) -> Unit = { value -> scope.launch { settings.setInHomeFeed(value) } }
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Switch) { turn(!on) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(Res.string.settings_in_home_timeline), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.settings_in_home_timeline_description, network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = turn)
    }
}
