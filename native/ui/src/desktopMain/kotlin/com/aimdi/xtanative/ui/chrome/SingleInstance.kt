package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.ui.links.LaunchPlace
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * What a later start of the app asks of the copy already running, one line
 * over its socket. A copy that does not know a line ignores it and gives no
 * answer, so a later start with a link can fall back to [Raise].
 */
sealed interface InstanceMessage {
    /** The line this message travels as. */
    val line: String

    /** Bring the window forward: XTA's second start presents the running window. */
    data object Raise : InstanceMessage {
        override val line: String = "raise"
    }

    /**
     * Only make sure a copy runs, its window left as it is: what a start from
     * the session's autostart (`--minimized`) asks, so a copy already open, or
     * hidden in the tray, is not brought up at login. A copy too old to know
     * it gives no answer, and is left alone.
     */
    data object Hello : InstanceMessage {
        override val line: String = "hello"
    }

    /** Open [url] (an X link, or any page a plugin reads), as a link another app opens here, and bring the window forward. */
    data class Open(val url: String) : InstanceMessage {
        override val line: String = "$OPEN$url"
    }

    /** Ask the reader about the file at [path] (absolute): an XTA backup to restore, an OPML list to follow; and bring the window forward. */
    data class OpenFile(val path: String) : InstanceMessage {
        override val line: String = "$FILE$path"
    }

    /** Show [place] (Saved, the settings, the deck), as the desktop entry's actions ask, and bring the window forward. */
    data class Show(val place: LaunchPlace) : InstanceMessage {
        override val line: String = "$SHOW${place.key}"
    }

    /** Whether a copy too old to know this message is asked to raise its window instead: all but a [Hello], which leaves it alone. */
    val raisesWhenUnknown: Boolean get() = this !is Hello && this !is Raise

    companion object {
        private const val OPEN = "open "
        private const val FILE = "file "
        private const val SHOW = "show "

        /** The longest link a later start hands over; anything longer is no link a reader typed or clicked. */
        const val MAX_URL = 4_096

        /** The message [line] carries; null for anything else, which the running copy ignores. */
        fun parse(line: String?): InstanceMessage? {
            val text = line?.trim() ?: return null
            return when {
                text == Raise.line -> Raise
                text == Hello.line -> Hello
                text.startsWith(OPEN) -> link(text.removePrefix(OPEN))?.let(::Open)
                text.startsWith(FILE) -> path(text.removePrefix(FILE))?.let(::OpenFile)
                text.startsWith(SHOW) -> LaunchPlace.of(text.removePrefix(SHOW))?.let(::Show)
                else -> null
            }
        }

        /**
         * The schemes besides the web's that the desktop entry claims
         * (`x-scheme-handler/…`), whose links a plugin opens: `pixiv://`, where
         * Pixiv's sign-in in the browser sends its one-time code back.
         */
        val APP_SCHEMES: Set<String> = setOf("pixiv")

        /**
         * The link among a start's [args] (`xta-native <url>`, as a
         * desktop entry's %U passes it): the first http, https or
         * [APP_SCHEMES] one, null when there is none, so a start without one
         * only raises the window.
         */
        fun linkIn(args: Array<String>): String? = args.firstNotNullOfOrNull(::link)

        /** [text] as a file's path to hand over: absolute, of no line breaks or control characters, else null. */
        private fun path(text: String): String? = text.takeIf { path ->
            path.length <= MAX_URL && path.startsWith("/") && path.none { it.isISOControl() }
        }

        /** [text] as a link to hand over: one http(s) or [APP_SCHEMES] address of no spaces, else null. */
        private fun link(text: String): String? = text.trim().takeIf { url ->
            url.length <= MAX_URL &&
                (url.startsWith("https://") || url.startsWith("http://") || APP_SCHEMES.any { url.startsWith("$it://") }) &&
                url.none { it.isWhitespace() || it.isISOControl() }
        }
    }
}

/**
 * One copy of the app at a time, as XTA is a unique GTK application: the
 * first start holds a lock in the data folder and listens on a local socket
 * in `$XDG_RUNTIME_DIR/xta-native`, named after that folder, and on the old
 * one beside the lock, which copies from before the move ask (each only
 * where its path is short enough); a later start finds the lock taken, asks the first to raise its
 * window and ends once it answers, rather than opening a second window over
 * the same settings and database. A copy that is ending stops answering, and
 * a start that no copy answers waits for its lock and runs. Where the folder cannot be locked, each start runs
 * alone, as XTA does without a session bus.
 */
class SingleInstance private constructor(
    private val lockChannel: FileChannel?,
    private val lock: FileLock?,
    private val servers: List<Pair<ServerSocketChannel, File>>,
) : AutoCloseable {
    /** Hands each message a later start sends to [onMessage], on a thread of its own, until [close]. */
    fun listen(onMessage: (InstanceMessage) -> Unit) {
        servers.forEach { (server, _) -> listen(server, onMessage) }
    }

    private fun listen(server: ServerSocketChannel, onMessage: (InstanceMessage) -> Unit) {
        Thread({
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { channel ->
                        val message = InstanceMessage.parse(Channels.newReader(channel, Charsets.UTF_8).buffered().readLine())
                        // A hello asks nothing of the app: it is answered here, the window left as it is.
                        if (message != null && message != InstanceMessage.Hello) onMessage(message)
                        // The later start ends only on this answer: a copy that is closing has stopped listening, and gives none.
                        if (message != null) channel.write(ByteBuffer.wrap("$HEARD\n".toByteArray(Charsets.UTF_8)))
                    }
                }
            }
        }, "xta-native-instance").apply { isDaemon = true }.start()
    }

    /** Whether [close] ran: the lock is let go, and the socket paths may already be another copy's. */
    private var closed = false

    /** Whether the sockets are still open and their files in place; both go once. */
    private var serving = true

    /** Stops answering later starts as soon as the app begins to end, before its window and writes are done; its socket goes with it, the lock still held. */
    private fun closeOnExit() {
        if (servers.isEmpty()) return
        runCatching { Runtime.getRuntime().addShutdownHook(Thread(::endOnExit, "xta-native-instance-exit")) }
    }

    /**
     * The shutdown hook's step: stop serving while the lock is still held. A
     * no-op once [close] ran, as Main's quit does before `exitProcess`: by
     * then a copy that took over may have bound its socket at the same path.
     */
    @Synchronized
    internal fun endOnExit() {
        if (!closed) stopServing()
    }

    /** Closes the sockets and deletes their files, once, only ever with the lock held. */
    private fun stopServing() {
        if (!serving) return
        serving = false
        servers.forEach { (server, socket) ->
            runCatching { server.close() }
            socket.delete()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        stopServing()
        closed = true
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
    }

    companion object {
        private const val LOCK_FILE = "instance.lock"

        /** Where copies before the socket moved to the runtime folder listened, beside the lock; still asked, and served too, so those copies reach this one. */
        private const val OLD_SOCKET_FILE = "instance.socket"

        /** The runtime folder's own subfolder for the app's sockets, the reader's alone (0700). */
        private const val RUNTIME_FOLDER = "xta-native"

        /** The longest path a socket may have: sun_path's 108 bytes, its ending zero among them. */
        private const val MAX_SOCKET_PATH = 107

        /** How long a later start keeps trying to reach a first copy that is still starting up. */
        private const val REACH_MILLIS = 3_000L

        /** How long a later start that no copy answered waits for the lock of one that is ending (quit, then started again at once). */
        private const val TAKE_OVER_MILLIS = 10_000L

        /** How long one try waits for the running copy's answer. */
        private const val ANSWER_MILLIS = 2_000L

        /** The running copy's answer once it has done what it was asked (raised its window). */
        private const val HEARD = "raised"

        /** How a running copy took a message. */
        private enum class Reply {
            /** It did what was asked. */
            HEARD,

            /** It runs but does not know the message: it read it and closed without an answer, as copies too old for it do. */
            IGNORED,

            /** No copy listens, or none answered in time: it is still starting, or ending. */
            NONE,
        }

        /** `$XDG_RUNTIME_DIR`, where the session keeps sockets; null where it is unset or not an absolute folder. */
        fun runtimeFolder(): File? = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isAbsolute && it.isDirectory }

        /**
         * The sockets a copy on [folder] may listen on, the one to serve first:
         * one in [runtime]'s own folder, named after [folder] (so copies on two
         * data folders never take each other's), then the old one beside the
         * lock. Only paths short enough for a socket count.
         */
        internal fun socketsFor(folder: File, runtime: File?): List<File> = listOfNotNull(
            runtime?.let { File(File(it, RUNTIME_FOLDER), "instance-${folderKey(folder)}.socket") },
            File(folder, OLD_SOCKET_FILE),
        ).filter { it.path.toByteArray(Charsets.UTF_8).size <= MAX_SOCKET_PATH }

        /** A short name for [folder]: the start of its full path's SHA-256, the same for each spelling of it. */
        internal fun folderKey(folder: File): String {
            val path = runCatching { folder.canonicalPath }.getOrDefault(folder.absolutePath)
            return MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
        }

        /**
         * This start's claim on [folder]: the running copy's when it is the
         * first, else null once the copy already running has been handed
         * [message] (raise its window, open a link and raise it, or only hear
         * that a start was made), and this start should end. A running copy too
         * old to open links is asked to raise its window instead; one too old to
         * answer a hello is left alone. The socket lives in [runtime]
         * (`$XDG_RUNTIME_DIR`), the lock in [folder].
         */
        fun claim(folder: File, message: InstanceMessage = InstanceMessage.Raise, runtime: File? = runtimeFolder()): SingleInstance? {
            val sockets = socketsFor(folder, runtime)
            if (sockets.isEmpty()) System.err.println("XTA Native: no socket path is short enough under ${runtime ?: folder}; a later start cannot reach this copy.")
            val first = take(folder, sockets)
            if (first != null) return first
            when (tell(sockets, message)) {
                Reply.HEARD -> return null
                // A copy too old to open links raises its window instead; one too old for a hello is left alone.
                Reply.IGNORED -> if (!message.raisesWhenUnknown || tell(sockets, InstanceMessage.Raise) != Reply.NONE) return null
                Reply.NONE -> Unit
            }
            // Nobody answered: the copy holding the lock is ending. This start runs once it has.
            val until = System.currentTimeMillis() + TAKE_OVER_MILLIS
            while (System.currentTimeMillis() < until) {
                Thread.sleep(100)
                take(folder, sockets)?.let { return it }
            }
            System.err.println("XTA Native: another copy holds ${File(folder, LOCK_FILE)} but does not answer; this start ends.")
            return null
        }

        /** The lock on [folder] taken, and this copy listening; a copy without the lock where the folder cannot be locked at all; null while another copy holds it. */
        private fun take(folder: File, sockets: List<File>): SingleInstance? {
            val channel = try {
                FileChannel.open(File(folder, LOCK_FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (_: IOException) {
                return SingleInstance(null, null, emptyList())
            }
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // Held in this very process (a test's second start).
                null
            } catch (_: IOException) {
                return SingleInstance(channel, null, emptyList())
            }
            if (lock == null) {
                channel.close()
                return null
            }
            // With the lock held no copy listens on these: an old one left behind by a copy that crashed goes.
            sockets.forEach { runCatching { Files.deleteIfExists(it.toPath()) } }
            val served = sockets.mapNotNull { socket -> serve(socket)?.let { it to socket } }
            return SingleInstance(channel, lock, served).also { it.closeOnExit() }
        }

        /** A socket a later start reaches this copy on; null where it cannot be made (its folder not made the reader's own). */
        private fun serve(socket: File): ServerSocketChannel? = runCatching {
            val parent = socket.parentFile
            if (socket.name != OLD_SOCKET_FILE) ownFolder(parent)
            Files.deleteIfExists(socket.toPath())
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
        }.getOrNull()

        /** [folder] made, the reader's alone (0700), so no other user reaches the socket in it. */
        private fun ownFolder(folder: File) {
            val owner = PosixFilePermissions.fromString("rwx------")
            if (!folder.isDirectory) Files.createDirectories(folder.toPath(), PosixFilePermissions.asFileAttribute(owner))
            Files.setPosixFilePermissions(folder.toPath(), owner)
        }

        /** Sends [message] to a copy listening on one of [sockets], trying for a while in case it is still starting, until one answered or read it. */
        private fun tell(sockets: List<File>, message: InstanceMessage): Reply {
            if (sockets.isEmpty()) return Reply.NONE
            val until = System.currentTimeMillis() + REACH_MILLIS
            while (true) {
                var reply = Reply.NONE
                for (socket in sockets) {
                    reply = minOf(reply, ask(socket, message))
                    if (reply == Reply.HEARD) return reply
                }
                if (reply != Reply.NONE || System.currentTimeMillis() >= until) return reply
                Thread.sleep(100)
            }
        }

        /** One try at [message]: how the copy on [socket] took it within [ANSWER_MILLIS]; a copy that hangs is given up on, its connection closed. */
        private fun ask(socket: File, message: InstanceMessage): Reply {
            val channel = runCatching { SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())) }.getOrNull() ?: return Reply.NONE
            val answer = CompletableFuture.supplyAsync {
                val bytes = ByteBuffer.wrap("${message.line}\n".toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                if (Channels.newReader(channel, Charsets.UTF_8).buffered().readLine()?.trim() == HEARD) Reply.HEARD else Reply.IGNORED
            }
            return try {
                answer.get(ANSWER_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                Reply.NONE
            } finally {
                runCatching { channel.close() }
            }
        }
    }
}
