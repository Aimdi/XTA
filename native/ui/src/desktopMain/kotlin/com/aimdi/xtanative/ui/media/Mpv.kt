package com.aimdi.xtanative.ui.media

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * libmpv through the app's JNI shim (desktopMain/c/xta_mpv.c), as SherpaJni
 * reaches sherpa-onnx: a few kilobytes beside the app, where JNA would add
 * its dispatch library and classes. The shim links against nothing of mpv's:
 * [nativeOpen] opens the system's library at run time. Strings cross as
 * NUL-terminated UTF-8.
 */
internal object MpvJni {
    @JvmStatic external fun nativeOpen(library: ByteArray): ByteArray?

    @JvmStatic external fun nativeApiVersion(): Long

    @JvmStatic external fun nativeCreate(): Long

    @JvmStatic external fun nativeSetOption(handle: Long, name: ByteArray, value: ByteArray): Int

    @JvmStatic external fun nativeInitialize(handle: Long): Int

    @JvmStatic external fun nativeObserve(handle: Long, id: Long, name: ByteArray, format: Int): Int

    @JvmStatic external fun nativeCommand(handle: Long, args: Array<ByteArray>): Long

    @JvmStatic external fun nativeSetString(handle: Long, name: ByteArray, value: ByteArray): Int

    @JvmStatic external fun nativeSetDouble(handle: Long, name: ByteArray, value: Double): Int

    @JvmStatic external fun nativeSetFlag(handle: Long, name: ByteArray, value: Boolean): Int

    @JvmStatic external fun nativeGetString(handle: Long, name: ByteArray): ByteArray?

    @JvmStatic external fun nativeWaitEvent(handle: Long, timeout: Double, out: DoubleArray): Int

    @JvmStatic external fun nativeWakeup(handle: Long)

    @JvmStatic external fun nativeDestroy(handle: Long)

    @JvmStatic external fun nativeErrorString(error: Int): ByteArray?

    @JvmStatic external fun nativeRenderCreate(handle: Long): Long

    @JvmStatic external fun nativeRenderWait(render: Long, timeoutMs: Int): Boolean

    @JvmStatic external fun nativeRenderWake(render: Long)

    @JvmStatic external fun nativeRenderUpdate(render: Long): Long

    @JvmStatic external fun nativeRender(render: Long, width: Int, height: Int, stride: Int, pixels: Long): Int

    @JvmStatic external fun nativeRenderFree(render: Long)

    /** [value] as the shim reads a string: UTF-8, ended by a NUL. */
    fun cString(value: String): ByteArray = (value.replace('\u0000', ' ') + '\u0000').toByteArray(Charsets.UTF_8)

    fun text(bytes: ByteArray?): String? = bytes?.toString(Charsets.UTF_8)

    fun errorText(error: Int): String = text(nativeErrorString(error)) ?: "error $error"
}

/** One of mpv's events, as the shim hands it over (client.h's mpv_event and the data of the kinds the players read). */
internal data class MpvEvent(
    val id: Int,
    val error: Int = 0,
    /** The property an observation is about, by the number it was observed with. */
    val reply: Long = 0,
    /** A property's new value; NaN while it has none (no file, or not known yet). */
    val value: Double = Double.NaN,
    val endReason: Int = 0,
    val endError: Int = 0,
    /** The playlist entry a file's start or end is about. */
    val entry: Long = 0,
) {
    companion object {
        const val NONE = 0
        const val SHUTDOWN = 1
        const val START_FILE = 6
        const val END_FILE = 7
        const val FILE_LOADED = 8
        const val VIDEO_RECONFIG = 17
        const val PLAYBACK_RESTART = 21
        const val PROPERTY_CHANGE = 22

        const val END_EOF = 0
        const val END_STOP = 2
        const val END_ERROR = 4

        fun of(id: Int, fields: DoubleArray) = MpvEvent(
            id = id,
            error = fields[0].toInt(),
            reply = fields[1].toLong(),
            value = fields[2],
            endReason = fields[3].toInt(),
            endError = fields[4].toInt(),
            entry = fields[5].toLong(),
        )
    }
}

/**
 * One libmpv instance and the thread that is its only caller: commands are
 * queued ([post]) and run there in order, between the events it waits for,
 * which [onEvent] hears on that thread. mpv's async calls may be reordered,
 * so none is used; nothing on the caller's thread waits for mpv. [close]
 * ends the thread, which destroys the instance last.
 *
 * A video player also has [render], the context its frames are drawn with,
 * made before anything plays (mpv's libmpv output needs it). One thread draws
 * with it and, done, frees it ([freeRender]) before it closes the player:
 * mpv must outlive its render context.
 */
internal class Mpv private constructor(
    private val handle: Long,
    render: Long,
    name: String,
    private val onEvent: (Mpv, MpvEvent) -> Unit,
) {
    private val queue = ConcurrentLinkedQueue<(Mpv) -> Unit>()
    private val lock = Any()
    private val renderContext = AtomicLong(render)

    /** The render context, 0 for a player without video or once freed. */
    val render: Long get() = renderContext.get()

    @Volatile
    private var closing = false
    private var destroyed = false

    init {
        thread(name = name, isDaemon = true) { run() }
    }

    /** Runs [command] on the player's thread, after those posted before it; false, and dropped, once the player is closing. */
    fun post(command: (Mpv) -> Unit): Boolean {
        synchronized(lock) {
            if (closing || destroyed) return false
            queue.add(command)
            MpvJni.nativeWakeup(handle)
            return true
        }
    }

    /** Ends the player: what is queued is dropped, and the thread destroys mpv as it leaves. */
    fun close() {
        synchronized(lock) {
            if (closing) return
            closing = true
            if (!destroyed) MpvJni.nativeWakeup(handle)
        }
    }

    /** The calls below run on the player's thread only, from a command or [onEvent]. */
    fun command(vararg args: String): Long = MpvJni.nativeCommand(handle, Array(args.size) { MpvJni.cString(args[it]) })

    fun set(name: String, value: String): Int = MpvJni.nativeSetString(handle, MpvJni.cString(name), MpvJni.cString(value))

    fun set(name: String, value: Double): Int = MpvJni.nativeSetDouble(handle, MpvJni.cString(name), value)

    fun set(name: String, value: Boolean): Int = MpvJni.nativeSetFlag(handle, MpvJni.cString(name), value)

    fun get(name: String): String? = MpvJni.text(MpvJni.nativeGetString(handle, MpvJni.cString(name)))

    fun observe(id: Long, name: String, format: Int): Int = MpvJni.nativeObserve(handle, id, MpvJni.cString(name), format)

    /** Frees the render context, by the thread that draws with it once it no longer does, or by [close]'s end. */
    fun freeRender() {
        val context = renderContext.getAndSet(0)
        if (context != 0L) MpvJni.nativeRenderFree(context)
    }

    private fun run() {
        val fields = DoubleArray(6)
        while (true) {
            while (true) {
                val next = queue.poll() ?: break
                if (!closing) runCatching { next(this) }.onFailure { report(it) }
            }
            if (closing) break
            val id = MpvJni.nativeWaitEvent(handle, -1.0, fields)
            if (id == MpvEvent.SHUTDOWN) break
            if (id != MpvEvent.NONE) runCatching { onEvent(this, MpvEvent.of(id, fields)) }.onFailure { report(it) }
        }
        synchronized(lock) {
            closing = true
            destroyed = true
            queue.clear()
        }
        freeRender()
        MpvJni.nativeDestroy(handle)
    }

    private fun report(error: Throwable) = System.err.println("XTA Native: video: ${error.message ?: error}")

    companion object {
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        /**
         * A player made with [options] and started, its events going to
         * [onEvent], with a render context when it draws [video]; null when
         * mpv would not start one, or would not draw for it. An option mpv
         * does not know is left out, unless the player needs it
         * ([MpvOptions.REQUIRED]).
         */
        fun create(options: List<Pair<String, String>>, name: String, video: Boolean, onEvent: (Mpv, MpvEvent) -> Unit): Mpv? {
            val handle = MpvJni.nativeCreate()
            if (handle == 0L) return null
            val refused = options.filter { (key, value) -> MpvJni.nativeSetOption(handle, MpvJni.cString(key), MpvJni.cString(value)) < 0 }
            refused.forEach { (key, value) -> println("XTA Native: video: mpv refused $key=$value") }
            val started = refused.none { it.first in MpvOptions.REQUIRED } && MpvJni.nativeInitialize(handle) >= 0
            val render = if (started && video) MpvJni.nativeRenderCreate(handle) else 0L
            if (!started || (video && render == 0L)) {
                MpvJni.nativeDestroy(handle)
                return null
            }
            return Mpv(handle, render, name, onEvent)
        }
    }
}
