package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun ProvidePlatformLook(content: @Composable () -> Unit) = content()

@Composable
internal actual fun systemPrefersDark(): Boolean = isSystemInDarkTheme()

@Composable
internal actual fun systemReducesMotion(): Boolean = false

@Composable
internal actual fun systemAccent(): Long? = null

@Composable
internal actual fun keyboardFocusShown(): Boolean = true
