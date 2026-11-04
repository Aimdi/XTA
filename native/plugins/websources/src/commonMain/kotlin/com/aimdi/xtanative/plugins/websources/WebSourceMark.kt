package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.ui.common.Graphemes

/** A source's colour, or the theme's when it names none. */
@Composable
internal fun sourceColor(source: WebSourceDefinition): Color = source.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary

/**
 * A source's picture when it has one, else its initial on its colour (web_source_mark.dart).
 * Without [picture] (before the reader agrees to the source's sites, one of which has it) the initial.
 */
@Composable
internal fun WebSourceMark(source: WebSourceDefinition, size: Dp = 24.dp, picture: Boolean = true) {
    val icon = source.icon?.toString()
    if (icon == null || !picture) return Initial(source, size)
    SubcomposeAsyncImage(
        model = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape),
        loading = { Initial(source, size) },
        error = { Initial(source, size) },
    )
}

@Composable
private fun Initial(source: WebSourceDefinition, size: Dp) {
    val color = sourceColor(source)
    val ink = if (color.luminance() < 0.5f) Color.White else Color.Black
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(
            Graphemes.first(source.name).uppercase(),
            style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp, lineHeight = (size.value * 0.5f).sp),
        )
    }
}
