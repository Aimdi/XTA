package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.ui.common.AppResources
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Whether the desktop plays videos and podcasts in the app, through which libmpv, or why not. */
internal sealed interface MpvEngineState {
    data class Ready(val library: String, val apiVersion: Long) : MpvEngineState

    /** The app hands its videos to the system's player, and plays no podcast itself. */
    data class Missing(val reason: String) : MpvEngineState
}

/**
 * The choice of the desktop's player (XTA's: media_kit, which runs the
 * system's libmpv): the app's JNI shim, carried in its resources, and mpv's
 * library, which the Arch package depends on and the tarball asks for.
 */
internal object MpvEngines {
    /** mpv's library, newest first: libmpv.so.2 since mpv 0.35; libmpv.so.1 before it, as Ubuntu 22.04 ships. */
    val LIBRARIES = listOf("libmpv.so.2", "libmpv.so.1")

    /** Client API 1.109 (mpv 0.33): the software renderer and the playlist entries of a file's events. */
    const val OLDEST_API: Long = (1L shl 16) or 109

    /** The shim's library name, `libxta_mpv.so`. */
    const val SHIM = "xta_mpv"

    /** The folder of the app's resources the shim is in. */
    const val FOLDER = "video-engine"

    /**
     * The engine with the shim at [shim] (null where the app carries none),
     * loaded by [loadShim], and the first of [libraries] that [open] opens
     * (each answers null when it worked, else why not), [version] telling
     * that library's client API. Ready only when all of it is there.
     */
    fun choose(
        shim: File?,
        loadShim: (File) -> String?,
        open: (String) -> String?,
        version: () -> Long,
        libraries: List<String> = LIBRARIES,
    ): MpvEngineState {
        if (shim == null || !shim.isFile) return MpvEngineState.Missing("the app carries no video engine" + (shim?.let { " at ${it.path}" } ?: ""))
        loadShim(shim)?.let { return MpvEngineState.Missing("the video engine did not load: $it") }
        val refusals = ArrayList<String>()
        for (library in libraries) {
            val refused = open(library)
            if (refused != null) {
                refusals += refused
                continue
            }
            val api = version()
            return if (api >= OLDEST_API) {
                MpvEngineState.Ready(library, api)
            } else {
                MpvEngineState.Missing("$library has mpv's client API ${versionText(api)}, older than ${versionText(OLDEST_API)} (mpv 0.33)")
            }
        }
        return MpvEngineState.Missing("no mpv library: " + refusals.joinToString("; "))
    }

    /** A client API version as mpv writes it: 2.2. */
    fun versionText(version: Long): String = "${version shr 16}.${version and 0xffff}"

    /** The line the app logs once it has chosen. */
    fun describe(state: MpvEngineState): String = when (state) {
        is MpvEngineState.Ready -> "XTA Native: video engine ${state.library}, mpv client API ${versionText(state.apiVersion)}"
        is MpvEngineState.Missing -> "XTA Native: video engine missing (${state.reason}): videos open in the system's player"
    }
}

/**
 * The desktop's engine, chosen once, as the first video on screen asks for
 * it ([warmUp], on a thread of its own: opening libmpv takes about 0.1 s and
 * some 30 MB the app does not need while no video is shown). Until then,
 * whether it is there is told without opening it ([expected]); a player
 * made before it is open waits for it. XTA_NATIVE_VIDEO_ENGINE names the
 * shim's folder for a build run from Gradle without the app's resources.
 */
object MpvEngine {
    private val chosen = lazy { load().also { println(MpvEngines.describe(it)) } }
    private val warming = AtomicBoolean(false)

    internal val state: MpvEngineState by chosen

    internal val ready: Boolean get() = state is MpvEngineState.Ready

    /** Whether the engine was opened already: what the log's diagnostics say without opening it. */
    internal val opened: Boolean get() = chosen.isInitialized()

    /** Whether the engine is there to open: the app's shim, and a libmpv the dynamic linker finds ([MpvLibraries]). */
    internal val expected: Boolean by lazy {
        if (opened) ready else shim()?.isFile == true && MpvEngines.LIBRARIES.any { MpvLibraries.findable(it) }
    }

    /** Chooses the engine on a thread of its own, now, once. */
    fun warmUp() {
        if (warming.compareAndSet(false, true)) thread(name = "mpv-engine", isDaemon = true) { state }
    }

    private fun shim(): File? = AppResources.folder(MpvEngines.FOLDER, System.getenv("XTA_NATIVE_VIDEO_ENGINE"))?.let { File(it, System.mapLibraryName(MpvEngines.SHIM)) }

    private fun load(): MpvEngineState = MpvEngines.choose(
        shim = shim(),
        loadShim = { file -> runCatching { System.load(file.path) }.exceptionOrNull()?.let { it.message ?: it.toString() } },
        open = { library -> MpvJni.text(MpvJni.nativeOpen(MpvJni.cString(library))) },
        version = MpvJni::nativeApiVersion,
    )
}

/**
 * Whether the dynamic linker would find a library by its name, as dlopen
 * looks: the folders of LD_LIBRARY_PATH, the linker's cache
 * (/etc/ld.so.cache, which names every library ldconfig found), then the
 * system's own folders. Asked instead of opening libmpv, which loads
 * ffmpeg and the rest with it.
 */
internal object MpvLibraries {
    private val SYSTEM = listOf("/lib64", "/usr/lib64", "/lib", "/usr/lib", "/lib/x86_64-linux-gnu", "/usr/lib/x86_64-linux-gnu")

    fun findable(
        name: String,
        libraryPath: String? = System.getenv("LD_LIBRARY_PATH"),
        cache: File = File("/etc/ld.so.cache"),
        system: List<String> = SYSTEM,
    ): Boolean {
        val folders = libraryPath.orEmpty().split(':').filter { it.isNotEmpty() }
        return folders.any { File(it, name).exists() } || cached(cache, name) || system.any { File(it, name).exists() }
    }

    /** Whether [cache] names [name], as a bare name or the end of a path, among its NUL-ended strings. */
    private fun cached(cache: File, name: String): Boolean {
        val bytes = runCatching { cache.readBytes() }.getOrNull() ?: return false
        val text = String(bytes, Charsets.ISO_8859_1)
        return text.contains("/$name\u0000") || text.contains("\u0000$name\u0000")
    }
}
