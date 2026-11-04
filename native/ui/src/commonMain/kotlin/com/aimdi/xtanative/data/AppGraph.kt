package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.FileAccountHealthStore
import com.aimdi.xtanative.core.x.XAccountHealthStore
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.FileSignerStore
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.antenna.AntennaStore
import com.aimdi.xtanative.store.downloads.DownloadQueue
import com.aimdi.xtanative.store.downloads.DownloadTransfer
import com.aimdi.xtanative.store.downloads.FileDownloadHistory
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.follows.DiscoveryFeedbackStore
import com.aimdi.xtanative.store.follows.FollowsCache
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.offline.TimelineCache
import com.aimdi.xtanative.store.published.PublishedDocument
import com.aimdi.xtanative.store.published.RepairsRegistry
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.saved.ArchiveNotesStore
import com.aimdi.xtanative.store.saved.LikesStore
import com.aimdi.xtanative.store.saved.LocalPostsStore
import com.aimdi.xtanative.store.saved.NoteDrafts
import com.aimdi.xtanative.store.saved.ProfileFiltersStore
import com.aimdi.xtanative.store.saved.ProfileNotesStore
import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.search.RecentSearches
import com.aimdi.xtanative.store.settings.CrashReportPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.downloads.PlatformFiles
import com.aimdi.xtanative.ui.settings.ChromeAvatar
import com.aimdi.xtanative.ui.settings.CrashReports
import com.aimdi.xtanative.ui.settings.DeviceBackup
import com.aimdi.xtanative.ui.settings.KeptSections
import com.aimdi.xtanative.ui.settings.PluginBackups
import com.aimdi.xtanative.ui.settings.SettingsStart
import com.aimdi.xtanative.ui.settings.StoredWebDavBaselines
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the screens share, made once per process by the app's shell
 * (Android's Application, the desktop's main): [files] is the app's private
 * folder, [connection] whether there is one as it changes, [version] this
 * build's, [mockX] the address of XTA's stand-in for X to read instead of
 * x.com (see [MockX]), or null, and [platformFiles] what only the platform
 * does with files: where downloads are saved, and how a file is opened.
 */
class AppGraph(
    private val files: File,
    connection: Flow<Boolean>,
    val version: String,
    private val mockX: String? = null,
    val platformFiles: PlatformFiles = PlatformFiles.Unavailable,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessions = SessionStore(File(files, "session.json"), initial = if (mockX == null) Session() else MockX.session)
    private val feedCaches = HashMap<String, FeedCache>()
    val posts = PostCache()

    /** What the device keeps beside the session (XTA's database and preferences), each store in its own files. */
    private val store = StoreDir(File(files, "store"))
    val settings = SettingsStore(store)
    val recentSearches = RecentSearches(settings)
    val saved = SavedStore(store)
    val likes = LikesStore(store)
    val localPosts = LocalPostsStore(store)
    val archiveNotes = ArchiveNotesStore(store)
    /** The reader's private notes on profiles, and the profiles whose reposts or replies the feeds hide. */
    val profileNotes = ProfileNotesStore(store)
    val profileFilters = ProfileFiltersStore(store)
    val antennas = AntennaStore(store)
    val readingJournal = ReadingJournal(store)
    val readingAppearance = ReadingAppearanceStore(store)
    val timelineCache = TimelineCache(store)
    val noteDrafts = NoteDrafts(store)

    /** Whom followed accounts follow, as last read: "Followed by" on a profile, and a group's Discover. */
    val follows = FollowsCache(store)
    val discoveryFeedback = DiscoveryFeedbackStore(store)

    /** Pictures and videos, streamed for downloads and the offline library. */
    private val mediaHttp = OkHttpMediaHttp()
    val offline = OfflineStore(store, mediaHttp)

    /** The picture the reader chose for the upper left (Settings › Accounts' Your avatar). */
    val avatar = ChromeAvatar(File(files, "chrome_avatar"), settings, mediaHttp)

    /** Partial downloads wait here until they are whole. */
    private val downloadStaging = File(files, "download-parts")
    val downloads = DownloadQueue(
        history = FileDownloadHistory(store),
        runner = DownloadTransfer(mediaHttp, downloadStaging, platformFiles) { platformFiles.sharedStorageAvailable() },
        discard = { entry -> DownloadTransfer.discardEntry(downloadStaging, entry) },
        cleanup = { DownloadTransfer.clearInterruptedFiles(downloadStaging) },
    )

    /** What a restored XTA backup carried that no store here holds yet, kept for the next export. */
    val kept = KeptSections(store)

    /** The running plugins' sections of the full backup, which the screens attach as the plugins run. */
    val pluginBackups = PluginBackups(kept, sessions)
    val webDavBaselines = StoredWebDavBaselines(store)

    /** Crash reports, written down on the device only while the reader has them on. */
    val crashReports = CrashReports(File(files, "crash-reports"))

    /** Whether there is a connection, as it changes: the present state first. */
    val online: SharedFlow<Boolean> = connection.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val http =
        if (mockX == null) OkHttpXHttp() else OkHttpXHttp(OkHttpXHttp.defaultClient.newBuilder().addInterceptor(MockX.interceptor(mockX)).build())
    val signers =
        if (mockX == null) XSigners(SigningBootstrap(http), FileSignerStore(File(files, "signers.json"))) else XSigners({ MockX.signer })

    /** The X accounts turned off for the feeds, as the settings hold them from [start] on: X is read with the others while one is on. */
    @Volatile
    var accountsOff: Set<String> = emptySet()
        private set
    /** Each account's sign-in health, kept across launches as XTA's accounts table keeps it: Diagnostics shows it. */
    val accountHealth: XAccountHealthStore = FileAccountHealthStore(File(files, "account-health.json"))

    val client = XClient(
        http,
        accounts = { sessions.state.value?.accounts.orEmpty() },
        signers = signers,
        guest = XGuest(http),
        health = accountHealth,
        disabledAccounts = { accountsOff },
    )

    /** The Following feed's cache for null, else the group's. */
    @Synchronized
    fun feedCache(groupId: String?): FeedCache {
        val name = if (groupId == null) "feed.json" else "feed-" + groupId.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".json"
        return feedCaches.getOrPut(name) { FeedCache(File(files, name)) }
    }

    /** Every feed's cache: after an import the follows they showed may be gone. */
    suspend fun clearFeedCaches() {
        withContext(Dispatchers.IO) {
            files.listFiles { file -> file.name.startsWith("feed") && file.name.endsWith(".json") }?.forEach { it.delete() }
        }
    }

    /**
     * Runs [write] to its end even when the screen that asked for it goes
     * away: the reading place, kept as the app stops or a window closes.
     */
    fun keep(write: suspend () -> Unit) {
        writes.launch { write() }
    }

    /** Waits for the [keep] writes under way: a closing desktop app lets them finish first. */
    suspend fun awaitWrites() {
        writes.coroutineContext[Job]?.children?.toList()?.joinAll()
    }

    /**
     * XTA's published repairs (`repairs.json`): the copy last read is in force
     * from the start; the networks take their values from [RepairsRegistry.published].
     */
    val repairs = RepairsRegistry(PublishedDocument(http, PublishedDocument.REPAIRS_URL, store.file("published/repairs.json")))

    fun start() {
        crashReports.install({ settings.state.value?.get(CrashReportPrefs.enabled) == true }, version)
        scope.launch { sessions.load() }
        // XTA's launch steps on the settings, once; then the feeds' switches follow the settings.
        scope.launch { SettingsStart.keepSessionInStep(settings, sessions) }
        scope.launch { SettingsStart.keepAccountsOffInStep(settings) { accountsOff = it } }
        scope.launch { DeviceBackup(this@AppGraph).adoptKeptProfiles() }
        if (mockX == null) scope.launch { SettingsStart.refreshEndpoints(settings, client) }
        scope.launch {
            repairs.applyKept()
            if (mockX == null) repairs.refresh()
        }
    }
}
