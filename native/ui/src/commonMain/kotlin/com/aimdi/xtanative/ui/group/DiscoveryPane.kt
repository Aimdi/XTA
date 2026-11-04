package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_add_member_failed
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_add_to_group
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_added
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_coverage
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_empty
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_feedback_saved
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_followed_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_hide
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_less_like
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_more_like
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_partial
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_reset
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_scan_more
import com.aimdi.xtanative.ui.generated.resources.groups_menu
import com.aimdi.xtanative.ui.generated.resources.groups_open_profile
import com.aimdi.xtanative.ui.generated.resources.groups_retry
import com.aimdi.xtanative.ui.generated.resources.groups_undo
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Discover's view model for [groupId], kept while the group's screen is. */
@Composable
internal fun discoveryViewModel(groupId: String): DiscoveryViewModel {
    val graph = LocalGraph.current
    return viewModel(key = "discovery-$groupId") { DiscoveryViewModel(graph, groupId) }
}

/**
 * Discover inside a group's feed (group_discovery_screen.dart's pane): how
 * many members' lists were read and a way to read more, then the accounts
 * they follow that the reader does not, best first, each with who follows it,
 * its bio, an add-to-group button and a menu of feedback and its profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryPane(groupId: String, groupName: String, actions: PostActions) {
    val vm = discoveryViewModel(groupId)
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    NoticesOf(vm, snackbars)
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.padding(top = 48.dp)) }
                state.candidates.isEmpty() && !state.scanning -> DiscoveryEmpty(state, vm)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "header") { DiscoveryHeader(state, vm, scanMore = true) }
                    items(state.candidates, key = { it.key }) { candidate ->
                        val open = candidate.key in expanded
                        CandidateRow(candidate, groupName, state, open, actions, vm) {
                            expanded = if (open) expanded - candidate.key else expanded + candidate.key
                        }
                    }
                }
            }
        }
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
}

/** "Suggestions updated" with its undo, and a member that could not be added. */
@Composable
private fun NoticesOf(vm: DiscoveryViewModel, snackbars: SnackbarHostState) {
    LaunchedEffect(vm) {
        vm.notice.collect { notice ->
            when (notice) {
                DiscoveryNotice.FEEDBACK_SAVED -> {
                    val result = snackbars.showSnackbar(getString(Res.string.groups_discovery_feedback_saved), getString(Res.string.groups_undo), duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) vm.undo()
                }
                DiscoveryNotice.ADD_FAILED -> snackbars.showSnackbar(getString(Res.string.groups_add_member_failed))
            }
        }
    }
}

/** Nothing to suggest: why, and "Scan more"; nothing read at all is a failure to report. */
@Composable
private fun DiscoveryEmpty(state: DiscoveryUi, vm: DiscoveryViewModel) {
    val nothingRead = state.failure != null && state.read == 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DiscoveryHeader(state, vm, scanMore = false)
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(DiscoveryIcons.ExploreOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(
                stringResource(if (nothingRead) Res.string.groups_discovery_partial else Res.string.groups_discovery_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = vm::scanMore) { Text(stringResource(Res.string.groups_discovery_scan_more)) }
        }
    }
}

/** How many members' lists were read, "Scan more" (unless the empty state offers it), the menu, and a failure. */
@Composable
private fun DiscoveryHeader(state: DiscoveryUi, vm: DiscoveryViewModel, scanMore: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.groups_discovery_coverage, state.total, state.read, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (scanMore) {
                TextButton(onClick = vm::scanMore, enabled = !state.scanning) { Text(stringResource(Res.string.groups_discovery_scan_more)) }
            }
            HeaderMenu(state, vm)
        }
        if (state.failure != null && (state.read > 0 || state.candidates.isNotEmpty())) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.groups_discovery_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::load) { Text(stringResource(Res.string.groups_retry)) }
            }
        }
        Hairline()
    }
}

@Composable
private fun HeaderMenu(state: DiscoveryUi, vm: DiscoveryViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.groups_menu)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.canUndo) {
                MenuRow(GroupIcons.Undo, stringResource(Res.string.groups_undo)) {
                    open = false
                    vm.undo()
                }
            }
            MenuRow(DiscoveryIcons.RestartAlt, stringResource(Res.string.groups_discovery_reset), enabled = state.hasFeedback) {
                open = false
                vm.reset()
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, enabled = enabled, onClick = onClick)
}

/** One suggestion: its face, name and handle, the add button and the menu; who follows it; its bio, whole once tapped. */
@Composable
private fun CandidateRow(candidate: Candidate, groupName: String, state: DiscoveryUi, open: Boolean, actions: PostActions, vm: DiscoveryViewModel, onToggle: () -> Unit) {
    val added = candidate.key in state.added
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { actions.openProfile(candidate.handle) }.padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(candidate.avatarUrl, 40.dp)
            Spacer(Modifier.width(12.dp))
            Identity(candidate, added, Modifier.weight(1f))
            AddButton(candidate, groupName, added, adding = candidate.key in state.adding) { vm.add(candidate) }
            RowMenu(candidate, actions, vm)
        }
        Column(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp)) {
            Text(
                pluralStringResource(Res.plurals.groups_discovery_followed_by, candidate.memberCount, candidate.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.snippet.isNotEmpty()) {
                Text(
                    candidate.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onToggle),
                )
            }
        }
        Hairline()
    }
}

@Composable
private fun Identity(candidate: Candidate, added: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(candidate.name.ifEmpty { candidate.handle }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "@" + candidate.handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (added) {
                val accent = MaterialTheme.colorScheme.primary
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 8.dp).size(14.dp))
                Text(stringResource(Res.string.groups_discovery_added), style = MaterialTheme.typography.bodySmall, color = accent, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun AddButton(candidate: Candidate, groupName: String, added: Boolean, adding: Boolean, onAdd: () -> Unit) {
    if (added) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CheckCircle, stringResource(Res.string.groups_discovery_added), tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    FilledTonalIconButton(onClick = onAdd, enabled = !adding) {
        if (adding) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(DiscoveryIcons.GroupAddOutlined, stringResource(Res.string.groups_discovery_add_to_group, groupName))
        }
    }
}

/** More like this, less like this, hide, and the profile (group_discovery_screen.dart's row menu). */
@Composable
private fun RowMenu(candidate: Candidate, actions: PostActions, vm: DiscoveryViewModel) {
    var open by remember { mutableStateOf(false) }
    fun pick(action: () -> Unit) {
        open = false
        action()
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.groups_menu)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(DiscoveryIcons.ThumbUpOutlined, stringResource(Res.string.groups_discovery_more_like)) { pick { vm.judge(candidate, Judgement.MORE) } }
            MenuRow(DiscoveryIcons.ThumbDownOutlined, stringResource(Res.string.groups_discovery_less_like)) { pick { vm.judge(candidate, Judgement.LESS) } }
            MenuRow(GroupIcons.VisibilityOffOutlined, stringResource(Res.string.groups_discovery_hide)) { pick { vm.judge(candidate, Judgement.HIDE) } }
            MenuRow(Icons.Outlined.Person, stringResource(Res.string.groups_open_profile)) { pick { actions.openProfile(candidate.handle) } }
        }
    }
}
