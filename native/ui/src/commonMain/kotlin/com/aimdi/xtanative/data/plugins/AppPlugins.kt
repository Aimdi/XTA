package com.aimdi.xtanative.data.plugins

import androidx.compose.runtime.Stable
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
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.home.StripNetwork
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PLUGIN_API_VERSION
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A plugin the app runs: the instance of its entry class, as it was installed. */
@Stable
class RunningPlugin internal constructor(val plugin: XtaPlugin, val installed: InstalledPlugin)

/** What a plugin takes on the device: the [items] it keeps, the bytes of its folder and of its code. */
data class PluginUse(val items: Int, val dataBytes: Long, val codeBytes: Long)

/**
 * The app's downloadable plugins (PLUGINS.md), beside [graph], made by the
 * shell with how its platform checks and loads a plugin ([platform]) and
 * where the store reads what is on offer ([catalogueUrl]; a stand-in build
 * names a local one), then started. [manager] installs, updates and
 * uninstalls them; what is installed is loaded at start and after each
 * install, cast to [XtaPlugin], given its [PluginHost] and listed in
 * [running], which Home's strip, the links and the plugin screens read
 * (through LocalPlugins).
 *
 * A plugin's code is kept under `plugins/` of the app's private folder
 * ([files]) and what it stores under `plugin-data/<id>/`. Uninstalling erases
 * that folder, what Home keeps of it (its place on the strip, its top bar's
 * pins) and its accounts' group memberships (member ids "<id>:…"), as XTA's
 * uninstall does, before the manager deletes its code.
 */
class AppPlugins(
    private val files: File,
    graph: AppGraph,
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
    val prefs = PluginPrefs(store.file("plugins/prefs.json"))

    val manager = PluginManager(
        host = platform,
        api = PluginApi(PLUGIN_API_VERSION),
        directory = File(files, "plugins"),
        store = store,
        erase = ::erase,
        catalogueUrl = catalogueUrl,
        http = http,
    )

    /** Where a plugin's navigation goes: the screens attach to it. */
    val navigator = AppNavigator()

    /** The media a plugin opened full screen, for the viewer to show. */
    val media = MediaShelf()

    /** Where the screens go once they are up, instead of staying on Home: CI's desktop check opens the store or a plugin so. */
    @Volatile
    var openAtStart: Destination? = null

    private val mutableRunning = MutableStateFlow<List<RunningPlugin>>(emptyList())

    /** The plugins running now, by id. */
    val running: StateFlow<List<RunningPlugin>> = mutableRunning.asStateFlow()

    private val mutableProblems = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Installed plugins that could not be started, with why: for the log and the store. */
    val startProblems: StateFlow<Map<String, String>> = mutableProblems.asStateFlow()

    private val language = MutableStateFlow(Locale.getDefault().toLanguageTag())

    /** The running plugins with a home tab, which Home's strip can show, named in the reader's language. */
    val stripNetworks: StateFlow<List<StripNetwork>> = combine(running, language) { list, tag ->
        list.filter { it.plugin.homeTab != null }.map { StripNetwork(it.plugin.id, guardedNow { it.plugin.title(tag) } ?: it.plugin.id) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The plugins the reader put on Home's strip, in their order. */
    val stripPins: Flow<List<String>> = settings.settings.map { it[HomePrefs.feedStripNetworks] }.distinctUntilChanged()

    private val changing = Mutex()

    /** Reads what is installed and starts it, then follows every install, update and uninstall. */
    fun start() {
        scope.launch {
            prefs.load()
            manager.showPrivate(prefs.state.value.showPrivate)
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

    /** Opens [url] on the screen of a running plugin that reads it, answering whether one did: the app's links ask this first. */
    fun openOnScreen(url: String): Boolean {
        val destination = destinationFor(url) ?: return false
        navigator.go(destination)
        return true
    }

    internal fun dataOf(id: String): File = File(files, "plugin-data/$id")

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

        override fun openMedia(media: List<MediaItem>, start: Int) = plugins.navigator.go(Destination.Media(plugins.media.put(media), start))

        override fun openSearch(query: String?) = plugins.navigator.go(Destination.PluginSearch(id, query))

        override fun openSettings() = plugins.navigator.go(Destination.PluginSettings(id))
    }
}
