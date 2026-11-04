package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.groups_create
import com.aimdi.xtanative.ui.generated.resources.groups_groups
import com.aimdi.xtanative.ui.generated.resources.groups_import_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_menu
import com.aimdi.xtanative.ui.generated.resources.groups_pack_imported
import com.aimdi.xtanative.ui.generated.resources.groups_pack_invalid
import com.aimdi.xtanative.ui.generated.resources.groups_search
import com.aimdi.xtanative.ui.generated.resources.groups_search_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_sort_applied
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Where the Subscriptions tab leads; a destination this build has no screen for yet is null. */
class SubscriptionsNavigation(
    val openProfile: (screenName: String) -> Unit,
    val search: (query: String) -> Unit,
    val openGroup: (groupId: String) -> Unit,
    val importFollowing: () -> Unit,
    val importList: () -> Unit,
    val sortUngrouped: () -> Unit,
    val settings: (() -> Unit)? = null,
    val antennas: (() -> Unit)? = null,
    val deck: (() -> Unit)? = null,
    /** A group's Discover, ranked by the reader's AI: the board's sparkle. */
    val discoverGroup: ((groupId: String) -> Unit)? = null,
)

/** A group the edit sheet is open on; [id] null is a new one. */
private data class Editing(val id: String?)

/**
 * XTA's Subscriptions tab (subscriptions.dart): Groups | Subscriptions, a
 * search over the page open, and the management actions in the bar. [onBack]
 * is null where the tab is a page of Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(navigation: SubscriptionsNavigation, onBack: (() -> Unit)? = null) {
    val graph = LocalGraph.current
    val follows = LocalPlugins.current.follows
    val vm: SubscriptionsViewModel = viewModel { SubscriptionsViewModel(graph, follows) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = state.tab.ordinal) { SubscriptionsTab.entries.size }
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var menu by rememberSaveable { mutableStateOf(false) }
    var cleanup by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var picking by remember { mutableStateOf<SubscriptionRow?>(null) }
    val choosePack = rememberPackChooser(onChosen = vm::importPack)
    val savePack = rememberPackSaver()

    LaunchedEffect(pager.currentPage) { vm.select(SubscriptionsTab.entries[pager.currentPage]) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshUnread() }
    LaunchedEffect(vm) {
        vm.notice.collect { notice ->
            val text = when (notice) {
                is SubscriptionsNotice.PackImported -> getString(Res.string.groups_pack_imported, notice.name, notice.count)
                SubscriptionsNotice.PackInvalid -> getString(Res.string.groups_pack_invalid)
            }
            snackbars.showSnackbar(text)
        }
    }
    val message by GroupsMessenger.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        when (val said = message) {
            is GroupsMessage.Placed -> {
                GroupsMessenger.said(said)
                snackbars.showSnackbar(getPluralString(Res.plurals.groups_sort_applied, said.count, said.count))
            }
            null -> Unit
        }
    }
    BackHandling(enabled = state.searching) { vm.closeSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            Column {
                if (state.searching) {
                    SearchBar(state, vm)
                } else {
                    Header(state, onBack, onSearch = vm::openSearch, onCreate = { editing = Editing(null) }, onImport = navigation.importFollowing, onMenu = { menu = true }) { tab ->
                        scope.launch { pager.animateScrollToPage(tab.ordinal) }
                    }
                }
                Hairline()
            }
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
            val content = PaddingValues(bottom = padding.calculateBottomPadding())
            if (!state.loaded) return@HorizontalPager
            when (SubscriptionsTab.entries[page]) {
                SubscriptionsTab.GROUPS -> GroupsPage(
                    state,
                    onOpen = navigation.openGroup,
                    onEdit = { editing = Editing(it) },
                    onCreate = { editing = Editing(null) },
                    onPin = vm.groups::setPinned,
                    onMove = vm::moveGroup,
                    contentPadding = content,
                    onDiscover = navigation.discoverGroup?.takeIf { state.aiReady },
                )
                SubscriptionsTab.PEOPLE -> PeoplePage(
                    state,
                    PeopleActions(
                        open = { row ->
                            when {
                                row.search -> navigation.search(row.id)
                                row.plugin != null -> vm.openInPlugin(row)
                                else -> navigation.openProfile(row.screenName)
                            }
                        },
                        unfollow = vm::unfollow,
                        pickGroups = { picking = it },
                        setInFeed = vm::setInFeed,
                        setPostCap = vm::setPostCap,
                        move = vm::movePerson,
                    ),
                    onImport = navigation.importFollowing,
                    onSearch = { navigation.search("") },
                    contentPadding = content,
                )
            }
        }
    }

    if (menu) {
        SubscriptionsMenuSheet(
            state,
            vm,
            SubscriptionsMenuActions(
                importPack = choosePack,
                sortUngrouped = navigation.sortUngrouped,
                importList = navigation.importList,
                findBroken = { cleanup = true },
                antennas = navigation.antennas,
                deck = navigation.deck,
                settings = navigation.settings,
            ),
            onDismiss = { menu = false },
        )
    }
    editing?.let { edit ->
        GroupEditSheet(state.session, state.allPeople, edit.id, vm.groups, savePack, onDismiss = { editing = null })
    }
    picking?.let { row ->
        val initial = state.groupsOf(row.id)
        GroupMembershipSheet(
            groups = state.orderedGroups,
            selected = initial,
            recent = GroupPicker.recent(state.recentPicks),
            create = vm.groups::create,
            onDone = { chosen ->
                picking = null
                if (chosen != null) vm.groups.setMemberships(row.id, initial, chosen)
            },
        )
    }
    if (cleanup) BrokenSubscriptionsDialog(onDismiss = { cleanup = false })
}

/** The switch where the title stood, beside the actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    state: SubscriptionsUi,
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onMenu: () -> Unit,
    onTab: (SubscriptionsTab) -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
        },
        title = {
            Row(Modifier.height(56.dp)) {
                HeaderTab(GroupIcons.FolderOutlined, stringResource(Res.string.groups_groups), state.tab == SubscriptionsTab.GROUPS) { onTab(SubscriptionsTab.GROUPS) }
                HeaderTab(GroupIcons.PeopleOutlined, stringResource(Res.string.groups_subscriptions), state.tab == SubscriptionsTab.PEOPLE) {
                    onTab(SubscriptionsTab.PEOPLE)
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(Res.string.groups_search)) }
            if (state.tab == SubscriptionsTab.GROUPS) {
                IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, stringResource(Res.string.groups_create)) }
            } else {
                IconButton(onClick = onImport) { Icon(GroupIcons.PersonAddOutlined, stringResource(Res.string.groups_import_subscriptions)) }
            }
            IconButton(onClick = onMenu) { Icon(GroupIcons.Tune, stringResource(Res.string.groups_menu)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** One section of the switch: its icon and name, underlined while it is the one open. */
@Composable
private fun HeaderTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxHeight().selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

/** The bar while searching: the field, focused, and a way back that closes the search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(state: SubscriptionsUi, vm: SubscriptionsViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        navigationIcon = { IconButton(onClick = vm::closeSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
        title = {
            TextField(
                value = state.query,
                onValueChange = vm::search,
                singleLine = true,
                placeholder = { Text(stringResource(if (state.tab == SubscriptionsTab.GROUPS) Res.string.groups_search else Res.string.groups_search_subscriptions)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
