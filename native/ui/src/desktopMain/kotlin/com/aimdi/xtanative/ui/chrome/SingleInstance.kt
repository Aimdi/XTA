package com.aimdi.xtanative.ui.chrome

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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** What a later start of the app asks of the copy already running, one line over its socket. */
enum class InstanceMessage(val line: String) {
    /** Bring the window forward: XTA's second start presents the running window. */
    RAISE("raise"),
    ;

    companion object {
        /** The message [line] carries; null for anything else, which the running copy ignores. */
        fun parse(line: String?): InstanceMessage? = line?.trim()?.let { text -> entries.firstOrNull { it.line == text } }
    }
}

/**
 * One copy of the app at a time, as XTA is a unique GTK application: the
 * first start holds a lock in the data folder and listens on a local socket
 * beside it; a later start finds the lock taken, asks the first to raise its
 * window and ends once it answers, rather than opening a second window over
 * the same settings and database. A copy that is ending stops answering, and
 * a start that no copy answers waits for its lock and runs. Where the folder cannot be locked, each start runs
 * alone, as XTA does without a session bus.
 */
class SingleInstance private constructor(
    private val lockChannel: FileChannel?,
    private val lock: FileLock?,
    private val server: ServerSocketChannel?,
    private val socket: File,
) : AutoCloseable {
    /** Hands each message a later start sends to [onMessage], on a thread of its own, until [close]. */
    fun listen(onMessage: (InstanceMessage) -> Unit) {
        val server = server ?: return
        Thread({
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { channel ->
                        val message = InstanceMessage.parse(Channels.newReader(channel, Charsets.UTF_8).buffered().readLine())
                        message?.let(onMessage)
                        // The later start ends only on this answer: a copy that is closing has stopped listening, and gives none.
                        if (message != null) channel.write(ByteBuffer.wrap("$HEARD\n".toByteArray(Charsets.UTF_8)))
                    }
                }
            }
        }, "xta-native-instance").apply { isDaemon = true }.start()
    }

    /** Stops answering later starts as soon as the app begins to end, before its window and writes are done. */
    private fun closeOnExit() {
        val server = server ?: return
        runCatching { Runtime.getRuntime().addShutdownHook(Thread({ runCatching { server.close() } }, "xta-native-instance-exit")) }
    }

    override fun close() {
        runCatching { server?.close() }
        if (server != null) socket.delete()
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
    }

    companion object {
        private const val LOCK_FILE = "instance.lock"
        private const val SOCKET_FILE = "instance.socket"

        /** How long a later start keeps trying to reach a first copy that is still starting up. */
        private const val REACH_MILLIS = 3_000L

        /** How long a later start that no copy answered waits for the lock of one that is ending (quit, then started again at once). */
        private const val TAKE_OVER_MILLIS = 10_000L

        /** How long one try waits for the running copy's answer. */
        private const val ANSWER_MILLIS = 2_000L

        /** The running copy's answer once it has raised its window. */
        private const val HEARD = "raised"

        /**
         * This start's claim on [folder]: the running copy's when it is the
         * first, else null once the copy already running has been asked to
         * raise its window, and this start should end.
         */
        fun claim(folder: File): SingleInstance? {
            val socket = File(folder, SOCKET_FILE)
            val first = take(folder, socket)
            if (first != null) return first
            if (tell(socket, InstanceMessage.RAISE)) return null
            // Nobody answered: the copy holding the lock is ending. This start runs once it has.
            val until = System.currentTimeMillis() + TAKE_OVER_MILLIS
            while (System.currentTimeMillis() < until) {
                Thread.sleep(100)
                take(folder, socket)?.let { return it }
            }
            return null
        }

        /** The lock on [folder] taken, and this copy listening; a copy without the lock where the folder cannot be locked at all; null while another copy holds it. */
        private fun take(folder: File, socket: File): SingleInstance? {
            val channel = try {
                FileChannel.open(File(folder, LOCK_FILE).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (_: IOException) {
                return SingleInstance(null, null, null, socket)
            }
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // Held in this very process (a test's second start).
                null
            } catch (_: IOException) {
                return SingleInstance(channel, null, null, socket)
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(channel, lock, serve(socket), socket).also { it.closeOnExit() }
        }

        /** The socket a later start reaches this copy on; null where it cannot be made (a path too long for one), and the copy is not reached. */
        private fun serve(socket: File): ServerSocketChannel? = runCatching {
            Files.deleteIfExists(socket.toPath())
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
        }.getOrNull()

        /** Sends [message] to the copy listening on [socket], trying for a while in case it is still starting; true once it answered that it raised its window. */
        private fun tell(socket: File, message: InstanceMessage): Boolean {
            val until = System.currentTimeMillis() + REACH_MILLIS
            while (true) {
                val sent = ask(socket, message)
                if (sent || System.currentTimeMillis() >= until) return sent
                Thread.sleep(100)
            }
        }

        /** One try at [message]: whether the copy on [socket] answered within [ANSWER_MILLIS]; a copy that hangs is given up on, its connection closed. */
        private fun ask(socket: File, message: InstanceMessage): Boolean {
            val channel = runCatching { SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())) }.getOrNull() ?: return false
            val answer = CompletableFuture.supplyAsync {
                val bytes = ByteBuffer.wrap("${message.line}\n".toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                Channels.newReader(channel, Charsets.UTF_8).buffered().readLine()?.trim() == HEARD
            }
            return try {
                answer.get(ANSWER_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            } finally {
                runCatching { channel.close() }
            }
        }
    }
}
