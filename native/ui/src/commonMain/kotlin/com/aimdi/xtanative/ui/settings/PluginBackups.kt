package com.aimdi.xtanative.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A section a running plugin keeps in the full backup, as its
 * [com.aimdi.xtanative.ui.plugins.PluginBackupSection] says: its [name], the
 * text [export] gives, and [import], which takes such a text back.
 */
class PluginSection(val name: String, val export: suspend () -> String?, val import: suspend (String) -> Unit)

/**
 * The full backup's part of the downloadable plugins (PLUGINS.md). The
 * screens [attach] the sections of the plugins that run, and an export writes
 * what each gives ([exported]). A restored section waits in [kept] for the
 * plugin that keeps it: [handOver] gives it to that plugin once it runs, and
 * it is kept no more, so uninstalling the plugin erases it as the rest of
 * what the plugin holds. Until then every export carries it. A plugin's code
 * may fail: what it fails at stays as it was.
 */
class PluginBackups(private val kept: KeptSections) {
    @Volatile
    private var running: List<PluginSection> = emptyList()
    private val turns = Mutex()

    /** The sections of the plugins running now; what was kept for them reaches them. */
    suspend fun attach(sections: List<PluginSection>) {
        running = sections
        handOver()
    }

    /** What the running plugins keep now, by section name. */
    suspend fun exported(): Map<String, String> =
        running.mapNotNull { section -> guarded { section.export() }?.let { section.name to it } }.toMap()

    /** Each kept section a running plugin keeps, given to it, and kept no more once it took it. */
    suspend fun handOver() {
        turns.withLock {
            val waiting = kept.get().pluginSections.orEmpty()
            val taken = running.filter { it.name in waiting }
                .filter { section -> guarded { section.import(waiting.getValue(section.name)) } != null }
                .mapTo(HashSet()) { it.name }
            if (taken.isNotEmpty()) kept.forgetPluginSections(taken)
        }
    }

    private suspend fun <T> guarded(call: suspend () -> T): T? = try {
        withContext(Dispatchers.IO) { call() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }
}
