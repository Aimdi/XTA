package com.aimdi.xtanative.data.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * How the installed plugins are loaded at start: side by side, [AT_ONCE] at a
 * time, since each load reads, digests and checks the signature of its whole
 * file. Every load is the same check it is alone; only the waiting overlaps.
 */
internal object PluginStarts {
    /** How many plugin files are checked and loaded at the same time. */
    const val AT_ONCE = 4

    /** [load] of every one of [items], at most [atOnce] at a time, the answers in the order of [items]; one that throws fails them all. */
    suspend fun <T, R> loadAll(items: List<T>, atOnce: Int = AT_ONCE, load: suspend (T) -> R): List<R> {
        val gate = Semaphore(atOnce)
        return coroutineScope { items.map { item -> async(Dispatchers.IO) { gate.withPermit { load(item) } } }.awaitAll() }
    }
}
