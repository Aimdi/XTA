package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_add_to_group
import com.aimdi.xtanative.ui.generated.resources.discover_hide_from_main_feed
import com.aimdi.xtanative.ui.generated.resources.discover_show_in_main_feed
import com.aimdi.xtanative.ui.generated.resources.discover_subscribe
import com.aimdi.xtanative.ui.generated.resources.discover_unsubscribe
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.subscriptions.GroupBoardModel
import com.aimdi.xtanative.ui.subscriptions.GroupMembershipSheet
import com.aimdi.xtanative.ui.subscriptions.GroupPicker
import com.aimdi.xtanative.ui.subscriptions.Unfollowing
import org.jetbrains.compose.resources.stringResource

/** [change] applied to the session, to its end even when the screen that asked goes away. */
private fun AppGraph.changeSession(change: (Session) -> Session) {
    keep { sessions.update(change) }
}

/**
 * XTA's follow button (user.dart's FollowButton): [face] shows whether
 * [target] is followed here and opens the menu, which offers Subscribe or
 * Unsubscribe (with XTA's undo), Add to group and, for an account followed,
 * showing it in the main feed or keeping it out. The face is an icon unless
 * one is given: a profile's Subscribe / Subscribed. Nothing is followed on X.
 */
@Composable
fun FollowMenu(target: FollowTarget, modifier: Modifier = Modifier, face: @Composable (Followed, () -> Unit) -> Unit = { state, open -> FollowIcon(state, open) }) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val state = session?.let(target::state) ?: Followed()
    var open by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        face(state) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (state.followed) Res.string.discover_unsubscribe else Res.string.discover_subscribe)) },
                onClick = {
                    open = false
                    if (state.followed) graph.keep { Unfollowing.unfollow(graph, target) } else graph.changeSession { target.followed(it, System.currentTimeMillis()) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.discover_add_to_group)) },
                onClick = {
                    open = false
                    picking = true
                },
            )
            if (state.followed && target is FollowTarget.Account) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (state.inFeed) Res.string.discover_hide_from_main_feed else Res.string.discover_show_in_main_feed)) },
                    onClick = {
                        open = false
                        graph.changeSession(target::feedToggled)
                    },
                )
            }
        }
    }
    if (picking) GroupsSheet(target, onDismiss = { picking = false })
}

/** The icon face: someone to follow, followed, or followed but kept out of the main feed. */
@Composable
private fun FollowIcon(state: Followed, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        val icon = when {
            !state.followed -> DiscoverIcons.PersonAdd
            state.inFeed -> DiscoverIcons.PersonRemove
            else -> DiscoverIcons.VisibilityOff
        }
        Icon(icon, contentDescription = stringResource(if (state.followed) Res.string.discover_unsubscribe else Res.string.discover_subscribe))
    }
}

/**
 * Which groups [target] is in (user.dart's pickUserGroups): XTA's membership
 * sheet, with its search that makes a group and its recently used. The
 * choice applies as it closes; one that changes anything follows [target]
 * here first, as XTA's does.
 */
@Composable
fun GroupsSheet(target: FollowTarget, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val loadedSession by graph.sessions.state.collectAsStateWithLifecycle()
    val loadedSettings by graph.settings.state.collectAsStateWithLifecycle()
    val session = loadedSession ?: return
    val settings = loadedSettings ?: return
    val initial = remember(target) { session.groups.filter { target.memberId in it.memberIds }.mapTo(LinkedHashSet()) { it.id } }
    // The app's own group changes, written to their end whatever screen asked.
    val actions = LocalPlugins.current.groups
    GroupMembershipSheet(
        groups = GroupBoardModel.ordered(session.groups, settings[SubscriptionPrefs.groupsOrder], settings[SubscriptionPrefs.groupsAscending]),
        selected = initial,
        recent = GroupPicker.recent(settings[SubscriptionPrefs.groupsRecentPicks]),
        create = actions::create,
        onDone = { chosen ->
            onDismiss()
            if (chosen != null) {
                graph.changeSession { target.followed(it, System.currentTimeMillis()).withMemberships(target.memberId, chosen) }
                actions.rememberPicks(initial, chosen)
            }
        },
    )
}
