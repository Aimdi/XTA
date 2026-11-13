package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.NetworkAccount
import com.aimdi.xtanative.store.backup.NetworkMembers
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
 * what each gives ([exported]). A section named by one of XTA's networks' keys
 * ([com.aimdi.xtanative.store.backup.BackupKeys.NETWORKS]) holds that
 * network's rows, which the backup carries under that key, where XTA reads them.
 *
 * A restored section waits in [kept] for the plugin that keeps it: [handOver]
 * gives it to that plugin once it runs, and it is kept no more, so
 * uninstalling the plugin erases it as the rest of what the plugin holds.
 * Until then every export carries it. A network's rows that reach their
 * plugin so make XTA's bare group members that name them the plugin's
 * ([NetworkMembers]), in [sessions]' groups. A plugin's code may fail: what
 * it fails at stays as it was.
 */
class PluginBackups(private val kept: KeptSections, private val sessions: SessionStore? = null) {
    @Volatile
    private var running: List<PluginSection> = emptyList()

    @Volatile
    private var named: suspend () -> List<NetworkAccount> = { emptyList() }
    private val turns = Mutex()

    /**
     * The sections of the plugins running now, and [accounts], what they
     * follow as each app names it; what was kept for them reaches them.
     */
    suspend fun attach(sections: List<PluginSection>, accounts: suspend () -> List<NetworkAccount> = { emptyList() }) {
        running = sections
        named = accounts
        handOver()
    }

    /** What the running plugins keep now, by section name. */
    suspend fun exported(): Map<String, String> =
        running.mapNotNull { section -> guarded { section.export() }?.let { section.name to it } }.toMap()

    /** What the running plugins follow, as each app names it: how an export writes their groups' members, and a restore reads XTA's. */
    suspend fun accounts(): List<NetworkAccount> = guarded { named() }.orEmpty()

    /**
     * Each kept section a running plugin keeps, given to it: its own text as
     * it was exported, then the networks' rows under its name, written over
     * what it keeps as XTA's import writes a backup's rows into a table.
     */
    suspend fun handOver() {
        turns.withLock {
            val own = kept.get().pluginSections.orEmpty()
            take(own, kept::forgetPluginSections) { it.import(own.getValue(it.name)) }
            val restored = kept.get()
            val rows = restored.networkRows()
            val taken = take(rows, kept::forgetNetworkSections) { it.import(rowsOver(it, restored)) }
            if (taken.isNotEmpty()) adopt(BackupDocument.ofNetworkRows(rows.filterKeys { it in taken }))
        }
    }

    /** [give] for each running section named in [waiting]; what it worked for is kept no more, and named back. */
    private suspend fun take(waiting: Map<String, String>, forget: suspend (Map<String, String>) -> Unit, give: suspend (PluginSection) -> Unit): Set<String> {
        val taken = running.filter { it.name in waiting }.filter { guarded { give(it) } != null }.mapTo(HashSet()) { it.name }
        if (taken.isNotEmpty()) forget(waiting.filterKeys { it in taken })
        return taken
    }

    /** What [section] keeps, with [restored]'s rows under its name over it. */
    private suspend fun rowsOver(section: PluginSection, restored: BackupDocument): String {
        val own = BackupDocument.ofNetworkRows(mapOf(section.name to section.export().orEmpty()))
        return own.withNetworksMerged(restored).networkRows().getValue(section.name)
    }

    /** The groups' bare members that name one of [arrived]'s rows, as the plugins now holding them name them. */
    private suspend fun adopt(arrived: BackupDocument) {
        val store = sessions ?: return
        val owners = NetworkMembers.named(NetworkMembers.accounts(arrived), accounts())
        if (owners.isEmpty()) return
        store.load()
        store.update { BackupRestore.withNetworkMembers(it, owners) }
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
