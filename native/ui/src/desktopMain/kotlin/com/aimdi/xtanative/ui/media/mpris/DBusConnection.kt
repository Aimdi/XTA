package com.aimdi.xtanative.ui.media.mpris

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A connection to a D-Bus bus over its Unix socket (the session bus where
 * the desktop's media controls look for players), with no library: it
 * authenticates as this user (EXTERNAL), says Hello, then hands each call
 * other programs make to [onCall] on a thread of its own and matches the
 * answers to its own calls. Nothing here throws once connected: a bus that
 * goes away only ends the connection.
 */
class DBusConnection private constructor(private val channel: SocketChannel) : AutoCloseable {
    private val serials = AtomicLong(0)
    private val waiting = ConcurrentHashMap<Long, CompletableFuture<DBusMessage>>()
    private val writing = Any()

    /** The calls other programs make to this connection. */
    @Volatile
    var onCall: ((DBusMessage) -> Unit)? = null

    /** The signals this connection's match rules ([DBusConnection.call] AddMatch) bring, on the connection's thread. */
    @Volatile
    var onSignal: ((DBusMessage) -> Unit)? = null

    /** Told once, on the connection's thread, when the bus went away or sent what is no message. */
    @Volatile
    var onClosed: (() -> Unit)? = null

    /** The name the bus gave this connection (":1.42"), once it said Hello. */
    var uniqueName: String? = null
        private set

    val open: Boolean get() = channel.isOpen

    private fun start() {
        Thread(::readAll, "xta-native-dbus").apply { isDaemon = true }.start()
        val hello = call(BUS, BUS_PATH, BUS, "Hello")
        uniqueName = hello?.body?.firstOrNull() as? String
    }

    /** Sends [message]; its serial, or null when the bus has gone. */
    fun send(message: DBusMessage): Long? = serials.incrementAndGet().takeIf { write(message, it) }

    private fun write(message: DBusMessage, serial: Long): Boolean {
        val bytes = runCatching { DBusWire.encode(message, serial) }.getOrElse { return false }
        return try {
            synchronized(writing) {
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
            true
        } catch (_: IOException) {
            close()
            false
        }
    }

    /** Calls [member] and waits for its answer (an error is an answer too); null when none came in [timeoutMs]. */
    fun call(
        destination: String,
        path: String,
        iface: String,
        member: String,
        signature: String = "",
        body: List<Any?> = emptyList(),
        timeoutMs: Long = CALL_MS,
    ): DBusMessage? {
        val answer = CompletableFuture<DBusMessage>()
        val serial = serials.incrementAndGet()
        // Waited for before it is sent, so a quick answer finds its waiter.
        waiting[serial] = answer
        val message = DBusMessage(DBusType.METHOD_CALL, path = path, iface = iface, member = member, destination = destination, signature = signature, body = body)
        if (!write(message, serial)) {
            waiting.remove(serial)
            return null
        }
        return try {
            answer.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } finally {
            waiting.remove(serial)
        }
    }

    /** Answers [call] with [body] of [signature], unless its caller wanted no answer. */
    fun reply(call: DBusMessage, signature: String = "", body: List<Any?> = emptyList()) {
        if (call.noReply) return
        send(DBusMessage(DBusType.METHOD_RETURN, replySerial = call.serial, destination = call.sender, signature = signature, body = body))
    }

    /** Answers [call] with the error [name] and its [text]. */
    fun fail(call: DBusMessage, name: String, text: String) {
        if (call.noReply) return
        send(DBusMessage(DBusType.ERROR, replySerial = call.serial, destination = call.sender, errorName = name, signature = "s", body = listOf(text)))
    }

    /** Emits the signal [member] of [iface] from [path]. */
    fun signal(path: String, iface: String, member: String, signature: String, body: List<Any?>) {
        send(DBusMessage(DBusType.SIGNAL, path = path, iface = iface, member = member, signature = signature, body = body))
    }

    private fun readAll() {
        try {
            while (channel.isOpen) {
                val start = readFully(DBusWire.FIXED)
                val rest = readFully(DBusWire.length(start) - DBusWire.FIXED)
                val message = runCatching { DBusWire.decode(start + rest) }.getOrNull() ?: continue
                when (message.type) {
                    DBusType.METHOD_RETURN, DBusType.ERROR -> message.replySerial?.let { waiting.remove(it)?.complete(message) }
                    DBusType.METHOD_CALL -> runCatching { onCall?.invoke(message) ?: fail(message, UNKNOWN_METHOD, "Nothing answers here") }
                    DBusType.SIGNAL -> runCatching { onSignal?.invoke(message) }
                }
            }
        } catch (_: Exception) {
            // The bus went away or sent what is no message: the connection ends.
        } finally {
            close()
            runCatching { onClosed?.invoke() }
        }
    }

    private fun readFully(count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count)
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw IOException("the bus closed")
        return buffer.array()
    }

    override fun close() {
        runCatching { channel.close() }
        waiting.values.forEach { it.cancel(false) }
        waiting.clear()
    }

    companion object {
        const val BUS = "org.freedesktop.DBus"
        const val BUS_PATH = "/org/freedesktop/DBus"
        const val UNKNOWN_METHOD = "org.freedesktop.DBus.Error.UnknownMethod"

        /** How long a call waits for its answer. */
        private const val CALL_MS = 3_000L

        /**
         * The session bus: $DBUS_SESSION_BUS_ADDRESS, else the user's bus in
         * $XDG_RUNTIME_DIR; null where there is none this can reach (an
         * abstract socket, which Java cannot open, or no bus at all).
         */
        fun session(): DBusConnection? {
            val socket = DBusAddress.socketOf(System.getenv("DBUS_SESSION_BUS_ADDRESS"), System.getenv("XDG_RUNTIME_DIR")) ?: return null
            return connect(socket)
        }

        /** Connects to the bus listening on [socket]; null when it cannot. */
        fun connect(socket: File): DBusConnection? {
            val channel = runCatching { SocketChannel.open(StandardProtocolFamily.UNIX) }.getOrNull() ?: return null
            return try {
                channel.connect(UnixDomainSocketAddress.of(socket.toPath()))
                authenticate(channel, uid() ?: return null.also { channel.close() })
                val connection = DBusConnection(channel).also { it.start() }
                // A bus that never said who this is: let go of the socket and its reader.
                connection.takeIf { it.uniqueName != null && it.open } ?: null.also { connection.close() }
            } catch (_: Exception) {
                runCatching { channel.close() }
                null
            }
        }

        /** The SASL exchange: EXTERNAL as [uid], then BEGIN; throws when the bus refuses. */
        private fun authenticate(channel: SocketChannel, uid: Long) {
            val hex = uid.toString().toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }
            write(channel, "\u0000AUTH EXTERNAL $hex\r\n")
            val answer = readLine(channel)
            if (!answer.startsWith("OK ")) throw IOException("the bus refused: $answer")
            write(channel, "BEGIN\r\n")
        }

        private fun write(channel: SocketChannel, text: String) {
            val buffer = ByteBuffer.wrap(text.toByteArray(Charsets.US_ASCII))
            while (buffer.hasRemaining()) channel.write(buffer)
        }

        /** One line of the exchange, read a byte at a time so nothing after it is taken. */
        private fun readLine(channel: SocketChannel): String {
            val line = StringBuilder()
            val one = ByteBuffer.allocate(1)
            while (line.length < 512) {
                one.clear()
                if (channel.read(one) < 0) throw IOException("the bus closed")
                val c = one.get(0).toInt().toChar()
                if (c == '\n') return line.toString().trimEnd('\r')
                line.append(c)
            }
            throw IOException("no answer from the bus")
        }

        /** This process's user id, from /proc. */
        private fun uid(): Long? = runCatching {
            File("/proc/self/status").readLines().first { it.startsWith("Uid:") }.split(Regex("\\s+"))[1].toLong()
        }.getOrNull()
    }
}

/** Where a D-Bus address says the bus listens. */
object DBusAddress {
    /**
     * The socket of the first `unix:path=` in [address] (several are
     * separated by ';', values %-escaped), else `bus` in [runtimeDir] when no
     * address is set; null for an address with no path (an abstract socket).
     */
    fun socketOf(address: String?, runtimeDir: String?): File? {
        if (address.isNullOrBlank()) return runtimeDir?.takeIf { it.isNotBlank() }?.let { File(it, "bus") }?.takeIf { it.exists() }
        return address.split(';').firstNotNullOfOrNull { entry ->
            val (transport, rest) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
            if (transport != "unix") return@firstNotNullOfOrNull null
            rest.split(',').firstNotNullOfOrNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "path" }?.let { File(unescape(it[1])) }
            }
        }
    }

    private fun unescape(value: String): String {
        val out = ByteArrayOutputStream()
        var at = 0
        while (at < value.length) {
            val c = value[at]
            if (c == '%' && at + 2 < value.length) {
                out.write(value.substring(at + 1, at + 3).toInt(16))
                at += 3
            } else {
                out.write(c.code)
                at++
            }
        }
        return out.toString(Charsets.UTF_8)
    }
}
