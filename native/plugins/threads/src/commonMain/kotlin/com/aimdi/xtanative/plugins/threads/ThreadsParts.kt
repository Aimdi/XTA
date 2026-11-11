package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.RetryNotice

/** A row of a sheet: its icon, its words, and a line under them when there is one. */
@Composable
internal fun SheetRow(icon: ImageVector, label: String, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A short line under or between rows, in the muted colour. */
@Composable
internal fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}

/**
 * How a read again over what is shown goes, as XTA's ReloadStatus says it:
 * a line over the top edge while it reads, taking no room, and when it
 * failed a floating notice with why and Retry; what is shown stays put.
 */
@Composable
internal fun BoxScope.ReloadStatus(reading: Boolean, problem: String?, onRetry: () -> Unit) {
    if (reading) {
        LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp), trackColor = Color.Transparent)
        return
    }
    if (problem == null) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 520.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(problem, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            RetryNotice(problem, onRetry)
        }
    }
}
