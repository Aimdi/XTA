package com.aimdi.xtanative.ui.home

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.navigation.NavHostController

/**
 * Android goes back with its own gesture and button (and a keyboard's Esc,
 * which Android makes Back). A hardware keyboard's reading keys are answered
 * here ([HardwareKeys]): only a key the screens left unused, so a field types
 * it, a button is pressed by it and the arrows walk the controls first.
 * Nothing takes the focus for it, and no key of the on-screen keyboard, a
 * remote or a headset is acted on.
 */
@Composable
actual fun DesktopNavigation(nav: NavHostController, openSearch: () -> Unit, openSettings: () -> Unit, openHome: () -> Unit, goForward: () -> Boolean, content: @Composable () -> Unit) {
    val actions = remember { DesktopActions() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val line by rememberUpdatedState(with(LocalDensity.current) { LINE.toPx() })
    val search by rememberUpdatedState(openSearch)
    DisposableEffect(view, actions) {
        val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { host, event ->
            actions.onHardwareKey(hardwareAction(event, host, actions), scope, line) { search() }
        }
        ViewCompat.addOnUnhandledKeyEventListener(view, listener)
        onDispose { ViewCompat.removeOnUnhandledKeyEventListener(view, listener) }
    }
    CompositionLocalProvider(LocalHardwareKeyActions provides actions, content = content)
}

/** What [event], which nothing in [host]'s window used, does ([HardwareKeys.action]): nothing unless it is a key going down on a hardware keyboard. */
private fun hardwareAction(event: KeyEvent, host: View, actions: DesktopActions): DeskAction {
    if (event.action != KeyEvent.ACTION_DOWN || !fromHardwareKeyboard(event)) return DeskAction.NONE
    // A field being typed in, Compose's or a view's: it has the keys, whatever it left of them.
    val typing = host.rootView.findFocus()?.onCheckIsTextEditor() == true
    return HardwareKeys.action(
        key = hardwareKey(event),
        alt = event.isAltPressed,
        ctrl = event.isCtrlPressed,
        shift = event.isShiftPressed,
        meta = event.isMetaPressed,
        typing = typing,
        repeated = event.repeatCount > 0,
        somethingOpen = actions.somethingOpen(),
        openIsRead = actions.openIsRead(),
    )
}

/** A key of a keyboard with letters, plugged in or paired: not the on-screen keyboard's (nor a key it sends), a remote's, a gamepad's or a headset's. */
private fun fromHardwareKeyboard(event: KeyEvent): Boolean =
    event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD &&
        (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) == 0 &&
        event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
        event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

/** The keys [HardwareKeys] answers; / is the character typed, on any layout. */
private fun hardwareKey(event: KeyEvent): DeskKey = when (event.keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> DeskKey.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> DeskKey.DOWN
    KeyEvent.KEYCODE_PAGE_UP -> DeskKey.PAGE_UP
    KeyEvent.KEYCODE_PAGE_DOWN -> DeskKey.PAGE_DOWN
    KeyEvent.KEYCODE_SPACE -> DeskKey.SPACE
    KeyEvent.KEYCODE_MOVE_HOME -> DeskKey.HOME
    KeyEvent.KEYCODE_MOVE_END -> DeskKey.END
    KeyEvent.KEYCODE_J -> DeskKey.J
    KeyEvent.KEYCODE_K -> DeskKey.K
    KeyEvent.KEYCODE_F -> DeskKey.F
    KeyEvent.KEYCODE_R -> DeskKey.R
    KeyEvent.KEYCODE_F5 -> DeskKey.F5
    else -> if (event.unicodeChar == '/'.code) DeskKey.SLASH else DeskKey.OTHER
}
