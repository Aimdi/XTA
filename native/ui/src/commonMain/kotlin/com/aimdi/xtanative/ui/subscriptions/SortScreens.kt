package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.groups_ai_fallback
import com.aimdi.xtanative.ui.generated.resources.groups_create
import com.aimdi.xtanative.ui.generated.resources.groups_create_button
import com.aimdi.xtanative.ui.generated.resources.groups_finish
import com.aimdi.xtanative.ui.generated.resources.groups_name
import com.aimdi.xtanative.ui.generated.resources.groups_no_results
import com.aimdi.xtanative.ui.generated.resources.groups_open_profile
import com.aimdi.xtanative.ui.generated.resources.groups_skip
import com.aimdi.xtanative.ui.generated.resources.groups_sort_ai
import com.aimdi.xtanative.ui.generated.resources.groups_sort_ai_note
import com.aimdi.xtanative.ui.generated.resources.groups_sort_applied
import com.aimdi.xtanative.ui.generated.resources.groups_sort_apply
import com.aimdi.xtanative.ui.generated.resources.groups_sort_assign
import com.aimdi.xtanative.ui.generated.resources.groups_sort_description
import com.aimdi.xtanative.ui.generated.resources.groups_sort_empty
import com.aimdi.xtanative.ui.generated.resources.groups_sort_failed
import com.aimdi.xtanative.ui.generated.resources.groups_sort_into
import com.aimdi.xtanative.ui.generated.resources.groups_sort_leftover
import com.aimdi.xtanative.ui.generated.resources.groups_sort_local
import com.aimdi.xtanative.ui.generated.resources.groups_sort_one_by_one
import com.aimdi.xtanative.ui.generated.resources.groups_sort_one_progress
import com.aimdi.xtanative.ui.generated.resources.groups_sort_several
import com.aimdi.xtanative.ui.generated.resources.groups_sort_skipped
import com.aimdi.xtanative.ui.generated.resources.groups_sort_skipped_round
import com.aimdi.xtanative.ui.generated.resources.groups_sort_suggest
import com.aimdi.xtanative.ui.generated.resources.groups_sort_title
import com.aimdi.xtanative.ui.generated.resources.groups_sort_ungrouped
import com.aimdi.xtanative.ui.generated.resources.groups_undo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Reviewing a plan for the accounts in no group (group_ungrouped_screen.dart).
 * [onSortOneByOne] opens the sorter on the accounts it leaves; [onApplied]
 * leaves once the plan is applied, for the sorter when some are left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortUngroupedScreen(onBack: () -> Unit, onSortOneByOne: (List<String>) -> Unit, onApplied: (leftovers: List<String>) -> Unit) {
    val graph = LocalGraph.current
    val follows = LocalPlugins.current.follows
    val vm: SortUngroupedViewModel = viewModel { SortUngroupedViewModel(graph, follows) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var away by rememberSaveable { mutableStateOf(false) }
    // Back from sorting by hand: plan again from what is left.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (away) {
            away = false
            vm.plan()
        }
    }
    LaunchedEffect(state.failed) { if (state.failed) snackbars.showSnackbar(getString(Res.string.groups_sort_failed)) }
    val sortOneByOne = {
        away = true
        onSortOneByOne(state.plan.leftoverIds)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.groups_sort_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.aiReady) {
                FilledTonalButton(onClick = { vm.plan(useAi = true) }, enabled = !state.applying, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    Icon(GroupIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.groups_sort_ai), modifier = Modifier.padding(start = 8.dp))
                }
            }
            PlanBody(state, Modifier.weight(1f), sortOneByOne)
            val footer = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            when {
                !state.plan.isEmpty -> Button(
                    onClick = {
                        scope.launch {
                            val placed = vm.apply() ?: return@launch
                            GroupsMessenger.leave(GroupsMessage.Placed(placed))
                            onApplied(state.plan.leftoverIds)
                        }
                    },
                    enabled = !state.applying,
                    modifier = footer,
                ) {
                    if (state.applying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.groups_sort_apply))
                }
                state.plan.leftoverIds.isNotEmpty() -> Button(onClick = sortOneByOne, enabled = !state.applying, modifier = footer) {
                    Text(stringResource(Res.string.groups_sort_one_by_one))
                }
            }
        }
    }
}

@Composable
private fun PlanBody(state: SortUi, modifier: Modifier, onSortOneByOne: () -> Unit) {
    val plan = state.plan
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.groups_sort_description))
        Spacer(Modifier.height(12.dp))
        if (plan.isEmpty) {
            if (state.aiFailed) Text(stringResource(Res.string.groups_ai_fallback))
            Text(stringResource(if (plan.leftoverIds.isEmpty()) Res.string.groups_sort_empty else Res.string.groups_no_results))
            if (plan.leftoverIds.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.groups_sort_leftover))
                Text(plan.leftoverIds.joinToString(", ") { state.handleOf(it) })
            }
            return@Column
        }
        Text(stringResource(if (plan.usedAi) Res.string.groups_sort_ai_note else Res.string.groups_sort_local))
        if (state.aiFailed) Text(stringResource(Res.string.groups_ai_fallback))
        if (plan.assign.isNotEmpty()) {
            Section(Res.string.groups_sort_assign)
            plan.assign.forEach { row ->
                Text(stringResource(Res.string.groups_sort_into, state.handleOf(row.accountId), state.groupNameOf(row.groupId)), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (plan.suggest.isNotEmpty()) {
            Section(Res.string.groups_sort_suggest)
            plan.suggest.forEach { group ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(group.name)
                    Text(
                        group.accountIds.joinToString(", ") { "@" + state.handleOf(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (plan.leftoverIds.isNotEmpty()) {
            Section(Res.string.groups_sort_leftover)
            Text(plan.leftoverIds.joinToString(", ") { "@" + state.handleOf(it) })
            TextButton(onClick = onSortOneByOne) {
                Icon(GroupIcons.StyleOutlined, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.groups_sort_one_by_one), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Section(title: org.jetbrains.compose.resources.StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
}

/**
 * Sorting [accountIds] one at a time (group_ungrouped_sorter_screen.dart): the
 * account's card above, the groups below, where they stay put from one
 * account to the next. [openProfile] shows who it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UngroupedSorterScreen(accountIds: List<String>, openProfile: (String) -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val follows = LocalPlugins.current.follows
    val vm: UngroupedSorterViewModel = viewModel { UngroupedSorterViewModel(graph, accountIds, follows) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var several by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val sorter = state.sorter
    LaunchedEffect(state.failed) {
        if (state.failed) {
            snackbars.showSnackbar(getString(Res.string.groups_sort_failed))
            vm.failureSeen()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.groups_sort_ungrouped)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                actions = {
                    if (!sorter.finished) {
                        Text(
                            stringResource(Res.string.groups_sort_one_progress, sorter.position + 1, sorter.accounts.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = vm::undo, enabled = sorter.choices.isNotEmpty()) {
                        Icon(GroupIcons.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.groups_undo), modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (!sorter.finished) TextButton(onClick = vm::skip) { Text(stringResource(Res.string.groups_skip)) }
                }
            }
        },
    ) { padding ->
        val account = state.current
        // An account unfollowed since the round began is passed over.
        LaunchedEffect(sorter.position, state.accounts.isEmpty()) {
            if (!sorter.finished && account == null && state.accounts.isNotEmpty()) vm.skip()
        }
        if (!sorter.finished && account == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (sorter.finished || account == null) {
            SorterEnd(sorter, vm::sortSkipped, onDone, Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                item(key = "card") {
                    AnimatedContent(
                        targetState = sorter.position,
                        transitionSpec = {
                            val side = if (sorter.backward) -1 else 1
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { side * it / 12 }) togetherWith
                                (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -side * it / 12 })
                        },
                    ) { _ ->
                        AccountCard(account) { vm.open(account, openProfile) }
                    }
                }
                item(key = "groups") {
                    GroupChips(state, onGroup = { vm.put(listOf(it)) }, onSeveral = { several = true }, onCreate = { naming = true })
                }
            }
        }
    }
    val current = sorter.current
    if (several && current != null) {
        GroupMembershipSheet(
            groups = state.groups,
            selected = emptySet(),
            recent = GroupPicker.recent(state.recentPicks),
            create = vm.groups::create,
            onDone = { chosen ->
                several = false
                if (!chosen.isNullOrEmpty()) {
                    vm.groups.rememberPicks(emptySet(), chosen)
                    vm.put(chosen.toList())
                }
            },
        )
    }
    if (naming) {
        GroupNameDialog(onCreate = {
            naming = false
            vm.create(it)
        }, onDismiss = { naming = false })
    }
}

/** Who is being sorted: their face (a plugin's with its network's mark), name and the line under it, and a way to their page. */
@Composable
private fun AccountCard(row: SubscriptionRow, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SubscriptionFace(row, 72.dp)
        Text(row.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        Text(subscriptionSubtitle(row), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        TextButton(onClick = onOpen, modifier = Modifier.padding(top = 4.dp)) {
            Icon(GroupIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.groups_open_profile), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The groups with their marks, then Several groups and Create group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChips(state: SorterUi, onGroup: (String) -> Unit, onSeveral: () -> Unit, onCreate: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.groups.forEach { group ->
            AssistChip(
                onClick = { onGroup(group.id) },
                label = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
                leadingIcon = { GroupMark(group, 24.dp) },
            )
        }
        if (state.groups.size > 1) {
            AssistChip(onClick = onSeveral, label = { Text(stringResource(Res.string.groups_sort_several)) }, leadingIcon = {
                Icon(GroupIcons.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
            })
        }
        AssistChip(onClick = onCreate, label = { Text(stringResource(Res.string.groups_create)) }, leadingIcon = {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        })
    }
}

/** How the round went, with Finish and, when some were skipped, a round for those. */
@Composable
private fun SorterEnd(sorter: SorterState, onSortSkipped: () -> Unit, onFinish: () -> Unit, modifier: Modifier) {
    val placed = sorter.placedCount
    val skipped = sorter.skipped.size
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(GroupIcons.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        if (placed == 0 && skipped == 0) Text(stringResource(Res.string.groups_sort_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (placed > 0) Text(pluralStringResource(Res.plurals.groups_sort_applied, placed, placed), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (skipped > 0) {
            Text(pluralStringResource(Res.plurals.groups_sort_skipped, skipped, skipped), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish) { Text(stringResource(Res.string.groups_finish)) }
        if (skipped > 0) TextButton(onClick = onSortSkipped, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(Res.string.groups_sort_skipped_round)) }
    }
}

/** Asks a new group's name; [onCreate] hears it trimmed. */
@Composable
fun GroupNameDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.groups_create)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(Res.string.groups_name)) })
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.groups_create_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
