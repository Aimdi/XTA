package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruEndpoints
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruTagGroups
import com.aimdi.xtanative.networks.booru.BooruTagInfo
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.ZoneId
import java.util.Locale

/**
 * Everything about one post (booru_post_screen.dart): the picture, its facts,
 * its tags by kind, more from its artist, its source, its parent and child
 * posts, its pools and its comments.
 */
@Composable
internal fun PostDetails(app: BooruApp, viewer: ViewerState, post: BooruPost, padding: PaddingValues) {
    val settings by app.settings.collectAsState()
    val ownKinds = post.tagCategories.mapValues { BooruTagInfo(it.value) }
    val info = produceState(ownKinds, post.key) { value = viewer.tagInfo(post) }.value
    val site = app.siteOf(post)
    val engine = app.site(site).engine
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
        item(key = "media") { PostMedia(app, viewer, post, BooruConfig.display(settings ?: Settings())) }
        item(key = "facts") { PostFacts(post) }
        item(key = "tags") { PostTags(app, viewer, post, info, settings ?: Settings()) }
        BooruTagGroups.artistTag(post.tags, info)?.let { artist ->
            item(key = "artist-$artist") { RelatedStrip(app, viewer, pluginString(Strings.more_from, BooruFormats.tagName(artist)), artist, post) }
        }
        post.source?.trim()?.ifEmpty { null }?.let { source -> item(key = "source") { SourceRow(source) } }
        if (post.hasFamily) item(key = "family") { RelatedStrip(app, viewer, pluginString(Strings.family), BooruQuery.familyQuery(post), post) }
        if (BooruEndpoints.supportsPools(engine)) item(key = "pools") { PoolsSection(app, viewer, post) }
        if (BooruEndpoints.supportsComments(engine)) item(key = "comments") { CommentsSection(app, post) }
    }
}

@Composable
private fun PostTags(app: BooruApp, viewer: ViewerState, post: BooruPost, info: Map<String, BooruTagInfo>, settings: Settings) {
    TagSection(app, viewer, post, info, settings)
    if (post.tags.isNotEmpty()) {
        Text(pluginString(Strings.tag_tap_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
    }
}

@Composable
private fun SourceRow(source: String) {
    val outside = rememberOutside()
    Row(Modifier.fillMaxWidth().clickable { outside.open(source) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(XIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(pluginString(Strings.source), style = MaterialTheme.typography.bodyLarge)
            Text(source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One thing the host said about a post, its [label] read aloud where the text alone is unclear. */
internal data class Fact(val icon: ImageVector, val text: String, val label: String?)

/** Score, votes, favourites, rating, size, file and who posted it when (booru_post_facts.dart): each one the host sent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostFacts(post: BooruPost) {
    val facts = postFacts(post, currentLanguage(), readerLocale())
    FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        facts.forEach { fact ->
            Row(Modifier.padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = fact.label ?: fact.text }, verticalAlignment = Alignment.CenterVertically) {
                Icon(fact.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(fact.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** What the host said about [post] in [language], numbers and times in [locale]; nothing it did not send. */
internal fun postFacts(post: BooruPost, language: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): List<Fact> {
    val number = { n: Int -> BooruFormats.number(n.toLong(), locale) }
    val created = post.createdAt?.let { BooruFormats.dateTime(it, locale, zone) }
    // A change time alone is not when the post went up, so it says what it is.
    val changed = post.updatedAt?.takeIf { post.createdAt == null }?.let { BooruFormats.dateTime(it, locale, zone) }
    val up = post.upScore
    val down = post.downScore
    return listOfNotNull(
        post.score?.let { Fact(Icons.Outlined.ThumbUp, number(it), Strings.score.get(language, it)) },
        if (up != null && down != null) Fact(BooruIcons.Votes, "+${number(up)} −${number(down)}", Strings.votes.get(language, up, down)) else null,
        post.favCount?.let { Fact(Icons.Filled.FavoriteBorder, number(it), Strings.favorites.get(it, language, it)) },
        post.rating?.let { Fact(BooruIcons.Rating, ratingText(it).get(language), null) },
        if (post.width > 0 && post.height > 0) Fact(BooruIcons.Size, "${post.width} × ${post.height}", null) else null,
        BooruFormats.file(post)?.let { Fact(BooruIcons.File, it, null) },
        created?.let { Fact(GroupIcons.ScheduleOutlined, it, Strings.posted_on.get(language, it)) },
        changed?.let { Fact(BooruIcons.Updated, Strings.updated_on.get(language, it), null) },
        post.uploader?.let { Fact(BooruIcons.Person, it, Strings.uploaded_by.get(language, it)) },
    )
}
