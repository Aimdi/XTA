package com.aimdi.xtanative.plugins.rss

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * An article row (rss_card.dart): its feed and author over the headline and
 * excerpt, the picture beside them (under them at large text), unread ones
 * marked by a dot and a heavier title. [showSource] names the feed, where
 * several feeds' items mix; [onLongPress] is what a long press opens, where
 * there is something.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RssItemCard(item: RssItem, unread: Boolean, onOpen: () -> Unit, showSource: Boolean = true, onLongPress: (() -> Unit)? = null) {
    val cover = item.imageUrl?.takeIf { it.isNotEmpty() }
    val largeText = LocalDensity.current.fontScale * 14 > 20
    val unreadLabel = pluginString(Strings.unread)
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress).semantics { if (unread) stateDescription = unreadLabel }.padding(16.dp),
    ) {
        SourceLine(item, unread, showSource)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Copy(item, unread, Modifier.weight(1f))
            if (cover != null && !largeText) {
                Spacer(Modifier.width(16.dp))
                Cover(cover, Modifier.size(88.dp))
            }
        }
        if (cover != null && largeText) Cover(cover, Modifier.padding(top = 12.dp).fillMaxWidth().height(140.dp))
    }
    Hairline()
}

/**
 * An item the app draws among others' posts (Home's timelines, the groups),
 * as XTA's draws it there: its article row naming its feed, read or not as
 * the reader left it; it opens in the reader, and a long press opens the
 * shared post menu.
 */
@Composable
internal fun SharedItemCard(app: RssApp, item: RssItem) {
    val read by app.read.collectAsState()
    RssItemCard(item, unread = item.id !in read, onOpen = { app.openItem(item) }, onLongPress = { app.host.openPostMenu(app.shared.menu(item)) })
}

@Composable
private fun SourceLine(item: RssItem, unread: Boolean, showSource: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (unread) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            listOfNotNull(item.feedTitle.takeIf { showSource }, item.author?.ifEmpty { null }).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        item.publishedAt?.let {
            Spacer(Modifier.width(8.dp))
            Text(agoText(it), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun Copy(item: RssItem, unread: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.3.em),
            fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        item.excerpt?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 1.4.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Cover(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
