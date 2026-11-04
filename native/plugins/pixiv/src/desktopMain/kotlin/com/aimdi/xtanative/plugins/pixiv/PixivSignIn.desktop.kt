package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val webSignIn: Boolean = false

@Composable
internal actual fun PixivLoginPage(url: String, onAddress: (String) -> Boolean, modifier: Modifier) = Unit
