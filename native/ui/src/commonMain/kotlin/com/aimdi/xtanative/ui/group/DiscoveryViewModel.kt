package com.aimdi.xtanative.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.services.ai.AiClient
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.follows.KnownFollow
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.profile.KnownFollows
import com.aimdi.xtanative.ui.subscriptions.GroupActions
import com.aimdi.xtanative.ui.subscriptions.PluginFollows
import com.aimdi.xtanative.ui.subscriptions.SubscriptionRow
import com.aimdi.xtanative.ui.subscriptions.aiConfigOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class DiscoveryUi(
    /** The first read, with nothing to show yet. */
    val loading: Boolean = true,
    /** Sources or the AI are answering; what is shown stays. */
    val scanning: Boolean = false,
    val candidates: List<Candidate> = emptyList(),
    val coverage: DiscoveryCoverage = DiscoveryCoverage(0, 0),
    /** The sources that could not be read, by source ([Discovery.X] or a plugin's id). */
    val failures: Map<String, Problem> = emptyMap(),
    val added: Set<String> = emptySet(),
    val adding: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val hasFeedback: Boolean = false,
    /** The list is in the order the reader's AI gave. */
    val usedAi: Boolean = false,
    /** The AI was asked and gave nothing usable: the order is the device's own. */
    val aiFailed: Boolean = false,
)

/** What Discover tells the reader once, below the list. */
enum class DiscoveryNotice { FEEDBACK_SAVED, ADD_FAILED }

/**
 * A group's Discover (group_discovery.dart's GroupDiscoveryStore): every
 * network the group has members on is read at once, X from what the group
 * feed kept, a rotating few members' latest posts and Following lists, each
 * plugin through its own part ([DiscoveryPlugins]); what is found is ranked
 * again as each answers, and the reader's AI may order it. Adding one
 * follows it here (in its plugin, for another network's) and puts it in the
 * group; nothing is sent to any network.
 */
class DiscoveryViewModel(
    private val graph: AppGraph,
    private val groupId: String,
    private val plugins: DiscoveryPlugins = DiscoveryPlugins.None,
    private val follows: PluginFollows = PluginFollows.None,
) : ViewModel() {
    private val state = MutableStateFlow(DiscoveryUi())
    val ui: StateFlow<DiscoveryUi> = state.asStateFlow()

    private val notices = MutableSharedFlow<DiscoveryNotice>(extraBufferCapacity = 4)
    val notice: SharedFlow<DiscoveryNotice> = notices.asSharedFlow()

    private val groups = GroupActions(graph, viewModelScope)
    private val ai by lazy { AiClient(OkHttpXHttp()) }

    private var board = DiscoveryBoard()
    private var ranked: List<Candidate> = emptyList()
    private var totals: Map<String, Int> = emptyMap()
    private var feedback: Map<String, Judgement> = emptyMap()
    private var undo: Map<String, Judgement>? = null
    private var pluginRows: List<SubscriptionRow> = emptyList()

    /** What each plugin found, by candidate: how to follow it there, and its screen. */
    private val found = HashMap<String, PluginDiscovered>()
    private var scan: Job? = null
    private var generation = 0

    /** "Rank with AI" asked before there was anything to rank: asked once a scan lands rows. */
    private var pendingAi: AiConfig? = null

    init {
        viewModelScope.launch {
            feedback = graph.discoveryFeedback.of(groupId)
            graph.sessions.state.filterNotNull().first()
            load()
        }
        viewModelScope.launch { graph.sessions.state.filterNotNull().collect { publish() } }
        viewModelScope.launch {
            follows.rows.collect { rows ->
                pluginRows = rows
                publish()
            }
        }
    }

    /** Reads again from the start: what is kept, then a few more members. */
    fun load() = scanAll(more = false)

    /** Reads a bigger rotating batch of members and adds what it finds. */
    fun scanMore() {
        val now = state.value
        if (!now.scanning && !now.loading) scanAll(more = true)
    }

    private fun scanAll(more: Boolean) {
        scan?.cancel()
        val run = ++generation
        board = board.begun(keep = more, ranked = ranked)
        scan = viewModelScope.launch {
            val sources = sources(session())
            totals = sources.associate { it.id to it.members }
            state.update { it.copy(scanning = true) }
            publish()
            coroutineScope { sources.forEach { source -> launch { read(source, more, run) } } }
            if (run != generation) return@launch
            state.update { it.copy(loading = false, scanning = false) }
            publish()
            pendingAi?.let { config ->
                pendingAi = null
                if (ranked.isNotEmpty()) rank(config)
            }
        }
    }

    /** One source of candidates: X, or a plugin, and how many of the group's members it speaks for. */
    private class Source(val id: String, val members: Int, val read: suspend (more: Boolean, partial: (DiscoveryBatch) -> Unit) -> DiscoveryBatch)

    /** A source that fails or stalls keeps what it already handed over, and says so. */
    private suspend fun read(source: Source, more: Boolean, run: Int) {
        val failure = try {
            val batch = withTimeout(Discovery.SOURCE_MILLIS) { source.read(more) { absorb(source.id, it, run) } }
            absorb(source.id, batch, run)
            null
        } catch (error: Throwable) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            error
        }
        if (failure != null && run == generation) {
            board = board.failed(source.id, failure)
            publish()
        }
    }

    private fun absorb(source: String, batch: DiscoveryBatch, run: Int) {
        if (run != generation) return
        board = board.absorbed(source, batch)
        publish()
    }

    private fun sources(session: Session): List<Source> {
        val x = xMembers(session)
        val plans = plugins.plans()
        val theirs = PluginMembers.of(session, groupId, emptySet(), plans.keys)
        return listOfNotNull(x.takeIf { it.isNotEmpty() }?.let { xSource(session, it) }) +
            plans.mapNotNull { (id, plan) -> theirs[id]?.takeIf { it.isNotEmpty() }?.let { pluginSource(id, plan, it) } }
    }

    private fun xSource(session: Session, members: Map<String, FollowedAccount>) =
        Source(Discovery.X, members.size) { more, partial -> XScan(session, members, more, partial).run() }

    /** X's stages, cheapest first (group_discovery_x.dart): each keeps what the others found, and the first failure is told. */
    private inner class XScan(session: Session, private val members: Map<String, FollowedAccount>, private val more: Boolean, private val partial: (DiscoveryBatch) -> Unit) {
        private val plan = session.feedPlan(groupId)
        private var feed = emptyList<Candidate>()
        private var authors = emptyList<Candidate>()
        private var followed = emptyList<Candidate>()
        private val covered = HashSet<String>()
        private var error: Throwable? = null

        suspend fun run(): DiscoveryBatch {
            stage(::mineFeed)
            coroutineScope {
                launch { stage(::readAuthors) }
                launch { stage(::readFollows) }
            }
            return batch()
        }

        private suspend fun stage(work: suspend () -> Unit) {
            try {
                work()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (error == null) error = failure
            }
        }

        private fun batch() = DiscoveryBatch(feed + authors + followed, covered.size, error)

        private fun emit() = partial(batch())

        /** What the group feed kept: no request. */
        private suspend fun mineFeed() {
            val saved = graph.feedCache(groupId).read()
            feed = XDiscovery.fromPosts(XDiscovery.feedThreads(saved), members, plan.includeReplies)
            covered += XDiscovery.coveredByFeed(saved, plan, members.keys)
            emit()
        }

        private suspend fun readAuthors() {
            val fetches = Discovery.rotating(Discovery.AUTHORS_PER_LOAD, more)
            val read = DiscoveryCaches.posts.read(members.keys.toList(), fetches, Discovery.memberBudgetMillis(fetches), ::postsOf, ::absorbAuthors)
            absorbAuthors(read)
            if (read.failed) read.error?.let { throw it }
        }

        private fun absorbAuthors(read: RotatingRead<XThread>) {
            authors = XDiscovery.fromPosts(read.rows.values.flatten(), members, includeReplies = false)
            covered += read.fresh
            emit()
        }

        private suspend fun readFollows() {
            val fetches = Discovery.fetches(more)
            val read = graph.follows.read(members.keys.toList(), fetches, Discovery.memberBudgetMillis(fetches), ::followingOf, ::absorbFollows)
            absorbFollows(read.follows)
            read.error?.let { throw it }
        }

        private fun absorbFollows(byMember: Map<String, List<KnownFollow>>) {
            followed = XDiscovery.fromFollows(byMember, members)
            covered += byMember.keys
            emit()
        }
    }

    private suspend fun postsOf(memberId: String): List<XThread> =
        graph.client.userPosts(memberId, count = Discovery.POSTS_PAGE).threads.also(graph.posts::remember)

    private suspend fun followingOf(memberId: String): List<KnownFollow> {
        val users = graph.client.following(memberId, null, Discovery.FOLLOWS_PAGE).users
        users.forEach(graph.posts::remember)
        return KnownFollows.of(users)
    }

    /** A plugin's members, a rotating few asked each time (group_discovery_sources.dart). */
    private fun pluginSource(id: String, plan: DiscoveryPlan, accounts: List<String>) = Source(id, accounts.size) { more, partial ->
        val keys = accounts.associateBy { PluginFinds.keyOf(plan, it) }
        val fetches = Discovery.rotating(plan.perScan, more)
        fun batch(read: RotatingRead<PluginDiscovered>): DiscoveryBatch {
            val rows = PluginFinds.byAccount(read.rows, plan, accounts)
            rows.values.flatten().forEach { found[Discovery.identity(id, it.id)] = it }
            return DiscoveryBatch(PluginFinds.candidates(id, rows, PluginFinds.members(id, accounts, pluginRows)), read.read)
        }
        val read = DiscoveryCaches.of(id).read(keys.keys.toList(), fetches, Discovery.memberBudgetMillis(fetches), { key -> plugins.around(id, keys.getValue(key)) }) { partial(batch(it)) }
        if (read.failed) read.error?.let { throw it }
        batch(read)
    }

    /** The last ask of the AI from outside the pane (a bar's or a tile's sparkle) already made. */
    private var asked = 0

    /** [request], a count that grows with each tap of a sparkle outside the pane, asks the AI once each, however often the pane is drawn. */
    fun rankWithAi(request: Int) {
        if (request <= asked) return
        asked = request
        rankWithAi()
    }

    /** Lets the reader's AI order what was found; asked while a scan runs, it waits for the scan to end. */
    fun rankWithAi() {
        viewModelScope.launch {
            val config = aiConfigOf(graph.settings.load())
            if (!config.isConfigured) return@launch
            val now = state.value
            if (ranked.isEmpty() || now.scanning || now.loading) pendingAi = config else rank(config)
        }
    }

    private suspend fun rank(config: AiConfig) {
        val run = generation
        val candidates = ranked
        board = board.aiRanked(null, failed = false)
        state.update { it.copy(scanning = true) }
        publish()
        val order = try {
            DiscoveryAi.order(ai.complete(config, DiscoveryAi.prompt(groupName(session()), candidates), timeoutMillis = DiscoveryAi.TIMEOUT_MILLIS), candidates)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (run != generation) return
        board = board.aiRanked(order, failed = order == null)
        state.update { it.copy(scanning = false) }
        publish()
    }

    /** [candidate] followed here and put in the group, keeping every group it is already in. */
    fun add(candidate: Candidate) {
        val now = state.value
        if (candidate.key in now.added || candidate.key in now.adding) return
        state.update { it.copy(adding = it.adding + candidate.key) }
        viewModelScope.launch {
            val done = attempt {
                val member = followed(candidate)
                graph.sessions.update { it.addingTo(member, listOf(groupId)) }
            }
            state.update { it.copy(adding = it.adding - candidate.key, added = if (done) it.added + candidate.key else it.added) }
            publish()
        }
    }

    /** What a group holds [candidate] as, while that is known (an X account met only by name is not yet). */
    fun memberOf(candidate: Candidate): String? = when {
        candidate.id.isEmpty() -> null
        candidate.source == Discovery.X -> candidate.id
        else -> PluginMembers.memberId(candidate.source, candidate.id)
    }

    /** The groups [candidate] is in now, with this one: what "Other groups…" opens on. */
    fun groupsOf(candidate: Candidate): Set<String> {
        val member = memberOf(candidate)
        return session().groups.filter { member != null && member in it.memberIds }.mapTo(LinkedHashSet()) { it.id } + groupId
    }

    /** The groups picked for [candidate] from [initial]: an X account is followed first, another network's once it joins a group, as XTA's pickers do. */
    fun putInGroups(candidate: Candidate, initial: Set<String>, chosen: Set<String>) {
        viewModelScope.launch {
            attempt {
                val member = if (candidate.source == Discovery.X || chosen.isNotEmpty()) followed(candidate) else memberOf(candidate) ?: return@attempt
                graph.sessions.update { it.withMemberships(member, chosen) }
                groups.rememberPicks(initial, chosen)
            }
        }
    }

    /** A new group named [name], made from the membership sheet's search; its id. */
    suspend fun createGroup(name: String): String = groups.create(name)

    /** The screen of its plugin a plugin's candidate opens on, when it named one. */
    fun routeOf(candidate: Candidate): String? = found[candidate.key]?.route

    /** [candidate] followed here, or in its plugin; the id its groups hold it by. */
    private suspend fun followed(candidate: Candidate): String {
        if (candidate.source != Discovery.X) {
            (found[candidate.key] ?: error("No longer found")).follow()
            return PluginMembers.memberId(candidate.source, candidate.id)
        }
        val user = if (candidate.id.isNotEmpty()) {
            XUser(candidate.id, candidate.handle, candidate.name, candidate.avatarUrl, description = candidate.bio)
        } else {
            graph.posts.user(candidate.handle) ?: graph.client.profile(candidate.handle).first
        }
        graph.sessions.update { it.follow(user, System.currentTimeMillis()) }
        return user.id
    }

    /** Runs [work]; a failure says so below the list. */
    private suspend fun attempt(work: suspend () -> Unit): Boolean = try {
        work()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        notices.tryEmit(DiscoveryNotice.ADD_FAILED)
        false
    }

    /** "More like this" (1), "less" (-1) or hide (0): kept for the group, and the list ranked again. */
    fun judge(candidate: Candidate, action: Int) = changeFeedback(feedback + (candidate.key to Discovery.judged(candidate, action)), action != Judgement.MORE)

    fun undo() {
        val before = undo ?: return
        changeFeedback(before, notify = false, keepUndo = false)
    }

    fun reset() = changeFeedback(emptyMap(), notify = false, keepUndo = false)

    private fun changeFeedback(next: Map<String, Judgement>, notify: Boolean, keepUndo: Boolean = true) {
        undo = if (keepUndo) feedback else null
        feedback = next
        publish()
        viewModelScope.launch { graph.discoveryFeedback.set(groupId, next) }
        if (notify) notices.tryEmit(DiscoveryNotice.FEEDBACK_SAVED)
    }

    private fun session(): Session = graph.sessions.state.value ?: Session()

    private fun groupName(session: Session): String = session.groups.firstOrNull { it.id == groupId }?.name.orEmpty()

    /** The group's X members, nested groups' included, by id. */
    private fun xMembers(session: Session): Map<String, FollowedAccount> {
        val ids = session.membersOf(groupId, emptySet()).filterNot { it.search }.mapTo(HashSet()) { it.id }
        return session.following.filter { it.id in ids }.associateBy { it.id }
    }

    /** Who the reader follows on every network, and the group's own members of the plugins read. */
    private fun followedOf(session: Session): Set<String> =
        Discovery.followedIdentities(session.following) + PluginFinds.followed(pluginRows) +
            PluginFinds.memberIdentities(PluginMembers.of(session, groupId, emptySet(), totals.keys - Discovery.X))

    private fun publish() {
        val session = session()
        val now = state.value
        val followed = Discovery.followedExcept(followedOf(session), now.added, board.found)
        ranked = board.ranked(followed, groupName(session), feedback)
        state.update {
            it.copy(
                loading = it.loading && ranked.isEmpty(),
                candidates = board.visible(ranked, followed, feedback),
                coverage = board.coverage(totals),
                failures = board.failures.mapValues { (_, error) -> problemOf(error) },
                canUndo = undo != null,
                hasFeedback = feedback.isNotEmpty(),
                usedAi = board.aiOrder != null,
                aiFailed = board.aiFailed,
            )
        }
    }
}
