package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow

/**
 * A home tab's own tabs as a row of chips, as the plugins' homes show XTA's
 * tab strip: each with its [icon] and [label], the [selected] one marked,
 * scrolling sideways when they do not fit, and [actions] (a search, the
 * settings) at the row's end.
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
    Row(modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { tab ->
                FilterChip(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    label = { Text(label(tab)) },
                    leadingIcon = { Icon(icon(tab), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        actions()
    }
}

/** [content] with the app's settings once they are read ([PluginSettings.state]); a loading row until then. */
@Composable
fun WithSettings(settings: PluginSettings, content: @Composable (Settings) -> Unit) {
    val now by settings.state.collectAsState()
    val loaded = now
    if (loaded == null) LoadingRow() else content(loaded)
}
