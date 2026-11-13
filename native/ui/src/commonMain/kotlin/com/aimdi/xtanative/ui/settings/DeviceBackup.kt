package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.NetworkAccount
import com.aimdi.xtanative.store.backup.NetworkMembers
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.group.ReadMarks
import java.time.Duration
import java.time.Instant

/** The XTA backup against the app's stores: what an export reads and where a restore writes each section. */
class DeviceBackup(private val graph: AppGraph, private val clock: () -> Instant = Instant::now) {
    /** Everything a backup can carry, as the device holds it now. */
    suspend fun read(): DeviceContent {
        graph.sessions.load()
        val session = graph.sessions.state.value ?: Session()
        val positions = buildMap<String, ReadPosition> {
            (listOf(FeedKeys.FOLLOWING, FeedKeys.FOR_YOU) + session.groups.map { it.id }).forEach { key ->
                marksOf(key).position()?.let { put(key, it) }
            }
        }
        val (reposts, replies) = graph.profileFilters.content()
        return DeviceContent(
            session = session,
            settings = graph.settings.load(),
            library = graph.saved.content(),
            likes = graph.likes.content(),
            localPosts = graph.localPosts.content(),
            annotations = graph.archiveNotes.content(),
            antennas = graph.antennas.load(),
            readPositions = positions,
            accountHealth = session.accounts.associate { it.id to graph.accountHealth.read(it.id) },
            showPrivate = graph.pluginPrefs.also { it.load() }.state.value.showPrivate,
            journal = graph.readingJournal.content(),
            appearance = graph.readingAppearance.get(),
            profileNotes = graph.profileNotes.content(),
            repostFilters = reposts,
            replyFilters = replies,
            pluginSections = graph.pluginBackups.exported(),
            networkAccounts = graph.pluginBackups.accounts(),
            kept = graph.kept.get(),
        )
    }

    /** The document an export of [choice] writes, as JSON. */
    suspend fun export(choice: ExportChoice): String = BackupExport.document(read(), choice, graph.version, clock()).encode()

    /**
     * Writes every section of [document] into its store, as XTA's import does:
     * the settings first (and XTA's launch steps after them), then the session,
     * its groups' members of other networks named as their plugins name them
     * ([networkAccounts]), the saved posts, likes, notes, article notes,
     * antennas, profile notes and filters and reading places, each network's
     * rows and each plugin's sections into the running plugin that keeps
     * them, and what no store here holds yet into [KeptSections]. The
     * accounts' health goes to the client's store, the plugin store's private
     * plugins to its own. Reading positions, Following's, For you's and the
     * groups', only when [includeReadPositions], into the read marks the
     * feeds keep ([ReadMarks]).
     */
    suspend fun restore(document: BackupDocument, includeReadPositions: Boolean) {
        val restoring = document.forRestore(includeReadPositions)
        val now = clock().toEpochMilli()
        restoring.settings?.let { graph.settings.restore(it) }
        val settings = graph.settings.load()
        graph.sessions.load()
        val accounts = networkAccounts(restoring)
        graph.sessions.update { FeedSwitches.mirrored(BackupRestore.session(it, restoring, accounts), settings) }
        BackupRestore.accountHealth(restoring)?.forEach { (id, health) -> graph.accountHealth.write(id, health) }
        BackupRestore.showPrivate(restoring)?.let { show -> graph.pluginPrefs.update { it.copy(showPrivate = show) } }
        BackupRestore.library(graph.saved.library(), restoring, now)?.let { graph.saved.replace(it) }
        BackupRestore.likes(graph.likes.likes(), restoring, now)?.let { graph.likes.replace(it) }
        BackupRestore.localPosts(graph.localPosts.notes(), restoring, now)?.let { graph.localPosts.replace(it) }
        BackupRestore.annotations(restoring)?.let { graph.archiveNotes.merge(it) }
        BackupRestore.antennas(restoring, now)?.let { graph.antennas.merge(it) }
        BackupRestore.journal(restoring)?.let { graph.readingJournal.replace(it) }
        BackupRestore.appearance(restoring)?.let { graph.readingAppearance.set(it) }
        restoreProfiles(restoring)
        graph.kept.merge(KeptSections.keptOf(restoring))
        graph.pluginBackups.handOver()
        // The feeds shown last may hold accounts no longer followed.
        if (restoring.subscriptions != null || restoring.groups != null || restoring.groupMembers != null) graph.clearFeedCaches()
        val groups = graph.sessions.state.value?.groups.orEmpty().mapTo(HashSet()) { it.id }
        BackupRestore.readPositions(restoring, groups).forEach { (key, position) -> marksOf(key).record(BackupRestore.markedThread(position), now) }
    }

    /** The read marks of the feed [key] names, where the feeds keep them. */
    private fun marksOf(key: String) = ReadMarks(graph.feedCache(ReadMarks.key(key)))

    /**
     * The networks' accounts a restore of [restoring] knows, as XTA's groups
     * read a member through every table that keeps its id: [restoring]'s
     * rows and those kept for plugins not running, as the running plugins
     * name them, and what the running plugins follow.
     */
    private suspend fun networkAccounts(restoring: BackupDocument): List<NetworkAccount> {
        val running = graph.pluginBackups.accounts()
        return NetworkMembers.named(NetworkMembers.accounts(graph.kept.get().withNetworksMerged(restoring)), running) + running
    }

    /** The profile notes and filters an older build kept aside on restoring a backup, put into their stores once. */
    suspend fun adoptKeptProfiles() = graph.kept.adoptProfiles(::restoreProfiles)

    /** The look and creation time an older build kept aside for restored groups, given to the groups that lack them. */
    suspend fun adoptKeptGroups() {
        val rows = graph.kept.get().groups?.takeIf { it.isNotEmpty() } ?: return
        graph.sessions.load()
        val session = graph.sessions.state.value ?: return
        if (BackupRestore.withKeptLooks(session, rows) != session) graph.sessions.update { BackupRestore.withKeptLooks(it, rows) }
    }

    private suspend fun restoreProfiles(document: BackupDocument) {
        BackupRestore.profileNotes(graph.profileNotes.content(), document)?.let { graph.profileNotes.replace(it) }
        BackupRestore.profileFilters(graph.profileFilters.content(), document)?.let { (reposts, replies) -> graph.profileFilters.replace(reposts, replies) }
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
        settings.observe(AccountPrefs.disabledAccountIds).collect { gate(it.toSet()) }
    }

    /**
     * Rotated X query ids repaired as XTA's EndpointRegistry does: the copy
     * of the published list kept from the last read applied first, so the
     * launch is repaired before X is asked anything; then, unless the reader
     * turned that off, the list read again at most once a day ([fetch]) and
     * kept with when it answered.
     */
    suspend fun refreshEndpoints(
        settings: SettingsStore,
        client: XClient,
        now: () -> Instant = Instant::now,
        fetch: suspend (url: String) -> String? = client::refreshEndpoints,
    ) {
        val current = settings.load()
        current[DataPrefs.endpointRegistryCache].takeIf { it.isNotBlank() }?.let { kept -> apply(client, kept) }
        if (!current[DataPrefs.endpointRegistryEnabled]) return
        val fetchedAt = XtaTime.parse(current[DataPrefs.endpointRegistryFetchedAt])
        if (fetchedAt != null && Duration.between(fetchedAt, now()) < ENDPOINTS_EVERY) return
        val body = fetch(current[DataPrefs.endpointRegistryUrl]) ?: return
        apply(client, body)
        settings.update { it.with(DataPrefs.endpointRegistryCache, body).with(DataPrefs.endpointRegistryFetchedAt, XtaTime.iso(now())) }
    }

    private fun apply(client: XClient, registry: String) {
        runCatching { client.endpoints.applyOverrides(J.parse(registry)) }
    }

    /** How often the published list is read: it changes only when X rotates an id. */
    private val ENDPOINTS_EVERY: Duration = Duration.ofHours(24)
}
