package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.DeckColumnOptions
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.settings.DeckWidth
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_include_replies
import com.aimdi.xtanative.ui.generated.resources.groups_include_reposts
import com.aimdi.xtanative.ui.generated.resources.home_deck_move_left
import com.aimdi.xtanative.ui.generated.resources.home_deck_move_right
import com.aimdi.xtanative.ui.generated.resources.home_deck_remove
import com.aimdi.xtanative.ui.generated.resources.home_deck_show_unread
import com.aimdi.xtanative.ui.generated.resources.home_deck_width
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_medium
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_narrow
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_same
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_wide
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_unread_count
import com.aimdi.xtanative.ui.generated.resources.saved_media_only
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What a deck column's own choices come to. */
internal object DeckLook {
    /** [options]' own width, else the deck's [deck]. */
    fun widthOf(options: DeckColumnOptions?, deck: DeckWidth): DeckWidth =
        options?.width?.let { stored -> DeckWidth.entries.firstOrNull { it.stored == stored } } ?: deck

    /** Whether [column] reads a feed of posts the column's filters and Media only can shape. */
    fun shapes(column: DeckColumn): Boolean = column is DeckColumn.Group || column == DeckColumn.Following

    /** Whether [column] keeps an unread count to show by its name. */
    fun counts(column: DeckColumn): Boolean = shapes(column)
}

/**
 * A column's ⋮: moving it, taking it off, its Mark all as read, then its
 * own choices: its width (or the deck's), and for a feed of posts its
 * reposts, replies, Media only and unread count.
 */
@Composable
internal fun ColumnMenu(column: DeckColumn, place: ColumnPlace, calls: ColumnCalls) {
    var open by remember { mutableStateOf(false) }
    val close: (() -> Unit) -> Unit = { action -> open = false; action() }
    Box {
        IconTip(stringResource(Res.string.home_more_options)) { IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.home_more_options)) } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.home_deck_move_left)) }, enabled = !place.first, onClick = { close { calls.onMove(-1) } })
            DropdownMenuItem(text = { Text(stringResource(Res.string.home_deck_move_right)) }, enabled = !place.last, onClick = { close { calls.onMove(1) } })
            DropdownMenuItem(text = { Text(stringResource(Res.string.home_deck_remove)) }, onClick = { close(calls.onRemove) })
            calls.onMarkRead?.let { mark ->
                HorizontalDivider()
                MarkReadItem { close(mark) }
            }
            calls.onOptions?.let { change -> OptionItems(column, calls.options) { chosen -> close { change(chosen) } } }
        }
    }
}

@Composable
private fun OptionItems(column: DeckColumn, options: DeckColumnOptions, onChange: (DeckColumnOptions) -> Unit) {
    HorizontalDivider()
    Text(
        stringResource(Res.string.home_deck_width),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    CheckItem(Res.string.home_deck_width_same, options.width == null) { onChange(options.copy(width = null)) }
    DeckWidth.entries.forEach { choice ->
        CheckItem(widthName(choice), options.width == choice.stored) { onChange(options.copy(width = choice.stored)) }
    }
    if (!DeckLook.shapes(column)) return
    HorizontalDivider()
    CheckItem(Res.string.groups_include_reposts, !options.hideReposts) { onChange(options.copy(hideReposts = !options.hideReposts)) }
    CheckItem(Res.string.groups_include_replies, !options.hideReplies) { onChange(options.copy(hideReplies = !options.hideReplies)) }
    CheckItem(Res.string.saved_media_only, options.mediaOnly) { onChange(options.copy(mediaOnly = !options.mediaOnly)) }
    if (DeckLook.counts(column)) CheckItem(Res.string.home_deck_show_unread, options.unreadCount) { onChange(options.copy(unreadCount = !options.unreadCount)) }
}

@Composable
private fun CheckItem(label: StringResource, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = onClick,
    )
}

internal fun widthName(width: DeckWidth): StringResource = when (width) {
    DeckWidth.NARROW -> Res.string.home_deck_width_narrow
    DeckWidth.MEDIUM -> Res.string.home_deck_width_medium
    DeckWidth.WIDE -> Res.string.home_deck_width_wide
}

/** The column's unread count by its name, while it has one. */
@Composable
internal fun ColumnUnread(column: DeckColumn) {
    val tab = UnreadTally.tabOf(column) ?: return
    val now by LocalGraph.current.unread.now.collectAsStateWithLifecycle()
    val count = now.count(tab.key) ?: return
    Text(
        stringResource(Res.string.home_unread_count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp),
    )
}
