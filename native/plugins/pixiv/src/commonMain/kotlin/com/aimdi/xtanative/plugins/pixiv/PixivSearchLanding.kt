package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.pixiv.PixivTrendTag
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString

/** Before a search: what to type, the recent searches, creators to look at (works only) and the tags trending now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchLanding(model: PixivSearchModel, view: PixivSearchView, padding: PaddingValues, onSearch: (String) -> Unit) {
    val trending by model.trending.state.collectAsState()
    PullToRefreshBox(isRefreshing = trending.loading && trending.value != null, onRefresh = { model.loadLanding(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + padding.calculateBottomPadding())) {
            item(key = "prompt") {
                Text(pluginString(if (model.kind.isWorks) Strings.search_prompt else Strings.novel_search_prompt), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
            item(key = "history") { SearchHistory(model, view, onSearch) }
            if (model.kind.isWorks) landingSection("creators", model.creators, Strings.recommended_users) { users -> CreatorStrip(users) }
            landingSection("trending", model.trending, Strings.trending_title) { tags -> TrendingGrid(tags, onSearch) }
        }
    }
}

/** The recent searches as chips, the oldest folded behind Show all; a long press forgets one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHistory(model: PixivSearchModel, view: PixivSearchView, onSearch: (String) -> Unit) {
    val sheets = LocalPixivSheets.current
    val language = currentLanguage()
    val queries = pixivSettings()[PixivSearchMemory.historyOf(model.kind)]
    Column {
        Text(pluginString(Strings.search_history), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (queries.isEmpty()) {
            Text(pluginString(Strings.search_history_empty))
            return@Column
        }
        val folds = queries.size > PixivRecentSearches.FOLDED
        val shown = if (folds && !view.historyExpanded) queries.take(PixivRecentSearches.FOLDED) else queries
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { query -> HistoryChip(query, onClick = { onSearch(query) }, onLongClick = { model.forget(query) }) }
            if (folds) {
                val label = if (view.historyExpanded) pluginString(Strings.search_history_fewer) else pluginString(Strings.search_history_all, queries.size)
                HistoryChip(label, icon = if (view.historyExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, onClick = model::toggleHistory)
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            sheets.ask.ask(Strings.search_history_clear_question.get(language), Strings.search_history_clear.get(language), model::clearHistory)
        }) {
            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.search_history_clear))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChip(label: String, icon: ImageVector? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A landing list read once: its spinner, its failure with Retry, nothing when it came back empty. */
private fun <T : Any> LazyListScope.landingSection(key: String, fetch: PixivFetch<List<T>>, title: PluginText, content: @Composable (List<T>) -> Unit) {
    item(key = key) {
        val state by fetch.state.collectAsState()
        val items = state.value.orEmpty()
        if (items.isEmpty() && !state.loading && state.error == null) return@item
        Column(Modifier.padding(top = 24.dp)) {
            Text(pluginString(title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            when {
                items.isNotEmpty() -> content(items)
                state.error != null -> LandingRetry(state.error, fetch::load)
                else -> Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun LandingRetry(error: Throwable?, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pixivErrorText(error), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) }
    }
}

@Composable
private fun CreatorStrip(users: List<PixivUser>) {
    val runtime = LocalPixiv.current
    val shown = pixivMutes().withoutMutedAuthors(users) { it.id }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shown, key = { it.id }) { user ->
            Column(Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).clickable { runtime.openUser(user.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                PixivAvatar(user.avatarUrl, user.name, size = 56.dp)
                Spacer(Modifier.height(6.dp))
                Text(user.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Trending tags as pictures in three columns (two under large text): a tap searches the tag, a long press opens its work. */
@Composable
private fun TrendingGrid(tags: List<PixivTrendTag>, onSearch: (String) -> Unit) {
    val shown = PixivSearchModel.visibleTrendTags(tags, pixivMutes())
    val columns = if (LocalDensity.current.fontScale > 1.5f) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { tag -> Box(Modifier.weight(1f)) { TrendTile(tag, onSearch) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrendTile(tag: PixivTrendTag, onSearch: (String) -> Unit) {
    val runtime = LocalPixiv.current
    val illust = tag.illust
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = { onSearch(tag.name) }, onLongClick = illust?.let { { runtime.openWork(it) } }),
    ) {
        illust?.let { PixivPicture(it.thumbnailUrl, Modifier.fillMaxSize()) }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text("#" + tag.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tag.translatedName?.takeIf { it.isNotEmpty() && it != tag.name }?.let {
                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A tag suggested while typing: a tap takes it, a long press copies it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagSuggestion(tag: PixivTrendTag, onPick: () -> Unit, onCopy: () -> Unit) {
    ListItem(
        headlineContent = { Text(tag.name) },
        supportingContent = tag.translatedName?.let { { Text(it) } },
        leadingContent = { Icon(PixivIcons.Tag, null) },
        modifier = Modifier.combinedClickable(onClick = onPick, onLongClick = onCopy),
    )
}
