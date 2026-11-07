package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.x.model.XUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The add box as it stands: what was typed, what X found for it, who was
 * added (their member ids), and which of the other networks' rows were.
 */
data class AddMemberUi(
    val query: String = "",
    val users: List<XUser>? = null,
    val failed: Boolean = false,
    val searching: Boolean = false,
    val adding: Boolean = false,
    val added: Set<String> = emptySet(),
    val addedRows: Set<String> = emptySet(),
)

/**
 * What the add sheet does (XTA's GroupAddMemberStore): a search that gives up
 * after [timeoutMillis] and that an earlier answer never overwrites, and one
 * follow at a time, never repeated for what was already added. A follow that
 * started runs to its end even when the sheet is dismissed meanwhile.
 */
class AddMemberModel(private val scope: CoroutineScope, private val timeoutMillis: Long = SEARCH_TIMEOUT_MILLIS) {
    private val mutable = MutableStateFlow(AddMemberUi())
    val state: StateFlow<AddMemberUi> = mutable.asStateFlow()
    private var search: Job? = null
    private var asked = 0

    fun type(query: String) {
        search?.cancel()
        asked++
        mutable.update { it.copy(query = query, users = null, failed = false, searching = false) }
    }

    /** Searches X for what was typed through [find]; a later search, or typing, makes this one's answer count for nothing. */
    fun find(find: suspend (String) -> List<XUser>) {
        val query = state.value.query.trim()
        if (query.isEmpty()) return
        search?.cancel()
        val ask = ++asked
        mutable.update { it.copy(searching = true, failed = false, users = null) }
        search = scope.launch {
            val users = try {
                withTimeout(timeoutMillis) { find(query) }
            } catch (timedOut: TimeoutCancellationException) {
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }
            if (ask == asked) mutable.update { if (users == null) it.copy(failed = true, searching = false) else it.copy(users = users, searching = false) }
        }
    }

    /**
     * Follows [key] through [follow], which answers the member id it added;
     * false when another follow is running, [key] was added already, or the
     * follow failed, which leaves it to be tried again.
     */
    suspend fun add(key: String, follow: suspend () -> String?): Boolean {
        if (state.value.adding || key in state.value.addedRows) return false
        mutable.update { it.copy(adding = true) }
        val member = withContext(NonCancellable) {
            try {
                follow()
            } catch (error: Throwable) {
                null
            }
        }
        mutable.update {
            if (member == null) it.copy(adding = false) else it.copy(adding = false, added = it.added + member, addedRows = it.addedRows + key)
        }
        return member != null
    }

    companion object {
        /** How long an X search may take before the sheet says it failed and a new one can be asked. */
        const val SEARCH_TIMEOUT_MILLIS = 30_000L
    }
}
