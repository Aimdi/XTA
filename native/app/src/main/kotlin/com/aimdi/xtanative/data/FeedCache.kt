package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XThread
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The feed as it was last shown, so a cold start opens on posts instead of a spinner. */
class FeedCache(private val file: File) {
    suspend fun read(): List<XThread> = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) XCodec.decode(file.readText()) else emptyList() }.getOrDefault(emptyList())
    }

    suspend fun write(threads: List<XThread>) = withContext(Dispatchers.IO) {
        runCatching { file.writeText(XCodec.encode(threads.take(KEPT))) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    private companion object {
        const val KEPT = 80
    }
}
