package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.DBusType
import com.aimdi.xtanative.ui.media.mpris.ObjectPath
import com.aimdi.xtanative.ui.media.mpris.Variant
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/** The files a chooser offers: those whose extension is one of [extensions], shown as [name]. */
data class FileFilter(val name: String, val extensions: Set<String>) {
    /** Whether [fileName] is one of this filter's. */
    fun accepts(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in extensions

    /** The portal's `(sa(us))`: [name] with a glob (type 0) per extension, matching any case. */
    fun toPortal(): List<Any?> = listOf(name, extensions.sorted().map { listOf(0L, glob(it)) })

    companion object {
        /** The pictures the app reads. */
        val PICTURE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

        /** `*.[pP][nN][gG]` for "png": portals match globs case by case. */
        fun glob(extension: String): String = "*." + extension.lowercase().map { c ->
            if (c.isLetter()) "[$c${c.uppercaseChar()}]" else c.toString()
        }.joinToString("")
    }
}

/** What the reader is asked for: a file to open, a place to save one, or a folder. */
enum class FileAskKind { OPEN, SAVE, FOLDER }

/** One chooser: titled [title], over [filter]'s files (any without one), opening in [folder], with [name] filled in to save. */
data class FileAsk(
    val title: String,
    val kind: FileAskKind,
    val filter: FileFilter? = null,
    val name: String? = null,
    val folder: File? = null,
)

/** How a chooser ended. */
sealed interface FileChoice {
    /** The reader picked [file]. */
    data class Picked(val file: File) : FileChoice

    /** The reader closed the chooser: nothing else opens. */
    data object Cancelled : FileChoice

    /** No portal could ask (none on the bus, too old, or it failed): the app's own dialog asks instead. */
    data object Unavailable : FileChoice
}

/**
 * The desktop's file chooser portal (org.freedesktop.portal.FileChooser),
 * as plain decisions: the request's object path, the options of OpenFile
 * and SaveFile, and what its Response signal says.
 */
object PortalFiles {
    const val PORTAL = "org.freedesktop.portal.Desktop"
    const val PATH = "/org/freedesktop/portal/desktop"
    const val CHOOSER = "org.freedesktop.portal.FileChooser"
    const val REQUEST = "org.freedesktop.portal.Request"
    const val PROPERTIES = "org.freedesktop.DBus.Properties"

    /** Every Request.Response this connection may be sent; [PortalFileChooser] keeps its own by path. */
    const val MATCH = "type='signal',interface='$REQUEST',member='Response'"

    /** The bus saying the portal's name changed hands: the portal asked went away (it crashed or was restarted). */
    const val OWNER_MATCH = "type='signal',sender='${DBusConnection.BUS}',interface='${DBusConnection.BUS}',member='NameOwnerChanged',arg0='$PORTAL'"

    /** The connection a NameOwnerChanged of the portal's name says lost it; null for any other signal or none. */
    fun lostOwner(signal: DBusMessage): String? {
        if (signal.sender != DBusConnection.BUS || signal.iface != DBusConnection.BUS || signal.member != "NameOwnerChanged") return null
        if (signal.body.getOrNull(0) != PORTAL) return null
        return (signal.body.getOrNull(1) as? String)?.takeIf { it.isNotEmpty() }
    }

    /** The unique name GetNameOwner answered; null for an error or none. */
    fun owner(answer: DBusMessage?): String? =
        answer?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull() as? String

    /** The first version whose OpenFile chooses folders and opens in a given one. */
    const val FOLDERS_SINCE = 3L

    private val tokens = AtomicLong(0)

    /** A handle_token of this process's, new each time. */
    fun token(): String = "xta_native_${ProcessHandle.current().pid()}_${tokens.incrementAndGet()}"

    /** The path the portal's request for [token] lives at, for the connection named [unique] (":1.42" → "1_42"). */
    fun requestPath(unique: String, token: String): String = "$PATH/request/${unique.removePrefix(":").replace('.', '_')}/$token"

    /** SaveFile to save, OpenFile for the rest. */
    fun method(ask: FileAsk): String = if (ask.kind == FileAskKind.SAVE) "SaveFile" else "OpenFile"

    /** Whether a portal of [version] can ask [ask]: any for files, [FOLDERS_SINCE] for a folder; none when it said no version. */
    fun usable(version: Long?, ask: FileAsk): Boolean = version != null && (ask.kind != FileAskKind.FOLDER || version >= FOLDERS_SINCE)

    /** The `a{sv}` options of [ask], its request named [token]. */
    fun options(ask: FileAsk, token: String): Map<String, Variant> = buildMap {
        put("handle_token", Variant("s", token))
        put("modal", Variant("b", true))
        if (ask.kind == FileAskKind.FOLDER) put("directory", Variant("b", true))
        ask.filter?.toPortal()?.let { filter ->
            put("filters", Variant("a(sa(us))", listOf(filter)))
            put("current_filter", Variant("(sa(us))", filter))
        }
        if (ask.kind == FileAskKind.SAVE) ask.name?.let { put("current_name", Variant("s", it)) }
        ask.folder?.takeIf { it.isAbsolute && it.isDirectory }?.let { put("current_folder", Variant("ay", pathBytes(it))) }
    }

    /** [file]'s path as the portal's `ay` takes it: its bytes, NUL-terminated. */
    fun pathBytes(file: File): ByteArray = (file.path + "\u0000").toByteArray(Charsets.UTF_8)

    /** What a Response says: 0 with a `file:` URI picked it, 1 the reader cancelled, anything else no answer. */
    fun result(response: DBusMessage): FileChoice {
        val code = response.body.getOrNull(0) as? Long ?: return FileChoice.Unavailable
        return when (code) {
            0L -> {
                val uris = ((response.body.getOrNull(1) as? Map<*, *>)?.get("uris") as? Variant)?.value as? List<*>
                (uris?.firstOrNull() as? String)?.let(::fileOf)?.let { FileChoice.Picked(it) } ?: FileChoice.Unavailable
            }
            1L -> FileChoice.Cancelled
            else -> FileChoice.Unavailable
        }
    }

    /** The file a `file:` URI names; null for any other. */
    fun fileOf(uri: String): File? = runCatching { URI(uri).takeIf { it.scheme == "file" }?.let(::File) }.getOrNull()

    /** The portal's version from Properties.Get's answer; null for an error or none. */
    fun version(answer: DBusMessage?): Long? =
        answer?.takeIf { it.type == DBusType.METHOD_RETURN }?.body?.firstOrNull()?.let { (it as? Variant)?.value ?: it } as? Long
}

/**
 * Asks the desktop's file chooser portal over a connection of its own
 * ([connect], the session bus in the app), so the desktop's own dialog
 * opens (GNOME's, KDE's), over the reader's window [ask] is given. The
 * Response signal is listened for before the call is made and kept by
 * path, so one sent before the call's answer, or for a handle an old
 * portal chose itself, is not lost. A chooser given up (the caller
 * cancelled, no answer in [callMs]) closes the portal's request. A portal
 * that goes away while its dialog is open (it crashed) ends the wait as
 * no answer, so the chooser does not wait for ever and the next can open.
 */
class PortalFileChooser(
    private val connect: () -> DBusConnection?,
    private val callMs: Long = 25_000,
) {
    /** How [ask] ended, the dialog over the window [parent] names ("x11:2a00007", or "" for none). */
    suspend fun ask(ask: FileAsk, parent: String): FileChoice = withContext(Dispatchers.IO) {
        val bus = connect() ?: return@withContext FileChoice.Unavailable
        try {
            asked(bus, ask, parent)
        } finally {
            bus.close()
        }
    }

    private suspend fun asked(bus: DBusConnection, ask: FileAsk, parent: String): FileChoice {
        val responses = Responses()
        bus.onSignal = responses::offer
        bus.onClosed = responses::end
        // Listened for before the portal is reached (and perhaps started by it), so its going away is never missed.
        blocking { bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(PortalFiles.OWNER_MATCH), callMs) }
        val version = PortalFiles.version(blocking { bus.call(PortalFiles.PORTAL, PortalFiles.PATH, PortalFiles.PROPERTIES, "Get", "ss", listOf(PortalFiles.CHOOSER, "version"), callMs) })
        if (!PortalFiles.usable(version, ask)) return FileChoice.Unavailable
        val owner = PortalFiles.owner(blocking { bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "GetNameOwner", "s", listOf(PortalFiles.PORTAL), callMs) })
            ?: return FileChoice.Unavailable
        responses.portal(owner)
        blocking { bus.call(DBusConnection.BUS, DBusConnection.BUS_PATH, DBusConnection.BUS, "AddMatch", "s", listOf(PortalFiles.MATCH), callMs) }
        val token = PortalFiles.token()
        val expected = PortalFiles.requestPath(bus.uniqueName.orEmpty(), token)
        var handle = expected
        var answered = false
        try {
            val reply = blocking {
                bus.call(PortalFiles.PORTAL, PortalFiles.PATH, PortalFiles.CHOOSER, PortalFiles.method(ask), "ssa{sv}", listOf(parent, ask.title, PortalFiles.options(ask, token)), callMs)
            }
            if (reply?.type != DBusType.METHOD_RETURN) {
                answered = reply != null
                return FileChoice.Unavailable
            }
            // Portals before 0.9 chose the handle themselves.
            handle = (reply.body.firstOrNull() as? ObjectPath)?.path ?: expected
            val response = responses.await(handle) ?: return FileChoice.Unavailable
            answered = true
            return PortalFiles.result(response)
        } finally {
            if (!answered) close(bus, handle)
        }
    }

    /** Tells the portal to close the request at [handle]: its dialog goes away. */
    private fun close(bus: DBusConnection, handle: String) {
        bus.send(DBusMessage(DBusType.METHOD_CALL, path = handle, iface = PortalFiles.REQUEST, member = "Close", destination = PortalFiles.PORTAL, noReply = true))
    }

    /** A blocking bus call the caller's cancelling interrupts (the call then answers null). */
    private suspend fun <T> blocking(call: () -> T): T = runInterruptible(Dispatchers.IO) { call() }

    /** Response signals by request path, kept until asked for, and the end of the connection or of the portal. */
    private class Responses {
        private val byPath = ConcurrentHashMap<String, CompletableDeferred<DBusMessage>>()
        private val ended = CompletableDeferred<Unit>()

        /** The portal's connections that lost its name, read before the one asked was known. */
        private val gone = ConcurrentHashMap.newKeySet<String>()

        @Volatile
        private var owner: String? = null

        /** The portal asked is the connection [unique]: its going away ends the wait. */
        fun portal(unique: String) {
            owner = unique
            if (unique in gone) end()
        }

        fun offer(signal: DBusMessage) {
            PortalFiles.lostOwner(signal)?.let { lost ->
                gone += lost
                if (lost == owner) end()
                return
            }
            if (signal.iface != PortalFiles.REQUEST || signal.member != "Response") return
            val path = signal.path ?: return
            byPath.computeIfAbsent(path) { CompletableDeferred() }.complete(signal)
        }

        fun end() {
            ended.complete(Unit)
        }

        /** The Response for [path], once it comes; null when the bus went away first. */
        suspend fun await(path: String): DBusMessage? {
            val response = byPath.computeIfAbsent(path) { CompletableDeferred() }
            return select {
                response.onAwait { it }
                ended.onAwait { null }
            }
        }
    }
}
