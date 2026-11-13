package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.media_alt_badge
import com.aimdi.xtanative.ui.generated.resources.media_alt_title
import com.aimdi.xtanative.ui.generated.resources.media_tap_to_show
import com.aimdi.xtanative.ui.generated.resources.reader_ok
import org.jetbrains.compose.resources.stringResource

private val BadgeShape = RoundedCornerShape(6.dp)

/**
 * A small label over a picture (tweet_chrome.dart's TweetMediaBadge): a
 * word, an icon or both, white on a dark pill. [description], when given,
 * is what a screen reader says instead.
 */
@Composable
fun MediaBadge(modifier: Modifier = Modifier, label: String? = null, icon: ImageVector? = null, description: String? = null) {
    Row(
        modifier
            .let { if (description != null) it.clearAndSetSemantics { contentDescription = description } else it }
            .clip(BadgeShape)
            .background(Color.Black.copy(alpha = 0.68f))
            .border(0.5.dp, Color.White.copy(alpha = 0.24f), BadgeShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        label?.let { Text(it, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

/**
 * "ALT" at the foot of a described picture (_media.dart, plugin_post_media.dart's
 * _AltBadge): a tap or a long press on it shows [alt] in [AltTextDialog].
 */
@Composable
fun AltBadge(alt: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AltBadge(onClick = { open = true }, modifier)
    if (open) AltTextDialog(alt) { open = false }
}

/** "ALT" on a described picture, in a touch target of its own: a tap or a long press calls [onClick], which shows the description. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AltBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.media_alt_title)
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .combinedClickable(role = Role.Button, onClickLabel = title, onClick = onClick, onLongClick = onClick)
            .semantics { contentDescription = title }
            .padding(8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        MediaBadge(label = stringResource(Res.string.media_alt_badge))
    }
}

/** "Image description" and a picture's [description], to read and copy, with OK (_media.dart's _showAltTextDialog). */
@Composable
fun AltTextDialog(description: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.media_alt_title)) },
        text = { SelectionContainer { Text(description.trim()) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.reader_ok)) } },
    )
}

/**
 * What stands in a picture's place while media loads manually (_media.dart,
 * media_grid.dart): [icon] and "Tap to show [what]"; a tap ([onShow]) loads
 * it. [compact] is a grid's tile, smaller and quieter.
 */
@Composable
fun ShowMediaTile(what: String, icon: ImageVector, onShow: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val label = stringResource(Res.string.media_tap_to_show, what)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = muted.copy(alpha = if (compact) 0.08f else 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
    Column(
        modifier
            .background(tint)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onShow)
            .padding(if (compact) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = muted)
        Text(label, style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
