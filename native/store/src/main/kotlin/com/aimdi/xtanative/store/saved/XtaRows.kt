package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The rows of XTA's backup file for what Saved keeps: `tweets`,
 * `savedTweetFolders`, `likedTweets` and `localPosts`, each a list of these.
 * XTA keeps a post's JSON as text in its `content` column, and no time for a
 * saved or liked post, so the caller gives the time the list order stands for.
 */
object XtaRows {
    fun savedPost(row: J, savedAt: Long): SavedPost? {
        val id = row["id"].string ?: return null
        return SavedPost(id, row["user_id"].string, content(row["content"]), row["folder_id"].string, row["note"].string, savedAt)
    }

    fun row(post: SavedPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("content", post.content?.toString())
        put("user_id", post.authorId)
        put("folder_id", post.folderId)
        put("note", post.note)
    }

    fun folder(row: J): SavedFolder? {
        val id = row["id"].string ?: return null
        return SavedFolder(
            id = id,
            name = row["name"].string ?: return null,
            position = row["position"].int ?: 0,
            createdAt = parseTime(row["created_at"].string) ?: 0,
            autoDownload = row["auto_download"].long == 1L,
            autoUpload = row["auto_upload"].long == 1L,
        )
    }

    fun row(folder: SavedFolder): JsonObject = buildJsonObject {
        put("id", folder.id)
        put("name", folder.name)
        put("position", folder.position)
        put("created_at", isoTime(folder.createdAt))
        put("auto_download", if (folder.autoDownload) 1 else 0)
        put("auto_upload", if (folder.autoUpload) 1 else 0)
    }

    fun likedPost(row: J, likedAt: Long): LikedPost? {
        val id = row["id"].string ?: return null
        return LikedPost(id, row["user_id"].string, content(row["content"]), likedAt)
    }

    fun row(like: LikedPost): JsonObject = buildJsonObject {
        put("id", like.id)
        put("content", like.content?.toString())
        put("user_id", like.authorId)
    }

    /** A note row; its files come from `media` (with their bytes) or else from the `media_json` column. */
    fun localPost(row: J, now: Long): LocalPost? {
        val id = row["id"].string ?: return null
        val media = row["media"].takeIf { it.exists } ?: row["media_json"]
        return LocalPost(
            id = id,
            body = row["body"].string.orEmpty(),
            createdAt = parseTime(row["created_at"].string) ?: now,
            updatedAt = parseTime(row["updated_at"].string) ?: now,
            media = LocalPosts.parseMedia(media),
            quotedId = row["quoted_tweet_id"].string,
            quoted = content(row["quoted_tweet_json"]),
            replyTo = row["in_reply_to_id"].string?.takeIf { it.isNotEmpty() },
        )
    }

    /** A note as XTA's backup writes it: `media_json` without bytes, and `media` with them. */
    fun row(post: LocalPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("body", post.body)
        put("created_at", isoTime(post.createdAt))
        put("updated_at", isoTime(post.updatedAt))
        put("media_json", JsonArray(post.media.map { LocalPosts.mediaJson(it, withData = false) }).toString())
        put("quoted_tweet_id", post.quotedId)
        put("quoted_tweet_json", post.quoted?.toString())
        put("in_reply_to_id", post.replyTo)
        put("media", JsonArray(post.media.map { LocalPosts.mediaJson(it, withData = true) }))
    }

    /** The annotations of XTA's `archiveAnnotations`, keyed `archive-note:<id>`; other keys are not annotations. */
    fun annotations(section: J): Map<String, ArchiveAnnotation> = section.keys
        .filter { it.startsWith(ArchiveNotesStore.BACKUP_PREFIX) && section[it].raw is JsonObject }
        .associate { it.removePrefix(ArchiveNotesStore.BACKUP_PREFIX) to ArchiveAnnotation.parse(section[it]) }

    fun annotationsSection(annotations: Map<String, ArchiveAnnotation>): JsonObject =
        JsonObject(annotations.entries.associate { ArchiveNotesStore.BACKUP_PREFIX + it.key to it.value.toJson() })

    /** A post's JSON kept as text in a column; text that is not a JSON object or list is no post. */
    private fun content(column: J): JsonElement? {
        val raw = column.string?.let { J.parse(it).raw } ?: column.raw
        return raw?.takeIf { it !is JsonNull && it !is JsonPrimitive }
    }
}
