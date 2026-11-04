package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/** A running plugin that searches its network: Discover's chips and the library search offer it. */
class SearchablePlugin(val plugin: XtaPlugin, val search: PluginSearch) {
    val id: String get() = plugin.id

    /** Its name in [language]; its id when its code cannot say. */
    fun title(language: String): String = runCatching { plugin.title(language) }.getOrNull() ?: plugin.id

    /** Its search field's placeholder in [language], or null when its code cannot say. */
    fun hint(language: String): String? = runCatching { search.hint(language) }.getOrNull()

    /** Whether Discover shows its landing while nothing is typed ([PluginSearch.landsInDiscover]); not when its code cannot say. */
    val landsInDiscover: Boolean get() = runCatching { search.landsInDiscover }.getOrDefault(false)
}

/** The running plugins that search, in the store's order, as XTA's searchablePlugins lists the networks that are on. */
@Composable
fun searchablePlugins(): List<SearchablePlugin> {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    return remember(running) {
        running.mapNotNull { entry -> runCatching { entry.plugin.search }.getOrNull()?.let { SearchablePlugin(entry.plugin, it) } }
    }
}

/** A searching plugin's mark, in its colour, as a chip's icon. */
@Composable
fun PluginChipMark(plugin: SearchablePlugin) {
    Icon(plugin.plugin.icon, contentDescription = null, tint = plugin.plugin.brandColor, modifier = Modifier.size(18.dp))
}
