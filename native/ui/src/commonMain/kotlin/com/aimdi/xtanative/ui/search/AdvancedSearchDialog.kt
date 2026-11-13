package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aimdi.xtanative.core.x.search.XAdvancedSearch
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Content
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Filter
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Inclusion
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_accounts
import com.aimdi.xtanative.ui.generated.resources.discover_advanced_search
import com.aimdi.xtanative.ui.generated.resources.discover_all
import com.aimdi.xtanative.ui.generated.resources.discover_all_of_these_words
import com.aimdi.xtanative.ui.generated.resources.discover_any_language
import com.aimdi.xtanative.ui.generated.resources.discover_any_of_these_words
import com.aimdi.xtanative.ui.generated.resources.discover_cancel
import com.aimdi.xtanative.ui.generated.resources.discover_clear
import com.aimdi.xtanative.ui.generated.resources.discover_close
import com.aimdi.xtanative.ui.generated.resources.discover_content_filter
import com.aimdi.xtanative.ui.generated.resources.discover_filters
import com.aimdi.xtanative.ui.generated.resources.discover_from_these_accounts
import com.aimdi.xtanative.ui.generated.resources.discover_hide_links
import com.aimdi.xtanative.ui.generated.resources.discover_hide_replies
import com.aimdi.xtanative.ui.generated.resources.discover_hide_retweets
import com.aimdi.xtanative.ui.generated.resources.discover_include_links
import com.aimdi.xtanative.ui.generated.resources.discover_include_replies
import com.aimdi.xtanative.ui.generated.resources.discover_include_reposts
import com.aimdi.xtanative.ui.generated.resources.discover_language
import com.aimdi.xtanative.ui.generated.resources.discover_media
import com.aimdi.xtanative.ui.generated.resources.discover_mentioning_these_accounts
import com.aimdi.xtanative.ui.generated.resources.discover_minimum_likes
import com.aimdi.xtanative.ui.generated.resources.discover_minimum_replies
import com.aimdi.xtanative.ui.generated.resources.discover_minimum_reposts
import com.aimdi.xtanative.ui.generated.resources.discover_none_of_these_words
import com.aimdi.xtanative.ui.generated.resources.discover_not_set
import com.aimdi.xtanative.ui.generated.resources.discover_ok
import com.aimdi.xtanative.ui.generated.resources.discover_only_links
import com.aimdi.xtanative.ui.generated.resources.discover_only_media
import com.aimdi.xtanative.ui.generated.resources.discover_only_replies
import com.aimdi.xtanative.ui.generated.resources.discover_photos
import com.aimdi.xtanative.ui.generated.resources.discover_search
import com.aimdi.xtanative.ui.generated.resources.discover_search_term
import com.aimdi.xtanative.ui.generated.resources.discover_since
import com.aimdi.xtanative.ui.generated.resources.discover_these_hashtags
import com.aimdi.xtanative.ui.generated.resources.discover_this_exact_phrase
import com.aimdi.xtanative.ui.generated.resources.discover_to_these_accounts
import com.aimdi.xtanative.ui.generated.resources.discover_until
import com.aimdi.xtanative.ui.generated.resources.discover_videos
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What [label] says, in the reader's language. */
@Composable
internal fun filterLabel(label: FilterLabel): String = stringResource(labelResource(label))

private fun labelResource(label: FilterLabel): StringResource = when (label) {
    FilterLabel.ALL_WORDS -> Res.string.discover_all_of_these_words
    FilterLabel.EXACT_PHRASE -> Res.string.discover_this_exact_phrase
    FilterLabel.ANY_WORDS -> Res.string.discover_any_of_these_words
    FilterLabel.NONE_WORDS -> Res.string.discover_none_of_these_words
    FilterLabel.HASHTAGS -> Res.string.discover_these_hashtags
    FilterLabel.LANGUAGE -> Res.string.discover_language
    FilterLabel.FROM_ACCOUNTS -> Res.string.discover_from_these_accounts
    FilterLabel.TO_ACCOUNTS -> Res.string.discover_to_these_accounts
    FilterLabel.MENTIONING_ACCOUNTS -> Res.string.discover_mentioning_these_accounts
    FilterLabel.MIN_REPLIES -> Res.string.discover_minimum_replies
    FilterLabel.MIN_LIKES -> Res.string.discover_minimum_likes
    FilterLabel.MIN_REPOSTS -> Res.string.discover_minimum_reposts
    FilterLabel.ONLY_MEDIA -> Res.string.discover_only_media
    FilterLabel.PHOTOS -> Res.string.discover_photos
    FilterLabel.VIDEOS -> Res.string.discover_videos
    FilterLabel.INCLUDE_LINKS -> Res.string.discover_include_links
    FilterLabel.ONLY_LINKS -> Res.string.discover_only_links
    FilterLabel.HIDE_LINKS -> Res.string.discover_hide_links
    FilterLabel.INCLUDE_REPLIES -> Res.string.discover_include_replies
    FilterLabel.ONLY_REPLIES -> Res.string.discover_only_replies
    FilterLabel.HIDE_REPLIES -> Res.string.discover_hide_replies
    FilterLabel.INCLUDE_REPOSTS -> Res.string.discover_include_reposts
    FilterLabel.HIDE_RETWEETS -> Res.string.discover_hide_retweets
    FilterLabel.SINCE -> Res.string.discover_since
    FilterLabel.UNTIL -> Res.string.discover_until
}

/**
 * XTA's advanced search: the filters, section by section, and the query
 * they write; Search hands them to the results, closing leaves the search
 * as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedSearchDialog(
    filters: XAdvancedSearch,
    onChange: ((XAdvancedSearch) -> XAdvancedSearch) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.discover_close)) }
                    },
                    title = { Text(stringResource(Res.string.discover_advanced_search)) },
                    actions = {
                        IconButton(onClick = { onChange { XAdvancedSearch() } }, enabled = filters.activeFilters.isNotEmpty()) {
                            Icon(Icons.Filled.Refresh, stringResource(Res.string.discover_clear))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { ApplyBar(onApply) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                Section(DiscoverIcons.TextFields, Res.string.discover_search_term) {
                    listOf(Filter.ALL_WORDS, Filter.EXACT_PHRASE, Filter.ANY_WORDS, Filter.NONE_WORDS, Filter.HASHTAGS).forEach { filter ->
                        TextFilter(filters, filter, onChange)
                    }
                    LanguageField(filters.language) { code -> onChange { it.copy(language = code) } }
                }
                Section(DiscoverIcons.AlternateEmail, Res.string.discover_accounts) {
                    listOf(Filter.FROM_ACCOUNTS, Filter.TO_ACCOUNTS, Filter.MENTIONING_ACCOUNTS).forEach { filter ->
                        TextFilter(filters, filter, onChange)
                    }
                }
                Section(DiscoverIcons.PermMedia, Res.string.discover_content_filter) { ContentFilters(filters, onChange) }
                Section(DiscoverIcons.Tune, Res.string.discover_filters) {
                    listOf(Filter.MIN_REPLIES, Filter.MIN_LIKES, Filter.MIN_RETWEETS).forEach { filter ->
                        TextFilter(filters, filter, onChange, number = true)
                    }
                    DateRow(stringResource(Res.string.discover_since), filters.since) { day -> onChange { it.copy(since = day) } }
                    DateRow(stringResource(Res.string.discover_until), filters.until) { day -> onChange { it.copy(until = day) } }
                }
                QueryPreview(filters.query)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ContentFilters(filters: XAdvancedSearch, onChange: ((XAdvancedSearch) -> XAdvancedSearch) -> Unit) {
    Choices(
        listOf(
            Content.ALL to stringResource(Res.string.discover_all),
            Content.MEDIA to stringResource(Res.string.discover_media),
            Content.PHOTOS to stringResource(Res.string.discover_photos),
            Content.VIDEOS to stringResource(Res.string.discover_videos),
        ),
        filters.content,
    ) { content -> onChange { it.copy(content = content) } }
    Choices(Inclusion.entries.map { it to filterLabel(SearchFilters.repliesLabel(it)) }, filters.replies) { replies ->
        onChange { it.copy(replies = replies) }
    }
    Choices(Inclusion.entries.map { it to filterLabel(SearchFilters.linksLabel(it)) }, filters.links) { links ->
        onChange { it.copy(links = links) }
    }
    ToggleRow(stringResource(Res.string.discover_include_reposts), filters.includeReposts) { on -> onChange { it.withIncludeReposts(on) } }
    ToggleRow(stringResource(Res.string.discover_hide_retweets), filters.excludeRetweets) { on -> onChange { it.withExcludeRetweets(on) } }
}

@Composable
private fun Section(icon: ImageVector, title: StringResource, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
        content()
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}

/** A filter typed in, with a cross that empties it; the minimums take digits only. */
@Composable
private fun TextFilter(filters: XAdvancedSearch, filter: Filter, onChange: ((XAdvancedSearch) -> XAdvancedSearch) -> Unit, number: Boolean = false) {
    val value = filters.textOf(filter)
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onChange { it.withText(filter, if (number) typed.filter(Char::isDigit) else typed) } },
        label = { Text(filterLabel(SearchFilters.labelOf(filter, filters))) },
        singleLine = true,
        trailingIcon = if (value.isEmpty()) null else {
            { IconButton(onClick = { onChange { it.cleared(filter) } }) { Icon(Icons.Filled.Close, stringResource(Res.string.discover_clear)) } }
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** What the field of [filter] holds, as typed. */
private fun XAdvancedSearch.textOf(filter: Filter): String = when (filter) {
    Filter.ALL_WORDS -> allWords
    Filter.EXACT_PHRASE -> exactPhrase
    Filter.ANY_WORDS -> anyWords
    Filter.NONE_WORDS -> noneWords
    Filter.HASHTAGS -> hashtags
    Filter.FROM_ACCOUNTS -> fromAccounts
    Filter.TO_ACCOUNTS -> toAccounts
    Filter.MENTIONING_ACCOUNTS -> mentioningAccounts
    Filter.MIN_REPLIES -> minReplies
    Filter.MIN_LIKES -> minLikes
    Filter.MIN_RETWEETS -> minRetweets
    else -> ""
}

/** Any language, or one of those X tags posts with, each named in its own language. */
@Composable
private fun LanguageField(code: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val any = stringResource(Res.string.discover_any_language)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = if (code.isEmpty()) any else SearchLanguages.name(code),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(Res.string.discover_language)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        // The whole field opens the list, as a drop-down does.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(any) },
                onClick = {
                    open = false
                    onPick("")
                },
            )
            SearchLanguages.choices(code).forEach { choice ->
                DropdownMenuItem(
                    text = { Text(SearchLanguages.name(choice)) },
                    onClick = {
                        open = false
                        onPick(choice)
                    },
                )
            }
        }
    }
}

/** One choice among [options], as chips that wrap on a narrow screen or at large text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Since or Until: the day set, or Not set; a tap picks one, the cross takes it away. */
@Composable
private fun DateRow(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val locale = currentLocale()
    val shown = value?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).format(it) } ?: stringResource(Res.string.discover_not_set)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { picking = true }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label)
            Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Close, stringResource(Res.string.discover_clear)) }
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (picking) {
        DayPicker(
            value,
            onPick = { day ->
                picking = false
                onChange(day)
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(value: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = SearchDates.millisOf(SearchDates.opening(value, today)),
        yearRange = SearchDates.years(today),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = SearchDates.selectable(utcTimeMillis, today)

                override fun isSelectableYear(year: Int): Boolean = year in SearchDates.years(today)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(SearchDates.dayOf(it)) } ?: onDismiss() }) {
                Text(stringResource(Res.string.discover_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.discover_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** The query the filters write, as X receives it. */
@Composable
private fun QueryPreview(query: String) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp),
    ) {
        Text(stringResource(Res.string.discover_search_term), style = MaterialTheme.typography.labelLarge)
        SelectionContainer(Modifier.padding(top = 8.dp)) {
            Text(
                query.ifEmpty { stringResource(Res.string.discover_not_set) },
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ApplyBar(onApply: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.discover_search), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
