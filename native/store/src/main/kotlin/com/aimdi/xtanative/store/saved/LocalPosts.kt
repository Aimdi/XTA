package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A file attached to a note. [data] carries its bytes, base64, in backups only; on the device they are a file. */
data class NoteMedia(val id: String, val name: String, val mime: String, val data: String? = null) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val isVideo: Boolean get() = mime.startsWith("video/")
}

/** A note the reader wrote, XTA's `local_post`. It stays on the device and in backups: it is never posted. */
data class LocalPost(
    val id: String,
    val body: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val updatedAt: Long,
    val media: List<NoteMedia> = emptyList(),
    val quotedId: String? = null,
    /** The post it quotes, as XTA keeps an X post ([XtaTweetJson]). */
    val quoted: JsonElement? = null,
    /** The note this one answers, on this device only; null for a note of its own. */
    val replyTo: String? = null,
)

/** One note of a thread as it is drawn: [depth] 0 for the first note, 1 for its replies, and so on. */
data class NoteInThread(val post: LocalPost, val depth: Int)

/** The rules of notes: what a note may hold, what a search finds, and how replies make threads. */
object LocalPosts {
    const val MAX_LENGTH = 20000

    /** [body] trimmed, or null when nothing is left; a body past [MAX_LENGTH] is cut, not refused. */
    fun normalizeBody(body: String): String? = body.trim().takeIf { it.isNotEmpty() }?.take(MAX_LENGTH)

    /** A note needs text or a file. */
    fun hasContent(body: String, media: List<NoteMedia>): Boolean = normalizeBody(body) != null || media.isNotEmpty()

    /** [query] must already be lowercased; an empty one finds everything. */
    fun matchesQuery(body: String, query: String): Boolean = query.isEmpty() || body.lowercase().contains(query)

    /** Whether [query] (lowercased) is in the note's text or in the name of a file it holds. */
    fun recordMatches(post: LocalPost, query: String): Boolean =
        matchesQuery(post.body, query) || post.media.any { it.name.lowercase().contains(query) }

    private val mimeByExtension = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "heic" to "image/heic", "heif" to "image/heic", "mp4" to "video/mp4", "mov" to "video/quicktime",
        "webm" to "video/webm", "mkv" to "video/x-matroska",
    )

    /** [mime] when the picker gave a real one, else what [name]'s extension says. */
    fun inferMime(name: String, mime: String?): String {
        if (!mime.isNullOrEmpty() && mime != "application/octet-stream") return mime
        val extension = name.substringAfterLast('.', "").lowercase()
        return mimeByExtension[extension] ?: mime?.takeIf { it.isNotEmpty() } ?: "application/octet-stream"
    }

    /** The notes Notes lists: those whose parent is unset or no longer kept. */
    fun roots(posts: List<LocalPost>): List<LocalPost> {
        val ids = posts.mapTo(HashSet()) { it.id }
        return posts.filter { it.replyTo == null || it.replyTo !in ids }
    }

    fun directReplyCount(posts: List<LocalPost>, id: String): Int = posts.count { it.replyTo == id }

    /** The replies to [id], oldest first. */
    fun children(posts: List<LocalPost>, id: String): List<LocalPost> = posts.filter { it.replyTo == id }.sortedBy { it.createdAt }

    /** [rootId] first, then each reply followed by its own replies, oldest sibling first; a loop of replies is walked once. */
    fun thread(posts: List<LocalPost>, rootId: String): List<NoteInThread> {
        val root = posts.firstOrNull { it.id == rootId } ?: return emptyList()
        val seen = HashSet<String>()
        fun walk(post: LocalPost, depth: Int): List<NoteInThread> =
            if (!seen.add(post.id)) emptyList()
            else listOf(NoteInThread(post, depth)) + children(posts, post.id).flatMap { walk(it, depth + 1) }
        return walk(root, 0)
    }

    /** The oldest note above [id] still kept: where its thread opens. */
    fun threadRootId(posts: List<LocalPost>, id: String): String {
        val byId = posts.associateBy { it.id }
        val seen = HashSet<String>()
        var current = id
        while (seen.add(current)) {
            val parent = byId[current]?.replyTo
            if (parent == null || parent !in byId) return current
            current = parent
        }
        return current
    }

    /** Files as XTA keeps them: a list, or that list as JSON text (`media_json`); a file without an id is dropped. */
    fun parseMedia(raw: J): List<NoteMedia> {
        val list = raw.string?.let { J.parse(it) } ?: raw
        return list.list.mapNotNull { item ->
            val id = item["id"].string?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            NoteMedia(id, item["name"].string.orEmpty(), item["mime"].string ?: "application/octet-stream", item["data"].string)
        }
    }

    fun mediaJson(media: NoteMedia, withData: Boolean): JsonObject = buildJsonObject {
        put("id", media.id)
        put("name", media.name)
        put("mime", media.mime)
        if (withData) media.data?.let { put("data", it) }
    }

    internal fun encode(posts: List<LocalPost>): JsonElement = buildJsonObject {
        put("v", 1)
        put("notes", JsonArray(posts.map { post ->
            buildJsonObject {
                put("id", post.id)
                put("body", post.body)
                put("createdAt", post.createdAt)
                put("updatedAt", post.updatedAt)
                put("media", JsonArray(post.media.map { mediaJson(it, withData = false) }))
                post.quotedId?.let { put("quotedId", it) }
                post.quoted?.let { put("quoted", it) }
                post.replyTo?.let { put("replyTo", it) }
            }
        }))
    }

    internal fun decode(root: J): List<LocalPost>? = if (root["v"].int != 1) null else root["notes"].list.mapNotNull { row ->
        val id = row["id"].string ?: return@mapNotNull null
        LocalPost(
            id = id,
            body = row["body"].string.orEmpty(),
            createdAt = row["createdAt"].long ?: 0,
            updatedAt = row["updatedAt"].long ?: 0,
            media = parseMedia(row["media"]),
            quotedId = row["quotedId"].string,
            quoted = row["quoted"].raw,
            replyTo = row["replyTo"].string?.takeIf { it.isNotEmpty() },
        )
    }.distinctBy { it.id }
}
