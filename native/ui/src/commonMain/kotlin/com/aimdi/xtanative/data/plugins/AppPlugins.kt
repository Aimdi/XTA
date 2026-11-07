package com.aimdi.xtanative.data.plugins

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.pluginhost.HostPlatform
import com.aimdi.xtanative.pluginhost.InstalledPlugin
import com.aimdi.xtanative.pluginhost.PluginApi
import com.aimdi.xtanative.pluginhost.PluginLoad
import com.aimdi.xtanative.pluginhost.PluginManager
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.backup.NetworkAccount
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.downloads.Downloading
import com.aimdi.xtanative.ui.downloads.PluginBatches
import com.aimdi.xtanative.ui.downloads.PluginDownloads
import com.aimdi.xtanative.ui.feed.PluginFeeds
import com.aimdi.xtanative.ui.feed.SourceAsk
import com.aimdi.xtanative.ui.group.DiscoveryPlan
import com.aimdi.xtanative.ui.group.DiscoveryPlugins
import com.aimdi.xtanative.ui.home.StripNetwork
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.OLDEST_PLUGIN_API
import com.aimdi.xtanative.ui.plugins.PLUGIN_API_VERSION
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.PluginFolderOption
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.plugins.PluginQueued
import com.aimdi.xtanative.ui.plugins.PluginSavedPost
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.post.CashtagLinks
import com.aimdi.xtanative.ui.saved.FolderFilings
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.Keeping
import com.aimdi.xtanative.ui.subscriptions.FollowableRow
import com.aimdi.xtanative.ui.subscriptions.GroupActions
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources
import com.aimdi.xtanative.ui.subscriptions.PluginFollows
import com.aimdi.xtanative.ui.subscriptions.SubscriptionList
import com.aimdi.xtanative.ui.subscriptions.SubscriptionRow
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A plugin the app runs: the instance of its entry class, as it was installed. */
@Stable
class RunningPlugin internal constructor(val plugin: XtaPlugin, val installed: InstalledPlugin)

/** What a plugin takes on the device: the [items] it keeps, the bytes of its folder and of its code. */
data class PluginUse(val items: Int, val dataBytes: Long, val codeBytes: Long)

/** A running plugin's entry in a post sheet, in the reader's language: [run] once the sheet has closed. */
class SheetEntry(val icon: ImageVector, val label: String, val run: () -> Unit)

/**
 * The app's downloadable plugins (PLUGINS.md), beside [graph], made by the
 * shell with how its platform checks and loads a plugin ([platform]) and
 * where the store reads what is on offer ([catalogueUrl]; a stand-in build
 * names a local one, and the address in Settings goes first, [CatalogueAddress]),
 * then started. [manager] installs, updates and
 * uninstalls them; what is installed is loaded at start and after each
 * install, cast to [XtaPlugin], given its [PluginHost] and listed in
 * [running], which Home's strip, the links and the plugin screens read
 * (through LocalPlugins).
 *
 * It also hands them what the post sheets and Saved's folders send on
 * ([sheetEntries], [folderOption], and each post filed in a folder), and
 * what a cashtag opens ([openCashtag], [cashtagEntries]), and lists what
 * they follow for Subscriptions and the backup ([follows]).
 *
 * A plugin's code is kept under `plugins/` of the app's private folder
 * ([files]) and what it stores under `plugin-data/<id>/`. Uninstalling erases
 * that folder, what Home keeps of it (its place on the strip, its top bar's
 * pins) and its accounts' group memberships (member ids "<id>:…"), as XTA's
 * uninstall does, before the manager deletes its code.
 */
class AppPlugins(
    private val files: File,
    internal val graph: AppGraph,
    platform: HostPlatform,
    catalogueUrl: String = PluginManager.CATALOGUE_URL,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val http: XHttp = OkHttpXHttp()
    internal val x: XClient = graph.client
    internal val sessions: SessionStore = graph.sessions
    internal val online: Flow<Boolean> = graph.online
    internal val readingJournal: ReadingJournal = graph.readingJournal
    internal val readingAppearance: ReadingAppearanceStore = graph.readingAppearance
    internal val offline: OfflineStore = graph.offline
    internal val repairs: StateFlow<Map<String, String>> = graph.repairs.published
    internal val settings: SettingsStore = graph.settings
    private val store = StoreDir(File(files, "store"))

    /** What the app keeps of the plugins itself: whether private plugins are offered. */
    val prefs: PluginPrefs = graph.pluginPrefs

    val manager = PluginManager(
        host = platform,
        api = PluginApi((OLDEST_PLUGIN_API..PLUGIN_API_VERSION).toSet()),
        directory = File(files, "plugins"),
        store = store,
        erase = ::erase,
        catalogueUrl = catalogueUrl,
        catalogueAddress = { CatalogueAddress.of(graph.settings.load(), catalogueUrl) },
        http = http,
    )

    /** Where a plugin's navigation goes: the screens attach to it. */
    val navigator = AppNavigator()

    /** The media a plugin opened full screen, for the viewer to show. */
    val media = MediaShelf()

    /** The ids filed in Saved, for the plugins' bookmarks. */
    val savedIds: StateFlow<Set<String>> = graph.saved.state
        .map { library -> library?.posts?.mapTo(HashSet()) { it.id }.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Plugin [id]'s own posts in Saved, with their notes, while a screen of it lists them ([PluginHost.savedPosts]). */
    internal fun savedPostsOf(id: String): StateFlow<List<PluginSavedPost>?> = graph.saved.state
        .map { library -> library?.let { PluginSaved.posts(it, id) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(SAVED_LIST_LINGER_MILLIS), null)

    private val mutableSheet = MutableStateFlow<PluginSheet?>(null)

    /** The sheet a plugin opened over the screens, which the window shows ([com.aimdi.xtanative.ui.pluginstore.PluginSheetHost]). */
    val sheet: StateFlow<PluginSheet?> = mutableSheet.asStateFlow()

    /** The group changes the plugins' group picker makes, written to their end whatever screen asked. */
    internal val groups = GroupActions(graph, scope)

    /** Where the screens go once they are up, instead of staying on Home: CI's desktop check opens the store or a plugin so. */
    @Volatile
    var openAtStart: Destination? = null

    private val mutableRunning = MutableStateFlow<List<RunningPlugin>>(emptyList())

    /** The plugins running now, by id. */
    val running: StateFlow<List<RunningPlugin>> = mutableRunning.asStateFlow()

    /** The running plugins' feeds, for the timelines that mix them in (Home's, the groups'). */
    val feeds: PluginFeeds = RunningFeeds()

    /** The accounts the running plugins follow, for the Subscriptions tab and the backup. */
    val follows: PluginFollows = RunningFollows()

    /**
     * The networks the running plugins name for contract addresses
     * ([XtaPlugin.contractNetworks], Stocks' watched tokens), for X posts'
     * text; the first plugin's name of a contract leads, and one that fails
     * names none.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val contractNetworks: StateFlow<Map<String, String>> = running.flatMapLatest { list ->
        val each = list.mapNotNull { running -> guardedNow { running.plugin.contractNetworks } }
            .map { networks -> networks.onStart { emit(emptyMap()) }.catch { emit(emptyMap()) } }
        if (each.isEmpty()) flowOf(emptyMap()) else combine(each) { maps -> maps.reversed().fold(emptyMap<String, String>()) { all, map -> all + map } }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The running plugins' parts of a group's Discover. */
    val discoveries: DiscoveryPlugins = RunningDiscoveries()

    private val mutableProblems = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Installed plugins that could not be started, with why: for the log and the store. */
    val startProblems: StateFlow<Map<String, String>> = mutableProblems.asStateFlow()

    private val language = MutableStateFlow(Locale.getDefault().toLanguageTag())

    /** The running plugins with a home tab, which Home's strip can show, named in the reader's language. */
    val stripNetworks: StateFlow<List<StripNetwork>> = combine(running, language) { list, tag ->
        list.filter { it.plugin.homeTab != null }.map { StripNetwork(it.plugin.id, guardedNow { it.plugin.title(tag) } ?: it.plugin.id) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The installed plugins Home has no timeline of: those that could not be started, and those running without a home tab. */
    val missingNetworks: StateFlow<Set<String>> = combine(running, startProblems) { list, problems ->
        problems.keys + list.filter { guardedNow { it.plugin.homeTab } == null }.map { it.plugin.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** The plugins the reader put on Home's strip, in their order. */
    val stripPins: Flow<List<String>> = settings.settings.map { it[HomePrefs.feedStripNetworks] }.distinctUntilChanged()

    private val changing = Mutex()

    init {
        FolderFilings.listen(graph, ::filed)
    }

    /** Reads what is installed and starts it, then follows every install, update and uninstall. */
    fun start() {
        scope.launch { guarded { graph.saved.library() } }
        scope.launch {
            prefs.load()
            manager.showPrivate(prefs.state.value.showPrivate)
            // A restored backup may change it while the app runs.
            launch { prefs.state.map { it.showPrivate }.distinctUntilChanged().drop(1).collect { manager.showPrivate(it) } }
            manager.installed()
            manager.state
                .map { state -> state.installed.mapNotNull { it.installed } }
                .distinctUntilChanged()
                .collect { reconcile(it) }
        }
    }

    /** Installs [id] as the catalogue offers it, as the store's Install does, and says how that went in the log, which CI's checks read. */
    suspend fun install(id: String): Boolean = manager.install(id).also { done ->
        println("XTA Native: plugin $id " + if (done) "installed" else "not installed: ${manager.state.value[id]?.status}")
    }

    /** Uninstalls [id], as the store's Uninstall does, and says how that went in the log. */
    suspend fun uninstall(id: String): Boolean = manager.uninstall(id).also { done ->
        println("XTA Native: plugin $id " + if (done) "uninstalled" else "not uninstalled: ${manager.state.value[id]?.status}")
    }

    /**
     * What CI's desktop check, which cannot tap, asks of the store at start:
     * [installs] installed from the catalogue, [pins] put on Home's strip,
     * then [uninstalls] uninstalled, in that order.
     */
    fun startWith(installs: List<String>, pins: List<String>, uninstalls: List<String>) {
        scope.launch {
            if (installs.isNotEmpty()) manager.refresh()
            installs.forEach { install(it) }
            pins.forEach { pinOnStrip(it, pinned = true) }
            uninstalls.forEach { uninstall(it) }
        }
    }

    /** The running plugin [id], or null. */
    fun runningOf(id: String): RunningPlugin? = running.value.firstOrNull { it.plugin.id == id }

    /** The language the reader reads the app in ([Locale.toLanguageTag]): the plugins' names on Home's strip follow it. */
    fun useLanguage(tag: String) {
        language.value = tag
    }

    /** Shows private plugins in the store, or hides them, and keeps the choice. */
    suspend fun showPrivate(show: Boolean) {
        manager.showPrivate(show)
        prefs.update { it.copy(showPrivate = show) }
    }

    /** Gives [id] a page on Home's bottom bar, or takes it away, as XTA's "Show as a tab" does ([withPluginAsTab]). */
    suspend fun showAsTab(id: String, on: Boolean) {
        settings.update { it.withPluginAsTab(id, on) }
    }

    /** Puts [id] on Home's strip after the networks there, or takes it off, as the strip's own sheet does. */
    suspend fun pinOnStrip(id: String, pinned: Boolean) {
        settings.update { it.withPluginOnStrip(id, pinned) }
    }

    /** What [id] takes on the device. */
    suspend fun use(id: String): PluginUse {
        val items = runningOf(id)?.let { running -> guarded { running.plugin.footprint().items } } ?: 0
        val data = withContext(Dispatchers.IO) { dataOf(id).walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
        return PluginUse(items, data, manager.footprint(id))
    }

    /** The screen of a running plugin that opens [url]; null when none does. */
    fun destinationFor(url: String): Destination? = running.value.firstNotNullOfOrNull { running ->
        guardedNow { running.plugin.routeFor(url) }?.let { Destination.PluginRoute(running.plugin.id, it) }
    }

    /**
     * The page on its network of group member [memberId] ("<plugin id>:<account>"):
     * the screen its running plugin has for the account
     * ([XtaPlugin.routeForAccount]), as XTA opens a group's member there; null
     * for an X account or a search in [searches], or where the plugin has none.
     */
    fun destinationForMember(memberId: String, searches: Set<String> = emptySet()): Destination? {
        val list = running.value
        val (id, account) = PluginMembers.owner(memberId, list.mapTo(HashSet()) { it.plugin.id }, searches) ?: return null
        val plugin = list.firstOrNull { it.plugin.id == id }?.plugin ?: return null
        return guardedNow { plugin.routeForAccount(account) }?.let { Destination.PluginRoute(id, it) }
    }

    /** Opens group member [memberId]'s page on its network ([destinationForMember]), answering whether one opened. */
    fun openMember(memberId: String, searches: Set<String> = emptySet()): Boolean {
        val destination = destinationForMember(memberId, searches) ?: return false
        navigator.go(destination)
        return true
    }

    /**
     * Opens [article], kept in the offline collection, in the reader of the
     * running plugin it came from ([XtaPlugin.routeForOffline]), as XTA's
     * offline library does; false when none reads it.
     */
    fun openOffline(article: OfflineArticle): Boolean {
        val destination = running.value.firstNotNullOfOrNull { running ->
            guardedNow { running.plugin.routeForOffline(article) }?.let { Destination.PluginRoute(running.plugin.id, it) }
        } ?: return false
        navigator.go(destination)
        return true
    }

    /** Opens [url] on the screen of a running plugin that reads it, answering whether one did: the app's links ask this first. */
    fun openOnScreen(url: String): Boolean {
        val destination = destinationFor(url) ?: return false
        navigator.go(destination)
        return true
    }

    /** Whether a running plugin may still open [url] after asking the network. Quick: no request. */
    fun mightOpenLater(url: String): Boolean = running.value.any { running ->
        guardedNow { running.plugin.mightRouteLater(url) } == true
    }

    /**
     * Opens [url] on the screen a running plugin has for it once the network
     * has been asked, answering whether one did. The link opener gives this a
     * few seconds, then the browser has the link.
     */
    suspend fun openLater(url: String): Boolean {
        val destination = running.value.firstNotNullOfOrNull { running ->
            guarded { running.plugin.routeForLater(url) }?.let { Destination.PluginRoute(running.plugin.id, it) }
        } ?: return false
        navigator.go(destination)
        return true
    }

    internal fun dataOf(id: String): File = File(files, "plugin-data/$id")

    /** Opens [sheet] over the screens, in place of the one open. */
    fun show(sheet: PluginSheet) {
        mutableSheet.value = sheet
    }

    fun dismissSheet() {
        mutableSheet.value = null
    }

    /**
     * The groups picked for [sheet]'s member, as the picker closed on [chosen]
     * from [initial]: the plugin follows it first when it joins a group, as
     * XTA's picker does, then the memberships are written.
     */
    fun putInGroups(sheet: PluginSheet.Groups, initial: Set<String>, chosen: Set<String>) {
        scope.launch {
            if (chosen.isNotEmpty()) guarded { sheet.follow() }
            groups.setMemberships(sheet.memberId, initial, chosen)
        }
    }

    /** Saves [media] of plugin [id] through the downloads, as [PluginHost.download] and the plugins' media viewer do. */
    fun download(id: String, media: MediaItem, fileName: String? = null, subfolder: String? = null) =
        Downloading.pluginMedia(graph, id, media, fileName, subfolder)

    /**
     * What [plugins] (the running ones) add to a post sheet for [post], in
     * [language], below the app's own entries: each runs beyond the sheet. A
     * plugin that fails to say what it offers offers nothing.
     */
    fun sheetEntries(post: PluginSharedPost, language: String, plugins: List<RunningPlugin> = running.value): List<SheetEntry> =
        plugins.flatMap { running -> guardedNow { running.plugin.postSheetActions(post) }.orEmpty() }.mapNotNull { action ->
            guardedNow { action.label(language) }?.let { label -> SheetEntry(action.icon, label) { scope.launch { guarded { action.run(post) } } } }
        }

    /** The screen the first of [plugins] (the running ones) has for cashtag [symbol] ([XtaPlugin.routeForCashtag]); null when none has one. */
    fun destinationForCashtag(symbol: String, plugins: List<RunningPlugin> = running.value): Destination? = plugins.firstNotNullOfOrNull { each ->
        guardedNow { each.plugin.routeForCashtag(symbol) }?.let { Destination.PluginRoute(each.plugin.id, it) }
    }

    /** Cashtag [symbol] opened as a tap on it opens it: a running plugin's screen for it, else X's search for it. */
    fun openCashtag(symbol: String) = navigator.go(destinationForCashtag(symbol) ?: Destination.XSearch(CashtagLinks.tag(symbol)))

    /**
     * What [plugins] (the running ones) add to cashtag [symbol]'s sheet, in
     * [language], below the cashtag: each runs beyond the sheet. A plugin
     * that fails to say what it offers offers nothing.
     */
    fun cashtagEntries(symbol: String, language: String, plugins: List<RunningPlugin> = running.value): List<SheetEntry> =
        plugins.flatMap { running -> guardedNow { running.plugin.cashtagActions(symbol.uppercase()) }.orEmpty() }.mapNotNull { action ->
            guardedNow { action.label(language) }?.let { label -> SheetEntry(action.icon, label) { scope.launch { guarded { action.run() } } } }
        }

    /** The switch Saved's folder dialog shows: the folder's upload switch, as the first of [plugins] that offers one names it. */
    fun folderOption(plugins: List<RunningPlugin> = running.value): PluginFolderOption? = folderOwner(plugins)?.second

    /** [item] was filed in [folder]: the plugin that has the folder's switch hears of it while the switch is on. */
    private fun filed(folder: SavedFolder, item: Keepable) {
        val post = PluginSaves.filed(folder, item) ?: return
        val owner = folderOwner(running.value)?.first ?: return
        scope.launch { guarded { owner.plugin.filed(post) } }
    }

    private fun folderOwner(plugins: List<RunningPlugin>): Pair<RunningPlugin, PluginFolderOption>? =
        plugins.firstNotNullOfOrNull { running -> guardedNow { running.plugin.folderOption }?.let { running to it } }

    /** The feeds of the running plugins that have one. */
    private inner class RunningFeeds : PluginFeeds {
        override val ids: StateFlow<Set<String>> = running
            .map { list -> list.filter { guardedNow { it.plugin.feed } != null }.mapTo(HashSet()) { it.plugin.id } }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

        /** Off the screen's thread: a plugin parses what it reads where it is called. */
        override suspend fun posts(id: String, ask: SourceAsk, force: Boolean): List<NetworkPost> = withContext(Dispatchers.Default) {
            val feed = runningOf(id)?.plugin?.feed ?: return@withContext emptyList()
            try {
                val following = if (ask.followed) feed.followed() else emptyList()
                val accounts = ask.accounts(following)
                if (accounts.isEmpty()) emptyList() else feed.posts(accounts, ask.kinds, force)
            } catch (e: LinkageError) {
                // Built against an API this app does not have after all: its posts do not come, and the others do.
                throw IllegalStateException(e)
            }
        }
    }

    /**
     * The running plugins' followed accounts, each plugin's as it says them
     * ([XtaPlugin.subscriptions]), off the screen's thread; a plugin that
     * fails lists none, and the others' stay.
     */
    private inner class RunningFollows : PluginFollows {
        @OptIn(ExperimentalCoroutinesApi::class)
        override val rows: Flow<List<SubscriptionRow>> = running.flatMapLatest { list ->
            val each = list.mapNotNull { running -> guardedNow { running.plugin.subscriptions }?.let { rowsOf(running.plugin.id, it) } }
            if (each.isEmpty()) flowOf(emptyList()) else combine(each) { lists -> lists.flatMap { it } }
        }

        override suspend fun current(): List<SubscriptionRow> = running.value.flatMap { running ->
            subscriptionsOf(running.plugin).orEmpty().map { SubscriptionList.pluginRow(running.plugin.id, it) }
        }

        private fun rowsOf(id: String, subscriptions: Flow<List<PluginSubscription>>): Flow<List<SubscriptionRow>> =
            subscriptions.map { list -> list.map { SubscriptionList.pluginRow(id, it) } }
                .onStart { emit(emptyList()) }
                .catch { emit(emptyList()) }
                .flowOn(Dispatchers.Default)

        /**
         * What the plugin keeps (its backup sections, its own settings) and
         * the groups [row] was in are read before and once the account has
         * left the plugin's list; the undo puts them back while the plugin
         * still keeps what the unfollow left.
         */
        override suspend fun unfollow(row: SubscriptionRow): (suspend () -> Boolean)? {
            val plugin = row.plugin?.let(::runningOf)?.plugin ?: return null
            val subscription = subscriptionsOf(plugin)?.firstOrNull { PluginMembers.memberId(plugin.id, it.id) == row.id } ?: return null
            val before = kept(plugin)
            val groups = sessions.state.value?.groups.orEmpty().filter { row.id in it.memberIds }.map { it.id }
            guarded { subscription.unfollow() } ?: return null
            guarded { withTimeoutOrNull(ASKED_MILLIS) { plugin.subscriptions?.first { list -> list.none { it.id == subscription.id } } } }
            val after = kept(plugin)
            if (before == after && groups.isEmpty()) return null
            return { undo(plugin, row.id, before, after, groups) }
        }

        private suspend fun kept(plugin: XtaPlugin) = PluginKept(
            sections = guardedNow { plugin.backupSections }.orEmpty().associate { it.name to guarded { it.export() } },
            settings = PluginUndo.ownSettings(settings.load(), plugin.id),
        )

        private suspend fun undo(plugin: XtaPlugin, memberId: String, before: PluginKept, after: PluginKept, groups: List<String>): Boolean {
            if (kept(plugin) != after) return false
            val sections = guardedNow { plugin.backupSections }.orEmpty().associateBy { it.name }
            PluginUndo.changedSections(before, after).forEach { (name, text) -> guarded { sections[name]?.import?.invoke(text) } }
            if (before.settings != after.settings) settings.update { PluginUndo.withOwn(it, plugin.id, before.settings) }
            if (groups.isNotEmpty()) sessions.update { it.addingTo(memberId, groups) }
            return true
        }

        override fun open(row: SubscriptionRow) {
            val plugin = row.plugin ?: return
            row.route?.let { navigator.go(Destination.PluginRoute(plugin, it)) }
        }

        override suspend fun accounts(): List<NetworkAccount> = running.value.flatMap { running ->
            subscriptionsOf(running.plugin).orEmpty().map { NetworkAccount(running.plugin.id, it.backupId, it.id) }
        }

        override fun followables(text: String): List<FollowableRow> = GroupAddSources.pluginsFor(text).mapNotNull { id ->
            val plugin = runningOf(id)?.plugin ?: return@mapNotNull null
            val followable = guardedNow { plugin.followable(text) } ?: return@mapNotNull null
            FollowableRow(id, followable.label) {
                val account = try {
                    withContext(Dispatchers.Default) { followable.follow() }
                } catch (e: LinkageError) {
                    throw IllegalStateException(e)
                }
                PluginMembers.memberId(id, account)
            }
        }

        /** What [plugin] follows now, or null when it cannot say in [ASKED_MILLIS]. */
        private suspend fun subscriptionsOf(plugin: XtaPlugin): List<PluginSubscription>? =
            guarded { withTimeoutOrNull(ASKED_MILLIS) { plugin.subscriptions?.first() } }
    }

    /** The running plugins' parts of a group's Discover ([XtaPlugin.discovery]), each asked off the screen's thread. */
    private inner class RunningDiscoveries : DiscoveryPlugins {
        override fun plans(): Map<String, DiscoveryPlan> = running.value.mapNotNull { running ->
            guardedNow { running.plugin.discovery?.let { DiscoveryPlan(it.perScan, it.scope) } }?.let { running.plugin.id to it }
        }.toMap()

        override suspend fun around(pluginId: String, account: String): List<PluginDiscovered> = withContext(Dispatchers.Default) {
            val discovery = runningOf(pluginId)?.plugin?.discovery ?: return@withContext emptyList()
            try {
                discovery.around(account)
            } catch (e: LinkageError) {
                // Built against an API this app does not have after all: it finds nothing, and the others do.
                throw IllegalStateException(e)
            }
        }
    }

    /** [account] of plugin [id] out of every group, as the plugin unfollowed it ([PluginHost.unfollowed]). */
    internal fun leaveGroups(id: String, account: String) {
        scope.launch {
            sessions.load()
            sessions.update { PluginMembers.unfollowed(it, id, account) }
        }
    }

    private suspend fun reconcile(installed: List<InstalledPlugin>) = changing.withLock {
        val wanted = installed.associateBy { it.id }
        mutableRunning.value.filter { wanted[it.plugin.id]?.version != it.installed.version }.forEach { stop(it) }
        val started = mutableRunning.value.mapTo(HashSet()) { it.plugin.id }
        installed.filter { it.id !in started }.forEach { startPlugin(it) }
    }

    private suspend fun startPlugin(installed: InstalledPlugin) {
        val id = installed.id
        val problem = when (val load = manager.load(id)) {
            is PluginLoad.Refused -> "refused: ${load.reason}"
            is PluginLoad.Loaded -> {
                val plugin = load.plugin as? XtaPlugin
                when {
                    plugin == null -> "${load.plugin.javaClass.name} is no XtaPlugin"
                    plugin.id != id -> "its entry says it is ${plugin.id}"
                    else -> attach(plugin, load.installed)
                }
            }
        }
        mutableProblems.update { if (problem == null) it - id else it + (id to problem) }
        println("XTA Native: plugin $id ${installed.version} " + (problem?.let { "not started: $it" } ?: "loaded"))
    }

    /** Gives [plugin] its host and runs it; why it would not attach, or null. */
    private fun attach(plugin: XtaPlugin, installed: InstalledPlugin): String? = try {
        plugin.attach(AppPluginHost(installed.id, this))
        mutableRunning.update { list -> (list.filter { it.plugin.id != installed.id } + RunningPlugin(plugin, installed)).sortedBy { it.plugin.id } }
        null
    } catch (e: Exception) {
        "attach: $e"
    } catch (e: LinkageError) {
        // Built against an API this app does not have after all.
        "attach: $e"
    }

    private fun stop(running: RunningPlugin) {
        mutableRunning.update { list -> list.filter { it !== running } }
        guardedNow { running.plugin.detach() }
    }

    /**
     * Erases what [id] stored, before the manager deletes its code: when this
     * fails, nothing is deleted. Not under [changing]: a start of [id] under
     * way waits for the manager, which waits for this; the state the uninstall
     * leaves then stops whatever such a start ran.
     */
    private suspend fun erase(id: String) {
        runningOf(id)?.let(::stop)
        withContext(Dispatchers.IO) {
            val folder = dataOf(id)
            if (folder.exists() && !folder.deleteRecursively()) throw IOException("$folder could not be erased")
        }
        settings.update { it.withoutPlugin(id) }
        sessions.update { it.withoutMembersOf(id) }
        mutableProblems.update { it - id }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

    private fun <T> guardedNow(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

    private class AppPluginHost(private val id: String, private val plugins: AppPlugins) : PluginHost {
        override val http: XHttp get() = plugins.http
        override val store: StoreDir = StoreDir(plugins.dataOf(id))
        override val settings = PluginSettings(id, plugins.settings.state) { change -> plugins.settings.update(change) }
        override val language: String get() = plugins.language.value
        override val online: Flow<Boolean> get() = plugins.online
        override val x: XClient get() = plugins.x
        override val session: StateFlow<Session?> get() = plugins.sessions.state
        override val repairs: StateFlow<Map<String, String>> get() = plugins.repairs
        override val readingJournal: ReadingJournal get() = plugins.readingJournal
        override val readingAppearance: ReadingAppearanceStore get() = plugins.readingAppearance
        override val offline: OfflineStore get() = plugins.offline

        override fun open(route: String) = plugins.navigator.go(Destination.PluginRoute(id, route))

        override fun back() = plugins.navigator.go(Destination.Back)

        override fun openLink(url: String) = plugins.navigator.go(Destination.Link(url))

        override fun openPost(id: String) = plugins.navigator.go(Destination.XPost(id))

        override fun openProfile(screenName: String) = plugins.navigator.go(Destination.XProfile(screenName))

        override fun openMedia(media: List<MediaItem>, start: Int) = openMedia(media, start, null)

        override fun openMedia(media: List<MediaItem>, start: Int, openPost: (() -> Unit)?) =
            plugins.navigator.go(Destination.Media(plugins.media.put(id, media, openPost), start))

        override fun openSearch(query: String?) = plugins.navigator.go(Destination.PluginSearch(id, query))

        override fun openSettings() = plugins.navigator.go(Destination.PluginSettings(id))

        override val saved: StateFlow<Set<String>> get() = plugins.savedIds

        override val savedPosts: StateFlow<List<PluginSavedPost>?> by lazy { plugins.savedPostsOf(id) }

        override fun toggleSaved(post: PluginPostArchive) = Keeping.toggleSave(plugins.graph, post.keepable())

        override fun chooseFolder(post: PluginPostArchive) = plugins.show(PluginSheet.Folders(post))

        override fun editNote(post: PluginPostArchive) = plugins.show(PluginSheet.Note(post))

        override fun setLiked(post: PluginPostArchive, liked: Boolean) = Keeping.setLiked(plugins.graph, post.keepable(), liked)

        override fun likedOnDevice() = Keeping.likedElsewhere(plugins.graph)

        override fun addToGroup(account: String, follow: suspend () -> Unit) =
            plugins.show(PluginSheet.Groups(PluginMembers.memberId(id, account), follow))

        override fun unfollowed(account: String) = plugins.leaveGroups(id, account)

        override fun openPostMenu(menu: PluginPostMenu) = plugins.show(PluginSheet.Menu(id, menu))

        override fun download(media: MediaItem, fileName: String?, subfolder: String?) = plugins.download(id, media, fileName, subfolder)

        override fun saveFile(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String?) =
            Downloading.made(plugins.graph, bytes, PluginDownloads.named(fileName, mimeType), subfolder)

        override fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) =
            Downloading.shareMade(plugins.graph, bytes, PluginDownloads.named(fileName, mimeType), mimeType)

        override fun openDownloads() = plugins.navigator.go(Destination.Downloads)

        override fun tell(text: String) = KeepNotices.say(text)

        override suspend fun <T> working(text: String, work: suspend () -> T): T {
            KeepNotices.show(KeepNotice.Said(text, working = true))
            return try {
                work()
            } finally {
                KeepNotices.endWorking()
            }
        }

        override suspend fun downloadAll(files: List<PluginDownload>, subfolder: String?): List<Boolean> =
            PluginBatches.saveAll(plugins.graph, files, subfolder)

        override suspend fun queueDownloads(files: List<PluginDownload>, subfolder: String?): PluginQueued? =
            PluginBatches.queueAll(plugins.graph, files, subfolder)

        override suspend fun downloadAll(files: List<PluginDownload>, subfolder: String?, progress: (current: Int, total: Int) -> String): List<Boolean> =
            PluginBatches.saveAllShowing(plugins.graph, files, subfolder, progress)

        override suspend fun saveFileAndWait(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String?): Boolean =
            Downloading.madeNow(plugins.graph, bytes, PluginDownloads.named(fileName, mimeType), subfolder)

        override suspend fun <T : Any> withProgress(first: PluginProgress, work: suspend (say: (PluginProgress) -> Unit) -> T): T? =
            PluginProgressing.run(first, work)
    }

    private companion object {
        /** How long a plugin's saved list keeps following Saved once no screen shows it: past a quick back and forth. */
        const val SAVED_LIST_LINGER_MILLIS = 5_000L

        /** How long a plugin is given to say what it follows, for an export or a restore: one that cannot is left as it is. */
        const val ASKED_MILLIS = 5_000L
    }
}
