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
 * window and ends, rather than opening a second window over the same
 * settings and database. Where the folder cannot be locked, each start runs
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
                    client.use { InstanceMessage.parse(Channels.newReader(it, Charsets.UTF_8).buffered().readLine()) }
                }.getOrNull()?.let(onMessage)
            }
        }, "xta-native-instance").apply { isDaemon = true }.start()
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

        /**
         * This start's claim on [folder]: the running copy's when it is the
         * first, else null once the copy already running has been asked to
         * raise its window, and this start should end.
         */
        fun claim(folder: File): SingleInstance? {
            val socket = File(folder, SOCKET_FILE)
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
                tell(socket, InstanceMessage.RAISE)
                return null
            }
            return SingleInstance(channel, lock, serve(socket), socket)
        }

        /** The socket a later start reaches this copy on; null where it cannot be made (a path too long for one), and the copy is not reached. */
        private fun serve(socket: File): ServerSocketChannel? = runCatching {
            Files.deleteIfExists(socket.toPath())
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
        }.getOrNull()

        /** Sends [message] to the copy listening on [socket], trying for a while in case it is still starting; false when it could not be reached. */
        private fun tell(socket: File, message: InstanceMessage): Boolean {
            val until = System.currentTimeMillis() + REACH_MILLIS
            while (true) {
                val sent = runCatching {
                    SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                        val bytes = ByteBuffer.wrap("${message.line}\n".toByteArray(Charsets.UTF_8))
                        while (bytes.hasRemaining()) channel.write(bytes)
                    }
                }.isSuccess
                if (sent || System.currentTimeMillis() >= until) return sent
                Thread.sleep(100)
            }
        }
    }
}
