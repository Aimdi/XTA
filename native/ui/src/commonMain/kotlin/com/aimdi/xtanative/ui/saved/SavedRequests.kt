package com.aimdi.xtanative.ui.saved

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The tab of Saved another part of the app asked for (the desktop's quick switcher, a folder picked), by its token, until Saved takes it. */
object SavedRequests {
    private val asked = MutableStateFlow<String?>(null)

    val tab: StateFlow<String?> = asked.asStateFlow()

    fun show(tab: String) {
        asked.value = tab
    }

    /** Taken: true when [tab] was the one asked for, so it is acted on once. */
    fun taken(tab: String): Boolean = asked.compareAndSet(tab, null)
}
