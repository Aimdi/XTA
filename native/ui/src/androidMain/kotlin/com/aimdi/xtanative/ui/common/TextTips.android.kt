package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val TextTipsShown: Boolean = false

@Composable
internal actual fun TextTip(tip: String?, modifier: Modifier, content: @Composable (Modifier) -> Unit) = content(modifier)
