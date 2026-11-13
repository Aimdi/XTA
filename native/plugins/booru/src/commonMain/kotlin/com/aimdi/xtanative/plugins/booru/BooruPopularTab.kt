package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruEndpoints
import com.aimdi.xtanative.networks.booru.BooruPopularQuery
import com.aimdi.xtanative.networks.booru.BooruPopularScale
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.monthText
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Popular (booru_popular_tab.dart): the day, week or month whose popular
 * posts show, stepped earlier and later or picked; a host that keeps no
 * popular list gets a note instead, over its highest-scored posts.
 */
@Composable
internal fun PopularTab(app: BooruApp, feeds: HomeFeeds, settings: Settings, frame: PluginFrame, grid: LazyStaggeredGridState) {
    val query by feeds.popularQuery.collectAsState()
    Column(Modifier.fillMaxSize()) {
        if (BooruEndpoints.hasPopularList(BooruConfig.engine(settings))) PopularControls(query, feeds::showPopular) else PopularNote()
        FeedView(app, feeds.popular, pluginString(Strings.empty_popular), frame.padding, grid = grid)
    }
}

@Composable
private fun PopularControls(query: BooruPopularQuery, onChange: (BooruPopularQuery) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BooruPopularScale.entries.forEach { scale ->
            FilterChip(selected = query.scale == scale, onClick = { onChange(query.withScale(scale)) }, label = { Text(scaleLabel(scale)) })
        }
        IconButton(onClick = { onChange(query.step(-1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, pluginString(Strings.popular_earlier))
        }
        TextButton(onClick = { picking = true }) { Text(dateLabel(query)) }
        IconButton(onClick = { onChange(query.step(1)) }, enabled = !query.isLatest()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, pluginString(Strings.popular_later))
        }
    }
    if (picking) {
        PopularDatePicker(query, onPick = { date -> picking = false; onChange(BooruPopularQuery(query.scale, date)) }, onDismiss = { picking = false })
    }
}

/** A month by its name and year, a day or week by its date. */
@Composable
private fun dateLabel(query: BooruPopularQuery): String = when (query.scale) {
    BooruPopularScale.MONTH -> monthText(query.date.atStartOfDay(ZoneId.systemDefault()).toInstant())
    else -> BooruFormats.day(query.date, readerLocale())
}

@Composable
private fun PopularNote() {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(GroupIcons.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(Strings.popular_top), style = MaterialTheme.typography.bodySmall)
    }
}

/** A day from 2005 to the newest a host can have a list for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopularDatePicker(query: BooruPopularQuery, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = BooruPopularQuery.hostToday()
    val first = LocalDate.of(2005, 1, 1)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = millis(if (query.date.isAfter(today)) today else query.date),
        yearRange = first.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = day(utcTimeMillis).let { !it.isBefore(first) && !it.isAfter(today) }

            override fun isSelectableYear(year: Int): Boolean = year in first.year..today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(day(it)) } ?: onDismiss() }) { Text(pluginString(Strings.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    ) {
        DatePicker(state, title = { Text(pluginString(Strings.pick_date), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

/** The picker counts days in UTC. */
private fun millis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
