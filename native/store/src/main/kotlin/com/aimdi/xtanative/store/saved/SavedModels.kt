package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A post kept on the device, from any network: XTA's `saved_tweet` row.
 * Nothing about it is ever sent to the network it came from.
 */
data class SavedPost(
    /** X's id, or the network's prefixed one (`reddit:…`, `mastodon:<url>`, `bluesky:<uri>`), so networks never collide. */
    val id: String,
    /** Whose post it is (X's user id, the subreddit, the handle): what groups and profiles match on. */
    val authorId: String?,
    /** The post as XTA keeps it, read by [about]; null when it was too large to keep. */
    val content: JsonElement?,
    /** The folder it is filed in; null for unfiled. */
    val folderId: String? = null,
    /** The reader's own note on it, searched along with the post. */
    val note: String? = null,
    /** When it was saved, in epoch milliseconds. */
    val savedAt: Long = 0,
) {
    /** What [content] is, read once. */
    val about: SavedContent by lazy { SavedContent.of(content) }
}

/** A folder of saved posts. Saving into one can also download its pictures or send its media to Immich. */
data class SavedFolder(
    val id: String,
    val name: String,
    /** Its place among the folders, from 0. */
    val position: Int = 0,
    /** When it was made, in epoch milliseconds: the order among folders of the same [position]. */
    val createdAt: Long = 0,
    val autoDownload: Boolean = false,
    val autoUpload: Boolean = false,
)

/** A post liked on this device only: the heart never reaches the network. XTA's `liked_tweet` row. */
data class LikedPost(
    val id: String,
    val authorId: String?,
    val content: JsonElement?,
    /** When it was liked, in epoch milliseconds. */
    val likedAt: Long = 0,
) {
    val about: SavedContent by lazy { SavedContent.of(content) }
}

/** Every saved post, newest first, and the folders in their order: what [SavedStore] keeps. */
data class SavedLibrary(val posts: List<SavedPost> = emptyList(), val folders: List<SavedFolder> = emptyList()) {
    private val byId by lazy { posts.associateBy { it.id } }

    fun post(id: String): SavedPost? = byId[id]

    fun isSaved(id: String): Boolean = id in byId

    /** The folder [id] is filed in; null when it is unfiled or not saved. */
    fun folderOf(id: String): String? = byId[id]?.folderId

    fun folder(id: String): SavedFolder? = folders.firstOrNull { it.id == id }

    /** How many saved posts [folderId] holds. */
    fun countIn(folderId: String): Int = posts.count { it.folderId == folderId }

    /** The posts of [authorId], newest first. */
    fun by(authorId: String): List<SavedPost> = posts.filter { it.authorId == authorId }

    companion object {
        /** XTA's order: by position, then the older folder first. */
        fun ordered(folders: List<SavedFolder>): List<SavedFolder> = folders.sortedWith(compareBy({ it.position }, { it.createdAt }))
    }
}

/** How the saved and liked collections are written in XTA Native's own files. */
internal object SavedCodec {
    const val VERSION = 1

    fun library(library: SavedLibrary): JsonElement = buildJsonObject {
        put("v", VERSION)
        put("folders", JsonArray(library.folders.map(::folder)))
        put("posts", JsonArray(library.posts.map(::post)))
    }

    fun library(root: J): SavedLibrary? = if (root["v"].int != VERSION) null else SavedLibrary(
        posts = root["posts"].list.mapNotNull(::post).distinctBy { it.id },
        folders = root["folders"].list.mapNotNull(::folder).distinctBy { it.id },
    )

    fun likes(likes: List<LikedPost>): JsonElement = buildJsonObject {
        put("v", VERSION)
        put("likes", JsonArray(likes.map(::like)))
    }

    fun likes(root: J): List<LikedPost>? = if (root["v"].int != VERSION) null else root["likes"].list.mapNotNull(::like).distinctBy { it.id }

    private fun post(post: SavedPost) = buildJsonObject {
        put("id", post.id)
        post.authorId?.let { put("author", it) }
        post.content?.let { put("content", it) }
        post.folderId?.let { put("folder", it) }
        post.note?.let { put("note", it) }
        put("at", post.savedAt)
    }

    private fun post(row: J): SavedPost? {
        val id = row["id"].string ?: return null
        return SavedPost(id, row["author"].string, row["content"].raw, row["folder"].string, row["note"].string, row["at"].long ?: 0)
    }

    private fun folder(folder: SavedFolder) = buildJsonObject {
        put("id", folder.id)
        put("name", folder.name)
        put("position", folder.position)
        put("createdAt", folder.createdAt)
        put("autoDownload", folder.autoDownload)
        put("autoUpload", folder.autoUpload)
    }

    private fun folder(row: J): SavedFolder? {
        val id = row["id"].string ?: return null
        return SavedFolder(
            id = id,
            name = row["name"].string ?: return null,
            position = row["position"].int ?: 0,
            createdAt = row["createdAt"].long ?: 0,
            autoDownload = row["autoDownload"].boolean ?: false,
            autoUpload = row["autoUpload"].boolean ?: false,
        )
    }

    private fun like(like: LikedPost) = buildJsonObject {
        put("id", like.id)
        like.authorId?.let { put("author", it) }
        like.content?.let { put("content", it) }
        put("at", like.likedAt)
    }

    private fun like(row: J): LikedPost? {
        val id = row["id"].string ?: return null
        return LikedPost(id, row["author"].string, row["content"].raw, row["at"].long ?: 0)
    }
}
