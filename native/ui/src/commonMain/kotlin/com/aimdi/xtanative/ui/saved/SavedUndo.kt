package com.aimdi.xtanative.ui.saved

/**
 * The undo a removal in Saved offers: "Changes saved" with Undo
 * ([KeepNotice.ChangesSaved]), which Ctrl+Z also presses on the desktop.
 */
object SavedUndo {
    /** Offers to put [taken] back with [putBack], when the removal took anything. */
    fun <T : Any> offer(taken: T?, putBack: suspend (T) -> Boolean) {
        if (taken != null) KeepNotices.show(KeepNotice.ChangesSaved { putBack(taken) })
    }
}
