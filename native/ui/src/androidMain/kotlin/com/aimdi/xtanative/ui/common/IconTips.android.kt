package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
actual fun IconTip(label: String, content: @Composable () -> Unit) = content()

@Composable
internal actual fun RingedFocus(content: @Composable () -> Unit) = content()

@Composable
internal actual fun UnringedFocus(content: @Composable () -> Unit) = content()

@Composable
internal actual fun Modifier.keyboardFocusRing(): Modifier = this

@Composable
internal actual fun Modifier.keyboardFocusRing(outset: Dp): Modifier = this

internal actual fun Modifier.pointerOnlyFocus(): Modifier = this
