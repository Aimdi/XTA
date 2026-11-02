package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.downloads.MediaResponse
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement

/** A store directory of its own, in a fresh temporary folder. */
fun tempStore(): StoreDir = StoreDir(Files.createTempDirectory("store-test").toFile())

/** [text] as a JSON element; tests write their fixtures inline. */
fun json(text: String): JsonElement = J.parse(text).raw ?: error("Not JSON: $text")

/** Runs a test that waits on other coroutines, failing it rather than hanging when what it waits for never comes. */
fun timed(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(120_000) { block() } }

/** Waits until [condition] holds, failing after [timeoutMs]. */
suspend fun waitUntil(timeoutMs: Long = 30_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > end) error("Condition not met in ${timeoutMs}ms")
        delay(5)
    }
}

/**
 * A response body handed out chunk by chunk, one per read, as a network
 * delivers it: chunks can be added later ([add]), the body can end ([end]) or
 * break ([fail]), and closing it wakes a read that waits.
 */
class ChunkStream(vararg chunks: ByteArray) : InputStream() {
    private val queue = LinkedBlockingQueue<Any>()
    private var current: ByteArray? = null
    private var offset = 0

    init {
        chunks.forEach(::add)
    }

    fun add(chunk: ByteArray) = queue.put(chunk)

    fun end() = queue.put(END)

    fun fail(error: IOException) = queue.put(error)

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val chunk = current ?: when (val next = queue.take()) {
            END -> return -1.also { queue.put(END) }
            is IOException -> throw next
            else -> next as ByteArray
        }
        val count = minOf(len, chunk.size - offset)
        chunk.copyInto(b, off, offset, offset + count)
        offset += count
        if (offset == chunk.size) {
            current = null
            offset = 0
        } else {
            current = chunk
        }
        return count
    }

    override fun close() = queue.put(IOException("Closed"))

    private object END
}

/** A whole answer: [chunks] then the end of the body. */
fun answer(code: Int, vararg chunks: ByteArray, length: Long? = null, headers: Map<String, String> = emptyMap()): MediaResponse =
    MediaResponse(code, headers, length, ChunkStream(*chunks).apply { end() })
