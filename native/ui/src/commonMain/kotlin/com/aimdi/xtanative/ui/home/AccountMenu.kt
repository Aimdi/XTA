package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_antennas
import com.aimdi.xtanative.ui.generated.resources.home_deck
import com.aimdi.xtanative.ui.generated.resources.home_discover
import com.aimdi.xtanative.ui.generated.resources.home_downloads
import com.aimdi.xtanative.ui.generated.resources.home_group_members
import com.aimdi.xtanative.ui.generated.resources.home_groups
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
 * is marked as the bar's page is; [marked] goes on the marked screen's row
 * (the focus F6 gives it).
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
                label = labelOf(destination),
                selected = destination.route == current,
                onClick = { onGo(destination.route) },
                icon = { Icon(iconOf(destination), contentDescription = null) },
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
            GroupRow(group, HomeTab.Group(group.id, group.name).key in unread, selected = group.id == currentGroup) { onGroup(group) }
        }
    }
}

/**
 * The account at the top: its mark, the app's name and the account's handle;
 * a tap opens Settings and a long press the sheet that sets the mark's
 * picture, as in XTA.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountHeader(account: XAccount?, onClose: (() -> Unit)?, onAvatar: () -> Unit, onSettings: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .onSecondaryClick(onAvatar)
            .combinedClickable(
                onClickLabel = stringResource(Res.string.settings),
                onLongClickLabel = stringResource(Res.string.settings_avatar_title),
                onLongClick = onAvatar,
                onClick = { onSettings?.invoke() },
            )
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountMark(account, 52.dp)
            Spacer(Modifier.weight(1f))
            if (onClose != null) IconTip(stringResource(Res.string.close)) { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) } }
        }
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
        account?.screenName?.takeIf { it.isNotEmpty() }?.let { name ->
            Text("@$name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupsHeader(count: Int, query: String, onQuery: (String) -> Unit) {
    Column {
        Hairline(Modifier.padding(top = 12.dp))
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.home_groups), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(Res.string.search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** A group: its mark (with the unread dot), name and size; pinned groups say so. */
@Composable
private fun GroupRow(group: FollowGroup, unread: Boolean, selected: Boolean, onClick: () -> Unit) {
    NavRowFrame(selected = selected, onClick = onClick, shape = NavRowTallShape, minHeight = 64.dp, modifier = Modifier.padding(vertical = 2.dp)) {
        UnreadDot(unread) { GroupMark(group, 40.dp) }
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
}

private fun iconOf(destination: HomeDestination): ImageVector = when (destination) {
    HomeDestination.SEARCH -> Icons.Filled.Search
    HomeDestination.SETTINGS -> Icons.Filled.Settings
    HomeDestination.SUBSCRIPTIONS -> HomeIcons.People
    HomeDestination.TRENDS -> HomeIcons.TrendingUp
    HomeDestination.SAVED -> HomeIcons.Bookmark
    HomeDestination.DOWNLOADS -> HomeIcons.Download
    HomeDestination.ANTENNAS -> HomeIcons.Sensors
    HomeDestination.PLUGINS -> HomeIcons.Extension
    HomeDestination.DECK -> HomeIcons.ViewColumn
}

@Composable
private fun labelOf(destination: HomeDestination): String = when (destination) {
    HomeDestination.SEARCH -> stringResource(Res.string.search)
    HomeDestination.SETTINGS -> stringResource(Res.string.settings)
    HomeDestination.SUBSCRIPTIONS -> stringResource(Res.string.home_subscriptions)
    HomeDestination.TRENDS -> stringResource(Res.string.home_discover)
    HomeDestination.SAVED -> stringResource(Res.string.home_saved)
    HomeDestination.DOWNLOADS -> stringResource(Res.string.home_downloads)
    HomeDestination.ANTENNAS -> stringResource(Res.string.home_antennas)
    HomeDestination.PLUGINS -> stringResource(Res.string.home_plugin_store)
    HomeDestination.DECK -> stringResource(Res.string.home_deck)
}
