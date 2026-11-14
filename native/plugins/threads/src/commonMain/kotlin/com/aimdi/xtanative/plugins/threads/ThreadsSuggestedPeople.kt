package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsBackground
import com.aimdi.xtanative.networks.threads.ThreadsSuggestedAccount
import com.aimdi.xtanative.networks.threads.attempt
import com.aimdi.xtanative.store.StoreFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The accounts Threads suggested the signed-in reader, and when they were read; empty until they were. */
internal data class ThreadsSuggestedState(
    val people: List<ThreadsSuggestedAccount> = emptyList(),
    val readAt: Long? = null,
)

/** When For you's "Suggested for you" row reads with the account, decided apart from reading it. */
internal object ThreadsSuggestedRules {
    /** How long the suggestions read are shown before a visit may read them again. */
    const val TTL_MILLIS = 6 * 60 * 60 * 1000L

    /** How many hidden accounts are remembered: the latest, so the file never grows without end. */
    const val DISMISSED_CAP = 500

    /**
     * Whether reaching the row reads the account now: only for the reader
     * looking at For you ([background] never), once a visit ([askedThisVisit]),
     * when the account may be used ([usable]), and when what was read last
     * is older than [TTL_MILLIS] or there is none.
     */
    fun asks(held: ThreadsSuggestedState, askedThisVisit: Boolean, usable: Boolean, background: Boolean, now: Long): Boolean =
        usable && !background && !askedThisVisit && (held.readAt?.let { now - it >= TTL_MILLIS } ?: true)

    /** Whether [held] is fresh enough to show. */
    fun fresh(held: ThreadsSuggestedState, now: Long): Boolean =
        held.people.isNotEmpty() && held.readAt?.let { now - it < TTL_MILLIS } == true

    /** [people] as the row shows them: without those the reader hid from suggestions here. Those followed here stay, turned to Following. */
    fun shown(people: List<ThreadsSuggestedAccount>, dismissed: Set<String>): List<ThreadsSuggestedAccount> =
        if (dismissed.isEmpty()) people else people.filterNot { it.handle.lowercase() in dismissed }

    /** [handles] with [handle] hidden too, the oldest forgotten past [DISMISSED_CAP]. */
    fun dismissing(handles: List<String>, handle: String): List<String> {
        val key = handle.trim().removePrefix("@").lowercase()
        return if (key.isEmpty() || key in handles) handles else (handles + key).takeLast(DISMISSED_CAP)
    }

    fun encode(handles: List<String>): String = JsonArray(handles.map(::JsonPrimitive)).toString()

    /** What [text] keeps; anything unreadable is nothing hidden. */
    fun decode(text: String?): List<String> =
        J.parse(text.orEmpty()).list.mapNotNull { it.string?.trim()?.lowercase()?.ifEmpty { null } }.distinct().takeLast(DISMISSED_CAP)
}

/**
 * For you's "Suggested for you" row: the accounts Threads suggests the
 * signed-in reader, read with their account only when they reach the row
 * in For you ([reached]), at most once a visit ([visit]), kept for six hours
 * and never read from the background. Where the Bearer For you page the
 * reader opted in to already carried suggestions ([timeline]), those are
 * shown and nothing is asked. Resting, signed out, without a route
 * ([usable] false) or failing, nothing is shown here and the guest strip
 * stands in, quietly.
 *
 * Following one follows it on this device only; hiding one ([dismiss])
 * hides it from the row here, remembered in [file]. Nothing is sent to Threads.
 */
internal class ThreadsSuggestedPeople(
    private val file: StoreFile,
    private val read: suspend () -> List<ThreadsSuggestedAccount>,
    private val usable: () -> Boolean,
    private val timeline: () -> Pair<Long, List<ThreadsSuggestedAccount>>? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val held = MutableStateFlow(ThreadsSuggestedState())
    private val hidden = MutableStateFlow<List<String>?>(null)
    private val lock = Mutex()

    @Volatile
    private var askedThisVisit = false

    /** The suggestions read, fresh or not; the row shows them only while [ThreadsSuggestedRules.fresh]. */
    val state: StateFlow<ThreadsSuggestedState> = held.asStateFlow()

    /** The handles hidden from suggestions here, lower case; null until the file was read. */
    val dismissed: StateFlow<List<String>?> = hidden.asStateFlow()

    /** Whether the row may come from the account at all now: a route, a session, an account ready. */
    fun mayAsk(): Boolean = usable() || timelineFresh() != null

    /** The reader opened For you: one read may be asked again this visit. */
    fun visit() {
        askedThisVisit = false
    }

    /**
     * The reader scrolled to the row: the suggestions the Bearer page carried
     * when it did, else one read with the account when [ThreadsSuggestedRules.asks]
     * says so. A failure, a rest or a sign-out leaves the row to the guest strip.
     */
    suspend fun reached() {
        val background = coroutineContext[ThreadsBackground.Key] != null
        loadDismissed()
        lock.withLock {
            if (background) return
            timelineFresh()?.let { (at, people) ->
                if (held.value.readAt != at) held.value = ThreadsSuggestedState(people, at)
                askedThisVisit = true
                return
            }
            if (!ThreadsSuggestedRules.asks(held.value, askedThisVisit, usable(), background = false, now = clock())) return
            askedThisVisit = true
            val found = attempt { read() }.orEmpty()
            if (found.isNotEmpty()) held.value = ThreadsSuggestedState(found, clock())
        }
    }

    /** [handle] hidden from suggestions on this device, remembered. Nothing is sent to Threads. */
    suspend fun dismiss(handle: String) {
        val next = ThreadsSuggestedRules.dismissing(loadDismissed(), handle)
        hidden.update { next }
        file.write(ThreadsSuggestedRules.encode(next))
    }

    private suspend fun loadDismissed(): List<String> = hidden.value ?: ThreadsSuggestedRules.decode(file.read()).also { read -> hidden.update { it ?: read } }

    private fun timelineFresh(): Pair<Long, List<ThreadsSuggestedAccount>>? =
        timeline()?.takeIf { (at, people) -> people.isNotEmpty() && clock() - at < ThreadsSuggestedRules.TTL_MILLIS }
}
