package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/** No system Back on the desktop: the bar's back arrow closes what is open. */
@Composable
internal actual fun BackHandling(enabled: Boolean, onBack: () -> Unit) = Unit
