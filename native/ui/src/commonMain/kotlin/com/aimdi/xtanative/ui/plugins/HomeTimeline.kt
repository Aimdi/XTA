package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.plugins.showsPluginAsTab
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_in_home_timeline_description
import com.aimdi.xtanative.ui.generated.resources.settings_in_home_timeline
import com.aimdi.xtanative.ui.home.HomeIcons
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A plugin's "Show <network> tab" switch for its settings, as XTA's Booru, EH,
 * Instagram and TikTok settings have one ([title] over [description]): on,
 * its page sits on Home's bottom bar; off, it waits among Home's timelines
 * instead, as the store's "Show as a tab" turns it.
 */
@Composable
fun ShowTabSwitch(settings: PluginSettings, title: String, description: String, modifier: Modifier = Modifier) {
    val current by settings.state.collectAsState()
    val plugins = LocalPlugins.current
    val scope = rememberCoroutineScope()
    val id = settings.pluginId
    PluginSwitchRow(title, current?.showsPluginAsTab(id) == true, { on -> scope.launch { plugins.showAsTab(id, on) } }, modifier, description)
}

/**
 * A plugin's "show in the home timeline" switch for its settings (XTA's
 * in_home_feed): on, the posts of what is followed in it join Home's
 * Following and For you ([PluginFeed]). [network] names the plugin in the
 * reader's language. Off until the reader turns it on.
 */
@Composable
fun HomeTimelineSwitch(settings: PluginSettings, network: String, modifier: Modifier = Modifier) = HomeTimelineSwitch(
    settings,
    stringResource(Res.string.settings_in_home_timeline),
    stringResource(Res.string.settings_in_home_timeline_description, network),
    modifier,
)

/**
 * The same switch in the plugin's own words, as each of XTA's networks words
 * it: [title] ("Mix followed feeds into Following") over [description].
 */
@Composable
fun HomeTimelineSwitch(settings: PluginSettings, title: String, description: String, modifier: Modifier = Modifier) {
    TimelineSwitch(settings, title, description, null, modifier)
}

/** The same switch with [icon] before its words, as XTA's settings lead it with dynamic_feed ([HomeTimelineIcon]). */
@Composable
fun HomeTimelineSwitch(settings: PluginSettings, title: String, description: String, icon: ImageVector, modifier: Modifier = Modifier) {
    TimelineSwitch(settings, title, description, icon, modifier)
}

/** The glyph XTA's networks lead their "show in the home timeline" switch with: Material's dynamic_feed. */
val HomeTimelineIcon: ImageVector get() = HomeIcons.DynamicFeed

@Composable
private fun TimelineSwitch(settings: PluginSettings, title: String, description: String, icon: ImageVector?, modifier: Modifier) {
    val current by settings.state.collectAsState()
    val on = settings.inHomeFeed(current)
    val scope = rememberCoroutineScope()
    val turn: (Boolean) -> Unit = { value -> scope.launch { settings.setInHomeFeed(value) } }
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Switch) { turn(!on) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp)) }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = turn)
    }
}
