package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.networks.ehviewer.EhNamespace
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberImageRequest

/** The site's colour for [category] (eh_ui.dart). */
internal fun categoryColor(category: EhCategory): Color = when (category) {
    EhCategory.MISC -> Color(0xFF777777)
    EhCategory.DOUJINSHI -> Color(0xFF9E2720)
    EhCategory.MANGA -> Color(0xFFDB6C24)
    EhCategory.ARTIST_CG -> Color(0xFFD38F1D)
    EhCategory.GAME_CG -> Color(0xFF6A936D)
    EhCategory.IMAGE_SET -> Color(0xFF325CA2)
    EhCategory.COSPLAY -> Color(0xFF6A32A2)
    EhCategory.ASIAN_PORN -> Color(0xFFA23282)
    EhCategory.NON_H -> Color(0xFF5FA9CF)
    EhCategory.WESTERN -> Color(0xFFAB9F60)
}

/** The site's label for [category] on its colour, in white or black, whichever reads. */
@Composable
internal fun CategoryBadge(category: EhCategory, modifier: Modifier = Modifier) {
    val color = categoryColor(category)
    Text(
        category.label,
        style = MaterialTheme.typography.labelSmall,
        color = if (color.luminance() > 0.45f) Color.Black else Color.White,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A picture of the site at [url], asked for with the headers it wants
 * ([com.aimdi.xtanative.networks.ehviewer.EhClient.imageHeaders]); when it
 * fails, [fallback], and a broken picture once nothing is left.
 */
@Composable
internal fun EhImage(app: EhApp, url: String?, modifier: Modifier = Modifier, fallback: String? = null, contentScale: ContentScale = ContentScale.Crop) {
    val urls = remember(url, fallback) { listOfNotNull(url, fallback).map(String::trim).filter(String::isNotEmpty).distinct() }
    var tried by remember(urls) { mutableIntStateOf(0) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val current = urls.getOrNull(tried)
        if (current == null) {
            Icon(EhIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        } else {
            AsyncImage(
                model = rememberImageRequest(current, app.client.imageHeaders(current)),
                contentDescription = null,
                contentScale = contentScale,
                onError = { tried++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Five stars filled to [rating], in halves, in a gold that reads on the theme. */
@Composable
internal fun RatingStars(rating: Double, size: Dp = 16.dp) {
    val gold = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFFB7791F) else Color(0xFFF5A623)
    Row {
        EhFormats.stars(rating).forEach { star ->
            val icon = when (star) {
                EhStar.FULL -> Icons.Filled.Star
                EhStar.HALF -> EhIcons.StarHalf
                EhStar.EMPTY -> EhIcons.StarEmpty
            }
            Icon(icon, contentDescription = null, tint = gold, modifier = Modifier.size(size))
        }
    }
}

/** A heading on the gallery's page. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A read that brought nothing: XTA's words for why, and Retry. */
@Composable
internal fun EhProblem(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    RetryMessage(pluginString(EhText.error(error)), onRetry, modifier)
}

/**
 * The colour a namespace's tags take, after the booru kinds they match
 * (eh_tags.dart, plugin_tag_chip.dart): artist and group red, parody purple,
 * character and cosplayer green, the people blue, language and reclass
 * yellow; temporary ones and those without a namespace none.
 */
@Composable
internal fun namespaceColor(namespace: EhNamespace?): Color? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (namespace) {
        EhNamespace.ARTIST, EhNamespace.GROUP -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        EhNamespace.PARODY -> if (dark) Color(0xFFD7A6F5) else Color(0xFF7B1FA2)
        EhNamespace.CHARACTER, EhNamespace.COSPLAYER -> if (dark) Color(0xFF7ED68A) else Color(0xFF2E7D32)
        EhNamespace.MALE, EhNamespace.FEMALE, EhNamespace.MIXED, EhNamespace.OTHER -> if (dark) Color(0xFF6CB8F5) else Color(0xFF0B5FBF)
        EhNamespace.LANGUAGE, EhNamespace.RECLASS -> if (dark) Color(0xFFFFD54F) else Color(0xFF7A5C00)
        EhNamespace.TEMP, null -> null
    }
}
