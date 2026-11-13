package com.aimdi.xtanative.plugins.threads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val webSignIn: Boolean = false

@Composable
internal actual fun ThreadsLoginPage(onCookies: (String) -> Boolean, modifier: Modifier) = Unit
