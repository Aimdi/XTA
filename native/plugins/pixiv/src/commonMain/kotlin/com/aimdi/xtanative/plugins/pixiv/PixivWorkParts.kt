package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivKindedTag
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivSeriesRef
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivTagKind
import com.aimdi.xtanative.networks.pixiv.PixivTagKinds
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Everything under a work's pages: its creator, title, series, figures, caption, tags and the way to its comments. */
@Composable
internal fun WorkMeta(illust: PixivIllust, state: PixivWorkState, surface: WorkSurface) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        AuthorRow(PixivBookmarking.authorOf(illust))
        if (illust.title.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(illust.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        illust.series?.let { SeriesBlock(it, state) }
        Spacer(Modifier.height(8.dp))
        WorkStats(illust)
        if (illust.captionHtml.isNotEmpty() || illust.caption.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PixivHtmlText(illust.captionHtml, illust.caption)
        }
        if (illust.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            TagChips(illust.tags)
        }
        CommentsLink(PixivKind.WORK, illust.id, illust.totalComments)
    }
}

/** A creator, as a work and a novel show them: picture, name and @account opening their profile, the follow kept here beside them. */
@Composable
internal fun AuthorRow(user: PixivUser, modifier: Modifier = Modifier) {
    val runtime = LocalPixiv.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { runtime.openUser(user.id) }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixivAvatar(user.avatarUrl, user.name)
            Spacer(Modifier.width(10.dp))
            NameLines(user.name, user.account.takeIf { it.isNotEmpty() }?.let { "@$it" }, Modifier.weight(1f))
        }
        if (user.id != runtime.ownId) FollowButton(user)
    }
}

/** Bookmarks (filled when the reader bookmarked it on Pixiv), views, date, the R-18 and AI marks, the work's ID and its size. */
@Composable
private fun WorkStats(illust: PixivIllust) {
    val copy = rememberCopy()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Stat(if (illust.isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, countText(illust.totalBookmarks))
        Stat(SavedIcons.Visibility, countText(illust.totalViews))
        illust.createdAt?.let { Text(dateText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (illust.isR18) Text(pluginString(Strings.r18), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        if (illust.isAi) Stat(GroupIcons.AutoAwesome, pluginString(Strings.ai))
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = pluginString(Strings.copy_illust_id)) { copy("${illust.id}", Strings.illust_id_copied) }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(PixivIcons.Tag, pluginString(Strings.illust_id, "${illust.id}"))
        }
        if (illust.width > 0 && illust.height > 0) Stat(PixivIcons.AspectRatio, pluginString(Strings.resolution, illust.width, illust.height))
    }
}

@Composable
internal fun Stat(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A day as the reader's language writes it. */
@Composable
internal fun dateText(instant: Instant): String {
    val language = currentLanguage()
    return remember(instant, language) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language)).format(instant.atZone(ZoneId.systemDefault()))
    }
}

/** Pixiv's HTML text (a caption, a bio), selectable, its Pixiv links opening here and others as the app opens links. */
@Composable
internal fun PixivHtmlText(html: String, plain: String = "") {
    val runtime = LocalPixiv.current
    val link = MaterialTheme.colorScheme.primary
    val text = remember(html, plain, link) { annotated(if (html.isEmpty()) listOf(PixivTextPart(plain)) else PixivHtml.parts(html), link, runtime::openHref) }
    SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

private fun annotated(parts: List<PixivTextPart>, link: Color, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
    parts.forEach { part ->
        val weight = if (part.bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
        val href = part.href
        if (href == null) {
            withStyle(weight) { append(part.text) }
        } else {
            withLink(LinkAnnotation.Clickable(href, linkStyle) { open(href) }) { withStyle(weight) { append(part.text) } }
        }
    }
}

/**
 * A work's or a novel's tags, ordered and coloured by kind (copyright,
 * character, general, meta, read off the tags themselves), each with Pixiv's
 * translation beside it. A tap searches the tag; a long press offers to mute,
 * pin or copy it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagChips(tags: List<PixivTag>, kind: PixivSearchKind = PixivSearchKind.WORKS, leaveOnMute: Boolean = true) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val kinded = remember(tags) { PixivTagKinds.kinded(tags) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kinded.forEach { entry ->
            TagChip(entry, Modifier.combinedClickable(
                onClick = { runtime.openTagSearch(entry.tag.name, kind) },
                onLongClick = {
                    haptics.medium()
                    sheets.show(PixivSheet.Tag(entry.tag, leaveOnMute))
                },
            ))
        }
    }
}

@Composable
private fun TagChip(entry: PixivKindedTag, clicks: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.clip(shape).background(kindColor(entry.kind).copy(alpha = 0.14f)).then(clicks).heightIn(min = 36.dp).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("#" + entry.tag.name, style = MaterialTheme.typography.labelLarge, color = kindColor(entry.kind), maxLines = 1, overflow = TextOverflow.Ellipsis)
        entry.tag.translation?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun kindColor(kind: PixivTagKind): Color = when (kind) {
    PixivTagKind.COPYRIGHT -> Color(0xFFAB47BC)
    PixivTagKind.CHARACTER -> Color(0xFF43A047)
    PixivTagKind.META -> Color(0xFFEF6C00)
    PixivTagKind.GENERAL -> MaterialTheme.colorScheme.primary
}

/** "View comments (N)" under a work or a novel; one listed without a total shows no number rather than a misleading zero. */
@Composable
internal fun CommentsLink(kind: PixivKind, id: Long, count: Long) {
    val runtime = LocalPixiv.current
    ActionRow(
        commentsLabel(count),
        onClick = { runtime.open(PixivRoute.Comments(kind, id)) },
        icon = PostIcons.Forum,
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

/** "View comments (N)" (pixiv_detail_comments.dart's pixivCommentsLabel), without a number where there is no total. */
@Composable
internal fun commentsLabel(count: Long): String =
    if (count > 0) pluginString(Strings.comments_view_count, countText(count)) else pluginString(Strings.comments_view)

/** "Series: <title>" leading to the series; [dense] is the one-line form under a tile's title. */
@Composable
internal fun SeriesLink(series: PixivSeriesRef, dense: Boolean = false, novel: Boolean = false) {
    val runtime = LocalPixiv.current
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable { runtime.open(if (novel) PixivRoute.NovelSeries(series.id) else PixivRoute.Series(series.id)) }
            .heightIn(min = if (dense) 24.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PixivIcons.Collections, null, tint = color, modifier = Modifier.size(if (dense) 14.dp else 18.dp))
        Spacer(Modifier.width(if (dense) 4.dp else 6.dp))
        Text(
            if (series.title.isEmpty()) pluginString(Strings.series) else pluginString(Strings.series_link, series.title),
            style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (dense) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The work's series and, once Pixiv says, "#n of N" between the works before and after it. */
@Composable
private fun SeriesBlock(series: PixivSeriesRef, state: PixivWorkState) {
    val runtime = LocalPixiv.current
    SeriesLink(series)
    val place = state.series ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { place.previous?.let(runtime::openWork) }, enabled = place.previous != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, pluginString(Strings.series_previous))
        }
        if (place.total > 0) {
            Text(pluginString(Strings.series_position, place.order, place.total.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { place.next?.let(runtime::openWork) }, enabled = place.next != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, pluginString(Strings.series_next))
        }
    }
}

/** "More by <name>" over a row of the creator's other works; the header opens their profile. */
@Composable
internal fun AuthorWorks(illust: PixivIllust, works: List<PixivIllust>) {
    val runtime = LocalPixiv.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { runtime.openUser(illust.userId) }.heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pluginString(Strings.more_by, illust.userName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(works, key = { _, work -> work.id }) { index, work ->
                PixivPicture(
                    work.thumbnailUrl,
                    Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)).clickable { runtime.openWork(works, index) },
                    contentDescription = work.title,
                )
            }
        }
    }
}
