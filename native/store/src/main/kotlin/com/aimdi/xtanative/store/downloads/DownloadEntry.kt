package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.isoTime
import com.aimdi.xtanative.store.saved.parseTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a download stands; [wire] is the name XTA's history file uses. */
enum class DownloadStatus(val wire: String) {
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    /** Waiting on the reader in the system's save dialog. */
    CHOOSING_LOCATION("choosingLocation"),
    SAVING("saving"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    /** Was running when the app stopped; it can be tried again. */
    INTERRUPTED("interrupted"),
}

/** One download of the downloads list, as XTA's history file keeps it. */
data class DownloadEntry(
    val id: String,
    val url: String,
    val fileName: String,
    /** The folder picked for it (a document tree on Android); null when it has none. */
    val treeUri: String? = null,
    /** Saved without asking into the shared folder for its type ([SharedDownloadFolder]); ignored when [treeUri] names a folder. */
    val background: Boolean = false,
    /** A folder path inside the destination, made on save when missing. */
    val subfolder: String? = null,
    /** Epoch milliseconds. */
    val createdAt: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val received: Long = 0,
    val total: Long? = null,
    /** Where it was saved, once it was. */
    val savedUri: String? = null,
    /** The HTTP status a failed fetch came back with, when the host answered. */
    val httpStatus: Int? = null,
) {
    val active: Boolean get() = status in ACTIVE

    val canRetry: Boolean get() = status == DownloadStatus.FAILED || status == DownloadStatus.INTERRUPTED || status == DownloadStatus.CANCELLED

    /** A download waiting on the save dialog is the reader's to close, not ours to cancel. */
    val canCancel: Boolean get() = active && status != DownloadStatus.CHOOSING_LOCATION

    /** How much has arrived, from 0 to 1; null while the size is unknown. */
    val fraction: Double? get() = total?.takeIf { it > 0 }?.let { (received.toDouble() / it).coerceIn(0.0, 1.0) }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("url", url)
        put("name", fileName)
        put("tree", treeUri)
        if (background) put("background", true)
        subfolder?.let { put("folder", it) }
        put("status", status.wire)
        put("created", isoTime(createdAt))
        put("received", received)
        put("total", total)
        put("saved", savedUri)
        httpStatus?.let { put("http", it) }
    }

    companion object {
        private val ACTIVE = setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.SAVING, DownloadStatus.CHOOSING_LOCATION)
        private val idPattern = Regex("^[a-zA-Z0-9_-]{1,100}$")
        private const val MAX_COUNT = 1L shl 53

        /**
         * An entry of the history file, or null when anything about it is not
         * what XTA writes. Names and folders are cleaned again, and a download
         * that was running is read as interrupted.
         */
        fun fromJson(row: J): DownloadEntry? {
            val id = row["id"].string?.takeIf(idPattern::matches) ?: return null
            val url = row["url"].string?.takeIf { it.length <= MAX_URL && isWebUrl(it) } ?: return null
            val name = row["name"].string?.takeIf { it.length <= 240 } ?: return null
            val created = parseTime(row["created"].string) ?: return null
            val status = DownloadStatus.entries.firstOrNull { it.wire == row["status"].string } ?: return null
            val entry = DownloadEntry(
                id = id,
                url = url,
                fileName = safeDownloadName(name),
                treeUri = row["tree"].string,
                background = row["background"].boolean == true,
                subfolder = row["folder"].string?.let(::safeDownloadFolder),
                createdAt = created,
                status = status,
                received = (row["received"].long ?: 0).coerceIn(0, MAX_COUNT),
                total = row["total"].long?.coerceIn(0, MAX_COUNT),
                savedUri = row["saved"].string,
                httpStatus = row["http"].int?.takeIf { it in 100..599 },
            )
            return if (entry.active) entry.copy(status = DownloadStatus.INTERRUPTED) else entry
        }

        /** The longest URL a download keeps. */
        const val MAX_URL = 16000
    }
}

/** A download asked for: what [DownloadQueue.enqueueBatch] takes. */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val treeUri: String? = null,
    val background: Boolean = false,
    val subfolder: String? = null,
)

/** How a batch went: [saved] of [total] reached their destination. */
data class DownloadBatchResult(val saved: Int, val total: Int)

/** Whether [url] is an http or https address with a host. */
internal fun isWebUrl(url: String): Boolean = url.toHttpUrlOrNull()?.host?.isNotEmpty() == true &&
    (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
