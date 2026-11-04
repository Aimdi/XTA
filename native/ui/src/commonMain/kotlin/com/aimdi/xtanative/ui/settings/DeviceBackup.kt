package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import java.time.Instant

/** The XTA backup against the app's stores: what an export reads and where a restore writes each section. */
class DeviceBackup(private val graph: AppGraph, private val clock: () -> Instant = Instant::now) {
    /** Everything a backup can carry, as the device holds it now. */
    suspend fun read(): DeviceContent {
        graph.sessions.load()
        val session = graph.sessions.state.value ?: Session()
        val places = buildMap<String, FeedPlace> {
            (listOf<String?>(null) + session.groups.map { it.id }).forEach { group ->
                graph.feedCache(group).readPlace()?.let { put(BackupExport.readPlaceKey(group), it) }
            }
        }
        return DeviceContent(
            session = session,
            settings = graph.settings.load(),
            library = graph.saved.content(),
            likes = graph.likes.content(),
            localPosts = graph.localPosts.content(),
            annotations = graph.archiveNotes.content(),
            antennas = graph.antennas.load(),
            readPlaces = places,
            journal = graph.readingJournal.content(),
            appearance = graph.readingAppearance.get(),
            kept = graph.kept.get(),
        )
    }

    /** The document an export of [choice] writes, as JSON. */
    suspend fun export(choice: ExportChoice): String = BackupExport.document(read(), choice, graph.version, clock()).encode()

    /**
     * Writes every section of [document] into its store, as XTA's import does:
     * the settings first (and XTA's launch steps after them), then the session,
     * the saved posts, likes, notes, article notes, antennas and reading
     * places, and what no store here holds yet into [KeptSections]. Reading
     * positions only when [includeReadPositions].
     */
    suspend fun restore(document: BackupDocument, includeReadPositions: Boolean) {
        val restoring = document.forRestore(includeReadPositions)
        val now = clock().toEpochMilli()
        restoring.settings?.let { graph.settings.restore(it) }
        val settings = graph.settings.load()
        graph.sessions.load()
        graph.sessions.update { FeedSwitches.mirrored(BackupRestore.session(it, restoring), settings) }
        BackupRestore.library(graph.saved.library(), restoring, now)?.let { graph.saved.replace(it) }
        BackupRestore.likes(graph.likes.likes(), restoring, now)?.let { graph.likes.replace(it) }
        BackupRestore.localPosts(graph.localPosts.notes(), restoring, now)?.let { graph.localPosts.replace(it) }
        BackupRestore.annotations(restoring)?.let { graph.archiveNotes.merge(it) }
        BackupRestore.antennas(restoring, now)?.let { graph.antennas.merge(it) }
        BackupRestore.journal(restoring)?.let { graph.readingJournal.replace(it) }
        BackupRestore.appearance(restoring)?.let { graph.readingAppearance.set(it) }
        graph.kept.merge(KeptSections.keptOf(restoring))
        // The feeds shown last may hold accounts no longer followed.
        if (restoring.subscriptions != null || restoring.groups != null || restoring.groupMembers != null) graph.clearFeedCaches()
        val groups = graph.sessions.state.value?.groups.orEmpty().mapTo(HashSet()) { it.id }
        BackupRestore.readPlaces(restoring, groups).forEach { (group, place) -> graph.feedCache(group).writePlace(place) }
    }
}

/** What the settings decide as the app starts. */
object SettingsStart {
    /**
     * XTA's launch steps on the settings ([com.aimdi.xtanative.store.settings.SettingsMigrations]
     * run as they are first read), then the session's feed switches kept in
     * step with the settings for as long as the app runs.
     */
    suspend fun keepSessionInStep(settings: SettingsStore, sessions: SessionStore) {
        settings.load()
        sessions.load()
        sessions.state.value?.let { session -> settings.update { FeedSwitches.adopted(it, session) } }
        settings.settings.collect { current ->
            val session = sessions.state.value ?: return@collect
            if (FeedSwitches.mirrored(session, current) != session) sessions.update { FeedSwitches.mirrored(it, current) }
        }
    }

    /**
     * The X accounts turned off in Settings › Accounts (or Home feed
     * accounts), handed to [gate] each time they change. One switch, as in
     * XTA: For you leaves them out, and every read goes to the accounts left
     * on while there is one (XTA's AccountFetchGate).
     */
    suspend fun keepAccountsOffInStep(settings: SettingsStore, gate: (Set<String>) -> Unit) {
        settings.observe(AccountPrefs.forYouHiddenAccountIds).collect { gate(it.toSet()) }
    }

    /** Rotated X query ids repaired from the published list, unless the reader turned that off; when it answered is noted. */
    suspend fun refreshEndpoints(settings: SettingsStore, client: XClient, now: () -> Instant = Instant::now) {
        val current = settings.load()
        if (!current[DataPrefs.endpointRegistryEnabled]) return
        if (client.refreshEndpoints(current[DataPrefs.endpointRegistryUrl]) != null) {
            settings.set(DataPrefs.endpointRegistryFetchedAt, XtaTime.iso(now()))
        }
    }
}
