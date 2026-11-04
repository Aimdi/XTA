package com.aimdi.xtanative.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.GroupPack
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.GroupsLayout
import com.aimdi.xtanative.store.settings.GroupsOrder
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import com.aimdi.xtanative.store.settings.SubscriptionsOrder
import com.aimdi.xtanative.ui.group.FeedPrefs
import com.aimdi.xtanative.ui.group.GroupFeedRules
import com.aimdi.xtanative.ui.group.ReadMarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SubscriptionsTab { GROUPS, PEOPLE }

/** The Subscriptions tab at one moment: its two pages, the search over them and how each is ordered and laid out. */
data class SubscriptionsUi(
    val loaded: Boolean = false,
    val session: Session = Session(),
    val tab: SubscriptionsTab = SubscriptionsTab.GROUPS,
    val searching: Boolean = false,
    val query: String = "",
    /** The people page: every account (X's and the plugins') and saved search followed, ordered, then filtered by the search. */
    val people: List<SubscriptionRow> = emptyList(),
    /** Everything followed, in the page's order, whatever the search: the members a group can pick from. */
    val allPeople: List<SubscriptionRow> = emptyList(),
    val followedCount: Int = 0,
    val board: GroupSections = GroupSections(emptyList(), emptyList()),
    val groupCount: Int = 0,
    val layout: GroupsLayout = GroupsLayout.BOARD,
    val columns: Int = 2,
    val compact: Boolean = false,
    val groupsOrder: GroupsOrder = GroupsOrder.NAME,
    val groupsAscending: Boolean = true,
    val peopleOrder: SubscriptionsOrder = SubscriptionsOrder.NAME,
    val peopleAscending: Boolean = true,
    val recentPicks: String = "",
    /** The reader configured an AI, which sorts ungrouped accounts. */
    val aiReady: Boolean = false,
) {
    val canDragPeople: Boolean get() = query.isBlank()

    val canDragGroups: Boolean get() = GroupBoardModel.canReorder(groupsOrder, query)

    /** The groups [rowId] is in. */
    fun groupsOf(rowId: String): Set<String> = session.groups.filter { rowId in it.memberIds }.mapTo(LinkedHashSet()) { it.id }

    /** The groups in the order the board lists them, for the pickers. */
    val orderedGroups: List<FollowGroup> get() = GroupBoardModel.ordered(session.groups, groupsOrder, groupsAscending)
}

/** What the screen says once, in a snackbar. */
sealed interface SubscriptionsNotice {
    data class PackImported(val name: String, val count: Int) : SubscriptionsNotice

    data object PackInvalid : SubscriptionsNotice
}

/**
 * XTA's Subscriptions tab (subscriptions.dart, _groups.dart, _list.dart,
 * subscriptions_menu_sheet.dart): the groups board and the people followed,
 * read from the session, the settings and the running plugins ([follows]),
 * and everything their menus change.
 */
class SubscriptionsViewModel(private val graph: AppGraph, private val follows: PluginFollows = PluginFollows.None) : ViewModel() {
    val groups = GroupActions(graph, viewModelScope)

    private val view = MutableStateFlow(View())
    private val unread = MutableStateFlow<Set<String>>(emptySet())
    private val notices = MutableSharedFlow<SubscriptionsNotice>(extraBufferCapacity = 4)
    val notice: SharedFlow<SubscriptionsNotice> = notices.asSharedFlow()

    private data class View(val tab: SubscriptionsTab = SubscriptionsTab.GROUPS, val searching: Boolean = false, val query: String = "")

    val ui: StateFlow<SubscriptionsUi> =
        combine(graph.sessions.state.filterNotNull(), graph.settings.settings, view, unread, follows.rows) { session, settings, view, unread, plugins ->
            build(session, settings, view, unread, plugins)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUi())

    private fun build(session: Session, settings: Settings, view: View, unread: Set<String>, plugins: List<SubscriptionRow>): SubscriptionsUi {
        val groupsOrder = settings[SubscriptionPrefs.groupsOrder]
        val groupsAscending = settings[SubscriptionPrefs.groupsAscending]
        val ordered = SubscriptionList.ordered(
            SubscriptionList.rows(session, plugins),
            settings[SubscriptionPrefs.order],
            settings[SubscriptionPrefs.ascending],
            settings[SubscriptionPrefs.customOrder],
        )
        val ai = settings.let { AiConfig(it[AiPrefs.baseUrl], it[AiPrefs.apiKey], it[AiPrefs.model]) }
        return SubscriptionsUi(
            loaded = true,
            session = session,
            tab = view.tab,
            searching = view.searching,
            query = view.query,
            people = SubscriptionList.filtered(ordered, view.query),
            allPeople = ordered,
            followedCount = ordered.size,
            board = GroupBoardModel.sections(GroupBoardModel.tiles(session, groupsOrder, groupsAscending, view.query, unread, plugins)),
            groupCount = session.groups.size,
            layout = settings[SubscriptionPrefs.groupsLayout],
            columns = settings[SubscriptionPrefs.groupsColumns],
            compact = settings[SubscriptionPrefs.groupsCompact],
            groupsOrder = groupsOrder,
            groupsAscending = groupsAscending,
            peopleOrder = settings[SubscriptionPrefs.order],
            peopleAscending = settings[SubscriptionPrefs.ascending],
            recentPicks = settings[SubscriptionPrefs.groupsRecentPicks],
            aiReady = ai.isConfigured,
        )
    }

    fun select(tab: SubscriptionsTab) = view.update { it.copy(tab = tab) }

    fun openSearch() = view.update { it.copy(searching = true) }

    /** Closing the search empties it. */
    fun closeSearch() = view.update { it.copy(searching = false, query = "") }

    fun search(query: String) = view.update { it.copy(query = query) }

    /**
     * Which groups have posts newer than their last read mark, for the dots on
     * the board: read again each time the tab comes back into view.
     */
    fun refreshUnread() {
        viewModelScope.launch {
            val session = graph.sessions.state.value ?: return@launch
            val prefs = FeedPrefs.of(graph.settings.load())
            unread.value = session.groups.filter { group ->
                val plan = GroupFeedRules.plan(session, prefs, group.id, emptySet())
                ReadMarks(graph.feedCache(ReadMarks.key(group.id))).unread(GroupFeedRules.tracks(plan, prefs))
            }.mapTo(HashSet()) { it.id }
        }
    }

    // ---- the menu sheet ----

    fun orderPeopleBy(field: SubscriptionsOrder) = settings { it.with(SubscriptionPrefs.customOrder, "").with(SubscriptionPrefs.order, field) }

    fun orderGroupsBy(field: GroupsOrder) = settings { it.with(SubscriptionPrefs.groupsOrder, field) }

    /** Reverses the order of the page open; on the people page the reader's own order gives way to it. */
    fun reverseOrder(groupsPage: Boolean) = settings {
        if (groupsPage) {
            it.with(SubscriptionPrefs.groupsAscending, !it[SubscriptionPrefs.groupsAscending])
        } else {
            it.with(SubscriptionPrefs.customOrder, "").with(SubscriptionPrefs.ascending, !it[SubscriptionPrefs.ascending])
        }
    }

    fun layOut(layout: GroupsLayout) = settings { it.with(SubscriptionPrefs.groupsLayout, layout) }

    /** Two or three columns, or the compact tiles; compact keeps the columns chosen. */
    fun columns(columns: Int?, compact: Boolean) = settings { settings ->
        (columns?.let { settings.with(SubscriptionPrefs.groupsColumns, it) } ?: settings).with(SubscriptionPrefs.groupsCompact, compact)
    }

    private fun settings(transform: (Settings) -> Settings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    // ---- the people page ----

    /** A drag in the people list: the list's order becomes the reader's own. */
    fun movePerson(from: Int, to: Int) {
        val rows = ui.value.people.takeIf { ui.value.canDragPeople } ?: return
        settings { it.with(SubscriptionPrefs.customOrder, SubscriptionList.customOrder(SubscriptionList.moved(rows, from, to))) }
    }

    /** [row] unfollowed and out of every group: a plugin's account by its plugin, as XTA's stores remove theirs. */
    fun unfollow(row: SubscriptionRow) {
        if (row.plugin != null) follows.unfollow(row) else session { if (row.search) it.unfollowSearch(row.id) else it.unfollow(row.id) }
    }

    /** A plugin's account's page, in its plugin. */
    fun openInPlugin(row: SubscriptionRow) = follows.open(row)

    fun setInFeed(row: SubscriptionRow, inFeed: Boolean) = session { it.withInFeed(row.id, inFeed) }

    fun setPostCap(row: SubscriptionRow, cap: Int?) = session { it.withPostCap(row.id, cap) }

    private fun session(transform: (Session) -> Session) {
        viewModelScope.launch { graph.sessions.update(transform) }
    }

    // ---- the groups page ----

    /** A drag in the groups list: the reader's own order, kept as each group's place. */
    fun moveGroup(from: Int, to: Int) {
        val now = ui.value
        if (!now.canDragGroups) return
        val ids = GroupBoardModel.reordered(now.board, from, to) ?: return
        session { GroupBoardModel.withPositions(it, ids) }
    }

    /** A pack someone exported, as a new group. */
    fun importPack(read: suspend () -> String?) {
        viewModelScope.launch {
            val pack = runCatching { withContext(Dispatchers.IO) { read() } }.getOrNull()?.let { runCatching { GroupPack.decode(it) }.getOrNull() }
            if (pack == null) {
                notices.tryEmit(SubscriptionsNotice.PackInvalid)
                return@launch
            }
            notices.tryEmit(SubscriptionsNotice.PackImported(pack.name, groups.importPack(pack)))
        }
    }
}
