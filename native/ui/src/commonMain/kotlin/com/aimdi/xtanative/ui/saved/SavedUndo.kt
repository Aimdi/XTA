package com.aimdi.xtanative.ui.saved

/**
 * The undo a removal in Saved offers: "Changes saved" with Undo
 * ([KeepNotice.ChangesSaved]), which Ctrl+Z also presses on the desktop.
 */
object SavedUndo {
    /** Offers to put [taken] back with [putBack], when the removal took anything. */
    fun <T : Any> offer(taken: T?, putBack: suspend (T) -> Boolean) = offer(taken, putBack, discard = {})

    /**
     * [offer], where [taken] holds something to let go of ([discard]) once
     * the undo is no longer offered: a deleted note's files set aside.
     */
    fun <T : Any> offer(taken: T?, putBack: suspend (T) -> Boolean, discard: (T) -> Unit) {
        if (taken != null) KeepNotices.show(KeepNotice.ChangesSaved(undo = { putBack(taken) }, ended = { discard(taken) }))
    }
}
