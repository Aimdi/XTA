package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberShowInFolder(): (suspend (savedUri: String) -> Boolean)? = null
