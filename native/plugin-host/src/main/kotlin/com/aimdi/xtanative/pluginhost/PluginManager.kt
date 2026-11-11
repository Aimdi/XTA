package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How fresh the catalogue the store shows is. */
data class CatalogueStatus(
    /** A refresh is under way. */
    val refreshing: Boolean = false,
    /** The last refresh could not read the catalogue: what is shown is the copy read at [fetchedAt], if there is one. */
    val unreachable: Boolean = false,
    /** When the catalogue shown was read, epoch milliseconds; null when it never was. */
    val fetchedAt: Long? = null,
)

/** What the plugin store shows at one moment. */
data class PluginStoreState(
    /** Installed plugins first, then what is on offer, by category. */
    val plugins: List<PluginState> = emptyList(),
    val catalogue: CatalogueStatus = CatalogueStatus(),
    val showPrivate: Boolean = false,
) {
    val installed: List<PluginState> get() = plugins.filter { it.installed != null }

    /** What is on offer and not installed, by category in the store's order. */
    val onOffer: Map<PluginCategory, List<PluginState>> get() = plugins.filter { it.installed == null }.groupBy { it.category }

    operator fun get(id: String): PluginState? = plugins.firstOrNull { it.id == id }
}

/**
 * What the app's platform gives the host: which file of a plugin it installs,
 * how it checks one was signed with the app's key, how it loads one, and the
 * app's class loader the plugin's classes see through. Android's comes from
 * `:app`; the desktop's is [desktop].
 */
class HostPlatform(
    val platform: PluginPlatform,
    val verifier: PluginVerifier,
    val classLoaders: PluginClassLoaderFactory,
    val parentClassLoader: ClassLoader,
) {
    companion object {
        /** The Linux desktop: JARs signed by the certificate whose SHA-256 is [certificateSha256], loaded with a URLClassLoader. */
        fun desktop(certificateSha256: String, parent: ClassLoader = HostPlatform::class.java.classLoader): HostPlatform =
            HostPlatform(PluginPlatform.DESKTOP, JarSignatureVerifier(certificateSha256), JarClassLoaderFactory, parent)
    }
}

/**
 * The plugin store's engine (PLUGINS.md): reads the catalogue, installs,
 * updates, uninstalls and loads plugins, and tells the UI where every plugin
 * stands through [state].
 *
 * Every function may be called from the UI. Installs, updates, uninstalls
 * and refreshes run on the IO dispatcher in the manager's own [scope], so
 * leaving a screen does not stop one halfway ([cancel] does); a second call
 * while one runs shares it. Failures land in [state], never as exceptions.
 *
 * What is installed stays installed whatever the catalogue says, or when it
 * cannot be read; only [uninstall] removes a plugin, and it first calls
 * [erase], the app's own clean-up of what the plugin stored (its data,
 * settings, home tab and group memberships), as XTA's uninstall does.
 */
class PluginManager(
    private val host: HostPlatform,
    private val api: PluginApi,
    /** Where plugins are kept, in the app's private files: `plugins/`. */
    directory: File,
    store: StoreDir,
    private val erase: suspend (id: String) -> Unit,
    catalogueUrl: String = CATALOGUE_URL,
    /** Where the catalogue is read from at each refresh: [catalogueUrl], unless the app finds another in its settings. */
    private val catalogueAddress: suspend () -> String = { catalogueUrl },
    private val http: XHttp = OkHttpXHttp(),
    artifacts: ArtifactSource = OkHttpArtifactSource(),
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    showPrivate: Boolean = false,
) {
    private data class Inputs(
        val catalogue: PluginCatalogue? = null,
        val catalogueStatus: CatalogueStatus = CatalogueStatus(),
        val installed: Map<String, InstalledPlugin> = emptyMap(),
        val showPrivate: Boolean = false,
        val busy: Map<String, PluginStatus> = emptyMap(),
        val failures: Map<String, PluginFailure> = emptyMap(),
    )

    private val files = PluginFiles(directory)
    private val records = InstalledRecords(store.file("plugins/installed.json"))
    private val cache = CatalogueCache(store.file("plugins/catalogue.json"))
    private val installer = PluginInstaller(files, PluginDownloader(artifacts), host.verifier, host.platform, api)
    private val loader = PluginLoader(host.classLoaders, host.parentClassLoader, api)

    private val lock = Any()
    private var inputs = Inputs(showPrivate = showPrivate)
    private val mutableState = MutableStateFlow(inputs.present())

    /** Every plugin's state, as the store shows it. */
    val state: StateFlow<PluginStoreState> = mutableState.asStateFlow()

    private val runs = ConcurrentHashMap<String, Deferred<Boolean>>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val loaded = ConcurrentHashMap<String, PluginLoad.Loaded>()
    private val opening = Mutex()

    @Volatile
    private var opened = false

    /**
     * Reads the catalogue again, after what is kept on the device (the
     * installed plugins, the catalogue as last read) is in [state]. False when
     * it could not be read: the copy last read stays, and [CatalogueStatus.unreachable] says so.
     */
    suspend fun refresh(): Boolean = shared(REFRESH) {
        open()
        fetchCatalogue()
    }

    /**
     * Installs [id] as the catalogue offers it, or moves an installed [id] to
     * the newer version it offers; true once that is on the device, and at
     * once when it already is.
     */
    suspend fun install(id: String): Boolean = shared(fetchKey(id)) { fetch(id, updateOnly = false) }

    /** Moves the installed [id] to the version the catalogue offers; true once it is there, false when [id] is not installed. */
    suspend fun update(id: String): Boolean = shared(fetchKey(id)) { fetch(id, updateOnly = true) }

    /**
     * Uninstalls [id]: stops a download of it, calls [erase], then deletes its
     * files and record. When [erase] fails nothing is deleted, so trying again
     * finishes the job.
     */
    suspend fun uninstall(id: String): Boolean = shared("uninstall/$id") {
        runs[fetchKey(id)]?.cancelAndJoin()
        remove(id)
    }

    /** Stops an install or update of [id] under way; what it downloaded is kept, to be resumed. */
    fun cancel(id: String) {
        runs[fetchKey(id)]?.cancel()
    }

    /** Shows private plugins on offer, or hides them again; the app keeps the switch with its settings. */
    fun showPrivate(show: Boolean) = change { it.copy(showPrivate = show) }

    /** What is installed, for the app to load at start. */
    suspend fun installed(): List<InstalledPlugin> {
        open()
        return current().installed.values.sortedBy { it.id }
    }

    /**
     * Loads the installed [id]: the instance of its entry class, for the app
     * to cast to XtaPlugin. The same one comes back until an update or an
     * uninstall; after an update the next call loads the new version.
     */
    suspend fun load(id: String): PluginLoad {
        open()
        return lockOf(id).withLock { loaded[id] ?: loadNow(id) }
    }

    /** The bytes [id]'s code takes on the device; what it stored is the app's to count. */
    suspend fun footprint(id: String): Long = withContext(Dispatchers.IO) { files.bytesOf(id) }

    /** Runs [block] in [scope], once at a time under [key]: a call while it runs waits for that run and shares its answer. */
    private suspend fun shared(key: String, block: suspend () -> Boolean): Boolean {
        val run = runs.computeIfAbsent(key) { scope.async(start = CoroutineStart.LAZY) { block() } }
        run.invokeOnCompletion { runs.remove(key, run) }
        run.start()
        return try {
            run.await()
        } catch (e: CancellationException) {
            // The caller's own cancellation goes on; a run that was cancelled did nothing.
            currentCoroutineContext().ensureActive()
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads what is kept on the device into [state], the first time anything
     * asks. Kept files that cannot be read now are tried again on the next
     * call, and are never written over meanwhile: a change re-reads them first.
     */
    private suspend fun open() {
        if (opened) return
        opening.withLock {
            if (opened) return
            val read = quietly {
                val installed = records.all()
                val kept = cache.read()
                change { before ->
                    before.copy(
                        installed = installed,
                        catalogue = before.catalogue ?: kept?.catalogue,
                        catalogueStatus = before.catalogueStatus.copy(fetchedAt = before.catalogueStatus.fetchedAt ?: kept?.fetchedAt),
                    )
                }
            }
            opened = read
        }
    }

    private suspend fun fetchCatalogue(): Boolean {
        change { it.copy(catalogueStatus = it.catalogueStatus.copy(refreshing = true)) }
        val read = try {
            readCatalogue()
        } catch (e: CancellationException) {
            change { it.copy(catalogueStatus = it.catalogueStatus.copy(refreshing = false)) }
            throw e
        }
        val at = now()
        // A copy that cannot be kept only costs the next start without a round trip; what was read is shown either way.
        if (read != null) quietly { cache.write(read.second, at) }
        change { before ->
            before.copy(
                catalogue = read?.first ?: before.catalogue,
                catalogueStatus = CatalogueStatus(unreachable = read == null, fetchedAt = if (read != null) at else before.catalogueStatus.fetchedAt),
            )
        }
        return read != null
    }

    /** The catalogue and the text it came as; null when it cannot be read, or what came is no catalogue. */
    private suspend fun readCatalogue(): Pair<PluginCatalogue, String>? {
        val url = catalogueAddress().toHttpUrlOrNull() ?: return null
        return try {
            val response = http.get(url, emptyMap(), followRedirects = true)
            response.body.takeIf { response.isSuccessful }?.let { body -> PluginCatalogue.parse(body)?.let { it to body } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetch(id: String, updateOnly: Boolean): Boolean {
        open()
        val known = current()
        val installed = known.installed[id]
        val entry = known.catalogue?.entry(id)
        return when {
            installed == null && updateOnly -> false
            // Installed and nothing newer to move to: already done.
            installed != null -> entry?.takeIf { isUpdate(it, installed, host.platform, api) }?.let { fetchAndKeep(it) } ?: true
            entry == null || !isOffered(entry, host.platform, api, known.showPrivate) -> failed(id, PluginFailure.NotOffered)
            else -> fetchAndKeep(entry)
        }
    }

    private suspend fun fetchAndKeep(entry: CatalogueEntry): Boolean {
        val id = entry.id
        change { it.copy(busy = it.busy + (id to PluginStatus.Downloading(0, entry.artifact(host.platform)?.size ?: 0)), failures = it.failures - id) }
        return try {
            val fetched = installer.fetch(
                entry,
                onProgress = { bytes, total -> busy(id, PluginStatus.Downloading(bytes, total)) },
                onChecking = { busy(id, PluginStatus.Installing) },
            )
            when (fetched) {
                is Fetched.Failed -> failed(id, fetched.reason)
                is Fetched.Ready -> withContext(NonCancellable) { keep(fetched) }
            }
        } catch (e: CancellationException) {
            change { it.copy(busy = it.busy - id) }
            throw e
        } catch (e: Exception) {
            failed(id, PluginFailure.StorageFailed("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    /** Puts a checked plugin in place and on record, replacing the version it updates. */
    private suspend fun keep(ready: Fetched.Ready): Boolean = lockOf(ready.entry.id).withLock {
        val plugin = installer.keep(ready, now())
        records.put(plugin)
        withContext(Dispatchers.IO) { files.keepOnly(plugin.id, plugin.version) }
        loaded.remove(plugin.id)
        change { it.copy(installed = it.installed + (plugin.id to plugin), busy = it.busy - plugin.id, failures = it.failures - plugin.id) }
        true
    }

    private suspend fun remove(id: String): Boolean {
        open()
        return try {
            lockOf(id).withLock {
                erase(id)
                withContext(NonCancellable) {
                    records.remove(id)
                    withContext(Dispatchers.IO) { files.remove(id) }
                    loaded.remove(id)
                    change { it.copy(installed = it.installed - id, busy = it.busy - id, failures = it.failures - id) }
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(id, PluginFailure.StorageFailed("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    private suspend fun loadNow(id: String): PluginLoad {
        val plugin = current().installed[id] ?: return PluginLoad.Refused(PluginFailure.NotInstalled)
        val result = withContext(Dispatchers.IO) { loader.load(plugin, files.fileOf(plugin)) }
        when (result) {
            is PluginLoad.Loaded -> loaded[id] = result
            // A refused API already shows in the plugin's status; anything else is news to the store.
            is PluginLoad.Refused -> if (api.refusal(plugin.apiVersion) == null) change { it.copy(failures = it.failures + (id to result.reason)) }
        }
        return result
    }

    private fun busy(id: String, status: PluginStatus) = change { it.copy(busy = it.busy + (id to status)) }

    private fun failed(id: String, reason: PluginFailure): Boolean {
        change { it.copy(busy = it.busy - id, failures = it.failures + (id to reason)) }
        return false
    }

    private fun lockOf(id: String): Mutex = locks.computeIfAbsent(id) { Mutex() }

    private fun current(): Inputs = synchronized(lock) { inputs }

    private fun change(edit: (Inputs) -> Inputs) = synchronized(lock) {
        inputs = edit(inputs)
        mutableState.value = inputs.present()
    }

    private fun Inputs.present() = PluginStoreState(
        plugins = storeListing(catalogue, installed, host.platform, api, showPrivate).map { it.over(busy[it.id], failures[it.id]) },
        catalogue = catalogueStatus,
        showPrivate = showPrivate,
    )

    /** What is under way, or went wrong last, wins over where the plugin otherwise stands. */
    private fun PluginState.over(busy: PluginStatus?, failure: PluginFailure?) =
        copy(status = busy ?: failure?.let { PluginStatus.Failed(it, installed?.version, (status as? PluginStatus.UpdateAvailable)?.available) } ?: status)

    /** Whether [block] went through; what it threw, cancellation aside, is only a false. */
    private suspend fun quietly(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        /** The catalogue CI publishes with the plugins, as an asset of the rolling `native-plugins` release. */
        const val CATALOGUE_URL = "https://github.com/Aimdi/XTA/releases/download/native-plugins/plugins.json"

        private const val REFRESH = "refresh"

        private fun fetchKey(id: String) = "fetch/$id"
    }
}
