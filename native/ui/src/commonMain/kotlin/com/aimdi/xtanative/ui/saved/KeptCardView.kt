package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.saved_too_large
import org.jetbrains.compose.resources.stringResource

/**
 * A kept post of another network, until that network's own card is here:
 * its network, who wrote it, its title and text, up to four pictures, and a
 * tap that opens it where it lives.
 */
@Composable
fun KeptCardView(card: KeptCard, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val open = stringResource(Res.string.saved_open_in_browser)
    Column(
        modifier
            .fillMaxWidth()
            .let { if (card.url != null) it.clickable(onClickLabel = open) { runCatching { uri.openUri(card.url) } } else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceMark(card.source, 16)
            Spacer(Modifier.width(8.dp))
            Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            card.handle?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        card.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
        if (card.text.isNotBlank()) Text(card.text, maxLines = 12, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        val images = card.images.take(4)
        if (images.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                images.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(if (images.size == 1) 220.dp else 140.dp),
                    )
                }
            }
        }
    }
}

/** A saved post too large to have been kept whole. */
@Composable
fun TooLarge() {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.saved_too_large), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
