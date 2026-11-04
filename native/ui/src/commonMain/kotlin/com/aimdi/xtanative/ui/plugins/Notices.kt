package com.aimdi.xtanative.ui.plugins

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What a plugin tells the reader at the foot of its screen ("Account
 * added", how a test went), as XTA's snack bars do: [tell] it from anywhere,
 * and the [NoticeHost] of the screen on view shows it, the newest over the
 * one before. Nothing is kept for a screen that is not on view.
 */
class PluginNotices {
    private val said = MutableSharedFlow<String>(extraBufferCapacity = KEPT, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each notice as it is told. */
    val notices: SharedFlow<String> = said.asSharedFlow()

    fun tell(text: String) {
        if (text.isNotBlank()) said.tryEmit(text)
    }

    private companion object {
        const val KEPT = 4
    }
}

/**
 * What a long piece of work says at the foot of the window while it runs
 * ([PluginHost.withProgress]), as XTA's working snack bar does: [text], what
 * it is doing; [fraction], how far it has come (0 to 1; null while it cannot
 * say, which turns the ring round and round); [note], a line under it; and
 * Cancel while [cancellable].
 */
data class PluginProgress(val text: String, val fraction: Float? = null, val note: String? = null, val cancellable: Boolean = true)
