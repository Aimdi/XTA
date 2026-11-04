package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkRange
import com.aimdi.xtanative.networks.pixiv.PixivDatePreset
import com.aimdi.xtanative.networks.pixiv.PixivDateRange
import com.aimdi.xtanative.networks.pixiv.PixivSearchFilter
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivSearchSort
import com.aimdi.xtanative.networks.pixiv.PixivSearchTarget
import com.aimdi.xtanative.networks.pixiv.PixivUgoiraFilter
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun targetLabel(target: PixivSearchTarget): PluginText = when (target) {
    PixivSearchTarget.PARTIAL_TAGS -> Strings.search_target_partial
    PixivSearchTarget.EXACT_TAGS -> Strings.search_target_exact
    PixivSearchTarget.TITLE_CAPTION -> Strings.search_target_title
    PixivSearchTarget.TEXT -> Strings.search_target_text
    PixivSearchTarget.KEYWORD -> Strings.search_target_keyword
}

internal fun sortLabel(sort: PixivSearchSort): PluginText = when (sort) {
    PixivSearchSort.NEWEST -> Strings.search_sort_newest
    PixivSearchSort.OLDEST -> Strings.search_sort_oldest
    PixivSearchSort.POPULAR -> Strings.search_sort_popular
    PixivSearchSort.POPULAR_MALE -> Strings.search_sort_popular_male
    PixivSearchSort.POPULAR_FEMALE -> Strings.search_sort_popular_female
}

private fun ugoiraLabel(filter: PixivUgoiraFilter): PluginText = when (filter) {
    PixivUgoiraFilter.ALL -> Strings.search_ugoira_all
    PixivUgoiraFilter.ONLY -> Strings.search_ugoira_only
    PixivUgoiraFilter.NONE -> Strings.search_ugoira_none
}

private fun dateLabel(preset: PixivDatePreset): PluginText = when (preset) {
    PixivDatePreset.ANY -> Strings.search_date_any
    PixivDatePreset.DAY -> Strings.search_date_day
    PixivDatePreset.WEEK -> Strings.search_date_week
    PixivDatePreset.MONTH -> Strings.search_date_month
    PixivDatePreset.HALF_YEAR -> Strings.search_date_half_year
    PixivDatePreset.YEAR -> Strings.search_date_year
    PixivDatePreset.CUSTOM -> Strings.search_date_custom
}

/** A count written out in full, grouped as [language] groups digits. */
internal fun decimal(count: Int, language: String): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag(language)).format(count)

@Composable
private fun usersIriLabel(threshold: Int): String = pluginString(Strings.search_users_iri, decimal(threshold, currentLanguage()))

@Composable
private fun bookmarksLabel(range: PixivBookmarkRange): String {
    val language = currentLanguage()
    val max = range.max ?: return pluginString(Strings.search_bookmarks_min, decimal(range.min, language))
    return pluginString(Strings.search_bookmarks_range, decimal(range.min, language), decimal(max, language))
}

/** Whether the sheet's choices differ from a fresh search's. */
private fun PixivSearchFilter.sheetDiffersFrom(base: PixivSearchFilter): Boolean =
    target != base.target || sort != base.sort || hideAi != base.hideAi || ugoira != base.ugoira

/**
 * The row over search results: Filters (the sheet), the posting dates (not
 * for Pixiv's free preview, which takes none), the users入り popularity tag,
 * and with Premium the bookmark bracket.
 */
@Composable
internal fun SearchFilterBar(view: PixivSearchView, kind: PixivSearchKind, base: PixivSearchFilter, onChange: (PixivSearchFilter) -> Unit, onOpenSheet: () -> Unit) {
    val filter = view.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterPill(PostIcons.Tune, pluginString(Strings.filters), filter.sheetDiffersFrom(base), onOpenSheet)
        if (!view.previewMode) DateMenu(filter, onChange)
        PopularityMenu(filter, onChange)
        if (view.isPremium && kind.isWorks) BookmarksMenu(filter, onChange)
    }
}

@Composable
private fun FilterPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (active) scheme.secondaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (active) scheme.secondaryContainer else scheme.outlineVariant),
        modifier = Modifier.heightIn(min = 40.dp).widthIn(max = 240.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (active) scheme.primary else scheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A pill opening a menu of [choices], the one picked marked. */
@Composable
private fun <T> MenuPill(icon: ImageVector, label: String, active: Boolean, choices: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterPill(icon, label, active) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateMenu(filter: PixivSearchFilter, onChange: (PixivSearchFilter) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val preset = filter.datePreset
    val range = filter.customRange
    val label = when {
        preset == PixivDatePreset.ANY -> pluginString(Strings.search_date)
        preset == PixivDatePreset.CUSTOM && range != null -> rangeLabel(range)
        else -> pluginString(dateLabel(preset))
    }
    MenuPill(Icons.Filled.DateRange, label, preset != PixivDatePreset.ANY, PixivDatePreset.entries.map { it to pluginString(dateLabel(it)) }) { choice ->
        if (choice == PixivDatePreset.CUSTOM) picking = true else onChange(filter.withDates(choice))
    }
    if (picking) DateRangeDialog(range, onClose = { picking = false }) { picked -> onChange(filter.withDates(PixivDatePreset.CUSTOM, picked)) }
}

@Composable
private fun rangeLabel(range: PixivDateRange): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(currentLanguage()))
    return pluginString(Strings.search_date_range, range.start.format(format), range.end.format(format))
}

/** Pixiv's opening day to today, both whole days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(current: PixivDateRange?, onClose: () -> Unit, onPick: (PixivDateRange) -> Unit) {
    val earliest = millisOf(PixivSearchFilter.EARLIEST_DATE)
    val today = millisOf(LocalDate.now())
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = current?.start?.let(::millisOf),
        initialSelectedEndDateMillis = current?.end?.let(::millisOf),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in earliest..today
        },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                if (start != null && end != null) onPick(PixivDateRange(dateOf(start), dateOf(end)))
                onClose()
            }) { Text(pluginString(Strings.sort_ungrouped_apply)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(pluginString(Strings.cancel)) } },
    ) {
        DateRangePicker(state, Modifier.weight(1f))
    }
}

private fun millisOf(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun PopularityMenu(filter: PixivSearchFilter, onChange: (PixivSearchFilter) -> Unit) {
    val threshold = filter.usersIri
    val choices = listOf<Pair<Int?, String>>(null to pluginString(Strings.search_popularity_any)) + PixivSearchFilter.USERS_IRI_THRESHOLDS.map { it to usersIriLabel(it) }
    MenuPill(PixivIcons.Whatshot, threshold?.let { usersIriLabel(it) } ?: pluginString(Strings.search_popularity), threshold != null, choices) { onChange(filter.copy(usersIri = it)) }
}

@Composable
private fun BookmarksMenu(filter: PixivSearchFilter, onChange: (PixivSearchFilter) -> Unit) {
    val range = filter.bookmarks
    val choices = listOf<Pair<PixivBookmarkRange?, String>>(null to pluginString(Strings.search_bookmarks_any)) + PixivSearchFilter.BOOKMARK_RANGES.map { it to bookmarksLabel(it) }
    MenuPill(Icons.Filled.Star, range?.let { bookmarksLabel(it) } ?: pluginString(Strings.search_bookmarks), range != null, choices) { onChange(filter.copy(bookmarks = it)) }
}

/**
 * Where to look, the order (Premium's own only with Premium), ugoira, Hide
 * AI, and whether the next searches start with these: applied together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFilterSheet(view: PixivSearchView, kind: PixivSearchKind, onClose: () -> Unit, onApply: (PixivSearchFilter, Boolean) -> Unit) {
    var filter by remember { mutableStateOf(view.filter) }
    var remember by remember { mutableStateOf(view.remembered) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(pluginString(Strings.search_filters_title), style = MaterialTheme.typography.titleLarge)
            Heading(pluginString(Strings.search_target))
            Choices(kind.targets, filter.target, ::targetLabel) { filter = filter.copy(target = it) }
            Heading(pluginString(Strings.search_sort))
            Choices(kind.offeredSorts(view.isPremium), filter.sort, ::sortLabel) { filter = filter.copy(sort = it) }
            if (!view.isPremium) {
                Text(
                    pluginString(if (kind.isWorks) Strings.search_premium_note else Strings.novel_search_premium_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (kind.isWorks) {
                Heading(pluginString(Strings.ugoira))
                Choices(PixivUgoiraFilter.entries, filter.ugoira, ::ugoiraLabel) { filter = filter.copy(ugoira = it) }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(pluginString(Strings.hide_ai), null, filter.hideAi) { filter = filter.copy(hideAi = it) }
            SwitchRow(pluginString(Strings.search_remember), null, remember) { remember = it }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onClose) { Text(pluginString(Strings.cancel)) }
                Button(onClick = {
                    onApply(filter, remember)
                    onClose()
                }) { Text(pluginString(Strings.sort_ungrouped_apply)) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun <T> Choices(values: List<T>, selected: T, label: (T) -> PluginText, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onPick(value) }, label = { Text(pluginString(label(value))) }) }
    }
}

/** A row that opens Pixiv's own page for what it names, as the shortcuts do. */
@Composable
internal fun ShortcutRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
