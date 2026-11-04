package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivIllustSeries
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivNovelChapter
import com.aimdi.xtanative.networks.pixiv.PixivNovelSeries
import com.aimdi.xtanative.networks.pixiv.PixivNovelSeriesPage
import com.aimdi.xtanative.networks.pixiv.pixivNovelSeriesUrl
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** A series' pages of works or chapters, and the series itself as its first page named it. */
private class SeriesReads<S, T : Any>(scope: CoroutineScope, key: (T) -> Any, read: suspend (next: String?) -> Pair<S?, Page<T>>, visible: (List<T>) -> List<T>) {
    val series = MutableStateFlow<S?>(null)
    val list = PixivList(scope, key, { next -> read(next).let { (detail, page) -> page.also { if (detail != null) series.value = detail } } }, visible)
}

/** A manga or illustration series: its cover, creator, count and caption over its works. */
@Composable
internal fun PixivSeriesScreen(id: Long) {
    val runtime = LocalPixiv.current
    val reads = rememberKept("pixiv-series-$id") { scope ->
        SeriesReads<PixivIllustSeries, PixivIllust>(scope, { it.id }, { next -> runtime.discovery.illustSeries(id, next).let { it.series to it.works } }) { runtime.mutes.now.filter(it) }
    }
    val series by reads.series.collectAsState()
    val outside = rememberOutside()
    PluginScaffold(
        title = series?.title ?: pluginString(Strings.series),
        onBack = runtime.host::back,
        actions = { series?.let { IconButton(onClick = { outside.open(it.url) }) { Icon(SavedIcons.OpenInNew, pluginString(Strings.open_on_pixiv)) } } },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            PixivWorksGrid(reads.list, pluginString(Strings.series_empty), header = {
                series?.let { item(key = "series", span = StaggeredGridItemSpan.FullLine) { IllustSeriesHeader(it) } }
            })
        }
    }
}

@Composable
private fun IllustSeriesHeader(series: PixivIllustSeries) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        series.coverUrl?.let { PixivPicture(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) }
        CreatorLine(series.user.name, series.user.avatarUrl) { runtime.openUser(series.user.id) }
        val counts = listOfNotNull(
            pluginPlural(Strings.works_count, series.workCount.toInt(), series.workCount.toInt(), countText(series.workCount)),
            series.createdAt?.let { Strings.series_started.get(language, mediumDate(it, language)) },
        )
        Text(counts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (series.caption.isNotBlank()) Text(series.caption.trim(), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun mediumDate(at: Instant, language: String): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language)).format(at.atZone(ZoneId.systemDefault()))

@Composable
private fun CreatorLine(name: String, avatar: String?, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PixivAvatar(avatar, name, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** A novel series: whether it is finished, its length, Start from chapter 1 and the newest chapter, over its numbered chapters. */
@Composable
internal fun PixivNovelSeriesScreen(id: Long) {
    val runtime = LocalPixiv.current
    val reads = rememberKept("pixiv-novel-series-$id") { scope ->
        NovelSeriesReads(scope) { next -> runtime.novels.series(id, next) }
    }
    val series by reads.reads.series.collectAsState()
    val outside = rememberOutside()
    PluginScaffold(
        title = series?.title ?: pluginString(Strings.novel_series),
        onBack = runtime.host::back,
        actions = { IconButton(onClick = { outside.open(pixivNovelSeriesUrl(id)) }) { Icon(SavedIcons.OpenInNew, pluginString(Strings.open_on_pixiv)) } },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val mutes = pixivMutes()
            PixivRows(reads.reads.list, { it.novel.id }, pluginString(Strings.novel_series_empty), shown = { chapters -> mutes.filterNovelsOf(chapters) { it.novel } }, header = {
                series?.let { item(key = "series") { NovelSeriesHeader(it, reads.first, reads.latest) } }
            }) { chapter -> NovelCard(chapter.novel, order = chapter.order, showSeries = false) }
        }
    }
}

/** A novel series' pages: chapters numbered across pages, and its first and newest chapters. */
private class NovelSeriesReads(scope: CoroutineScope, read: suspend (String?) -> PixivNovelSeriesPage) {
    var first: PixivNovel? = null
        private set
    var latest: PixivNovel? = null
        private set
    private var counted = 0
    private var asked: String? = null

    val reads = SeriesReads<PixivNovelSeries, PixivNovelChapter>(scope, { it.novel.id }, { next ->
        val page = read(next)
        if (next == null) {
            counted = 0
            first = page.first
            latest = page.latest
        }
        val before = if (next != null && next == asked) counted - page.listed else counted
        asked = next
        counted = before + page.listed
        page.series to Page(page.chapters.items.map { it.copy(order = it.order + before) }, page.chapters.next)
    }) { it }
}

@Composable
private fun NovelSeriesHeader(series: PixivNovelSeries, first: PixivNovel?, latest: PixivNovel?) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CreatorLine(series.user.name, series.user.avatarUrl) { runtime.openUser(series.user.id) }
        val summary = listOf(
            pluginString(if (series.isConcluded) Strings.novel_series_concluded else Strings.novel_series_ongoing),
            pluginPlural(Strings.novel_chapters_count, series.contentCount.toInt(), series.contentCount.toInt(), countText(series.contentCount)),
            pluginPlural(Strings.novel_characters, series.totalCharacterCount.toInt(), series.totalCharacterCount.toInt(), countText(series.totalCharacterCount)),
        )
        Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (series.caption.isNotBlank()) Text(series.caption.trim(), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            first?.let { novel ->
                Button(onClick = { runtime.openNovel(novel) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(Strings.novel_series_start))
                }
            }
            latest?.takeIf { it.id != first?.id }?.let { novel ->
                OutlinedButton(onClick = { runtime.openNovel(novel) }) {
                    Icon(PixivIcons.MenuBook, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(Strings.watchlist_view_latest))
                }
            }
        }
    }
}
