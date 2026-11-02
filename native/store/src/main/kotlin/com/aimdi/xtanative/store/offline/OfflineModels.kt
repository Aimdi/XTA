package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.isoTime
import com.aimdi.xtanative.store.saved.parseTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An article's text with its pictures, or the pictures and videos of a saved post. */
enum class OfflineKind { ARTICLE, MEDIA }

/** Whether a kept copy opens: wholly, with some of its pictures missing, or not at all. */
enum class OfflineAvailability { AVAILABLE, PARTIAL, UNAVAILABLE }

/** A picture or video to keep, by where it is fetched from. */
data class OfflineMediaSource(val url: String, val video: Boolean = false)

/** One kept picture or video: `media-<n>` in its entry's folder, fetched from [url]. */
data class OfflineFile(val name: String, val url: String, val mime: String, val bytes: Long) {
    val video: Boolean get() = mime.startsWith("video/")

    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("url", url)
        put("mime", mime)
        put("bytes", bytes)
    }

    companion object {
        fun fromJson(row: J): OfflineFile? {
            return OfflineFile(
                name = row["name"].string ?: return null,
                url = row["url"].string ?: return null,
                mime = row["mime"].string ?: return null,
                bytes = row["bytes"].long ?: return null,
            )
        }
    }
}

/**
 * One article or one post's media kept offline, as the library's manifest
 * lists it: its folder, what it is, and the files that were kept of
 * [totalMedia]. Only what is verified on disk is listed as kept.
 */
data class OfflineEntry(
    val id: String,
    /** The entry's folder in the library: `<sha256 of id>-<microseconds>`. */
    val directory: String,
    val title: String,
    val source: String,
    val kind: OfflineKind,
    val totalMedia: Int,
    val files: List<OfflineFile>,
    /** Epoch milliseconds. */
    val retainedAt: Long,
    val url: String? = null,
    val articleBytes: Long = 0,
    /** Shown only after the reader agrees, as a sensitive post is. */
    val sensitive: Boolean = false,
) {
    val bytes: Long get() = articleBytes + files.sumOf { it.bytes }

    val hasArticle: Boolean get() = kind == OfflineKind.ARTICLE && articleBytes > 0

    val canOpen: Boolean get() = hasArticle || (kind == OfflineKind.MEDIA && files.isNotEmpty())

    val availability: OfflineAvailability get() = when {
        !canOpen -> OfflineAvailability.UNAVAILABLE
        files.size == totalMedia -> OfflineAvailability.AVAILABLE
        else -> OfflineAvailability.PARTIAL
    }

    /** This entry as found on disk: the article text and the files that are really there. */
    fun verified(articleBytes: Long, files: List<OfflineFile>): OfflineEntry = copy(articleBytes = articleBytes, files = files)

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("directory", directory)
        put("title", title)
        put("source", source)
        put("kind", kind.name.lowercase())
        put("totalMedia", totalMedia)
        put("files", JsonArray(files.map { it.toJson() }))
        put("retainedAt", isoTime(retainedAt))
        put("url", url)
        put("articleBytes", articleBytes)
        put("sensitive", sensitive)
    }

    companion object {
        /** A manifest row, as XTA writes it; null when any part is missing or of the wrong kind. */
        fun fromJson(row: J): OfflineEntry? {
            val files = row["files"].takeIf { it.raw is JsonArray }?.list?.map { OfflineFile.fromJson(it) ?: return null } ?: return null
            return OfflineEntry(
                id = row["id"].string ?: return null,
                directory = row["directory"].string ?: return null,
                title = row["title"].string ?: return null,
                source = row["source"].string ?: return null,
                kind = OfflineKind.entries.firstOrNull { it.name.lowercase() == row["kind"].string } ?: return null,
                totalMedia = row["totalMedia"].int ?: return null,
                files = files,
                retainedAt = parseTime(row["retainedAt"].string) ?: return null,
                url = row["url"].string,
                articleBytes = row["articleBytes"].long ?: 0,
                sensitive = row["sensitive"].boolean == true,
            )
        }
    }
}

/**
 * An article's text kept for reading offline, apart from the article cache
 * that expires and from Saved. [payload] is what its network needs to draw
 * it again (an RSS item, a Substack post), with its `kind`.
 */
data class OfflineArticle(
    val id: String,
    val title: String,
    val source: String,
    val bodyHtml: String,
    val payload: JsonObject,
    val url: String? = null,
) {
    /** Which network's reader opens it: `rss` or `substack`. */
    val kind: String? get() = J(payload)["kind"].string

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("source", source)
        put("bodyHtml", bodyHtml)
        put("url", url)
        put("payload", payload)
    }

    companion object {
        fun fromJson(root: J): OfflineArticle? {
            return OfflineArticle(
                id = root["id"].string ?: return null,
                title = root["title"].string ?: return null,
                source = root["source"].string ?: return null,
                bodyHtml = root["bodyHtml"].string ?: return null,
                payload = root["payload"].raw as? JsonObject ?: return null,
                url = root["url"].string,
            )
        }
    }
}

/**
 * The library as its screen shows it: the entries newest first, those being
 * kept now ([busy]) and those whose keeping failed. [storageError] while the
 * library cannot be read or written.
 */
data class OfflineState(
    val entries: List<OfflineEntry> = emptyList(),
    val busy: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
    val loading: Boolean = true,
    val storageError: Boolean = false,
) {
    val bytes: Long get() = entries.sumOf { it.bytes }

    fun entry(id: String): OfflineEntry? = entries.firstOrNull { it.id == id }
}
