package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Where an empty state ([EmptyState]) stands in the room it has. */
object EmptyStates {
    /** The widest its words run, in dp: a short paragraph, not a line across a wide window. */
    const val MAX_WIDTH = 420f

    /** Its height above the room's third, in dp: the icon and title stand about a third of the way down. */
    private const val ABOVE_THIRD = 72f

    /** The least room over it, in dp, in a short window. */
    private const val MIN_TOP = 24f

    /** The room over it in a place [height] dp high: about a third of the way down; a little where the height is unbounded. */
    fun top(height: Float): Float = if (height.isFinite()) (height / 3 - ABOVE_THIRD).coerceAtLeast(MIN_TOP) else MIN_TOP
}

/**
 * In place of a list with nothing in it yet, one look for every screen: a
 * line [icon] in the muted colour, what there is to say ([title]), more
 * about it ([body]) and what may be done ([action], a button), centred
 * about a third of the way down. It scrolls where the window is too short.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier, body: String? = null, action: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = EmptyStates.top(maxHeight.value).dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = EmptyStates.MAX_WIDTH.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp).size(48.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                if (action != null) Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { action() }
            }
        }
    }
}
