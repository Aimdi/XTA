package com.aimdi.xtanative.ui.links

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What a start hands the main window besides links: files to open
 * (`xta-native backup.json`). Each waits until the
 * window's screens take it, and goes to one of them only.
 */
object OpenRequests {
    private const val WAITING = 8
    private val files = Channel<String>(WAITING, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Opens the file at [path] (absolute) once the window is up, after asking the reader. */
    fun file(path: String) {
        files.trySend(path)
    }

    internal val openedFiles: Flow<String> = files.receiveAsFlow()
}
