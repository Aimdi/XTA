package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhGalleryDetail
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The cover's width; it keeps the site's 0.7 proportion. */
private val CoverWidth = 120.dp

/**
 * The cover on the start side and, beside it, the titles, the uploader
 * (opening their galleries) and the category (eh_gallery_header.dart).
 * [listCover] is the cover the gallery list showed, for when the page has
 * none or its own fails to load.
 */
@Composable
internal fun GalleryHeader(app: EhApp, gallery: EhGallery, listCover: String?, japanese: Boolean, onUploader: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        EhImage(
            app,
            gallery.thumbUrl ?: listCover,
            Modifier.width(CoverWidth).height(CoverWidth / 0.7f).clip(RoundedCornerShape(8.dp)),
            fallback = listCover,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = gallery.titleFor(japanese)
            val other = gallery.titleFor(!japanese)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (other != title) {
                Text(
                    other,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            gallery.uploader?.trim()?.takeIf(String::isNotEmpty)?.let { name -> UploaderButton(name) { onUploader(name) } }
            gallery.category?.let { CategoryBadge(it, Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun UploaderButton(name: String, onClick: () -> Unit) {
    val spoken = pluginString(Strings.uploader, name)
    TextButton(onClick = onClick, contentPadding = PaddingValues(start = 4.dp, end = 8.dp), modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = spoken }) {
        Icon(EhIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
    }
}

/** What the site says of the gallery, each fact only when it sent it, each with what is read aloud. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryFacts(detail: EhGalleryDetail) {
    val gallery = detail.gallery
    val locale = Locale.forLanguageTag(currentLanguage())
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gallery.rating?.let { RatingFact(it, detail.ratingCount, locale) }
        gallery.pageCount?.let { Fact(EhIcons.Pages, pluginPlural(Strings.pages, it, it), pluginPlural(Strings.pages, it, it)) }
        detail.language?.let { LanguageFact(it, detail.translated) }
        detail.fileSizeBytes?.let { val size = EhFormats.size(it); Fact(EhIcons.Storage, size, pluginString(Strings.gallery_file_size, size)) }
        detail.favoritedCount?.let { Fact(Icons.Filled.FavoriteBorder, countText(it.toLong()), favoritedText(it)) }
        gallery.postedAt?.let { posted ->
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()).format(posted)
            Fact(EhIcons.Calendar, date, pluginString(Strings.gallery_posted, date))
        }
    }
}

@Composable
private fun favoritedText(count: Int): String =
    if (count == 0) pluginString(Strings.gallery_never_favorited) else pluginPlural(Strings.gallery_favorited, count, count)

@Composable
private fun RatingFact(rating: Double, count: Int?, locale: Locale) {
    val value = EhFormats.rating(rating, locale)
    val text = count?.let { pluginString(Strings.gallery_rating_value, value, "%,d".format(locale, it)) } ?: value
    val spoken = count?.let { pluginPlural(Strings.gallery_rating_semantics, it, value, it) } ?: pluginString(Strings.rating, value)
    Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        RatingStars(rating)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun LanguageFact(language: String, translated: Boolean) {
    val spoken = pluginString(if (translated) Strings.gallery_language_translated else Strings.gallery_language, language)
    Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        FactIcon(GroupIcons.Language)
        Text(language, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
        if (translated) Icon(PostIcons.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(14.dp))
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String, spoken: String) {
    Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        FactIcon(icon)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun FactIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
}

/**
 * Read from the start, and Continue where history left off when that is
 * past the first page (eh_gallery_actions.dart): side by side from 440 dp
 * (at normal text size), stacked below that rather than wrapping their words.
 */
@Composable
internal fun ReadActions(continuePage: Int?, onRead: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        val read: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = { onRead(1) }, modifier = modifier.heightIn(min = 48.dp)) {
                Icon(EhIcons.Read, contentDescription = null)
                Text(pluginString(Strings.read), Modifier.padding(start = 8.dp))
            }
        }
        if (continuePage == null) {
            read(Modifier.fillMaxWidth())
            return@BoxWithConstraints
        }
        val resume: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedButton(onClick = { onRead(continuePage) }, modifier = modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(pluginString(Strings.gallery_continue_page, continuePage), Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
            }
        }
        if (maxWidth >= 440.dp * LocalDensity.current.fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                read(Modifier.weight(1f))
                resume(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                read(Modifier.fillMaxWidth())
                resume(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The page below its header while the detail loads: bones where the facts, Read, the tags and the first previews will be. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GallerySkeleton() {
    val bone = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Box(Modifier.width(240.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(bone))
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(bone))
        FlowRow(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(72, 96, 64, 110, 84, 58, 90).forEach { width ->
                Box(Modifier.width(width.dp).height(30.dp).clip(RoundedCornerShape(10.dp)).background(bone))
            }
        }
    }
}
