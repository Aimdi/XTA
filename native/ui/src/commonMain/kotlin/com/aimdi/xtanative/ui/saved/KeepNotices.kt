package com.aimdi.xtanative.ui.saved

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A line saving, liking, downloading or keeping something offline says at
 * the foot of the window, as XTA's snack bars do. What it says is chosen
 * here; the words are the screens'.
 */
sealed interface KeepNotice {
    /** Shown until the next notice replaces it, as XTA's working snack bar is. */
    val working: Boolean get() = false

    /** Read for longer than the others. */
    val long: Boolean get() = false

    data object Downloading : KeepNotice {
        override val working get() = true
    }

    data object MediaSaved : KeepNotice

    /** Saved into a folder the reader did not pick, which [savedUri] opens. */
    data class SavedTo(val folder: String, val savedUri: String, val fileName: String) : KeepNotice

    data object RetryHint : KeepNotice

    data object DownloadFailed : KeepNotice

    /** A save of X's media the host answered, naming the status it returned. */
    data class MediaRefused(val status: Int) : KeepNotice

    /** The media is a stream with no file to save. */
    data object NoMediaFile : KeepNotice

    /** A folder set to auto-download cannot while downloads ask where to save. */
    data object NeedsDownloadFolder : KeepNotice

    data class Batch(val saved: Int, val total: Int) : KeepNotice

    /** Once, after the first save: a long press files into a folder. */
    data object FolderHint : KeepNotice

    /** Once, after the first like: likes stay here. */
    data object LikesStayHere : KeepNotice {
        override val long get() = true
    }

    /** The lock beside Saved's title, tapped. */
    data class OnDevice(val notice: SavedNotice) : KeepNotice

    data object OpenFailed : KeepNotice

    data object OfflineFailed : KeepNotice

    data object OfflineUnavailable : KeepNotice

    data class OfflineKept(val article: Boolean, val saved: Int, val total: Int) : KeepNotice

    data object NoteSaveFailed : KeepNotice

    /** A note written about a post is in Notes. */
    data object NoteSaved : KeepNotice

    data object SomethingWentWrong : KeepNotice

    /** Words already in the reader's language: a plugin's (how a post it sent to the reader's own server went), or a screen's own. */
    data class Said(val text: String, override val working: Boolean = false) : KeepNotice

    /** "Changes saved" with XTA's undo (UndoHost): [undo] takes the change back, answering whether it could. */
    class ChangesSaved(val undo: suspend () -> Boolean) : KeepNotice {
        override val long get() = true
    }

    /** The undo could not be made: what it would have put back changed since. */
    data object UndoFailed : KeepNotice

    /**
     * A long piece of work in a plugin's words, as XTA's working snack bar
     * with its ring: what it does, how far (0 to 1, null while it cannot
     * say), a note under it, and Cancel while [onCancel] is there.
     */
    data class Progress(val text: String, val fraction: Float?, val note: String?, val onCancel: (() -> Unit)?) : KeepNotice {
        override val working get() = true
    }

    /** The reader stopped a long piece of work. */
    data object Cancelled : KeepNotice
}

/** The notice showing now, one at a time: a new one replaces it. [serial] tells two of the same apart. */
data class ShownNotice(val notice: KeepNotice, val serial: Long)

/** Where notices are posted from anywhere, and read by the one host at the root of the window. */
object KeepNotices {
    private val serials = AtomicLong()
    private val current = MutableStateFlow<ShownNotice?>(null)

    val shown: StateFlow<ShownNotice?> = current.asStateFlow()

    fun show(notice: KeepNotice) {
        current.value = ShownNotice(notice, serials.incrementAndGet())
    }

    /** Says [text], already in the reader's language; nothing for a blank one. */
    fun say(text: String) {
        if (text.isNotBlank()) show(KeepNotice.Said(text))
    }

    /** Hides [shown] if it is still the one showing: a newer notice stays. */
    fun hide(shown: ShownNotice) {
        current.update { if (it?.serial == shown.serial) null else it }
    }

    /** Takes a working notice away, as XTA hides its working snack bar once the work is done. */
    fun endWorking() {
        current.update { if (it?.notice?.working == true) null else it }
    }
}
