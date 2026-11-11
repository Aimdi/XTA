package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow

/**
 * A home tab's own tabs with their [icon] and [label], the [selected] one
 * marked, and [actions] (a search, the settings) at the row's end: XTA's tab
 * strip, [PluginHomeTabs], which it is kept for the plugins built with it.
 */
@Composable
fun <T> PluginChipTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: (T) -> ImageVector,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    PluginHomeTabs(tabs, selected, onSelect, icon, label, modifier, actions = actions)
}

/** [content] with the app's settings once they are read ([PluginSettings.state]); a loading row until then. */
@Composable
fun WithSettings(settings: PluginSettings, content: @Composable (Settings) -> Unit) {
    val now by settings.state.collectAsState()
    val loaded = now
    if (loaded == null) LoadingRow() else content(loaded)
}
