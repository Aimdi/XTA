package com.aimdi.xtanative.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.follows.KnownFollow
import com.aimdi.xtanative.ui.profile.KnownFollows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUi(
    /** The first read, with nothing to show yet. */
    val loading: Boolean = true,
    /** Lists are being read; what is shown stays. */
    val scanning: Boolean = false,
    val candidates: List<Candidate> = emptyList(),
    /** Members whose lists are known, over the group's X members. */
    val read: Int = 0,
    val total: Int = 0,
    /** A list could not be read. */
    val failure: Problem? = null,
    val added: Set<String> = emptySet(),
    val adding: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val hasFeedback: Boolean = false,
)

/** What Discover tells the reader once, below the list. */
enum class DiscoveryNotice { FEEDBACK_SAVED, ADD_FAILED }

/**
 * A group's Discover (group_discovery.dart's GroupDiscoveryStore): the lists
 * already read show at once, then a rotating few members' Following lists
 * are read (more on "Scan more"), each once every three days, and the
 * candidates ranked again as each arrives. Adding one follows it here and
 * puts it in the group; nothing is sent to X.
 */
class DiscoveryViewModel(private val graph: AppGraph, private val groupId: String) : ViewModel() {
    private val state = MutableStateFlow(DiscoveryUi())
    val ui: StateFlow<DiscoveryUi> = state.asStateFlow()

    private val notices = MutableSharedFlow<DiscoveryNotice>(extraBufferCapacity = 4)
    val notice: SharedFlow<DiscoveryNotice> = notices.asSharedFlow()

    private var known: Map<String, List<KnownFollow>> = emptyMap()
    private var feedback: Map<String, Judgement> = emptyMap()
    private var undo: Map<String, Judgement>? = null
    private var scan: Job? = null

    init {
        viewModelScope.launch {
            feedback = graph.discoveryFeedback.of(groupId)
            load()
        }
        viewModelScope.launch { graph.sessions.state.filterNotNull().collect { publish() } }
    }

    /** Reads again from the start: the lists kept, then a few more. */
    fun load() = read(more = false)

    /** Reads a bigger rotating batch of members and adds what it finds. */
    fun scanMore() {
        if (!state.value.scanning) read(more = true)
    }

    private fun read(more: Boolean) {
        scan?.cancel()
        scan = viewModelScope.launch {
            val members = members(session())
            state.update { it.copy(scanning = true, failure = null, total = members.size) }
            known = known + graph.follows.remembered().mapValues { it.value.follows }.filterKeys { it in members }
            publish()
            val fetches = Discovery.fetches(more)
            val result = graph.follows.read(
                members.keys.toList(),
                maxFetches = fetches,
                timeoutMillis = Discovery.memberBudgetMillis(fetches),
                fetch = ::following,
                onPartial = { soFar ->
                    known = known + soFar
                    publish()
                },
            )
            known = known + result.follows
            state.update { it.copy(loading = false, scanning = false, failure = result.error?.let(Problem::of)) }
            publish()
        }
    }

    private suspend fun following(memberId: String): List<KnownFollow> {
        val users = graph.client.following(memberId, null, Discovery.FOLLOWS_PAGE).users
        users.forEach(graph.posts::remember)
        return KnownFollows.of(users)
    }

    /** [candidate] followed here and put in the group, keeping every group it is already in. */
    fun add(candidate: Candidate) {
        val now = state.value
        if (candidate.key in now.added || candidate.key in now.adding) return
        state.update { it.copy(adding = it.adding + candidate.key) }
        viewModelScope.launch {
            val done = try {
                val user = XUser(candidate.id, candidate.handle, candidate.name, candidate.avatarUrl, description = candidate.bio)
                graph.sessions.update { it.follow(user, System.currentTimeMillis()).addingTo(candidate.id, listOf(groupId)) }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                notices.tryEmit(DiscoveryNotice.ADD_FAILED)
                false
            }
            state.update { it.copy(adding = it.adding - candidate.key, added = if (done) it.added + candidate.key else it.added) }
            publish()
        }
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

    /** The group's X members, nested groups' included, by id. */
    private fun members(session: Session): Map<String, FollowedAccount> {
        val ids = session.membersOf(groupId, emptySet()).filterNot { it.search }.mapTo(HashSet()) { it.id }
        return session.following.filter { it.id in ids }.associateBy { it.id }
    }

    private fun publish() {
        val session = session()
        val members = members(session)
        val name = session.groups.firstOrNull { it.id == groupId }?.name.orEmpty()
        state.update { now ->
            val found = Discovery.candidates(known, members)
            val followed = Discovery.followedExcept(Discovery.followedIdentities(session.following), now.added, found)
            val ranked = Discovery.ranked(found, followed, name, feedback)
            now.copy(
                loading = now.loading && ranked.isEmpty(),
                candidates = Discovery.visible(ranked, feedback, followed),
                read = Discovery.covered(known, members.keys),
                total = members.size,
                canUndo = undo != null,
                hasFeedback = feedback.isNotEmpty(),
            )
        }
    }
}
