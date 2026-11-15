package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * "More on …" under one conversation: the topic's posts, read once when
 * [ask]ed and never before, so opening a conversation asks Meta nothing more.
 * [state] is null until they are asked for.
 */
internal class ThreadsTopicBeside(private val scope: CoroutineScope, private val read: suspend (String) -> List<ThreadsPost>) {
    private val found = MutableStateFlow<Found?>(null)

    /** What was asked and what came back; null until [ask]. */
    val state: StateFlow<Found?> get() = found

    /** Reads [topic]'s posts, the first time only: a conversation's topic does not change. */
    fun ask(topic: String) {
        synchronized(this) {
            if (found.value != null) return
            found.value = Found(topic, null)
        }
        scope.launch { found.value = Found(topic, read(topic)) }
    }

    /** The topic asked for and its posts, null while they are read. */
    data class Found(val topic: String, val posts: List<ThreadsPost>?) {
        val loading: Boolean get() = posts == null
    }
}
