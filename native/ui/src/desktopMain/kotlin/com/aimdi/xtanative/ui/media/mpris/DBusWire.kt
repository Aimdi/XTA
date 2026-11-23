package com.aimdi.xtanative.ui.media.mpris

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A D-Bus object path ('o'), kept apart from a string ('s') so it is written as one. */
data class ObjectPath(val path: String)

/** A D-Bus signature ('g') as a value. */
data class Signature(val text: String)

/** A D-Bus variant ('v'): [value] written as [signature] says. */
data class Variant(val signature: String, val value: Any?)

/** What a D-Bus message is: a call, its answer, a failed call, or a signal. */
enum class DBusType(val code: Int) {
    METHOD_CALL(1),
    METHOD_RETURN(2),
    ERROR(3),
    SIGNAL(4),
    ;

    companion object {
        fun of(code: Int): DBusType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One D-Bus message, as the session bus carries it: [body] holds a value
 * per complete type of [signature] (String for 's', Int for 'i' and 'u',
 * Long for 'x', Double for 'd', Boolean for 'b', List for an array or a
 * struct, Map for a dictionary, [Variant], [ObjectPath], [Signature]).
 */
data class DBusMessage(
    val type: DBusType,
    val serial: Long = 0,
    val path: String? = null,
    val iface: String? = null,
    val member: String? = null,
    val errorName: String? = null,
    val replySerial: Long? = null,
    val destination: String? = null,
    val sender: String? = null,
    val signature: String = "",
    val body: List<Any?> = emptyList(),
    /** NO_REPLY_EXPECTED: a call the caller wants no answer to. */
    val noReply: Boolean = false,
)

/**
 * The D-Bus wire format (the specification's "Message Protocol"), just as
 * much of it as the media controls need, with no library: little-endian
 * messages written, either byte order read, no file descriptors.
 */
object DBusWire {
    private const val PATH = 1
    private const val INTERFACE = 2
    private const val MEMBER = 3
    private const val ERROR_NAME = 4
    private const val REPLY_SERIAL = 5
    private const val DESTINATION = 6
    private const val SENDER = 7
    private const val SIGNATURE = 8

    /** The size of the fixed start of a message, up to its header fields' length. */
    const val FIXED = 16

    /** The longest message the bus allows (128 MiB); anything longer is no message. */
    const val MAX_MESSAGE = 134_217_728

    /** [message] as the bytes that travel, numbered [serial]. */
    fun encode(message: DBusMessage, serial: Long): ByteArray {
        val body = Writer()
        val types = split(message.signature)
        require(types.size == message.body.size) { "${message.body.size} values for signature ${message.signature}" }
        types.zip(message.body).forEach { (type, value) -> body.write(type, value) }
        val fields = buildList {
            message.path?.let { add(listOf(PATH, Variant("o", ObjectPath(it)))) }
            message.iface?.let { add(listOf(INTERFACE, Variant("s", it))) }
            message.member?.let { add(listOf(MEMBER, Variant("s", it))) }
            message.errorName?.let { add(listOf(ERROR_NAME, Variant("s", it))) }
            message.replySerial?.let { add(listOf(REPLY_SERIAL, Variant("u", it))) }
            message.destination?.let { add(listOf(DESTINATION, Variant("s", it))) }
            message.sender?.let { add(listOf(SENDER, Variant("s", it))) }
            if (message.signature.isNotEmpty()) add(listOf(SIGNATURE, Variant("g", Signature(message.signature))))
        }
        val head = Writer()
        head.byte('l'.code)
        head.byte(message.type.code)
        head.byte(if (message.noReply) 1 else 0)
        head.byte(1)
        head.uint(body.size.toLong())
        head.uint(serial)
        head.write("a(yv)", fields)
        head.pad(8)
        return head.bytes() + body.bytes()
    }

    /** How long the whole message is whose first [FIXED] bytes are [start]. */
    fun length(start: ByteArray): Int {
        val buffer = ByteBuffer.wrap(start).order(orderOf(start[0]))
        val body = buffer.getInt(4).toLong() and 0xffffffffL
        val fields = buffer.getInt(12).toLong() and 0xffffffffL
        val total = FIXED + fields + (8 - (FIXED + fields) % 8) % 8 + body
        require(total <= MAX_MESSAGE) { "a message of $total bytes" }
        return total.toInt()
    }

    /** The message [bytes] hold, whole; null for a kind of message this does not know. */
    fun decode(bytes: ByteArray): DBusMessage? {
        val reader = Reader(ByteBuffer.wrap(bytes).order(orderOf(bytes[0])))
        reader.byte()
        val type = DBusType.of(reader.byte()) ?: return null
        val flags = reader.byte()
        reader.byte()
        val bodyLength = reader.uint()
        val serial = reader.uint()

        @Suppress("UNCHECKED_CAST")
        val fields = (reader.read("a(yv)") as List<List<Any?>>).associate { (code, value) -> (code as Int) to (value as Variant).value }
        reader.align(8)
        val signature = (fields[SIGNATURE] as? Signature)?.text ?: ""
        val body = split(signature).map(reader::read)
        check(reader.position == bytes.size && bodyLength >= 0) { "the body is not as long as it says" }
        return DBusMessage(
            type = type,
            serial = serial,
            path = (fields[PATH] as? ObjectPath)?.path,
            iface = fields[INTERFACE] as? String,
            member = fields[MEMBER] as? String,
            errorName = fields[ERROR_NAME] as? String,
            replySerial = fields[REPLY_SERIAL] as? Long,
            destination = fields[DESTINATION] as? String,
            sender = fields[SENDER] as? String,
            signature = signature,
            body = body,
            noReply = flags and 1 != 0,
        )
    }

    private fun orderOf(mark: Byte): ByteOrder = when (mark.toInt().toChar()) {
        'l' -> ByteOrder.LITTLE_ENDIAN
        'B' -> ByteOrder.BIG_ENDIAN
        else -> throw IllegalArgumentException("no D-Bus message")
    }

    /** [signature]'s complete types, one per value: "sa{sv}as" is "s", "a{sv}", "as". */
    fun split(signature: String): List<String> {
        val types = mutableListOf<String>()
        var at = 0
        while (at < signature.length) {
            val end = endOf(signature, at)
            types += signature.substring(at, end)
            at = end
        }
        return types
    }

    /** Where the complete type starting at [at] of [signature] ends. */
    private fun endOf(signature: String, at: Int): Int {
        require(at < signature.length) { "signature $signature ends too soon" }
        return when (val c = signature[at]) {
            'a' -> endOf(signature, at + 1)
            '(', '{' -> {
                val close = if (c == '(') ')' else '}'
                var next = at + 1
                while (signature.getOrNull(next) != close) next = endOf(signature, next)
                next + 1
            }
            in BASIC, 'v' -> at + 1
            else -> throw IllegalArgumentException("type $c of $signature")
        }
    }

    private const val BASIC = "ybnqiuxtdsogh"

    private fun alignmentOf(type: Char): Int = when (type) {
        'y', 'g', 'v' -> 1
        'n', 'q' -> 2
        'b', 'i', 'u', 's', 'o', 'a', 'h' -> 4
        else -> 8
    }

    /** Writes values as their types say, aligned from the start of what it writes. */
    private class Writer {
        private val out = ByteArrayOutputStream()
        private val scratch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        val size: Int get() = out.size()

        fun bytes(): ByteArray = out.toByteArray()

        fun pad(alignment: Int) {
            while (out.size() % alignment != 0) out.write(0)
        }

        fun byte(value: Int) = out.write(value and 0xff)

        fun uint(value: Long) {
            pad(4)
            scratch.clear()
            scratch.putInt(value.toInt())
            out.write(scratch.array(), 0, 4)
        }

        private fun long(value: Long) {
            pad(8)
            scratch.clear()
            scratch.putLong(value)
            out.write(scratch.array(), 0, 8)
        }

        private fun short(value: Int) {
            pad(2)
            scratch.clear()
            scratch.putShort(value.toShort())
            out.write(scratch.array(), 0, 2)
        }

        private fun text(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            uint(bytes.size.toLong())
            out.write(bytes)
            out.write(0)
        }

        private fun signature(value: String) {
            val bytes = value.toByteArray(Charsets.US_ASCII)
            byte(bytes.size)
            out.write(bytes)
            out.write(0)
        }

        fun write(type: String, value: Any?) {
            when (type[0]) {
                'y' -> byte((value as Number).toInt())
                'b' -> uint(if (value as Boolean) 1 else 0)
                'n', 'q' -> short((value as Number).toInt())
                'i', 'u', 'h' -> uint((value as Number).toLong())
                'x', 't' -> long((value as Number).toLong())
                'd' -> long((value as Number).toDouble().toRawBits())
                's' -> text(value as String)
                'o' -> text((value as ObjectPath).path)
                'g' -> signature((value as Signature).text)
                'v' -> {
                    val variant = value as Variant
                    signature(variant.signature)
                    write(variant.signature, variant.value)
                }
                '(' -> {
                    pad(8)
                    split(type.substring(1, type.length - 1)).zip(value as List<*>).forEach { (inner, item) -> write(inner, item) }
                }
                'a' -> array(type.substring(1), value)
                else -> throw IllegalArgumentException("type $type")
            }
        }

        private fun array(element: String, value: Any?) {
            pad(4)
            val lengthAt = out.size()
            uint(0)
            pad(alignmentOf(element[0]))
            val start = out.size()
            if (element[0] == '{') {
                val (key, item) = split(element.substring(1, element.length - 1))
                (value as Map<*, *>).forEach { (k, v) ->
                    pad(8)
                    write(key, k)
                    write(item, v)
                }
            } else {
                (value as List<*>).forEach { write(element, it) }
            }
            val length = out.size() - start
            val bytes = out.toByteArray()
            ByteBuffer.wrap(bytes, lengthAt, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(length)
            out.reset()
            out.write(bytes)
        }
    }

    /** Reads values as their types say, aligned from the start of the message. */
    private class Reader(private val buffer: ByteBuffer) {
        val position: Int get() = buffer.position()

        fun align(alignment: Int) {
            val skip = (alignment - buffer.position() % alignment) % alignment
            buffer.position(buffer.position() + skip)
        }

        fun byte(): Int = buffer.get().toInt() and 0xff

        fun uint(): Long {
            align(4)
            return buffer.int.toLong() and 0xffffffffL
        }

        private fun text(): String {
            val length = uint().toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            buffer.get()
            return String(bytes, Charsets.UTF_8)
        }

        private fun signature(): String {
            val length = byte()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            buffer.get()
            return String(bytes, Charsets.US_ASCII)
        }

        fun read(type: String): Any? = when (type[0]) {
            'y' -> byte()
            'b' -> uint() != 0L
            'n' -> { align(2); buffer.short.toInt() }
            'q' -> { align(2); buffer.short.toInt() and 0xffff }
            'i', 'h' -> { align(4); buffer.int }
            'u' -> uint()
            'x', 't' -> { align(8); buffer.long }
            'd' -> { align(8); buffer.double }
            's' -> text()
            'o' -> ObjectPath(text())
            'g' -> Signature(signature())
            'v' -> {
                val inner = signature()
                Variant(inner, read(inner))
            }
            '(' -> {
                align(8)
                split(type.substring(1, type.length - 1)).map(::read)
            }
            'a' -> array(type.substring(1))
            else -> throw IllegalArgumentException("type $type")
        }

        private fun array(element: String): Any {
            val length = uint().toInt()
            align(alignmentOf(element[0]))
            val end = buffer.position() + length
            require(length >= 0 && end <= buffer.limit()) { "an array longer than its message" }
            return if (element[0] == '{') {
                val (key, item) = split(element.substring(1, element.length - 1))
                buildMap {
                    while (buffer.position() < end) {
                        align(8)
                        put(read(key), read(item))
                    }
                }
            } else {
                buildList { while (buffer.position() < end) add(read(element)) }
            }
        }
    }
}
