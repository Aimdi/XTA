package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.XIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An account in a list, as XTA's user tile: picture, name and handle, the
 * follow menu at its end; a tap opens the profile.
 */
@Composable
fun PersonRow(user: XUser, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.largeAvatarUrl, 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.verified) {
                    Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
            Text(
                "@" + user.screenName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FollowMenu(FollowTarget.Account(user))
    }
}

/** The line between two people, from under the name, as XTA draws it. */
@Composable
fun PersonDivider() {
    Hairline(Modifier.padding(start = 68.dp))
}

/**
 * What a paged list shows after its rows: the first page loading, failing
 * or bringing nothing ([icon] and [empty] say so), then the next page
 * loading or failing.
 */
fun LazyListScope.pageStatus(
    paged: Paged<*>,
    empty: StringResource,
    onRetry: () -> Unit,
    onMore: () -> Unit,
    icon: ImageVector? = null,
) {
    val problem = paged.problem
    when {
        paged.loading -> item(key = "status-loading", contentType = "status") { LoadingRow() }
        problem != null -> item(key = "status-problem", contentType = "status") { ProblemView(problem, onRetry) }
        paged.empty -> item(key = "status-empty", contentType = "status") { EmptyNote(stringResource(empty), icon) }
    }
    item(key = "status-footer", contentType = "footer") {
        val more = paged.moreProblem
        when {
            paged.loadingMore -> LoadingRow()
            more != null -> ProblemRow(more, onMore)
            else -> Spacer(Modifier.padding(bottom = 48.dp))
        }
    }
}

/** "No results", under an icon when there is one, as XTA's empty lists say it. */
@Composable
fun EmptyNote(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp).size(48.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Asks [onMore] for the page after once the list is scrolled near its end, and again each time that page arrives. */
@Composable
fun LoadMoreNearEnd(list: LazyListState, paged: Paged<*>, onMore: () -> Unit) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, paged.items.size, paged.next) {
        if (nearEnd && paged.canLoadMore) onMore()
    }
}

/** A media grid's rows: [shape] says how many tiles a row holds and their shape; a tap opens the viewer at that photo or video. */
fun LazyListScope.mediaRows(tiles: List<MediaTile>, shape: MediaGridShape, onOpen: (MediaTile) -> Unit) {
    items(tiles.chunked(shape.columns), key = { row -> "media-" + row.first().key }, contentType = { "media" }) { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.forEach { tile -> MediaThumb(tile, shape, Modifier.weight(1f)) { onOpen(tile) } }
            repeat(shape.columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MediaThumb(tile: MediaTile, shape: MediaGridShape, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(shape.aspectOf(tile.media))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photoUrl(tile.media.url, if (shape.columns == 1) PhotoSize.MEDIUM else PhotoSize.SMALL),
            contentDescription = tile.media.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (tile.media.kind != XMedia.Kind.PHOTO) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
    }
}

