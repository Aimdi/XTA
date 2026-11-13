package com.aimdi.xtanative.plugins.immich

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.immich.ImmichUploadLog
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What has been sent to the reader's Immich from this device (XTA's
 * `immich_upload` table: each media id, and the asset Immich made of it
 * when it said), in the plugin's own folder. Uninstalling forgets it with
 * the folder, so a later install sends again rather than trust a server it
 * no longer knows; no backup carries it, as XTA's carries no such table.
 */
internal class ImmichDeviceLog(private val file: StoreFile) : ImmichUploadLog {
    private val mutex = Mutex()
    private var loaded: Map<String, String?>? = null

    override suspend fun pending(ids: Set<String>): Set<String> = mutex.withLock { ids - uploads().keys }

    override suspend fun record(mediaId: String, assetId: String?) = mutex.withLock {
        val next = uploads() + (mediaId to assetId)
        file.write(encode(next))
        loaded = next
    }

    /** How many files were sent: what the store counts it keeping. */
    suspend fun count(): Int = mutex.withLock { uploads().size }

    private suspend fun uploads(): Map<String, String?> = loaded ?: decode(file.read()).also { loaded = it }

    companion object {
        /** The rows as written, in XTA's columns; nothing for no file, or one that does not read. */
        fun decode(text: String?): Map<String, String?> {
            val rows = J.parse(text ?: return emptyMap()).list
            return rows.mapNotNull { row -> row["media_id"].string?.let { it to row["asset_id"].string } }.toMap()
        }

        fun encode(uploads: Map<String, String?>): String = JsonArray(
            uploads.map { (media, asset) ->
                buildJsonObject {
                    put("media_id", media)
                    put("asset_id", asset)
                }
            },
        ).toString()
    }
}
