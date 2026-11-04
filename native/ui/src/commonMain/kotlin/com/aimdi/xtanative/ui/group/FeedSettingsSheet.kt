package com.aimdi.xtanative.ui.group

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_mode
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_mode_description
import com.aimdi.xtanative.ui.generated.resources.groups_custom
import com.aimdi.xtanative.ui.generated.resources.groups_deck
import com.aimdi.xtanative.ui.generated.resources.groups_deck_pin
import com.aimdi.xtanative.ui.generated.resources.groups_deck_unpin
import com.aimdi.xtanative.ui.generated.resources.groups_filters
import com.aimdi.xtanative.ui.generated.resources.groups_include_replies
import com.aimdi.xtanative.ui.generated.resources.groups_include_reposts
import com.aimdi.xtanative.ui.generated.resources.groups_not_all_posts
import com.aimdi.xtanative.ui.generated.resources.groups_popular
import com.aimdi.xtanative.ui.generated.resources.groups_popular_description
import com.aimdi.xtanative.ui.generated.resources.groups_recent
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A group feed's filters (_settings.dart): its own reply and repost choices,
 * which show the session's until it has one, catch-up mode for a
 * chronological feed, the deck column and the order. The Following feed
 * ([following]) keeps its place by itself and is no deck column: it has
 * neither row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSettingsSheet(state: GroupFeedUi, actions: GroupSettingsActions, onCustom: () -> Unit, onDismiss: () -> Unit, following: Boolean = false) {
    var orders by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.groups_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                stringResource(Res.string.groups_not_all_posts),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            SwitchRow(Res.string.groups_include_replies, null, state.includeReplies, actions::setIncludeReplies)
            SwitchRow(Res.string.groups_include_reposts, null, state.includeReposts, actions::setIncludeReposts)
            // Only a chronological feed has a place where the reader left off.
            if (state.settings.sort != FeedSort.POPULAR && !following) {
                SwitchRow(Res.string.groups_catch_up_mode, Res.string.groups_catch_up_mode_description, state.settings.catchUp, actions::setCatchUp)
            }
            if (!following) {
                SwitchRow(if (state.inDeck) Res.string.groups_deck_unpin else Res.string.groups_deck_pin, Res.string.groups_deck, state.inDeck) { actions.toggleDeck() }
            }
            Column(Modifier.animateContentSize()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { orders = !orders }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GroupIcons.Sort, contentDescription = null)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(stringResource(sortLabel(state.settings.sort)))
                        Text(stringResource(Res.string.groups_popular_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (orders) GroupIcons.ExpandLess else GroupIcons.ExpandMore, contentDescription = null)
                }
                if (orders) {
                    SortRow(FeedSort.RECENT, state.settings.sort, actions)
                    SortRow(FeedSort.POPULAR, state.settings.sort, actions)
                    SortRow(FeedSort.CUSTOM, state.settings.sort, actions) {
                        IconButton(onClick = {
                            actions.setSort(FeedSort.CUSTOM)
                            onCustom()
                        }) { Icon(Icons.Filled.AddCircle, stringResource(Res.string.groups_custom)) }
                    }
                }
            }
        }
    }
}

fun sortLabel(sort: FeedSort): StringResource = when (sort) {
    FeedSort.RECENT -> Res.string.groups_recent
    FeedSort.POPULAR -> Res.string.groups_popular
    FeedSort.CUSTOM -> Res.string.groups_custom
}

@Composable
private fun SortRow(sort: FeedSort, current: FeedSort, actions: GroupSettingsActions, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = sort == current, role = Role.RadioButton) { actions.setSort(sort) }.padding(start = 32.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = sort == current, onClick = null)
        Text(stringResource(sortLabel(sort)), modifier = Modifier.padding(start = 16.dp).weight(1f))
        trailing()
    }
}

@Composable
private fun SwitchRow(title: StringResource, subtitle: StringResource?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = value, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title))
            if (subtitle != null) Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}
