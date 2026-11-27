package com.aimdi.xtanative.ui.links

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** A place of the app a start can ask for (`xta-native --saved`), as the desktop entry's actions do. */
enum class LaunchPlace(val key: String) {
    SAVED("saved"),
    SETTINGS("settings"),
    DECK("deck"),
    ;

    companion object {
        fun of(key: String): LaunchPlace? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a start hands the main window besides links: files to open
 * (`xta-native backup.json`) and places to show. Each waits until the
 * window's screens take it, and goes to one of them only.
 */
object OpenRequests {
    private const val WAITING = 8
    private val files = Channel<String>(WAITING, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val places = Channel<LaunchPlace>(WAITING, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Opens the file at [path] (absolute) once the window is up, after asking the reader. */
    fun file(path: String) {
        files.trySend(path)
    }

    /** Shows [place] once the window is up. */
    fun place(place: LaunchPlace) {
        places.trySend(place)
    }

    internal val openedFiles: Flow<String> = files.receiveAsFlow()
    internal val shownPlaces: Flow<LaunchPlace> = places.receiveAsFlow()
}
