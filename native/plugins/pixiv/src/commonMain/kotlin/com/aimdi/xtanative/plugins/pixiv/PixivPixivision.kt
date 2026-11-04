package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivSpotlightArticle
import com.aimdi.xtanative.networks.pixiv.Pixivision
import com.aimdi.xtanative.networks.pixiv.PixivisionArticle
import com.aimdi.xtanative.networks.pixiv.PixivisionWork
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons

/** Pixivision's articles, newest first, as cards opening each one here. */
@Composable
internal fun PixivisionListScreen() {
    val runtime = LocalPixiv.current
    val list = rememberKept("pixiv-pixivision") { scope -> PixivList<PixivSpotlightArticle>(scope, { it.id }, { next -> runtime.discovery.spotlightArticles(next) }) }
    PluginScaffold(title = pluginString(Strings.pixivision_articles), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding)) {
            PixivRows(list, { it.id }, pluginString(Strings.pixivision_empty), divided = false) { article ->
                ArticleCard(article, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** A Pixivision article read from its page: cover, title and intro over the works it features, which open here. */
@Composable
internal fun PixivisionArticleScreen(id: Long, language: String) {
    val runtime = LocalPixiv.current
    val outside = rememberOutside()
    val fetch = rememberKept("pixiv-pixivision-$id") { scope -> PixivFetch(scope) { runtime.discovery.pixivisionArticle(id) }.also { it.ensure() } }
    val state by fetch.state.collectAsState()
    val url = Pixivision.articleUrl(id, language)
    PluginScaffold(
        title = state.value?.title?.ifEmpty { null } ?: pluginString(Strings.pixivision_articles),
        onBack = runtime.host::back,
        actions = {
            IconButton(onClick = { outside.share(url) }) { Icon(Icons.Filled.Share, pluginString(Strings.share_link)) }
            IconButton(onClick = { outside.open(url) }) { Icon(SavedIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val article = state.value
            when {
                article != null -> Article(article)
                state.error != null && !state.loading -> PixivProblem(state.error, fetch::load)
                else -> LoadingRow()
            }
        }
    }
}

@Composable
private fun Article(article: PixivisionArticle) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item(key = "head") {
            Column {
                article.coverUrl?.let { PixivPicture(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) }
                Spacer(Modifier.height(12.dp))
                if (article.title.isNotEmpty()) Text(article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (article.intro.isNotEmpty()) Text(article.intro, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                if (article.works.isNotEmpty()) {
                    Text(pluginString(Strings.pixivision_featured), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                }
            }
        }
        items(article.works, key = { it.artworkId }) { work -> FeaturedWork(work) }
    }
}

/** A featured work: its picture and title open it, its creator's line opens them. */
@Composable
private fun FeaturedWork(work: PixivisionWork) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PixivPicture(
            work.imageUrl,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { runtime.open(PixivRoute.Work(work.artworkId)) },
            contentDescription = work.title,
        )
        Text(work.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (work.userId > 0) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { runtime.openUser(work.userId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PixivAvatar(work.avatarUrl, work.userName, size = 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(work.userName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
