package com.aimdi.xtanative.ui.group

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.feed.PluginPostRow
import com.aimdi.xtanative.ui.feed.SourcedPost
import com.aimdi.xtanative.ui.feed.rememberRunningPlugins
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.group_discovery_suggested_for
import com.aimdi.xtanative.ui.generated.resources.groups_add_member_failed
import com.aimdi.xtanative.ui.generated.resources.groups_ai_fallback
import com.aimdi.xtanative.ui.generated.resources.groups_discover
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_add_to_group
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_added
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_ai
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_and_more
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_coverage
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_description
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_empty
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_feedback_saved
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_followed_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_hide
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_less_like
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_mentioned_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_more_like
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_open_post
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_other_groups
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_partial
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_quoted_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_related_to
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_replied_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_reposted_by
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_reset
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_scan_more
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_show_post
import com.aimdi.xtanative.ui.generated.resources.groups_discovery_suggested_for
import com.aimdi.xtanative.ui.generated.resources.groups_menu
import com.aimdi.xtanative.ui.generated.resources.groups_more_info
import com.aimdi.xtanative.ui.generated.resources.groups_open_profile
import com.aimdi.xtanative.ui.generated.resources.groups_retry
import com.aimdi.xtanative.ui.generated.resources.groups_sort_ai_note
import com.aimdi.xtanative.ui.generated.resources.groups_undo
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.networkProblemText
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.subscriptions.GroupBoardModel
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.GroupMembershipSheet
import com.aimdi.xtanative.ui.subscriptions.GroupPicker
import com.aimdi.xtanative.ui.subscriptions.aiConfigOf
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Discover's view model for [groupId], kept while the screen that shows it is. */
@Composable
internal fun discoveryViewModel(groupId: String): DiscoveryViewModel {
    val graph = LocalGraph.current
    val plugins = LocalPlugins.current
    return viewModel(key = "discovery-$groupId") { DiscoveryViewModel(graph, groupId, plugins.discoveries, plugins.follows) }
}

/** Whether the reader set up an AI provider, which "Rank with AI" asks. */
@Composable
fun rememberAiReady(): Boolean {
    val settings by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    return settings?.let { aiConfigOf(it).isConfigured } == true
}

/** "Rank with AI" as a bar's or a tile's button: the sparkle. */
@Composable
fun RankWithAiButton(modifier: Modifier = Modifier, iconSize: Dp = 24.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(GroupIcons.AutoAwesome, stringResource(Res.string.groups_discovery_ai), modifier = Modifier.size(iconSize))
    }
}

/**
 * A group's Discover on a screen of its own (group_discovery_screen.dart's
 * GroupDiscoveryScreen): what the board's sparkle opens, ranked by the
 * reader's AI as soon as there is something to rank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDiscoveryScreen(groupId: String, actions: PostActions, onBack: () -> Unit) {
    val session by LocalGraph.current.sessions.state.collectAsStateWithLifecycle()
    val name = session?.groups?.firstOrNull { it.id == groupId }?.name.orEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.groups_discover) + " · " + name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            DiscoveryPane(groupId, name, actions, showAiChip = true, rankRequest = 1)
        }
    }
}

/**
 * Discover inside a group (group_discovery_screen.dart's pane): how many
 * members were read and a way to read more, then the accounts they point at
 * that the reader does not follow, best first, each with why, the post that
 * shows it, an add-to-group button and a menu. [showAiChip] offers "Rank with
 * AI" in the header (the group's own screen has it in its bar); each time
 * [rankRequest] grows, the AI is asked, as soon as there is something to rank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryPane(groupId: String, groupName: String, actions: PostActions, showAiChip: Boolean = false, rankRequest: Int = 0) {
    val vm = discoveryViewModel(groupId)
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    NoticesOf(vm, snackbars)
    LaunchedEffect(vm, rankRequest) { vm.rankWithAi(rankRequest) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var info by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Candidate?>(null) }
    val aiChip = showAiChip && rememberAiReady()
    val header = remember(vm, aiChip) { DiscoveryHeaderActions(vm, aiChip, onInfo = { info = true }) }
    val rows = remember(vm, actions, groupName) { RowActions(vm, actions, groupName, onOtherGroups = { picking = it }) }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> DiscoverySkeleton()
                state.candidates.isEmpty() && !state.scanning -> DiscoveryEmpty(state, header)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "header") { DiscoveryHeader(state, header, scanMore = true) }
                    items(state.candidates, key = { it.key }) { candidate ->
                        val open = candidate.key in expanded
                        CandidateRow(candidate, state, open, rows) { expanded = if (open) expanded - candidate.key else expanded + candidate.key }
                    }
                }
            }
        }
        if (state.scanning && !state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
    if (info) InfoDialog { info = false }
    picking?.let { candidate -> OtherGroupsSheet(vm, candidate) { picking = null } }
}

/** What the header's controls do. */
private class DiscoveryHeaderActions(val vm: DiscoveryViewModel, val aiChip: Boolean, val onInfo: () -> Unit)

/** What a row's controls do: [groupName] is the group they add to. */
private class RowActions(val vm: DiscoveryViewModel, val posts: PostActions, val groupName: String, val onOtherGroups: (Candidate) -> Unit)

/** "Suggestions updated" with its undo, and an account that could not be added. */
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
private fun DiscoveryEmpty(state: DiscoveryUi, header: DiscoveryHeaderActions) {
    val nothingRead = state.failures.isNotEmpty() && state.coverage.read == 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DiscoveryHeader(state, header, scanMore = false)
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(DiscoveryIcons.ExploreOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(
                stringResource(if (nothingRead) Res.string.groups_discovery_partial else Res.string.groups_discovery_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = header.vm::scanMore) { Text(stringResource(Res.string.groups_discovery_scan_more)) }
        }
    }
}

/** How many members were read, "Scan more" (unless the empty pane offers it), the menu, the AI's line and each source that failed. */
@Composable
private fun DiscoveryHeader(state: DiscoveryUi, header: DiscoveryHeaderActions, scanMore: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.groups_discovery_coverage, state.coverage.total, state.coverage.read, state.coverage.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (scanMore) {
                TextButton(onClick = header.vm::scanMore, enabled = !state.scanning) { Text(stringResource(Res.string.groups_discovery_scan_more)) }
            }
            HeaderMenu(state, header)
        }
        if (header.aiChip || state.usedAi || state.aiFailed) AiLine(state, header)
        state.failures.forEach { (source, problem) -> FailureRow(source, problem, header.vm::load) }
        Hairline()
    }
}

/** "Rank with AI", and what the AI did: the list in its order, or the device's own when it gave nothing usable. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AiLine(state: DiscoveryUi, header: DiscoveryHeaderActions) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (header.aiChip) {
            AssistChip(
                onClick = header.vm::rankWithAi,
                enabled = !state.scanning,
                label = { Text(stringResource(Res.string.groups_discovery_ai)) },
                leadingIcon = { Icon(GroupIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        val note = when {
            state.usedAi -> Res.string.groups_sort_ai_note
            state.aiFailed -> Res.string.groups_ai_fallback
            else -> null
        }
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** A source that could not be read, named by its mark, with why and a retry. */
@Composable
private fun FailureRow(source: String, problem: Problem, onRetry: () -> Unit) {
    val plugins = rememberRunningPlugins()
    val language = currentLanguage()
    val why = if (source == Discovery.X) problemText(problem) else networkProblemText(problem, plugins[source]?.let { runCatching { it.title(language) }.getOrNull() } ?: source)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SourceMark(source, plugins, 16.dp)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(stringResource(Res.string.groups_discovery_partial), style = MaterialTheme.typography.bodySmall)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.groups_retry)) }
    }
}

@Composable
private fun HeaderMenu(state: DiscoveryUi, header: DiscoveryHeaderActions) {
    var open by remember { mutableStateOf(false) }
    fun pick(action: () -> Unit) {
        open = false
        action()
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.groups_menu)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(GroupIcons.InfoOutline, stringResource(Res.string.groups_more_info)) { pick(header.onInfo) }
            if (state.canUndo) MenuRow(GroupIcons.Undo, stringResource(Res.string.groups_undo)) { pick(header.vm::undo) }
            MenuRow(DiscoveryIcons.RestartAlt, stringResource(Res.string.groups_discovery_reset), enabled = state.hasFeedback) { pick(header.vm::reset) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, enabled = enabled, onClick = onClick)
}

/** What Discover is, in XTA's words. */
@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_discover)) },
        text = { Text(stringResource(Res.string.groups_discovery_description)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

/** "Other groups…": the membership sheet with this group ticked, its account followed first as a choice is made. */
@Composable
private fun OtherGroupsSheet(vm: DiscoveryViewModel, candidate: Candidate, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown = session ?: return
    val prefs = settings ?: return
    val initial = remember(candidate.key) { vm.groupsOf(candidate) }
    GroupMembershipSheet(
        groups = GroupBoardModel.ordered(shown.groups, prefs[SubscriptionPrefs.groupsOrder], prefs[SubscriptionPrefs.groupsAscending]),
        selected = initial,
        recent = GroupPicker.recent(prefs[SubscriptionPrefs.groupsRecentPicks]),
        create = vm::createGroup,
        onDone = { chosen ->
            onDismiss()
            if (chosen != null) vm.putInGroups(candidate, initial, chosen)
        },
    )
}

/** One suggestion: its face, name and network, handle, the add button and the menu; why it is here; what shows it, whole once tapped. */
@Composable
private fun CandidateRow(candidate: Candidate, state: DiscoveryUi, open: Boolean, rows: RowActions, onToggle: () -> Unit) {
    val plugins = rememberRunningPlugins()
    val openProfile = rememberOpenProfile(candidate, rows)
    val added = candidate.key in state.added
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = if (open) onToggle else openProfile).padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(candidate.avatarUrl, 40.dp)
            Spacer(Modifier.width(12.dp))
            Identity(candidate, plugins, added, Modifier.weight(1f))
            AddButton(rows.groupName, added, adding = candidate.key in state.adding) { rows.vm.add(candidate) }
            RowMenu(candidate, rows, openProfile)
        }
        reasonText(candidate, plugins)?.let { reason ->
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TEXT_INSET, end = 16.dp, top = 4.dp),
            )
        }
        if (open) Shown(candidate, plugins, rows.posts) else Snippet(candidate, onToggle)
        if (!open || candidate.post == null) Hairline()
    }
}

/** Text under the name lines up with the name, past the avatar. */
private val TEXT_INSET = 68.dp

/** Where a candidate's profile opens: X's screen, else its plugin's; where a plugin named none, the post that shows it, as the app opens links. */
@Composable
private fun rememberOpenProfile(candidate: Candidate, rows: RowActions): () -> Unit {
    val plugins = LocalPlugins.current
    return remember(candidate.key, rows) {
        {
            val route = rows.vm.routeOf(candidate)
            when {
                candidate.source == Discovery.X -> rows.posts.openProfile(candidate.handle)
                route != null -> plugins.navigator.go(Destination.PluginRoute(candidate.source, route))
                else -> candidate.postUrl.takeIf { it.isNotEmpty() }?.let(rows.posts.openLink)
            }
        }
    }
}

@Composable
private fun Identity(candidate: Candidate, plugins: Map<String, XtaPlugin>, added: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                candidate.name.ifEmpty { candidate.handle },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            SourceMark(candidate.source, plugins, 14.dp)
        }
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

/** The mark of the network a candidate or a failure belongs to. */
@Composable
private fun SourceMark(source: String, plugins: Map<String, XtaPlugin>, size: Dp) {
    val plugin = plugins[source]
    when {
        source == Discovery.X -> Icon(ReaderIcons.XMark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size))
        plugin != null -> Icon(plugin.icon, contentDescription = null, tint = plugin.brandColor, modifier = Modifier.size(size))
        else -> Spacer(Modifier.size(size))
    }
}

@Composable
private fun AddButton(groupName: String, added: Boolean, adding: Boolean, onAdd: () -> Unit) {
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

/** "Reposted by @a, @b and 3 more", "Followed by 4 members": the signal most of its supporters share. */
@Composable
private fun reasonText(candidate: Candidate, plugins: Map<String, XtaPlugin>): String? {
    val reason = remember(candidate) { DiscoveryReason.of(candidate) } ?: return null
    if (reason.signal == DiscoverySignal.FOLLOWED) return pluralStringResource(Res.plurals.groups_discovery_followed_by, reason.count, reason.count)
    val names = reason.names.joinToString(", ")
    val language = currentLanguage()
    val text = when (reason.signal) {
        DiscoverySignal.REPOSTED -> stringResource(Res.string.groups_discovery_reposted_by, names)
        DiscoverySignal.QUOTED -> stringResource(Res.string.groups_discovery_quoted_by, names)
        DiscoverySignal.REPLIED -> stringResource(Res.string.groups_discovery_replied_by, names)
        DiscoverySignal.MENTIONED -> stringResource(Res.string.groups_discovery_mentioned_by, names)
        DiscoverySignal.SUGGESTED -> if (candidate.source == BLUESKY) stringResource(Res.string.group_discovery_suggested_for, names) else stringResource(
            Res.string.groups_discovery_suggested_for,
            plugins[candidate.source]?.let { runCatching { it.title(language) }.getOrNull() } ?: candidate.source,
            names,
        )
        DiscoverySignal.RELATED -> stringResource(Res.string.groups_discovery_related_to, names)
        DiscoverySignal.FOLLOWED -> ""
    }
    return if (reason.more > 0) text + " " + pluralStringResource(Res.plurals.groups_discovery_and_more, reason.more, reason.more) else text
}

/** Two lines of what shows the account; a tap shows the post itself. */
@Composable
private fun Snippet(candidate: Candidate, onToggle: () -> Unit) {
    val snippet = candidate.snippet
    if (snippet.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        return
    }
    val label = stringResource(Res.string.groups_discovery_show_post)
    Text(
        snippet,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
            .padding(start = TEXT_INSET, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

/** The post that shows the account, drawn as its network draws it; the whole text where there is none. */
@Composable
private fun Shown(candidate: Candidate, plugins: Map<String, XtaPlugin>, actions: PostActions) {
    when (val shown = candidate.post) {
        is ShownPost.X -> ThreadRows(XThread(shown.post.id, listOf(shown.post)), actions)
        is ShownPost.Network -> PluginPostRow(SourcedPost(candidate.source, shown.post), plugins[candidate.source])
        null -> Text(
            candidate.snippet,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = TEXT_INSET, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

/** More like this, less like this, hide, other groups, the profile, and the post (group_discovery_screen.dart's row menu). */
@Composable
private fun RowMenu(candidate: Candidate, rows: RowActions, openProfile: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun pick(action: () -> Unit) {
        open = false
        action()
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.groups_menu)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(DiscoveryIcons.ThumbUpOutlined, stringResource(Res.string.groups_discovery_more_like)) { pick { rows.vm.judge(candidate, Judgement.MORE) } }
            MenuRow(DiscoveryIcons.ThumbDownOutlined, stringResource(Res.string.groups_discovery_less_like)) { pick { rows.vm.judge(candidate, Judgement.LESS) } }
            MenuRow(GroupIcons.VisibilityOffOutlined, stringResource(Res.string.groups_discovery_hide)) { pick { rows.vm.judge(candidate, Judgement.HIDE) } }
            MenuRow(GroupIcons.FolderOutlined, stringResource(Res.string.groups_discovery_other_groups)) { pick { rows.onOtherGroups(candidate) } }
            MenuRow(Icons.Outlined.Person, stringResource(Res.string.groups_open_profile)) { pick(openProfile) }
            if (candidate.hasPost) {
                MenuRow(GroupIcons.OpenInNew, stringResource(Res.string.groups_discovery_open_post)) { pick { rows.posts.openLink(candidate.postUrl) } }
            }
        }
    }
}

/** Account-shaped placeholders for the first load (XTA's _DiscoverySkeleton): avatar, name, reason, snippet. */
@Composable
private fun DiscoverySkeleton() {
    val reduced = LocalReducedMotion.current
    val pulse = rememberInfiniteTransition(label = "discovery-skeleton")
    val animated by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bone")
    val alpha = if (reduced) 0.75f else animated
    val bone = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxSize().alpha(alpha)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(Modifier.size(40.dp).background(bone, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = 140.dp, height = 12.dp).background(bone, RoundedCornerShape(4.dp)))
                    Box(Modifier.size(width = 200.dp, height = 10.dp).background(bone, RoundedCornerShape(4.dp)))
                    Box(Modifier.fillMaxWidth().height(12.dp).background(bone, RoundedCornerShape(4.dp)))
                    Box(Modifier.size(width = 180.dp, height = 12.dp).background(bone, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

/** The one network whose discovery suggests accounts, XTA saying its name in the reason. */
private const val BLUESKY = "bluesky"
