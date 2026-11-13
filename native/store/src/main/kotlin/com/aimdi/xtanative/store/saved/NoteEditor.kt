package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A note being written, as an interruption leaves it: its text, its files (without their bytes) and the note it rewrites. */
data class NoteDraft(val body: String, val media: List<NoteMedia> = emptyList(), val postId: String? = null)

/** Drafts of notes and highlight notes, by the editor's key, in `saved/drafts.json`. */
class NoteDrafts(dir: StoreDir) {
    private val stored = StoredJson(dir, "saved/drafts.json", emptyMap(), ::decode, ::encode)

    suspend fun read(key: String): NoteDraft? = stored.get()[key]

    suspend fun write(key: String, draft: NoteDraft) {
        stored.update { drafts -> if (drafts[key] == draft) drafts else drafts + (key to draft) }
    }

    suspend fun remove(key: String) {
        stored.update { drafts -> if (key in drafts) drafts - key else drafts }
    }

    private fun encode(drafts: Map<String, NoteDraft>): JsonElement = buildJsonObject {
        put("v", 1)
        put("drafts", JsonObject(drafts.mapValues { (_, draft) ->
            buildJsonObject {
                put("body", draft.body)
                put("media", JsonArray(draft.media.map { LocalPosts.mediaJson(it, withData = false) }))
                draft.postId?.let { put("postId", it) }
            }
        }))
    }

    private fun decode(root: J): Map<String, NoteDraft>? {
        if (root["v"].int != 1) return null
        val drafts = root["drafts"]
        return drafts.keys.mapNotNull { key ->
            val draft = drafts[key]
            draft["body"].string?.let { key to NoteDraft(it, LocalPosts.parseMedia(draft["media"]), draft["postId"].string) }
        }.toMap()
    }
}

/** Where a note editor stands. [busy] holds off typing, files and saving while one of them runs. */
data class NoteEditorState(
    val body: String,
    val media: List<NoteMedia> = emptyList(),
    val saving: Boolean = false,
    val attaching: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
    val attachFailed: Boolean = false,
    /** The draft is being read back; nothing can be typed until it is. */
    val restoring: Boolean = false,
    val draftFailed: Boolean = false,
    /** The text came back from a draft an interruption left. */
    val recovered: Boolean = false,
    val discarded: Boolean = false,
) {
    val busy: Boolean get() = saving || attaching || restoring
    val hasContent: Boolean get() = LocalPosts.hasContent(body, media)
}

/**
 * One note being written or rewritten, for notes, a saved post's note or a
 * highlight's. With a [draftKey] and [drafts], what is typed is kept as a
 * draft (in [scope] as it is typed), read back by [restoreDraft] after an
 * interruption and dropped once the note is saved or discarded. A failed save
 * keeps the draft and the files, ready to try again.
 */
class NoteEditor(
    body: String = "",
    media: List<NoteMedia> = emptyList(),
    private val draftKey: String? = null,
    postId: String? = null,
    private val drafts: NoteDrafts? = null,
    private val scope: CoroutineScope? = null,
) {
    val initialBody: String = body
    val initialMedia: List<NoteMedia> = media.toList()

    /** The note being rewritten; a draft may name the one it was started for. */
    var postId: String? = postId
        private set

    private val current = MutableStateFlow(NoteEditorState(body, initialMedia, restoring = draftKey != null))
    val state: StateFlow<NoteEditorState> = current.asStateFlow()
    private val draftLock = Mutex()

    @Volatile
    private var closed = false

    /** Whether leaving now loses anything: the text or the set of files differs from what the editor opened with. */
    val dirty: Boolean get() = current.value.let { it.body != initialBody || it.media.map(NoteMedia::id) != initialMedia.map(NoteMedia::id) }

    /** The text as typed, kept as a draft; ignored while something else runs. */
    fun setBody(body: String) {
        if (current.value.busy) return
        current.update { it.copy(body = body) }
        keepDraft()
    }

    fun removeMedia(id: String) {
        if (current.value.busy) return
        current.update { state -> state.copy(media = state.media.filterNot { it.id == id }) }
        keepDraft()
    }

    /** Adds the file [pick] gives; a cancelled pick (null) or a failed one keeps the files there were. */
    suspend fun attach(pick: suspend () -> NoteMedia?) {
        if (current.value.busy) return
        current.update { it.copy(attaching = true, attachFailed = false) }
        try {
            val picked = pick()
            if (closed) return
            current.update { state -> state.copy(attaching = false, media = state.media + listOfNotNull(picked)) }
            persistDraft()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!closed) current.update { it.copy(attaching = false, attachFailed = true) }
        }
    }

    /** Runs [write], which stores the note; true once it is stored and its draft dropped. A second save while one runs is refused. */
    suspend fun save(write: suspend () -> Unit): Boolean {
        if (current.value.busy) return false
        current.update { it.copy(saving = true, saveFailed = false) }
        return try {
            write()
            draftLock.withLock {
                draftKey?.let { drafts?.remove(it) }
                if (!closed) current.update { it.copy(saving = false, saved = true) }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!closed) current.update { it.copy(saving = false, saveFailed = true) }
            false
        }
    }

    /** Reads back the draft an interruption left, if any; until this runs nothing can be typed. */
    suspend fun restoreDraft() {
        val key = draftKey ?: return
        val draft = runCatching { drafts?.read(key) }.getOrNull()
        if (closed) return
        if (draft == null) {
            current.update { it.copy(restoring = false) }
            return
        }
        postId = draft.postId ?: postId
        current.update { it.copy(body = draft.body, media = draft.media, restoring = false, recovered = true) }
    }

    /** Writes the draft now. Nothing is written while restoring, nor ever again once saved or discarded. */
    suspend fun persistDraft() = draftLock.withLock {
        val state = current.value
        if (draftKey == null || drafts == null || state.restoring || state.saved || state.discarded) return@withLock
        try {
            drafts.write(draftKey, NoteDraft(state.body, state.media.map { it.copy(data = null) }, postId))
            if (!closed && state.draftFailed) current.update { it.copy(draftFailed = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!closed) current.update { it.copy(draftFailed = true) }
        }
    }

    /** Drops this editor's draft, and only its own; a write already on its way cannot bring it back. */
    suspend fun discard(): Boolean = draftLock.withLock {
        try {
            draftKey?.let { drafts?.remove(it) }
            if (!closed) current.update { it.copy(discarded = true) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!closed) current.update { it.copy(draftFailed = true) }
            false
        }
    }

    /** The editor is gone: late answers no longer change its state. */
    fun close() {
        closed = true
    }

    private fun keepDraft() {
        scope?.launch { persistDraft() }
    }
}
