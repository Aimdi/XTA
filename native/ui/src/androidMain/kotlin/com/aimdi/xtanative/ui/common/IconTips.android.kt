package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun IconTip(label: String, content: @Composable () -> Unit) = content()

@Composable
internal actual fun Modifier.keyboardFocusRing(): Modifier = this
