package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruMetatags
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruRatingRules
import com.aimdi.xtanative.networks.booru.BooruTagOperator
import com.aimdi.xtanative.networks.booru.BooruTagSuggestion
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/**
 * What sits under the field while editing (booru_search_landing.dart): the
 * suggestions for the tag being typed, else the quick operators, the engine's
 * metatags, the saved searches and the recent ones. [onField] takes what
 * stays in the field.
 */
@Composable
internal fun SearchLanding(app: BooruApp, search: BooruSearch, state: BooruSearchState, onField: (String) -> Unit) {
    if (state.input.isNotEmpty() && state.suggestions.isNotEmpty()) {
        val prefix = BooruQuery.suggestionPrefix(state.input).orEmpty()
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.suggestions, key = { "s-" + it.name }) { suggestion -> SuggestionRow(suggestion, prefix) { onField(search.pick(suggestion)) } }
        }
        return
    }
    val settings by app.settings.collectAsState()
    val current = settings ?: app.current()
    var editingText by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "tools") { QueryTools(search, state, current, onField, onEditAsText = { editingText = true }) }
        item(key = "metatags") { Metatags(current) { name -> onField(search.start(name)) } }
        item(key = "saved") { SavedSearches(app) { query -> onField(""); search.run(query) } }
        item(key = "recent") { RecentSearches(app, current) { query -> onField(""); search.run(query) } }
    }
    if (editingText) {
        RawQueryDialog(state.text, onSearch = { text ->
            editingText = false
            onField("")
            if (BooruQuery.tokens(text).isEmpty()) search.clear() else search.run(text)
        }, onDismiss = { editingText = false })
    }
}

/** A suggestion: the other name it was found by, dimmed, then the tag; the word that starts with what is typed in bold; its post count. */
@Composable
internal fun SuggestionRow(suggestion: BooruTagSuggestion, prefix: String, onPick: () -> Unit) {
    val color = tagColor(suggestion.category) ?: MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = readerLocale()
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick).defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            suggestion.alias?.let { alias ->
                Text(matched(alias, prefix), color = muted, style = MaterialTheme.typography.bodyLarge)
                Icon(BooruIcons.ArrowRight, contentDescription = null, tint = muted, modifier = Modifier.padding(horizontal = 4.dp).size(18.dp))
            }
            Text(if (suggestion.alias == null) matched(suggestion.name, prefix) else buildAnnotatedString { append(suggestion.name) }, color = color, style = MaterialTheme.typography.bodyLarge)
        }
        suggestion.postCount?.let { count ->
            val label = pluginPlural(Strings.tag_count, count, count)
            Text(
                BooruFormats.suggestionCount(count.toLong(), locale),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = label },
            )
        }
    }
}

/** [text] with the word that starts with what is typed in bold. */
private fun matched(text: String, prefix: String) = buildAnnotatedString {
    val start = BooruQuery.matchStart(text, prefix)
    if (start == null) {
        append(text)
    } else {
        append(text.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start, start + prefix.length)) }
        append(text.substring(start + prefix.length))
    }
}

/** Operators and metatags awkward to type on a phone: exclude, either, the ratings the cap allows, highest score, the whole search as text, clear. */
@Composable
private fun QueryTools(search: BooruSearch, state: BooruSearchState, settings: Settings, onField: (String) -> Unit, onEditAsText: () -> Unit) {
    val engine = BooruConfig.engine(settings)
    val host = BooruConfig.host(settings)
    val cap = BooruConfig.maxRating(settings)
    val ratings = BooruRating.entries.filterNot { it.exceeds(cap) }.mapNotNull { BooruRatingRules.metatag(engine, it, host) }
    val order = BooruQuery.scoreOrderMetatag(engine)
    val toggle = { token: String -> if (token in state.tags) search.removeTag(token) else search.addTags(listOf(token)) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tool(pluginString(Strings.exclude_tag), BooruIcons.Remove) { onField(search.toggleOperator(BooruTagOperator.EXCLUDE)) }
        if (BooruQuery.supportsEither(engine)) Tool(pluginString(Strings.either_tag), BooruIcons.Either) { onField(search.toggleOperator(BooruTagOperator.EITHER)) }
        ratings.forEach { rating -> Tool(rating, null, selected = rating in state.tags) { toggle(rating) } }
        Tool(pluginString(Strings.order_score), GroupIcons.TrendingUp, selected = order in state.tags) { toggle(order) }
        Tool(pluginString(Strings.edit_as_text), BooruIcons.EditNote, onClick = onEditAsText)
        if (state.tags.isNotEmpty()) Tool(pluginString(Strings.clear_tags), BooruIcons.ClearAll) { onField(""); search.clear() }
    }
}

@Composable
private fun Tool(label: String, icon: ImageVector?, selected: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
    )
}

/** The metatags the site's engine understands; a tap starts one in the field, where its known values are then offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metatags(settings: Settings, onStart: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Text(pluginString(Strings.metatags), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BooruMetatags.of(BooruConfig.engine(settings)).forEach { name -> AssistChip(onClick = { onStart("$name:") }, label = { Text("$name:") }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedSearches(app: BooruApp, onRun: (String) -> Unit) {
    val saved = app.follows.state.collectAsState().value.orEmpty()
    if (saved.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(pluginString(Strings.saved_searches))
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            saved.forEach { query ->
                AssistChip(
                    onClick = { onRun(query) },
                    label = { Text(queryText(query)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun RecentSearches(app: BooruApp, settings: Settings, onRun: (String) -> Unit) {
    val history = settings[BooruPrefs.searchHistory]
    val forget = { query: String -> app.update { it.with(BooruPrefs.searchHistory, BooruHistory.forgotten(it[BooruPrefs.searchHistory], query)) } }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(pluginString(Strings.search_history)) {
            if (history.isNotEmpty()) {
                IconButton(onClick = { app.update { it.with(BooruPrefs.searchHistory, emptyList()) } }) { Icon(DiscoverIcons.DeleteSweep, pluginString(Strings.clear_history)) }
            }
        }
        if (history.isEmpty()) ListItem(headlineContent = { Text(pluginString(Strings.search_history_empty)) })
        history.forEach { query -> key(query) { HistoryRow(query, onRun, forget) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRow(query: String, onRun: (String) -> Unit, onForget: (String) -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value != SwipeToDismissBoxValue.Settled) onForget(query)
        value != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(swipe, backgroundContent = {}) {
        ListItem(
            headlineContent = { Text(queryText(query)) },
            leadingContent = { Icon(PostIcons.History, contentDescription = null) },
            trailingContent = { IconButton(onClick = { onForget(query) }) { Icon(Icons.Filled.Close, pluginString(Strings.delete)) } },
            modifier = Modifier.clickable { onRun(query) },
        )
    }
}
