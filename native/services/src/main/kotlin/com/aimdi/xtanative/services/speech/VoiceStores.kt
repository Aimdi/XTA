package com.aimdi.xtanative.services.speech

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An installed voice and the folder it loads from. */
data class InstalledVoice(val voice: OfflineVoice, val path: File)

/**
 * The engine that runs downloaded voices, on this device (voice_engine_store.dart).
 * On Android it is downloaded, and only a tap downloads it: on its own row,
 * or on a voice that needs it. The Linux build carries it ([bundled]).
 * [engine] is what this device downloads; null when the app carries it or
 * none is published for the processor.
 */
class VoiceEngineStore(
    val engine: VoiceEnginePackage?,
    val bundled: Boolean,
    private val root: File,
    private val transfer: VoiceTransfer,
    private val reader: ArchiveReader,
    private val urls: (VoiceEnginePackage) -> List<String>,
) {
    private val current = MutableStateFlow<VoiceInstall>(VoiceInstall.Absent)
    val state: StateFlow<VoiceInstall> = current.asStateFlow()

    private val lock = Mutex()
    private var loaded = false
    private var running: CompletableDeferred<Boolean>? = null

    @Volatile
    private var cancellation: VoiceCancellation? = null

    /** Whether this device can have the engine at all. */
    val available: Boolean get() = bundled || engine != null

    /** Whether a downloaded voice can read now. */
    val usable: Boolean get() = bundled || current.value is VoiceInstall.Ready

    /** The folder to load the engine from; null to load the one that came with the app. */
    val loadFrom: File? get() = (current.value as? VoiceInstall.Ready)?.path?.takeIf { !bundled }

    /** Reads whether the engine is already on the device, once. Local files only. */
    suspend fun ensureLoaded() = lock.withLock {
        if (loaded) return@withLock
        loaded = true
        val engine = engine ?: return@withLock
        if (bundled) return@withLock
        if (current.value is VoiceInstall.Absent && withContext(Dispatchers.IO) { VoiceArchives.engineFilesPresent(root, engine) }) {
            current.value = VoiceInstall.Ready(VoiceEngines.directory(root, engine), engine.installedBytes)
        }
    }

    /**
     * Downloads, checks and installs the engine from each of its addresses
     * in turn until one installs, and answers whether it is ready. A call
     * while one runs waits for that one; cancelling [linked] (a voice's
     * download) cancels this one too.
     */
    suspend fun download(linked: VoiceCancellation? = null): Boolean {
        if (bundled) return true
        val engine = engine ?: return false
        ensureLoaded()
        if (current.value is VoiceInstall.Ready) return true
        val (mine, result) = lock.withLock {
            running?.let { false to it } ?: (true to CompletableDeferred<Boolean>().also { running = it })
        }
        if (!mine) return result.await()
        try {
            return run(engine, linked).also(result::complete)
        } catch (error: Throwable) {
            result.completeExceptionally(error)
            throw error
        } finally {
            lock.withLock { running = null }
        }
    }

    private suspend fun run(engine: VoiceEnginePackage, linked: VoiceCancellation?): Boolean {
        val cancellation = VoiceCancellation().also { cancellation = it }
        linked?.onCancel(cancellation::cancel)
        current.value = VoiceInstall.Downloading(0, null)
        try {
            root.mkdirs()
            var outcome: VoiceInstall = VoiceInstall.Failed(VoiceFailure.NETWORK)
            for (url in urls(engine)) {
                outcome = downloadArchive(
                    transfer = transfer,
                    url = url,
                    expectedBytes = engine.archiveBytes,
                    cancellation = cancellation,
                    install = { archive ->
                        withContext(Dispatchers.IO) {
                            VoiceArchives.installEngine(archive, engine, root, reader)
                            installed(engine)
                        }
                    },
                    discard = { withContext(Dispatchers.IO) { VoiceEngines.directory(root, engine).deleteRecursively() } },
                    report = { current.value = it },
                )
                if (outcome is VoiceInstall.Ready || outcome is VoiceInstall.Absent) break
            }
            current.value = outcome
            return outcome is VoiceInstall.Ready
        } finally {
            this.cancellation = null
        }
    }

    private fun installed(engine: VoiceEnginePackage): VoiceInstall.Ready? =
        if (VoiceArchives.engineFilesPresent(root, engine)) VoiceInstall.Ready(VoiceEngines.directory(root, engine), engine.installedBytes) else null

    /** Stops a running download; what it fetched so far is thrown away. */
    fun cancel() {
        cancellation?.cancel()
    }

    suspend fun delete() {
        cancel()
        val engine = engine ?: return
        withContext(Dispatchers.IO) { VoiceEngines.directory(root, engine).deleteRecursively() }
        current.value = VoiceInstall.Absent
    }
}

/**
 * The downloadable voices and where each stands, by voice id
 * (voice_download_store.dart). Nothing touches the network until
 * [download], which only a tap calls; a voice that needs the engine fetches
 * it first when it is not on the device.
 */
class VoiceDownloadStore(
    val engine: VoiceEngineStore,
    private val root: File,
    private val transfer: VoiceTransfer,
    private val reader: ArchiveReader,
    val catalog: List<OfflineVoice> = OfflineVoices.CATALOG,
) {
    private val current = MutableStateFlow<Map<String, VoiceInstall>>(emptyMap())
    val state: StateFlow<Map<String, VoiceInstall>> = current.asStateFlow()

    private val lock = Mutex()
    private var loaded = false
    private val cancellations = ConcurrentHashMap<String, VoiceCancellation>()

    fun installOf(id: String): VoiceInstall = current.value[id] ?: VoiceInstall.Absent

    /** Reads which voices, and whether the engine, are already on the device, once. Local files only. */
    suspend fun ensureLoaded() {
        lock.withLock {
            if (!loaded) {
                loaded = true
                val found = withContext(Dispatchers.IO) { catalog.mapNotNull { voice -> installed(voice)?.let { voice.id to it } }.toMap() }
                current.update { found + it }
            }
        }
        engine.ensureLoaded()
    }

    private fun installed(voice: OfflineVoice): VoiceInstall.Ready? {
        val path = VoiceArchives.voiceDirectory(root, voice)
        return if (VoiceArchives.filesPresent(path, voice)) VoiceInstall.Ready(path, VoiceArchives.directoryBytes(path)) else null
    }

    /** The installed voice that should read [language], if any; none reads while the engine it needs is missing. */
    suspend fun installedFor(language: String?): InstalledVoice? {
        ensureLoaded()
        if (!engine.usable) return null
        val ready = catalog.mapNotNull { voice -> (installOf(voice.id) as? VoiceInstall.Ready)?.let { voice to it.path } }.toMap()
        return OfflineVoices.pick(ready.keys, language)?.let { InstalledVoice(it, ready.getValue(it)) }
    }

    private fun set(id: String, install: VoiceInstall) = current.update { it + (id to install) }

    /** Downloads, checks and installs [voice], the engine first when it is needed and missing. */
    suspend fun download(voice: OfflineVoice) {
        val cancellation = VoiceCancellation()
        if (cancellations.putIfAbsent(voice.id, cancellation) != null) return
        try {
            ensureLoaded()
            set(voice.id, VoiceInstall.Downloading(0, null))
            if (!engine.download(cancellation)) {
                set(voice.id, (engine.state.value as? VoiceInstall.Failed)?.let { VoiceInstall.Failed(it.reason) } ?: VoiceInstall.Absent)
                return
            }
            root.mkdirs()
            val outcome = downloadArchive(
                transfer = transfer,
                url = voice.url,
                expectedBytes = voice.archiveBytes,
                cancellation = cancellation,
                install = { archive ->
                    withContext(Dispatchers.IO) {
                        VoiceArchives.install(archive, voice, root, reader)
                        installed(voice)
                    }
                },
                discard = { withContext(Dispatchers.IO) { VoiceArchives.voiceDirectory(root, voice).deleteRecursively() } },
                report = { set(voice.id, it) },
            )
            set(voice.id, outcome)
        } finally {
            cancellations.remove(voice.id)
        }
    }

    /** Stops a running download; what it fetched so far is thrown away. */
    fun cancel(id: String) {
        cancellations[id]?.cancel()
    }

    suspend fun delete(voice: OfflineVoice) {
        cancel(voice.id)
        withContext(Dispatchers.IO) { VoiceArchives.voiceDirectory(root, voice).deleteRecursively() }
        set(voice.id, VoiceInstall.Absent)
    }
}
