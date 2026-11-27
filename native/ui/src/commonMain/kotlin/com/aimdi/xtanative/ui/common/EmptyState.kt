package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    StateFrame(modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp).size(STATE_MARK.dp))
        StateTitle(title)
        body?.let { StateBody(it) }
        if (action != null) StateActions { action() }
    }
}

/** How big an empty or failed page's mark is ([EmptyState], [ProblemView]), in dp. */
internal const val STATE_MARK = 48

/**
 * Where an empty or failed page's words stand ([EmptyState], [ProblemView]):
 * centred, no wider than [EmptyStates.MAX_WIDTH], about a third of the way
 * down the room it is given, scrolling where that is too short; in a list
 * (no height to fill) just under what is above it.
 */
@Composable
internal fun StateFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bounded = constraints.hasBoundedHeight
        Column(
            (if (bounded) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
                .padding(start = 32.dp, end = 32.dp, top = EmptyStates.top(if (bounded) maxHeight.value else Float.POSITIVE_INFINITY).dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = EmptyStates.MAX_WIDTH.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** An empty or failed page's title, read out as it appears. */
@Composable
internal fun StateTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** More about it, under [StateTitle], muted. */
@Composable
internal fun StateBody(text: String, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** What may be done about it: its buttons, a little apart from the words. */
@Composable
internal fun StateActions(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
