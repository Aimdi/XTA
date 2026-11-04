package com.aimdi.xtanative.ui.common

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings › Accessibility › Haptic feedback silences every haptic played through the app (XTA's hapticFeedbackOn). */
class SwitchedHapticsTest {
    private class Played : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }

    @Test
    fun aHapticPlaysOnlyWhileTheSwitchIsOn() {
        val platform = Played()
        var on = true
        val switched = SwitchedHaptics(platform) { on }
        switched.performHapticFeedback(HapticFeedbackType.LongPress)
        on = false
        switched.performHapticFeedback(HapticFeedbackType.LongPress)
        switched.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        on = true
        switched.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        assertEquals(listOf(HapticFeedbackType.LongPress, HapticFeedbackType.TextHandleMove), platform.types)
    }
}
