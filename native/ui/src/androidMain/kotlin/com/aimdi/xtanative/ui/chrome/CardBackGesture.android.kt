package com.aimdi.xtanative.ui.chrome

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException

@Composable
internal actual fun CardBackGesture(enabled: Boolean, events: CardBackEvents) {
    val latest by rememberUpdatedState(events)
    PredictiveBackHandler(enabled = enabled) { progress ->
        var started = false
        try {
            progress.collect { event ->
                if (!started) {
                    started = true
                    latest.started(event.touchX, event.touchY, event.swipeEdge == BackEventCompat.EDGE_RIGHT)
                }
                latest.progressed(event.progress)
            }
            if (started) latest.committed() else latest.pressed()
        } catch (e: CancellationException) {
            if (started) latest.cancelled()
            throw e
        }
    }
}
