package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.session.GroupBoard
import com.aimdi.xtanative.store.settings.GroupsLayout
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_censored
import com.aimdi.xtanative.ui.generated.resources.groups_create
import com.aimdi.xtanative.ui.generated.resources.groups_has_unread
import com.aimdi.xtanative.ui.generated.resources.groups_member_count
import com.aimdi.xtanative.ui.generated.resources.groups_none_description
import com.aimdi.xtanative.ui.generated.resources.groups_none_yet
import com.aimdi.xtanative.ui.generated.resources.groups_pin
import com.aimdi.xtanative.ui.generated.resources.groups_unpin
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Each level of nesting moves a list row this far in. */
private val NEST_INDENT = 20.dp

/** The Groups page: a board of member-faced tiles, or a list that can be dragged into the reader's own order. */
@Composable
fun GroupsPage(
    state: SubscriptionsUi,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCreate: () -> Unit,
    onPin: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    contentPadding: PaddingValues,
) {
    when {
        state.groupCount == 0 -> EmptyGroups(onCreate, contentPadding)
        state.layout == GroupsLayout.LIST -> GroupsList(state, onOpen, onEdit, onPin, onMove, contentPadding)
        else -> GroupsBoard(state, onOpen, onEdit, contentPadding)
    }
}

@Composable
private fun EmptyGroups(onCreate: () -> Unit, contentPadding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(start = 24.dp, end = 24.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(GroupIcons.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.groups_none_yet), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.groups_none_description), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.groups_create), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The tile's own fill: a small step off the page, so tiles read without shadows. */
@Composable
private fun tileColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f).compositeOver(MaterialTheme.colorScheme.background)

@Composable
private fun GroupsBoard(state: SubscriptionsUi, onOpen: (String) -> Unit, onEdit: (String) -> Unit, contentPadding: PaddingValues) {
    // Compact tiles sit two to a phone row, like the large ones.
    val chosen = if (state.compact) 2 else state.columns
    val textScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = GroupBoard.columns(chosen, (maxWidth - 24.dp).value)
        val fixedHeight = if (state.compact) GroupBoard.compactTileHeight(textScale) else GroupBoard.tileHeight(chosen, columns, textScale)
        val ratio = GroupBoard.tileRatio(columns) / GroupBoard.textGrowth(textScale)
        val spacing = if (state.compact) 8.dp else 10.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding() + 10.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.board.safe, key = { it.id }) { tile ->
                GroupTileView(tile, state.compact, fixedHeight, ratio, { onOpen(tile.id) }, { onEdit(tile.id) })
            }
            if (state.board.nsfw.isNotEmpty()) {
                item(key = "censored", span = { GridItemSpan(maxLineSpan) }) { CensoredHeader() }
                items(state.board.nsfw, key = { it.id }) { tile ->
                    GroupTileView(tile, state.compact, fixedHeight, ratio, { onOpen(tile.id) }, { onEdit(tile.id) })
                }
            }
        }
    }
}

/**
 * One group on the board (group_tile.dart): a flat card with a hairline
 * border, its members' faces or its mark, its name after the pinned and NSFW
 * marks, and its member count. A colour the reader chose tints its border
 * and washes over it faintly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTileView(tile: GroupTile, compact: Boolean, fixedHeight: Float?, ratio: Float, onTap: () -> Unit, onLongPress: () -> Unit) {
    val group = tile.group
    val base = tileColor()
    val chosen = group.color?.let(::groupColor)
    val shape = RoundedCornerShape(16.dp)
    val count = pluralStringResource(Res.plurals.groups_member_count, tile.memberCount, tile.memberCount)
    val label = listOfNotNull(group.name, count, stringResource(Res.string.groups_has_unread).takeIf { tile.unread }).joinToString(", ")
    Box(
        Modifier
            .fillMaxWidth()
            .let { if (fixedHeight != null) it.height(fixedHeight.dp) else it.aspectRatio(ratio) }
            .clip(shape)
            .background(chosen?.copy(alpha = 0.07f)?.compositeOver(base) ?: base)
            .border(1.dp, chosen?.copy(alpha = 0.55f) ?: MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        val cover = Modifier.let { if (group.nsfw) it.blur(8.dp) else it }
        if (compact) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupCover(tile, base, GroupBoard.COMPACT_COVER.dp, cover.size(GroupBoard.COMPACT_COVER.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    TileName(tile)
                    TileCount(count)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                GroupCover(tile, base, 74.dp, cover.weight(1f, fill = true))
                Spacer(Modifier.height(10.dp))
                TileName(tile)
                Spacer(Modifier.height(2.dp))
                TileCount(count)
            }
        }
        if (tile.unread) UnreadDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun TileName(tile: GroupTile) {
    val meta = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tile.group.pinned) {
            Icon(GroupIcons.PushPin, contentDescription = null, tint = meta, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (tile.group.nsfw) {
            Icon(GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = meta, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(tile.group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TileCount(count: String) {
    Text(count, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The accent dot of a group with posts newer than its last read mark. */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

/** The label above the NSFW groups. */
@Composable
private fun CensoredHeader() {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.groups_censored),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private const val HEADER = "censored-header"

/**
 * The groups as rows (_group_list_item.dart): indented by nesting, with a pin
 * and, in the reader's own order, a handle to drag them by. A drag stays on
 * its side of Censored.
 */
@Composable
private fun GroupsList(
    state: SubscriptionsUi,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val list = rememberLazyListState()
    val drag = rememberRowDrag(list)
    val tiles = state.board.all.associateBy { it.id }
    val keys: List<Any> = state.board.safe.map { it.id } + (if (state.board.nsfw.isNotEmpty()) listOf(HEADER) else emptyList()) + state.board.nsfw.map { it.id }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(drag.shown(keys), key = { it }) { key ->
            val tile = tiles[key]
            if (tile == null) {
                CensoredHeader()
            } else {
                val dragged = drag.key == key
                Box(Modifier.graphicsLayer { translationY = if (dragged) drag.offset else 0f }.let { if (dragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh) else it }) {
                    GroupListRow(
                        tile = tile,
                        onOpen = { onOpen(tile.id) },
                        onEdit = { onEdit(tile.id) },
                        onPin = { onPin(tile.id, !tile.group.pinned) },
                        handle = if (!state.canDragGroups) {
                            null
                        } else {
                            Modifier.pointerInput(key, keys) {
                                detectDragGestures(
                                    onDragStart = { drag.start(key, keys) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.drag(amount.y)
                                    },
                                    onDragEnd = { drag.end()?.let { (from, to) -> onMove(from, to) } },
                                    onDragCancel = { drag.end() },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupListRow(tile: GroupTile, onOpen: () -> Unit, onEdit: () -> Unit, onPin: () -> Unit, handle: Modifier?) {
    val group = tile.group
    val count = pluralStringResource(Res.plurals.groups_member_count, tile.memberCount, tile.memberCount)
    val hidden = tile.memberCount - tile.faces.size
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onEdit)
            .padding(start = 16.dp + NEST_INDENT * tile.depth, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ListMark(tile)
            if (tile.unread) UnreadDot(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.nsfw) {
                    Icon(GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tile.faces.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FaceCluster(tile.faces)
                    if (hidden > 0) {
                        Text("+$hidden", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        IconButton(onClick = onPin) {
            Icon(
                if (group.pinned) GroupIcons.PushPin else GroupIcons.PushPinOutlined,
                contentDescription = stringResource(if (group.pinned) Res.string.groups_unpin else Res.string.groups_pin),
                tint = if (group.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (handle != null) {
            Icon(GroupIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = handle.padding(8.dp))
        } else {
            Icon(GroupIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A list row's disc, as XTA draws it there: the group's colour with its icon
 * when it has one, else its first character.
 */
@Composable
private fun ListMark(tile: GroupTile) {
    val seed = GroupLook.seedColor(tile.group)
    val icon = GroupLook.iconKey(tile.group.icon)?.takeIf { tile.group.icon != GroupLook.DEFAULT_ICON }?.let(GroupIcons::mark)
    Box(Modifier.size(40.dp).clip(CircleShape).background(groupColor(seed)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = onGroupColor(seed), modifier = Modifier.size(20.dp))
        } else {
            Text(
                tile.group.name.trim().take(1).uppercase().ifEmpty { "?" },
                color = onGroupColor(seed),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

/** A few members' faces overlapping, beside the count. */
@Composable
private fun FaceCluster(faces: List<MemberFace>) {
    val ring = MaterialTheme.colorScheme.surface
    Box(Modifier.size(width = (13 * (faces.size - 1) + 23).dp, height = 23.dp)) {
        faces.forEachIndexed { index, face ->
            MemberFaceView(face, 20.dp, Modifier.offset(x = (13 * index).dp).border(1.5.dp, ring, CircleShape))
        }
    }
}
