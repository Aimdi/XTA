package com.aimdi.xtanative.ui.plugins

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How a settings screen's connection test stands (XTA's PluginConnectionStatus). */
enum class ConnectionStatus { IDLE, RUNNING, OK, WARNING, FAILED }

/** A connection test's [status], and what it says of the fields tested. */
data class ConnectionState(val status: ConnectionStatus = ConnectionStatus.IDLE, val message: String? = null) {
    val running: Boolean get() = status == ConnectionStatus.RUNNING
}

/**
 * The connection test of a settings screen for the reader's own server, as
 * XTA's PluginConnectionStore keeps it: what a test says belongs to the
 * fields as they were tested, so editing them takes it away, and a test that
 * ends after an edit, or after another test began, says nothing.
 */
class PluginConnection {
    private val current = MutableStateFlow(ConnectionState())
    private var revision = 0

    val state: StateFlow<ConnectionState> = current.asStateFlow()

    /** The fields changed: what was said of them no longer holds, and a test under way will say nothing. */
    @Synchronized
    fun invalidate() {
        revision++
        current.value = ConnectionState()
    }

    /**
     * Tests the fields: [save] keeps them first, as XTA's Test connection
     * does, then [test] says how they did, shown unless they changed
     * meanwhile. Either failing leaves nothing said rather than a test
     * that never ends.
     */
    suspend fun test(save: suspend () -> Unit, test: suspend () -> ConnectionState) {
        val started = begin()
        var result = ConnectionState()
        try {
            save()
            if (isCurrent(started)) result = test()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to say of fields that could not be kept or tested.
        } finally {
            finish(started, result)
        }
    }

    @Synchronized
    private fun begin(): Int {
        revision++
        current.value = ConnectionState(ConnectionStatus.RUNNING)
        return revision
    }

    @Synchronized
    private fun isCurrent(started: Int): Boolean = started == revision

    @Synchronized
    private fun finish(started: Int, result: ConnectionState) {
        if (started == revision) current.value = result
    }
}
