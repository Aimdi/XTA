package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.upsert
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The parts of a restored XTA backup no store of XTA Native holds yet: each
 * network's follows, likes, votes and galleries as XTA wrote them, and the
 * sections of plugins that are not running, each until the plugin that keeps
 * it runs and takes it (see [PluginBackups]); and what a group's row carries
 * beyond the session's groups (its icon, colour, mark and when it was made).
 * They are kept in `backup/kept.json`, so the next export carries them again
 * and nothing a reader restored is lost.
 */
class KeptSections(dir: StoreDir) {
    private val file = dir.file(FILE)
    private val mutex = Mutex()
    private var current: BackupDocument? = null

    /** Profile notes and filters a restore kept here before their stores came, until [adoptProfiles] hands them over. */
    private var legacyProfiles: BackupDocument? = null

    suspend fun get(): BackupDocument = mutex.withLock { loaded() }

    /** [incoming]'s kept sections merged in, row by row, as XTA's import replaces rows by their key. */
    suspend fun merge(incoming: BackupDocument): BackupDocument = edit { merged(it, incoming) }

    /** The plugins' sections in [taken] kept no more, their plugins took them; one a restore has changed since stays. */
    suspend fun forgetPluginSections(taken: Map<String, String>) {
        edit { kept -> kept.copy(pluginSections = kept.pluginSections?.filterNot { (name, text) -> taken[name] == text }?.takeIf { it.isNotEmpty() }) }
    }

    /** The networks' rows in [taken] kept no more, the plugins named by their keys took them; rows a restore has changed since stay. */
    suspend fun forgetNetworkSections(taken: Map<String, String>) {
        edit { kept -> kept.withoutNetworks(kept.networkRows().filter { (key, rows) -> taken[key] == rows }.keys) }
    }

    private suspend fun edit(change: (BackupDocument) -> BackupDocument): BackupDocument = mutex.withLock { write(change(loaded())) }

    /**
     * Hands [adopt] the profile notes and filters an older build kept here,
     * when there are any, and keeps them no more once it has taken them.
     */
    suspend fun adoptProfiles(adopt: suspend (BackupDocument) -> Unit) = mutex.withLock {
        loaded()
        val legacy = legacyProfiles ?: return@withLock
        adopt(legacy)
        legacyProfiles = null
        file.write(loaded().encode())
    }

    private suspend fun write(next: BackupDocument): BackupDocument {
        if (next != current) {
            file.write(next.encode())
            current = next
        }
        return next
    }

    private suspend fun loaded(): BackupDocument {
        current?.let { return it }
        val stored = file.read()?.let(BackupDocument::parse)
        legacyProfiles = stored?.let(::profilesOf)
        return (stored?.let(::keptOf) ?: BackupDocument()).also { current = it }
    }

    companion object {
        const val FILE = "backup/kept.json"

        /** Only the sections this store keeps. */
        fun keptOf(document: BackupDocument): BackupDocument =
            BackupDocument(groups = document.groups, pluginSections = document.pluginSections).withNetworks(document)

        /** The profile notes and filters [document] carries, or null when it carries none. */
        private fun profilesOf(document: BackupDocument): BackupDocument? =
            BackupDocument(repostFilters = document.repostFilters, replyFilters = document.replyFilters, profileNotes = document.profileNotes)
                .takeIf { it.repostFilters != null || it.replyFilters != null || it.profileNotes != null }

        /** [kept] with each of [incoming]'s kept sections merged in; a section [incoming] does not carry stays as it was. */
        fun merged(kept: BackupDocument, incoming: BackupDocument): BackupDocument = BackupDocument(
            groups = upsert(kept.groups, incoming.groups) { it.id },
            pluginSections = incoming.pluginSections?.let { kept.pluginSections.orEmpty() + it } ?: kept.pluginSections,
        ).withNetworks(kept).withNetworksMerged(incoming)
    }
}
