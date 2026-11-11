package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.common.ReadFailureSheet
import com.aimdi.xtanative.ui.common.ReadSource

/**
 * One chip of a plugin's filter row, as XTA draws its FilterChip
 * (showCheckmark): [label], and a check before it while it is [selected].
 */
@Composable
fun PluginFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }) else null,
        modifier = modifier,
    )
}

/**
 * Some of a surface's sources failed while the rest loaded (XTA's
 * PartialFailureButton): a warning for a row the surface already has (its
 * filter chips, its toolbar), which opens what failed ([message]) and Retry
 * in the failure's sheet, so the content below keeps its place.
 */
@Composable
fun PartialFailureButton(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val surface = LocalPluginSurface.current
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) { Icon(Icons.Outlined.Warning, message, tint = MaterialTheme.colorScheme.error) }
    if (open) {
        ReadFailureSheet(ReadSource(surface?.name.orEmpty(), isX = false, openSettings = surface?.openSettings), null, onRetry, message) { open = false }
    }
}
