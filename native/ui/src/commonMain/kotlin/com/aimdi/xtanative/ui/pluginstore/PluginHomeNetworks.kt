package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.home.HomeNetworks
import com.aimdi.xtanative.ui.home.NetworkOption
import com.aimdi.xtanative.ui.home.StripNetwork
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.currentLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource

/**
 * The plugins' timelines for Home's strip: every running plugin with a home
 * tab can sit there (the reader pins it in the strip's sheet, or in the
 * store). Its page is its home tab, its mark its icon in its brand colour, and
 * its options its search and its settings, where it has them.
 */
internal class PluginHomeNetworks(private val plugins: AppPlugins) : HomeNetworks {
    private val refreshes = MutableStateFlow<Map<String, Int>>(emptyMap())

    override val available: StateFlow<List<StripNetwork>> get() = plugins.stripNetworks

    @Composable
    override fun Mark(id: String, size: Dp) {
        val plugin = runningPlugin(id)?.plugin ?: return
        Icon(plugin.icon, contentDescription = null, tint = plugin.brandColor, modifier = Modifier.size(size))
    }

    @Composable
    override fun Page(id: String, contentPadding: PaddingValues, toTop: Int) {
        val body = runningPlugin(id)?.plugin?.homeTab ?: return
        val refreshed by refreshes.collectAsState()
        SavesMediaOf(id) { body(PluginFrame(contentPadding, toTop, refreshed[id] ?: 0)) }
    }

    @Composable
    override fun options(id: String): List<NetworkOption> {
        val plugin = runningPlugin(id)?.plugin ?: return emptyList()
        val language = currentLanguage()
        val searchLabel = stringResource(Res.string.search)
        val settings = stringResource(Res.string.settings)
        return remember(plugin, language, searchLabel, settings) {
            listOfNotNull(
                plugin.search?.let { search ->
                    val label = runCatching { search.hint(language) }.getOrNull() ?: searchLabel
                    NetworkOption(SEARCH, label, Icons.Filled.Search) { plugins.navigator.go(Destination.PluginSearch(id, null)) }
                },
                plugin.settings?.let {
                    NetworkOption(SETTINGS, settings, Icons.Filled.Settings) { plugins.navigator.go(Destination.PluginSettings(id)) }
                },
            )
        }
    }

    /** A plugin does not say what its reader has not seen: no dot. */
    @Composable
    override fun unread(id: String): Boolean = false

    override fun refresh(id: String) {
        refreshes.update { it + (id to (it[id] ?: 0) + 1) }
    }

    private companion object {
        /** The options' ids, which the top bar's pins keep. */
        const val SEARCH = "search"
        const val SETTINGS = "settings"
    }
}
