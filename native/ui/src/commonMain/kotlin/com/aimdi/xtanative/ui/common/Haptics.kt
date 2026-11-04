package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccessibilityPrefs

/**
 * Settings › Accessibility › Haptic feedback (XTA's hapticFeedbackOn): on
 * unless the reader turned it off. The desktop has nothing to tap with, so
 * it has no switch and its haptics do nothing.
 */
val LocalHapticsOn = compositionLocalOf { true }

/** [platform]'s haptics, played only while [on] says so. */
class SwitchedHaptics(private val platform: HapticFeedback, private val on: () -> Boolean) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (on()) platform.performHapticFeedback(hapticFeedbackType)
    }
}

/**
 * [content] with every haptic behind the reader's switch: the app's, the
 * plugins' and Compose's own (a long click), all of which play through
 * [LocalHapticFeedback].
 */
@Composable
fun SwitchedHapticsFrame(content: @Composable () -> Unit) {
    val stored by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    val on = stored?.get(AccessibilityPrefs.haptics) ?: true
    val latest = rememberUpdatedState(on)
    val platform = LocalHapticFeedback.current
    val switched = remember(platform) { SwitchedHaptics(platform) { latest.value } }
    CompositionLocalProvider(LocalHapticFeedback provides switched, LocalHapticsOn provides on, content = content)
}

/**
 * The haptics to play from here, behind the reader's switch: Compose gives
 * a dialog's or a sheet's own window haptics of its own, past
 * [SwitchedHapticsFrame], so what plays from one goes through this.
 */
@Composable
fun rememberSwitchedHaptics(): HapticFeedback {
    val platform = LocalHapticFeedback.current
    val on = rememberUpdatedState(LocalHapticsOn.current)
    return remember(platform) { SwitchedHaptics(platform) { on.value } }
}
