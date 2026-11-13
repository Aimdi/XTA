package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * In place of a list, as XTA's plugins draw an empty tab: a large [icon],
 * what there is to say ([title], then [detail] in the muted colour), and
 * what may be done about it ([actions], buttons stacked under it).
 */
@Composable
fun EmptyPane(icon: ImageVector, title: String, modifier: Modifier = Modifier, detail: String? = null, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(52.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { actions() }
    }
}
