package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.ui.common.AppResources
import java.io.File
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
 * The desktop's engine, chosen once: opening libmpv takes tens of
 * milliseconds, so the app does it on a thread of its own once the window is
 * up ([warmUp]), as XTA opens it after its first frame, and the first video
 * does not wait for it; whatever plays sooner chooses it then.
 * XTA_NATIVE_VIDEO_ENGINE names the shim's folder for a build run from
 * Gradle without the app's resources.
 */
object MpvEngine {
    internal val state: MpvEngineState by lazy { load().also { println(MpvEngines.describe(it)) } }

    internal val ready: Boolean get() = state is MpvEngineState.Ready

    /** Chooses the engine on a thread of its own, a few seconds after the app starts. */
    fun warmUp() {
        thread(name = "mpv-engine", isDaemon = true) {
            Thread.sleep(WARM_UP_MS)
            state
        }
    }

    private const val WARM_UP_MS = 3_000L

    private fun load(): MpvEngineState = MpvEngines.choose(
        shim = AppResources.folder(MpvEngines.FOLDER, System.getenv("XTA_NATIVE_VIDEO_ENGINE"))?.let { File(it, System.mapLibraryName(MpvEngines.SHIM)) },
        loadShim = { file -> runCatching { System.load(file.path) }.exceptionOrNull()?.let { it.message ?: it.toString() } },
        open = { library -> MpvJni.text(MpvJni.nativeOpen(MpvJni.cString(library))) },
        version = MpvJni::nativeApiVersion,
    )
}
