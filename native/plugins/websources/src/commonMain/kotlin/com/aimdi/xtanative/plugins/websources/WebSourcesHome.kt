package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.InstalledWebSource
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * Web sources' home tab (web_sources_screen.dart): the sources installed,
 * each opening its lists and offering an update that waits for the reader
 * to agree to a new site, with Add source beside them.
 */
@Composable
internal fun WebSourcesHome(app: WebSourcesApp, frame: PluginFrame) {
    val installed by app.installed.collectAsState()
    Column(Modifier.fillMaxSize()) {
        // XTA's chrome here has no tabs and, on Home, no title: Home names the plugin. Add source, then Settings.
        ReadingControlsBar {
            PluginHomeTabs(emptyList()) {
                IconButton(onClick = app::openAdd) { Icon(Icons.Default.Add, pluginString(Strings.add)) }
                IconButton(onClick = app.host::openSettings) { Icon(Icons.Outlined.Settings, pluginString(Strings.settings)) }
            }
        }
        Hairline()
        val sources = installed
        when {
            sources == null -> PluginFeedSkeleton()
            sources.isEmpty() -> EmptySources(app)
            else -> {
                val list = rememberLazyListState()
                ScrollsToTop(list, frame.toTop)
                LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
                    items(sources, key = { it.source.id }) { entry -> SourceRow(app, entry) }
                }
            }
        }
    }
}

@Composable
private fun EmptySources(app: WebSourcesApp) {
    EmptyPane(WebSourcesIcons.TravelExplore, pluginString(Strings.empty)) { Button(onClick = app::openAdd) { Text(pluginString(Strings.add)) } }
}

@Composable
private fun SourceRow(app: WebSourcesApp, entry: InstalledWebSource) {
    val source = entry.source
    var reviewing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { app.openSource(source) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        WebSourceMark(source, 40.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                source.description.ifEmpty { pluginPlural(Strings.lists, source.feeds.size, source.feeds.size) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.waiting != null) FilledTonalButton(onClick = { reviewing = true }) { Text(pluginString(Strings.review_update)) }
    }
    Hairline()
    val waiting = entry.waiting
    if (reviewing && waiting != null) {
        ConsentDialog(waiting, origin = entry.origin?.host, update = true, onDismiss = { reviewing = false }) {
            reviewing = false
            app.launch { app.agreeToUpdate(source.id) }
        }
    }
}

/** Back, as the app's bars draw it. */
@Composable
internal fun BackButton(app: WebSourcesApp) {
    IconButton(onClick = app.host::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back)) }
}
