package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

@Composable
internal actual fun IconTip(label: String, side: TipSide, content: @Composable () -> Unit) = content()
