package com.aimdi.xtanative.plugins.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.rss.RssOpml
import com.aimdi.xtanative.networks.rss.RssOpmlImport
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.HomeTimelineIcon
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginPlatform
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginFileDrop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberTextFileChooser
import com.aimdi.xtanative.ui.plugins.rememberTextFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RSS's settings, as XTA's RssSettingsScreen: what the plugin keeps where,
 * the followed feeds' items in Home's timeline, then the feeds followed to an OPML file and an OPML file's feeds followed
 * here, as every feed reader trades them.
 */
@Composable
internal fun RssSettingsBody(app: RssApp, frame: PluginFrame) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val importOpml = rememberOpmlImport(app) { notice = it }
    val importing by app.opml.progress.collectAsState()
    LazyColumn(
        contentPadding = frame.padding,
        modifier = Modifier.fillMaxSize().pluginFileDrop(accept = ::isOpml) { files ->
            if (importing?.let { it.done < it.total } != true) importOpml { files.first().text() }
        },
    ) {
        item(key = "blurb") { Text(pluginString(Strings.settings_blurb), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) }
        item(key = "home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.in_home_feed), pluginString(Strings.in_home_feed_description), HomeTimelineIcon) }
        item(key = "opml") {
            Hairline()
            OpmlRows(app, importOpml) { notice = it }
            notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
        }
    }
}

/** An OPML import under way, kept beyond the settings screen while it runs. */
internal class RssOpmlImports {
    private val mutable = MutableStateFlow<RssOpmlImport?>(null)
    val progress: StateFlow<RssOpmlImport?> = mutable.asStateFlow()

    /** Follows each feed of [urls] not followed yet, one at a time, as Add feed would. */
    suspend fun run(app: RssApp, urls: List<String>): RssOpmlImport = try {
        app.loadedFeeds()
        RssOpml.import(urls, isFollowed = app::isFollowing, lookup = { app.client.lookup(it) }, follow = { app.follow(it) }, progress = { mutable.value = it })
    } catch (e: CancellationException) {
        mutable.value = null
        throw e
    }
}

/** Follows the feeds of an OPML file read by the function it is given (one chosen or dropped), telling [onNotice] how it went. */
@Composable
private fun rememberOpmlImport(app: RssApp, onNotice: (String) -> Unit): (read: suspend () -> String?) -> Unit {
    val language = currentLanguage()
    val empty = pluginString(Strings.opml_empty)
    val notice by rememberUpdatedState(onNotice)
    return remember(app, language, empty) {
        { read ->
            app.launch {
                val urls = runCatching { read() }.getOrNull()?.let(RssOpml::feedUrls).orEmpty()
                if (urls.isEmpty()) return@launch notice(empty)
                val result = app.opml.run(app, urls)
                notice(Strings.opml_result.get(language, result.added, result.alreadyFollowed, result.failed))
            }
        }
    }
}

/** A dropped file that may be an OPML list: by its name, or a type of XML or text. */
internal fun isOpml(name: String, mime: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("opml", "xml") || "xml" in mime || mime == "text/plain"

@Composable
private fun OpmlRows(app: RssApp, importOpml: (read: suspend () -> String?) -> Unit, onNotice: (String) -> Unit) {
    val progress by app.opml.progress.collectAsState()
    val importing = progress?.takeIf { it.done < it.total }
    val language = currentLanguage()
    val title = pluginString(Strings.plugin_name)
    val scope = rememberCoroutineScope()
    val save = rememberTextFileSaver(pluginString(Strings.opml_export), "text/x-opml") { name -> if (name != null) onNotice(Strings.data_exported_to.get(language, name)) }
    val choose = rememberTextFileChooser(pluginString(Strings.opml_import), importOpml)
    Column {
        ActionRow(RssIcons.Upload, pluginString(Strings.opml_export), pluginString(Strings.opml_export_description), enabled = importing == null) {
            scope.launch { save(FILE_NAME, RssOpml.export(app.loadedFeeds(), title)) }
        }
        ActionRow(
            RssIcons.Download,
            pluginString(Strings.opml_import),
            when {
                importing != null -> pluginString(Strings.opml_importing, importing.done, importing.total)
                PluginPlatform.isDesktop -> pluginString(Strings.opml_import_description_desktop)
                else -> pluginString(Strings.opml_import_description)
            },
            enabled = importing == null,
            onClick = choose,
        )
        if (importing != null) LinearProgressIndicator(progress = { importing.done.toFloat() / importing.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val FILE_NAME = "rss-feeds.opml"
