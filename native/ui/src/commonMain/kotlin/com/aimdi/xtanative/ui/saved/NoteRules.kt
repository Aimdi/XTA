package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.store.saved.NoteEditorState
import com.aimdi.xtanative.store.saved.NoteMedia
import kotlinx.serialization.json.JsonElement

/** The title a note editor opens with. */
enum class NoteTitle { NEW, EDIT, REPLY }

/** What the note editor writes: a note of its own, or the text of a saved post's note or of a highlight. */
sealed interface NoteTarget {
    /**
     * A note of the Notes tab: a new one, [editing] an existing one, or
     * [replyTo] another; a new one can quote the post [quotedId], kept as [quoted].
     */
    data class Note(
        val editing: LocalPost? = null,
        val replyTo: LocalPost? = null,
        val quotedId: String? = null,
        val quoted: JsonElement? = null,
    ) : NoteTarget

    /** The note under a saved post, or under an article's highlight: text alone. */
    data class Text(val note: String?, val draftKey: String, val allowUnchanged: Boolean = false) : NoteTarget
}

/** XTA's note editors, as rules (local_post_compose.dart, saved_note_editor.dart, note_editor_frame.dart). */
object NoteRules {
    /** The draft key an editor keeps what is typed under: one per note edited, per note answered, per post quoted. */
    fun draftKey(target: NoteTarget.Note): String = when {
        target.editing != null -> "edit:${target.editing.id}"
        target.replyTo != null -> "reply:${target.replyTo.id}"
        target.quotedId != null -> "quote:${target.quotedId}"
        else -> "new"
    }

    /** The saved post's note is kept as a draft under this key. */
    fun savedNoteKey(postId: String): String = "saved:$postId"

    fun title(target: NoteTarget): NoteTitle = when (target) {
        is NoteTarget.Note -> when {
            target.editing != null -> NoteTitle.EDIT
            target.replyTo != null -> NoteTitle.REPLY
            else -> NoteTitle.NEW
        }
        is NoteTarget.Text -> if (target.note.isNullOrEmpty()) NoteTitle.NEW else NoteTitle.EDIT
    }

    /** A note needs something in it, and an edit something changed; a text note anything changed, unless any is fine. */
    fun canSave(target: NoteTarget, state: NoteEditorState, dirty: Boolean): Boolean = when (target) {
        is NoteTarget.Note -> state.hasContent && (target.editing == null || dirty)
        is NoteTarget.Text -> dirty || target.allowUnchanged
    }

    /** What closing the editor does: nothing while it works, at once when nothing would be lost, else it asks first. */
    fun onClose(state: NoteEditorState, dirty: Boolean): NoteClose = when {
        state.busy -> NoteClose.WAIT
        !dirty || state.saved || state.discarded -> NoteClose.CLOSE
        else -> NoteClose.CONFIRM
    }

    /** How full the note is, from 0 to 1, for the ring beside Save. */
    fun fullness(body: String): Float = (body.codePointCount(0, body.length).toFloat() / LocalPosts.MAX_LENGTH).coerceIn(0f, 1f)

    /** How far a note of a thread is indented, in dp: replies step in, at most two steps. */
    fun indent(depth: Int): Int = depth.coerceIn(0, 2) * 12

    /** The files of a note as drawn: its pictures in a grid of at most four, the rest as rows. */
    fun layout(media: List<NoteMedia>): NoteMediaLayout {
        val images = media.filter { it.isImage }
        return NoteMediaLayout(images.take(4), images.size == 1, media.filterNot { it.isImage })
    }

    /** What a note's reply button does: open the thread once there are replies to read, else write one. */
    fun replyOpensThread(replies: Int): Boolean = replies > 0
}

/** What closing a note editor does. */
enum class NoteClose { WAIT, CLOSE, CONFIRM }

/** A note's [images] (one drawn [single] and tall), then its [files]. */
data class NoteMediaLayout(val images: List<NoteMedia>, val single: Boolean, val files: List<NoteMedia>)
