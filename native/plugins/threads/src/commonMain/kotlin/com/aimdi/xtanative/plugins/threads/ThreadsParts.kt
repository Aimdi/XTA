package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.RetryNotice
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant
import kotlinx.coroutines.delay

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
 * Retry, or while Meta's throttle lasts, a Retry that cannot be pressed and
 * says when it can ("Try again after 10:15"): asking within the wait is how a
 * throttle becomes a ban. It wakes by itself when the wait ends.
 */
@Composable
internal fun RetryOrWait(message: String, until: Instant?, onRetry: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(until) {
        if (until == null) return@LaunchedEffect
        delay(maxOf(0L, until.toEpochMilli() - System.currentTimeMillis()))
        now = Instant.now()
    }
    if (until == null || !until.isAfter(now)) {
        RetryNotice(message, onRetry)
        return
    }
    TextButton(onClick = {}, enabled = false) { Text(pluginString(Strings.retry_after, ThreadsFailures.time(until, currentLanguage()))) }
}

/**
 * In place of the feed, why its read failed, and Retry or, while Meta's
 * throttle lasts, when Retry can be pressed.
 */
@Composable
internal fun FailedRead(message: String, retryAfter: Instant?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        RetryOrWait(message, retryAfter, onRetry)
    }
}

/**
 * How a read again over what is shown goes, as XTA's ReloadStatus says it:
 * a line over the top edge while it reads, taking no room, and when it
 * failed a floating notice with why and Retry; what is shown stays put.
 */
@Composable
internal fun BoxScope.ReloadStatus(reading: Boolean, problem: String?, onRetry: () -> Unit, retryAfter: Instant? = null) {
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
            RetryOrWait(problem, retryAfter, onRetry)
        }
    }
}
