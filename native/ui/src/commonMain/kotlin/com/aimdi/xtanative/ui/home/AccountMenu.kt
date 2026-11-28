package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_antennas
import com.aimdi.xtanative.ui.generated.resources.history_title
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.generated.resources.home_deck
import com.aimdi.xtanative.ui.generated.resources.home_discover
import com.aimdi.xtanative.ui.generated.resources.home_downloads
import com.aimdi.xtanative.ui.generated.resources.home_group_members
import com.aimdi.xtanative.ui.generated.resources.home_groups
import com.aimdi.xtanative.ui.generated.resources.home_mark_all_read
import com.aimdi.xtanative.ui.generated.resources.home_no_groups
import com.aimdi.xtanative.ui.generated.resources.home_no_results
import com.aimdi.xtanative.ui.generated.resources.home_plugin_store
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.home_subscriptions
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The account menu (XTA's home drawer): the reader's account at the top, then
 * search and settings, the screens beside the bar and those of its pages the
 * reader took off it, and the groups, which are this app's lists and what the
 * reader reaches for most. In a wide window it stands open beside Home
 * ([HomeWide]), with the bar's pages ([pages]) under the account and
 * nothing to close ([onClose] null). The row of the screen shown beside it
 * ([current], its route), or of the group shown ([currentGroup], its id),
 * is marked as the bar's page is; [marked] goes on the marked screen's or
 * group's row (the focus F6 gives it).
 */
@Composable
internal fun AccountMenu(
    ui: HomeUi,
    routes: HomeRoutes,
    deckFits: Boolean,
    unread: Set<String>,
    onGo: (String) -> Unit,
    onGroup: (FollowGroup) -> Unit,
    onClose: (() -> Unit)?,
    onAvatar: () -> Unit,
    pages: LazyListScope.() -> Unit = {},
    current: String? = null,
    currentGroup: String? = null,
    marked: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = DrawerGroups.listed(ui.session.groups, ui.session.feedGroups, routes.exists(HomeRoutes.GROUP))
    val shown = DrawerGroups.matching(groups, query)
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "account") {
            AccountHeader(ui.account, onClose = onClose, onAvatar = onAvatar, onSettings = { onGo(HomeDestination.SETTINGS.route) }.takeIf { routes.exists(HomeDestination.SETTINGS.route) })
        }
        pages()
        // Search and Settings, then the screens beside the bar: one kind of row, the one shown marked.
        items(HomeMenu.shown(HomeMenu.buttons, routes.exists, deckFits) + HomeMenu.shown(HomeMenu.list, routes.exists, deckFits, onBar = ui.bar.mapTo(HashSet()) { it.id }), key = { it.route }) { destination ->
            NavRow(
                label = menuLabel(destination),
                selected = destination.route == current,
                onClick = { onGo(destination.route) },
                icon = { Icon(menuIcon(destination, destination.route == current), contentDescription = null) },
                modifier = if (destination.route == current) marked else Modifier,
            )
        }
        item(key = "groups") {
            GroupsHeader(groups.size, query) { query = it }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(if (groups.isEmpty()) Res.string.home_no_groups else Res.string.home_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(shown, key = { "group-" + it.id }) { group ->
            val selected = group.id == currentGroup
            GroupRow(group, HomeTab.Group(group.id, group.name).key in unread, selected, modifier = if (selected) marked else Modifier) { onGroup(group) }
        }
    }
}

/**
 * The account at the top: its mark, the app's name and the account's handle;
 * a tap opens Settings and a long press the sheet that sets the mark's
 * picture, as in XTA. For a pointer ([LocalNavCompact]) it is one line, its
 * mark small beside the names, lit on a pill inset from the menu's edges as
 * the rows below are.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountHeader(account: XAccount?, onClose: (() -> Unit)?, onAvatar: () -> Unit, onSettings: (() -> Unit)?) {
    val compact = LocalNavCompact.current
    val opens = Modifier
        .onSecondaryClick(onAvatar)
        .combinedClickable(
            onClickLabel = stringResource(Res.string.settings),
            onLongClickLabel = stringResource(Res.string.settings_avatar_title),
            onLongClick = onAvatar,
            onClick = { onSettings?.invoke() },
        )
    if (compact) return CompactAccountHeader(account, onClose, opens)
    Column(
        Modifier
            .fillMaxWidth()
            .then(opens)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountMark(account, 52.dp)
            Spacer(Modifier.weight(1f))
            if (onClose != null) CloseButton(onClose)
        }
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
        account?.screenName?.takeIf { it.isNotEmpty() }?.let { name ->
            Text("@$name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The account at the top for a pointer: its 32 dp mark, the app's name over the handle, and the close button where there is one. */
@Composable
private fun CompactAccountHeader(account: XAccount?, onClose: (() -> Unit)?, opens: Modifier) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(HEADER_SHAPE).then(opens).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountMark(account, 32.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                account?.screenName?.takeIf { it.isNotEmpty() }?.let { name ->
                    Text("@$name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (onClose != null) CloseButton(onClose)
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconTip(stringResource(Res.string.close)) { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) } }
}

/** The compact header's pill, rounded as the desktop's hovers are. */
private val HEADER_SHAPE = RoundedCornerShape(8.dp)

@Composable
private fun GroupsHeader(count: Int, query: String, onQuery: (String) -> Unit) {
    Column {
        Hairline(Modifier.padding(top = 12.dp))
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.home_groups), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SearchField(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(Res.string.search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * A group: its mark (with the unread dot, or how many posts are unread),
 * name and size; pinned groups say so. A right click offers Mark all as read
 * while it has unread posts.
 */
@Composable
private fun GroupRow(group: FollowGroup, unread: Boolean, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val compact = LocalNavCompact.current
    val tally = LocalGraph.current.unread
    val now by tally.now.collectAsState()
    val tab = HomeTab.Group(group.id, group.name)
    var menu by remember { mutableStateOf(false) }
    Box {
        NavRowFrame(
            selected = selected,
            onClick = onClick,
            shape = NavRowTallShape,
            minHeight = NavRowSizes.groupRow(compact),
            modifier = Modifier.padding(vertical = if (compact) 1.dp else 2.dp).onSecondaryClick { if (unread) menu = true }.then(modifier),
        ) {
            UnreadDot(unread, now.count(tab.key)) { GroupMark(group, NavRowSizes.groupMark(compact)) }
            GroupRowText(group)
        }
        MarkReadMenu(menu, onDismiss = { menu = false }) { tally.markRead(tab) }
    }
}

@Composable
private fun RowScope.GroupRowText(group: FollowGroup) {
    Column(Modifier.weight(1f)) {
        TipText(group.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(Res.plurals.home_group_members, group.memberIds.size, group.memberIds.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    if (group.pinned) {
        Icon(XIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    } else {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A menu of one item, Mark all as read, which [onMark] does. */
@Composable
internal fun MarkReadMenu(expanded: Boolean, onDismiss: () -> Unit, onMark: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MarkReadItem {
            onDismiss()
            onMark()
        }
    }
}

/** Mark all as read, as a menu's row. */
@Composable
internal fun MarkReadItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.home_mark_all_read)) },
        leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
        onClick = onClick,
    )
}

/** A menu entry's icon: outlined, as every icon of the menu is, and filled while its screen is shown ([selected]), as the bar's pages are. */
internal fun menuIcon(destination: HomeDestination, selected: Boolean): ImageVector = when (destination) {
    HomeDestination.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
    HomeDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    HomeDestination.SUBSCRIPTIONS -> if (selected) HomeIcons.People else HomeIcons.PeopleOutlined
    HomeDestination.TRENDS -> HomeIcons.TrendingUp
    HomeDestination.SAVED -> if (selected) HomeIcons.BookmarkFilled else HomeIcons.Bookmark
    HomeDestination.DOWNLOADS -> if (selected) HomeIcons.Download else HomeIcons.DownloadOutlined
    HomeDestination.HISTORY -> DiscoverIcons.History
    HomeDestination.ANTENNAS -> HomeIcons.Sensors
    HomeDestination.PLUGINS -> if (selected) HomeIcons.Extension else HomeIcons.ExtensionOutlined
    HomeDestination.DECK -> if (selected) HomeIcons.ViewColumn else HomeIcons.ViewColumnOutlined
}

/** A menu entry's name. */
@Composable
internal fun menuLabel(destination: HomeDestination): String = when (destination) {
    HomeDestination.SEARCH -> stringResource(Res.string.search)
    HomeDestination.SETTINGS -> stringResource(Res.string.settings)
    HomeDestination.SUBSCRIPTIONS -> stringResource(Res.string.home_subscriptions)
    HomeDestination.TRENDS -> stringResource(Res.string.home_discover)
    HomeDestination.SAVED -> stringResource(Res.string.home_saved)
    HomeDestination.DOWNLOADS -> stringResource(Res.string.home_downloads)
    HomeDestination.HISTORY -> stringResource(Res.string.history_title)
    HomeDestination.ANTENNAS -> stringResource(Res.string.home_antennas)
    HomeDestination.PLUGINS -> stringResource(Res.string.home_plugin_store)
    HomeDestination.DECK -> stringResource(Res.string.home_deck)
}
