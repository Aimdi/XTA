package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable

/** No system back on the desktop: Esc, Alt+← and the mouse's back button reach the card through the desktop's navigation. */
@Composable
internal actual fun CardBackGesture(enabled: Boolean, events: CardBackEvents) = Unit
