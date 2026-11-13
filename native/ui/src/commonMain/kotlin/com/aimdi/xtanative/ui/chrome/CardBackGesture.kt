package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable

/** What the system's back does to a card: the gesture moves it, a press slides it out. */
internal interface CardBackEvents {
    /** The gesture touched at [x], [y] (pixels in the window), from the right edge when [fromRight]. */
    fun started(x: Float, y: Float, fromRight: Boolean)

    /** It is [progress] of the way across. */
    fun progressed(progress: Float)

    fun committed()

    fun cancelled()

    /** Back was pressed, or the gesture gave no progress. */
    fun pressed()
}

/**
 * The system's back while [enabled]: on Android 14 and newer its gesture
 * moves the card (XTA's handleStartBackGesture), on older ones and for a
 * button it is a press. The desktop has none: its keys and mouse go back
 * through [com.aimdi.xtanative.ui.home.DesktopShortcuts].
 */
@Composable
internal expect fun CardBackGesture(enabled: Boolean, events: CardBackEvents)
