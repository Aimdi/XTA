package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsTrend
import com.aimdi.xtanative.networks.threads.ThreadsTrends
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/** Meta's trending topics, read once for every screen that shows them (the client keeps them half an hour). */
@Composable
private fun rememberTrends(app: ThreadsApp): Fetch<List<ThreadsTrend>> {
    val fetch = rememberKept("threads-trends") { scope -> Fetch(scope, app.direct.knownTrends()) { force -> app.ready(); app.direct.trends(force) } }
    LaunchedEffect(fetch) { fetch.ensure() }
    return fetch
}

/**
 * "Trending now" before a search, as threads.com's search page shows a
 * guest: each of Meta's trending topics with the line Meta writes under it,
 * opening its search. Nothing while there are none; why the read failed and
 * Retry (or, during a throttle, when Retry can be pressed) when it failed.
 */
@Composable
internal fun TrendingTopics(app: ThreadsApp, onOpen: (ThreadsSearchTerms) -> Unit) {
    val fetch = rememberTrends(app)
    val state by fetch.state.collectAsState()
    val trends = state.value
    val problem = state.problem
    when {
        trends == null && problem != null -> Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TrendsTitle()
            val message = ThreadsFailures.search(problem, currentLanguage())
            NoteText(message)
            RetryOrWait(message, ThreadsFailures.retryBlockedUntil(problem)) { fetch.load(force = true) }
        }
        trends == null -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TrendsTitle()
            LoadingRow()
        }
        trends.isNotEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TrendsTitle()
            trends.forEach { trend -> TrendRow(trend) { onOpen(trend.terms) } }
        }
    }
}

@Composable
private fun TrendsTitle() {
    Text(pluginString(Strings.trending_now), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun TrendRow(trend: ThreadsTrend, onClick: () -> Unit) {
    val line = listOfNotNull(trend.context, trend.summary).joinToString(" · ").ifEmpty { null }
    ListItem(
        leadingContent = { Icon(PostIcons.TrendingUp, contentDescription = null) },
        headlineContent = { Text(trend.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = line?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Over a search a trend opened, what Meta wrote of it (its context line and
 * summary), from the trends already read: nothing more is asked of Meta, and
 * a search no trend opened shows nothing.
 */
@Composable
internal fun TrendHeader(app: ThreadsApp, terms: ThreadsSearchTerms) {
    val trend = ThreadsTrends.matching(app.direct.knownTrends().orEmpty(), terms) ?: return
    if (trend.context == null && trend.summary == null) return
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pluginString(Strings.trending_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        trend.context?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        trend.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
