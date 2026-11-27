package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.generated.resources.groups_delete
import com.aimdi.xtanative.ui.generated.resources.groups_edit
import com.aimdi.xtanative.ui.generated.resources.plugin_open
import com.aimdi.xtanative.ui.post.PostMenuSurface
import com.aimdi.xtanative.ui.post.MenuGroupDivider
import com.aimdi.xtanative.ui.post.MenuRow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.session.GroupBoard
import com.aimdi.xtanative.store.settings.GroupsLayout
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_censored
import com.aimdi.xtanative.ui.generated.resources.groups_create
import com.aimdi.xtanative.ui.generated.resources.groups_has_unread
import com.aimdi.xtanative.ui.generated.resources.groups_member_count
import com.aimdi.xtanative.ui.generated.resources.groups_none_description
import com.aimdi.xtanative.ui.generated.resources.groups_none_description_desktop
import com.aimdi.xtanative.ui.generated.resources.groups_none_yet
import com.aimdi.xtanative.ui.generated.resources.groups_pin
import com.aimdi.xtanative.ui.generated.resources.groups_unpin
import com.aimdi.xtanative.ui.group.RankWithAiButton
import com.aimdi.xtanative.ui.home.rememberRetappedList
import com.aimdi.xtanative.ui.home.rememberRowKeyMoves
import com.aimdi.xtanative.ui.home.rowMovedByKeys
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.aimdi.xtanative.ui.settings.isDesktop
import com.aimdi.xtanative.ui.common.EmptyState

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
    onStep: (id: String, up: Boolean) -> Unit,
    contentPadding: PaddingValues,
    onDiscover: ((String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
) {
    val menu = { tile: GroupTile ->
        GroupMenuActions(
            open = { onOpen(tile.id) },
            edit = { onEdit(tile.id) },
            pin = { onPin(tile.id, !tile.group.pinned) },
            delete = onDelete?.let { { it(tile.id) } },
        )
    }
    when {
        state.groupCount == 0 -> EmptyGroups(onCreate, contentPadding)
        state.layout == GroupsLayout.LIST -> GroupsList(state, menu, onMove, onStep, contentPadding, onDiscover)
        else -> GroupsBoard(state, menu, contentPadding, onDiscover)
    }
}

/** What a group's menu ([GroupMenu]) and its tile or row do: open it, edit it, pin or unpin it, delete it (asked first). */
private class GroupMenuActions(val open: () -> Unit, val edit: () -> Unit, val pin: () -> Unit, val delete: (() -> Unit)?)

/**
 * A group's menu at the pointer, which a right-click on its tile or row
 * opens ([PostMenuSurface]) as a desktop opens a thing's menu: Open, Edit,
 * Pin or Unpin, and Delete in the error colour. A long press still opens the
 * edit sheet, as it did.
 */
@Composable
private fun GroupMenu(group: FollowGroup, actions: GroupMenuActions, onDismiss: () -> Unit) {
    val picked = { action: () -> Unit -> { onDismiss(); action() } }
    PostMenuSurface(onDismiss) {
        MenuRow(GroupIcons.ChevronRight, stringResource(Res.string.plugin_open), picked(actions.open))
        MenuRow(GroupIcons.Tune, stringResource(Res.string.groups_edit), picked(actions.edit))
        MenuRow(
            if (group.pinned) GroupIcons.PushPin else GroupIcons.PushPinOutlined,
            stringResource(if (group.pinned) Res.string.groups_unpin else Res.string.groups_pin),
            picked(actions.pin),
        )
        actions.delete?.let { delete ->
            MenuGroupDivider()
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                MenuRow(Icons.Filled.Delete, stringResource(Res.string.groups_delete), picked(delete))
            }
        }
    }
}

@Composable
private fun EmptyGroups(onCreate: () -> Unit, contentPadding: PaddingValues) {
    EmptyState(
        GroupIcons.Workspaces,
        stringResource(Res.string.groups_none_yet),
        Modifier.padding(contentPadding),
        body = stringResource(if (isDesktop) Res.string.groups_none_description_desktop else Res.string.groups_none_description),
    ) {
        Button(onClick = onCreate) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.groups_create), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** What a tile stands on: the page itself, as tiles have no box of their own. */
@Composable
private fun tileColor(): Color = MaterialTheme.colorScheme.background

@Composable
private fun GroupsBoard(state: SubscriptionsUi, menu: (GroupTile) -> GroupMenuActions, contentPadding: PaddingValues, onDiscover: ((String) -> Unit)?) {
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
                GroupTileView(tile, state.compact, fixedHeight, ratio, menu(tile), onDiscover?.let { { it(tile.id) } })
            }
            if (state.board.nsfw.isNotEmpty()) {
                item(key = "censored", span = { GridItemSpan(maxLineSpan) }) { CensoredHeader() }
                items(state.board.nsfw, key = { it.id }) { tile ->
                    GroupTileView(tile, state.compact, fixedHeight, ratio, menu(tile), onDiscover?.let { { it(tile.id) } })
                }
            }
        }
    }
}

/**
 * One group on the board (group_tile.dart): straight on the page, its
 * members' faces or its mark, its name after the pinned and NSFW marks, and
 * its member count. A colour the reader chose draws a hairline of it around
 * the tile; a group without one has no outline. With [onDiscover], its sparkle opens its
 * Discover ranked by the reader's AI.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTileView(tile: GroupTile, compact: Boolean, fixedHeight: Float?, ratio: Float, actions: GroupMenuActions, onDiscover: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    if (menu) GroupMenu(tile.group, actions) { menu = false }
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
            .let { if (chosen != null) it.border(1.dp, chosen.copy(alpha = 0.55f), shape) else it }
            // A right-click opens the group's menu at the pointer, as a desktop opens a thing's menu.
            .onSecondaryClick { menu = true }
            .combinedClickable(onClick = actions.open, onLongClick = actions.edit)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        val cover = Modifier.let { if (group.nsfw) it.blur(8.dp) else it }
        if (compact) {
            Row(
                Modifier.fillMaxSize().padding(start = 10.dp, end = if (onDiscover == null) 10.dp else 48.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkedCover(tile.unread) { GroupCover(tile, base, GroupBoard.COMPACT_COVER.dp, cover.size(GroupBoard.COMPACT_COVER.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    TileName(tile)
                    TileCount(count)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                // The cover as big as it gets, up to its largest, the name right under it and any room left below.
                MarkedCover(tile.unread, Modifier.weight(1f, fill = false)) { GroupCover(tile, base, 74.dp, cover) }
                Spacer(Modifier.height(10.dp))
                TileName(tile)
                Spacer(Modifier.height(2.dp))
                TileCount(count)
            }
        }
        if (onDiscover != null) {
            RankWithAiButton(Modifier.align(if (compact) Alignment.CenterEnd else Alignment.TopEnd).padding(2.dp), iconSize = 20.dp, onClick = onDiscover)
        }
    }
}

/**
 * A tile's cover with the unread dot ([unread]) on its face's rim
 * ([CoverDot]), as the sidebar's row marks a group's avatar, not off at the
 * tile's far edge.
 */
@Composable
private fun MarkedCover(unread: Boolean, modifier: Modifier = Modifier, cover: @Composable () -> Unit) {
    Layout(content = { cover(); if (unread) UnreadDot() }, modifier = modifier) { measurables, constraints ->
        val face = measurables[0].measure(constraints)
        val dot = measurables.getOrNull(1)?.measure(Constraints())
        layout(face.width, face.height) {
            face.place(0, 0)
            dot?.let {
                val centre = CoverDot.centre(face.width.toFloat())
                it.place((face.width - centre - it.width / 2f).roundToInt(), (centre - it.height / 2f).roundToInt())
            }
        }
    }
}

/** Where a cover's unread dot sits: on its round face's rim (the face [FACE] of the cover), up and to the end at 45°. */
internal object CoverDot {
    /** How much of the cover a single face or a group's mark spans ([MemberMosaic]). */
    const val FACE = 0.78f
    private const val COS_45 = 0.70710677f

    /** The dot's centre from the cover's top and its end edge, for a cover [extent] wide. */
    fun centre(extent: Float): Float = extent * (1 - FACE) / 2 + extent * FACE / 2 * (1 - COS_45)
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
        TipText(tile.group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TileCount(count: String) {
    TipText(count, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    menu: (GroupTile) -> GroupMenuActions,
    onMove: (Int, Int) -> Unit,
    onStep: (id: String, up: Boolean) -> Unit,
    contentPadding: PaddingValues,
    onDiscover: ((String) -> Unit)?,
) {
    val list = rememberRetappedList()
    val drag = rememberRowDrag(list)
    val tiles = state.board.all.associateBy { it.id }
    val keys: List<Any> = state.board.safe.map { it.id } + (if (state.board.nsfw.isNotEmpty()) listOf(HEADER) else emptyList()) + state.board.nsfw.map { it.id }
    val moves = rememberRowKeyMoves { key, up ->
        (state.canDragGroups && key is String && GroupBoardModel.keyStep(state.board, keys.indexOf(key), up) != null).also { if (it) onStep(key as String, up) }
    }
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
                Box(Modifier.rowMovedByKeys(moves, key).graphicsLayer { translationY = if (dragged) drag.offset else 0f }.let { if (dragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh) else it }) {
                    GroupListRow(
                        tile = tile,
                        actions = menu(tile),
                        onDiscover = onDiscover?.let { { it(tile.id) } },
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
private fun GroupListRow(tile: GroupTile, actions: GroupMenuActions, onDiscover: (() -> Unit)?, handle: Modifier?) {
    val group = tile.group
    var menu by remember { mutableStateOf(false) }
    if (menu) GroupMenu(group, actions) { menu = false }
    val count = pluralStringResource(Res.plurals.groups_member_count, tile.memberCount, tile.memberCount)
    val hidden = tile.memberCount - tile.faces.size
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            // A right-click opens the group's menu at the pointer, as a desktop opens a thing's menu.
            .onSecondaryClick { menu = true }
            .combinedClickable(onClick = actions.open, onLongClick = actions.edit)
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
                TipText(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TipText(count, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tile.faces.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FaceCluster(tile.faces)
                    if (hidden > 0) {
                        Text("+$hidden", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        if (onDiscover != null) RankWithAiButton(iconSize = 20.dp, onClick = onDiscover)
        IconTip(stringResource(if (group.pinned) Res.string.groups_unpin else Res.string.groups_pin)) { IconButton(onClick = actions.pin) {
            Icon(
                if (group.pinned) GroupIcons.PushPin else GroupIcons.PushPinOutlined,
                contentDescription = stringResource(if (group.pinned) Res.string.groups_unpin else Res.string.groups_pin),
                tint = if (group.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } }
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
