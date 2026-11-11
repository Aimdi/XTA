package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.ui.plugins.pluginString

/** A list row drawn on what it sits on, a screen or a sheet. */
@Composable
internal fun plainRow(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

/** The star of a ticker's or a token's page: the getquin "add to watchlist" from a symbol. */
@Composable
internal fun WatchButton(watched: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(if (watched) Icons.Filled.Star else Icons.Outlined.Star, pluginString(if (watched) Strings.unwatch else Strings.watch))
    }
}

/** No market data came: the reason ([notFound]: the host has no price for it, else it did not answer), and a way to ask again. */
@Composable
internal fun Unavailable(onRetry: () -> Unit, notFound: Boolean = false) {
    ListItem(
        headlineContent = { Text(pluginString(Strings.data_unavailable)) },
        supportingContent = if (notFound) ({ Text(pluginString(Strings.no_price_reason)) }) else null,
        leadingContent = { Icon(StocksIcons.CloudOff, null) },
        trailingContent = { TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) } },
        colors = plainRow(),
    )
}

/**
 * Key figures as label and value rows in as many columns as fit (ticker_stats.dart,
 * Google Finance's grid). A figure the source did not send is a dash, so the
 * grid keeps its shape from symbol to symbol.
 */
@Composable
internal fun StatGrid(stats: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / (150.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 3)
        Column {
            stats.chunked(columns).forEach { row ->
                Row {
                    (0 until columns).forEach { index ->
                        if (index > 0) Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) { row.getOrNull(index)?.let { (label, value) -> StatCell(label, value) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.width(8.dp))
        val spoken = if (value == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = StockNumbers.spoken(value) }
        Text(
            value ?: StockNumbers.PLACEHOLDER,
            spoken.alignByBaseline(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR),
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
