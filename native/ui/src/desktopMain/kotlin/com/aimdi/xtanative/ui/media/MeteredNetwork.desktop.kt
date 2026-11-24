package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

private val UNMETERED: State<Boolean> = mutableStateOf(false)

/** The desktop's network is never taken for metered. */
@Composable
internal actual fun rememberMeteredNetwork(): State<Boolean> = UNMETERED
