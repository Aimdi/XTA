package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.Composable

@Composable
internal actual fun ProvidePlatformLook(content: @Composable () -> Unit) = content()
