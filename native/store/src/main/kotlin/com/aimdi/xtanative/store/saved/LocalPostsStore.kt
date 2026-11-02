package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * The reader's notes, newest first, in `saved/notes.json`, with their files
 * under `notes-media/<note>/<file>`. Notes never leave the device except in
 * a backup, which carries the files' bytes too.
 */
class LocalPostsStore(
    private val dir: StoreDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val stored = StoredJson(dir, "saved/notes.json", emptyList(), LocalPosts::decode, LocalPosts::encode)
    private val mediaRoot = File(dir.root, "notes-media")

    /** The notes once read, newest first; null before the first call reads them. */
    val state: StateFlow<List<LocalPost>?> = stored.state

    suspend fun notes(): List<LocalPost> = stored.get()

    /**
     * Writes a note, or rewrites note [id]; null when there is nothing to keep
     * (a blank body and no file). A rewrite keeps when it was written, what it
     * quotes and what it answers unless new ones are given; files it no longer
     * holds are deleted.
     */
    suspend fun save(
        body: String,
        media: List<NoteMedia> = emptyList(),
        id: String? = null,
        quotedId: String? = null,
        quoted: JsonElement? = null,
        replyTo: String? = null,
    ): LocalPost? {
        if (!LocalPosts.hasContent(body, media)) return null
        val post = stored.updateWith { notes ->
            val existing = id?.let { key -> notes.firstOrNull { it.id == key } }
            val now = clock()
            val post = LocalPost(
                id = id ?: newId(),
                body = LocalPosts.normalizeBody(body).orEmpty(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                media = media.map { it.copy(data = null) },
                quotedId = quotedId ?: existing?.quotedId,
                quoted = quoted ?: existing?.quoted,
                replyTo = replyTo ?: existing?.replyTo,
            )
            (if (existing == null) listOf(post) + notes else notes.map { if (it.id == post.id) post else it }) to post
        }
        runCatching { deleteRemovedMedia(post.id, media) }
        return post
    }

    /** Deletes note [id] and its files. Its replies stay, each now opening a thread of its own. */
    suspend fun delete(id: String) {
        stored.update { notes -> if (notes.none { it.id == id }) notes else notes.filterNot { it.id == id } }
        withContext(Dispatchers.IO) { folderOf(id).deleteRecursively() }
    }

    /** Where file [mediaId] of note [postId] is kept. */
    fun mediaFile(postId: String, mediaId: String): File = File(folderOf(postId), safeName(mediaId))

    /** Keeps [bytes] as file [mediaId] of note [postId], before the note that holds it is saved. */
    suspend fun writeMedia(postId: String, mediaId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = mediaFile(postId, mediaId)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }

    /** Every note with its files' bytes, for the backup. */
    suspend fun content(): List<LocalPost> {
        val notes = notes()
        return withContext(Dispatchers.IO) { notes.map(::withData) }
    }

    /**
     * Replaces every note with [posts], as a restored backup does: the bytes
     * they carry become files again, and the files of notes no longer kept are
     * deleted. A damaged file in the backup is skipped, not the import.
     */
    suspend fun replace(posts: List<LocalPost>) {
        val kept = posts.distinctBy { it.id }
        withContext(Dispatchers.IO) {
            kept.forEach(::materialize)
            val folders = kept.mapTo(HashSet()) { safeName(it.id) }
            mediaRoot.listFiles()?.filter { it.name !in folders }?.forEach { it.deleteRecursively() }
        }
        stored.update { kept.map { post -> post.copy(media = post.media.map { it.copy(data = null) }) } }
    }

    private fun withData(post: LocalPost): LocalPost = post.copy(media = post.media.map { media ->
        val file = mediaFile(post.id, media.id)
        if (media.data != null || !file.isFile) media else media.copy(data = Base64.getEncoder().encodeToString(file.readBytes()))
    })

    private fun materialize(post: LocalPost) = post.media.filter { !it.data.isNullOrEmpty() }.forEach { media ->
        runCatching {
            val bytes = Base64.getDecoder().decode(media.data)
            mediaFile(post.id, media.id).apply { parentFile.mkdirs() }.writeBytes(bytes)
        }
    }

    private suspend fun deleteRemovedMedia(postId: String, keep: List<NoteMedia>) = withContext(Dispatchers.IO) {
        val names = keep.mapTo(HashSet()) { safeName(it.id) }
        folderOf(postId).listFiles()?.filter { it.isFile && it.name !in names }?.forEach { it.delete() }
    }

    private fun folderOf(postId: String) = File(mediaRoot, safeName(postId))

    /** An id as a file name that stays in its folder: ids from a backup are not trusted to. */
    private fun safeName(id: String): String =
        if (id.matches(Regex("[A-Za-z0-9._-]{1,128}")) && id != "." && id != "..") id else sha256(id)
}
