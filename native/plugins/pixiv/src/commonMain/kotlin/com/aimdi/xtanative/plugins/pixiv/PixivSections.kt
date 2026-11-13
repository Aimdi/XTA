package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivRanking
import com.aimdi.xtanative.networks.pixiv.PixivRankingMode
import com.aimdi.xtanative.networks.pixiv.PixivSpotlightArticle
import com.aimdi.xtanative.networks.pixiv.PixivWatchlistSeries
import com.aimdi.xtanative.networks.pixiv.Pixivision
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Above Recommended's works: Pixivision articles, then suggested creators, each with See all; a part with nothing to show is left out. */
@Composable
internal fun RecommendedHeader(model: PixivHomeModel) {
    val runtime = LocalPixiv.current
    val articles by model.spotlight.state.collectAsState()
    val users by model.users.state.collectAsState()
    val mutes = pixivMutes()
    Column(Modifier.fillMaxWidth()) {
        if (articles.items.isNotEmpty()) {
            HeaderPart(pluginString(Strings.pixivision_articles), onSeeAll = { runtime.open(PixivRoute.PixivisionList) })
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(articles.items, key = { it.id }) { article -> ArticleCard(article, Modifier.width(240.dp)) }
            }
        }
        val creators = mutes.withoutMutedAuthors(users.items) { it.user.id }
        if (creators.isNotEmpty()) {
            HeaderPart(pluginString(Strings.recommended_users), onSeeAll = { runtime.open(PixivRoute.RecommendedUsers) })
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(creators, key = { it.user.id }) { preview ->
                    Column(Modifier.width(72.dp).clickable { runtime.openUser(preview.user.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PixivAvatar(preview.user.avatarUrl, preview.user.name, 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(preview.user.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A part's title with See all at its end, shrinking before it pushes See all off the row. */
@Composable
internal fun HeaderPart(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(3f))
        TextButton(onClick = onSeeAll, modifier = Modifier.weight(2f, fill = false)) { Text(pluginString(Strings.see_all), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A Pixivision article: its picture with its title over a dark fade; it opens the article. */
@Composable
internal fun ArticleCard(article: PixivSpotlightArticle, modifier: Modifier = Modifier) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    Box(modifier.height(135.dp).clip(RoundedCornerShape(12.dp)).clickable { runtime.open(PixivRoute.Pixivision(article.id, Pixivision.language(language))) }) {
        PixivPicture(article.thumbnailUrl, Modifier.fillMaxSize(), contentDescription = article.displayTitle)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Text(
            article.displayTitle,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

/** The series the reader watches, newest update first: cover, title, author, when it last updated, and View latest. */
@Composable
internal fun WatchlistFeed(list: PixivList<PixivWatchlistSeries>, empty: String, padding: PaddingValues, toTop: Int, novels: Boolean) {
    PixivRows(list, { it.id }, empty, padding, toTop) { series -> WatchlistRow(series, novels) }
}

@Composable
private fun WatchlistRow(series: PixivWatchlistSeries, novels: Boolean) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    var opening by remember(series.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { runtime.open(if (novels) PixivRoute.NovelSeries(series.id) else PixivRoute.Series(series.id)) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp))) { PixivPicture(series.coverUrl, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(series.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            if (series.userName.isNotEmpty()) Text(series.userName, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
            series.maskText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            series.lastPublishedAt?.let { Text(pluginString(Strings.watchlist_updated, agoText(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
            TextButton(enabled = !opening, onClick = {
                opening = true
                runtime.launch {
                    openLatest(runtime, series, novels, language)
                    opening = false
                }
            }) { Text(pluginString(Strings.watchlist_view_latest)) }
        }
    }
}

/** The newest part of a watched series, else the series itself when no newest part is known. */
private suspend fun openLatest(runtime: PixivRuntime, series: PixivWatchlistSeries, novels: Boolean, language: String) {
    val latest = series.latestContentId
    when {
        latest == null -> runtime.open(if (novels) PixivRoute.NovelSeries(series.id) else PixivRoute.Series(series.id))
        novels -> runtime.open(PixivRoute.Novel(latest))
        else -> runCatching { runtime.client.illustDetail(latest) }
            .onSuccess { runtime.openWork(it) }
            .onFailure { runtime.tell(failureText(PixivFailure.of(it)).get(language)) }
    }
}

/** Rankings: the pinned boards as chips with Edit, a past day's board, and the ranked works or novels. */
@Composable
internal fun RankingSection(model: PixivHomeModel, view: PixivHomeView, padding: PaddingValues, toTop: Int) {
    val settings = pixivSettings()
    val novels = view.novels
    val table = PixivRankingPins.tableOf(novels)
    val visible = PixivRankingPins.visible(PixivRankingPins.of(settings, novels), table, settings[PixivPrefs.showR18])
    val mode = PixivRankingPins.effective(if (novels) view.novelRankingMode else view.rankingMode, visible)
    val date = if (novels) view.novelRankingDate else view.rankingDate
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ChipRow {
                    visible.forEach { board -> Chip(rankingLabel(board), board.id == mode, { if (novels) model.rankNovels(board.id) else model.rank(board.id) }) }
                    AssistChip(onClick = { editing = true }, label = { Text(pluginString(Strings.ranking_edit)) }, leadingIcon = { Icon(PostIcons.Tune, null, Modifier.size(18.dp)) })
                }
            }
            DateControls(date) { picked -> if (novels) model.rankNovelsOn(picked) else model.rankOn(picked) }
        }
        Box(Modifier.weight(1f)) {
            if (novels) PixivNovelFeed(model.novelRanking, pluginString(Strings.novel_ranking_empty), padding, toTop)
            else PixivWorksGrid(model.ranking, pluginString(Strings.ranking_empty), padding = padding, toTop = toTop)
        }
    }
    if (editing) RankingPinsSheet(novels) { editing = false }
}

/** A board's name, from XTA's own words for it. */
@Composable
internal fun rankingLabel(mode: PixivRankingMode): String = Strings.get(PixivRankingPins.labelName(mode), currentLanguage())

/** The archive day shown, the calendar that picks another, and the way back to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateControls(date: LocalDate?, onDate: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (date != null) Text(dateText(date.atStartOfDay().toInstant(ZoneOffset.UTC)), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    IconButton(onClick = { picking = true }) { Icon(Icons.Filled.DateRange, pluginString(Strings.ranking_pick_date)) }
    if (date != null) IconButton(onClick = { onDate(null) }) { Icon(Icons.Filled.Close, pluginString(Strings.ranking_back_to_today)) }
    if (!picking) return
    val today = LocalDate.now()
    val first = LocalDate.of(2007, 9, 13)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (date ?: today.minusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        yearRange = first.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !day.isBefore(first) && !day.isAfter(today)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(onClick = {
                picking = false
                state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(pluginString(Strings.save)) }
        },
        dismissButton = { TextButton(onClick = { picking = false }) { Text(pluginString(Strings.cancel)) } },
    ) { DatePicker(state) }
}

/** Every board the reader may pin, each tap kept at once; the last one on show cannot be taken off, so the row never empties. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RankingPinsSheet(novels: Boolean, onClose: () -> Unit) {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    val table = PixivRankingPins.tableOf(novels)
    val offered = PixivRanking.offered(table, settings[PixivPrefs.showR18])
    val pins = PixivRankingPins.of(settings, novels)
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(pluginString(Strings.ranking_edit_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(pluginString(Strings.ranking_edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                offered.forEach { board ->
                    FilterChip(
                        selected = board.id in pins,
                        enabled = !PixivRankingPins.lastVisible(pins, board.id, offered),
                        onClick = { runtime.settings.set(PixivRankingPins.prefOf(novels), PixivRankingPins.toggled(pins, board.id, table)) },
                        label = { Text(rankingLabel(board)) },
                    )
                }
            }
        }
    }
}

/** Favorites: the account's public or private bookmarks on Pixiv, or those kept on this device, every one or one tag's. */
@Composable
internal fun FavoritesSection(model: PixivHomeModel, view: PixivHomeView, padding: PaddingValues, toTop: Int) {
    var picking by remember { mutableStateOf(false) }
    val source = if (view.novels) view.novelFavorites else view.favorites
    Column(Modifier.fillMaxSize()) {
        ChipRow {
            listOf(
                PixivFavoritesSource.PUBLIC to Strings.bookmarks_public,
                PixivFavoritesSource.PRIVATE to Strings.bookmarks_private,
                PixivFavoritesSource.DEVICE to Strings.device_bookmarks,
            ).forEach { (value, label) ->
                Chip(pluginString(label), source == value, { if (view.novels) model.novelFavorites(value) else model.favorites(value, view.tag) })
            }
            if (!view.novels) {
                InputChip(
                    selected = view.tag != null,
                    onClick = { picking = true },
                    label = { Text(tagLabel(view.tag), maxLines = 1) },
                    leadingIcon = { Icon(PixivIcons.Label, null, Modifier.size(18.dp)) },
                    trailingIcon = view.tag?.let { { IconButton(onClick = { model.favorites(view.favorites, null) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, pluginString(Strings.bookmark_tag_clear), Modifier.size(16.dp)) } } },
                )
            }
        }
        Box(Modifier.weight(1f)) { FavoritesList(model, view, source, padding, toTop) }
    }
    if (picking) BookmarkTagPicker(view.favorites, view.tag, onPick = { source, tag -> model.favorites(source, tag, picked = true) }) { picking = false }
}

@Composable
private fun FavoritesList(model: PixivHomeModel, view: PixivHomeView, source: PixivFavoritesSource, padding: PaddingValues, toTop: Int) {
    when {
        source == PixivFavoritesSource.DEVICE -> DeviceBookmarks(if (view.novels) PixivKind.NOVEL else PixivKind.WORK, view.tag.takeIf { !view.novels }, padding, toTop)
        view.novels -> PixivNovelFeed(
            model.novelBookmarks,
            pluginString(if (source == PixivFavoritesSource.PRIVATE) Strings.novel_bookmarks_private_empty else Strings.novel_bookmarks_empty),
            padding,
            toTop,
        )
        else -> PixivWorksGrid(model.bookmarks, favoritesEmpty(view), padding = padding, toTop = toTop, hideMuted = false)
    }
}

@Composable
private fun favoritesEmpty(view: PixivHomeView): String = pluginString(
    when {
        view.tag != null -> Strings.bookmark_tag_empty
        view.favorites == PixivFavoritesSource.PRIVATE -> Strings.bookmarks_private_empty
        else -> Strings.bookmarks_empty
    },
)

/** What the tag chip says: All, Uncategorized, or the tag. */
@Composable
internal fun tagLabel(tag: String?): String = tagLabel(tag, currentLanguage())

internal fun tagLabel(tag: String?, language: String): String = when (tag) {
    null -> Strings.bookmark_tag_all.get(language)
    PixivDeviceBookmarks.UNTAGGED -> Strings.bookmark_tag_unclassified.get(language)
    else -> tag
}
