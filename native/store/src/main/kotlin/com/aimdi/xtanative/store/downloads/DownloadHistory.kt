package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where the downloads list is kept between launches. An interface, so tests can keep it in memory. */
interface DownloadHistory {
    /** The kept list; throws when it cannot be read, which is not the same as there being none. */
    suspend fun read(): List<DownloadEntry>

    suspend fun write(entries: List<DownloadEntry>)
}

/**
 * The downloads list in `downloads/history.json`, in XTA's format. At most
 * [MAX_ENTRIES] are kept and the file stays under [MAX_BYTES]; a file
 * bigger than that, or not shaped as XTA writes it, reads as empty, and an
 * entry that does not read is skipped.
 */
class FileDownloadHistory(private val dir: StoreDir, private val name: String = "downloads/history.json") : DownloadHistory {
    override suspend fun read(): List<DownloadEntry> {
        val text = dir.file(name).read() ?: return emptyList()
        if (text.toByteArray().size > MAX_BYTES) return emptyList()
        val root = J.parse(text)
        if (root["version"].int != 1 || root["entries"].raw !is JsonArray) return emptyList()
        return root["entries"].list.take(MAX_ENTRIES).mapNotNull(DownloadEntry::fromJson).distinctBy { it.id }
    }

    override suspend fun write(entries: List<DownloadEntry>) {
        val encoded = buildJsonObject {
            put("version", 1)
            put("entries", JsonArray(entries.take(MAX_ENTRIES).map { it.toJson() }))
        }.toString()
        if (encoded.toByteArray().size > MAX_BYTES) throw IOException("Download history exceeds its limit")
        dir.file(name).write(encoded)
    }

    companion object {
        const val MAX_ENTRIES = 200
        const val MAX_BYTES = 2_000_000
    }
}
