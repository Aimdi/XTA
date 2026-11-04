package com.aimdi.xtanative.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.services.ai.AiClient
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiPrompts
import com.aimdi.xtanative.services.ai.GroupingAccount
import com.aimdi.xtanative.services.ai.GroupingGroup
import com.aimdi.xtanative.services.ai.GroupingPlan
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The reader's AI connection, from the settings. */
internal fun aiConfigOf(settings: Settings) = AiConfig(settings[AiPrefs.baseUrl], settings[AiPrefs.apiKey], settings[AiPrefs.model])

/** The plan for the accounts in no group, and how it was made. */
data class SortUi(
    val loading: Boolean = true,
    val plan: GroupingPlan = GroupingPlan(),
    val handles: Map<String, String> = emptyMap(),
    val groupNames: Map<String, String> = emptyMap(),
    /** The AI was asked and gave nothing usable: the plan is the device's own. */
    val aiFailed: Boolean = false,
    val aiReady: Boolean = false,
    val applying: Boolean = false,
    val failed: Boolean = false,
) {
    fun handleOf(id: String): String = handles[id] ?: id

    fun groupNameOf(id: String): String = groupNames[id] ?: id
}

/**
 * XTA's sorting of ungrouped subscriptions (group_ungrouped_screen.dart,
 * group_ungrouped_model.dart): a plan made on the device from account and
 * group names, or by the reader's AI when they configured one, reviewed and
 * then applied. What it cannot place is sorted one by one. Every network's
 * followed accounts are sorted, the plugins' ([follows]) beside X's.
 */
class SortUngroupedViewModel(private val graph: AppGraph, private val follows: PluginFollows = PluginFollows.None) : ViewModel() {
    private val state = MutableStateFlow(SortUi())
    val ui: StateFlow<SortUi> = state.asStateFlow()
    private val ai by lazy { AiClient(OkHttpXHttp()) }

    init {
        plan()
        viewModelScope.launch { graph.settings.settings.collect { settings -> state.update { it.copy(aiReady = aiConfigOf(settings).isConfigured) } } }
    }

    /** Plans again from what is ungrouped now; with [useAi], the reader's AI writes it. */
    fun plan(useAi: Boolean = false) {
        viewModelScope.launch {
            state.update { it.copy(loading = true) }
            val session = graph.sessions.state.filterNotNull().first()
            val plugins = follows.current()
            val ungrouped = UngroupedPlans.ungrouped(session, plugins)
            val groups = UngroupedPlans.groups(session, plugins)
            val heuristic = UngroupedPlans.planByNames(ungrouped, groups)
            val config = aiConfigOf(graph.settings.load())
            val (plan, aiFailed) = if (useAi && config.isConfigured && ungrouped.isNotEmpty()) askAi(config, ungrouped, groups, heuristic) else heuristic to false
            state.update {
                it.copy(
                    loading = false,
                    plan = plan,
                    handles = ungrouped.associate { account -> account.id to account.handle },
                    groupNames = groups.associate { group -> group.id to group.name },
                    aiFailed = aiFailed,
                    applying = false,
                    failed = false,
                )
            }
        }
    }

    private suspend fun askAi(config: AiConfig, ungrouped: List<GroupingAccount>, groups: List<GroupingGroup>, heuristic: GroupingPlan): Pair<GroupingPlan, Boolean> = try {
        val reply = ai.complete(config, AiPrompts.grouping(ungrouped, groups))
        val parsed = AiPrompts.groupingPlan(reply, ungrouped.mapTo(HashSet()) { it.id }, groups.mapTo(HashSet()) { it.id })
        if (parsed.isEmpty) heuristic to true else heuristic.mergedWith(parsed).let { it to !it.usedAi }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        heuristic to true
    }

    /** Applies the plan; how many accounts it placed, or null when it could not. */
    suspend fun apply(): Int? {
        val now = state.value
        if (now.applying || now.loading) return null
        state.update { it.copy(applying = true) }
        return try {
            var placed = 0
            graph.sessions.update { session ->
                UngroupedPlans.applied(session, now.plan, { GroupActions.newId() }, System.currentTimeMillis()).let { (next, count) ->
                    placed = count
                    next
                }
            }
            placed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state.update { it.copy(applying = false, failed = true) }
            null
        }
    }

}

/** The one-by-one sorter at one moment: where it stands, the account it is on (X's or a plugin's), and the groups to put it in. */
data class SorterUi(
    val sorter: SorterState = SorterState(emptyList()),
    val accounts: Map<String, SubscriptionRow> = emptyMap(),
    val groups: List<FollowGroup> = emptyList(),
    val recentPicks: String = "",
    val failed: Boolean = false,
) {
    val current: SubscriptionRow? get() = sorter.current?.let(accounts::get)
}

/**
 * XTA's one-by-one sorter (group_ungrouped_sorter_screen.dart): each account
 * in turn, a tap on a group puts it there and shows the next. Every choice is
 * saved as it is made, and an undo takes it back out of those groups. The
 * plugins' accounts ([follows]) are read once, before the first is shown.
 */
class UngroupedSorterViewModel(private val graph: AppGraph, accountIds: List<String>, private val follows: PluginFollows = PluginFollows.None) : ViewModel() {
    private val sorter = MutableStateFlow(SorterState(accountIds))
    private val failed = MutableStateFlow(false)
    private val plugins = MutableStateFlow<List<SubscriptionRow>?>(null)
    val groups = GroupActions(graph, viewModelScope)

    init {
        viewModelScope.launch { plugins.value = follows.current() }
    }

    val ui: StateFlow<SorterUi> = combine(sorter, graph.sessions.state.filterNotNull(), graph.settings.settings, failed, plugins.filterNotNull()) { sorter, session, settings, failed, plugins ->
        SorterUi(
            sorter = sorter,
            accounts = SubscriptionList.rows(session, plugins).filterNot { it.search }.associateBy { it.id },
            groups = GroupBoardModel.ordered(session.groups, settings[SubscriptionPrefs.groupsOrder], settings[SubscriptionPrefs.groupsAscending]),
            recentPicks = settings[SubscriptionPrefs.groupsRecentPicks],
            failed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SorterUi(SorterState(accountIds)))

    /** The account on show into [groupIds]; nothing when a choice is being saved. */
    fun put(groupIds: List<String>) {
        val now = sorter.value
        val account = now.current ?: return
        if (now.saving || groupIds.isEmpty()) return
        save({ groups.addTo(account, groupIds) }) { it.decide(groupIds) }
    }

    /** [row]'s page: X's profile, or its plugin's screen. */
    fun open(row: SubscriptionRow, openProfile: (String) -> Unit) = if (row.plugin != null) follows.open(row) else openProfile(row.screenName)

    fun skip() = sorter.update { if (it.current == null || it.saving) it else it.decide(emptyList()) }

    fun undo() {
        val now = sorter.value
        val last = now.choices.lastOrNull() ?: return
        if (now.saving) return
        if (last.placed) save({ groups.removeFrom(last.accountId, last.groupIds) }) { it.undo() } else sorter.update { it.undo() }
    }

    fun sortSkipped() = sorter.update { if (!it.finished || it.saving || it.skipped.isEmpty()) it else it.skippedRound() }

    /** A new group named [name], with the account on show in it. */
    fun create(name: String) {
        viewModelScope.launch { put(listOf(groups.create(name))) }
    }

    fun failureSeen() {
        failed.value = false
    }

    private fun save(write: suspend () -> Unit, next: (SorterState) -> SorterState) {
        sorter.update { it.withSaving(true) }
        viewModelScope.launch {
            try {
                write()
                sorter.update { next(it).withSaving(false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                sorter.update { it.withSaving(false) }
                failed.value = true
            }
        }
    }
}
